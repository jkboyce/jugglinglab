// AnimationPrefs.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.core;

import java.awt.*;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

import jugglinglab.jml.JMLPattern;
import jugglinglab.util.*;
import jugglinglab.view.View;


public class AnimationPrefs {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    public static final int GROUND_AUTO = 0;  // must be sequential
    public static final int GROUND_ON = 1;  // starting from 0
    public static final int GROUND_OFF = 2;

    // default values of all items
    public static final int width_def = 400;
    public static final int height_def = 450;
    public static final double fps_def;
    public static final double slowdown_def = 2.0;
    public static final int border_def = 0;
    public static final int showGround_def = GROUND_AUTO;
    public static final boolean stereo_def = false;
    public static final boolean startPause_def = false;
    public static final boolean mousePause_def = false;
    public static final boolean catchSound_def = false;
    public static final boolean bounceSound_def;
    public static final int view_def = View.VIEW_NONE;

    static {
        // audio clip playback seems to block on Linux
        if (jugglinglab.JugglingLab.isLinux)
            bounceSound_def = false;
        else
            bounceSound_def = false;

        // set default `fps` to screen refresh rate, if possible
        double fps_screen = 0.0;

        try {
            GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice[] devices = env.getScreenDevices();
            if (devices.length > 0)
                fps_screen = (double)devices[0].getDisplayMode().getRefreshRate();
                // getRefreshRate() returns 0 when refresh is unknown
        } catch (Exception e) {
            // HeadlessException when running headless (from CLI)
        }

        fps_def = (fps_screen < 20.0 ? 60.0 : fps_screen);
    }

    public int width = width_def;
    public int height = height_def;
    public double fps = fps_def;
    public double slowdown = slowdown_def;
    public int border = border_def;
    public int showGround = showGround_def;
    public boolean stereo = stereo_def;
    public boolean startPause = startPause_def;
    public boolean mousePause = mousePause_def;
    public boolean catchSound = catchSound_def;
    public boolean bounceSound = bounceSound_def;
    public double[] camangle;  // in degrees! null means use default
    public int view = view_def;  // one of the values in View
    public int[] hideJugglers;


    public AnimationPrefs() {
        super();
    }

    public AnimationPrefs(AnimationPrefs jc) {
        if (jc.width > 0)
            width = jc.width;
        if (jc.height > 0)
            height = jc.height;
        if (jc.slowdown >= 0.0)
            slowdown = jc.slowdown;
        if (jc.fps >= 0.0)
            fps = jc.fps;
        if (jc.border >= 0)
            border = jc.border;
        showGround = jc.showGround;
        stereo = jc.stereo;
        startPause = jc.startPause;
        mousePause = jc.mousePause;
        catchSound = jc.catchSound;
        bounceSound = jc.bounceSound;
        if (jc.camangle != null)
            camangle = jc.camangle.clone();
        view = jc.view;
        if (jc.hideJugglers != null)
            hideJugglers = jc.hideJugglers.clone();
    }

    public AnimationPrefs fromParameters(ParameterList pl) throws JuggleExceptionUser {
        int tempint;
        double tempdouble;
        String value = null;

        if ((value = pl.removeParameter("stereo")) != null)
            stereo = Boolean.parseBoolean(value);
        if ((value = pl.removeParameter("startpaused")) != null)
            startPause = Boolean.parseBoolean(value);
        if ((value = pl.removeParameter("mousepause")) != null)
            mousePause = Boolean.parseBoolean(value);
        if ((value = pl.removeParameter("catchsound")) != null)
            catchSound = Boolean.parseBoolean(value);
        if ((value = pl.removeParameter("bouncesound")) != null)
            bounceSound = Boolean.parseBoolean(value);
        if ((value = pl.removeParameter("fps")) != null) {
            try {
                tempdouble = Double.parseDouble(value);
                fps = tempdouble;
            } catch (NumberFormatException e) {
                String template = errorstrings.getString("Error_number_format");
                Object[] arguments = { "fps" };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
        if ((value = pl.removeParameter("slowdown")) != null) {
            try {
                tempdouble = Double.parseDouble(value);
                slowdown = tempdouble;
            } catch (NumberFormatException e) {
                String template = errorstrings.getString("Error_number_format");
                Object[] arguments = { "slowdown" };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
        if ((value = pl.removeParameter("border")) != null) {
            try {
                tempint = Integer.parseInt(value);
                border = tempint;
            } catch (NumberFormatException e) {
                String template = errorstrings.getString("Error_number_format");
                Object[] arguments = { "border" };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
        if ((value = pl.removeParameter("width")) != null) {
            try {
                tempint = Integer.parseInt(value);
                width = tempint;
            } catch (NumberFormatException e) {
                String template = errorstrings.getString("Error_number_format");
                Object[] arguments = { "width" };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
        if ((value = pl.removeParameter("height")) != null) {
            try {
                tempint = Integer.parseInt(value);
                height = tempint;
            } catch (NumberFormatException e) {
                String template = errorstrings.getString("Error_number_format");
                Object[] arguments = { "height" };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
        if ((value = pl.removeParameter("showground")) != null) {
            if (value.equalsIgnoreCase("auto"))
                showGround = GROUND_AUTO;
            else if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on")
                        || value.equalsIgnoreCase("yes"))
                showGround = GROUND_ON;
            else if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("off")
                        || value.equalsIgnoreCase("no"))
                showGround = GROUND_OFF;
            else {
                String template = errorstrings.getString("Error_showground_value");
                Object[] arguments = { value };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
        if ((value = pl.removeParameter("camangle")) != null) {
            try {
                double[] ca = new double[2];
                ca[1] = 90.0;        // default if second angle isn't given

                value = value.replace("(", "").replace(")", "");
                value = value.replace("{", "").replace("}", "");

                StringTokenizer st = new StringTokenizer(value, ",");
                int numangles = st.countTokens();
                if (numangles > 2) {
                    String template = errorstrings.getString("Error_too_many_elements");
                    Object[] arguments = { "camangle" };
                    throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                }

                for (int i = 0; i < numangles; i++)
                    ca[i] = Double.parseDouble(st.nextToken().trim());

                camangle = new double[2];
                camangle[0] = ca[0];
                camangle[1] = ca[1];
            } catch (NumberFormatException e) {
                String template = errorstrings.getString("Error_number_format");
                Object[] arguments = { "camangle" };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
        if ((value = pl.removeParameter("view")) != null) {
            view = -1;
            for (int view_index = 0; view_index < View.viewNames.length; view_index++)
                if (value.equalsIgnoreCase(View.viewNames[view_index]))
                    view = view_index + 1;
            if (view == -1) {
                String template = errorstrings.getString("Error_unrecognized_view");
                Object[] arguments = { "'" + value + "'" };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
        if ((value = pl.removeParameter("hidejugglers")) != null) {
            value = value.replace("(", "").replace(")", "");

            StringTokenizer st = new StringTokenizer(value, ",");
            int numjugglers = st.countTokens();
            hideJugglers = new int[numjugglers];

            try {
                for (int i = 0; i < numjugglers; i++)
                    hideJugglers[i] = Integer.parseInt(st.nextToken().trim());
            } catch (NumberFormatException e) {
                String template = errorstrings.getString("Error_number_format");
                Object[] arguments = { "hidejugglers" };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
        return this;
    }

    public AnimationPrefs fromString(String s) throws JuggleExceptionUser {
        ParameterList pl = new ParameterList(s);
        fromParameters(pl);
        pl.errorIfParametersLeft();
        return this;
    }

    public Dimension getSize() {
        return new Dimension(width, height);
    }

    public void setSize(Dimension dim) {
        width = dim.width;
        height = dim.height;
    }

    @Override
    public String toString() {
        String result = "";

        if (width != width_def)
            result += "width=" + width + ";";
        if (height != height_def)
            result += "height=" + height + ";";
        if (fps != fps_def)
            result += "fps=" + JLFunc.toStringRounded(fps, 2) + ";";
        if (slowdown != slowdown_def)
            result += "slowdown=" + JLFunc.toStringRounded(slowdown, 2) + ";";
        if (border != border_def)
            result += "border=" + border + ";";
        if (showGround != showGround_def) {
            switch (showGround) {
                case GROUND_AUTO:
                    result += "showground=auto;";
                    break;
                case GROUND_ON:
                    result += "showground=true;";
                    break;
                case GROUND_OFF:
                    result += "showground=false;";
                    break;
            }
        }
        if (stereo != stereo_def)
            result += "stereo=" + stereo + ";";
        if (startPause != startPause_def)
            result += "startpaused=" + startPause + ";";
        if (mousePause != mousePause_def)
            result += "mousepause=" + mousePause + ";";
        if (catchSound != catchSound_def)
            result += "catchsound=" + catchSound + ";";
        if (bounceSound != bounceSound_def)
            result += "bouncesound=" + bounceSound + ";";
        if (camangle != null)
            result += "camangle=(" + camangle[0] + "," + camangle[1] + ");";
        if (view != view_def)
            result += "view=" + View.viewNames[view - 1] + ";";
        if (hideJugglers != null) {
            result += "hidejugglers=(";
            for (int i = 0; i < hideJugglers.length; i++) {
                result += Integer.toString(hideJugglers[i]);
                if (i != hideJugglers.length - 1)
                    result += ",";
            }
            result += ");";
        }

        if (result.length() != 0)
            result = result.substring(0, result.length() - 1);

        return result;
    }
}
