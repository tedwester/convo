package tedwester.convo.ui.chat.message

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.SubcomposeAsyncImage
import tedwester.convo.features.chat.model.WebSearchCitation
import tedwester.convo.features.chat.model.domainFromUrl
import tedwester.convo.features.chat.model.faviconUrlFor
import tedwester.convo.ui.components.ConvoBottomSheet
import tedwester.convo.ui.components.ScrollColumnWithEdgeFades
import tedwester.convo.ui.components.rememberConvoSheetController
import tedwester.convo.ui.theme.AssistantSerifFamily
import tedwester.convo.ui.theme.convoModalSurface

private val PreviewIconSize = 22.dp
private val PreviewIconOverlap = 10.dp

@Composable
internal fun WebSearchResultsPill(
    citations: List<WebSearchCitation>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (citations.isEmpty()) return
    val haptics = LocalHapticFeedback.current
    val preview = citations.take(3)
    val pageLabel = if (citations.size == 1) "1 page" else "${citations.size} pages"

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(StatusLabelGrey.copy(alpha = 0.12f))
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
            .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StackedCitationFavicons(
            citations = preview,
            modifier = Modifier.width(
                PreviewIconSize + PreviewIconOverlap * (preview.size - 1).coerceAtLeast(0),
            ),
        )
        Text(
            text = pageLabel,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
            fontFamily = AssistantSerifFamily,
            fontSize = 14.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun StackedCitationFavicons(
    citations: List<WebSearchCitation>,
    modifier: Modifier = Modifier,
) {
    val iconBacking = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier.height(PreviewIconSize),
        contentAlignment = Alignment.CenterStart,
    ) {
        citations.forEachIndexed { index, citation ->
            val domain = domainFromUrl(citation.url)
            Box(
                modifier = Modifier
                    .zIndex(index.toFloat())
                    .offset(x = PreviewIconOverlap * index)
                    .size(PreviewIconSize)
                    .clip(CircleShape)
                    .background(iconBacking),
                contentAlignment = Alignment.Center,
            ) {
                SubcomposeAsyncImage(
                    model = faviconUrlFor(citation.url),
                    contentDescription = null,
                    modifier = Modifier
                        .size(PreviewIconSize)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    loading = {
                        CitationFaviconFallback(domain = domain, size = PreviewIconSize)
                    },
                    error = {
                        CitationFaviconFallback(domain = domain, size = PreviewIconSize)
                    },
                )
            }
        }
    }
}

@Composable
internal fun WebSearchResultsModal(
    citations: List<WebSearchCitation>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheet = rememberConvoSheetController()
    val context = LocalContext.current
    val sheetBackground = convoModalSurface()

    ConvoBottomSheet(
        controller = sheet,
        onDismissRequest = onDismiss,
        useDialog = true,
        sheetHeightFraction = 0.82f,
        contentScrollable = false,
        contentHorizontalPadding = 20.dp,
        contentVerticalPadding = 10.dp,
        consumeSheetClicks = false,
        title = "Sources",
        modifier = modifier,
    ) {
        ScrollColumnWithEdgeFades(
            modifier = Modifier.weight(1f),
            background = sheetBackground,
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            citations.forEach { citation ->
                WebSearchResultCard(
                    citation = citation,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(citation.url)),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun WebSearchResultCard(
    citation: WebSearchCitation,
    onClick: () -> Unit,
) {
    val domain = domainFromUrl(citation.url)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StatusLabelGrey.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = citation.title.ifBlank { domain },
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = AssistantSerifFamily,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (citation.publishedDate.isNotBlank()) {
            Text(
                text = citation.publishedDate,
                color = StatusLabelGrey,
                fontFamily = AssistantSerifFamily,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        if (citation.description.isNotBlank()) {
            Text(
                text = citation.description.trim(),
                color = StatusLabelGrey.copy(alpha = 0.95f),
                fontFamily = AssistantSerifFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SubcomposeAsyncImage(
                model = faviconUrlFor(citation.url),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(StatusLabelGrey.copy(alpha = 0.18f)),
                contentScale = ContentScale.Crop,
                loading = {
                    CitationFaviconFallback(domain = domain, size = 18.dp)
                },
                error = {
                    CitationFaviconFallback(domain = domain, size = 18.dp)
                },
            )
            Text(
                text = domain,
                color = StatusLabelGrey,
                fontFamily = AssistantSerifFamily,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CitationFaviconFallback(
    domain: String,
    size: androidx.compose.ui.unit.Dp,
) {
    val initial = domain.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(StatusLabelGrey.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = StatusLabelGrey,
            fontFamily = AssistantSerifFamily,
            fontSize = (size.value * 0.45f).sp,
        )
    }
}
