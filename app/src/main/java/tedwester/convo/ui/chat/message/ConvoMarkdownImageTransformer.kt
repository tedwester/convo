package tedwester.convo.ui.chat.message

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.Density
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
        // PlaceholderConfig.size is consumed as sp downstream
        // (InlineTextContent Placeholder(width = size.width.sp, ...)), matching
        // the library default. Delegate for sizing, keep top alignment.
        return Coil2ImageTransformerImpl.placeholderConfig(
            density = density,
            containerSize = containerSize,
            intrinsicImageSize = intrinsicImageSize,
        ).copy(verticalAlign = PlaceholderVerticalAlign.Top)
    }
}
