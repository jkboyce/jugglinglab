// AnimationGIFWriter.java
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


public class AnimationGIFWriter extends Thread {
    static ResourceBundle guistrings;
    // static ResourceBundle errorstrings;
    static {
        guistrings = JLLocale.getBundle("GUIStrings");
        // errorstrings = JLLocale.getBundle("ErrorStrings");
    }

    Animator anim = null;
    JMLPattern pat = null;
    int num_frames;
    double sim_interval_secs;
    long real_interval_millis;
    // OutputStream out = null;

    Runnable cleanup = null;


    public AnimationGIFWriter(Animator anim) {
        this.anim = anim;

        this.pat = anim.pat;
        this.num_frames = anim.num_frames;
        this.sim_interval_secs = anim.sim_interval_secs;
        this.real_interval_millis = anim.real_interval_millis;
    }

    public void writeGIF(OutputStream os, ProgressMonitor pm) throws FileNotFoundException,
                IOException, JuggleExceptionInternal {

        // Create the object that will actually do the writing
        GIFAnimWriter gaw = new GIFAnimWriter();

        Dimension dim = anim.getDimension();
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

                    anim.drawFrame(time, g, false);

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
                            // ja.writingGIF = false;
                            return;
                        }
                    }

                    time += sim_interval_secs;
                }

                anim.advanceProps();
            }
        }

        gaw.writeTrailer(os);
        g.dispose();
        os.close();
    }

    public void writeGIF_interactive(Runnable cleanup) {
        // Do all of the processing in a thread separate from the main event loop.
        // This may be overkill since the processing is usually pretty quick, but
        // it doesn't hurt.
        this.cleanup = cleanup;
        this.setPriority(Thread.MIN_PRIORITY);
        this.start();
    }

    @Override
    public void run() {
        try {
            try {
                int option = PlatformSpecific.getPlatformSpecific().showSaveDialog(null);

                if (option == JFileChooser.APPROVE_OPTION) {
                    if (PlatformSpecific.getPlatformSpecific().getSelectedFile() != null) {
                        File file = PlatformSpecific.getPlatformSpecific().getSelectedFile();
                        FileOutputStream out = new FileOutputStream(file);
                        ProgressMonitor pm = new ProgressMonitor(null,
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
            }
        } catch (Exception e) {
            jugglinglab.util.ErrorDialog.handleException(e);
        }

        if (cleanup != null)
            SwingUtilities.invokeLater(cleanup);
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
