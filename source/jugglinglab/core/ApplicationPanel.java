// ApplicationPanel.java
//
// Copyright 2002-2021 Jack Boyce and the Juggling Lab contributors

package jugglinglab.core;

import java.awt.*;
import java.awt.event.*;
import java.util.ResourceBundle;
import java.text.MessageFormat;
import javax.swing.*;
import javax.swing.event.*;

import jugglinglab.generator.*;
import jugglinglab.jml.JMLPattern;
import jugglinglab.notation.*;
import jugglinglab.util.*;
import jugglinglab.view.View;


// This class represents the entire contents of the ApplicationWindow frame.
// For a given notation type it creates a tabbed pane with separate panels for
// pattern entry, transitions, and generator.

public class ApplicationPanel extends JPanel implements ActionListener {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    protected JFrame parent;
    protected JTabbedPane jtp;
    protected View animtarget;
    protected PatternListPanel patlist;
    protected boolean patlisttab = false;

    protected int currentnum = -1;
    protected JButton juggle_button;
    protected JButton trans_button;
    protected JButton gen_button;
    protected JLabel gen_busy;

    // Execution limits for generator
    protected static final int max_patterns = 1000;
    protected static final double max_time = 15.0;


    public ApplicationPanel(JFrame parent) {
        this(parent, null, null, false);
    }

    public ApplicationPanel(JFrame p, View v, PatternListPanel pl, boolean patlisttab) {
        parent = p;

        // fields below are currently unused; they supported the applet version
        // of Juggling Lab
        animtarget = v;
        patlist = pl;
        patlisttab = (pl == null) ? patlisttab : true;
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        String command = ae.getActionCommand();

        try {
            if (command.startsWith("notation")) {
                try {
                    int num = Integer.parseInt(command.substring(8));
                    if (num != currentnum) {
                        setNotation(num);
                        if (parent != null)
                            parent.pack();
                        currentnum = num;
                    }
                } catch (NumberFormatException nfe) {
                    throw new JuggleExceptionInternal("Error in notation number coding");
                }
            }
        } catch (Exception e) {
            ErrorDialog.handleFatalException(e);
        }
    }

    // input is for example Pattern.NOTATION_SITESWAP
    public void setNotation(int num) throws JuggleExceptionUser, JuggleExceptionInternal {
        if (num > Pattern.builtinNotations.length)
            return;

        if (jtp != null)
            remove(jtp);
        jtp = new JTabbedPane();

        switch (num) {
            case Pattern.NOTATION_SITESWAP:
                addPatternEntryControl(new SiteswapNotationControl());
                break;
        }

        PatternListPanel pl = patlist;
        if (pl == null && patlisttab)
            pl = new PatternListPanel(animtarget);

        Transitioner trans = Transitioner.newTransitioner(Pattern.builtinNotations[num - 1]);
        if (trans != null)
            addTransitionerControl(trans, pl);

        Generator gen = Generator.newGenerator(Pattern.builtinNotations[num - 1]);
        if (gen != null)
            addGeneratorControl(gen, pl);

        if (pl != null) {
            jtp.addTab(guistrings.getString("Pattern_list_tab"), pl);
            if (patlist != null)
                jtp.setSelectedComponent(pl);  // if we loaded from a file
        }

        // Change the default button when the tab changes
        jtp.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                getRootPane().setDefaultButton(getDefaultButton());
            }
        });

        setLayout(new BorderLayout());
        add(jtp, BorderLayout.CENTER);

        if (parent != null)
            parent.getRootPane().setDefaultButton(getDefaultButton());
    }

    protected void addPatternEntryControl(NotationControl control) {
        final NotationControl fcontrol = control;

        JPanel np1 = new JPanel();
        np1.setLayout(new BorderLayout());
        np1.add(control, BorderLayout.PAGE_START);

        JPanel np2 = new JPanel();
        np2.setLayout(new FlowLayout(FlowLayout.TRAILING));
        JButton nbut1 = new JButton(guistrings.getString("Defaults"));
        nbut1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                try {
                    fcontrol.resetControl();
                } catch (Exception e) {
                    ErrorDialog.handleFatalException(e);
                }
            }
        });
        np2.add(nbut1);

        juggle_button = new JButton(guistrings.getString("Juggle"));
        juggle_button.setDefaultCapable(true);

        juggle_button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                PatternWindow jaw2 = null;
                try {
                    ParameterList pl = fcontrol.getParameterList();
                    Pattern p = fcontrol.newPattern().fromParameters(pl);
                    AnimationPrefs jc = (new AnimationPrefs()).fromParameters(pl);
                    pl.errorIfParametersLeft();

                    String notation = p.getNotationName();
                    String config = p.toString();
                    JMLPattern pat = JMLPattern.fromBasePattern(notation, config);
                    pat.layoutPattern();

                    if (PatternWindow.bringToFront(pat.getHashCode()))
                        return;

                    if (animtarget != null)
                        animtarget.restartView(pat, jc);
                    else
                        jaw2 = new PatternWindow(pat.getTitle(), pat, jc);
                } catch (JuggleExceptionUser je) {
                    if (jaw2 != null)
                        jaw2.dispose();
                    new ErrorDialog(ApplicationPanel.this, je.getMessage());
                } catch (Exception e) {
                    if (jaw2 != null)
                        jaw2.dispose();
                    ErrorDialog.handleFatalException(e);
                }
            }
        });
        np2.add(juggle_button);
        np1.add(np2, BorderLayout.PAGE_END);

        jtp.addTab(guistrings.getString("Pattern_entry"), np1);
    }

    protected void addTransitionerControl(Transitioner trans, PatternListPanel pl) {
        JPanel p1 = new JPanel();
        p1.setLayout(new BorderLayout());
        p1.add(trans.getTransitionerControl(), BorderLayout.PAGE_START);
        JPanel p2 = new JPanel();
        p2.setLayout(new FlowLayout(FlowLayout.TRAILING));

        JButton but1 = new JButton(guistrings.getString("Defaults"));

        but1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                trans.resetTransitionerControl();
            }
        });
        p2.add(but1);

        trans_button = new JButton(guistrings.getString("Run"));
        trans_button.setDefaultCapable(true);

        trans_button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                Thread t = new Thread() {
                    @Override
                    public void run() {
                        trans_button.setEnabled(false);
                        PatternListWindow pw = null;
                        try {
                            trans.initTransitioner();
                            GeneratorTarget pwot = null;
                            if (pl != null) {
                                pl.clearList();
                                pwot = new GeneratorTarget(pl);
                                //jtp.setSelectedComponent(pl);
                            } else {
                                String title = trans.getNotationName() + " " + guistrings.getString("Patterns");
                                pw = new PatternListWindow(title, this);
                                pwot = new GeneratorTarget(pw);
                            }
                            trans.runTransitioner(pwot, max_patterns, max_time);
                            if (pl != null)
                                jtp.setSelectedComponent(pl);
                        } catch (JuggleExceptionDone ex) {
                            if (pl != null)
                                jtp.setSelectedComponent(pl);
                            Component parent = pw;
                            if (pw == null)
                                parent = pl;
                            new LabelDialog(parent, guistrings.getString("Generator_stopped_title"), ex.getMessage());
                        } catch (JuggleExceptionInterrupted jei) {
                            //System.out.println("generator thread quit");
                        } catch (JuggleExceptionUser ex) {
                            if (pw != null)
                                pw.dispose();
                            new ErrorDialog(ApplicationPanel.this, ex.getMessage());
                        } catch (Exception e) {
                            if (pw != null)
                                pw.dispose();
                            ErrorDialog.handleFatalException(e);
                        }

                        trans_button.setEnabled(true);
                    }
                };
                t.start();
            }
        });
        p2.add(trans_button);

        JPanel p3 = new JPanel();
        p3.setLayout(new BorderLayout());
        p3.add(p2, BorderLayout.LINE_END);

        p1.add(p3, BorderLayout.PAGE_END);

        jtp.addTab(guistrings.getString("Transitions"), p1);
    }

    protected void addGeneratorControl(Generator gen, PatternListPanel pl) {
        JPanel p1 = new JPanel();
        p1.setLayout(new BorderLayout());
        p1.add(gen.getGeneratorControl(), BorderLayout.PAGE_START);
        JPanel p2 = new JPanel();
        p2.setLayout(new FlowLayout(FlowLayout.TRAILING));

        JButton but1 = new JButton(guistrings.getString("Defaults"));

        but1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                gen.resetGeneratorControl();
            }
        });
        p2.add(but1);

        gen_button = new JButton(guistrings.getString("Run"));

        gen_button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                Thread t = new Thread() {
                    @Override
                    public void run() {
                        gen_busy.setVisible(true);
                        gen_button.setEnabled(false);
                        PatternListWindow pw = null;
                        try {
                            gen.initGenerator();
                            GeneratorTarget pwot = null;
                            if (pl != null) {
                                pl.clearList();
                                pwot = new GeneratorTarget(pl);
                                //jtp.setSelectedComponent(pl);
                            } else {
                                String title = gen.getNotationName() + " " + guistrings.getString("Patterns");
                                pw = new PatternListWindow(title, this);
                                pwot = new GeneratorTarget(pw);
                            }
                            gen.runGenerator(pwot, max_patterns, max_time);
                            if (pl != null)
                                jtp.setSelectedComponent(pl);
                        } catch (JuggleExceptionDone ex) {
                            if (pl != null)
                                jtp.setSelectedComponent(pl);
                            Component parent = pw;
                            if (pw == null)
                                parent = pl;
                            new LabelDialog(parent, guistrings.getString("Generator_stopped_title"), ex.getMessage());
                        } catch (JuggleExceptionInterrupted jei) {
                            //System.out.println("generator thread quit");
                        } catch (JuggleExceptionUser ex) {
                            if (pw != null)
                                pw.dispose();
                            new ErrorDialog(ApplicationPanel.this, ex.getMessage());
                        } catch (Exception e) {
                            if (pw != null)
                                pw.dispose();
                            ErrorDialog.handleFatalException(e);
                        }

                        gen_busy.setVisible(false);
                        gen_button.setEnabled(true);
                    }
                };
                t.start();
            }
        });
        p2.add(gen_button);

        gen_busy = new JLabel(guistrings.getString("Processing"));
        gen_busy.setVisible(false);
        JPanel p3 = new JPanel();
        p3.setLayout(new BorderLayout());
        JPanel p4 = new JPanel();
        GridBagLayout gb = new GridBagLayout();
        p4.setLayout(gb);
        p4.add(gen_busy);
        gb.setConstraints(gen_busy, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 0, new Insets(0,10,0,0)));
        p3.add(p4, BorderLayout.LINE_START);
        p3.add(p2, BorderLayout.LINE_END);

        p1.add(p3, BorderLayout.PAGE_END);

        jtp.addTab(guistrings.getString("Generator"), p1);
    }

    protected JButton getDefaultButton() {
        if (jtp == null)
            return null;
        if (jtp.getSelectedIndex() == 0)
            return juggle_button;
        else if (jtp.getSelectedIndex() == 1)
            return trans_button;
        else
            return gen_button;
    }
}
