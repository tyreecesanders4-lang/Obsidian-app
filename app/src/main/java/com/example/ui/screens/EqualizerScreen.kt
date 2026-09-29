package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.PlayerEngineState
import com.example.data.BuiltInEqPresets
import com.example.data.CustomEqPresetEntity
import com.example.data.EqPresetProfile
import com.example.ui.components.NeonBadge
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.glassPanel

@Composable
fun EqualizerScreen(
    playerState: PlayerEngineState,
    customPresets: List<CustomEqPresetEntity>,
    onEqEnabledChange: (Boolean) -> Unit,
    onBandGainChange: (Int, Float) -> Unit,
    onApplyPreset: (EqPresetProfile) -> Unit,
    onPreampChange: (Float) -> Unit,
    onToggleBassBoost: () -> Unit,
    onBassBoostPercentChange: (Int) -> Unit,
    onToggleSpatialReverb: () -> Unit,
    onSaveCustomPreset: (String) -> Unit,
    onDeleteCustomPreset: (CustomEqPresetEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var presetDropdownExpanded by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("equalizer_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header + Master DSP Switch + Preset Dropdown
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassPanel(cornerRadius = 22.dp, highlightCyan = playerState.eqEnabled)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(26.dp)
                    )
                    Column {
                        Text(
                            text = "10-Band Graphic DSP Suite",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Biquad Filter Cascade • 32Hz – 16kHz",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                Switch(
                    checked = playerState.eqEnabled,
                    onCheckedChange = onEqEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianVoid,
                        checkedTrackColor = ElectricCyan,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = ObsidianVoid
                    ),
                    modifier = Modifier.testTag("eq_master_switch")
                )
            }

            // Preset Dropdown & Reset/Save Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { presetDropdownExpanded = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("eq_preset_dropdown_button")
                    ) {
                        Text(
                            text = "Preset: ${playerState.activePresetName}",
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select EQ Preset",
                            tint = ElectricCyan
                        )
                    }

                    DropdownMenu(
                        expanded = presetDropdownExpanded,
                        onDismissRequest = { presetDropdownExpanded = false },
                        modifier = Modifier.background(ObsidianSurfaceElevated)
                    ) {
                        BuiltInEqPresets.ALL_PRESETS.forEach { preset ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = preset.name,
                                        color = if (playerState.activePresetName == preset.name) ElectricCyan else TextPrimary,
                                        fontWeight = if (playerState.activePresetName == preset.name) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    onApplyPreset(preset)
                                    presetDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { showSavePresetDialog = true },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeonPurple.copy(alpha = 0.22f))
                        .border(1.dp, NeonPurple.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .testTag("save_eq_preset_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkAdd,
                        contentDescription = "Save Custom EQ Preset",
                        tint = NeonPurple
                    )
                }

                IconButton(
                    onClick = { onApplyPreset(BuiltInEqPresets.ALL_PRESETS.first()) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianSurface)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .testTag("reset_eq_flat_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset EQ to Flat",
                        tint = TextSecondary
                    )
                }
            }

            // Instant Preset Pill Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BuiltInEqPresets.ALL_PRESETS.forEach { preset ->
                    val isSelected = playerState.activePresetName.equals(preset.name, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isSelected) ElectricCyan.copy(alpha = 0.22f) else ObsidianSurface
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) ElectricCyan else Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { onApplyPreset(preset) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag("eq_preset_chip_${preset.name.lowercase().replace(' ', '_')}")
                    ) {
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) ElectricCyan else TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                customPresets.forEach { custom ->
                    val isSelected = playerState.activePresetName.equals(custom.name, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) NeonPurple.copy(alpha = 0.3f) else ObsidianSurface)
                            .border(1.dp, NeonPurple, RoundedCornerShape(50))
                            .clickable {
                                onApplyPreset(
                                    EqPresetProfile(
                                        name = custom.name,
                                        bandsDb = custom.gainsList(),
                                        preampDb = custom.preampDb,
                                        bassBoostPercent = custom.bassBoostPercent,
                                        spatialReverb = custom.spatialReverb
                                    )
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = custom.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete ${custom.name}",
                            tint = NeonMagenta,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onDeleteCustomPreset(custom) }
                        )
                    }
                }
            }
        }

        // 2. Live 10-Band Frequency Response Curve Canvas
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassPanel(cornerRadius = 20.dp)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SPLINE FREQUENCY RESPONSE",
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Range: -12 dB to +12 dB",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianVoid.copy(alpha = 0.78f))
                    .border(1.dp, ElectricCyan.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                    .padding(10.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val midY = h / 2f
                    val bands = playerState.eqBandsDb

                    // 0 dB center line
                    drawLine(
                        color = Color.White.copy(alpha = 0.18f),
                        start = Offset(0f, midY),
                        end = Offset(w, midY),
                        strokeWidth = 1.5f
                    )

                    val points = bands.mapIndexed { idx, db ->
                        val px = (idx.toFloat() / 9f) * w
                        val py = (midY - (db / 12f) * (midY - 8f)).coerceIn(6f, h - 6f)
                        Offset(px, py)
                    }

                    if (points.size >= 2) {
                        val curvePath = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            for (i in 0 until points.lastIndex) {
                                val p0 = points[i]
                                val p1 = points[i + 1]
                                val ctrlX = (p0.x + p1.x) / 2f
                                cubicTo(ctrlX, p0.y, ctrlX, p1.y, p1.x, p1.y)
                            }
                        }

                        val fillPath = Path().apply {
                            addPath(curvePath)
                            lineTo(w, h)
                            lineTo(0f, h)
                            close()
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                listOf(
                                    ElectricCyan.copy(alpha = 0.32f),
                                    NeonPurple.copy(alpha = 0.06f)
                                )
                            )
                        )

                        drawPath(
                            path = curvePath,
                            brush = Brush.horizontalGradient(
                                listOf(NeonPurple, ElectricCyan, NeonMagenta)
                            ),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )

                        points.forEach { pt ->
                            drawCircle(
                                color = ElectricCyan,
                                radius = 4.5.dp.toPx(),
                                center = pt
                            )
                        }
                    }
                }
            }
        }

        // 3. Interactive 10-Band Graphic Sliders (32Hz to 16kHz)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassPanel(cornerRadius = 22.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "10-Band Parametric Sliders",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            BuiltInEqPresets.FREQUENCY_LABELS.forEachIndexed { idx, freqLabel ->
                val gainDb = playerState.eqBandsDb.getOrElse(idx) { 0f }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = freqLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(48.dp)
                    )

                    Slider(
                        value = gainDb,
                        onValueChange = { newDb -> onBandGainChange(idx, newDb) },
                        valueRange = -12f..12f,
                        enabled = playerState.eqEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = if (gainDb >= 0f) ElectricCyan else NeonPurple,
                            activeTrackColor = if (gainDb >= 0f) ElectricCyan else NeonPurple,
                            inactiveTrackColor = Color.White.copy(alpha = 0.10f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .testTag("eq_band_slider_$freqLabel")
                    )

                    Text(
                        text = "%+.1f dB".format(gainDb),
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            gainDb > 0.2f -> NeonEmerald
                            gainDb < -0.2f -> NeonMagenta
                            else -> TextSecondary
                        },
                        modifier = Modifier.width(62.dp)
                    )
                }
            }
        }

        // 4. Dynamic Audio Effects: Pre-amp Gain, Bass Boost & Spatial Reverb
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassPanel(cornerRadius = 20.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Dynamic Audio Effects & Pre-Amp",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            // Pre-amp Gain Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Pre-Amp",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonPurple,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(64.dp)
                )
                Slider(
                    value = playerState.preampDb,
                    onValueChange = onPreampChange,
                    valueRange = -6f..12f,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonPurple,
                        activeTrackColor = NeonPurple
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("preamp_gain_slider")
                )
                Text(
                    text = "%+.1f dB".format(playerState.preampDb),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    modifier = Modifier.width(60.dp)
                )
            }

            // Sub-Bass Boost Slider & Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = playerState.bassBoostEnabled,
                    onClick = onToggleBassBoost,
                    label = { Text("Bass Boost") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan.copy(alpha = 0.22f),
                        selectedLabelColor = ElectricCyan
                    ),
                    modifier = Modifier.testTag("bass_boost_toggle")
                )

                Slider(
                    value = playerState.bassBoostPercent.toFloat(),
                    onValueChange = { onBassBoostPercentChange(it.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricCyan,
                        activeTrackColor = ElectricCyan
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("bass_boost_slider")
                )

                Text(
                    text = "${playerState.bassBoostPercent}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan,
                    modifier = Modifier.width(42.dp)
                )
            }

            // Spatial Reverb Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SurroundSound,
                        contentDescription = null,
                        tint = NeonMagenta
                    )
                    Column {
                        Text(
                            text = "Spatial Hall Reverb",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Expands stereo soundstage with acoustic hall reflections",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                FilterChip(
                    selected = playerState.spatialReverbEnabled,
                    onClick = onToggleSpatialReverb,
                    label = { Text(if (playerState.spatialReverbEnabled) "ACTIVE" else "OFF") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonMagenta.copy(alpha = 0.25f),
                        selectedLabelColor = NeonMagenta
                    ),
                    modifier = Modifier.testTag("spatial_reverb_toggle")
                )
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }

    if (showSavePresetDialog) {
        var presetName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            containerColor = ObsidianSurfaceElevated,
            title = {
                Text("Save Custom 10-Band EQ Preset", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("Preset Name (e.g. Late Night Sub)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveCustomPreset(presetName.ifBlank { "My Studio EQ" })
                        showSavePresetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                ) {
                    Text("Save Preset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
