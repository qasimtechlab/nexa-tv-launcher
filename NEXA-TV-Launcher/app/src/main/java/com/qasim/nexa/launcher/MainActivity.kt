package com.qasim.nexa.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import org.json.JSONObject

private data class LaunchableApp(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable
)

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var root: FrameLayout
    private lateinit var wallpaperView: ImageView
    private lateinit var dimLayer: View
    private lateinit var contentHost: FrameLayout
    private var currentPage: String = PAGE_HOME

    private val bgDark = Color.rgb(8, 14, 25)
    private val textLight = Color.rgb(241, 246, 255)
    private val textMuted = Color.rgb(170, 188, 211)
    private val accent = Color.rgb(115, 177, 255)
    private val pickerRequest = 4112
    private lateinit var keyVault: GeminiKeyVault
    private val aiExecutor = Executors.newSingleThreadExecutor()
    private val youtubeExecutor = Executors.newFixedThreadPool(3)
    private val youtubeVideoCache = ConcurrentHashMap<String, List<YoutubeVideo>>()
    private val youtubeFetchInProgress = mutableSetOf<String>()
    private val youtubeErrors = mutableMapOf<String, String>()
    private val youtubeThumbnailCache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > 12
    }
    private var futuristicWidgetView: FuturisticWidgetView? = null
    private val widgetFrameTicker = object : Runnable {
        override fun run() {
            val widget = futuristicWidgetView
            if (currentPage == PAGE_HOME && widget != null) {
                widget.advanceFrame()
                mainHandler.postDelayed(this, 70L)
            }
        }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val aiMessages = mutableListOf<Pair<String, String>>()
    private var aiBusy = false
    private var aiStatusMessage: String? = null
    private var activeAiInput: EditText? = null
    private var pendingSpeechInput: EditText? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechListening = false
    private var aiMicButton: Button? = null
    private var textToSpeech: TextToSpeech? = null
    private var textToSpeechReady = false
    private var movingAppPackage: String? = null
    private var focusAppAfterRender: String? = null
    private var originalAppOrderBeforeMove: List<String>? = null
    private var appGridColumns = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("nexa_preferences", MODE_PRIVATE)
        val savedYoutubeTopics = prefs.getString(KEY_YOUTUBE_INTERESTS, null)
        val supportedYoutubeTopics = YoutubeChannelCatalog.categoryNames
        if (savedYoutubeTopics.isNullOrBlank() ||
            savedYoutubeTopics.split("|").none { it.trim() in supportedYoutubeTopics }
        ) {
            prefs.edit().putString(KEY_YOUTUBE_INTERESTS, DEFAULT_YOUTUBE_INTERESTS).apply()
        }
        // Remove the legacy YouTube Data API key from earlier versions; the new feed needs no key.
        prefs.edit().remove("youtube_data_api_key").apply()
        keyVault = GeminiKeyVault(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersiveMode()

        root = FrameLayout(this)
        wallpaperView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(bgDark)
        }
        dimLayer = View(this)
        contentHost = FrameLayout(this)
        root.addView(wallpaperView, FrameLayout.LayoutParams(-1, -1))
        root.addView(dimLayer, FrameLayout.LayoutParams(-1, -1))
        root.addView(contentHost, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        applyWallpaper()
        renderPage(PAGE_HOME)
        initializeTextToSpeech()
        if (intent.getBooleanExtra(EXTRA_START_AI_VOICE, false)) mainHandler.postDelayed({ openAiAndListen() }, 500L)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_START_AI_VOICE, false)) openAiAndListen()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            (event.keyCode == KeyEvent.KEYCODE_VOICE_ASSIST || event.keyCode == KeyEvent.KEYCODE_ASSIST) &&
            currentPage == PAGE_AI
        ) {
            activeAiInput?.let { startVoiceInput(it) }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
        if (::contentHost.isInitialized) {
            // Refresh the visible launchable app list and Home feed after returning from an app.
            if (currentPage == PAGE_APPS || currentPage == PAGE_HOME) renderPage(currentPage)
        }
    }

    override fun onPause() {
        mainHandler.removeCallbacks(widgetFrameTicker)
        super.onPause()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(widgetFrameTicker)
        try { speechRecognizer?.cancel(); speechRecognizer?.destroy() } catch (_: Exception) { }
        speechRecognizer = null
        textToSpeech?.stop(); textToSpeech?.shutdown()
        aiExecutor.shutdownNow(); youtubeExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC_PERMISSION) {
            val input = pendingSpeechInput
            pendingSpeechInput = null
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED && input != null)
                startSpeechRecognition(input)
            else toast("Microphone permission is required for voice input.")
        }
    }

    private fun applyImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }

    override fun onBackPressed() {
        if (movingAppPackage != null) {
            val selected = movingAppPackage
            val originalOrder = originalAppOrderBeforeMove
            if (!originalOrder.isNullOrEmpty()) {
                prefs.edit().putString(KEY_APP_ORDER, originalOrder.joinToString("|")).apply()
            }
            originalAppOrderBeforeMove = null
            movingAppPackage = null
            focusAppAfterRender = selected
            renderPage(PAGE_APPS)
            toast("App movement cancelled.")
            return
        }
        if (speechListening) { stopVoiceInput(); return }
        if (currentPage != PAGE_HOME) {
            renderPage(PAGE_HOME)
        } else {
            // A Home activity should remain a usable home surface when BACK is pressed.
            super.onBackPressed()
        }
    }

    private fun renderPage(page: String) {
        currentPage = page
        mainHandler.removeCallbacks(widgetFrameTicker)
        futuristicWidgetView = null
        contentHost.removeAllViews()
        applyWallpaper()

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(42), dp(24), dp(42), dp(38))
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        scroll.addView(column, FrameLayout.LayoutParams(-1, -2))
        contentHost.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        addHeader(column)
        addNavigation(column, page)

        when (page) {
            PAGE_HOME -> buildHome(column)
            PAGE_APPS -> buildApps(column)
            PAGE_CUSTOMIZE -> buildCustomize(column)
            PAGE_AI -> buildAi(column)
            PAGE_YOUTUBE -> buildYoutube(column)
            PAGE_SETTINGS -> buildSettings(column)
            PAGE_WIDGETS -> buildWidgets(column)
            else -> buildHome(column)
        }

        column.addView(textView("NEXA  •  Created by Mirza Qasim", 12, textMuted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, dp(4))
        }) 
        contentHost.post {
            try {
                val packageToFocus = if (page == PAGE_APPS) focusAppAfterRender else null
                if (!packageToFocus.isNullOrBlank()) {
                    val tile = contentHost.findViewWithTag<View>("nexa_app_tile:$packageToFocus")
                    focusAppAfterRender = null
                    if (tile != null && tile.requestFocus()) return@post
                }
                val navScroll = column.getChildAt(1) as? HorizontalScrollView
                val navRow = navScroll?.getChildAt(0) as? LinearLayout
                navRow?.getChildAt(0)?.requestFocus()
            } catch (_: Exception) {
                // Focus can be restored by the user with the remote if the view is not ready.
            }
        }
    }

    private fun addHeader(parent: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(textView("NEXA", 30, textLight, true))
        brand.addView(textView("SMART TV EXPERIENCE", 10, textMuted, true))
        row.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        val date = SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(Date())
        val time = TextClock(this).apply {
            format12Hour = "hh:mm a"
            format24Hour = "HH:mm"
            textSize = 20f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (prefsOrDefaultTheme() == THEME_LIGHT) Color.rgb(22, 35, 53) else textLight)
            gravity = Gravity.END
        }
        right.addView(time)
        right.addView(textView(date, 12, textMuted).apply { gravity = Gravity.END })
        row.addView(right)
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(22) })
    }

        private fun addNavigation(parent: LinearLayout, selected: String) {
        val horizontal = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val entries = listOf(
            Triple("Home", PAGE_HOME, "⌂"),
            Triple("Apps", PAGE_APPS, "▦"),
            Triple("Customize", PAGE_CUSTOMIZE, "✦"),
            Triple("YouTube", PAGE_YOUTUBE, "▶"),
            Triple("BixSigma AI", PAGE_AI, "◇"),
            Triple("Settings", PAGE_SETTINGS, "⚙"),
            Triple("Widgets", PAGE_WIDGETS, "◉")
        )
        entries.forEach { (label, page, symbol) ->
            val button = navButton("$symbol   $label", page == selected) { renderPage(page) }
            row.addView(button, LinearLayout.LayoutParams(-2, dp(48)).apply {
                rightMargin = dp(10)
            })
        }
        horizontal.addView(row)
        parent.addView(horizontal, LinearLayout.LayoutParams(-1, dp(54)).apply {
            bottomMargin = dp(18)
        })
    }

    private fun buildHome(parent: LinearLayout) {
        buildHomeWidgetStrip(parent)
        val cleanHome = prefs.getBoolean(KEY_CLEAN_HOME, false)

        if (!cleanHome) {
            val hero = glassCard(true).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(30), dp(22), dp(30), dp(22))
            }
            hero.addView(textView("YOUR TV. YOUR WORLD.", 12, accent, true))
            hero.addView(textView("Everything, beautifully\nin one place.", 29, textLight, true).apply {
                setPadding(0, dp(7), 0, dp(7))
            })
            hero.addView(textView("A clean, customizable home for your entertainment.", 14, textMuted))
            parent.addView(hero, LinearLayout.LayoutParams(-1, dp(166)).apply { bottomMargin = dp(18) })

            parent.addView(sectionTitle("Explore NEXA"))
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val cards = listOf(
                Triple("▦", "Applications", "Open your installed apps"),
                Triple("✦", "Personalize", "Wallpaper, themes and colors"),
                Triple("◇", "BixSigma AI", "Your optional AI space"),
                Triple("⚙", "Settings", "Manage your launcher")
            )
            cards.forEachIndexed { index, item ->
                val destination = listOf(PAGE_APPS, PAGE_CUSTOMIZE, PAGE_AI, PAGE_SETTINGS)[index]
                actions.addView(actionCard(item.first, item.second, item.third) {
                    renderPage(destination)
                }, LinearLayout.LayoutParams(dp(220), dp(126)).apply { rightMargin = dp(12) })
            }
            val actionScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            actionScroll.addView(actions)
            parent.addView(actionScroll, LinearLayout.LayoutParams(-1, dp(138)).apply { bottomMargin = dp(16) })
        }

        parent.addView(sectionTitle("Your favorites"))
        val favorites = getLaunchableApps().filter { isFavoritePackage(it.packageName) }
        if (favorites.isEmpty()) {
            parent.addView(infoCard("Your Home screen only shows apps you mark as favorites. Open Apps, highlight an app and hold OK on the remote to add it here."))
        } else {
            val rail = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            favorites.forEach { app ->
                rail.addView(appTile(app), LinearLayout.LayoutParams(dp(172), dp(142)).apply {
                    rightMargin = dp(12)
                })
            }
            val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            scroll.addView(rail)
            parent.addView(scroll, LinearLayout.LayoutParams(-1, dp(152)).apply { bottomMargin = dp(16) })
        }

        buildHomeYoutubeRail(parent)
    }

    private fun buildHomeWidgetStrip(parent: LinearLayout) {
        val strip = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        val clock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = cardDrawable(false, true)
            isFocusable = true
            isClickable = true
            contentDescription = "Movable time and date widget. Use LEFT, RIGHT, UP and DOWN on the remote to position it."
            setOnFocusChangeListener { view, focused -> view.background = cardDrawable(focused, true) }
        }
        val time = TextClock(this).apply {
            format12Hour = "hh:mm"
            format24Hour = "HH:mm"
            textSize = 36f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextColor(if (prefsOrDefaultTheme() == THEME_LIGHT) Color.rgb(22, 35, 53) else textLight)
        }
        val date = textView(SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault()).format(Date()), 12, textMuted)
        clock.addView(time)
        clock.addView(date, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(1) })

        val widgetStyle = prefs.getString(KEY_FUTURE_WIDGET, WIDGET_GLOBE) ?: WIDGET_GLOBE
        val placement = prefs.getString(KEY_FUTURISTIC_POSITION, "side") ?: "side"
        if (widgetStyle != WIDGET_OFF) {
            val art = FuturisticWidgetView(this, widgetStyle).apply {
                isFocusable = true
                isClickable = true
                contentDescription = if (widgetStyle == WIDGET_REACTOR)
                    "Animated orbital reactor widget. Use all four arrow buttons to move it on Home."
                else "Animated rotating Earth widget. Use all four arrow buttons to move it on Home."
                setOnClickListener { renderPage(PAGE_WIDGETS) }
                setOnFocusChangeListener { view, focused ->
                    view.alpha = if (focused) 1f else 0.9f
                    view.animate().scaleX(if (focused) 1.07f else 1f)
                        .scaleY(if (focused) 1.07f else 1f).setDuration(130L).start()
                }
                setOnKeyListener { view, keyCode, event ->
                    if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                    val params = view.layoutParams as FrameLayout.LayoutParams
                    val maxLeft = (strip.width - view.width).coerceAtLeast(0)
                    val maxTop = (strip.height - view.height).coerceAtLeast(0)
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> params.leftMargin = (params.leftMargin - dp(18)).coerceAtLeast(0)
                        KeyEvent.KEYCODE_DPAD_RIGHT -> params.leftMargin = (params.leftMargin + dp(18)).coerceAtMost(maxLeft)
                        KeyEvent.KEYCODE_DPAD_UP -> params.topMargin = (params.topMargin - dp(18)).coerceAtLeast(0)
                        KeyEvent.KEYCODE_DPAD_DOWN -> params.topMargin = (params.topMargin + dp(18)).coerceAtMost(maxTop)
                        else -> return@setOnKeyListener false
                    }
                    view.layoutParams = params
                    val density = resources.displayMetrics.density
                    prefs.edit()
                        .putInt(KEY_FUTURE_WIDGET_X_DP, (params.leftMargin / density).roundToInt())
                        .putInt(KEY_FUTURE_WIDGET_Y_DP, (params.topMargin / density).roundToInt())
                        .apply()
                    true
                }
            }
            val artParams = FrameLayout.LayoutParams(dp(126), dp(126)).apply {
                gravity = Gravity.TOP or Gravity.LEFT
            }
            strip.addView(art, artParams)
            art.post {
                if (art.width > 0 && strip.width > 0) {
                    val params = art.layoutParams as FrameLayout.LayoutParams
                    val maxLeft = (strip.width - art.width).coerceAtLeast(0)
                    val maxTop = (strip.height - art.height).coerceAtLeast(0)
                    val savedX = prefs.getInt(KEY_FUTURE_WIDGET_X_DP, -1)
                    val savedY = prefs.getInt(KEY_FUTURE_WIDGET_Y_DP, -1)
                    val defaultLeft = if (placement == "center") maxLeft / 2 else maxLeft
                    val defaultTop = maxTop / 2
                    params.leftMargin = (if (savedX < 0) defaultLeft else dp(savedX)).coerceIn(0, maxLeft)
                    params.topMargin = (if (savedY < 0) defaultTop else dp(savedY)).coerceIn(0, maxTop)
                    art.layoutParams = params
                }
            }
            futuristicWidgetView = art
        }

        val clockParams = FrameLayout.LayoutParams(dp(272), dp(92)).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            topMargin = dp(26)
        }
        strip.addView(clock, clockParams)

        clock.post {
            if (clock.width > 0 && strip.width > 0) {
                val reserved = if (widgetStyle != WIDGET_OFF && placement == "side") dp(142) else dp(12)
                val maxLeft = (strip.width - clock.width - reserved).coerceAtLeast(0)
                val maxTop = (strip.height - clock.height).coerceAtLeast(0)
                val savedX = prefs.getInt(KEY_CLOCK_POSITION_DP, -1)
                val savedY = prefs.getInt(KEY_CLOCK_POSITION_Y_DP, -1)
                val desiredX = when {
                    savedX == -2 -> maxLeft / 2
                    savedX < 0 -> 0
                    else -> dp(savedX)
                }
                val desiredY = when {
                    savedY == -2 -> maxTop / 2
                    savedY < 0 -> dp(26)
                    else -> dp(savedY)
                }
                clockParams.leftMargin = desiredX.coerceIn(0, maxLeft)
                clockParams.topMargin = desiredY.coerceIn(0, maxTop)
                clock.layoutParams = clockParams
            }
        }

        var dragStartX = 0f
        var dragStartY = 0f
        var startLeft = 0
        var startTop = 0
        clock.setOnTouchListener { view, event ->
            val params = view.layoutParams as FrameLayout.LayoutParams
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    startLeft = params.leftMargin
                    startTop = params.topMargin
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val reserved = if (widgetStyle != WIDGET_OFF && placement == "side") dp(142) else dp(12)
                    val maxLeft = (strip.width - view.width - reserved).coerceAtLeast(0)
                    val maxTop = (strip.height - view.height).coerceAtLeast(0)
                    params.leftMargin = (startLeft + (event.rawX - dragStartX).toInt()).coerceIn(0, maxLeft)
                    params.topMargin = (startTop + (event.rawY - dragStartY).toInt()).coerceIn(0, maxTop)
                    view.layoutParams = params
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val density = resources.displayMetrics.density
                    prefs.edit()
                        .putInt(KEY_CLOCK_POSITION_DP, (params.leftMargin / density).roundToInt())
                        .putInt(KEY_CLOCK_POSITION_Y_DP, (params.topMargin / density).roundToInt())
                        .apply()
                    true
                }
                else -> false
            }
        }
        clock.setOnLongClickListener {
            toast("Clock selected. Use all four arrow buttons to move it; press OK to center.")
            true
        }
        clock.setOnKeyListener { view, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            val params = view.layoutParams as FrameLayout.LayoutParams
            val reserved = if (widgetStyle != WIDGET_OFF && placement == "side") dp(142) else dp(12)
            val maxLeft = (strip.width - view.width - reserved).coerceAtLeast(0)
            val maxTop = (strip.height - view.height).coerceAtLeast(0)
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    params.leftMargin = (params.leftMargin - dp(18)).coerceAtLeast(0)
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    params.leftMargin = (params.leftMargin + dp(18)).coerceAtMost(maxLeft)
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    params.topMargin = (params.topMargin - dp(18)).coerceAtLeast(0)
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    params.topMargin = (params.topMargin + dp(18)).coerceAtMost(maxTop)
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    params.leftMargin = maxLeft / 2
                    params.topMargin = maxTop / 2
                }
                else -> return@setOnKeyListener false
            }
            view.layoutParams = params
            val density = resources.displayMetrics.density
            prefs.edit()
                .putInt(KEY_CLOCK_POSITION_DP, (params.leftMargin / density).roundToInt())
                .putInt(KEY_CLOCK_POSITION_Y_DP, (params.topMargin / density).roundToInt())
                .apply()
            true
        }

        parent.addView(strip, LinearLayout.LayoutParams(-1, dp(154)).apply { bottomMargin = dp(18) })
        if (futuristicWidgetView != null) {
            mainHandler.removeCallbacks(widgetFrameTicker)
            mainHandler.post(widgetFrameTicker)
        }
    }

            private fun buildApps(parent: LinearLayout) {
        parent.addView(sectionTitle("Application library"))
        val rows = prefs.getBoolean(KEY_APPS_ROWS, false)
        val moving = movingAppPackage
        parent.addView(textView(
            if (moving == null) "Select an app to open it. Hold OK for options, then choose Move app to arrange it with the remote."
            else "MOVE MODE  •  Use LEFT / RIGHT / UP / DOWN. Press OK to finish or BACK to cancel.",
            14, if (moving == null) textMuted else accent, moving != null
        ).apply { setPadding(0, 0, 0, dp(12)) })
        addSettingCard(parent, if (rows) "▤   Layout: Rows" else "▦   Layout: Grid",
            "Change how installed applications are arranged") {
            movingAppPackage = null
            prefs.edit().putBoolean(KEY_APPS_ROWS, !rows).apply()
            renderPage(PAGE_APPS)
        }
        val installed = getLaunchableApps()
        if (installed.isEmpty()) { parent.addView(infoCard("No compatible launchable applications were found on this device.")); return }
        if (rows) {
            appGridColumns = 1
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            installed.forEach { app ->
                list.addView(appTile(app, true), LinearLayout.LayoutParams(-1, dp(82)).apply { bottomMargin = dp(9) })
            }
            parent.addView(list, LinearLayout.LayoutParams(-1, -2))
        } else {
            val screenWidth = resources.configuration.screenWidthDp
            val columns = when { screenWidth < 780 -> 2; screenWidth < 1150 -> 3; else -> 4 }
            appGridColumns = columns
            val tileWidth = (screenWidth / columns - 24).coerceIn(150, 220)
            val grid = GridLayout(this).apply { columnCount = columns; alignmentMode = GridLayout.ALIGN_BOUNDS; useDefaultMargins = false }
            installed.forEach { app ->
                val tile = appTile(app)
                val lp = GridLayout.LayoutParams().apply {
                    width = dp(tileWidth); height = dp(142)
                    setMargins(dp(5), dp(5), dp(12), dp(12))
                }
                grid.addView(tile, lp)
            }
            parent.addView(grid, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun buildCustomize(parent: LinearLayout) {
        parent.addView(sectionTitle("Make NEXA yours"))
        parent.addView(textView("Personal settings are saved on this TV. No account is required.", 14, textMuted).apply {
            setPadding(0, 0, 0, dp(16))
        })

        parent.addView(sectionTitle("Built-in NEXA wallpapers"))
        parent.addView(textView("Choose an offline wallpaper, or use your own gaming, space or landscape image.", 13, textMuted).apply {
            setPadding(0, 0, 0, dp(10))
        })
        val wallpaperRail = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        WallpaperPresets.all.forEach { preset ->
            wallpaperRail.addView(wallpaperPresetCard(preset), LinearLayout.LayoutParams(dp(188), dp(142)).apply {
                rightMargin = dp(12)
            })
        }
        val wallpaperScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        wallpaperScroll.addView(wallpaperRail)
        parent.addView(wallpaperScroll, LinearLayout.LayoutParams(-1, dp(152)).apply { bottomMargin = dp(14) })

        addSettingCard(parent, "▧   Choose my own wallpaper", "Use a photo, gaming wallpaper or any image stored on this device") { openWallpaperPicker() }
        addSettingCard(parent, "▣   Wallpaper framing: ${wallpaperScaleName()}", "Fill screen, fit full image or center the wallpaper") { showWallpaperScaleDialog() }
        addSettingCard(parent, "◐   Wallpaper overlay: ${prefs.getInt(KEY_WALLPAPER_DIM, 48)}%", "Adjust readability without making the wallpaper look messy") { showWallpaperDimDialog() }
        addSettingCard(parent, "◐   Change appearance", "Current theme: ${themeName()}") { cycleTheme() }
        addSettingCard(parent, "✦   Accent color", "Current accent: ${accentName()}") { cycleAccent() }
        addSettingCard(
            parent,
            if (prefs.getBoolean(KEY_CLEAN_HOME, false)) "✓   Clean Home layout: ON" else "□   Clean Home layout: OFF",
            "Hide the large intro and shortcut panels for a minimal wallpaper-first screen"
        ) {
            prefs.edit().putBoolean(KEY_CLEAN_HOME, !prefs.getBoolean(KEY_CLEAN_HOME, false)).apply()
            renderPage(PAGE_CUSTOMIZE)
        }
        addSettingCard(
            parent,
            if (prefs.getBoolean(KEY_APPS_ROWS, false)) "▤   Apps layout: Rows" else "▦   Apps layout: Grid",
            "Choose line-wise rows or a compact app grid"
        ) {
            prefs.edit().putBoolean(KEY_APPS_ROWS, !prefs.getBoolean(KEY_APPS_ROWS, false)).apply()
            renderPage(PAGE_CUSTOMIZE)
        }
        addSettingCard(parent, "◉   Futuristic Widgets settings", "Add an animated rotating Earth or orbital reactor to Home") {
            renderPage(PAGE_WIDGETS)
        }
        addSettingCard(parent, "⌚   Center the clock widget", "Reset to center. On Home, focus the clock and use all four remote arrows to move it") {
            prefs.edit()
                .putInt(KEY_CLOCK_POSITION_DP, -2)
                .putInt(KEY_CLOCK_POSITION_Y_DP, -2)
                .apply()
            renderPage(PAGE_HOME)
        }

        addSettingCard(parent, "↺   Reset wallpaper and appearance", "Restore the original NEXA look and default wallpaper") {
            prefs.edit()
                .remove(KEY_WALLPAPER)
                .remove(KEY_BUILTIN_WALLPAPER)
                .remove(KEY_WALLPAPER_SCALE)
                .remove(KEY_WALLPAPER_DIM)
                .putBoolean(KEY_CLEAN_HOME, false)
                .putString(KEY_THEME, THEME_GLASS)
                .putInt(KEY_ACCENT, 0)
                .apply()
            applyWallpaper()
            renderPage(PAGE_CUSTOMIZE)
            toast("Personalization reset")
        }
    }

    private fun buildWidgets(parent: LinearLayout) {
        parent.addView(sectionTitle("Futuristic Widgets"))
        parent.addView(infoCard("Choose one offline animated widget for your NEXA Home screen. The artwork is drawn on the TV itself and does not need an internet connection."))
        val selected = prefs.getString(KEY_FUTURE_WIDGET, WIDGET_GLOBE) ?: WIDGET_GLOBE
        addSettingCard(
            parent,
            if (selected == WIDGET_GLOBE) "✓   Rotating Earth — ACTIVE" else "🌐   Rotating Earth Globe",
            "A glowing 3D-style globe with orbit, latitude and longitude animation"
        ) {
            prefs.edit().putString(KEY_FUTURE_WIDGET, WIDGET_GLOBE).apply()
            renderPage(PAGE_WIDGETS)
        }
        addSettingCard(
            parent,
            if (selected == WIDGET_REACTOR) "✓   Orbital Reactor — ACTIVE" else "✧   Orbital Reactor",
            "A futuristic rotating energy core with animated rings and orbiting lights"
        ) {
            prefs.edit().putString(KEY_FUTURE_WIDGET, WIDGET_REACTOR).apply()
            renderPage(PAGE_WIDGETS)
        }
        addSettingCard(
            parent,
            if (selected == WIDGET_OFF) "✓   Animation: OFF" else "○   Turn animation off",
            "Keep the Home screen minimal and save a little processing power"
        ) {
            prefs.edit().putString(KEY_FUTURE_WIDGET, WIDGET_OFF).apply()
            renderPage(PAGE_WIDGETS)
        }
        val placement = prefs.getString(KEY_FUTURISTIC_POSITION, "side") ?: "side"
        addSettingCard(
            parent,
            "↔   Default widget position: ${if (placement == "center") "Center" else "Right"}",
            "Choose its starting place. On Home, focus the animation and use all four remote arrows to move it freely."
        ) {
            prefs.edit()
                .putString(KEY_FUTURISTIC_POSITION, if (placement == "center") "side" else "center")
                .remove(KEY_FUTURE_WIDGET_X_DP)
                .remove(KEY_FUTURE_WIDGET_Y_DP)
                .apply()
            renderPage(PAGE_WIDGETS)
        }
        addSettingCard(parent, "⌚   Center clock widget", "Reset to center. On Home, focus the clock and use LEFT / RIGHT / UP / DOWN to reposition") {
            prefs.edit()
                .putInt(KEY_CLOCK_POSITION_DP, -2)
                .putInt(KEY_CLOCK_POSITION_Y_DP, -2)
                .apply()
            renderPage(PAGE_WIDGETS)
        }
        addSettingCard(parent, "←   Back to Home", "Preview your selected widgets") { renderPage(PAGE_HOME) }
    }

        private fun buildSettings(parent: LinearLayout) {
        parent.addView(sectionTitle("Launcher settings"))
        parent.addView(infoCard("NEXA is an independent launcher. Your factory launcher and system apps are not modified."))
        val recommendationsEnabled = prefs.getBoolean(KEY_YOUTUBE_RECOMMENDATIONS_ENABLED, true)
        addSettingCard(
            parent,
            if (recommendationsEnabled) "✓   YouTube Recommendations: ON" else "○   YouTube Recommendations: OFF",
            "Show or hide the YouTube video section on Home. Favorites and the rest of Home stay unchanged."
        ) { showYouTubeRecommendationsDialog() }
        addSettingCard(parent, "☷   Manage YouTube categories", "Choose Pakistani/Indian drama, movie, tech, gaming and vlog feeds") {
            showYouTubeInterestsDialog()
        }
        addSettingCard(parent, "▦   Refresh app library", "Scan apps available to this launcher") {
            renderPage(PAGE_APPS)
            toast("Application list refreshed")
        }
        addSettingCard(parent, "⌂   Default Home app", "Open Android Home app settings") {
            try {
                startActivity(Intent("android.settings.HOME_SETTINGS"))
            } catch (_: Exception) {
                toast("Open Android Settings and choose Default Apps → Home app")
            }
        }
        addSettingCard(parent, "◉   Futuristic Widgets", "Configure the animated globe or orbital reactor") { renderPage(PAGE_WIDGETS) }
        addSettingCard(parent, "ⓘ   About NEXA", "Version 1.3.2 · Created by Mirza Qasim") {
            renderAbout(parent)
        }
        addSettingCard(parent, "↻   Restart launcher screen", "Reload NEXA without rebooting the TV") {
            renderPage(PAGE_HOME)
        }
    }

    private fun showYouTubeRecommendationsDialog() {
        val enabled = prefs.getBoolean(KEY_YOUTUBE_RECOMMENDATIONS_ENABLED, true)
        val options = arrayOf(
            "ON — Show curated Hindi/Urdu video cards on Home",
            "OFF — Hide recommendations from Home"
        )
        AlertDialog.Builder(this)
            .setTitle("Home YouTube recommendations")
            .setSingleChoiceItems(options, if (enabled) 0 else 1) { dialog, which ->
                val turnOn = which == 0
                prefs.edit().putBoolean(KEY_YOUTUBE_RECOMMENDATIONS_ENABLED, turnOn).apply()
                dialog.dismiss()
                renderPage(PAGE_SETTINGS)
                toast(if (turnOn) "YouTube recommendations enabled on Home" else "YouTube recommendations hidden from Home")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

            private fun buildAi(parent: LinearLayout) {
        val hero = glassCard(true).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }
        hero.addView(textView("BIXSIGMA AI", 11, accent, true))
        hero.addView(textView("Your TV. Your assistant.", 27, textLight, true).apply { setPadding(0, dp(4), 0, dp(3)) })
        hero.addView(textView("Ask by typing or speak with your remote.", 14, textMuted))
        parent.addView(hero, LinearLayout.LayoutParams(-1, dp(126)).apply { bottomMargin = dp(12) })

        val savedKey = keyVault.read()
        addSettingCard(parent, if (savedKey.isNullOrBlank()) "＋   Add API key" else "✓   API key saved  ·  Change key",
            if (savedKey.isNullOrBlank()) "Connect your private key to activate replies" else "Your key is stored locally on this TV") {
            showAiKeyDialog()
        }
        addSettingCard(parent, "↗   Get API key", "Open the secure key provider dashboard") { openApiKeyPortal() }
        addSettingCard(parent, "⌁   Set remote voice button",
            "Choose BixSigma AI as your TV assistant so the remote voice key can open this screen") { requestDefaultAssistant() }

        parent.addView(sectionTitle("Quick actions"))
        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Open Spotify", "Suggest a drama", "Suggest a movie").forEach { prompt ->
            quick.addView(navButton(prompt, false) {
                activeAiInput?.let { field -> field.setText(prompt); sendAiMessage(field) }
            }, LinearLayout.LayoutParams(-2, dp(48)).apply { rightMargin = dp(9) })
        }
        val quickScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
        quickScroll.addView(quick)
        parent.addView(quickScroll, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(12) })

        parent.addView(sectionTitle("Conversation"))
        if (aiMessages.isEmpty()) parent.addView(infoCard("Welcome to BixSigma AI. Ask for help, a movie or drama recommendation, or say “Open Spotify”."))
        else aiMessages.takeLast(12).forEach { (role, message) ->
            parent.addView(aiChatBubble(role, message), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        }
        if (aiBusy) {
            val progress = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                addView(ProgressBar(this@MainActivity), LinearLayout.LayoutParams(dp(22), dp(22)))
                addView(textView("BixSigma is replying…", 13, textMuted).apply { setPadding(dp(10), 0, 0, 0) })
            }
            parent.addView(progress)
        }
        aiStatusMessage?.takeIf { it.isNotBlank() }?.let { parent.addView(infoCard(it)) }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val input = EditText(this).apply {
            tag = "bixsigma_chat_input"; hint = "Type your question…"
            setTextColor(textLight); setHintTextColor(textMuted); textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND; minLines = 1; maxLines = 3
            setPadding(dp(14), dp(10), dp(14), dp(10)); background = cardDrawable(false)
            isFocusable = true; isFocusableInTouchMode = true; isEnabled = !aiBusy
            setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEND) { sendAiMessage(this); true } else false }
        }
        activeAiInput = input
        row.addView(input, LinearLayout.LayoutParams(0, dp(60), 1f).apply { rightMargin = dp(8) })
        val mic = Button(this).apply {
            text = if (speechListening) "Stop mic" else "Mic"
            isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
            background = navDrawable(false, false); isFocusable = true
            setOnClickListener { if (speechListening) stopVoiceInput() else startVoiceInput(input) }
        }
        aiMicButton = mic
        row.addView(mic, LinearLayout.LayoutParams(dp(92), dp(56)).apply { rightMargin = dp(8) })
        val send = Button(this).apply {
            text = "Send  ➤"; isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
            background = navDrawable(true, false); isFocusable = true; isEnabled = !aiBusy
            setOnClickListener { sendAiMessage(input) }
        }
        row.addView(send, LinearLayout.LayoutParams(dp(106), dp(56)))
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(10) })

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(navButton("＋  New chat", false) {
            aiMessages.clear(); aiStatusMessage = null
            prefs.edit().remove(KEY_GEMINI_INTERACTION_ID).remove(KEY_GEMINI_MODEL).apply()
            renderPage(PAGE_AI)
        }, LinearLayout.LayoutParams(-2, dp(46)).apply { rightMargin = dp(9) })
        if (!savedKey.isNullOrBlank()) actions.addView(navButton("Remove API key", false) {
            AlertDialog.Builder(this@MainActivity).setTitle("Remove API key?")
                .setMessage("BixSigma AI will stop replying until a key is added again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove") { _, _ ->
                    keyVault.clear()
                    prefs.edit().remove(KEY_GEMINI_INTERACTION_ID).remove(KEY_GEMINI_MODEL).apply()
                    aiMessages.clear(); renderPage(PAGE_AI)
                }.show()
        }, LinearLayout.LayoutParams(-2, dp(46)))
        parent.addView(actions, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(2) })
    }

    private fun aiChatBubble(role: String, message: String): View {
        val assistant = role == "assistant"
        val card = glassCard(false).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(10)) }
        card.addView(textView(if (assistant) "BIXSIGMA AI" else "YOU", 10, if (assistant) accent else textMuted, true))
        card.addView(textView(message, 15, textLight).apply { setPadding(0, dp(5), 0, dp(6)); setTextIsSelectable(true) })
        if (assistant) card.addView(navButton("◖))  Listen to reply", false) { speakAnswer(message) },
            LinearLayout.LayoutParams(-2, dp(42)))
        return card
    }

    private fun showAiKeyDialog() {
        val input = EditText(this).apply {
            hint = "Paste your API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(keyVault.read().orEmpty())
            setPadding(dp(14), dp(12), dp(14), dp(12)); setTextColor(textLight); setHintTextColor(textMuted)
        }
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(8), dp(22), 0)
            addView(input, LinearLayout.LayoutParams(-1, dp(56)))
            addView(textView("Create or copy your key, then paste it here. It stays on this TV and is not saved in the repository.", 12, textMuted).apply {
                setPadding(0, dp(10), 0, 0)
            })
        }
        AlertDialog.Builder(this).setTitle("Add API key").setView(holder)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save key") { _, _ ->
                val key = input.text?.toString()?.trim().orEmpty()
                if (key.length < 20) toast("Please paste a valid API key.")
                else try {
                    keyVault.save(key)
                    prefs.edit().remove(KEY_GEMINI_INTERACTION_ID).remove(KEY_GEMINI_MODEL).apply()
                    aiMessages.clear(); aiStatusMessage = "API key saved. BixSigma AI is ready."
                    renderPage(PAGE_AI); toast("API key saved on this TV.")
                } catch (error: Exception) { toast(error.message ?: "Could not save the API key.") }
            }.show()
    }

    private fun openApiKeyPortal() {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))) }
        catch (_: Exception) { toast("Open the key provider dashboard in your browser.") }
    }

    private fun requestDefaultAssistant() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val manager = getSystemService(RoleManager::class.java)
                if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                    if (manager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) toast("BixSigma AI is already selected as the assistant.")
                    else startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), REQUEST_ASSISTANT_ROLE)
                    return
                }
            }
            startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
            catch (_: Exception) { toast("This TV does not expose a changeable assistant setting.") }
        }
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(this) { result ->
            textToSpeechReady = result == TextToSpeech.SUCCESS
            if (textToSpeechReady) {
                val lang = textToSpeech?.setLanguage(Locale.getDefault())
                if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED)
                    textToSpeech?.language = Locale.US
            }
        }
    }

    private fun speakAnswer(text: String) {
        if (!textToSpeechReady || textToSpeech == null) { toast("Voice playback is not ready on this TV yet."); return }
        textToSpeech?.stop()
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bixsigma-" + System.currentTimeMillis())
    }

    private fun startVoiceInput(input: EditText) {
        if (aiBusy) { toast("Wait for the current reply to finish."); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingSpeechInput = input
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC_PERMISSION)
            return
        }
        startSpeechRecognition(input)
    }

    private fun startSpeechRecognition(input: EditText) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { toast("Speech recognition is not available on this TV."); return }
        try {
            speechRecognizer?.cancel(); speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    speechListening = true
                    mainHandler.post { aiMicButton?.text = "Stop mic"; toast("Listening… speak now.") }
                }
                override fun onBeginningOfSpeech() { }
                override fun onRmsChanged(rmsdB: Float) { }
                override fun onBufferReceived(buffer: ByteArray?) { }
                override fun onEndOfSpeech() { speechListening = false; mainHandler.post { aiMicButton?.text = "Mic" } }
                override fun onError(error: Int) {
                    speechListening = false
                    mainHandler.post {
                        aiMicButton?.text = "Mic"
                        toast(when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Microphone audio could not be captured."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network is unavailable."
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Press Mic and try again."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                            else -> "Voice input stopped. Please try again."
                        })
                    }
                    try { speechRecognizer?.destroy() } catch (_: Exception) { }
                    speechRecognizer = null
                }
                override fun onResults(results: Bundle?) {
                    speechListening = false
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    mainHandler.post {
                        aiMicButton?.text = "Mic"
                        if (spoken.isNotBlank()) {
                            input.setText(spoken); input.setSelection(input.text.length); input.requestFocus()
                            toast("Transcript ready. Review it, then press Send.")
                        } else toast("No words were recognized. Try again.")
                    }
                    try { speechRecognizer?.destroy() } catch (_: Exception) { }
                    speechRecognizer = null
                }
                override fun onPartialResults(results: Bundle?) {
                    val partial = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!partial.isNullOrBlank()) mainHandler.post { input.setText(partial); input.setSelection(input.text.length) }
                }
                override fun onEvent(eventType: Int, params: Bundle?) { }
            })
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to BixSigma AI")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            }
            speechRecognizer?.startListening(request); speechListening = true; aiMicButton?.text = "Listening…"
        } catch (_: Exception) {
            speechListening = false; aiMicButton?.text = "Mic"
            toast("Could not start the microphone. Check TV microphone permissions.")
        }
    }

    private fun stopVoiceInput() {
        try { speechRecognizer?.stopListening() } catch (_: Exception) { try { speechRecognizer?.cancel() } catch (_: Exception) { } }
        speechListening = false; aiMicButton?.text = "Mic"
    }

    private fun openAiAndListen() {
        renderPage(PAGE_AI)
        mainHandler.postDelayed({ activeAiInput?.let { startVoiceInput(it) } ?: toast("Open BixSigma AI and press Mic.") }, 650L)
    }

    private fun handleLocalAiCommand(prompt: String): Boolean {
        val lower = prompt.trim().lowercase(Locale.getDefault())
        val match = Regex("^(?:please\\s+)?(?:open|launch|start|kholo|khol\\s+do|chalao|chala\\s+do|play)\\s+(.+)$").find(lower) ?: return false
        val target = match.groupValues[1].trim().removeSuffix(" please").removeSuffix(" kar do").removeSuffix(" kardo")
        if (target.isBlank()) return false
        aiMessages.add("user" to prompt)
        val aliases = mapOf(
            "spotify" to listOf("spotify", "com.spotify"),
            "youtube" to listOf("youtube", "com.google.android.youtube"),
            "netflix" to listOf("netflix", "com.netflix"),
            "prime video" to listOf("prime video", "amazon video"),
            "chrome" to listOf("chrome", "com.android.chrome")
        )
        val search = aliases.entries.firstOrNull { target.contains(it.key) }?.value ?: listOf(target.replace(" ", ""))
        val app = getLaunchableApps().firstOrNull { candidate ->
            val label = candidate.label.lowercase(Locale.getDefault())
            val pkg = candidate.packageName.lowercase(Locale.getDefault())
            search.any { label.contains(it) || pkg.contains(it.replace(" ", "")) }
        }
        if (app != null) {
            aiMessages.add("assistant" to "Opening ${app.label}.")
            renderPage(PAGE_AI); launchApp(app.packageName)
        } else {
            val name = target.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            aiMessages.add("assistant" to "I couldn't find $name in the apps installed on this TV.")
            renderPage(PAGE_AI)
        }
        return true
    }

    private fun sendAiMessage(input: EditText) {
        val prompt = input.text?.toString()?.trim().orEmpty()
        if (prompt.isBlank()) { toast("Type or speak a question first."); return }
        if (aiBusy) return
        if (handleLocalAiCommand(prompt)) { input.setText(""); return }
        val key = keyVault.read()
        if (key.isNullOrBlank()) { showAiKeyDialog(); return }

        aiMessages.add("user" to prompt)
        val context = aiMessages.dropLast(1).takeLast(8).joinToString("\n") { (role, line) ->
            (if (role == "assistant") "BixSigma AI: " else "User: ") + line.take(1200)
        }
        val fullPrompt = if (context.isBlank()) prompt else "Conversation context:\n$context\n\nLatest user message: $prompt\nAnswer the latest message directly and naturally."
        aiBusy = true; aiStatusMessage = null; input.setText(""); renderPage(PAGE_AI)
        aiExecutor.execute {
            try {
                val reply = GeminiApi.send(apiKey = key, prompt = fullPrompt, previousInteractionId = null,
                    preferredModel = GeminiApi.MODEL, fallbackPrompt = null)
                mainHandler.post {
                    aiMessages.add("assistant" to reply.text)
                    prefs.edit().remove(KEY_GEMINI_INTERACTION_ID).putString(KEY_GEMINI_MODEL, reply.model).apply()
                    aiBusy = false; aiStatusMessage = null
                    if (currentPage == PAGE_AI) renderPage(PAGE_AI)
                }
            } catch (error: Exception) {
                mainHandler.post {
                    aiBusy = false
                    val detail = (error.cause?.message ?: error.message).orEmpty().lowercase(Locale.getDefault())
                    aiStatusMessage = when {
                        error is java.net.SocketTimeoutException || error.cause is java.net.SocketTimeoutException ->
                            "The reply took too long. Check your connection and try again."
                        error is java.io.IOException ->
                            "The AI service could not be reached. Check the internet and try again."
                        detail.contains("429") || detail.contains("quota") || detail.contains("rate limit") ->
                            "The AI usage limit has been reached. Check your API key's limits and try again later."
                        detail.contains("401") || detail.contains("403") || detail.contains("invalid api key") ->
                            "Your API key was rejected. Check the key and add it again."
                        else -> "BixSigma AI could not reply. Please check your connection and try again."
                    }
                    if (currentPage == PAGE_AI) renderPage(PAGE_AI)
                }
            }
        }
    }

    private fun buildHomeYoutubeRail(parent: LinearLayout) {
        if (!prefs.getBoolean(KEY_YOUTUBE_RECOMMENDATIONS_ENABLED, true)) return

        parent.addView(sectionTitle("Videos picked for you"))
        parent.addView(textView(
            "Fresh uploads from curated Pakistani and Indian Hindi/Urdu channels. Choose a category to browse.",
            13,
            textMuted
        ).apply { setPadding(0, 0, 0, dp(10)) })
        addYoutubeCategoryRail(parent, PAGE_HOME)
        val active = prefs.getString(KEY_YOUTUBE_ACTIVE_TOPIC, null) ?: youtubeInterests().first()
        buildYoutubeVideoRail(parent, active, PAGE_HOME, 6)
        addSettingCard(parent, "▶   Open official YouTube Home", "Use your signed-in YouTube recommendations") {
            openYouTubeHome()
        }
    }

    private fun buildYoutube(parent: LinearLayout) {
        parent.addView(sectionTitle("YouTube channel library"))
        parent.addView(infoCard(
            "No YouTube API key is needed. NEXA reads public uploads from ${YoutubeChannelCatalog.uniqueChannelCount} curated Pakistani and Indian channels across Hindi/Urdu dramas, movies, technology, gaming and vlogs. Your private YouTube Home remains available in the official app."
        ))
        addSettingCard(parent, "▶   Open my official YouTube Home", "See your account-based recommendations and subscriptions") {
            openYouTubeHome()
        }
        addSettingCard(parent, "☷   Choose video categories", "Turn the built-in Hindi/Urdu channel categories on or off") {
            showYouTubeInterestsDialog()
        }

        parent.addView(sectionTitle("Browse categories"))
        addYoutubeCategoryRail(parent, PAGE_YOUTUBE)
        val active = prefs.getString(KEY_YOUTUBE_ACTIVE_TOPIC, null) ?: youtubeInterests().first()
        parent.addView(sectionTitle(active))
        buildYoutubeVideoRail(parent, active, PAGE_YOUTUBE, 8)
    }

    private fun youtubeInterests(): List<String> {
        val stored = prefs.getString(KEY_YOUTUBE_INTERESTS, DEFAULT_YOUTUBE_INTERESTS)
            ?: DEFAULT_YOUTUBE_INTERESTS
        val topics = stored.split("|")
            .map { it.trim() }
            .filter { it in YoutubeChannelCatalog.categoryNames }
            .distinct()
            .take(8)
            .ifEmpty { DEFAULT_YOUTUBE_INTERESTS.split("|") }
        val scores = try {
            JSONObject(prefs.getString(KEY_YOUTUBE_SCORES, "{}") ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
        return topics.withIndex().sortedWith(
            compareByDescending<IndexedValue<String>> { scores.optInt(it.value, 0) }
                .thenBy { it.index }
        ).map { it.value }
    }

    private fun showYouTubeInterestsDialog() {
        val categories = YoutubeChannelCatalog.categoryNames
        val checked = BooleanArray(categories.size) { index -> categories[index] in youtubeInterests() }
        AlertDialog.Builder(this)
            .setTitle("Choose YouTube categories")
            .setMultiChoiceItems(categories.toTypedArray(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val selected = categories.filterIndexed { index, _ -> checked[index] }
                if (selected.isEmpty()) {
                    toast("Keep at least one video category selected")
                } else {
                    prefs.edit()
                        .putString(KEY_YOUTUBE_INTERESTS, selected.joinToString("|"))
                        .putString(KEY_YOUTUBE_ACTIVE_TOPIC, selected.first())
                        .apply()
                    youtubeVideoCache.clear()
                    youtubeErrors.clear()
                    renderPage(if (currentPage == PAGE_YOUTUBE) PAGE_YOUTUBE else PAGE_HOME)
                }
            }
            .show()
    }

    private fun addYoutubeCategoryRail(parent: LinearLayout, targetPage: String) {
        val active = prefs.getString(KEY_YOUTUBE_ACTIVE_TOPIC, null) ?: youtubeInterests().first()
        val rail = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        youtubeInterests().forEach { topic ->
            val button = navButton(topic, topic == active) {
                prefs.edit().putString(KEY_YOUTUBE_ACTIVE_TOPIC, topic).apply()
                renderPage(targetPage)
            }
            rail.addView(button, LinearLayout.LayoutParams(-2, dp(46)).apply {
                rightMargin = dp(9)
            })
        }
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        scroll.addView(rail)
        parent.addView(scroll, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(12) })
    }

    private fun buildYoutubeVideoRail(parent: LinearLayout, topic: String, targetPage: String, maxResults: Int) {
        val channels = YoutubeChannelCatalog.channelsFor(topic)
        if (channels.isEmpty()) {
            parent.addView(infoCard("No built-in channel sources are configured for this category yet."))
            return
        }

        val cached = youtubeVideoCache[topic]
        if (cached != null) {
            if (cached.isEmpty()) {
                parent.addView(infoCard("No recent uploads were returned by these channels. Check the internet connection or refresh."))
            } else {
                addYoutubeVideoCards(parent, cached, topic)
            }
            addSettingCard(parent, "↻   Refresh videos", "Fetch the latest public uploads from ${channels.size} channels") {
                youtubeVideoCache.remove(topic)
                youtubeErrors.remove(topic)
                renderPage(targetPage)
            }
            return
        }

        val error = youtubeErrors[topic]
        if (!error.isNullOrBlank()) {
            parent.addView(infoCard(error))
            addSettingCard(parent, "↻   Try this category again", "Refresh public channel feeds") {
                youtubeErrors.remove(topic)
                renderPage(targetPage)
            }
            return
        }

        parent.addView(textView("Loading latest uploads from ${channels.size} channels…", 14, textMuted).apply {
            setPadding(0, dp(12), 0, dp(18))
        })
        if (youtubeFetchInProgress.add(topic)) {
            youtubeExecutor.execute {
                try {
                    val videos = YoutubeFeed.fetchVideos(channels, maxResults)
                    mainHandler.post {
                        youtubeVideoCache[topic] = videos
                        youtubeErrors.remove(topic)
                        youtubeFetchInProgress.remove(topic)
                        if (currentPage == targetPage) renderPage(targetPage)
                    }
                } catch (error: Exception) {
                    val message = error.message?.takeIf { it.isNotBlank() }
                        ?: "Could not load YouTube uploads. Check the internet connection and try again."
                    mainHandler.post {
                        youtubeErrors[topic] = message
                        youtubeFetchInProgress.remove(topic)
                        if (currentPage == targetPage) renderPage(targetPage)
                    }
                }
            }
        }
    }

    private fun addYoutubeVideoCards(parent: LinearLayout, videos: List<YoutubeVideo>, topic: String) {
        val rail = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        videos.forEach { video ->
            rail.addView(youtubeVideoCard(video, topic), LinearLayout.LayoutParams(dp(258), dp(220)).apply {
                rightMargin = dp(13)
            })
        }
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        scroll.addView(rail)
        parent.addView(scroll, LinearLayout.LayoutParams(-1, dp(232)).apply { bottomMargin = dp(10) })
    }

    private fun youtubeVideoCard(video: YoutubeVideo, topic: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = cardDrawable(false)
            isFocusable = true
            isClickable = true
            contentDescription = "Play ${video.title}"
            setOnFocusChangeListener { view, focused -> view.background = cardDrawable(focused) }
            setOnClickListener {
                recordYoutubePreference(topic)
                openYouTubeUrl("https://www.youtube.com/watch?v=${Uri.encode(video.videoId)}")
            }
        }
        val poster = ImageView(this).apply {
            tag = video.thumbnailUrl
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF16283D.toInt())
            contentDescription = "Video thumbnail: ${video.title}"
        }
        card.addView(poster, LinearLayout.LayoutParams(-1, dp(136)))
        card.addView(textView(video.title, 13, textLight, true).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(3), dp(8), dp(3), dp(2))
        })
        card.addView(textView(video.channelTitle, 10, textMuted).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(3), dp(1), dp(3), 0)
        })
        loadYoutubeThumbnail(video.thumbnailUrl, poster)
        return card
    }

    private fun loadYoutubeThumbnail(imageUrl: String, target: ImageView) {
        if (imageUrl.isBlank()) return
        val cached = synchronized(youtubeThumbnailCache) { youtubeThumbnailCache[imageUrl] }
        if (cached != null) {
            target.setImageBitmap(cached)
            return
        }
        youtubeExecutor.execute {
            val bitmap = try {
                YoutubeFeed.downloadThumbnail(imageUrl)
            } catch (_: Exception) {
                null
            }
            if (bitmap != null) {
                synchronized(youtubeThumbnailCache) { youtubeThumbnailCache[imageUrl] = bitmap }
                mainHandler.post {
                    if (target.tag == imageUrl) {
                        target.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }

    private fun recordYoutubePreference(topic: String) {
        val scores = try {
            JSONObject(prefs.getString(KEY_YOUTUBE_SCORES, "{}") ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
        scores.put(topic, scores.optInt(topic, 0) + 1)
        prefs.edit()
            .putString(KEY_YOUTUBE_SCORES, scores.toString())
            .putString(KEY_YOUTUBE_ACTIVE_TOPIC, topic)
            .apply()
    }

        private fun openYouTubeHome() {
        val packages = listOf("com.google.android.youtube.tv", "com.google.android.youtube")
        for (packageName in packages) {
            try {
                val launch = packageManager.getLeanbackLaunchIntentForPackage(packageName)
                    ?: packageManager.getLaunchIntentForPackage(packageName)
                if (launch != null) {
                    startActivity(launch)
                    return
                }
            } catch (_: Exception) {
                // Try the next YouTube package or web fallback.
            }
        }
        openYouTubeUrl("https://www.youtube.com")
    }

    private fun openYouTubeSearch(query: String) {
        openYouTubeUrl("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
    }

    private fun openYouTubeUrl(url: String) {
        for (packageName in listOf("com.google.android.youtube.tv", "com.google.android.youtube")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(packageName)
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    return
                }
            } catch (_: Exception) {
                // Try the next handler.
            }
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            toast("Install or update the YouTube app to watch videos.")
        }
    }

    private fun wallpaperPresetCard(preset: WallpaperPreset): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = cardDrawable(false)
            isFocusable = true
            isClickable = true
            setOnFocusChangeListener { view, focused -> view.background = cardDrawable(focused) }
            setOnClickListener { setBuiltInWallpaper(preset.id) }
        }
        val preview = FrameLayout(this).apply {
            background = WallpaperPresets.drawable(preset.id)
            addView(textView("✦", 26, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(-1, -1))
        }
        card.addView(preview, LinearLayout.LayoutParams(-1, dp(78)))
        card.addView(textView(preset.title, 14, textLight, true).apply {
            setPadding(dp(3), dp(7), dp(3), 0)
        })
        card.addView(textView(preset.subtitle, 10, textMuted).apply {
            setPadding(dp(3), dp(2), dp(3), 0)
            maxLines = 1
        })
        return card
    }

    private fun setBuiltInWallpaper(id: String) {
        prefs.edit().remove(KEY_WALLPAPER).putString(KEY_BUILTIN_WALLPAPER, id).apply()
        applyWallpaper()
        renderPage(PAGE_CUSTOMIZE)
        toast("${WallpaperPresets.find(id).title} wallpaper applied")
    }

    private fun renderAbout(parent: LinearLayout) {
        parent.addView(infoCard("NEXA Smart TV Launcher\nVersion 1.2.0\nCreated by Mirza Qasim\nFaisalabad, Pakistan\n\nBuilt as an independent Android TV home experience."))
    }

    private fun addSettingCard(parent: LinearLayout, title: String, subtitle: String, action: () -> Unit) {
        val card = glassCard(false).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            isFocusable = true
            isClickable = true
            setOnClickListener { action() }
            setOnFocusChangeListener { v, hasFocus -> v.background = cardDrawable(hasFocus) }
        }
        card.addView(textView(title, 18, textLight, true))
        card.addView(textView(subtitle, 13, textMuted).apply { setPadding(0, dp(5), 0, 0) })
        parent.addView(card, LinearLayout.LayoutParams(-1, dp(82)).apply { bottomMargin = dp(10) })
    }

    private fun actionCard(symbol: String, title: String, subtitle: String, action: () -> Unit): View {
        val card = glassCard(false).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            isFocusable = true
            isClickable = true
            setOnClickListener { action() }
            setOnFocusChangeListener { v, hasFocus -> v.background = cardDrawable(hasFocus) }
        }
        card.addView(textView(symbol, 25, accent, true))
        card.addView(textView(title, 17, textLight, true).apply { setPadding(0, dp(4), 0, dp(3)) })
        card.addView(textView(subtitle, 11, textMuted))
        return card
    }

        private fun appTile(app: LaunchableApp, rowMode: Boolean = false): View {
        val tile = LinearLayout(this).apply {
            orientation = if (rowMode) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10))
            background = cardDrawable(false); isFocusable = true; isClickable = true; isLongClickable = true
            tag = "nexa_app_tile:${app.packageName}"
            contentDescription = if (movingAppPackage == app.packageName)
                "Moving ${app.label}. Use directional buttons and press OK to finish."
            else "${app.label}. Hold OK for app options."
            setOnFocusChangeListener { view, focused ->
                view.background = cardDrawable(focused)
                view.animate().scaleX(if (focused) 1.035f else 1f).scaleY(if (focused) 1.035f else 1f).setDuration(120L).start()
            }
            setOnClickListener { if (movingAppPackage != null) finishAppMove() else launchApp(app.packageName) }
            setOnLongClickListener { if (movingAppPackage == null) showAppOptions(app); true }
            setOnKeyListener { _, code, event ->
                if (movingAppPackage != app.packageName || event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (code) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { if (!rowMode) moveAppByDirection(app, MOVE_LEFT); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { if (!rowMode) moveAppByDirection(app, MOVE_RIGHT); true }
                    KeyEvent.KEYCODE_DPAD_UP -> { moveAppByDirection(app, MOVE_UP); true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { moveAppByDirection(app, MOVE_DOWN); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { finishAppMove(); true }
                    else -> false
                }
            }
        }
        val icon = ImageView(this).apply { setImageDrawable(app.icon); scaleType = ImageView.ScaleType.FIT_CENTER }
        val iconDp = if (rowMode) 54 else 58
        tile.addView(icon, LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)).apply {
            if (rowMode) rightMargin = dp(16) else bottomMargin = dp(9)
        })
        val label = textView(app.label, 14, textLight, true).apply {
            gravity = if (rowMode) Gravity.CENTER_VERTICAL or Gravity.START else Gravity.CENTER
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        if (rowMode) {
            tile.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
            tile.addView(textView("HOLD OK  ·  OPTIONS", 10, textMuted, true).apply { gravity = Gravity.CENTER_VERTICAL })
        } else tile.addView(label, LinearLayout.LayoutParams(-1, -2))
        return tile
    }

    private fun beginAppMove(app: LaunchableApp) {
        originalAppOrderBeforeMove = getLaunchableApps().map { it.packageName }
        movingAppPackage = app.packageName; focusAppAfterRender = app.packageName
        renderPage(PAGE_APPS)
        toast("Move mode: use LEFT, RIGHT, UP and DOWN. Press OK to finish.")
    }

    private fun moveAppByDirection(app: LaunchableApp, direction: Int) {
        val apps = getLaunchableApps().toMutableList()
        val index = apps.indexOfFirst { it.packageName == app.packageName }
        if (index < 0) return
        val cols = if (prefs.getBoolean(KEY_APPS_ROWS, false)) 1 else appGridColumns.coerceAtLeast(1)
        val target = when (direction) {
            MOVE_LEFT -> if (cols > 1 && index % cols > 0) index - 1 else -1
            MOVE_RIGHT -> if (cols > 1 && index % cols < cols - 1 && index + 1 < apps.size) index + 1 else -1
            MOVE_UP -> index - cols
            MOVE_DOWN -> index + cols
            else -> -1
        }
        if (target !in apps.indices) return
        val other = apps[target]; apps[target] = apps[index]; apps[index] = other
        prefs.edit().putString(KEY_APP_ORDER, apps.joinToString("|") { it.packageName }).apply()
        focusAppAfterRender = app.packageName; renderPage(PAGE_APPS)
    }

    private fun finishAppMove() {
        val selected = movingAppPackage
        movingAppPackage = null
        originalAppOrderBeforeMove = null
        focusAppAfterRender = selected
        renderPage(PAGE_APPS)
        toast("App position saved.")
    }

    private fun showAppOptions(app: LaunchableApp) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(18))
            background = cardDrawable(false, true)
        }
        panel.addView(textView(app.label, 22, textLight, true))
        panel.addView(textView("Application options", 12, textMuted).apply { setPadding(0, dp(3), 0, dp(14)) })
        val dialog = AlertDialog.Builder(this).create()
        fun option(title: String, subtitle: String, action: () -> Unit): View {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(15), dp(10), dp(15), dp(10)); background = cardDrawable(false)
                isFocusable = true; isClickable = true
                setOnFocusChangeListener { view, focus -> view.background = cardDrawable(focus) }
                setOnClickListener { dialog.dismiss(); action() }
            }
            item.addView(textView(title, 16, textLight, true))
            item.addView(textView(subtitle, 11, textMuted).apply { setPadding(0, dp(2), 0, 0) })
            panel.addView(item, LinearLayout.LayoutParams(-1, dp(62)).apply { bottomMargin = dp(7) })
            return item
        }
        val first = option("▶   Open app", "Launch this application") { launchApp(app.packageName) }
        option(if (isFavoritePackage(app.packageName)) "★   Remove from Favorites" else "☆   Add to Favorites",
            if (isFavoritePackage(app.packageName)) "Remove this app from Home Favorites" else "Show it in Home Favorites") { toggleFavorite(app) }
        option("↔   Move app", "Use LEFT / RIGHT / UP / DOWN on the remote to place it") { beginAppMove(app) }
        option("▣   Uninstall app", "Open Android's system uninstall confirmation") { requestAppUninstall(app) }
        option("Close", "Return to the application library") { }
        dialog.setView(panel); dialog.setCanceledOnTouchOutside(true)
        dialog.setOnShowListener { first.requestFocus() }
        dialog.show(); dialog.window?.setLayout(dp(500), -2)
    }

    private fun favoritePackages(): MutableList<String> {
        return prefs.getString(KEY_FAVORITE_APPS, "")
            .orEmpty()
            .split("|")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toMutableList()
    }

    private fun isFavoritePackage(packageName: String): Boolean = favoritePackages().contains(packageName)

    private fun toggleFavorite(app: LaunchableApp) {
        val favorites = favoritePackages()
        if (favorites.contains(app.packageName)) {
            favorites.remove(app.packageName)
            toast("${app.label} removed from Favorites")
        } else {
            favorites.add(app.packageName)
            toast("${app.label} added to Favorites")
        }
        prefs.edit().putString(KEY_FAVORITE_APPS, favorites.joinToString("|")).apply()
        renderPage(if (currentPage == PAGE_HOME) PAGE_HOME else PAGE_APPS)
    }

    private fun moveAppInLibrary(app: LaunchableApp, direction: Int) {
        val apps = getLaunchableApps().toMutableList()
        val from = apps.indexOfFirst { it.packageName == app.packageName }
        val to = from + direction
        if (from < 0 || to !in apps.indices) {
            toast("This app is already at that end of the list")
            return
        }
        val moved = apps.removeAt(from)
        apps.add(to, moved)
        prefs.edit().putString(KEY_APP_ORDER, apps.joinToString("|") { it.packageName }).apply()
        renderPage(if (currentPage == PAGE_HOME) PAGE_HOME else PAGE_APPS)
        toast("${app.label} moved")
    }

    private fun showMoveAppDialog(app: LaunchableApp) {
        val apps = getLaunchableApps().toMutableList()
        val currentPosition = apps.indexOfFirst { it.packageName == app.packageName }
        if (currentPosition < 0) return
        val input = EditText(this).apply {
            hint = "Enter new position"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setText((currentPosition + 1).toString())
            setSelection(text.length)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), 0)
            addView(textView("Enter the new position. Position 1 means the first app.", 13, textMuted))
            addView(input, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(10) })
        }
        AlertDialog.Builder(this)
            .setTitle("Move application")
            .setView(holder)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Move") { _, _ ->
                val newPosition = input.text.toString().toIntOrNull()
                if (newPosition == null || newPosition !in 1..apps.size) {
                    toast("Enter a position from 1 to " + apps.size)
                } else {
                    val moved = apps.removeAt(currentPosition)
                    apps.add((newPosition - 1).coerceIn(0, apps.size), moved)
                    prefs.edit().putString(KEY_APP_ORDER, apps.joinToString("|") { it.packageName }).apply()
                    renderPage(if (currentPage == PAGE_HOME) PAGE_HOME else PAGE_APPS)
                    toast("Application position updated")
                }
            }
            .show()
    }

    private fun requestAppUninstall(app: LaunchableApp) {
        try {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
        } catch (_: Exception) {
            toast("Android did not allow this app to be uninstalled. System apps may be protected.")
        }
    }

        private fun getLaunchableApps(): List<LaunchableApp> {
        val found = LinkedHashMap<String, LaunchableApp>()
        val categories = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        categories.forEach { category ->
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            val resolved: List<ResolveInfo> = try {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0)
            } catch (_: Exception) {
                emptyList()
            }
            resolved.forEach { info ->
                val packageName = info.activityInfo?.packageName ?: return@forEach
                if (packageName == packageNameForSelf()) return@forEach
                if (!found.containsKey(packageName)) {
                    try {
                        val label = info.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() } ?: packageName
                        val icon = info.loadIcon(packageManager)
                        found[packageName] = LaunchableApp(packageName, label, icon)
                    } catch (_: Exception) {
                        // Skip malformed or disappearing packages safely.
                    }
                }
            }
        }
        val savedOrder = prefs.getString(KEY_APP_ORDER, "").orEmpty().split("|")
        val orderIndex = savedOrder.withIndex().associate { it.value to it.index }
        return found.values.sortedWith(
            compareBy<LaunchableApp> { orderIndex[it.packageName] ?: Int.MAX_VALUE }
                .thenBy { it.label.lowercase(Locale.getDefault()) }
        )
    }

    private fun packageNameForSelf(): String = applicationContext.packageName

    private fun launchApp(packageName: String) {
        try {
            val leanback = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            val launch = leanback ?: packageManager.getLaunchIntentForPackage(packageName)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launch)
            } else {
                toast("This app does not expose a launch activity.")
            }
        } catch (_: Exception) {
            toast("Could not open this application.")
        }
    }

    private fun openWallpaperPicker() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            startActivityForResult(intent, pickerRequest)
        } catch (_: Exception) {
            toast("This device does not provide a compatible image picker.")
        }
    }

    @Deprecated("Legacy result API retained for Android 5.0+ compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ASSISTANT_ROLE) {
            toast(if (resultCode == Activity.RESULT_OK) "BixSigma AI is selected as the assistant where supported."
                else "Assistant selection was not changed.")
            if (currentPage == PAGE_AI) renderPage(PAGE_AI)
            return
        }
        if (requestCode == pickerRequest && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val readPermissionGranted =
                    (data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
                if (readPermissionGranted) {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            } catch (_: Exception) {
                // Some providers return transient permissions only; still try to apply for this session.
            }
            prefs.edit().putString(KEY_WALLPAPER, uri.toString()).remove(KEY_BUILTIN_WALLPAPER).apply()
            applyWallpaper()
            renderPage(PAGE_CUSTOMIZE)
            toast("Wallpaper updated")
        }
    }

    private fun applyWallpaper() {
        if (!::wallpaperView.isInitialized) return
        wallpaperView.scaleType = when (prefs.getString(KEY_WALLPAPER_SCALE, "fill")) {
            "fit" -> ImageView.ScaleType.FIT_CENTER
            "center" -> ImageView.ScaleType.CENTER
            else -> ImageView.ScaleType.CENTER_CROP
        }
        val uriText = prefs.getString(KEY_WALLPAPER, null)
        if (uriText.isNullOrBlank()) {
            val presetId = prefs.getString(KEY_BUILTIN_WALLPAPER, "aurora")
            wallpaperView.setImageDrawable(WallpaperPresets.drawable(presetId))
            wallpaperView.setBackgroundColor(bgDark)
        } else {
            try {
                wallpaperView.setImageURI(Uri.parse(uriText))
            } catch (_: Exception) {
                wallpaperView.setImageDrawable(WallpaperPresets.drawable("aurora"))
                wallpaperView.setBackgroundColor(bgDark)
                toast("Saved wallpaper could not be opened. Choose another image.")
            }
        }
        val theme = prefs.getString(KEY_THEME, THEME_GLASS)
        val defaultDim = when (theme) {
            THEME_LIGHT -> 10
            THEME_DARK -> 72
            else -> 48
        }
        val dimPercent = prefs.getInt(KEY_WALLPAPER_DIM, defaultDim).coerceIn(0, 85)
        val overlay = when (theme) {
            THEME_LIGHT -> Color.WHITE
            THEME_DARK -> Color.BLACK
            else -> Color.rgb(7, 17, 32)
        }
        dimLayer.setBackgroundColor(
            Color.argb(
                (dimPercent * 255) / 100,
                Color.red(overlay),
                Color.green(overlay),
                Color.blue(overlay)
            )
        )
    }

    private fun wallpaperScaleName(): String = when (prefs.getString(KEY_WALLPAPER_SCALE, "fill")) {
        "fit" -> "Fit full image"
        "center" -> "Center"
        else -> "Fill screen"
    }

    private fun showWallpaperScaleDialog() {
        val choices = arrayOf("Fill screen (crop edges)", "Fit full image", "Center at original scale")
        val current = when (prefs.getString(KEY_WALLPAPER_SCALE, "fill")) {
            "fit" -> 1
            "center" -> 2
            else -> 0
        }
        AlertDialog.Builder(this)
            .setTitle("Wallpaper framing")
            .setSingleChoiceItems(choices, current) { dialog, which ->
                val value = when (which) {
                    1 -> "fit"
                    2 -> "center"
                    else -> "fill"
                }
                prefs.edit().putString(KEY_WALLPAPER_SCALE, value).apply()
                dialog.dismiss()
                applyWallpaper()
                renderPage(PAGE_CUSTOMIZE)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showWallpaperDimDialog() {
        val label = textView("Wallpaper overlay: ${prefs.getInt(KEY_WALLPAPER_DIM, 48)}%", 14, textLight, true)
        val slider = SeekBar(this).apply {
            max = 85
            progress = prefs.getInt(KEY_WALLPAPER_DIM, 48).coerceIn(0, 85)
        }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                label.text = "Wallpaper overlay: $progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(12))
            addView(label)
            addView(slider, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(8) })
            addView(textView("Lower values keep the image vivid. Higher values make text and cards easier to read.", 12, textMuted))
        }
        AlertDialog.Builder(this)
            .setTitle("Wallpaper brightness")
            .setView(panel)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply") { _, _ ->
                prefs.edit().putInt(KEY_WALLPAPER_DIM, slider.progress).apply()
                applyWallpaper()
                renderPage(PAGE_CUSTOMIZE)
            }
            .show()
    }

        private fun cycleTheme() {
        val themes = listOf(THEME_GLASS, THEME_DARK, THEME_LIGHT)
        val current = prefs.getString(KEY_THEME, THEME_GLASS) ?: THEME_GLASS
        val next = themes[(themes.indexOf(current).coerceAtLeast(0) + 1) % themes.size]
        prefs.edit().putString(KEY_THEME, next).apply()
        applyWallpaper()
        renderPage(PAGE_CUSTOMIZE)
    }

    private fun cycleAccent() {
        val next = (prefs.getInt(KEY_ACCENT, 0) + 1) % 4
        prefs.edit().putInt(KEY_ACCENT, next).apply()
        renderPage(PAGE_CUSTOMIZE)
    }

    private fun accentName(): String = when (prefs.getInt(KEY_ACCENT, 0)) {
        1 -> "Violet"
        2 -> "Teal"
        3 -> "Amber"
        else -> "Ice Blue"
    }

    private fun themeName(): String = when (prefs.getString(KEY_THEME, THEME_GLASS)) {
        THEME_DARK -> "Dark"
        THEME_LIGHT -> "Light"
        else -> "Glass"
    }

    private fun sectionTitle(title: 
String): TextView = textView(title, 21, textLight, true).apply {
        setPadding(0, dp(7), 0, dp(12))
    }

    private fun infoCard(message: String): View = TextView(this).apply {
        text = message
        textSize = 15f
        setTextColor(textMuted)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(20), dp(18), dp(20), dp(18))
        background = cardDrawable(false)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
    }

    private fun navButton(label: String, selected: Boolean, action: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (selected) Color.WHITE else textMuted)
            gravity = Gravity.CENTER
            setPadding(dp(19), 0, dp(19), 0)
            background = navDrawable(selected, false)
            isFocusable = true
            isClickable = true
            setOnFocusChangeListener { v, focus ->
                v.background = navDrawable(selected || focus, focus)
                setTextColor(if (selected || focus) Color.WHITE else textMuted)
            }
            setOnClickListener { action() }
        }
    }

    private fun glassCard(large: Boolean): LinearLayout = LinearLayout(this).apply {
        background = cardDrawable(false, large)
        clipToPadding = false
    }

    private fun cardDrawable(focused: Boolean, large: Boolean = false): GradientDrawable {
        val tint = when (prefsOrDefaultTheme()) {
            THEME_LIGHT -> if (focused) 0xFFF3F7FF.toInt() else 0xDDEAF0FA.toInt()
            THEME_DARK -> if (focused) 0xFF26364E.toInt() else 0xBB18263A.toInt()
            else -> if (focused) 0xCC44658A.toInt() else 0x8F233650.toInt()
        }
        val strokeColor = if (focused) accentColor() else 0x557F9BBD.toInt()
        return GradientDrawable().apply {
            cornerRadius = dp(if (large) 26 else 20).toFloat()
            setColor(tint)
            setStroke(dp(if (focused) 2 else 1), strokeColor)
        }
    }

    private fun navDrawable(active: Boolean, focused: Boolean): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(22).toFloat()
        setColor(if (active) 0xAA345579.toInt() else if (focused) 0xAA3A5879.toInt() else 0x66304460)
        setStroke(dp(if (focused) 2 else 1), if (focused) accentColor() else 0x557F9BBD.toInt())
    }

    private fun accentColor(): Int = when (prefsOrDefaultAccent()) {
        1 -> Color.rgb(181, 139, 255)
        2 -> Color.rgb(91, 220, 198)
        3 -> Color.rgb(255, 190, 105)
        else -> accent
    }

    private fun prefsOrDefaultTheme() = if (::prefs.isInitialized) prefs.getString(KEY_THEME, THEME_GLASS) ?: THEME_GLASS else THEME_GLASS
    private fun prefsOrDefaultAccent() = if (::prefs.isInitialized) prefs.getInt(KEY_ACCENT, 0) else 0

    private fun textView(text: String, size: Int, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        val resolvedColor = when {
            prefsOrDefaultTheme() == THEME_LIGHT && color == textLight -> Color.rgb(22, 35, 53)
            prefsOrDefaultTheme() == THEME_LIGHT && color == textMuted -> Color.rgb(75, 91, 113)
            color == accent -> accentColor()
            else -> color
        }
        setTextColor(resolvedColor)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        includeFontPadding = true
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        internal const val EXTRA_START_AI_VOICE = "com.qasim.nexa.launcher.START_AI_VOICE"
        private const val REQUEST_MIC_PERMISSION = 7201
        private const val REQUEST_ASSISTANT_ROLE = 7202
        private const val MOVE_LEFT = -1
        private const val MOVE_RIGHT = 1
        private const val MOVE_UP = -2
        private const val MOVE_DOWN = 2
        private const val PAGE_HOME = "home"
        private const val PAGE_APPS = "apps"
        private const val PAGE_CUSTOMIZE = "customize"
        private const val PAGE_AI = "ai"
        private const val PAGE_YOUTUBE = "youtube"
        private const val PAGE_SETTINGS = "settings"
        private const val PAGE_WIDGETS = "widgets"
        private const val KEY_WALLPAPER = "wallpaper_uri"
        private const val KEY_BUILTIN_WALLPAPER = "builtin_wallpaper"
        private const val KEY_GEMINI_INTERACTION_ID = "gemini_interaction_id"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_YOUTUBE_INTERESTS = "youtube_interests"
        private const val KEY_YOUTUBE_ACTIVE_TOPIC = "youtube_active_topic"
        private const val KEY_YOUTUBE_SCORES = "youtube_interest_scores"
        private const val KEY_YOUTUBE_RECOMMENDATIONS_ENABLED = "youtube_recommendations_enabled"
        private const val KEY_FAVORITE_APPS = "favorite_apps"
        private const val KEY_APP_ORDER = "app_manual_order"
        private const val KEY_APPS_ROWS = "apps_rows_layout"
        private const val KEY_WALLPAPER_SCALE = "wallpaper_scale"
        private const val KEY_WALLPAPER_DIM = "wallpaper_dim"
        private const val KEY_CLEAN_HOME = "clean_home_layout"
        private const val KEY_CLOCK_POSITION_DP = "clock_position_dp"
        private const val KEY_CLOCK_POSITION_Y_DP = "clock_position_y_dp"
        private const val KEY_FUTURE_WIDGET = "future_widget_style"
        private const val KEY_FUTURISTIC_POSITION = "futuristic_widget_position"
        private const val KEY_FUTURE_WIDGET_X_DP = "future_widget_x_dp"
        private const val KEY_FUTURE_WIDGET_Y_DP = "future_widget_y_dp"
        private const val WIDGET_OFF = "off"
        private const val WIDGET_GLOBE = "globe"
        private const val WIDGET_REACTOR = "reactor"
        private const val DEFAULT_YOUTUBE_INTERESTS = "Pakistani Dramas|Indian Movies|Indian TV & Dramas|Hindi/Urdu Technology|Hindi/Urdu Gaming|Pakistani Vlogs|Indian Vlogs"
        private const val KEY_THEME = "theme"
        private const val KEY_ACCENT = "accent"
        private const val THEME_GLASS = "glass"
        private const val THEME_DARK = "dark"
        private const val THEME_LIGHT = "light"
    }
}
