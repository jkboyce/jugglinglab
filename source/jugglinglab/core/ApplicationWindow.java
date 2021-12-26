// ApplicationWindow.java
//
// Copyright 2002-2021 Jack Boyce and the Juggling Lab contributors

package jugglinglab.core;

import java.awt.*;
import java.awt.desktop.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.text.MessageFormat;
import java.util.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.xml.sax.*;

import jugglinglab.jml.*;
import jugglinglab.notation.*;
import jugglinglab.util.*;


// This is the main application window visible when Juggling Lab is launched
// as an application. The contents of the window are split into a different
// class (ApplicationPanel).
//
// Currently only a single notation (siteswap) is included with Juggling Lab
// so the notation menu is suppressed.

public class ApplicationWindow extends JFrame implements ActionListener {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    protected JMenu windowmenu;


    public ApplicationWindow(String title) throws JuggleExceptionUser,
                                        JuggleExceptionInternal {
        super(title);
        createMenus();

        ApplicationPanel ap = new ApplicationPanel(this);
        ap.setDoubleBuffered(true);
        setBackground(new Color(0.9f, 0.9f, 0.9f));
        setContentPane(ap);  // entire contents of window

        // does the real work of adding controls etc.
        ap.setNotation(Pattern.NOTATION_SITESWAP);

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    doMenuCommand(FILE_EXIT);
                } catch (Exception ex) {
                    System.exit(0);
                }
            }
        });

        Locale loc = JLLocale.getLocale();
        applyComponentOrientation(ComponentOrientation.getOrientation(loc));

        Point center = GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint();
        int locx = Math.max(0, center.x - Constants.RESERVED_WIDTH_PIXELS / 2);
        pack();
        setResizable(false);
        setLocation(locx, 50);
        setVisible(true);

        // There are two ways we can handle requests from the OS to open files:
        // with a OpenFilesHandler (macOS) and with our own OpenFilesServer
        // (Windows)

        if (!registerOpenFilesHandler())
            new OpenFilesServer();

        // launch a background thread to check for updates online
        new UpdateChecker();

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ApplicationWindow.updateWindowMenus();
            }
        });
    }

    // Try to register a handler for when the OS wants us to open a file type
    // associated with Juggling Lab (i.e., a .jml file)
    //
    // Returns true if successfully installed, false otherwise
    static protected boolean registerOpenFilesHandler() {
        if (!Desktop.isDesktopSupported())
            return false;

        if (!Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_FILE))
            return false;

        Desktop.getDesktop().setOpenFileHandler(new OpenFilesHandler() {
            @Override
            public void openFiles(OpenFilesEvent ofe) {
                if (Desktop.isDesktopSupported()
                        && Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND))
                    Desktop.getDesktop().requestForeground(true);

                try {
                    for (File file : ofe.getFiles()) {
                        try {
                            openJMLFile(file);
                        } catch (JuggleExceptionUser jeu) {
                            String template = errorstrings.getString("Error_reading_file");
                            Object[] arguments = { file.getName() };
                            String msg = MessageFormat.format(template, arguments) +
                                         ":\n" + jeu.getMessage();
                            new ErrorDialog(null, msg);
                        }
                    }
                } catch (JuggleExceptionInternal jei) {
                    ErrorDialog.handleFatalException(jei);
                }
            }
        });
        return true;
    }

    protected void createMenus() {
        JMenuBar mb = new JMenuBar();
        mb.add(createFileMenu());

        if (Pattern.builtinNotations.length > 1) {
            JMenu notationmenu = createNotationMenu();
            mb.add(notationmenu);
            // make siteswap notation the default selection
            notationmenu.getItem(Pattern.NOTATION_SITESWAP - 1).setSelected(true);
        }

        windowmenu = new JMenu(guistrings.getString("Window"));
        mb.add(windowmenu);

        mb.add(createHelpMenu());
        setJMenuBar(mb);
    }

    protected static final String[] fileItems = new String[]
        {
            "New Pattern",
            "New Pattern List",
            "Open JML...",
            null,
            "Quit",
        };
    protected static final String[] fileCommands = new String[]
        {
            "newpat",
            "newpl",
            "open",
            null,
            "exit",
        };
    protected static final char[] fileShortcuts =
        {
            'N',
            'L',
            'O',
            ' ',
            'Q',
        };

    protected JMenu createFileMenu() {
        boolean quit_handler = Desktop.isDesktopSupported() &&
                Desktop.getDesktop().isSupported(Desktop.Action.APP_QUIT_HANDLER);

        if (quit_handler) {
            Desktop.getDesktop().setQuitHandler(new QuitHandler() {
                @Override
                public void handleQuitRequestWith(QuitEvent e, QuitResponse response) {
                    try {
                        doMenuCommand(FILE_EXIT);
                    } catch (JuggleExceptionInternal jei) {
                        response.performQuit();
                    }
                }
            });
        }

        JMenu filemenu = new JMenu(guistrings.getString("File"));

        for (int i = 0; i < (quit_handler ? fileItems.length - 2 : fileItems.length); i++) {
            if (fileItems[i] == null)
                filemenu.addSeparator();
            else {
                JMenuItem fileitem = new JMenuItem(guistrings.getString(fileItems[i].replace(' ', '_')));
                if (fileShortcuts[i] != ' ')
                    fileitem.setAccelerator(KeyStroke.getKeyStroke(fileShortcuts[i],
                            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
                fileitem.setActionCommand(fileCommands[i]);
                fileitem.addActionListener(this);
                filemenu.add(fileitem);
            }
        }
        return filemenu;
    }

    protected JMenu createNotationMenu() {
        JMenu notationmenu = new JMenu(guistrings.getString("Notation"));
        ButtonGroup buttonGroup = new ButtonGroup();

        for (int i = 0; i < Pattern.builtinNotations.length; i++) {
            JRadioButtonMenuItem notationitem = new JRadioButtonMenuItem(Pattern.builtinNotations[i]);
            notationitem.setActionCommand("notation"+(i+1));
            notationitem.addActionListener(this);
            notationmenu.add(notationitem);
            buttonGroup.add(notationitem);
        }

        return notationmenu;
    }

    public JMenu getWindowMenu() {
        return windowmenu;
    }

    // Updates the "Window" menu attached to most of our JFrames.
    //
    // Should call this whenever a window is added or removed.
    public static void updateWindowMenus() {
        ArrayList<ApplicationWindow> apps = new ArrayList<ApplicationWindow>();
        ArrayList<PatternListWindow> pls = new ArrayList<PatternListWindow>();
        ArrayList<PatternWindow> anims = new ArrayList<PatternWindow>();
        ArrayList<JMenu> menus = new ArrayList<JMenu>();

        for (Frame fr : Frame.getFrames()) {
            if (!fr.isVisible())
                continue;

            if (fr instanceof ApplicationWindow) {
                apps.add((ApplicationWindow)fr);
                menus.add(((ApplicationWindow)fr).getWindowMenu());
            } else if (fr instanceof PatternListWindow) {
                pls.add((PatternListWindow)fr);
                menus.add(((PatternListWindow)fr).getWindowMenu());
            } else if (fr instanceof PatternWindow) {
                anims.add((PatternWindow)fr);
                menus.add(((PatternWindow)fr).getWindowMenu());
            }
        }

        ActionListener al = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                String command = ae.getActionCommand();

                if (command.equals("front")) {
                    boolean foregroundSupported = Desktop.isDesktopSupported() &&
                                    Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND);

                    if (foregroundSupported) {
                        Desktop.getDesktop().requestForeground(true);
                        return;
                    } else {
                        for (Frame fr : apps)
                            fr.toFront();
                        for (Frame fr : pls)
                            fr.toFront();
                        for (Frame fr : anims)
                            fr.toFront();
                        return;
                    }
                } else {
                    int windownum = Integer.parseInt(command);

                    if (windownum < apps.size()) {
                        apps.get(windownum).toFront();
                        return;
                    }
                    windownum -= apps.size();
                    if (windownum < pls.size()) {
                        pls.get(windownum).toFront();
                        return;
                    }
                    windownum -= pls.size();
                    if (windownum < anims.size()) {
                        anims.get(windownum).toFront();
                        return;
                    }

                    ErrorDialog.handleFatalException(
                            new JuggleExceptionInternal("Window number out of range: " + command));
                }
            }
        };

        for (JMenu wm : menus) {
            wm.removeAll();

            JMenuItem alltofront = new JMenuItem(guistrings.getString("Bring_All_To_Front"));
            alltofront.setActionCommand("front");
            alltofront.addActionListener(al);
            wm.add(alltofront);

            wm.addSeparator();

            int windownum = 0;
            for (ApplicationWindow aw : apps) {
                JMenuItem awitem = new JMenuItem(aw.getTitle());
                awitem.setActionCommand(String.valueOf(windownum++));
                awitem.addActionListener(al);
                wm.add(awitem);
            }
            //if (apps.size() > 0)
            //    wm.addSeparator();

            for (PatternListWindow pl : pls) {
                JMenuItem plitem = new JMenuItem(pl.getTitle());
                plitem.setActionCommand(String.valueOf(windownum++));
                plitem.addActionListener(al);
                wm.add(plitem);
            }
            //if (pls.size() > 0)
            //    wm.addSeparator();

            for (PatternWindow anim : anims) {
                JMenuItem animitem = new JMenuItem(anim.getTitle());
                animitem.setActionCommand(String.valueOf(windownum++));
                animitem.addActionListener(al);
                wm.add(animitem);
            }
        }
    }

    protected static final String[] helpItems = new String[]
        {
            "About Juggling Lab",
            "Juggling Lab Online Help",
        };
    protected static final String[] helpCommands = new String[]
        {
            "about",
            "online",
        };

    protected JMenu createHelpMenu() {
        // skip the about menu item if About handler was already installed
        // in JugglingLab.java
        boolean include_about = !Desktop.isDesktopSupported() ||
                !Desktop.getDesktop().isSupported(Desktop.Action.APP_ABOUT);

        JMenu helpmenu = new JMenu(guistrings.getString("Help"));

        for (int i = (include_about ? 0 : 1); i < helpItems.length; i++) {
            if (helpItems[i] == null)
                helpmenu.addSeparator();
            else {
                JMenuItem helpitem = new JMenuItem(guistrings.getString(helpItems[i].replace(' ', '_')));
                helpitem.setActionCommand(helpCommands[i]);
                helpitem.addActionListener(this);
                helpmenu.add(helpitem);
            }
        }
        return helpmenu;
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        String command = ae.getActionCommand();

        try {
            if (command.equals("newpat"))
                doMenuCommand(FILE_NEWPAT);
            else if (command.equals("newpl"))
                doMenuCommand(FILE_NEWPL);
            else if (command.equals("open"))
                doMenuCommand(FILE_OPEN);
            else if (command.equals("exit"))
                doMenuCommand(FILE_EXIT);
            else if (command.equals("about"))
                doMenuCommand(HELP_ABOUT);
            else if (command.equals("online"))
                doMenuCommand(HELP_ONLINE);
        } catch (JuggleExceptionInternal jei) {
            ErrorDialog.handleFatalException(jei);
        }
    }

    protected static final int FILE_NONE = 0;
    protected static final int FILE_NEWPAT = 1;
    protected static final int FILE_NEWPL = 2;
    protected static final int FILE_OPEN = 3;
    protected static final int FILE_EXIT = 4;
    protected static final int HELP_ABOUT = 5;
    protected static final int HELP_ONLINE = 6;

    protected void doMenuCommand(int action) throws JuggleExceptionInternal {
        switch (action) {
            case FILE_NONE:
                break;

            case FILE_NEWPAT:
                ApplicationWindow.newPattern();
                break;

            case FILE_NEWPL:
                new PatternListWindow("Pattern list");
                break;

            case FILE_OPEN:
                ApplicationWindow.openJMLFile();
                break;

            case FILE_EXIT:
                boolean noOpenFilesHandler = (!Desktop.isDesktopSupported() ||
                    !Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_FILE));

                if (noOpenFilesHandler) {
                    if (Constants.DEBUG_OPEN_SERVER)
                        System.out.println("interrupting server");
                    OpenFilesServer.cleanup();
                    try {
                        Thread.sleep(100);
                    } catch (Exception e) {}
                }
                System.exit(0);
                break;

            case HELP_ABOUT:
                showAboutBox();
                break;

            case HELP_ONLINE:
                boolean browse_supported = (Desktop.isDesktopSupported() &&
                                Desktop.getDesktop().isSupported(Desktop.Action.BROWSE));
                boolean browse_problem = false;

                if (browse_supported) {
                    try {
                        Desktop.getDesktop().browse(new URI(Constants.help_URL));
                    } catch (Exception e) {
                        browse_problem = true;
                    }
                }

                if (!browse_supported || browse_problem) {
                    new LabelDialog(this, "Help", "Find online help at " +
                                    Constants.help_URL);
                }
                break;
        }

    }

    // Implementation of the File menu "New Pattern" command
    public static void newPattern() throws JuggleExceptionInternal {
        try {
            JMLPattern pat = JMLPattern.fromBasePattern("Siteswap", "pattern=3");
            ParameterList pl = new ParameterList("view=pattern_editor");
            AnimationPrefs ap = new AnimationPrefs().fromParameters(pl);
            new PatternWindow("3", pat, ap);
        } catch (JuggleExceptionUser jeu) {
            throw new JuggleExceptionInternal(jeu.getMessage());
        }
    }

    // Open a JML file with a file chooser
    public static void openJMLFile() throws JuggleExceptionInternal {
        JLFunc.jfc().setFileFilter(new FileNameExtensionFilter("JML file", "jml"));
        if (JLFunc.jfc().showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
            return;

        File file = JLFunc.jfc().getSelectedFile();
        if (file != null) {
            try {
                openJMLFile(file);
            } catch (JuggleExceptionUser jeu) {
                String template = errorstrings.getString("Error_reading_file");
                Object[] arguments = { file.getName() };
                String msg = MessageFormat.format(template, arguments) +
                             ":\n" + jeu.getMessage();
                new ErrorDialog(null, msg);
            }
        }
    }

    public static void openJMLFile(File jmlf) throws JuggleExceptionUser, JuggleExceptionInternal {
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
                        pat.layoutPattern();
                        if (!PatternWindow.bringToFront(pat.getHashCode()))
                            frame = new PatternWindow(pat.getTitle(), pat, new AnimationPrefs());
                        break;
                    }
                    case JMLParser.JML_LIST:
                    {
                        JMLNode root = parser.getTree();
                        pw = new PatternListWindow(root);
                        PatternListPanel pl = pw.getPatternList();
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
                String template = errorstrings.getString("Error_JML_parsing");
                Object[] arguments = { Integer.valueOf(spe.getLineNumber()),
                                       spe.getMessage().length() > 0 ? (":\n" + spe.getMessage()) : "" };
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

    public static void showAboutBox() {
        final JFrame aboutBox = new JFrame(guistrings.getString("About_Juggling_Lab"));
        aboutBox.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel aboutPanel = new JPanel(new BorderLayout());
        aboutPanel.setOpaque(true);

        java.net.URL url = ApplicationWindow.class.getResource("/about.png");
        if (url != null) {
            ImageIcon aboutPicture = new ImageIcon(url, "A lab");
            if (aboutPicture != null) {
                JLabel aboutLabel = new JLabel(aboutPicture);
                aboutPanel.add(aboutLabel, BorderLayout.LINE_START);
            }
        }

        JPanel textPanel = new JPanel();
        aboutPanel.add(textPanel, BorderLayout.LINE_END);
        GridBagLayout gb = new GridBagLayout();
        textPanel.setLayout(gb);

        JLabel abouttext1 = new JLabel("Juggling Lab");
        abouttext1.setFont(new Font("SansSerif", Font.BOLD, 18));
        textPanel.add(abouttext1);
        gb.setConstraints(abouttext1, JLFunc.constraints(GridBagConstraints.LINE_START,0,0,
                                                       new Insets(15,15,0,15)));

        String template = guistrings.getString("Version");
        Object[] arguments = { Constants.version };
        JLabel abouttext5 = new JLabel(MessageFormat.format(template, arguments));
        abouttext5.setFont(new Font("SansSerif", Font.PLAIN, 16));
        textPanel.add(abouttext5);
        gb.setConstraints(abouttext5, JLFunc.constraints(GridBagConstraints.LINE_START,0,1,
                                                       new Insets(0,15,0,15)));

        String template2 = guistrings.getString("Copyright_message");
        Object[] arguments2 = { Constants.year };
        JLabel abouttext6 = new JLabel(MessageFormat.format(template2, arguments2));
        abouttext6.setFont(new Font("SansSerif", Font.PLAIN, 14));
        textPanel.add(abouttext6);
        gb.setConstraints(abouttext6, JLFunc.constraints(GridBagConstraints.LINE_START,0,2,
                                                       new Insets(15,15,15,15)));

        JLabel abouttext3 = new JLabel(guistrings.getString("GPL_message"));
        abouttext3.setFont(new Font("SansSerif", Font.PLAIN, 12));
        textPanel.add(abouttext3);
        gb.setConstraints(abouttext3, JLFunc.constraints(GridBagConstraints.LINE_START,0,3,
                                                       new Insets(0,15,15,15)));

        String javaversion = System.getProperty("java.version");
        String javavmname = System.getProperty("java.vm.name");
        String javavmversion = System.getProperty("java.vm.version");

        int gridrow = 4;
        if (javaversion != null) {
            JLabel java1 = new JLabel("Java version " + javaversion);
            java1.setFont(new Font("SansSerif", Font.PLAIN, 12));
            textPanel.add(java1);
            gb.setConstraints(java1, JLFunc.constraints(GridBagConstraints.LINE_START,0,gridrow++,
                                                       new Insets(0,15,0,15)));
        }
        if (javavmname != null && javavmversion != null) {
            JLabel java2 = new JLabel(javavmname + " (" + javavmversion +")");
            java2.setFont(new Font("SansSerif", Font.PLAIN, 12));
            textPanel.add(java2);
            gb.setConstraints(java2, JLFunc.constraints(GridBagConstraints.LINE_START,0,gridrow++,
                                                       new Insets(0,15,0,15)));
        }

        JButton okbutton = new JButton(guistrings.getString("OK"));
        textPanel.add(okbutton);
        gb.setConstraints(okbutton, JLFunc.constraints(GridBagConstraints.LINE_END,0,gridrow++,
                                                     new Insets(15,15,15,15)));
        okbutton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                aboutBox.setVisible(false);
                aboutBox.dispose();
            }
        });

        aboutBox.setContentPane(aboutPanel);

        Locale loc = JLLocale.getLocale();
        aboutBox.applyComponentOrientation(ComponentOrientation.getOrientation(loc));

        aboutBox.pack();
        aboutBox.setResizable(false);
        aboutBox.setLocationRelativeTo(null);    // center frame on screen
        aboutBox.setVisible(true);
    }
}
