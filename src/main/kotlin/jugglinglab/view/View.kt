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

import jugglinglab.JugglingLab.errorstrings
import jugglinglab.JugglingLab.guistrings
import jugglinglab.core.AnimationPanel
import jugglinglab.core.AnimationPrefs
import jugglinglab.core.Animator.WriteGIFMonitor
import jugglinglab.core.PatternWindow
import jugglinglab.jml.JMLPattern
import jugglinglab.util.ErrorDialog.handleFatalException
import jugglinglab.util.ErrorDialog.handleUserException
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import java.awt.Dimension
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.MessageFormat
import java.util.*
import javax.swing.JPanel
import javax.swing.ProgressMonitor
import javax.swing.SwingUtilities

abstract class View : JPanel() {
    var patternWindow: PatternWindow? = null

    protected var undo: ArrayList<JMLPattern>? = null

    var undoIndex: Int = 0
        protected set

    val hashCode: Int
        get() {
            return pattern?.getHashCode() ?: 0
        }

    //--------------------------------------------------------------------------
    // Methods to handle undo/redo functionality.
    //
    // The enclosing PatternWindow owns the undo list, so it's preserved when
    // we switch views. All of the methods are here however in case we want to
    // embed the View in something other than a PatternWindow in the future.
    //--------------------------------------------------------------------------
    
    // For the PatternWindow to pass into a newly-initialized view.

    fun setUndoList(u: ArrayList<JMLPattern>, uIndex: Int) {
        undo = u
        this.undoIndex = uIndex
    }

    // Add a pattern to the undo list.

    fun addToUndoList(p: JMLPattern) {
        try {
            val pcopy = JMLPattern(p) // add copy so it won't change
            ++this.undoIndex
            undo!!.add(this.undoIndex, pcopy)
            while (this.undoIndex + 1 < undo!!.size) {
                undo!!.removeAt(this.undoIndex + 1)
            }

            if (patternWindow != null) {
                patternWindow!!.updateUndoMenu()
            }
        } catch (jeu: JuggleExceptionUser) {
            handleFatalException(JuggleExceptionInternal(jeu.message))
        } catch (jei: JuggleExceptionInternal) {
            handleFatalException(jei)
        }
    }

    // Undo to the previous save state.

    @Throws(JuggleExceptionInternal::class)
    fun undoEdit() {
        if (this.undoIndex > 0) {
            try {
                --this.undoIndex
                val pcopy = JMLPattern(undo!![this.undoIndex])
                restartView(pcopy, null)

                if (this.undoIndex == 0 || this.undoIndex == undo!!.size - 2) {
                    if (patternWindow != null) {
                        patternWindow!!.updateUndoMenu()
                    }
                }
            } catch (jeu: JuggleExceptionUser) {
                // errors here aren't user errors since pattern was successfully
                // animated before
                throw JuggleExceptionInternal(jeu.message)
            }
        }
    }

    // Redo to the next save state.

    @Throws(JuggleExceptionInternal::class)
    fun redoEdit() {
        if (this.undoIndex < undo!!.size - 1) {
            try {
                ++this.undoIndex
                val pcopy = JMLPattern(undo!![this.undoIndex])
                restartView(pcopy, null)

                if (this.undoIndex == 1 || this.undoIndex == undo!!.size - 1) {
                    if (patternWindow != null) {
                        patternWindow!!.updateUndoMenu()
                    }
                }
            } catch (jeu: JuggleExceptionUser) {
                throw JuggleExceptionInternal(jeu.message)
            }
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

    abstract val pattern: JMLPattern?

    abstract val animationPrefs: AnimationPrefs

    abstract var zoomLevel: Double

    abstract var isPaused: Boolean

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
            ProgressMonitor(patternWindow, guistrings.getString("Saving_animated_GIF"), "", 0, 1)
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

                override fun isCanceled(): Boolean {
                    return (pm.isCanceled() || interrupted())
                }
            }

            val jc = ap.animationPrefs
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
                val template: String = errorstrings.getString("Error_writing_file")
                val arg = arrayOf<Any?>(file.toString())
                handleUserException(parent, MessageFormat.format(template, *arg))
            } catch (jei: JuggleExceptionInternal) {
                handleFatalException(jei)
            } finally {
                if (cleanup != null) {
                    SwingUtilities.invokeLater(cleanup)
                }
            }
        }
    }

    companion object {
        // these should be sequential and in the same order as in the View menu,
        // because of assumptions in PatternWindow's constructor
        const val VIEW_NONE: Int = 0
        const val VIEW_SIMPLE: Int = 1
        const val VIEW_EDIT: Int = 2
        const val VIEW_PATTERN: Int = 3
        const val VIEW_SELECTION: Int = 4

        // used for `view` parameter setting in AnimationPrefs, these must be in the
        // same order as VIEW_ constants above
        @JvmField
        val viewNames: Array<String> = arrayOf(
            "simple", "visual_editor", "pattern_editor", "selection_editor",
        )
    }
}
