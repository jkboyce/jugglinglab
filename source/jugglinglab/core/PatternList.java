// PatternList.java
//
// Copyright 2004 by Jack Boyce (jboyce@users.sourceforge.net) and others

/*
    This file is part of Juggling Lab.

    Juggling Lab is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    Juggling Lab is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Juggling Lab; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package jugglinglab.core;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.text.MessageFormat;

import jugglinglab.jml.*;
import jugglinglab.notation.*;
import jugglinglab.util.*;
import jugglinglab.view.*;


public class PatternList extends JPanel {
    static ResourceBundle guistrings;
    static ResourceBundle errorstrings;
    static {
        guistrings = JLLocale.getBundle("GUIStrings");
        errorstrings = JLLocale.getBundle("ErrorStrings");
    }
	final static Font font_nopattern = new Font("SanSerif", Font.BOLD | Font.ITALIC, 14);
	final static Font font_pattern = new Font("Monospaced", Font.PLAIN, 14);
	
	
	View animtarget = null;
    String title = null;
    JList list = null;
    DefaultListModel model = null;
    // JLabel status = null;


	public PatternList() {
		makePanel();
		this.setOpaque(false);
	}
	
    public PatternList(View target) {
        makePanel();
		this.setOpaque(false);
		setTargetView(target);
    }

    protected void makePanel() {
        model = new DefaultListModel();
        list = new JList(model);
        list.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new PatternCellRenderer());
        
        list.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent lse) {
                PatternWindow jaw2 = null;
                try {
                    if (lse.getValueIsAdjusting()) {
                        PatternRecord rec = (PatternRecord)model.elementAt(list.getSelectedIndex());

                        JMLPattern pat = null;

                        if (rec.notation != null && rec.notation.equalsIgnoreCase("JML") && rec.pattern != null) {
                            pat = new JMLPattern(rec.pattern, PatternList.this.loadingversion);
                        } else if (rec.notation != null && rec.anim != null) {
                            Notation not = Notation.getNotation(rec.notation);
                            pat = not.getJMLPattern(rec.anim);
                        } else
                            return;

                        if (pat != null) {
                            pat.setTitle(rec.display);

                            AnimatorPrefs ap = new AnimatorPrefs();
                            if (rec.animprefs != null)
                                ap.parseInput(rec.animprefs);

                            if (animtarget != null)
                                animtarget.restartView(pat, ap);
                            else
                                jaw2 = new PatternWindow(pat.getTitle(), pat, ap);
                        }
                    }
                } catch (JuggleExceptionUser je) {
                    if (jaw2 != null)
                        jaw2.dispose();
                    new ErrorDialog(PatternList.this, je.getMessage());
                } catch (Exception e) {
                    if (jaw2 != null)
                        jaw2.dispose();
                    jugglinglab.util.ErrorDialog.handleException(e);
                }
            }
        });

        JScrollPane pane = new JScrollPane(list);

        this.setLayout(new BorderLayout());
        this.add(pane, BorderLayout.CENTER);
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

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return this.title;
    }

    String loadingversion = null;

    public void readJML(JMLNode root) throws JuggleExceptionUser {
        if (!root.getNodeType().equalsIgnoreCase("jml"))
            throw new JuggleExceptionUser(errorstrings.getString("Error_missing_JML_tag"));
		
        loadingversion = root.getAttributes().getAttribute("version");
        if (loadingversion == null)
            loadingversion = "1.0";

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
                    display = " ";		// JList won't display empty strings
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
            vers = JMLDefs.default_JML_on_save;
        write.println("<jml version=\""+vers+"\">");
        write.println("<patternlist>");
        write.println("<title>" + JMLNode.xmlescape(this.title) + "</title>");

        for (int i = 0; i < model.size(); i++) {
            PatternRecord rec = (PatternRecord)model.elementAt(i);
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
        //		write.close();
    }

    public void writeText(Writer wr) throws IOException {
        PrintWriter write = new PrintWriter(wr);

        for (int i = 0; i < model.size(); i++) {
            PatternRecord rec = (PatternRecord)model.elementAt(i);
            write.println(rec.display);
        }
        write.flush();
        //		write.close();
    }


    class PatternRecord {
        public String  display;
        public String  animprefs;
        public String  notation;
        public String  anim;
		public JMLNode pattern;		// if the pattern is in JML notation

        public PatternRecord(String dis, String ap, String not, String ani, JMLNode pat) {
            this.display = dis;
            this.animprefs = ap;
            this.notation = not;
            this.anim = ani;
			this.pattern = pat;
        }
    }
	
	
	class PatternCellRenderer extends JLabel implements ListCellRenderer {
        public Component getListCellRendererComponent(
                        JList list,              // the list
                        Object value,            // value to display
                        int index,               // cell index
                        boolean isSelected,      // is the cell selected
                        boolean cellHasFocus)    // does the cell have focus
        {
			PatternRecord rec = (PatternRecord)value;

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
