package tedwester.convo.ui.chat.message

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import tedwester.convo.features.chat.model.WebSearchCitation
import tedwester.convo.features.chat.model.WebSearchStep
import tedwester.convo.features.chat.model.domainFromUrl
import tedwester.convo.features.chat.model.faviconUrlFor
import tedwester.convo.ui.components.ScrollRowWithEdgeFades
import tedwester.convo.ui.icons.ConvoIcons
import tedwester.convo.ui.theme.AssistantSerifFamily

private val TimelineLineColor = StatusLabelGrey.copy(alpha = 0.35f)
private val SearchCircleSize = 28.dp
private val TimelineConnectorMinHeight = 20.dp
private val TimelineStepGap = 12.dp

@Composable
internal fun WebSearchTimeline(
    steps: List<WebSearchStep>,
    modifier: Modifier = Modifier,
) {
    val visibleSteps = steps.filter { step ->
        step.isSearching || step.query.isNotBlank() || step.citations.isNotEmpty()
    }
    if (visibleSteps.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        visibleSteps.forEachIndexed { index, step ->
            WebSearchTimelineStep(
                step = step,
                isFirst = index == 0,
                isLast = index == visibleSteps.lastIndex,
            )
        }
    }
}

@Composable
private fun WebSearchTimelineStep(
    step: WebSearchStep,
    isFirst: Boolean,
    isLast: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(SearchCircleSize)
                .fillMaxHeight(),
        ) {
            if (!isFirst) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(TimelineStepGap)
                        .background(TimelineLineColor),
                )
            }
            SearchTimelineNode(inProgress = step.isSearching)
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .weight(1f)
                        .defaultMinSize(minHeight = TimelineConnectorMinHeight)
                        .background(TimelineLineColor),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Box(
                modifier = Modifier.heightIn(min = SearchCircleSize),
                contentAlignment = Alignment.CenterStart,
            ) {
                SearchStepLabel(step = step)
            }
            if (step.citations.isNotEmpty()) {
                WebSearchCitationRow(
                    citations = step.citations,
                    modifier = Modifier.padding(top = 8.dp, bottom = if (isLast) 0.dp else TimelineStepGap),
                )
            } else if (!isLast) {
                Spacer(modifier = Modifier.height(TimelineStepGap))
            }
        }
    }
}

@Composable
private fun SearchStepLabel(step: WebSearchStep) {
    val queryColor = MaterialTheme.colorScheme.onSurface

    if (step.isSearching && step.query.isBlank()) {
        ShimmerStatusLabel(text = "Searching")
        return
    }

    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = StatusLabelGrey)) {
                append("Searching")
            }
            if (step.query.isNotBlank()) {
                append(" ")
                withStyle(SpanStyle(color = queryColor)) {
                    append(step.query)
                }
            }
        },
        fontFamily = AssistantSerifFamily,
        fontSize = 15.5.sp,
        lineHeight = 22.sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SearchTimelineNode(inProgress: Boolean) {
    Box(
        modifier = Modifier
            .size(SearchCircleSize)
            .clip(CircleShape)
            .border(
                width = 1.dp,
                color = StatusLabelGrey.copy(alpha = if (inProgress) 0.55f else 0.4f),
                shape = CircleShape,
            )
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = ConvoIcons.Search(),
            contentDescription = if (inProgress) "Searching" else "Searched",
            tint = StatusLabelGrey.copy(alpha = if (inProgress) 0.9f else 0.75f),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun WebSearchCitationRow(
    citations: List<WebSearchCitation>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val background = MaterialTheme.colorScheme.background

    ScrollRowWithEdgeFades(
        background = background,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        citations.forEach { citation ->
            WebSearchCitationChip(
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

@Composable
private fun WebSearchCitationChip(
    citation: WebSearchCitation,
    onClick: () -> Unit,
) {
    val label = citation.title.ifBlank { domainFromUrl(citation.url) }
    val domain = domainFromUrl(citation.url)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(StatusLabelGrey.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
    ) {
        SubcomposeAsyncImage(
            model = faviconUrlFor(citation.url),
            contentDescription = null,
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(StatusLabelGrey.copy(alpha = 0.18f)),
            contentScale = ContentScale.Crop,
            loading = {
                CitationFaviconFallback(domain = domain)
            },
            error = {
                CitationFaviconFallback(domain = domain)
            },
        )
        Text(
            text = label,
            color = StatusLabelGrey,
            fontFamily = AssistantSerifFamily,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CitationFaviconFallback(domain: String) {
    val initial = domain.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(StatusLabelGrey.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = StatusLabelGrey,
            fontFamily = AssistantSerifFamily,
            fontSize = 9.sp,
        )
    }
}
