// PatternWindow.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.core;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import jugglinglab.jml.JMLPattern;
import jugglinglab.util.*;
import jugglinglab.view.*;


// This class is the window that contains juggling animations. The animation
// itself is rendered by the View object.

public class PatternWindow extends JFrame implements ActionListener {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;
    static protected boolean exit_on_last_close;

    // used for tiling the animation windows on the screen as they're created
    static protected final int NUM_TILES = 8;
    static protected final Point TILE_START = new Point(420, 50);
    static protected final Point TILE_OFFSET = new Point(25, 25);
    static protected Point[] tile_locations;
    static protected int next_tile_num;

    static protected Class<?> optimizer;
    static protected boolean optimizer_loaded;

    protected View view;
    protected JMenu viewmenu;
    protected JMenu windowmenu;
    protected ArrayList<JMLPattern> undo = new ArrayList<JMLPattern>();


    public PatternWindow(String title, JMLPattern pat, AnimationPrefs jc) throws
                            JuggleExceptionUser, JuggleExceptionInternal {
        super(title);
        loadOptimizer();  // Do this before creating menus
        createMenus();
        createContents(pat, jc);

        view.restartView(pat, jc);
        view.setUndoList(undo, -1);
        view.addToUndoList(pat);

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

    // Create a new PatternWindow with the same JMLPattern and default View.
    protected PatternWindow(PatternWindow pw) throws
                            JuggleExceptionUser, JuggleExceptionInternal {
        this(pw.getTitle(),
             new JMLPattern(pw.view.getPattern()),
             new AnimationPrefs(pw.view.getAnimationPrefs()));
    }

    //-------------------------------------------------------------------------
    // Methods to create and manage window contents
    //-------------------------------------------------------------------------

    protected void createContents(JMLPattern pat, AnimationPrefs jc) throws
                        JuggleExceptionUser, JuggleExceptionInternal {
        if (jc != null && jc.view != View.VIEW_NONE) {
            setViewMode(jc.view);
            viewmenu.getItem(jc.view - 1).setSelected(true);
            jc.view = View.VIEW_NONE;
        } else {
            // no view type specified, use defaults
            if (pat.getNumberOfJugglers() > 1) {
                setViewMode(View.VIEW_SIMPLE);
                viewmenu.getItem(View.VIEW_SIMPLE - 1).setSelected(true);
            } else {
                setViewMode(View.VIEW_EDIT);
                viewmenu.getItem(View.VIEW_EDIT - 1).setSelected(true);
            }
        }
        view.setDoubleBuffered(true);
        if (jc != null)
            view.setAnimationPanelPreferredSize(new Dimension(jc.width, jc.height));

        Locale loc = JLLocale.getLocale();
        applyComponentOrientation(ComponentOrientation.getOrientation(loc));

        setBackground(Color.white);
        pack();
    }

    protected void setViewMode(int mode) throws JuggleExceptionUser,
                        JuggleExceptionInternal {
        View newview = null;

        // items to carry over from old view to the new:
        JMLPattern pat = null;
        AnimationPrefs jc = null;
        boolean paused = false;
        int undo_index = 0;

        if (view != null) {
            pat = view.getPattern();
            jc = view.getAnimationPrefs();
            paused = view.getPaused();
            undo_index = view.getUndoIndex();
        } else
            jc = new AnimationPrefs();

        Dimension animsize = new Dimension(jc.width, jc.height);

        switch (mode) {
            case View.VIEW_NONE:
                break;
            case View.VIEW_SIMPLE:
                newview = new SimpleView(animsize);
                break;
            case View.VIEW_EDIT:
                newview = new EditView(animsize);
                break;
            case View.VIEW_PATTERN:
                newview = new PatternView(animsize);
                break;
            case View.VIEW_SELECTION:
                newview = new SelectionView(animsize);
                break;
        }
        if (newview == null)
            throw new JuggleExceptionInternal("setViewMode: problem creating view");

        newview.setParent(this);
        newview.setPaused(paused);
        newview.setOpaque(true);
        setContentPane(newview);

        if (view != null) {
            // don't get here for a newly-constructed window; pack(),
            // restartView(), and setUndoList() happen in constructor
            if (isWindowMaximized())
                validate();
            else
                pack();
            newview.restartView(pat, jc);
            newview.setUndoList(undo, undo_index);

            view.disposeView();
        }

        view = newview;
    }

    public int getViewMode() {
        if (view == null)
            return View.VIEW_NONE;
        if (view instanceof SimpleView)
            return View.VIEW_SIMPLE;
        if (view instanceof EditView)
            return View.VIEW_EDIT;
        if (view instanceof PatternView)
            return View.VIEW_PATTERN;
        if (view instanceof SelectionView)
            return View.VIEW_SELECTION;
        return View.VIEW_NONE;
    }

    public JMLPattern getPattern() {
        return (view == null) ? null : view.getPattern();
    }

    public AnimationPrefs getAnimationPrefs() {
        return (view == null) ? null : view.getAnimationPrefs();
    }

    // Used for testing whether a given JMLPattern is already being animated.
    // See bringToFront().
    //
    // DO NOT override java.lang.Object.hashCode() -- for some reason the
    // system calls it a lot, and menu shortcut keys stop working. Weird.
    protected int getHashCode() {
        return (view == null) ? 0 : view.getHashCode();
    }

    // For determining if the current window is maximized in the UI.
    //
    // Note this does not work on macOS, where java does not set a Frame's
    // extended state flag when it's maximized. Nor is there any other
    // replacement for com.apple.eawt.FullScreenListener. See e.g.
    // https://bugs.openjdk.java.net/browse/JDK-8228638
    public boolean isWindowMaximized() {
        return ((getExtendedState() & MAXIMIZED_BOTH) != 0);
    }

    //-------------------------------------------------------------------------
    // Static methods
    //-------------------------------------------------------------------------

    // Load the pattern optimizer. Do this using the reflection API so we can
    // omit the feature by leaving those source files out of the compile.
    protected static void loadOptimizer() {
        if (optimizer_loaded)
            return;

        try {
            optimizer = Class.forName("jugglinglab.optimizer.Optimizer");

            Method optimizerAvailable = optimizer.getMethod("optimizerAvailable");
            Boolean canOptimize = (Boolean)optimizerAvailable.invoke(null);
            if (canOptimize.booleanValue() == false)
                optimizer = null;
        } catch (Exception e) {
            optimizer = null;
            if (jugglinglab.core.Constants.DEBUG_OPTIMIZE)
                System.out.println("Exception loading optimizer: " + e.toString());
        }
        optimizer_loaded = true;
    }

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

    // Static method to check if a given pattern is already being animated, and
    // if so then bring that window to the front.
    //
    // Returns true if animation found, false if not.
    public static boolean bringToFront(int hash) {
        for (Frame fr : Frame.getFrames()) {
            if (fr instanceof PatternWindow && fr.isVisible()) {
                final PatternWindow pw = (PatternWindow)fr;

                if (pw.getHashCode() == hash) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            pw.toFront();
                        }
                    });
                    return true;
                }
            }
        }
        return false;
    }

    // Used when a single animation is created from the command line
    public static void setExitOnLastClose(boolean value) {
        exit_on_last_close = value;
    }

    //-------------------------------------------------------------------------
    // Menu creation and handlers
    //-------------------------------------------------------------------------

    protected void createMenus() {
        JMenuBar mb = new JMenuBar();
        mb.add(createFileMenu());
        mb.add(createViewMenu());
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
            "Save Animated GIF As...",
            null,
            "Duplicate",
            "Optimize",
            "Flip Pattern in X",
            "Flip Pattern in Time",
            null,
            "Close",
        };
    protected static final String[] fileCommands = new String[]
        {
            "newpat",
            "newpl",
            "open",
            "saveas",
            "savegifanim",
            null,
            "duplicate",
            "optimize",
            "invertx",
            "inverttime",
            null,
            "close",
        };
    protected static final char[] fileShortcuts =
        {
            'N',
            'L',
            'O',
            'S',
            'G',
            ' ',
            'D',
            'J',
            'M',
            'T',
            ' ',
            'W',
        };

    protected JMenu createFileMenu() {
        JMenu filemenu = new JMenu(guistrings.getString("File"));
        for (int i = 0; i < fileItems.length; i++) {
            if (fileItems[i] == null) {
                filemenu.addSeparator();
                continue;
            }

            JMenuItem fileitem = new JMenuItem(
                    guistrings.getString(fileItems[i].replace(' ', '_')));

            if (fileShortcuts[i] != ' ')
                fileitem.setAccelerator(KeyStroke.getKeyStroke(fileShortcuts[i],
                        Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));

            fileitem.setActionCommand(fileCommands[i]);
            fileitem.addActionListener(this);
            filemenu.add(fileitem);

            if (fileCommands[i].equals("optimize") && optimizer == null)
                fileitem.setEnabled(false);
        }
        return filemenu;
    }

    protected static final String[] viewItems = new String[]
        {
            "Simple",
            "Visual Editor",
            "Pattern Editor",
            "Selection Editor",
            null,
            "Restart",
            "Animation Preferences...",
            "Undo",
            "Redo",
        };
    protected static final String[] viewCommands = new String[]
        {
            "simple",
            "visual_edit",
            "pattern_edit",
            "selection_edit",
            null,
            "restart",
            "prefs",
            "undo",
            "redo",
        };
    protected static final char[] viewShortcuts =
        {
            '1',
            '2',
            '3',
            '4',
            ' ',
            ' ',
            'P',
            'Z',
            'Y',
        };

    protected JMenu createViewMenu() {
        viewmenu = new JMenu(guistrings.getString("View"));
        ButtonGroup buttonGroup = new ButtonGroup();
        boolean addingviews = true;

        for (int i = 0; i < viewItems.length; i++) {
            if (viewItems[i] == null) {
                viewmenu.addSeparator();
                addingviews = false;
                continue;
            }

            if (addingviews) {
                JRadioButtonMenuItem viewitem = new JRadioButtonMenuItem(
                        guistrings.getString(viewItems[i].replace(' ', '_')));

                if (viewShortcuts[i] != ' ')
                    viewitem.setAccelerator(KeyStroke.getKeyStroke(viewShortcuts[i],
                            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));

                viewitem.setActionCommand(viewCommands[i]);
                viewitem.addActionListener(this);
                viewmenu.add(viewitem);
                buttonGroup.add(viewitem);
            } else {
                JMenuItem viewitem = new JMenuItem(
                        guistrings.getString(viewItems[i].replace(' ', '_')));

                if (viewShortcuts[i] != ' ')
                    viewitem.setAccelerator(KeyStroke.getKeyStroke(viewShortcuts[i],
                            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));

                viewitem.setActionCommand(viewCommands[i]);
                viewitem.addActionListener(this);
                viewmenu.add(viewitem);
            }
        }
        return viewmenu;
    }

    public void updateUndoMenu() {
        if (view == null || viewmenu == null)
            return;

        int undo_index = view.getUndoIndex();
        boolean undo_enabled = (undo_index > 0);
        boolean redo_enabled = (undo_index < undo.size() - 1);

        for (int i = 0; i < viewmenu.getItemCount(); ++i) {
            JMenuItem jmi = viewmenu.getItem(i);
            if (jmi == null || jmi.getActionCommand() == null)
                continue;

            if (jmi.getActionCommand().equals("undo"))
                jmi.setEnabled(undo_enabled);
            else if (jmi.getActionCommand().equals("redo"))
                jmi.setEnabled(redo_enabled);
        }
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
                JMenuItem helpitem = new JMenuItem(
                        guistrings.getString(helpItems[i].replace(' ', '_')));
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
            else if (command.equals("savegifanim"))
                doMenuCommand(MenuCommand.FILE_GIFSAVE);
            else if (command.equals("duplicate"))
                doMenuCommand(MenuCommand.FILE_DUPLICATE);
            else if (command.equals("optimize"))
                doMenuCommand(MenuCommand.FILE_OPTIMIZE);
            else if (command.equals("invertx"))
                doMenuCommand(MenuCommand.FILE_INVERTX);
            else if (command.equals("inverttime"))
                doMenuCommand(MenuCommand.FILE_INVERTTIME);
            else if (command.equals("restart"))
                doMenuCommand(MenuCommand.VIEW_RESTART);
            else if (command.equals("prefs"))
                doMenuCommand(MenuCommand.VIEW_ANIMPREFS);
            else if (command.equals("undo"))
                doMenuCommand(MenuCommand.VIEW_UNDO);
            else if (command.equals("redo"))
                doMenuCommand(MenuCommand.VIEW_REDO);
            else if (command.equals("simple")) {
                if (getViewMode() != View.VIEW_SIMPLE)
                    setViewMode(View.VIEW_SIMPLE);
            }
            else if (command.equals("visual_edit")) {
                if (getViewMode() != View.VIEW_EDIT)
                    setViewMode(View.VIEW_EDIT);
            }
            else if (command.equals("pattern_edit")) {
                if (getViewMode() != View.VIEW_PATTERN)
                    setViewMode(View.VIEW_PATTERN);
            }
            else if (command.equals("selection_edit")) {
                if (getViewMode() != View.VIEW_SELECTION)
                    setViewMode(View.VIEW_SELECTION);
            } else if (command.equals("about"))
                doMenuCommand(MenuCommand.HELP_ABOUT);
            else if (command.equals("online"))
                doMenuCommand(MenuCommand.HELP_ONLINE);
        } catch (JuggleExceptionUser je) {
            new ErrorDialog(this, je.getMessage());
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
        FILE_GIFSAVE,
        FILE_DUPLICATE,
        FILE_OPTIMIZE,
        FILE_INVERTX,
        FILE_INVERTTIME,
        VIEW_RESTART,
        VIEW_ANIMPREFS,
        VIEW_UNDO,
        VIEW_REDO,
        HELP_ABOUT,
        HELP_ONLINE,
    }

    protected void doMenuCommand(MenuCommand action) throws
                            JuggleExceptionUser, JuggleExceptionInternal {
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

                if (PatternWindow.exit_on_last_close) {
                    int window_count = 0;
                    for (Frame fr : Frame.getFrames()) {
                        if (fr instanceof PatternWindow && fr.isVisible())
                            ++window_count;
                    }
                    if (window_count == 0)
                        System.exit(0);
                }
                break;

            case FILE_SAVE:
                if (view == null)
                    break;
                if (!view.getPattern().isValid())
                    throw new JuggleExceptionUser(errorstrings.getString(
                                "Error_saving_invalid_pattern"));

                // create default filename
                String fname = getTitle() + ".jml";
                fname = JLFunc.sanitizeFilename(fname);
                JLFunc.jfc().setSelectedFile(new File(fname));
                JLFunc.jfc().setFileFilter(new FileNameExtensionFilter("JML file", "jml"));

                if (JLFunc.jfc().showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
                    break;

                File f = JLFunc.jfc().getSelectedFile();
                if (f == null)
                    break;
                if (!f.getAbsolutePath().endsWith(".jml"))
                    f = new File(f.getAbsolutePath() + ".jml");

                JLFunc.errorIfNotSanitized(f.getName());

                try {
                    FileWriter fw = new FileWriter(f);
                    view.getPattern().writeJML(fw, true);
                    fw.close();
                } catch (FileNotFoundException fnfe) {
                    throw new JuggleExceptionInternal("FileNotFound: " +
                                    fnfe.getMessage());
                } catch (IOException ioe) {
                    throw new JuggleExceptionInternal("IOException: " +
                                    ioe.getMessage());
                }
                break;

            case FILE_GIFSAVE:
                if (view != null)
                    view.writeGIF();
                break;

            case FILE_DUPLICATE:
                new PatternWindow(this);
                break;

            case FILE_OPTIMIZE:
                if (jugglinglab.core.Constants.DEBUG_OPTIMIZE) {
                    System.out.println("-------------------------------------------");
                    System.out.println("optimizing in PatternWindow.doMenuCommand()");
                }

                if (optimizer == null || view == null)
                    break;

                try {
                    Method optimize = optimizer.getMethod("optimize", JMLPattern.class);
                    JMLPattern pat = view.getPattern();
                    JMLPattern new_pat = (JMLPattern)optimize.invoke(null, pat);
                    view.restartView(new_pat, null);
                    view.addToUndoList(new_pat);
                } catch (JuggleExceptionUser jeu) {
                    throw new JuggleExceptionInternal("optimizer jeu: " +
                                        jeu.getMessage());
                } catch (InvocationTargetException ite) {
                    // exceptions thrown by Optimizer.optimize() land here
                    Throwable ex = ite.getCause();
                    if (jugglinglab.core.Constants.DEBUG_OPTIMIZE)
                        System.out.println("ite: " + ex.getMessage());
                    if (ex instanceof JuggleExceptionUser)
                        throw (JuggleExceptionUser)ex;
                    else if (ex instanceof JuggleExceptionInternal)
                        throw (JuggleExceptionInternal)ex;
                    else
                        throw new JuggleExceptionInternal("optimizer unknown ite: " +
                                ex.getMessage());
                } catch (NoSuchMethodException nsme) {
                    if (jugglinglab.core.Constants.DEBUG_OPTIMIZE)
                        System.out.println("nsme: " + nsme.getMessage());
                    throw new JuggleExceptionInternal("optimizer nsme: " +
                                        nsme.getMessage());
                } catch (IllegalAccessException iae) {
                    if (jugglinglab.core.Constants.DEBUG_OPTIMIZE)
                        System.out.println("iae: " + iae.getMessage());
                    throw new JuggleExceptionInternal("optimizer iae: " +
                                        iae.getMessage());
                }
                break;

            case FILE_INVERTX:
                if (view == null)
                    break;

                try {
                    JMLPattern newpat = new JMLPattern(view.getPattern());
                    newpat.invertXAxis();
                    view.restartView(newpat, null);
                    view.addToUndoList(newpat);
                } catch (JuggleExceptionUser jeu) {
                    throw new JuggleExceptionInternal("Error in FILE_INVERTX");
                }
                break;

            case FILE_INVERTTIME:
                if (view == null)
                    break;

                try {
                    JMLPattern newpat = new JMLPattern(view.getPattern());
                    newpat.invertTime();
                    view.restartView(newpat, null);
                    view.addToUndoList(newpat);
                } catch (JuggleExceptionUser jeu) {
                    throw new JuggleExceptionInternal("Error in FILE_INVERTTIME");
                }
                break;

            case VIEW_RESTART:
                if (view != null)
                    view.restartView();
                break;

            case VIEW_ANIMPREFS:
                if (view == null)
                    break;

                AnimationPrefs jc = view.getAnimationPrefs();
                AnimationPrefsDialog japd = new AnimationPrefsDialog(this);
                AnimationPrefs newjc = japd.getPrefs(jc);

                if (newjc.width != jc.width || newjc.height != jc.height) {
                    // user changed the width and/or height
                    view.setAnimationPanelPreferredSize(new Dimension(newjc.width,
                                                                newjc.height));
                    pack();
                }

                if (newjc != jc)
                    view.restartView(null, newjc);
                break;

            case VIEW_UNDO:
                if (view != null)
                    view.undoEdit();
                break;

            case VIEW_REDO:
                if (view != null)
                    view.redoEdit();
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
            title = guistrings.getString("PWINDOW_Default_window_title");

        super.setTitle(title);
        ApplicationWindow.updateWindowMenus();
    }

    // java.awt.Window methods

    @Override
    public void dispose() {
        super.dispose();
        if (view != null) {
            view.disposeView();
            view = null;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ApplicationWindow.updateWindowMenus();
            }
        });
    }
}
