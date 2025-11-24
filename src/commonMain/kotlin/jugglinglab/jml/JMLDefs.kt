//
// JMLDefs.java
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

object JMLDefs {
    const val CURRENT_JML_VERSION: String = "3"

    const val JML_DTD: String = ("<!ELEMENT jml (pattern|patternlist)>\n"
            + "<!ATTLIST jml\n"
            + "          version  CDATA   \""
            + CURRENT_JML_VERSION
            + "\">\n"
            + "\n"
            + "<!ELEMENT pattern"
            + " (title?,info?,basepattern?,prop*,setup,symmetry+,(event|position)*)>\n"
            + "\n"
            + "<!ELEMENT title (#PCDATA)>\n"
            + "\n"
            + "<!ELEMENT info (#PCDATA)>\n"
            + "<!ATTLIST info\n"
            + "          tags     CDATA   #IMPLIED>\n"
            + "\n"
            + "<!ELEMENT basepattern (#PCDATA)>\n"
            + "<!ATTLIST basepattern\n"
            + "          notation CDATA   #REQUIRED>\n"
            + "\n"
            + "<!ELEMENT prop EMPTY>\n"
            + "<!ATTLIST prop\n"
            + "          type     CDATA   \"ball\"\n"
            + "          mod      CDATA   #IMPLIED>\n"
            + "\n"
            + "<!ELEMENT setup EMPTY>\n"
            + "<!ATTLIST setup\n"
            + "          jugglers CDATA   \"1\"\n"
            + "          paths    CDATA   #REQUIRED\n"
            + "          props    CDATA   #IMPLIED>\n"
            + "\n"
            + "<!ELEMENT symmetry EMPTY>\n"
            + "<!ATTLIST symmetry\n"
            + "          type     CDATA   #REQUIRED\n"
            + "          jperm    CDATA   #IMPLIED\n"
            + "          pperm    CDATA   #REQUIRED\n"
            + "          delay    CDATA   #IMPLIED>\n"
            + "\n"
            + "<!ELEMENT event (throw|catch|softcatch|holding)*>\n"
            + "<!ATTLIST event\n"
            + "          x        CDATA   #REQUIRED\n"
            + "          y        CDATA   \"0.0\"\n"
            + "          z        CDATA   \"0.0\"\n"
            + "          t        CDATA   #REQUIRED\n"
            + "          hand     CDATA   #REQUIRED>\n"
            + "\n"
            + "<!ELEMENT throw EMPTY>\n"
            + "<!ATTLIST throw\n"
            + "          path     CDATA   #REQUIRED\n"
            + "          type     CDATA   \"toss\"\n"
            + "          mod      CDATA   #IMPLIED>\n"
            + "\n"
            + "<!ELEMENT catch EMPTY>\n"
            + "<!ATTLIST catch\n"
            + "          path     CDATA   #REQUIRED\n"
            + "          type     CDATA   \"natural\">\n"
            + "\n"
            + "<!ELEMENT holding EMPTY>\n"
            + "<!ATTLIST holding\n"
            + "          path     CDATA   #REQUIRED>\n"
            + "\n"
            + "<!ELEMENT position EMPTY>\n"
            + "<!ATTLIST position\n"
            + "          x        CDATA   #REQUIRED\n"
            + "          y        CDATA   #REQUIRED\n"
            + "          z        CDATA   \"100.0\"\n"
            + "          t        CDATA   #REQUIRED\n"
            + "          angle    CDATA   \"0.0\"\n"
            + "          juggler  CDATA   \"1\">\n"
            + "\n"
            + "<!ELEMENT patternlist (title?,info?,line*)>\n"
            + "\n"
            + "<!ELEMENT line (#PCDATA|pattern|info)*>\n"
            + "<!ATTLIST line\n"
            + "          display    CDATA   #REQUIRED\n"
            + "          animprefs  CDATA   #IMPLIED\n"
            + "          notation   CDATA   #IMPLIED>\n")

    val jmlprefix: List<String> = listOf(
        "<?xml version=\"1.0\"?>",
        "<!DOCTYPE jml SYSTEM \"file://jml.dtd\">",
    )

    val jmlsuffix: List<String> = listOf()

    @Suppress("unused")
    val taglist: List<String> = listOf(
        "jml",
        "pattern",
        "patternlist",
        "title",
        "info",
        "basepattern",
        "prop",
        "setup",
        "symmetry",
        "event",
        "throw",
        "catch",
        "softcatch",
        "line",
        "holding",
        "position",
    )
}
