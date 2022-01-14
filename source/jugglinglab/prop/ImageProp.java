// ImageProp.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.prop;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.net.MalformedURLException;
import java.io.IOException;
import java.text.MessageFormat;
import javax.imageio.ImageIO;

import jugglinglab.util.*;


public class ImageProp extends Prop {
    static URL image_url_default;
    static {
        image_url_default = ImageProp.class.getResource("/ball.png");
    }

    protected URL url;
    protected BufferedImage image;
    protected BufferedImage scaled_image;
    protected final double width_def = 10.0f;  // in centimeters
    protected double width;
    protected double height;
    protected Dimension size;
    protected Dimension center;
    protected Dimension grip;

    private double last_zoom;


    public ImageProp() throws JuggleExceptionUser {
        if (image_url_default == null)
            throw new JuggleExceptionUser("ImageProp error: Default image not set");
        url = image_url_default;
        loadImage();
        rescaleImage(1.0);
    }

    private void loadImage() throws JuggleExceptionUser {
        try {
            MediaTracker mt = new MediaTracker(new Component() {});
            image = ImageIO.read(url);
            mt.addImage(image, 0);
            // Try to laod the image
            try {
                mt.waitForAll();
            } catch (InterruptedException ex) {}

            if (mt.isErrorAny()) {
                image = null;
                // This could also be bad image data, but it is usually a nonexistent file.
                throw new JuggleExceptionUser(errorstrings.getString("Error_bad_file"));
            }

            double aspectRatio = ((double)image.getHeight()) / ((double)image.getWidth());
            width = width_def;
            height = width_def * aspectRatio;
        } catch (IOException e) {
            throw new JuggleExceptionUser(errorstrings.getString("Error_bad_file"));
        } catch (SecurityException se) {
            throw new JuggleExceptionUser(errorstrings.getString("Error_security_restriction"));
        }
    }

    private void rescaleImage(double zoom) {
        int image_pixel_width = (int)(0.5 + zoom * width);
        if (image_pixel_width < 1)
            image_pixel_width = 1;
        int image_pixel_height = (int)(0.5 + zoom * height);
        if (image_pixel_height < 1)
            image_pixel_height = 1;
        size = new Dimension(image_pixel_width, image_pixel_height);
        center = new Dimension(image_pixel_width/2, image_pixel_height/2);

        int offsetx = image_pixel_width / 2;
        int offsety = image_pixel_height;
        grip = new Dimension(offsetx, offsety);

        last_zoom = zoom;

        scaled_image = new BufferedImage(image_pixel_width, image_pixel_height, image.getType());
        Graphics2D g = scaled_image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, image_pixel_width, image_pixel_height,
                    0, 0, image.getWidth(), image.getHeight(), null);
        g.dispose();
    }

    // View methods

    @Override
    public String getType() {
        return "Image";
    }

    @Override
    public Color getEditorColor() {
        // The color that shows up in the visual editor
        // We could try to get an average color for the image
        return Color.white;
    }

    @Override
    public ParameterDescriptor[] getParameterDescriptors() {
        ParameterDescriptor[] result = new ParameterDescriptor[2];

        result[0] = new ParameterDescriptor("image", ParameterDescriptor.TYPE_ICON,
                            null, image_url_default, url);
        result[1] = new ParameterDescriptor("width", ParameterDescriptor.TYPE_FLOAT,
                            null, Double.valueOf(width_def), Double.valueOf(width));

        return result;
    }

    @Override
    protected void init(String st) throws JuggleExceptionUser {
        if (st == null) return;

        ParameterList pl = new ParameterList(st);

        String sourcestr = pl.getParameter("image");
        if (sourcestr != null) {
            try {
                url = new URL(sourcestr);
                loadImage();
            } catch (MalformedURLException ex) {
                throw new JuggleExceptionUser(errorstrings.getString("Error_malformed_URL"));
            }
        }

        String widthstr = pl.getParameter("width");
        if (widthstr != null) {
            try {
                double temp = Double.valueOf(widthstr).doubleValue();
                if (temp > 0) {
                    width = temp;
                    double aspectRatio = ((double)image.getHeight(null)) / ((double)image.getWidth(null));
                    height = width * aspectRatio;
                }
                else
                    throw new NumberFormatException();
            } catch (NumberFormatException nfe) {
                String template = errorstrings.getString("Error_number_format");
                Object[] arguments = { "width" };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
    }

    @Override
    public Image getProp2DImage(double zoom, double[] camangle) {
        if (zoom != last_zoom)
            rescaleImage(zoom);
        return scaled_image;
    }

    @Override
    public Coordinate getMax() {
        return new Coordinate(width / 2.0, 0.0, width);
    }

    @Override
    public Coordinate getMin() {
        return new Coordinate(-width / 2.0, 0.0, 0.0);
    }

    @Override
    public double getWidth() {
        return width;
    }

    @Override
    public Dimension getProp2DSize(double zoom) {
        if (size == null || zoom != last_zoom)
            rescaleImage(zoom);
        return size;
    }

    @Override
    public Dimension getProp2DCenter(double zoom) {
        if (center == null || zoom != last_zoom)
            rescaleImage(zoom);
        return center;
    }

    @Override
    public Dimension getProp2DGrip(double zoom) {
        if (grip == null || zoom != last_zoom)
            rescaleImage(zoom);
        return grip;
    }
}
