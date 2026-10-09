//
// BuiltinPatternLists.kt
//
// Metadata for built-in JML pattern lists packaged with Juggling Lab.
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.common

data class BuiltinPatternEntry(
    val filename: String,
    val displayName: String
)

object BuiltinPatternLists {
    val basic: List<BuiltinPatternEntry> = listOf(
        BuiltinPatternEntry("basic_how to.jml", "How to Juggle"),
        BuiltinPatternEntry("basic_solo.jml", "Solo Patterns"),
        BuiltinPatternEntry("basic_passing.jml", "Passing Patterns"),
        BuiltinPatternEntry("basic_siteswaps.jml", "Common Siteswaps"),
    )

    val other: List<BuiltinPatternEntry> = listOf(
        BuiltinPatternEntry("Alanz_3BallBounce V 2Edit.jml", "Alan's 3 Ball Bounce"),
        BuiltinPatternEntry("Alanz_Multiplex etcetera.jml", "Alan's Multiplex Etcetera"),
        BuiltinPatternEntry("Alanz_Some Patterns Without 3's.jml", "Alan's Patterns Without 3's"),
        BuiltinPatternEntry("Alanz_Synchronous Favorites.jml", "Alan's Synchronous Favorites"),
        BuiltinPatternEntry("Roeland_7-Cascade Step by Step.jml", "Roeland's 7-Cascade Step by Step"),
        BuiltinPatternEntry("Roeland_Rolling Patterns.jml", "Roeland's Rolling Patterns"),
        BuiltinPatternEntry("hss_2JugglersAsymmetric.jml", "HSS: 2 Jugglers Asymmetric"),
        BuiltinPatternEntry("hss_2JugglersSymmetric.jml", "HSS: 2 Jugglers Symmetric"),
        BuiltinPatternEntry("hss_2UnequalPassers.jml", "HSS: 2 Unequal Passers"),
        BuiltinPatternEntry("hss_3JugglersAsymmetric.jml", "HSS: 3 Jugglers Asymmetric"),
        BuiltinPatternEntry("hss_3JugglersSymmetric.jml", "HSS: 3 Jugglers Symmetric"),
        BuiltinPatternEntry("hss_PrechacWeaves.jml", "HSS: Prechac Weaves"),
        BuiltinPatternEntry("hss_TwoHandedPatterns.jml", "HSS: Two Handed Patterns"),
        BuiltinPatternEntry("Are you God.jml", "Are you God?"),
        BuiltinPatternEntry("Omnikrabundi_FunWithJugglingLab.jml", "Fun with Juggling Lab"),
        BuiltinPatternEntry("arham_stupid jugging lab patterns.jml", "Arham: Stupid Juggling Lab Patterns"),
        BuiltinPatternEntry("jboyce_Juggling Lab demo.jml", "Juggling Lab Demo"),
    )
}
