//
// SiteswapParser.kt
//
// Parse a siteswap string into a tree of SiteswapTreeItems.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.notation.ssparser

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.notation.ssparser.generated.JlSiteswapLexer
import org.jugglinglab.notation.ssparser.generated.JlSiteswapParser
import org.jugglinglab.util.jlGetStringResource
import org.antlr.v4.kotlinruntime.BaseErrorListener
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.RecognitionException
import org.antlr.v4.kotlinruntime.Recognizer
import org.antlr.v4.kotlinruntime.Token

object SiteswapParser {
    @Throws(SiteswapParseException::class)
    fun parsePattern(pattern: String): SiteswapTreeItem {
        val stream = CharStreams.fromString(pattern)
        val lexer = JlSiteswapLexer(stream)
        val tokens = CommonTokenStream(lexer)
        val parser = JlSiteswapParser(tokens)

        val logger = SiteswapErrorLogger()
        lexer.removeErrorListeners()
        lexer.addErrorListener(logger)
        parser.removeErrorListeners()
        parser.addErrorListener(logger)

        // start parsing at the 'pattern' rule
        val tree = parser.pattern()
        logger.throwIfErrors()

        // build the SiteswapTreeItem hierarchy
        return SiteswapAstVisitor().visit(tree)
    }
}

// Helper to log error messages during lexing/parsing

class SiteswapErrorLogger : BaseErrorListener() {
    val errorLog = mutableListOf<String>()

    override fun syntaxError(
        recognizer: Recognizer<*, *>,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String,
        e: RecognitionException?
    ) {
        val message = if (offendingSymbol == null) {
            jlGetStringResource(Res.string.error_pattern_parsing, msg)
        } else {
            val token = offendingSymbol as Token
            jlGetStringResource(
                Res.string.error_pattern_syntax,
                token.text,
                charPositionInLine + 1
            )
        }
        errorLog.add(message)
    }

    @Throws(SiteswapParseException::class)
    fun throwIfErrors() {
        if (errorLog.isNotEmpty()) {
            throw SiteswapParseException(errorLog.joinToString("\n"))
        }
    }
}
