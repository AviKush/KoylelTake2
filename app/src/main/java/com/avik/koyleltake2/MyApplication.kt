package com.avik.koyleltake2

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

class MyApplication : Application() {
    override fun attachBaseContext(base: Context) {
        // Get saved language
        val language = base.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString(MainActivity.PREF_LANGUAGE, null)
        
        if (language != null) {
            val locale = Locale(language)
            Locale.setDefault(locale)
            super.attachBaseContext(ContextUtils.updateLocale(base, locale))
        } else {
            super.attachBaseContext(base)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Get saved language
        val language = getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString(MainActivity.PREF_LANGUAGE, null)
        
        if (language != null) {
            val locale = Locale(language)
            Locale.setDefault(locale)
            val newConfiguration = updateConfigurationLocale(newConfig, locale)
            resources.updateConfiguration(newConfiguration, resources.displayMetrics)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Get saved language
        val language = getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString(MainActivity.PREF_LANGUAGE, null)
        
        if (language != null) {
            val locale = Locale(language)
            Locale.setDefault(locale)
            val config = resources.configuration
            updateConfigurationLocale(config, locale)
            resources.updateConfiguration(config, resources.displayMetrics)
        }
    }
    
    private fun updateConfigurationLocale(config: Configuration, locale: Locale): Configuration {
        val newConfig = Configuration(config)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            newConfig.setLocales(localeList)
        } else {
            newConfig.locale = locale
        }
        newConfig.setLayoutDirection(locale)
        return newConfig
    }
} 