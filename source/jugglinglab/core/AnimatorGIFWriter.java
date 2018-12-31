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
    // OutputStream out = null;


    public AnimatorGIFWriter() {}

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

    public void writeGIF(OutputStream os, ProgressMonitor pm) throws FileNotFoundException,
                IOException, JuggleExceptionInternal {

        // Create the object that will actually do the writing
        GIFAnimWriter gaw = new GIFAnimWriter();

        Dimension dim = ja.getSize();
        int appWidth = dim.width;
        int appHeight = dim.height;

        BufferedImage image = new BufferedImage(appWidth, appHeight, BufferedImage.TYPE_INT_RGB);
        Graphics g = image.getGraphics();

        int[] gifpropnum = new int[pat.getNumberOfPaths()];
        for (int i = 0; i < pat.getNumberOfPaths(); i++)
            gifpropnum[i] = pat.getPropAssignment(i+1);
        int patperiod = pat.getPeriod();
        int totalframes = patperiod * num_frames * 2;
        int framecount = 0;

        if (pm != null)
            pm.setMaximum(totalframes);

        // loop through the individual frames twice, first to build the
        // color map and the second to write the GIF frames
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1)
                gaw.writeHeader(os);

            for (int i = 0; i < patperiod; i++)  {
                double time = pat.getLoopStartTime();

                for (int j = 0; j < num_frames; j++) {
                    if (pass == 1)
                        gaw.writeDelay((int)(real_interval_millis/10), os);

                    if (ren2 != null) {
                        this.ren1.drawFrame(time, gifpropnum,
                                            g.create(0,0,dim.width/2,dim.height), ja);
                        this.ren2.drawFrame(time, gifpropnum,
                                            g.create(dim.width/2,0,dim.width/2,dim.height), ja);
                    } else {
                        this.ren1.drawFrame(time, gifpropnum, g, ja);
                    }

                    if (pass == 0)
                        gaw.doColorMap(image);
                    else
                        gaw.writeGIF(image, os);

                    if (pm != null) {
                        framecount++;
                        String note = (pass==0 ? guistrings.getString("Message_GIFsave_color_map") :
                                guistrings.getString("Message_GIFsave_writing_frame")+" "+(framecount-num_frames)+"/"+num_frames);
                        SwingUtilities.invokeLater(new PBUpdater(pm, framecount, note));
                        if (pm.isCanceled()) {
                            os.close();
                            ja.writingGIF = false;
                            return;
                        }
                    }

                    time += sim_interval_secs;
                }

                ja.advanceProps(gifpropnum);
            }
        }

        gaw.writeTrailer(os);
        g.dispose();
        os.close();
        ja.writingGIF = false;
    }

    public void writeGIF_interactive() {
        // Do all of the processing in a thread separate from the main event loop.
        // This may be overkill since the processing is usually pretty quick, but
        // it doesn't hurt.
        this.setPriority(Thread.MIN_PRIORITY);
        this.start();
    }

    @Override
    public void run() {
        try {
            boolean origpause = ja.getPaused();
            ja.setPaused(true);

            try {
                int option = PlatformSpecific.getPlatformSpecific().showSaveDialog(ja);

                if (option == JFileChooser.APPROVE_OPTION) {
                    if (PlatformSpecific.getPlatformSpecific().getSelectedFile() != null) {
                        ja.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                        File file = PlatformSpecific.getPlatformSpecific().getSelectedFile();
                        FileOutputStream out = new FileOutputStream(file);
                        ProgressMonitor pm = new ProgressMonitor(ja,
                                guistrings.getString("Saving_animated_GIF"), "", 0, 1);
                        this.writeGIF(out, pm);
                    }
                }
            } catch (FileNotFoundException fnfe) {
                throw new JuggleExceptionInternal("AnimGIFSave file not found: "+fnfe.getMessage());
            } catch (IOException ioe) {
                throw new JuggleExceptionInternal("AnimGIFSave IOException: "+ioe.getMessage());
            } catch (IllegalArgumentException iae) {
                throw new JuggleExceptionInternal("AnimGIFSave IllegalArgumentException: "+iae.getMessage());
            } finally {
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

    @Override
    public void run() {
        pro.setProgress(setting);
        pro.setNote(note);
    }
}
