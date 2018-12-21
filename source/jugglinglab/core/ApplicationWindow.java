// ApplicationWindow.java
//
// Copyright 2004 by Jack Boyce (jboyce@users.sourceforge.net) and others

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

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import org.xml.sax.*;
import java.util.*;
import java.text.MessageFormat;

import jugglinglab.jml.*;
import jugglinglab.notation.*;
import jugglinglab.util.*;



public class ApplicationWindow extends JFrame implements ActionListener, WindowListener {
    static ResourceBundle guistrings;
    static ResourceBundle errorstrings;
    static {
        guistrings = JLLocale.getBundle("GUIStrings");
        errorstrings = JLLocale.getBundle("ErrorStrings");
    }

	protected NotationGUI ng = null;
	protected boolean macos = false;

    public ApplicationWindow(String title) throws JuggleExceptionUser, JuggleExceptionInternal {
        super(title);
		ng = new NotationGUI(this);

		macos = PlatformSpecific.getPlatformSpecific().isMacOS();

        JMenuBar mb = new JMenuBar();
		JMenu filemenu = createFileMenu();
        mb.add(filemenu);
        JMenu notationmenu = ng.createNotationMenu();
		mb.add(notationmenu);
        JMenu helpmenu = ng.createHelpMenu(!macos);
		if (helpmenu != null)
			mb.add(helpmenu);
        setJMenuBar(mb);

        PlatformSpecific.getPlatformSpecific().registerParent(this);
        PlatformSpecific.getPlatformSpecific().setupPlatform();

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		ng.setDoubleBuffered(true);
		this.setBackground(new Color(0.9f, 0.9f, 0.9f));
        setContentPane(ng);
        ng.setNotation(Notation.NOTATION_SITESWAP);

		Locale loc = JLLocale.getLocale();
		this.applyComponentOrientation(ComponentOrientation.getOrientation(loc));

		notationmenu.getItem(Notation.NOTATION_SITESWAP-1).setSelected(true);
		pack();
		setResizable(false);
        setVisible(true);
		addWindowListener(this);
    }


    protected static final String[] fileItems = new String[]
    { "Open JML...", null, "Quit" };
    protected static final String[] fileCommands = new String[]
    { "open", null, "exit" };
    protected static final char[] fileShortcuts =
    { 'O', ' ', 'Q' };

	protected JMenu createFileMenu() {
        JMenu filemenu = new JMenu(guistrings.getString("File"));

        for (int i = 0; i < (macos ? fileItems.length-2 : fileItems.length); i++) {
            if (fileItems[i] == null)
                filemenu.addSeparator();
            else {
				JMenuItem fileitem = new JMenuItem(guistrings.getString(fileItems[i].replace(' ', '_')));
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


	public void actionPerformed(ActionEvent ae) {
        String command = ae.getActionCommand();

        try {
			if (command.equals("open"))
                doMenuCommand(FILE_OPEN);
            else if (command.equals("exit"))
                doMenuCommand(FILE_EXIT);
        } catch (Exception e) {
            ErrorDialog.handleException(e);
        }
    }


    public static final int FILE_NONE = 0;
    public static final int FILE_OPEN = 1;
    public static final int	FILE_EXIT = 2;


    public void doMenuCommand(int action) throws JuggleExceptionInternal {
        switch (action) {
            case FILE_NONE:
                break;

            case FILE_OPEN:
				javax.swing.filechooser.FileFilter filter = new javax.swing.filechooser.FileFilter() {
					public boolean accept(File f) {
						StringTokenizer st = new StringTokenizer(f.getName(), ".");
						String ext = "";
						while (st.hasMoreTokens())
							ext = st.nextToken();
						return (ext.equals("jml") || f.isDirectory());
					}

					public String getDescription() {
						return "JML Files";
					}
				};

                try {
                    if (PlatformSpecific.getPlatformSpecific().showOpenDialog(this, filter) == JFileChooser.APPROVE_OPTION) {
                        if (PlatformSpecific.getPlatformSpecific().getSelectedFile() != null)
                            showJMLWindow(PlatformSpecific.getPlatformSpecific().getSelectedFile());
                    }
                } catch (JuggleExceptionUser je) {
                    new ErrorDialog(this, je.getMessage());
                }
                break;

            case FILE_EXIT:
                System.exit(0);
                break;
        }

    }


    public void showJMLWindow(File jmlf) throws JuggleExceptionUser, JuggleExceptionInternal {
        JFrame frame = null;
        PatternListWindow pw = null;

        try {
            try {
                JMLParser parser = new JMLParser();
                parser.parse(new FileReader(jmlf));

                switch (parser.getFileType()) {
                    case JMLParser.JML_PATTERN:
                    {
                        JMLNode root = parser.getTree();
                        JMLPattern pat = new JMLPattern(root);
                        frame = new PatternWindow(pat.getTitle(), pat, new AnimatorPrefs());
                        break;
                    }
                    case JMLParser.JML_LIST:
                    {
                        JMLNode root = parser.getTree();
                        pw = new PatternListWindow(root);
                        PatternList pl = pw.getPatternList();
                        break;
                    }
                    default:
                    {
                        throw new JuggleExceptionUser(errorstrings.getString("Error_invalid_JML"));
                    }
                }
            } catch (FileNotFoundException fnfe) {
                throw new JuggleExceptionUser(errorstrings.getString("Error_file_not_found")+": "+fnfe.getMessage());
            } catch (IOException ioe) {
                throw new JuggleExceptionUser(errorstrings.getString("Error_IO")+": "+ioe.getMessage());
            } catch (SAXParseException spe) {
				String template = errorstrings.getString("Error_parsing");
				Object[] arguments = { new Integer(spe.getLineNumber()) };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            } catch (SAXException se) {
                throw new JuggleExceptionUser(se.getMessage());
            }
        } catch (JuggleExceptionUser jeu) {
            if (frame != null) frame.dispose();
            if (pw != null) pw.dispose();
            throw jeu;
        } catch (JuggleExceptionInternal jei) {
            if (frame != null) frame.dispose();
            if (pw != null) pw.dispose();
            throw jei;
        }
    }


	public NotationGUI getNotationGUI() { return ng; }

	public void windowOpened(WindowEvent e) { }
	public void windowClosing(WindowEvent e) {
		try {
			doMenuCommand(FILE_EXIT);
        } catch (Exception ex) {
            System.exit(0);
        }
	}
	public void windowClosed(WindowEvent e) { }
	public void windowIconified(WindowEvent e) { }
	public void windowDeiconified(WindowEvent e) { }
	public void windowActivated(WindowEvent e) { }
	public void windowDeactivated(WindowEvent e) { }
}
