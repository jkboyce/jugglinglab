//
// LadderDiagramControllerTest.kt
//
// Unit tests for LadderDiagramController.
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.common

import androidx.compose.ui.geometry.Offset
import org.jugglinglab.core.AnimationPrefs
import org.jugglinglab.core.PatternAnimationState
import org.jugglinglab.notation.SiteswapPattern
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LadderDiagramControllerTest {
    @Test
    fun testDragEventInHighTempoPattern() {
        val sp = SiteswapPattern().fromString("{100}{100}{100}{100}{100}")
        val pat = sp.asJmlPattern()
        val state = PatternAnimationState(pat, AnimationPrefs())

        var errorReported: Throwable? = null
        val controller = LadderDiagramController(
            state = state,
            onMakePopup = null,
            onError = { errorReported = it }
        )

        val widthPx = 300
        val heightPx = 600
        val density = 1.0f
        val layout = LadderDiagramLayout(state.pattern, widthPx, heightPx, density)
        controller.onLayoutUpdate(layout)

        val eventItems = layout.eventItems.filter { it.type == LadderItem.TYPE_EVENT }
        assertTrue(eventItems.isNotEmpty())

        for (item in eventItems) {
            val cx = 0.5f * (item.xLow + item.xHigh)
            val cy = 0.5f * (item.yLow + item.yHigh)
            val startOffset = Offset(cx, cy)

        val pressed = controller.handlePress(
            offset = startOffset,
            isPopup = false
        )
            if (!pressed) continue

            // Drag event up and down over adjacent events
            for (dy in -60..60 step 2) {
                controller.handleDrag(Offset(cx, cy + dy))
                val newLayout = LadderDiagramLayout(state.pattern, widthPx, heightPx, density)
                controller.onLayoutUpdate(newLayout)

                // Layout evaluation must never fail validation
                state.pattern.layout
            }

            controller.handleRelease()
        }

        assertEquals(null, errorReported, "Unexpected error reported during drag: $errorReported")
    }

    @Test
    fun testDragEventInStandardCascade() {
        val sp = SiteswapPattern().fromString("3")
        val pat = sp.asJmlPattern()
        val state = PatternAnimationState(pat, AnimationPrefs())

        var errorReported: Throwable? = null
        val controller = LadderDiagramController(
            state = state,
            onMakePopup = null,
            onError = { errorReported = it }
        )

        val widthPx = 300
        val heightPx = 600
        val density = 1.0f
        val layout = LadderDiagramLayout(state.pattern, widthPx, heightPx, density)
        controller.onLayoutUpdate(layout)

        val eventItems = layout.eventItems.filter { it.type == LadderItem.TYPE_EVENT }
        assertTrue(eventItems.isNotEmpty())

        val item = eventItems.first()
        val cx = 0.5f * (item.xLow + item.xHigh)
        val cy = 0.5f * (item.yLow + item.yHigh)
        val startOffset = Offset(cx, cy)

        val pressed = controller.handlePress(
            offset = startOffset,
            isPopup = false
        )
        assertTrue(pressed)

        for (dy in -100..100 step 5) {
            controller.handleDrag(Offset(cx, cy + dy))
            val newLayout = LadderDiagramLayout(state.pattern, widthPx, heightPx, density)
            controller.onLayoutUpdate(newLayout)
            state.pattern.layout
        }

        controller.handleRelease()
        assertEquals(null, errorReported, "Unexpected error reported during drag: $errorReported")
    }

    @Test
    fun testDragPositionMaintainsValidity() {
        // Pattern with juggler positions
        val basePat = SiteswapPattern().fromString("3").asJmlPattern()
        val pat = basePat.copy(
            positions = listOf(
                org.jugglinglab.jml.JmlPosition(t = 0.1, juggler = 1),
                org.jugglinglab.jml.JmlPosition(t = 0.5, juggler = 1)
            )
        )
        val state = PatternAnimationState(pat, AnimationPrefs())

        var errorReported: Throwable? = null
        val controller = LadderDiagramController(
            state = state,
            onMakePopup = null,
            onError = { errorReported = it }
        )

        val widthPx = 300
        val heightPx = 600
        val density = 1.0f
        val layout = LadderDiagramLayout(state.pattern, widthPx, heightPx, density)
        controller.onLayoutUpdate(layout)

        val posItems = layout.positionItems
        assertTrue(posItems.isNotEmpty())

        val item = posItems.first()
        val cx = 0.5f * (item.xLow + item.xHigh)
        val cy = 0.5f * (item.yLow + item.yHigh)
        val startOffset = Offset(cx, cy)

        val pressed = controller.handlePress(
            offset = startOffset,
            isPopup = false
        )
        assertTrue(pressed)

        for (dy in -50..50 step 5) {
            controller.handleDrag(Offset(cx, cy + dy))
            val newLayout = LadderDiagramLayout(state.pattern, widthPx, heightPx, density)
            controller.onLayoutUpdate(newLayout)
            state.pattern.layout
        }

        controller.handleRelease()
        assertEquals(null, errorReported, "Unexpected error reported during drag: $errorReported")
    }
}
