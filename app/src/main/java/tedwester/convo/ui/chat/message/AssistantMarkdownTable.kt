package tedwester.convo.ui.chat.message

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextOverflow
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox

/**
 * Table cells wrap instead of cutting off with an ellipsis. The library's default
 * forces every header and row cell onto a single ellipsized line, which eats
 * content in narrow chat bubbles.
 */
@Composable
internal fun rememberAssistantMarkdownComponents(): MarkdownComponents = remember {
    markdownComponents(
        checkbox = { MarkdownCheckBox(it.content, it.node, it.typography.text) },
        table = { model ->
            MarkdownTable(
                content = model.content,
                node = model.node,
                style = model.typography.table,
                headerBlock = { content, header, tableWidth, style ->
                    MarkdownTableHeader(
                        content = content,
                        header = header,
                        tableWidth = tableWidth,
                        style = style,
                        maxLines = Int.MAX_VALUE,
                        overflow = TextOverflow.Clip,
                    )
                },
                rowBlock = { content, row, tableWidth, style ->
                    MarkdownTableRow(
                        content = content,
                        header = row,
                        tableWidth = tableWidth,
                        style = style,
                        maxLines = Int.MAX_VALUE,
                        overflow = TextOverflow.Clip,
                    )
                },
            )
        },
    )
}
