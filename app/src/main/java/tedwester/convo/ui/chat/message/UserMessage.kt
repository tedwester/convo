package tedwester.convo.ui.chat.message

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tedwester.convo.features.chat.model.ChatAttachment
import tedwester.convo.features.chat.model.ChatMessage
import tedwester.convo.ui.chat.RectAnchor
import tedwester.convo.ui.chat.attachments.AttachmentFileBadge
import tedwester.convo.ui.chat.attachments.AttachmentImageThumb
import kotlin.math.PI
import kotlin.math.sin

private const val PromptBarAnimMs = 200
private val PromptBarTopGap = 6.dp

/**
 * The send animation is the genie: the bubble you just typed is the same object that arrives
 * in the thread, so it climbs out of the compose field instead of appearing somewhere else.
 *
 * The bubble leaves the field at the end of the line you typed on — where the send button
 * sits — and travels up into the thread along a curve that bows out to the side mid-flight,
 * the way the Mac genie veers instead of going straight. While it travels it is tall and
 * narrow, the way a thread of liquid behaves when it is drawn out of a narrow opening, and it
 * relaxes into its final shape as it lands.
 *
 * The shape stretches while the text stays put: the bubble's body carries the scale, and the
 * text carries its inverse about the same corner, so the letters never smear. The text is
 * resolved once the body has opened out enough to hold it, so the message is never shown
 * twice or shown wrong.
 *
 * [flightOrigin] is where the compose field sits on screen. Without it — a chat that is
 * opening rather than a message being sent — the bubble simply scales into place.
 */
private val GenieSpring = spring<Float>(
    dampingRatio = 0.9f,
    stiffness = 380f,
)

/** Width the bubble is at while it is still a thread leaving the field. */
private const val GenieStartWidth = 0.42f

/** How much taller than usual the bubble is at that moment. */
private const val GenieStartHeight = 1.6f

/** How far sideways the flight bows out at the middle of its curve. */
private val GenieSway = 36.dp

/** How far the bubble tips over mid-flight, in degrees. Zero at both ends. */
private const val GenieTiltDeg = 2.5f

/** How far the shape has to open out before the text starts to resolve. */
private const val GenieTextOpenness = 0.72f

@Composable
internal fun UserMessage(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    animToken: Int = 0,
    flightOrigin: RectAnchor? = null,
    promptBarVisible: Boolean = false,
    actionsEnabled: Boolean = true,
    onTogglePromptBar: () -> Unit = {},
    onStartEdit: () -> Unit = {},
    onViewAttachment: (ChatAttachment) -> Unit = {},
) {
    val dark = isSystemInDarkTheme()
    val clipboard = LocalClipboardManager.current
    val images = remember(message.attachments) { message.attachments.filter { it.isImage } }
    val files = remember(message.attachments) {
        message.attachments.filterNot {
            it.isImage || it.mimeType?.startsWith("audio/", ignoreCase = true) == true
        }
    }
    val audioAttachment = remember(message.attachments) {
        message.attachments.firstOrNull {
            it.mimeType?.startsWith("audio/", ignoreCase = true) == true
        }
    }
    val textToShow = remember(message.content) { message.userDisplayText() }

    val bubbleGrey = if (dark) Color(0xFF2C2B29) else Color(0xFFE4E6EC)
    val onBubble = if (dark) Color(0xFFE7EAF0) else Color(0xFF1A1D23)

    // Flight progress: 1 means settled. A send drives it back to 0 and lets it spring home.
    val progress = remember { Animatable(1f) }
    var lastAnimatedToken by remember(message.id) { mutableIntStateOf(0) }

    // Where the bubble comes to rest, kept by reference. It is only turned into a
    // rectangle on the frames a flight is running, so settled bubbles and keyboard
    // transitions never pay for measuring.
    val settledAnchor = remember(message.id) { RectAnchor() }
    val density = LocalDensity.current
    val swayPx = with(density) { GenieSway.toPx() }

    LaunchedEffect(animToken) {
        if (animToken <= 0) {
            progress.snapTo(1f)
            lastAnimatedToken = 0
            return@LaunchedEffect
        }
        if (animToken == lastAnimatedToken) return@LaunchedEffect
        lastAnimatedToken = animToken
        progress.snapTo(0f)
        progress.animateTo(1f, GenieSpring)
    }

    // Before the effect above runs on the very first frame, a freshly sent message already
    // has a token waiting for it. Treat that frame as progress 0 so the bubble never flashes
    // into its resting place before it starts climbing.
    val pendingSend = animToken > lastAnimatedToken && animToken > 0

    val toggleInteraction = remember { MutableInteractionSource() }
    val promptBarProgress by animateFloatAsState(
        targetValue = if (promptBarVisible) 1f else 0f,
        animationSpec = tween(durationMillis = PromptBarAnimMs, easing = FastOutSlowInEasing),
        label = "promptBarProgress",
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clickable(
                        interactionSource = toggleInteraction,
                        indication = null,
                        onClick = onTogglePromptBar,
                    ),
            ) {
            if (images.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    images.take(4).forEach { attachment ->
                        AttachmentImageThumb(
                            attachment = attachment,
                            onRemove = null,
                            onClick = { onViewAttachment(attachment) },
                            size = 72.dp,
                        )
                    }
                }
            }

            if (files.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    files.forEach { attachment ->
                        AttachmentFileBadge(
                            attachment = attachment,
                            onRemove = null,
                        )
                    }
                }
            }

            if (textToShow.isNotBlank()) {
                val bubbleShape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier.onGloballyPositioned { settledAnchor.coords = it },
                ) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                val p = if (pendingSend) 0f else progress.value
                                // Only a flying bubble measures anything. Settled bubbles
                                // skip it entirely, so scrolling and keyboard transitions
                                // never pay for the flight's bookkeeping.
                                val origin = if (p < 1f) {
                                    flightOrigin?.current()
                                } else {
                                    null
                                }
                                val settled = if (p < 1f) settledAnchor.current() else Rect.Zero
                                val hasFlight = origin != null && !origin.isEmpty && !settled.isEmpty

                                if (hasFlight) {
                                    val e = p.coerceIn(0f, 1f)
                                    // Climb out of the compose field along a curve that bows
                                    // out to the side mid-flight instead of going straight.
                                    val bow = sin(PI * e).toFloat()
                                    translationX = (origin.right - settled.right) * (1f - e) -
                                        bow * swayPx
                                    translationY = (origin.bottom - settled.bottom) * (1f - e)
                                    rotationZ = -bow * GenieTiltDeg
                                    // A thread of liquid: narrow and tall on the way out.
                                    scaleX = GenieStartWidth + (1f - GenieStartWidth) * e
                                    scaleY = GenieStartHeight + (1f - GenieStartHeight) * e
                                } else {
                                    scaleX = 0.55f + 0.45f * p
                                    scaleY = 0.55f + 0.45f * p
                                }
                                // Anchored at the corner that sits over the send button.
                                transformOrigin = TransformOrigin(1f, 1f)
                            }
                            .clip(bubbleShape)
                            .background(bubbleGrey),
                    ) {
                        Text(
                            text = textToShow,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = onBubble,
                                fontWeight = FontWeight.ExtraLight,
                            ),
                            modifier = Modifier
                                .graphicsLayer {
                                    val p = if (pendingSend) 0f else progress.value
                                    val origin = if (p < 1f) {
                                        flightOrigin?.current()
                                    } else {
                                        null
                                    }
                                    val settled = if (p < 1f) {
                                        settledAnchor.current()
                                    } else {
                                        Rect.Zero
                                    }
                                    val hasFlight =
                                        origin != null && !origin.isEmpty && !settled.isEmpty
                                    if (hasFlight) {
                                        val e = p.coerceIn(0f, 1f)
                                        val bow = sin(PI * e).toFloat()
                                        // Counteract the body so the letters stay crisp and
                                        // level while the shape stretches and tips around them.
                                        val openness = GenieStartWidth +
                                            (1f - GenieStartWidth) * e
                                        val stretch = GenieStartHeight +
                                            (1f - GenieStartHeight) * e
                                        scaleX = 1f / openness
                                        scaleY = 1f / stretch
                                        rotationZ = bow * GenieTiltDeg
                                    }
                                    transformOrigin = TransformOrigin(1f, 1f)
                                    // Resolve the text only once the bubble has opened out
                                    // enough to hold it.
                                    val openness = if (hasFlight) {
                                        GenieStartWidth + (1f - GenieStartWidth) * p
                                    } else {
                                        0.55f + 0.45f * p
                                    }
                                    alpha = ((openness - GenieTextOpenness) /
                                        (1f - GenieTextOpenness)).coerceIn(0f, 1f)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }

            audioAttachment?.let { attachment ->
                AudioPlaybackRow(
                    path = attachment.path,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            }

            if (promptBarProgress > 0f) {
                Box(
                    modifier = Modifier
                        .height((PromptBarTopGap + PromptBarHeight) * promptBarProgress)
                        .clipToBounds()
                        .graphicsLayer { alpha = promptBarProgress },
                ) {
                    UserPromptControls(
                        copyEnabled = textToShow.isNotBlank(),
                        editEnabled = actionsEnabled,
                        onCopy = {
                            if (textToShow.isNotBlank()) {
                                clipboard.setText(AnnotatedString(textToShow))
                            }
                        },
                        onEdit = onStartEdit,
                        modifier = Modifier.padding(top = PromptBarTopGap),
                    )
                }
            }
        }
    }
}
