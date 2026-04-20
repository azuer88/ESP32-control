package esp32.remotecontrol.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import esp32.remotecontrol.model.Control

@Composable
fun ControlCard(control: Control, onChanged: (Int) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        when (control.type) {
            Control.Type.SWITCH       -> SwitchControl(control, onChanged)
            Control.Type.SLIDER       -> SliderControl(control, onChanged)
            Control.Type.DIGITAL_INPUT -> DigitalInputControl(control)
        }
    }
}

@Composable
private fun DigitalInputControl(control: Control) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = control.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Switch(
            checked = control.value != 0,
            onCheckedChange = null,
            enabled = false,
        )
    }
}

@Composable
private fun SwitchControl(control: Control, onChanged: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = control.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = control.value != 0,
            onCheckedChange = { checked -> onChanged(if (checked) 1 else 0) },
        )
    }
}

@Composable
private fun SliderControl(control: Control, onChanged: (Int) -> Unit) {
    // Track the slider position locally to avoid stuttering while dragging
    var sliderValue by remember(control.id, control.value) {
        mutableFloatStateOf(control.value.toFloat())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = control.label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = sliderValue.toInt().toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onChanged(sliderValue.toInt()) },
            valueRange = control.min.toFloat()..control.max.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
