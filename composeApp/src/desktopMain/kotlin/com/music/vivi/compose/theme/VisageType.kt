package com.music.vivi.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle

/**
 * Named text roles. Use these instead of picking a Material slot at the call site, so the same kind of text is the
 * same size everywhere. Sizes come from the Material 3 scale; fonts and weights from [visageTypography].
 * The full table (with sizes) is in docs/design-system.md.
 */
object VisageType {
    /** Title of a top-level page ("Settings", "Home"). */
    val pageTitle: TextStyle @Composable get() = MaterialTheme.typography.headlineMedium
    /** Title of a sub-page or detail header, next to the back button. */
    val subPageTitle: TextStyle @Composable get() = MaterialTheme.typography.headlineSmall
    /** A shelf or group heading on a page. */
    val sectionTitle: TextStyle @Composable get() = MaterialTheme.typography.titleLarge
    /** Small coloured group label above a settings card. */
    val groupLabel: TextStyle @Composable get() = MaterialTheme.typography.titleSmall
    /** Title of a card, tile or dialog section. */
    val cardTitle: TextStyle @Composable get() = MaterialTheme.typography.titleMedium
    /** Primary line of a list row or setting. */
    val rowTitle: TextStyle @Composable get() = MaterialTheme.typography.bodyLarge
    /** Secondary line under a row title (artist, album, description). Use onSurfaceVariant. */
    val rowSubtitle: TextStyle @Composable get() = MaterialTheme.typography.bodyMedium
    /** Metadata and helper text (hints, counts, footnotes). */
    val caption: TextStyle @Composable get() = MaterialTheme.typography.bodySmall
    /** Buttons, chips, tabs and menu items. */
    val control: TextStyle @Composable get() = MaterialTheme.typography.labelLarge
    /** Numbers that change while you watch: elapsed/total time, volume %, counters. */
    val readout: TextStyle @Composable get() = MaterialTheme.typography.labelMedium
    /** "New", "E", "HD" and other tiny badges. */
    val badge: TextStyle @Composable get() = MaterialTheme.typography.labelSmall
}
