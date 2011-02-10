// JMLDefs.java
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

package jugglinglab.jml;


public class JMLDefs {
	public static final String default_JML_on_save = "1.2";

	public static final String default_JML_on_load = "1.0";
	
	public static final String jmldtd =
"<!ELEMENT jml (pattern|patternlist)>\n" +
"<!ATTLIST jml\n" +
"          version  CDATA   \"1.0\">\n" +
"\n" +
"<!ELEMENT pattern (title?,prop*,setup,symmetry+,(event|position)*)>\n" +
"\n" +
"<!ELEMENT title (#PCDATA)>\n" +
"\n" +
"<!ELEMENT prop EMPTY>\n" +
"<!ATTLIST prop\n" +
"          type     CDATA   \"ball\"\n" +
"          mod      CDATA   #IMPLIED>\n" +
"\n" +
"<!ELEMENT setup EMPTY>\n" +
"<!ATTLIST setup\n" +
"          jugglers CDATA   \"1\"\n" +
"          paths    CDATA   #REQUIRED\n" +
"          props    CDATA   #IMPLIED>\n" +
"\n" +
"<!ELEMENT symmetry EMPTY>\n" +
"<!ATTLIST symmetry\n" +
"          type     CDATA   #REQUIRED\n" +
"          jperm    CDATA   #IMPLIED\n" +
"          pperm    CDATA   #REQUIRED\n" +
"          delay    CDATA   #IMPLIED>\n" +
"\n" +
"<!ELEMENT event (throw|catch|softcatch|holding)*>\n" +
"<!ATTLIST event\n" +
"          x        CDATA   #REQUIRED\n" +
"          y        CDATA   \"0.0\"\n" +
"          z        CDATA   \"0.0\"\n" +
"          t        CDATA   #REQUIRED\n" +
"          hand     CDATA   #REQUIRED>\n" +
"\n" +
"<!ELEMENT throw EMPTY>\n" +
"<!ATTLIST throw\n" +
"          path     CDATA   #REQUIRED\n" +
"          type     CDATA   \"toss\"\n" +
"          mod      CDATA   #IMPLIED>\n" +
"\n" +
"<!ELEMENT catch EMPTY>\n" +
"<!ATTLIST catch\n" +
"          path     CDATA   #REQUIRED>\n" +
"\n" +
"<!ELEMENT softcatch EMPTY>\n" +
"<!ATTLIST softcatch\n" +
"          path     CDATA   #REQUIRED>\n" +
"\n" +
"<!ELEMENT holding EMPTY>\n" +
"<!ATTLIST holding\n" +
"          path     CDATA   #REQUIRED>\n" +
"\n" +
"<!ELEMENT position EMPTY>\n" +
"<!ATTLIST position\n" +
"          x        CDATA   #REQUIRED\n" +
"          y        CDATA   #REQUIRED\n" +
"          z        CDATA   \"100.0\"\n" +
"          t        CDATA   #REQUIRED\n" +
"          angle    CDATA   \"0.0\"\n" +
"          juggler  CDATA   \"1\">\n" +
"\n" +
"<!ELEMENT patternlist (title?,line*)>\n" +
"\n" +
"<!ELEMENT line (#PCDATA|pattern)*>\n"+
"<!ATTLIST line\n" +
"          display    CDATA   #REQUIRED\n" +
"          animprefs  CDATA   #IMPLIED\n" +
"          notation   CDATA   #IMPLIED>\n";



	public static final String[] jmlprefix = {
	    "<?xml version=\"1.0\"?>",
	    "<!DOCTYPE jml SYSTEM \"file://jml.dtd\">"
	};

	public static final String[] jmlsuffix = {
	};

	public static final String[] taglist = {
	    "jml", "pattern", "patternlist", "title", "prop", "setup",
		"symmetry", "event", "throw", "catch", "softcatch", "line",
		"holding", "position"
	};

}
