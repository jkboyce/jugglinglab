//
// AnimationPrefs.kt
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("SimplifyBooleanWithConstants")

package jugglinglab.core

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.ParameterList
import jugglinglab.util.jlGetScreenFps
import jugglinglab.util.jlGetStringResource
import jugglinglab.util.jlToStringRounded

data class AnimationPrefs(
    val width: Int = WIDTH_DEF,
    val height: Int = HEIGHT_DEF,
    val fps: Double = FPS_DEF,
    val slowdown: Double = SLOWDOWN_DEF,
    val borderPixels: Int = BORDERPIXELS_DEF,
    val showGround: Int = SHOWGROUND_DEF,
    val stereo: Boolean = STEREO_DEF,
    val startPaused: Boolean = STARTPAUSED_DEF,
    val mousePause: Boolean = MOUSEPAUSE_DEF,
    val catchSound: Boolean = CATCHSOUND_DEF,
    val bounceSound: Boolean = BOUNCESOUND_DEF,
    val defaultCameraAngle: List<Double>? = null,
    val defaultView: Int = VIEW_DEF, // one of the values in View
    val hideJugglers: List<Int> = listOf()
) {
    @Suppress("KotlinConstantConditions")
    override fun toString(): String {
        val sb = StringBuilder()
        if (width != WIDTH_DEF) {
            sb.append("width=$width;")
        }
        if (height != HEIGHT_DEF) {
            sb.append("height=$height;")
        }
        if (fps != FPS_DEF) {
            sb.append("fps=").append(jlToStringRounded(fps, 2)).append(";")
        }
        if (slowdown != SLOWDOWN_DEF) {
            sb.append("slowdown=").append(jlToStringRounded(slowdown, 2)).append(";")
        }
        if (borderPixels != BORDERPIXELS_DEF) {
            sb.append("border=$borderPixels;")
        }
        if (showGround != SHOWGROUND_DEF) {
            when (showGround) {
                GROUND_AUTO -> sb.append("showground=auto;")
                GROUND_ON -> sb.append("showground=true;")
                GROUND_OFF -> sb.append("showground=false;")
            }
        }
        if (stereo != STEREO_DEF) {
            sb.append("stereo=$stereo;")
        }
        if (startPaused != STARTPAUSED_DEF) {
            sb.append("startpaused=$startPaused;")
        }
        if (mousePause != MOUSEPAUSE_DEF) {
            sb.append("mousepause=$mousePause;")
        }
        if (catchSound != CATCHSOUND_DEF) {
            sb.append("catchsound=$catchSound;")
        }
        if (bounceSound != BOUNCESOUND_DEF) {
            sb.append("bouncesound=$bounceSound;")
        }
        if (defaultCameraAngle != null) {
            sb.append("camangle=(").append(defaultCameraAngle[0])
                .append(",").append(defaultCameraAngle[1])
                .append(");")
        }
        if (defaultView != VIEW_DEF) {
            sb.append("view=").append(viewNames[defaultView - 1]).append(";")
        }
        if (hideJugglers.isNotEmpty()) {
            sb.append("hidejugglers=(")
            for ((index, juggler) in hideJugglers.withIndex()) {
                sb.append(juggler)
                if (index != hideJugglers.size - 1) {
                    sb.append(",")
                }
            }
            sb.append(");")
        }
        if (sb.isNotEmpty()) {
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
        val FPS_DEF: Double = jlGetScreenFps()
        const val SLOWDOWN_DEF: Double = 2.0
        const val BORDERPIXELS_DEF: Int = 0
        const val SHOWGROUND_DEF: Int = GROUND_AUTO
        const val STEREO_DEF: Boolean = false
        const val STARTPAUSED_DEF: Boolean = false
        const val MOUSEPAUSE_DEF: Boolean = false
        const val CATCHSOUND_DEF: Boolean = false
        const val BOUNCESOUND_DEF: Boolean = false
        const val VIEW_DEF: Int = VIEW_NONE

        // Constructing AnimationPrefs

        @Throws(JuggleExceptionUser::class)
        fun fromParameters(pl: ParameterList): AnimationPrefs {
            var result = AnimationPrefs()
            var tempint: Int
            var tempdouble: Double
            var value: String?

            if ((pl.removeParameter("width").also { value = it }) != null) {
                try {
                    tempint = value!!.toInt()
                    result = result.copy(width = tempint)
                } catch (_: NumberFormatException) {
                    val message = jlGetStringResource(Res.string.error_number_format, "width")
                    throw JuggleExceptionUser(message)
                }
            }
            if ((pl.removeParameter("height").also { value = it }) != null) {
                try {
                    tempint = value!!.toInt()
                    result = result.copy(height = tempint)
                } catch (_: NumberFormatException) {
                    val message = jlGetStringResource(Res.string.error_number_format, "height")
                    throw JuggleExceptionUser(message)
                }
            }
            if ((pl.removeParameter("fps").also { value = it }) != null) {
                try {
                    tempdouble = value!!.toDouble()
                    result = result.copy(fps = tempdouble)
                } catch (_: NumberFormatException) {
                    val message = jlGetStringResource(Res.string.error_number_format, "fps")
                    throw JuggleExceptionUser(message)
                }
            }
            if ((pl.removeParameter("slowdown").also { value = it }) != null) {
                try {
                    tempdouble = value!!.toDouble()
                    result = result.copy(slowdown = tempdouble)
                } catch (_: NumberFormatException) {
                    val message = jlGetStringResource(Res.string.error_number_format, "slowdown")
                    throw JuggleExceptionUser(message)
                }
            }
            if ((pl.removeParameter("border").also { value = it }) != null) {
                try {
                    tempint = value!!.toInt()
                    result = result.copy(borderPixels = tempint)
                } catch (_: NumberFormatException) {
                    val message = jlGetStringResource(Res.string.error_number_format, "border")
                    throw JuggleExceptionUser(message)
                }
            }
            if ((pl.removeParameter("showground").also { value = it }) != null) {
                result = result.copy(
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
                        val message = jlGetStringResource(Res.string.error_showground_value, value)
                        throw JuggleExceptionUser(message)
                    }
                )
            }
            if ((pl.removeParameter("stereo").also { value = it }) != null) {
                result = result.copy(stereo = value.toBoolean())
            }
            if ((pl.removeParameter("startpaused").also { value = it }) != null) {
                result = result.copy(startPaused = value.toBoolean())
            }
            if ((pl.removeParameter("mousepause").also { value = it }) != null) {
                result = result.copy(mousePause = value.toBoolean())
            }
            if ((pl.removeParameter("catchsound").also { value = it }) != null) {
                result = result.copy(catchSound = value.toBoolean())
            }
            if ((pl.removeParameter("bouncesound").also { value = it }) != null) {
                result = result.copy(bounceSound = value.toBoolean())
            }
            if ((pl.removeParameter("camangle").also { value = it }) != null) {
                try {
                    val ca = DoubleArray(2)
                    ca[1] = 90.0  // default if second angle isn't given

                    val tokens = value!!.filterNot {
                        it == '(' || it == ')' || it == '{' || it == '}'
                    }.split(',')
                    if (tokens.size > 2) {
                        val message =
                            jlGetStringResource(Res.string.error_too_many_elements, "camangle")
                        throw JuggleExceptionUser(message)
                    }
                    tokens.forEachIndexed { i, token ->
                        if (token.isNotBlank()) {
                            ca[i] = token.trim().toDouble()
                        }
                    }
                    result = result.copy(defaultCameraAngle = ca.toList())
                } catch (_: NumberFormatException) {
                    val message = jlGetStringResource(Res.string.error_number_format, "camangle")
                    throw JuggleExceptionUser(message)
                }
            }
            if ((pl.removeParameter("view").also { value = it }) != null) {
                var newDefaultView = -1
                for (viewIndex in viewNames.indices) {
                    if (value.equals(viewNames[viewIndex], ignoreCase = true)) {
                        newDefaultView = viewIndex + 1
                    }
                }

                if (newDefaultView == -1) {
                    val message = jlGetStringResource(Res.string.error_unrecognized_view, value)
                    throw JuggleExceptionUser(message)
                }
                result = result.copy(defaultView = newDefaultView)
            }
            if ((pl.removeParameter("hidejugglers").also { value = it }) != null) {
                try {
                    val tokens = value!!.filterNot { it == '(' || it == ')' }.split(',')
                    result = result.copy(
                        hideJugglers = tokens.mapNotNull { token ->
                            token.trim().takeIf { it.isNotEmpty() }?.toInt()
                        }.toList()
                    )
                } catch (_: NumberFormatException) {
                    val message = jlGetStringResource(Res.string.error_number_format, "hidejugglers")
                    throw JuggleExceptionUser(message)
                }
            }
            return result
        }

        @Throws(JuggleExceptionUser::class)
        fun fromString(s: String?): AnimationPrefs {
            val pl = ParameterList(s)
            val result = fromParameters(pl)
            pl.errorIfParametersLeft()
            return result
        }
    }
}
