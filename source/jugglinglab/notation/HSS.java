package jugglinglab.notation;

import java.text.MessageFormat;
import java.util.*;

import jugglinglab.util.*;

class ossPatBnc {
	ArrayList<ArrayList<Character>> objPat;
	ArrayList<ArrayList<String>> bnc;
}

class hssParms {
	ArrayList<Character> pat;
	int hands;
}

class patParms {
//	int[][] beatJugHndMap;
	String newPat;
	double[] dwellBt;
}

class modParms {
	String convertedPattern;
	double[] dwellBeatsArray;
}

public class HSS {
	
	protected static double hss_dwell_default = 0.3;
	
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;
    
    public static modParms processHSS(String p, String h, boolean hld, boolean dwlmax, String hndspc, double dwl) throws 
                                 JuggleExceptionUser, JuggleExceptionInternal {
    	int ossPer, hssPer, hssOrb, patPer, numHnd, numJug;
    	String convPat;
    	double[] dwellArr;
    	modParms modinf = new modParms();
    		  
	    ArrayList<ArrayList<Character>> ossPat = new ArrayList<ArrayList<Character>>();
	    ArrayList<Character> hssPat = new ArrayList<Character>();
	    ArrayList<ArrayList<String>> bounc = new ArrayList<ArrayList<String>>();
	    hssParms hssinfo;
	    patParms patinfo;
	    ossPatBnc ossinfo;
	    
	    ossinfo = OssSyntax(p);
	    ossPat = ossinfo.objPat;
	    bounc = ossinfo.bnc;
	    
	    
	    hssinfo = HssSyntax(h);
	    
        hssPat = hssinfo.pat;
        numHnd = hssinfo.hands;
	    ossPer = ossPat.size();
	    hssPer = hssPat.size();
	    
	    ossPerm(ossPat, ossPer);
	    
	    hssOrb = hssPerm(hssPat, hssPer);
	    
	    int[][] handmap = new int[numHnd][2];//size not required here?
	    
	    if (hndspc != null) {
	    	handmap = parsehandspec(hndspc,numHnd);
	    } else {
	    	handmap = defhandspec(numHnd);
	    }
	    
	    numJug = 1;
	    for (int i = 0; i < numHnd; i++) {
	    	if (handmap[i][0] > numJug) {
	    		numJug = handmap[i][0];
	    	}
	    }
	    
	    //recalculate ossPer, hssPer inside convNotation itself; even numJug??
	    patinfo = convNotation(ossPat, hssPat, ossPer, hssPer, hssOrb, handmap, numJug, hld, dwlmax, dwl, bounc);		
    	modinf.convertedPattern = patinfo.newPat;
//	    convPat = patinfo.newPat;
	    if (modinf.convertedPattern == null) {
	    	throw new JuggleExceptionUser("no pattern");
	    }
	    
	    modinf.dwellBeatsArray = patinfo.dwellBt;
	    return modinf;
    }   
    
    // ensure object pattern is a vanilla pattern (multiplex allowed)
    // also perform average test
    // convert input pattern string to ArrayList identifying throws made on each beat
    public static ossPatBnc OssSyntax(String ss) throws 
                             JuggleExceptionUser, JuggleExceptionInternal{
//    	boolean pass = true;
	    boolean muxThrow = false;
	    boolean muxThrowFound = false;
	    boolean minOneThrow = false;
	    boolean b1 = false;
	    boolean b2 = false;
	    boolean b3 = false;
	    int throwSum = 0;
	    int numBeats = 0;
	    int subBeats = 0;
	    int numObj = 0;
	    ArrayList<ArrayList<Character>> oPat = new ArrayList<ArrayList<Character>>();
	    ArrayList<ArrayList<String>> bncinfo = new ArrayList<ArrayList<String>>();
	    
        for (int i = 0; i < ss.length(); i++) {
    	    char c = ss.charAt(i);
    	    if (muxThrow) {
    		    if (Character.toString(c).matches("[0-9,a-z]")) {
    			    minOneThrow = true;
    			    muxThrowFound = true;
                    oPat.get(numBeats-1).add(subBeats, c);
                    bncinfo.get(numBeats-1).add(subBeats,"null");
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
//    				    pass = false;
//    				    break;
    				    throw new JuggleExceptionUser("Syntax error in object pattern at position " + (i+1));
    			    }
    		    } else if (c == '\s') {
				    b1 = false;
				    b2 = false;
				    b3 = false;
    			    continue;
    		    } else if (c == 'B') {
    		    	if (b1) {
    		    		bncinfo.get(numBeats-1).set(subBeats-1,"B");
    		    		b1 = false;
    		    		b2 = true;
    		    		continue;
    		    	} else {
    				    throw new JuggleExceptionUser("Syntax error in object pattern at position " + (i+1));
    		    	}
    		    } else if (c == 'F' || c == 'L') {
    		    	if (b2) {
    		    		bncinfo.get(numBeats-1).set(subBeats-1,"B" + Character.toString(c));
    		    		b2 = false;
    		    		continue;
    		    	} else if (b3) {
    		    		bncinfo.get(numBeats-1).set(subBeats-1,"BH" + Character.toString(c));
    		    		b3 = false;
    		    		continue;
    		    	} else {
    				    throw new JuggleExceptionUser("Syntax error in object pattern at position " + (i+1));
    		    	}
    		    } else if (c == 'H') {
    		    	if (b2) {
    		    		bncinfo.get(numBeats-1).set(subBeats-1,"BH");
    		    		b2 = false;
    		    		b3 = true;
    		    	} else {
    				    throw new JuggleExceptionUser("Syntax error in object pattern at position " + (i+1));
    		    	}
    		    } else {
//    			    pass = false;
//    		    	break;
    			    throw new JuggleExceptionUser("Syntax error in object pattern at position " + (i+1));
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
    		    } else if (c == '\s') {
				    b1 = false;
				    b2 = false;
				    b3 = false;
    			    continue;
    		    } else if (c == 'B') {
    		    	if (b1) {
    		    		bncinfo.get(numBeats-1).set(subBeats,"B");
    		    		b1 = false;
    		    		b2 = true;
    		    		continue;
    		    	} else {
    				    throw new JuggleExceptionUser("Syntax error in object pattern at position " + (i+1));
    		    	}
    		    } else if (c == 'F' || c == 'L') {
    		    	if (b2) {
    		    		bncinfo.get(numBeats-1).set(subBeats,"B" + Character.toString(c));
    		    		b2 = false;
    		    		continue;
    		    	} else if (b3) {
    		    		bncinfo.get(numBeats-1).set(subBeats,"BH" + Character.toString(c));
    		    		b3 = false;
    		    		continue;
    		    	} else {
    				    throw new JuggleExceptionUser("Syntax error in object pattern at position " + (i+1));
    		    	}
    		    } else if (c == 'H') {
    		    	if (b2) {
    		    		bncinfo.get(numBeats-1).set(subBeats,"BH");
    		    		b2 = false;
    		    		b3 = true;
    		    	} else {
    				    throw new JuggleExceptionUser("Syntax error in object pattern at position " + (i+1));
    		    	}
    		    } else {
//    			    pass = false;
//    		    	break;
    			    throw new JuggleExceptionUser("Syntax error in object pattern at position " + (i+1));
    		    }
    	    } // if-else muxThrow
        } //for
//        if (!muxThrow && minOneThrow && pass ) {
        if (!muxThrow && minOneThrow ) {
    	    if (throwSum%numBeats == 0) {
    		    numObj = throwSum/numBeats;
//    		    pass = true;
    	    } else {
    	    	throw new JuggleExceptionUser("Bad average for object pattern");
//    		    pass = false;
    	    }
        } else {
//    	    pass = false;
        	throw new JuggleExceptionUser("Syntax error in object pattern");
        }
//	    if (!pass) {
//		    throw new JuggleExceptionUser("Invalid syntax or average for object pattern");
//	    }
        for (int i = 0; i < bncinfo.size(); i++) {
        	for (int j = 0; j < bncinfo.get(i).size(); j++) {
        		if (bncinfo.get(i).get(j) == "null") {
        			bncinfo.get(i).set(j, "\s");
        		} else {
        			bncinfo.get(i).set(j, bncinfo.get(i).get(j) + "\s");
        		}
        	}
        }
        ossPatBnc ossinf = new ossPatBnc();
        ossinf.objPat = oPat;
	    ossinf.bnc = bncinfo;

        return ossinf;  
      } //OssSyntax

    // ensure hand pattern is a vanilla pattern (multiplex NOT allowed)
    // also perform average test
    // convert input pattern string to ArrayList identifying throws made on each beat
    // also return number of hands, used later to build handmap 
      public static hssParms HssSyntax(String ss) throws 
                          JuggleExceptionUser, JuggleExceptionInternal{
    	  
    	  int throwSum = 0;
	      int numBeats = 0;
	      int nh = 0;
//	      boolean pass = true;
	      ArrayList<Character> hPat = new ArrayList<Character>();
          for (int i = 0; i < ss.length(); i++) {
    	      char c = ss.charAt(i);
		      if (Character.toString(c).matches("[0-9,a-z]")) {
                  hPat.add(numBeats, c);
			      numBeats++;
			      throwSum += Character.getNumericValue(c);
			      continue;
		      } else if (c == '\s') {
			      continue;
		      } else {
//			      pass = false;
//			      break;
			      throw new JuggleExceptionUser("Syntax error in hand pattern at position " + (i+1));
		      }
          }	
//          if (pass) {
    	      if (throwSum%numBeats == 0) {
    		      nh = throwSum/numBeats;
//    		      pass = true;
    	      } else {
//    		      pass = false;
    		      throw new JuggleExceptionUser("Bad average for hand pattern");
    	      }
//          }
//          if (!pass) {
//		      throw new JuggleExceptionUser("Invalid syntax or average for hand pattern");
//	      }

          hssParms hssinf = new hssParms();
	      hssinf.pat = hPat;
	      hssinf.hands = nh;
	      
          return hssinf;  
      }
      
      // permutation test for object pattern
      public static void ossPerm(ArrayList<ArrayList<Character>> os, int op) throws 
                                            JuggleExceptionUser, JuggleExceptionInternal {
	      boolean pass = true;
		  ArrayList<ArrayList<Integer>> mods = new ArrayList<ArrayList<Integer>>();
	      int m;
	      int[] cmp = new int[op];
	      for (int i = 0; i < op; i++) {
	    	  mods.add(i, new ArrayList<Integer>());
	    	  for (int j = 0; j < os.get(i).size(); j++) {
	    		  m = (Character.getNumericValue(os.get(i).get(j)) + i) % op;
	    		  mods.get(i).add(j,m);
	    		  cmp[m]++;
	    	  }
	      }
	      
	      for (int i = 0; i < op; i++) {
    		  if (cmp[i] != os.get(i).size()) {
    			  pass = false;
	    	  }
	      }
	      if (!pass) {
	    	  throw new JuggleExceptionUser("object pattern is invalid");
	      }
      }

      // permutation test for hand pattern 
      // return overall hand orbit period which is lcm of individual hand orbit periods
      public static int hssPerm(ArrayList<Character> hs, int hp) throws 
                                         JuggleExceptionUser, JuggleExceptionInternal{
	      boolean pass = true;
	      int m, ho;
		  int[] mods = new int[hp];
	      int[] cmp = new int[hp];
	      int[] orb = new int[hp];
	      boolean[] touched = new boolean[hp];
	      for (int i = 0; i < hp; i++) {
    		  m = (Character.getNumericValue(hs.get(i)) + i) % hp;
    		  mods[i] = m;
    		  cmp[m]++;
	      }
	      
	      for (int i = 0; i < hp; i++) {
    		  if (cmp[i] != 1) {
    			  pass = false;
	    	  }
	      }
	      ho = 1;
	      if (pass) {
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
	    	  
	      } else {
	    	  throw new JuggleExceptionUser("hand pattern is invalid");
	      }
	      return ho;  
      }

      // read and validate user defined handspec
      // if valid, convert to handmap assigning juggler number and that juggler's left or right hand to each hand
      public static int[][] parsehandspec(String hs, int nh) throws 
                                             JuggleExceptionUser, JuggleExceptionInternal{

    	  int[][] hm = new int[nh][2];
    	  //assigningLefthand, assigningRighthand, jugglerActive
    	  boolean al = false; //assignLeft
    	  boolean ar = false; //assignRight
    	  boolean ja = false; //jugglerActive
    	  boolean pass = false;
    	  boolean hp = false; //handPresent
    	  boolean ns = false; //numberformationStarted
    	  boolean mf = false; //matchFound
    	  int jn = 0; //jugglerNumber
    	  String bhn = null; //buildHandNumber
    	  
    	  for (int i = 0; i < hs.length(); i++) {
    		  char c = hs.charAt(i);
    		  if (!ja) { //if not in the middle of processing a () pair
    			  if (c == '(') {
    				  ja = true; //juggler assignment is active
    				  al = true; //at opening "(" assign left hand
    				  ns = true;
    				  bhn = null;
    				  pass = false;
    				  hp = false;
    				  jn++;
    				  continue;
    			  } else if (c == '\s') {
    				  continue;
    			  } else {
    				  throw new JuggleExceptionUser("error in handspec syntax at position " + (i+1));
    			  }
    		  } else {
    			  if (al) {
    				  if (Character.toString(c).matches("[0-9]")) {
    					  if (ns) {
        				      if (bhn == null) {
        				    	  bhn = Character.toString(c);
        				      } else {
        				    	  bhn = bhn + Character.toString(c);
        				      }
        				      continue;
    					  } else {
    						  throw new JuggleExceptionUser("error in handspec syntax at position " + (i+1)); 
    					  }
        			  } else if (c == '\s') {
        				  if ((bhn != null) && (bhn.length() >= 1)) {
        					  ns = false;
        					  continue;
        				  } else {
        					  continue;
        				  }
        			  } else if (c == ',') {
        				  al = false; // at "," left hand assignment complete
        				  ar = true; 
        				  ns = true;
 
        				  if (bhn != null) {
        					  if ((Integer.parseInt(bhn) >= 1) &&(Integer.parseInt(bhn) <= nh)) {
        						  if (hm[Integer.parseInt(bhn)-1][0] == 0) {//if juggler not already assigned to this hand
            						  hm[Integer.parseInt(bhn)-1][0] = jn;
                					  hm[Integer.parseInt(bhn)-1][1] = 0;
                					  hp = true;
        						  } else {
               						  throw new JuggleExceptionUser("hand " + Integer.parseInt(bhn) + " assigned more than once"); 
        						  }
        					  } else {
        						  throw new JuggleExceptionUser("hand number " + Integer.parseInt(bhn) + " is out of range in handspec"); 
        					  }
        				  } else {
        					  hp = false;
        				  }
        				  bhn = null; //reset bhn string
        				  continue;
        			  } else {
        				  throw new JuggleExceptionUser("error in handspec syntax at position " + (i+1));
        			  }
    			  } else if (ar) {
        			  if (Character.toString(c).matches("[0-9]")) {
    					  if (ns) {
        				      if (bhn == null) {
        				    	  bhn = Character.toString(c);
        				      } else {
        				    	  bhn = bhn + Character.toString(c);
        				      }
        				      continue;
    					  } else {
    						  throw new JuggleExceptionUser("error in handspec syntax at position " + (i+1)); 
    					  }
        			  } else if (c == '\s') {
        				  if ((bhn != null) && (bhn.length() >= 1)) {
        					  ns = false;
        					  continue;
        				  } else {
        					  continue;
        				  }
        			  } else if (c == ')') {
        				  ar = false;
        				  ja = false; //juggler assignment is inactive after ")"

        				  if (bhn != null) {
        					  if ((Integer.parseInt(bhn) >= 1) &&(Integer.parseInt(bhn) <= nh)) {
        						  hm[Integer.parseInt(bhn)-1][0] = jn;
            					  hm[Integer.parseInt(bhn)-1][1] = 1;
        					  } else {
        						  throw new JuggleExceptionUser("hand number " + Integer.parseInt(bhn) + " is out of range in handspec"); 
        					  }
        				  } else if (!hp) { //if left hand was also not present
        					  throw new JuggleExceptionUser("specify at least one hand for each juggler");
        				  }
        				  bhn = null; //reset bhn string
        				  pass = true;
        				  continue;
        			  } else {
        				  throw new JuggleExceptionUser("error in handspec syntax at position " + (i+1));
        			  }
   				  
    			  }
    			  
    		  }
    		  
    	  }
    	  if (jn > nh) { //will this ever happen?
    		  throw new JuggleExceptionUser("error in handspec, too many jugglers. Max " + nh + " jugglers possible");
    	  }
    	  if (pass) {
    		  for (int i = 0; i < nh; i++) {//what is this for?
				  if (hm[i][0] != 0) {
					  mf = true;
					  break;
				  }
    			  if (!mf) {
    				  throw new JuggleExceptionUser("hand " + (i+1) + " missing in handspec");
    			  }
    		  }
    	  } else {
    		  throw new JuggleExceptionUser("error in handspec syntax");
    	  }
    	  
    	  for (int i = 0; i < nh; i++) {
    		  if (hm[i][0] == 0) {
    			  throw new JuggleExceptionUser("juggler not assigned to hand " +(i+1));
    		  }
    	  }
    	  return hm;
      }

      // in the absence of user defined handspec, build a default handmap
      // assume numHnd/2 jugglers if numHnd even, else (numHnd+1)/2 jugglers
      // assign hand 1 to J1 right hand, hand 2 to J2 right hand and so on
      // once all right hands assigned, come back to J1 and start assigning left hand
      public static int[][] defhandspec(int nh) throws 
                                  JuggleExceptionUser, JuggleExceptionInternal{

    	  int[][] hm = new int[nh][2];
    	  int nj; //numberofJugglers
    	  
    	  if (nh%2==0) {
    		  nj = nh/2;
    	  } else {
    		  nj = (nh+1)/2;
    	  }
    	  
    	  for (int i = 0; i < nh; i++) {
    		  if (i < nj) {
    			  hm[i][0] = i+1; // juggler number
        		  hm[i][1] = 1; // 0 for left hand, 1 for right
    		  } else {
    			  hm[i][0] = i+1 - nj;
    			  hm[i][1] = 0;
    		  }
    	  }
    	  
    	  return hm;
      }

      //convert oss hss format to Juggling Lab synchronous passing notation with suppressed
      //empty beats so that odd synchronous throws are also allowed
      public static patParms convNotation(ArrayList<ArrayList<Character>> os, 
    		                       ArrayList<Character> hs, int op, int hp, 
    		                       int ho, int[][] hm, int nj, boolean h, boolean dm, double dd,
    		                       ArrayList<ArrayList<String>> bn) throws 
                                      JuggleExceptionUser, JuggleExceptionInternal{
	      int pp, ch, t, cj; //pattern period, current hand, throw, current juggler
    	  String cp = null;  //converted pattern
          patParms patinf = new patParms();
          boolean flag = false;
          
    	  //invert, pass and hold for x, p and H
    	  ArrayList<ArrayList<String>> iph = new ArrayList<ArrayList<String>>(); 
	      
		  pp = Permutation.lcm(op, ho); //pattern period
		  
		  int[] ah = new int[pp]; //assigned hand
		  boolean ad[] = new boolean[pp]; //assignment done
	      int[][] ji = new int[pp][2]; //jugglerInfo: juggler#, hand#
	      double[] db= new double[pp]; //dwell beats
	      
	      //extend oss size to pp
	      if (pp > op) {
	    	  for (int i = op; i < pp; i++) {
	    		  os.add(i,os.get(i-op));
//	    		  os.add(i, new ArrayList<Character>());
//	    		  for (int j = 0; j < os.get(i-op).size(); j++) {
//	    			  os.get(i).add(j, os.get(i-op).get(j));
//	    		  }
	    	  }
	      }

	      //extend bounce size to pp
	      if (pp > op) {
	    	  for (int i = op; i < pp; i++) {
	    		  bn.add(i,bn.get(i-op));
	    	  }
	      }

	      //extend hss size to pp
	      if (pp > hp) {
	    	  for (int i = hp; i < pp; i++) {
	    		  hs.add(i, hs.get(i-hp));
	    	  }
	      }
	      
	      //check if hss is 0 when oss is not 0; else assign juggler and hand to each beat
	      ch = 0;
	      for (int i = 0; i < pp; i++) {
	    	  if (hs.get(i) == '0') {
	    		  ji[i][0] = 0; //assign juggler number 0 for no hand
	    		  ji[i][1] = -1; //assign hand number -1 for no hand
	    		  ad[i] = true;
	    		  for (int j = 0; j < os.get(i).size(); j++) {
	    			  if (os.get(i).get(j) != '0') {
	    				  throw new JuggleExceptionUser("no hand to throw object at beat " +(i+1));
	    			  }
	    		  }
	    	  } else {
	    	      if (!ad[i]) {
	    		      ch++;
	    		      ah[i] = ch;
	    		      ad[i] = true;
	    		      int next = (i + Character.getNumericValue(hs.get(i)))%pp;
	    		      while (next != i) {
	    			      ah[next] = ch;
	    			      ad[next] = true;
	    			      next = (next + Character.getNumericValue(hs.get(next)))%pp;
	    		      }
	    	      }
	    	      ji[i][0] = hm[ah[i]-1][0]; //juggler number at beat i based on handmap
	    	      ji[i][1] = hm[ah[i]-1][1]; //throwing hand at beat i based on handmap
	    	  }
	      }
//	      patinf.beatJugHndMap = ji;
	      
	      //determine dwellbeats array
	      flag = false;
	      int[] mincaught = new int[pp];
	      int ti = 0; //target index
	      int curThrow; //current throw
//	      for (int i = 0; i < pp; i++) {
//	    	  mincaught[i] = 0;
//	      }

	      // find minimum throw being caught at each beat: more than one throw could be
	      // getting caught in case of multiplex throw. Dwell time on that beat will be 
	      // maximized for the minimum throw being caught. Higher throws may thus show
	      // more flight time than is strictly required going by hand availability.
	      for (int i = 0; i < pp; i++) {
	    	  for (int j = 0; j < os.get(i).size(); j++) {
	    		  curThrow = Character.getNumericValue(os.get(i).get(j));
	    		  ti = (i + curThrow)  % pp;
	    		  if (curThrow > 0) {
	    			  if (mincaught[ti] == 0) {
	    				  mincaught[ti] = curThrow;
	    			  } else if (curThrow < mincaught[ti]) {
	    				  mincaught[ti] = curThrow;
	    			  }
	    		  }
	    	  }
	      }
	      if (!dm) {
	    	  for (int i = 0; i < pp; i++) {
	    		  if ((ji[i][0] == ji[(i+1)%pp][0]) && (ji[i][1] == ji[(i+1)%pp][1])) {
	    			  flag = true; //if same hand throws on successive beats
	    			  break;
	    		  }
	    	  }
	    	  if (flag) {
	    		  for (int i = 0; i < pp; i++) {
	    			  db[i] = hss_dwell_default;
	    		  }
	    	  } else {
	    		  for (int i = 0; i < pp; i++) {
	    			  db[i] = dd; //user defined default dwell in front panel
	    		  }	  
	    	  }
	    	  for (int i = 0; i < pp; i++) {
	    		  if (db[i] >= (double)mincaught[i]) {
	    			  db[i] = (double)mincaught[i] - (1 - hss_dwell_default);
	    		  }
	    	  }
	      } else {//if dwellmax is true
	    	  for (int i = 0; i < pp; i++) {
	    		  int j = (i+1)%pp;
	    		  int diff = 1;
	    		  while ((ji[i][0] != ji[j][0]) || (ji[i][1] != ji[j][1])) {
	    			  j = (j+1)%pp;
	    			  diff++;
	    		  }
	    		  db[j] = (double)diff - (1 - hss_dwell_default);
	    	  }
	    	  for (int i = 0; i < pp; i++) {
	    		  if (db[i] >= (double)mincaught[i]) {
	    			  db[i] = (double)mincaught[i] - (1 - hss_dwell_default);
	    		  } else if (db[i] <= 0) {
	    			  db[i] = hss_dwell_default;
	    		  }
	    	  }
	      }
	      
	      //remove clashes in db array in case dwell times from different beats get optimized
	      //to same time instant
	      boolean[] clash = new boolean[pp];
	      int clashcnt = 0;
	      for (int i = 0; i < pp; i++) {
	    	  for (int j = 1; j < pp; j++) {
	    		  if ((db[(i+j)%pp] - db[i] - j)%pp == 0) {
	    			  clash[(i+j)%pp] = true;
	    			  clashcnt++;
	    		  }
	    	  }
	    	  while (clashcnt != 0) {
	    		  for (int k = 0; k < pp; k++) {
	    			  if (clash[k]) {
	    				  db[k] = db[k] + hss_dwell_default/clashcnt;
	    				  clashcnt--;
	    				  clash[k] = false;
	    			  }
	    		  }
	    	  }
	      }
	      
	      patinf.dwellBt = db;

	      //determine x, p and H throws
	      for (int i = 0; i < pp; i++) {
	    	  iph.add(i, new ArrayList<String>());
	    	  for (int j = 0; j < os.get(i).size(); j++) {
		    	  iph.get(i).add(j, null);
		    	  t = Character.getNumericValue(os.get(i).get(j));
	    		  if (t%2 == 0 && ji[i][1] != ji[(i+t)%pp][1]) {
	    			  iph.get(i).set(j,"x"); //put x for even throws to other hand
	    		  } else if (t%2 != 0 && ji[i][1] == ji[(i+t)%pp][1]) {
	    			  iph.get(i).set(j,"x"); //put x for odd throws to same hand
	    		  }
	    		  if (ji[i][0] != ji[(i+t)%pp][0]) { //if target juggler is different from one throwing
	    			  if (iph.get(i).get(j) != "x") {
	    				  iph.get(i).set(j,"p"+ji[(i+t)%pp][0]);
	    			  } else {
	    				  iph.get(i).set(j,"xp"+ji[(i+t)%pp][0]);
	    			  }
	    		  } else if (h) {
	    			  if (t == Character.getNumericValue(hs.get(i))) {
	    				  if (iph.get(i).get(j) != "x") {
	    					  iph.get(i).set(j,"H");
	    				  } else {
	    					  iph.get(i).set(j,"xH");
	    				  }
	    			  }
	    		  }
	    	  }

	      }
	      
          for (int i = 0; i < pp; i++) {
	       	  for (int j = 0; j < bn.get(i).size(); j++) {
	        	  if (iph.get(i).get(j) == null) {
		       		  iph.get(i).set(j, bn.get(i).get(j));
	        	  } else {
		       		  iph.get(i).set(j, iph.get(i).get(j) + bn.get(i).get(j));
	        	  }
	          }
	      }
	      
	      //construct the pattern string
	      for (int i = 0; i < pp; i++) {
	    	  cj = 0;
	    	  while (cj < nj) {
	    		  if (cp == null) {  //at the start of building the converted pattern
	    			  cp = "<";
	    		  } else if (cj == 0){ //at the start of a new beat
	    			  cp = cp + "<";
	    		  }
	    		  if (ji[i][1] == 0) {//if left hand is throwing at current beat
	    			  cp = cp + "(";
	    			  if (ji[i][0] == cj+1) {//if currentjuggler is throwing at current beat
	    				  if (os.get(i).size() > 1) {// if it is a multiplex throw
	    					  cp = cp + "[";
	    					  for (int j = 0; j < os.get(i).size(); j++) {
	    						  cp = cp + Character.toString(os.get(i).get(j));
	    						  if (iph.get(i).get(j) != null) {
	    							  cp = cp + iph.get(i).get(j);
	    						  }
	    					  }
	    					  cp = cp + "]";
	    				  } else {// if not multiplex throw
	    					  cp = cp + Character.toString(os.get(i).get(0));
	    					  if (iph.get(i).get(0) != null) {
	    						  cp = cp + iph.get(i).get(0);
	    					  }
	    				  }
	    				  cp = cp + ",0)!"; //no sync throws allowed, put 0 for right hand
	    				  if (cj == nj-1) {
	    					  cp = cp + ">";
	    				  } else {
	    					  cp = cp + "|";
	    				  }
	    				  cj++;
	    			  } else {// if current juggler is not throwing at this beat
	    				  cp = cp + "0,0)!";
	    				  if (cj == nj-1) {
	    					  cp = cp + ">";
	    				  } else {
	    					  cp = cp + "|";
	    				  }
	    				  cj++;
	    			  }
	    		  } else {// if right hand is throwing at this beat
	    			  cp = cp + "(0,";  //no sync throws allowed, put 0 for left hand
	    			  if (ji[i][0] == cj+1) {//if currentjuggler is throwing at current beat
	    				  if (os.get(i).size() > 1) {// if it is a multiplex throw
	    					  cp = cp + "[";
	    					  for (int j = 0; j < os.get(i).size(); j++) {
	    						  cp = cp + Character.toString(os.get(i).get(j));
	    						  if (iph.get(i).get(j) != null) {
	    							  cp = cp + iph.get(i).get(j);
	    						  }
	    					  }
	    					  cp = cp + "]";
	    				  } else {// if not multiplex throw
	    					  cp = cp + Character.toString(os.get(i).get(0));
	    					  if (iph.get(i).get(0) != null) {
	    						  cp = cp + iph.get(i).get(0);
	    					  }
	    				  }
	    				  cp = cp + ")!";
	    				  if (cj == nj-1) {
	    					  cp = cp + ">";
	    				  } else {
	    					  cp = cp + "|";
	    				  }
	    				  cj++;
	    			  } else {// if current juggler is not throwing at this beat
	    				  cp = cp + "0)!";
	    				  if (cj == nj-1) {
	    					  cp = cp + ">";
	    				  } else {
	    					  cp = cp + "|";
	    				  }
	    				  cj++;
	    			  }
	    		  } //if-else left-right hand
	    	  } //while cj < nj
	      } //for all beats
	      
	      patinf.newPat = cp;
	      return patinf;  
      } //convNotation

}