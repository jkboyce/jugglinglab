// SAX exception class.
// No warranty; no copyright -- use this as you will.
// $Id: SAXException.java,v 1.1.1.1 2002/03/31 07:47:39 jboyce Exp $

package org.xml.sax;

/**
  * Encapsulate a general SAX error or warning.
  *
  * <p>This class can contain basic error or warning information from
  * either the XML parser or the application: a parser writer or
  * application writer can subclass it to provide additional
  * functionality.  SAX handlers may throw this exception or
  * any exception subclassed from it.</p>
  *
  * <p>If the application needs to pass through other types of
  * exceptions, it must wrap those exceptions in a SAXException
  * or an exception derived from a SAXException.</p>
  *
  * <p>If the parser or application needs to include information about a
  * specific location in an XML document, it should use the
  * SAXParseException subclass.</p>
  *
  * @author David Megginson (ak117@freenet.carleton.ca)
  * @version 1.0
  * @see org.xml.sax.SAXParseException
  */
public class SAXException extends Exception {


  /**
    * Create a new SAXException.
    *
    * @param message The error or warning message.
    * @see org.xml.sax.Parser#setLocale
    */
  public SAXException (String message) {
    super();
    this.message = message;
    this.exception = null;
  }


  /**
    * Create a new SAXException wrapping an existing exception.
    *
    * <p>The existing exception will be embedded in the new
    * one, and its message will become the default message for
    * the SAXException.</p>
    *
    * @param e The exception to be wrapped in a SAXException.
    */
  public SAXException (Exception e)
  {
    super();
    this.message = null;
    this.exception = e;
  }


  /**
    * Create a new SAXException from an existing exception.
    *
    * <p>The existing exception will be embedded in the new
    * one, but the new exception will have its own message.</p>
    *
    * @param message The detail message.
    * @param e The exception to be wrapped in a SAXException.
    * @see org.xml.sax.Parser#setLocale
    */
  public SAXException (String message, Exception e)
  {
    super();
    this.message = message;
    this.exception = e;
  }


  /**
    * Return a detail message for this exception.
    *
    * <p>If there is a embedded exception, and if the SAXException
    * has no detail message of its own, this method will return
    * the detail message from the embedded exception.</p>
    *
    * @return The error or warning message.
    * @see org.xml.sax.Parser#setLocale
    */
  public String getMessage ()
  {
    if (message == null && exception != null) {
      return exception.getMessage();
    } else {
      return this.message;
    }
  }


  /**
    * Return the embedded exception, if any.
    *
    * @return The embedded exception, or null if there is none.
    */
  public Exception getException ()
  {
    return exception;
  }


  /**
    * Convert this exception to a string.
    *
    * @return A string version of this exception.
    */
  public String toString ()
  {
    return getMessage();
  }



  //////////////////////////////////////////////////////////////////////
  // Internal state.
  //////////////////////////////////////////////////////////////////////

  private String message;
  private Exception exception;

}
