// JMLNode.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.jml;

import java.util.*;
import java.io.*;

import jugglinglab.util.*;


public class JMLNode {
    protected String nodeType;  // from taglist in JMLDefs.java
    protected String nodeValue;  // nodes with character content
    protected JMLNode parentNode;
    protected ArrayList<JMLNode> childNodes;
    protected JMLNode previousSibling;
    protected JMLNode nextSibling;
    protected JMLAttributes attributes;


    public JMLNode(String nodeType) {
        this.nodeType = nodeType;
        childNodes = new ArrayList<JMLNode>();
        attributes = new JMLAttributes(this);
    }

    public String getNodeType() {
        return nodeType;
    }

    public String getNodeValue() {
        return nodeValue;
    }

    public void setNodeValue(String nodeValue) {
        this.nodeValue = nodeValue;
    }

    public JMLNode getParentNode() {
        return parentNode;
    }

    public void setParentNode(JMLNode parent) {
        parentNode = parent;
    }

    public int getNumberOfChildren() {
        return childNodes.size();
    }

    public JMLNode getChildNode(int index) {
        return childNodes.get(index);
    }

    public JMLNode getFirstChild() {
        return childNodes.get(0);
    }

    public JMLNode getLastChild() {
        int n = childNodes.size();
        if (n > 0)
            return childNodes.get(n-1);
        return null;
    }

    public JMLNode getPreviousSibling() {
        return previousSibling;
    }

    public void setPreviousSibling(JMLNode sibling) {
        previousSibling = sibling;
    }

    public JMLNode getNextSibling() {
        return nextSibling;
    }

    public void setNextSibling(JMLNode sibling) {
        nextSibling = sibling;
    }

    public void addAttribute(String name, String value) {
        attributes.addAttribute(name, value);
    }

    public JMLAttributes getAttributes() { return attributes; }

    // Inserts a child node newChild before the existing child node refChild.
    public void insertBefore(JMLNode newChild, JMLNode refChild) {
        if (refChild != null) {
            int refindex = childNodes.indexOf(refChild);
            childNodes.add(refindex, newChild);
            // now fix references
            newChild.setParentNode(this);
            JMLNode prevsibling = refChild.getPreviousSibling();
            if (prevsibling != null)
                prevsibling.setNextSibling(newChild);
            newChild.setPreviousSibling(prevsibling);
            newChild.setNextSibling(refChild);
            refChild.setPreviousSibling(newChild);
        } else {
            appendChild(newChild);
        }
    }

    // Replaces the child node oldChild with newChild in the set of children
    // of the given node.
    public void replaceChild(JMLNode newChild, JMLNode oldChild)
        throws JuggleExceptionInternal {
            if (childNodes.contains(oldChild)) {
                int refindex = childNodes.indexOf(oldChild);
                JMLNode prev = oldChild.getPreviousSibling();
                JMLNode next = oldChild.getNextSibling();
                childNodes.set(refindex, newChild);
                newChild.setPreviousSibling(prev);
                newChild.setNextSibling(next);
                newChild.setParentNode(this);
            } else
                throw new JuggleExceptionInternal("Node to replace doesn't exist");
        }

    // Removes the child node indicated by oldChild from the list of children.
    public void removeChild(JMLNode oldChild) throws JuggleExceptionInternal {
        if (childNodes.remove(oldChild)) {
            JMLNode prev = oldChild.getPreviousSibling();
            JMLNode next = oldChild.getNextSibling();
            if (prev != null)
                prev.setNextSibling(next);
            if (next != null)
                next.setPreviousSibling(prev);
        } else
            throw new JuggleExceptionInternal("Node to remove doesn't exist");
    }

    public void appendChild(JMLNode newChild) {
        JMLNode lastnode = null;

        if (childNodes.size() != 0) {
            lastnode = childNodes.get(childNodes.size() - 1);
            lastnode.setNextSibling(newChild);
        }
        childNodes.add(newChild);
        newChild.setPreviousSibling(lastnode);
        newChild.setNextSibling(null);
        newChild.setParentNode(this);
    }

    public boolean hasChildNodes() { return (childNodes.size() != 0); }

    // Recursively traverse the node tree to find the first instance of a given
    // node type
    public JMLNode findNode(String type) {
        if (getNodeType().equals(type))
            return this;

        for (int i = 0; i < getNumberOfChildren(); ++i) {
            JMLNode match = getChildNode(i).findNode(type);
            if (match != null)
                return match;
        }

        return null;
    }

    public void writeNode(PrintWriter write, int indentlevel) throws IOException {
        int i;
        StringBuffer result = new StringBuffer();

        /*
            for (i = 0; i < indentlevel; i++)
         result.append('\t');
         */

        result.append("<" + nodeType);
        // output attributes
        for (i = 0; i < attributes.getNumberOfAttributes(); i++) {
            result.append(" " + attributes.getAttributeName(i));
            result.append("=\"" + JMLNode.xmlescape(attributes.getAttributeValue(i)) + "\"");
        }

        if (getNumberOfChildren() == 0) {
            if (nodeValue == null)
                result.append("/>");
            else
                result.append(">" + JMLNode.xmlescape(nodeValue) + "</" + nodeType + ">");
            write.println(result.toString());
            result = new StringBuffer();
        } else {
            result.append('>');
            write.println(result.toString());
            result = new StringBuffer();

            if (nodeValue != null) {
                /*
                 for (i = 0; i <= indentlevel; i++)
                 result.append('\t');
                 */
                result.append(JMLNode.xmlescape(nodeValue));
                write.println(result.toString());
                result = new StringBuffer();
            }
            write.flush();

            for (i = 0; i < getNumberOfChildren(); i++)
                getChildNode(i).writeNode(write, indentlevel + 1);

            /*
             for (i = 0; i < indentlevel; i++)
             result.append('\t');
             */
            result.append("</" + nodeType + ">");
            write.println(result.toString());
        }
        write.flush();
    }

    public static String xmlescape(String in) {
        String result = in.replace("&", "&amp;");
        result = result.replace("<", "&lt;");
        result = result.replace(">", "&gt;");
        result = result.replace("'", "&apos;");
        result = result.replace("\"", "&quot;");
        return result;
    }
}

