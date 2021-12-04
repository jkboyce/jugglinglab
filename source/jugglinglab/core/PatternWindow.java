// PatternWindow.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.core;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import javax.swing.*;

import jugglinglab.jml.*;
import jugglinglab.util.*;
import jugglinglab.view.*;


public class PatternWindow extends JFrame implements ActionListener {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;
    static protected Class<?> optimizer;

    protected View view;
    protected JMenu filemenu;
    protected JMenu viewmenu;
    protected boolean exit_on_close = false;

    protected String base_notation = null;
    protected String base_config = null;
    protected boolean base_edited = false;

    // used for tiling the animation windows on the screen as they're created
    static protected final int NUM_TILES = 8;
    static protected final Point TILE_SHIFT = new Point(195, 0);  // relative to screen center
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

    public PatternWindow(String name, JMLPattern pat, AnimationPrefs jc) throws
                            JuggleExceptionUser, JuggleExceptionInternal {
        super(name);

        JMenuBar mb = new JMenuBar();
        this.filemenu = createFileMenu();
        mb.add(filemenu);
        this.viewmenu = createViewMenu();
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
                if (exit_on_close)
                    System.exit(0);
            }
        });
    }

    // Return the location (screen pixels) of where the next animation window to
    // be created should go. This allows us to create a tiling effect.
    protected Point getNextScreenLocation() {
        if (tile_locations == null) {
            tile_locations = new Point[NUM_TILES];
            Point center = GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint();
            Point middle_tile_loc = new Point(center.x + TILE_SHIFT.x - getSize().width / 2,
                                              center.y + TILE_SHIFT.y - getSize().height / 2);

            for (int i = 0; i < NUM_TILES; ++i) {
                int loc_x = middle_tile_loc.x + (i - NUM_TILES / 2) * TILE_OFFSET.x;
                int loc_y = middle_tile_loc.y + (i - NUM_TILES / 2) * TILE_OFFSET.y;
                tile_locations[i] = new Point(loc_x, loc_y);
            }

            next_tile_num = 0;
        }

        Point loc = tile_locations[next_tile_num];
        if (++next_tile_num == NUM_TILES)
            next_tile_num = 0;
        return loc;
    }

    // Each PatternWindow retains the config string and notation for the
    // pattern it contains.
    //
    // The config strings are always assumed to be in canonical order, i.e.,
    // what is produced by Pattern.toString().
    public void setBasePattern(String notation, String config) {
        base_notation = notation;
        base_config = config;
        base_edited = false;
    }

    public String getBasePatternNotation() {
        return base_notation;
    }

    public String getBasePatternConfig() {
        return base_config;
    }

    // Test whether the JMLPattern being animated is identical to the
    // given base pattern.
    public boolean isUneditedBasePattern(String notation, String config) {
        if (base_edited)
            return false;

        return (config.equals(base_config) &&
                notation.equalsIgnoreCase(base_notation));
    }

    // For containing views to notify that the pattern has been edited.
    public void notifyEdited() {
        base_edited = true;
    }

    // Static method to check if a given pattern is already being animated, and
    // is unedited. If so then bring that animation to the front.
    //
    // Returns true if animation found, false if not.
    public static boolean bringToFront(String notation, String config) {
        for (Frame fr : Frame.getFrames()) {
            if (fr instanceof PatternWindow) {
                final PatternWindow pw = (PatternWindow)fr;

                if (!pw.isVisible()) {
                    //System.out.println("found a matching hidden PatternWindow");
                    continue;
                }

                if (pw.isUneditedBasePattern(notation, config)) {
                    //System.out.println("found a matching PatternWindow");
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
        //System.out.println("no matching PatternWindow");

        return false;
    }

    // Used when a single animation is created from the command line
    public void setExitOnClose(boolean value) {
        this.exit_on_close = value;
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
                    new PatternWindow(getTitle(),
                                      (JMLPattern)view.getPattern().clone(),
                                      new AnimationPrefs(view.getAnimationPrefs()));
                } catch (JuggleExceptionUser jeu) {
                    new ErrorDialog(this, jeu.getMessage());
                }
                break;

            case FILE_OPTIMIZE:
                if (optimizer != null && view != null) {
                    if (jugglinglab.core.Constants.DEBUG_OPTIMIZE) {
                        System.out.println("------------------------------------------------------");
                        System.out.println("optimizing in PatternWindow.doMenuCommand()");
                    }

                    try {
                        Method optimize = optimizer.getMethod("optimize", JMLPattern.class);
                        JMLPattern pat = view.getPattern();
                        JMLPattern new_pat = (JMLPattern)optimize.invoke(null, pat);
                        view.restartView(new_pat, null);
                    } catch (JuggleExceptionUser jeu) {
                        new ErrorDialog(this, jeu.getMessage());
                    } catch (NoSuchMethodException nsme) {
                        if (jugglinglab.core.Constants.DEBUG_OPTIMIZE)
                            System.out.println("nsme: " + nsme.toString());
                    } catch (IllegalAccessException iae) {
                        if (jugglinglab.core.Constants.DEBUG_OPTIMIZE)
                            System.out.println("iae: " + iae.toString());
                    } catch (InvocationTargetException ite) {
                        if (jugglinglab.core.Constants.DEBUG_OPTIMIZE)
                            System.out.println("ite: " + ite.toString());
                    }
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

    @Override
    public synchronized void dispose() {
        super.dispose();
        if (view != null) {
            view.disposeView();
            view = null;
        }
    }
}
