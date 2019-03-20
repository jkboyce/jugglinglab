// SiteswapNotationControl.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.notation;

public class SiteswapNotationControl extends MHNNotationControl {
    @Override
    public Pattern newPattern() {
        return new SiteswapPattern();
    }
}
