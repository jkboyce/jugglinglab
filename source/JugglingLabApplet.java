// JugglingLabApplet.java
//
// Copyright 2018 by Jack Boyce (jboyce@gmail.com) and others

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

import java.awt.*;
import java.util.Arrays;
import java.util.Locale;
import java.util.ResourceBundle;
import java.io.*;
import java.net.URL;
import java.net.MalformedURLException;
import javax.swing.*;
import org.xml.sax.SAXException;

import jugglinglab.core.*;
import jugglinglab.jml.*;
import jugglinglab.util.*;
import jugglinglab.notation.Notation;
import jugglinglab.view.View;


public class JugglingLabApplet extends JApplet {
    static ResourceBundle guistrings;
    static ResourceBundle errorstrings;

    protected JugglingLabPanel jlp = null;
    protected AnimationPrefs jc = null;


    public JugglingLabApplet() {}

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

        JugglingLabApplet.guistrings = JLLocale.getBundle("GUIStrings");
        JugglingLabApplet.errorstrings = JLLocale.getBundle("ErrorStrings");

        try {
            jc = new AnimationPrefs();
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

    @Override
    public void init() {
        // do it this way, so that calls to Swing methods happen on the event dispatch thread
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                configure_applet();
            }
        });
    }

    @Override
    public void start() {
        if ((jlp != null) && (jlp.getView() != null) && !jc.mousePause)
            jlp.getView().setPaused(false);
    }

    @Override
    public void stop() {
        if ((jlp != null) && (jlp.getView() != null) && !jc.mousePause)
            jlp.getView().setPaused(true);
    }

    @Override
    public void destroy() {
        if ((jlp != null) && (jlp.getView() != null))
            jlp.getView().disposeView();
    }
}
