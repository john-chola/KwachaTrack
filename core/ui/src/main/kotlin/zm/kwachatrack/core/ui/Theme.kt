package zm.kwachatrack.core.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Copper and green: a nod to the kwacha and Zambia's flag, with AA contrast on both themes.
private val Light =
    lightColorScheme(
        primary = Color(0xFF1B6B3A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFA6F4B5),
        onPrimaryContainer = Color(0xFF00210C),
        secondary = Color(0xFF8A4F1D),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFDCC4),
        onSecondaryContainer = Color(0xFF2F1400),
        error = Color(0xFFBA1A1A),
        background = Color(0xFFF8FAF4),
        surface = Color(0xFFF8FAF4),
    )

private val Dark =
    darkColorScheme(
        primary = Color(0xFF8BD79A),
        onPrimary = Color(0xFF00391A),
        primaryContainer = Color(0xFF005228),
        onPrimaryContainer = Color(0xFFA6F4B5),
        secondary = Color(0xFFFFB780),
        onSecondary = Color(0xFF4D2600),
        secondaryContainer = Color(0xFF6D3906),
        onSecondaryContainer = Color(0xFFFFDCC4),
        background = Color(0xFF111411),
        surface = Color(0xFF111411),
    )

/** Money colours used for income/spending figures; chosen to stay readable in both themes. */
object MoneyColours {
    val income: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFF8BD79A) else Color(0xFF1B6B3A)
    val spending: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFFB4AB) else Color(0xFFB3261E)
}

@Composable
fun KwachaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colours =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> Dark
            else -> Light
        }
    MaterialTheme(colorScheme = colours, content = content)
}
