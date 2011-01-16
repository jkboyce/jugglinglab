// XmlException.java: Simple base class for AElfred processors.
// NO WARRANTY! See README, and copyright below.
// $Id: XmlException.java,v 1.1.1.1 2002/03/31 07:47:08 jboyce Exp $

package com.microstar.xml;


/**
  * Convenience exception class for reporting XML parsing errors.
  * <p>This is an exception class that you can use to encapsulate all
  * of the information from &AElig;lfred's <code>error</code> callback.
  * This is not necessary for routine use of &AElig;lfred, but it
  * is used by the optional <code>HandlerBase</code> class.
  * <p>Note that the core &AElig;lfred classes do <em>not</em>
  * use this exception.
  * @author Copyright (c) 1998 by Microstar Software Ltd.
  * @author written by David Megginson &lt;dmeggins@microstar.com&gt;
  * @version 1.1
  * @see XmlHandler#error
  * @see HandlerBase
  */
public class XmlException extends Exception
{
  private String message;
  private String systemId;
  private int line;
  private int column;


  /**
    * Construct a new XML parsing exception.
    * @param message The error message from the parser.
    * @param systemId The URI of the entity containing the error.
    * @param line The line number where the error appeared.
    * @param column The column number where the error appeared.
    */
  public XmlException (String message, String systemId, int line, int column)
  {
    this.message = message;
    this.systemId = systemId;
    this.line = line;
    this.column = column;
  }


  /**
    * Get the error message from the parser.
    * @return A string describing the error.
    */
  public String getMessage ()
  {
    return message;
  }


  /**
    * Get the URI of the entity containing the error.
    * @return The URI as a string.
    */
  public String getSystemId ()
  {
    return systemId;
  }


  /**
    * Get the line number containing the error.
    * @return The line number as an integer.
    */
  public int getLine ()
  {
    return line;
  }

  /**
    * Get the column number containing the error.
    * @return The column number as an integer.
    */
  public int getColumn ()
  {
    return column;
  }

}
