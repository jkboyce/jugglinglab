// MHNPattern.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.notation;

import java.text.MessageFormat;
import java.util.*;
import java.awt.event.*;

import jugglinglab.core.*;
import jugglinglab.util.*;
import jugglinglab.jml.*;
import jugglinglab.path.Path;


// "Multi-Hand Notation" (name due to Ed Carstens) is a very general notation that
// defines a matrix of hands and discrete event times, and describes patterns as
// transitions between elements in this matrix.
//
// MHN has no standardized string (textual) representation. Hence this class is
// abstract because it lacks a fromString() method. It is however useful as a
// building block for other notations. For example we model siteswap notation
// as a type of MHN, with a parser to interpret siteswap notation into the
// internal MHN matrix representation.
//
// The main business of this class is to go from the matrix-based internal
// representation of an MHN pattern to JML. Any notation that builds on MHN can
// avoid duplicating this functionality.

public abstract class MHNPattern extends Pattern {
    protected static double bps_default = -1;  // calculate bps
    protected static double dwell_default = 1.3;
    protected static double gravity_default = 980;
    protected static double propdiam_default = 10;
    protected static double bouncefrac_default = 0.9;
    protected static double squeezebeats_default = 0.4;
    protected static String prop_default = "ball";
    //hss begin
    protected static boolean hold_default = false;
    protected static boolean dwellmax_default = true;
    //hss end

    // original config string:
    protected String config;

    // input parameters:
    protected String pattern;
    protected double bps_set = bps_default;
    protected double dwell = dwell_default;
    protected double gravity = gravity_default;
    protected double propdiam = propdiam_default;
    protected double bouncefrac = bouncefrac_default;
    protected double squeezebeats = squeezebeats_default;
    protected String prop = prop_default;
    protected String[] color;
    protected String title;
    //hss begin
    protected String hss;  
    protected boolean hold = hold_default; 
    protected boolean dwellmax = dwellmax_default;
    protected String handspec;
    protected double[] dwellarray;
    //hss end

    // internal variables:
    protected int numjugglers;
    protected int numpaths;
    protected int period;
    protected int max_occupancy;
    protected MHNThrow[][][][] th;
    protected MHNHands hands;
    protected MHNBody bodies;
    protected int max_throw;
    protected int indexes;
    protected ArrayList<MHNSymmetry> symmetry;
    protected double bps;

    public static final int RIGHT_HAND = 0;
    public static final int LEFT_HAND = 1;

    public int getNumberOfJugglers() {
        return numjugglers;
    }

    public int getNumberOfPaths() {
        return numpaths;
    }

    public int getPeriod() {
        return period;
    }

    public int getIndexes() {
        return indexes;
    }

    public int getMaxOccupancy() {
        return max_occupancy;
    }

    public int getMaxThrow() {
        return max_throw;
    }

    public MHNThrow[][][][] getThrows() {
        return th;
    }

    public int getNumberOfSymmetries() {
        return symmetry.size();
    }

    public String getPropName() {
        return prop;
    }

    public void addSymmetry(MHNSymmetry ss) {
        symmetry.add(ss);
    }

    public MHNSymmetry getSymmetry(int i) {
        return symmetry.get(i);
    }

    // Pull out the MHN-related parameters from the given list, leaving any
    // other parameters alone.
    //
    // Note this doesn't create a valid pattern as-is, since MHNNotation doesn't
    // know how to interpret `pattern`. Subclasses like SiteswapPattern should
    // override this to add that functionality.
    @Override
    public MHNPattern fromParameters(ParameterList pl) throws
                                    JuggleExceptionUser, JuggleExceptionInternal {
        config = pl.toString();

        pattern = pl.removeParameter("pattern");  // only required parameter
        if (pattern == null)
            throw new JuggleExceptionUser(errorstrings.getString("Error_no_pattern"));

        String temp = null;
        if ((temp = pl.removeParameter("bps")) != null) {
            try {
                bps_set = Double.parseDouble(temp);
                bps = bps_set;
            } catch (NumberFormatException nfe) {
                throw new JuggleExceptionUser(errorstrings.getString("Error_bps_value"));
            }
        }

        if ((temp = pl.removeParameter("dwell")) != null) {
            try {
                dwell = Double.parseDouble(temp);
                if (dwell <= 0 || dwell >= 2)
                    throw new JuggleExceptionUser(errorstrings.getString("Error_dwell_range"));
            } catch (NumberFormatException nfe) {
                throw new JuggleExceptionUser(errorstrings.getString("Error_dwell_value"));
            }
        }

        if ((temp = pl.removeParameter("hands")) != null)
            hands = new MHNHands(temp);

        if ((temp = pl.removeParameter("body")) != null)
            bodies = new MHNBody(temp);

        if ((temp = pl.removeParameter("gravity")) != null) {
            try {
                gravity = Double.parseDouble(temp);
            } catch (NumberFormatException e) {
            }
        }

        if ((temp = pl.removeParameter("propdiam")) != null) {
            try {
                propdiam = Double.parseDouble(temp);
            } catch (NumberFormatException e) {
            }
        }

        if ((temp = pl.removeParameter("bouncefrac")) != null) {
            try {
                bouncefrac = Double.parseDouble(temp);
            } catch (NumberFormatException e) {
            }
        }

        if ((temp = pl.removeParameter("squeezebeats")) != null) {
            try {
                squeezebeats = Double.parseDouble(temp);
            } catch (NumberFormatException e) {
            }
        }

        if ((temp = pl.removeParameter("prop")) != null) {
            prop = temp;
        }

        if ((temp = pl.removeParameter("colors")) != null) {
            if (temp.strip().equals("mixed"))
                temp = "{red}{green}{blue}{yellow}{cyan}{magenta}{orange}{pink}{gray}{black}";
            else
                temp = JLFunc.expandRepeats(temp);

            StringTokenizer st1 = new StringTokenizer(temp, "}", false);
            StringTokenizer st2 = null;
            String          str = null;

            int numcolors = st1.countTokens();
            color = new String[numcolors];

            // Parse the colors parameter
            for (int i = 0; i < numcolors; i++) {
                // Look for next {...} block
                str = st1.nextToken().replace('{', ' ').strip();

                // Parse commas
                st2 = new StringTokenizer(str, ",", false);

                switch (st2.countTokens()) {
                    case 1:
                        // Use the value as a color name
                        color[i] = st2.nextToken().strip().toLowerCase();
                        break;
                    case 3:
                        // Use the three values as RGB values
                        color[i] = "{" + str + "}";
                        break;
                    default:
                        throw new JuggleExceptionUser(errorstrings.getString("Error_color_format"));
                }
            }
        }

        if ((temp = pl.removeParameter("hss")) != null) {
            hss = temp;
        }

        if (hss != null) {
            if ((temp = pl.removeParameter("hold")) != null) {
                try {
                    hold = Boolean.parseBoolean(temp);
                } catch (IllegalFormatException ife) {
                    throw new JuggleExceptionUser(errorstrings.getString("Error_hss_hold_value_error"));
                }
            } 

            if ((temp = pl.removeParameter("dwellmax")) != null) {
                try {
                    dwellmax = Boolean.parseBoolean(temp);
                } catch (IllegalFormatException ife) {
                    throw new JuggleExceptionUser(errorstrings.getString("Error_hss_dwellmax_value_error"));
                }
            }

            if ((temp = pl.removeParameter("handspec")) != null) {
                handspec = temp;
            }
        }

        if ((temp = pl.removeParameter("title")) != null) {
            title = temp.strip();
        }

        return this;
    }

    @Override
    public String toString() {
        // write out configuration parameters in a standard order
        if (config == null)
            return null;

        String result = "";

        try {
            ParameterList pl = new ParameterList(config);

            String[] keys = {
                "pattern", 
                "bps", 
                "dwell", 
                "hands", 
                "body",         
                "gravity", 
                "propdiam", 
                "bouncefrac",
                "squeezebeats",
                "prop", 
                "colors",        
                "hss", 
                "hold", 
                "dwellmax", 
                "handspec", 
                "title",
            };

            for (String key : keys) {
                String value = pl.getParameter(key);
                if (value != null)
                    result += key + "=" + value + ";";
            }

            if (result.length() > 0)
                result = result.substring(0, result.length() - 1);
        } catch (JuggleExceptionUser jeu) {
            // can't be a user error since config has already been successfully read
            ErrorDialog.handleFatalException(new JuggleExceptionInternal(jeu.getMessage()));
        }

        return result;
    }

    //-------------------------------------------------------------------------
    // Fill in details of the juggling matrix th[], to prepare for animation
    //
    // Note that th[] is assumed to be pre-populated with MHNThrows from the
    // parsing step, prior to this. This function fills in missing data elements
    // in the MHNThrows, connecting them up into a pattern. See MHNThrow.java
    // for more details.
    //-------------------------------------------------------------------------

    protected void buildJugglingMatrix() throws JuggleExceptionUser, JuggleExceptionInternal {
        // build out the juggling matrix in steps
        //
        // this will find and raise many types of errors in the pattern
        if (Constants.DEBUG_SITESWAP_PARSING) {
            System.out.println("-----------------------------------------------------");
            System.out.println("Building internal MHNPattern representation...\n");
            System.out.println("findMasterThrows()");
        }
        findMasterThrows();

        if (Constants.DEBUG_SITESWAP_PARSING)
            System.out.println("assignPaths()");
        assignPaths();

        if (Constants.DEBUG_SITESWAP_PARSING)
            System.out.println("addThrowSources()");
        addThrowSources();

        if (Constants.DEBUG_SITESWAP_PARSING)
            System.out.println("setCatchOrder()");
        setCatchOrder();

        if (Constants.DEBUG_SITESWAP_PARSING)
            System.out.println("findDwellWindows()");
        findDwellWindows();

        if (Constants.DEBUG_SITESWAP_PARSING) {
            String s = getInternalRepresentation();
            if (s != null) {
                System.out.println("\nInternal MHNPattern representation:\n");
                System.out.println(s);
                System.out.println("-----------------------------------------------------");
            }
        }
    }

    // Determine which throws are "master" throws. Because of symmetries defined
    // for the pattern, some throws are shifted or reflected copies of others.
    // For each such chain of related throws, appoint one as the master.
    protected void findMasterThrows() throws JuggleExceptionInternal {
        // start by making every throw a master throw
        for (int i = 0; i < indexes; ++i) {
            for (int j = 0; j < numjugglers; ++j) {
                for (int h = 0; h < 2; ++h) {
                    for (int slot = 0; slot < max_occupancy; ++slot) {
                        MHNThrow mhnt = th[j][h][i][slot];
                        if (mhnt != null) {
                            mhnt.master = mhnt;
                            mhnt.source = null;
                        }
                    }
                }
            }
        }

        boolean changed = true;
        while (changed) {
            changed = false;

            for (int s = 0; s < getNumberOfSymmetries(); ++s) {
                MHNSymmetry sym = getSymmetry(s);
                Permutation jperm = sym.getJugglerPerm();
                int delay = sym.getDelay();

                for (int i = 0; i < indexes; ++i) {
                    int imagei = i + delay;
                    if (imagei >= indexes)
                        continue;

                    for (int j = 0; j < numjugglers; ++j) {
                        for (int h = 0; h < 2; ++h) {
                            for (int slot = 0; slot < max_occupancy; ++slot) {
                                MHNThrow mhnt = th[j][h][i][slot];
                                if (mhnt == null)
                                    continue;

                                int imagej = jperm.getMapping(j + 1);
                                int imageh = (imagej > 0 ? h : 1 - h);
                                imagej = Math.abs(imagej) - 1;

                                MHNThrow imaget = th[imagej][imageh][imagei][slot];
                                if (imaget == null)
                                    throw new JuggleExceptionInternal("Problem finding master throws");

                                MHNThrow m = mhnt.master;
                                MHNThrow im = imaget.master;
                                if (m == im)
                                    continue;

                                // we have a disagreement about which is the master;
                                // choose one of them and set them equal
                                MHNThrow newm = m;
                                if (m.index > im.index)
                                    newm = im;
                                else if (m.index == im.index) {
                                    if (m.juggler > im.juggler)
                                        newm = im;
                                    else if (m.juggler == im.juggler) {
                                        if (m.hand > im.hand)
                                            newm = im;
                                    }
                                }
                                mhnt.master = imaget.master = newm;
                                changed = true;
                            }
                        }
                    }
                }
            }
        }

        if (Constants.DEBUG_SITESWAP_PARSING) {
            for (int i = 0; i < indexes; ++i) {
                for (int j = 0; j < numjugglers; ++j) {
                    for (int h = 0; h < 2; ++h) {
                        for (int slot = 0; slot < max_occupancy; ++slot) {
                            MHNThrow mhnt = th[j][h][i][slot];
                            if (mhnt != null && mhnt.master == mhnt)
                                System.out.println("master throw at j="+j+",h="+h+",i="+i+",slot="+slot);
                        }
                    }
                }
            }
        }
    }

    // Figure out which throws are filling other throws, and assigns path
    // numbers to all throws.
    //
    // This process is complicated by the fact that we allow multiplexing.
    protected void assignPaths() throws JuggleExceptionUser, JuggleExceptionInternal {
        for (int i = 0; i < indexes; ++i) {
            for (int j = 0; j < numjugglers; ++j) {
                for (int h = 0; h < 2; ++h) {
                    for (int slot = 0; slot < max_occupancy; ++slot) {
                        MHNThrow sst = th[j][h][i][slot];
                        if (sst == null || sst.master != sst)  // loop over master throws
                            continue;

                        // Figure out which slot number we're filling with this
                        // master throw. We need to find a value of `targetslot`
                        // that is empty for the master throw's target, as well as
                        // the targets of its images.

                        int targetslot = 0;
                        while (targetslot < max_occupancy) {  // find value of targetslot that works
                            boolean itworks = true;

                            // loop over all throws that have sst as master
                            for (int i2 = 0; i2 < indexes; ++i2) {
                                for (int j2 = 0; j2 < numjugglers; ++j2) {
                                    for (int h2 = 0; h2 < 2; ++h2) {
                                        for (int slot2 = 0; slot2 < max_occupancy; ++slot2) {
                                            MHNThrow sst2 = th[j2][h2][i2][slot2];
                                            if (sst2 == null || sst2.master != sst || sst2.targetindex >= indexes)
                                                continue;

                                            MHNThrow target = th[sst2.targetjuggler-1][sst2.targethand][sst2.targetindex][targetslot];
                                            if (target == null)
                                                itworks = false;
                                            else
                                                itworks &= (target.source == null);  // target also unfilled?
                                        }
                                    }
                                }
                            }
                            if (itworks)
                                break;
                            ++targetslot;
                        }

                        if (targetslot == max_occupancy) {
                            if (Constants.DEBUG_SITESWAP_PARSING)
                                System.out.println("Error: Too many objects landing on beat "
                                        + (sst.targetindex + 1) + " for juggler "
                                        + sst.targetjuggler + ", "
                                        + (sst.targethand == 0 ? "right hand" : "left hand"));

                            String template = errorstrings.getString("Error_badpattern_landings");
                            String hand = (sst.targethand == 0 ? errorstrings.getString("Error_right_hand")
                                        : errorstrings.getString("Error_left_hand"));
                            Object[] arguments = { Integer.valueOf(sst.targetindex + 1),
                                        Integer.valueOf(sst.targetjuggler), hand };
                            throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                        }

                        // loop again over all throws that have sst as master,
                        // wiring up sources and targets using the value of `targetslot`

                        for (int i2 = 0; i2 < indexes; ++i2) {
                            for (int j2 = 0; j2 < numjugglers; ++j2) {
                                for (int h2 = 0; h2 < 2; ++h2) {
                                    for (int slot2 = 0; slot2 < max_occupancy; ++slot2) {
                                        MHNThrow sst2 = th[j2][h2][i2][slot2];
                                        if (sst2 == null || sst2.master != sst || sst2.targetindex >= indexes)
                                            continue;

                                        MHNThrow target2 = th[sst2.targetjuggler-1][sst2.targethand][sst2.targetindex][targetslot];
                                        if (target2 == null)
                                            throw new JuggleExceptionInternal("Got null target in assignPaths()");

                                        sst2.target = target2;      // hook source and target together
                                        target2.source = sst2;
                                    }
                                }
                            }
                        }


                    }
                }
            }
        }

        // assign path numbers to all of the throws
        int currentpath = 1;
        for (int i = 0; i < indexes; ++i) {
            for (int j = 0; j < numjugglers; ++j) {
                for (int h = 0; h < 2; ++h) {
                    for (int slot = 0; slot < max_occupancy; ++slot) {
                        MHNThrow sst = th[j][h][i][slot];
                        if (sst == null)
                            continue;

                        if (sst.source != null) {
                            sst.pathnum = sst.source.pathnum;
                            continue;
                        }

                        if (currentpath > numpaths) {
                            if (Constants.DEBUG_SITESWAP_PARSING) {
                                System.out.println("j="+j+", h="+h+", index="+i+", slot="+slot+"\n");
                                System.out.println("---------------------------");
                                for (int tempi = 0; tempi <= i; ++tempi) {
                                    for (int temph = 0; temph < 2; ++temph) {
                                        for (int tempslot = 0; tempslot < max_occupancy; ++tempslot) {
                                            MHNThrow tempsst = th[0][temph][tempi][tempslot];
                                            System.out.println("index="+tempi+", hand="+temph+", slot="+tempslot+":");
                                            if (tempsst == null)
                                                System.out.println("   null entry");
                                            else
                                                System.out.println("   targetindex="+tempsst.targetindex+", targethand="+
                                                                   tempsst.targethand+", targetslot="+tempsst.targetslot+
                                                                   ", pathnum="+tempsst.pathnum);
                                        }
                                    }
                                }
                                System.out.println("---------------------------");
                            }

                            throw new JuggleExceptionUser(errorstrings.getString("Error_badpattern"));
                        }

                        sst.pathnum = currentpath;
                        ++currentpath;
                    }
                }
            }
        }

        if (currentpath <= numpaths)
            throw new JuggleExceptionInternal("Problem assigning path numbers 2");
    }

    // Set the `source` field for all throws that don't already have it set.
    //
    // In doing this we create new MHNThrows that are not part of the juggling
    // matrix th[] because they occur before index 0.
    protected void addThrowSources() throws JuggleExceptionInternal {
        for (int i = indexes - 1; i >= 0; --i) {
            for (int j = 0; j < numjugglers; ++j) {
                for (int h = 0; h < 2; ++h) {
                    for (int slot = 0; slot < max_occupancy; ++slot) {
                        MHNThrow sst = th[j][h][i][slot];
                        if (sst == null || sst.source != null)
                            continue;

                        if ((i + getPeriod()) >= indexes)
                            throw new JuggleExceptionInternal("Could not get throw source 2");

                        MHNThrow sst2 = th[j][h][i + getPeriod()][slot].source;
                        if (sst2 == null)
                            throw new JuggleExceptionInternal("Could not get throw source 1");

                        MHNThrow sst3 = new MHNThrow();
                        sst3.juggler = sst2.juggler;
                        sst3.hand = sst2.hand;
                        sst3.index = sst2.index - getPeriod();
                        sst3.slot = sst2.slot;
                        sst3.targetjuggler = j;
                        sst3.targethand = h;
                        sst3.targetindex = i;
                        sst3.targetslot = slot;
                        sst3.handsindex = -1;   // undefined
                        sst3.pathnum = sst.pathnum;
                        sst3.mod = sst2.mod;
                        sst3.master = sst2.master;
                        sst3.source = null;
                        sst3.target = sst;

                        sst.source = sst3;
                    }
                }
            }
        }
    }

    // Set the MHNThrow.catching and MHNThrow.catchnum fields
    protected void setCatchOrder() throws JuggleExceptionInternal {
        // Figure out the correct catch order for master throws
        for (int k = 0; k < getIndexes(); ++k) {
            for (int j = 0; j < getNumberOfJugglers(); ++j) {
                for (int h = 0; h < 2; ++h) {
                    int slotcatches = 0;

                    for (int slot = 0; slot < getMaxOccupancy(); ++slot) {
                        MHNThrow sst = th[j][h][k][slot];
                        if (sst == null)
                            break;

                        sst.catching = (sst.source.mod.charAt(0) != 'H');
                        if (sst.catching) {
                            sst.catchnum = slotcatches;
                            ++slotcatches;
                        }
                    }

                    // Arrange the order of the catches, if more than one

                    if (slotcatches < 2)
                        continue;

                    for (int slot1 = 0; slot1 < getMaxOccupancy(); ++slot1) {
                        MHNThrow sst1 = th[j][h][k][slot1];
                        if (sst1 == null || sst1.master != sst1)  // only master throws
                            break;

                        if (!sst1.catching)
                            continue;

                        for (int slot2 = (slot1 + 1); slot2 < getMaxOccupancy(); ++slot2) {
                            MHNThrow sst2 = th[j][h][k][slot2];
                            if (sst2 == null || sst2.master != sst2)
                                break;

                            if (!sst2.catching)
                                continue;

                            boolean switchcatches = false;
                            if (sst1.catchnum < sst2.catchnum)
                                switchcatches = isCatchOrderIncorrect(sst1, sst2);
                            else
                                switchcatches = isCatchOrderIncorrect(sst2, sst1);

                            if (switchcatches) {
                                int temp = sst1.catchnum;
                                sst1.catchnum = sst2.catchnum;
                                sst2.catchnum = temp;
                            }
                        }
                    }
                }
            }
        }

        // Copy that over to the non-master throws
        for (int k = 0; k < getIndexes(); ++k) {
            for (int j = 0; j < getNumberOfJugglers(); ++j) {
                for (int h = 0; h < 2; ++h) {
                    for (int slot = 0; slot < getMaxOccupancy(); ++slot) {
                        MHNThrow sst = th[j][h][k][slot];
                        if (sst == null || sst.master == sst)  // skip master throws
                            break;

                        sst.catchnum = sst.master.catchnum;
                    }
                }
            }
        }
    }

    // Decide whether the catches immediately prior to the two given throws should
    // be made in the order given, or whether they should be switched.
    //
    // The following implementation isn't ideal; we would like a function that is
    // invariant with respect to the various pattern symmetries we can apply, but
    // I don't think this is possible with respect to the jugglers.
    protected static boolean isCatchOrderIncorrect(MHNThrow t1, MHNThrow t2) {
        // first look at the time spent in the air; catch higher throws first
        if (t1.source.index > t2.source.index)
            return true;
        if (t1.source.index < t2.source.index)
            return false;

        // look at which juggler it's from; catch from "faraway" jugglers first
        int jdiff1 = Math.abs(t1.juggler - t1.source.juggler);
        int jdiff2 = Math.abs(t2.juggler - t2.source.juggler);
        if (jdiff1 < jdiff2)
            return true;
        if (jdiff1 > jdiff2)
            return false;

        // look at which hand it's from; catch from same hand first
        int hdiff1 = Math.abs(t1.hand - t1.source.hand);
        int hdiff2 = Math.abs(t2.hand - t2.source.hand);
        if (hdiff1 > hdiff2)
            return true;
        if (hdiff1 < hdiff2)
            return false;

        return false;
    }

    // Determine, for each throw in the juggling matrix, how many beats prior to
    // that throw was the last throw from that hand. This determines the
    // earliest we can catch (i.e., the maximum dwell time).
    protected void findDwellWindows() {
        for (int k = 0; k < getIndexes(); ++k) {
            for (int j = 0; j < getNumberOfJugglers(); ++j) {
                for (int h = 0; h < 2; ++h) {
                    for (int slot = 0; slot < getMaxOccupancy(); ++slot) {
                        MHNThrow sst = th[j][h][k][slot];
                        if (sst == null)
                            continue;

                        // see if we made a throw on the beat immediately prior
                        int index = k - 1;
                        if (index < 0)
                            index += getPeriod();

                        boolean prev_beat_throw = false;
                        for (int slot2 = 0; slot2 < getMaxOccupancy(); ++slot2) {
                            MHNThrow sst2 = th[j][h][index][slot2];
                            if (sst2 != null && !sst2.isZero())
                                prev_beat_throw = true;
                        }

                        // don't bother with dwellwindow > 2 since in practice
                        // we never want to dwell for more than two beats
                        sst.dwellwindow = (prev_beat_throw ? 1 : 2);
                    }
                }
            }
        }
    }

    // Dump the internal state of the pattern to a string; intended to be used
    // for debugging
    protected String getInternalRepresentation() {
        StringBuffer sb = new StringBuffer();

        sb.append("numjugglers = " + getNumberOfJugglers() + "\n");
        sb.append("numpaths = " + getNumberOfPaths() + "\n");
        sb.append("period = " + getPeriod() + "\n");
        sb.append("max_occupancy = " + getMaxOccupancy() + "\n");
        sb.append("max_throw = " + getMaxThrow() + "\n");
        sb.append("indexes = " + getIndexes() + "\n");
        sb.append("throws:\n");

        for (int i = 0; i < getIndexes(); ++i) {
            for (int j = 0; j < numjugglers; ++j) {
                for (int h = 0; h < 2; ++h) {
                    for (int s = 0; s < getMaxOccupancy(); ++s) {
                        sb.append("  th[" + j + "][" + h + "][" + i + "][" + s + "] = ");
                        MHNThrow mhnt = th[j][h][i][s];
                        if (mhnt == null)
                            sb.append("null\n");
                        else
                            sb.append(mhnt.toString() + "\n");
                    }
                }
            }
        }

        sb.append("symmetries:\n");  // not finished
        sb.append("hands:\n");
        sb.append("bodies:\n");

        return sb.toString();
    }

    // Return the pattern's starting state
    //
    // Result is a matrix of dimension (jugglers) x 2 x (indexes), with values
    // between 0 and (max_occupancy) inclusive
    public int[][][] getStartingState(int indexes) {
        int[][][] result = new int[getNumberOfJugglers()][2][indexes];

        for (int i = getPeriod(); i < getIndexes(); ++i) {
            for (int j = 0; j < numjugglers; ++j) {
                for (int h = 0; h < 2; ++h) {
                    for (int s = 0; s < getMaxOccupancy(); ++s) {
                        MHNThrow mhnt = th[j][h][i][s];

                        if (mhnt != null && mhnt.source.index < getPeriod()) {
                            if ((i - getPeriod()) < indexes)
                                result[j][h][i - getPeriod()]++;
                        }
                    }
                }
            }
        }

        return result;
    }

    //--------------------------------------------------------------------------
    // Convert from juggling matrix representation to JML
    //--------------------------------------------------------------------------

    // The following are default spatial coordinates to use
    protected static final double[] samethrowx =
            { 0, 20, 25, 12, 10, 7.5,  5,  5,  5 };
    protected static final double[] crossingthrowx =
            { 0, 17, 17, 12, 10,  18, 25, 25, 30 };
    protected static final double[] catchx =
            { 0, 17, 25, 30, 40,  45, 45, 50, 50 };
    protected static final double restingx = 25;

    // How many beats early to throw a '1' (all other throws are on-beat)
    protected static double BEATS_ONE_THROW_EARLY = 0;

    // Minimum airtime for a throw, in beats
    protected static double BEATS_AIRTIME_MIN = 0.3;

    // Minimum time from a throw to a subsequent catch for that hand, in beats
    protected static double BEATS_THROW_CATCH_MIN = 0.3;

    // Minimum time from a catch to a subsequent throw for that hand, in beats
    protected static double BEATS_CATCH_THROW_MIN = 0;

    // Maximum allowed time without events for a given hand, in seconds
    protected static double SECS_EVENT_GAP_MAX = 0.5;

    @Override
    public JMLPattern asJMLPattern() throws JuggleExceptionUser, JuggleExceptionInternal {
        if (bps_set <= 0)  // signal that we should calculate bps
            bps = calcBps();

        //hss begin
        //  code to modify bps according to number of jugglers
        //  and potentially the specific pattern being juggled
        //  can be inserted here for example:
        //        if (hss != null) {
        //          bps *= getNumberOfJugglers();
        //        }
        //hss end

        JMLPattern result = new JMLPattern();

        // Step 1: Add basic information about the pattern
        result.setNumberOfJugglers(getNumberOfJugglers());
        addPropsToJML(result);
        addSymmetriesToJML(result);

        // Step 2: Assign catch and throw times to each MHNThrow in the
        // juggling matrix
        findCatchThrowTimes();

        // Step 3: Add the primary events to the pattern
        //
        // We keep track of which hands/paths don't get any events, so we can
        // add positioning events later
        boolean[][] handtouched = new boolean[getNumberOfJugglers()][2];
        boolean[] pathtouched = new boolean[getNumberOfPaths()];
        addPrimaryEventsToJML(result, handtouched, pathtouched);

        // Step 4: Define a body position for this juggler and beat, if specified
        addJugglerPositionsToJML(result);

        // Step 5: Add simple positioning events for hands that got no events
        addEventsForUntouchedHandsToJML(result, handtouched);

        // Step 6: Add <holding> transitions for paths that got no events
        addEventsForUntouchedPathsToJML(result, pathtouched);

        // Step 7: Build the full event list so we can scan through it
        // chronologically in Steps 8-10
        result.buildEventList();

        // Step 8: Add events where there are long gaps for a hand
        if (hands == null)
            addEventsForGapsToJML(result);

        // Step 9: Specify positions for events that don't have them defined yet
        addLocationsForIncompleteEventsToJML(result);

        // Step 10: Add additional <holding> transitions where needed (i.e., a
        // ball is in a hand)
        addMissingHoldsToJML(result);

        // Step 11: Confirm that each throw in the JMLPattern has enough time to
        // satisfy its minimum duration requirement. If not then rescale time
        // (bps) to make everything feasible.
        //
        // This should only be done if the user has not manually set `bps`.
        if (bps_set <= 0) {
            double scale_factor = result.scaleTimeToFitThrows(1.01);

            if (scale_factor > 1.0) {
                bps /= scale_factor;

                if (hands == null) {
                    // redo steps 8-10
                    addEventsForGapsToJML(result);
                    addLocationsForIncompleteEventsToJML(result);
                    addMissingHoldsToJML(result);
                }

                if (Constants.DEBUG_LAYOUT)
                    System.out.println("Rescaled time; scale factor = " + scale_factor);
            }
        }

        result.setTitle(title == null ? pattern : title);

        if (Constants.DEBUG_LAYOUT) {
            System.out.println("Pattern in JML format:\n");
            System.out.println(result);
        }

        return result;
    }

    //--------------------------------------------------------------------------
    // Helpers for converting to JML
    //--------------------------------------------------------------------------

    protected static final double[] throwspersec =
        { 2.00, 2.00, 2.00, 2.90, 3.40, 4.10, 4.25, 5.00, 5.00, 5.50 };

    protected double calcBps() {
        // Calculate a default beats per second (bps) for the pattern
        double result = 0;
        int numberaveraged = 0;

        for (int k = 0; k < getPeriod(); k++) {
            for (int j = 0; j < getNumberOfJugglers(); j++) {
                for (int h = 0; h < 2; h++) {
                    for (int slot = 0; slot < getMaxOccupancy(); slot++) {
                        MHNThrow sst = th[j][h][k][slot];
                        if (sst != null) {
                            int throwval = sst.targetindex - k;
                            if (throwval > 2) {
                                result += throwspersec[throwval > 9 ? 9 : throwval];
                                numberaveraged++;
                            }
                        }
                    }
                }
            }
        }
        if (numberaveraged > 0)
            result /= (double)numberaveraged;
        else
            result = 2;

        return result;
    }

    protected void addPropsToJML(JMLPattern pat) {
        int balls = getNumberOfPaths();
        pat.setNumberOfPaths(balls);
        int props = (color == null) ? Math.min(balls, 1) : Math.min(balls, color.length);
        for (int i = 0; i < props; i++) {
            String mod = null;
            if (propdiam != MHNPattern.propdiam_default)
                mod = "diam="+propdiam;
            if (color != null) {
                String colorstr = "color="+color[i];
                if (mod == null)
                    mod = colorstr;
                else
                    mod = mod + ";" + colorstr;
            }
            pat.addProp(new PropDef(getPropName(), mod));
        }
        int[] pa = new int[balls];
        for (int i = 0; i < balls; i++)
            pa[i] = 1 + (i % props);
        pat.setPropAssignments(pa);
    }

    protected void addSymmetriesToJML(JMLPattern pat) throws JuggleExceptionUser {
        int balls = getNumberOfPaths();

        for (int i = 0; i < getNumberOfSymmetries(); i++) {
            MHNSymmetry sss = getSymmetry(i);
            int symtype;
            int[] pathmap = new int[balls+1];

            switch (sss.getType()) {
                case MHNSymmetry.TYPE_DELAY:
                    symtype = JMLSymmetry.TYPE_DELAY;
                    // figure out the path mapping
                    {
                        MHNThrow[][][][] th = getThrows();
                        for (int k = 0; k < (getIndexes()-sss.getDelay()); k++) {
                            for (int j = 0; j < getNumberOfJugglers(); j++) {
                                for (int h = 0; h < 2; h++) {
                                    for (int slot = 0; slot < getMaxOccupancy(); slot++) {
                                        MHNThrow sst = th[j][h][k][slot];
                                        if (sst != null && sst.pathnum != -1) {
                                            MHNThrow sst2 = th[j][h][k+sss.getDelay()][slot];
                                            if (sst2 == null)
                                                throw new JuggleExceptionUser(errorstrings.getString("Error_badpattern_paths"));
                                            if ((sst.pathnum == 0) || (sst2.pathnum == 0))
                                                throw new JuggleExceptionUser(errorstrings.getString("Error_badpattern_paths"));
                                            if (pathmap[sst.pathnum] == 0)
                                                pathmap[sst.pathnum] = sst2.pathnum;
                                            else if (pathmap[sst.pathnum] != sst2.pathnum)
                                                throw new JuggleExceptionUser(errorstrings.getString("Error_badpattern_delay"));
                                        }
                                    }
                                }
                            }
                        }
                    }
                        break;
                case MHNSymmetry.TYPE_SWITCH:
                    symtype = JMLSymmetry.TYPE_SWITCH;
                    break;
                case MHNSymmetry.TYPE_SWITCHDELAY:
                    symtype = JMLSymmetry.TYPE_SWITCHDELAY;

                    // figure out the path mapping
                    {
                        Permutation jugperm = sss.getJugglerPerm();

                        MHNThrow[][][][] th = getThrows();
                        for (int k = 0; k < (getIndexes()-sss.getDelay()); k++) {
                            for (int j = 0; j < getNumberOfJugglers(); j++) {
                                for (int h = 0; h < 2; h++) {
                                    for (int slot = 0; slot < getMaxOccupancy(); slot++) {
                                        MHNThrow sst = th[j][h][k][slot];
                                        if (sst != null && sst.pathnum != -1) {
                                            int map = jugperm.getMapping(j+1);
                                            int newj = Math.abs(map)-1;
                                            int newh = (map > 0 ? h : 1-h);
                                            MHNThrow sst2 = th[newj][newh][k+sss.getDelay()][slot];
                                            if (sst2 == null)
                                                throw new JuggleExceptionUser(errorstrings.getString("Error_badpattern_paths"));
                                            if (sst.pathnum == 0 || sst2.pathnum == 0)
                                                throw new JuggleExceptionUser(errorstrings.getString("Error_badpattern_paths"));
                                            if (pathmap[sst.pathnum] == 0)
                                                pathmap[sst.pathnum] = sst2.pathnum;
                                            else if (pathmap[sst.pathnum] != sst2.pathnum)
                                                throw new JuggleExceptionUser(errorstrings.getString("Error_badpattern_switchdelay"));
                                        }
                                    }
                                }
                            }
                        }
                    }
                    break;
                default:
                    throw new JuggleExceptionUser(errorstrings.getString("Error_unknown_symmetry"));
            }
            // convert path mapping to a string
            String pathmapstring = "";
            for (int j = 1; j < balls; j++)
                pathmapstring += pathmap[j] + ",";
            if (balls > 0)
                pathmapstring += pathmap[balls];

            JMLSymmetry sym = new JMLSymmetry(symtype, sss.getNumberOfJugglers(),
                                                sss.getJugglerPerm().toString(),
                                                getNumberOfPaths(), pathmapstring,
                                                (double)sss.getDelay() / bps);

            pat.addSymmetry(sym);
        }
    }

    // Assign throw and catch times to each element in the juggling matrix.
    //
    // The catch time here refers to that prop's catch immediately prior to the
    // throw represented by MHNThrow.
    public void findCatchThrowTimes() {
        for (int k = 0; k < getPeriod(); k++) {
            for (int j = 0; j < getNumberOfJugglers(); j++) {
                for (int h = 0; h < 2; h++) {
                    MHNThrow sst = th[j][h][k][0];
                    if (sst == null)
                        continue;

                    // Are we throwing a 1 on this beat?
                    boolean onethrown = false;
                    for (int slot = 0; slot < getMaxOccupancy(); slot++) {
                        MHNThrow sst2 = th[j][h][k][slot];

                        if (sst2 != null && sst2.isThrownOne())
                            onethrown = true;
                    }

                    // Set throw times
                    for (int slot = 0; slot < getMaxOccupancy(); slot++) {
                        MHNThrow sst2 = th[j][h][k][slot];
                        if (sst2 == null)
                            break;

                        if (onethrown)
                            sst2.throwtime = ((double)k - BEATS_ONE_THROW_EARLY) / bps;
                        else
                            sst2.throwtime = (double)k / bps;

                        //hss begin
                        if (hss != null) {
                            //if (onethrown)
                            //    throwtime = ((double)k - 0.25*(double)dwellarray[k]) / bps;
                            //else
                            sst2.throwtime = (double)k / bps;
                        }
                        //hss end
                    }

                    int num_catches = 0;
                    boolean onecaught = false;

                    for (int slot = 0; slot < getMaxOccupancy(); slot++) {
                        MHNThrow sst2 = th[j][h][k][slot];
                        if (sst2 == null)
                            break;

                        if (sst2.catching) {
                            num_catches++;

                            if (sst2.source.isThrownOne())
                                onecaught = true;
                        }
                    }

                    // Figure out when the object we just threw was caught, prior
                    // to the throw.
                    //
                    // We call this `firstcatchtime` because if there are multiple
                    // catches spread out over time on this beat (i.e., a squeeze
                    // catch), this is the earliest one.

                    // Did the previous throw out of this same hand contain
                    // a 1 throw?

                    boolean prev_onethrown = false;
                    int tempindex = k - sst.dwellwindow;
                    while (tempindex < 0)
                        tempindex += getPeriod();

                    for (int slot = 0; slot < getMaxOccupancy(); ++slot) {
                        MHNThrow sst2 = th[j][h][tempindex][slot];
                        if (sst2 != null && sst2.isThrownOne())
                            prev_onethrown = true;
                    }

                    // first, give the requested dwell beats
                    double firstcatchtime = sst.throwtime - dwell / bps;

                    // don't allow catch to move before the previous throw
                    // from the same hand (plus margin)
                    firstcatchtime = Math.max(firstcatchtime,
                            ((double)(k - sst.dwellwindow) -
                                (prev_onethrown ? BEATS_ONE_THROW_EARLY : 0) +
                                BEATS_THROW_CATCH_MIN) / bps);

                    // if catching a 1 throw, allocate enough air time to it
                    if (onecaught) {
                        firstcatchtime = Math.max(firstcatchtime,
                            ((double)(k - 1) - BEATS_ONE_THROW_EARLY +
                                BEATS_AIRTIME_MIN) / bps);
                    }

                    // ensure we have enough time between catch and throw
                    firstcatchtime = Math.min(firstcatchtime,
                            sst.throwtime - BEATS_CATCH_THROW_MIN / bps);

                    // Set catch times
                    for (int slot = 0; slot < getMaxOccupancy(); slot++) {
                        MHNThrow sst2 = th[j][h][k][slot];
                        if (sst2 == null)
                            break;

                        double catchtime = firstcatchtime;

                        if (num_catches > 1) {
                            catchtime += (double)sst2.catchnum /
                                    (double)(num_catches - 1) * squeezebeats / bps;
                        }

                        //hss begin
                        if (hss != null) {
                            // if getPeriod() > size of dwellarray due to repeats
                            // to account for hand/body positions, then reuse
                            // dwellarray timings from prior array elements
                            int newk = k % dwellarray.length;

                            catchtime = ((double)k - dwellarray[newk]) / bps;

                            if (num_catches > 1) {
                                catchtime += (double)sst2.catchnum /
                                    (double)(num_catches - 1) * squeezebeats / bps;
                            }
                        }
                        //hss end

                        catchtime = Math.min(catchtime,
                                sst.throwtime - BEATS_CATCH_THROW_MIN / bps);

                        sst2.catchtime = catchtime;
                    }
                }
            }
        }
    }

    protected void addPrimaryEventsToJML(JMLPattern pat,
                    boolean[][] handtouched, boolean[] pathtouched)
                                throws JuggleExceptionUser, JuggleExceptionInternal {
        for (int j = 0; j < getNumberOfJugglers(); j++) {
            for (int h = 0; h < 2; h++) {
                handtouched[j][h] = false;
            }
        }
        for (int j = 0; j < getNumberOfPaths(); j++)
            pathtouched[j] = false;

        for (int k = 0; k < getPeriod(); k++) {
            for (int j = 0; j < getNumberOfJugglers(); j++) {
                for (int h = 0; h < 2; h++) {
                    MHNThrow sst = th[j][h][k][0];
                    if (sst == null || sst.master != sst)
                        continue;

                    // Step 3a: Add transitions to the on-beat event (throw or holding transitions)

                    JMLEvent ev = new JMLEvent();
                    double throwxsum = 0;
                    int num_throws = 0;

                    for (int slot = 0; slot < getMaxOccupancy(); slot++) {
                        MHNThrow sst2 = th[j][h][k][slot];
                        if (sst2 == null)
                            break;

                        String type = null;
                        String mod = null;

                        switch (sst2.mod.charAt(0)) {
                            case 'B':
                                type = "bounce";
                                mod = null;
                                if (sst2.mod.indexOf("F") != -1) {
                                    if (mod == null)
                                        mod = "forced=true";
                                    else
                                        mod = mod + ";forced=true";
                                }
                                if (sst2.mod.indexOf("H") != -1) {
                                    if (mod == null)
                                        mod = "hyper=true";
                                    else
                                        mod = mod + ";hyper=true";
                                }
                                int bounces = 1;
                                for (int i = 1; i < sst2.mod.length(); i++) {
                                    if (sst2.mod.charAt(i) == 'B')
                                        bounces++;
                                }
                                if (bounces > 1) {
                                    if (mod == null)
                                        mod = "bounces=" + bounces;
                                    else
                                        mod = mod + ";bounces=" + bounces;
                                }
                                if (bouncefrac != MHNPattern.bouncefrac_default)
                                    if (mod == null)
                                        mod = "bouncefrac=" + bouncefrac;
                                    else
                                        mod = mod + ";bouncefrac=" + bouncefrac;
                                if (gravity != MHNPattern.gravity_default) {
                                    if (mod == null)
                                        mod = "g=" + gravity;
                                    else
                                        mod = mod + ";g=" + gravity;
                                }
                                break;
                            case 'F':
                                type = "bounce";
                                mod = "forced=true";
                                if (bouncefrac != MHNPattern.bouncefrac_default)
                                    mod += ";bouncefrac="+bouncefrac;
                                if (gravity != MHNPattern.gravity_default)
                                    mod = mod + ";g=" + gravity;
                                break;
                            case 'H':
                                type = "hold";
                                mod = null;
                                break;
                            case 'T':
                            default:
                                type = "toss";
                                mod = null;
                                if (gravity != MHNPattern.gravity_default)
                                    mod = "g=" + gravity;
                                break;
                        }

                        if (sst2.mod.charAt(0) != 'H') {
                            if (sst2.isZero()) {
                                String template = errorstrings.getString("Error_modifier_on_0");
                                Object[] arguments = { sst2.mod, Integer.valueOf(k + 1) };
                                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                            }

                            ev.addTransition(new JMLTransition(JMLTransition.TRANS_THROW, sst2.pathnum, type, mod));

                            int throwval = sst2.targetindex - k;
                            //System.out.println("k = " + k + ", hand = " + h + ", throwval = " + throwval);

                            if (sst2.targethand == h) {
                                throwxsum += (throwval > 8 ? samethrowx[8] : samethrowx[throwval]);
                            } else {
                                throwxsum += (throwval > 8 ? crossingthrowx[8] : crossingthrowx[throwval]);
                            }
                            num_throws++;
                        } else if (hands != null) {
                            if (!sst2.isZero()) {
                                // add holding transition if there's a ball in hand and "hands" is specified
                                ev.addTransition(new JMLTransition(JMLTransition.TRANS_HOLDING, sst2.pathnum, type, mod));
                                pathtouched[sst2.pathnum - 1] = true;
                            }
                        }
                    }

                    // Step 3b: Finish off the on-beat event based on the transitions we've added

                    if (hands == null && num_throws == 0) {
                        // don't add on-beat event if there are no throws -- unless a hand
                        // layout is specified
                    } else {
                        // set the event position
                        if (hands == null) {
                            if (num_throws > 0) {
                                double throwxav = throwxsum / (double)num_throws;
                                if (h == MHNPattern.LEFT_HAND)
                                    throwxav = -throwxav;
                                ev.setLocalCoordinate(new Coordinate(throwxav, 0, 0));
                                ev.calcpos = false;
                            } else {
                                // mark event to calculate coordinate later
                                ev.calcpos = true;
                            }
                        } else {
                            Coordinate c = hands.getCoordinate(sst.juggler, sst.handsindex, 0);
                            if (h == MHNPattern.LEFT_HAND)
                                c.x = -c.x;
                            ev.setLocalCoordinate(c);
                            ev.calcpos = false;
                        }

                        ev.setT(sst.throwtime);
                        ev.setHand(j+1, (h==MHNPattern.RIGHT_HAND ? HandLink.RIGHT_HAND : HandLink.LEFT_HAND));
                        pat.addEvent(ev);

                        // record which hands are touched by this event, for later reference
                        for (int i2 = 0; i2 < getIndexes(); i2++) {
                            for (int j2 = 0; j2 < getNumberOfJugglers(); j2++) {
                                for (int h2 = 0; h2 < 2; h2++) {
                                    for (int slot2 = 0; slot2 < getMaxOccupancy(); slot2++) {
                                        MHNThrow sst2 = th[j2][h2][i2][slot2];
                                        if (sst2 != null && sst2.master == sst)
                                            handtouched[j2][h2] = true;
                                    }
                                }
                            }
                        }
                    }

                    // Step 3c: Add any catching (or holding) events immediately prior to the
                    // on-beat event added in Step 3b above:

                    double catchxsum = 0;
                    int num_catches = 0;

                    for (int slot = 0; slot < getMaxOccupancy(); slot++) {
                        MHNThrow sst2 = th[j][h][k][slot];
                        if (sst2 == null || !sst2.catching)
                            continue;

                        int catchpath = sst2.pathnum;
                        int catchval = k - sst2.source.index;
                        pathtouched[catchpath - 1] = true;
                        catchxsum += (catchval > 8 ? catchx[8] : catchx[catchval]);
                        num_catches++;
                    }

                    // Don't put an event at the catch time if there are no catches on
                    // this beat -- unless a hand layout is specified
                    if (hands == null && num_catches == 0)
                        continue;

                    // Now add the catch event(s). Two cases to consider: (1) all catches
                    // happen at the same event, or (2) multiple catch events are made in
                    // succession.

                    // keep track of the time of last catch, for Step 3d below
                    double lastcatchtime = 0;

                    if (squeezebeats == 0 || num_catches < 2) {
                        // Case 1: everything happens at a single event
                        ev = new JMLEvent();

                        // first set the event position
                        if (hands == null) {
                            if (num_catches > 0) {
                                double cx = catchxsum / (double)num_catches;
                                // System.out.println("average catch pos. = "+cx);
                                ev.setLocalCoordinate(new Coordinate((h==MHNPattern.RIGHT_HAND?cx:-cx), 0, 0));
                                ev.calcpos = false;
                            } else {
                                // mark event to calculate coordinate later
                                ev.calcpos = true;
                            }
                        } else {
                            int pos = sst.handsindex - 2;
                            while (pos < 0)
                                pos += hands.getPeriod(sst.juggler);
                            int index = hands.getCatchIndex(sst.juggler, pos);
                            Coordinate c = hands.getCoordinate(sst.juggler, pos, index);
                            if (h == MHNPattern.LEFT_HAND)
                                c.x = -c.x;
                            ev.setLocalCoordinate(c);
                            ev.calcpos = false;
                        }

                        ev.setT(sst.catchtime);
                        lastcatchtime = sst.catchtime;

                        ev.setHand(j+1, (h==MHNPattern.RIGHT_HAND ? HandLink.RIGHT_HAND : HandLink.LEFT_HAND));

                        // add all the transitions
                        for (int slot = 0; slot < getMaxOccupancy(); slot++) {
                            MHNThrow sst2 = th[j][h][k][slot];
                            if (sst2 == null)
                                break;

                            if (sst2.catching) {
                                ev.addTransition(new JMLTransition(JMLTransition.TRANS_CATCH, sst2.pathnum, null, null));
                            } else if (hands != null) {
                                if (sst2.pathnum != -1) {   // -1 signals a 0 throw
                                    // add holding transition if there's a ball in hand and "hands" is specified
                                    ev.addTransition(new JMLTransition(JMLTransition.TRANS_HOLDING, sst2.pathnum, null, null));
                                    pathtouched[sst2.pathnum-1] = true;
                                }
                            }
                        }

                        pat.addEvent(ev);
                    } else {
                        // Case 2: separate event for each catch; we know that numcatches > 1 here
                        for (int slot = 0; slot < getMaxOccupancy(); slot++) {
                            MHNThrow sst2 = th[j][h][k][slot];
                            if (sst2 == null || !sst2.catching)
                                continue;

                            // we only need to add catch transitions here; holding transitions will be
                            // added in Step 9

                            ev = new JMLEvent();

                            // first set the event position
                            if (hands == null) {
                                double cx = catchxsum / (double)num_catches;
                                // System.out.println("average catch pos. = "+cx);
                                ev.setLocalCoordinate(new Coordinate((h==MHNPattern.RIGHT_HAND?cx:-cx), 0, 0));
                            } else {
                                int pos = sst.handsindex - 2;
                                while (pos < 0)
                                    pos += hands.getPeriod(sst.juggler);
                                int index = hands.getCatchIndex(sst.juggler, pos);
                                Coordinate c = hands.getCoordinate(sst.juggler, pos, index);
                                if (h == MHNPattern.LEFT_HAND)
                                    c.x = -c.x;
                                ev.setLocalCoordinate(c);
                            }
                            ev.calcpos = false;

                            ev.setT(sst2.catchtime);
                            if (sst2.catchnum == (num_catches - 1))
                                lastcatchtime = sst2.catchtime;

                            ev.setHand(j+1, (h==MHNPattern.RIGHT_HAND ? HandLink.RIGHT_HAND : HandLink.LEFT_HAND));
                            ev.addTransition(new JMLTransition(JMLTransition.TRANS_CATCH, sst2.pathnum, null, null));
                            pat.addEvent(ev);
                        }
                    }

                    // Step 3d: If hand positionss are specified, add any extra hand positioning
                    // events after the catch above, until the next catch for the hand

                    if (hands == null)
                        continue;

                    // add other events between the previous catch and the current throw
                    int pos = sst.handsindex - 2;
                    while (pos < 0)
                        pos += hands.getPeriod(sst.juggler);
                    int catchindex = hands.getCatchIndex(sst.juggler, pos);
                    int numcoords = hands.getNumberOfCoordinates(sst.juggler, pos) - catchindex;

                    for (int di = 1; di < numcoords; di++) {
                        Coordinate c = hands.getCoordinate(sst.juggler, pos, catchindex+di);
                        if (c == null)
                            continue;
                        ev = new JMLEvent();
                        if (h == MHNPattern.LEFT_HAND)
                            c.x = -c.x;
                        ev.setLocalCoordinate(c);
                        ev.calcpos = false;
                        ev.setT(lastcatchtime + (double)di*(sst.throwtime-lastcatchtime)/numcoords);
                        ev.setHand(sst.juggler, (h==MHNPattern.RIGHT_HAND?HandLink.RIGHT_HAND:HandLink.LEFT_HAND));
                        pat.addEvent(ev);
                    }

                    // figure out when the next catch or hold is
                    double nextcatchtime = lastcatchtime;
                    int k2 = k + 1;

                    while (nextcatchtime == lastcatchtime) {
                        int tempk = k2;
                        int wrap = 0;

                        while (tempk >= getIndexes()) {
                            tempk -= getIndexes();
                            wrap++;
                        }

                        if (wrap > 1)
                            throw new JuggleExceptionInternal("Couldn't find next catch/hold past t=" + lastcatchtime);

                        for (int tempslot = 0; tempslot < getMaxOccupancy(); tempslot++) {
                            MHNThrow tempsst = th[j][h][tempk][tempslot];
                            if (tempsst == null)
                                break;

                            double catcht = tempsst.catchtime + wrap * getIndexes() / bps;
                            nextcatchtime = (tempslot == 0 ? catcht : Math.min(nextcatchtime, catcht));
                        }

                        k2++;
                    }

                    // add other events between the current throw and the next catch
                    pos = sst.handsindex;
                    numcoords = hands.getCatchIndex(sst.juggler, pos);

                    for (int di = 1; di < numcoords; di++) {
                        Coordinate c = hands.getCoordinate(sst.juggler, pos, di);
                        if (c == null)
                            continue;
                        ev = new JMLEvent();
                        if (h == MHNPattern.LEFT_HAND)
                            c.x = -c.x;
                        ev.setLocalCoordinate(c);
                        ev.calcpos = false;
                        ev.setT(sst.throwtime + (double)di*(nextcatchtime-sst.throwtime)/numcoords);
                        ev.setHand(sst.juggler, (h==MHNPattern.RIGHT_HAND?HandLink.RIGHT_HAND:HandLink.LEFT_HAND));
                        pat.addEvent(ev);
                    }
                }
            }
        }
    }

    protected void addJugglerPositionsToJML(JMLPattern pat) {
        if (bodies == null)
            return;

        for (int k = 0; k < getPeriod(); k++) {
            for (int j = 0; j < getNumberOfJugglers(); j++) {
                int index = k % bodies.getPeriod(j + 1);
                int coords = bodies.getNumberOfPositions(j + 1, index);
                for (int z = 0; z < coords; z++) {
                    JMLPosition jmlp = bodies.getPosition(j + 1, index, z);
                    if (jmlp != null) {
                        jmlp.setT(((double)k + (double)z / (double)coords) / bps);
                        pat.addPosition(jmlp);
                    }
                }
            }
        }
    }

    protected void addEventsForUntouchedHandsToJML(JMLPattern pat, boolean[][] handtouched) {
        for (int j = 0; j < getNumberOfJugglers(); j++) {
            for (int h = 0; h < 2; h++) {
                if (!handtouched[j][h]) {
                    JMLEvent ev = new JMLEvent();
                    ev.setLocalCoordinate(new Coordinate((h==MHNPattern.RIGHT_HAND?restingx:-restingx), 0, 0));
                    ev.setT(-1.0);
                    ev.setHand(j+1, (h==0?HandLink.RIGHT_HAND:HandLink.LEFT_HAND));
                    ev.calcpos = false;
                    pat.addEvent(ev);
                }
            }
        }
    }

    protected void addEventsForUntouchedPathsToJML(JMLPattern pat, boolean[] pathtouched) {
        // first, apply all pattern symmetries to figure out which paths don't get touched
        for (int j = 0; j < pat.getNumberOfSymmetries(); j++) {
            Permutation perm = pat.getSymmetry(j).getPathPerm();
            for (int k = 0; k < getNumberOfPaths(); k++) {
                if (pathtouched[k]) {
                    for (int l = 1; l < perm.getOrder(k + 1); l++) {
                        pathtouched[perm.getMapping(k + 1, l) - 1] = true;
                    }
                }
            }
        }

        // next, add <holding> transitions for the untouched paths
        for (int k = 0; k < getNumberOfPaths(); k++) {
            if (pathtouched[k])
                continue;

            // figure out which hand it should belong in
            int hand = HandLink.LEFT_HAND;
            int juggler = 0;

            top:
            for (int tempk = 0; tempk < getIndexes(); tempk++) {
                for (int tempj = 0; tempj < getNumberOfJugglers(); tempj++) {
                    for (int temph = 0; temph < 2; temph++) {
                        for (int slot = 0; slot < getMaxOccupancy(); slot++) {
                            MHNThrow sst = th[tempj][temph][tempk][slot];
                            if (sst != null && sst.pathnum == (k + 1)) {
                                hand = (temph==MHNPattern.RIGHT_HAND?HandLink.RIGHT_HAND:HandLink.LEFT_HAND);
                                juggler = tempj;
                                break top;
                            }
                        }
                    }
                }
            }

            // add <holding> transitions to each of that hand's events
            JMLEvent ev = pat.getEventList();
            while (ev != null) {
                if (ev.getHand() == hand && ev.getJuggler() == (juggler + 1)) {
                    ev.addTransition(new JMLTransition(JMLTransition.TRANS_HOLDING, (k+1), null, null));

                    // mark related paths as touched
                    pathtouched[k] = true;
                    for (int j = 0; j < pat.getNumberOfSymmetries(); j++) {
                        Permutation perm = pat.getSymmetry(j).getPathPerm();
                        for (int l = 1; l < perm.getOrder(k + 1); l++) {
                            pathtouched[perm.getMapping(k + 1, l) - 1] = true;
                        }
                    }
                }

                ev = ev.getNext();
            }
            //  if (ev == null)
            //      throw new JuggleExceptionUser("Could not find event for hand");
        }
    }

    protected void addEventsForGapsToJML(JMLPattern pat) throws JuggleExceptionUser, JuggleExceptionInternal {
        for (int h = 0; h < 2; h++) {
            int hand = (h==0 ? HandLink.RIGHT_HAND : HandLink.LEFT_HAND);

            if (h == 1) {
                // only need to do this if there's a switch or switchdelay symmetry
                pat.buildEventList();
            }

            for (int j = 1; j <= getNumberOfJugglers(); j++) {
                JMLEvent ev = pat.getEventList();
                JMLEvent start = null;

                while (ev != null) {
                    if (ev.getJuggler() == j && ev.getHand() == hand) {
                        if (start != null) {
                            double gap = ev.getT() - start.getT();

                            if (gap > SECS_EVENT_GAP_MAX) {
                                int add = (int)(gap / SECS_EVENT_GAP_MAX);
                                double deltat = gap / (double)(add + 1);

                                for (int i = 1; i <= add; i++) {
                                    double evtime = start.getT() + i * deltat;
                                    if (evtime < pat.getLoopStartTime() || evtime >= pat.getLoopEndTime())
                                        continue;

                                    JMLEvent ev2 = new JMLEvent();
                                    ev2.setT(evtime);
                                    ev2.setHand(j, hand);
                                    ev2.calcpos = true;
                                    pat.addEvent(ev2);
                                }
                            }
                        }

                        start = ev;
                    }

                    ev = ev.getNext();
                }
            }
        }
    }

    protected void addLocationsForIncompleteEventsToJML(JMLPattern pat) {
        for (int j = 1; j <= getNumberOfJugglers(); j++) {
            for (int h = 0; h < 2; h++) {
                int hand = (h==MHNPattern.RIGHT_HAND?HandLink.RIGHT_HAND:HandLink.LEFT_HAND);

                JMLEvent ev = pat.getEventList();
                JMLEvent start = null;
                int scanstate = 1;  // 1 = starting, 2 = on defined event, 3 = on undefined event
                while (ev != null) {
                    if (ev.getJuggler() == j && ev.getHand() == hand) {
                        // System.out.println("j = "+j+", h = "+h+", t = "+ev.getT()+", calcpos = "+ev.calcpos);
                        // System.out.println("   initial state = "+scanstate);

                        if (ev.calcpos) {
                            scanstate = 3;
                        } else {
                            switch (scanstate) {
                                case 1:
                                    scanstate = 2;
                                    break;
                                case 2:
                                    break;
                                case 3:
                                    if (start != null) {
                                        JMLEvent end = ev;
                                        double t_end = end.getT();
                                        Coordinate pos_end = end.getLocalCoordinate();
                                        double t_start = start.getT();
                                        Coordinate pos_start = start.getLocalCoordinate();

                                        ev = start.getNext();
                                        while (ev != end) {
                                            if (ev.getJuggler() == j && ev.getHand() == hand) {
                                                double t = ev.getT();
                                                double x = pos_start.x + (t - t_start)*
                                                    (pos_end.x - pos_start.x) / (t_end - t_start);
                                                double y = pos_start.y + (t - t_start)*
                                                    (pos_end.y - pos_start.y) / (t_end - t_start);
                                                double z = pos_start.z + (t - t_start)*
                                                    (pos_end.z - pos_start.z) / (t_end - t_start);
                                                ev.setLocalCoordinate(new Coordinate(x, y, z));
                                                ev.calcpos = false;
                                            }
                                            ev = ev.getNext();
                                        }
                                    }
                                    scanstate = 2;
                                    break;
                            }
                            start = ev;
                        }
                        // System.out.println("   final state = "+scanstate);
                    }
                    ev = ev.getNext();
                }

                // do a last scan through to define any remaining undefined positions
                ev = pat.getEventList();
                while (ev != null) {
                    if (ev.getJuggler() == j && ev.getHand() == hand && ev.calcpos) {
                        ev.setLocalCoordinate(new Coordinate((h==MHNPattern.RIGHT_HAND?restingx:-restingx), 0, 0));
                        ev.calcpos = false;
                    }
                    ev = ev.getNext();
                }
            }
        }
    }

    // Step 9: Scan through the list of events, and look for cases where we need
    // to add additional <holding> transitions.  These are marked by cases where the
    // catch and throw transitions for a given path have intervening events in that
    // hand; we want to add <holding> transitions to these intervening events.

    protected void addMissingHoldsToJML(JMLPattern pat) {
        for (int k = 0; k < getNumberOfPaths(); k++) {
            boolean add_mode = false;
            boolean found_event = false;
            int add_juggler = 0, add_hand = 0;

            JMLEvent ev = pat.getEventList();
            while (ev != null) {
                JMLTransition tr = ev.getPathTransition((k+1), JMLTransition.TRANS_ANY);
                if (tr != null) {
                    switch (tr.getType()) {
                        case JMLTransition.TRANS_THROW:
                            if (!found_event && !add_mode) {
                                // first event mentioning path is a throw
                                // rewind to beginning of list and add holds
                                add_mode = true;
                                add_juggler = ev.getJuggler();
                                add_hand = ev.getHand();
                                ev = pat.getEventList();
                                continue;
                            }
                            add_mode = false;
                            break;
                        case JMLTransition.TRANS_CATCH:
                        case JMLTransition.TRANS_SOFTCATCH:
                        case JMLTransition.TRANS_GRABCATCH:
                            add_mode = true;
                            add_juggler = ev.getJuggler();
                            add_hand = ev.getHand();
                            break;
                        case JMLTransition.TRANS_HOLDING:
                            if (!found_event && !add_mode) {
                                // first event mentioning path is a hold
                                // rewind to beginning of list and add holds
                                add_mode = true;
                                add_juggler = ev.getJuggler();
                                add_hand = ev.getHand();
                                ev = pat.getEventList();
                                continue;
                            }
                            add_mode = true;
                            add_juggler = ev.getJuggler();
                            add_hand = ev.getHand();
                            break;
                    }
                    found_event = true;
                } else if (add_mode) {
                    if (ev.getJuggler()==add_juggler && ev.getHand()==add_hand && ev.isMaster())
                        ev.addTransition(new JMLTransition(JMLTransition.TRANS_HOLDING, (k+1), null, null));
                }
                ev = ev.getNext();
            }
        }
    }

}
