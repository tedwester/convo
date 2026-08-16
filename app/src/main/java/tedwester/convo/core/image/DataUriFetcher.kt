package tedwester.convo.core.image

import android.net.Uri
import android.util.Base64
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
        val comma = dataUri.indexOf(',')
        require(comma > 5) { "Invalid data URI" }
        val meta = dataUri.substring(5, comma)
        val payload = dataUri.substring(comma + 1).replace(Regex("\\s+"), "")
        val bytes = if (meta.contains(";base64", ignoreCase = true)) {
            Base64.decode(payload, Base64.DEFAULT)
        } else {
            java.net.URLDecoder.decode(payload, Charsets.UTF_8.name()).toByteArray()
        }
        return SourceResult(
            source = ImageSource(Buffer().write(bytes), options.context),
            mimeType = meta.substringBefore(';').ifBlank { null },
            dataSource = DataSource.MEMORY,
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: coil.ImageLoader): Fetcher? {
            if (!data.scheme.equals("data", ignoreCase = true)) return null
            return DataUriFetcher(data.toString(), options)
        }
    }
}
