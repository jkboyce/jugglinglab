// SiteswapNotationControl.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.notation;

import jugglinglab.jml.JMLPattern;
import jugglinglab.util.JuggleExceptionUser;
import jugglinglab.util.JuggleExceptionInternal;


public class SiteswapNotationControl extends MHNNotationControl {
    @Override
    public JMLPattern makePattern() throws JuggleExceptionUser, JuggleExceptionInternal {
        JMLPattern pat = (new SiteswapPattern()).fromString(getConfigString())
                                                .asJMLPattern();

        // if the hands setting not default, append its name to the title
        int index = cb1.getSelectedIndex();
        if (index > 0)
            pat.setTitle(pat.getTitle() + " " + cb1.getItemAt(index));

        return pat;
    }
}
