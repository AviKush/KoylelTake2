package com.avik.koyleltake2

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale
import android.icu.util.HebrewCalendar
import android.os.Handler
import android.os.Looper

class MyApplication : Application() {
    companion object {
        private const val TAG = "MyApplication"
    }
    
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
        
        Log.d(TAG, "MyApplication onCreate called")
        
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
        
        // We'll let the MainActivity handle service start after a delay
        // but register a watchdog timer to check if service is running after some time
        // This ensures service eventually starts even if MainActivity fails to start it
        
        if (hasAllLocationPermissions()) {
            Log.d(TAG, "Location permissions already granted, scheduling service check")
            
            // Register delayed check to ensure service eventually starts
            // This is a backup in case MainActivity fail to start the service
            Handler(Looper.getMainLooper()).postDelayed({
                checkAndEnsureServiceRunning()
            }, 10000) // Check 10 seconds after app start
        } else {
            Log.d(TAG, "Location permissions not granted, skipping service check scheduling")
        }
    }
    
    /**
     * Check if service is running and start it if needed
     */
    private fun checkAndEnsureServiceRunning() {
        Log.d(TAG, "Watchdog timer: checking if location service is running")
        
        try {
            // Check service preference
            val serviceEnabled = getSharedPreferences("service_prefs", Context.MODE_PRIVATE)
                .getBoolean("pref_service_enabled", true)
            
            Log.d(TAG, "Service enabled in preferences: $serviceEnabled")
            
            // Check if service is already running
            val isRunning = isServiceRunning(LocationMonitoringService::class.java)
            Log.d(TAG, "Service running status: $isRunning")
            
            // Only start the service if:
            // 1. It's enabled in preferences
            // 2. It's not already running
            // 3. We have all permissions
            if (serviceEnabled && !isRunning && hasAllLocationPermissions()) {
                Log.d(TAG, "Watchdog: service not running but should be, attempting to start it")
                startLocationService()
            } else if (isRunning) {
                Log.d(TAG, "Watchdog: service is already running, no action needed")
            } else if (!serviceEnabled) {
                Log.d(TAG, "Watchdog: service is disabled in preferences, no action needed")
            } else {
                Log.d(TAG, "Watchdog: permissions missing, can't start service")
            }
            
            // IMPORTANT: Never automatically stop a running service
            // Let the user explicitly stop it through the UI
        } catch (e: Exception) {
            Log.e(TAG, "Error in service watchdog check", e)
        }
    }
    
    /**
     * Check if a service is running
     */
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        try {
            // Method 1: Check using ActivityManager
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
            
            for (service in runningServices) {
                if (serviceClass.name == service.service.className) {
                    Log.d(TAG, "Service found running via ActivityManager")
                    return true
                }
            }
            
            // Method 2: For LocationMonitoringService specifically, check its static flag
            if (serviceClass.name == "com.avik.koyleltake2.LocationMonitoringService") {
                val isRunning = LocationMonitoringService.isServiceRunning
                if (isRunning) {
                    Log.d(TAG, "Service found running via static flag")
                    return true
                }
            }
            
            Log.d(TAG, "Service not running")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if service is running", e)
            return false
        }
    }
    
    /**
     * Check if all necessary location permissions are granted
     */
    private fun hasAllLocationPermissions(): Boolean {
        // Check for basic location permissions
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        // For Android 10+, check for background location permissions
        val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Background location not needed for Android 9 and below
        }
        
        return hasFineLocation && hasCoarseLocation && hasBackgroundLocation
    }
    
    /**
     * Start the location monitoring service
     */
    private fun startLocationService() {
        try {
            Log.d(TAG, "Starting location monitoring service from Application")
            val serviceIntent = Intent(this, LocationMonitoringService::class.java)
            
            // Add manual start flag
            serviceIntent.putExtra("MANUAL_START", true)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location service", e)
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

    /**
     * Format a timestamp into a readable string with Hebrew calendar if available
     */
    fun formatTimestamp(timestamp: Long): String {
        fun intToHebrew(num: Int): String {
            // Hebrew numerals for 1-400
            val letters = listOf(
                "", "א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט", // 0-9
                "י", "יא", "יב", "יג", "יד", "טו", "טז", "יז", "יח", "יט", // 10-19
                "כ", "כא", "כב", "כג", "כד", "כה", "כו", "כז", "כח", "כט", // 20-29
                "ל", "לא", "לב", "לג", "לד", "לה", "לו", "לז", "לח", "לט", // 30-39
                "מ", "מא", "מב", "מג", "מד", "מה", "מו", "מז", "מח", "מט", // 40-49
                "נ", "נא", "נב", "נג", "נד", "נה", "נו", "נז", "נח", "נט", // 50-59
                "ס", "סא", "סב", "סג", "סד", "סה", "סו", "סז", "סח", "סט", // 60-69
                "ע", "עא", "עב", "עג", "עד", "עה", "עו", "עז", "עח", "עט", // 70-79
                "פ", "פא", "פב", "פג", "פד", "פה", "פו", "פז", "פח", "פט", // 80-89
                "צ", "צא", "צב", "צג", "צד", "צה", "צו", "צז", "צח", "צט", // 90-99
                "ק", "ר", "ש", "ת" // 100, 200, 300, 400
            )
            // For days (1-30)
            if (num in 1..30) {
                val special = mapOf(15 to "ט" + '\u05D5', 16 to "ט" + '\u05D6') // ט"ו, ט"ז
                return special[num] ?: run {
                    val s = StringBuilder()
                    var n = num
                    if (n >= 10) {
                        s.append(letters[10 + n - 10])
                    } else {
                        s.append(letters[n])
                    }
                    return s.toString()
                }
            }
            // For years (e.g., 5785 -> תשפ"ה)
            val hebrewDigits = listOf(
                Pair(400, 'ת'), Pair(300, 'ש'), Pair(200, 'ר'), Pair(100, 'ק'),
                Pair(90, 'צ'), Pair(80, 'פ'), Pair(70, 'ע'), Pair(60, 'ס'), Pair(50, 'נ'),
                Pair(40, 'מ'), Pair(30, 'ל'), Pair(20, 'כ'), Pair(10, 'י'),
                Pair(9, 'ט'), Pair(8, 'ח'), Pair(7, 'ז'), Pair(6, 'ו'), Pair(5, 'ה'), Pair(4, 'ד'), Pair(3, 'ג'), Pair(2, 'ב'), Pair(1, 'א')
            )
            var n = num % 1000 // Only last 3 digits for Hebrew years
            val sb = StringBuilder()
            for ((value, letter) in hebrewDigits) {
                while (n >= value) {
                    sb.append(letter)
                    n -= value
                }
            }
            // Insert gershayim (״) before last letter, or geresh (׳) if only one letter
            if (sb.length > 1) {
                sb.insert(sb.length - 1, '\u05F4') // ״
            } else if (sb.isNotEmpty()) {
                sb.append('\u05F3') // ׳
            }
            return sb.toString()
        }
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val cal = HebrewCalendar()
            cal.timeInMillis = timestamp
            val year = cal.get(HebrewCalendar.YEAR)
            val month = cal.get(HebrewCalendar.MONTH) + 1 // 0-based
            val day = cal.get(HebrewCalendar.DAY_OF_MONTH)
            val hour = cal.get(HebrewCalendar.HOUR_OF_DAY)
            val minute = cal.get(HebrewCalendar.MINUTE)
            val months = arrayOf(
                "תשרי", "חשוון", "כסלו", "טבת", "שבט", "אדר א'", "אדר", "ניסן", "אייר", "סיוון", "תמוז", "אב", "אלול"
            )
            val monthName = if (month in 1..months.size) months[month - 1] else month.toString()
            val dayHeb = intToHebrew(day)
            val yearHeb = intToHebrew(year)
            val time = String.format("%02d:%02d", hour, minute)
            "$dayHeb $monthName $yearHeb $time"
        } else {
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy – HH:mm", java.util.Locale.getDefault())
            sdf.format(timestamp)
        }
    }
} 