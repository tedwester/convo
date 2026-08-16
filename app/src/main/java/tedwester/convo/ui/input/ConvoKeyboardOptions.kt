package tedwester.convo.ui.input

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

object ConvoKeyboardOptions {
    val Text: KeyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.Sentences,
        keyboardType = KeyboardType.Text,
    )

    val Search: KeyboardOptions = Text.copy(imeAction = ImeAction.Search)

    val Password: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)

    val Number: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
}
