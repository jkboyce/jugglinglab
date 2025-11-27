//
// AnimationPrefs.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("SimplifyBooleanWithConstants")

package jugglinglab.core

import jugglinglab.generated.resources.*
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.ParameterList
import jugglinglab.util.getScreenFps
import jugglinglab.util.getStringResource
import jugglinglab.util.jlToStringRounded
import androidx.compose.ui.unit.IntSize

class AnimationPrefs {
    var width: Int = WIDTH_DEF
    var height: Int = HEIGHT_DEF
    var fps: Double = FPS_DEF
    var slowdown: Double = SLOWDOWN_DEF
    var border: Int = BORDER_DEF
    var showGround: Int = SHOWGROUND_DEF
    var stereo: Boolean = STEREO_DEF
    var startPause: Boolean = STARTPAUSE_DEF
    var mousePause: Boolean = MOUSEPAUSE_DEF
    var catchSound: Boolean = CATCHSOUND_DEF
    var bounceSound: Boolean = BOUNCESOUND_DEF
    var camangle: DoubleArray? = null // in degrees! null means use default
    var view: Int = VIEW_DEF // one of the values in View
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
                val message = getStringResource(Res.string.error_number_format, "fps")
                throw JuggleExceptionUser(message)
            }
        }
        if ((pl.removeParameter("slowdown").also { value = it }) != null) {
            try {
                tempdouble = value!!.toDouble()
                slowdown = tempdouble
            } catch (_: NumberFormatException) {
                val message = getStringResource(Res.string.error_number_format, "slowdown")
                throw JuggleExceptionUser(message)
            }
        }
        if ((pl.removeParameter("border").also { value = it }) != null) {
            try {
                tempint = value!!.toInt()
                border = tempint
            } catch (_: NumberFormatException) {
                val message = getStringResource(Res.string.error_number_format, "border")
                throw JuggleExceptionUser(message)
            }
        }
        if ((pl.removeParameter("width").also { value = it }) != null) {
            try {
                tempint = value!!.toInt()
                width = tempint
            } catch (_: NumberFormatException) {
                val message = getStringResource(Res.string.error_number_format, "width")
                throw JuggleExceptionUser(message)
            }
        }
        if ((pl.removeParameter("height").also { value = it }) != null) {
            try {
                tempint = value!!.toInt()
                height = tempint
            } catch (_: NumberFormatException) {
                val message = getStringResource(Res.string.error_number_format, "height")
                throw JuggleExceptionUser(message)
            }
        }
        if ((pl.removeParameter("showground").also { value = it }) != null) {
            showGround = if (value.equals("auto", ignoreCase = true)) {
                GROUND_AUTO
            } else if (value.equals("true", ignoreCase = true)
                || value.equals("on", ignoreCase = true)
                || value.equals("yes", ignoreCase = true)
            ) {
                GROUND_ON
            } else if (value.equals("false", ignoreCase = true)
                || value.equals("off", ignoreCase = true)
                || value.equals("no", ignoreCase = true)
            ) {
                GROUND_OFF
            } else {
                val message = getStringResource(Res.string.error_showground_value, value)
                throw JuggleExceptionUser(message)
            }
        }
        if ((pl.removeParameter("camangle").also { value = it }) != null) {
            try {
                val ca = DoubleArray(2)
                ca[1] = 90.0  // default if second angle isn't given

                val tokens = value!!.replace(Regex("[(){}]"), "").split(',')
                if (tokens.size > 2) {
                    val message = getStringResource(Res.string.error_too_many_elements, "camangle")
                    throw JuggleExceptionUser(message)
                }
                tokens.forEachIndexed { i, token ->
                    if (token.isNotBlank()) {
                        ca[i] = token.trim().toDouble()
                    }
                }
                camangle = ca
            } catch (_: NumberFormatException) {
                val message = getStringResource(Res.string.error_number_format, "camangle")
                throw JuggleExceptionUser(message)
            }
        }
        if ((pl.removeParameter("view").also { value = it }) != null) {
            view = -1
            for (viewIndex in viewNames.indices) {
                if (value.equals(viewNames[viewIndex], ignoreCase = true)) {
                    view = viewIndex + 1
                }
            }

            if (view == -1) {
                val message = getStringResource(Res.string.error_unrecognized_view, value)
                throw JuggleExceptionUser(message)
            }
        }
        if ((pl.removeParameter("hidejugglers").also { value = it }) != null) {
            try {
                val tokens = value!!.replace(Regex("[()]"), "").split(',')
                hideJugglers = tokens.mapNotNull { token ->
                    token.trim().takeIf { it.isNotEmpty() }?.toInt()
                }.toIntArray()
            } catch (_: NumberFormatException) {
                val message = getStringResource(Res.string.error_number_format, "hidejugglers")
                throw JuggleExceptionUser(message)
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

    var size: IntSize
        get() = IntSize(width, height)
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
            sb.append("view=").append(viewNames[view - 1]).append(";")
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
        // showground options
        const val GROUND_AUTO: Int = 0 // must be sequential
        const val GROUND_ON: Int = 1 // starting from 0
        const val GROUND_OFF: Int = 2

        // view options
        //
        // these should be sequential and in the same order as in the View menu,
        // because of assumptions in PatternWindow's constructor
        const val VIEW_NONE: Int = 0
        const val VIEW_SIMPLE: Int = 1
        const val VIEW_EDIT: Int = 2
        const val VIEW_PATTERN: Int = 3
        const val VIEW_SELECTION: Int = 4

        // in the same order as the VIEW_ constants above, used for `view`
        // parameter setting in AnimationPrefs
        val viewNames: List<String> = listOf(
            "simple",
            "visual_editor",
            "pattern_editor",
            "selection_editor",
        )

        // default values of all AnimationPrefs items
        const val WIDTH_DEF: Int = 400
        const val HEIGHT_DEF: Int = 450
        val FPS_DEF: Double = getScreenFps()
        const val SLOWDOWN_DEF: Double = 2.0
        const val BORDER_DEF: Int = 0
        const val SHOWGROUND_DEF: Int = GROUND_AUTO
        const val STEREO_DEF: Boolean = false
        const val STARTPAUSE_DEF: Boolean = false
        const val MOUSEPAUSE_DEF: Boolean = false
        const val CATCHSOUND_DEF: Boolean = false
        const val BOUNCESOUND_DEF: Boolean = false
        const val VIEW_DEF: Int = VIEW_NONE
    }
}
