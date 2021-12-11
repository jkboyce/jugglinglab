// PatternWindow.java
//
// Copyright 2021 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.core;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
    static protected Class<?> optimizer;
    static protected boolean exit_on_last_close = false;

    protected View view;
    protected JMenu filemenu;
    protected JMenu viewmenu;

    // used for tiling the animation windows on the screen as they're created
    static protected final int NUM_TILES = 8;
    static protected final Point TILE_START = new Point(470, 80);
    static protected final Point TILE_OFFSET = new Point(25, 25);
    static protected Point[] tile_locations = null;
    static protected int next_tile_num;

    static {
        // load the optimizer using the reflection API so we can omit it by
        // leaving those source files out of the compile.
        try {
            optimizer = Class.forName("jugglinglab.optimizer.Optimizer");

            Method optimizerAvailable = optimizer.getMethod("optimizerAvailable");
            Boolean canOptimize = (Boolean)optimizerAvailable.invoke(null);
            if (!canOptimize.booleanValue())
                optimizer = null;
        } catch (Exception e) {
            optimizer = null;
            if (jugglinglab.core.Constants.DEBUG_OPTIMIZE)
                System.out.println("Exception loading optimizer: " + e.toString());
        }
    }


    public PatternWindow(String title, JMLPattern pat, AnimationPrefs jc) throws
                            JuggleExceptionUser, JuggleExceptionInternal {
        super(title);

        JMenuBar mb = new JMenuBar();
        filemenu = createFileMenu();
        mb.add(filemenu);
        viewmenu = createViewMenu();
        mb.add(viewmenu);
        setJMenuBar(mb);

        if (jc != null && jc.view != View.VIEW_NONE) {
            setViewMode(jc.view);
            viewmenu.getItem(jc.view - 1).setSelected(true);
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

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setBackground(Color.white);

        Locale loc = JLLocale.getLocale();
        applyComponentOrientation(ComponentOrientation.getOrientation(loc));

        pack();
        view.restartView(pat, jc);
        //setLocationRelativeTo(null);    // center frame on screen
        setLocation(getNextScreenLocation());
        setVisible(true);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (!PatternWindow.exit_on_last_close)
                    return;

                int window_count = 0;
                for (Frame fr : Frame.getFrames()) {
                    if (fr instanceof PatternWindow && fr.isVisible())
                        window_count++;
                }
                if (window_count == 1)
                    System.exit(0);
            }
        });
    }

    // Create a new PatternWindow with the same JMLPattern and base pattern,
    // and the default View.
    protected PatternWindow(PatternWindow pw) throws JuggleExceptionUser, JuggleExceptionInternal {
        this(pw.getTitle(),
             new JMLPattern(pw.view.getPattern()),
             new AnimationPrefs(pw.view.getAnimationPrefs()));

        String bp_notation = pw.view.getBasePatternNotation();
        String bp_config = pw.view.getBasePatternConfig();
        if (bp_notation != null && bp_config != null) {
            setBasePattern(bp_notation, bp_config);

            if (pw.view.getBasePatternEdited())
                notifyEdited();
        }
    }

    // Return the location (screen pixels) of where the next animation window to
    // be created should go. This allows us to create a tiling effect.
    protected Point getNextScreenLocation() {
        if (tile_locations == null) {
            tile_locations = new Point[NUM_TILES];

            for (int i = 0; i < NUM_TILES; ++i) {
                int loc_x = TILE_START.x + i * TILE_OFFSET.x;
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

    // The View retains the notation and config string for the pattern it contains.
    public void setBasePattern(String notation, String config) throws JuggleExceptionUser {
        if (view != null)
            view.setBasePattern(notation, config);
    }

    // Allow containing elements to notify that the pattern has been edited.
    public void notifyEdited() {
        if (view != null)
            view.setBasePatternEdited(true);
    }

    // Used for testing whether a given JMLPattern is already being animated.
    // See bringToFront().
    //
    // DO NOT override java.lang.Object.hashCode() -- for some reason the
    // system calls it a lot, and menu shortcut keys stop working. Weird.
    protected int getHashCode() {
        return (view == null) ? 0 : view.getHashCode();
    }

    // Static method to check if a given pattern is already being animated, and
    // if so then bring that window to the front.
    //
    // Returns true if animation found, false if not.
    public static boolean bringToFront(int hash) {
        for (Frame fr : Frame.getFrames()) {
            if (fr instanceof PatternWindow) {
                final PatternWindow pw = (PatternWindow)fr;

                if (!pw.isVisible())
                    continue;

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

    protected static final String[] fileItems = new String[]
        { "Close", null, "Save JML As...", "Save Animated GIF As...", null, "Duplicate", "Optimize" };
    protected static final String[] fileCommands = new String[]
        { "close", null, "saveas", "savegifanim", null, "duplicate", "optimize" };
    protected static final char[] fileShortcuts =
        { 'W', ' ', 'S', ' ', ' ', 'D', 'J' };

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

                if (fileCommands[i].equals("optimize") && optimizer == null)
                    fileitem.setEnabled(false);
            }
        }
        return filemenu;
    }

    protected static final String[] viewItems = new String[]
        { "Simple", "Visual Editor", "Pattern Editor", "Selection Editor",
          null, "Restart", "Animation Preferences..." };
    protected static final String[] viewCommands = new String[]
        { "simple", "visual_edit", "pattern_edit", "selection_edit",
          null, "restart", "prefs" };
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
            else if (command.equals("optimize"))
                doMenuCommand(FILE_OPTIMIZE);
            else if (command.equals("restart"))
                doMenuCommand(VIEW_RESTART);
            else if (command.equals("prefs"))
                doMenuCommand(VIEW_ANIMPREFS);
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
    protected static final int FILE_OPTIMIZE = 5;
    protected static final int VIEW_RESTART = 6;
    protected static final int VIEW_ANIMPREFS = 7;

    protected void doMenuCommand(int action) throws JuggleExceptionInternal {
        switch (action) {
            case FILE_NONE:
                break;

            case FILE_CLOSE:
                dispose();
                break;

            case FILE_SAVE:
                if (view == null || !view.getPattern().isValid()) {
                    new ErrorDialog(this, "Could not save: pattern is not valid");
                    break;
                }

                // create default filename
                String t = view.getPattern().getTitle();
                if (t == null || t.length() == 0)
                    t = "pattern";
                JLFunc.jfc().setSelectedFile(new File(t + ".jml"));
                JLFunc.jfc().setFileFilter(new FileNameExtensionFilter("JML file", "jml"));

                if (JLFunc.jfc().showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
                    break;

                File f = JLFunc.jfc().getSelectedFile();
                if (f == null)
                    break;
                if (!f.getAbsolutePath().endsWith(".jml"))
                    f = new File(f.getAbsolutePath() + ".jml");

                try {
                    FileWriter fw = new FileWriter(f);
                    view.getPattern().writeJML(fw, true);
                    fw.close();
                } catch (FileNotFoundException fnfe) {
                    throw new JuggleExceptionInternal("FileNotFound: "+fnfe.getMessage());
                } catch (IOException ioe) {
                    throw new JuggleExceptionInternal("IOException: "+ioe.getMessage());
                }
                break;

            case FILE_GIFSAVE:
                if (view != null)
                    view.writeGIF();
                break;

            case FILE_DUPLICATE:
                try {
                    new PatternWindow(this);
                } catch (JuggleExceptionUser jeu) {
                    // This shouldn't ever happen
                    new ErrorDialog(this, jeu.getMessage());
                }
                break;

            case FILE_OPTIMIZE:
                if (jugglinglab.core.Constants.DEBUG_OPTIMIZE) {
                    System.out.println("------------------------------------------------------");
                    System.out.println("optimizing in PatternWindow.doMenuCommand()");
                }

                if (optimizer == null || view == null)
                    break;

                try {
                    Method optimize = optimizer.getMethod("optimize", JMLPattern.class);
                    JMLPattern pat = view.getPattern();
                    JMLPattern new_pat = (JMLPattern)optimize.invoke(null, pat);
                    view.restartView(new_pat, null);
                    notifyEdited();
                } catch (JuggleExceptionUser jeu) {
                    new ErrorDialog(this, jeu.getMessage());
                } catch (InvocationTargetException ite) {
                    // exceptions thrown by Optimizer.optimize() land here
                    Throwable ex = ite.getCause();
                    if (jugglinglab.core.Constants.DEBUG_OPTIMIZE)
                        System.out.println("ite: " + ex.getMessage());
                    if (ex instanceof JuggleExceptionUser)
                        new ErrorDialog(this, ex.getMessage());
                    else if (ex instanceof JuggleExceptionInternal)
                        throw (JuggleExceptionInternal)ex;
                    else
                        throw new JuggleExceptionInternal("optimizer unknown ite: " + ex.getMessage());
                } catch (NoSuchMethodException nsme) {
                    if (jugglinglab.core.Constants.DEBUG_OPTIMIZE)
                        System.out.println("nsme: " + nsme.getMessage());
                    throw new JuggleExceptionInternal("optimizer nsme: " + nsme.getMessage());
                } catch (IllegalAccessException iae) {
                    if (jugglinglab.core.Constants.DEBUG_OPTIMIZE)
                        System.out.println("iae: " + iae.getMessage());
                    throw new JuggleExceptionInternal("optimizer iae: " + iae.getMessage());
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

    protected void setViewMode(int mode) throws JuggleExceptionUser,
                        JuggleExceptionInternal {
        View newview = null;

        // items to carry over from old view to the new:
        JMLPattern pat = null;
        String bp_notation = null;
        String bp_config = null;
        boolean bp_edited = false;
        AnimationPrefs jc = null;
        Dimension animsize = null;
        boolean paused = false;

        if (view != null) {
            pat = view.getPattern();
            bp_notation = view.getBasePatternNotation();
            bp_config = view.getBasePatternConfig();
            bp_edited = view.getBasePatternEdited();
            jc = view.getAnimationPrefs();
            animsize = view.getAnimationPanelSize();
            paused = view.getPaused();
        } else {
            // use default size
            AnimationPrefs tempjc = new AnimationPrefs();
            animsize = new Dimension(tempjc.width, tempjc.height);
        }

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
            view.disposeView();
            view = newview;
            pack();
            if (bp_notation != null) {
                view.setBasePattern(bp_notation, bp_config);
                view.setBasePatternEdited(bp_edited);
            }
            view.restartView(pat, jc);
        } else
            // pack() and restartView() happen in constructor
            view = newview;
    }

    protected int getViewMode() {
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

    // java.awt.Window method overrides

    @Override
    public void dispose() {
        super.dispose();
        if (view != null) {
            view.disposeView();
            view = null;
        }
    }
}
