//
// JMLNode.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

class JMLNode(
    val nodeType: String?  // from taglist in JMLDefs.java
) {
    var nodeValue: String? = null  // for nodes with character content
    var attributes = JMLAttributes()
        private set
    var parentNode: JMLNode? = null
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

        for (i in 0..<numberOfChildren) {
            val match = getChildNode(i).findNode(type)
            if (match != null) {
                return match
            }
        }
        return null
    }

    fun writeNode(appendable: Appendable, indentlevel: Int) {
        appendable.append("<").append(nodeType)
        for (i in 0..<attributes.numberOfAttributes) {
            appendable.append(" ").append(attributes.getAttributeName(i))
                .append("=\"").append(xmlescape(attributes.getAttributeValue(i)))
                .append("\"")
        }

        if (numberOfChildren == 0) {
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
            for (child in childNodes) {
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
