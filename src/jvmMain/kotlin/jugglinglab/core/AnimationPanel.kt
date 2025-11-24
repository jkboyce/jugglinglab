//
// AnimationPanel.kt
//
// This class creates the juggling animation on screen. It spawns a thread that
// loops over time and draws frames. It also interprets some mouse interactions
// such as camera drag and click to pause.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.core

import jugglinglab.JugglingLab.guistrings
import jugglinglab.jml.JMLPattern
import jugglinglab.util.ErrorDialog.handleFatalException
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import java.awt.Color
import java.awt.Graphics
import java.awt.event.*
import java.util.*
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.DataLine
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.abs

open class AnimationPanel : JPanel(), Runnable {
    val animator: Animator = Animator()
    protected var jc: AnimationPrefs = AnimationPrefs()
    protected var engine: Thread? = null
    protected var engineRunning: Boolean = false
    protected var enginePaused: Boolean = false
    var engineAnimating: Boolean = false
    protected var simTime: Double = 0.0
    var writingGIF: Boolean = false
    var message: String? = null

    protected var catchclip: Clip? = null
    protected var bounceclip: Clip? = null

    protected var waspaused: Boolean = false // for pause on mouse away
    protected var outside: Boolean = false
    protected var outsideValid: Boolean = false

    // for camera dragging
    protected var draggingCamera: Boolean = false
    protected var startx: Int = 0
    protected var starty: Int = 0
    protected var lastx: Int = 0
    protected var lasty: Int = 0
    protected var dragcamangle: DoubleArray? = null

    // attached objects to be notified of events
    protected var attachments: ArrayList<AnimationAttachment> = ArrayList<AnimationAttachment>()

    init {
        setOpaque(true)
        loadAudioClips()
        initHandlers()
    }

    protected fun loadAudioClips() {
        try {
            val catchurl = AnimationPanel::class.java.getResource("/catch.au")
            val catchAudioIn = AudioSystem.getAudioInputStream(catchurl)
            val info = DataLine.Info(Clip::class.java, catchAudioIn.getFormat())
            catchclip = AudioSystem.getLine(info) as Clip?
            catchclip!!.open(catchAudioIn)
        } catch (_: Exception) {
            // System.out.println("Error loading catch.au: " + e.getMessage());
            catchclip = null
        }
        try {
            val bounceurl = AnimationPanel::class.java.getResource("/bounce.au")
            val bounceAudioIn = AudioSystem.getAudioInputStream(bounceurl)
            val info = DataLine.Info(Clip::class.java, bounceAudioIn.getFormat())
            bounceclip = AudioSystem.getLine(info) as Clip?
            bounceclip!!.open(bounceAudioIn)
        } catch (_: Exception) {
            // System.out.println("Error loading bounce.au: " + e.getMessage());
            bounceclip = null
        }
    }

    protected open fun initHandlers() {
        addMouseListener(
            object : MouseAdapter() {
                var lastpress: Long = 0L
                var lastenter: Long = 1L

                override fun mousePressed(me: MouseEvent) {
                    lastpress = me.getWhen()

                    // The following (and the equivalent in mouseReleased()) is a hack to
                    // swallow a mouseclick when the browser stops reporting enter/exit
                    // events because the user has clicked on something else.  The system
                    // reports simultaneous enter/press events when the user mouses down
                    // in the component; we want to swallow this as a click, and just use
                    // it to get focus back.
                    if (jc.mousePause && lastpress == lastenter) return
                    if (!engineAnimating) return
                    if (writingGIF) return
                    startx = me.getX()
                    starty = me.getY()
                }

                override fun mouseReleased(me: MouseEvent) {
                    if (jc.mousePause && lastpress == lastenter) return
                    if (writingGIF) return
                    draggingCamera = false

                    if (!engineAnimating && engine != null && engine!!.isAlive) {
                        this@AnimationPanel.isPaused = !enginePaused
                        return
                    }
                    if (me.getX() == startx && me.getY() == starty && engine != null && engine!!.isAlive) {
                        this@AnimationPanel.isPaused = !enginePaused
                        getParent().dispatchEvent(me)
                    }
                    if (this@AnimationPanel.isPaused) {
                        repaint()
                    }
                }

                override fun mouseEntered(me: MouseEvent) {
                    lastenter = me.getWhen()
                    if (jc.mousePause && !writingGIF) {
                        this@AnimationPanel.isPaused = waspaused
                    }
                    outside = false
                    outsideValid = true
                }

                override fun mouseExited(me: MouseEvent?) {
                    if (jc.mousePause && !writingGIF) {
                        waspaused = this@AnimationPanel.isPaused
                        this@AnimationPanel.isPaused = true
                    }
                    outside = true
                    outsideValid = true
                }
            })

        addMouseMotionListener(
            object : MouseMotionAdapter() {
                override fun mouseDragged(me: MouseEvent) {
                    if (!engineAnimating) {
                        return
                    }
                    if (writingGIF) {
                        return
                    }
                    if (!draggingCamera) {
                        draggingCamera = true
                        lastx = startx
                        lasty = starty
                        dragcamangle = this@AnimationPanel.cameraAngle
                    }

                    val xdelta = me.getX() - lastx
                    val ydelta = me.getY() - lasty
                    lastx = me.getX()
                    lasty = me.getY()
                    val ca = dragcamangle!!
                    ca[0] += (xdelta).toDouble() * 0.02
                    ca[1] -= (ydelta).toDouble() * 0.02
                    if (ca[1] < Math.toRadians(0.0001)) {
                        ca[1] = Math.toRadians(0.0001)
                    }
                    if (ca[1] > Math.toRadians(179.9999)) {
                        ca[1] = Math.toRadians(179.9999)
                    }
                    while (ca[0] < 0.0) {
                        ca[0] += Math.toRadians(360.0)
                    }
                    while (ca[0] >= Math.toRadians(360.0)) {
                        ca[0] -= Math.toRadians(360.0)
                    }

                    val snappedcamangle = snapCamera(ca)
                    this@AnimationPanel.cameraAngle = snappedcamangle

                    // send event to the parent so that SelectionView can update
                    // camera angles of other animations
                    getParent().dispatchEvent(me)

                    if (this@AnimationPanel.isPaused) {
                        repaint()
                    }
                }
            })

        addComponentListener(
            object : ComponentAdapter() {
                var hasResized: Boolean = false

                override fun componentResized(e: ComponentEvent?) {
                    if (!engineAnimating) {
                        return
                    }
                    if (writingGIF) {
                        return
                    }
                    animator.dimension = size
                    repaint()

                    // Don't update the preferred animation size if the enclosing
                    // window is maximized
                    val comp = SwingUtilities.getRoot(this@AnimationPanel)
                    if (comp is PatternWindow) {
                        if (comp.isWindowMaximized) {
                            return
                        }
                    }

                    if (hasResized) {
                        jc.size = size
                    }
                    hasResized = true
                }
            })
    }

    fun removeAllAttachments() = attachments.clear()

    fun addAnimationAttachment(att: AnimationAttachment) = attachments.add(att)

    protected open fun snapCamera(ca: DoubleArray): DoubleArray {
        val result = DoubleArray(2)
        result[0] = ca[0]
        result[1] = ca[1]

        if (result[1] < SNAPANGLE) {
            result[1] = Math.toRadians(0.0001)
        } else if (anglediff(Math.toRadians(90.0) - result[1]) < SNAPANGLE) {
            result[1] = Math.toRadians(90.0)
        } else if (result[1] > (Math.toRadians(180.0) - SNAPANGLE)) {
            result[1] = Math.toRadians(179.9999)
        }

        if (animator.pat!!.numberOfJugglers == 1) {
            var a = -Math.toRadians(animator.pat!!.getJugglerAngle(1, this.time))

            while (a < 0) {
                a += Math.toRadians(360.0)
            }
            while (a >= Math.toRadians(360.0)) {
                a -= Math.toRadians(360.0)
            }

            if (anglediff(a - result[0]) < SNAPANGLE) {
                result[0] = a
            } else if (anglediff(a + 0.5 * Math.PI - result[0]) < SNAPANGLE) {
                result[0] = a + 0.5 * Math.PI
            } else if (anglediff(a + Math.PI - result[0]) < SNAPANGLE) {
                result[0] = a + Math.PI
            } else if (anglediff(a + 1.5 * Math.PI - result[0]) < SNAPANGLE) {
                result[0] = a + 1.5 * Math.PI
            }
        }
        return result
    }

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    open fun restartJuggle(pat: JMLPattern?, newjc: AnimationPrefs?) {
        // Do pattern layout first so if there's an error we don't disrupt the
        // current animation
        pat?.layoutPattern()

        // stop the current animation thread, if one is running
        killAnimationThread()

        if (newjc != null) {
            jc = newjc
        }

        animator.dimension = size
        animator.restartAnimator(pat, newjc)
        setBackground(animator.background)

        engine = Thread(this)
        engine!!.start()
    }

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    fun restartJuggle() = restartJuggle(null, null)

    override fun run() {
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY)
        engineRunning = true // ok to start painting
        engineAnimating = false

        if (jc.mousePause) {
            waspaused = jc.startPause
        }

        try {
            if (jc.startPause) {
                message = guistrings.getString("Message_click_to_start")
                repaint()
                enginePaused = true
                while (enginePaused) {
                    synchronized(this) {
                        (this as Object).wait()
                    }
                }
            }

            message = null

            var realTimeStart = System.currentTimeMillis()
            var realTimeWait: Long
            var oldtime: Double
            var newtime: Double

            if (jc.mousePause) {
                if (outsideValid) {
                    this.isPaused = outside
                } else {
                    this.isPaused = true // assume mouse is outside animator, if not known
                }
                waspaused = false
            }

            engineAnimating = true

            while (true) {
                this.time = animator.pat!!.loopStartTime

                while (this.time < (animator.pat!!.loopEndTime - 0.5 * animator.simIntervalSecs)) {
                    repaint()
                    realTimeWait =
                        animator.realIntervalMillis - (System.currentTimeMillis() - realTimeStart)

                    if (realTimeWait > 0) {
                        Thread.sleep(realTimeWait)
                    } else if (engine == null || Thread.interrupted()) {
                        throw InterruptedException()
                    }

                    realTimeStart = System.currentTimeMillis()

                    while (enginePaused) {
                        synchronized(this) {
                            (this as Object).wait()
                        }
                    }

                    oldtime = this.time
                    this.time += animator.simIntervalSecs
                    newtime = this.time

                    if (jc.catchSound && catchclip != null) {
                        // use synchronized here to prevent editing actions in
                        // EditLadderDiagram from creating data consistency problems
                        val pattemp = animator.pat!!
                        synchronized(pattemp) {
                            for (path in 1..pattemp.numberOfPaths) {
                                if (pattemp.getPathCatchVolume(path, oldtime, newtime) > 0.0) {
                                    // do audio playback on the EDT -- not strictly
                                    // necessary but it seems to work better on Linux
                                    SwingUtilities.invokeLater {
                                        if (catchclip!!.isActive) {
                                            catchclip!!.stop()
                                        }
                                        catchclip!!.framePosition = 0
                                        catchclip!!.start()
                                    }
                                }
                            }
                        }
                    }
                    if (jc.bounceSound && bounceclip != null) {
                        val pattemp = animator.pat!!
                        synchronized(pattemp) {
                            for (path in 1..pattemp.numberOfPaths) {
                                if (pattemp.getPathBounceVolume(path, oldtime, newtime) > 0.0) {
                                    SwingUtilities.invokeLater {
                                        if (bounceclip!!.isActive) {
                                            bounceclip!!.stop()
                                        }
                                        bounceclip!!.framePosition = 0
                                        bounceclip!!.start()
                                    }
                                }
                            }
                        }
                    }
                }
                animator.advanceProps()
            }
        } catch (_: InterruptedException) {
        }
    }

    // stop the current animation thread, if one is running
    protected fun killAnimationThread() {
        try {
            if (engine != null && engine!!.isAlive) {
                engine!!.interrupt()
                engine!!.join()
            }
        } catch (_: InterruptedException) {
        } finally {
            engine = null
            engineRunning = false
            enginePaused = false
            engineAnimating = false
            message = null
        }
    }

    @set:Synchronized
    var isPaused: Boolean
        get() = enginePaused
        set(wanttopause) {
            if (enginePaused && !wanttopause) {
                (this as Object).notify()  // wake up wait() in run() method
            }
            enginePaused = wanttopause
        }

    var time: Double
        get() = simTime
        set(time) {
            simTime = time
            for (att in attachments) {
                att.setTime(time)
            }
        }

    var cameraAngle: DoubleArray
        get() = animator.cameraAngle
        set(ca) {
            animator.cameraAngle = ca
        }

    protected fun drawString(message: String, g: Graphics) {
        val fm = g.fontMetrics
        val messageWidth = fm.stringWidth(message)

        val dim = size
        val x = if (dim.width > messageWidth) (dim.width - messageWidth) / 2 else 0
        val y = (dim.height + fm.height) / 2

        g.color = Color.white
        g.fillRect(0, 0, dim.width, dim.height)
        g.color = Color.black
        g.drawString(message, x, y)
    }

    val pattern: JMLPattern?
        get() = animator.pat

    val animationPrefs: AnimationPrefs
        get() = jc

    open var zoomLevel: Double
        get() = animator.zoomLevel
        set(z) {
            if (!writingGIF) {
                animator.zoomLevel = z
                repaint()
            }
        }

    fun disposeAnimation() {
        killAnimationThread()
    }

    //--------------------------------------------------------------------------
    // javax.swing.JComponent methods
    //--------------------------------------------------------------------------

    public override fun paintComponent(g: Graphics) {
        if (message != null) {
            drawString(message!!, g)
        } else if (engineRunning && !writingGIF) {
            try {
                animator.drawFrame(this.time, g, draggingCamera, true)
            } catch (jei: JuggleExceptionInternal) {
                killAnimationThread()
                handleFatalException(jei)
            }
        }
    }

    // Interface for other elements to implement, to be notified by
    // AnimationPanel about simulation time updates, etc.

    interface AnimationAttachment {
        // AnimationPanel we're attached to
        fun setAnimationPanel(animPanel: AnimationPanel?)

        // simulation time (seconds)
        fun setTime(t: Double)

        // force a redraw
        fun repaintAttachment()
    }

    companion object {
        val SNAPANGLE: Double = Math.toRadians(8.0)

        fun anglediff(delta: Double): Double {
            var delta = delta
            while (delta > Math.PI) {
                delta -= 2 * Math.PI
            }
            while (delta <= -Math.PI) {
                delta += 2 * Math.PI
            }
            return abs(delta)
        }
    }
}
