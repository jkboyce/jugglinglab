// SAX locator interface for document events.
// No warranty; no copyright -- use this as you will.
// $Id: Locator.java,v 1.1.1.1 2002/03/31 07:47:38 jboyce Exp $

package org.xml.sax;


/**
  * Interface for associating a SAX event with a document location.
  *
  * <p>If a SAX parser provides location information to the SAX
  * application, it does so by implementing this interface and then
  * passing an instance to the application using the document
  * handler's setDocumentLocator method.  The application can use the
  * object to obtain the location of any other document handler event
  * in the XML source document.</p>
  *
  * <p>Note that the results returned by the object will be valid only
  * during the scope of each document handler method: the application
  * will receive unpredictable results if it attempts to use the
  * locator at any other time.</p>
  *
  * <p>SAX parsers are not required to supply a locator, but they are
  * very strong encouraged to do so.  If the parser supplies a
  * locator, it must do so before reporting any other document events.
  * If no locator has been set by the time the application receives
  * the startDocument event, the application should assume that a
  * locator is not available.</p>
  *
  * @author David Megginson (ak117@freenet.carleton.ca)
  * @version 1.0
  * @see org.xml.sax.DocumentHandler#setDocumentLocator 
  */
public interface Locator {


  /**
    * Return the public identifier for the current document event.
    * <p>This will be the public identifier
    * @return A string containing the public identifier, or
    *         null if none is available.
    * @see #getSystemId
    */
  public abstract String getPublicId ();


  /**
    * Return the system identifier for the current document event.
    *
    * <p>If the system identifier is a URL, the parser must resolve it
    * fully before passing it to the application.</p>
    *
    * @return A string containing the system identifier, or null
    *         if none is available.
    * @see #getPublicId
    */
  public abstract String getSystemId ();


  /**
    * Return the line number where the current document event ends.
    * Note that this is the line position of the first character
    * after the text associated with the document event.
    * @return The line number, or -1 if none is available.
    * @see #getColumnNumber
    */
  public abstract int getLineNumber ();


  /**
    * Return the column number where the current document event ends.
    * Note that this is the column number of the first
    * character after the text associated with the document
    * event.  The first column in a line is position 1.
    * @return The column number, or -1 if none is available.
    * @see #getLineNumber
    */
  public abstract int getColumnNumber ();

}
