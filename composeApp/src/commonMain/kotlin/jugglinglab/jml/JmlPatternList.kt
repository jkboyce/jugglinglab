//
// JmlPatternList.kt
//
// This class represents a JML pattern list. This is the data model; the
// visualization is in PatternListPanel and PatternListWindow.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.core.AnimationPrefs
import jugglinglab.jml.JmlNode.Companion.xmlescape
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.ParameterList
import jugglinglab.util.jlGetStringResource
import jugglinglab.util.jlCompareVersions

class JmlPatternList(
    jmlNode: JmlNode? = null
) {
    var loadingJmlVersion: String = JmlDefs.CURRENT_JML_VERSION

    var title: String? = null
        set(t) {
            // by convention we don't allow title to be zero-length string "",
            // but use null instead
            field = if (t == null || t.trim().isEmpty()) null else t.trim()
        }

    var info: String? = null
        private set

    val model = mutableListOf<PatternRecord>()

    val size: Int
        get() = if (BLANK_AT_END) model.size - 1 else model.size

    val jlHashCode: Int
        get() {
            val sb = StringBuilder()
            writeJml(sb)
            return sb.toString().hashCode()
        }

    init {
        clearModel()
        if (jmlNode != null) {
            readJml(jmlNode)
        }
    }

    //--------------------------------------------------------------------------
    // Methods to define the pattern list
    //--------------------------------------------------------------------------

    fun clearModel() {
        model.clear()
        if (BLANK_AT_END) {
            model.add(PatternRecord(" ", null, null, null, null, null, null))
        }
    }

    fun addLine(
        row: Int,
        rec: PatternRecord
    ): Int {
        var index = row
        if (row < 0) {
            if (BLANK_AT_END) {
                index = model.size - 1
                model.add(model.size - 1, rec)
            } else {
                index = model.size
                model.add(rec)  // adds at end
            }
        } else {
            model.add(row, rec)
        }
        return index
    }

    @Suppress("unused")
    fun getLine(row: Int): PatternRecord? {
        return if (row in 0..<size) model[row] else null
    }

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    fun getPatternForLine(row: Int): JmlPattern? {
        val rec = model[row]
        if (rec.notation == null) {
            return null
        }

        val pat: JmlPattern?
        if (rec.notation.equals("jml", ignoreCase = true) && rec.patnode != null) {
            pat = JmlPattern.fromJmlNode(rec.patnode!!, loadingJmlVersion)
        } else if (rec.anim != null) {
            pat = JmlPattern.fromBasePattern(rec.notation!!, rec.anim!!)

            val record = PatternBuilder.fromJmlPattern(pat)
            if (rec.info != null) {
                record.info = rec.info
            }
            if (rec.tags != null) {
                record.tags.addAll(rec.tags!!)
            }
        } else {
            return null
        }
        return pat
    }

    @Throws(JuggleExceptionUser::class)
    fun getAnimationPrefsForLine(row: Int): AnimationPrefs? {
        val rec = model[row]
        if (rec.animprefs == null) {
            return null
        }
        val params = ParameterList(rec.animprefs)
        val ap = AnimationPrefs.fromParameters(params)
        params.errorIfParametersLeft()
        return ap
    }

    //--------------------------------------------------------------------------
    // Reader/writer methods
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionUser::class)
    private fun readJml(root: JmlNode) {
        var current: JmlNode = root

        if (current.nodeType.equals("#root")) {
            current = current.children.find {
                it.nodeType.equals("jml", ignoreCase = true)
            } ?: throw JuggleExceptionUser(jlGetStringResource(Res.string.error_missing_jml_tag))
        }

        if (!current.nodeType.equals("jml", ignoreCase = true)) {
            val message = jlGetStringResource(Res.string.error_missing_jml_tag)
            throw JuggleExceptionUser(message)
        }

        val vers = current.attributes.getValueOf("version")
        if (vers != null) {
            if (jlCompareVersions(vers, JmlDefs.CURRENT_JML_VERSION) > 0) {
                val message = jlGetStringResource(Res.string.error_jml_version)
                throw JuggleExceptionUser(message)
            }
            loadingJmlVersion = vers
        }

        val listnode = current.children[0]
        if (!listnode.nodeType.equals("patternlist", ignoreCase = true)) {
            val message = jlGetStringResource(Res.string.error_missing_patternlist_tag)
            throw JuggleExceptionUser(message)
        }

        var linenumber = 0

        for (child in listnode.children) {
            if (child.nodeType.equals("title", ignoreCase = true)) {
                title = child.nodeValue!!.trim()
            } else if (child.nodeType.equals("info", ignoreCase = true)) {
                info = child.nodeValue!!.trim()
            } else if (child.nodeType.equals("line", ignoreCase = true)) {
                ++linenumber
                val attr = child.attributes
                val display = attr.getValueOf("display")
                val animprefs = attr.getValueOf("animprefs")
                val notation = attr.getValueOf("notation")
                var anim: String? = null
                var patnode: JmlNode? = null
                var infonode: JmlNode? = null

                if (notation != null) {
                    if (notation.equals("jml", ignoreCase = true)) {
                        patnode = child.findNode("pattern")
                        if (patnode == null) {
                            val message = jlGetStringResource(
                                Res.string.error_missing_pattern,
                                linenumber
                            )
                            throw JuggleExceptionUser(message)

                        }
                        infonode = patnode.findNode("info")
                    } else {
                        anim = child.nodeValue!!.trim()
                        infonode = child.findNode("info")
                    }
                }

                val rec = PatternRecord(
                    display, animprefs, notation, anim, patnode, infonode
                )
                addLine(-1, rec)
            } else {
                val message = jlGetStringResource(Res.string.error_illegal_tag)
                throw JuggleExceptionUser(message)
            }
        }
    }

    fun writeJml(wr: Appendable) {
        JmlDefs.jmlPrefix.forEach { wr.append(it).append('\n') }
        wr.append("<jml version=\"${xmlescape(JmlDefs.CURRENT_JML_VERSION)}\">\n")
        wr.append("<patternlist>\n")
        if (title?.isNotEmpty() ?: false) {
            wr.append("<title>${xmlescape(title!!)}</title>\n")
        }
        if (info?.isNotEmpty() ?: false) {
            wr.append("<info>${xmlescape(info!!)}</info>\n")
        }

        val empty = (model.size == if (BLANK_AT_END) 1 else 0)
        if (!empty) {
            wr.append('\n')
        }

        var previousLineWasAnimation = false

        for (i in 0..<(if (BLANK_AT_END) model.size - 1 else model.size)) {
            val rec = model[i]
            var line = "<line display=\"${xmlescape(rec.display.trimEnd())}\""
            var hasAnimation = false

            if (rec.notation != null) {
                line += " notation=\"${xmlescape(rec.notation!!.lowercase())}\""
                hasAnimation = true
            }
            if (rec.animprefs != null) {
                line += " animprefs=\"${xmlescape(rec.animprefs!!)}\""
                hasAnimation = true
            }

            if (hasAnimation) {
                line += ">"
                if (i > 0) {
                    wr.append('\n')
                }
                wr.append(line).append('\n')

                if (rec.notation != null && rec.notation.equals("jml", ignoreCase = true) && rec.patnode != null) {
                    rec.patnode!!.writeNode(wr, 0)
                } else if (rec.anim != null) {
                    wr.append(xmlescape(rec.anim!!)).append('\n')
                    if (rec.info != null || (rec.tags != null && !rec.tags!!.isEmpty())) {
                        val tagstr = rec.tags?.joinToString(",") ?: ""
                        if (rec.info != null) {
                            if (tagstr.isEmpty()) {
                                wr.append("<info>${xmlescape(rec.info!!)}</info>\n")
                            } else {
                                wr.append(
                                    "<info tags=\"${xmlescape(tagstr)}\">${xmlescape(rec.info!!)}</info>\n"
                                )
                            }
                        } else {
                            wr.append("<info tags=\"${xmlescape(tagstr)}\"/>\n")
                        }
                    }
                }

                wr.append("</line>\n")
            } else {
                line += "/>"
                if (previousLineWasAnimation && i > 0) {
                    wr.append('\n')
                }
                wr.append(line).append('\n')
            }

            previousLineWasAnimation = hasAnimation
        }

        if (!empty) {
            wr.append('\n')
        }

        wr.append("</patternlist>\n")
        wr.append("</jml>\n")
        JmlDefs.jmlSuffix.forEach { wr.append(it).append('\n') }
    }

    fun writeText(wr: Appendable) {
        for (i in 0..<(if (BLANK_AT_END) model.size - 1 else model.size)) {
            wr.append(model[i].display).append('\n')
        }
    }

    //--------------------------------------------------------------------------
    // Record to encapsulate the data for a single line
    //--------------------------------------------------------------------------

    class PatternRecord {
        var display: String
        var animprefs: String?
        var notation: String?
        var anim: String?  // if pattern is not in JML notation
        var patnode: JmlNode?  // if pattern is in JML
        var info: String?
        var tags: MutableList<String>? = null

        constructor(
            dis: String,
            ap: String?,
            not: String?,
            ani: String?,
            pat: JmlNode?,
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

        // Copy constructor.

        constructor(pr: PatternRecord) {
            display = pr.display
            animprefs = pr.animprefs
            notation = pr.notation
            anim = pr.anim
            patnode = pr.patnode
            info = pr.info

            if (pr.tags != null) {
                tags = mutableListOf()
                tags!!.addAll(pr.tags!!)
            } else {
                tags = null
            }
        }

        // Convenience constructor for during JML parsing.

        constructor(
            dis: String?,
            ap: String?,
            not: String?,
            ani: String?,
            pnode: JmlNode?,
            inode: JmlNode?
        ) {
            display = dis ?: ""
            animprefs = ap?.trim()
            notation = not?.trim()
            anim = ani?.trim()
            patnode = pnode

            if (inode == null) {
                info = null
                tags = null
                return
            }

            val infoString = inode.nodeValue
            info = if (!infoString.isNullOrBlank()) {
                infoString.trim()
            } else null

            val tagstr = inode.attributes.getValueOf("tags")
            if (tagstr == null) {
                tags = null
                return
            }

            val infotags: ArrayList<String> = ArrayList()
            tagstr.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { t ->
                    if (infotags.none { it.equals(t, ignoreCase = true) }) {
                        infotags.add(t)
                    }
                }
            tags = infotags
        }
    }

    companion object {
        // whether to maintain a blank line at the end of every pattern list,
        // so that items can be inserted at the end
        const val BLANK_AT_END: Boolean = true
    }
}
