//
// JMLNode.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

import java.io.PrintWriter

class JMLNode(
    @JvmField var nodeType: String?  // from taglist in JMLDefs.java
) {
    @JvmField var nodeValue: String? = null  // for nodes with character content
    var attributes = JMLAttributes(this)
        private set
    @JvmField var parentNode: JMLNode? = null
    private var childNodes: MutableList<JMLNode> = ArrayList<JMLNode>()
    var previousSibling: JMLNode? = null
    var nextSibling: JMLNode? = null
    
    fun addAttribute(name: String, value: String) {
        attributes.addAttribute(name, value)
    }
    
    val numberOfChildren: Int
        get() = childNodes.size

    fun getChildNode(index: Int) = childNodes[index]
    
    fun addChildNode(newChild: JMLNode) {
        val lastNode = childNodes.lastOrNull()
        lastNode?.nextSibling = newChild
        childNodes.add(newChild)
        newChild.previousSibling = lastNode
        newChild.nextSibling = null
        newChild.parentNode = this
    }
    
    // Recursively traverse the node tree to find the first instance of a given
    // node type.
    
    fun findNode(type: String?): JMLNode? {
        if (this.nodeType == type) {
            return this
        }

        for (i in 0..<this.numberOfChildren) {
            val match = getChildNode(i).findNode(type)
            if (match != null) {
                return match
            }
        }
        return null
    }

    fun writeNode(write: PrintWriter, indentlevel: Int) {
        var i: Int
        var sb = StringBuilder()
        
        sb.append("<").append(nodeType)
        // output attributes
        i = 0
        while (i < attributes.numberOfAttributes) {
            sb.append(" ").append(attributes.getAttributeName(i))
                .append("=\"").append(xmlescape(attributes.getAttributeValue(i)))
                .append("\"")
            i++
        }

        if (this.numberOfChildren == 0) {
            if (nodeValue == null) {
                sb.append("/>")
            } else {
                sb.append(">").append(xmlescape(nodeValue!!))
                    .append("</").append(nodeType).append(">")
            }
            write.println(sb)
        } else {
            sb.append('>')
            write.println(sb)
            sb = StringBuilder()

            if (nodeValue != null) {
                sb.append(xmlescape(nodeValue!!))
                write.println(sb)
                sb = StringBuilder()
            }
            write.flush()

            i = 0
            while (i < this.numberOfChildren) {
                getChildNode(i).writeNode(write, indentlevel + 1)
                i++
            }
            
            sb.append("</").append(nodeType).append(">")
            write.println(sb)
        }
        write.flush()
    }

    companion object {
        @JvmStatic
        fun xmlescape(`in`: String): String {
            var result = `in`.replace("&", "&amp;")
            result = result.replace("<", "&lt;")
            result = result.replace(">", "&gt;")
            result = result.replace("'", "&apos;")
            result = result.replace("\"", "&quot;")
            return result
        }
    }
}
