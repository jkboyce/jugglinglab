// PatternListPanel.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.core;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.ResourceBundle;
import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.event.*;

import jugglinglab.jml.*;
import jugglinglab.notation.Pattern;
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

    protected JFrame parent;
    protected View animtarget;
    protected String version = JMLDefs.CURRENT_JML_VERSION;
    protected String title;
    protected String info;
    protected JList<PatternRecord> list;
    protected DefaultListModel<PatternRecord> model;
    protected String loadingversion = JMLDefs.CURRENT_JML_VERSION;

    // for mouse/popup menu handling
    protected boolean didPopup;
    protected ArrayList<PatternWindow> popupPatterns;
    protected JDialog dialog;
    protected JTextField tf;
    protected JButton okbutton;

    // for drag and drop operations
    protected boolean draggingOut;

    // whether to include a blank line at the end of every pattern list,
    // so that items can be inserted at the end
    protected static final boolean BLANK_AT_END = true;


    protected PatternListPanel() {
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

    protected void makePanel() {
        model = new DefaultListModel<PatternRecord>();
        list = new JList<PatternRecord>(model);
        list.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new PatternCellRenderer());
        clearList();

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
        PatternWindow pw = null;

        try {
            int row = list.getSelectedIndex();
            if (row < 0 || (BLANK_AT_END && row == model.size() - 1))
                return;

            PatternRecord rec = model.get(row);
            if (rec.notation == null)
                return;

            JMLPattern pat = null;
            Pattern p = null;

            if (rec.notation.equalsIgnoreCase("jml") && rec.patnode != null) {
                pat = new JMLPattern(rec.patnode, PatternListPanel.this.loadingversion);
            } else if (rec.anim != null) {
                pat = JMLPattern.fromBasePattern(rec.notation, rec.anim);

                if (rec.info != null)
                    pat.setInfo(rec.info);
                if (rec.tags != null) {
                    for (String tag : rec.tags)
                        pat.addTag(tag);
                }
            } else
                return;

            pat.layoutPattern();

            if (PatternWindow.bringToFront(pat.getHashCode()))
                return;

            AnimationPrefs ap = new AnimationPrefs();
            if (rec.animprefs != null) {
                ParameterList pl = new ParameterList(rec.animprefs);
                ap.fromParameters(pl);
                pl.errorIfParametersLeft();
            }

            if (animtarget != null)
                animtarget.restartView(pat, ap);
            else
                pw = new PatternWindow(pat.getTitle(), pat, ap);
        } catch (JuggleExceptionUser jeu) {
            if (pw != null)
                pw.dispose();
            new ErrorDialog(PatternListPanel.this, jeu.getMessage());
        } catch (JuggleExceptionInternal jei) {
            if (pw != null)
                pw.dispose();
            ErrorDialog.handleFatalException(jei);
        }
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
            if (row < 0 || (BLANK_AT_END && row == model.size() - 1))
                return null;

            draggingOut = true;
            PatternRecord rec = model.get(row);
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
                index = (BLANK_AT_END ? model.size() - 1 : model.size());

            // Get the record that is being dropped
            Transferable t = info.getTransferable();

            try {
                if (t.isDataFlavorSupported(patternFlavor)) {
                    PatternRecord rec = (PatternRecord)t.getTransferData(patternFlavor);
                    model.add(index, new PatternRecord(rec));
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
                        model.add(index, rec);
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

                if (!model.removeElement(((PatternTransferable)data).rec))
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
                    model.remove(row);
                } else {
                    // adding a pattern
                    int patnum = Integer.parseInt(command.substring(3, command.length()));
                    PatternWindow pw = popupPatterns.get(patnum);
                    addPattern(row, pw);
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
                        BLANK_AT_END && row == model.size() - 1)
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
    protected void addPattern(int row, PatternWindow pw) {
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

        addPattern(row, display, animprefs, notation, anim, patnode, infonode);

        if (row < 0) {
            if (BLANK_AT_END)
                list.setSelectedIndex(model.size() - 2);
            else
                list.setSelectedIndex(model.size() - 1);
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

                PatternRecord rec = new PatternRecord(display, null, null, null, null, null, null);

                if (row < 0) {
                    model.addElement(rec);  // adds at end
                    list.setSelectedIndex(model.size() - 1);
                } else {
                    model.add(row, rec);
                    list.setSelectedIndex(row);
                }
            }
        });

        dialog.setVisible(true);
    }

    // Open a dialog to allow the user to change the pattern list's title
    protected void changeTitle() {
        makeDialog(guistrings.getString("PLDIALOG_Change_title"), getTitle());

        okbutton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setTitle(tf.getText());
                dialog.dispose();

                if (parent != null)
                    parent.setTitle(getTitle());
            }
        });

        dialog.setVisible(true);
        list.clearSelection();
    }

    // Open a dialog to allow the user to change the display text of a line
    protected void changeDisplayText(int row) {
        PatternRecord rec = model.get(row);
        makeDialog(guistrings.getString("PLDIALOG_Change_display_text"), rec.display);

        okbutton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                rec.display = tf.getText();
                dialog.dispose();

                model.set(row, rec);
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
        if (BLANK_AT_END && list.getSelectedIndex() == model.size() - 1)
            list.clearSelection();

        popupPatterns = null;
        dialog = null;
        tf = null;
        okbutton = null;
    }

    //-------------------------------------------------------------------------

    public void setTargetView(View target) {
        animtarget = target;
    }

    // Used by GeneratorTarget
    public void addPattern(String display, String animprefs, String notation, String anim) {
        addPattern(-1, display, animprefs, notation, anim, null, null);
    }

    // Add a pattern at a specific row in the list.
    // When `row` < 0, add it at the end.
    protected void addPattern(int row, String display, String animprefs, String notation,
                        String anim, JMLNode patnode, JMLNode infonode) {
        if (display == null)
            display = "";
        if (animprefs != null)
            animprefs = animprefs.strip();
        if (notation != null)
            notation = notation.strip();
        if (anim != null)
            anim = anim.strip();

        String info = null;
        ArrayList<String> tags = null;

        if (infonode != null) {
            info = infonode.getNodeValue();
            info = (info != null && info.strip().length() > 0) ? info.strip() : null;

            String tagstr = infonode.getAttributes().getAttribute("tags");
            if (tagstr != null) {
                tags = new ArrayList<String>();

                for (String t : tagstr.split(",")) {
                    t = t.strip();
                    boolean is_new = true;

                    for (String t2 : tags) {
                        if (t2.equalsIgnoreCase(t))
                            is_new = false;
                    }

                    if (is_new)
                        tags.add(t);
                }
            }
        }

        PatternRecord rec = new PatternRecord(display, animprefs, notation, anim, patnode, info, tags);

        if (row < 0) {
            if (BLANK_AT_END)
                model.add(model.size() - 1, rec);
            else
                model.addElement(rec);  // adds at end
        } else
            model.add(row, rec);
    }

    public void clearList() {
        model.clear();

        if (BLANK_AT_END)
            model.addElement(new PatternRecord(" ", null, null, null, null, null, null));
    }

    public void setTitle(String t) {
        // by convention we don't allow title to be zero-length string "", but
        // use null instead
        title = ((t == null || t.length() == 0) ? null : t.strip());
    }

    public String getTitle() {
        return title;
    }

    public void readJML(JMLNode root) throws JuggleExceptionUser {
        if (!root.getNodeType().equalsIgnoreCase("jml"))
            throw new JuggleExceptionUser(errorstrings.getString("Error_missing_JML_tag"));

        String version = root.getAttributes().getAttribute("version");
        if (version != null) {
            if (JLFunc.compareVersions(version, JMLDefs.CURRENT_JML_VERSION) > 0)
                throw new JuggleExceptionUser(errorstrings.getString("Error_JML_version"));
            loadingversion = version;
        }

        JMLNode listnode = root.getChildNode(0);
        if (!listnode.getNodeType().equalsIgnoreCase("patternlist"))
            throw new JuggleExceptionUser(errorstrings.getString("Error_missing_patternlist_tag"));

        int linenumber = 0;

        for (int i = 0; i < listnode.getNumberOfChildren(); i++) {
            JMLNode child = listnode.getChildNode(i);
            if (child.getNodeType().equalsIgnoreCase("title")) {
                title = child.getNodeValue().strip();
            } else if (child.getNodeType().equalsIgnoreCase("info")) {
                info = child.getNodeValue().strip();
            } else if (child.getNodeType().equalsIgnoreCase("line")) {
                linenumber++;
                JMLAttributes attr = child.getAttributes();

                String display = attr.getAttribute("display");
                String animprefs = attr.getAttribute("animprefs");
                String notation = attr.getAttribute("notation");
                String anim = null;
                JMLNode patnode = null;
                JMLNode infonode = null;

                if (notation != null) {
                    if (notation.equalsIgnoreCase("jml")) {
                        patnode = child.findNode("pattern");
                        if (patnode == null) {
                            String template = errorstrings.getString("Error_missing_pattern");
                            Object[] arguments = { Integer.valueOf(linenumber) };
                            throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                        }
                        infonode = patnode.findNode("info");
                    } else {
                        anim = child.getNodeValue().strip();
                        infonode = child.findNode("info");
                    }
                }

                addPattern(-1, display, animprefs, notation, anim, patnode, infonode);
            } else
                throw new JuggleExceptionUser(errorstrings.getString("Error_illegal_tag"));
        }
    }

    public void writeJML(Writer wr) throws IOException {
        PrintWriter write = new PrintWriter(wr);
        for (int i = 0; i < JMLDefs.jmlprefix.length; i++)
            write.println(JMLDefs.jmlprefix[i]);

        write.println("<jml version=\"" + JMLNode.xmlescape(version) + "\">");
        write.println("<patternlist>");
        if (title != null && title.length() > 0)
            write.println("<title>" + JMLNode.xmlescape(title) + "</title>");
        if (info != null && info.length() > 0)
            write.println("<info>" + JMLNode.xmlescape(info) + "</info>");

        boolean empty = (model.size() == (BLANK_AT_END ? 1 : 0));
        if (!empty)
            write.println();

        boolean previousLineWasAnimation = false;

        for (int i = 0; i < (BLANK_AT_END ? model.size() - 1 : model.size()); ++i) {
            PatternRecord rec = model.get(i);
            String line = "<line display=\"" + JMLNode.xmlescape(rec.display.stripTrailing()) + "\"";
            boolean hasAnimation = false;

            if (rec.notation != null) {
                line += " notation=\"" + JMLNode.xmlescape(rec.notation.toLowerCase()) + "\"";
                hasAnimation = true;
            }
            if (rec.animprefs != null) {
                line += " animprefs=\"" + JMLNode.xmlescape(rec.animprefs) + "\"";
                hasAnimation = true;
            }

            if (hasAnimation) {
                line += ">";
                if (i > 0)
                    write.println();
                write.println(line);

                if (rec.notation != null && rec.notation.equalsIgnoreCase("jml") && rec.patnode != null)
                    rec.patnode.writeNode(write, 0);
                else if (rec.anim != null) {
                    write.println(JMLNode.xmlescape(rec.anim));
                    if (rec.info != null || (rec.tags != null && rec.tags.size() > 0)) {
                        String tagstr = (rec.tags != null ? String.join(",", rec.tags) : "");

                        if (rec.info != null) {
                            if (tagstr.length() == 0)
                                write.println("<info>" + JMLNode.xmlescape(rec.info) + "</info>");
                            else
                                write.println("<info tags=\"" + JMLNode.xmlescape(tagstr) + "\">" +
                                            JMLNode.xmlescape(rec.info) + "</info>");
                        } else {
                            write.println("<info tags=\"" + JMLNode.xmlescape(tagstr) + "\"/>");
                        }
                    }
                }

                write.println("</line>");
            } else {
                line += "/>";
                if (previousLineWasAnimation && i > 0)
                    write.println();
                write.println(line);
            }

            previousLineWasAnimation = hasAnimation;
        }

        if (!empty)
            write.println();

        write.println("</patternlist>");
        write.println("</jml>");
        for (int i = 0; i < JMLDefs.jmlsuffix.length; i++)
            write.println(JMLDefs.jmlsuffix[i]);
        write.flush();
    }

    public void writeText(Writer wr) throws IOException {
        PrintWriter write = new PrintWriter(wr);

        for (int i = 0; i < (BLANK_AT_END ? model.size() - 1 : model.size()); i++) {
            PatternRecord rec = model.get(i);
            write.println(rec.display);
        }
        write.flush();
    }


    class PatternRecord {
        public String display;
        public String animprefs;
        public String notation;
        public String anim;  // if pattern is not in JML notation
        public JMLNode patnode;  // if pattern is in JML
        public String info;
        public ArrayList<String> tags;

        public PatternRecord(String dis, String ap, String not, String ani, JMLNode pat,
                             String inf, ArrayList<String> t) {
            display = dis;
            animprefs = ap;
            notation = not;
            anim = ani;
            patnode = pat;
            info = inf;
            tags = t;
        }

        public PatternRecord(PatternRecord pr) {
            display = pr.display;
            animprefs = pr.animprefs;
            notation = pr.notation;
            anim = pr.anim;
            patnode = pr.patnode;
            info = pr.info;

            if (pr.tags != null) {
                tags = new ArrayList<String>();
                for (String tag : pr.tags)
                    tags.add(tag);
            } else
                tags = null;
        }
    }


    class PatternCellRenderer extends JLabel implements ListCellRenderer<PatternRecord> {
        public Component getListCellRendererComponent(
                        JList<? extends PatternRecord> list,  // the list
                        PatternRecord value,     // value to display
                        int index,               // cell index
                        boolean isSelected,      // is the cell selected
                        boolean cellHasFocus)    // does the cell have focus
        {
            PatternRecord rec = value;

            setFont((rec.anim == null && rec.patnode == null) ? font_nopattern : font_pattern);
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
