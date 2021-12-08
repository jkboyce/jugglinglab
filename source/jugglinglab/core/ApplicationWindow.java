// ApplicationWindow.java
//
// Copyright 2020 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.core;

import java.awt.*;
import java.awt.desktop.AboutEvent;
import java.awt.desktop.AboutHandler;
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

        pack();
        setResizable(false);
        setLocation(100, 50);
        setVisible(true);

        // launch a background thread to check for updates online
        new UpdateChecker();
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

        JMenu helpmenu = createHelpMenu();
        if (helpmenu != null)
            mb.add(helpmenu);

        setJMenuBar(mb);
    }

    protected static final String[] fileItems = new String[]
        { "Open JML...", null, "Quit" };
    protected static final String[] fileCommands = new String[]
        { "open", null, "exit" };
    protected static final char[] fileShortcuts =
        { 'O', ' ', 'Q' };

    protected JMenu createFileMenu() {
        // When we move to Java 9+ we can use Desktop.setQuitHandler() here.
        boolean include_quit = true;

        JMenu filemenu = new JMenu(guistrings.getString("File"));

        for (int i = 0; i < (include_quit ? fileItems.length : fileItems.length - 2); i++) {
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

    protected static final String[] helpItems = new String[]
        { "About Juggling Lab", "Juggling Lab Online Help" };
    protected static final String[] helpCommands = new String[]
        { "about", "online" };

    protected JMenu createHelpMenu() {
        boolean include_about = true;

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().setAboutHandler(new AboutHandler() {
                @Override
                public void handleAbout(AboutEvent e) {
                    try {
                        doMenuCommand(HELP_ABOUT);
                    } catch (JuggleExceptionInternal jei) {
                        ErrorDialog.handleFatalException(jei);
                    }
                }
            });

            include_about = false;  // don't need to include in menu
        }

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
            if (command.equals("open"))
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
    protected static final int FILE_OPEN = 1;
    protected static final int FILE_EXIT = 2;
    protected static final int HELP_ABOUT = 3;
    protected static final int HELP_ONLINE = 4;

    protected void doMenuCommand(int action) throws JuggleExceptionInternal {
        switch (action) {
            case FILE_NONE:
                break;

            case FILE_OPEN:
                try {
                    JLFunc.jfc().setFileFilter(new FileNameExtensionFilter("JML file", "jml"));
                    if (JLFunc.jfc().showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                        File f = JLFunc.jfc().getSelectedFile();
                        if (f != null)
                            showJMLWindow(f);
                    }
                } catch (JuggleExceptionUser je) {
                    new ErrorDialog(this, je.getMessage());
                }
                break;

            case FILE_EXIT:
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

    protected static void showJMLWindow(File jmlf) throws JuggleExceptionUser, JuggleExceptionInternal {
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
                String template = errorstrings.getString("Error_parsing");
                Object[] arguments = { Integer.valueOf(spe.getLineNumber()) };
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

    protected static void showAboutBox() {
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
        abouttext3.setFont(new Font("SansSerif", Font.PLAIN, 14));
        textPanel.add(abouttext3);
        gb.setConstraints(abouttext3, JLFunc.constraints(GridBagConstraints.LINE_START,0,3,
                                                       new Insets(0,15,0,15)));

        JButton okbutton = new JButton(guistrings.getString("OK"));
        textPanel.add(okbutton);
        gb.setConstraints(okbutton, JLFunc.constraints(GridBagConstraints.LINE_END,0,4,
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
