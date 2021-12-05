// JMLPattern.java
//
// Copyright 2021 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.jml;

import java.io.*;
import java.text.MessageFormat;
import java.util.*;
import org.xml.sax.*;

import jugglinglab.core.*;
import jugglinglab.curve.*;
import jugglinglab.path.*;
import jugglinglab.prop.*;
import jugglinglab.renderer.*;
import jugglinglab.util.*;


/*
    This is one of the core classes, representing a juggling pattern in generalized
    form. It is used in three steps:

    1) Define a pattern, in one of three ways:

       A)  Manually, by calling methods in this class.
       B)  Parsing from pre-existing JML stream (file, user input, etc.).
           (JML = Juggling Markup Language, an XML document type)
       C)  Output from a Notation class, which converts from other notations.

    2) Call layoutPattern() to calculate flight paths for all the props and
       hands.

    3) Call various methods to get information about the pattern, e.g., prop/hand
       coordinates at points in time.
*/

public class JMLPattern {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    protected String version = JMLDefs.default_JML_on_save;  // JML version number
    protected String title;
    protected int numjugglers;
    protected int numpaths;
    protected ArrayList<PropDef> props;
    protected int[] propassignment;

    // whether pattern has a velocity-defining transition
    protected boolean[] hasVDPathJMLTransition;     // for a given path
    protected boolean[][] hasVDHandJMLTransition;   // for a given juggler/hand

    protected ArrayList<JMLSymmetry> symmetries;
    protected JMLEvent eventlist;
    protected JMLPosition positionlist;
    protected boolean laidout;
    protected boolean valid;

    // list of PathLink objects for each path
    protected ArrayList<ArrayList<PathLink>> pathlinks;

    // list of HandLink objects for each juggler/hand combination
    protected ArrayList<ArrayList<ArrayList<HandLink>>> handlinks;

    protected Curve[] jugglercurve;  // coordinates for each juggler
    protected Curve[] jugglerangle;  // angles for each juggler


    public JMLPattern() {
        laidout = false;
        valid = true;
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

    // ------------------------------------------------------------------------
    //   Methods to define the pattern
    // ------------------------------------------------------------------------

    public void setTitle(String t) {
        title = (t == null ? null : t.trim());
    }

    public void setNumberOfJugglers(int n) {
        numjugglers = n;
        setNeedsLayout(true);
    }

    public void setNumberOfPaths(int n) {
        numpaths = n;
        setNeedsLayout(true);
    }

    public void addProp(PropDef pd) {
        props.add(pd);
        setNeedsLayout(true);
    }
    public void removeProp(int propnum) {
        props.remove(propnum - 1);
        for (int i = 1; i <= getNumberOfPaths(); i++) {
            if (getPropAssignment(i) > propnum)
                setPropAssignment(i, getPropAssignment(i) - 1);
        }
        setNeedsLayout(true);
    }
    public void setPropAssignment(int pathnum, int propnum) {
        propassignment[pathnum - 1] = propnum;
        setNeedsLayout(true);
    }
    public void setPropAssignments(int[] pa) {
        propassignment = pa;
        setNeedsLayout(true);
    }

    public void addSymmetry(JMLSymmetry sym) {
        symmetries.add(sym);
        setNeedsLayout(true);
    }

    public void addEvent(JMLEvent ev) {
        setNeedsLayout(true);
        if (eventlist == null || eventlist.getT() > ev.getT()) {
            ev.setPrevious(null);
            ev.setNext(eventlist);
            if (eventlist != null)
                eventlist.setPrevious(ev);
            eventlist = ev;
            return;
        }

        JMLEvent current = eventlist;
        while (current.getNext() != null) {
            current = current.getNext();

            if (current.getT() > ev.getT()) {
                ev.setNext(current);
                ev.setPrevious(current.getPrevious());
                current.getPrevious().setNext(ev);
                current.setPrevious(ev);
                return;
            }
        }

        current.setNext(ev);
        ev.setNext(null);
        ev.setPrevious(current);
    }

    public void removeEvent(JMLEvent ev) {
        setNeedsLayout(true);
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

    public void setNeedsLayout(boolean needslayout) {
        laidout = !needslayout;
    }

    public JMLEvent getEventList()  { return eventlist; }

	public ArrayList<ArrayList<PathLink>> getPathlinks()  { return pathlinks; }

    public void addPosition(JMLPosition pos) throws JuggleExceptionUser {
        if (pos.getT() < getLoopStartTime() || pos.getT() > getLoopEndTime())
            return;  // throw new JuggleExceptionUser("<position> time out of range");
        setNeedsLayout(true);

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
        setNeedsLayout(true);
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

    public JMLPosition getPositionList()  { return positionlist; }

    // ------------------------------------------------------------------------
    //   Lay out the spatial paths in the pattern
    // ------------------------------------------------------------------------

    public void layoutPattern() throws JuggleExceptionInternal, JuggleExceptionUser {
        if (!valid)
            throw new JuggleExceptionInternal("Cannot do layout of invalid pattern");

        if (getNumberOfProps() == 0)
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
    }

    // ------------------------------------------------------------------------
    // Step 1: construct the list of events
    // Extend events in list using known symmetries
    // ------------------------------------------------------------------------

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

    // ------------------------------------------------------------------------
    // Step 2: figure out which events should be considered master events
    // ------------------------------------------------------------------------

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

    // ------------------------------------------------------------------------
    // Step 3: find positions/angles for all jugglers at all points in time,
    // using <position> tags. This is done by finding spline functions passing
    // through the specified locations and angles.
    // ------------------------------------------------------------------------

    public void findPositions() throws JuggleExceptionInternal {
        jugglercurve = new SplineCurve[getNumberOfJugglers()];
        jugglerangle = ( (Constants.ANGLE_LAYOUT_METHOD == Curve.lineCurve) ?
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
                jugglerangle[i - 1] = ( (Constants.ANGLE_LAYOUT_METHOD == Curve.lineCurve) ?
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
                    positions[0].setCoordinate(0.0, 0.0, 100.0);
                    angles[0].setCoordinate(0.0, 0.0, 0.0);
                } else {
                    double r = 70.0;
                    double theta = 360.0 / (double)getNumberOfJugglers();
                    if (r * Math.sin(Math.toRadians(0.5*theta)) < 65.0)
                        r = 65.0 / Math.sin(Math.toRadians(0.5*theta));
                    positions[0].setCoordinate(r*Math.cos(Math.toRadians(theta*(double)(i-1))),
                                               r*Math.sin(Math.toRadians(theta*(double)(i-1))), 100.0);
                    angles[0].setCoordinate(90.0 + theta*(double)(i-1), 0.0, 0.0);
                }

                positions[1] = positions[0];
                angles[1] = angles[0];
                jugglercurve[i - 1].setCurve(positions, times);
                jugglercurve[i - 1].calcCurve();
                jugglerangle[i - 1].setCurve(angles, times);
                jugglerangle[i - 1].calcCurve();
            } else {
                jugglercurve[i - 1] = new SplineCurve();
                jugglerangle[i - 1] = ( (Constants.ANGLE_LAYOUT_METHOD == Curve.lineCurve) ?
                                        (Curve)(new LineCurve()) : (Curve)(new SplineCurve()) );
                double[] times = new double[num+1];
                Coordinate[] positions = new Coordinate[num+1];
                Coordinate[] angles = new Coordinate[num+1];

                current = positionlist;
                int j = 0;

                while (current != null) {
                    if (current.getJuggler() == i) {
                        times[j] = current.getT();
                        positions[j] = current.getCoordinate();
                        angles[j] = new Coordinate(current.getAngle(),0.0,0.0);
                        ++j;
                    }
                    current = current.getNext();
                }
                times[num] = times[0] + getLoopEndTime() - getLoopStartTime();
                positions[num] = positions[0];
                angles[num] = new Coordinate(angles[0]);

                for (j = 1; j <= num; j++) {
                    while ((angles[j].x - angles[j-1].x) > 180.0)
                        angles[j].x -= 360.0;
                    while ((angles[j].x - angles[j-1].x) < -180.0)
                        angles[j].x += 360.0;
                }

                jugglercurve[i - 1].setCurve(positions, times);
                jugglercurve[i - 1].calcCurve();
                jugglerangle[i - 1].setCurve(angles, times);
                jugglerangle[i - 1].calcCurve();
            }
        }
    }

    // ------------------------------------------------------------------------
    // Step 4: transform event coordinates from local to global reference frame
    // ------------------------------------------------------------------------

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

    // ------------------------------------------------------------------------
    // Step 5: construct the links connecting events; build PathLink and HandLink lists
    // ------------------------------------------------------------------------

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
                            if (lasttr.getType() != JMLTransition.TRANS_THROW) {
                                String template = errorstrings.getString("Error_successive_catches");
                                Object[] arguments = { Integer.valueOf(i+1) };
                                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                            }
                            pl.setThrow(lasttr.getThrowType(), lasttr.getMod());
                            break;
                        case JMLTransition.TRANS_SOFTCATCH:
                            if (lasttr.getType() != JMLTransition.TRANS_THROW) {
                                String template = errorstrings.getString("Error_successive_catches");
                                Object[] arguments = { Integer.valueOf(i+1) };
                                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                            }
                            pl.setThrow(lasttr.getThrowType(), lasttr.getMod());
                            break;
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
        handlinks = new ArrayList<ArrayList<ArrayList<HandLink>>>();   // [getNumberOfJugglers()][2];

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
                                    vr = new VelocityRef(pl.getPath(), true);
                            } else if (tr.getType() == JMLTransition.TRANS_SOFTCATCH) {
                                PathLink pl = tr.getIncomingPathLink();
                                if (pl != null)
                                    vr = new VelocityRef(pl.getPath(), false);
                            }
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

    // ------------------------------------------------------------------------
    // Step 6: do a physical layout of the handlink paths
    // (Props were physically laid out in PathLink.setThrow() in Step 5 above)
    // ------------------------------------------------------------------------

    protected void layoutHandPaths() throws JuggleExceptionInternal {

        // go through HandLink lists, creating Path objects and calculating paths

        for (int i = 0; i < getNumberOfJugglers(); i++) {
            for (int j = 0; j < 2; j++) {
                // There are two cases -- a hand has throw or softcatch events (which define
                // hand velocities at points in time), or it does not (no velocities known).
                // To determine the spline paths, we need to solve for hand velocity at each
                // of its events, but this is done differently in the two cases.

                if (hasVDHandJMLTransition[i][j]) {
                    int num = 0;
                    HandLink startlink = null;

                    for (int k = 0; k < handlinks.get(i).get(j).size(); k++) {
                        HandLink hl = handlinks.get(i).get(j).get(k);
                        if (hl.getStartVelocityRef() != null) {
                            // this is guaranteed to happen before the loop start time, given
                            // the way we built the event list above
                            startlink = hl;
                            num = 1;
                        }
                        if ((hl.getEndVelocityRef() != null) && (startlink != null)) {
                            Coordinate[] pos = new Coordinate[num+1];
                            double[] times = new double[num+1];
                            Curve hp = new SplineCurve();

                            for (int l = 0; l < num; l++) {
                                HandLink hl2 = handlinks.get(i).get(j).get(k-num+1+l);
                                pos[l] = hl2.getStartEvent().getGlobalCoordinate();
                                times[l] = hl2.getStartEvent().getT();
                                hl2.setHandCurve(hp);
                            }
                            pos[num] = hl.getEndEvent().getGlobalCoordinate();
                            times[num] = hl.getEndEvent().getT();
                            Coordinate startvel = startlink.getStartVelocityRef().getVelocity();
                            Coordinate endvel = hl.getEndVelocityRef().getVelocity();
                            hp.setCurve(pos, times, startvel, endvel);
                            hp.calcCurve();
                            startlink = null;
                        }
                        ++num;
                    }
                } else {
                    // Build chain and solve for velocities.  This implementation is a little
                    // inefficient since it builds the second chain by a duplicate calculation rather
                    // than a copy.  Sketch of algorithm:
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
                    for (k = 0; k < handlinks.get(i).get(j).size(); ++k) {
                        hl = handlinks.get(i).get(j).get(k);
                        if (hl.getEndEvent().getT() > getLoopStartTime())
                            break;
                    }

                    for (int chain = 0; chain < 2; ++chain) {
                        HandLink startlink = hl;
                        JMLEvent startevent = startlink.getStartEvent();
                        int num = 1;    // number of links in chain
                        while (hl.getEndEvent().isDelayOf(startevent) == false) {
                            hl = handlinks.get(i).get(j).get(++k);
                            ++num;
                        }
                        Coordinate[] pos = new Coordinate[num + 1];
                        double[] times = new double[num+1];
                        Curve hp = new SplineCurve();

                        for (int l = 0; l < num; ++l) {
                            HandLink hl2 = handlinks.get(i).get(j).get(k - num + 1 + l);
                            pos[l] = hl2.getStartEvent().getGlobalCoordinate();
                            times[l] = hl2.getStartEvent().getT();
                            hl2.setHandCurve(hp);
                        }
                        pos[num] = hl.getEndEvent().getGlobalCoordinate();
                        times[num] = hl.getEndEvent().getT();
                        hp.setCurve(pos, times, null, null); // null endpoint velocities signals to calculate
                        hp.calcCurve();

                        if (chain == 0)
                            hl = handlinks.get(i).get(j).get(++k);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // Methods used by animator to get prop and body locations at specified times.
    // ------------------------------------------------------------------------

    public String getTitle()                { return title; }
    public int getNumberOfJugglers()        { return numjugglers; }
    public int getNumberOfPaths()           { return numpaths; }

    public int getNumberOfProps()           { return props.size(); }
    public Prop getProp(int propnum)        { return getPropDef(propnum).getProp(); }
    public PropDef getPropDef(int propnum)  { return props.get(propnum - 1); }
    public int getPropAssignment(int path)  { return propassignment[path - 1]; }

    public int getNumberOfSymmetries()      { return symmetries.size(); }
    public JMLSymmetry getSymmetry(int index)   { return symmetries.get(index); }

    public double getLoopStartTime()        { return 0.0; }
    public double getLoopEndTime() {
        for (int i = 0; i < getNumberOfSymmetries(); i++)
            if (getSymmetry(i).getType() == JMLSymmetry.TYPE_DELAY)
                return getSymmetry(i).getDelay();
        return -1.0;
    }

    // returns path coordinate in global frame
    public void getPathCoordinate(int path, double time, Coordinate newPosition) throws JuggleExceptionInternal {
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
        axis.x = 0.0;       // components of unit vector to rotate around
        axis.y = 0.0;
        axis.z = 1.0;
        return (3.0 * time);
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

    // Get volume of any catch made between time1 and time2; if no catch, returns 0.0
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
            return 0.0;
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
            return 1.0;

        return 0.0;
    }

    // Get volume of any bounce between time1 and time2; if no catch, returns 0.0
    public double getPathBounceVolume(int path, double time1, double time2) {
        int i;
        PathLink pl = null;

        for (i = 0; i < pathlinks.get(path - 1).size(); i++) {
            pl = pathlinks.get(path - 1).get(i);
            if (time1 >= pl.getStartEvent().getT() && time1 <= pl.getEndEvent().getT())
                break;
        }
        if (i == pathlinks.get(path - 1).size())
            return 0.0;
        while (true) {
            pl = pathlinks.get(path - 1).get(i);
            Path p = pl.getPath();
            if (p instanceof BouncePath) {
                BouncePath bp = (BouncePath)p;
                double vol = bp.getBounceVolume(time1, time2);
                if (vol > 0.0)
                    return vol;
            }
            if (time2 >= pl.getStartEvent().getT() && time2 <= pl.getEndEvent().getT())
                break;

            i++;
            if (i == pathlinks.get(path - 1).size())
                i = 0;
        }

        return 0.0;
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
            if (delay > 0.0)
                sym.setDelay(delay * scale);
        }

        setNeedsLayout(true);
    }

    public boolean isValid()    { return valid; }
    public boolean isLaidout()  { return laidout; }

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

    // ------------------------------------------------------------------------
    //   Use the JMLNode tree to build our internal pattern representation
    // ------------------------------------------------------------------------
    String loadingversion = null;

    protected void readJML(JMLNode current) throws JuggleExceptionUser {
        // process current node, then treat subnodes recursively

        String type = current.getNodeType();

        if (type.equalsIgnoreCase("jml")) {
            loadingversion = current.getAttributes().getAttribute("version");
            if (loadingversion == null)
                loadingversion = JMLDefs.default_JML_on_load;
        } else if (type.equalsIgnoreCase("pattern")) {
            // do nothing
        } else if (type.equalsIgnoreCase("title")) {
            setTitle(current.getNodeValue());
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
    }


    public void writeJML(Writer wr, boolean write_title) throws IOException {
        PrintWriter write = new PrintWriter(wr);

        for (int i = 0; i < JMLDefs.jmlprefix.length; i++)
            write.println(JMLDefs.jmlprefix[i]);
        write.println("<jml version=\"" + version + "\">");
        write.println("<pattern>");
        if (write_title && title != null)
            write.println("<title>" + JMLNode.xmlescape(title) + "</title>");
        for (int i = 0; i < props.size(); i++)
            props.get(i).writeJML(write);

        String out = "<setup jugglers=\"" + getNumberOfJugglers() + "\" paths=\""+
            getNumberOfPaths()+"\" props=\""+getPropAssignment(1);
        for (int i = 2; i <= getNumberOfPaths(); i++)
            out += "," + getPropAssignment(i);
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

    // java.lang.Object method overrides

    @Override
    public String toString() {
        StringWriter sw = new StringWriter();
        try {
            writeJML(sw, true);
        } catch (IOException ioe) {
        }

        return sw.toString();
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
