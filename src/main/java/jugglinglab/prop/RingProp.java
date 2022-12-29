// RingProp.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.prop;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.StringTokenizer;

import jugglinglab.util.*;


public class RingProp extends Prop {
    public static final String[] COLOR_NAMES =
        {
            "transparent",
            "black",
            "blue",
            "cyan",
            "gray",
            "green",
            "magenta",
            "orange",
            "pink",
            "red",
            "white",
            "yellow",
        };

    public static final Color[] COLOR_VALS =
        {
            new Color(0, 0, 0, 0),
            Color.black,
            Color.blue,
            Color.cyan,
            Color.gray,
            Color.green,
            Color.magenta,
            Color.orange,
            Color.pink,
            Color.red,
            Color.white,
            Color.yellow,
        };

    protected static final Color COLOR_DEF = Color.red;
    protected static final int COLORNUM_DEF = 9;  // red
    protected static final double OUTSIDE_DIAM_DEF = 25;  // in cm
    protected static final double INSIDE_DIAM_DEF = 20;  // in cm
    protected static final int POLYSIDES = 200;

    protected double outside_diam = OUTSIDE_DIAM_DEF;
    protected double inside_diam = INSIDE_DIAM_DEF;
    protected Color color = COLOR_DEF;
    protected int colornum = COLORNUM_DEF;

    protected BufferedImage image;

    protected double lastzoom;
    protected double[] lastcamangle = { 0, 0 };

    protected Dimension size;
    protected Dimension center;
    protected Dimension grip;
    protected int[] px;
    protected int[] py;


    // Prop methods

    @Override
    public String getType() {
        return "Ring";
    }

    @Override
    public Color getEditorColor() {
        return color;
    }

    @Override
    public ParameterDescriptor[] getParameterDescriptors() {
        ParameterDescriptor[] result = new ParameterDescriptor[3];

        ArrayList<String> range = new ArrayList<String>();
        for (int i = 0; i < COLOR_NAMES.length; i++)
            range.add(COLOR_NAMES[i]);

        result[0] = new ParameterDescriptor("color", ParameterDescriptor.TYPE_CHOICE,
                            range, COLOR_NAMES[COLORNUM_DEF], COLOR_NAMES[colornum]);
        result[1] = new ParameterDescriptor("outside", ParameterDescriptor.TYPE_FLOAT,
                            null, Double.valueOf(OUTSIDE_DIAM_DEF), Double.valueOf(outside_diam));
        result[2] = new ParameterDescriptor("inside", ParameterDescriptor.TYPE_FLOAT,
                            null, Double.valueOf(INSIDE_DIAM_DEF), Double.valueOf(inside_diam));

        return result;
    }

    @Override
    protected void init(String st) throws JuggleExceptionUser {
        px = new int[POLYSIDES];
        py = new int[POLYSIDES];

        if (st == null)
            return;
        ParameterList pl = new ParameterList(st);

        String colorstr = pl.getParameter("color");
        if (colorstr != null) {
            Color temp = null;

            if (colorstr.indexOf((int)',') == -1) { // color name
                for (int i = 0; i < COLOR_NAMES.length; i++) {
                    if (COLOR_NAMES[i].equalsIgnoreCase(colorstr)) {
                        temp = COLOR_VALS[i];
                        colornum = i;
                        break;
                    }
                }
            } else {  // RGB or RGBA
                // delete the '{' and '}' characters first
                String str = colorstr;
                int pos;
                while ((pos = str.indexOf('{')) >= 0)
                    str = str.substring(0, pos) + str.substring(pos + 1, str.length());
                while ((pos = str.indexOf('}')) >= 0)
                    str = str.substring(0, pos) + str.substring(pos + 1, str.length());

                StringTokenizer st2 = new StringTokenizer(str, ",", false);
                int tokens = st2.countTokens();

                if (tokens == 3 || tokens == 4) {
                    int red = 0, green = 0, blue = 0, alpha = 255;
                    String token = null;
                    try {
                        token = st2.nextToken().trim();
                        red = Integer.valueOf(token).intValue();
                        token = st2.nextToken().trim();
                        green = Integer.valueOf(token).intValue();
                        token = st2.nextToken().trim();
                        blue = Integer.valueOf(token).intValue();
                        if (tokens == 4) {
                            token = st2.nextToken().trim();
                            alpha = Integer.valueOf(token).intValue();
                        }
                    } catch (NumberFormatException nfe) {
                        String template = errorstrings.getString("Error_number_format");
                        Object[] arguments = { token };
                        throw new JuggleExceptionUser("Ring prop color: " +
                                MessageFormat.format(template, arguments));
                    }
                    temp = new Color(red, green, blue, alpha);
                } else
                    throw new JuggleExceptionUser("Ring prop color: " +
                            errorstrings.getString("Error_token_count"));
            }

            if (temp != null)
                color = temp;
            else {
                String template = errorstrings.getString("Error_prop_color");
                Object[] arguments = { colorstr };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }

        String outsidestr = pl.getParameter("outside");
        if (outsidestr != null) {
            try {
                double temp = JLFunc.parseDouble(outsidestr);
                if (temp > 0)
                    outside_diam = temp;
                else
                    throw new JuggleExceptionUser(errorstrings.getString("Error_prop_diameter"));
            } catch (NumberFormatException nfe) {
                String template = errorstrings.getString("Error_number_format");
                Object[] arguments = { "diam" };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }

        String insidestr = pl.getParameter("inside");
        if (insidestr != null) {
            try {
                double temp = JLFunc.parseDouble(insidestr);
                if (temp > 0)
                    inside_diam = temp;
                else
                    throw new JuggleExceptionUser(errorstrings.getString("Error_prop_diameter"));
            } catch (NumberFormatException nfe) {
                String template = errorstrings.getString("Error_number_format");
                Object[] arguments = { "diam" };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
    }

    @Override
    public Coordinate getMax() {
        return new Coordinate(outside_diam / 2, 0, outside_diam / 2);
    }

    @Override
    public Coordinate getMin() {
        return new Coordinate(-outside_diam / 2, 0, -outside_diam / 2);
    }

    @Override
    public double getWidth() {
        return 0.05 * outside_diam;
    }

    @Override
    public Image getProp2DImage(double zoom, double[] camangle) {
        if (image == null || zoom != lastzoom ||
                    camangle[0] != lastcamangle[0] || camangle[1] != lastcamangle[1]) {
            // first call or display resized?
            redrawImage(zoom, camangle);
        }
        return image;
    }

    @Override
    public Dimension getProp2DSize(double zoom) {
        if (size == null || zoom != lastzoom)  // first call or display resized?
            redrawImage(zoom, lastcamangle);
        return size;
    }

    @Override
    public Dimension getProp2DCenter(double zoom) {
        if (center == null || zoom != lastzoom)  // first call or display resized?
            redrawImage(zoom, lastcamangle);
        return center;
    }

    @Override
    public Dimension getProp2DGrip(double zoom) {
        if (grip == null || zoom != lastzoom)  // first call or display resized?
            redrawImage(zoom, lastcamangle);
        return grip;
    }

    private void redrawImage(double zoom, double[] camangle) {
        int outside_pixel_diam = (int)(0.5 + zoom * outside_diam);
        int inside_pixel_diam = (int)(0.5 + zoom * inside_diam);

        double c0 = Math.cos(camangle[0]);
        double s0 = Math.sin(camangle[0]);
        double s1 = Math.sin(camangle[1]);

        int width = (int)(outside_pixel_diam * Math.abs(s0*s1));
        if (width < 2)
            width = 2;
        int height = outside_pixel_diam;
        if (height < 2)
            height = 2;

        int inside_width = (int)(inside_pixel_diam * Math.abs(s0*s1));
        if (inside_width == width)
            inside_width -= 2;

        int inside_height = inside_pixel_diam;
        if (inside_height == height)
            inside_height -= 2;

        // The angle of rotation of the ring.
        double term1 = Math.sqrt(c0*c0 / (1 - s0*s0*s1*s1));
        double angle = (term1 < 1) ? Math.acos(term1) : 0;
        if (c0*s0 > 0)
            angle = -angle;
        double sa = Math.sin(angle);
        double ca = Math.cos(angle);

        int pxmin = 0, pxmax = 0, pymin = 0, pymax = 0;
        for (int i = 0; i < POLYSIDES; i++) {
            double theta = (double)i * 2 * Math.PI / (double)POLYSIDES;
            double x = (double)width * Math.cos(theta) * 0.5;
            double y = (double)height * Math.sin(theta) * 0.5;
            px[i] = (int)(ca*x - sa*y + 0.5);
            py[i] = (int)(ca*y + sa*x + 0.5);
            if (i == 0 || px[i] < pxmin)
                pxmin = px[i];
            if (i == 0 || px[i] > pxmax)
                pxmax = px[i];
            if (i == 0 || py[i] < pymin)
                pymin = py[i];
            if (i == 0 || py[i] > pymax)
                pymax = py[i];
        }

        int bbwidth = pxmax - pxmin + 1;
        int bbheight = pymax - pymin + 1;
        size = new Dimension(bbwidth, bbheight);

        image = new BufferedImage(bbwidth, bbheight, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = image.createGraphics();

        /*
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);
        */
        g.setColor(color);
        for (int i = 0; i < POLYSIDES; i++) {
            px[i] -= pxmin;
            py[i] -= pymin;
        }
        g.fillPolygon(px, py, POLYSIDES);

        // make the transparent hole in the center
        g.setComposite(AlphaComposite.Src);
        g.setColor(new Color(1f, 1f, 1f, 0f));

        for (int i = 0; i < POLYSIDES; i++) {
            double theta = (double)i * 2.0 * Math.PI / (double)POLYSIDES;
            double x = (double)inside_width * Math.cos(theta) * 0.5;
            double y = (double)inside_height * Math.sin(theta) * 0.5;
            px[i] = (int)(ca*x - sa*y + 0.5) - pxmin;
            py[i] = (int)(ca*y + sa*x + 0.5) - pymin;
        }
        g.fillPolygon(px, py, POLYSIDES);

        center = new Dimension(bbwidth / 2, bbheight / 2);

        int gripx = (s0 < 0) ? (bbwidth - 1) : 0;
        double bbw = sa*sa + ca*ca*Math.abs(s0*s1);
        double dsq = s0*s0*s1*s1*ca*ca + sa*sa - bbw*bbw;
        double d = (dsq > 0) ? Math.sqrt(dsq) : 0;
        if (c0 > 0)
            d = -d;
        int gripy = (int)((double)outside_pixel_diam * d) + bbheight / 2;
        grip = new Dimension(gripx, gripy);

        lastzoom = zoom;
        lastcamangle = new double[] {camangle[0], camangle[1]};
    }
}
