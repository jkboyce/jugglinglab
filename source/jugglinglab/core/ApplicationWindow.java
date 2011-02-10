// ApplicationWindow.java
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

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import org.xml.sax.*;
import java.util.*;
import java.util.regex.*;
import java.text.MessageFormat;

import jugglinglab.jml.*;
import jugglinglab.notation.*;
import jugglinglab.util.*;


public class ApplicationWindow extends JFrame implements ActionListener, WindowListener {
    static ResourceBundle guistrings;
    static ResourceBundle errorstrings;
    static {
        guistrings = JLLocale.getBundle("GUIStrings");
        errorstrings = JLLocale.getBundle("ErrorStrings");
    }

	protected NotationGUI ng = null;
	protected boolean macos = false;

    public ApplicationWindow(String title) throws JuggleExceptionUser, JuggleExceptionInternal {
        super(title);
		ng = new NotationGUI(this);

		macos = PlatformSpecific.getPlatformSpecific().isMacOS();
		
        JMenuBar mb = new JMenuBar();
		JMenu filemenu = createFileMenu();
        mb.add(filemenu);
        JMenu notationmenu = ng.createNotationMenu();
		mb.add(notationmenu);
        JMenu helpmenu = ng.createHelpMenu(!macos);
		if (helpmenu != null)
			mb.add(helpmenu);
        setJMenuBar(mb);

        PlatformSpecific.getPlatformSpecific().registerParent(this);
        PlatformSpecific.getPlatformSpecific().setupPlatform();

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		ng.setDoubleBuffered(true);
		this.setBackground(new Color(0.9f, 0.9f, 0.9f));
        setContentPane(ng);
        ng.setNotation(Notation.NOTATION_SITESWAP);

		Locale loc = JLLocale.getLocale();
		this.applyComponentOrientation(ComponentOrientation.getOrientation(loc));
		
		notationmenu.getItem(Notation.NOTATION_SITESWAP-1).setSelected(true);
		pack();
		setResizable(false);
        setVisible(true);
		addWindowListener(this);
    }


    protected static final String[] fileItems = new String[]
    { "Open JML...", "Convert JML...", null, "Quit" };
    protected static final String[] fileCommands = new String[]
    { "open", "convert", null, "exit" };
    protected static final char[] fileShortcuts =
    { 'O', ' ', ' ', 'Q' };

	protected JMenu createFileMenu() {
        JMenu filemenu = new JMenu(guistrings.getString("File"));

        for (int i = 0; i < (macos ? fileItems.length-2 : fileItems.length); i++) {
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


	public void actionPerformed(ActionEvent ae) {
        String command = ae.getActionCommand();

        try {
			if (command.equals("open"))
                doMenuCommand(FILE_OPEN);
            /*else if (command.equals("newpat"))
                doFileMenuCommand(FILE_NEWPAT);
            else if (command.equals("newlist"))
                doFileMenuCommand(FILE_NEWLIST);
            else if (command.equals("juggleanim"))
                doMenuCommand(FILE_CONVERT);*/
            else if (command.equals("convert"))
                doMenuCommand(FILE_CONVERT);
            else if (command.equals("exit"))
                doMenuCommand(FILE_EXIT);
        } catch (Exception e) {
            ErrorDialog.handleException(e);
        }
    }


    public static final int FILE_NONE = 0;
    // public static final int FILE_NEWPAT = 1;
    // public static final int FILE_NEWLIST = 2;
    public static final int FILE_OPEN = 3;
    // public static final int FILE_JUGGLEANIM = 4;
	public static final int FILE_CONVERT = 5;
    public static final int	FILE_EXIT = 6;

    
    public void doMenuCommand(int action) throws JuggleExceptionInternal {
        switch (action) {

            case FILE_NONE:
                break;

			/*
            case FILE_NEWPAT:
            {
                PatternWindow jaw2 = null;

                try {
                    siteswapNotation sn = new siteswapNotation();

                    JMLPattern pat = sn.getJMLPattern("pattern=5");
                    jaw2 = new PatternWindow(pat.getTitle(), pat, new AnimatorPrefs());
                } catch (JuggleExceptionUser je) {
                    if (jaw2 != null)
                        jaw2.dispose();
                    new ErrorDialog(this, je.getMessage());
                } catch (JuggleException je) {
                    if (jaw2 != null)
                        jaw2.dispose();
                    throw new JuggleExceptionInternal(je.getMessage());
                }
            }
                break;

            case FILE_NEWLIST:
                new PatternListWindow(guistrings.getString("Pattern_List"));
                break;
			*/
			
            case FILE_OPEN:
			{
				javax.swing.filechooser.FileFilter filter = new javax.swing.filechooser.FileFilter() {
					public boolean accept(File f) {
						StringTokenizer st = new StringTokenizer(f.getName(), ".");
						String ext = "";
						while (st.hasMoreTokens())
							ext = st.nextToken();
						return (ext.equals("jml") || f.isDirectory());
					}
					
					public String getDescription() {
						return "JML Files";
					}
				};
				
                try {
                    if (PlatformSpecific.getPlatformSpecific().showOpenDialog(this, filter) == JFileChooser.APPROVE_OPTION) {
                        if (PlatformSpecific.getPlatformSpecific().getSelectedFile() != null)
                            showJMLWindow(PlatformSpecific.getPlatformSpecific().getSelectedFile());
                    }
                } catch (JuggleExceptionUser je) {
                    new ErrorDialog(this, je.getMessage());
                }
                break;
			}
				
			/*
            case FILE_JUGGLEANIM:
            {
                PatternListWindow pw = null;

                try {
                    try {
                        int option = PlatformSpecific.getPlatformSpecific().showOpenDialog(this);
                        if (option == JFileChooser.APPROVE_OPTION) {
                            if (PlatformSpecific.getPlatformSpecific().getSelectedFile() != null) {
                                pw = new PatternListWindow(guistrings.getString("Patterns"));
                                readJuggleAnimPatternfile(pw,
                                        new FileReader(PlatformSpecific.getPlatformSpecific().getSelectedFile()));
                            }
                        }
                    } catch (FileNotFoundException fnfe) {
                        throw new JuggleExceptionUser(errorstrings.getString("Error_file_not_found")+
                                                      ": "+fnfe.getMessage());
                    } catch (IOException ioe) {
                        throw new JuggleExceptionUser(errorstrings.getString("Error_IO")+": "+ioe.getMessage());
                    }
                } catch (JuggleExceptionUser jeu) {
                    if (pw != null) pw.dispose();
                    new ErrorDialog(this, jeu.getMessage());
                } catch (JuggleExceptionInternal jei) {
                    if (pw != null) pw.dispose();
                    ErrorDialog.handleException(jei);
                }
				break;
            }
			*/
			
			case FILE_CONVERT:
			{
				javax.swing.filechooser.FileFilter filter = new javax.swing.filechooser.FileFilter() {
					public boolean accept(File f) {
						StringTokenizer st = new StringTokenizer(f.getName(), ".");
						String ext = "";
						while (st.hasMoreTokens())
							ext = st.nextToken();
						return (ext.equals("jml") || f.isDirectory());
					}
					
					public String getDescription() {
						return "JML Files";
					}
				};
				
                try {
                    if (PlatformSpecific.getPlatformSpecific().showOpenDialog(this, filter) == JFileChooser.APPROVE_OPTION) {
                        if (PlatformSpecific.getPlatformSpecific().getSelectedFile() != null)
                            convertJML(PlatformSpecific.getPlatformSpecific().getSelectedFile());
                    }
                } catch (JuggleExceptionUser je) {
                    new ErrorDialog(this, je.getMessage());
                }
				break;
			}
				
            case FILE_EXIT:
                System.exit(0);
                break;
        }

    }
	

    public void showJMLWindow(File jmlf) throws JuggleExceptionUser, JuggleExceptionInternal {
        JFrame frame = null;
        PatternListWindow pw = null;

        try {
            try {
                JMLParser parser = new JMLParser();
                parser.parse(new FileReader(jmlf));
        
                switch (parser.getFileType()) {
                    case JMLParser.JML_PATTERN:
                    {
                        JMLNode root = parser.getTree();
                        JMLPattern pat = new JMLPattern(root);
                        frame = new PatternWindow(pat.getTitle(), pat, new AnimatorPrefs());
                        break;
                    }
                    case JMLParser.JML_LIST:
                    {
                        JMLNode root = parser.getTree();
                        pw = new PatternListWindow(root);
                        PatternList pl = pw.getPatternList();
                        break;
                    }
                    default:
                    {
                        throw new JuggleExceptionUser(errorstrings.getString("Error_invalid_JML"));
                    }
                }
            } catch (FileNotFoundException fnfe) {
                throw new JuggleExceptionUser(errorstrings.getString("Error_file_not_found")+": "+fnfe.getMessage());
            } catch (IOException ioe) {
                throw new JuggleExceptionUser(errorstrings.getString("Error_IO")+": "+ioe.getMessage());
            } catch (SAXParseException spe) {
				String template = errorstrings.getString("Error_parsing");
				Object[] arguments = { new Integer(spe.getLineNumber()) };					
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            } catch (SAXException se) {
                throw new JuggleExceptionUser(se.getMessage());
            }
        } catch (JuggleExceptionUser jeu) {
            if (frame != null) frame.dispose();
            if (pw != null) pw.dispose();
            throw jeu;
        } catch (JuggleExceptionInternal jei) {
            if (frame != null) frame.dispose();
            if (pw != null) pw.dispose();
            throw jei;
        }
    }

	
	public void convertJML(File jmlf) throws JuggleExceptionUser {
		String in = null, out = null;
		
		try {
			int len = (int)jmlf.length();
			FileReader fr = new FileReader(jmlf);
			char[] ch = new char[len];
			fr.read(ch, 0, len);
			in = new String(ch);
		} catch (IOException ioe) {
			String template = errorstrings.getString("Error_reading_file");
			Object[] arguments = { jmlf.getName() };					
			throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
		}
		
		// check to see if input file is already JML version 1.2
		// check if we should be loading as JML version 1.0 (switch y and z coordinates in events)
		boolean switchyz = false;
		Matcher m = Pattern.compile("<jml[^>]*version[\\s]*=[\\s]*['\"]([^'\"]*)['\"]").matcher(in);
		if (m.find()) {
			if (m.group(1).equals("1.0"))
				switchyz = true;
			else if (m.group(1).equals("1.2"))
				throw new JuggleExceptionUser(errorstrings.getString("Error_already_12"));				
		} else if (JMLDefs.default_JML_on_load.equals("1.0"))
			switchyz = true;
		
		// <jml (...)> ---> <jml version="1.2">
		m = Pattern.compile("<jml[^>]*>").matcher(in);
		out = m.replaceAll("<jml version=\"1.2\">");

		// <title>(...1)</title> ---> <title>XMLc(...1)</title>
		StringBuffer sb = new StringBuffer();
		m = Pattern.compile("<title>(.*?)</title>").matcher(out);
		while (m.find()) {
			String g = "<title>" + JMLNode.xmlescape(m.group(1)).trim() + "</title>";
			m.appendReplacement(sb, g);
		}
		m.appendTail(sb);
		out = sb.toString();
		
		// display="(...1)" ---> display="XMLc(...1)"
		sb = new StringBuffer();
		m = Pattern.compile("display[\\s]*=[\\s]*\"([^\"]*)\"").matcher(out);
		while (m.find()) {
			String g = "display=\"" + JMLNode.xmlescape(m.group(1)).trim() + "\"";
			m.appendReplacement(sb, g);
		}
		m.appendTail(sb);
		out = sb.toString();
		
		// animprefs="(...1)" ---> animprefs="XMLc(...1)"
		sb = new StringBuffer();
		m = Pattern.compile("animprefs[\\s]*=[\\s]*\"([^\"]*)\"").matcher(out);
		while (m.find()) {
			String g = "animprefs=\"" + JMLNode.xmlescape(m.group(1)).trim() + "\"";
			m.appendReplacement(sb, g);
		}
		m.appendTail(sb);
		out = sb.toString();
		
		// <line (...1) notation="jml" pattern='(...2)'(...3)/> ---> <line (...1) notation="jml"(...3)>(...2)</line>
		sb = new StringBuffer();
		m = Pattern.compile("(<line[^>]*notation[\\s]*=[\\s]*\"jml\"[^>]*?)[\\s]+pattern[\\s]*=[\\s]*['\"]([^']*)['\"]([^>]*)/>").matcher(out);
		while (m.find()) {
			String g = m.group(1) + m.group(3) + ">" + m.group(2) + "</line>";
			m.appendReplacement(sb, g);
		}
		m.appendTail(sb);
		out = sb.toString();
		
		// <line (...1) pattern='(...2)' notation="jml" (...3)/> ---> <line (...1) notation="jml"(...3)>(...2)</line>
		sb = new StringBuffer();
		m = Pattern.compile("(<line[^>]*?)[\\s]+pattern[\\s]*=[\\s]*['\"]([^']*)['\"]([^>]*notation[\\s]*=[\\s]*\"jml\"[^>]*?)[\\s]*/>").matcher(out);
		while (m.find()) {
			String g = m.group(1) + m.group(3) + ">" + m.group(2) + "</line>";
			m.appendReplacement(sb, g);
		}
		m.appendTail(sb);
		out = sb.toString();
		
		// all of the JML patterns are taken care of above; any remaining lines with
		// 'pattern=' are in other notations
		
		// <line (...1) pattern='(...2)' (...3)/> ---> <line (...1) (...3)>XMLc(...2)</line>
		sb = new StringBuffer();
		m = Pattern.compile("(<line[^>]*?)[\\s]+pattern[\\s]*=[\\s]*['\"]([^'\"]*)['\"]([^>]*?)[\\s]*/>").matcher(out);
		while (m.find()) {
			String g = m.group(1) + m.group(3) + ">\n" + JMLNode.xmlescape(m.group(2)).trim() + "\n</line>";
			m.appendReplacement(sb, g);
		}
		m.appendTail(sb);
		out = sb.toString();
		
		if (switchyz) {
			// <event (...1) y="(...2)" (...3) z="(...4)" ---> <event (...1) y="(...4)" (...3) z="(...2)"
			sb = new StringBuffer();
			m = Pattern.compile("(<event[^>]*)[\\s]+([yz])[\\s]*=[\\s]*\"([^\"]*)\"([^>]*)[\\s]+([yz])[\\s]*=[\\s]*\"([^\"]*)\"").matcher(out);
			while (m.find()) {
				String g = m.group(1) + " " + m.group(2) + "=\"" + m.group(6) + "\"" + m.group(4) + " " +
							m.group(5) + "=\"" + m.group(3) + "\"";
				m.appendReplacement(sb, g);
			}
			m.appendTail(sb);
			out = sb.toString();
		}
		
		
		String outname = jmlf.getName();
		m = Pattern.compile("^(.*)(\\.[^\\.]*)$").matcher(outname);
		if (m.find())
			outname = m.group(1) + "_converted" + m.group(2);	// insert before filename extension
		else
			outname = outname + "_converted";
		
		if (Constants.DEBUG_PARSING) {
			System.out.println("------------------- input file: -------------------");
			System.out.println(in);
			System.out.println("------------------- output file: -------------------");
			System.out.println(out);
			System.out.println("output filename = " + outname);
		}
		
		try {
			File outfile = new File(jmlf.getParent(), outname);
			if (outfile.exists()) {
				String template = errorstrings.getString("Error_already_exists");
				Object[] arguments = { outname };					
				throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
			}
			
			OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(outfile), "UTF-8");
			osw.write(out, 0, out.length());
			osw.flush();
			osw.close();
		} catch (IOException ioe) {
			String template = errorstrings.getString("Error_writing_file");
			Object[] arguments = { outname };					
			throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
		}
	}
	
	
	/*
    protected void readJuggleAnimPatternfile(PatternListWindow plw, Reader instream)
        throws JuggleExceptionUser, JuggleExceptionInternal, IOException {
		String currentline;
		java.util.StringTokenizer st;
		int numtokens;

		BufferedReader dis = new BufferedReader(instream);

		for (;;) {
		 currentline = dis.readLine();
		 if (currentline == null)
			 break;
		 st = new java.util.StringTokenizer(currentline, "\t");
		 numtokens = st.countTokens();

		 String display = " ";
		 if (numtokens > 0) {
			 display = st.nextToken();
			 // remove any quotes from the displayed string
			 int pos;
			 StringBuffer sb = null;
			 while ((pos = display.indexOf('\"')) >= 0) {
				 if (sb == null)
					 sb = new StringBuffer(display);
				 sb.deleteCharAt(pos);
				 display = sb.toString();
			 }
		 }

		 String notation = null;
		 String anim = null;
		 if (numtokens > 1) {
			 notation = "siteswap";
			 anim = st.nextToken();
		 }

		 if (anim != null) {
			 String newanim = null;
			 ParameterList pl = new ParameterList(anim);

			 String pat = pl.getParameter("pattern");
			 String term = "pattern="+pat;
			 newanim = (newanim==null) ? term : (newanim+";"+term);

			 String DR = pl.getParameter("mat_DR");
			 if (DR != null) {
				 term = "dratio="+DR;
				 newanim = (newanim==null) ? term : (newanim+";"+term);
			 }

			 String HR = pl.getParameter("mat_HR");
			 if (HR != null) {
				 double tps = -1.0;
				 // convert HR to tps
				 try {

					 Siteswap ss = new Siteswap(pat);
					 int mt = ss.getMaxThrow();
					 double mat_hr = 1.0;
					 double dwell = 0.5;

					 mat_hr = Double.valueOf(HR).doubleValue();
					 if (DR != null)
						 dwell = Double.valueOf(DR).doubleValue();
					 tps = Math.sqrt(9.8 / (8.0 * mat_hr *
											(double)mt)) * ((double)mt - dwell);
				 } catch (NumberFormatException e) {
					 throw new JuggleExceptionUser("Number format error in \"mat_HR\"");
				 }

				 term = "bps="+JMLPattern.toStringTruncated(tps,3);
				 newanim = (newanim==null) ? term : (newanim+";"+term);
			 }

			 String hands = pl.getParameter("mat_style");
			 if (hands != null) {
				 java.util.StringTokenizer	st1 = new java.util.StringTokenizer(hands, "}", false);
				 java.util.StringTokenizer st2 = null;
				 String			str = null;
				 int				size = st1.countTokens();

				 if ((size % 2) == 1)
					 throw new JuggleExceptionUser("Odd number of coordinates \"mat_style\"");
				 double[][] handpath = new double[size][3];
				 try {
					 for (int i = 0; i < size/2; i++) {
						 for (int j = 0; j < 2; j++) {
							 str = st1.nextToken().replace('{', ' ');
							 st2 = new java.util.StringTokenizer(str, ",", false);
							 handpath[2*i+j][0] =
								 Double.valueOf(st2.nextToken()).doubleValue();
							 if (st2.hasMoreTokens())
								 handpath[2*i+j][1] =
									 Double.valueOf(st2.nextToken()).doubleValue();
							 if (st2.hasMoreTokens())
								 handpath[2*i+j][2] =
									 Double.valueOf(st2.nextToken()).doubleValue();
						 }
					 }
				 } catch (NumberFormatException e) {
					 throw new JuggleExceptionUser("Number format error in \"mat_style\" coordinate");
				 } catch (java.util.NoSuchElementException e) {
					 throw new JuggleExceptionUser("No such element exception in \"mat_style\"");
				 }

				 String newhands = null;
				 for (int i = 0; i < size/2; i++) {
					 term = "{";
					 for (int j = 0; j < 2; j++) {
						 term += "(" + JMLPattern.toStringTruncated((2.5*handpath[i*2+j][0]),2);
						 if (handpath[i*2+j][1] != 0.0)
							 term += "," + JMLPattern.toStringTruncated((5.0*handpath[i*2+j][1]),2);
						 term += ")";
					 }
					 term += "}";
					 newhands = (newhands==null) ? term : (newhands+term);
				 }
				 term = "hands="+newhands;
				 newanim = (newanim==null) ? term : (newanim+";"+term);
			 }

			 anim = newanim;
		 }

		 plw.addPattern(display, notation, anim, null, null);
		}
		dis.close();
	}
	*/
	
	public NotationGUI getNotationGUI() { return ng; }
	
	public void windowOpened(WindowEvent e) { }
	public void windowClosing(WindowEvent e) {
		try {
			doMenuCommand(FILE_EXIT);
        } catch (Exception ex) {
            System.exit(0);
        }
	}
	public void windowClosed(WindowEvent e) { }
	public void windowIconified(WindowEvent e) { }
	public void windowDeiconified(WindowEvent e) { }
	public void windowActivated(WindowEvent e) { }
	public void windowDeactivated(WindowEvent e) { }
}
