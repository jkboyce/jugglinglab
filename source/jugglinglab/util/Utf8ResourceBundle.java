// Utf8ResourceBundle.java
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


public abstract class Utf8ResourceBundle {
	
	public static final ResourceBundle getBundle(String baseName) {
		ResourceBundle bundle = ResourceBundle.getBundle(baseName);
		return createUtf8PropertyResourceBundle(bundle);
	}
	
	public static final ResourceBundle getBundle(String baseName, Locale locale) {
		ResourceBundle bundle = ResourceBundle.getBundle(baseName, locale); 
		return createUtf8PropertyResourceBundle(bundle);
	}
	
	public static ResourceBundle getBundle(String baseName, Locale locale, ClassLoader loader) {
		ResourceBundle bundle = ResourceBundle.getBundle(baseName, locale); 
		return createUtf8PropertyResourceBundle(bundle);
	}
	
	private static ResourceBundle createUtf8PropertyResourceBundle(ResourceBundle bundle) {
		if (!(bundle instanceof PropertyResourceBundle)) return bundle;
		
		return new Utf8PropertyResourceBundle((PropertyResourceBundle)bundle);
	}
	
	private static class Utf8PropertyResourceBundle extends ResourceBundle {
		PropertyResourceBundle bundle;
		
		private Utf8PropertyResourceBundle(PropertyResourceBundle bundle) {
			this.bundle = bundle;
		}
		/* (non-Javadoc)
		 * @see java.util.ResourceBundle#getKeys()
		 */
		public Enumeration getKeys() {
			return bundle.getKeys();
		}
		/* (non-Javadoc)
		 * @see java.util.ResourceBundle#handleGetObject(java.lang.String)
		 */
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