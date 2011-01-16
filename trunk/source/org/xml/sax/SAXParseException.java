// SAX exception class.
// No warranty; no copyright -- use this as you will.
// $Id: SAXParseException.java,v 1.1.1.1 2002/03/31 07:47:40 jboyce Exp $

package org.xml.sax;

/**
  * Encapsulate an XML parse error or warning.
  *
  * <p>This exception will include information for locating the error
  * in the original XML document.  Note that although the application
  * will receive a SAXParseException as the argument to the handlers
  * in the ErrorHandler interface, the application is not actually
  * required to throw the exception; instead, it can simply read the
  * information in it and take a different action.</p>
  *
  * <p>Since this exception is a subclass of SAXException, it
  * inherits the ability to wrap another exception.</p>
  *
  * @author David Megginson (ak117@freenet.carleton.ca)
  * @version 1.0
  * @see org.xml.sax.SAXException
  * @see org.xml.sax.Locator
  * @see org.xml.sax.ErrorHandler
  */
public class SAXParseException extends SAXException {


  //////////////////////////////////////////////////////////////////////
  // Constructors.
  //////////////////////////////////////////////////////////////////////

  /**
    * Create a new SAXParseException from a message and a Locator.
    *
    * <p>This constructor is especially useful when an application is
    * creating its own exception from within a DocumentHandler
    * callback.</p>
    *
    * @param message The error or warning message.
    * @param locator The locator object for the error or warning.
    * @see org.xml.sax.Locator
    * @see org.xml.sax.Parser#setLocale 
    */
  public SAXParseException (String message, Locator locator) {
    super(message);
    this.publicId = locator.getPublicId();
    this.systemId = locator.getSystemId();
    this.lineNumber = locator.getLineNumber();
    this.columnNumber = locator.getColumnNumber();
  }


  /**
    * Wrap an existing exception in a SAXParseException.
    *
    * <p>This constructor is especially useful when an application is
    * creating its own exception from within a DocumentHandler
    * callback, and needs to wrap an existing exception that is not a
    * subclass of SAXException.</p>
    *
    * @param message The error or warning message, or null to
    *                use the message from the embedded exception.
    * @param locator The locator object for the error or warning.
    * @param e Any exception
    * @see org.xml.sax.Locator
    * @see org.xml.sax.Parser#setLocale
    */
  public SAXParseException (String message, Locator locator,
			    Exception e) {
    super(message, e);
    this.publicId = locator.getPublicId();
    this.systemId = locator.getSystemId();
    this.lineNumber = locator.getLineNumber();
    this.columnNumber = locator.getColumnNumber();
  }


  /**
    * Create a new SAXParseException.
    *
    * <p>This constructor is most useful for parser writers.</p>
    *
    * <p>If the system identifier is a URL, the parser must resolve it
    * fully before creating the exception.</p>
    *
    * @param message The error or warning message.
    * @param publicId The public identifer of the entity that generated
    *                 the error or warning.
    * @param systemId The system identifer of the entity that generated
    *                 the error or warning.
    * @param lineNumber The line number of the end of the text that
    *                   caused the error or warning.
    * @param columnNumber The column number of the end of the text that
    *                     cause the error or warning.
    * @see org.xml.sax.Parser#setLocale
    */
  public SAXParseException (String message, String publicId, String systemId,
			    int lineNumber, int columnNumber)
  {
    super(message);
    this.publicId = publicId;
    this.systemId = systemId;
    this.lineNumber = lineNumber;
    this.columnNumber = columnNumber;
  }


  /**
    * Create a new SAXParseException with an embedded exception.
    *
    * <p>This constructor is most useful for parser writers who
    * need to wrap an exception that is not a subclass of
    * SAXException.</p>
    *
    * <p>If the system identifier is a URL, the parser must resolve it
    * fully before creating the exception.</p>
    *
    * @param message The error or warning message, or null to use
    *                the message from the embedded exception.
    * @param publicId The public identifer of the entity that generated
    *                 the error or warning.
    * @param systemId The system identifer of the entity that generated
    *                 the error or warning.
    * @param lineNumber The line number of the end of the text that
    *                   caused the error or warning.
    * @param columnNumber The column number of the end of the text that
    *                     cause the error or warning.
    * @param e Another exception to embed in this one.
    * @see org.xml.sax.Parser#setLocale
    */
  public SAXParseException (String message, String publicId, String systemId,
			    int lineNumber, int columnNumber, Exception e)
  {
    super(message, e);
    this.publicId = publicId;
    this.systemId = systemId;
    this.lineNumber = lineNumber;
    this.columnNumber = columnNumber;
  }


  /**
    * Get the public identifier of the entity where the exception occurred.
    *
    * @return A string containing the public identifier, or null
    *         if none is available.
    * @see org.xml.sax.Locator#getPublicId
    */
  public String getPublicId ()
  {
    return this.publicId;
  }


  /**
    * Get the system identifier of the entity where the exception occurred.
    *
    * <p>If the system identifier is a URL, it will be resolved
    * fully.</p>
    *
    * @return A string containing the system identifier, or null
    *         if none is available.
    * @see org.xml.sax.Locator#getSystemId
    */
  public String getSystemId ()
  {
    return this.systemId;
  }


  /**
    * The line number of the end of the text where the exception occurred.
    *
    * @return An integer representing the line number, or -1
    *         if none is available.
    * @see org.xml.sax.Locator#getLineNumber
    */
  public int getLineNumber ()
  {
    return this.lineNumber;
  }


  /**
    * The column number of the end of the text where the exception occurred.
    *
    * <p>The first column in a line is position 1.</p>
    *
    * @return An integer representing the column number, or -1
    *         if none is available.
    * @see org.xml.sax.Locator#getColumnNumber
    */
  public int getColumnNumber ()
  {
    return this.columnNumber;
  }



  //////////////////////////////////////////////////////////////////////
  // Internal state.
  //////////////////////////////////////////////////////////////////////

  private String publicId;
  private String systemId;
  private int lineNumber;
  private int columnNumber;

}
