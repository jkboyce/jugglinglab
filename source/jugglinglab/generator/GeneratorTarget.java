// GeneratorTarget.java
//
// Copyright 2020 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.generator;

import java.io.PrintStream;
import javax.swing.SwingUtilities;

import jugglinglab.core.PatternList;
import jugglinglab.core.PatternListWindow;


// This class is an adapter to handle the generated output.
// It can send output to a PatternList, PrintStream, or StringBuffer

public class GeneratorTarget {
    PatternList ltarget;
    PrintStream ptarget;
    StringBuffer btarget;
    String prefix;
    String suffix;

    public GeneratorTarget(PatternListWindow target) {
        this.ltarget = target.getPatternList();
    }

    public GeneratorTarget(PatternList target) {
        this.ltarget = target;
    }

    public GeneratorTarget(PrintStream ps) {
        this.ptarget = ps;
    }

    public GeneratorTarget(StringBuffer sb) {
        this.btarget = sb;
    }

    public void writePattern(String display, final String notation, String anim) {
        if (prefix != null) {
            display = prefix + display;
            anim = prefix + anim;
        }
        if (suffix != null) {
            display = display + suffix;
            anim = anim + suffix;
        }

        final String fdisplay = display;
        final String fanim = anim;

        if (ltarget != null) {
            // This method isn't necessarily being called from the event dispatch
            // thread, so do it this way to ensure the displayed list is only
            // updated from the event dispatch thread.
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    ltarget.addPattern(fdisplay, null, notation, fanim, null);
                }
            });
        }
        if (ptarget != null)
            ptarget.println(fdisplay);
        if (btarget != null)
            btarget.append(fdisplay + '\n');
    }

    // Sets a prefix and suffix for both the displayed string and animation string.
    public void setPrefixSuffix(String pr, String su) {
        prefix = pr;
        suffix = su;
    }

    public void setStatus(String display) {
        if (ptarget != null)
            ptarget.println(display);
    }
}
