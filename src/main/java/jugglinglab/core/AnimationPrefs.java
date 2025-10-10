//
// AnimationPrefs.java
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.core;

import java.awt.*;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.StringTokenizer;
import jugglinglab.util.*;
import jugglinglab.view.View;

public class AnimationPrefs {
  static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
  static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

  public static final int GROUND_AUTO = 0; // must be sequential
  public static final int GROUND_ON = 1; // starting from 0
  public static final int GROUND_OFF = 2;

  // default values of all items
  public static final int WIDTH_DEF = 400;
  public static final int HEIGHT_DEF = 450;
  public static final double FPS_DEF;
  public static final double SLOWDOWN_DEF = 2;
  public static final int BORDER_DEF = 0;
  public static final int SHOWGROUND_DEF = GROUND_AUTO;
  public static final boolean STEREO_DEF = false;
  public static final boolean STARTPAUSE_DEF = false;
  public static final boolean MOUSEPAUSE_DEF = false;
  public static final boolean CATCHSOUND_DEF = false;
  public static final boolean BOUNCESOUND_DEF;
  public static final int VIEW_DEF = View.VIEW_NONE;

  static {
    // audio clip playback seems to block on Linux
    if (jugglinglab.JugglingLab.isLinux) {
      BOUNCESOUND_DEF = false;
    } else {
      BOUNCESOUND_DEF = false;
    }

    // set default `fps` to screen refresh rate, if possible
    double fps_screen = 0;

    try {
      GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
      GraphicsDevice[] devices = env.getScreenDevices();
      if (devices.length > 0) {
        fps_screen = (double) devices[0].getDisplayMode().getRefreshRate();
        // getRefreshRate() returns 0 when refresh is unknown
      }
    } catch (Exception e) {
      // HeadlessException when running headless (from CLI)
    }

    FPS_DEF = (fps_screen < 20 ? 60 : fps_screen);
  }

  public int width = WIDTH_DEF;
  public int height = HEIGHT_DEF;
  public double fps = FPS_DEF;
  public double slowdown = SLOWDOWN_DEF;
  public int border = BORDER_DEF;
  public int showGround = SHOWGROUND_DEF;
  public boolean stereo = STEREO_DEF;
  public boolean startPause = STARTPAUSE_DEF;
  public boolean mousePause = MOUSEPAUSE_DEF;
  public boolean catchSound = CATCHSOUND_DEF;
  public boolean bounceSound = BOUNCESOUND_DEF;
  public double[] camangle; // in degrees! null means use default
  public int view = VIEW_DEF; // one of the values in View
  public int[] hideJugglers;

  public AnimationPrefs() {
    super();
  }

  public AnimationPrefs(AnimationPrefs jc) {
    if (jc.width > 0) {
      width = jc.width;
    }
    if (jc.height > 0) {
      height = jc.height;
    }
    if (jc.slowdown >= 0) {
      slowdown = jc.slowdown;
    }
    if (jc.fps >= 0) {
      fps = jc.fps;
    }
    if (jc.border >= 0) {
      border = jc.border;
    }
    showGround = jc.showGround;
    stereo = jc.stereo;
    startPause = jc.startPause;
    mousePause = jc.mousePause;
    catchSound = jc.catchSound;
    bounceSound = jc.bounceSound;
    if (jc.camangle != null) {
      camangle = jc.camangle.clone();
    }
    view = jc.view;
    if (jc.hideJugglers != null) {
      hideJugglers = jc.hideJugglers.clone();
    }
  }

  public AnimationPrefs fromParameters(ParameterList pl) throws JuggleExceptionUser {
    int tempint;
    double tempdouble;
    String value = null;

    if ((value = pl.removeParameter("stereo")) != null) {
      stereo = Boolean.parseBoolean(value);
    }
    if ((value = pl.removeParameter("startpaused")) != null) {
      startPause = Boolean.parseBoolean(value);
    }
    if ((value = pl.removeParameter("mousepause")) != null) {
      mousePause = Boolean.parseBoolean(value);
    }
    if ((value = pl.removeParameter("catchsound")) != null) {
      catchSound = Boolean.parseBoolean(value);
    }
    if ((value = pl.removeParameter("bouncesound")) != null) {
      bounceSound = Boolean.parseBoolean(value);
    }
    if ((value = pl.removeParameter("fps")) != null) {
      try {
        tempdouble = Double.parseDouble(value);
        fps = tempdouble;
      } catch (NumberFormatException e) {
        String template = errorstrings.getString("Error_number_format");
        Object[] arguments = {"fps"};
        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
      }
    }
    if ((value = pl.removeParameter("slowdown")) != null) {
      try {
        tempdouble = Double.parseDouble(value);
        slowdown = tempdouble;
      } catch (NumberFormatException e) {
        String template = errorstrings.getString("Error_number_format");
        Object[] arguments = {"slowdown"};
        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
      }
    }
    if ((value = pl.removeParameter("border")) != null) {
      try {
        tempint = Integer.parseInt(value);
        border = tempint;
      } catch (NumberFormatException e) {
        String template = errorstrings.getString("Error_number_format");
        Object[] arguments = {"border"};
        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
      }
    }
    if ((value = pl.removeParameter("width")) != null) {
      try {
        tempint = Integer.parseInt(value);
        width = tempint;
      } catch (NumberFormatException e) {
        String template = errorstrings.getString("Error_number_format");
        Object[] arguments = {"width"};
        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
      }
    }
    if ((value = pl.removeParameter("height")) != null) {
      try {
        tempint = Integer.parseInt(value);
        height = tempint;
      } catch (NumberFormatException e) {
        String template = errorstrings.getString("Error_number_format");
        Object[] arguments = {"height"};
        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
      }
    }
    if ((value = pl.removeParameter("showground")) != null) {
      if (value.equalsIgnoreCase("auto")) {
        showGround = GROUND_AUTO;
      } else if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on")
          || value.equalsIgnoreCase("yes")) {
        showGround = GROUND_ON;
      } else if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("off")
          || value.equalsIgnoreCase("no")) {
        showGround = GROUND_OFF;
      } else {
        String template = errorstrings.getString("Error_showground_value");
        Object[] arguments = {value};
        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
      }
    }
    if ((value = pl.removeParameter("camangle")) != null) {
      try {
        double[] ca = new double[2];
        ca[1] = 90.0; // default if second angle isn't given

        value = value.replace("(", "").replace(")", "");
        value = value.replace("{", "").replace("}", "");

        StringTokenizer st = new StringTokenizer(value, ",");
        int numangles = st.countTokens();
        if (numangles > 2) {
          String template = errorstrings.getString("Error_too_many_elements");
          Object[] arguments = {"camangle"};
          throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
        }

        for (int i = 0; i < numangles; i++) {
          ca[i] = Double.parseDouble(st.nextToken().trim());
        }

        camangle = new double[2];
        camangle[0] = ca[0];
        camangle[1] = ca[1];
      } catch (NumberFormatException e) {
        String template = errorstrings.getString("Error_number_format");
        Object[] arguments = {"camangle"};
        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
      }
    }
    if ((value = pl.removeParameter("view")) != null) {
      view = -1;
      for (int view_index = 0; view_index < View.viewNames.length; view_index++) {
        if (value.equalsIgnoreCase(View.viewNames[view_index])) {
          view = view_index + 1;
        }
      }

      if (view == -1) {
        String template = errorstrings.getString("Error_unrecognized_view");
        Object[] arguments = {"'" + value + "'"};
        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
      }
    }
    if ((value = pl.removeParameter("hidejugglers")) != null) {
      value = value.replace("(", "").replace(")", "");

      StringTokenizer st = new StringTokenizer(value, ",");
      int numjugglers = st.countTokens();
      hideJugglers = new int[numjugglers];

      try {
        for (int i = 0; i < numjugglers; i++) {
          hideJugglers[i] = Integer.parseInt(st.nextToken().trim());
        }
      } catch (NumberFormatException e) {
        String template = errorstrings.getString("Error_number_format");
        Object[] arguments = {"hidejugglers"};
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

    if (width != WIDTH_DEF) {
      result += "width=" + width + ";";
    }
    if (height != HEIGHT_DEF) {
      result += "height=" + height + ";";
    }
    if (fps != FPS_DEF) {
      result += "fps=" + JLFunc.toStringRounded(fps, 2) + ";";
    }
    if (slowdown != SLOWDOWN_DEF) {
      result += "slowdown=" + JLFunc.toStringRounded(slowdown, 2) + ";";
    }
    if (border != BORDER_DEF) {
      result += "border=" + border + ";";
    }
    if (showGround != SHOWGROUND_DEF) {
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
    if (stereo != STEREO_DEF) {
      result += "stereo=" + stereo + ";";
    }
    if (startPause != STARTPAUSE_DEF) {
      result += "startpaused=" + startPause + ";";
    }
    if (mousePause != MOUSEPAUSE_DEF) {
      result += "mousepause=" + mousePause + ";";
    }
    if (catchSound != CATCHSOUND_DEF) {
      result += "catchsound=" + catchSound + ";";
    }
    if (bounceSound != BOUNCESOUND_DEF) {
      result += "bouncesound=" + bounceSound + ";";
    }
    if (camangle != null) {
      result += "camangle=(" + camangle[0] + "," + camangle[1] + ");";
    }
    if (view != VIEW_DEF) {
      result += "view=" + View.viewNames[view - 1] + ";";
    }
    if (hideJugglers != null) {
      result += "hidejugglers=(";
      for (int i = 0; i < hideJugglers.length; i++) {
        result += Integer.toString(hideJugglers[i]);
        if (i != hideJugglers.length - 1) {
          result += ",";
        }
      }
      result += ");";
    }

    if (result.length() != 0) {
      result = result.substring(0, result.length() - 1);
    }

    return result;
  }
}
