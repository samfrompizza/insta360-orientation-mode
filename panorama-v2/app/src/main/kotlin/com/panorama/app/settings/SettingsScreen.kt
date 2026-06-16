package com.panorama.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** VR viewer tuning, shown as a translucent overlay on the player (hence the explicit light
 *  [contentColor] for readability over the dimmed camera). All values are hoisted: the current
 *  values come in, edits go out through the callbacks, and the owner applies them to the live VR
 *  view. These knobs only affect the split-screen VR render. */
@Composable
fun SettingsScreen(
    sensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    eyeScale: Float,
    onEyeScaleChange: (Float) -> Unit,
    eyeGap: Float,
    onEyeGapChange: (Float) -> Unit,
    onBack: () -> Unit,
    contentColor: Color = Color.White,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("VR settings", color = contentColor)

        Text("Gyro sensitivity: ${"%.2f".format(sensitivity)}×", color = contentColor)
        Slider(value = sensitivity, onValueChange = onSensitivityChange, valueRange = 0.5f..2f)

        Text("Screen size: ${"%.0f".format(eyeScale * 100)}%", color = contentColor)
        Slider(value = eyeScale, onValueChange = onEyeScaleChange, valueRange = 0.5f..1f)

        Text("Screen distance: ${"%.0f".format(eyeGap * 100)}%", color = contentColor)
        Slider(value = eyeGap, onValueChange = onEyeGapChange, valueRange = 0f..0.3f)

        TextButton(onClick = onBack) { Text("Back", color = contentColor) }
    }
}
