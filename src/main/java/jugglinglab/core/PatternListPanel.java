// PatternListPanel.java
//
// Copyright 2002-2023 Jack Boyce and the Juggling Lab contributors

package jugglinglab.core;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.ResourceBundle;
import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.event.*;

import jugglinglab.jml.*;
import jugglinglab.jml.JMLPatternList.PatternRecord;
import jugglinglab.util.*;
import jugglinglab.view.View;


public class PatternListPanel extends JPanel {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    static final Font font_nopattern = new Font("SanSerif", Font.BOLD | Font.ITALIC, 14);
    static final Font font_pattern = new Font("Monospaced", Font.PLAIN, 14);
    static final Font font_pattern_popup = new Font("Monospaced", Font.ITALIC, 14);

    static final DataFlavor patternFlavor = new DataFlavor(PatternRecord.class,
                                                 "Juggling Lab pattern record");

    protected JMLPatternList pl;
    protected JFrame parent;
    protected View animtarget;
    protected JList<PatternRecord> list;

    // for mouse/popup menu handling
    protected boolean didPopup;
    protected ArrayList<PatternWindow> popupPatterns;
    protected JDialog dialog;
    protected JTextField tf;
    protected JButton okbutton;

    // for drag and drop operations
    protected boolean draggingOut;


    protected PatternListPanel() {
        pl = new JMLPatternList();
        makePanel();
        setOpaque(false);
    }

    public PatternListPanel(JFrame parent) {
        this();
        this.parent = parent;
    }

    public PatternListPanel(View target) {
        this();
        setTargetView(target);
    }

    //-------------------------------------------------------------------------
    // Methods to create and manage contents
    //-------------------------------------------------------------------------

    protected void makePanel() {
        list = new JList<PatternRecord>(pl.getModel());
        list.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new PatternCellRenderer());

        list.setDragEnabled(true);
        list.setTransferHandler(new PatternTransferHandler());

        JScrollPane pane = new JScrollPane(list);

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(final MouseEvent me) {
                int row = list.locationToIndex(me.getPoint());
                if (row >= 0)
                    list.setSelectedIndex(row);

                didPopup = false;

                if (me.isPopupTrigger()) {
                    // On macOS the popup triggers here
                    makePopupMenu().show(list, me.getX(), me.getY());
                    didPopup = true;
                }
            }

            @Override
            public void mouseReleased(final MouseEvent me) {
                if (me.isPopupTrigger()) {
                    // On Windows the popup triggers here
                    makePopupMenu().show(list, me.getX(), me.getY());
                    didPopup = true;
                }

                if (!didPopup) {
                    launchAnimation();
                    checkSelection();
                }
            }
        });

        setLayout(new BorderLayout());
        add(pane, BorderLayout.CENTER);
    }

    // Try to launch an animation window for the currently-selected item in the
    // list. If there is no pattern associated with the line, do nothing.
    protected void launchAnimation() {
        try {
            int row = list.getSelectedIndex();
            if (row < 0 || (JMLPatternList.BLANK_AT_END && row == pl.getModel().size() - 1))
                return;

            JMLPattern pat = pl.getPatternForLine(row);
            if (pat == null)
                return;
            pat.layoutPattern();  // do this before getting hash code
            if (PatternWindow.bringToFront(pat.getHashCode()))
                return;

            AnimationPrefs ap = pl.getAnimationPrefsForLine(row);

            if (animtarget != null)
                animtarget.restartView(pat, ap);
            else
                new PatternWindow(pat.getTitle(), pat, ap);
        } catch (JuggleExceptionUser jeu) {
            new ErrorDialog(PatternListPanel.this, jeu.getMessage());
        } catch (JuggleExceptionInternal jei) {
            ErrorDialog.handleFatalException(jei);
        }
    }

    public JMLPatternList getPatternList() {
        return pl;
    }

    public void clearList() {
        pl.clearModel();
    }

    public void setTargetView(View target) {
        animtarget = target;
    }

    // Used by GeneratorTarget
    public void addPattern(String display, String animprefs, String notation, String anim) {
        pl.addLine(-1, display, animprefs, notation, anim, null, null);
    }

    //-------------------------------------------------------------------------
    // Popup menu and associated handler methods
    //-------------------------------------------------------------------------

    protected static final String[] popupItems =
        {
            "PLPOPUP Insert text...",
            null,
            "PLPOPUP Insert pattern",
            null,
            "PLPOPUP Change title...",
            "PLPOPUP Change display text...",
            null,
            "PLPOPUP Remove line",
        };

    protected static final String[] popupCommands =
        {
            "inserttext",
            null,
            "insertpattern",
            null,
            "title",
            "displaytext",
            null,
            "remove",
        };

    protected JPopupMenu makePopupMenu() {
        JPopupMenu popup = new JPopupMenu();
        int row = list.getSelectedIndex();

        popupPatterns = new ArrayList<PatternWindow>();
        for (Frame fr : Frame.getFrames()) {
            if (fr.isVisible() && fr instanceof PatternWindow)
                popupPatterns.add((PatternWindow)fr);
        }

        ActionListener al = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                String command = ae.getActionCommand();
                int row = list.getSelectedIndex();

                if (command.equals("inserttext")) {
                    insertText(row);
                } else if (command.equals("insertpattern")) {
                    // do nothing
                } else if (command.equals("title")) {
                    changeTitle();
                } else if (command.equals("displaytext")) {
                    changeDisplayText(row);
                } else if (command.equals("remove")) {
                    pl.getModel().remove(row);
                } else {
                    // inserting a pattern
                    int patnum = Integer.parseInt(command.substring(3, command.length()));
                    PatternWindow pw = popupPatterns.get(patnum);
                    insertPattern(row, pw);
                }
            }
        };

        for (int i = 0; i < popupItems.length; ++i) {
            String name = popupItems[i];

            if (name == null) {
                popup.addSeparator();
                continue;
            }

            JMenuItem item = new JMenuItem(guistrings.getString(name.replace(' ', '_')));
            item.setActionCommand(popupCommands[i]);
            item.addActionListener(al);

            if ((popupCommands[i].equals("displaytext") || popupCommands[i].equals("remove")) && row < 0)
                item.setEnabled(false);
            if ((popupCommands[i].equals("displaytext") || popupCommands[i].equals("remove")) &&
                        JMLPatternList.BLANK_AT_END && row == pl.getModel().size() - 1)
                item.setEnabled(false);
            if (popupCommands[i].equals("insertpattern") && popupPatterns.size() == 0)
                item.setEnabled(false);

            popup.add(item);

            if (popupCommands[i].equals("insertpattern")) {
                int patnum = 0;
                for (PatternWindow pw : popupPatterns) {
                    JMenuItem pitem = new JMenuItem("   " + pw.getTitle());
                    pitem.setActionCommand("pat" + patnum);
                    pitem.addActionListener(al);
                    pitem.setFont(font_pattern_popup);
                    popup.add(pitem);
                    ++patnum;
                }
            }
        }

        popup.setBorder(new BevelBorder(BevelBorder.RAISED));

        popup.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuCanceled(PopupMenuEvent e) { checkSelection(); }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}

            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {}
        });

        return popup;
    }

    // Insert the pattern in the given PatternWindow into the given row
    protected void insertPattern(int row, PatternWindow pw) {
        String display = pw.getTitle();
        String animprefs = pw.getAnimationPrefs().toString();
        if (animprefs.length() == 0)
            animprefs = null;

        String notation = "jml";
        String anim = null;

        JMLPattern pattern = pw.getPattern();
        JMLNode patnode = null;
        try {
            patnode = pattern.getRootNode().findNode("pattern");
        } catch (JuggleExceptionInternal jei) {
            // any error here cannot be user error since pattern is
            // already animating in another window
            ErrorDialog.handleFatalException(jei);
            return;
        }
        JMLNode infonode = patnode.findNode("info");

        if (pattern.hasBasePattern() && !pattern.isBasePatternEdited()) {
            // add as base pattern instead of JML
            notation = pattern.getBasePatternNotation();
            anim = pattern.getBasePatternConfig();
            patnode = null;
        }

        pl.addLine(row, display, animprefs, notation, anim, patnode, infonode);

        if (row < 0) {
            if (JMLPatternList.BLANK_AT_END)
                list.setSelectedIndex(pl.getModel().size() - 2);
            else
                list.setSelectedIndex(pl.getModel().size() - 1);
        } else
            list.setSelectedIndex(row);
    }

    // Open a dialog to allow the user to insert a line of text
    protected void insertText(int row) {
        makeDialog(guistrings.getString("PLDIALOG_Insert_text"), "");

        okbutton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String display = tf.getText();
                dialog.dispose();

                pl.addLine(row, display, null, null, null, null, null);

                if (row < 0)
                    list.setSelectedIndex(pl.size() - 1);
                else
                    list.setSelectedIndex(row);
            }
        });

        dialog.setVisible(true);
    }

    // Open a dialog to allow the user to change the pattern list's title
    protected void changeTitle() {
        makeDialog(guistrings.getString("PLDIALOG_Change_title"), pl.getTitle());

        okbutton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pl.setTitle(tf.getText());
                dialog.dispose();

                if (parent != null)
                    parent.setTitle(pl.getTitle());
            }
        });

        dialog.setVisible(true);
        list.clearSelection();
    }

    // Open a dialog to allow the user to change the display text of a line
    protected void changeDisplayText(int row) {
        PatternRecord rec = pl.getModel().get(row);
        makeDialog(guistrings.getString("PLDIALOG_Change_display_text"), rec.display);

        okbutton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                rec.display = tf.getText();
                dialog.dispose();

                pl.getModel().set(row, rec);
            }
        });

        dialog.setVisible(true);
    }

    // Helper to make popup dialog boxes
    protected JDialog makeDialog(String title, String default_text) {
        dialog = new JDialog(parent, title, true);
        GridBagLayout gb = new GridBagLayout();
        dialog.getContentPane().setLayout(gb);

        tf = new JTextField(20);
        tf.setText(default_text);

        okbutton = new JButton(guistrings.getString("OK"));

        dialog.getContentPane().add(tf);
        gb.setConstraints(tf, JLFunc.constraints(GridBagConstraints.LINE_START,0,0,
                                               new Insets(10,10,0,10)));
        dialog.getContentPane().add(okbutton);
        gb.setConstraints(okbutton, JLFunc.constraints(GridBagConstraints.LINE_END,0,1,
                                                     new Insets(10,10,10,10)));
        dialog.getRootPane().setDefaultButton(okbutton);  // OK button is default
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(this);

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) { checkSelection(); }
        });

        return dialog;
    }

    // Be sure to call this at the very end of any mouse-related interaction
    protected void checkSelection() {
        if (JMLPatternList.BLANK_AT_END && list.getSelectedIndex() == pl.getModel().size() - 1)
            list.clearSelection();

        popupPatterns = null;
        dialog = null;
        tf = null;
        okbutton = null;
    }

    //-------------------------------------------------------------------------
    // Classes to support drag and drop operations
    //-------------------------------------------------------------------------

    class PatternTransferHandler extends TransferHandler {
        @Override
        public int getSourceActions(JComponent c) {
            return TransferHandler.COPY_OR_MOVE;
        }

        @Override
        public Transferable createTransferable(JComponent c) {
            int row = list.getSelectedIndex();
            if (row < 0 || (JMLPatternList.BLANK_AT_END && row == pl.getModel().size() - 1))
                return null;

            draggingOut = true;
            PatternRecord rec = pl.getModel().get(row);
            return new PatternTransferable(rec);
        }

        @Override
        public boolean canImport(TransferHandler.TransferSupport info) {
            // support only drop (not clipboard paste)
            if (!info.isDrop())
                return false;

            if (draggingOut)
                info.setDropAction(MOVE);  // within same list
            else
                info.setDropAction(COPY);  // between lists

            if (info.isDataFlavorSupported(patternFlavor))
                return true;
            if (info.isDataFlavorSupported(DataFlavor.stringFlavor))
                return true;

            return false;
        }

        @Override
        public boolean importData(TransferHandler.TransferSupport info) {
            if (!info.isDrop())
                return false;

            JList.DropLocation dl = (JList.DropLocation)info.getDropLocation();
            int index = dl.getIndex();
            if (index < 0)
                index = (JMLPatternList.BLANK_AT_END ? pl.getModel().size() - 1 : pl.getModel().size());

            // Get the record that is being dropped
            Transferable t = info.getTransferable();

            try {
                if (t.isDataFlavorSupported(patternFlavor)) {
                    PatternRecord rec = (PatternRecord)t.getTransferData(patternFlavor);
                    pl.getModel().add(index, new PatternRecord(rec));
                    list.setSelectedIndex(index);
                    return true;
                }

                if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    String s = (String)t.getTransferData(DataFlavor.stringFlavor);

                    // allow for multi-line strings
                    String[] lines = s.stripTrailing().split("\n");

                    for (int i = lines.length - 1; i >= 0; --i) {
                        PatternRecord rec = new PatternRecord(lines[i],
                                                null, null, null, null, null, null);
                        pl.getModel().add(index, rec);
                    }
                    list.setSelectedIndex(index);
                    return true;
                }
            } catch (Exception e) {
                ErrorDialog.handleFatalException(e);
            }

            return false;
        }

        @Override
        protected void exportDone(JComponent c, Transferable data, int action) {
            if (action == TransferHandler.MOVE) {
                if (!(data instanceof PatternTransferable))
                    return;

                if (!pl.getModel().removeElement(((PatternTransferable)data).rec))
                    ErrorDialog.handleFatalException(
                            new JuggleExceptionInternal("PLP: exportDone()"));
            }

            draggingOut = false;
        }
    }

    class PatternTransferable implements Transferable {
        public PatternRecord rec;

        public PatternTransferable(PatternRecord pr) {
            rec = pr;
        }

        @Override
        public Object getTransferData(DataFlavor flavor) {
            if (flavor.equals(patternFlavor))
                return rec;

            if (flavor.equals(DataFlavor.stringFlavor)) {
                String s;
                if (rec.anim == null || rec.anim.equals(""))
                    s = rec.display;
                else
                    s = rec.anim;
                return s;
            }

            return null;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] {
                patternFlavor,
                DataFlavor.stringFlavor,
            };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return flavor.equals(patternFlavor) || flavor.equals(DataFlavor.stringFlavor);
        }
    }

    //-------------------------------------------------------------------------
    // Class to support rendering of list items
    //-------------------------------------------------------------------------

    class PatternCellRenderer extends JLabel implements ListCellRenderer<PatternRecord> {
        public Component getListCellRendererComponent(
                        JList<? extends PatternRecord> list,  // the list
                        PatternRecord value,     // value to display
                        int index,               // cell index
                        boolean isSelected,      // is the cell selected
                        boolean cellHasFocus)    // does the cell have focus
        {
            PatternRecord rec = value;

            setFont(rec.anim == null && rec.patnode == null ? font_nopattern : font_pattern);
            setText(rec.display.length() > 0 ? rec.display : " ");

            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }
            setEnabled(list.isEnabled());
            setOpaque(true);
            return this;
        }
    }
}
