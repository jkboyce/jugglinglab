// VersionSpecific2.java
//
// Copyright 2004 by Jack Boyce (jboyce@users.sourceforge.net) and others

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

package jugglinglab.core;

import java.awt.*;
import java.awt.image.*;
import java.net.*;


public class VersionSpecific2 extends VersionSpecific {
    
    public void setAntialias(Graphics g) {
        if (g instanceof Graphics2D) {
            Graphics2D g2 = (Graphics2D)g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
    }
	
	public void setColorTransparent(Graphics g) {
		if (g instanceof Graphics2D) {
			Graphics2D g2 = (Graphics2D)g;
			g2.setComposite(AlphaComposite.Src);  // Change for VersionSpecific
			g2.setColor(new Color(1f, 1f, 1f, 0f));
		}
	}
    
    public Image makeImage(Component comp, int width, int height) {
        Image im = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        if (im == null)
            im = super.makeImage(comp, width, height);
        return im;
    }
	
	public URL getDefaultPropImage() {
		return images[1];
	}
}