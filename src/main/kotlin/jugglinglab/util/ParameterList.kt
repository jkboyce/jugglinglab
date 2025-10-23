//
// ParameterList.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.util

import java.text.MessageFormat
import java.util.StringTokenizer

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
        if (this.numberOfParameters == 0) {
            names = ArrayList()
            values = ArrayList()
        }

        for (i in this.numberOfParameters - 1 downTo 0) {
            if (name.equals(getParameterName(i), ignoreCase = true)) {
                values[i] = value
                return true
            }
        }

        names.add(name)
        values.add(value)
        ++this.numberOfParameters
        return false
    }

    fun getParameter(name: String): String? {
        for (i in this.numberOfParameters - 1 downTo 0) {
            if (name.equals(getParameterName(i), ignoreCase = true)) {
                return getParameterValue(i)
            }
        }
        return null
    }

    fun removeParameter(name: String): String? {
        for (i in this.numberOfParameters - 1 downTo 0) {
            if (name.equals(getParameterName(i), ignoreCase = true)) {
                --this.numberOfParameters
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
        var source = source ?: return
        source = source.replace("\n".toRegex(), "").replace("\r".toRegex(), "")
        val st1 = StringTokenizer(source, ";")

        while (st1.hasMoreTokens()) {
            var str = st1.nextToken()
            val index = str.indexOf("=")
            if (index > 0) {
                val name = str.take(index).trim { it <= ' ' }
                val value = str.substring(index + 1).trim { it <= ' ' }
                if (!name.isEmpty()) {
                    addParameter(name, value)
                }
            } else {
                str = str.trim { it <= ' ' }
                if (!str.isEmpty()) {
                    val template: String = errorstrings.getString("Error_param_has_no_value")
                    val arg = arrayOf<Any?>(str)
                    throw JuggleExceptionUser(MessageFormat.format(template, *arg))
                }
            }
        }
    }

    override fun toString(): String {
        val sb = StringBuilder()
        for (i in 0..<this.numberOfParameters) {
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
        val count = this.numberOfParameters
        if (count == 1) {
            val template: String = errorstrings.getString("Error_unused_param")
            val arguments = arrayOf<Any?>("\"" + getParameterName(0) + "\"")
            throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
        } else if (count > 1) {
            val template: String = errorstrings.getString("Error_unused_params")
            val names = ArrayList<String?>()
            for (i in 0..<count) {
                names.add("\"" + getParameterName(i) + "\"")
            }
            val arguments = arrayOf<Any?>(names.joinToString(", "))
            throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
        }
    }
}
