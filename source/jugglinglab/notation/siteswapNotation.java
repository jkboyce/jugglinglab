// siteswapNotation.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.notation;

import java.util.*;

import jugglinglab.jml.*;
import jugglinglab.util.*;


public class siteswapNotation extends mhnNotation {
    public String getName() {
        return "Siteswap";
    }

    public JMLPattern getJMLPattern(String config) throws JuggleExceptionUser, JuggleExceptionInternal {

        // This entire method will need to be double-checked for suitability with siteswap2 notation

        siteswapPattern p = new siteswapPattern();

        // delete newlines and carriage returns from string
        int pos;
        while ((pos = config.indexOf('\n')) >= 0) {
            config = config.substring(0,pos) + config.substring(pos+1,config.length());
        }
        while ((pos = config.indexOf('\r')) >= 0) {
            config = config.substring(0,pos) + config.substring(pos+1,config.length());
        }

        p.parseInput(config);
        String origpattern = p.pattern;

        // see if we need to repeat the pattern to match hand or body periods:
        if (p.hands != null || p.bodies != null) {
            p.parsePattern();
            int patperiod = p.getNorepPeriod();

            int handperiod = 1;
            if (p.hands != null) {
                for (int i = 1; i <= p.getNumberOfJugglers(); i++)
                    handperiod = Permutation.lcm(handperiod, p.hands.getPeriod(i));
            }

            int bodyperiod = 1;
            if (p.bodies != null) {
                for (int i = 1; i <= p.getNumberOfJugglers(); i++)
                    bodyperiod = Permutation.lcm(bodyperiod, p.bodies.getPeriod(i));
            }

            int totalperiod = patperiod;
            totalperiod = Permutation.lcm(totalperiod, handperiod);
            totalperiod = Permutation.lcm(totalperiod, bodyperiod);

            if (totalperiod != patperiod) {
                int repeats = totalperiod / patperiod;
                p.pattern = "(" + p.pattern + "^" + repeats + ")";
            }
        }
        p.parsePattern();

        JMLPattern result = getJML(p);
        result.setTitle(origpattern);

        if (jugglinglab.core.Constants.DEBUG_LAYOUT)
            System.out.println(result);

        return result;
    }

}
