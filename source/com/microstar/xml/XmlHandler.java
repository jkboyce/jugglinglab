// XmlHandler.java: the callback interface.
// NO WARRANTY! See README, and copyright below.
// $Id: XmlHandler.java,v 1.1.1.1 2002/03/31 07:47:08 jboyce Exp $

package com.microstar.xml;

/**
  * XML Processing Interface.
  * <p>Whenever you parse an XML document, you must provide an object
  * from a class that implements this interface to receive the parsing 
  * events.
  * <p>If you do not want to implement this entire interface, you
  * can extend the <code>HandlerBase</code> convenience class and
  * then implement only what you need.
  * <p>If you are using SAX, you should implement the SAX handler
  * interfaces rather than this one.
  * @author Copyright (c) 1997, 1998 by Microstar Software Ltd.
  * @author written by David Megginson &lt;dmeggins@microstar.com&gt;
  * @version 1.1
  * @see XmlParser
  * @see HandlerBase
  * @see org.xml.sax.EntityHandler
  * @see org.xml.sax.DocumentHandler
  * @see org.xml.sax.ErrorHandler
  */
public interface XmlHandler {

  /**
    * Start the document.
    * <p>&AElig;lfred will call this method just before it
    * attempts to read the first entity (the root of the document).
    * It is guaranteed that this will be the first method called.
    * @exception java.lang.Exception The handler may throw any exception.
    * @see #endDocument
    */
  public void startDocument ()
    throws java.lang.Exception;


  /**
    * End the document.
    * <p>&AElig;lfred will call this method once, when it has
    * finished parsing the XML document.
    * It is guaranteed that this will be the last method called.
    * @exception java.lang.Exception The handler may throw any exception.
    * @see #startDocument
    */
  public void endDocument ()
    throws java.lang.Exception;


  /**
    * Resolve an External Entity.
    * <p>Give the handler a chance to redirect external entities
    * to different URIs.  &AElig;lfred will call this method for the
    * top-level document entity, for external text (XML) entities, 
    * and the external DTD subset (if any).
    * @param publicId The public identifier, or null if none was supplied.
    * @param systemId The system identifier.
    * @return The replacement system identifier, or null to use
    *         the default.
    * @exception java.lang.Exception The handler may throw any exception.
    * @see #startExternalEntity
    * @see #endExternalEntity
    */
  public Object resolveEntity (String publicId, String systemId)
    throws java.lang.Exception;


  /**
    * Begin an external entity.
    * <p>&AElig;lfred will call this method at the beginning of
    * each external entity, including the top-level document entity
    * and the external DTD subset (if any).
    * <p>If necessary, you can use this method to track the location
    * of the current entity so that you can resolve relative URIs
    * correctly.
    * @param systemId The URI of the external entity that is starting.
    * @exception java.lang.Exception The handler may throw any exception.
    * @see #endExternalEntity
    * @see #resolveEntity
    */
  public void startExternalEntity (String systemId)
    throws java.lang.Exception;


  /**
    * End an external entity.
    * <p>&AElig;lfred will call this method at the end of
    * each external entity, including the top-level document entity
    * and the external DTD subset.
    * <p>If necessary, you can use this method to track the location
    * of the current entity so that you can resolve relative URIs
    * correctly.
    * @param systemId The URI of the external entity that is ending.
    * @exception java.lang.Exception The handler may throw any exception.
    * @see #startExternalEntity
    * @see #resolveEntity
    */
  public void endExternalEntity (String systemId)
    throws java.lang.Exception;


  /**
    * Document type declaration.
    * <p>&AElig;lfred will call this method when or if it encounters
    * the document type (DOCTYPE) declaration.
    * <p>Please note that the public and system identifiers will
    * not always be a reliable indication of the DTD in use.
    * @param name The document type name.
    * @param publicId The public identifier, or null if unspecified.
    * @param systemId The system identifier, or null if unspecified.
    * @exception java.lang.Exception The handler may throw any exception.
    */
  public void doctypeDecl (String name, String publicId, String systemId)
    throws java.lang.Exception;


  /**
    * Attribute.
    * <p>&AElig;lfred will call this method once for each attribute 
    * (specified or defaulted) before reporting a startElement event.
    * It is up to your handler to collect the attributes, if
    * necessary.
    * <p>You may use XmlParser.getAttributeType() to find the attribute's
    * declared type.
    * @param name The name of the attribute.
    * @param type The type of the attribute (see below).
    * @param value The value of the attribute, or null if the attribute
    *        is <code>#IMPLIED</code>.
    * @param isSpecified True if the value was specified, false if it
    *       was defaulted from the DTD.
    * @exception java.lang.Exception The handler may throw any exception.
    * @see #startElement
    * @see XmlParser#declaredAttributes
    * @see XmlParser#getAttributeType
    * @see XmlParser#getAttributeDefaultValue
    */
  public void attribute (String aname, String value, boolean isSpecified)
    throws java.lang.Exception;


  /**
    * Start an element.
    * <p>&AElig;lfred will call this method at the beginning of each
    * element.  By the time this is called, all of the attributes
    * for the element will already have been reported using the
    * <code>attribute</code> method.
    * @param elname The element type name.
    * @exception java.lang.Exception The handler may throw any exception.
    * @see #attribute
    * @see #endElement
    * @see XmlParser#declaredElements
    * @see XmlParser#getElementContentType
    */
  public void startElement (String elname)
    throws java.lang.Exception;


  /**
    * End an element.
    * <p>&AElig;lfred will call this method at the end of each element
    * (including EMPTY elements).
    * @param elname The element type name.
    * @exception java.lang.Exception The handler may throw any exception.
    * @see #startElement
    * @see XmlParser#declaredElements
    * @see XmlParser#getElementContentType
    */
  public void endElement (String elname)
    throws java.lang.Exception;


  /**
    * Character data.
    * <p>&AElig;lfred will call this method once for each chunk of
    * character data found in the contents of elements.  Note that
    * the parser may break up a long sequence of characters into
    * smaller chunks and call this method once for each chunk.
    * <p>Do <em>not</em> attempt to read more than <var>length</var>
    * characters from the array, or to read before the 
    * <var>start</var> position.
    * @param ch The character data.
    * @param start The starting position in the array.
    * @param length The number of characters available.
    * @exception java.lang.Exception The handler may throw any exception.
    */
  public void charData (char ch[], int start, int length)
    throws java.lang.Exception;


  /**
    * Ignorable whitespace.
    * <p>&AElig;lfred will call this method once for each sequence
    * of ignorable whitespace in element content (never in mixed content).
    * <p>For details, see section 2.10 of the XML 1.0 recommendation.
    * <p>Do <em>not</em> attempt to read more than <var>length</var>
    * characters from the array or to read before the <var>start</var>
    * position.
    * @param ch The literal whitespace characters.
    * @param start The starting position in the array.
    * @param length The number of whitespace characters available.
    * @exception java.lang.Exception The handler may throw any exception.
    */
  public void ignorableWhitespace (char ch[], int start, int length)
    throws java.lang.Exception;


  /**
    * Processing instruction.
    * <p>&AElig;lfred will call this method once for each
    * processing instruction.  Note that processing instructions may
    * appear outside of the top-level element.  The
    * @param target The target (the name at the start of the PI).
    * @param data The data, if any (the rest of the PI).
    * @exception java.lang.Exception The handler may throw any exception.
    */
  public void processingInstruction (String target, String data)
    throws java.lang.Exception;


  /**
    * Fatal XML parsing error.
    * <p>&AElig;lfred will call this method whenever it encounters
    * a serious error.  The parser will attempt to continue past this 
    * point so that you can find more possible error points, but if
    * this method is called you should assume that the document is
    * corrupt and you should not try to use its contents.
    * <p>Note that you can use the <code>XmlException</code> class
    * to encapsulate all of the information provided, though the
    * use of the class is not mandatory.
    * @param message The error message.
    * @param systemId The system identifier of the entity that 
    *        contains the error.
    * @param line The approximate line number of the error.
    * @param column The approximate column number of the error.
    * @exception java.lang.Exception The handler may throw any exception.
    * @see XmlException
    */
  public void error (String message, String systemId, int line, int column)
    throws java.lang.Exception;

}
