//
// SimpleView.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.view

import jugglinglab.core.AnimationPanel
import jugglinglab.core.AnimationPrefs
import jugglinglab.jml.JMLPattern
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File

class SimpleView(dim: Dimension?) : View() {
    private val ja: AnimationPanel = AnimationPanel()

    init {
        ja.preferredSize = dim
        ja.minimumSize = Dimension(10, 10)
        setLayout(BorderLayout())
        add(ja, BorderLayout.CENTER)
    }

    //--------------------------------------------------------------------------
    // View methods
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    override fun restartView(p: JMLPattern?, c: AnimationPrefs?) {
        ja.restartJuggle(p, c)
        setAnimationPanelPreferredSize(
            Dimension(animationPrefs.size.width, animationPrefs.size.height))
        if (p != null) {
            patternWindow?.setTitle(p.title)
            patternWindow?.updateColorsMenu()
        }
    }

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    override fun restartView() {
        ja.restartJuggle()
    }

    override val animationPanelSize: Dimension?
        get() = ja.getSize(Dimension())

    override fun setAnimationPanelPreferredSize(d: Dimension) {
        ja.preferredSize = d
    }

    override val pattern: JMLPattern?
        get() = ja.pattern

    override val animationPrefs: AnimationPrefs
        get() = ja.animationPrefs

    override var zoomLevel: Double
        get() = ja.zoomLevel
        set(z) {
            ja.zoomLevel = z
        }

    override var isPaused: Boolean
        get() = ja.isPaused
        set(pause) {
            if (ja.message == null) {
                ja.isPaused = pause
            }
        }

    override fun disposeView() {
        ja.disposeAnimation()
    }

    override fun writeGIF(f: File) {
        ja.writingGIF = true
        val origpause = isPaused
        isPaused = true
        patternWindow?.setResizable(false)

        val cleanup =
            Runnable {
                ja.writingGIF = false
                isPaused = origpause
                patternWindow?.setResizable(true)
            }

        GIFWriter(ja, f, cleanup)
    }
}
