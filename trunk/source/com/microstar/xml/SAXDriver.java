// SAXDriver.java: The SAX driver for AElfred.
// NO WARRANTY! See README, and copyright below.
// $Id: SAXDriver.java,v 1.1.1.1 2002/03/31 07:47:08 jboyce Exp $

package com.microstar.xml;

import org.xml.sax.Parser;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.AttributeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.DTDHandler;
import org.xml.sax.DocumentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.HandlerBase;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.microstar.xml.XmlParser;
import com.microstar.xml.XmlHandler;

import java.io.IOException;
import java.io.Reader;
import java.util.Enumeration;
import java.util.Stack;
import java.util.Locale;
import java.util.Vector;


/**
  * A SAX driver for Microstar's &AElig;lfred XML parser.
  *
  * <p>This driver acts as a front-end for &AElig;lfred, and 
  * translates &AElig;lfred's events into SAX events.  It 
  * implements the SAX parser interface, and you can use it without 
  * directly calling &AElig;lfred at all:</p>
  *
  * <pre>
  * org.xml.sax.Parser parser = new com.microstar.xml.SAXDriver();
  * </pre>
  *
  * <p>When you are using SAX, you do not need to use the
  * <code>XmlParser</code> or <code>XmlHandler</code> classes at
  * all: this class is your entry point.</p>
  *
  * <p>This driver is based on the 1.0gamma version of SAX,
  * available from http://www.megginson.com/SAX/</p>
  *
  * @author Copyright (c) 1998 by Microstar Software Ltd.
  * @author written by David Megginson &lt;dmeggins@microstar.com&gt;
  * @version 1.1
  * @see org.xml.sax.Parser
  * @see XmlParser
  */
public class SAXDriver
  implements XmlHandler, Locator, AttributeList, Parser
{


  //
  // Variables.
  //

  private HandlerBase base = new HandlerBase();
  private XmlParser parser;

  private boolean seenDTDEvents = false;

				// Encapsulate the default behaviour
				// from HandlerBase
  private EntityResolver entityResolver = base;
  private DTDHandler dtdHandler = base;
  private DocumentHandler documentHandler = base;
  private ErrorHandler errorHandler = base;

  private String elementName = null;
  private Stack entityStack = new Stack();

  private Vector attributeNames = new Vector();
  private Vector attributeValues = new Vector();



  //
  // Constructor.
  //

  public SAXDriver ()
  {
  }



  //
  // Implementation of org.xml.sax.Parser.
  //

  /**
    * Set the locale.
    * @see org.xml.sax.Parser#setLocale
    */
  public void setLocale (Locale locale)
    throws SAXException
  {
    throw new SAXException("AElfred driver does not yet have locale support.");
  }


  /**
    * Set the entity resolver for this parser.
    * @param handler The object to receive entity events.
    * @see org.xml.sax.Parser#setEntityResolver
    */
  public void setEntityResolver (EntityResolver resolver) 
  {
    this.entityResolver = resolver;
  }


  /**
    * Set the DTD handler for this parser.
    * @param handler The object to receive DTD events.
    * @see org.xml.sax.Parser#setDTDHandler
    */
  public void setDTDHandler (DTDHandler handler) 
  {
    this.dtdHandler = handler;
  }


  /**
    * Set the document handler for this parser.
    * @param handler The object to receive document events.
    * @see org.xml.sax.Parser#setDocumentHandler
    */
  public void setDocumentHandler (DocumentHandler handler) 
  {
    this.documentHandler = handler;
  }


  /**
    * Set the error handler for this parser.
    * @param handler The object to receive error events.
    * @see org.xml.sax.Parser#setErrorHandler
    */
  public void setErrorHandler (ErrorHandler handler) 
  {
    this.errorHandler = handler;
  }


  /**
    * Parse a document.
    * <p>If you want anything useful to happen, you should set
    * at least one type of handler.
    * @param source The XML input source.
    * @see org.xml.sax.Parser#parse(InputSource)
    * @see #setEntityResolver
    * @see #setDTDHandler
    * @see #setDocumentHandler
    * @see #setErrorHandler
    * @exception SAXException The handlers may throw any exception.
    */
  public void parse (InputSource source)
    throws SAXException
  {
    parser = new XmlParser();
    parser.setHandler(this);

    try {
      if (source.getCharacterStream() != null) {
	parser.parse(source.getSystemId(),
		     source.getPublicId(),
		     source.getCharacterStream());
      } else if (source.getByteStream() != null) {
	parser.parse(source.getSystemId(),
		     source.getPublicId(),
		     source.getByteStream(),
		     source.getEncoding());
      } else {
	parser.parse(source.getSystemId(),
		     source.getPublicId(),
		     source.getEncoding());
      }
    } catch (SAXException e) {
      throw e;
    } catch (Exception e) {
      throw new SAXException(e);
    } finally {
      try {
	closeStreams(source);
      } catch (Exception e) {};
    }

    try {
      closeStreams(source);
    } catch (IOException e) {
      throw new SAXException(e);
    }
  }


  /**
    * Parse an XML document from a system identifier (URI).
    * @see org.xml.sax.Parser#parse(String)
    */
  public void parse (String systemId)
    throws SAXException
  {
    parse(new InputSource(systemId));
  }


  /**
    * Close any streams provided.
    */
  private void closeStreams (InputSource source)
    throws IOException
  {
    if (source.getCharacterStream() != null) {
      source.getCharacterStream().close();
    }
    if (source.getByteStream() != null) {
      source.getByteStream().close();
    }
  }



  //
  // Implementation of com.microstar.xml.XmlHandler.
  // This is where the driver receives AElfred events and translates
  // them into SAX events.
  //


  /**
    * Implement com.microstar.xml.XmlHandler#startDocument.
    * <p>Translate to the SAX interface.
    * <p>Users should never invoke this method directly.
    * @see com.microstar.xml.XmlHandler#startDocument
    * @exception SAXException May throw any exception.
    */
  public void startDocument ()
    throws SAXException
  {
    documentHandler.setDocumentLocator(this);
    documentHandler.startDocument();
    attributeNames.removeAllElements();
    attributeValues.removeAllElements();
  }


  /**
    * Implement com.microstar.xml.XmlHandler#endDocument.
    * <p>Translate to the SAX interface.
    * <p>Users should never invoke this method directly.
    * @see com.microstar.xml.XmlHandler#endDocument
    * @exception SAXException May throw any exception.
    */
  public void endDocument ()
    throws SAXException
  {
    documentHandler.endDocument();
  }


  /**
    * Implement com.microstar.xml.XmlHandler.resolveSystemId
    * <p>Translate to the SAX interface.
    * <p>Users should never invoke this method directly.
    * @see com.microstar.xml.XmlHandler#resolveEntity
    * @exception SAXException May throw any exception.
    */
  public Object resolveEntity(String publicId, String systemId)
    throws SAXException, IOException
  {
    InputSource source = entityResolver.resolveEntity(publicId,
						      systemId);

    if (source == null) {
      return null;
    } else if (source.getCharacterStream() != null) {
      return source.getCharacterStream();
    } else if (source.getByteStream() != null) {
      return source.getByteStream();
    } else {
      return source.getSystemId();
    }
				// FIXME: no way to tell AElfred
				// about a new public id.
  }


  /**
    * Implement com.microstar.xml.XmlHandler#startExternalEntity.
    * <p>Translate to the SAX interface.
    * <p>Users should never invoke this method directly.
    * @see com.microstar.xml.XmlHandler#startExternalEntity
    * @exception SAXException May throw any exception.
    */
  public void startExternalEntity (String systemId)
    throws SAXException
  {
    entityStack.push(systemId);
  }


  /**
    * Implement com.microstar.xml.XmlHandler#endExternalEntity.
    * <p>Translate to the SAX interface.
    * <p>Users should never invoke this method directly.
    * @see com.microstar.xml.XmlHandler#endExternalEntity
    * @exception SAXException May throw any exception.
    */
  public void endExternalEntity (String systemId)
    throws SAXException
  {
    entityStack.pop();
  }


  /**
    * Implement com.microstar.xml.XmlHandler#doctypeDecl.
    * <p>Translate to the SAX interface.
    * <p>Users should never invoke this method directly.
    * @see com.microstar.xml.XmlHandler#doctypeDecl
    * @exception SAXException May throw any exception.
    */
  public void doctypeDecl (String name, String publicId, String systemId)
    throws SAXException
  {
    // no op
  }


  /**
    * Implement com.microstar.xml.XmlHandler#attribute.
    * <p>Translate to the SAX interface.
    * <p>Users should never invoke this method directly.
    * @see com.microstar.xml.XmlHandler#attribute
    * @exception SAXException May throw any exception.
    */
  public void attribute (String aname, String value, boolean isSpecified)
    throws SAXException
  {
    if (value != null) {
      attributeNames.addElement(aname);
      attributeValues.addElement(value);
    }
  }


  /**
    * Implement com.microstar.xml.XmlHandler#startElement.
    * <p>Translate to the SAX interface.
    * <p>Users should never invoke this method directly.
    * @see com.microstar.xml.XmlHandler#startElement
    * @exception SAXException May throw any exception.
    */
  public void startElement (String elname)
    throws SAXException
  {
				// We should deliver all DTD events
				// before the first startElement event.
    if (!seenDTDEvents) {
      deliverDTDEvents();
      seenDTDEvents = true;
    }

    elementName = elname;
    documentHandler.startElement(elname, this);
    elementName = null;
    attributeNames.removeAllElements();
    attributeValues.removeAllElements();
  }


  /**
    * Implement com.microstar.xml.XmlHandler#endElement.
    * <p>Translate to the SAX interface.
    * <p>Users should never invoke this method directly.
    * @see com.microstar.xml.XmlHandler#endElement
    * @exception SAXException May throw any exception.
    */
  public void endElement (String elname)
    throws SAXException
  {
    documentHandler.endElement(elname);
  }


  /**
    * Implement com.microstar.xml.XmlHandler#charData.
    * <p>Translate to the SAX interface.
    * <p>Users should never invoke this method directly.
    * @see com.microstar.xml.XmlHandler#charData
    * @exception SAXException May throw any exception.
    */
  public void charData (char ch[], int start, int length)
    throws SAXException
  {
    documentHandler.characters(ch, start, length);
  }


  /**
    * Implement com.microstar.xml.XmlHandler#ignorableWhitespace.
    * <p>Translate to the SAX interface.
    * <p>Users should never invoke this method directly.
    * @see com.microstar.xml.XmlHandler#ignorableWhitespace
    * @exception SAXException May throw any exception.
    */
  public void ignorableWhitespace (char ch[], int start, int length)
    throws SAXException
  {
    documentHandler.ignorableWhitespace(ch, start, length);
  }


  /**
    * Implement com.microstar.xml.XmlHandler#processingInstruction.
    * <p>Translate to the SAX interface.
    * <p>Users should never invoke this method directly.
    * @see com.microstar.xml.XmlHandler#processingInstruction
    * @exception SAXException May throw any exception.
    */
  public void processingInstruction (String target, String data)
    throws SAXException
  {
    documentHandler.processingInstruction(target, data);
  }


  /**
    * Implement com.microstar.xml.XmlHandler#error.
    * <p>Translate to the SAX interface.
    * <p>Users should never invoke this method directly.
    * @see com.microstar.xml.XmlHandler#error
    * @exception SAXException May throw any exception.
    */
  public void error (String message, String url, int line, int column)
    throws SAXException
  {
      errorHandler.fatalError(new SAXParseException(message, null,
						    url, line, column));
  }


  /**
    * Before the first startElement event, deliver all notation
    * and unparsed entity declarations.
    */
  private void deliverDTDEvents ()
    throws SAXException
  {
    String ename;
    String nname;
    String publicId;
    String systemId;

    Enumeration notationNames = parser.declaredNotations();
    Enumeration entityNames = parser.declaredEntities();

				// First, report all notations.
    while (notationNames.hasMoreElements()) {
      nname = (String)notationNames.nextElement();
      publicId = parser.getNotationPublicId(nname);
      systemId = parser.getNotationSystemId(nname);
      dtdHandler.notationDecl(nname, publicId, systemId);
    }

				// Next, report all unparsed entities.
    while (entityNames.hasMoreElements()) {
      ename = (String)entityNames.nextElement();
      if (parser.getEntityType(ename) == XmlParser.ENTITY_NDATA) {
	publicId = parser.getEntityPublicId(ename);
	systemId = parser.getEntitySystemId(ename);
	nname = parser.getEntityNotationName(ename);
	dtdHandler.unparsedEntityDecl(ename, publicId, systemId, nname);
      }
    }
  }



  //
  // Implementation of org.xml.sax.AttributeList.
  //

  public int getLength ()
  {
    return attributeNames.size();
  }

  public String getName (int i)
  {
    return (String)(attributeNames.elementAt(i));
  }


  public String getType (int i)
  {
    switch (parser.getAttributeType(elementName, getName(i))) {

    case XmlParser.ATTRIBUTE_UNDECLARED:
    case XmlParser.ATTRIBUTE_CDATA:
      return "CDATA";
    case XmlParser.ATTRIBUTE_ID:
      return "ID";
    case XmlParser.ATTRIBUTE_IDREF:
      return "IDREF";
    case XmlParser.ATTRIBUTE_IDREFS:
      return "IDREFS";
    case XmlParser.ATTRIBUTE_ENTITY:
      return "ENTITY";
    case XmlParser.ATTRIBUTE_ENTITIES:
      return "ENTITIES";
    case XmlParser.ATTRIBUTE_NMTOKEN:
    case XmlParser.ATTRIBUTE_ENUMERATED:
      return "NMTOKEN";
    case XmlParser.ATTRIBUTE_NMTOKENS:
      return "NMTOKENS";
    case XmlParser.ATTRIBUTE_NOTATION:
      return "NOTATION";

    }

    return null;
  }


  public String getValue (int i)
  {
    return (String)(attributeValues.elementAt(i));
  }


  public String getType (String name)
  {
    for (int i = 0; i < getLength(); i++) {
      if (name.equals(getName(i))) {
	return getType(i);
      }
    }
    return null;
  }


  public String getValue (String name)
  {
    for (int i = 0; i < getLength(); i++) {
      if (name.equals(getName(i))) {
	return getValue(i);
      }
    }
    return null;
  }



  //
  // Implementation of org.xml.sax.Locator.
  //

  public String getPublicId ()
  {
    return null;		// TODO
  }

  public String getSystemId ()
  {
    return (String)(entityStack.peek());
  }

  public int getLineNumber ()
  {
    return parser.getLineNumber();
  }

  public int getColumnNumber ()
  {
    return parser.getColumnNumber();
  }

}
