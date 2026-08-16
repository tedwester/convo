package tedwester.convo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tedwester.convo.ui.input.ConvoKeyboardOptions
import tedwester.convo.ui.theme.ConvoFieldTokens
import tedwester.convo.ui.theme.convoFieldFill
import tedwester.convo.ui.theme.convoFieldOutline
import tedwester.convo.ui.theme.convoFieldPlaceholder

@Composable
fun ConvoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = ConvoKeyboardOptions.Text,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: ImageVector? = null,
    leadingIconPainter: Painter? = null,
    focusRequester: FocusRequester? = null,
    shape: Shape = RoundedCornerShape(ConvoFieldTokens.CornerRadius),

    textAlpha: Float = 1f,

    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val fillColor = convoFieldFill()
    val outlineColor = convoFieldOutline()
    val iconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val textColor = MaterialTheme.colorScheme.onBackground.copy(
        alpha = if (enabled) textAlpha else 0.45f * textAlpha,
    )
    val baseGhostColor = convoFieldPlaceholder()
    val ghostColor = baseGhostColor.copy(alpha = baseGhostColor.alpha * textAlpha)

    val fieldTextStyle = MaterialTheme.typography.bodyMedium.copy(
        color = textColor,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    )
    val placeholderStyle = fieldTextStyle.copy(color = ghostColor)
    val lineHeight = ConvoFieldTokens.SingleLineHeight
    val showLeading = leadingIconPainter != null || leadingIcon != null

    Row(
        modifier = modifier
            .then(if (singleLine) Modifier.height(lineHeight) else Modifier.heightIn(min = lineHeight))
            .clip(shape)
            .background(fillColor)
            .border(
                width = ConvoFieldTokens.OutlineWidth,
                color = outlineColor,
                shape = shape,
            )
            .then(
                if (onClick != null && enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(
                horizontal = ConvoFieldTokens.HorizontalPadding,
                vertical = if (singleLine) 0.dp else ConvoFieldTokens.MultilineVerticalPadding,
            ),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
    ) {
        if (showLeading) {
            if (leadingIconPainter != null) {
                Icon(
                    painter = leadingIconPainter,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp),
                )
            } else if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp),
                )
            }
            Box(modifier = Modifier.width(10.dp))
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (singleLine) {
                        Modifier.height(lineHeight)
                    } else {
                        Modifier.fillMaxHeight()
                    },
                ),
            contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
        ) {
            if (onClick != null) {
                Text(
                    text = value.ifEmpty { placeholder },
                    style = if (value.isEmpty() && placeholder.isNotEmpty()) {
                        placeholderStyle
                    } else {
                        fieldTextStyle
                    },
                    maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    textStyle = fieldTextStyle,
                    cursorBrush = SolidColor(textColor),
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    interactionSource = interactionSource,
                    decorationBox = { innerField ->
                        Box(
                            modifier = if (singleLine) {
                                Modifier
                            } else {
                                Modifier.fillMaxSize()
                            },
                        ) {
                            if (value.isEmpty() && placeholder.isNotEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = placeholderStyle,
                                )
                            }
                            innerField()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (!singleLine) Modifier.fillMaxHeight() else Modifier)
                        .then(
                            if (focusRequester != null) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }

        if (trailing != null) {
            Box(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}
