// AnimationPrefs.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.core;

import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

import jugglinglab.jml.JMLPattern;
import jugglinglab.util.*;
import jugglinglab.view.View;


public class AnimationPrefs {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    public static final int     GROUND_AUTO = 0;    // must be sequential
    public static final int     GROUND_ON = 1;      // starting from 0
    public static final int     GROUND_OFF = 2;

    public static final int     width_def = 400;
    public static final int     height_def = 450;
    public static final double  fps_def = 60.0;
    public static final double  slowdown_def = 2.0;
    public static final int     border_def = 0;
    public static final int     showGround_def = GROUND_AUTO;
    public static final boolean stereo_def = false;
    public static final boolean startPause_def = false;
    public static final boolean mousePause_def = false;
    public static final boolean catchSound_def = false;
    public static final boolean bounceSound_def;
    public static final int     view_def = View.VIEW_NONE;

    static {
        String osname = System.getProperty("os.name").toLowerCase();
        // audio clip playback seems to block on Linux
        bounceSound_def = !osname.startsWith("linux");
    }

    public int      width = width_def;
    public int      height = height_def;
    public double   fps = fps_def;
    public double   slowdown = slowdown_def;
    public int      border = border_def;
    public int      showGround = showGround_def;
    public boolean  stereo = stereo_def;
    public boolean  startPause = startPause_def;
    public boolean  mousePause = mousePause_def;
    public boolean  catchSound = catchSound_def;
    public boolean  bounceSound = bounceSound_def;
    public double[] camangle;               // in degrees! null means use default
    public int      view = view_def;        // one of the values in View
    public int[]    hideJugglers;


    public AnimationPrefs() { super(); }

    public AnimationPrefs(AnimationPrefs jc) {
        if (jc.width > 0)           this.width = jc.width;
        if (jc.height > 0)          this.height = jc.height;
        if (jc.slowdown >= 0.0)     this.slowdown = jc.slowdown;
        if (jc.fps >= 0.0)          this.fps = jc.fps;
        if (jc.border >= 0)         this.border = jc.border;
        this.showGround = jc.showGround;
        this.stereo = jc.stereo;
        this.startPause = jc.startPause;
        this.mousePause = jc.mousePause;
        this.catchSound = jc.catchSound;
        this.bounceSound = jc.bounceSound;
        if (jc.camangle != null)
            this.camangle = jc.camangle.clone();
        this.view = jc.view;
        if (jc.hideJugglers != null)
            this.hideJugglers = jc.hideJugglers.clone();
    }

    public AnimationPrefs fromParameters(ParameterList pl) throws JuggleExceptionUser {
        int     tempint;
        double  tempdouble;
        String  value = null;

        if ((value = pl.removeParameter("stereo")) != null)
            this.stereo = Boolean.parseBoolean(value);
        if ((value = pl.removeParameter("startpaused")) != null)
            this.startPause = Boolean.parseBoolean(value);
        if ((value = pl.removeParameter("mousepause")) != null)
            this.mousePause = Boolean.parseBoolean(value);
        if ((value = pl.removeParameter("catchsound")) != null)
            this.catchSound = Boolean.parseBoolean(value);
        if ((value = pl.removeParameter("bouncesound")) != null)
            this.bounceSound = Boolean.parseBoolean(value);
        if ((value = pl.removeParameter("fps")) != null) {
            try {
                tempdouble = Double.parseDouble(value);
                this.fps = tempdouble;
            } catch (NumberFormatException e) {
                String template = errorstrings.getString("Error_number_format");
                Object[] arguments = { "fps" };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
        if ((value = pl.removeParameter("slowdown")) != null) {
            try {
                tempdouble = Double.parseDouble(value);
                this.slowdown = tempdouble;
            } catch (NumberFormatException e) {
                String template = errorstrings.getString("Error_number_format");
                Object[] arguments = { "slowdown" };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
        if ((value = pl.removeParameter("border")) != null) {
            try {
                tempint = Integer.parseInt(value);
                this.border = tempint;
            } catch (NumberFormatException e) {
                String template = errorstrings.getString("Error_number_format");
                Object[] arguments = { "border" };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
        if ((value = pl.removeParameter("width")) != null) {
            try {
                tempint = Integer.parseInt(value);
                this.width = tempint;
            } catch (NumberFormatException e) {
                String template = errorstrings.getString("Error_number_format");
                Object[] arguments = { "width" };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
        if ((value = pl.removeParameter("height")) != null) {
            try {
                tempint = Integer.parseInt(value);
                this.height = tempint;
            } catch (NumberFormatException e) {
                String template = errorstrings.getString("Error_number_format");
                Object[] arguments = { "height" };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
        if ((value = pl.removeParameter("showground")) != null) {
            if (value.equalsIgnoreCase("auto"))
                this.showGround = GROUND_AUTO;
            else if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on")
                        || value.equalsIgnoreCase("yes"))
                this.showGround = GROUND_ON;
            else if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("off")
                        || value.equalsIgnoreCase("no"))
                this.showGround = GROUND_OFF;
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

                this.camangle = new double[2];
                this.camangle[0] = ca[0];
                this.camangle[1] = ca[1];
            } catch (NumberFormatException e) {
                String template = errorstrings.getString("Error_number_format");
                Object[] arguments = { "camangle" };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
        if ((value = pl.removeParameter("view")) != null) {
            this.view = -1;
            for (int view_index = 0; view_index < View.viewNames.length; view_index++)
                if (value.equalsIgnoreCase(View.viewNames[view_index]))
                    this.view = view_index + 1;
            if (this.view == -1) {
                String template = errorstrings.getString("Error_unrecognized_view");
                Object[] arguments = { "'" + value + "'" };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
        if ((value = pl.removeParameter("hidejugglers")) != null) {
            value = value.replace("(", "").replace(")", "");

            StringTokenizer st = new StringTokenizer(value, ",");
            int numjugglers = st.countTokens();
            this.hideJugglers = new int[numjugglers];

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

    public String toString() {
        String result = "";

        if (this.width != width_def)
            result += "width=" + this.width + ";";
        if (this.height != height_def)
            result += "height=" + this.height + ";";
        if (this.fps != fps_def)
            result += "fps=" + JLFunc.toStringTruncated(this.fps, 2) + ";";
        if (this.slowdown != slowdown_def)
            result += "slowdown=" + JLFunc.toStringTruncated(this.slowdown, 2) + ";";
        if (this.border != border_def)
            result += "border=" + this.border + ";";
        if (this.showGround != showGround_def) {
            switch (this.showGround) {
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
        if (this.stereo != stereo_def)
            result += "stereo=" + this.stereo + ";";
        if (this.startPause != startPause_def)
            result += "startpaused=" + this.startPause + ";";
        if (this.mousePause != mousePause_def)
            result += "mousepause=" + this.mousePause + ";";
        if (this.catchSound != catchSound_def)
            result += "catchsound=" + this.catchSound + ";";
        if (this.bounceSound != bounceSound_def)
            result += "bouncesound=" + this.bounceSound + ";";
        if (this.camangle != null)
            result += "camangle=(" + this.camangle[0] + "," + this.camangle[1] + ");";
        if (this.view != view_def)
            result += "view=" + View.viewNames[this.view - 1] + ";";
        if (this.hideJugglers != null) {
            result += "hidejugglers=(";
            for (int i = 0; i < this.hideJugglers.length; i++) {
                result += Integer.toString(hideJugglers[i]);
                if (i != this.hideJugglers.length - 1)
                    result += ",";
            }
            result += ");";
        }

        if (result.length() != 0)
            result = result.substring(0, result.length() - 1);

        return result;
    }
}
