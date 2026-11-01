package tedwester.convo.core.image

import android.net.Uri
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer

class DataUriFetcher(
    private val dataUri: String,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val (bytes, mimeType) = AssistantImages.decodeDataUri(dataUri)
            ?: throw IllegalArgumentException("Invalid data URI")
        return SourceResult(
            source = ImageSource(Buffer().write(bytes), options.context),
            mimeType = mimeType,
            dataSource = DataSource.MEMORY,
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: coil.ImageLoader): Fetcher? {
            if (!data.scheme.equals("data", ignoreCase = true)) return null
            return DataUriFetcher(data.toString(), options)
        }
    }

    class StringFactory : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: coil.ImageLoader): Fetcher? {
            if (!data.startsWith("data:", ignoreCase = true)) return null
            return DataUriFetcher(data, options)
        }
    }
}
