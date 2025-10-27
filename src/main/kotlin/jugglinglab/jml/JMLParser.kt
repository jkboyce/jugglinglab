//
// JMLParser.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("KotlinConstantConditions")

package jugglinglab.jml

import jugglinglab.core.Constants
import jugglinglab.util.JuggleException
import jugglinglab.util.JuggleExceptionInternal
import org.xml.sax.*
import org.xml.sax.helpers.DefaultHandler
import java.io.IOException
import java.io.Reader
import java.io.StringReader
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

class JMLParser : DefaultHandler() {
    private var patternStarted: Boolean = false
    private var patternFinished: Boolean = false

    var tree: JMLNode? = null // tree of JML tags
        private set
    private var currentNode: JMLNode? = null

    //--------------------------------------------------------------------------
    // Methods to parse JML and get the results
    //--------------------------------------------------------------------------

    @Throws(SAXException::class, IOException::class)
    fun parse(read: Reader?) {
        try {
            if (Constants.DEBUG_JML_PARSING) {
                println("Starting JMLParser.parse()...")
            }

            val factory = SAXParserFactory.newInstance()
            factory.isValidating = true

            // Parse the document
            factory.newSAXParser().parse(InputSource(read), this)
        } catch (pce: ParserConfigurationException) {
            throw SAXException(pce.message)
        }
    }

    val fileType: Int
        get() {
            if (tree!!.nodeType.equals("jml", ignoreCase = true)) {
                return if (tree!!.numberOfChildren == 1) {
                    val child = tree!!.getChildNode(0).nodeType
                    if (child.equals("pattern", ignoreCase = true)) {
                        JML_PATTERN
                    } else if (child.equals("patternlist", ignoreCase = true)) {
                        JML_LIST
                    } else {
                        JML_INVALID
                    }
                } else {
                    JML_INVALID
                }
            } else {
                return JML_INVALID
            }
        }

    // Implementation of org.xml.sax.EntityResolver
    override fun resolveEntity(publicId: String?, systemId: String): InputSource? {
        if (Constants.DEBUG_JML_PARSING) {
            print("Resolve entity:")
            if (publicId != null) {
                print(" publicId=\"$publicId\"")
            }
            println(" systemId=\"$systemId\"")
        }

        if (systemId.equals("file://jml.dtd", ignoreCase = true)) {
            if (Constants.DEBUG_JML_PARSING) {
                println("--------- jml.dtd -----------")
                println(JMLDefs.JML_DTD)
                println("-----------------------------")
            }
            return InputSource(StringReader(JMLDefs.JML_DTD))
        }
        return null
    }

    // Implementation of org.xml.sax.DTDHandler
    override fun notationDecl(name: String?, publicId: String?, systemId: String?) {
        if (Constants.DEBUG_JML_PARSING) {
            print("Notation declaration: $name")
            if (publicId != null) {
                print(" publicId=\"$publicId\"")
            }
            if (systemId != null) {
                print(" systemId=\"$systemId\"")
            }
            print('\n')
        }
    }

    override fun unparsedEntityDecl(
        name: String?, publicId: String?, systemId: String?, notationName: String?
    ) {
        if (Constants.DEBUG_JML_PARSING) {
            print("Unparsed Entity Declaration: $name")
            if (publicId != null) {
                print(" publicId=\"$publicId\"")
            }
            if (systemId != null) {
                print(" systemId=\"$systemId\"")
            }
            println(" notationName=\"$notationName\"")
        }
    }

    // Implementation of org.xml.sax.ContentHandler
    override fun setDocumentLocator(locator: Locator?) {
        super.setDocumentLocator(locator)
        if (Constants.DEBUG_JML_PARSING) {
            println("Document locator supplied.")
        }
    }

    @Throws(SAXException::class)
    override fun startDocument() {
        if (Constants.DEBUG_JML_PARSING) {
            println("Start document")
        }
        try {
            startJMLPattern()
        } catch (je: JuggleException) {
            throw SAXException(je.message)
        }
    }

    @Throws(SAXException::class)
    override fun endDocument() {
        if (Constants.DEBUG_JML_PARSING) {
            println("End document")
        }
        try {
            endJMLPattern()
        } catch (je: JuggleException) {
            throw SAXException(je.message)
        }
    }

    @Throws(SAXException::class)
    override fun startElement(namespaceURI: String?, localName: String?, qName: String?, atts: Attributes) {
        if (Constants.DEBUG_JML_PARSING) {
            println("Start element: $qName")
            for (i in 0..<atts.length) {
                println(
                    ("  Attribute: "
                            + atts.getQName(i)
                            + ' '
                            + atts.getType(i)
                            + " \""
                            + atts.getValue(i)
                            + '"')
                )
            }
        }
        try {
            startJMLElement(qName)
            for (i in 0..<atts.length) {
                addJMLAttribute(atts.getQName(i), atts.getValue(i))
            }
        } catch (je: JuggleException) {
            throw SAXException(je.message)
        }
    }

    @Throws(SAXException::class)
    override fun endElement(namespaceURI: String?, localName: String?, qName: String?) {
        if (Constants.DEBUG_JML_PARSING) {
            println("End element: $qName")
        }
        try {
            endJMLElement(qName)
        } catch (je: JuggleException) {
            throw SAXException(je.message)
        }
    }

    @Throws(SAXException::class)
    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (Constants.DEBUG_JML_PARSING) {
            print("Characters: ")
            display(ch, start, length)
        }
        try {
            addJMLText(String(ch, start, length))
        } catch (je: JuggleException) {
            throw SAXException(je.message)
        }
    }

    override fun ignorableWhitespace(ch: CharArray, start: Int, length: Int) {
        if (Constants.DEBUG_JML_PARSING) {
            print("Ignorable Whitespace: ")
            display(ch, start, length)
        }
    }

    override fun processingInstruction(target: String?, data: String?) {
        if (Constants.DEBUG_JML_PARSING) {
            println("Processing instruction: $target $data")
        }
    }

    // Implementation of org.xml.sax.ErrorHandler
    @Throws(SAXException::class)
    override fun warning(exception: SAXParseException) {
        if (Constants.DEBUG_JML_PARSING) {
            println(
                ("SAX parsing warning: "
                        + exception.message
                        + " ("
                        + exception.systemId
                        + ':'
                        + exception.lineNumber
                        + ','
                        + exception.columnNumber
                        + ')')
            )
        }
        throw exception
    }

    @Throws(SAXException::class)
    override fun error(exception: SAXParseException) {
        if (Constants.DEBUG_JML_PARSING) {
            println(
                ("SAX parsing error: "
                        + exception.message
                        + " ("
                        + exception.systemId
                        + ':'
                        + exception.lineNumber
                        + ','
                        + exception.columnNumber
                        + ')')
            )
        }
        throw exception
    }

    @Throws(SAXException::class)
    override fun fatalError(exception: SAXParseException) {
        if (Constants.DEBUG_JML_PARSING) {
            println(
                ("SAX parsing fatal error: "
                        + exception.message
                        + " ("
                        + exception.systemId
                        + ':'
                        + exception.lineNumber
                        + ','
                        + exception.columnNumber
                        + ')')
            )
        }
        throw exception
    }

    //--------------------------------------------------------------------------
    // Methods called by parser to create the JMLNode tree
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionInternal::class)
    private fun startJMLPattern() {
        if (patternStarted) {
            throw JuggleExceptionInternal("startJMLPattern(): pattern already started")
        }
        patternStarted = true
    }

    @Throws(JuggleExceptionInternal::class)
    private fun startJMLElement(name: String?) {
        if (!patternStarted) {
            throw JuggleExceptionInternal("startJMLEleent(): pattern not started")
        }
        if (patternFinished) {
            throw JuggleExceptionInternal("startJMLElement(): pattern already finished")
        }
        if (currentNode == null && tree != null) {
            throw JuggleExceptionInternal("startJMLElement(): can only have one root element")
        }

        val newNode = JMLNode(name)
        if (currentNode != null) {
            currentNode!!.addChildNode(newNode)
            currentNode = newNode
        } else {
            currentNode = newNode
            tree = currentNode
        }
    }

    @Suppress("unused")
    @Throws(JuggleExceptionInternal::class)
    private fun endJMLElement(name: String?) {
        if (!patternStarted) {
            throw JuggleExceptionInternal("endJMLElement(): pattern not started")
        }
        if (patternFinished) {
            throw JuggleExceptionInternal("endJMLElement(): pattern already finished")
        }
        val cnode = currentNode
            ?: throw JuggleExceptionInternal("endJMLElement(): no correspanding startElement()")
        currentNode = cnode.parentNode
    }

    @Throws(JuggleExceptionInternal::class)
    private fun addJMLAttribute(name: String, value: String) {
        if (!patternStarted) {
            throw JuggleExceptionInternal("addJMLAttribute(): pattern not started")
        }
        if (patternFinished) {
            throw JuggleExceptionInternal("addJMLAttribute(): pattern already finished")
        }
        val cnode =
            currentNode ?: throw JuggleExceptionInternal("addJMLAttribute(): no element to add to")
        cnode.addAttribute(name, value)
    }

    @Throws(JuggleExceptionInternal::class)
    private fun addJMLText(text: String?) {
        if (!patternStarted) {
            throw JuggleExceptionInternal("addJMLText(): pattern not started")
        }
        if (patternFinished) {
            throw JuggleExceptionInternal("addJMLText(): pattern already finished")
        }
        val cnode =
            currentNode ?: throw JuggleExceptionInternal("addJMLText(): no element to add to")
        val newvalue: String? = if (cnode.nodeValue == null) {
            text
        } else {
            cnode.nodeValue + text
        }
        cnode.nodeValue = newvalue
    }

    @Throws(JuggleExceptionInternal::class)
    private fun endJMLPattern() {
        if (!patternStarted) {
            throw JuggleExceptionInternal("endJMLPattern(): pattern not started")
        }
        if (patternFinished) {
            throw JuggleExceptionInternal("endJMLPattern(): pattern already finished")
        }
        if (this.tree == null) {
            throw JuggleExceptionInternal("endJMLPattern(): empty pattern")
        }
        if (currentNode != null) {
            throw JuggleExceptionInternal("endJMLPattern(): missing endElement()")
        }
        patternFinished = true
    }

    companion object {
        const val JML_INVALID: Int = 0 // file types
        const val JML_PATTERN: Int = 1
        const val JML_LIST: Int = 2

        // Display text, escaping some characters.
        private fun display(ch: CharArray, start: Int, length: Int) {
            if (Constants.DEBUG_JML_PARSING) {
                for (i in start..<start + length) {
                    when (ch[i]) {
                        '\n' -> print("\\n")
                        '\t' -> print("\\t")
                        else -> print(ch[i])
                    }
                }
                print("\n")
            }
        }
    }
}
