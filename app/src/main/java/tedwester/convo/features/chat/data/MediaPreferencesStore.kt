package tedwester.convo.features.chat.data

import android.content.Context
import android.content.SharedPreferences

data class MediaPreferences(

    val saveMediaToGallery: Boolean = true,
)

class MediaPreferencesStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): MediaPreferences = MediaPreferences(
        saveMediaToGallery = prefs.getBoolean(KEY_SAVE_MEDIA_TO_GALLERY, true),
    )

    fun save(value: MediaPreferences) {
        prefs.edit()
            .putBoolean(KEY_SAVE_MEDIA_TO_GALLERY, value.saveMediaToGallery)
            .apply()
    }

    private companion object {
        const val PREFS = "convo_media_prefs"
        const val KEY_SAVE_MEDIA_TO_GALLERY = "save_media_to_gallery"
    }
}
