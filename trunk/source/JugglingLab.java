// JugglingLab.java
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

import java.applet.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.io.*;
import java.net.*;
import javax.swing.*;
import org.xml.sax.*;

import jugglinglab.core.*;
import jugglinglab.jml.*;
import jugglinglab.util.*;
import jugglinglab.notation.*;
import jugglinglab.view.*;


public class JugglingLab extends JApplet {
    static ResourceBundle guistrings;
    static ResourceBundle errorstrings;

	protected Animator ja = null;
    protected JugglingLabPanel jlp = null;
	protected AnimatorPrefs jc = null;
	
    
    public JugglingLab() {
        initAudioClips();
		initDefaultPropImages();
    }


    protected void configure_applet() {
		String config = getParameter("config");
        String prefs = getParameter("animprefs");
        String jmldir = getParameter("jmldir");
        String jmlfile = getParameter("jmlfile");
		
		// default values
		int entry_type = Notation.NOTATION_SITESWAP;
		int view_type = View.VIEW_SIMPLE;
		
		if (config != null) {
			ParameterList param = new ParameterList(config);
			String entry = param.getParameter("entry");
			if (entry != null) {
				if (entry.equalsIgnoreCase("none"))
					entry_type = Notation.NOTATION_NONE;
				else if (entry.equalsIgnoreCase("siteswap"))
					entry_type = Notation.NOTATION_SITESWAP;
			}
			String view = param.getParameter("view");
			if (view != null) {
				if (view.equalsIgnoreCase("none"))
					view_type = View.VIEW_NONE;
				else if (view.equalsIgnoreCase("simple"))
					view_type = View.VIEW_SIMPLE;
				else if (view.equalsIgnoreCase("edit"))
					view_type = View.VIEW_EDIT;
				else if (view.equalsIgnoreCase("selection"))
					view_type = View.VIEW_SELECTION;
				else if (view.equalsIgnoreCase("jml"))
					view_type = View.VIEW_JML;
			}
			String loc = param.getParameter("locale");
			if (loc != null) {
				// want to get to this before the resource bundles are needed
				// by this or any other objects
				String[] locarray = loc.split("_", 5);
				System.out.println("locarray = " + locarray);
				Locale newloc = null;
				if (locarray.length == 1)
					newloc = new Locale(locarray[0]);
				else if (locarray.length == 2)
					newloc = new Locale(locarray[0], locarray[1]);
				else if (locarray.length > 2)
					newloc = new Locale(locarray[0], locarray[1], locarray[2]);
				
				JLLocale.setLocale(newloc);
			}
		}

		JugglingLab.guistrings = JLLocale.getBundle("GUIStrings");
        JugglingLab.errorstrings = JLLocale.getBundle("ErrorStrings");

		try {
			jc = new AnimatorPrefs();
			if (prefs != null)
				jc.parseInput(prefs);

			JMLPattern pat = null;
			PatternList pl = null;
			boolean readerror = false;
			
			if (jmlfile != null) {
				if (!jmlfile.endsWith(".jml"))
					throw new JuggleExceptionUser(errorstrings.getString("Error_JML_extension"));
				if (jmlfile.indexOf(".") != (jmlfile.length()-4))
					throw new JuggleExceptionUser(errorstrings.getString("Error_JML_filename"));
				
				try {
					URL jmlfileURL = null;
					if (jmldir != null)
						jmlfileURL = new URL(new URL(jmldir), jmlfile);
					else
						jmlfileURL = new URL(getDocumentBase(), jmlfile);

					JMLParser parse = new JMLParser();
					parse.parse(new InputStreamReader(jmlfileURL.openStream()));
					if (parse.getFileType() == JMLParser.JML_PATTERN)
						pat = new JMLPattern(parse.getTree());
					else {
						pl = new PatternList();
						pl.readJML(parse.getTree());
					}
				} catch (MalformedURLException mue) {
					throw new JuggleExceptionUser(errorstrings.getString("Error_URL_syntax")+" '"+jmldir+"'");
				} catch (IOException ioe) {
					readerror = true;
					// System.out.println(ioe.getMessage());
					// throw new JuggleExceptionUser(errorstrings.getString("Error_reading_pattern"));
				} catch (SAXException se) {
					throw new JuggleExceptionUser(errorstrings.getString("Error_parsing_pattern"));
				}
			}
			
			if (pat == null) {
				String notation = getParameter("notation");
				String pattern = getParameter("pattern");
				if ((notation != null) && (pattern != null)) {
					Notation not = null;
					if (notation.equalsIgnoreCase("jml")) {
						try {
							pat = new JMLPattern(new StringReader(pattern));
						} catch (IOException ioe) {
							throw new JuggleExceptionUser(errorstrings.getString("Error_reading_JML"));
						} catch (SAXException se) {
							throw new JuggleExceptionUser(errorstrings.getString("Error_parsing_JML"));
						}
					} else {
						not = Notation.getNotation(notation);
						pat = not.getJMLPattern(pattern);
					}
				}
			}
			
			if (readerror)
				throw new JuggleExceptionUser(errorstrings.getString("Error_reading_pattern"));
				
			JugglingLabPanel jlp = new JugglingLabPanel(null, entry_type, pl, view_type);
			jlp.setDoubleBuffered(true);
			this.setBackground(new Color(0.9f, 0.9f, 0.9f));
			setContentPane(jlp);
			
			Locale loc = JLLocale.getLocale();
			this.applyComponentOrientation(ComponentOrientation.getOrientation(loc));

			validate();
			
			// NOTE: animprefs will only be applied when some kind of pattern is defined
			if (pat != null)
				jlp.getView().restartView(pat, jc);

		} catch (Exception e) {
			String message = "";
			if (e instanceof JuggleExceptionUser)
				message = e.getMessage();
			else {
				if (e instanceof JuggleExceptionInternal)
					message = "Internal error";
				else
					message = e.getClass().getName();
				if (e.getMessage() != null)
					message = message + ": " + e.getMessage();
			}
			
			JPanel p = new JPanel();
			p.setBackground(Color.white);
			GridBagLayout gb = new GridBagLayout();
			p.setLayout(gb);
		
			JLabel lab = new JLabel(message);
			lab.setHorizontalAlignment(SwingConstants.CENTER);
			p.add(lab);
			GridBagConstraints gbc = new GridBagConstraints();
			gbc.anchor = GridBagConstraints.CENTER;
			gbc.fill = GridBagConstraints.BOTH;
			gbc.gridwidth = gbc.gridheight = 1;
			gbc.gridx = 0;
			gbc.gridy = 0;
			gbc.insets = new Insets(0,0,0,0);
			gbc.weightx = 1.0;
			gbc.weighty = 1.0;
			gb.setConstraints(lab, gbc);

			setContentPane(p);
			validate();
			
			if (e instanceof JuggleExceptionInternal)
				ErrorDialog.handleException(e);
			else if (!(e instanceof JuggleExceptionUser))
				e.printStackTrace();
		}
    }

    // applet version starts here
	public void init() {
		// do it this way, so that calls to Swing methods happen on the event dispatch thread
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				configure_applet();
			}
		});	
	}
	
    public void start() {
		if ((jlp != null) && (jlp.getView() != null) && !jc.mousePause)
			jlp.getView().setPaused(false);
		if ((ja != null) && (ja.message == null) && !jc.mousePause)
			ja.setPaused(false);
    }

    public void stop() {
		if ((jlp != null) && (jlp.getView() != null) && !jc.mousePause)
			jlp.getView().setPaused(true);
		if ((ja != null) && (ja.message == null) && !jc.mousePause)
			ja.setPaused(true);
    }

    public void destroy() {
		if ((jlp != null) && (jlp.getView() != null))
			jlp.getView().dispose();
		if (ja != null)
			ja.dispose();
    }

    // It's easiest to load the audioclips here and pass them along to Animator.
    // For some reason AudioClip is part of the Applet package.
    protected void initAudioClips() {
        AudioClip[] clips = new AudioClip[2];
        URL catchurl = this.getClass().getResource("/resources/catch.au");
		if (catchurl != null)
			clips[0] = newAudioClip(catchurl);
        URL bounceurl = this.getClass().getResource("/resources/bounce.au");
		if (bounceurl != null)
			clips[1] = newAudioClip(bounceurl);
        Animator.setAudioClips(clips);
    }
	
	// The class loader delegation model makes it difficult to find resources from
	// within the VersionSpecific class.  There must be a better way to do it but I 
	// give it up already.
	protected void initDefaultPropImages() {
		URL[] images = new URL[2];
		images[0] = this.getClass().getResource("/resources/ball.gif");
        images[1] = this.getClass().getResource("/resources/ball.png");
        VersionSpecific.setDefaultPropImages(images);
	}

	
    // application version starts here
    public static void main(String[] args) {
        try {
            new JugglingLab();		// to load audio clips
            new ApplicationWindow("Juggling Lab");
        } catch (JuggleExceptionUser jeu) {
            new ErrorDialog(null, jeu.getMessage());
        } catch (JuggleExceptionInternal jei) {
            ErrorDialog.handleException(jei);
        }
    }
}
