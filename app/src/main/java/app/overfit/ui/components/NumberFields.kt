package app.overfit.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val NumberTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center)

/** Großes Zahlenfeld mit Einheit als Platzhalter/Suffix (z. B. "kg", "Wdh."). */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Number,
    placeholder: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = NumberTextStyle,
        placeholder = {
            Text(
                placeholder ?: unit,
                style = NumberTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        suffix = if (value.isNotEmpty()) ({ Text(unit, style = MaterialTheme.typography.bodySmall) }) else null,
        trailingIcon = trailing,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        modifier = modifier.heightIn(min = 56.dp),
    )
}

/**
 * Gewichtsfeld in kg. Viele Zahlentastaturen haben kein Minus, daher ein „±“-Knopf
 * (negatives Gewicht = Unterstützung, z. B. Band).
 */
@Composable
fun WeightField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String? = null) {
    NumberField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter { it.isDigit() || it == ',' || it == '.' || it == '-' }) },
        unit = "kg",
        keyboardType = KeyboardType.Decimal,
        placeholder = placeholder,
        modifier = modifier,
        trailing = {
            TextButton(
                onClick = { onValueChange(if (value.startsWith("-")) value.removePrefix("-") else "-$value") },
                modifier = Modifier.padding(end = 2.dp),
            ) { Text("±") }
        },
    )
}

@Composable
fun RepsField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String? = null) {
    NumberField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter(Char::isDigit).take(4)) },
        unit = "Wdh.",
        placeholder = placeholder,
        modifier = modifier,
    )
}

/** Dauer als Sekunden oder m:ss. */
@Composable
fun DurationField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String? = null) {
    NumberField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter { it.isDigit() || it == ':' }.take(6)) },
        unit = "s",
        keyboardType = KeyboardType.Phone,
        placeholder = placeholder,
        modifier = modifier,
    )
}
