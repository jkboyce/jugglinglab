// JLLocale.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.util;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.ResourceBundle.Control;


public class JLLocale {
    static Locale locale;

    // convenience functions for managing locale
    public static void setLocale(Locale loc) {
        JLLocale.locale = loc;
    }

    public static Locale getLocale() {
        if (JLLocale.locale != null)
            return JLLocale.locale;

        return Locale.getDefault();
    }

    public static ResourceBundle getBundle(String baseName) {
        ResourceBundle bundle = null;
        Locale loc = JLLocale.getLocale();

        // In Juggling Lab we save our language files in UTF-8 format.
        // Java's default implementation of ResourceBundle.getBundle() loads
        // properties files in ISO 8859-1 format, so we use our own version
        // of ResourceBundle.Control to load as UTF-8:
        Control ctrl = new UTF8Control();

        if (loc == null)
            bundle = ResourceBundle.getBundle(baseName, ctrl);
        else
            bundle = ResourceBundle.getBundle(baseName, loc, ctrl);

        return bundle;
    }

    // see note above; this is to load bundle files with UTF-8 encodings

    private static class UTF8Control extends Control {
        public ResourceBundle newBundle
            (String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
                throws IllegalAccessException, InstantiationException, IOException
        {
            // The below is a copy of the default implementation.
            String bundleName = toBundleName(baseName, locale);
            String resourceName = toResourceName(bundleName, "properties");
            ResourceBundle bundle = null;
            InputStream stream = null;
            if (reload) {
                URL url = loader.getResource(resourceName);
                if (url != null) {
                    URLConnection connection = url.openConnection();
                    if (connection != null) {
                        connection.setUseCaches(false);
                        stream = connection.getInputStream();
                    }
                }
            } else {
                stream = loader.getResourceAsStream(resourceName);
            }
            if (stream != null) {
                try {
                    // Only this line is changed to make it to read properties files as UTF-8.
                    bundle = new PropertyResourceBundle(new InputStreamReader(stream, "UTF-8"));
                } finally {
                    stream.close();
                }
            }
            return bundle;
        }
    }
}
