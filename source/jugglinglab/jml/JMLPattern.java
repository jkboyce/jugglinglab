// JMLPattern.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.jml;

import java.io.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.ResourceBundle;
import org.xml.sax.*;

import jugglinglab.core.Constants;
import jugglinglab.curve.*;
import jugglinglab.notation.Pattern;
import jugglinglab.path.*;
import jugglinglab.prop.Prop;
import jugglinglab.renderer.Juggler;
import jugglinglab.util.*;


/*------------------------------------------------------------------------------
This is one of the core classes, representing a juggling pattern in generalized
form. It is used in three steps:

1) Define a pattern, in one of four ways:

   a) Manually, by calling methods in this class.
   b) Parsing from pre-existing JML stream (file, user input, etc.).
      (JML = Juggling Markup Language, an XML document type)
   c) Output from a Notation instance's asJMLPattern() method.
   d) The fromBasePattern() method in this class.

2) Call layoutPattern() to calculate flight paths for all the props and hands.

3) Call various methods to get information about the pattern, e.g., prop/hand
   coordinates at points in time.
------------------------------------------------------------------------------*/

public class JMLPattern {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    protected String version = JMLDefs.CURRENT_JML_VERSION;
    protected String title;
    protected String info;
    protected ArrayList<String> tags;
    protected int numjugglers;
    protected int numpaths;
    protected ArrayList<PropDef> props;
    protected int[] propassignment;

    // for retaining the base pattern this pattern was created from
    protected String base_pattern_notation;
    protected String base_pattern_config;
    protected int base_pattern_hashcode;
    protected boolean base_pattern_hashcode_valid;

    // whether pattern has a velocity-defining transition
    protected boolean[] hasVDPathJMLTransition;  // for a given path
    protected boolean[][] hasVDHandJMLTransition;  // for a given juggler/hand

    protected ArrayList<JMLSymmetry> symmetries;
    protected JMLEvent eventlist;
    protected JMLPosition positionlist;

    // list of PathLink objects for each path
    protected ArrayList<ArrayList<PathLink>> pathlinks;

    // list of HandLink objects for each juggler/hand combination
    protected ArrayList<ArrayList<ArrayList<HandLink>>> handlinks;

    protected Curve[] jugglercurve;  // coordinates for each juggler
    protected Curve[] jugglerangle;  // angles for each juggler

    protected String loadingversion = JMLDefs.CURRENT_JML_VERSION;
    protected boolean laidout;
    protected boolean valid;


    public JMLPattern() {
        laidout = false;
        valid = true;
        tags = new ArrayList<String>();
        props = new ArrayList<PropDef>();
        symmetries = new ArrayList<JMLSymmetry>();
    }

    public JMLPattern(JMLNode root) throws JuggleExceptionUser {
        this();
        readJML(root);
        valid = true;
    }

    // Used to specify the jml version number, when pattern is part of a patternlist
    public JMLPattern(JMLNode root, String jmlvers) throws JuggleExceptionUser {
        this();
        loadingversion = jmlvers;
        readJML(root);
        valid = true;
    }

    public JMLPattern(Reader read) throws JuggleExceptionUser, JuggleExceptionInternal {
        this();
        try {
            JMLParser parser = new JMLParser();
            parser.parse(read);
            readJML(parser.getTree());
            valid = true;
        } catch (SAXException se) {
            throw new JuggleExceptionUser(se.getMessage());
        } catch (IOException ioe) {
            throw new JuggleExceptionInternal(ioe.getMessage());
        }
    }

    public JMLPattern(JMLPattern pat) throws JuggleExceptionUser, JuggleExceptionInternal {
        this(new StringReader(pat.toString()));
    }

    //-------------------------------------------------------------------------
    // Methods to define the pattern
    //-------------------------------------------------------------------------

    public void setTitle(String t) {
        if (t != null)
            t = t.replaceAll(";", "");  // semicolons not allowed in titles

        title = ((t != null && t.strip().length() > 0) ? t.strip() : null);

        // Check if there is a base pattern defined, and if so set the new title
        // in the base pattern as well
        if (base_pattern_notation == null || base_pattern_config == null)
            return;

        try {
            ParameterList pl = new ParameterList(base_pattern_config);

            // Is the title equal to the default title? If so then remove the
            // title parameter
            if (pl.getParameter("pattern").equals(title))
                pl.removeParameter("title");
            else
                pl.addParameter("title", title == null ? "" : title);

            base_pattern_config = pl.toString();
            base_pattern_hashcode_valid = false;  // recalculate hash code
        } catch (JuggleExceptionUser jeu) {
            // can't be a user error since base pattern has already successfully
            // compiled
            ErrorDialog.handleFatalException(new JuggleExceptionInternal(jeu.getMessage()));
        }
    }

    public void setInfo(String info_string) {
        if (info_string != null && info_string.strip().length() > 0)
            info = info_string.strip();
        else
            info = null;
    }

    public void addTag(String tag) {
        if (tag != null && tag.length() > 0 && !isTaggedWith(tag))
            tags.add(tag);
    }

    public boolean removeTag(String tag) {
        if (tag == null || !isTaggedWith(tag))
            return false;

        for (int i = 0; i < tags.size(); i++) {
            if (tags.get(i).equalsIgnoreCase(tag)) {
                tags.remove(i);
                return true;
            }
        }
        return false;  // shouldn't happen
    }

    public void setNumberOfJugglers(int n) {
        numjugglers = n;
        setNeedsLayout();
    }

    public void setNumberOfPaths(int n) {
        numpaths = n;
        setNeedsLayout();
    }

    public void addProp(PropDef pd) {
        props.add(pd);
        setNeedsLayout();
    }

    public void removeProp(int propnum) {
        props.remove(propnum - 1);
        for (int i = 1; i <= getNumberOfPaths(); i++) {
            if (getPropAssignment(i) > propnum)
                setPropAssignment(i, getPropAssignment(i) - 1);
        }
        setNeedsLayout();
    }

    public void setPropAssignment(int pathnum, int propnum) {
        propassignment[pathnum - 1] = propnum;
        setNeedsLayout();
    }

    public void setPropAssignments(int[] pa) {
        propassignment = pa;
        setNeedsLayout();
    }

    public void addSymmetry(JMLSymmetry sym) {
        symmetries.add(sym);
        setNeedsLayout();
    }

    public void addEvent(JMLEvent ev) {
        setNeedsLayout();

        if (eventlist == null || ev.getT() < eventlist.getT()) {
            // set `ev` as new list head
            ev.setPrevious(null);
            ev.setNext(eventlist);
            if (eventlist != null)
                eventlist.setPrevious(ev);
            eventlist = ev;
            return;
        }

        JMLEvent current = eventlist;
        while (true) {
            boolean combine_events =
                    current.getT() == ev.getT() &&
                    current.getHand() == ev.getHand() &&
                    current.getJuggler() == ev.getJuggler();

            if (combine_events) {
                // replace `current` with `ev` in the list...
                ev.setPrevious(current.getPrevious());
                ev.setNext(current.getNext());
                if (current.getNext() != null)
                    current.getNext().setPrevious(ev);
                if (current.getPrevious() == null)
                    eventlist = ev;  // new head of the list
                else
                    current.getPrevious().setNext(ev);

                // ...then move all the transitions from `current` to `ev`,
                // except those for a path number that already has a transition
                // in `ev`.
                for (int i = 0; i < current.getNumberOfTransitions(); ++i) {
                    JMLTransition tr_current = current.getTransition(i);
                    boolean add_transition = true;

                    for (int j = 0; j < ev.getNumberOfTransitions(); ++j) {
                        if (ev.getTransition(j).getPath() == tr_current.getPath())
                            add_transition = false;
                    }

                    if (add_transition)
                        ev.addTransition(tr_current);
                }
                return;
            }

            if (ev.getT() < current.getT()) {
                // insert `ev` before `current`
                ev.setNext(current);
                ev.setPrevious(current.getPrevious());
                current.getPrevious().setNext(ev);
                current.setPrevious(ev);
                return;
            }

            if (current.getNext() == null) {
                // append `ev` at the list end, after current
                current.setNext(ev);
                ev.setNext(null);
                ev.setPrevious(current);
                return;
            }

            current = current.getNext();
        }
    }

    public void removeEvent(JMLEvent ev) {
        setNeedsLayout();
        if (eventlist == ev) {
            eventlist = ev.getNext();
            if (eventlist != null)
                eventlist.setPrevious(null);
            return;
        }

        JMLEvent next = ev.getNext();
        JMLEvent prev = ev.getPrevious();
        if (next != null)
            next.setPrevious(prev);
        if (prev != null)
            prev.setNext(next);
    }

    public JMLEvent getEventList() {
        return eventlist;
    }

    // used for debugging
    protected void printEventList() {
        JMLEvent current = eventlist;
        java.io.PrintWriter pw = new java.io.PrintWriter(System.out);
        while (current != null) {
            if (current.isMaster())
                System.out.println("  Master event:");
            else
                System.out.println("  Slave event; master at t="+current.getMaster().getT());

            try {
                current.writeJML(pw);
            } catch (java.io.IOException ioe) {
            }
            pw.flush();
            current = current.getNext();
        }
    }

    public ArrayList<ArrayList<PathLink>> getPathLinks() {
        return pathlinks;
    }

    public void addPosition(JMLPosition pos) {
        if (pos.getT() < getLoopStartTime() || pos.getT() > getLoopEndTime())
            return;  // throw new JuggleExceptionUser("<position> time out of range");
        setNeedsLayout();

        if (positionlist == null || positionlist.getT() > pos.getT()) {
            pos.setPrevious(null);
            pos.setNext(positionlist);
            if (positionlist != null)
                positionlist.setPrevious(pos);
            positionlist = pos;
            return;
        }

        JMLPosition current = positionlist;

        while (current.getNext() != null) {
            current = current.getNext();

            if (current.getT() > pos.getT()) {
                pos.setNext(current);
                pos.setPrevious(current.getPrevious());
                current.getPrevious().setNext(pos);
                current.setPrevious(pos);
                return;
            }
        }

        current.setNext(pos);
        pos.setNext(null);
        pos.setPrevious(current);
    }

    public void removePosition(JMLPosition pos) {
        setNeedsLayout();
        if (positionlist == pos) {
            positionlist = pos.getNext();
            if (positionlist != null)
                positionlist.setPrevious(null);
            return;
        }

        JMLPosition next = pos.getNext();
        JMLPosition prev = pos.getPrevious();
        if (next != null)
            next.setPrevious(prev);
        if (prev != null)
            prev.setNext(next);
    }

    public JMLPosition getPositionList() {
        return positionlist;
    }

    public int getHashCode() {
        StringWriter sw = new StringWriter();
        try {
            // Omit <info> tag metadata for the purposes of evaluating hash code.
            // Two patterns that differ only by metadata are treated as identical.
            writeJML(sw, true, false);
        } catch (IOException ioe) {
        }

        return sw.toString().hashCode();
    }

    //-------------------------------------------------------------------------
    // Methods related to the base pattern (if set)
    //-------------------------------------------------------------------------

    public String getBasePatternNotation() {
        return base_pattern_notation;
    }

    public String getBasePatternConfig() {
        return base_pattern_config;
    }

    public boolean hasBasePattern() {
        return (getBasePatternNotation() != null && getBasePatternConfig() != null);
    }

    public boolean isBasePatternEdited() {
        if (base_pattern_notation == null || base_pattern_config == null)
            return false;

        if (!base_pattern_hashcode_valid) {
            try {
                base_pattern_hashcode = JMLPattern
                        .fromBasePattern(base_pattern_notation, base_pattern_config)
                        .layoutPattern()
                        .getHashCode();
                base_pattern_hashcode_valid = true;
            } catch (JuggleException je) {
                base_pattern_hashcode = 0;
                base_pattern_hashcode_valid = false;
                return false;
            }
        }

        return (getHashCode() != base_pattern_hashcode);
    }

    // Here `config` can be regular (like `pattern=3`) or not (like `3`)
    public static JMLPattern fromBasePattern(String notation, String config)
                throws JuggleExceptionUser, JuggleExceptionInternal {
        Pattern p = Pattern.newPattern(notation).fromString(config);

        JMLPattern pat = p.asJMLPattern();

        // regularize the notation name and config string
        pat.base_pattern_notation = p.getNotationName();
        pat.base_pattern_config = p.toString();

        return pat;
    }

    //-------------------------------------------------------------------------
    // Some pattern transformations
    //-------------------------------------------------------------------------

    // Multiply all times in the pattern by a common factor `scale`.
    public void scaleTime(double scale) {
        JMLEvent ev = getEventList();
        while (ev != null) {
            if (ev.isMaster())
                ev.setT(ev.getT() * scale);
            ev = ev.getNext();
        }
        JMLPosition pos = getPositionList();
        while (pos != null) {
            pos.setT(pos.getT() * scale);
            pos = pos.getNext();
        }

        for (int i = 0; i < getNumberOfSymmetries(); i++) {
            JMLSymmetry sym = getSymmetry(i);
            double delay = sym.getDelay();
            if (delay > 0)
                sym.setDelay(delay * scale);
        }

        setNeedsLayout();
    }

    // Rescale the pattern in time to ensure that all throws are allotted
    // more time than their minimum required.
    //
    // `multiplier` should typically be a little over 1
    public double scaleTimeToFitThrows(double multiplier) throws
                            JuggleExceptionUser, JuggleExceptionInternal {
        layoutPattern();  // to ensure we have PathLinks
        double scale_factor = 1;

        for (int path = 1; path <= getNumberOfPaths(); path++) {
            for (PathLink pl : getPathLinks().get(path - 1)) {
                Path p = pl.getPath();
                if (p != null) {
                    double d = p.getDuration();
                    double dmin = p.getMinDuration();

                    if (d < dmin && d > 0)
                        scale_factor = Math.max(scale_factor, dmin / d);
                }
            }
        }

        if (scale_factor > 1) {
            scale_factor *= multiplier;  // so things aren't just barely feasible
            scaleTime(scale_factor);
        }

        return scale_factor;
    }

    // Flip the x-axis in the local coordinates of each juggler.
    public void invertXAxis() {
        JMLEvent ev = getEventList();
        while (ev != null) {
            int hand = ev.getHand();
            Coordinate c = ev.getLocalCoordinate();

            // flip hand assignment, invert x coordinate
            if (hand == HandLink.LEFT_HAND)
                hand = HandLink.RIGHT_HAND;
            else
                hand = HandLink.LEFT_HAND;

            c.x = -c.x;

            ev.setHand(ev.getJuggler(), hand);
            ev.setLocalCoordinate(c);
            ev = ev.getNext();
        }

        setNeedsLayout();
    }

    // Flip the time axis to create (as nearly as possible) what the pattern
    // looks like played in reverse.
    public void invertTime() throws JuggleExceptionInternal {
        try {
            layoutPattern();  // to ensure we have PathLinks

            // For each JMLEvent:
            //     - set t = looptime - t
            //     - reverse the doubly-linked event list
            double looptime = getLoopEndTime();

            JMLEvent ev = getEventList();
            while (ev != null) {
                ev.setT(looptime - ev.getT());

                JMLEvent prev = ev.getPrevious();
                JMLEvent next = ev.getNext();
                ev.setPrevious(next);
                ev.setNext(prev);

                if (next == null)
                    eventlist = ev;  // new list head

                ev = next;
            }

            // For each JMLPosition:
            //     - set t = looptime - t
            //     - sort the position list in time
            JMLPosition pos = getPositionList();
            positionlist = null;
            while (pos != null) {
                // no notion analagous to master events, so have to keep
                // position time within [0, looptime).
                if (pos.getT() != 0)
                    pos.setT(looptime - pos.getT());

                JMLPosition next = pos.getNext();
                addPosition(pos);

                pos = next;
            }

            // For each symmetry (besides type SWITCH):
            //     - invert pperm
            for (int i = 0; i < getNumberOfSymmetries(); ++i) {
                JMLSymmetry sym = getSymmetry(i);

                if (sym.getType() == JMLSymmetry.TYPE_SWITCH)
                    continue;

                Permutation newpathperm = sym.getPathPerm().getInverse();
                sym.setPathPerm(sym.getNumberOfPaths(), newpathperm.toString());
            }

            // For each PathLink:
            //     - find corresponding throw-type JMLTransition in startevent
            //     - find corresponding catch-type JMLTransition in endevent
            //     - swap {type, throw type, throw mod} for the two transitions
            for (int path = 1; path <= getNumberOfPaths(); ++path) {
                for (PathLink pl : getPathLinks().get(path - 1)) {
                    if (pl.isInHand())
                        continue;

                    JMLEvent start = pl.getStartEvent();
                    JMLEvent end = pl.getEndEvent();

                    JMLTransition start_tr = null;
                    for (int i = 0; i < start.getNumberOfTransitions(); ++i) {
                        if (start.getTransition(i).getPath() == path) {
                            start_tr = start.getTransition(i);
                            break;
                        }
                    }

                    JMLTransition end_tr = null;
                    for (int i = 0; i < end.getNumberOfTransitions(); ++i) {
                        if (end.getTransition(i).getPath() == path) {
                            end_tr = end.getTransition(i);
                            break;
                        }
                    }

                    if (start_tr == null || end_tr == null)
                        throw new JuggleExceptionInternal("invertTime() error 1");
                    if (start_tr.getOutgoingPathLink() != pl)
                        throw new JuggleExceptionInternal("invertTime() error 2");
                    if (end_tr.getIncomingPathLink() != pl)
                        throw new JuggleExceptionInternal("invertTime() error 3");

                    int start_tr_type = start_tr.getType();
                    String start_tr_throw_type = start_tr.getThrowType();
                    String start_tr_throw_mod = start_tr.getMod();

                    start_tr.setType(end_tr.getType());
                    start_tr.setThrowType(end_tr.getThrowType());
                    start_tr.setMod(end_tr.getMod());
                    end_tr.setType(start_tr_type);
                    end_tr.setThrowType(start_tr_throw_type);
                    end_tr.setMod(start_tr_throw_mod);

                    // don't need to do surgery on PathLinks or Paths since those
                    // will be recalculated during pattern layout
                }
            }
        } catch (JuggleExceptionUser jeu) {
            // No user errors here because the pattern has already been animated
            throw new JuggleExceptionInternal("invertTime() error 4: " + jeu.getMessage());
        } finally {
            setNeedsLayout();
        }
    }

    // Streamline the pattern to remove excess empty and holding events.
    //
    // Scan forward in time through the pattern and remove any event for which
    // all of the following are true:
    //
    // (a) event is empty or contains only <holding> transitions
    // (b) event has a different master event than the previous (surviving)
    //     event for that hand
    // (c) event is within `twindow` seconds of the previous (surviving) event
    //     for that hand
    // (d) event is not immediately adjacent to a throw or catch event for that
    //     hand that involves a pass to/from a different juggler
    public void streamlinePatternWithWindow(double twindow) throws
                                    JuggleExceptionUser, JuggleExceptionInternal {
        layoutPattern();  // to ensure we have PathLinks

        int n_events = 0;  // for reporting stats
        int n_holds = 0;
        int n_removed = 0;

        JMLEvent ev = getEventList();

        while (ev != null) {
            JMLEvent prev = ev.getPreviousForHand();
            JMLEvent next = ev.getNextForHand();

            boolean holding_only = true;
            for (int i = 0; i < ev.getNumberOfTransitions(); ++i) {
                if (ev.getTransition(i).getType() != JMLTransition.TRANS_HOLDING)
                    holding_only = false;
            }
            boolean different_masters = (prev == null || !ev.isSameMasterAs(prev));
            boolean inside_window = (prev != null && (ev.getT() - prev.getT()) < twindow);
            boolean not_pass_adjacent = (prev != null && next != null &&
                        !prev.hasPassingTransition() && !next.hasPassingTransition());

            boolean remove = holding_only && different_masters &&
                             inside_window && not_pass_adjacent;

            if (remove) {
                removeEvent(ev);
                n_removed++;
            }

            n_events++;
            if (holding_only)
                n_holds++;

            ev = ev.getNext();
        }

        if (Constants.DEBUG_LAYOUT) {
            System.out.println("Streamlined with time window " + twindow + " secs:");
            System.out.println("    Removed " + n_removed + " of " + n_holds +
                               " holding events (" + n_events + " events total)");
        }
    }

    //-------------------------------------------------------------------------
    // Lay out the spatial paths in the pattern
    //
    // Note that this can change the pattern's toString() representation,
    // and therefore its hash code.
    //-------------------------------------------------------------------------

    public JMLPattern layoutPattern() throws JuggleExceptionInternal, JuggleExceptionUser {
        if (laidout)
            return this;

        if (!valid)
            throw new JuggleExceptionInternal("Cannot do layout of invalid pattern");

        try {
            if (getNumberOfProps() == 0 && getNumberOfPaths() > 0)
                addProp(new PropDef("ball", null));
            for (int i = 0; i < getNumberOfProps(); i++)
                props.get(i).layoutProp();

            buildEventList();
            findMasterEvents();
            findPositions();
            gotoGlobalCoordinates();
            buildLinkLists();
            layoutHandPaths();

            if (Constants.DEBUG_LAYOUT) {
                for (int i = 0; i < getNumberOfPaths(); i++) {
                    System.out.println(pathlinks.get(i).size()+" pathlinks for path "+(i+1)+":");
                    for (int jtemp = 0; jtemp < pathlinks.get(i).size(); jtemp++)
                        System.out.println("   "+pathlinks.get(i).get(jtemp));
                }
                for (int i = 0; i < getNumberOfJugglers(); i++) {
                    for (int j = 0; j < 2; j++) {
                        System.out.println(handlinks.get(i).get(j).size()+" handlinks for juggler "+(i+1)+
                                           ", hand "+(j+1)+":");
                        for (int k = 0; k < handlinks.get(i).get(j).size(); k++)
                            System.out.println("   "+handlinks.get(i).get(j).get(k));
                    }
                }
            }
            laidout = true;
        } catch (JuggleExceptionUser jeu) {
            valid = false;
            throw jeu;
        } catch (JuggleExceptionInternal jei) {
            valid = false;
            throw jei;
        }

        return this;
    }

    public void setNeedsLayout() {
        laidout = false;
    }

    public boolean isValid() {
        return valid;
    }

    //-------------------------------------------------------------------------
    // Step 1: construct the list of events
    // Extend events in list using known symmetries
    //-------------------------------------------------------------------------

    public void buildEventList() throws JuggleExceptionInternal, JuggleExceptionUser {
        // figure out how many events there are
        int numevents = 0;
        JMLEvent current = eventlist;
        while (current != null) {
            if ((current.getJuggler() < 1) || (current.getJuggler() > numjugglers))
                throw new JuggleExceptionUser(errorstrings.getString("Error_juggler_outofrange"));
            if (current.isMaster())
                numevents++;
            else
                removeEvent(current);
            current = current.getNext();
        }
        // construct event images for extending event list
        EventImages[] ei = new EventImages[numevents];
        current = eventlist;
        for (int i = 0; i < numevents; i++) {
            ei[i] = new EventImages(this, current);
            current = current.getNext();
        }

        // arrays used for creating the event list
        boolean[][] needHandEvent = new boolean[numjugglers][2];
        boolean[][] needVDHandEvent = new boolean[numjugglers][2];
        boolean[] needPathEvent = new boolean[numpaths];
        boolean[] needSpecialPathEvent = new boolean[numpaths];
        hasVDHandJMLTransition = new boolean[numjugglers][2];
        hasVDPathJMLTransition = new boolean[numpaths];

        // make sure each hand and path are hit at least once
        for (int i = 0; i < numjugglers; i++) {
            boolean hasJMLTransitionForLeft = false;
            boolean hasJMLTransitionForRight = false;
            hasVDHandJMLTransition[i][0] = hasVDHandJMLTransition[i][1] = false;

            for (int j = 0; j < numevents; j++) {
                if (hasJMLTransitionForLeft == false)
                    hasJMLTransitionForLeft = ei[j].hasJMLTransitionForHand(i+1, HandLink.LEFT_HAND);
                if (hasJMLTransitionForRight == false)
                    hasJMLTransitionForRight = ei[j].hasJMLTransitionForHand(i+1, HandLink.RIGHT_HAND);
                if (hasVDHandJMLTransition[i][0] == false)
                    hasVDHandJMLTransition[i][0] = ei[j].hasVDJMLTransitionForHand(i+1, HandLink.LEFT_HAND);
                if (hasVDHandJMLTransition[i][1] == false)
                    hasVDHandJMLTransition[i][1] = ei[j].hasVDJMLTransitionForHand(i+1, HandLink.RIGHT_HAND);
            }
            if (hasJMLTransitionForLeft == false) {
                String template = errorstrings.getString("Error_no_left_events");
                Object[] arguments = { Integer.valueOf(i+1) };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
            if (hasJMLTransitionForRight == false) {
                String template = errorstrings.getString("Error_no_right_events");
                Object[] arguments = { Integer.valueOf(i+1) };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
            needVDHandEvent[i][0] = hasVDHandJMLTransition[i][0];   // set up for later
            needVDHandEvent[i][1] = hasVDHandJMLTransition[i][1];
            needHandEvent[i][0] = needHandEvent[i][1] = true;
        }
        for (int i = 0; i < numpaths; i++) {
            boolean hasPathJMLTransition = false;
            hasVDPathJMLTransition[i] = false;

            for (int j = 0; j < numevents; j++) {
                if (hasPathJMLTransition == false)
                    hasPathJMLTransition = ei[j].hasJMLTransitionForPath(i+1);
                if (hasVDPathJMLTransition[i] == false)
                    hasVDPathJMLTransition[i] = ei[j].hasVDJMLTransitionForPath(i+1);
            }
            if (hasPathJMLTransition == false) {
                String template = errorstrings.getString("Error_no_path_events");
                Object[] arguments = { Integer.valueOf(i+1) };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
            needPathEvent[i] = true;            // set up for later
            needSpecialPathEvent[i] = false;
        }

        // queue used to store events while building event list
        JMLEvent[] eventqueue = new JMLEvent[numevents];
        for (int i = 0; i < numevents; i++)
            eventqueue[i] = ei[i].getPrevious();    // seed the queue

        // start by extending each master event backward in time
        boolean contin = false;
        do {
            // find latest event in queue
            JMLEvent maxevent = eventqueue[0];
            double maxtime = maxevent.getT();
            int maxnum = 0;
            for (int i = 1; i < numevents; i++) {
                if (eventqueue[i].getT() > maxtime) {
                    maxevent = eventqueue[i];
                    maxtime = maxevent.getT();
                    maxnum = i;
                }
            }

            addEvent(maxevent);
            eventqueue[maxnum] = ei[maxnum].getPrevious();  // restock queue

            // now update the needs arrays, so we know when to stop
            if (maxtime < getLoopStartTime()) {
                int jug = maxevent.getJuggler() - 1;
                int han = HandLink.index(maxevent.getHand());

                if (hasVDHandJMLTransition[jug][han] == false)
                    needHandEvent[jug][han] = false;

                for (int i = 0; i < maxevent.getNumberOfTransitions(); i++) {
                    JMLTransition tr = maxevent.getTransition(i);
                    int path = tr.getPath() - 1;

                    switch (tr.getType()) {
                        case JMLTransition.TRANS_THROW:
                            needPathEvent[path] = false;
                            needVDHandEvent[jug][han] = needHandEvent[jug][han] = false;
                            needSpecialPathEvent[path] = false;
                            break;
                        case JMLTransition.TRANS_CATCH:
                        case JMLTransition.TRANS_GRABCATCH:
                            break;
                        case JMLTransition.TRANS_SOFTCATCH:
                            if (needVDHandEvent[jug][han] == true)
                                needSpecialPathEvent[path] = true;  // need corresponding throw to get velocity
                            needVDHandEvent[jug][han] = needHandEvent[jug][han] = false;
                            break;
                        case JMLTransition.TRANS_HOLDING:
                            if (hasVDPathJMLTransition[path] == false)  // if no throws for this path, then done
                                needPathEvent[path] = false;
                            break;
                        default:
                            throw new JuggleExceptionInternal("Unrecognized transition type in buildEventList()");
                    }
                }
            }
            // do we need to continue adding earlier events?
            contin = false;
            for (int i = 0; i < numjugglers; i++) {
                contin |= needHandEvent[i][0];
                contin |= needHandEvent[i][1];
                contin |= needVDHandEvent[i][0];
                contin |= needVDHandEvent[i][1];
            }
            for (int i = 0; i < numpaths; i++) {
                contin |= needPathEvent[i];
                contin |= needSpecialPathEvent[i];
            }
        } while (contin);

        // reset things to go forward in time
        for (int i = 0; i < numjugglers; i++) {
            needVDHandEvent[i][0] = hasVDHandJMLTransition[i][0];
            needVDHandEvent[i][1] = hasVDHandJMLTransition[i][1];
            needHandEvent[i][0] = needHandEvent[i][1] = true;
        }
        for (int i = 0; i < numpaths; i++) {
            needPathEvent[i] = true;
            needSpecialPathEvent[i] = false;
        }
        for (int i = 0; i < numevents; i++) {
            ei[i].resetPosition();
            eventqueue[i] = ei[i].getNext();
        }

        do {
            // find earliest event in queue
            JMLEvent minevent = eventqueue[0];
            double mintime = minevent.getT();
            int minnum = 0;
            for (int i = 1; i < numevents; i++) {
                if (eventqueue[i].getT() < mintime) {
                    minevent = eventqueue[i];
                    mintime = minevent.getT();
                    minnum = i;
                }
            }

            addEvent(minevent);
            eventqueue[minnum] = ei[minnum].getNext();  // restock queue

            // now update the needs arrays, so we know when to stop
            if (mintime > getLoopEndTime()) {
                int jug = minevent.getJuggler() - 1;
                int han = HandLink.index(minevent.getHand());

                // if this hand has no throws/catches, then need to build out event list
                // past a certain time, due to how the hand layout is done in this case
                // (see layoutHandPaths() below)
                if ((hasVDHandJMLTransition[jug][han] == false) &&
                            (mintime > (2 * getLoopEndTime() - getLoopStartTime())))
                    needHandEvent[jug][han] = false;

                for (int i = 0; i < minevent.getNumberOfTransitions(); i++) {
                    JMLTransition tr = minevent.getTransition(i);
                    int path = tr.getPath() - 1;

                    switch (tr.getType()) {
                        case JMLTransition.TRANS_THROW:
                            needPathEvent[path] = false;
                            if (needVDHandEvent[jug][han] == true)
                                // need corresponding catch to get velocity
                                needSpecialPathEvent[path] = true;
                                needVDHandEvent[jug][han] = needHandEvent[jug][han] = false;
                            break;
                        case JMLTransition.TRANS_CATCH:
                        case JMLTransition.TRANS_GRABCATCH:
                            needPathEvent[path] = false;
                            needSpecialPathEvent[path] = false;
                            break;
                        case JMLTransition.TRANS_SOFTCATCH:
                            needPathEvent[path] = false;
                            needVDHandEvent[jug][han] = needHandEvent[jug][han] = false;
                            needSpecialPathEvent[path] = false;
                            break;
                        case JMLTransition.TRANS_HOLDING:
                            if (hasVDPathJMLTransition[path] == false)      // if no throws for this path,
                                needPathEvent[path] = false;            // then done
                            break;
                        default:
                            throw new JuggleExceptionInternal("Unrecognized transition type in buildEventList()");
                    }
                }
            }
            // do we need to continue adding later events?
            contin = false;
            for (int i = 0; i < numjugglers; i++) {
                contin |= needHandEvent[i][0];
                contin |= needHandEvent[i][1];
                contin |= needVDHandEvent[i][0];
                contin |= needVDHandEvent[i][1];
            }
            for (int i = 0; i < numpaths; i++) {
                contin |= needPathEvent[i];
                contin |= needSpecialPathEvent[i];
            }
        } while (contin);
    }

    //-------------------------------------------------------------------------
    // Step 2: figure out which events should be considered master events
    //-------------------------------------------------------------------------

    public void findMasterEvents() throws JuggleExceptionInternal, JuggleExceptionUser {
        boolean rebuildList = false;
        JMLEvent ev = eventlist;

        while (ev != null) {
            if (ev.isMaster()) {
                JMLEvent newmaster = ev;
                double tmaster = getLoopEndTime();
                if (ev.getT() >= getLoopStartTime() && ev.getT() < tmaster)
                    tmaster = ev.getT();

                JMLEvent ev2 = eventlist;
                while (ev2 != null) {
                    if (ev2.getMaster() == ev) {
                        if (ev2.getT() >= getLoopStartTime() && ev2.getT() < tmaster) {
                            newmaster = ev2;
                            tmaster = ev2.getT();
                        }
                    }
                    ev2 = ev2.getNext();
                }

                if (newmaster != ev) {
                    rebuildList = true;
                    ev2 = eventlist;
                    while (ev2 != null) {
                        if (ev2.getMaster() == ev)
                            ev2.setMaster(newmaster);
                        ev2 = ev2.getNext();
                    }
                    newmaster.setMaster(null);
                    ev.setMaster(newmaster);
                }
            }
            ev = ev.getNext();
        }

        if (rebuildList)
            buildEventList();
    }

    //-------------------------------------------------------------------------
    // Step 3: find positions/angles for all jugglers at all points in time,
    // using <position> tags. This is done by finding spline functions passing
    // through the specified locations and angles.
    //-------------------------------------------------------------------------

    public void findPositions() throws JuggleExceptionInternal {
        jugglercurve = new SplineCurve[getNumberOfJugglers()];
        jugglerangle = ( (Constants.ANGLE_LAYOUT_METHOD == Curve.CURVE_LINE) ?
                              (Curve[])(new LineCurve[getNumberOfJugglers()]) :
                              (Curve[])(new SplineCurve[getNumberOfJugglers()]) );

        for (int i = 1; i <= getNumberOfJugglers(); i++) {
            int num = 0;
            JMLPosition current = positionlist;

            while (current != null) {
                if (current.getJuggler() == i)
                    num++;
                current = current.getNext();
            }

            if (num == 0) {
                jugglercurve[i - 1] = new SplineCurve();
                jugglerangle[i - 1] = ( (Constants.ANGLE_LAYOUT_METHOD == Curve.CURVE_LINE) ?
                                        (Curve)(new LineCurve()) : (Curve)(new SplineCurve()) );
                double[] times = new double[2];
                times[0] = getLoopStartTime();
                times[1] = getLoopEndTime();
                Coordinate[] positions = new Coordinate[2];
                Coordinate[] angles = new Coordinate[2];
                positions[0] = new Coordinate();
                angles[0] = new Coordinate();

                // default juggler body positions
                if (getNumberOfJugglers() == 1) {
                    positions[0].setCoordinate(0, 0, 100);
                    angles[0].setCoordinate(0, 0, 0);
                } else {
                    double r = 70;
                    double theta = 360 / (double)getNumberOfJugglers();
                    if (r * Math.sin(Math.toRadians(0.5 * theta)) < 65)
                        r = 65 / Math.sin(Math.toRadians(0.5 * theta));
                    positions[0].setCoordinate(r*Math.cos(Math.toRadians(theta*(double)(i-1))),
                                               r*Math.sin(Math.toRadians(theta*(double)(i-1))), 100);
                    angles[0].setCoordinate(90 + theta*(double)(i-1), 0, 0);
                }

                positions[1] = positions[0];
                angles[1] = angles[0];
                jugglercurve[i - 1].setCurve(times, positions, new Coordinate[2]);
                jugglercurve[i - 1].calcCurve();
                jugglerangle[i - 1].setCurve(times, angles, new Coordinate[2]);
                jugglerangle[i - 1].calcCurve();
            } else {
                jugglercurve[i - 1] = new SplineCurve();
                jugglerangle[i - 1] = ( (Constants.ANGLE_LAYOUT_METHOD == Curve.CURVE_LINE) ?
                                        (Curve)(new LineCurve()) : (Curve)(new SplineCurve()) );
                double[] times = new double[num + 1];
                Coordinate[] positions = new Coordinate[num + 1];
                Coordinate[] angles = new Coordinate[num + 1];

                current = positionlist;
                int j = 0;

                while (current != null) {
                    if (current.getJuggler() == i) {
                        times[j] = current.getT();
                        positions[j] = current.getCoordinate();
                        angles[j] = new Coordinate(current.getAngle(), 0, 0);
                        ++j;
                    }
                    current = current.getNext();
                }
                times[num] = times[0] + getLoopEndTime() - getLoopStartTime();
                positions[num] = positions[0];
                angles[num] = new Coordinate(angles[0]);

                for (j = 1; j <= num; j++) {
                    while ((angles[j].x - angles[j-1].x) > 180)
                        angles[j].x -= 360;
                    while ((angles[j].x - angles[j-1].x) < -180)
                        angles[j].x += 360;
                }

                jugglercurve[i - 1].setCurve(times, positions, new Coordinate[num + 1]);
                jugglercurve[i - 1].calcCurve();
                jugglerangle[i - 1].setCurve(times, angles, new Coordinate[num + 1]);
                jugglerangle[i - 1].calcCurve();
            }
        }
    }

    //-------------------------------------------------------------------------
    // Step 4: transform event coordinates from local to global reference frame
    //-------------------------------------------------------------------------

    public void gotoGlobalCoordinates() {
        JMLEvent ev = eventlist;

        while (ev != null) {
            Coordinate lc = ev.getLocalCoordinate();
            int juggler = ev.getJuggler();
            double t = ev.getT();

            ev.setGlobalCoordinate(convertLocalToGlobal(lc, juggler, t));
            ev = ev.getNext();
        }
    }

    //-------------------------------------------------------------------------
    // Step 5: construct the links connecting events; build PathLink and
    // HandLink lists
    //-------------------------------------------------------------------------

    protected void buildLinkLists() throws JuggleExceptionUser, JuggleExceptionInternal {
        int i, j, k;

        pathlinks = new ArrayList<ArrayList<PathLink>>(getNumberOfPaths());

        for (i = 0; i < getNumberOfPaths(); i++) {
            // build the PathLink list for the ith path
            pathlinks.add(new ArrayList<PathLink>());
            JMLEvent ev = eventlist;
            JMLEvent lastev = null;
            JMLTransition lasttr = null;

            done1:
            while (true) {
                // find the next transition for this path
                JMLTransition tr = null;
                while (true) {
                    tr = ev.getPathTransition(i + 1, JMLTransition.TRANS_ANY);
                    if (tr != null)
                        break;
                    ev = ev.getNext();
                    if (ev == null)
                        break done1;
                }

                if (lastev != null) {
                    PathLink pl = new PathLink(i + 1, lastev, ev);

                    switch (tr.getType()) {
                        case JMLTransition.TRANS_THROW:
                        case JMLTransition.TRANS_HOLDING:
                            if (lasttr.getType() == JMLTransition.TRANS_THROW) {
                                String template = errorstrings.getString("Error_successive_throws");
                                Object[] arguments = { Integer.valueOf(i+1) };
                                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                            }
                            if (lastev.getJuggler() != ev.getJuggler()) {
                                String template = errorstrings.getString("Error_juggler_changed");
                                Object[] arguments = { Integer.valueOf(i+1) };
                                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                            }
                            if (lastev.getHand() != ev.getHand()) {
                                String template = errorstrings.getString("Error_hand_changed");
                                Object[] arguments = { Integer.valueOf(i+1) };
                                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                            }
                            pl.setInHand(ev.getJuggler(), ev.getHand());
                            break;
                        case JMLTransition.TRANS_CATCH:
                        case JMLTransition.TRANS_SOFTCATCH:
                        case JMLTransition.TRANS_GRABCATCH:
                            if (lasttr.getType() != JMLTransition.TRANS_THROW) {
                                String template = errorstrings.getString("Error_successive_catches");
                                Object[] arguments = { Integer.valueOf(i+1) };
                                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                            }
                            pl.setThrow(lasttr.getThrowType(), lasttr.getMod());
                            break;
                        default:
                            throw new JuggleExceptionInternal("unrecognized transition type in buildLinkLists()");
                    }

                    pathlinks.get(i).add(pl);
                    if (lasttr != null)
                        lasttr.setOutgoingPathLink(pl);
                    tr.setIncomingPathLink(pl);
                }

                lastev = ev;
                lasttr = tr;
                ev = ev.getNext();
                if (ev == null)
                    break done1;
            }

            if (pathlinks.get(i).size() == 0)
                throw new JuggleExceptionInternal("No event found for path "+(i+1));
        }

        // now build the HandLink lists
        handlinks = new ArrayList<ArrayList<ArrayList<HandLink>>>();

        for (i = 0; i < getNumberOfJugglers(); i++) {
            // build the HandLink list for the ith juggler

            handlinks.add(new ArrayList<ArrayList<HandLink>>());

            for (j = 0; j < 2; j++) {
                int handnum = (j == 0 ? HandLink.LEFT_HAND : HandLink.RIGHT_HAND);

                handlinks.get(i).add(new ArrayList<HandLink>());

                JMLEvent ev = eventlist;
                JMLEvent lastev = null;
                VelocityRef vr = null;
                VelocityRef lastvr = null;

                done2:
                while (true) {
                    // find the next event touching hand
                    while (true) {
                        if (ev.getJuggler() == (i+1) && ev.getHand() == handnum)
                            break;
                        ev = ev.getNext();
                        if (ev == null)
                            break done2;
                    }

                    // find velocity of hand path ending
                    vr = null;
                    if (ev.getJuggler() == (i+1) && ev.getHand() == handnum) {
                        for (k = 0; k < ev.getNumberOfTransitions(); k++) {
                            JMLTransition tr = ev.getTransition(k);
                            if (tr.getType() == JMLTransition.TRANS_THROW) {
                                PathLink pl = tr.getOutgoingPathLink();
                                if (pl != null)
                                    vr = new VelocityRef(pl.getPath(), VelocityRef.VR_THROW);
                            } else if (tr.getType() == JMLTransition.TRANS_SOFTCATCH) {
                                PathLink pl = tr.getIncomingPathLink();
                                if (pl != null)
                                    vr = new VelocityRef(pl.getPath(), VelocityRef.VR_SOFTCATCH);
                            } else if (tr.getType() == JMLTransition.TRANS_CATCH) {
                                PathLink pl = tr.getIncomingPathLink();
                                if (pl != null)
                                    vr = new VelocityRef(pl.getPath(), VelocityRef.VR_CATCH);
                            }
                            // can skip adding VelocityRef for GRABCATCH because it's
                            // never used by hand layout
                        }
                    }

                    if (lastev != null) {
                        // add HandLink from lastev to ev
                        HandLink hl = new HandLink(i, handnum, lastev, ev);
                        hl.setStartVelocityRef(lastvr); // may be null, which is ok
                        hl.setEndVelocityRef(vr);
                        handlinks.get(i).get(j).add(hl);
                    }
                    lastev = ev;
                    lastvr = vr;
                    ev = ev.getNext();
                    if (ev == null)
                        break done2;
                }
            }
        }
    }

    //-------------------------------------------------------------------------
    // Step 6: do a physical layout of the handlink paths
    // (Props were physically laid out in PathLink.setThrow() in Step 5 above)
    //-------------------------------------------------------------------------

    protected void layoutHandPaths() throws JuggleExceptionInternal {

        // go through HandLink lists, creating Path objects and calculating paths

        for (int j = 0; j < getNumberOfJugglers(); j++) {
            for (int h = 0; h < 2; h++) {
                // There are two cases: a hand has throw or softcatch events (which define
                // hand velocities at points in time), or it does not (no velocities known).
                // To determine the spline paths, we need to solve for hand velocity at each
                // of its events, but this is done differently in the two cases.

                if (hasVDHandJMLTransition[j][h]) {
                    HandLink startlink = null;
                    int num = 0;

                    for (int k = 0; k < handlinks.get(j).get(h).size(); k++) {
                        HandLink hl = handlinks.get(j).get(h).get(k);

                        VelocityRef vr = hl.getStartVelocityRef();
                        if (vr != null && (vr.getSource() == VelocityRef.VR_THROW ||
                                           vr.getSource() == VelocityRef.VR_SOFTCATCH)) {
                            // this is guaranteed to happen before the loop start time,
                            // given the way we built the event list above
                            startlink = hl;
                            num = 1;
                        }

                        vr = hl.getEndVelocityRef();
                        if (startlink != null && vr != null &&
                                    (vr.getSource() == VelocityRef.VR_THROW ||
                                    vr.getSource() == VelocityRef.VR_SOFTCATCH)) {
                            double[] times = new double[num + 1];
                            Coordinate[] pos = new Coordinate[num + 1];
                            Coordinate[] velocities = new Coordinate[num + 1];
                            Curve hp = new SplineCurve();

                            for (int l = 0; l < num; l++) {
                                HandLink hl2 = handlinks.get(j).get(h).get(k-num+1+l);
                                times[l] = hl2.getStartEvent().getT();
                                pos[l] = hl2.getStartEvent().getGlobalCoordinate();
                                VelocityRef vr2 = hl2.getStartVelocityRef();
                                if (l > 0 && vr2 != null && vr2.getSource() == VelocityRef.VR_CATCH)
                                    velocities[l] = vr2.getVelocity();
                                hl2.setHandCurve(hp);
                            }
                            times[num] = hl.getEndEvent().getT();
                            pos[num] = hl.getEndEvent().getGlobalCoordinate();
                            velocities[0] = startlink.getStartVelocityRef().getVelocity();
                            velocities[num] = hl.getEndVelocityRef().getVelocity();

                            hp.setCurve(times, pos, velocities);
                            hp.calcCurve();
                            startlink = null;
                        }
                        ++num;
                    }
                } else {
                    // Build chain and solve for velocities. This implementation is a little
                    // inefficient since it builds the second chain by a duplicate calculation rather
                    // than a copy. Sketch of algorithm:
                    //    find first handlink that straddles loopStartTime -- call it startlink
                    //    startevent = first event in startlink
                    //    delayedstartevent = corresponding event 1 delay period after startevent
                    //    find handlink that ends with delayedstartevent -- call it endlink
                    //    build spline hand path from startlink to endlink, and calculate (chain 1)
                    //    startlink = next link after endlink
                    //    delayed2startevent = corresponding event 1 delay period after delayedstartevent
                    //    find handlink that ends with delayed2startevent -- call it endlink
                    //    build spline hand path from startlink to endlink, and calculate (chain 2)
                    int k;
                    HandLink hl = null;
                    for (k = 0; k < handlinks.get(j).get(h).size(); ++k) {
                        hl = handlinks.get(j).get(h).get(k);
                        if (hl.getEndEvent().getT() > getLoopStartTime())
                            break;
                    }

                    for (int chain = 0; chain < 2; ++chain) {
                        HandLink startlink = hl;
                        JMLEvent startevent = startlink.getStartEvent();
                        int num = 1;    // number of links in chain
                        while (hl.getEndEvent().isDelayOf(startevent) == false) {
                            hl = handlinks.get(j).get(h).get(++k);
                            ++num;
                        }
                        double[] times = new double[num + 1];
                        Coordinate[] pos = new Coordinate[num + 1];
                        Curve hp = new SplineCurve();

                        for (int l = 0; l < num; ++l) {
                            HandLink hl2 = handlinks.get(j).get(h).get(k - num + 1 + l);
                            pos[l] = hl2.getStartEvent().getGlobalCoordinate();
                            times[l] = hl2.getStartEvent().getT();
                            hl2.setHandCurve(hp);
                        }
                        pos[num] = hl.getEndEvent().getGlobalCoordinate();
                        times[num] = hl.getEndEvent().getT();
                        // all velocities are null (unknown) -> signal to calculate
                        hp.setCurve(times, pos, new Coordinate[num + 1]);
                        hp.calcCurve();

                        if (chain == 0)
                            hl = handlinks.get(j).get(h).get(++k);
                    }
                }
            }
        }
    }

    //-------------------------------------------------------------------------
    // Methods used by the animator to animate the pattern
    //-------------------------------------------------------------------------

    public String getTitle() {
        return title;
    }

    public String getInfo() {
        return info;
    }

    public ArrayList<String> getTags() {
        return tags;
    }

    public boolean isTaggedWith(String tag) {
        if (tag == null)
            return false;

        for (String t : tags) {
            if (t.equalsIgnoreCase(tag))
                return true;
        }
        return false;
    }

    public int getNumberOfJugglers() {
        return numjugglers;
    }

    public int getNumberOfPaths() {
        return numpaths;
    }

    public int getNumberOfProps() {
        return props.size();
    }

    public Prop getProp(int propnum) {
        return getPropDef(propnum).getProp();
    }

    public PropDef getPropDef(int propnum) {
        return props.get(propnum - 1);
    }

    public int getPropAssignment(int path) {
        return propassignment[path - 1];
    }

    public int getNumberOfSymmetries() {
        return symmetries.size();
    }

    public JMLSymmetry getSymmetry(int index) {
        return symmetries.get(index);
    }

    public double getLoopStartTime() {
        return 0;
    }

    public double getLoopEndTime() {
        for (int i = 0; i < getNumberOfSymmetries(); i++)
            if (getSymmetry(i).getType() == JMLSymmetry.TYPE_DELAY)
                return getSymmetry(i).getDelay();
        return -1;
    }

    // returns path coordinate in global frame
    public void getPathCoordinate(int path, double time, Coordinate newPosition)
                            throws JuggleExceptionInternal {
        for (PathLink pl : pathlinks.get(path - 1)) {
            if (time >= pl.getStartEvent().getT() && time <= pl.getEndEvent().getT()) {
                if (pl.isInHand()) {
                    int jug = pl.getHoldingJuggler();
                    int hand = pl.getHoldingHand();
                    getHandCoordinate(jug, hand, time, newPosition);
                    return;
                } else {
                    pl.getPath().getCoordinate(time, newPosition);
                    return;
                }
            }
        }
        throw new JuggleExceptionInternal("time t=" + time + " is out of path range");
    }

    // returns true if a given hand is holding the path at a given time
    public boolean isHandHoldingPath(int juggler, int hand, double time, int path) {
        for (PathLink pl : pathlinks.get(path - 1)) {
            if (!pl.isInHand())
                continue;
            if (pl.getHoldingJuggler() != juggler)
                continue;
            if (pl.getHoldingHand() != hand)
                continue;
            if (time >= pl.getStartEvent().getT() && time <= pl.getEndEvent().getT())
                return true;
        }
        return false;
    }

    // returns orientation of prop on given path, in global frame
    // result is {pitch, yaw, roll}
    public double getPathOrientation(int path, double time, Coordinate axis) {
        axis.x = 0;       // components of unit vector to rotate around
        axis.y = 0;
        axis.z = 1;
        return (3 * time);
    }

    // returns juggler coordinate in global frame
    public void getJugglerPosition(int juggler, double time, Coordinate newPosition) {
        Curve p = jugglercurve[juggler - 1];

        while (time < p.getStartTime())
            time += (getLoopEndTime() - getLoopStartTime());
        while (time > p.getEndTime())
            time -= (getLoopEndTime() - getLoopStartTime());

        p.getCoordinate(time, newPosition);
    }

    // returns angle (in degrees) between local x axis and global x axis
    // (rotation around vertical z axis)
    public double getJugglerAngle(int juggler, double time) {
        Curve p = jugglerangle[juggler - 1];

        while (time < p.getStartTime())
            time += (getLoopEndTime() - getLoopStartTime());
        while (time > p.getEndTime())
            time -= (getLoopEndTime() - getLoopStartTime());

        Coordinate coord = new Coordinate();
        p.getCoordinate(time, coord);

        return coord.x;
    }

    // Convert from local juggler frame to global frame
    public Coordinate convertLocalToGlobal(Coordinate lc, int juggler, double time) {
        Coordinate origin = new Coordinate();
        getJugglerPosition(juggler, time, origin);
        double angle = Math.toRadians(getJugglerAngle(juggler, time));
        lc.y += Juggler.pattern_y;

        Coordinate gc = new Coordinate(origin.x + lc.x * Math.cos(angle) - lc.y * Math.sin(angle),
                                       origin.y + lc.x * Math.sin(angle) + lc.y * Math.cos(angle),
                                       origin.z + lc.z);
        return gc;
    }


    // Convert from global to local frame for a juggler
    public Coordinate convertGlobalToLocal(Coordinate gc, int juggler, double t) {
        Coordinate origin = new Coordinate();
        getJugglerPosition(juggler, t, origin);
        double angle = Math.toRadians(getJugglerAngle(juggler, t));
        Coordinate c2 = Coordinate.sub(gc, origin);

        Coordinate lc = new Coordinate(c2.x * Math.cos(angle) + c2.y * Math.sin(angle),
                                       -c2.x * Math.sin(angle) + c2.y * Math.cos(angle),
                                       c2.z);
        lc.y -= Juggler.pattern_y;
        return lc;
    }

    // returns hand coordinate in global frame
    public void getHandCoordinate(int juggler, int hand, double time, Coordinate newPosition)
                        throws JuggleExceptionInternal {
        int handindex = (hand == HandLink.LEFT_HAND) ? 0 : 1;

        for (HandLink hl : handlinks.get(juggler - 1).get(handindex)) {
            if (time >= hl.getStartEvent().getT() && time < hl.getEndEvent().getT()) {
                Curve hp = hl.getHandCurve();
                if (hp == null)
                    throw new JuggleExceptionInternal("getHandCoordinate() null pointer");
                hl.getHandCurve().getCoordinate(time, newPosition);
                return;
            }
        }
        throw new JuggleExceptionInternal("time t="+time+" (j="+juggler+",h="+handindex+") is out of handpath range");
    }

    // Get volume of any catch made between time1 and time2; if no catch, returns 0
    public double getPathCatchVolume(int path, double time1, double time2) {
        int i;
        PathLink pl1 = null, pl2 = null;
        boolean wasinair = false;
        boolean gotcatch = false;

        for (i = 0; i < pathlinks.get(path - 1).size(); i++) {
            pl1 = pathlinks.get(path - 1).get(i);
            if (time1 >= pl1.getStartEvent().getT() && time1 <= pl1.getEndEvent().getT())
                break;
        }
        if (i == pathlinks.get(path - 1).size())
            return 0;
        while (true) {
            pl2 = pathlinks.get(path - 1).get(i);
            if (!pl2.isInHand())
                wasinair = true;
            if (pl2.isInHand() && wasinair) {
                gotcatch = true;
                break;
            }
            if (time2 >= pl2.getStartEvent().getT() && time2 <= pl2.getEndEvent().getT())
                break;

            i++;
            if (i == pathlinks.get(path - 1).size())
                i = 0;
        }

        // We don't adjust the playback volume of the audio clip, so this is just
        // yes/no for now
        if (gotcatch)
            return 1;

        return 0;
    }

    // Get volume of any bounce between time1 and time2; if no catch, returns 0
    public double getPathBounceVolume(int path, double time1, double time2) {
        int i;
        PathLink pl = null;

        for (i = 0; i < pathlinks.get(path - 1).size(); i++) {
            pl = pathlinks.get(path - 1).get(i);
            if (time1 >= pl.getStartEvent().getT() && time1 <= pl.getEndEvent().getT())
                break;
        }
        if (i == pathlinks.get(path - 1).size())
            return 0;
        while (true) {
            pl = pathlinks.get(path - 1).get(i);
            Path p = pl.getPath();
            if (p instanceof BouncePath) {
                BouncePath bp = (BouncePath)p;
                double vol = bp.getBounceVolume(time1, time2);
                if (vol > 0)
                    return vol;
            }
            if (time2 >= pl.getStartEvent().getT() && time2 <= pl.getEndEvent().getT())
                break;

            i++;
            if (i == pathlinks.get(path - 1).size())
                i = 0;
        }

        return 0;
    }

    public Coordinate getPathMax(int path) {    // maximum of each coordinate
        Coordinate result = null;
        double t1 = getLoopStartTime();
        double t2 = getLoopEndTime();

        for (int i = 0; i < pathlinks.get(path - 1).size(); i++) {
            PathLink pl = pathlinks.get(path - 1).get(i);
            if (pl.isInHand())
                result = Coordinate.max(result,
                                        getHandMax(pl.getHoldingJuggler(),
                                                   pl.getHoldingHand()));
            else
                result = Coordinate.max(result,
                                        pl.getPath().getMax(t1, t2));
        }
        return result;
    }

    public Coordinate getPathMin(int path) {
        Coordinate result = null;
        double t1 = getLoopStartTime();
        double t2 = getLoopEndTime();

        for (int i = 0; i < pathlinks.get(path - 1).size(); i++) {
            PathLink pl = pathlinks.get(path - 1).get(i);
            if (pl.isInHand())
                result = Coordinate.min(result,
                                        getHandMin(pl.getHoldingJuggler(),
                                                   pl.getHoldingHand()));
            else
                result = Coordinate.min(result,
                                        pl.getPath().getMin(t1, t2));
        }
        return result;
    }

    public Coordinate getHandMax(int juggler, int hand) {
        Coordinate result = null;
        double t1 = getLoopStartTime();
        double t2 = getLoopEndTime();
        int handnum = (hand == HandLink.LEFT_HAND) ? 0 : 1;

        for (int i = 0; i < handlinks.get(juggler - 1).get(handnum).size(); i++) {
            HandLink hl = handlinks.get(juggler - 1).get(handnum).get(i);
            Curve hp = hl.getHandCurve();
            if (hp != null)
                result = Coordinate.max(result, hp.getMax(t1, t2));
        }
        return result;
    }

    public Coordinate getHandMin(int juggler, int hand) {
        Coordinate result = null;
        double t1 = getLoopStartTime();
        double t2 = getLoopEndTime();
        int handnum = (hand == HandLink.LEFT_HAND) ? 0 : 1;

        for (int i = 0; i < handlinks.get(juggler - 1).get(handnum).size(); i++) {
            HandLink hl = handlinks.get(juggler - 1).get(handnum).get(i);
            Curve hp = hl.getHandCurve();
            if (hp != null)
                result = Coordinate.min(result, hp.getMin(t1, t2));
        }
        return result;
    }

    public Coordinate getJugglerMax(int juggler) {
        return jugglercurve[juggler - 1].getMax();
    }

    public Coordinate getJugglerMin(int juggler) {
        return jugglercurve[juggler - 1].getMin();
    }

    public Permutation getPathPermutation() {
        for (int i = 0; i < getNumberOfSymmetries(); i++)
            if (getSymmetry(i).getType() == JMLSymmetry.TYPE_DELAY)
                return getSymmetry(i).getPathPerm();
        return null;
    }

    public int getPeriod() {
        return getPeriod(getPathPermutation(), propassignment);
    }

    public static int getPeriod(Permutation perm, int[] propassign) {
        int i, j, k, cperiod, period = 1;
        boolean matches;
        int size = perm.getSize();
        boolean[] notdone = new boolean[size];

        for (i = 0; i < size; i++)
            notdone[i] = true;
        for (i = 0; i < size; i++) {
            if (notdone[i]) {
                int[] cycle = perm.getCycle(i+1);
                for (j = 0; j < cycle.length; j++) {
                    notdone[cycle[j] - 1] = false;
                    cycle[j] = propassign[cycle[j] - 1];
                }
                // now find the period of the current cycle
                for (cperiod = 1; cperiod < cycle.length; cperiod++) {
                    if ((cycle.length % cperiod) == 0) {
                        matches = true;
                        for (k = 0; k < cycle.length; k++) {
                            if (cycle[k] != cycle[(k+cperiod)%cycle.length]) {
                                matches = false;
                                break;
                            }
                        }
                        if (matches) break;
                    }
                }

                period = Permutation.lcm(period, cperiod);
            }
        }
        return period;
    }

    public boolean isBouncePattern() {
        for (int path = 1; path <= getNumberOfPaths(); path++) {
            for (PathLink pl : pathlinks.get(path - 1)) {
                if (pl.getPath() instanceof BouncePath)
                    return true;
            }
        }
        return false;
    }

    //-------------------------------------------------------------------------
    // Reader/writer methods
    //-------------------------------------------------------------------------

    protected void readJML(JMLNode current) throws JuggleExceptionUser {
        // process current node, then treat subnodes recursively

        String type = current.getNodeType();

        if (type.equalsIgnoreCase("jml")) {
            String vers = current.getAttributes().getAttribute("version");
            if (vers != null) {
                if (JLFunc.compareVersions(vers, JMLDefs.CURRENT_JML_VERSION) > 0)
                    throw new JuggleExceptionUser(errorstrings.getString("Error_JML_version"));
                loadingversion = vers;
            }
        } else if (type.equalsIgnoreCase("pattern")) {
            // do nothing
        } else if (type.equalsIgnoreCase("title")) {
            setTitle(current.getNodeValue());
        } else if (type.equalsIgnoreCase("info")) {
            setInfo(current.getNodeValue());
            String tagstr = current.getAttributes().getAttribute("tags");
            if (tagstr != null) {
                for (String t : tagstr.split(","))
                    addTag(t.strip());
            }
        } else if (type.equalsIgnoreCase("basepattern")) {
            base_pattern_notation = Pattern.canonicalNotation(
                                current.getAttributes().getAttribute("notation"));
            base_pattern_config = current.getNodeValue().strip();
        } else if (type.equalsIgnoreCase("prop")) {
            PropDef pd = new PropDef();
            pd.readJML(current, loadingversion);
            addProp(pd);
        } else if (type.equalsIgnoreCase("setup")) {
            JMLAttributes at = current.getAttributes();
            String jugglerstring, pathstring, propstring;
            jugglerstring = at.getAttribute("jugglers");
            pathstring = at.getAttribute("paths");
            propstring = at.getAttribute("props");

            try {
                if (jugglerstring != null)
                    setNumberOfJugglers(Integer.valueOf(jugglerstring).intValue());
                else
                    setNumberOfJugglers(1);
                setNumberOfPaths(Integer.valueOf(pathstring).intValue());
            } catch (Exception ex) {
                throw new JuggleExceptionUser(errorstrings.getString("Error_setup_tag"));
            }

            int[] pa = new int[numpaths];
            if (propstring != null) {
                StringTokenizer st = new StringTokenizer(propstring, ",");
                if (st.countTokens() != numpaths)
                    throw new JuggleExceptionUser(errorstrings.getString("Error_prop_assignments"));
                try {
                    for (int i = 0; i < numpaths; i++) {
                        pa[i] = Integer.valueOf(st.nextToken()).intValue();
                        if (pa[i] < 1 || pa[i] > getNumberOfProps())
                            throw new JuggleExceptionUser(errorstrings.getString("Error_prop_number"));
                    }
                } catch (NumberFormatException nfe) {
                    throw new JuggleExceptionUser(errorstrings.getString("Error_prop_format"));
                }
            } else {
                for (int i = 0; i < numpaths; i++)
                    pa[i] = 1;
            }
            setPropAssignments(pa);
        } else if (type.equalsIgnoreCase("symmetry")) {
            JMLSymmetry sym = new JMLSymmetry();
            sym.readJML(current, numjugglers, numpaths, loadingversion);
            addSymmetry(sym);
        } else if (type.equalsIgnoreCase("event")) {
            JMLEvent ev = new JMLEvent();
            ev.readJML(current, loadingversion, getNumberOfJugglers(), getNumberOfPaths()); // look at subnodes
            addEvent(ev);
            return;  // stop recursion
        } else if (type.equalsIgnoreCase("position")) {
            JMLPosition pos = new JMLPosition();
            pos.readJML(current, loadingversion);
            addPosition(pos);
            return;
        } else {
            String template = errorstrings.getString("Error_unknown_tag");
            Object[] arguments = { type };
            throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
        }

        for (int i = 0; i < current.getNumberOfChildren(); i++)
            readJML(current.getChildNode(i));

        // Set title in base pattern, if any. Do this after reading the <basepattern>
        // tag so that we don't overwrite these changes.
        if (current.getNodeType().equalsIgnoreCase("jml"))
            setTitle(title);
    }

    public void writeJML(Writer wr, boolean write_title, boolean write_info) throws IOException {
        PrintWriter write = new PrintWriter(wr);

        for (int i = 0; i < JMLDefs.jmlprefix.length; i++)
            write.println(JMLDefs.jmlprefix[i]);
        write.println("<jml version=\"" + JMLNode.xmlescape(version) + "\">");
        write.println("<pattern>");
        if (write_title && title != null)
            write.println("<title>" + JMLNode.xmlescape(title) + "</title>");
        if (write_info && (info != null || tags.size() > 0)) {
            String tagstr = String.join(",", tags);

            if (info != null) {
                if (tagstr.length() == 0)
                    write.println("<info>" + JMLNode.xmlescape(info) + "</info>");
                else
                    write.println("<info tags=\"" + JMLNode.xmlescape(tagstr) + "\">" +
                                JMLNode.xmlescape(info) + "</info>");
            } else {
                write.println("<info tags=\"" + JMLNode.xmlescape(tagstr) + "\"/>");
            }
        }
        if (base_pattern_notation != null && base_pattern_config != null) {
            write.println("<basepattern notation=\"" +
                            JMLNode.xmlescape(base_pattern_notation.toLowerCase()) + "\">");
            write.println(JMLNode.xmlescape(base_pattern_config.replaceAll(";", ";\n")));
            write.println("</basepattern>");
        }
        for (int i = 0; i < props.size(); i++)
            props.get(i).writeJML(write);

        String out = "<setup jugglers=\"" + getNumberOfJugglers() + "\" paths=\""+
            getNumberOfPaths()+"\" props=\"";

        if (getNumberOfPaths() > 0) {
            out += getPropAssignment(1);
            for (int i = 2; i <= getNumberOfPaths(); i++)
                out += "," + getPropAssignment(i);
        }
        write.println(out + "\"/>");

        for (int i = 0; i < symmetries.size(); i++)
            symmetries.get(i).writeJML(write);

        JMLPosition pos = positionlist;
        while (pos != null) {
            pos.writeJML(write);
            pos = pos.getNext();
        }

        JMLEvent ev = eventlist;
        while (ev != null) {
            if (ev.isMaster())
                ev.writeJML(write);
            ev = ev.getNext();
        }
        write.println("</pattern>");

        write.println("</jml>");
        for (int i = 0; i < JMLDefs.jmlsuffix.length; i++)
            write.println(JMLDefs.jmlsuffix[i]);
        write.flush();
    }

    public JMLNode getRootNode() throws JuggleExceptionInternal {
        try {
            JMLParser parser = new JMLParser();
            parser.parse(new StringReader(toString()));
            return parser.getTree();
        } catch (SAXException se) {
            throw new JuggleExceptionInternal(se.getMessage());
        } catch (IOException ioe) {
            throw new JuggleExceptionInternal(ioe.getMessage());
        }
    }

    // java.lang.Object methods

    @Override
    public String toString() {
        StringWriter sw = new StringWriter();
        try {
            writeJML(sw, true, true);
        } catch (IOException ioe) {
        }

        return sw.toString();
    }
}
