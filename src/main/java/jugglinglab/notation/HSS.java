// HSS.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.notation;

import java.text.MessageFormat;
import java.util.*;

import jugglinglab.util.*;


// This class adds Hand Siteswap (HSS) functionality to Juggling Lab's
// siteswap notation component.

class OssPatBnc {
    ArrayList<ArrayList<Character>> objPat;
    ArrayList<ArrayList<String>> bnc;
}

class HssParms {
    ArrayList<Character> pat;
    int hands;
}

class PatParms {
    String newPat;
    double[] dwellBt;
}

class ModParms {
    String convertedPattern;
    double[] dwellBeatsArray;
}

public class HSS {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    protected static double hss_dwell_default = 0.3;

    // This function is the external interface for the HSS processor. It takes
    // object and hand siteswaps and produces (among other things) a converted
    // pattern for the Siteswap component to animate.
    //
    // See SiteswapPattern.fromParameters().
    public static ModParms processHSS(String p, String h, boolean hld, boolean dwlmax,
                                      String hndspc, double dwl) throws JuggleExceptionUser {
        int ossPer, hssPer, hssOrb, numHnd, numJug;
        ModParms modinf = new ModParms();

        ArrayList<ArrayList<Character>> ossPat = new ArrayList<ArrayList<Character>>();
        ArrayList<Character> hssPat = new ArrayList<Character>();
        ArrayList<ArrayList<String>> bounc = new ArrayList<ArrayList<String>>();
        HssParms hssinfo;
        PatParms patinfo;
        OssPatBnc ossinfo;

        ossinfo = ossSyntax(p);
        ossPat = ossinfo.objPat;
        bounc = ossinfo.bnc;

        hssinfo = hssSyntax(h);

        hssPat = hssinfo.pat;
        numHnd = hssinfo.hands;
        ossPer = ossPat.size();
        hssPer = hssPat.size();

        ossPermTest(ossPat, ossPer);

        hssOrb = hssPermTest(hssPat, hssPer);

        int[][] handmap = (hndspc != null) ? parseHandspec(hndspc, numHnd) : defHandspec(numHnd);

        numJug = 1;
        for (int i = 0; i < numHnd; i++) {
            if (handmap[i][0] > numJug) {
                numJug = handmap[i][0];
            }
        }

        patinfo = convNotation(ossPat, hssPat, hssOrb, handmap, numJug, hld, dwlmax, dwl, bounc);
        modinf.convertedPattern = patinfo.newPat;
        if (modinf.convertedPattern == null) {
            throw new JuggleExceptionUser(errorstrings.getString("Error_no_pattern"));
        }

        modinf.dwellBeatsArray = patinfo.dwellBt;
        return modinf;
    }

    // ensure object pattern is a vanilla pattern (multiplex allowed)
    // also perform average test
    // convert input pattern string to ArrayList identifying throws made on each beat
    @SuppressWarnings("unused")
    private static OssPatBnc ossSyntax(String ss) throws JuggleExceptionUser {
        boolean muxThrow = false;
        boolean muxThrowFound = false;
        boolean minOneThrow = false;
        //at most three bounce characters are possible after siteswap number
        //b1, b2, b3 detect which'th bounce character may be expected as current character
        //only possible bounce strings are: B, BL, BF, BHL, BHF
        boolean b1 = false;
        boolean b2 = false;
        boolean b3 = false;
        int throwSum = 0;
        int numBeats = 0;
        int subBeats = 0; //for multiplex throws
        int numObj = 0;
        ArrayList<ArrayList<Character>> oPat = new ArrayList<ArrayList<Character>>();
        ArrayList<ArrayList<String>> bncinfo = new ArrayList<ArrayList<String>>();

        for (int i = 0; i < ss.length(); i++) {
            char c = ss.charAt(i);
            if (muxThrow) {
                if (Character.toString(c).matches("[0-9,a-z]")) {
                    minOneThrow = true;
                    muxThrowFound = true;
                    oPat.get(numBeats - 1).add(subBeats, c);
                    bncinfo.get(numBeats - 1).add(subBeats, "null");
                    subBeats++;
                    throwSum += Character.getNumericValue(c);
                    b1 = true;
                    b2 = false;
                    b3 = false;
                    continue;
                } else if (c == ']') {
                    if (muxThrowFound) {
                        muxThrow = false;
                        muxThrowFound = false;
                        subBeats = 0;
                        b1 = false;
                        b2 = false;
                        b3 = false;
                        continue;
                    } else {
                        String template = errorstrings.getString("Error_hss_object_syntax_error_at_pos");
                        Object[] arguments = { Integer.valueOf(i + 1) };
                        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                    }
                } else if (Character.isWhitespace(c)) {
                    b1 = false;
                    b2 = false;
                    b3 = false;
                    continue;
                } else if (c == 'B') {
                    if (b1) {
                        bncinfo.get(numBeats - 1).set(subBeats - 1, "B");
                        b1 = false;
                        b2 = true;
                        continue;
                    } else {
                        String template = errorstrings.getString("Error_hss_object_syntax_error_at_pos");
                        Object[] arguments = { Integer.valueOf(i + 1) };
                        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                    }
                } else if (c == 'F' || c == 'L') {
                    if (b2) {
                        bncinfo.get(numBeats - 1).set(subBeats - 1, "B" + Character.toString(c));
                        b2 = false;
                        continue;
                    } else if (b3) {
                        bncinfo.get(numBeats - 1).set(subBeats - 1, "BH" + Character.toString(c));
                        b3 = false;
                        continue;
                    } else {
                        String template = errorstrings.getString("Error_hss_object_syntax_error_at_pos");
                        Object[] arguments = { Integer.valueOf(i + 1) };
                        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                    }
                } else if (c == 'H') {
                    if (b2) {
                        bncinfo.get(numBeats - 1).set(subBeats - 1, "BH");
                        b2 = false;
                        b3 = true;
                    } else {
                        String template = errorstrings.getString("Error_hss_object_syntax_error_at_pos");
                        Object[] arguments = { Integer.valueOf(i + 1) };
                        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                    }
                } else {
                    String template = errorstrings.getString("Error_hss_object_syntax_error_at_pos");
                    Object[] arguments = { Integer.valueOf(i + 1) };
                    throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                }
            } else {
                if (Character.toString(c).matches("[0-9,a-z]")) {
                    minOneThrow = true;
                    oPat.add(numBeats, new ArrayList<Character>());
                    oPat.get(numBeats).add(subBeats, c);
                    bncinfo.add(numBeats, new ArrayList<String>());
                    bncinfo.get(numBeats).add(subBeats, "null");
                    numBeats++;
                    throwSum += Character.getNumericValue(c);
                    b1 = true;
                    b2 = false;
                    b3 = false;
                    continue;
                } else if (c == '[') {
                    muxThrow = true;
                    oPat.add(numBeats, new ArrayList<Character>());
                    bncinfo.add(numBeats, new ArrayList<String>());
                    numBeats++;
                    b1 = false;
                    b2 = false;
                    b3 = false;
                    continue;
                } else if (Character.isWhitespace(c)) {
                    b1 = false;
                    b2 = false;
                    b3 = false;
                    continue;
                } else if (c == 'B') {
                    if (b1) {
                        bncinfo.get(numBeats - 1).set(subBeats, "B");
                        b1 = false;
                        b2 = true;
                        continue;
                    } else {
                        String template = errorstrings.getString("Error_hss_object_syntax_error_at_pos");
                        Object[] arguments = { Integer.valueOf(i + 1) };
                        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                    }
                } else if (c == 'F' || c == 'L') {
                    if (b2) {
                        bncinfo.get(numBeats - 1).set(subBeats, "B" + Character.toString(c));
                        b2 = false;
                        continue;
                    } else if (b3) {
                        bncinfo.get(numBeats - 1).set(subBeats, "BH" + Character.toString(c));
                        b3 = false;
                        continue;
                    } else {
                        String template = errorstrings.getString("Error_hss_object_syntax_error_at_pos");
                        Object[] arguments = { Integer.valueOf(i + 1) };
                        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                    }
                } else if (c == 'H') {
                    if (b2) {
                        bncinfo.get(numBeats - 1).set(subBeats, "BH");
                        b2 = false;
                        b3 = true;
                    } else {
                        String template = errorstrings.getString("Error_hss_object_syntax_error_at_pos");
                        Object[] arguments = { Integer.valueOf(i + 1) };
                        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                    }
                } else {
                    String template = errorstrings.getString("Error_hss_object_syntax_error_at_pos");
                    Object[] arguments = { Integer.valueOf(i + 1) };
                    throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                }
            } // if-else muxThrow
        } //for
        if (!muxThrow && minOneThrow) {
            if (throwSum % numBeats == 0) {
                numObj = throwSum / numBeats;
            } else {
                throw new JuggleExceptionUser(errorstrings.getString("Error_hss_bad_average_object"));
            }
        } else {
            throw new JuggleExceptionUser(errorstrings.getString("Error_hss_syntax_error"));
        }
        //append a space after setting the bounceinfo arraylist. Eventually bouncinfo
        //is appended to iph so the space will get transferred there.
        //Reason for space:
        //The primary reason is that if a multiplex throw involving a pass is created
        //then the space will ensure the multiplex throw is written as, for example,
        //[5p3 4] as opposed to [5p34]. In the former, a 5 is to be passed to juggler 3
        //and simultaneously a multiplex 4 is to be thrown. In the latter, it'll get
        //interpreted as a 5 to be passed to juggler 34. In other cases, the space may
        //not matter. However, for coding convenience, the space is appended everywhere.
        for (int i = 0; i < bncinfo.size(); i++) {
            for (int j = 0; j < bncinfo.get(i).size(); j++) {
                if (bncinfo.get(i).get(j) == "null") {
                    bncinfo.get(i).set(j, " ");
                } else {
                    bncinfo.get(i).set(j, bncinfo.get(i).get(j) + " ");
                }
            }
        }
        OssPatBnc ossinf = new OssPatBnc();
        ossinf.objPat = oPat;
        ossinf.bnc = bncinfo;

        return ossinf;
    } //OssSyntax

    // ensure hand pattern is a vanilla pattern (multiplex NOT allowed)
    // also perform average test
    // convert input pattern string to ArrayList identifying throws made on each beat
    // also return number of hands, used later to build handmap
    private static HssParms hssSyntax(String ss) throws JuggleExceptionUser {
        int throwSum = 0;
        int numBeats = 0;
        int nHnds = 0;
        ArrayList<Character> hPat = new ArrayList<Character>();
        for (int i = 0; i < ss.length(); i++) {
            char c = ss.charAt(i);
            if (Character.toString(c).matches("[0-9,a-z]")) {
                hPat.add(numBeats, c);
                numBeats++;
                throwSum += Character.getNumericValue(c);
                continue;
            } else if (Character.isWhitespace(c)) {
                continue;
            } else {
                String template = errorstrings.getString("Error_hss_hand_syntax_error_at_pos");
                Object[] arguments = { Integer.valueOf(i + 1) };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
        if (throwSum % numBeats == 0) {
            nHnds = throwSum / numBeats;
        } else {
            throw new JuggleExceptionUser(errorstrings.getString("Error_hss_bad_average_hand"));
        }

        HssParms hssinf = new HssParms();
        hssinf.pat = hPat;
        hssinf.hands = nHnds;

        return hssinf;
    }

    // permutation test for object pattern
    private static void ossPermTest(ArrayList<ArrayList<Character>> os, int op) throws JuggleExceptionUser {
        ArrayList<ArrayList<Integer>> mods = new ArrayList<ArrayList<Integer>>();
        int modulo;
        int[] cmp = new int[op];
        for (int i = 0; i < op; i++) {
            mods.add(i, new ArrayList<Integer>());
            for (int j = 0; j < os.get(i).size(); j++) {
                modulo = (Character.getNumericValue(os.get(i).get(j)) + i) % op;
                mods.get(i).add(j, modulo);
                cmp[modulo]++;
            }
        }

        for (int i = 0; i < op; i++) {
            if (cmp[i] != os.get(i).size()) {
                throw new JuggleExceptionUser(errorstrings.getString("Error_hss_object_pattern_invalid"));
            }
        }
    }

    // permutation test for hand pattern
    // return overall hand orbit period which is lcm of individual hand orbit periods
    private static int hssPermTest(ArrayList<Character> hs, int hp) throws JuggleExceptionUser {
        int modulo, ho;
        int[] mods = new int[hp];
        int[] cmp = new int[hp];
        int[] orb = new int[hp];
        boolean[] touched = new boolean[hp];
        for (int i = 0; i < hp; i++) {
            modulo = (Character.getNumericValue(hs.get(i)) + i) % hp;
            mods[i] = modulo;
            cmp[modulo]++;
        }

        for (int i = 0; i < hp; i++) {
            if (cmp[i] != 1) {
                throw new JuggleExceptionUser(errorstrings.getString("Error_hss_hand_pattern_invalid"));
            }
        }
        ho = 1;
        for (int i = 0; i < hp; i++) {
            if (!touched[i]) {
                int j = i;
                orb[i] = Character.getNumericValue(hs.get(i));
                touched[i] = true;
                j = mods[i];
                while (j != i) {
                    orb[i] += Character.getNumericValue(hs.get(j));
                    touched[j] = true;
                    j = mods[j];
                }
            }
            if (orb[i] != ho && orb[i] != 0) {
                ho = Permutation.lcm(orb[i], ho);
            }
        }
        return ho;
    }

    // read and validate user defined handspec
    // if valid, convert to handmap assigning juggler number and that juggler's left or right hand to each hand
    private static int[][] parseHandspec(String hspec, int nh) throws JuggleExceptionUser {
        int[][] hmap = new int[nh][2]; //handmap: map hand number to juggler number (first index) and left/right hand (second index)
        boolean assignLH = false; //assignLeftHand
        boolean assignRH = false; //assignRightHand
        boolean jugAct = false; //jugglerActive
        boolean pass = false;
        boolean handPresent = false;
        boolean numFormStart = false; //numberformationStarted
        boolean matchFnd = false; //matchFound
        int jugNum = 0; //jugglerNumber
        String buildHndNum = null; //buildHandNumber

        for (int i = 0; i < hspec.length(); i++) {
            char c = hspec.charAt(i);
            if (!jugAct) { //if not in the middle of processing a () pair
                if (c == '(') {
                    jugAct = true; //juggler assignment for the current hand is now active
                    assignLH = true; //at opening "(" assign left hand
                    numFormStart = true; //hand number might be a multiple digit number
                    buildHndNum = null;
                    pass = false;
                    handPresent = false;
                    jugNum++;
                    continue;
                } else if (Character.isWhitespace(c)) {
                    continue;
                } else {
                    String template = errorstrings.getString("Error_hss_handspec_syntax_error_at_pos");
                    Object[] arguments = { Integer.valueOf(i + 1) };
                    throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                }
            } else {
                if (assignLH) {
                    if (Character.toString(c).matches("[0-9]")) {
                        if (numFormStart) {
                            if (buildHndNum == null) {
                                buildHndNum = Character.toString(c);
                            } else {
                                buildHndNum = buildHndNum + Character.toString(c);
                            }
                            continue;
                        } else {
                            String template = errorstrings.getString("Error_hss_handspec_syntax_error_at_pos");
                            Object[] arguments = { Integer.valueOf(i + 1) };
                            throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                        }
                    } else if (Character.isWhitespace(c)) {
                        if ((buildHndNum != null) && (buildHndNum.length() >= 1)) {
                            numFormStart = false;
                            continue;
                        } else {
                            continue;
                        }
                    } else if (c == ',') {
                        assignLH = false; // at "," left hand assignment complete
                        assignRH = true;
                        numFormStart = true;

                        if (buildHndNum != null) {
                            if ((Integer.parseInt(buildHndNum) >= 1) && (Integer.parseInt(buildHndNum) <= nh)) {
                                if (hmap[Integer.parseInt(buildHndNum) - 1][0] == 0) { //if juggler not already assigned to this hand
                                    hmap[Integer.parseInt(buildHndNum) - 1][0] = jugNum;
                                    hmap[Integer.parseInt(buildHndNum) - 1][1] = 0;
                                    handPresent = true;
                                } else {
                                    String template = errorstrings.getString("Error_hss_hand_assigned_more_than_once");
                                    Object[] arguments = { Integer.valueOf(buildHndNum) };
                                    throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                                }
                            } else {
                                String template = errorstrings.getString("Error_hss_hand_number_out_of_range");
                                Object[] arguments = { Integer.valueOf(buildHndNum) };
                                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                            }
                        } else {
                            handPresent = false;
                        }
                        buildHndNum = null; //reset bhn string
                        continue;
                    } else {
                        String template = errorstrings.getString("Error_hss_handspec_syntax_error_at_pos");
                        Object[] arguments = { Integer.valueOf(i + 1) };
                        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                    }
                } else if (assignRH) {
                    if (Character.toString(c).matches("[0-9]")) {
                        if (numFormStart) {
                            if (buildHndNum == null) {
                                buildHndNum = Character.toString(c);
                            } else {
                                buildHndNum = buildHndNum + Character.toString(c);
                            }
                            continue;
                        } else {
                            String template = errorstrings.getString("Error_hss_handspec_syntax_error_at_pos");
                            Object[] arguments = { Integer.valueOf(i + 1) };
                            throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                        }
                    } else if (Character.isWhitespace(c)) {
                        if ((buildHndNum != null) && (buildHndNum.length() >= 1)) {
                            numFormStart = false;
                            continue;
                        } else {
                            continue;
                        }
                    } else if (c == ')') {
                        assignRH = false;
                        jugAct = false; //juggler assignment is inactive after ")"

                        if (buildHndNum != null) {
                            if ((Integer.parseInt(buildHndNum) >= 1) && (Integer.parseInt(buildHndNum) <= nh)) {
                                hmap[Integer.parseInt(buildHndNum) - 1][0] = jugNum;
                                hmap[Integer.parseInt(buildHndNum) - 1][1] = 1;
                            } else {
                                String template = errorstrings.getString("Error_hss_hand_number_out_of_range");
                                Object[] arguments = { Integer.valueOf(buildHndNum) };
                                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                            }
                        } else if (!handPresent) { //if left hand was also not present
                            throw new JuggleExceptionUser(errorstrings.getString("Error_hss_at_least_one_hand_per_juggler"));
                        }
                        buildHndNum = null; //reset bhn string
                        pass = true;
                        continue;
                    } else {
                        String template = errorstrings.getString("Error_hss_handspec_syntax_error_at_pos");
                        Object[] arguments = { Integer.valueOf(i + 1) };
                        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                    }

                }

            }

        }
        if (jugNum > nh) {  //will this ever happen?
            String template = errorstrings.getString("Error_hss_handspec_too_many_jugglers");
            Object[] arguments = { Integer.valueOf(nh) };
            throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
        }
        if (pass) {
            for (int i = 0; i < nh; i++) {  //what is this for?
                if (hmap[i][0] != 0) {
                    matchFnd = true;
                    break;
                }
                if (!matchFnd) {
                    String template = errorstrings.getString("Error_hss_handspec_hand_missing");
                    Object[] arguments = { Integer.valueOf(i + 1) };
                    throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                }
            }
        } else {
            throw new JuggleExceptionUser(errorstrings.getString("Error_hss_handspec_syntax_error"));
        }

        for (int i = 0; i < nh; i++) {
            if (hmap[i][0] == 0) {
                String template = errorstrings.getString("Error_hss_juggler_not_assigned_to_hand");
                Object[] arguments = { Integer.valueOf(i + 1) };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
        return hmap;
    }

    // in the absence of user defined handspec, build a default handmap
    // assume numHnd/2 jugglers if numHnd even, else (numHnd+1)/2 jugglers
    // assign hand 1 to J1 right hand, hand 2 to J2 right hand and so on
    // once all right hands assigned, come back to J1 and start assigning left hand
    private static int[][] defHandspec(int nh) {
        int[][] hmap = new int[nh][2];
        int nJugs; //numberofJugglers

        if (nh % 2 == 0) {
            nJugs = nh / 2;
        } else {
            nJugs = (nh + 1) / 2;
        }

        for (int i = 0; i < nh; i++) {
            if (i < nJugs) {
                hmap[i][0] = i + 1; // juggler number
                hmap[i][1] = 1; // 0 for left hand, 1 for right
            } else {
                hmap[i][0] = i + 1 - nJugs;
                hmap[i][1] = 0;
            }
        }

        return hmap;
    }

    //convert oss hss format to Juggling Lab synchronous passing notation with suppressed
    //empty beats so that odd synchronous throws are also allowed
    private static PatParms convNotation(ArrayList<ArrayList<Character>> os,
                                        ArrayList<Character> hs, int ho, int[][] hm, int nj,
                                        boolean hldOpt, boolean dwlMaxOpt, double defDwl,
                                        ArrayList<ArrayList<String>> bncStr) throws JuggleExceptionUser {
        //pattern period, current hand, throw value, current juggler, ossPeriod, hssPeriod
        int patPer, currHand, throwVal, currJug, objPer, hndPer;
        String modPat = null;  //modified pattern
        PatParms patinf = new PatParms();
        boolean flag = false;

        //invert, pass and hold for x, p and H
        ArrayList<ArrayList<String>> iph = new ArrayList<ArrayList<String>>();

        objPer = os.size();
        hndPer = hs.size();
        patPer = Permutation.lcm(objPer, ho); //pattern period

        int[] ah = new int[patPer]; //assigned hand
        boolean assignDone[] = new boolean[patPer];
        int[][] ji = new int[patPer][2]; //jugglerInfo: juggler#, hand#
        double[] dwlBts = new double[patPer]; //dwell beats

        //extend oss size to pp
        if (patPer > objPer) {
            for (int i = objPer; i < patPer; i++) {
                os.add(i, os.get(i - objPer));
            }
        }

        //extend bounce size to pp
        if (patPer > objPer) {
            for (int i = objPer; i < patPer; i++) {
                bncStr.add(i, bncStr.get(i - objPer));
            }
        }

        //extend hss size to pp
        if (patPer > hndPer) {
            for (int i = hndPer; i < patPer; i++) {
                hs.add(i, hs.get(i - hndPer));
            }
        }

        //check if hss is 0 when oss is not 0; else assign juggler and hand to each beat
        currHand = 0;
        for (int i = 0; i < patPer; i++) {
            if (hs.get(i) == '0') {
                for (int j = 0; j < os.get(i).size(); j++) {
                    if (os.get(i).get(j) != '0') {
                        String template = errorstrings.getString("Error_hss_no_hand_to_throw_at_beat");
                        Object[] arguments = { Integer.valueOf(i + 1) };
                        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                    }
                }
                ji[i][0] = 0; //assign juggler number 0 for no hand
                ji[i][1] = -1; //assign hand number -1 for no hand
                assignDone[i] = true;
            } else {
                if (!assignDone[i]) {
                    currHand++;
                    ah[i] = currHand;
                    assignDone[i] = true;
                    int next = (i + Character.getNumericValue(hs.get(i))) % patPer;
                    while (next != i) {
                        ah[next] = currHand;
                        assignDone[next] = true;
                        next = (next + Character.getNumericValue(hs.get(next))) % patPer;
                    }
                }
                ji[i][0] = hm[ah[i] - 1][0]; //juggler number at beat i based on handmap
                ji[i][1] = hm[ah[i] - 1][1]; //throwing hand at beat i based on handmap
            }
        }

        //determine dwellbeats array
        flag = false;
        int[] mincaught = new int[patPer];
        int tgtIdx = 0; //target index
        int curThrow; //current throw

        // find minimum throw being caught at each beat: more than one throw could be
        // getting caught in case of multiplex throw. Dwell time on that beat will be
        // maximized for the minimum throw being caught. Higher throws may thus show
        // more flight time than is strictly required going by hand availability.
        for (int i = 0; i < patPer; i++) {
            for (int j = 0; j < os.get(i).size(); j++) {
                curThrow = Character.getNumericValue(os.get(i).get(j));
                tgtIdx = (i + curThrow)  % patPer;
                if (curThrow > 0) {
                    if (mincaught[tgtIdx] == 0) {
                        mincaught[tgtIdx] = curThrow;
                    } else if (curThrow < mincaught[tgtIdx]) {
                        mincaught[tgtIdx] = curThrow;
                    }
                }
            }
        }
        if (!dwlMaxOpt) {
            for (int i = 0; i < patPer; i++) {
                if ((ji[i][0] == ji[(i + 1) % patPer][0]) && (ji[i][1] == ji[(i + 1) % patPer][1])) {
                    flag = true; //if same hand throws on successive beats
                    break;
                }
            }
            if (flag) {
                for (int i = 0; i < patPer; i++) {
                    dwlBts[i] = hss_dwell_default;
                }
            } else {
                for (int i = 0; i < patPer; i++) {
                    dwlBts[i] = defDwl; //user defined default dwell in front panel
                }
            }
            for (int i = 0; i < patPer; i++) {
                if (dwlBts[i] >= (double)mincaught[i]) {
                    dwlBts[i] = (double)mincaught[i] - (1 - hss_dwell_default);
                }
            }
        } else {//if dwellmax is true
            for (int i = 0; i < patPer; i++) {
                int j = (i + 1) % patPer;
                int diff = 1;
                while ((ji[i][0] != ji[j][0]) || (ji[i][1] != ji[j][1])) {
                    j = (j + 1) % patPer;
                    diff++;
                }
                dwlBts[j] = (double)diff - (1 - hss_dwell_default);
            }
            for (int i = 0; i < patPer; i++) {
                if (dwlBts[i] >= (double)mincaught[i]) {
                    dwlBts[i] = (double)mincaught[i] - (1 - hss_dwell_default);
                } else if (dwlBts[i] <= 0) {
                    dwlBts[i] = hss_dwell_default;
                }
            }
        }

        //remove clashes in db array in case dwell times from different beats get optimized
        //to same time instant
        boolean[] clash = new boolean[patPer];
        int clashcnt = 0;
        for (int i = 0; i < patPer; i++) {
            for (int j = 1; j < patPer; j++) {
                if ((dwlBts[(i + j) % patPer] - dwlBts[i] - j) % patPer == 0) {
                    clash[(i + j) % patPer] = true;
                    clashcnt++;
                }
            }
            while (clashcnt != 0) {
                for (int k = 0; k < patPer; k++) {
                    if (clash[k]) {
                        dwlBts[k] = dwlBts[k] + hss_dwell_default / clashcnt;
                        clashcnt--;
                        clash[k] = false;
                    }
                }
            }
        }

        patinf.dwellBt = dwlBts;

        //determine x, p and H throws
        for (int i = 0; i < patPer; i++) {
            iph.add(i, new ArrayList<String>());
            for (int j = 0; j < os.get(i).size(); j++) {
                iph.get(i).add(j, null);
                throwVal = Character.getNumericValue(os.get(i).get(j));

                int sourceJug = ji[i][0];
                int sourceHnd = ji[i][1];
                int targetJug = ji[(i + throwVal) % patPer][0];
                int targetHnd = ji[(i + throwVal) % patPer][1];

                if (throwVal % 2 == 0 && sourceHnd != targetHnd) {
                    iph.get(i).set(j, "x"); //put x for even throws to other hand
                } else if (throwVal % 2 != 0 && sourceHnd == targetHnd) {
                    iph.get(i).set(j, "x"); //put x for odd throws to same hand
                }
                if (sourceJug != targetJug) {
                    if (iph.get(i).get(j) != "x") {
                        iph.get(i).set(j, "p" + targetJug);
                    } else {
                        iph.get(i).set(j, "xp" + targetJug);
                    }
                } else if (hldOpt) {
                    if (throwVal == Character.getNumericValue(hs.get(i))) {
                        if (iph.get(i).get(j) != "x") {
                            iph.get(i).set(j, "H"); //enable hold for even throw to same hand
                        } else {
                            iph.get(i).set(j, "xH"); //enable hold for odd throw to same hand
                        }
                    }
                }
            }

        }

        for (int i = 0; i < patPer; i++) {
            for (int j = 0; j < bncStr.get(i).size(); j++) {
                if (iph.get(i).get(j) == null) {
                    iph.get(i).set(j, bncStr.get(i).get(j));
                } else {
                    iph.get(i).set(j, iph.get(i).get(j) + bncStr.get(i).get(j));
                }
            }
        }

        //construct the pattern string
        for (int i = 0; i < patPer; i++) {
            currJug = 0;
            while (currJug < nj) {
                if (modPat == null) {  //at the start of building the converted pattern
                    modPat = "<";
                } else if (currJug == 0) { //at the start of a new beat
                    modPat = modPat + "<";
                }
                if (ji[i][1] == 0) {//if left hand is throwing at current beat
                    modPat = modPat + "(";
                    if (ji[i][0] == currJug + 1) { //if currentjuggler is throwing at current beat
                        if (os.get(i).size() > 1) {// if it is a multiplex throw
                            modPat = modPat + "[";
                            for (int j = 0; j < os.get(i).size(); j++) {
                                modPat = modPat + Character.toString(os.get(i).get(j));
                                if (iph.get(i).get(j) != null) {
                                    modPat = modPat + iph.get(i).get(j);
                                }
                            }
                            modPat = modPat + "]";
                        } else {// if not multiplex throw
                            modPat = modPat + Character.toString(os.get(i).get(0));
                            if (iph.get(i).get(0) != null) {
                                modPat = modPat + iph.get(i).get(0);
                            }
                        }
                        modPat = modPat + ",0)!"; //no sync throws allowed, put 0 for right hand
                        if (currJug == nj - 1) {
                            modPat = modPat + ">";
                        } else {
                            modPat = modPat + "|";
                        }
                        currJug++;
                    } else {// if current juggler is not throwing at this beat
                        modPat = modPat + "0,0)!";
                        if (currJug == nj - 1) {
                            modPat = modPat + ">";
                        } else {
                            modPat = modPat + "|";
                        }
                        currJug++;
                    }
                } else {// if right hand is throwing at this beat
                    modPat = modPat + "(0,";  //no sync throws allowed, put 0 for left hand
                    if (ji[i][0] == currJug + 1) { //if currentjuggler is throwing at current beat
                        if (os.get(i).size() > 1) {// if it is a multiplex throw
                            modPat = modPat + "[";
                            for (int j = 0; j < os.get(i).size(); j++) {
                                modPat = modPat + Character.toString(os.get(i).get(j));
                                if (iph.get(i).get(j) != null) {
                                    modPat = modPat + iph.get(i).get(j);
                                }
                            }
                            modPat = modPat + "]";
                        } else {// if not multiplex throw
                            modPat = modPat + Character.toString(os.get(i).get(0));
                            if (iph.get(i).get(0) != null) {
                                modPat = modPat + iph.get(i).get(0);
                            }
                        }
                        modPat = modPat + ")!";
                        if (currJug == nj - 1) {
                            modPat = modPat + ">";
                        } else {
                            modPat = modPat + "|";
                        }
                        currJug++;
                    } else {// if current juggler is not throwing at this beat
                        modPat = modPat + "0)!";
                        if (currJug == nj - 1) {
                            modPat = modPat + ">";
                        } else {
                            modPat = modPat + "|";
                        }
                        currJug++;
                    }
                } //if-else left-right hand
            } //while currJug < nj
        } //for all beats

        patinf.newPat = modPat;
        return patinf;
    } //convNotation
}
