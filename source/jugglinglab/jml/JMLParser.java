// JMLParser.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.jml;

import java.io.*;
import java.net.*;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import jugglinglab.core.Constants;
import jugglinglab.util.*;


public class JMLParser extends DefaultHandler {
    protected boolean patternStarted;
    protected boolean patternFinished;

    protected JMLNode rootNode;  // tree of JML tags
    protected JMLNode currentNode;

    public static final int JML_INVALID = 0;
    public static final int JML_PATTERN = 1;
    public static final int JML_LIST = 2;


    public JMLParser() {
        patternStarted = patternFinished = false;
    }

    // --------------- call parser to create pattern from XML -----------------

    public void parse(Reader read) throws SAXException, IOException {
        try {
            if (Constants.DEBUG_JML_PARSING)
                System.out.println("Starting JMLParser.parse()...");
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

    @Override
    public InputSource resolveEntity(String publicId, String systemId) {
        if (Constants.DEBUG_JML_PARSING) {
            System.out.print("Resolve entity:");
            if (publicId != null)
                System.out.print(" publicId=\"" + publicId + '"');
            System.out.println(" systemId=\"" + systemId + '"');
        }

        if (systemId.equalsIgnoreCase("file://jml.dtd")) {
            if (Constants.DEBUG_JML_PARSING) {
                System.out.println("--------- jml.dtd -----------");
                System.out.println(JMLDefs.jmldtd);
                System.out.println("-----------------------------");
            }
            return new InputSource(new StringReader(JMLDefs.jmldtd));
        }
        return null;
    }

    // Implementation of org.xml.sax.DTDHandler

    @Override
    public void notationDecl(String name, String publicId, String systemId) {
        if (Constants.DEBUG_JML_PARSING) {
            System.out.print("Notation declaration: " + name);
            if (publicId != null)
                System.out.print(" publicId=\"" + publicId + '"');
            if (systemId != null)
                System.out.print(" systemId=\"" + systemId + '"');
            System.out.print('\n');
        }
    }

    @Override
    public void unparsedEntityDecl(String name,
                                   String publicId,
                                   String systemId,
                                   String notationName) {
        if (Constants.DEBUG_JML_PARSING) {
            System.out.print("Unparsed Entity Declaration: " + name);
            if (publicId != null)
                System.out.print(" publicId=\"" + publicId + '"');
            if (systemId != null)
                System.out.print(" systemId=\"" + systemId + '"');
            System.out.println(" notationName=\"" + notationName + '"');
        }
    }

    // Implementation of org.xml.sax.ContentHandler

    @Override
    public void setDocumentLocator(Locator locator) {
        super.setDocumentLocator(locator);
        if (Constants.DEBUG_JML_PARSING)
            System.out.println("Document locator supplied.");
    }

    @Override
    public void startDocument() throws SAXException {
        if (Constants.DEBUG_JML_PARSING)
            System.out.println("Start document");
        try {
            startJMLPattern();
        } catch (JuggleException je) {
            throw new SAXException(je.getMessage());
        }
    }

    @Override
    public void endDocument() throws SAXException {
        if (Constants.DEBUG_JML_PARSING)
            System.out.println("End document");
        try {
            endJMLPattern();
        } catch (JuggleException je) {
            throw new SAXException(je.getMessage());
        }
    }

    @Override
    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
        if (Constants.DEBUG_JML_PARSING) {
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

    @Override
    public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
        if (Constants.DEBUG_JML_PARSING)
            System.out.println("End element: " + qName);
        try {
            endJMLElement(qName);
        } catch (JuggleException je) {
            throw new SAXException(je.getMessage());
        }
    }

    @Override
    public void characters(char ch[], int start, int length) throws SAXException {
        if (Constants.DEBUG_JML_PARSING) {
            System.out.print("Characters: ");
            display(ch, start, length);
        }
        try {
            addJMLText(new String(ch, start, length));
        } catch (JuggleException je) {
            throw new SAXException(je.getMessage());
        }
    }

    @Override
    public void ignorableWhitespace(char ch[], int start, int length) {
        if (Constants.DEBUG_JML_PARSING) {
            System.out.print("Ignorable Whitespace: ");
            display(ch, start, length);
        }
    }

    @Override
    public void processingInstruction(String target, String data) {
        if (Constants.DEBUG_JML_PARSING)
            System.out.println("Processing instruction: " + target + ' ' + data);
    }

    // Implementation of org.xml.sax.ErrorHandler

    @Override
    public void warning(SAXParseException exception) throws SAXException {
        if (Constants.DEBUG_JML_PARSING) {
            System.out.println("SAX parsing warning: " +
                          exception.getMessage() + " (" +
                          exception.getSystemId() + ':' +
                          exception.getLineNumber() + ',' +
                          exception.getColumnNumber() + ')');
        }
        throw exception;
    }

    @Override
    public void error(SAXParseException exception) throws SAXException {
        if (Constants.DEBUG_JML_PARSING) {
            System.out.println("SAX parsing error: " +
                          exception.getMessage() + " (" +
                          exception.getSystemId() + ':' +
                          exception.getLineNumber() + ',' +
                          exception.getColumnNumber() + ')');
        }
        throw exception;
    }

    @Override
    public void fatalError(SAXParseException exception) throws SAXException {
        if (Constants.DEBUG_JML_PARSING) {
            System.out.println("SAX parsing fatal error: " +
                          exception.getMessage() + " (" +
                          exception.getSystemId() + ':' +
                          exception.getLineNumber() + ',' +
                          exception.getColumnNumber() + ')');
        }
        throw exception;
    }


    // Utility routines.

    /**
        * Display text, escaping some characters.
     */
    private static void display(char ch[], int start, int length) {
        if (Constants.DEBUG_JML_PARSING) {
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

    public JMLNode getTree()    { return rootNode; }

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
