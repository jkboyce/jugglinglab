//
// ParameterList.kt
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.util

import jugglinglab.composeapp.generated.resources.*

class ParameterList() {
    var numberOfParameters: Int = 0
        private set
    private lateinit var names: ArrayList<String>
    private lateinit var values: ArrayList<String>

    @Throws(JuggleExceptionUser::class)
    constructor(source: String?) : this() {
        readParameters(source)
    }

    // Return true if parameter already existed, false if it was new.
    fun addParameter(name: String, value: String): Boolean {
        if (numberOfParameters == 0) {
            names = ArrayList()
            values = ArrayList()
        }

        for (i in numberOfParameters - 1 downTo 0) {
            if (name.equals(getParameterName(i), ignoreCase = true)) {
                values[i] = value
                return true
            }
        }

        names.add(name)
        values.add(value)
        ++numberOfParameters
        return false
    }

    fun getParameter(name: String): String? {
        for (i in numberOfParameters - 1 downTo 0) {
            if (name.equals(getParameterName(i), ignoreCase = true)) {
                return getParameterValue(i)
            }
        }
        return null
    }

    fun removeParameter(name: String): String? {
        for (i in numberOfParameters - 1 downTo 0) {
            if (name.equals(getParameterName(i), ignoreCase = true)) {
                --numberOfParameters
                names.removeAt(i)
                return values.removeAt(i)
            }
        }
        return null
    }

    fun getParameterName(index: Int): String {
        return names[index]
    }

    fun getParameterValue(index: Int): String {
        return values[index]
    }

    @Throws(JuggleExceptionUser::class)
    fun readParameters(source: String?) {
        val cleanSource = source?.replace("\n", "")?.replace("\r", "") ?: return

        for (token in cleanSource.split(';')) {
            val index = token.indexOf("=")
            if (index > 0) {
                val name = token.take(index).trim()
                val value = token.substring(index + 1).trim()
                if (name.isNotEmpty()) {
                    addParameter(name, value)
                }
            } else {
                val str = token.trim()
                if (str.isNotEmpty()) {
                    val message = jlGetStringResource(Res.string.error_param_has_no_value, str)
                    throw JuggleExceptionUser(message)
                }
            }
        }
    }

    override fun toString(): String {
        val sb = StringBuilder()
        for (i in 0..<numberOfParameters) {
            if (i != 0) {
                sb.append(';')
            }
            sb.append(getParameterName(i)).append('=').append(getParameterValue(i))
        }
        return sb.toString()
    }

    // Throw an appropriate error if there are parameters left over after parsing.

    @Throws(JuggleExceptionUser::class)
    fun errorIfParametersLeft() {
        val count = numberOfParameters
        if (count == 1) {
            val argument = "\"${getParameterName(0)}\""
            val message = jlGetStringResource(Res.string.error_unused_param, argument)
            throw JuggleExceptionUser(message)
        } else if (count > 1) {
            val argument = (0..<count).joinToString(", ") { i -> "\"${getParameterName(i)}\"" }
            val message = jlGetStringResource(Res.string.error_unused_params, argument)
            throw JuggleExceptionUser(message)
        }
    }
}
