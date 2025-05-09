package com.avik.koyleltake2

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale
import com.avik.koyleltake2.DateTimePickerDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import android.view.Menu
import android.view.MenuItem
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONArray
import org.json.JSONObject
import androidx.appcompat.widget.Toolbar
import android.os.Build
import android.icu.util.HebrewCalendar
import android.icu.util.ULocale
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.ActionBarDrawerToggle
import android.os.Handler
import android.os.Looper
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Color
import android.widget.TextView
import android.widget.GridLayout
import android.widget.ScrollView
import android.util.TypedValue
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.app.Activity

class MainActivity : AppCompatActivity() {

    private lateinit var logEntryAdapter: LogEntryAdapter
    private val logEntries = mutableListOf<LogEntry>()
    private val sharedPreferences by lazy { getSharedPreferences("log_entries", MODE_PRIVATE) }
    private var hebrewTimestamps = mutableListOf<String>()

    // Add ActivityResultLauncher for import
    private val importJsonLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { importJsonFromUri(it) }
    }

    // Store selected colors
    private var selectedMainColor: Int = 0
    private var selectedBgColor1: Int = 0
    private var selectedBgColor2: Int = 0
    private var selectedTextColor: Int = 0

    companion object {
        const val PREF_LANGUAGE = "pref_language"
    }

    private fun setLocale(context: Context, language: String, forceRestart: Boolean = false) {
        // Check if we're actually changing the language
        val currentLanguage = loadLanguage()
        val isLanguageChange = currentLanguage != language
        
        // Save language preference
        saveLanguage(language)
        
        // Set locale
        val locale = Locale(language)
        Locale.setDefault(locale)
        
        // Update configuration
        val resources = context.resources
        val configuration = Configuration(resources.configuration)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            configuration.setLocales(localeList)
        } else {
            configuration.locale = locale
        }
        configuration.setLayoutDirection(locale)
        
        resources.updateConfiguration(configuration, resources.displayMetrics)
        context.applicationContext.resources.updateConfiguration(configuration, resources.displayMetrics)
        
        // Only restart if we're changing languages or explicitly forcing restart
        if (isLanguageChange && forceRestart) {
            // Force restart
            val packageManager = context.packageManager
            val intent = packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            if (context is Activity) {
                context.finish()
            }
        }
    }

    private fun saveLanguage(language: String) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        prefs.edit().putString(PREF_LANGUAGE, language).apply()
    }

    private fun loadLanguage(): String? {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        return prefs.getString(PREF_LANGUAGE, null)
    }

    override fun attachBaseContext(newBase: Context) {
        val lang = newBase.getSharedPreferences("settings", MODE_PRIVATE)
            .getString(PREF_LANGUAGE, null)
        if (lang != null) {
            val locale = Locale(lang)
            Locale.setDefault(locale)
            super.attachBaseContext(ContextUtils.updateLocale(newBase, locale))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved language before super.onCreate
        loadLanguage()?.let { setLocale(this, it, false) }
        super.onCreate(savedInstanceState)
        
        // Add debug logs
        android.util.Log.d("LocaleDebug", "Current locale: ${resources.configuration.locales.toLanguageTags()}")
        android.util.Log.d("LocaleDebug", "App name from resources: ${getString(R.string.app_name)}")
        android.util.Log.d("LocaleDebug", "Menu language from resources: ${getString(R.string.menu_language)}")
        
        setContentView(R.layout.activity_main)

        // Load user-selected colors
        val prefs = getSharedPreferences("color_prefs", MODE_PRIVATE)
        selectedMainColor = prefs.getInt("main_color", Color.parseColor("#1976D2"))
        selectedBgColor1 = prefs.getInt("bg_color1", Color.parseColor("#FFFFFF"))
        selectedBgColor2 = prefs.getInt("bg_color2", Color.parseColor("#EEEEEE"))
        selectedTextColor = prefs.getInt("text_color", Color.parseColor("#222222"))
        
        // Set status bar color
        window.statusBarColor = selectedMainColor

        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navigationView: NavigationView = findViewById(R.id.navigation_view)
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Set toolbar color
        toolbar.setBackgroundColor(selectedMainColor)
        toolbar.setTitleTextColor(Color.WHITE)

        // Set FAB color and click listener
        val fabAdd: com.google.android.material.floatingactionbutton.FloatingActionButton = findViewById(R.id.fabAdd)
        fabAdd.backgroundTintList = android.content.res.ColorStateList.valueOf(selectedMainColor)
        fabAdd.setOnClickListener {
            logCurrentTime()
        }

        // Set up ActionBarDrawerToggle to sync the hamburger icon with the drawer
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.app_name, R.string.app_name
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Initialize RecyclerView
        val recyclerView: RecyclerView = findViewById(R.id.recyclerViewTimestamps)
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = false // Start from the top
        recyclerView.layoutManager = layoutManager
        logEntryAdapter = LogEntryAdapter(logEntries, hebrewTimestamps, { logEntry ->
            deleteLogEntry(logEntry)
        }, selectedTextColor)
        recyclerView.adapter = logEntryAdapter

        // Add custom ItemDecoration for colored stripes
        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            private val colors = listOf(selectedBgColor1, selectedBgColor2)
            private val paint = Paint()
            override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                val childCount = parent.childCount
                for (i in 0 until childCount) {
                    val child = parent.getChildAt(i)
                    val position = parent.getChildAdapterPosition(child)
                    if (position == RecyclerView.NO_POSITION) continue
                    paint.color = colors[position % colors.size]
                    c.drawRect(
                        0f,
                        child.top.toFloat(),
                        parent.width.toFloat(),
                        child.bottom.toFloat(),
                        paint
                    )
                }
            }
        })

        // Load existing log entries
        loadLogEntries()

        // Handle navigation drawer item clicks
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_add_time_manually -> {
                    addTimeManually()
                }
                R.id.nav_export_json -> {
                    exportToJson()
                }
                R.id.nav_import_json -> {
                    importFromJson()
                }
                R.id.nav_clear_all -> {
                    showClearAllConfirmation(drawerLayout)
                }
                R.id.nav_choose_colors -> {
                    showChooseColorsDialog()
                }
                R.id.menu_sum_car_entries -> {
                    showCarEntriesDialog()
                }
                R.id.nav_language_english -> {
                    setLocale(this, "en", true)
                    return@setNavigationItemSelectedListener true
                }
                R.id.nav_language_hebrew -> {
                    setLocale(this, "he", true)
                    return@setNavigationItemSelectedListener true
                }
            }
            drawerLayout.closeDrawer(Gravity.START)
            true
        }

        val fabCar: com.google.android.material.floatingactionbutton.FloatingActionButton = findViewById(R.id.fabCar)
        fabCar.backgroundTintList = android.content.res.ColorStateList.valueOf(selectedMainColor)
        fabCar.setOnClickListener {
            logCarEntry()
        }
    }

    private fun logCurrentTime() {
        val recyclerView: RecyclerView = findViewById(R.id.recyclerViewTimestamps)
        // First scroll to the end (footer)
        recyclerView.smoothScrollToPosition(logEntries.size)
        recyclerView.post {
            // Add the new entry after the scroll
            val currentTime = System.currentTimeMillis()
            val logEntry = LogEntry(currentTime, formatTimestamp(currentTime))
            logEntries.add(logEntry) // Add to end
            hebrewTimestamps.add(formatTimestamp(currentTime))
            logEntryAdapter.notifyItemInserted(logEntries.size - 1)
            saveLogEntries()
            // Scroll again to the new footer after a short delay for smoothness
            Handler(Looper.getMainLooper()).postDelayed({
                recyclerView.smoothScrollToPosition(logEntries.size)
            }, 200)
        }
    }

    private fun addTimeManually() {
        val recyclerView: RecyclerView = findViewById(R.id.recyclerViewTimestamps)
        val dialog = DateTimePickerDialog { selectedTime: Long ->
            // First scroll to the end (footer)
            recyclerView.smoothScrollToPosition(logEntries.size)
            recyclerView.post {
                // Add the new entry after the scroll
                val logEntry = LogEntry(selectedTime, formatTimestamp(selectedTime))
                logEntries.add(logEntry) // Add to end
                hebrewTimestamps.add(formatTimestamp(selectedTime))
                logEntryAdapter.notifyItemInserted(logEntries.size - 1)
                saveLogEntries()
                // Scroll again to the new footer after a short delay for smoothness
                Handler(Looper.getMainLooper()).postDelayed({
                    recyclerView.smoothScrollToPosition(logEntries.size)
                }, 200)
            }
        }
        dialog.show(supportFragmentManager, "DateTimePickerDialog")
    }

    private fun deleteLogEntry(logEntry: LogEntry) {
        val position = logEntries.indexOf(logEntry)
        if (position != -1) {
            logEntries.removeAt(position)
            hebrewTimestamps.removeAt(position)
            logEntryAdapter.notifyItemRemoved(position)
            saveLogEntries()
        }
    }

    private fun saveLogEntries() {
        val editor = sharedPreferences.edit()
        // Create a JSON array to store all entry data
        val jsonArray = JSONArray()
        for (entry in logEntries) {
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("timestamp", entry.timestamp)
            obj.put("type", entry.type.name)
            entry.amount?.let { obj.put("amount", it) }
            jsonArray.put(obj)
        }
        editor.putString("log_entries_json", jsonArray.toString())
        editor.apply()
    }

    private fun loadLogEntries() {
        logEntries.clear() // Clear existing entries before loading
        hebrewTimestamps.clear()
        
        // Try to load from the new JSON format first
        val jsonString = sharedPreferences.getString("log_entries_json", null)
        if (jsonString != null) {
            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.getLong("id")
                    val timestamp = obj.getString("timestamp")
                    val type = if (obj.has("type")) 
                        LogEntryType.valueOf(obj.getString("type")) 
                    else 
                        LogEntryType.NORMAL
                    val amount = if (obj.has("amount")) obj.getInt("amount") else null
                    
                    logEntries.add(LogEntry(id, timestamp, type, amount))
                    hebrewTimestamps.add(formatTimestamp(id))
                }
            } catch (e: Exception) {
                // Fallback to old format in case of error
                loadLegacyEntries()
            }
        } else {
            // Fallback to old format if new format not found
            loadLegacyEntries()
        }
        
        logEntryAdapter.notifyDataSetChanged()
        scrollToBottom()
    }
    
    private fun loadLegacyEntries() {
        val timestamps = sharedPreferences.getStringSet("timestamps", emptySet())
        timestamps?.forEach {
            try {
                val timestamp = it.toLong()
                logEntries.add(LogEntry(timestamp, formatTimestamp(timestamp)))
                hebrewTimestamps.add(formatTimestamp(timestamp))
            } catch (e: NumberFormatException) {
                // Handle the exception if needed
            }
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
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

    private fun scrollToBottom() {
        val recyclerView: RecyclerView = findViewById(R.id.recyclerViewTimestamps)
        recyclerView.post {
            recyclerView.smoothScrollToPosition(logEntries.size)
        }
    }

    private fun exportToJson() {
        val jsonArray = JSONArray()
        for (entry in logEntries) {
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("timestamp", entry.timestamp)
            jsonArray.put(obj)
        }
        val jsonString = jsonArray.toString(2)
        // Use SAF to let user pick where to save
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "log_entries.json")
        }
        startActivityForResult(intent, EXPORT_JSON_REQUEST_CODE)
        exportJsonString = jsonString
    }

    private var exportJsonString: String? = null
    private val EXPORT_JSON_REQUEST_CODE = 1001

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EXPORT_JSON_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                exportJsonString?.let { json ->
                    contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    Toast.makeText(this, getString(R.string.exported_to_json), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun importFromJson() {
        importJsonLauncher.launch("application/json")
    }

    private fun importJsonFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val jsonString = inputStream?.bufferedReader().use { it?.readText() } ?: return
            val jsonArray = JSONArray(jsonString)
            logEntries.clear()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getLong("id")
                val timestamp = obj.getString("timestamp")
                logEntries.add(LogEntry(id, timestamp))
            }
            saveLogEntries()
            logEntryAdapter.notifyDataSetChanged()
            Toast.makeText(this, getString(R.string.imported_from_json), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.failed_to_import_json), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showClearAllConfirmation(drawerLayout: DrawerLayout) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.clear_all_confirmation))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                logEntries.clear()
                saveLogEntries()
                logEntryAdapter.notifyDataSetChanged()
                Toast.makeText(this, R.string.menu_clear_all, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    // Helper to show a color picker dialog with preview and OK/Cancel
    private fun showColorPickerDialog(
        title: String,
        colors: Array<String>,
        initialColor: Int,
        onColorPicked: (Int) -> Unit
    ) {
        val context = this
        var pickedColor = initialColor
        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        val preview = View(context).apply {
            val size = (56 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, 24)
            }
            background = resources.getDrawable(R.drawable.circle_swatch, null)
            background.setTint(pickedColor)
        }
        dialogLayout.addView(preview)
        val grid = GridLayout(context)
        grid.rowCount = (colors.size + 4) / 5
        grid.columnCount = 5
        val size = (40 * resources.displayMetrics.density).toInt()
        val margin = (8 * resources.displayMetrics.density).toInt()
        val outValue = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
        for (colorStr in colors) {
            val color = Color.parseColor(colorStr)
            val circle = View(context).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    setMargins(margin, margin, margin, margin)
                }
                background = resources.getDrawable(R.drawable.circle_swatch, null)
                background.setTint(color)
                setOnClickListener {
                    pickedColor = color
                    preview.background.setTint(color)
                }
                foreground = ContextCompat.getDrawable(context, outValue.resourceId)
            }
            grid.addView(circle)
        }
        dialogLayout.addView(grid)
        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(dialogLayout)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                onColorPicked(pickedColor)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        dialog.show()
    }

    private fun showChooseColorsDialog() {
        val resources = resources
        val mainColors = resources.getStringArray(R.array.main_colors)
        val backgroundColors = resources.getStringArray(R.array.background_colors)
        val textColors = resources.getStringArray(R.array.text_colors)

        val prefs = getSharedPreferences("color_prefs", MODE_PRIVATE)
        val editor = prefs.edit()

        val defaultMain = Color.parseColor("#1976D2")
        val defaultBg1 = Color.parseColor("#FFFFFF")
        val defaultBg2 = Color.parseColor("#EEEEEE")
        val defaultText = Color.parseColor("#222222")

        var selectedMain = prefs.getInt("main_color", defaultMain)
        var selectedBg1 = prefs.getInt("bg_color1", defaultBg1)
        var selectedBg2 = prefs.getInt("bg_color2", defaultBg2)
        var selectedText = prefs.getInt("text_color", defaultText)

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        fun colorPreview(color: Int): View {
            return View(this).apply {
                val size = (48 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(0, 0, 0, 24)
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                background = resources.getDrawable(R.drawable.circle_swatch, null)
                background.setTint(color)
            }
        }
        val mainColorPreview = colorPreview(selectedMain)
        val bg1Preview = colorPreview(selectedBg1)
        val bg2Preview = colorPreview(selectedBg2)
        val textColorPreview = colorPreview(selectedText)

        dialogLayout.addView(TextView(this).apply { text = getString(R.string.main_color) })
        dialogLayout.addView(mainColorPreview)
        dialogLayout.addView(TextView(this).apply { text = getString(R.string.background_color_1) })
        dialogLayout.addView(bg1Preview)
        dialogLayout.addView(TextView(this).apply { text = getString(R.string.background_color_2) })
        dialogLayout.addView(bg2Preview)
        dialogLayout.addView(TextView(this).apply { text = getString(R.string.text_color) })
        dialogLayout.addView(textColorPreview)

        mainColorPreview.setOnClickListener {
            showColorPickerDialog(getString(R.string.choose_main_color), mainColors, selectedMain) { color ->
                selectedMain = color
                mainColorPreview.background.setTint(color)
            }
        }
        bg1Preview.setOnClickListener {
            showColorPickerDialog(getString(R.string.choose_background_color_1), backgroundColors, selectedBg1) { color ->
                selectedBg1 = color
                bg1Preview.background.setTint(color)
            }
        }
        bg2Preview.setOnClickListener {
            showColorPickerDialog(getString(R.string.choose_background_color_2), backgroundColors, selectedBg2) { color ->
                selectedBg2 = color
                bg2Preview.background.setTint(color)
            }
        }
        textColorPreview.setOnClickListener {
            showColorPickerDialog(getString(R.string.choose_text_color), textColors, selectedText) { color ->
                selectedText = color
                textColorPreview.background.setTint(color)
            }
        }

        val scrollView = ScrollView(this)
        scrollView.addView(dialogLayout)

        // Custom button row
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, 32, 0, 0)
        }
        var dialog: AlertDialog? = null
        val resetButton = Button(this).apply {
            text = getString(R.string.reset)
            setOnClickListener {
                selectedMain = defaultMain
                selectedBg1 = defaultBg1
                selectedBg2 = defaultBg2
                selectedText = defaultText
                mainColorPreview.background.setTint(defaultMain)
                bg1Preview.background.setTint(defaultBg1)
                bg2Preview.background.setTint(defaultBg2)
                textColorPreview.background.setTint(defaultText)
            }
        }
        val cancelButton = Button(this).apply {
            text = getString(R.string.cancel)
            setOnClickListener { dialog?.dismiss() }
        }
        val saveButton = Button(this).apply {
            text = getString(R.string.save)
            setOnClickListener {
                editor.putInt("main_color", selectedMain)
                editor.putInt("bg_color1", selectedBg1)
                editor.putInt("bg_color2", selectedBg2)
                editor.putInt("text_color", selectedText)
                editor.apply()
                applyColors(selectedMain, selectedBg1, selectedBg2, selectedText)
                Toast.makeText(this@MainActivity, getString(R.string.colors_saved), Toast.LENGTH_SHORT).show()
                dialog?.dismiss()
            }
        }
        buttonLayout.addView(resetButton)
        buttonLayout.addView(cancelButton)
        buttonLayout.addView(saveButton)
        dialogLayout.addView(buttonLayout)

        dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.choose_colors))
            .setView(scrollView)
            .create()
        dialog.show()
    }

    // Apply the selected colors to the UI immediately
    private fun applyColors(mainColor: Int, bg1: Int, bg2: Int, textColor: Int) {
        selectedMainColor = mainColor
        selectedBgColor1 = bg1
        selectedBgColor2 = bg2
        selectedTextColor = textColor

        // Set status bar color
        window.statusBarColor = mainColor

        // Toolbar
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        toolbar.setBackgroundColor(mainColor)
        toolbar.setTitleTextColor(Color.WHITE)

        // FAB
        val fabAdd: com.google.android.material.floatingactionbutton.FloatingActionButton = findViewById(R.id.fabAdd)
        fabAdd.backgroundTintList = android.content.res.ColorStateList.valueOf(mainColor)
        
        // Car FAB
        val fabCar: com.google.android.material.floatingactionbutton.FloatingActionButton = findViewById(R.id.fabCar)
        fabCar.backgroundTintList = android.content.res.ColorStateList.valueOf(mainColor)

        // RecyclerView stripes
        val recyclerView: RecyclerView = findViewById(R.id.recyclerViewTimestamps)
        recyclerView.invalidateItemDecorations()
        recyclerView.adapter = LogEntryAdapter(logEntries, hebrewTimestamps, { logEntry ->
            deleteLogEntry(logEntry)
        }, textColor)
        recyclerView.adapter?.notifyDataSetChanged()
    }

    private fun logCarEntry() {
        val recyclerView: RecyclerView = findViewById(R.id.recyclerViewTimestamps)
        recyclerView.smoothScrollToPosition(logEntries.size)
        recyclerView.post {
            val currentTime = System.currentTimeMillis()
            val logEntry = LogEntry(currentTime, formatTimestamp(currentTime), LogEntryType.CAR, 1)
            logEntries.add(logEntry)
            hebrewTimestamps.add(formatTimestamp(currentTime))
            logEntryAdapter.notifyItemInserted(logEntries.size - 1)
            saveLogEntries()
            Handler(Looper.getMainLooper()).postDelayed({
                recyclerView.smoothScrollToPosition(logEntries.size)
            }, 200)
        }
    }

    private fun showCarEntriesDialog() {
        val carEntries = logEntries.filter { it.type == LogEntryType.CAR }
        val sum = carEntries.sumOf { it.amount ?: 0 }
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.menu_sum_car_entries))
            .setMessage(getString(R.string.total_car_entries, sum))
            .setPositiveButton(getString(R.string.delete_all_car_entries)) { _, _ ->
                logEntries.removeAll { it.type == LogEntryType.CAR }
                hebrewTimestamps.clear()
                logEntries.forEach { hebrewTimestamps.add(it.timestamp) }
                logEntryAdapter.notifyDataSetChanged()
                saveLogEntries()
                Toast.makeText(this, getString(R.string.all_car_entries_deleted), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}