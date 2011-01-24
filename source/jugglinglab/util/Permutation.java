// Permutation.java
//
// Copyright 2002 by Jack Boyce (jboyce@users.sourceforge.net) and others

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

package jugglinglab.util;

import java.util.*;


public class Permutation {
	protected int 		size;
	protected int[] 	mapping;
	protected boolean	reverses;
	
	
	public Permutation(int n, boolean reverses) {	// identity permutation
		this.size = n;
		this.reverses = reverses;
		
		if (reverses) {
			mapping = new int[size*2+1];
			
			for (int i = 0; i < (size*2+1); i++)
				mapping[i] = i-size;
		} else {
			mapping = new int[size];
	
			for (int i = 0; i < n; i++)
				mapping[i] = i+1;
		}
	}
	
	public Permutation(int n, int[] mapping, boolean reverses) {
		this.size = n;
		this.reverses = reverses;
		this.mapping = mapping;
	}
	
	public Permutation(int n, String perm, boolean reverses) throws JuggleException {
		int i;
		boolean[] used;
		
		this.size = n;
		this.reverses = reverses;
		
		if (reverses) {
			mapping = new int[size*2+1];
			used = new boolean[size*2+1];
			
			for (i = 0; i < (size*2+1); i++) {
				mapping[i] = 0;
				used[i] = false;
			}
		} else {
			mapping = new int[size];
			used = new boolean[size];
	
			for (i = 0; i < n; i++) {
				mapping[i] = 0;
				used[i] = false;
			}
		}
		
		// two ways to specify permutation: cycle notation (parenthesis)
		// and an explicit mapping (comma-separated integers)
		
		if (perm.indexOf('(') == -1) {
				// explicit mapping
			int num;
			StringTokenizer st = new StringTokenizer(perm, ",");
			
			if (st.countTokens() != size)
				throw new JuggleException("Permutation init error: must have "+n+
								" elements in mapping");
			for (i = 0; i < size; i++) {
				String s = st.nextToken().trim();
				try {
					num = Integer.parseInt(s);
				} catch (NumberFormatException nfe) {
					throw new JuggleException("Permutation init error: number format");
				}
				if ((num < 1) || (num > size))
					throw new JuggleException("Permutation init error: out of range");
				
				if (used[num-1])
					throw new JuggleException("Permutation init error: not one-to-one");
				
				used[num-1] = true;
				mapping[i] = num;
			}
		} else {
				// cycle notation
			StringTokenizer st1 = new StringTokenizer(perm, ")");
			
			while (st1.hasMoreTokens()) {
				String s1 = st1.nextToken().trim();
				if (s1.charAt(0) != '(')
					throw new JuggleException("Permutation init error: parenthesis not grouped");
				s1 = s1.substring(1);
				int num = 0, lastnum = -(size+1);
				StringTokenizer st2 = new StringTokenizer(s1, ",");
				while (st2.hasMoreTokens()) {
					String s2 = st2.nextToken().trim();
					try {
						if (reverses) {
							boolean negate = false;
							if (s2.endsWith("*")) {
								negate = true;
								s2 = s2.replace('*',' ').trim();
							}
							num = Integer.parseInt(s2);
							if (negate)
								num = -num;
						} else
							num = Integer.parseInt(s2);
					} catch (NumberFormatException nfe) {
						throw new JuggleException("Permutation init error: number format");
					}

					if (reverses) {
						if ((num < -size) || (num > size) || (num == 0))
							throw new JuggleException("Permutation init error: out of range");
					
						if (used[num+size])
							throw new JuggleException("Permutation init error: not one-to-one");
						used[num+size] = true;
						
						if (lastnum == -(size+1))
							mapping[num+size] = num;
						else {
							mapping[num+size] = mapping[lastnum+size];
							mapping[lastnum+size] = num;
							if (used[-lastnum+size] && (mapping[-lastnum+size] != -num))
								throw new JuggleException("Permutation init error: input not reversible");
						}
					} else {
						if ((num < 1) || (num > size))
							throw new JuggleException("Permutation init error: out of range");
					
						if (used[num-1])
							throw new JuggleException("Permutation init error: not one-to-one");
						used[num-1] = true;
						
						if (lastnum == -(size+1))
							mapping[num-1] = num;
						else {
							mapping[num-1] = mapping[lastnum-1];
							mapping[lastnum-1] = num;
						}
					}
					lastnum = num;
				}
			}
		}
		
		if (reverses) {
			for (i = 1; i <= size; i++) {
				if (used[i+size] && !used[-i+size])
					mapping[-i+size] = -mapping[i+size];
				else if (!used[i+size] && used[-i+size])
					mapping[i+size] = -mapping[-i+size];
				else if (!used[i+size] && !used[-i+size]) {
					mapping[-i+size] = 0;
					mapping[i+size] = 0;
				}
			}
		} else {
			for (i = 0; i < size; i++)
				if (!used[i])
					mapping[i] = i+1;
		}
		
//		if (reverses) {
//			for (i = -size; i <= size; i++)
//				System.out.println("mapping["+i+"] = "+mapping[i+size]);
//		}
	}

	
	public String toString() 	{ return this.toString(true); }
	
	public String toString(boolean cyclenotation) {
		int i;
		String s;
		
		if (cyclenotation) {
			if (reverses) {
				int start, current;
				boolean[] printed = new boolean[size];
				for (i = 0; i < size; i++)
					printed[i] = false;
				s = "";
				
				for (i = 0; i < size; i++) {
					if (printed[i] == false) {
						start = i+1;
						printed[i] = true;
						current = mapping[start+size];
						if (current != 0) {
							s += "(" + convertReverse(start);
							while (current != start) {
								if (current > 0)
									printed[current-1] = true;
								else if (current < 0)
									printed[-current-1] = true;
								s += "," + convertReverse(current);
								current = mapping[current+size];
							}
							s += ")";
						}
					}
				}
			} else {
				int start, current, left = size;
				boolean[] printed = new boolean[size];
				for (i = 0; i < size; i++)
					printed[i] = false;
				s = "";
				
				while (left > 0) {
					for (i = 0; i < size; i++)
						if (printed[i] == false)
							break;
					start = i+1;
					printed[i] = true;
					s = s + "(" + start;
					left--;
					current = mapping[i];
					while (current != start) {
						s = s + "," + current;
						printed[current-1] = true;
						left--;
						current = mapping[current-1];
					}
					s = s + ")";
				}
			}
		} else {
			if (reverses) {
				s = convertReverse(mapping[size+1]);
				for (i = 1; i < size; i++)
					s = s + "," + convertReverse(mapping[size+1+i]);
			} else {
				s = "" + mapping[0];
				for (i = 1; i < size; i++)
					s = s + "," + mapping[i];
			}
		}
		return s;
	}

	protected String convertReverse(int num) {
		if (num >= 0)
			return (""+num);
		else
			return ((-num)+"*");
	}
	
	public int getSize() 	{ return size; }
	
	public boolean hasReverses()	{ return reverses; }
	
	public boolean equals(Permutation p) {
		if (p == null)
			return false;
		if (reverses != p.hasReverses())
			return false;
		if (getSize() != p.getSize())
			return false;
		for (int i = 0; i < getSize(); i++)
			if (getMapping(i+1) != p.getMapping(i+1))
				return false;
		return true;
	}
	
	public int getMapping(int elem) {
		if (reverses)
			return mapping[elem+size];
		else
			return mapping[elem-1];
	}
	
	public int getMapping(int elem, int power) {
		if (power > 0) {
			for (int i = 0; i < power; i++)
				elem = getMapping(elem);
		} else if (power < 0) {
			for (int i = power; i < 0; i++)
				elem = getInverseMapping(elem);
		}
		return elem;
	}

	public Permutation apply(Permutation firstp) {
		if (this.getSize() != firstp.getSize())
			return null;
		if (this.hasReverses() || firstp.hasReverses())
			return null;
			
		int[] res = new int[this.getSize()];
		for (int i = 0; i < this.getSize(); i++)
			res[i] = this.getMapping(firstp.getMapping(i+1));
		
		return new Permutation(this.getSize(), res, false);
	}
	
	public int getInverseMapping(int elem) {
		if (reverses) {
			for (int i = 0; i < (2*size+1); i++)
				if (mapping[i] == elem)
					return (i-size);
		} else {
			for (int i = 0; i < size; i++) {
				if (mapping[i] == elem)
					return (i+1);
			}
		}
		return 0;
	}
	
	public Permutation getInverse() {
		int[] invmapping = null;
		
		if (reverses) {
			invmapping = new int[size*2+1];
			
			for (int i = 0; i < (size*2+1); i++)
				invmapping[mapping[i]+size] = i-size;
		} else {
			invmapping = new int[size];
	
			for (int i = 0; i < size; i++)
				invmapping[mapping[i]-1] = i+1;
		}

		return new Permutation(size, invmapping, reverses);
	}
	
	
	public static int lcm(int x, int y) {	// Euclid's GCD algorithm (x>0, y>0)
		int x0 = x;
		int y0 = y;
		int	g = y;
		
		while (x > 0) {
			g = x;
			x = y % x;
			y = g;
		}
		return (x0*y0)/g;
	}
	
	public int getOrder() {
		int ord = 1;
		
		for (int elem = 1; elem <= size; elem++)
			if (getMapping(elem) != 0)
				ord = lcm(ord, getOrder(elem));
			
		return ord;
	}

	
	public int getOrder(int elem) {
		int index;
		int ord = 1;
		
		index = (reverses ? elem+size : elem-1);
		while (mapping[index] != elem) {
			ord++;
			index = mapping[index] + (reverses ? size : -1);
		}
		
		return ord;
	}
	
	public int[] getCycle(int elem) {
		int ord = getOrder(elem);
		int[] result = new int[ord];
		int term = elem;
		
		for (int i = 0; i < ord; i++) {
			result[i] = term;
			term = mapping[(reverses ? term+size : term-1)];
		}
		return result;
	}
}