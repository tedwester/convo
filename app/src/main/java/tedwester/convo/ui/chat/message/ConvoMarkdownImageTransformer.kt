package tedwester.convo.ui.chat.message

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.coil2.Coil2ImageTransformerImpl
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.PlaceholderConfig
import tedwester.convo.core.image.AssistantImages

/**
 * Markdown image rendering for assistant replies. All URL validity lives in
 * [AssistantImages] — this transformer only maps the verdict onto Coil:
 *
 * - unrenderable URLs return null so no blank placeholder is ever reserved
 *   for a broken or half-streamed image
 * - valid URLs delegate to Coil (data-URI capable via
 *   `DataUriFetcher.StringFactory`), top-aligned so no gap opens above
 * - the loading placeholder is density-based (dp), not font-scaled (sp),
 *   so large-font users don't get a taller blank box
 */
internal object ConvoMarkdownImageTransformer : ImageTransformer {

    @Composable
    override fun transform(link: String): ImageData? {
        val normalized = AssistantImages.normalizeImageUrl(link) ?: return null
        return Coil2ImageTransformerImpl.transform(normalized)?.copy(
            contentScale = ContentScale.Fit,
            alignment = Alignment.TopCenter,
        )
    }

    @Composable
    override fun intrinsicSize(painter: Painter): Size {
        return Coil2ImageTransformerImpl.intrinsicSize(painter)
    }

    override fun placeholderConfig(
        density: Density,
        containerSize: Size,
        intrinsicImageSize: Size,
    ): PlaceholderConfig {
        if (!intrinsicImageSize.isUnspecified &&
            intrinsicImageSize.width > 0f &&
            intrinsicImageSize.height > 0f
        ) {
            return Coil2ImageTransformerImpl.placeholderConfig(
                density = density,
                containerSize = containerSize,
                intrinsicImageSize = intrinsicImageSize,
            ).copy(verticalAlign = PlaceholderVerticalAlign.Top)
        }
        with(density) {
            val maxWidthPx = 320.dp.toPx()
            val minWidthPx = 120.dp.toPx()
            val widthPx = when {
                containerSize.isUnspecified || containerSize.width <= 0f -> maxWidthPx
                else -> containerSize.width.coerceIn(minWidthPx, maxWidthPx)
            }
            val heightPx = (widthPx * 9f / 16f).coerceIn(120.dp.toPx(), 320.dp.toPx())
            return PlaceholderConfig(
                size = Size(widthPx, heightPx),
                verticalAlign = PlaceholderVerticalAlign.Top,
            )
        }
    }
}
