// JLLocale.java
//
// Copyright 2011 by Jack Boyce (jboyce@users.sourceforge.net) and others

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
			bundle = ResourceBundle.getBundle(baseName);	// use default
		else
			bundle = ResourceBundle.getBundle(baseName, loc);
		
		if (!(bundle instanceof PropertyResourceBundle))
			return bundle;
		
		return new Utf8PropertyResourceBundle((PropertyResourceBundle)bundle);
	}
	
	private static class Utf8PropertyResourceBundle extends ResourceBundle {
		PropertyResourceBundle bundle;
		
		private Utf8PropertyResourceBundle(PropertyResourceBundle bundle) {
			this.bundle = bundle;
		}
		
		public Enumeration getKeys() {
			return bundle.getKeys();
		}
		
		protected Object handleGetObject(String key) {
			String value = (String)bundle.handleGetObject(key);
			try {
				return new String (value.getBytes("ISO-8859-1"),"UTF-8") ;
			} catch (UnsupportedEncodingException e) {
				// Shouldn't fail - but should we still add logging message?
				return null;
			} 
		}
	}
	
}