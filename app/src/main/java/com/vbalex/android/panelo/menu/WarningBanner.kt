// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vbalex.android.panelo.config.ConfigWarnings

/** Warning-colored banners (top of the panel) for rejected config / fallbacks; see [ConfigWarnings]. */
@Composable
internal fun WarningBanner() {
    val warnings by ConfigWarnings.active.collectAsState()
    if (warnings.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        warnings.forEach { warning ->
            Column(
                Modifier.fillMaxWidth().background(Color(0xFFFFE08A), RoundedCornerShape(8.dp)).padding(12.dp),
            ) {
                Text("WARNING: ${warning.kind.title}", color = Color(0xFF4A3000), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(warning.detail, color = Color(0xFF4A3000), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                Text("Time: ${Clock.dateTime(warning.atMs)}", color = Color(0xFF4A3000), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { ConfigWarnings.dismiss(warning) }, modifier = Modifier.align(Alignment.End)) { Text("Dismiss", color = Color(0xFF4A3000)) }
            }
        }
    }
}
