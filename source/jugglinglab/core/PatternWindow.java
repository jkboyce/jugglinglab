// PatternWindow.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.core;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

import jugglinglab.jml.*;
import jugglinglab.util.*;
import jugglinglab.view.*;


public class PatternWindow extends JFrame implements ActionListener {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    protected View view;
    protected JMenu filemenu;
    protected JMenu viewmenu;
    protected boolean exit_on_close = false;


    public PatternWindow(String name, JMLPattern pat, AnimationPrefs jc) throws
                            JuggleExceptionUser, JuggleExceptionInternal {
        super(name);

        JMenuBar mb = new JMenuBar();
        this.filemenu = createFileMenu();
        mb.add(filemenu);
        this.viewmenu = createViewMenu();
        mb.add(viewmenu);
        setJMenuBar(mb);

        if (pat.getNumberOfJugglers() > 1) {
            setViewMode(VIEW_SIMPLE);
            viewmenu.getItem(0).setSelected(true);
        } else {
            setViewMode(VIEW_EDIT);
            viewmenu.getItem(1).setSelected(true);
        }

        view.setDoubleBuffered(true);
        if (jc != null)
            view.setAnimationPanelPreferredSize(new Dimension(jc.width, jc.height));

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setBackground(Color.white);

        Locale loc = JLLocale.getLocale();
        applyComponentOrientation(ComponentOrientation.getOrientation(loc));

        pack();
        view.restartView(pat, jc);
        setLocationRelativeTo(null);    // center frame on screen
        setVisible(true);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (exit_on_close)
                    System.exit(0);
            }
        });
    }

    public void setExitOnClose(boolean value) {
        this.exit_on_close = value;
    }

    protected static final String[] fileItems = new String[]
        { "Close", null, "Save JML As...", "Save Animated GIF As...", null, "Duplicate" };
    protected static final String[] fileCommands = new String[]
        { "close", null, "saveas", "savegifanim", null, "duplicate" };
    protected static final char[] fileShortcuts =
        { 'W', ' ', 'S', ' ', ' ', 'D' };

    protected JMenu createFileMenu() {
        JMenu filemenu = new JMenu(guistrings.getString("File"));
        for (int i = 0; i < fileItems.length; i++) {
            if (fileItems[i] == null)
                filemenu.addSeparator();
            else {
                JMenuItem fileitem = new JMenuItem(
                        guistrings.getString(fileItems[i].replace(' ', '_')));

                if (fileShortcuts[i] != ' ')
                    fileitem.setAccelerator(KeyStroke.getKeyStroke(fileShortcuts[i],
                            Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));

                fileitem.setActionCommand(fileCommands[i]);
                fileitem.addActionListener(this);
                filemenu.add(fileitem);
            }
        }
        return filemenu;
    }

    protected static final String[] viewItems = new String[]
        { "Simple", "Visual editor", "Selection editor", "JML editor", null,
          "Restart", "Animation Preferences..." };
    protected static final String[] viewCommands = new String[]
        { "simple", "edit", "selection", "jml", null, "restart", "prefs" };
    protected static final char[] viewShortcuts =
        { '1', '2', '3', '4', ' ', ' ', 'P' };

    protected JMenu createViewMenu() {
        JMenu viewmenu = new JMenu(guistrings.getString("View"));
        ButtonGroup buttonGroup = new ButtonGroup();
        boolean addingviews = true;

        for (int i = 0; i < viewItems.length; i++) {
            if (viewItems[i] == null) {
                viewmenu.addSeparator();
                addingviews = false;
            } else if (addingviews) {
                JRadioButtonMenuItem viewitem = new JRadioButtonMenuItem(
                        guistrings.getString(viewItems[i].replace(' ', '_')));

                if (viewShortcuts[i] != ' ')
                    viewitem.setAccelerator(KeyStroke.getKeyStroke(viewShortcuts[i],
                            Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));

                viewitem.setActionCommand(viewCommands[i]);
                viewitem.addActionListener(this);
                viewmenu.add(viewitem);
                buttonGroup.add(viewitem);
            } else {
                JMenuItem viewitem = new JMenuItem(
                        guistrings.getString(viewItems[i].replace(' ', '_')));

                if (viewShortcuts[i] != ' ')
                    viewitem.setAccelerator(KeyStroke.getKeyStroke(viewShortcuts[i],
                            Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));

                viewitem.setActionCommand(viewCommands[i]);
                viewitem.addActionListener(this);
                viewmenu.add(viewitem);
            }
        }
        return viewmenu;
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        String command = ae.getActionCommand();

        try {
            if (command.equals("close"))
                doMenuCommand(FILE_CLOSE);
            else if (command.equals("saveas"))
                doMenuCommand(FILE_SAVE);
            else if (command.equals("savegifanim"))
                doMenuCommand(FILE_GIFSAVE);
            else if (command.equals("duplicate"))
                doMenuCommand(FILE_DUPLICATE);
            else if (command.equals("restart"))
                doMenuCommand(VIEW_RESTART);
            else if (command.equals("prefs"))
                doMenuCommand(VIEW_ANIMPREFS);
            else if (command.equals("simple")) {
                if (getViewMode() != VIEW_SIMPLE)
                    setViewMode(VIEW_SIMPLE);
            }
            else if (command.equals("edit")) {
                if (getViewMode() != VIEW_EDIT)
                    setViewMode(VIEW_EDIT);
            }
            else if (command.equals("selection")) {
                if (getViewMode() != VIEW_SELECTION)
                    setViewMode(VIEW_SELECTION);
            }
            else if (command.equals("jml")) {
                if (getViewMode() != VIEW_JML)
                    setViewMode(VIEW_JML);
            }
        } catch (JuggleExceptionUser je) {
            new ErrorDialog(this, je.getMessage());
        } catch (Exception e) {
            ErrorDialog.handleFatalException(e);
        }
    }

    protected static final int FILE_NONE = 0;
    protected static final int FILE_CLOSE = 1;
    protected static final int FILE_SAVE = 2;
    protected static final int FILE_GIFSAVE = 3;
    protected static final int FILE_DUPLICATE = 4;
    protected static final int VIEW_RESTART = 5;
    protected static final int VIEW_ANIMPREFS = 6;

    protected void doMenuCommand(int action) throws JuggleExceptionInternal {
        switch (action) {
            case FILE_NONE:
                break;

            case FILE_CLOSE:
                dispose();
                break;

            case FILE_SAVE:
                if (view != null && view.getPattern().isValid()) {
                    try {
                        int option = PlatformSpecific.getPlatformSpecific().showSaveDialog(this);
                        if (option == JFileChooser.APPROVE_OPTION) {
                            File f = PlatformSpecific.getPlatformSpecific().getSelectedFile();
                            if (f != null) {
                                FileWriter fw = new FileWriter(f);
                                view.getPattern().writeJML(fw, true);
                                fw.close();
                            }
                        }
                    } catch (FileNotFoundException fnfe) {
                        throw new JuggleExceptionInternal("FileNotFound: "+fnfe.getMessage());
                    } catch (IOException ioe) {
                        throw new JuggleExceptionInternal("IOException: "+ioe.getMessage());
                    }
                } else {
                    new ErrorDialog(this, "Could not save: pattern is not valid");
                }
                break;

            case FILE_GIFSAVE:
                if (view != null)
                    view.writeGIF();
                break;

            case FILE_DUPLICATE:
                try {
                    new PatternWindow(this.getTitle(),
                                      (JMLPattern)view.getPattern().clone(),
                                      new AnimationPrefs(view.getAnimationPrefs()));
                } catch (JuggleExceptionUser jeu) {
                    new ErrorDialog(this, jeu.getMessage());
                }
                break;

            case VIEW_RESTART:
                try {
                    if (view != null)
                        view.restartView();
                } catch (JuggleExceptionUser je) {
                    new ErrorDialog(this, je.getMessage());
                } catch (JuggleException je) {
                    throw new JuggleExceptionInternal(je.getMessage());
                }
                break;

            case VIEW_ANIMPREFS:
                AnimationPrefsDialog japd = new AnimationPrefsDialog(this);
                AnimationPrefs jc = null;

                if (view != null) {
                    jc = view.getAnimationPrefs();
                    Dimension dim = view.getAnimationPanelSize();
                    jc.width = dim.width;
                    jc.height = dim.height;
                } else
                    jc = new AnimationPrefs();

                AnimationPrefs newjc = japd.getPrefs(jc);

                if (newjc.width != jc.width || newjc.height != jc.height) {
                    // user changed the width and/or height
                    view.setAnimationPanelPreferredSize(new Dimension(newjc.width,
                                                                newjc.height));
                    pack();
                }

                if (newjc != jc) {  // user clicked OK instead of Cancel?
                    try {
                        if (view != null)
                            view.restartView(null, newjc);
                    } catch (JuggleExceptionUser je) {
                        new ErrorDialog(this, je.getMessage());
                    }
                }
                break;
        }

    }

    // these should be in the same order as in the View menu
    protected static final int VIEW_NONE = 0;
    protected static final int VIEW_SIMPLE = 1;
    protected static final int VIEW_EDIT = 2;
    protected static final int VIEW_SELECTION = 3;
    protected static final int VIEW_JML = 4;

    protected void setViewMode(int mode) throws JuggleExceptionUser,
                        JuggleExceptionInternal {
        View newview = null;

        // items to carry over from old view to the new:
        JMLPattern pat = null;
        AnimationPrefs jc = null;
        Dimension animsize = null;
        boolean paused = false;

        if (view != null) {
            pat = view.getPattern();
            jc = view.getAnimationPrefs();
            animsize = view.getAnimationPanelSize();
            paused = view.getPaused();
        } else {
            // use default size
            AnimationPrefs tempjc = new AnimationPrefs();
            animsize = new Dimension(tempjc.width, tempjc.height);
        }

        switch (mode) {
            case VIEW_NONE:
                break;
            case VIEW_SIMPLE:
                newview = new SimpleView(animsize);
                break;
            case VIEW_EDIT:
                newview = new EditView(animsize);
                break;
            case VIEW_SELECTION:
                newview = new SelectionView(animsize);
                break;
            case VIEW_JML:
                newview = new JMLView(animsize);
                break;
        }
        if (newview == null)
            throw new JuggleExceptionInternal("setViewMode: problem creating view");

        newview.setParent(this);
        newview.setPaused(paused);
        newview.setOpaque(true);
        setContentPane(newview);

        if (view != null) {
            view.disposeView();
            view = newview;
            pack();
            view.restartView(pat, jc);
        } else
            // pack() and restartView() happen in constructor
            view = newview;
    }

    protected int getViewMode() {
        if (view == null)
            return VIEW_NONE;
        if (view instanceof SimpleView)
            return VIEW_SIMPLE;
        if (view instanceof EditView)
            return VIEW_EDIT;
        if (view instanceof SelectionView)
            return VIEW_SELECTION;
        if (view instanceof JMLView)
            return VIEW_JML;
        return VIEW_NONE;
    }

    @Override
    public synchronized void dispose() {
        super.dispose();
        if (view != null) {
            view.disposeView();
            view = null;
        }
    }
}
