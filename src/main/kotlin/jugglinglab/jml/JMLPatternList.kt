//
// JMLPatternList.kt
//
// This class represents a JML pattern list. This is the data model; the
// visualization is in PatternListPanel and PatternListWindow.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

import jugglinglab.JugglingLab.errorstrings
import jugglinglab.core.AnimationPrefs
import jugglinglab.jml.JMLNode.Companion.xmlescape
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.ParameterList
import jugglinglab.util.compareVersions
import java.io.IOException
import java.io.PrintWriter
import java.io.Writer
import java.text.MessageFormat
import java.util.*
import javax.swing.DefaultListModel

class JMLPatternList() {
    var version: String = JMLDefs.CURRENT_JML_VERSION
    var loadingversion: String = JMLDefs.CURRENT_JML_VERSION

    var title: String? = null
        set(t) {
            // by convention we don't allow title to be zero-length string "",
            // but use null instead
            field = if (t == null || t.trim().isEmpty()) null else t.trim()
        }

    var info: String? = null
        private set

    val model = DefaultListModel<PatternRecord>()

    val size: Int
        get() = if (BLANK_AT_END) model.size() - 1 else model.size()

    init {
        clearModel()
    }

    //--------------------------------------------------------------------------
    // Methods to define the pattern list
    //--------------------------------------------------------------------------

    // Construct from a JML tree.

    @Throws(JuggleExceptionUser::class)
    constructor(root: JMLNode) : this() {
        readJML(root)
    }

    fun clearModel() {
        model.clear()
        if (BLANK_AT_END) {
            model.addElement(PatternRecord(" ", null, null, null, null, null, null))
        }
    }

    // Add a pattern at a specific row in the list. When `row` < 0, add it at
    // the end.

    fun addLine(
        row: Int,
        display: String?,
        animprefs: String?,
        notation: String?,
        anim: String?,
        patnode: JMLNode?,
        infonode: JMLNode?
    ) {
        val display = display ?: ""
        val animprefs = animprefs?.trim()
        val notation = notation?.trim()
        val anim = anim?.trim()
        var info: String? = null
        var tags: ArrayList<String>? = null

        if (infonode != null) {
            info = infonode.nodeValue
            info = if (info != null && !info.isBlank()) info.trim() else null

            val tagstr = infonode.attributes.getAttribute("tags")
            if (tagstr != null) {
                tags = ArrayList<String>()

                for (t in tagstr.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                    var t = t
                    t = t.trim()
                    var isNew = true

                    for (t2 in tags) {
                        if (t2.equals(t, ignoreCase = true)) {
                            isNew = false
                            break
                        }
                    }

                    if (isNew) {
                        tags.add(t)
                    }
                }
            }
        }

        val rec = PatternRecord(display, animprefs, notation, anim, patnode, info, tags)

        if (row < 0) {
            if (BLANK_AT_END) {
                model.add(model.size() - 1, rec)
            } else {
                model.addElement(rec)  // adds at end
            }
        } else {
            model.add(row, rec)
        }
    }

    @Suppress("unused")
    fun getLine(row: Int): PatternRecord? {
        return if (row in 0..<size) model.get(row) else null
    }

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    fun getPatternForLine(row: Int): JMLPattern? {
        val rec = model.get(row)
        if (rec.notation == null) {
            return null
        }

        val pat: JMLPattern?
        if (rec.notation.equals("jml", ignoreCase = true) && rec.patnode != null) {
            pat = JMLPattern(rec.patnode!!, loadingversion)
        } else if (rec.anim != null) {
            pat = JMLPattern.fromBasePattern(rec.notation, rec.anim)

            if (rec.info != null) {
                pat.info = rec.info
            }
            if (rec.tags != null) {
                for (tag in rec.tags) {
                    pat.addTag(tag)
                }
            }
        } else {
            return null
        }
        return pat
    }

    @Throws(JuggleExceptionUser::class)
    fun getAnimationPrefsForLine(row: Int): AnimationPrefs? {
        val rec = model.get(row)
        if (rec.animprefs == null) {
            return null
        }
        val ap = AnimationPrefs()
        val params = ParameterList(rec.animprefs)
        ap.fromParameters(params)
        params.errorIfParametersLeft()
        return ap
    }

    //--------------------------------------------------------------------------
    // Reader/writer methods
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionUser::class)
    fun readJML(root: JMLNode) {
        if (!root.nodeType.equals("jml", ignoreCase = true)) {
            throw JuggleExceptionUser(errorstrings.getString("Error_missing_JML_tag"))
        }

        val vers = root.attributes.getAttribute("version")
        if (vers != null) {
            if (compareVersions(vers, JMLDefs.CURRENT_JML_VERSION) > 0) {
                throw JuggleExceptionUser(errorstrings.getString("Error_JML_version"))
            }
            loadingversion = vers
        }

        val listnode = root.getChildNode(0)
        if (!listnode.nodeType.equals("patternlist", ignoreCase = true)) {
            throw JuggleExceptionUser(errorstrings.getString("Error_missing_patternlist_tag"))
        }

        var linenumber = 0

        for (i in 0..<listnode.numberOfChildren) {
            val child = listnode.getChildNode(i)
            if (child.nodeType.equals("title", ignoreCase = true)) {
                title = child.nodeValue!!.trim()
            } else if (child.nodeType.equals("info", ignoreCase = true)) {
                info = child.nodeValue!!.trim()
            } else if (child.nodeType.equals("line", ignoreCase = true)) {
                ++linenumber
                val attr = child.attributes
                val display = attr.getAttribute("display")
                val animprefs = attr.getAttribute("animprefs")
                val notation = attr.getAttribute("notation")
                var anim: String? = null
                var patnode: JMLNode? = null
                var infonode: JMLNode? = null

                if (notation != null) {
                    if (notation.equals("jml", ignoreCase = true)) {
                        patnode = child.findNode("pattern")
                        if (patnode == null) {
                            val template: String = errorstrings.getString("Error_missing_pattern")
                            val arguments = arrayOf<Any?>(linenumber)
                            throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                        }
                        infonode = patnode.findNode("info")
                    } else {
                        anim = child.nodeValue!!.trim()
                        infonode = child.findNode("info")
                    }
                }

                addLine(-1, display, animprefs, notation, anim, patnode, infonode)
            } else {
                throw JuggleExceptionUser(errorstrings.getString("Error_illegal_tag"))
            }
        }
    }

    @Throws(IOException::class)
    fun writeJML(wr: Writer) {
        val write = PrintWriter(wr)
        for (i in JMLDefs.jmlprefix.indices) {
            write.println(JMLDefs.jmlprefix[i])
        }

        write.println("<jml version=\"${xmlescape(version)}\">")
        write.println("<patternlist>")
        if (title != null && !title!!.isEmpty()) {
            write.println("<title>${xmlescape(title!!)}</title>")
        }
        if (info != null && !info!!.isEmpty()) {
            write.println("<info>${xmlescape(info!!)}</info>")
        }

        val empty = (model.size() == (if (BLANK_AT_END) 1 else 0))
        if (!empty) {
            write.println()
        }

        var previousLineWasAnimation = false

        for (i in 0..<(if (BLANK_AT_END) model.size() - 1 else model.size())) {
            val rec = model.get(i)
            var line = "<line display=\"${xmlescape(rec.display.trimEnd())}\""
            var hasAnimation = false

            if (rec.notation != null) {
                line += " notation=\"${xmlescape(rec.notation!!.lowercase(Locale.getDefault()))}\""
                hasAnimation = true
            }
            if (rec.animprefs != null) {
                line += " animprefs=\"${xmlescape(rec.animprefs!!)}\""
                hasAnimation = true
            }

            if (hasAnimation) {
                line += ">"
                if (i > 0) {
                    write.println()
                }
                write.println(line)

                if (rec.notation != null && rec.notation.equals("jml", ignoreCase = true) && rec.patnode != null) {
                    rec.patnode!!.writeNode(write, 0)
                } else if (rec.anim != null) {
                    write.println(xmlescape(rec.anim!!))
                    if (rec.info != null || (rec.tags != null && !rec.tags!!.isEmpty())) {
                        val tagstr = rec.tags?.joinToString(",") ?: ""
                        if (rec.info != null) {
                            if (tagstr.isEmpty()) {
                                write.println("<info>${xmlescape(rec.info!!)}</info>")
                            } else {
                                write.println(
                                    "<info tags=\"${xmlescape(tagstr)}\">${xmlescape(rec.info!!)}</info>"
                                )
                            }
                        } else {
                            write.println("<info tags=\"${xmlescape(tagstr)}\"/>")
                        }
                    }
                }

                write.println("</line>")
            } else {
                line += "/>"
                if (previousLineWasAnimation && i > 0) {
                    write.println()
                }
                write.println(line)
            }

            previousLineWasAnimation = hasAnimation
        }

        if (!empty) {
            write.println()
        }

        write.println("</patternlist>")
        write.println("</jml>")
        for (i in JMLDefs.jmlsuffix.indices) {
            write.println(JMLDefs.jmlsuffix[i])
        }
        write.flush()
    }

    @Throws(IOException::class)
    fun writeText(wr: Writer) {
        val write = PrintWriter(wr)
        for (i in 0..<(if (BLANK_AT_END) model.size() - 1 else model.size())) {
            val rec = model.get(i)
            write.println(rec.display)
        }
        write.flush()
    }

    //--------------------------------------------------------------------------
    // Record to encapsulate the data for a single line
    //--------------------------------------------------------------------------

    class PatternRecord {
        @JvmField
        var display: String
        var animprefs: String?
        var notation: String?
        @JvmField
        var anim: String? // if pattern is not in JML notation
        @JvmField
        var patnode: JMLNode? // if pattern is in JML
        var info: String?
        var tags: ArrayList<String>? = null

        constructor(
            dis: String,
            ap: String?,
            not: String?,
            ani: String?,
            pat: JMLNode?,
            inf: String?,
            t: ArrayList<String>?
        ) {
            display = dis
            animprefs = ap
            notation = not
            anim = ani
            patnode = pat
            info = inf
            tags = t
        }

        constructor(pr: PatternRecord) {
            display = pr.display
            animprefs = pr.animprefs
            notation = pr.notation
            anim = pr.anim
            patnode = pr.patnode
            info = pr.info

            if (pr.tags != null) {
                tags = ArrayList<String>()
                tags!!.addAll(pr.tags!!)
            } else {
                tags = null
            }
        }
    }

    companion object {
        // whether to maintain a blank line at the end of every pattern list,
        // so that items can be inserted at the end
        const val BLANK_AT_END: Boolean = true
    }
}
