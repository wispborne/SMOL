/*
 * This file is distributed under the GPLv3. An informal description follows:
 * - Anyone can copy, modify and distribute this software as long as the other points are followed.
 * - You must include the license and copyright notice with each and every distribution.
 * - You may this software for commercial purposes.
 * - If you modify it, you must indicate changes made to the code.
 * - Any modifications of this code base MUST be distributed with the same license, GPLv3.
 * - This software is provided without warranty.
 * - The software author or license can not be held liable for any damages inflicted by the software.
 * The full license is available from <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package smol_app.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

typealias StringAnnotation = AnnotatedString.Range<String>
typealias SymbolAnnotation = Pair<AnnotatedString, StringAnnotation?>

object MarkdownParser {

    // Regex containing the syntax tokens
    val symbolPattern by lazy {
        Regex("""(https?://[^\s\t\n]+)|(`[^`]+`)|(@.+?)|(\*\*.+?\*\*)|(\*.+?\*)|(__.+?__)|(_.+?_)|(~~.+?~~)""")
    }

    // Accepted annotations for the ClickableTextWrapper
    enum class SymbolAnnotationType {
        PERSON, LINK
    }
    // Pair returning styled content and annotation for ClickableText when matching syntax token

    /**
     * Format a message following Markdown-lite syntax
     * | @username -> bold, primary color and clickable element
     * | http(s)://... -> clickable link, opening it into the browser
     * | **bold** -> bold
     * | *italic* -> italic
     * | _italic_ -> italic
     * | ~~strikethrough~~ -> strikethrough
     * | __underline__ -> underline
     * | `MyClass.myMethod` -> inline code styling
     *
     * @param text contains message to be parsed
     * @param primary whether to use primary color
     * @return AnnotatedString with annotations used inside the ClickableText wrapper
     */
    @Composable
    fun messageFormatter(
        text: String,
        linkColor: Color
    ): AnnotatedString {
        val tokens = symbolPattern.findAll(text)
        val matchedtokens = mutableListOf(tokens)

        return buildAnnotatedString {

            var cursorPosition = 0

            for (token in tokens) {
                append(text.slice(cursorPosition until token.range.first))

                val (annotatedString, stringAnnotation) = getSymbolAnnotation(
                    matchResult = token,
                    linkColor = linkColor,
                )
                append(annotatedString)

                if (stringAnnotation != null) {
                    val (item, start, end, tag) = stringAnnotation
                    addStringAnnotation(tag = tag, start = start, end = end, annotation = item)
                }

                cursorPosition = token.range.last + 1
            }

            if (!tokens.none()) {
                append(text.slice(cursorPosition..text.lastIndex))
            } else {
                append(text)
            }
        }
    }

    /**
     * Map regex matches found in a message with supported syntax symbols
     *
     * @param matchResult is a regex result matching our syntax symbols
     * @return pair of AnnotatedString with annotation (optional) used inside the ClickableText wrapper
     */
    private fun getSymbolAnnotation(
        matchResult: MatchResult,
        linkColor: Color,
    ): SymbolAnnotation {
        return when {
            matchResult.value.startsWith("@") -> SymbolAnnotation(
                AnnotatedString(
                    text = matchResult.value,
                    spanStyle = SpanStyle(
                        color = linkColor,
                        fontWeight = FontWeight.Bold
                    )
                ),
                StringAnnotation(
                    item = matchResult.value.substring(1),
                    start = matchResult.range.first,
                    end = matchResult.range.last,
                    tag = SymbolAnnotationType.PERSON.name
                )
            )
            matchResult.value.startsWith("**") -> SymbolAnnotation(
                AnnotatedString(
                    text = matchResult.value.removeSurrounding("**"),
                    spanStyle = SpanStyle(fontWeight = FontWeight.Bold)
                ),
                null
            )
            matchResult.value.startsWith('*') -> SymbolAnnotation(
                AnnotatedString(
                    text = matchResult.value.removeSurrounding("*"),
                    spanStyle = SpanStyle(fontStyle = FontStyle.Italic)
                ),
                null
            )
            matchResult.value.startsWith("__") -> SymbolAnnotation(
                AnnotatedString(
                    text = matchResult.value.removeSurrounding("__"),
                    spanStyle = SpanStyle(textDecoration = TextDecoration.Underline)
                ),
                null
            )
            matchResult.value.startsWith('_') -> SymbolAnnotation(
                AnnotatedString(
                    text = matchResult.value.removeSurrounding("_"),
                    spanStyle = SpanStyle(fontStyle = FontStyle.Italic)
                ),
                null
            )
            matchResult.value.startsWith('~') -> SymbolAnnotation(
                AnnotatedString(
                    text = matchResult.value.removeSurrounding("~"),
                    spanStyle = SpanStyle(textDecoration = TextDecoration.LineThrough)
                ),
                null
            )
            matchResult.value.startsWith('`') -> SymbolAnnotation(
                AnnotatedString(
                    text = matchResult.value.removeSurrounding("`"),
                    spanStyle = SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        baselineShift = BaselineShift(0.2f)
                    )
                ),
                null
            )
            matchResult.value.startsWith("http") -> SymbolAnnotation(
                AnnotatedString(
                    text = matchResult.value,
                    spanStyle = SpanStyle(
                        color = linkColor
                    )
                ),
                StringAnnotation(
                    item = matchResult.value,
                    start = matchResult.range.first,
                    end = matchResult.range.last,
                    tag = SymbolAnnotationType.LINK.name
                )
            )
            else -> SymbolAnnotation(AnnotatedString(matchResult.value), null)
        }
    }
}