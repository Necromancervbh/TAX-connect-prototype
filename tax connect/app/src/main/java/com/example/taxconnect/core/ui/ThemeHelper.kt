package com.example.taxconnect.core.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeHelper {
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val THEME_SYSTEM = "system"


    fun applyTheme(context: Context) {
        val theme = getSelectedTheme(context)
        setTheme(theme)
    }

    fun getThemeResource(theme: String): Int {
        return when (theme) {

            else -> com.example.taxconnect.R.style.Theme_MyApplication
        }
    }

    fun setTheme(theme: String) {
        val targetMode = when (theme) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            THEME_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_YES
        }
        
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode)
        }
    }

    fun getSelectedTheme(context: Context): String {
        val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        return prefs.getString("selected_theme", THEME_SYSTEM) ?: THEME_SYSTEM
    }

    fun saveTheme(context: Context, theme: String) {
        val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_theme", theme).apply()
        setTheme(theme)
    }

    fun isBiometricEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("biometric_enabled", false)
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("biometric_enabled", enabled).apply()
    }

    fun setDarkMode(enabled: Boolean) {
        if (enabled) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
    fun getThemeColor(context: Context, attrId: Int): Int {
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(attrId, typedValue, true)
        return typedValue.data
    }
}
