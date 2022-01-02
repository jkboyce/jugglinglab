// PatternListWindow.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.core;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import jugglinglab.jml.JMLNode;
import jugglinglab.util.*;


public class PatternListWindow extends JFrame implements ActionListener {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    // used for tiling the windows on the screen as they're created
    static protected final int NUM_TILES = 8;
    static protected final Point TILE_START = new Point(0, 620);
    static protected final Point TILE_OFFSET = new Point(25, 25);
    static protected Point[] tile_locations;
    static protected int next_tile_num;

    protected PatternListPanel pl;
    protected JMenu windowmenu;


    public PatternListWindow(String title) {
        super();
        createMenus();
        createContents();
        pl.setTitle(title);
        setTitle(title);

        setLocation(getNextScreenLocation());
        setVisible(true);

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    doMenuCommand(MenuCommand.FILE_CLOSE);
                } catch (JuggleException je) {
                    ErrorDialog.handleFatalException(je);
                }
            }
        });

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ApplicationWindow.updateWindowMenus();
            }
        });
    }

    // JML loaded from a file
    public PatternListWindow(JMLNode root) throws JuggleExceptionUser {
        this("");

        if (root != null) {
            pl.readJML(root);
            setTitle(pl.getTitle());
        }
    }

    // Target of a (running) pattern generator
    public PatternListWindow(String title, Thread gen) {
        this(title);

        if (gen != null) {
            final Thread generator = gen;

            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    try {
                        generator.interrupt();
                    } catch (Exception ex) {
                    }
                }
            });
        }
    }

    //-------------------------------------------------------------------------
    // Methods to create and manage window contents
    //-------------------------------------------------------------------------

    protected void createContents() {
        pl = new PatternListPanel(this);
        pl.setDoubleBuffered(true);
        setContentPane(pl);

        Locale loc = JLLocale.getLocale();
        applyComponentOrientation(ComponentOrientation.getOrientation(loc));
        // list contents are always left-to-right -- DISABLE FOR NOW
        // this.getContentPane().applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);

        setBackground(Color.white);
        setSize(300, 450);
    }

    public PatternListPanel getPatternListPanel() {
        return pl;
    }

    //-------------------------------------------------------------------------
    // Static methods
    //-------------------------------------------------------------------------

    // Return the location (screen pixels) of where the next animation window to
    // be created should go. This allows us to create a tiling effect.
    protected static Point getNextScreenLocation() {
        if (tile_locations == null) {
            tile_locations = new Point[NUM_TILES];

            Point center = GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint();
            int locx = Math.max(0, center.x - Constants.RESERVED_WIDTH_PIXELS / 2);

            for (int i = 0; i < NUM_TILES; ++i) {
                int loc_x = locx + TILE_START.x + i * TILE_OFFSET.x;
                int loc_y = TILE_START.y + i * TILE_OFFSET.y;
                tile_locations[i] = new Point(loc_x, loc_y);
            }

            next_tile_num = 0;
        }

        Point loc = tile_locations[next_tile_num];
        if (++next_tile_num == NUM_TILES)
            next_tile_num = 0;
        return loc;
    }

    //-------------------------------------------------------------------------
    // Menu creation and handlers
    //-------------------------------------------------------------------------

    protected void createMenus() {
        JMenuBar mb = new JMenuBar();
        mb.add(createFileMenu());
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
            "Save JML As...",
            "Save Text As...",
            null,
            "Close",
        };
    protected static final String[] fileCommands = new String[]
        {
            "newpat",
            "newpl",
            "open",
            "saveas",
            "savetext",
            null,
            "close",
        };
    protected static final char[] fileShortcuts =
        {
            'N',
            'L',
            'O',
            'S',
            'T',
            ' ',
            'W',
        };

    protected JMenu createFileMenu() {
        JMenu filemenu = new JMenu(guistrings.getString("File"));
        JMenuItem[] fileitems = new JMenuItem[fileItems.length];
        for (int i = 0; i < fileItems.length; i++) {
            if (fileItems[i] == null)
                filemenu.addSeparator();
            else {
                JMenuItem fileitem = new JMenuItem(
                        guistrings.getString(fileItems[i].replace(' ', '_')));

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

    public JMenu getWindowMenu() {
        return windowmenu;
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

        String menuname = guistrings.getString("Help");
        // Menus titled "Help" are handled differently by macOS; only want to
        // have one of them across the entire app.
        if (jugglinglab.JugglingLab.isMacOS)
            menuname += ' ';
        JMenu helpmenu = new JMenu(menuname);

        for (int i = (include_about ? 0 : 1); i < helpItems.length; i++) {
            if (helpItems[i] == null)
                helpmenu.addSeparator();
            else {
                JMenuItem helpitem = new JMenuItem(guistrings.getString(
                                            helpItems[i].replace(' ', '_')));
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
                doMenuCommand(MenuCommand.FILE_NEWPAT);
            else if (command.equals("newpl"))
                doMenuCommand(MenuCommand.FILE_NEWPL);
            else if (command.equals("open"))
                doMenuCommand(MenuCommand.FILE_OPEN);
            else if (command.equals("close"))
                doMenuCommand(MenuCommand.FILE_CLOSE);
            else if (command.equals("saveas"))
                doMenuCommand(MenuCommand.FILE_SAVE);
            else if (command.equals("savetext"))
                doMenuCommand(MenuCommand.FILE_SAVETEXT);
            else if (command.equals("about"))
                doMenuCommand(MenuCommand.HELP_ABOUT);
            else if (command.equals("online"))
                doMenuCommand(MenuCommand.HELP_ONLINE);

        } catch (JuggleExceptionInternal jei) {
            ErrorDialog.handleFatalException(jei);
        }
    }

    protected static enum MenuCommand {
        FILE_NONE,
        FILE_NEWPAT,
        FILE_NEWPL,
        FILE_OPEN,
        FILE_CLOSE,
        FILE_SAVE,
        FILE_SAVETEXT,
        HELP_ABOUT,
        HELP_ONLINE,
    }

    protected void doMenuCommand(MenuCommand action) throws JuggleExceptionInternal {
        switch (action) {
            case FILE_NONE:
                break;

            case FILE_NEWPAT:
                ApplicationWindow.newPattern();
                break;

            case FILE_NEWPL:
                (new PatternListWindow("")).setTitle(null);
                break;

            case FILE_OPEN:
                ApplicationWindow.openJMLFile();
                break;

            case FILE_CLOSE:
                dispose();
                break;

            case FILE_SAVE:
                try {
                    // create default filename
                    JLFunc.jfc().setSelectedFile(new File(getTitle() + ".jml"));
                    JLFunc.jfc().setFileFilter(new FileNameExtensionFilter("JML file", "jml"));

                    if (JLFunc.jfc().showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
                        break;

                    File f = JLFunc.jfc().getSelectedFile();
                    if (f == null)
                        break;
                    if (!f.getAbsolutePath().endsWith(".jml"))
                        f = new File(f.getAbsolutePath() + ".jml");

                    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    FileWriter fw = new FileWriter(f);
                    pl.writeJML(fw);
                    fw.close();
                } catch (FileNotFoundException fnfe) {
                    throw new JuggleExceptionInternal("File not found on save: " +
                            fnfe.getMessage());
                } catch (IOException ioe) {
                    throw new JuggleExceptionInternal("IOException on save: " +
                            ioe.getMessage());
                } finally {
                    setCursor(Cursor.getDefaultCursor());
                }
                break;

            case FILE_SAVETEXT:
                try {
                    // create default filename
                    JLFunc.jfc().setSelectedFile(new File(getTitle() + ".txt"));
                    JLFunc.jfc().setFileFilter(new FileNameExtensionFilter("Text file", "txt"));

                    if (JLFunc.jfc().showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
                        break;

                    File f = JLFunc.jfc().getSelectedFile();
                    if (f == null)
                        break;
                    if (!f.getAbsolutePath().endsWith(".txt"))
                        f = new File(f.getAbsolutePath() + ".txt");

                    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    FileWriter fw = new FileWriter(f);
                    pl.writeText(fw);
                    fw.close();
                } catch (FileNotFoundException fnfe) {
                    throw new JuggleExceptionInternal("File not found on save: " +
                            fnfe.getMessage());
                } catch (IOException ioe) {
                    throw new JuggleExceptionInternal("IOException on save: " +
                            ioe.getMessage());
                } finally {
                    setCursor(Cursor.getDefaultCursor());
                }
                break;

            case HELP_ABOUT:
                ApplicationWindow.showAboutBox();
                break;

            case HELP_ONLINE:
                ApplicationWindow.showOnlineHelp();
                break;
        }
    }

    // java.awt.Frame methods

    @Override
    public void setTitle(String title) {
        if (title == null || title.length() == 0)
            title = guistrings.getString("PLWINDOW_Default_window_title");

        super.setTitle(title);
        ApplicationWindow.updateWindowMenus();
    }

    // java.awt.Window methods

    @Override
    public void dispose() {
        super.dispose();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ApplicationWindow.updateWindowMenus();
            }
        });
    }
}
