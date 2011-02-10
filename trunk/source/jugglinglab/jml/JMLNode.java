// JMLNode.java
//
// Copyright 2004 by Jack Boyce (jboyce@users.sourceforge.net) and others

/*
    This file is part of Juggling Lab.

    Juggling Lab is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    Juggling Lab is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Juggling Lab; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package jugglinglab.jml;

import java.util.Vector;
import java.io.*;

import jugglinglab.util.*;


public class JMLNode {
    protected String nodeType;		// from taglist in JMLDefs.java
    protected String nodeValue;		// nodes with character content
    protected JMLNode parentNode;
    protected Vector childNodes;
    protected JMLNode previousSibling;
    protected JMLNode nextSibling;
    protected JMLAttributes attributes;

    public JMLNode(String nodeType) {
        this.nodeType = nodeType;
        childNodes = new Vector();
        attributes = new JMLAttributes(this);
    }

    public String getNodeType() { return nodeType; }
    public String getNodeValue() { return nodeValue; }
    public void setNodeValue(String nodeValue) {
        this.nodeValue = nodeValue;
    }

    public JMLNode getParentNode() { return parentNode; }
    public void setParentNode(JMLNode parent) { parentNode = parent; }

    public int getNumberOfChildren() { return childNodes.size(); }

    public JMLNode getChildNode(int index) {
        return (JMLNode)childNodes.elementAt(index);
    }

    public JMLNode getFirstChild() { return (JMLNode)childNodes.firstElement(); }
    public JMLNode getLastChild() { return (JMLNode)childNodes.lastElement(); }

    public JMLNode getPreviousSibling() { return previousSibling; }
    public void setPreviousSibling(JMLNode sibling) {
        this.previousSibling = sibling;
    }

    public JMLNode getNextSibling() { return nextSibling; }
    public void setNextSibling(JMLNode sibling) { this.nextSibling = sibling; }


    public void addAttribute(String name, String value) {
        attributes.addAttribute(name, value);
    }

    public JMLAttributes getAttributes() { return attributes; }

    // Inserts a child node newChild before the existing child node refChild.
    public void insertBefore(JMLNode newChild, JMLNode refChild) {
        if (refChild != null) {
            int refindex = childNodes.indexOf(refChild);
            childNodes.insertElementAt(newChild, refindex);
            // now fix references
            newChild.setParentNode(this);
            JMLNode prevsibling = refChild.getPreviousSibling();
            if (prevsibling != null)
                prevsibling.setNextSibling(newChild);
            newChild.setPreviousSibling(prevsibling);
            newChild.setNextSibling(refChild);
            refChild.setPreviousSibling(newChild);
        } else {
            this.appendChild(newChild);
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
                childNodes.setElementAt(newChild, refindex);
                newChild.setPreviousSibling(prev);
                newChild.setNextSibling(next);
                newChild.setParentNode(this);
            } else
                throw new JuggleExceptionInternal("Node to replace doesn't exist");
        }

    // Removes the child node indicated by oldChild from the list of children.
    public void removeChild(JMLNode oldChild) throws JuggleExceptionInternal {
        if (childNodes.removeElement(oldChild)) {
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
            lastnode = (JMLNode)childNodes.lastElement();
            lastnode.setNextSibling(newChild);
        }
        childNodes.addElement(newChild);
        newChild.setPreviousSibling(lastnode);
        newChild.setNextSibling(null);
        newChild.setParentNode(this);
    }

    public boolean hasChildNodes() { return (childNodes.size() != 0); }

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

