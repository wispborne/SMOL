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

package smol.app.composables

import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import smol.access.Constants
import smol.app.themes.SmolTheme.hyperlink


/**
 * Autolinkifies text, written by Wisp.
 */
@Composable
fun SmolLinkText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val matches = Constants.URI_REGEX.findAll(text)
    val slices = matches.map { it.groups.firstOrNull()?.range }.toList()
    var index = 0
    val data = mutableListOf<LinkTextData>()

    while (index < text.length) {
        if (slices.any { it?.first == index }) {
            val url = buildString {
                while (slices.none { it?.last == (index) }) {
                    append(text[index])
                    index++
                }
                append(text[index])
            }

            data += LinkTextData(
                text = url,
                url = url
            )
            index++
        } else {
            data += LinkTextData(
                text = buildString {
                    while (slices.none { it?.first == index } && index < text.length) {
                        append(text[index])
                        index++
                    }
                },
            )
        }
    }

    SmolLinkText(
        linkTextData = data,
        modifier = modifier,
        style = style,
        softWrap = softWrap,
        overflow = overflow,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
    )
}

data class LinkTextData(
    val text: String,
    val url: String? = null,
    val onClick: ((str: AnnotatedString.Range<String>) -> Unit)? = null,
)

@Composable
fun SmolLinkText(
    linkTextData: List<LinkTextData>,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default.copy(color = LocalContentColor.current),
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val annotatedString = createAnnotatedString(linkTextData)
    val uriHandler = LocalUriHandler.current

    SmolClickableText(
        text = annotatedString,
        onClick = { offset ->
            linkTextData.forEach { annotatedStringData ->
                if (annotatedStringData.url != null) {
                    annotatedString.getStringAnnotations(
                        tag = annotatedStringData.url,
                        start = offset,
                        end = offset,
                    )
                        .firstOrNull()
                        ?.let {
                            if (annotatedStringData.onClick != null) {
                                annotatedStringData.onClick.invoke(it)
                            } else {
                                uriHandler.openUri(annotatedStringData.url)
                            }
                        }
                }
            }
        },
        modifier = modifier,
        style = style,
        softWrap = softWrap,
        overflow = overflow,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
    )
}

@Composable
private fun createAnnotatedString(data: List<LinkTextData>): AnnotatedString {
    return buildAnnotatedString {
        data.forEach { linkTextData ->
            if (linkTextData.url != null) {
                pushStringAnnotation(
                    tag = linkTextData.url,
                    annotation = linkTextData.url,
                )
                withStyle(
                    style = SpanStyle(
                        color = MaterialTheme.colors.hyperlink,
                        textDecoration = TextDecoration.Underline,
                    ),
                ) {
                    append(linkTextData.text)
                }
                pop()
            } else {
                withStyle(
                    style = SpanStyle(
                        color = LocalContentColor.current,
                    ),
                ) {
                    append(linkTextData.text)
                }
            }
        }
    }
}
