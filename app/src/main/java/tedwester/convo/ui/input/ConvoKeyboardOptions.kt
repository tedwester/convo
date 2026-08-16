package tedwester.convo.ui.input

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

/**
 * IME configuration for Convo text fields.
 *
 * Compose requires every [androidx.compose.foundation.text.BasicTextField] to supply
 * [KeyboardOptions]. The framework default is not neutral: [KeyboardCapitalization.Unspecified]
 * is resolved to [KeyboardCapitalization.None] when Compose talks to the IME, which disables
 * auto-capitalization even when the user has it enabled in their keyboard app.
 *
 * [Text] restores normal free-text behavior (equivalent to a plain [android.widget.EditText])
 * so the user's keyboard settings apply. [Search] and [Password] are the only other variants
 * the app needs — layout/action hints for those field types, not stylistic overrides.
 */
object ConvoKeyboardOptions {
    val Text: KeyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.Sentences,
        keyboardType = KeyboardType.Text,
    )

    val Search: KeyboardOptions = Text.copy(imeAction = ImeAction.Search)

    val Password: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)

    val Number: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
}
