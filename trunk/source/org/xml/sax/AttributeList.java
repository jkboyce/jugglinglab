// SAX Attribute List Interface.
// No warranty; no copyright -- use this as you will.
// $Id: AttributeList.java,v 1.1.1.1 2002/03/31 07:47:35 jboyce Exp $

package org.xml.sax;

/**
  * Interface for an element's attribute specifications.
  *
  * <p>The SAX parser implements this interface and passes an instance
  * to the SAX application as the second argument of each startElement
  * event.</p>
  *
  * <p>The instance provided will return valid results only during the
  * scope of the startElement invocation (to save it for future
  * use, the application must make a copy: the AttributeListImpl
  * helper class provides a convenient constructor for doing so).</p>
  *
  * <p>An AttributeList includes only attributes that have been
  * specified or defaulted: #IMPLIED attributes will not be included.</p>
  *
  * <p>There are two ways for the SAX application to obtain information
  * from the AttributeList.  First, it can iterate through the entire
  * list:</p>
  *
  * <pre>
  * public void startElement (String name, AttributeList atts) {
  *   for (int i = 0; i < atts.getLength(); i++) {
  *     String name = atts.getName(i);
  *     String type = atts.getType(i);
  *     String value = atts.getValue(i);
  *     [...]
  *   }
  * }
  * </pre>
  *
  * <p>(Note that the result of getLength() will be zero if there
  * are no attributes.)
  *
  * <p>As an alternative, the application can request the value or
  * type of specific attributes:</p>
  *
  * <pre>
  * public void startElement (String name, AttributeList atts) {
  *   String identifier = atts.getValue("id");
  *   String label = atts.getValue("label");
  *   [...]
  * }
  * </pre>
  *
  * <p>The AttributeListImpl helper class provides a convenience 
  * implementation for use by parser or application writers.</p>
  *
  * @author David Megginson (ak117@freenet.carleton.ca)
  * @version 1.0
  * @see org.xml.sax.DocumentHandler#startElement 
  * @see org.xml.sax.helpers.AttributeListImpl
  */
public interface AttributeList {

  /**
    * Return the number of attributes in this list.
    *
    * <p>The SAX parser may provide attributes in any
    * arbitrary order, regardless of the order in which they were
    * declared or specified.  The number of attributes may be
    * zero.</p>
    *
    * @return The number of attributes in the list.  
    */
  public abstract int getLength ();


  /**
    * Return the name of an attribute in this list (by position).
    *
    * <p>The names must be unique: the SAX parser shall not include the
    * same attribute twice.  Attributes without values (those declared
    * #IMPLIED without a value specified in the start tag) will be
    * omitted from the list.</p>
    *
    * <p>If the attribute name has a namespace prefix, the prefix
    * will still be attached.</p>
    *
    * @param i The index of the attribute in the list (starting at 0).
    * @return The name of the indexed attribute, or null
    *         if the index is out of range.
    * @see #getLength 
    */
  public abstract String getName (int i);


  /**
    * Return the type of an attribute in the list (by position).
    *
    * <p>The attribute type is one of the strings "CDATA", "ID",
    * "IDREF", "IDREFS", "NMTOKEN", "NMTOKENS", "ENTITY", "ENTITIES",
    * or "NOTATION" (always in upper case).</p>
    *
    * <p>If the parser has not read a declaration for the attribute,
    * or if the parser does not report attribute types, then it must
    * return the value "CDATA" as stated in the XML 1.0 Recommentation
    * (clause 3.3.3, "Attribute-Value Normalization").</p>
    *
    * <p>For an enumerated attribute that is not a notation, the
    * parser will report the type as "NMTOKEN".</p>
    *
    * @param i The index of the attribute in the list (starting at 0).
    * @return The attribute type as a string, or
    *         null if the index is out of range.
    * @see #getLength 
    * @see #getType(java.lang.String)
    */
  public abstract String getType (int i);


  /**
    * Return the value of an attribute in the list (by position).
    *
    * <p>If the attribute value is a list of tokens (IDREFS,
    * ENTITIES, or NMTOKENS), the tokens will be concatenated
    * into a single string separated by whitespace.</p>
    *
    * @param i The index of the attribute in the list (starting at 0).
    * @return The attribute value as a string, or
    *         null if the index is out of range.
    * @see #getLength
    * @see #getValue(java.lang.String)
    */
  public abstract String getValue (int i);


  /**
    * Return the type of an attribute in the list (by name).
    *
    * <p>The return value is the same as the return value for
    * getType(int).</p>
    *
    * <p>If the attribute name has a namespace prefix in the document,
    * the application must include the prefix here.</p>
    *
    * @param name The name of the attribute.
    * @return The attribute type as a string, or null if no
    *         such attribute exists.
    * @see #getType(int)
    */
  public abstract String getType (String name);


  /**
    * Return the value of an attribute in the list (by name).
    *
    * <p>The return value is the same as the return value for
    * getValue(int).</p>
    *
    * <p>If the attribute name has a namespace prefix in the document,
    * the application must include the prefix here.</p>
    *
    * @param i The index of the attribute in the list.
    * @return The attribute value as a string, or null if
    *         no such attribute exists.
    * @see #getValue(int)
    */
  public abstract String getValue (String name);

}
