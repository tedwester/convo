package tedwester.convo.ui.chat.message

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.Density
import com.mikepenz.markdown.coil2.Coil2ImageTransformerImpl
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.PlaceholderConfig

internal object ConvoMarkdownImageTransformer : ImageTransformer {

    @Composable
    override fun transform(link: String): ImageData? {
        return Coil2ImageTransformerImpl.transform(link)?.copy(
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center,
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
        val widthSp = with(density) {
            if (containerSize.isUnspecified) 220f
            else containerSize.width.toSp().value.coerceAtMost(260f)
        }
        val heightSp = (widthSp * 9f / 16f).coerceIn(80f, 150f)
        return PlaceholderConfig(
            size = Size(widthSp, heightSp),
            verticalAlign = PlaceholderVerticalAlign.Top,
        )
    }
}
