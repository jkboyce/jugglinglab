//
// AnimationLayout.kt
//
// Data class to hold the selection view data structures. These are calculated by
// AnimationPanel and passed to AnimationView for rendering, and also used by
// AnimationPanel to interpret mouse events.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.ui

data class AnimationLayout(
    // Screen coordinates of visual representations for events.
    // [event index][stereo view 0/1][control point index][x/y]
    val eventPoints: Array<Array<Array<DoubleArray>>> = Array(0) { Array(0) { Array(0) { DoubleArray(0) } } },
    
    // Screen coordinates for hand paths.
    // [stereo view 0/1][point index][x/y]
    val handpathPoints: Array<Array<DoubleArray>> = Array(0) { Array(0) { DoubleArray(0) } },
    
    // Whether each hand path segment is a "hold" (solid line) or not (dashed line).
    val handpathHold: BooleanArray = BooleanArray(0),
    
    // Screen coordinates for position editing controls.
    // [stereo view 0/1][control point index][x/y]
    val posPoints: Array<Array<DoubleArray>> = Array(0) { Array(0) { DoubleArray(0) } },

    // Visibility flags for drag controls
    val showXzDragControl: Boolean = false,
    val showYDragControl: Boolean = false,
    val showXyDragControl: Boolean = false,
    val showZDragControl: Boolean = false,
    val showAngleDragControl: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AnimationLayout

        if (!eventPoints.contentDeepEquals(other.eventPoints)) return false
        if (!handpathPoints.contentDeepEquals(other.handpathPoints)) return false
        if (!handpathHold.contentEquals(other.handpathHold)) return false
        if (!posPoints.contentDeepEquals(other.posPoints)) return false
        if (showXzDragControl != other.showXzDragControl) return false
        if (showYDragControl != other.showYDragControl) return false
        if (showXyDragControl != other.showXyDragControl) return false
        if (showZDragControl != other.showZDragControl) return false
        if (showAngleDragControl != other.showAngleDragControl) return false

        return true
    }

    override fun hashCode(): Int {
        var result = eventPoints.contentDeepHashCode()
        result = 31 * result + handpathPoints.contentDeepHashCode()
        result = 31 * result + handpathHold.contentHashCode()
        result = 31 * result + posPoints.contentDeepHashCode()
        result = 31 * result + showXzDragControl.hashCode()
        result = 31 * result + showYDragControl.hashCode()
        result = 31 * result + showXyDragControl.hashCode()
        result = 31 * result + showZDragControl.hashCode()
        result = 31 * result + showAngleDragControl.hashCode()
        return result
    }
}
