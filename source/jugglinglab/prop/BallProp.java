// BallProp.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.prop;

import java.util.*;
import java.awt.*;
import java.awt.image.*;
import java.text.MessageFormat;

import jugglinglab.core.*;
import jugglinglab.util.*;
import jugglinglab.renderer.*;


public class BallProp extends Prop {
    public static final String[] colornames =
        {
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

    public static final Color[] colorvals =
        {
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
            Color.yellow
        };

    protected static final int colornum_def = 8;  // red
    protected static final double diam_def = 10.0;  // in cm
    protected static final boolean highlight_def = false;

    protected double diam = diam_def;  // diameter, in cm
    protected int colornum = colornum_def;
    protected Color color;
    protected boolean highlight = highlight_def;

    protected BufferedImage ballimage;
    protected double lastzoom;
    protected Dimension size;
    protected Dimension center;
    protected Dimension grip;


    // Prop methods

    @Override
    public String getType() {
        return "Ball";
    }

    @Override
    public Color getEditorColor() {
        return color;
    }

    @Override
    public ParameterDescriptor[] getParameterDescriptors() {
        ParameterDescriptor[] result = new ParameterDescriptor[3];

        ArrayList<String> range = new ArrayList<String>();
        for (int i = 0; i < colornames.length; i++)
            range.add(colornames[i]);

        result[0] = new ParameterDescriptor("color", ParameterDescriptor.TYPE_CHOICE,
                            range, colornames[colornum_def], colornames[colornum]);
        result[1] = new ParameterDescriptor("diam", ParameterDescriptor.TYPE_FLOAT,
                            null, Double.valueOf(diam_def), Double.valueOf(diam));
        result[2] = new ParameterDescriptor("highlight", ParameterDescriptor.TYPE_BOOLEAN,
                            null, Boolean.valueOf(highlight_def), Boolean.valueOf(highlight));

        return result;
    }

    @Override
    protected void init(String st) throws JuggleExceptionUser {
        color = Color.red;

        if (st == null) return;
        ParameterList pl = new ParameterList(st);

        String colorstr = pl.getParameter("color");
        if (colorstr != null) {
            Color temp = null;
            if (colorstr.indexOf((int)',') == -1) { // color name
                for (int i = 0; i < colornames.length; i++) {
                    if (colornames[i].equalsIgnoreCase(colorstr)) {
                        temp = colorvals[i];
                        colornum = i;
                        break;
                    }
                }
            } else {    // RGB triplet
                     // delete the '{' and '}' characters first
                String str = colorstr;
                int pos;
                while ((pos = str.indexOf('{')) >= 0) {
                    str = str.substring(0,pos) + str.substring(pos+1,str.length());
                }
                while ((pos = str.indexOf('}')) >= 0) {
                    str = str.substring(0,pos) + str.substring(pos+1,str.length());
                }
                StringTokenizer st2 = new StringTokenizer(str, ",", false);
                if (st2.countTokens() == 3) {
                    int red = 0, green = 0, blue = 0;
                    String token = null;
                    try {
                        token = st2.nextToken().trim();
                        red = Integer.valueOf(token).intValue();
                        token = st2.nextToken().trim();
                        green = Integer.valueOf(token).intValue();
                        token = st2.nextToken().trim();
                        blue = Integer.valueOf(token).intValue();
                    } catch (NumberFormatException nfe) {
                        String template = errorstrings.getString("Error_number_format");
                        Object[] arguments = { token };
                        throw new JuggleExceptionUser("Ball prop color: " +
                                MessageFormat.format(template, arguments));
                    }
                    temp = new Color(red, green, blue);
                } else
                    throw new JuggleExceptionUser("Ball prop color: " +
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

        String diamstr = pl.getParameter("diam");
        if (diamstr != null) {
            try {
                double temp = JLFunc.parseDouble(diamstr.trim());
                if (temp > 0.0)
                    diam = temp;
                else
                    throw new JuggleExceptionUser(errorstrings.getString("Error_prop_diameter"));
            } catch (NumberFormatException nfe) {
                String template = errorstrings.getString("Error_number_format");
                Object[] arguments = { "diam" };
                throw new JuggleExceptionUser("Ball prop diameter: " +
                        MessageFormat.format(template, arguments));
            }
        }

        String highlightstr = pl.getParameter("highlight");
        if (highlightstr != null) {
            Boolean bhighlight = Boolean.valueOf(highlightstr);
            highlight = bhighlight.booleanValue();
        }
    }

    @Override
    public Coordinate getMax() {
        return new Coordinate(diam / 2.0, 0.0, diam / 2.0);
    }

    @Override
    public Coordinate getMin() {
        return new Coordinate(-diam / 2.0, 0, -diam / 2.0);
    }

    @Override
    public double getWidth() {
        return diam;
    }

    @Override
    public Image getProp2DImage(double zoom, double[] camangle) {
        if (ballimage == null || zoom != lastzoom)  // first call or display resized?
            recalc2D(zoom);
        return ballimage;
    }

    @Override
    public Dimension getProp2DSize(double zoom) {
        if (size == null || zoom != lastzoom)       // first call or display resized?
            recalc2D(zoom);
        return size;
    }

    @Override
    public Dimension getProp2DCenter(double zoom) {
        if (center == null || zoom != lastzoom)
            recalc2D(zoom);
        return center;
    }

    @Override
    public Dimension getProp2DGrip(double zoom) {
        if (grip == null || zoom != lastzoom)       // first call or display resized?
            recalc2D(zoom);
        return grip;
    }

    protected void recalc2D(double zoom) {
        int ball_pixel_size = (int)(0.5 + zoom * diam);
        if (ball_pixel_size < 1)
            ball_pixel_size = 1;
        int offsetx = -ball_pixel_size / 2;
        int offsety = -ball_pixel_size;

        // Create a ball image of diameter ball_pixel_size, and transparent background

        ballimage = new BufferedImage(ball_pixel_size + 1, ball_pixel_size + 1,
                            BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D ballg = ballimage.createGraphics();

        /*
        ballg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                               RenderingHints.VALUE_ANTIALIAS_ON);
        */
        if (highlight) {
            float highlightOvals = ball_pixel_size / 1.2f;  // Number of concentric circles to draw.
            float[] rgb = new float[3];
            rgb[0] = (float)color.getRed() / 255f;
            rgb[1] = (float)color.getGreen() / 255f;
            rgb[2] = (float)color.getBlue() / 255f;
            // Make the color a little darker so that there is some contrast.
            for (int i = 0; i < rgb.length; i++) {
                rgb[i] = rgb[i] / 2.5f;
            }

            ballg.setColor(new Color(rgb[0], rgb[1], rgb[2]));
            ballg.fillOval(0, 0, ball_pixel_size, ball_pixel_size); // Full sized ellipse.

            // Now draw the highlight on the ball.
            for (int i = 0; i < highlightOvals; i++) {
                // Calculate the new color
                for (int j = 0; j < rgb.length; j++) {
                    rgb[j] = Math.min(rgb[j] + (1f / highlightOvals), 1f);
                }
                ballg.setColor(new Color(rgb[0], rgb[1], rgb[2]));
                ballg.fillOval((int)(i/1.1), (int)(i/2.5),  // Literals control how fast highlight
                                                            // moves right and down respectively.
                               ball_pixel_size - (int)(i*1.3),   // These control how fast the
                               ball_pixel_size - (int)(i*1.3));  // highlight converges to a point.
            }
        } else {
            ballg.setColor(color);
            ballg.fillOval(0, 0, ball_pixel_size, ball_pixel_size);
        }

        size = new Dimension(ball_pixel_size, ball_pixel_size);
        center = new Dimension(ball_pixel_size/2, ball_pixel_size/2);
        grip = new Dimension(ball_pixel_size/2, ball_pixel_size/2);

        lastzoom = zoom;
    }
}
