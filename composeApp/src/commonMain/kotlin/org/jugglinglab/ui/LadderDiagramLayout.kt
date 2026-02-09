//
// LadderDiagramLayout.kt
//
// This class calculates and holds the layout data for the ladder diagram.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.util.jlGetStringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.sp
import org.jugglinglab.jml.JmlEvent
import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.jml.JmlPosition
import org.jugglinglab.jml.JmlSymmetry
import org.jugglinglab.jml.JmlTransition
import kotlin.math.*

class LadderDiagramLayout(
    val pattern: JmlPattern,
    val width: Int,
    val height: Int,
    val density: Float
) {
    var eventItems: List<LadderEventItem> = emptyList()
    var pathItems: List<LadderPathItem> = emptyList()
    var positionItems: List<LadderPositionItem> = emptyList()

    // Layout metrics
    var leftX: Int = 0
    var rightX: Int = 0
    var jugglerDeltaX: Int = 0

    val borderTop = (BORDER_TOP_DP * density).toInt()
    val transitionRadius = (TRANSITION_RADIUS_DP * density).toInt()
    val positionRadius = (POSITION_RADIUS_DP * density).toInt()
    val lineWidth = (LINE_WIDTH_DP * density)
    val dashOn = (DASH_ON_DP * density)
    val dashOff = (DASH_OFF_DP * density)

    val hasSwitchSymmetry: Boolean = pattern.symmetries.any { it.type == JmlSymmetry.Companion.TYPE_SWITCH }
    val hasSwitchDelaySymmetry: Boolean = pattern.symmetries.any { it.type == JmlSymmetry.Companion.TYPE_SWITCHDELAY }

    init {
        calculateLayout()
    }

    private fun calculateLayout() {
        val loopStart = pattern.loopStartTime
        val loopEnd = pattern.loopEndTime

        // 1. Create items (Logic from createLadderView)

        // Create events
        eventItems = buildList {
            for (ei in pattern.loopEvents) {
                val item = LadderEventItem(
                    event = ei.event,
                    primary = ei.primary
                )
                item.type = LadderItem.TYPE_EVENT
                item.transEventItem = item
                add(item)

                for (index in ei.event.transitions.indices) {
                    add(
                        LadderEventItem(
                            event = ei.event,
                            primary = ei.primary
                        ).apply {
                            type = LadderItem.TYPE_TRANSITION
                            transEventItem = item
                            transNum = index
                        })
                }
            }
        }

        // Create paths
        pathItems = buildList {
            for ((indexEv, ei) in pattern.allEvents.withIndex()) {
                for ((indexTr, tr) in ei.event.transitions.withIndex()) {
                    val endEi = pattern.allEvents
                        .asSequence()
                        .drop(indexEv + 1)
                        .firstOrNull { it.event.transitions.any { tr2 -> tr2.path == tr.path } }
                        ?: continue

                    add(
                        LadderPathItem(
                            startEvent = ei.event,
                            endEvent = endEi.event,
                        ).apply {
                            transnumStart = indexTr
                            type = if (tr.type != JmlTransition.Companion.TRANS_THROW) {
                                LadderItem.TYPE_HOLD
                            } else if (ei.event.juggler != endEvent.juggler) {
                                LadderItem.TYPE_PASS
                            } else if (ei.event.hand == endEvent.hand) {
                                LadderItem.TYPE_SELF
                            } else {
                                LadderItem.TYPE_CROSS
                            }
                            pathNum = tr.path
                        })
                }
            }
        }

        // Create positions
        positionItems = buildList {
            for (pos in pattern.positions) {
                if (pos.t in loopStart..<loopEnd) {
                    val item = LadderPositionItem(pos)
                    item.type = LadderItem.TYPE_POSITION
                    add(item)
                }
            }
        }

        // 2. Map to screen

        // distance between left/right hands of each juggler is 1.0 in these
        // dimensionless units; translate to pixels
        val scale: Double = width.toDouble() / (BORDER_SIDES * 2 +
                JUGGLER_SEPARATION * (pattern.numberOfJugglers - 1) +
                pattern.numberOfJugglers)
        leftX = (scale * BORDER_SIDES).roundToInt()
        rightX = (scale * (BORDER_SIDES + 1.0)).roundToInt()
        jugglerDeltaX = (scale * (1.0 + JUGGLER_SEPARATION)).roundToInt()

        // Set locations of events
        for (item in eventItems) {
            val ev = item.event
            var eventX: Int =
                ((if (ev.hand == JmlEvent.Companion.LEFT_HAND) leftX else rightX)
                        + (ev.juggler - 1) * jugglerDeltaX
                        - transitionRadius)
            val eventY: Int = timeToY(ev.t) - transitionRadius

            if (item.type != LadderItem.TYPE_EVENT) {
                if (ev.hand == JmlEvent.Companion.LEFT_HAND) {
                    eventX += 2 * transitionRadius * (item.transNum + 1)
                } else {
                    eventX -= 2 * transitionRadius * (item.transNum + 1)
                }
            }
            item.xLow = eventX
            item.xHigh = eventX + 2 * transitionRadius
            item.yLow = eventY
            item.yHigh = eventY + 2 * transitionRadius
        }

        // Set locations of paths
        for (item in pathItems) {
            item.xStart =
                ((if (item.startEvent.hand == JmlEvent.Companion.LEFT_HAND)
                    (leftX + (item.transnumStart + 1) * 2 * transitionRadius)
                else
                    (rightX - (item.transnumStart + 1) * 2 * transitionRadius))
                        + (item.startEvent.juggler - 1) * jugglerDeltaX)
            item.yStart = timeToY(item.startEvent.t)
            item.yEnd = timeToY(item.endEvent.t)

            val slot = item.endEvent.transitions.indexOfFirst { it.path == item.pathNum }
            if (slot != -1) {
                item.xEnd = ((if (item.endEvent.hand == JmlEvent.Companion.LEFT_HAND)
                    (leftX + (slot + 1) * 2 * transitionRadius)
                else
                    (rightX - (slot + 1) * 2 * transitionRadius))
                        + (item.endEvent.juggler - 1) * jugglerDeltaX)

                if (item.type == LadderItem.TYPE_SELF) {
                    val a = 0.5 * sqrt(
                        ((item.xStart - item.xEnd) * (item.xStart - item.xEnd)).toDouble() +
                                ((item.yStart - item.yEnd) * (item.yStart - item.yEnd)).toDouble()
                    )
                    val xt = 0.5 * (item.xStart + item.xEnd).toDouble()
                    val yt = 0.5 * (item.yStart + item.yEnd).toDouble()
                    val b: Double =
                        SELFTHROW_WIDTH * (width.toDouble() / pattern.numberOfJugglers)
                    var d = 0.5 * (a * a / b - b)
                    if (d < (0.5 * b)) {
                        d = 0.5 * b
                    }
                    val mult = if (item.endEvent.hand == JmlEvent.Companion.LEFT_HAND) -1.0 else 1.0
                    val xc = xt + mult * d * (yt - item.yStart.toDouble()) / a
                    val yc = yt + mult * d * (item.xStart.toDouble() - xt) / a
                    val rad = sqrt(
                        (item.xStart.toDouble() - xc) * (item.xStart.toDouble() - xc)
                                + (item.yStart.toDouble() - yc) * (item.yStart.toDouble() - yc)
                    )
                    item.xCenter = (0.5 + xc).toInt()
                    item.yCenter = (0.5 + yc).toInt()
                    item.radius = (0.5 + rad).toInt()
                }
            }
        }

        // Set locations of positions
        for (item in positionItems) {
            val pos = item.position
            val positionX: Int =
                (leftX + rightX) / 2 + (pos.juggler - 1) * jugglerDeltaX - positionRadius
            val positionY: Int = timeToY(pos.t) - positionRadius

            item.xLow = positionX
            item.xHigh = positionX + 2 * positionRadius
            item.yLow = positionY
            item.yHigh = positionY + 2 * positionRadius
        }
    }

    fun timeToY(time: Double): Int {
        val loopStart = pattern.loopStartTime
        val loopEnd = pattern.loopEndTime
        return ((0.5 + (height - 2 * borderTop).toDouble() *
                (time - loopStart) / (loopEnd - loopStart)).toInt() + borderTop)
    }

    fun yToTime(y: Int): Double {
        val loopStart = pattern.loopStartTime
        val loopEnd = pattern.loopEndTime
        val scale = (loopEnd - loopStart) / (height - 2 * borderTop).toDouble()
        return (y - borderTop).toDouble() * scale
    }

    companion object {
        // overall sizing
        const val MAX_JUGGLERS: Int = 8
        const val LADDER_WIDTH_PER_JUGGLER_DP: Int = 150
        const val LADDER_MIN_WIDTH_PER_JUGGLER_DP: Int = 60

        const val BORDER_TOP_DP: Int = 25
        const val TRANSITION_RADIUS_DP: Int = 5
        const val POSITION_RADIUS_DP: Int = 5
        const val LINE_WIDTH_DP: Float = 1.125f
        const val DASH_ON_DP: Float = 6.0f
        const val DASH_OFF_DP: Float = 4.0f
        const val BORDER_SIDES: Double = 0.15
        const val JUGGLER_SEPARATION: Double = 0.45
        const val SELFTHROW_WIDTH: Double = 0.25

        // Return preferred width of the overall panel, in logical pixels.

        fun getPreferredWidthDp(jugglers: Int, textMeasurer: TextMeasurer): Int {
            if (jugglers > MAX_JUGGLERS) {
                // allocate enough space for a "too many jugglers" message
                val text = jlGetStringResource(Res.string.gui_too_many_jugglers, MAX_JUGGLERS)
                val textLayoutResult = textMeasurer.measure(
                    text = text,
                    style = TextStyle(color = Color.Black, fontSize = 13.sp)
                )
                // Convert measured pixels to DP
                val messageWidth = 20 + (textLayoutResult.size.width / textLayoutResult.layoutInput.density.density).toInt()
                return messageWidth
            } else {
                var prefWidth: Int = LADDER_WIDTH_PER_JUGGLER_DP * jugglers
                val minWidth: Int = LADDER_MIN_WIDTH_PER_JUGGLER_DP * jugglers
                val widthMult = doubleArrayOf(1.0, 1.0, 0.85, 0.72, 0.65, 0.55)
                prefWidth = (prefWidth.toDouble() *
                        (if (jugglers >= widthMult.size) 0.5 else widthMult[jugglers])).toInt()
                return max(prefWidth, minWidth)
            }
        }
    }
}

// Classes to hold the items in the view

open class LadderItem {
    var type: Int = 0
    open val jlHashCode: Int = 0

    companion object {
        const val TYPE_EVENT: Int = 0
        const val TYPE_TRANSITION: Int = 1
        const val TYPE_SELF: Int = 2
        const val TYPE_CROSS: Int = 3
        const val TYPE_HOLD: Int = 4
        const val TYPE_PASS: Int = 5
        const val TYPE_POSITION: Int = 6
    }
}

class LadderEventItem(
    val event: JmlEvent,
    val primary: JmlEvent
) : LadderItem() {
    var xLow: Int = 0
    var xHigh: Int = 0
    var yLow: Int = 0
    var yHigh: Int = 0
    var transEventItem: LadderEventItem? = null
    var transNum: Int = 0

    override val jlHashCode: Int
        get() = event.jlHashCode + type * 23 + transNum * 27
}

class LadderPathItem(
    val startEvent: JmlEvent,
    val endEvent: JmlEvent,
) : LadderItem() {
    var xStart: Int = 0
    var yStart: Int = 0
    var xEnd: Int = 0
    var yEnd: Int = 0
    var xCenter: Int = 0
    var yCenter: Int = 0
    var radius: Int = 0
    var transnumStart: Int = 0
    var pathNum: Int = 0
}

class LadderPositionItem(
    val position: JmlPosition
) : LadderItem() {
    var xLow: Int = 0
    var xHigh: Int = 0
    var yLow: Int = 0
    var yHigh: Int = 0

    override val jlHashCode: Int
        get() = position.jlHashCode
}
