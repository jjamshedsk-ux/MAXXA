package com.maxxa.ai

import android.content.Context

internal object CharacterThemeStore {
    private const val PREFS_NAME = "maxxa_settings"
    private const val KEY_SELECTED_THEME = "character_ui_theme"
    private const val DEFAULT_THEME = "Itachi"

    private val supportedThemes = setOf(
        "Itachi", "Naruto", "Sasuke", "Kakashi", "Madara", "Hinata",
        "Sakura", "Nezuko", "Obito", "Minato", "Mitsuri", "Nobara"
    )

    fun load(context: Context): String {
        val saved = context.getSharedPreferences(
            PREFS_NAME, Context.MODE_PRIVATE
        ).getString(KEY_SELECTED_THEME, DEFAULT_THEME)

        return saved?.takeIf { it in supportedThemes } ?: DEFAULT_THEME
    }

    fun save(context: Context, themeName: String): Boolean {
        if (themeName !in supportedThemes) return false

        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_THEME, themeName)
            .commit()
    }
}
