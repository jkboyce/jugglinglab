// PatternListWindow.java
//
// Copyright 2002-2021 Jack Boyce and the Juggling Lab contributors

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

    protected String title;
    protected PatternListPanel pl;
    protected JMenuItem[] fileitems;


    public PatternListWindow(String title) {
        super(title);
        this.title = title;
        makeWindow();
        pl.setTitle(title);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    }

    public PatternListWindow(String title, Thread th) {
        this(title);
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

        setLocation(getNextScreenLocation());
        setVisible(true);
    }

    // Return the location (screen pixels) of where the next animation window to
    // be created should go. This allows us to create a tiling effect.
    protected Point getNextScreenLocation() {
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
                                                                       Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
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
                    // create default filename
                    String t = title;
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

                    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    FileWriter fw = new FileWriter(f);
                    pl.writeJML(fw);
                    fw.close();
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
                    // create default filename
                    String t = title;
                    if (t == null || t.length() == 0)
                        t = "pattern";
                    JLFunc.jfc().setSelectedFile(new File(t + ".txt"));
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
