//
// SiteswapAstVisitor.kt
//
// Visitor for the ANTLR parser to generate SiteswapTreeItems.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.notation.ssparser

import org.jugglinglab.notation.ssparser.generated.JlSiteswapBaseVisitor
import org.jugglinglab.notation.ssparser.generated.JlSiteswapParser.*
import org.jugglinglab.util.JuggleExceptionUser
import org.antlr.v4.kotlinruntime.tree.TerminalNode

class SiteswapAstVisitor : JlSiteswapBaseVisitor<SiteswapTreeItem>() {
    // State variables
    private var jugglers = -1
    private var currentJuggler = 0
    private var currentBeat = 0
    private var currentBeatSub = 0

    // Helper to return a dummy item for rules we handle manually (like terminals)
    override fun defaultResult(): SiteswapTreeItem = SiteswapTreeItem(0)

    override fun visitPattern(ctx: PatternContext): SiteswapTreeItem {
        val b = SiteswapTreeItem(SiteswapTreeItem.TYPE_PATTERN)

        val children = ctx.children ?: emptyList()
        var i = 0
        while (i < children.size) {
            val child = children[i]

            // Handle Wildcards (merge consecutive '?' into one item)
            if (child is TerminalNode && child.symbol.type == Tokens.WILDCARD) {
                var beats = 1
                // Look ahead for more wildcards
                while (i + 1 < children.size) {
                    val next = children[i + 1]
                    if (next is TerminalNode && next.symbol.type == Tokens.WILDCARD) {
                        beats++
                        i++
                    } else {
                        break
                    }
                }
                val w = SiteswapTreeItem(SiteswapTreeItem.TYPE_WILDCARD)
                w.beats = beats
                b.addChild(w)
            }
            // Handle Switch Reverse '*'
            else if (child is TerminalNode && child.symbol.type == Tokens.SWITCHREVERSE) {
                b.switchrepeat = true
            }
            // Handle standard children (GroupedPattern, SoloSequence, PassingSequence)
            else {
                val res = child.accept(this)
                // Only add valid tree items (ignore terminals like whitespace)
                if (res.type != 0) {
                    b.addChild(res)
                }
            }
            i++
        }

        b.jugglers = jugglers
        return b
    }

    override fun visitGroupedpattern(ctx: GroupedpatternContext): SiteswapTreeItem {
        val b = SiteswapTreeItem(SiteswapTreeItem.TYPE_GROUPED_PATTERN)

        // Grammar: '(' pattern '^' number ')'
        val pattern = ctx.pattern().accept(this)
        b.addChild(pattern)

        val numberText = ctx.number().text
        b.repeats = numberText.toIntOrNull() ?: 1

        return b
    }

    override fun visitSolosequence(ctx: SolosequenceContext): SiteswapTreeItem {
        val b = SiteswapTreeItem(SiteswapTreeItem.TYPE_SOLO_SEQUENCE)
        currentJuggler = 1
        currentBeat = 0

        val children = ctx.children ?: emptyList()
        var i = 0
        while (i < children.size) {
            val child = children[i]

            if (child is SolomultithrowContext) {
                b.addChild(visitSolomultithrow(child))
                currentBeat++
            } else if (child is SolopairedthrowContext) {
                b.addChild(visitSolopairedthrow(child))
                currentBeat += 2

                // Check for optional '!' syncopation
                if (i + 1 < children.size) {
                    val next = children[i + 1]
                    if (next is TerminalNode && next.text == "!") {
                        currentBeat--
                    }
                }
            } else if (child is SolohandspecifierContext) {
                b.addChild(visitSolohandspecifier(child))
            }
            i++
        }

        if (jugglers == -1) {
            jugglers = 1
        } else if (jugglers != 1) {
            throw JuggleExceptionUser("Inconsistent number of jugglers")
        }

        b.sourceJuggler = 1
        b.beats = currentBeat
        return b
    }

    override fun visitSolopairedthrow(ctx: SolopairedthrowContext): SiteswapTreeItem {
        val b = SiteswapTreeItem(SiteswapTreeItem.TYPE_SOLO_PAIRED_THROW)
        // Grammar: '(' SPC* solomultithrow ',' SPC* solomultithrow ')'
        val multis = ctx.children?.filterIsInstance<SolomultithrowContext>() ?: emptyList()
        if (multis.size >= 2) {
            b.addChild(visitSolomultithrow(multis[0]))
            b.addChild(visitSolomultithrow(multis[1]))
        }
        b.sourceJuggler = 1
        b.seqBeatnum = currentBeat
        return b
    }

    fun visitSolomultithrow(ctx: SolomultithrowContext): SiteswapTreeItem {
        val b = SiteswapTreeItem(SiteswapTreeItem.TYPE_SOLO_MULTI_THROW)
        // Grammar: solosinglethrow | '[' solosinglethrow+ ']'
        ctx.children?.filterIsInstance<SolosinglethrowContext>()?.forEach { single ->
            b.addChild(visitSolosinglethrow(single))
        }
        b.sourceJuggler = 1
        b.seqBeatnum = currentBeat
        return b
    }

    override fun visitSolosinglethrow(ctx: SolosinglethrowContext): SiteswapTreeItem {
        val b = SiteswapTreeItem(SiteswapTreeItem.TYPE_SOLO_SINGLE_THROW)

        // Parse Value
        when (val tv = ctx.throwvalue()) {
            is BracevalueContext -> {
                val n = tv.number().text.toIntOrNull() ?: -1
                b.value = n
            }
            is DigitvalueContext -> {
                b.value = tv.text.toInt()
            }
            is LettervalueContext -> {
                b.value = tv.text[0].digitToInt(36)
            }
            is XvalueContext -> {
                b.value = 33 // 'x' as value
            }
            is PvalueContext -> {
                b.value = 25 // 'p' as value
            }
        }

        // Parse 'x' modifier (crossing)
        // Grammar: throwvalue 'x'? modifier? '/'?
        // We check children for the literal 'x' token
        ctx.children?.forEach { child ->
            if (child is TerminalNode && child.text == "x") {
                b.x = true
            }
        }

        // Parse Modifier
        val mod = ctx.modifier()?.text
        if (mod != null) {
            b.mod = mod
        }

        b.sourceJuggler = 1
        b.destJuggler = 1
        b.seqBeatnum = currentBeat
        return b
    }

    override fun visitSolohandspecifier(ctx: SolohandspecifierContext): SiteswapTreeItem {
        val b = SiteswapTreeItem(SiteswapTreeItem.TYPE_HAND_SPEC)
        b.specLeft = (ctx.text == "L")
        b.sourceJuggler = 1
        b.seqBeatnum = currentBeat
        return b
    }

    // --- Passing Rules ---

    override fun visitPassingsequence(ctx: PassingsequenceContext): SiteswapTreeItem {
        val b = SiteswapTreeItem(SiteswapTreeItem.TYPE_PASSING_SEQUENCE)
        currentBeat = 0

        ctx.children?.filterIsInstance<PassinggroupContext>()?.forEach { group ->
            val c = visitPassinggroup(group)
            b.jugglers = c.jugglers
            b.addChild(c)
        }

        b.beats = currentBeat
        return b
    }

    override fun visitPassinggroup(ctx: PassinggroupContext): SiteswapTreeItem {
        val b = SiteswapTreeItem(SiteswapTreeItem.TYPE_PASSING_GROUP)
        currentJuggler = 1

        // Grammar: '<' passingthrows ('|' passingthrows)* '>'
        val throwsList = ctx.children?.filterIsInstance<PassingthrowsContext>() ?: emptyList()

        if (throwsList.isNotEmpty()) {
            // First juggler
            val first = visitPassingthrows(throwsList[0])
            b.beats = first.beats
            b.addChild(first)
            currentJuggler++

            // Subsequent jugglers
            for (i in 1 until throwsList.size) {
                val next = visitPassingthrows(throwsList[i])
                if (next.beats != b.beats) {
                    throw JuggleExceptionUser("Inconsistent number of beats between jugglers")
                }
                b.addChild(next)
                currentJuggler++
            }
        }

        b.jugglers = currentJuggler - 1
        if (jugglers == -1) {
            jugglers = b.jugglers
        } else if (b.jugglers != jugglers) {
            throw JuggleExceptionUser("Inconsistent number of jugglers")
        }

        b.seqBeatnum = currentBeat
        currentBeat += b.beats
        return b
    }

    override fun visitPassingthrows(ctx: PassingthrowsContext): SiteswapTreeItem {
        val b = SiteswapTreeItem(SiteswapTreeItem.TYPE_PASSING_THROWS)
        currentBeatSub = 0

        val children = ctx.children ?: emptyList()
        var i = 0
        while (i < children.size) {
            val child = children[i]

            if (child is PassingmultithrowContext) {
                b.addChild(visitPassingmultithrow(child))
                currentBeatSub++
            } else if (child is PassingpairedthrowContext) {
                b.addChild(visitPassingpairedthrow(child))
                currentBeatSub += 2

                if (i + 1 < children.size) {
                    val next = children[i + 1]
                    if (next is TerminalNode && next.text == "!") {
                        currentBeatSub--
                    }
                }
            } else if (child is PassinghandspecifierContext) {
                b.addChild(visitPassinghandspecifier(child))
            }
            i++
        }

        b.sourceJuggler = currentJuggler
        b.beats = currentBeatSub
        b.seqBeatnum = currentBeat
        return b
    }

    override fun visitPassingpairedthrow(ctx: PassingpairedthrowContext): SiteswapTreeItem {
        val b = SiteswapTreeItem(SiteswapTreeItem.TYPE_PASSING_PAIRED_THROW)
        val multis = ctx.children?.filterIsInstance<PassingmultithrowContext>() ?: emptyList()
        if (multis.size >= 2) {
            b.addChild(visitPassingmultithrow(multis[0]))
            b.addChild(visitPassingmultithrow(multis[1]))
        }
        b.sourceJuggler = currentJuggler
        b.seqBeatnum = currentBeat + currentBeatSub
        return b
    }

    fun visitPassingmultithrow(ctx: PassingmultithrowContext): SiteswapTreeItem {
        val b = SiteswapTreeItem(SiteswapTreeItem.TYPE_PASSING_MULTI_THROW)
        ctx.children?.filterIsInstance<PassingsinglethrowContext>()?.forEach { single ->
            b.addChild(visitPassingsinglethrow(single))
        }
        b.sourceJuggler = currentJuggler
        b.seqBeatnum = currentBeat + currentBeatSub
        return b
    }

    override fun visitPassingsinglethrow(ctx: PassingsinglethrowContext): SiteswapTreeItem {
        val b = SiteswapTreeItem(SiteswapTreeItem.TYPE_PASSING_SINGLE_THROW)
        var dest = currentJuggler

        // Parse Value
        when (val tv = ctx.throwvalue()) {
            is BracevalueContext -> b.value = tv.number().text.toIntOrNull() ?: -1
            is DigitvalueContext -> b.value = tv.text.toInt()
            is LettervalueContext -> b.value = tv.text[0].digitToInt(36)
            is XvalueContext -> b.value = 33
            is PvalueContext -> b.value = 25
        }

        // Parse 'x' modifier
        ctx.children?.forEach { child ->
            if (child is TerminalNode && child.text == "x") {
                b.x = true
            }
        }

        // Parse 'p' target specifier
        // Grammar: ... ('p' number)? ...
        // We look for 'p' token in children
        val children = ctx.children ?: emptyList()
        for (i in children.indices) {
            val child = children[i]
            if (child is TerminalNode && child.text == "p") {
                dest = currentJuggler + 1
                // Check if next child is a number
                if (i + 1 < children.size) {
                    val next = children[i + 1]
                    // In ANTLR-Kotlin, we might need to check if next corresponds to the 'number' rule
                    // But since 'number' is a parser rule, it won't be a TerminalNode.
                    // We can check ctx.number()
                }
            }
        }

        // Explicit 'p' number override
        val pNum = ctx.number()
        if (pNum != null) {
            dest = pNum.text.toInt()
        }

        // Parse Modifier
        val mod = ctx.modifier()?.text
        if (mod != null) {
            b.mod = mod
        }

        b.sourceJuggler = currentJuggler
        b.destJuggler = dest
        b.seqBeatnum = currentBeat + currentBeatSub
        return b
    }

    override fun visitPassinghandspecifier(ctx: PassinghandspecifierContext): SiteswapTreeItem {
        val b = SiteswapTreeItem(SiteswapTreeItem.TYPE_HAND_SPEC)
        b.specLeft = (ctx.text == "L")
        b.sourceJuggler = currentJuggler
        b.seqBeatnum = currentBeat + currentBeatSub
        return b
    }
}
