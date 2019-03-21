// GeneratorTarget.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.generator;

import java.io.PrintStream;
import javax.swing.SwingUtilities;

import jugglinglab.core.PatternList;
import jugglinglab.core.PatternListWindow;


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
                @Override
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
