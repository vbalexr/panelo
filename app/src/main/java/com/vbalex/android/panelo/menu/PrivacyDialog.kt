// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.menu

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vbalex.android.panelo.BuildConfig

/** Bundled PRIVACY.md, rendered as readable text in a full-screen dialog. */
object Privacy {
    sealed interface Block {
        data class Heading(val text: String, val level: Int) : Block
        data class Bullet(val text: String) : Block
        data class Table(val text: String) : Block
        data class Paragraph(val text: String) : Block
    }

    private const val ASSET = "PRIVACY.md"
    private val tableSeparator = Regex("""^\|[\s:|-]+\|$""")
    private val link = Regex("""\[([^\]]+)]\([^)]*\)""")
    private val bold = Regex("""\*\*(.+?)\*\*""")

    /** Reads the policy bundled into the APK. */
    fun load(context: Context): Result<String> =
        runCatching { context.assets.open(ASSET).bufferedReader().use { it.readText() } }

    /** Turns markdown into simple blocks: headings, bullets, table rows and paragraphs. */
    fun parse(markdown: String): List<Block> = markdown.lineSequence().mapNotNull(::block).toList()

    /** Shows the policy full screen, with the online URL at the bottom. */
    @Composable
    fun Show(onClose: () -> Unit) {
        val context = LocalContext.current
        val blocks = remember(context) { load(context).map(::parse) }
        Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Privacy policy", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = onClose) { Text("Close") }
                    }
                    HorizontalDivider()
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val list = blocks.getOrNull()
                        if (list == null) Text("The privacy policy is not available in this build.", color = MaterialTheme.colorScheme.error)
                        list?.forEach { RenderBlock(it) }
                    }
                    HorizontalDivider()
                    SelectionContainer {
                        Text(
                            "Online: ${BuildConfig.PRIVACY_URL}",
                            Modifier.fillMaxWidth().padding(16.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }

    private fun block(raw: String): Block? {
        val line = raw.trim()
        if (line.isEmpty()) return null
        if (tableSeparator.matches(line)) return null
        if (line.startsWith("|")) return Block.Table(line.trim('|').split('|').joinToString(" · ") { inline(it.trim()) })
        if (line.startsWith("#")) {
            val level = line.takeWhile { it == '#' }.length
            return Block.Heading(inline(line.drop(level).trim()), level)
        }
        if (line.startsWith("- ") || line.startsWith("* ")) return Block.Bullet(inline(line.drop(2)))
        return Block.Paragraph(inline(line))
    }

    /** Drops link targets, bold markers, backticks and whole-line italics. */
    private fun inline(text: String): String {
        val italic = text.length > 2 && text.startsWith("_") && text.endsWith("_")
        val plain = if (italic) text.substring(1, text.length - 1) else text
        return plain.replace(link, "$1").replace(bold, "$1").replace("`", "")
    }
}

@Composable
private fun RenderBlock(block: Privacy.Block) {
    when (block) {
        is Privacy.Block.Heading -> Text(
            block.text,
            style = if (block.level <= 1) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
        is Privacy.Block.Bullet -> Text("•  ${block.text}")
        is Privacy.Block.Table -> Text(block.text, style = MaterialTheme.typography.bodyMedium)
        is Privacy.Block.Paragraph -> Text(block.text)
    }
}
