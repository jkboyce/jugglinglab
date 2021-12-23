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


public class PatternListPanel extends JPanel implements ActionListener {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    static final Font font_nopattern = new Font("SanSerif", Font.BOLD | Font.ITALIC, 14);
    static final Font font_pattern = new Font("Monospaced", Font.PLAIN, 14);
    static final Font font_pattern_popup = new Font("Monospaced", Font.ITALIC, 14);

    protected View animtarget;
    protected String title;
    protected JList<PatternRecord> list;
    protected DefaultListModel<PatternRecord> model;
    protected String loadingversion;

    protected boolean willLaunchAnimation;
    protected ArrayList<PatternWindow> popupPatterns;


    public PatternListPanel() {
        makePanel();
        setOpaque(false);
    }

    public PatternListPanel(View target) {
        makePanel();
        setOpaque(false);
        setTargetView(target);
    }

    protected void makePanel() {
        model = new DefaultListModel<PatternRecord>();
        list = new JList<PatternRecord>(model);
        list.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new PatternCellRenderer());

        list.setDragEnabled(true);

        list.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }

            @Override
            public Transferable createTransferable(JComponent c) {
                PatternRecord rec = model.get(list.getSelectedIndex());
                String s;
                if (rec.anim == null || rec.anim.equals(""))
                    s = rec.display;
                else
                    s = rec.anim;
                //System.out.println("copying data '" + s + "'");
                return new StringSelection(s);
            }
        });

        list.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent lse) {
                //System.out.println("list value changed");
                willLaunchAnimation = true;
            }
        });

        JScrollPane pane = new JScrollPane(list);

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(final MouseEvent me) {
                //System.out.println("mouse pressed");
                if (me.isPopupTrigger()) {
                    //System.out.println("got a popup trigger");

                    int row = list.locationToIndex(me.getPoint());
                    //System.out.println("selected row = " + row);
                    list.setSelectedIndex(row);
                    //System.out.println("list selected row = " + list.getSelectedIndex());

                    willLaunchAnimation = false;
                    makePopupMenu().show(PatternListPanel.this, me.getX(), me.getY());
                }
            }

            @Override
            public void mouseReleased(final MouseEvent me) {
                //System.out.println("mouse released");
                if (willLaunchAnimation) {
                    launchAnimation();
                    willLaunchAnimation = false;
                }
            }
        });

        setLayout(new BorderLayout());
        add(pane, BorderLayout.CENTER);
    }

    protected static final String[] popupItems = new String[]
        {
            "Insert Text...",
            null,
            "Insert Pattern",
            null,
            "Change Title...",
            "Change Display Text...",
            null,
            "Remove Line",
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

        for (int i = 0; i < popupItems.length; ++i) {
            String name = popupItems[i];

            if (name == null) {
                popup.addSeparator();
                continue;
            }

            JMenuItem item = new JMenuItem(name /*guistrings.getString(name.replace(' ', '_'))*/);
            item.setActionCommand(popupCommands[i]);
            item.addActionListener(this);

            if ((popupCommands[i].equals("displaytext") || popupCommands[i].equals("remove")) && row < 0)
                item.setEnabled(false);
            else if (popupCommands[i].equals("insertpattern") && popupPatterns.size() == 0)
                item.setEnabled(false);

            popup.add(item);

            if (popupCommands[i].equals("insertpattern")) {
                int patnum = 0;
                for (PatternWindow pw : popupPatterns) {
                    JMenuItem pitem = new JMenuItem("   " + pw.getTitle());
                    pitem.setActionCommand("pat" + patnum);
                    pitem.addActionListener(this);
                    pitem.setFont(font_pattern_popup);
                    popup.add(pitem);
                    ++patnum;
                }
            }
        }

        popup.setBorder(new BevelBorder(BevelBorder.RAISED));

        /*
        popup.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {}
            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                //System.out.println("popup becoming invisible");
            }
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                //System.out.println("popup becoming visible");
                //willLaunchAnimation = false;
            }
        });*/

        return popup;
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        String command = ae.getActionCommand();
        System.out.println(command);
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

                pat = JMLPattern.fromBasePattern(rec.notation, p.toString());
            } else
                return;

            pat.setTitle(rec.display);
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

        model.addElement(rec);
    }

    public void clearList() {
        model.clear();
    }

    public void setTitle(String t) {
        title = t;
    }

    public String getTitle() {
        return title;
    }

    public void readJML(JMLNode root) throws JuggleExceptionUser {
        if (!root.getNodeType().equalsIgnoreCase("jml"))
            throw new JuggleExceptionUser(errorstrings.getString("Error_missing_JML_tag"));

        loadingversion = root.getAttributes().getAttribute("version");
        if (loadingversion == null)
            loadingversion = JMLDefs.CURRENT_JML_VERSION;
        else if (JLFunc.compareVersions(loadingversion, JMLDefs.CURRENT_JML_VERSION) > 0)
            throw new JuggleExceptionUser(errorstrings.getString("Error_JML_version"));

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
        String vers = this.loadingversion;
        if (vers == null)
            vers = JMLDefs.CURRENT_JML_VERSION;
        write.println("<jml version=\""+vers+"\">");
        write.println("<patternlist>");
        write.println("<title>" + JMLNode.xmlescape(this.title) + "</title>");

        for (int i = 0; i < model.size(); i++) {
            PatternRecord rec = model.get(i);
            String line = "<line display=\"" + JMLNode.xmlescape(rec.display) + "\"";

            if (rec.notation != null)
                line += " notation=\"" + JMLNode.xmlescape(rec.notation.toLowerCase()) + "\"";
            if (rec.animprefs != null)
                line += " animprefs=\"" + JMLNode.xmlescape(rec.animprefs) + "\"";
            line += ">";
            write.println(line);

            if ((rec.notation != null) && rec.notation.equalsIgnoreCase("JML") && rec.pattern != null) {
                rec.pattern.writeNode(write, 0);
            } else if (rec.anim != null) {
                write.println(JMLNode.xmlescape(rec.anim));
            }

            write.println("</line>");
        }
        write.println("</patternlist>");
        write.println("</jml>");
        for (int i = 0; i < JMLDefs.jmlsuffix.length; i++)
            write.println(JMLDefs.jmlsuffix[i]);
        write.flush();
        //write.close();
    }

    public void writeText(Writer wr) throws IOException {
        PrintWriter write = new PrintWriter(wr);

        for (int i = 0; i < model.size(); i++) {
            PatternRecord rec = model.get(i);
            write.println(rec.display);
        }
        write.flush();
        //write.close();
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
