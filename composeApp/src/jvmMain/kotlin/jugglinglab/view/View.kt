//
// View.kt
//
// This class represents the entire displayed contents of a PatternWindow.
// Subclasses of this are used to show different pattern views, which the
// user can select from a menu on the pattern window.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.view

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.core.AnimationPanel
import jugglinglab.core.AnimationPrefs
import jugglinglab.core.Animator.WriteGIFMonitor
import jugglinglab.core.PatternAnimationState
import jugglinglab.core.PatternWindow
import jugglinglab.jml.JMLPattern
import jugglinglab.util.jlHandleFatalException
import jugglinglab.util.jlHandleUserException
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.jlGetStringResource
import java.awt.Dimension
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.swing.JPanel
import javax.swing.ProgressMonitor
import javax.swing.SwingUtilities

abstract class View(
    val state: PatternAnimationState
) : JPanel() {
    var patternWindow: PatternWindow? = null

    protected var undo: MutableList<JMLPattern> = mutableListOf()

    var undoIndex: Int = 0
        protected set

    //--------------------------------------------------------------------------
    // Methods to handle undo/redo functionality.
    //
    // The enclosing PatternWindow owns the undo list, so it's preserved when
    // we switch views. All of the methods are here however in case we want to
    // embed the View in something other than a PatternWindow in the future.
    //--------------------------------------------------------------------------

    // For the PatternWindow to pass into a newly-initialized view.

    fun setUndoList(u: MutableList<JMLPattern>, uIndex: Int) {
        undo = u
        undoIndex = uIndex
    }

    // Add a pattern to the undo list.

    fun addToUndoList(p: JMLPattern) {
        try {
            ++undoIndex
            undo.add(undoIndex, p)
            while (undoIndex + 1 < undo.size) {
                undo.removeAt(undoIndex + 1)
            }
            patternWindow?.updateUndoMenu()
        } catch (jeu: JuggleExceptionUser) {
            // pattern was animated before so user error should not occur
            jlHandleFatalException(
                JuggleExceptionInternal(jeu.message ?: "", p)
            )
        } catch (jei: JuggleExceptionInternal) {
            jei.pattern = p
            jlHandleFatalException(jei)
        }
    }

    // Undo to the previous save state.

    @Throws(JuggleExceptionInternal::class)
    fun undoEdit() {
        if (undoIndex == 0)
            return
        try {
            --undoIndex
            restartView(undo[undoIndex], null)
            if (undoIndex == 0 || undoIndex == undo.size - 2) {
                patternWindow?.updateUndoMenu()
            }
        } catch (jeu: JuggleExceptionUser) {
            // pattern was animated before so user error should not occur
            throw JuggleExceptionInternal(jeu.message ?: "")
        }
    }

    // Redo to the next save state.

    @Throws(JuggleExceptionInternal::class)
    fun redoEdit() {
        if (undoIndex == undo.size - 1)
            return
        try {
            ++undoIndex
            restartView(undo[undoIndex], null)
            if (undoIndex == 1 || undoIndex == undo.size - 1) {
                patternWindow?.updateUndoMenu()
            }
        } catch (jeu: JuggleExceptionUser) {
            // pattern was animated before so user error should not occur
            throw JuggleExceptionInternal(jeu.message ?: "")
        }
    }

    //--------------------------------------------------------------------------
    // Abstract methods for subclasses to define
    //--------------------------------------------------------------------------

    // restart view with a new pattern and/or preferences
    //
    // note:
    // - a null argument means no update for that item
    // - this method is responsible for setting preferred sizes of all UI
    //   elements, since it may be followed by layout
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    abstract fun restartView(p: JMLPattern?, c: AnimationPrefs?)

    // restart without changing pattern or preferences
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    abstract fun restartView()

    // size of just the juggler animation, not any extra elements
    abstract val animationPanelSize: Dimension?

    abstract fun setAnimationPanelPreferredSize(d: Dimension)

    abstract fun disposeView()

    abstract fun writeGIF(f: File)

    //--------------------------------------------------------------------------
    // Utility class for the various View subclasses to use for writing GIFs.
    // This does the processing in a thread separate from the EDT.
    //--------------------------------------------------------------------------

    protected inner class GIFWriter(
        private val ap: AnimationPanel,
        private val file: File,
        private val cleanup: Runnable?
    ) : Thread() {
        private val pm =
            ProgressMonitor(
                patternWindow,
                jlGetStringResource(Res.string.gui_saving_animated_gif),
                "",
                0,
                1
            )
        private val wgm: WriteGIFMonitor
        private val fps: Double

        init {
            pm.millisToPopup = 200

            wgm = object : WriteGIFMonitor {
                override fun update(step: Int, stepsTotal: Int) {
                    SwingUtilities.invokeLater {
                        pm.setMaximum(stepsTotal)
                        pm.setProgress(step)
                    }
                }

                override val isCanceled: Boolean
                    get() = (pm.isCanceled() || interrupted())
            }

            val jc = ap.state.prefs
            fps = if (jc.fps == AnimationPrefs.FPS_DEF) 33.3 else jc.fps

            // Note the GIF header specifies inter-frame delay in terms of
            // hundredths of a second, so only `fps` values like 50, 33 1/3,
            // 25, 20, ... are precisely achieveable.
            setPriority(MIN_PRIORITY)
            start()
        }

        override fun run() {
            try {
                ap.animator.writeGIF(FileOutputStream(file), wgm, fps)
            } catch (_: IOException) {
                val message = jlGetStringResource(Res.string.error_writing_file, file.toString())
                jlHandleUserException(parent, message)
            } catch (jei: JuggleExceptionInternal) {
                jlHandleFatalException(jei)
            } finally {
                if (cleanup != null) {
                    SwingUtilities.invokeLater(cleanup)
                }
            }
        }
    }
}
