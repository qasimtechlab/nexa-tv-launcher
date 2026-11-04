package com.qasim.nexa.launcher

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("nexa_preferences", MODE_PRIVATE)
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
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
        if (::contentHost.isInitialized) {
            // Refresh app list when the user returns from another application.
            if (currentPage == PAGE_APPS || currentPage == PAGE_HOME) renderPage(currentPage)
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
        if (currentPage != PAGE_HOME) {
            renderPage(PAGE_HOME)
        } else {
            // A Home activity should remain a usable home surface when BACK is pressed.
            super.onBackPressed()
        }
    }

    private fun renderPage(page: String) {
        currentPage = page
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
            PAGE_SETTINGS -> buildSettings(column)
            else -> buildHome(column)
        }

        column.addView(textView("NEXA  •  Created by Mirza Qasim", 12, textMuted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, dp(4))
        }) 
        contentHost.post {
            try {
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
        val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        right.addView(textView(time, 20, textLight, true).apply { gravity = Gravity.END })
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
            Triple("BixSigma AI", PAGE_AI, "◇"),
            Triple("Settings", PAGE_SETTINGS, "⚙")
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
        val hero = glassCard(true).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(30), dp(24), dp(30), dp(24))
        }
        hero.addView(textView("YOUR TV. YOUR WORLD.", 12, accent, true))
        hero.addView(textView("Everything, beautifully\nin one place.", 31, textLight, true).apply {
            setPadding(0, dp(8), 0, dp(8))
        })
        hero.addView(textView("A clean, customizable home for your entertainment.", 15, textMuted))
        parent.addView(hero, LinearLayout.LayoutParams(-1, dp(190)).apply { bottomMargin = dp(22) })

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
            val card = actionCard(item.first, item.second, item.third) { renderPage(destination) }
            actions.addView(card, LinearLayout.LayoutParams(dp(228), dp(132)).apply {
                rightMargin = dp(14)
            })
        }
        val actionScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        actionScroll.addView(actions)
        parent.addView(actionScroll, LinearLayout.LayoutParams(-1, dp(144)).apply { bottomMargin = dp(22) })

        parent.addView(sectionTitle("Your applications"))
        val apps = getLaunchableApps().take(5)
        if (apps.isEmpty()) {
            parent.addView(infoCard("No launchable apps were discovered. Open Apps to refresh, or check Android package visibility."))
        } else {
            val rail = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            apps.forEach { app -> rail.addView(appTile(app), LinearLayout.LayoutParams(dp(172), dp(142)).apply { rightMargin = dp(12) }) }
            val railScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            railScroll.addView(rail)
            parent.addView(railScroll, LinearLayout.LayoutParams(-1, dp(152)))
        }
    }

    private fun buildApps(parent: LinearLayout) {
        parent.addView(sectionTitle("Application library"))
        parent.addView(textView("Select an application with your TV remote to open it.", 14, textMuted).apply {
            setPadding(0, 0, 0, dp(18))
        })
        val apps = getLaunchableApps()
        if (apps.isEmpty()) {
            parent.addView(infoCard("No compatible launchable applications were found on this device."))
            return
        }
        val screenWidthDp = resources.configuration.screenWidthDp
        val columns = when {
            screenWidthDp < 780 -> 2
            screenWidthDp < 1150 -> 3
            else -> 4
        }
        val tileWidth = (screenWidthDp / columns - 24).coerceIn(150, 220)
        val grid = GridLayout(this).apply {
            columnCount = columns
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        apps.forEach { app ->
            val tile = appTile(app)
            val lp = GridLayout.LayoutParams().apply {
                width = dp(tileWidth)
                height = dp(142)
                setMargins(dp(5), dp(5), dp(12), dp(12))
            }
            grid.addView(tile, lp)
        }
        parent.addView(grid, LinearLayout.LayoutParams(-1, -2))
    }
    
    private fun buildCustomize(parent: LinearLayout) {
        parent.addView(sectionTitle("Make NEXA yours"))
        parent.addView(textView("Personal settings are stored on this TV. No account is required.", 14, textMuted).apply {
            setPadding(0, 0, 0, dp(18))
        })
        addSettingCard(parent, "▧   Choose wallpaper", "Use an image saved on your device") { openWallpaperPicker() }
        addSettingCard(parent, "◐   Change appearance", "Current theme: ${themeName()}") { cycleTheme() }
        addSettingCard(parent, "✦   Accent color", "Current accent: ${accentName()}") { cycleAccent() }
        addSettingCard(parent, "↺   Reset personalization", "Restore the original NEXA appearance") {
            prefs.edit().remove(KEY_WALLPAPER).putString(KEY_THEME, THEME_GLASS).putInt(KEY_ACCENT, 0).apply()
            applyWallpaper()
            renderPage(PAGE_CUSTOMIZE)
            toast("Personalization reset")
        }
    }

    private fun buildSettings(parent: LinearLayout) {
        parent.addView(sectionTitle("Launcher settings"))
        parent.addView(infoCard("NEXA is an independent launcher. Your factory launcher and system apps are not modified."))
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
        addSettingCard(parent, "ⓘ   About NEXA", "Version 1.0.0 · Created by Mirza Qasim") {
            renderAbout(parent)
        }
        addSettingCard(parent, "↻   Restart launcher screen", "Reload NEXA without rebooting the TV") {
            renderPage(PAGE_HOME)
        }
    }

    private fun buildAi(parent: LinearLayout) {
        val logo = glassCard(true).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
        }
        logo.addView(textView("◇", 48, accent, true))
        logo.addView(textView("BixSigma AI", 30, textLight, true))
        logo.addView(textView("Your personal AI space", 14, textMuted))
        parent.addView(logo, LinearLayout.LayoutParams(-1, dp(190)).apply { bottomMargin = dp(18) })

        parent.addView(sectionTitle("Assistant identity"))
        parent.addView(infoCard("BixSigma AI is created by Mirza Qasim from Faisalabad, Pakistan. This identity is part of the assistant configuration; it is not model training."))
        parent.addView(sectionTitle("AI activation"))
        parent.addView(infoCard("AI provider is not configured in this first launcher build. No fake replies are generated and no API key is requested or stored."))
        addSettingCard(parent, "Provider integration", "Will be enabled after provider eligibility, terms and secure key handling are verified.") {
            toast("BixSigma AI interface is ready; provider integration is not active yet.")
        }
    }

    private fun renderAbout(parent: LinearLayout) {
        parent.addView(infoCard("NEXA Smart TV Launcher\nVersion 1.0.0\nCreated by Mirza Qasim\nFaisalabad, Pakistan\n\nBuilt as an independent Android TV home experience."))
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

    private fun appTile(app: LaunchableApp): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(12), dp(10), dp(10))
            background = cardDrawable(false)
            isFocusable = true
            isClickable = true
            contentDescription = app.label
            setOnFocusChangeListener { v, hasFocus -> v.background = cardDrawable(hasFocus) }
            setOnClickListener { launchApp(app.packageName) }
        }
        val icon = ImageView(this).apply {
            setImageDrawable(app.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        tile.addView(icon, LinearLayout.LayoutParams(dp(58), dp(58)).apply { bottomMargin = dp(9) })
        tile.addView(textView(app.label, 13, textLight, true).apply {
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        return tile
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
        return found.values.sortedBy { it.label.lowercase(Locale.getDefault()) }
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
        if (requestCode == pickerRequest && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                contentResolver.takePersistableUriPermission(uri, flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
                // Some providers return transient permissions only; still try to apply for this session.
            }
            prefs.edit().putString(KEY_WALLPAPER, uri.toString()).apply()
            applyWallpaper()
            renderPage(PAGE_CUSTOMIZE)
            toast("Wallpaper updated")
        }
    }

    private fun applyWallpaper() {
        if (!::wallpaperView.isInitialized) return
        val uriText = prefs.getString(KEY_WALLPAPER, null)
        if (uriText.isNullOrBlank()) {
            wallpaperView.setImageDrawable(null)
            wallpaperView.setBackgroundColor(bgDark)
        } else {
            try {
                wallpaperView.setImageURI(Uri.parse(uriText))
            } catch (_: Exception) {
                wallpaperView.setImageDrawable(null)
                wallpaperView.setBackgroundColor(bgDark)
                toast("Saved wallpaper could not be opened. Choose another image.")
            }
        }
        val theme = prefs.getString(KEY_THEME, THEME_GLASS)
        dimLayer.setBackgroundColor(
            when (theme) {
                THEME_LIGHT -> 0xBFEAF0F8.toInt()
                THEME_DARK -> 0xD9081020.toInt()
                else -> 0xA90A1425.toInt()
            }
        )
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
        private const val PAGE_HOME = "home"
        private const val PAGE_APPS = "apps"
        private const val PAGE_CUSTOMIZE = "customize"
        private const val PAGE_AI = "ai"
        private const val PAGE_SETTINGS = "settings"
        private const val KEY_WALLPAPER = "wallpaper_uri"
        private const val KEY_THEME = "theme"
        private const val KEY_ACCENT = "accent"
        private const val THEME_GLASS = "glass"
        private const val THEME_DARK = "dark"
        private const val THEME_LIGHT = "light"
    }
}
