//
// AnimationPrefs.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("SimplifyBooleanWithConstants")

package jugglinglab.core

import jugglinglab.JugglingLab.errorstrings
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.ParameterList
import jugglinglab.util.jlToStringRounded
import jugglinglab.view.View
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.text.MessageFormat
import java.util.*

class AnimationPrefs {
    @JvmField
    var width: Int = WIDTH_DEF
    @JvmField
    var height: Int = HEIGHT_DEF
    @JvmField
    var fps: Double = FPS_DEF
    @JvmField
    var slowdown: Double = SLOWDOWN_DEF
    @JvmField
    var border: Int = BORDER_DEF
    @JvmField
    var showGround: Int = SHOWGROUND_DEF
    @JvmField
    var stereo: Boolean = STEREO_DEF
    @JvmField
    var startPause: Boolean = STARTPAUSE_DEF
    @JvmField
    var mousePause: Boolean = MOUSEPAUSE_DEF
    @JvmField
    var catchSound: Boolean = CATCHSOUND_DEF
    @JvmField
    var bounceSound: Boolean = BOUNCESOUND_DEF
    @JvmField
    var camangle: DoubleArray? = null // in degrees! null means use default
    @JvmField
    var view: Int = VIEW_DEF // one of the values in View
    @JvmField
    var hideJugglers: IntArray? = null

    constructor() : super()

    constructor(jc: AnimationPrefs) {
        if (jc.width > 0) {
            width = jc.width
        }
        if (jc.height > 0) {
            height = jc.height
        }
        if (jc.slowdown >= 0) {
            slowdown = jc.slowdown
        }
        if (jc.fps >= 0) {
            fps = jc.fps
        }
        if (jc.border >= 0) {
            border = jc.border
        }
        showGround = jc.showGround
        stereo = jc.stereo
        startPause = jc.startPause
        mousePause = jc.mousePause
        catchSound = jc.catchSound
        bounceSound = jc.bounceSound
        if (jc.camangle != null) {
            camangle = jc.camangle!!.clone()
        }
        view = jc.view
        if (jc.hideJugglers != null) {
            hideJugglers = jc.hideJugglers!!.clone()
        }
    }

    @Throws(JuggleExceptionUser::class)
    fun fromParameters(pl: ParameterList): AnimationPrefs {
        var tempint: Int
        var tempdouble: Double
        var value: String?

        if ((pl.removeParameter("stereo").also { value = it }) != null) {
            stereo = value.toBoolean()
        }
        if ((pl.removeParameter("startpaused").also { value = it }) != null) {
            startPause = value.toBoolean()
        }
        if ((pl.removeParameter("mousepause").also { value = it }) != null) {
            mousePause = value.toBoolean()
        }
        if ((pl.removeParameter("catchsound").also { value = it }) != null) {
            catchSound = value.toBoolean()
        }
        if ((pl.removeParameter("bouncesound").also { value = it }) != null) {
            bounceSound = value.toBoolean()
        }
        if ((pl.removeParameter("fps").also { value = it }) != null) {
            try {
                tempdouble = value!!.toDouble()
                fps = tempdouble
            } catch (_: NumberFormatException) {
                val template: String = errorstrings.getString("Error_number_format")
                val arguments = arrayOf<Any?>("fps")
                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
            }
        }
        if ((pl.removeParameter("slowdown").also { value = it }) != null) {
            try {
                tempdouble = value!!.toDouble()
                slowdown = tempdouble
            } catch (_: NumberFormatException) {
                val template: String = errorstrings.getString("Error_number_format")
                val arguments = arrayOf<Any?>("slowdown")
                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
            }
        }
        if ((pl.removeParameter("border").also { value = it }) != null) {
            try {
                tempint = value!!.toInt()
                border = tempint
            } catch (_: NumberFormatException) {
                val template: String = errorstrings.getString("Error_number_format")
                val arguments = arrayOf<Any?>("border")
                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
            }
        }
        if ((pl.removeParameter("width").also { value = it }) != null) {
            try {
                tempint = value!!.toInt()
                width = tempint
            } catch (_: NumberFormatException) {
                val template: String = errorstrings.getString("Error_number_format")
                val arguments = arrayOf<Any?>("width")
                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
            }
        }
        if ((pl.removeParameter("height").also { value = it }) != null) {
            try {
                tempint = value!!.toInt()
                height = tempint
            } catch (_: NumberFormatException) {
                val template: String = errorstrings.getString("Error_number_format")
                val arguments = arrayOf<Any?>("height")
                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
            }
        }
        if ((pl.removeParameter("showground").also { value = it }) != null) {
            showGround = if (value.equals("auto", ignoreCase = true)) {
                GROUND_AUTO
            } else if (value.equals("true", ignoreCase = true) || value.equals("on", ignoreCase = true)
                || value.equals("yes", ignoreCase = true)
            ) {
                GROUND_ON
            } else if (value.equals("false", ignoreCase = true) || value.equals("off", ignoreCase = true)
                || value.equals("no", ignoreCase = true)
            ) {
                GROUND_OFF
            } else {
                val template: String = errorstrings.getString("Error_showground_value")
                val arguments = arrayOf<Any?>(value)
                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
            }
        }
        if ((pl.removeParameter("camangle").also { value = it }) != null) {
            try {
                val ca = DoubleArray(2)
                ca[1] = 90.0 // default if second angle isn't given

                value = value!!.replace("(", "").replace(")", "")
                value = value.replace("{", "").replace("}", "")

                val st = StringTokenizer(value, ",")
                val numangles = st.countTokens()
                if (numangles > 2) {
                    val template: String = errorstrings.getString("Error_too_many_elements")
                    val arguments = arrayOf<Any?>("camangle")
                    throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                }

                for (i in 0..<numangles) {
                    ca[i] = st.nextToken().trim { it <= ' ' }.toDouble()
                }

                camangle = DoubleArray(2)
                camangle!![0] = ca[0]
                camangle!![1] = ca[1]
            } catch (_: NumberFormatException) {
                val template: String = errorstrings.getString("Error_number_format")
                val arguments = arrayOf<Any?>("camangle")
                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
            }
        }
        if ((pl.removeParameter("view").also { value = it }) != null) {
            view = -1
            for (viewIndex in View.viewNames.indices) {
                if (value.equals(View.viewNames[viewIndex], ignoreCase = true)) {
                    view = viewIndex + 1
                }
            }

            if (view == -1) {
                val template: String = errorstrings.getString("Error_unrecognized_view")
                val arguments = arrayOf<Any?>("'$value'")
                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
            }
        }
        if ((pl.removeParameter("hidejugglers").also { value = it }) != null) {
            value = value!!.replace("(", "").replace(")", "")

            val st = StringTokenizer(value, ",")
            val numjugglers = st.countTokens()
            hideJugglers = IntArray(numjugglers)

            try {
                for (i in 0..<numjugglers) {
                    hideJugglers!![i] = st.nextToken().trim { it <= ' ' }.toInt()
                }
            } catch (_: NumberFormatException) {
                val template: String = errorstrings.getString("Error_number_format")
                val arguments = arrayOf<Any?>("hidejugglers")
                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
            }
        }
        return this
    }

    @Throws(JuggleExceptionUser::class)
    fun fromString(s: String?): AnimationPrefs {
        val pl = ParameterList(s)
        fromParameters(pl)
        pl.errorIfParametersLeft()
        return this
    }

    var size: Dimension
        get() = Dimension(width, height)
        set(dim) {
            width = dim.width
            height = dim.height
        }

    @Suppress("KotlinConstantConditions")
    override fun toString(): String {
        val sb = StringBuilder()
        if (width != WIDTH_DEF) {
            sb.append("width=").append(width).append(";")
        }
        if (height != HEIGHT_DEF) {
            sb.append("height=").append(height).append(";")
        }
        if (fps != FPS_DEF) {
            sb.append("fps=").append(jlToStringRounded(fps, 2)).append(";")
        }
        if (slowdown != SLOWDOWN_DEF) {
            sb.append("slowdown=").append(jlToStringRounded(slowdown, 2)).append(";")
        }
        if (border != BORDER_DEF) {
            sb.append("border=").append(border).append(";")
        }
        if (showGround != SHOWGROUND_DEF) {
            when (showGround) {
                GROUND_AUTO -> sb.append("showground=auto;")
                GROUND_ON -> sb.append("showground=true;")
                GROUND_OFF -> sb.append("showground=false;")
            }
        }
        if (stereo != STEREO_DEF) {
            sb.append("stereo=").append(stereo).append(";")
        }
        if (startPause != STARTPAUSE_DEF) {
            sb.append("startpaused=").append(startPause).append(";")
        }
        if (mousePause != MOUSEPAUSE_DEF) {
            sb.append("mousepause=").append(mousePause).append(";")
        }
        if (catchSound != CATCHSOUND_DEF) {
            sb.append("catchsound=").append(catchSound).append(";")
        }
        if (bounceSound != BOUNCESOUND_DEF) {
            sb.append("bouncesound=").append(bounceSound).append(";")
        }
        if (camangle != null) {
            sb.append("camangle=(").append(camangle!![0]).append(",").append(camangle!![1]).append(");")
        }
        if (view != VIEW_DEF) {
            sb.append("view=").append(View.viewNames[view - 1]).append(";")
        }
        if (hideJugglers != null) {
            sb.append("hidejugglers=(")
            for (i in hideJugglers!!.indices) {
                sb.append(hideJugglers!![i])
                if (i != hideJugglers!!.size - 1) {
                    sb.append(",")
                }
            }
            sb.append(");")
        }
        if (!sb.isEmpty()) {
            sb.setLength(sb.length - 1)
        }
        return sb.toString()
    }

    companion object {
        const val GROUND_AUTO: Int = 0 // must be sequential
        const val GROUND_ON: Int = 1 // starting from 0
        const val GROUND_OFF: Int = 2

        // default values of all items
        const val WIDTH_DEF: Int = 400
        const val HEIGHT_DEF: Int = 450
        val FPS_DEF: Double  // initialized below
        const val SLOWDOWN_DEF: Double = 2.0
        const val BORDER_DEF: Int = 0
        const val SHOWGROUND_DEF: Int = GROUND_AUTO
        const val STEREO_DEF: Boolean = false
        const val STARTPAUSE_DEF: Boolean = false
        const val MOUSEPAUSE_DEF: Boolean = false
        const val CATCHSOUND_DEF: Boolean = false
        const val BOUNCESOUND_DEF: Boolean = false
        const val VIEW_DEF: Int = View.VIEW_NONE

        init {
            // set `FPS_DEF` to screen refresh rate, if possible
            var fpsScreen = 0.0
            try {
                val devices = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
                if (!devices.isEmpty()) {
                    fpsScreen = devices[0]!!.getDisplayMode().refreshRate.toDouble()
                    // refreshRate returns 0 when refresh is unknown
                }
            } catch (_: Exception) {
                // HeadlessException when running headless (from CLI)
            }
            FPS_DEF = if (fpsScreen < 20) 60.0 else fpsScreen
        }
    }
}
