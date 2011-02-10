// JMLParser.java
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

import java.io.*;
import java.net.*;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import jugglinglab.util.*;


public class JMLParser extends DefaultHandler {
    protected boolean patternStarted;
    protected boolean patternFinished;

    protected JMLNode rootNode;			// tree of JML tags
    protected JMLNode currentNode;

    protected static final boolean saxdebug = false;	// print messages during parsing?

    public static final int JML_INVALID = 0;
    public static final int JML_PATTERN = 1;
    public static final int JML_LIST = 2;


    public JMLParser() {
        patternStarted = patternFinished = false;
    }

    // --------------- call parser to create pattern from XML -----------------


    public void parse(Reader read) throws SAXException, IOException {
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setValidating(true);
			SAXParser parser = factory.newSAXParser();
			
			// Parse the document.
			parser.parse(new InputSource(read), this);
		} catch (ParserConfigurationException pce) {
			throw new SAXException(pce.getMessage());
		}
	}


    // Implementation of org.xml.sax.EntityResolver
	
    public InputSource resolveEntity(String publicId, String systemId) {
        if (saxdebug) {
            System.out.print("Resolve entity:");
            if (publicId != null)
                System.out.print(" publicId=\"" + publicId + '"');
            System.out.println(" systemId=\"" + systemId + '"');
        }
		
        if (systemId.equalsIgnoreCase("file://jml.dtd")) {
            if (saxdebug) {
                System.out.println("--------- jml.dtd -----------");
                System.out.println(JMLDefs.jmldtd);
                System.out.println("-----------------------------");
            }
            return new InputSource(new StringReader(JMLDefs.jmldtd));
        }
        return null;
    }
	
    // Implementation of org.xml.sax.DTDHandler
	
    public void notationDecl(String name, String publicId, String systemId) {
        if (saxdebug) {
            System.out.print("Notation declaration: " + name);
            if (publicId != null)
                System.out.print(" publicId=\"" + publicId + '"');
            if (systemId != null)
                System.out.print(" systemId=\"" + systemId + '"');
            System.out.print('\n');
        }
    }
	
    public void unparsedEntityDecl(String name,
                                   String publicId,
                                   String systemId,
                                   String notationName) {
        if (saxdebug) {
            System.out.print("Unparsed Entity Declaration: " + name);
            if (publicId != null)
                System.out.print(" publicId=\"" + publicId + '"');
            if (systemId != null)
                System.out.print(" systemId=\"" + systemId + '"');
            System.out.println(" notationName=\"" + notationName + '"');
        }
    }
	
    // Implementation of org.xml.sax.ContentHandler
	
    public void setDocumentLocator(Locator locator) {
		super.setDocumentLocator(locator);
        if (saxdebug)
            System.out.println("Document locator supplied.");
    }
	
    public void startDocument() throws SAXException {
        if (saxdebug)
            System.out.println("Start document");
        try {
            startJMLPattern();
        } catch (JuggleException je) {
            throw new SAXException(je.getMessage());
        }
    }
	
    public void endDocument() throws SAXException {
        if (saxdebug)
            System.out.println("End document");
        try {
            endJMLPattern();
        } catch (JuggleException je) {
            throw new SAXException(je.getMessage());
        }
    }
	
	public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
        if (saxdebug) {
            System.out.println("Start element: " + qName);
            for (int i = 0; i < atts.getLength(); i++) {
                System.out.println("  Attribute: " +
                                   atts.getQName(i) + ' ' +
                                   atts.getType(i) + " \"" +
                                   atts.getValue(i) + '"');
            }
        }
        try {
            startJMLElement(qName);
            for (int i = 0; i < atts.getLength(); i++)
                addJMLAttribute(atts.getQName(i), atts.getValue(i));
        } catch (JuggleException je) {
            throw new SAXException(je.getMessage());
        }
    }
	
	public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
        if (saxdebug)
            System.out.println("End element: " + qName);
        try {
            endJMLElement(qName);
        } catch (JuggleException je) {
            throw new SAXException(je.getMessage());
        }
    }
	
    public void characters(char ch[], int start, int length) throws SAXException {
        if (saxdebug) {
            System.out.print("Characters: ");
            display(ch, start, length);
        }
        try {
            addJMLText(new String(ch, start, length));
        } catch (JuggleException je) {
            throw new SAXException(je.getMessage());
        }
    }
	
    public void ignorableWhitespace(char ch[], int start, int length) {
        if (saxdebug) {
            System.out.print("Ignorable Whitespace: ");
            display(ch, start, length);
        }
    }
	
    public void processingInstruction(String target, String data) {
        if (saxdebug)
            System.out.println("Processing instruction: " + target + ' ' + data);
    }
	
    // Implementation of org.xml.sax.ErrorHandler
	
    public void warning(SAXParseException exception) throws SAXException {
        //		throw new SAXException("Warning: " +
        //					exception.getMessage() + " (" +
        //					exception.getSystemId() + ':' +
        //					exception.getLineNumber() + ',' +
        //					exception.getColumnNumber() + ')');
		throw exception;
    }
	
    public void error(SAXParseException exception) throws SAXException {
        //		throw new SAXException("Recoverable Error: " +
        //					exception.getMessage() + " (" +
        //					exception.getSystemId() + ':' +
        //					exception.getLineNumber() + ',' +
        //					exception.getColumnNumber() + ')');
        throw exception;
    }
	
    public void fatalError(SAXParseException exception) throws SAXException {
        //		throw new SAXException("Fatal Error: " +
        //					exception.getMessage() + " (" +
        //					exception.getSystemId() + ':' +
        //					exception.getLineNumber() + ',' +
        //					exception.getColumnNumber() + ')');
        throw exception;
    }
	
	
    // Utility routines.

    /**
        * Display text, escaping some characters.
     */
    private static void display(char ch[], int start, int length) {
        if (saxdebug) {
            for (int i = start; i < start + length; i++) {
                switch (ch[i]) {
                    case '\n':
                        System.out.print("\\n");
                        break;
                    case '\t':
                        System.out.print("\\t");
                        break;
                    default:
                        System.out.print(ch[i]);
                        break;
                }
            }
            System.out.print("\n");
        }
    }

    // ------------------------------------------------------------------------
    //   Methods for pattern creation, used to create the JMLNode tree.
    // ------------------------------------------------------------------------

    public void startJMLPattern() throws JuggleExceptionInternal {
        if (patternStarted)
            throw new JuggleExceptionInternal("startJMLPattern(): pattern already started");
        patternStarted = true;
    }

    public void startJMLElement(String name) throws JuggleExceptionInternal {
        if (!patternStarted)
            throw new JuggleExceptionInternal("startJMLEleent(): pattern not started");
        if (patternFinished)
            throw new JuggleExceptionInternal("startJMLElement(): pattern already finished");
        if ((currentNode == null) && (rootNode != null))
            throw new JuggleExceptionInternal("startJMLElement(): can only have one root element");

        JMLNode newNode = new JMLNode(name);
        if (currentNode != null) {
            currentNode.appendChild(newNode);
            currentNode = newNode;
        } else
            rootNode = currentNode = newNode;
    }

    public void endJMLElement(String name) throws JuggleExceptionInternal {
        if (!patternStarted)
            throw new JuggleExceptionInternal("endJMLElement(): pattern not started");
        if (patternFinished)
            throw new JuggleExceptionInternal("endJMLElement(): pattern already finished");
        if (currentNode == null)
            throw new JuggleExceptionInternal("endJMLElement(): no correspanding startElement()");
        currentNode = currentNode.getParentNode();
    }

    public void addJMLAttribute(String name, String value) throws JuggleExceptionInternal {
        if (!patternStarted)
            throw new JuggleExceptionInternal("addJMLAttribute(): pattern not started");
        if (patternFinished)
            throw new JuggleExceptionInternal("addJMLAttribute(): pattern already finished");
        if (currentNode == null)
            throw new JuggleExceptionInternal("addJMLAttribute(): no element to add to");

        currentNode.addAttribute(name, value);
    }

    public void addJMLText(String text) throws JuggleExceptionInternal {
        if (!patternStarted)
            throw new JuggleExceptionInternal("addJMLText(): pattern not started");
        if (patternFinished)
            throw new JuggleExceptionInternal("addJMLText(): pattern already finished");
        if (currentNode == null)
            throw new JuggleExceptionInternal("addJMLText(): no element to add to");

		String newvalue = null;
		if (currentNode.getNodeValue() == null)
			newvalue = text;
		else
			newvalue = currentNode.getNodeValue() + text;
        currentNode.setNodeValue(newvalue);
    }

    public void endJMLPattern() throws JuggleExceptionInternal {
        if (!patternStarted)
            throw new JuggleExceptionInternal("endJMLPattern(): pattern not started");
        if (patternFinished)
            throw new JuggleExceptionInternal("endJMLPattern(): pattern already finished");
        if (rootNode == null)
            throw new JuggleExceptionInternal("endJMLPattern(): empty pattern");
        if (currentNode != null)
            throw new JuggleExceptionInternal("endJMLPattern(): missing endElement()");

        patternFinished = true;
    }

    public JMLNode getTree()	{ return rootNode; }

    public int getFileType() {
        if (rootNode.getNodeType().equalsIgnoreCase("jml")) {
            if (rootNode.getNumberOfChildren() == 1) {
                String child = rootNode.getChildNode(0).getNodeType();

                if (child.equalsIgnoreCase("pattern"))
                    return JML_PATTERN;
                else if (child.equalsIgnoreCase("patternlist"))
                    return JML_LIST;
                else
                    return JML_INVALID;
            } else
                return JML_INVALID;
        } else
            return JML_INVALID;
    }
}
