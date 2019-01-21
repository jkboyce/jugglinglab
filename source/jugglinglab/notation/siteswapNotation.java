// siteswapNotation.java
//
// Copyright 2018 by Jack Boyce (jboyce@gmail.com) and others

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
