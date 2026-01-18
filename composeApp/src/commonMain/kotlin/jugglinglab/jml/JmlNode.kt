//
// JmlNode.kt
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

class JmlNode(
    val nodeType: String?  // from taglist in JmlDefs.java
) {
    var nodeValue: String? = null  // for nodes with character content
    var attributes = JmlAttributes()
        private set
    var parentNode: JmlNode? = null
    var previousSibling: JmlNode? = null
    var nextSibling: JmlNode? = null
    var children: MutableList<JmlNode> = mutableListOf()

    fun addAttribute(name: String, value: String) {
        attributes = attributes.addAttribute(name, value)
    }
    
    fun addChildNode(newChild: JmlNode) {
        val lastNode = children.lastOrNull()
        lastNode?.nextSibling = newChild
        children.add(newChild)
        newChild.previousSibling = lastNode
        newChild.nextSibling = null
        newChild.parentNode = this
    }
    
    // Recursively traverse the node tree to find the first instance of a given
    // node type.
    
    fun findNode(type: String?): JmlNode? {
        if (nodeType == type) {
            return this
        }
        return children.firstNotNullOfOrNull { it.findNode(type) }
    }

    fun writeNode(appendable: Appendable, indentlevel: Int) {
        appendable.append("<").append(nodeType)
        
        for ((name, value) in attributes.entries) {
            appendable.append(" ")
                .append(name)
                .append("=\"")
                .append(xmlescape(value))
                .append("\"")
        }

        if (children.isEmpty()) {
            if (nodeValue == null) {
                appendable.append("/>")
            } else {
                appendable.append(">").append(xmlescape(nodeValue!!))
                    .append("</").append(nodeType).append(">")
            }
            appendable.appendLine()
        } else {
            appendable.append('>')
            appendable.appendLine()
            if (nodeValue != null) {
                appendable.append(xmlescape(nodeValue!!))
                appendable.appendLine()
            }
            for (child in children) {
                child.writeNode(appendable, indentlevel + 1)
            }
            appendable.append("</").append(nodeType).append(">")
            appendable.appendLine()
        }
    }

    companion object {
        fun xmlescape(str: String): String {
            var result = str.replace("&", "&amp;")
            result = result.replace("<", "&lt;")
            result = result.replace(">", "&gt;")
            result = result.replace("'", "&apos;")
            result = result.replace("\"", "&quot;")
            return result
        }
    }
}
