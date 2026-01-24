//
// SiteswapParser.kt
//
// Parse a siteswap string into a tree of SiteswapTreeItems.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.notation.ssparser

import jugglinglab.notation.ssparser.generated.JlSiteswapLexer
import jugglinglab.notation.ssparser.generated.JlSiteswapParser
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream

object SiteswapParser {
    //@Throws(ParseException::class)
    fun parsePattern(pattern: String): SiteswapTreeItem {
        val stream = CharStreams.fromString(pattern)
        val lexer = JlSiteswapLexer(stream)
        val tokens = CommonTokenStream(lexer)
        val parser = JlSiteswapParser(tokens)

        // Start parsing at the 'pattern' rule
        val tree = parser.pattern()

        // Visit the tree to build the SiteswapTreeItem hierarchy
        val visitor = SiteswapAstVisitor()
        return visitor.visit(tree)
    }
}
