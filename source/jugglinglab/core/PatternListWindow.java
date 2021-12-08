// PatternListWindow.java
//
// Copyright 2020 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.core;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;

import jugglinglab.jml.*;
import jugglinglab.notation.*;
import jugglinglab.util.*;


public class PatternListWindow extends JFrame implements ActionListener {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    String title = null;
    PatternListPanel pl = null;
    protected JMenuItem[] fileitems = null;


    public PatternListWindow(String ti) {
        super(ti);
        title = ti;
        makeWindow();
        pl.setTitle(ti);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    }

    public PatternListWindow(String ti, Thread th) {
        this(ti);
        final Thread generator = th;

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    if (generator != null)
                        generator.interrupt();
                } catch (Exception ex) {
                }
            }
        });
    }

    public PatternListWindow(JMLNode root) throws JuggleExceptionUser {
        super();
        makeWindow();
        pl.readJML(root);
        if (pl.getTitle() != null)
            title = pl.getTitle();
        else
            title = guistrings.getString("Patterns");

        setTitle(title);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    }

    protected void makeWindow() {
        pl = new PatternListPanel(null);

        pl.setDoubleBuffered(true);
        setBackground(Color.white);
        setContentPane(pl);

        setSize(300,450);
        createMenuBar();

        Locale loc = JLLocale.getLocale();
        applyComponentOrientation(ComponentOrientation.getOrientation(loc));
        // list contents are always left-to-right -- DISABLE FOR NOW
        // this.getContentPane().applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        setLocation(150, 200);
        setVisible(true);
    }

    public PatternListPanel getPatternList() { return pl; }

    protected String[] fileItems = new String[]     { "Close", null, "Save JML As...", "Save Text As..." };
    protected String[] fileCommands = new String[]  { "close", null, "saveas", "savetext" };
    protected char[] fileShortcuts =            { 'W', ' ', 'S', 'T' };

    protected void createMenuBar() {
        JMenuBar mb = new JMenuBar();

        JMenu filemenu = new JMenu(guistrings.getString("File"));
        this.fileitems = new JMenuItem[fileItems.length];
        for (int i = 0; i < fileItems.length; i++) {
            if (fileItems[i] == null)
                filemenu.addSeparator();
            else {
                fileitems[i] = new JMenuItem(guistrings.getString(fileItems[i].replace(' ', '_')));
                if (fileShortcuts[i] != ' ')
                    fileitems[i].setAccelerator(KeyStroke.getKeyStroke(fileShortcuts[i],
                                                                       Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
                fileitems[i].setActionCommand(fileCommands[i]);
                fileitems[i].addActionListener(this);
                filemenu.add(fileitems[i]);
            }
        }
        mb.add(filemenu);

        setJMenuBar(mb);
    }

    public static final int FILE_NONE = 0;
    public static final int FILE_CLOSE = 1;
    public static final int FILE_SAVE = 2;
    public static final int FILE_SAVETEXT = 3;

    // Implements ActionListener to wait for MenuItem events
    @Override
    public void actionPerformed(ActionEvent ae) {
        String command = ae.getActionCommand();

        try {
            if (command.equals("close"))
                doFileMenuCommand(FILE_CLOSE);
            else if (command.equals("saveas"))
                doFileMenuCommand(FILE_SAVE);
            else if (command.equals("savetext"))
                doFileMenuCommand(FILE_SAVETEXT);
        } catch (Exception e) {
            ErrorDialog.handleFatalException(e);
        }
    }

    public void doFileMenuCommand(int action) throws JuggleExceptionInternal {
        switch (action) {

            case FILE_NONE:
                break;

            case FILE_CLOSE:
                dispose();
                break;

            case FILE_SAVE:
                try {
                    int option = JLFunc.showSaveDialog(this);
                    if (option == JFileChooser.APPROVE_OPTION) {
                        if (JLFunc.getSelectedFile() != null) {
                            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                            FileWriter fw = new FileWriter(JLFunc.getSelectedFile());
                            pl.writeJML(fw);
                            fw.close();
                        }
                    }
                } catch (FileNotFoundException fnfe) {
                    throw new JuggleExceptionInternal("File not found on save: " + fnfe.getMessage());
                } catch (IOException ioe) {
                    throw new JuggleExceptionInternal("IOException on save: " + ioe.getMessage());
                } finally {
                    setCursor(Cursor.getDefaultCursor());
                }
                break;

            case FILE_SAVETEXT:
                try {
                    int option = JLFunc.showSaveDialog(this);
                    if (option == JFileChooser.APPROVE_OPTION) {
                        if (JLFunc.getSelectedFile() != null) {
                            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                            FileWriter fw = new FileWriter(JLFunc.getSelectedFile());
                            pl.writeText(fw);
                            fw.close();
                        }
                    }
                } catch (FileNotFoundException fnfe) {
                    throw new JuggleExceptionInternal("File not found on save: " + fnfe.getMessage());
                } catch (IOException ioe) {
                    throw new JuggleExceptionInternal("IOException on save: " + ioe.getMessage());
                } finally {
                    setCursor(Cursor.getDefaultCursor());
                }
                break;
        }
    }

    public void addPattern(String display, String animprefs, String notation, String anim, JMLNode pattern) {
        pl.addPattern(display, animprefs, notation, anim, pattern);
    }
}
