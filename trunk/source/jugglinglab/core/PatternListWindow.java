// PatternListWindow.java
//
// Copyright 2002 by Jack Boyce (jboyce@users.sourceforge.net) and others

/*
    This file is part of Juggling Lab.

    Juggling Lab is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    Juggling Lab is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Juggling Lab; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package jugglinglab.core;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

import jugglinglab.jml.*;
import jugglinglab.notation.*;
import jugglinglab.util.*;


public class PatternListWindow extends JFrame implements ActionListener {
    static ResourceBundle guistrings;
    // static ResourceBundle errorstrings;
    static {
        guistrings = JLLocale.getBundle("GUIStrings");
        // errorstrings = JLLocale.getBundle("ErrorStrings");
    }

    String title = null;
    PatternList pl = null;
    protected JMenuItem[] fileitems = null;


    public PatternListWindow(String title) {
        super(title);
        this.title = title;
        makeWindow();
        pl.setTitle(title);
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    }

    public PatternListWindow(JMLNode root) throws JuggleExceptionUser {
        super();
        makeWindow();
        pl.readJML(root);
        if (pl.getTitle() != null)
            title = pl.getTitle();
        else
            title = ResourceBundle.getBundle("JugglingLabStrings").getString("Patterns");

        setTitle(title);
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    }

    protected void makeWindow() {
        this.pl = new PatternList(null);

		pl.setDoubleBuffered(true);
		this.setBackground(Color.white);
		this.setContentPane(pl);
		
		this.setSize(300,450);
        createMenuBar();

		Locale loc = JLLocale.getLocale();
		this.applyComponentOrientation(ComponentOrientation.getOrientation(loc));
		// list contents are always left-to-right -- DISABLE FOR NOW
		// this.getContentPane().applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);

		this.setVisible(true);
    }


    public PatternList getPatternList() {
        return pl;
    }

    protected String[] fileItems = new String[]		{ "Close", null, "Save JML As...", "Save Text As..." };
    protected String[] fileCommands = new String[]	{ "close", null, "saveas", "savetext" };
    protected char[] fileShortcuts =			{ 'W', ' ', 'S', 'T' };

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

    public static final int	FILE_NONE = 0;
    public static final int	FILE_CLOSE = 1;
    public static final int	FILE_SAVE = 2;
    public static final int	FILE_SAVETEXT = 3;

    // Implements ActionListener to wait for MenuItem events
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
            jugglinglab.util.ErrorDialog.handleException(e);
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
                    int option = PlatformSpecific.getPlatformSpecific().showSaveDialog(this);
                    if (option == JFileChooser.APPROVE_OPTION) {
                        if (PlatformSpecific.getPlatformSpecific().getSelectedFile() != null) {
                            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                            FileWriter fw = new FileWriter(PlatformSpecific.getPlatformSpecific().getSelectedFile());
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
                    int option = PlatformSpecific.getPlatformSpecific().showSaveDialog(this);
                    if (option == JFileChooser.APPROVE_OPTION) {
                        if (PlatformSpecific.getPlatformSpecific().getSelectedFile() != null) {
                            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                            FileWriter fw = new FileWriter(PlatformSpecific.getPlatformSpecific().getSelectedFile());
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
