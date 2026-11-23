package com.peyi.sonypodsbattery

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * What changed in each version. The notes come from CHANGELOG.md, which ships inside the app, so
 * they show after any kind of update (in-app, Obtainium, a downloaded APK) and work offline.
 */
internal object WhatsNew {
    private const val PREFS = "whats_new"

    /** The changelog section for [version], or null if there isn't one. */
    fun notes(context: Context, version: String): String? = runCatching {
        val text = context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() }
        section(text, version)
    }.getOrNull()

    fun section(changelog: String, version: String): String? {
        val lines = changelog.lines()
        val start = lines.indexOfFirst { it.trim() == "## $version" }
        if (start < 0) return null
        val end = (start + 1 until lines.size).firstOrNull { lines[it].startsWith("## ") } ?: lines.size
        return lines.subList(start + 1, end).joinToString("\n").trim().takeIf { it.isNotEmpty() }
    }

    /** True once per version, only after an update (a fresh install has nothing to compare). */
    fun shouldShow(context: Context): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = p.getString("seen", null)
        if (seen == BuildConfig.VERSION_NAME) return false
        p.edit().putString("seen", BuildConfig.VERSION_NAME).apply()
        return seen != null && notes(context, BuildConfig.VERSION_NAME) != null
    }
}

/** The notes as a sheet: section heads, bullet points and plain lines, no markdown symbols. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WhatsNewSheet(version: String, notes: String, action: String? = null, onAction: () -> Unit = {}, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()
        ) {
            Text("What's New in $version", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            notes.lines().map { it.trimEnd() }.filter { it.isNotBlank() }.forEach { raw ->
                val line = raw.replace("**", "").replace("`", "")
                when {
                    raw.trim().startsWith("**") && raw.trim().endsWith("**") -> {
                        Spacer(Modifier.height(10.dp))
                        Text(line.trim().uppercase(), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                    }
                    raw.trimStart().startsWith("- ") || raw.trimStart().startsWith("* ") -> Row(Modifier.padding(vertical = 3.dp)) {
                        Text("•", style = MaterialTheme.typography.bodyMedium, color = colors.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(line.trimStart().drop(2), style = MaterialTheme.typography.bodyMedium)
                    }
                    raw.startsWith("#") -> Text(line.trimStart('#', ' '), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    else -> Text(line, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { if (action != null) onAction(); scope.launch { sheet.hide(); onDismiss() } },
                modifier = Modifier.fillMaxWidth()
            ) { Text(action ?: "Done") }
        }
    }
}
