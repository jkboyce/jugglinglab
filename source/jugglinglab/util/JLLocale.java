// JLLocale.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.util;

import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;


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

        if (loc == null)
            bundle = ResourceBundle.getBundle(baseName);    // use default
        else
            bundle = ResourceBundle.getBundle(baseName, loc);

        if (!(bundle instanceof PropertyResourceBundle))
            return bundle;

        // RA: it seems that the default encoding of resources files is now UTF-8
        //          -> no need to convert it internaly anymore ??
        //          -> this works for French translation at least,
        //             as the GUIStrings_fr.properties file seems
        //             to be encoded in UTF-8
        //          -> others translations not checked
        // TODO: add an encoding check to decide if we should go through Utf8PropertyResourceBundle
        return bundle;
        //return new Utf8PropertyResourceBundle((PropertyResourceBundle)bundle);
    }

    private static class Utf8PropertyResourceBundle extends ResourceBundle {
        PropertyResourceBundle bundle;

        private Utf8PropertyResourceBundle(PropertyResourceBundle bundle) {
            this.bundle = bundle;
        }

        public Enumeration<String> getKeys() {
            return bundle.getKeys();
        }

        protected Object handleGetObject(String key) {
            String value = (String)bundle.handleGetObject(key);
            try {
                return new String(value.getBytes("ISO-8859-1"),"UTF-8");
            } catch (UnsupportedEncodingException e) {
                // Shouldn't fail - but should we still add logging message?
                return null;
            }
        }
    }

}
