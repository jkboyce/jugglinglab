// View.java
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

package jugglinglab.view;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

import jugglinglab.core.*;
import jugglinglab.jml.*;
import jugglinglab.util.*;


public class View extends JPanel implements ActionListener {
    static ResourceBundle guistrings;
    static ResourceBundle errorstrings;
    static {
        guistrings = JLLocale.getBundle("GUIStrings");
        errorstrings = JLLocale.getBundle("ErrorStrings");
    }

	protected JFrame parent = null;
	protected Dimension animsize = null;
    protected AnimatorPrefs jc = null;
    protected JMLPattern pat = null;
    protected View subview = null;
	
	public View() {}
	
	public View(JFrame p, Dimension animsize) {
		setParent(p);
		this.animsize = animsize;
		this.jc = new AnimatorPrefs();
	}
	
	protected void setParent(JFrame p) {
		this.parent = p;
	}
	
    public void restartView() throws JuggleExceptionUser, JuggleExceptionInternal {
		if (subview != null)
			subview.restartView();
	}
    // In the following, a null argument means no update for that item
    public void restartView(JMLPattern p, AnimatorPrefs c) throws JuggleExceptionUser, JuggleExceptionInternal {
        if (p != null)
            this.pat = p;
        if (c != null)
            this.jc = c;
			
		if (subview != null)
			subview.restartView(p, c);
	}
	
    public Dimension getAnimatorSize() {
		if (subview != null)
			return subview.getAnimatorSize();
		return null;
	}
    public void dispose() {
		if (subview != null)
			subview.dispose();
		subview = null;
	}

    public JMLPattern getPattern() { return pat; }
    
    public boolean getPaused() {
		if (subview != null)
			return subview.getPaused();
		return false;
	}
    public void setPaused(boolean pause) {
		if (subview != null)
			subview.setPaused(pause);
	}
	

    protected static final String[] fileItems = new String[]
    { "Close", null, "Save JML As...", "Save Animated GIF As...", "Save HTML As..." };
    protected static final String[] fileCommands = new String[]
    { "close", null, "saveas", "savegifanim", "savehtml" };
    protected static final char[] fileShortcuts =
    { 'W', ' ', 'S', ' ', ' ' };

	public JMenu createFileMenu() {
        JMenu filemenu = new JMenu(guistrings.getString("File"));
        for (int i = 0; i < fileItems.length; i++) {
            if (fileItems[i] == null)
                filemenu.addSeparator();
            else {
				JMenuItem fileitem = new JMenuItem(guistrings.getString(fileItems[i].replace(' ', '_')));
				
                if (fileShortcuts[i] != ' ')
                    fileitem.setAccelerator(KeyStroke.getKeyStroke(fileShortcuts[i],
											Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));

                fileitem.setActionCommand(fileCommands[i]);
                fileitem.addActionListener(this);
                filemenu.add(fileitem);
            }
        }
		return filemenu;
	}


    protected static final String[] viewItems = new String[]
    { "Simple", "Visual editor", /*"Selection editor",*/ "JML editor", null, "Restart", "Animation Preferences..." };
    protected static final String[] viewCommands = new String[]
    { "simple", "edit", /*"selection",*/ "jml", null, "restart", "prefs" };
    protected static final char[] viewShortcuts =
    { '1', '2', '3', /*'4',*/ ' ', ' ', 'P' };
	
	public JMenu createViewMenu() {
        JMenu viewmenu = new JMenu(guistrings.getString("View"));
        ButtonGroup buttonGroup = new ButtonGroup();
		boolean addingviews = true;
        for (int i = 0; i < viewItems.length; i++) {
            if (viewItems[i] == null) {
                viewmenu.addSeparator();
				addingviews = false;
            } else if (addingviews) {
				JRadioButtonMenuItem viewitem = new JRadioButtonMenuItem(guistrings.getString(viewItems[i].replace(' ', '_')));

                if (viewShortcuts[i] != ' ')
                    viewitem.setAccelerator(KeyStroke.getKeyStroke(viewShortcuts[i],
											Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
				
                viewitem.setActionCommand(viewCommands[i]);
                viewitem.addActionListener(this);
                viewmenu.add(viewitem);
                buttonGroup.add(viewitem);
            } else {
				JMenuItem viewitem = new JMenuItem(guistrings.getString(viewItems[i].replace(' ', '_')));
				
                if (viewShortcuts[i] != ' ')
                    viewitem.setAccelerator(KeyStroke.getKeyStroke(viewShortcuts[i],
											Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));

                viewitem.setActionCommand(viewCommands[i]);
                viewitem.addActionListener(this);
                viewmenu.add(viewitem);
			}
        }
		// viewmenu.getItem(0).setSelected(true);  // start in Normal view
		
		return viewmenu;
	}
	
	
    public void actionPerformed(ActionEvent ae) {
        String command = ae.getActionCommand();

        try {
            if (command.equals("close"))
                doMenuCommand(FILE_CLOSE);
            else if (command.equals("saveas"))
                doMenuCommand(FILE_SAVE);
            else if (command.equals("savegifanim")) {
                if (jugglinglab.core.Constants.INCLUDE_GIF_SAVE)
                    doMenuCommand(FILE_GIFSAVE);
            }
            else if (command.equals("savehtml"))
                doMenuCommand(FILE_HTMLSAVE);
            else if (command.equals("restart"))
                doMenuCommand(VIEW_RESTART);
            else if (command.equals("prefs"))
                doMenuCommand(VIEW_ANIMPREFS);
            else if (command.equals("simple")) {
				if (getViewMode() != VIEW_SIMPLE) {
					setViewMode(VIEW_SIMPLE);
					if (parent != null)
						parent.pack();
					restartView(pat, jc);
				}
			}
            else if (command.equals("edit")) {
                if (getViewMode() != VIEW_EDIT) {
					setViewMode(VIEW_EDIT);
					if (parent != null)
						parent.pack();
					restartView(pat, jc);
				}
			}
            else if (command.equals("jml")) {
                if (getViewMode() != VIEW_JML) {
					setViewMode(VIEW_JML);
					if (parent != null)
						parent.pack();
					restartView(pat, jc);
				}
			}
            else if (command.equals("selection")) {
				if (getViewMode() != VIEW_SELECTION) {
					setViewMode(VIEW_SELECTION);
					if (parent != null)
						parent.pack();
					restartView(pat, jc);
				}
			}
        } catch (JuggleExceptionUser je) {
            new ErrorDialog(this, je.getMessage());
        } catch (Exception e) {
            jugglinglab.util.ErrorDialog.handleException(e);
        }
    }


    public static final int FILE_NONE = 0;
    public static final int	FILE_CLOSE = 1;
    public static final int FILE_SAVE = 2;
    public static final int FILE_GIFSAVE = 3;
    public static final int FILE_HTMLSAVE = 4;
    public static final int VIEW_RESTART = 5;
    public static final int	VIEW_ANIMPREFS = 6;
	
	public void doMenuCommand(int action) throws JuggleExceptionInternal {
        switch (action) {

            case FILE_NONE:
                break;

            case FILE_CLOSE:
                parent.dispose();
                break;

            case FILE_SAVE:
                if (getPattern().isValid()) {
                    try {
                        int option = PlatformSpecific.getPlatformSpecific().showSaveDialog(this);
                        if (option == JFileChooser.APPROVE_OPTION) {
                            if (PlatformSpecific.getPlatformSpecific().getSelectedFile() != null) {
                                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                                FileWriter fw = new FileWriter(PlatformSpecific.getPlatformSpecific().getSelectedFile());
                                PrintWriter write = new PrintWriter(fw);
                                getPattern().writeJML(fw, true);
                                fw.close();
                            }
                        }
                    } catch (FileNotFoundException fnfe) {
                        throw new JuggleExceptionInternal("FileNotFound: "+fnfe.getMessage());
                    } catch (IOException ioe) {
                        throw new JuggleExceptionInternal("IOException: "+ioe.getMessage());
                    } finally {
                        setCursor(Cursor.getDefaultCursor());
                    }
                } else {
                    new ErrorDialog(this, "Could not save: pattern is not valid");
                }
                break;

            case FILE_GIFSAVE:
                if (jugglinglab.core.Constants.INCLUDE_GIF_SAVE) {
                    if (getViewMode() == VIEW_SIMPLE) {
                        NormalView nv = (NormalView)subview;
                        Animator ja = nv.getAnimator();
                        if (!ja.isAnimInited())
                            break;
                        ja.writeGIFAnim();
                    }
                    else {
                        new LabelDialog(this, "Not available",
                                        "Switch to Simple view to save an animated GIF");
                    }
                }
                break;

            case FILE_HTMLSAVE:
                if (getPattern().isValid()) {
                    try {
                        int option = PlatformSpecific.getPlatformSpecific().showSaveDialog(this);
                        if (option == JFileChooser.APPROVE_OPTION) {
                            if (PlatformSpecific.getPlatformSpecific().getSelectedFile() != null) {
                                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                                FileWriter fw = new FileWriter(PlatformSpecific.getPlatformSpecific().getSelectedFile());
                                PrintWriter pw = new PrintWriter(fw);

                                String config = null;
                                Dimension dim = null;
                                if (this.getViewMode() == VIEW_EDIT) {
                                    config = "entry=none;view=edit";
                                    dim = getSize();
                                } else {
                                    config = "entry=none;view=simple";
                                    dim = getAnimatorSize();
                                }

                                String prefs = this.jc.toString();

                                int w = dim.width;
                                int h = dim.height;
                                JMLPattern pat = getPattern();

                                pw.println("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">");
                                pw.println("<html>");
                                pw.println("<head>");
                                pw.println("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">");
                                pw.println("<title>"+JMLNode.xmlescape(pat.getTitle())+"</title>");
                                pw.println("</head>");
                                pw.println("<body>");
                                pw.println("<applet archive=\"JugglingLab.jar\" code=\"JugglingLab\" width=\""+w+"\" height=\""+h+"\">");
                                pw.println("<param name=\"config\" value=\""+JMLNode.xmlescape(config)+"\">");
                                if (prefs.length() != 0)
                                    pw.println("<param name=\"animprefs\" value=\""+JMLNode.xmlescape(prefs)+"\">");
                                pw.println("<param name=\"notation\" value=\"jml\">");
                                pw.println("<param name=\"pattern\" value=\"");
                                pw.println(JMLNode.xmlescape(pat.toString()));
                                pw.println("\">");
                                pw.println("Java not available");
                                pw.println("</applet>");
                                pw.println("</body>");
                                pw.println("</html>");
                                pw.flush();

                                fw.close();
                            }
                        }
                    } catch (FileNotFoundException fnfe) {
                        throw new JuggleExceptionInternal("FileNotFound: "+fnfe.getMessage());
                    } catch (IOException ioe) {
                        throw new JuggleExceptionInternal("IOException: "+ioe.getMessage());
                    } finally {
                        setCursor(Cursor.getDefaultCursor());
                    }
                } else {
                    new ErrorDialog(this, "Could not save: pattern is not valid");
                }
                break;

            case VIEW_RESTART:
                try {
                    restartView();
                } catch (JuggleExceptionUser je) {
                    new ErrorDialog(this, je.getMessage());
                } catch (JuggleException je) {
                    throw new JuggleExceptionInternal(je.getMessage());
                }
                break;

            case VIEW_ANIMPREFS:
                AnimatorPrefsDialog japd = new AnimatorPrefsDialog(parent);

                //  AnimatorPrefs oldjc = this.getAnimator().getAnimatorPrefs();
                AnimatorPrefs newjc = japd.getPrefs(this.jc);
                if (newjc != jc)	 {	// user made change?
                    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    this.jc = newjc;
                    try {
                        restartView(null, newjc);
                    } catch (JuggleExceptionUser je) {
                        new ErrorDialog(this, je.getMessage());
                    } catch (JuggleException je) {
                        throw new JuggleExceptionInternal(je.getMessage());
                    } finally {
                        setCursor(Cursor.getDefaultCursor());
                    }
                }
				break;
        }

    }

	// these should be in the same order as in the View menu
    public static final int VIEW_NONE = 0;
    public static final int	VIEW_SIMPLE = 1;
    public static final int VIEW_EDIT = 2;
    public static final int VIEW_SELECTION = 3;
    public static final int VIEW_JML = 4;

    public void setViewMode(int mode) throws JuggleExceptionUser, JuggleExceptionInternal {
        View newview = null;
        boolean paused = false;
		
        if (subview != null) {
            animsize = subview.getAnimatorSize();
            pat = subview.getPattern();	// retrieve possibly edited pattern from old view
            paused = subview.getPaused();
			remove(subview);
        }

        switch (mode) {
            case VIEW_NONE:
                break;
            case VIEW_SIMPLE:
                newview = new NormalView(animsize);
                break;
            case VIEW_EDIT:
                newview = new EditView(animsize);
                break;
            case VIEW_JML:
                newview = new JMLView(animsize);
                break;
            case VIEW_SELECTION:
                newview = new SelectionView(animsize);
                break;
        }

		if (newview == null)
			return;
			
		GridBagLayout gb = new GridBagLayout();
		this.setLayout(gb);
		this.add(newview);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.LINE_START;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.gridwidth = gbc.gridheight = 1;
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.insets = new Insets(0,0,0,0);
		gbc.weightx = 1.0;
		gbc.weighty = 1.0;
		gb.setConstraints(newview, gbc);
		
		newview.setParent(parent);
        newview.setPaused(paused);
		if (subview != null)
			subview.dispose();

        subview = newview;
    }

	protected int getViewMode() {
		if (subview == null)
			return VIEW_NONE;
		if (subview instanceof NormalView)
			return VIEW_SIMPLE;
		if (subview instanceof EditView)
			return VIEW_EDIT;
		if (subview instanceof JMLView)
			return VIEW_JML;
		if (subview instanceof SelectionView)
			return VIEW_SELECTION;
		return VIEW_NONE;
	}
}
