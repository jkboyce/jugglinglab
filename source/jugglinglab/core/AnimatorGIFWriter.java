// AnimatorGIFWriter.java
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
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.net.*;
// import java.lang.reflect.*;

import jugglinglab.jml.*;
import jugglinglab.renderer.*;
import jugglinglab.util.*;

import gifwriter.*;


public class AnimatorGIFWriter extends Thread {
    static ResourceBundle guistrings;
    // static ResourceBundle errorstrings;
    static {
        guistrings = JLLocale.getBundle("GUIStrings");
        // errorstrings = JLLocale.getBundle("ErrorStrings");
    }

    Animator ja = null;
    JMLPattern pat = null;
    jugglinglab.renderer.Renderer ren1 = null, ren2 = null;
    int num_frames;
    double sim_interval_secs;
    long real_interval_millis;
    OutputStream out = null;

    public AnimatorGIFWriter() {
        this.setPriority(Thread.MIN_PRIORITY);
    }

    public void setup(Animator ja, jugglinglab.renderer.Renderer ren1,
                      jugglinglab.renderer.Renderer ren2, int num_frames,
                      double sim_interval_secs, long real_interval_millis) {
        this.ja = ja;
        this.pat = ja.getPattern();
        this.ren1 = ren1;
        this.ren2 = ren2;
        this.num_frames = num_frames;
        this.sim_interval_secs = sim_interval_secs;
        this.real_interval_millis = real_interval_millis;
    }

    public void run() {
        if (!jugglinglab.core.Constants.INCLUDE_GIF_SAVE)
            return;

        try {
            /*
             Class gawclass = null;
             try {
                 gawclass = Class.forName("gifwriter.GIFAnimWriter");
             } catch (ClassNotFoundException cnfe) {
                 try {
                     URL[] jarurl = new URL[] {new URL("file:GIFAnimWriter.jar")};
                     URLClassLoader loader = new URLClassLoader(jarurl);
                     gawclass = Class.forName("gifwriter.GIFAnimWriter", true, loader);
                 } catch (ClassNotFoundException cnfe2) {
                     new ErrorDialog(parent, "Required file GIFAnimWriter.jar not found");
                     return;
                 } catch (MalformedURLException ex) {
                     throw new JuggleExceptionInternal("Malformed URL");
                 }
             }
             Method docolormap1 = null;	// public void doColorMap(Color color, boolean defaultcolor) throws IOException;
             Method writeheader = null;	// public void writeHeader(OutputStream out) throws IOException;
             Method writedelay = null;	// public void writeDelay(int delay, OutputStream out) throws IOException;
             Method writegif = null;		// public void writeGIF(Image img, OutputStream out) throws IOException;
             Method writetrailer = null;	// public void writeTrailer(OutputStream out) throws IOException;
             try {
                 docolormap1 = gawclass.getMethod("doColorMap", new Class[] {Color.class, Boolean.TYPE});
                 writeheader = gawclass.getMethod("writeHeader", new Class[] {OutputStream.class});
                 writedelay = gawclass.getMethod("writeDelay", new Class[] {Integer.TYPE, OutputStream.class});
                 writegif = gawclass.getMethod("writeGIF", new Class[] {Image.class, OutputStream.class});
                 writetrailer = gawclass.getMethod("writeTrailer", new Class[] {OutputStream.class});
             } catch (NoSuchMethodException nsme) {
                 throw new JuggleExceptionInternal("Could not find method: "+nsme.getMessage());
             } catch (SecurityException se) {
                 throw new JuggleExceptionInternal("Method not accessible (security): "+se.getMessage());
             }
             */

            boolean origpause = ja.getPaused();
            ja.setPaused(true);

            try {
                FileOutputStream out = null;
                ProgressMonitor pm = null;

                int option = PlatformSpecific.getPlatformSpecific().showSaveDialog(ja);

                if (option == JFileChooser.APPROVE_OPTION) {
                    if (PlatformSpecific.getPlatformSpecific().getSelectedFile() != null) {
                        ja.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                        File file = PlatformSpecific.getPlatformSpecific().getSelectedFile();
                        out = new FileOutputStream(file);

                        // Create the object that will actually do the writing
                        /*  Object gaw = null;
                        try {
                            gaw = gawclass.newInstance();
                        } catch (IllegalAccessException iae) {
                            throw new JuggleExceptionInternal("Cannot access gifwriter.GIFAnimWriter class (security)");
                        } catch (InstantiationException ie) {
                            throw new JuggleExceptionInternal("Could not instantiate gifwriter.GIFAnimWriter object");
                        }*/
                        GIFAnimWriter gaw = new GIFAnimWriter();

                        // set black as default drawing color
                        //   docolormap1.invoke(gaw, new Object[] {Color.black, new Boolean(true)});
                        // gaw.doColorMap(Color.black, true);

                        Dimension dim = ja.getSize();
                        int appWidth = dim.width;
                        int appHeight = dim.height;
                        Image tempoffscreen = ja.createImage(appWidth, appHeight);
                        Graphics tempoffg = tempoffscreen.getGraphics();


                        // writeheader.invoke(gaw, new Object[] {out});
                        //		gaw.writeHeader(out);

                        int[] gifpropnum = new int[pat.getNumberOfPaths()];
                        for (int i = 0; i < pat.getNumberOfPaths(); i++)
                            gifpropnum[i] = pat.getPropAssignment(i+1);
                        int patperiod = pat.getPeriod();
                        int totalframes = patperiod * num_frames * 2;
                        int framecount = 0;

                        pm = new ProgressMonitor(ja, guistrings.getString("Saving_animated_GIF"),
                                "", 0, totalframes);
                        boolean canceled = false;

                        // loop through the individual frames twice, first to build the
                        // color map and the second to write the GIF frames
                        for (int pass = 0; pass < 2; pass++) {
                            if (pass == 1)
                                gaw.writeHeader(out);

                            for (int i = 0; i < patperiod; i++)  {
                                double time = pat.getLoopStartTime();

                                for (int j = 0; j < num_frames; j++) {
                                    if (pass == 1)
                                        gaw.writeDelay((int)(real_interval_millis/10), out);

                                    if (ren2 != null) {
                                        this.ren1.drawFrame(time, gifpropnum,
                                                            tempoffg.create(0,0,dim.width/2,dim.height), ja);
                                        this.ren2.drawFrame(time, gifpropnum,
                                                            tempoffg.create(dim.width/2,0,dim.width/2,dim.height), ja);
                                    } else {
                                        this.ren1.drawFrame(time, gifpropnum, tempoffg, ja);
                                    }
                                    // ren.drawFrame(time, gifpropnum, tempoffg, ja);

                                    if (pass == 0)
                                        gaw.doColorMap(tempoffscreen);
                                    else
                                        gaw.writeGIF(tempoffscreen, out);

                                    if (pm != null) {
                                        framecount++;
                                        String note = (pass==0 ? guistrings.getString("Message_GIFsave_color_map") :
                                                guistrings.getString("Message_GIFsave_writing_frame")+" "+(framecount-num_frames)+"/"+num_frames);
                                        SwingUtilities.invokeLater(new PBUpdater(pm,framecount,note));
                                        if (pm.isCanceled())
                                            return;
                                    }

                                    time += sim_interval_secs;
                                }

                                ja.advanceProps(gifpropnum);
                            }
                        }

                        //  writetrailer.invoke(gaw, new Object[] {out});
                        gaw.writeTrailer(out);
                        tempoffg.dispose();
                    }
                }
            } catch (FileNotFoundException fnfe) {
                throw new JuggleExceptionInternal("AnimGIFSave file not found: "+fnfe.getMessage());
            } catch (IOException ioe) {
                throw new JuggleExceptionInternal("AnimGIFSave IOException: "+ioe.getMessage());
            } /* catch (IllegalAccessException iae) {
                throw new JuggleExceptionInternal("AnimGIFSave IllegalAccessException: "+iae.getMessage());
            } catch (InvocationTargetException ite) {
                throw new JuggleExceptionInternal("AnimGIFSave InvocationTargetException: "+ite.getMessage());
            } */ catch (IllegalArgumentException iae) {
                throw new JuggleExceptionInternal("AnimGIFSave IllegalArgumentException: "+iae.getMessage());
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ioe) {}
                }
                ja.setCursor(Cursor.getDefaultCursor());
                ja.setPaused(origpause);
            }
        } catch (Exception e) {
            jugglinglab.util.ErrorDialog.handleException(e);
        }

        ja.writingGIF = false;
    }
}

class PBUpdater implements Runnable {
    ProgressMonitor pro = null;
    int setting;
    String note;

    public PBUpdater(ProgressMonitor pro, int setting, String note) {
        this.pro = pro;
        this.setting = setting;
        this.note = note;
    }

    public void run() {
        pro.setProgress(setting);
        pro.setNote(note);
    }
}
