//
// JMLParser.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("KotlinConstantConditions")

package jugglinglab.jml

import jugglinglab.core.Constants
import jugglinglab.util.JuggleExceptionUser
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.parser.Parser
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.select.NodeVisitor
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import com.fleeksoft.ksoup.nodes.Comment

class JMLParser {
    var tree: JMLNode? = null  // tree of JML tags
        private set

    // Parse JML and populate the tree of JMLNodes.

    @Throws(JuggleExceptionUser::class)
    fun parse(xmlString: String) {
        if (Constants.DEBUG_JML_PARSING) {
            println("--------------------------------------------")
            println("Starting JMLParser.parse()...")
        }

        val doc = Ksoup.parse(
            html = xmlString,
            parser = Parser.xmlParser().setTrackPosition(true)
        )
        val builder = JMLTreeBuilder()
        doc.traverse(builder)
        tree = builder.root

        if (Constants.DEBUG_JML_PARSING) {
            println("JML parsing complete")
            println("--------------------------------------------")
        }
    }

    // Return the type of JML file parsed.

    val fileType: Int
        get() {
            var current: JMLNode = tree!!

            if (current.nodeType.equals("#root")) {
                current = current.children.find {
                    it.nodeType.equals("jml", ignoreCase = true)
                } ?: return JML_INVALID
            }

            return if (current.nodeType.equals("jml", ignoreCase = true)) {
                if (current.children.any {
                        it.nodeType.equals(
                            "pattern",
                            ignoreCase = true
                        )
                    }) {
                    JML_PATTERN
                } else if (current.children.any {
                        it.nodeType.equals(
                            "patternlist",
                            ignoreCase = true
                        )
                    }) {
                    JML_LIST
                } else {
                    JML_INVALID
                }
            } else {
                JML_INVALID
            }
        }

    companion object {
        // JML file types
        const val JML_INVALID: Int = 0
        const val JML_PATTERN: Int = 1
        const val JML_LIST: Int = 2
    }
}

// Helper class to build the tree of JMLNodes.

class JMLTreeBuilder : NodeVisitor {
    var root: JMLNode? = null

    // stack to track the current parent Element
    private val stack = ArrayDeque<JMLNode>()

    override fun head(node: Node, depth: Int) {
        if (node is Element) {
            if (Constants.DEBUG_JML_PARSING) {
                val start = node.sourceRange().start()
                println(
                    "Read element: <${node.tagName()}> at line " +
                        "${start.lineNumber()}, column ${start.columnNumber()}"
                )
            }

            val myNode = JMLNode(node.tagName())
            node.attributes().forEach { myNode.addAttribute(it.key, it.value) }

            if (stack.isNotEmpty()) {
                stack.last().addChildNode(myNode)
            } else {
                root = myNode
            }

            stack.addLast(myNode)
        } else if (node is TextNode) {
            if (Constants.DEBUG_JML_PARSING) {
                val start = node.sourceRange().start()
                println(
                    "Read text node: ${node.text()} at line " +
                        "${start.lineNumber()}, column ${start.columnNumber()}"
                )
            }

            val text = node.text().trim()
            if (text.isNotEmpty() && stack.isNotEmpty()) {
                // append text to the current parent in the stack
                stack.last().nodeValue = text
            }
        } else if (node is Comment) {
            if (Constants.DEBUG_JML_PARSING) {
                val start = node.sourceRange().start()
                println(
                    "Read comment node: ${node.getData()} at line " +
                        "${start.lineNumber()}, column ${start.columnNumber()}"
                )
            }

            // add comment as leaf child to the parent Element
            val myNode = JMLNode("comment")
            myNode.nodeValue = node.getData()

            if (stack.isNotEmpty()) {
                stack.last().addChildNode(myNode)
            } else {
                root = myNode
            }
        }
    }

    override fun tail(node: Node, depth: Int) {
        if (node is Element) {
            stack.removeLast()
        }
    }
}
