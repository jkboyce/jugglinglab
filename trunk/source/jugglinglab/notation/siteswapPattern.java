// siteswapPattern.java
//
// Copyright 2004 by Jack Boyce (jboyce@users.sourceforge.net) and others

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
import java.text.*;
import jugglinglab.util.*;


public class siteswapPattern extends mhnPattern {
    protected boolean oddperiod = false;


    // only works after parsePattern() is called:
    protected int getNorepPeriod() {
        return (oddperiod ? getPeriod()/2 : getPeriod());
    }

    public void parseInput(String config) throws JuggleExceptionUser, JuggleExceptionInternal {
        if (config.indexOf((int)'=') == -1)	// just the pattern
            pattern = config;
        else {
            super.parseInput(config);		// parse params common to all MHN notations
            parseLegacyParameters(config);	// parse legacy JuggleAnim params
        }
    }

    protected void parseLegacyParameters(String config) throws JuggleExceptionUser, JuggleExceptionInternal {
        ParameterList pl = new ParameterList(config);

        int		tempint;
        double 	tempdouble;
        String	temp = null;

        double rightthrowx = 0.2;	// JuggleAnim uses meters; Juggling Lab uses centimeters
        double rightcatchx = 0.5;
        double leftthrowx = 0.2;
        double leftcatchx = 0.5;
        boolean gotthrowcatch = false;

        if ((temp = pl.getParameter("tps")) != null) {
            try {
                bps = Double.valueOf(temp).doubleValue();
            } catch (NumberFormatException nfe) {
            }
        }

        if ((temp = pl.getParameter("dratio")) != null) {
            try {
                tempdouble = Double.valueOf(temp).doubleValue();
                if ((tempdouble > 0.0) && (tempdouble < 1.9))
                    dwell = 2.0 * tempdouble;
            } catch (NumberFormatException e) {
            }
        }

        if ((temp = pl.getParameter("throwx")) != null) {
            try {
                tempdouble = Double.valueOf(temp).doubleValue();
                rightthrowx = leftthrowx = tempdouble;
                gotthrowcatch = true;
            } catch (NumberFormatException e) {
            }
        }
        if ((temp = pl.getParameter("rightthrowx")) != null) {
            try {
                tempdouble = Double.valueOf(temp).doubleValue();
                rightthrowx = tempdouble;
                gotthrowcatch = true;
            } catch (NumberFormatException e) {
            }
        }
        if ((temp = pl.getParameter("leftthrowx")) != null) {
            try {
                tempdouble = Double.valueOf(temp).doubleValue();
                leftthrowx = tempdouble;
                gotthrowcatch = true;
            } catch (NumberFormatException e) {
            }
        }

        if ((temp = pl.getParameter("catchx")) != null) {
            try {
                tempdouble = Double.valueOf(temp).doubleValue();
                rightcatchx = leftcatchx = tempdouble;
                gotthrowcatch = true;
            } catch (NumberFormatException e) {
            }
        }
        if ((temp = pl.getParameter("rightcatchx")) != null) {
            try {
                tempdouble = Double.valueOf(temp).doubleValue();
                rightcatchx = tempdouble;
                gotthrowcatch = true;
            } catch (NumberFormatException e) {
            }
        }
        if ((temp = pl.getParameter("leftcatchx")) != null) {
            try {
                tempdouble = Double.valueOf(temp).doubleValue();
                leftcatchx = tempdouble;
                gotthrowcatch = true;
            } catch (NumberFormatException e) {
            }
        }

        if ((temp = pl.getParameter("balldiam")) != null) {
            try {
                propdiam = 100.0 * Double.valueOf(temp).doubleValue();
            } catch (NumberFormatException e) {
            }
        }

        if ((temp = pl.getParameter("g")) != null) {
            try {
                gravity = 100.0 * Double.valueOf(temp).doubleValue();
            } catch (NumberFormatException e) {
            }
        }

        if ((temp = pl.getParameter("mat_style")) != null) {
            throw new JuggleExceptionUser(errorstrings.getString("Error_unsupported_setting")+": 'mat_style'");
        }

        if ((temp = pl.getParameter("mat_DR")) != null) {
            throw new JuggleExceptionUser(errorstrings.getString("Error_unsupported_setting")+": 'mat_DR'");
        }

        if ((temp = pl.getParameter("mat_HR")) != null) {
            throw new JuggleExceptionUser(errorstrings.getString("Error_unsupported_setting")+": 'mat_HR'");
        }

        if ((hands == null) && gotthrowcatch) {	// not created by hands parameter
            hands = new mhnHands("("+(100.0*rightthrowx)+")("+(100.0*rightcatchx)+").("+
                                 (100.0*leftthrowx)+")("+(100.0*leftcatchx)+").");
        }
    }


    public void parsePattern() throws JuggleExceptionUser, JuggleExceptionInternal {
        // first clear out the internal variables
        th = null;
        symmetry = new Vector();

		SiteswapTreeItem tree = null;   // should probably be class variable
		
		try {
			// System.out.println("---------------");
			// System.out.println("Parsing pattern \"" + pattern + "\"");
			tree = SiteswapParser.parsePattern(pattern);
			// System.out.println("");
			// System.out.println(tree.toString());
		} catch (ParseException pe) {
			// System.out.println("---------------");
			// System.out.println("Parse error:");
			// System.out.println(pe.getMessage());
			// System.out.println("---------------");
			
			String template = errorstrings.getString("Error_pattern_syntax");
			String problem = ParseException.add_escapes(pe.currentToken.next.image);
			Object[] arguments = { problem, new Integer(pe.currentToken.next.beginColumn) };					
			throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
		} catch (TokenMgrError tme) {
			String template = errorstrings.getString("Error_pattern_syntax");
			String problem = TokenMgrError.addEscapes(String.valueOf(tme.curChar));
			Object[] arguments = { problem, new Integer(tme.errorColumn - 1) };					
			throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
		}
		
		// Need to use tree to fill in mhnPattern internal variables:
		/*
		protected int numjugglers;
		protected int numpaths;
		protected int period;
		protected int max_occupancy;
		protected mhnThrow[][][][] th;
		protected mhnHands hands = null;
		protected mhnBody bodies = null;
		protected int max_throw;
		protected int indexes;
		protected Vector symmetry = null;
		*/

		this.numjugglers = tree.jugglers;
		this.max_occupancy = 0;			// calculated in doFirstPass()
		this.max_throw = 0;
		
		this.right_on_even = new boolean[this.numjugglers];
		for (int i = 0; i < this.numjugglers; i++)
			right_on_even[i] = true;
			
		tree.beatnum = 0;
		doFirstPass(tree);
		
		if (!tree.switchrepeat) {
			if (tree.vanilla_asynch && ((tree.beats % 2) == 1)) {
				tree.switchrepeat = true;
				tree.beats *= 2;
				tree.throw_sum *= 2;
				this.oddperiod = true;
			}
		}
		
		this.period = tree.beats;
		if ((tree.throw_sum % tree.beats) != 0)
			throw new JuggleExceptionUser(errorstrings.getString("Error_siteswap_bad_average"));
		this.numpaths = tree.throw_sum / tree.beats;
		this.indexes = this.max_throw + this.period + 1;
		this.th = new mhnThrow[numjugglers][2][indexes][max_occupancy];
		
		/*
		System.out.println("period = "+period+", numpaths = "+numpaths+", max_throw = "+
							max_throw+", max_occupancy = "+max_occupancy);
		*/
		
		doSecondPass(tree, false, 0);
		
		// Finally, add pattern symmetries
        addSymmetry(new mhnSymmetry(mhnSymmetry.TYPE_DELAY, numjugglers, null, period));
        if (tree.switchrepeat) {	// know that tree is of type Pattern
            StringBuffer sb = new StringBuffer();
            for (int i = 1; i <= numjugglers; i++)
                sb.append("("+i+","+i+"*)");
            addSymmetry(new mhnSymmetry(mhnSymmetry.TYPE_SWITCHDELAY, numjugglers, sb.toString(), period/2));
        }
		
		// Random error check, not sure where this should go
        if ((bodies != null) && (bodies.getNumberOfJugglers() < this.getNumberOfJugglers()))
            throw new JuggleExceptionUser(errorstrings.getString("Error_jugglers_body"));
    }

	// The following are methods used to find transitions between patterns

	// First item in array argument is assumed to be a GroupedPattern type
	protected int[][] findExitState(SiteswapTreeItem[] item) {
		return null;
	}
	
	// Last item in array argument is assumed to be a GroupedPattern type
	protected int[][] findEntranceState(SiteswapTreeItem[] item) {
		return null;
	}
	
	protected SiteswapTreeItem findShortestTransition(int[][] start, int[][] end, int minbeats, boolean evenbeats) {
		return null;
	}
	
	
	// First pass through the tree:
	// 1)  Assign hands to "Single Throw" types
	// 2)  Determine whether any Pattern items need switchrepeat turned on
	// 3)  Calculate sti.beats for Pattern and GroupedPattern types
	// 4)  Determine absolute beat numbers for each throw
	// 5)  Calculate max_throw, period, numpaths, and max_occupancy
	// 6)  Resolve wildcards
	
	// What we need to evaluate wildcards:
	//   
	boolean[] right_on_even;	// asynch throws on even beat numbers made with right hand?
	
	protected void doFirstPass(SiteswapTreeItem sti) throws JuggleExceptionUser, JuggleExceptionInternal {
		SiteswapTreeItem child = null;
		
		sti.throw_sum = 0;
		sti.vanilla_asynch = true;
		
		switch (sti.type) {
			case SiteswapTreeItem.TYPE_PATTERN:
				// Can contain Grouped_Pattern, Solo_Sequence, Passing_Sequence, or Wildcard
				sti.beats = 0;

				for (int i = 0; i < sti.getNumberOfChildren(); i++) {
					child = sti.getChild(i);
					child.beatnum = sti.beatnum + sti.beats;
					
					if (child.type == SiteswapTreeItem.TYPE_WILDCARD) {
						// resolve this wildcard by finding a suitable transition sequence
						
						child.transition = null;	// remove any previously-found transition sequence
						
						// First find the pattern state immediately prior to the wildcard
						SiteswapTreeItem[] item = new SiteswapTreeItem[sti.getNumberOfChildren()];
						int index = sti.getNumberOfChildren() - 1;
						boolean done = false;
						for (int j = i-1; j >= 0; j--) {
							item[index--] = sti.getChild(j);
							if (sti.getChild(j).type == SiteswapTreeItem.TYPE_GROUPED_PATTERN) {
								done = true;
								break;
							}
							if (sti.getChild(j).type == SiteswapTreeItem.TYPE_WILDCARD)
								throw new JuggleExceptionUser("Can only have one wildcard between grouped patterns");
						}
						if (!done) {
							int beatsum = 0;
							for (int j = sti.getNumberOfChildren()-1; j > i; j--) {
								SiteswapTreeItem c = sti.getChild(j);
								item[index--] = c;
								if (c.type == SiteswapTreeItem.TYPE_GROUPED_PATTERN) {
									done = true;
									break;
								}
								if (c.type == SiteswapTreeItem.TYPE_WILDCARD)
									throw new JuggleExceptionUser("Can only have one wildcard between grouped patterns");
							}
						}
						if (!done)
							throw new JuggleExceptionUser("Must have at least one grouped subpattern to use wildcard");
						SiteswapTreeItem[] item2 = new SiteswapTreeItem[sti.getNumberOfChildren() - 1 - index];
						index++;
						for (int j = 0; index < sti.getNumberOfChildren(); j++, index++)
							item2[j] = item[index];
						for (int j = item2.length; j >= 0; j--) {
							/*	Need to assign beatnum to items in item2[]
							
								beatsum += c.beats;
								c.beatnum = sti.beat - beatsum;
								doFirstPass(c);		// make sure child has hands assigned
							*/
						}
						int[][] start_state = findExitState(item2);
							
						// Next find the pattern state we need to end up at.  Two cases: even number of transition beats, and odd.
						index = 0;
						done = false;
						for (int j = i+1; j < sti.getNumberOfChildren(); j++) {
							item[index++] = sti.getChild(j);
							if (sti.getChild(j).type == SiteswapTreeItem.TYPE_GROUPED_PATTERN) {
								done = true;
								break;
							}
							if (sti.getChild(j).type == SiteswapTreeItem.TYPE_WILDCARD)
								throw new JuggleExceptionUser("Can only have one wildcard between grouped patterns");
						}
						if (!done) {
							for (int j = 0; j < i; j++) {
								SiteswapTreeItem c = sti.getChild(j);
								item[index++] = c;
								if (c.type == SiteswapTreeItem.TYPE_GROUPED_PATTERN) {
									done = true;
									break;
								}
								if (c.type == SiteswapTreeItem.TYPE_WILDCARD)
									throw new JuggleExceptionUser("Can only have one wildcard between grouped patterns");
							}
						}
						if (!done)
							throw new JuggleExceptionUser("Must have at least one grouped subpattern to use wildcard");
						item2 = new SiteswapTreeItem[index];
						for (int j = 0; j < index; j++)
							item2[j] = item[j];

						for (int transition_beats = 0; transition_beats < 2; transition_beats++) {
							for (int j = 0; j < item2.length; j++) {
								/*	Need to assign beatnum to items in item2[]
								
									beatsum += c.beats;
									c.beatnum = sti.beat - beatsum;
									doFirstPass(c);		// make sure child has hands assigned
								*/
							}
							int[][] finish_state = findEntranceState(item2);
							
							// Rest of stuff goes here (find transition, fill in child.transition)
						}
					}
						
					doFirstPass(child);
					sti.beats += child.beats;
					sti.throw_sum += child.throw_sum;
					sti.vanilla_asynch &= child.vanilla_asynch;
				}
				if (sti.switchrepeat) {
					sti.beats *= 2;
					sti.throw_sum *= 2;
				}
				break;
			case SiteswapTreeItem.TYPE_GROUPED_PATTERN:
				// Contains only a Pattern type (single child)
				child = sti.getChild(0);
				if (sti.getNumberOfChildren() > 1) {
					sti.removeChildren();
					sti.addChild(child);
				}
				child.beatnum = sti.beatnum;
				doFirstPass(child);
				for (int i = 1; i < sti.repeats; i++) {
					SiteswapTreeItem child2 = (SiteswapTreeItem)(child.clone());
					sti.addChild(child2);
					child2.beatnum = sti.beatnum + i * child.beats;
					doFirstPass(child2);
				}
				sti.beats = child.beats * sti.repeats;
				sti.throw_sum = child.throw_sum * sti.repeats;
				sti.vanilla_asynch &= child.vanilla_asynch;
				break;
			case SiteswapTreeItem.TYPE_SOLO_SEQUENCE:
				// Contains Solo Paired Throw, Solo Multi Throw, or Hand Specifier types
				for (int i = 0; i < sti.getNumberOfChildren(); i++) {
					child = sti.getChild(i);
					child.beatnum = sti.beatnum + child.seq_beatnum;
					doFirstPass(child);
					sti.throw_sum += child.throw_sum;
					sti.vanilla_asynch &= child.vanilla_asynch;
				}
				break;
			case SiteswapTreeItem.TYPE_SOLO_PAIRED_THROW:
				// Contains only Solo Multi Throw type
				for (int i = 0; i < sti.getNumberOfChildren(); i++) {
					child = sti.getChild(i);
					child.beatnum = sti.beatnum;
					doFirstPass(child);
					child.left = (i == 0);
					child.synch_throw = true;
					sti.throw_sum += child.throw_sum;
				}
				sti.vanilla_asynch = false;
				break;
			case SiteswapTreeItem.TYPE_SOLO_MULTI_THROW:
				// Contains only Solo Single Throw type
				for (int i = 0; i < sti.getNumberOfChildren(); i++) {
					child = sti.getChild(i);
					child.beatnum = sti.beatnum;
					doFirstPass(child);
					sti.throw_sum += child.value;
				}
				if ((sti.beatnum % 2) == 0)
					sti.left = !right_on_even[sti.source_juggler - 1];
				else
					sti.left = right_on_even[sti.source_juggler - 1];
				if (sti.getNumberOfChildren() > this.max_occupancy)
					this.max_occupancy = sti.getNumberOfChildren();
				break;
			case SiteswapTreeItem.TYPE_SOLO_SINGLE_THROW:
				// No children
				if (sti.value > this.max_throw)
					this.max_throw = sti.value;
				break;
			case SiteswapTreeItem.TYPE_PASSING_SEQUENCE:
				// Contains only Passing Group type
			case SiteswapTreeItem.TYPE_PASSING_GROUP:
				// Contains only Passing Throws type
				for (int i = 0; i < sti.getNumberOfChildren(); i++) {
					child = sti.getChild(i);
					child.beatnum = sti.beatnum;
					doFirstPass(child);
					sti.throw_sum += child.throw_sum;
					sti.vanilla_asynch &= child.vanilla_asynch;
				}
				break;
			case SiteswapTreeItem.TYPE_PASSING_THROWS:
				// Contains Passing Paired Throw, Passing Multi Throw, or Hand Specifier types
				for (int i = 0; i < sti.getNumberOfChildren(); i++) {
					child = sti.getChild(i);
					child.beatnum = sti.beatnum + child.seq_beatnum;
					doFirstPass(child);
					sti.throw_sum += child.throw_sum;
					sti.vanilla_asynch &= child.vanilla_asynch;
				}
				break;
			case SiteswapTreeItem.TYPE_PASSING_PAIRED_THROW:
				// Contains only Passing Multi Throw type
				for (int i = 0; i < sti.getNumberOfChildren(); i++) {
					child = sti.getChild(i);
					child.beatnum = sti.beatnum;
					doFirstPass(child);
					child.left = (i == 0);
					child.synch_throw = true;
					sti.throw_sum += child.throw_sum;
				}
				sti.vanilla_asynch = false;
				break;
			case SiteswapTreeItem.TYPE_PASSING_MULTI_THROW:
				// Contains only Passing Single Throw type
				for (int i = 0; i < sti.getNumberOfChildren(); i++) {
					child = sti.getChild(i);
					child.beatnum = sti.beatnum;
					doFirstPass(child);
					sti.throw_sum += child.value;
				}
				if ((sti.beatnum % 2) == 0)
					sti.left = !right_on_even[sti.source_juggler - 1];
				else
					sti.left = right_on_even[sti.source_juggler - 1];
				if (sti.getNumberOfChildren() > this.max_occupancy)
					this.max_occupancy = sti.getNumberOfChildren();
				break;
			case SiteswapTreeItem.TYPE_PASSING_SINGLE_THROW:
				// No children
				if (sti.value > this.max_throw)
					this.max_throw = sti.value;
				break;
			case SiteswapTreeItem.TYPE_WILDCARD:
				if (sti.transition != null) {
					sti.transition.beatnum = sti.beatnum;
					doFirstPass(sti.transition);
					// copy variables from sti.transition to sti
					sti.throw_sum = sti.transition.throw_sum;
					sti.vanilla_asynch = sti.transition.vanilla_asynch;
					sti.beats = sti.transition.beats;
				} else
					throw new JuggleExceptionInternal("Wildcard not resolved");
				break;
			case SiteswapTreeItem.TYPE_HAND_SPEC:
				if ((sti.beatnum % 2) == 0)
					right_on_even[sti.source_juggler - 1] = !sti.spec_left;
				else
					right_on_even[sti.source_juggler - 1] = sti.spec_left;
				sti.throw_sum = 0;
				if (sti.beatnum > 0)
					sti.vanilla_asynch = false;
				break;		
		}
		
	}
	
	// Second pass through the tree:
	// 1)  Fill in the th[] array with mhnThrow objects
	protected void doSecondPass(SiteswapTreeItem sti, boolean switchhands, int beatoffset) throws JuggleExceptionUser {
		SiteswapTreeItem child = null;

		switch (sti.type) {
			case SiteswapTreeItem.TYPE_PATTERN:
				// Can contain Grouped_Pattern, Solo_Sequence, or Passing_Sequence
				for (int i = 0; i < sti.getNumberOfChildren(); i++) {
					child = sti.getChild(i);
					doSecondPass(child, switchhands, beatoffset);
				}

				if (sti.switchrepeat) {
					for (int i = 0; i < sti.getNumberOfChildren(); i++) {
						child = sti.getChild(i);
						doSecondPass(child, !switchhands, beatoffset + sti.beats/2);
					}
				}
				break;
				
			case SiteswapTreeItem.TYPE_GROUPED_PATTERN:
			case SiteswapTreeItem.TYPE_SOLO_SEQUENCE:
			case SiteswapTreeItem.TYPE_SOLO_PAIRED_THROW:
			case SiteswapTreeItem.TYPE_PASSING_SEQUENCE:
			case SiteswapTreeItem.TYPE_PASSING_GROUP:
			case SiteswapTreeItem.TYPE_PASSING_THROWS:
			case SiteswapTreeItem.TYPE_PASSING_PAIRED_THROW:
				for (int i = 0; i < sti.getNumberOfChildren(); i++) {
					child = sti.getChild(i);
					doSecondPass(child, switchhands, beatoffset);
				}
				break;
				
			case SiteswapTreeItem.TYPE_SOLO_MULTI_THROW:
			case SiteswapTreeItem.TYPE_PASSING_MULTI_THROW:
				int index = sti.beatnum + beatoffset;
				while (index < this.indexes) {
					for (int i = 0; i < sti.getNumberOfChildren(); i++) {
						child = sti.getChild(i);

						int source_hand;
						if (switchhands)
							source_hand = (sti.left ? RIGHT_HAND : LEFT_HAND);
						else
							source_hand = (sti.left ? LEFT_HAND : RIGHT_HAND);
				
						int dest_hand = ((child.value % 2) == 0) ? source_hand : (1-source_hand);
						if (child.x)
							dest_hand = 1 - dest_hand;
						
						String mod = child.mod;
						if (mod == null) {
							mod = "T";		// default throw modifier
							if ((child.source_juggler == child.dest_juggler) && (source_hand == dest_hand)) {
								if (child.value <= 2)	// want something more sophisticated?
									mod = "H";
							}
						}
						
						int dest_juggler = child.dest_juggler;
						if (dest_juggler > getNumberOfJugglers())
							dest_juggler = 1 + (dest_juggler-1) % getNumberOfJugglers();
							
						mhnThrow t = new mhnThrow(child.source_juggler, source_hand, index, i,
										  dest_juggler, dest_hand, index+child.value, -1, mod);
						if (hands != null) {
							int idx = index;
							if (sti.synch_throw && (source_hand == RIGHT_HAND))
								idx++;
							idx %= hands.getPeriod(child.source_juggler);
							t.handsindex = idx;
						}
						th[child.source_juggler-1][source_hand][index][i] = t;
						
						// System.out.println("added throw value "+child.value+" at index "+index);
					}
				
					index += this.period;
				}
				break;
		}
	}

}
