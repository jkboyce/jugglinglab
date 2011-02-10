// GeneratorTarget.java
//
// Copyright 2003 by Jack Boyce (jboyce@users.sourceforge.net) and others

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

package jugglinglab.generator;

import java.io.*;
import javax.swing.*;

import jugglinglab.core.*;


	// This is used as an adapter to handle the generator output
public class GeneratorTarget {
    PatternList ltarget = null;
    PrintStream ptarget = null;


    public GeneratorTarget(PatternListWindow target) {
        this.ltarget = target.getPatternList();
    }

    public GeneratorTarget(PatternList target) {
        this.ltarget = target;
    }

    public GeneratorTarget(PrintStream ps) {
        this.ptarget = ps;
    }


    public void writePattern(final String display, final String notation, final String anim) {
        if (ltarget != null) {
			// This method isn't necessarily being called from the event dispatch
			// thread, so do it this way to ensure the displayed list is only
			// updated from the event dispatch thread.
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					ltarget.addPattern(display, null, notation, anim, null);
				}
			});
		}
        if (ptarget != null)
            ptarget.println(display);
    }

    public void setStatus(String display) {
        if (ptarget != null)
            ptarget.println(display);
    }
}