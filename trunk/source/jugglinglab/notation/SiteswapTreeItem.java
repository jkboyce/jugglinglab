// SiteswapTreeItem.java
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


public class SiteswapTreeItem {
	static final int TYPE_PATTERN = 1;
	static final int TYPE_GROUPED_PATTERN = 2;
	static final int TYPE_SOLO_SEQUENCE = 3;
	static final int TYPE_SOLO_PAIRED_THROW = 4;
	static final int TYPE_SOLO_MULTI_THROW = 5;
	static final int TYPE_SOLO_SINGLE_THROW = 6;
	static final int TYPE_PASSING_SEQUENCE = 7;
	static final int TYPE_PASSING_GROUP = 8;
	static final int TYPE_PASSING_THROWS = 9;
	static final int TYPE_PASSING_PAIRED_THROW = 10;
	static final int TYPE_PASSING_MULTI_THROW = 11;
	static final int TYPE_PASSING_SINGLE_THROW = 12;
	static final int TYPE_WILDCARD = 13;
	static final int TYPE_HAND_SPEC = 14;

	int type;
	Vector children;
	
	// variables that the parser determines:
	int jugglers;					// for type 1, 7, 8
	int repeats;					// for type 2
	boolean switchrepeat = false;   // for type 1
	int beats;						// for types 3, 7, 8, 9, 13
	int seq_beatnum;				// for types 4, 5, 6, 8, 9, 10, 11, 12, 14
	int source_juggler;				// for types 3, 4, 5, 6, 9, 10, 11, 12, 14
	int value;						// for types 6, 12
	boolean x = false;				// for types 6, 12
	int dest_juggler;				// for types 6, 12		// Note: can be > # jugglers -> mod down into range
	String mod;						// for types 6, 12
	boolean spec_left = false;		// for type 14
	
	// variables determined by subsequent layout stages:
	int throw_sum;
	int beatnum;
	boolean left;
	boolean vanilla_asynch;
	boolean synch_throw;
	SiteswapTreeItem transition;	// used only for Wildcard type -- holds the calculated transition sequence
	
	
	public SiteswapTreeItem(int type) {
		this.type = type;
		this.children = new Vector();
		this.synch_throw = false;
	}
	
	public void addChild(SiteswapTreeItem item) {
		children.addElement(item);
	}
	
	public SiteswapTreeItem getChild(int index) {
		return (SiteswapTreeItem)(children.elementAt(index));
	}
	
	public void removeChildren() {
		this.children = new Vector();
	}
	
	public int getNumberOfChildren() {
		return children.size();
	}
	
	public Object clone() {
		SiteswapTreeItem result = new SiteswapTreeItem(this.type);
		
		result.repeats = this.repeats;
		result.switchrepeat = this.switchrepeat;
		result.beats = this.beats;
		result.seq_beatnum = this.seq_beatnum;
		result.source_juggler = this.source_juggler;
		result.value = this.value;
		result.x = this.x;
		result.dest_juggler = this.dest_juggler;
		result.mod = this.mod;
		result.spec_left = this.spec_left;

		for (int i = 0; i < getNumberOfChildren(); i++)
			result.addChild((SiteswapTreeItem)(getChild(i).clone()));
			
		return result;
	}
	
	public String toString() {
		return toString(0);
	}


	private static final String[] typenames = { "Pattern", "Grouped Pattern",
		"Solo Sequence", "Solo Paired Throw", "Solo Multi Throw", "Solo Single Throw",
		"Passing Sequence", "Passing Group", "Passing Throws",
		"Passing Paired Throw", "Passing Multi Throw", "Passing Single Throw",
		"Wildcard", "Hand Specifier" };
	
	private String toString(int indentlevel) {
		String result = "";
		for (int i = 0; i < indentlevel; i++)
			result += "  ";
		result += typenames[type-1] + "(";
		if (field_active(0, type))
			result += "jugglers=" + jugglers + ", ";
		if (field_active(1, type))
			result += "repeats=" + repeats + ", ";
		if (field_active(2, type))
			result += "*=" + switchrepeat + ", ";
		if (field_active(3, type))
			result += "beats=" + beats + ", ";
		if (field_active(4, type))
			result += "seq_beatnum=" + seq_beatnum + ", ";
		if (field_active(5, type))
			result += "fromj=" + source_juggler + ", ";
		if (field_active(6, type))
			result += "val=" + value + ", ";
		if (field_active(7, type))
			result += "x=" + x + ", ";
		if (field_active(8, type))
			result += "toj=" + dest_juggler + ", ";
		if (field_active(9, type))
			result += "mod=" + mod + ", ";
		if (field_active(10, type))
			result += "spec_left=" + spec_left;
		result += ") {\n";
		
		for (int i = 0; i < getNumberOfChildren(); i++) {
			SiteswapTreeItem item = getChild(i);
			result += item.toString(indentlevel + 1);
		}
		
		for (int i = 0; i < indentlevel; i++)
			result += "  ";
		result += "}\n";
		return result;
	}
	
	// the following is just the "for types" comments above
	private static final int[][] field_defined_types = { {1, 7, 8}, {2}, {1},
		{3, 7, 8, 9, 13}, {4, 5, 6, 8, 9, 10, 11, 12, 14}, {3, 4, 5, 6, 9, 10, 11, 12, 14},
		{6, 12}, {6, 12}, {6, 12}, {6, 12}, {14} };
		
	private static boolean field_active(int fieldnum, int type) {
		int[] a = field_defined_types[fieldnum];
		for (int i = 0; i < a.length; i++) {
			if (a[i] == type)
				return true;
		}
		return false;
	}
}
