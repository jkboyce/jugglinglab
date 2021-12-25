// PatternListPanel.java
//
// Copyright 2002-2021 Jack Boyce and the Juggling Lab contributors

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
    protected JList<PatternRecord> list;
    protected DefaultListModel<PatternRecord> model;
    protected String loadingversion = JMLDefs.CURRENT_JML_VERSION;

    // for mouse/popup menu handling
    protected boolean willLaunchAnimation;
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
                if (me.isPopupTrigger()) {
                    int row = list.locationToIndex(me.getPoint());
                    list.setSelectedIndex(row);

                    willLaunchAnimation = false;
                    makePopupMenu().show(list, me.getX(), me.getY());
                    return;
                }

                if (BLANK_AT_END && list.getSelectedIndex() == model.size() - 1) {
                    list.clearSelection();
                    willLaunchAnimation = false;
                    return;
                }

                // otherwise it's a normal (left) mouse click on a regular
                // list item --> allow animation to launch on mouse release
                willLaunchAnimation = true;
            }

            @Override
            public void mouseReleased(final MouseEvent me) {
                if (willLaunchAnimation) {
                    launchAnimation();
                    willLaunchAnimation = false;
                }
            }
        });

        setLayout(new BorderLayout());
        add(pane, BorderLayout.CENTER);
    }

    // Try to launch an animation window for the currently-selected item in the
    // list. If there is no pattern associated with the line, do nothing.
    protected void launchAnimation() {
        PatternWindow jaw2 = null;
        try {
            PatternRecord rec = model.get(list.getSelectedIndex());

            if (rec.notation == null)
                return;

            JMLPattern pat = null;
            Pattern p = null;

            if (rec.notation.equalsIgnoreCase("jml") && rec.pattern != null) {
                pat = new JMLPattern(rec.pattern, PatternListPanel.this.loadingversion);
            } else if (rec.anim != null) {
                p = Pattern.newPattern(rec.notation).fromString(rec.anim);

                /*
                // check if we want to add rec.display as the pattern's title
                // so it won't be lost when the pattern is recompiled in Pattern View
                ParameterList pl = new ParameterList(p.toString());
                String pattern = pl.getParameter("pattern");
                String title = pl.getParameter("title");
                if (title == null && pattern != null && rec.display != null &&
                            !pattern.equals(rec.display.trim())) {
                    pl.addParameter("title", rec.display.trim());
                    p = Pattern.newPattern(rec.notation).fromParameters(pl);
                }
                */

                pat = JMLPattern.fromBasePattern(rec.notation, p.toString());
            } else
                return;

            //pat.setTitle(rec.display);
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
                jaw2 = new PatternWindow(pat.getTitle(), pat, ap);
        } catch (JuggleExceptionUser je) {
            if (jaw2 != null)
                jaw2.dispose();
            new ErrorDialog(PatternListPanel.this, je.getMessage());
        } catch (Exception e) {
            if (jaw2 != null)
                jaw2.dispose();
            ErrorDialog.handleFatalException(e);
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
            if (list.getSelectedIndex() < 0)
                return null;

            draggingOut = true;
            PatternRecord rec = model.get(list.getSelectedIndex());
            return new PatternTransferable(rec);
        }

        @Override
        public boolean canImport(TransferHandler.TransferSupport info) {
            // support only drop (not clipboard paste)
            if (!info.isDrop())
                return false;

            if (draggingOut)
                info.setDropAction(MOVE);
            else
                info.setDropAction(COPY);

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
                index = model.size() - 1;

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
                    while (s.endsWith("\n"))
                        s = s.substring(0, s.length() - 1);
                    String[] lines = s.split("\n");

                    for (int i = lines.length - 1; i >= 0; --i) {
                        PatternRecord rec = new PatternRecord(lines[i], null, null, null, null);
                        model.add(index, rec);
                    }
                    list.setSelectedIndex(index);
                    return true;
                }
            } catch (Exception e) {}

            return false;
        }

        @Override
        protected void exportDone(JComponent c, Transferable data, int action) {
            if (action == TransferHandler.MOVE) {
                if (!(data instanceof PatternTransferable))
                    return;

                if (!model.removeElement(((PatternTransferable)data).rec))
                    ErrorDialog.handleFatalException(new JuggleExceptionInternal("PLP: exportDone()"));
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

    protected static final String[] popupItems = new String[]
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
    protected static final String[] popupCommands = new String[]
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

        return popup;
    }

    // Open a dialog to allow the user to insert a line of text
    protected void insertText(int row) {
        makeDialog(guistrings.getString("PLDIALOG_Insert_text"), "");

        okbutton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String newline = tf.getText();
                dialog.dispose();

                PatternRecord rec = new PatternRecord(newline, null, null, null, null);

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

    // Insert the pattern in the given PatternWindow into the given row
    protected void addPattern(int row, PatternWindow pw) {
        JMLPattern pat = pw.getPattern();
        PatternRecord rec = null;

        String display = pw.getTitle();
        String animprefs = pw.getAnimationPrefs().toString();
        if (animprefs.length() == 0)
            animprefs = null;

        if (pat.isBasePatternEdited()) {
            // add as JML pattern
            String notation = "jml";
            String anim = null;
            JMLNode pattern = null;

            try {
                JMLParser parser = new JMLParser();
                parser.parse(new StringReader(pat.toString()));
                pattern = parser.getTree().findNode("pattern");
            } catch (Exception e) {
                // any error here cannot be user error since pattern is
                // already animating in another window
                ErrorDialog.handleFatalException(e);
            }

            rec = new PatternRecord(display, animprefs, notation, anim, pattern);
        } else {
            // add as base pattern
            String notation = pat.getBasePatternNotation();
            String anim = pat.getBasePatternConfig();
            JMLNode pattern = null;

            rec = new PatternRecord(display, animprefs, notation, anim, pattern);
        }

        if (row < 0) {
            model.addElement(rec);  // adds at end
            list.setSelectedIndex(model.size() - 1);
        } else {
            model.add(row, rec);
            list.setSelectedIndex(row);
        }
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
        return dialog;
    }

    //-------------------------------------------------------------------------

    public void setTargetView(View target) {
        animtarget = target;
    }

    public void addPattern(String display, String animprefs, String notation, String anim, JMLNode pat) {
        // display = display.trim();
        if (notation != null)
            notation = notation.trim();
        if (animprefs != null)
            animprefs = animprefs.trim();
        if (anim != null)
            anim = anim.trim();

        PatternRecord rec = new PatternRecord(display, animprefs, notation, anim, pat);

        if (BLANK_AT_END)
            model.add(model.size() - 1, rec);
        else
            model.addElement(rec);
    }

    public void clearList() {
        model.clear();

        if (BLANK_AT_END) {
            PatternRecord rec = new PatternRecord(" ", null, null, null, null);
            model.addElement(rec);
        }
    }

    public void setTitle(String t) {
        // by convention we don't allow title to be zero-length string "", but
        // use null instead
        title = ((t == null || t.length() == 0) ? null : t.trim());
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
                title = child.getNodeValue().trim();
            } else if (child.getNodeType().equalsIgnoreCase("line")) {
                linenumber++;
                JMLAttributes attr = child.getAttributes();

                String display = attr.getAttribute("display");
                if ((display==null) || display.equals(""))
                    display = " ";      // JList won't display empty strings
                String animprefs = attr.getAttribute("animprefs");
                String notation = attr.getAttribute("notation");
                String anim = null;
                JMLNode pattern = null;

                if (notation != null) {
                    if (notation.equalsIgnoreCase("JML")) {
                        for (int j = 0; j < child.getNumberOfChildren(); j++) {
                            JMLNode subchild = child.getChildNode(j);
                            if (subchild.getNodeType().equalsIgnoreCase("pattern")) {
                                pattern = subchild;
                                break;
                            }
                        }
                        if (pattern == null) {
                            String template = errorstrings.getString("Error_missing_pattern");
                            Object[] arguments = { Integer.valueOf(linenumber) };
                            throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                        }
                    } else {
                        anim = child.getNodeValue().trim();
                    }
                }

                addPattern(display, animprefs, notation, anim, pattern);
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
        if (title != null)
            write.println("<title>" + JMLNode.xmlescape(title) + "</title>");

        for (int i = 0; i < (BLANK_AT_END ? model.size() - 1 : model.size()); ++i) {
            PatternRecord rec = model.get(i);
            String line = "<line display=\"" + JMLNode.xmlescape(rec.display) + "\"";

            if (rec.notation != null)
                line += " notation=\"" + JMLNode.xmlescape(rec.notation.toLowerCase()) + "\"";
            if (rec.animprefs != null)
                line += " animprefs=\"" + JMLNode.xmlescape(rec.animprefs) + "\"";
            line += ">";
            write.println(line);

            if (rec.notation != null && rec.notation.equalsIgnoreCase("jml") && rec.pattern != null)
                rec.pattern.writeNode(write, 0);
            else if (rec.anim != null)
                write.println(JMLNode.xmlescape(rec.anim));

            write.println("</line>");
        }

        write.println("</patternlist>");
        write.println("</jml>");
        for (int i = 0; i < JMLDefs.jmlsuffix.length; i++)
            write.println(JMLDefs.jmlsuffix[i]);
        write.flush();
    }

    public void writeText(Writer wr) throws IOException {
        PrintWriter write = new PrintWriter(wr);

        for (int i = 0; i < model.size(); i++) {
            PatternRecord rec = model.get(i);
            write.println(rec.display);
        }
        write.flush();
    }


    class PatternRecord {
        public String display;
        public String animprefs;
        public String notation;
        public String anim;
        public JMLNode pattern;  // if the pattern is in JML notation

        public PatternRecord(String dis, String ap, String not, String ani, JMLNode pat) {
            display = dis;
            animprefs = ap;
            notation = not;
            anim = ani;
            pattern = pat;
        }

        public PatternRecord(PatternRecord pr) {
            display = pr.display;
            animprefs = pr.animprefs;
            notation = pr.notation;
            anim = pr.anim;
            pattern = pr.pattern;
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

            setFont((rec.anim == null && rec.pattern == null) ? font_nopattern : font_pattern);
            setText(rec.display);

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
