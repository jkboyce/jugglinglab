// GeneratorTarget.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.generator;

import java.io.PrintStream;
import javax.swing.SwingUtilities;

import jugglinglab.core.Constants;
import jugglinglab.core.PatternListPanel;
import jugglinglab.core.PatternListWindow;
import jugglinglab.notation.SiteswapPattern;
import jugglinglab.util.*;


// This class is an adapter to handle the generated output. It can send output
// to a PatternListPanel, PrintStream, or StringBuffer.

public class GeneratorTarget {
    PatternListPanel ltarget;
    PrintStream ptarget;
    StringBuffer btarget;
    String prefix;
    String suffix;

    public GeneratorTarget(PatternListPanel target) {
        this.ltarget = target;
    }

    public GeneratorTarget(PrintStream ps) {
        this.ptarget = ps;
    }

    public GeneratorTarget(StringBuffer sb) {
        this.btarget = sb;
    }

    public void writePattern(String display, final String notation, String anim)
                                            throws JuggleExceptionInternal {
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

        if (Constants.VALIDATE_GENERATED_PATTERNS) {
            if (ltarget != null || ptarget != null) {
                if (notation.equalsIgnoreCase("siteswap") && anim.length() > 0) {
                    try {
                        (new SiteswapPattern()).fromString(anim);
                    } catch (JuggleException je) {
                        String msg = "Error: pattern \"" +
                                            anim + "\" did not validate";
                        System.out.println(msg);
                        throw new JuggleExceptionInternal(msg);
                    }
                    System.out.println("pattern \"" + anim + "\" validated");
                }
            }
        }

        if (ltarget != null) {
            // Note we may not be running on the event dispatch thread
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    ltarget.addPattern(fdisplay, null, notation, fanim);
                }
            });
        }
        if (ptarget != null)
            ptarget.println(fdisplay);
        if (btarget != null)
            btarget.append(fdisplay + '\n');
    }

    // Sets a prefix and suffix for both the displayed string and animation string
    public void setPrefixSuffix(String pr, String su) {
        prefix = pr;
        suffix = su;
    }

    // Messages like "# of patterns found" come through here
    public void setStatus(String display) {
        if (ltarget != null) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    ltarget.addPattern(display, null, null, null);
                }
            });
        }

        if (ptarget != null)
            ptarget.println(display);
    }
}
