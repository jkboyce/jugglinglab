// JMLPatternList.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.jml;

import java.io.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.ResourceBundle;
import javax.swing.DefaultListModel;

import jugglinglab.core.AnimationPrefs;
import jugglinglab.util.*;


// This class represents a JML pattern list. This is the data model; the
// visualization is in PatternListPanel and PatternListWindow.

public class JMLPatternList {
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    // whether to maintain a blank line at the end of every pattern list,
    // so that items can be inserted at the end
    public static final boolean BLANK_AT_END = true;

    public String version = JMLDefs.CURRENT_JML_VERSION;
    public String loadingversion = JMLDefs.CURRENT_JML_VERSION;

    protected String title;
    protected String info;
    protected DefaultListModel<PatternRecord> model;


    public JMLPatternList() {
        model = new DefaultListModel<PatternRecord>();
        clearModel();
    }

    public JMLPatternList(JMLNode root) throws JuggleExceptionUser {
        this();
        readJML(root);
    }

    //-------------------------------------------------------------------------
    // Methods to define the pattern list
    //-------------------------------------------------------------------------

    public DefaultListModel<PatternRecord> getModel() {
        return model;
    }

    public void clearModel() {
        model.clear();

        if (BLANK_AT_END)
            model.addElement(new PatternRecord(" ", null, null, null, null, null, null));
    }

    public int size() {
        return (BLANK_AT_END ? model.size() - 1 : model.size());
    }

    // Add a pattern at a specific row in the list.
    // When `row` < 0, add it at the end.
    public void addLine(int row, String display, String animprefs, String notation,
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

    public PatternRecord getLine(int row) {
        return (row >=0 && row < size() ? model.get(row) : null);
    }

    public JMLPattern getPatternForLine(int row) throws JuggleExceptionUser,
                                        JuggleExceptionInternal {
        PatternRecord rec = model.get(row);
        if (rec.notation == null)
            return null;

        JMLPattern pat = null;
        if (rec.notation.equalsIgnoreCase("jml") && rec.patnode != null) {
            pat = new JMLPattern(rec.patnode, loadingversion);
        } else if (rec.anim != null) {
            pat = JMLPattern.fromBasePattern(rec.notation, rec.anim);

            if (rec.info != null)
                pat.setInfo(rec.info);
            if (rec.tags != null) {
                for (String tag : rec.tags)
                    pat.addTag(tag);
            }
        } else
            return null;

        return pat;
    }

    public AnimationPrefs getAnimationPrefsForLine(int row) throws JuggleExceptionUser {
        PatternRecord rec = model.get(row);
        if (rec.animprefs == null)
            return null;

        AnimationPrefs ap = new AnimationPrefs();
        ParameterList params = new ParameterList(rec.animprefs);
        ap.fromParameters(params);
        params.errorIfParametersLeft();
        return ap;
    }

    public void setTitle(String t) {
        // by convention we don't allow title to be zero-length string "", but
        // use null instead
        title = ((t == null || t.length() == 0) ? null : t.strip());
    }

    public String getTitle() {
        return title;
    }

    //-------------------------------------------------------------------------
    // Reader/writer methods
    //-------------------------------------------------------------------------

    public void readJML(JMLNode root) throws JuggleExceptionUser {
        if (!root.getNodeType().equalsIgnoreCase("jml"))
            throw new JuggleExceptionUser(errorstrings.getString("Error_missing_JML_tag"));

        String vers = root.getAttributes().getAttribute("version");
        if (vers != null) {
            if (JLFunc.compareVersions(vers, JMLDefs.CURRENT_JML_VERSION) > 0)
                throw new JuggleExceptionUser(errorstrings.getString("Error_JML_version"));
            loadingversion = vers;
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

                addLine(-1, display, animprefs, notation, anim, patnode, infonode);
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

    //-------------------------------------------------------------------------
    // Record to encapsulate the data for a single line
    //-------------------------------------------------------------------------

    public static class PatternRecord {
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
}
