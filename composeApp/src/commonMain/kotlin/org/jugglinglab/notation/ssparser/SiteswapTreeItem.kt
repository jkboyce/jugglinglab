//
// SiteswapTreeItem.kt
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.notation.ssparser

class SiteswapTreeItem(var type: Int) : Cloneable {
    var children: MutableList<SiteswapTreeItem> = mutableListOf()

    // variables that the parser determines:
    var jugglers: Int = 0 // for type 1, 7, 8
    var repeats: Int = 0 // for type 2
    var switchrepeat: Boolean = false // for type 1
    var beats: Int = 0 // for types 3, 7, 8, 9, 13
    var seqBeatnum: Int = 0 // for types 4, 5, 6, 8, 9, 10, 11, 12, 14
    var sourceJuggler: Int = 0 // for types 3, 4, 5, 6, 9, 10, 11, 12, 14
    var value: Int = 0 // for types 6, 12
    var x: Boolean = false // for types 6, 12
    var destJuggler: Int = 0 // for types 6, 12      // Note: can be > # jugglers -> mod down into range
    var mod: String? = null // for types 6, 12
    var specLeft: Boolean = false // for type 14

    // variables determined by subsequent layout stages:
    var throwSum: Int = 0
    var beatNum: Int = 0
    var left: Boolean = false
    var isVanillaAsync: Boolean = false
    var isSyncThrow: Boolean = false
    var transition: SiteswapTreeItem? = null // used only for Wildcard type -- holds the calculated transition sequence


    fun addChild(item: SiteswapTreeItem?) {
        children.add(item!!)
    }

    fun getChild(index: Int): SiteswapTreeItem {
        return children[index]
    }

    fun removeChildren() {
        children = mutableListOf()
    }

    val numberOfChildren: Int
        get() = children.size

    public override fun clone(): Any {
        val result = SiteswapTreeItem(type)
        result.repeats = repeats
        result.switchrepeat = switchrepeat
        result.beats = beats
        result.seqBeatnum = seqBeatnum
        result.sourceJuggler = sourceJuggler
        result.value = value
        result.x = x
        result.destJuggler = destJuggler
        result.mod = mod
        result.specLeft = specLeft
        for (i in 0..<numberOfChildren) {
            result.addChild((getChild(i).clone()) as SiteswapTreeItem?)
        }
        return result
    }

    override fun toString(): String {
        return toString(0)
    }

    private fun toString(indentlevel: Int): String {
        var result = ""
        repeat (indentlevel) {
            result += "  "
        }
        result += typenames[type - 1] + "("
        if (isFieldActive(0, type)) result += "jugglers=$jugglers, "
        if (isFieldActive(1, type)) result += "repeats=$repeats, "
        if (isFieldActive(2, type)) result += "*=$switchrepeat, "
        if (isFieldActive(3, type)) result += "beats=$beats, "
        if (isFieldActive(4, type)) result += "seq_beatnum=$seqBeatnum, "
        if (isFieldActive(5, type)) result += "fromj=$sourceJuggler, "
        if (isFieldActive(6, type)) result += "val=$value, "
        if (isFieldActive(7, type)) result += "x=$x, "
        if (isFieldActive(8, type)) result += "toj=$destJuggler, "
        if (isFieldActive(9, type)) result += "mod=$mod, "
        if (isFieldActive(10, type)) result += "spec_left=$specLeft"
        result += ") {\n"

        for (i in 0..<numberOfChildren) {
            result += getChild(i).toString(indentlevel + 1)
        }

        repeat (indentlevel) {
            result += "  "
        }
        result += "}\n"
        return result
    }

    companion object {
        const val TYPE_PATTERN: Int = 1
        const val TYPE_GROUPED_PATTERN: Int = 2
        const val TYPE_SOLO_SEQUENCE: Int = 3
        const val TYPE_SOLO_PAIRED_THROW: Int = 4
        const val TYPE_SOLO_MULTI_THROW: Int = 5
        const val TYPE_SOLO_SINGLE_THROW: Int = 6
        const val TYPE_PASSING_SEQUENCE: Int = 7
        const val TYPE_PASSING_GROUP: Int = 8
        const val TYPE_PASSING_THROWS: Int = 9
        const val TYPE_PASSING_PAIRED_THROW: Int = 10
        const val TYPE_PASSING_MULTI_THROW: Int = 11
        const val TYPE_PASSING_SINGLE_THROW: Int = 12
        const val TYPE_WILDCARD: Int = 13
        const val TYPE_HAND_SPEC: Int = 14

        private val typenames = arrayOf<String?>(
            "Pattern",
            "Grouped Pattern",
            "Solo Sequence",
            "Solo Paired Throw",
            "Solo Multi Throw",
            "Solo Single Throw",
            "Passing Sequence",
            "Passing Group",
            "Passing Throws",
            "Passing Paired Throw",
            "Passing Multi Throw",
            "Passing Single Throw",
            "Wildcard",
            "Hand Specifier",
        )

        // The following codifies the "for types" comments above
        private val fieldDefinedTypes = arrayOf(
            intArrayOf(1, 7, 8),
            intArrayOf(2),
            intArrayOf(1),
            intArrayOf(3, 7, 8, 9, 13),
            intArrayOf(4, 5, 6, 8, 9, 10, 11, 12, 14),
            intArrayOf(3, 4, 5, 6, 9, 10, 11, 12, 14),
            intArrayOf(6, 12),
            intArrayOf(6, 12),
            intArrayOf(6, 12),
            intArrayOf(6, 12),
            intArrayOf(14),
        )

        private fun isFieldActive(fieldnum: Int, type: Int): Boolean {
            return (type in fieldDefinedTypes[fieldnum])
        }
    }
}
