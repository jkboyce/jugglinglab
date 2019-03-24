// Animator.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.core;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ResourceBundle;

import javax.imageio.*;
import javax.imageio.metadata.*;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.w3c.dom.Node;

import jugglinglab.jml.*;
import jugglinglab.renderer.Renderer;
import jugglinglab.renderer.Renderer2D;
import jugglinglab.util.*;

// import gifwriter.GIFAnimWriter;


public class Animator {
    protected JMLPattern        pat;
    protected AnimationPrefs    jc;
    protected Renderer          ren1, ren2;
    protected Coordinate        overallmax, overallmin;

    protected int               num_frames;
    protected double            sim_interval_secs;
    protected long              real_interval_millis;
    protected int[]             animpropnum;
    protected Permutation       invpathperm;

    // camera angles for viewing
    protected double[]          camangle;       // in radians
    protected double[]          camangle1;      // for stereo display
    protected double[]          camangle2;

    protected Dimension         dim;

    public Animator() {
        this.camangle = new double[2];
        this.camangle1 = new double[2];
        this.camangle2 = new double[2];
        jc = new AnimationPrefs();
    }

    // Do a full (re)start of the animator with a new pattern, new animation
    // preferences, or both. Passing in null indicates no update for that item.
    public void restartAnimator(JMLPattern newpat, AnimationPrefs newjc)
                    throws JuggleExceptionUser, JuggleExceptionInternal {
        // try to lay out new pattern first so if there's an error we
        // won't disrupt the current animation
        if (newpat != null && !newpat.isLaidout())
            newpat.layoutPattern();

        if (newpat != null)
            this.pat = newpat;
        if (newjc != null)
            this.jc = newjc;

        if (this.pat == null)
            return;

        boolean sg = (this.jc.showGround == AnimationPrefs.GROUND_ON
                || (this.jc.showGround == AnimationPrefs.GROUND_AUTO && this.pat.isBouncePattern()));

        this.ren1 = new Renderer2D();
        this.ren1.setPattern(this.pat);
        this.ren1.setGround(sg);
        if (this.jc.stereo) {
            this.ren2 = new Renderer2D();
            this.ren2.setPattern(this.pat);
            this.ren2.setGround(sg);
        } else
            this.ren2 = null;

        initAnimator();

        double[] ca = new double[2];
        if (this.jc.camangleGiven) {
            ca[0] = Math.toRadians(this.jc.camangle[0]);
            double theta = Math.min(179.9999, Math.max(0.0001, this.jc.camangle[1]));
            ca[1] = Math.toRadians(theta);
        } else {
            if (this.pat.getNumberOfJugglers() == 1) {
                ca[0] = Math.toRadians(0.0);
                ca[1] = Math.toRadians(90.0);
            } else {
                ca[0] = Math.toRadians(340.0);
                ca[1] = Math.toRadians(70.0);
            }
        }
        setCameraAngle(ca);

        if (jugglinglab.core.Constants.DEBUG_LAYOUT)
            System.out.println(this.pat);
    }

    public Dimension getDimension() { return new Dimension(this.dim); }

    public void setDimension(Dimension d) {
        this.dim = new Dimension(d);
        if (ren1 != null)
            syncRenderersToSize();
    }

    public double[] getCameraAngle() {
        double[] result = new double[2];
        result[0] = this.camangle[0];
        result[1] = this.camangle[1];
        return result;
    }

    protected void setCameraAngle(double[] ca) {
        while (ca[0] < 0.0)
            ca[0] += 2.0 * Math.PI;
        while (ca[0] >= 2.0 * Math.PI)
            ca[0] -= 2.0 * Math.PI;

        this.camangle[0] = ca[0];
        this.camangle[1] = ca[1];

        if (jc.stereo) {
            this.camangle1[0] = ca[0] - 0.05;
            this.camangle1[1] = ca[1];
            ren1.setCameraAngle(this.camangle1);
            this.camangle2[0] = ca[0] + 0.05;
            this.camangle2[1] = ca[1];
            ren2.setCameraAngle(this.camangle2);
        } else {
            this.camangle1[0] = ca[0];
            this.camangle1[1] = ca[1];
            ren1.setCameraAngle(this.camangle1);
        }
    }

    public void drawFrame(double sim_time, Graphics g, boolean draw_axes)
                        throws JuggleExceptionInternal {
        if (this.jc.stereo) {
            this.ren1.drawFrame(sim_time, this.animpropnum,
                                g.create(0, 0, this.dim.width/2, this.dim.height));
            this.ren2.drawFrame(sim_time, this.animpropnum,
                                g.create(this.dim.width/2, 0, this.dim.width/2, this.dim.height));
        } else {
            this.ren1.drawFrame(sim_time, this.animpropnum, g);
        }

        if (draw_axes) {
            if (g instanceof Graphics2D) {
                Graphics2D g2 = (Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
            }

            double[] ca = ren1.getCameraAngle();
            double theta = ca[0];
            double phi = ca[1];

            double xya = 30.0;
            double xyb = xya * Math.cos(phi);
            double zlen = xya * Math.sin(phi);
            int cx = 38;
            int cy = 45;
            int xx = cx + (int)(0.5 - xya * Math.cos(theta));
            int xy = cy + (int)(0.5 + xyb * Math.sin(theta));
            int yx = cx + (int)(0.5 + xya * Math.sin(theta));
            int yy = cy + (int)(0.5 + xyb * Math.cos(theta));
            int zx = cx;
            int zy = cy - (int)(0.5 + zlen);

            g.setColor(Color.green);
            g.drawLine(cx, cy, xx, xy);
            g.drawLine(cx, cy, yx, yy);
            g.drawLine(cx, cy, zx, zy);
            g.fillOval(xx-2, xy-2, 5, 5);
            g.fillOval(yx-2, yy-2, 5, 5);
            g.fillOval(zx-2, zy-2, 5, 5);
            g.drawString("x", xx-2, xy-4);
            g.drawString("y", yx-2, yy-4);
            g.drawString("z", zx-2, zy-4);
        }

        if (this.jc.stereo && draw_axes) {
            double[] ca = ren2.getCameraAngle();
            double theta = ca[0];
            double phi = ca[1];

            double xya = 30.0;
            double xyb = xya * Math.cos(phi);
            double zlen = xya * Math.sin(phi);
            int cx = 38 + this.dim.width/2;
            int cy = 45;
            int xx = cx + (int)(0.5 - xya * Math.cos(theta));
            int xy = cy + (int)(0.5 + xyb * Math.sin(theta));
            int yx = cx + (int)(0.5 + xya * Math.sin(theta));
            int yy = cy + (int)(0.5 + xyb * Math.cos(theta));
            int zx = cx;
            int zy = cy - (int)(0.5 + zlen);

            g.setColor(Color.green);
            g.drawLine(cx, cy, xx, xy);
            g.drawLine(cx, cy, yx, yy);
            g.drawLine(cx, cy, zx, zy);
            g.fillOval(xx-2, xy-2, 5, 5);
            g.fillOval(yx-2, yy-2, 5, 5);
            g.fillOval(zx-2, zy-2, 5, 5);
            g.drawString("x", xx-2, xy-4);
            g.drawString("y", yx-2, yy-4);
            g.drawString("z", zx-2, zy-4);
        }
    }

    // After each cycle through the pattern we need to assign props to new paths,
    // to maintain continuity. After pat.getPeriod() times through this the props
    // will return to their original assignments.
    public void advanceProps() {
        int paths = this.pat.getNumberOfPaths();
        int[] temppropnum = new int[paths];

        for (int i = 0; i < paths; i++)
            temppropnum[this.invpathperm.getMapping(i + 1) - 1] = this.animpropnum[i];
        for (int i = 0; i < paths; i++)
            this.animpropnum[i] = temppropnum[i];
    }

    // Rescales the animator so that the pattern and key parts of the juggler
    // are visible. Call this whenever the pattern changes.
    public void initAnimator() {
        findMaxMin();
        syncRenderersToSize();

        // figure out timing constants; this in effect adjusts fps to get an integer
        // number of frames in one repetition of the pattern
        this.num_frames = (int)(0.5 + (pat.getLoopEndTime() - pat.getLoopStartTime()) *
                                jc.slowdown * jc.fps);
        this.sim_interval_secs = (pat.getLoopEndTime() - pat.getLoopStartTime()) / num_frames;
        this.real_interval_millis = (long)(1000.0 * sim_interval_secs * jc.slowdown);

        this.animpropnum = new int[pat.getNumberOfPaths()];
        for (int i = 0; i < pat.getNumberOfPaths(); i++)
            this.animpropnum[i] = pat.getPropAssignment(i + 1);
        this.invpathperm = pat.getPathPermutation().getInverse();
    }

    // Find the overall bounding box of the juggler and pattern, in real-space
    // (centimeters) coordinates.
    //
    // The algorithm here could be improved to take into account which props are
    // on which paths.  We may also want to leave room for the rest of the juggler.
    private void findMaxMin() {
        Coordinate patternmax = null, patternmin = null;
        Coordinate handmax = null, handmin = null;
        Coordinate propmax = null, propmin = null;

        for (int i = 1; i <= pat.getNumberOfPaths(); i++) {
            patternmax = Coordinate.max(patternmax, pat.getPathMax(i));
            patternmin = Coordinate.min(patternmin, pat.getPathMin(i));

            if (jugglinglab.core.Constants.DEBUG_LAYOUT)
                System.out.println("Pattern max "+i+" = "+patternmax);
        }

        // make sure all hands are visible
        for (int i = 1; i <= pat.getNumberOfJugglers(); i++) {
            handmax = Coordinate.max(handmax, pat.getHandMax(i, HandLink.LEFT_HAND));
            handmin = Coordinate.min(handmin, pat.getHandMin(i, HandLink.LEFT_HAND));
            handmax = Coordinate.max(handmax, pat.getHandMax(i, HandLink.RIGHT_HAND));
            handmin = Coordinate.min(handmin, pat.getHandMin(i, HandLink.RIGHT_HAND));
        }

        for (int i = 1; i <= pat.getNumberOfProps(); i++) {
            propmax = Coordinate.max(propmax, pat.getProp(i).getMax());
            propmin = Coordinate.min(propmin, pat.getProp(i).getMin());
        }

        // make sure props are entirely visible along all paths
        patternmax = Coordinate.add(patternmax, propmax);
        patternmin = Coordinate.add(patternmin, propmin);

        // make sure hands are entirely visible
        handmax = Coordinate.add(handmax, ren1.getHandWindowMax());
        handmin = Coordinate.add(handmin, ren1.getHandWindowMin());

        // make sure jugglers' bodies are visible
        this.overallmax = Coordinate.max(handmax, ren1.getJugglerWindowMax());
        this.overallmax = Coordinate.max(overallmax, patternmax);

        this.overallmin = Coordinate.min(handmin, ren1.getJugglerWindowMin());
        this.overallmin = Coordinate.min(overallmin, patternmin);

        // we want to ensure everything stays visible as we rotate the camera
        // viewpoint.  the following is simple and seems to work ok.
        if (pat.getNumberOfJugglers() == 1) {
            overallmin.z -= 0.3 * Math.max(Math.abs(overallmin.y), Math.abs(overallmax.y));
            overallmax.z += 5.0;    // keeps objects from rubbing against top of window
        } else {
            double tempx = Math.max(Math.abs(overallmin.x), Math.abs(overallmax.x));
            double tempy = Math.max(Math.abs(overallmin.y), Math.abs(overallmax.y));
            overallmin.z -= 0.4 * Math.max(tempx, tempy);
            overallmax.z += 0.4 * Math.max(tempx, tempy);
        }

        // make the x-coordinate origin at the center of the view
        double maxabsx = Math.max(Math.abs(this.overallmin.x),
                                  Math.abs(this.overallmax.x));
        this.overallmin.x = -maxabsx;
        this.overallmax.x = maxabsx;

        if (jugglinglab.core.Constants.DEBUG_LAYOUT) {
            System.out.println("Hand max = "+handmax);
            System.out.println("Hand min = "+handmin);
            System.out.println("Prop max = "+propmax);
            System.out.println("Prop min = "+propmin);
            System.out.println("Pattern max = "+patternmax);
            System.out.println("Pattern min = "+patternmin);
            System.out.println("Overall max = "+this.overallmax);
            System.out.println("Overall min = "+this.overallmin);

            this.overallmax = new Coordinate(100.0,0.0,500.0);
            this.overallmin = new Coordinate(-100.0,0.0,-100.0);
        }
    }

    private void syncRenderersToSize() {
        Dimension d = new Dimension(this.dim);

        if (this.jc.stereo) {
            d.width /= 2;
            this.ren1.initDisplay(d, jc.border, this.overallmax, this.overallmin);
            this.ren2.initDisplay(d, jc.border, this.overallmax, this.overallmin);
        } else
            this.ren1.initDisplay(d, jc.border, this.overallmax, this.overallmin);
    }

    public int[] getAnimPropNum()               { return animpropnum; }
    public Color getBackground()                { return ren1.getBackground(); }
    public AnimationPrefs getAnimationPrefs()   { return jc; }


    // There are two versions of writeGIF, one that uses Java's ImageIO library
    // and a second (much older) version that uses a standalone GIF writer that
    // we wrote. The ImageIO version is slower but does a better job of building
    // the GIF colormap so we use that one for now.

    public void writeGIF(OutputStream os, Animator.WriteGIFMonitor wgm) throws
                        IOException, JuggleExceptionInternal {
        ImageWriter iw = ImageIO.getImageWritersByFormatName("gif").next();
        ImageOutputStream ios = new MemoryCacheImageOutputStream(os);
        iw.setOutput(ios);
        iw.prepareWriteSequence(null);

        BufferedImage image = new BufferedImage(this.dim.width, this.dim.height,
                                                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        // antialiased rendering creates too many distinct color values for
        // GIF to handle well
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_OFF);

        // reset prop assignments so we'll generate an identical GIF every time
        for (int i = 0; i < pat.getNumberOfPaths(); i++)
            this.animpropnum[i] = pat.getPropAssignment(i + 1);

        int totalframes = pat.getPeriod() * this.num_frames;
        int framecount = 0;

        // delay time is embedded in GIF header in terms of hundredths of a second
        String delayTime = String.valueOf((int)(0.5 + this.real_interval_millis / 10));

        ImageWriteParam iwp = iw.getDefaultWriteParam();
        IIOMetadata metadata = null;

        for (int i = 0; i < pat.getPeriod(); i++)  {
            double time = pat.getLoopStartTime();

            for (int j = 0; j < this.num_frames; j++) {
                this.drawFrame(time, g, false);

                // after the second frame all subsequent frames have identical metadata
                if (framecount < 2) {
                    metadata = iw.getDefaultImageMetadata(
                            new ImageTypeSpecifier(image), iwp);
                    configureGIFMetadata(metadata, delayTime, framecount);
                }

                IIOImage ii = new IIOImage(image, null, metadata);
                iw.writeToSequence(ii, (ImageWriteParam) null);

                time += this.sim_interval_secs;
                framecount++;

                if (wgm != null) {
                    wgm.update(framecount, totalframes);
                    if (wgm.isCanceled()) {
                        ios.close();
                        os.close();
                        return;
                    }
                }
            }

            this.advanceProps();
        }

        g.dispose();
        iw.endWriteSequence();
        ios.close();
        os.close();
    }

    // Helper method for writeGIF() above
    // Adapted from https://community.oracle.com/thread/1264385
    private static void configureGIFMetadata(IIOMetadata meta,
                                            String delayTime,
                                            int imageIndex) {
        String metaFormat = meta.getNativeMetadataFormatName();

        if (!"javax_imageio_gif_image_1.0".equals(metaFormat)) {
            throw new IllegalArgumentException(
                    "Unfamiliar gif metadata format: " + metaFormat);
        }

        Node root = meta.getAsTree(metaFormat);

        //find the GraphicControlExtension node
        Node child = root.getFirstChild();
        while (child != null) {
            if ("GraphicControlExtension".equals(child.getNodeName())) {
                break;
            }
            child = child.getNextSibling();
        }

        IIOMetadataNode gce = (IIOMetadataNode) child;
        gce.setAttribute("userInputFlag", "FALSE");
        gce.setAttribute("delayTime", delayTime);

        //only the first node needs the ApplicationExtensions node
        if (imageIndex == 0) {
            IIOMetadataNode aes =
                    new IIOMetadataNode("ApplicationExtensions");
            IIOMetadataNode ae =
                    new IIOMetadataNode("ApplicationExtension");
            ae.setAttribute("applicationID", "NETSCAPE");
            ae.setAttribute("authenticationCode", "2.0");
            byte[] uo = new byte[]{
                //last two bytes is an unsigned short (little endian) that
                //indicates the the number of times to loop.
                //0 means loop forever.
                0x1, 0x0, 0x0
            };
            ae.setUserObject(uo);
            aes.appendChild(ae);
            root.appendChild(aes);
        }

        try {
            meta.setFromTree(metaFormat, root);
        } catch (IIOInvalidTreeException e) {
            //shouldn't happen
            throw new Error(e);
        }
    }


    // Version that uses our own standalone GIF writer. It has trouble building
    // the color map when there are many individual colors, for example with the
    // image prop.
    /*
    public void writeGIF(OutputStream os, Animator.WriteGIFMonitor wgm) throws
                IOException, JuggleExceptionInternal {

        // Create the object that will actually do the writing
        GIFAnimWriter gaw = new GIFAnimWriter();

        int appWidth = this.dim.width;
        int appHeight = this.dim.height;

        BufferedImage image = new BufferedImage(appWidth, appHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        // antialiased rendering creates too many distinct color values for
        // GIF to handle well
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_OFF);

        // reset prop assignments so we'll generate an identical GIF every time
        for (int i = 0; i < pat.getNumberOfPaths(); i++)
            this.animpropnum[i] = pat.getPropAssignment(i + 1);

        int totalframes = pat.getPeriod() * this.num_frames * 2;
        int framecount = 0;

        // loop through the individual frames twice, first to build the
        // color map and the second to write the GIF frames
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1)
                gaw.writeHeader(os);

            for (int i = 0; i < pat.getPeriod(); i++)  {
                double time = pat.getLoopStartTime();

                for (int j = 0; j < this.num_frames; j++) {
                    if (pass == 1)
                        gaw.writeDelay((int)(0.5 + real_interval_millis / 10), os);

                    this.drawFrame(time, g, false);

                    if (pass == 0)
                        gaw.doColorMap(image);
                    else
                        gaw.writeGIF(image, os);

                    if (wgm != null) {
                        framecount++;
                        wgm.update(framecount, totalframes);
                        if (wgm.isCanceled()) {
                            os.close();
                            return;
                        }
                    }

                    time += this.sim_interval_secs;
                }

                this.advanceProps();
            }
        }

        gaw.writeTrailer(os);
        g.dispose();
        os.close();
    }
    */

    public interface WriteGIFMonitor {
        // callback method invoked when a processing step is completed
        public void update(int step, int steps_total);

        // callback method should return true when user wants to cancel
        public boolean isCanceled();
    }
}
