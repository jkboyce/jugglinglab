// SiteswapNotationControl.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.notation;

import jugglinglab.core.AnimationPrefs;
import jugglinglab.jml.JMLPattern;
import jugglinglab.util.JuggleExceptionUser;
import jugglinglab.util.JuggleExceptionInternal;
import jugglinglab.util.ParameterList;


public class SiteswapNotationControl extends MHNNotationControl {
    @Override
    public Pattern getPattern() throws JuggleExceptionUser, JuggleExceptionInternal {
        ParameterList pl = new ParameterList(getConfigString());

        // create a dummy AnimationPrefs object to extract any related parameters
        // from the ParameterList
        (new AnimationPrefs()).fromParameters(pl);

        SiteswapPattern p = new SiteswapPattern();
        p.fromParameters(pl);
        pl.errorIfParametersLeft();

        // if the hands setting is not default, append its name to the title
        int index = cb1.getSelectedIndex();
        if (index > 0)
            p.setTitle(p.getTitle() + " " + cb1.getItemAt(index));

        return p;
    }

    @Override
    public AnimationPrefs getAnimationPrefs() throws JuggleExceptionUser, JuggleExceptionInternal {
        ParameterList pl = new ParameterList(getConfigString());
        return (new AnimationPrefs()).fromParameters(pl);
    }
}
