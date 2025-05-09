package com.avik.koyleltake2

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

class ContextUtils(base: Context) : ContextWrapper(base) {
    companion object {
        fun updateLocale(context: Context, localeToSwitchTo: Locale): ContextWrapper {
            var newContext = context
            val resources: Resources = context.resources
            val configuration: Configuration = resources.configuration
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val localeList = LocaleList(localeToSwitchTo)
                LocaleList.setDefault(localeList)
                configuration.setLocales(localeList)
                configuration.setLayoutDirection(localeToSwitchTo)
            } else {
                configuration.locale = localeToSwitchTo
                configuration.setLayoutDirection(localeToSwitchTo)
                resources.updateConfiguration(configuration, resources.displayMetrics)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                newContext = context.createConfigurationContext(configuration)
            }
            
            return ContextUtils(newContext)
        }
    }
} 