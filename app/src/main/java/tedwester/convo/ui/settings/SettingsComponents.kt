package tedwester.convo.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tedwester.convo.ui.components.ConvoIconButton
import tedwester.convo.ui.components.ConvoIconButtonGap
import tedwester.convo.ui.icons.ConvoIcons

internal val SettingsHeaderHorizontalPadding = 16.dp
internal val SettingsHeaderVerticalPadding = 10.dp
internal val SettingsTitleStartGap = 12.dp

internal fun formatModelDisplay(id: String, name: String?): String {
    if (!name.isNullOrBlank()) return name
    val slug = id.substringAfterLast('/').ifBlank { id }
    return slug.replace('-', ' ')
}

@Composable
internal fun SettingsSection(
    title: String,
    description: String,
    showDividerAbove: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (showDividerAbove) {
        SettingsSectionDivider()
    }
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.2).sp,
        ),
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(16.dp))
    content()
}

@Composable
internal fun SettingsSectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 24.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    )
}

@Composable
internal fun SettingsHeader(
    onClose: () -> Unit,
    onOpenHelp: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = SettingsHeaderHorizontalPadding,
                end = SettingsHeaderHorizontalPadding,
                top = SettingsHeaderVerticalPadding,
                bottom = SettingsHeaderVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConvoIconButton(
            painter = ConvoIcons.Close(),
            contentDescription = "Close settings",
            onClick = onClose,
        )
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = (-0.2).sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = SettingsTitleStartGap, end = ConvoIconButtonGap),
        )
        if (onOpenHelp != null) {
            ConvoIconButton(
                painter = ConvoIcons.CircleHelp(),
                contentDescription = "Help",
                onClick = onOpenHelp,
            )
        }
    }
}
