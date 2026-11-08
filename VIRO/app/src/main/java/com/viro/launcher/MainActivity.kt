package com.viro.launcher

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.palette.graphics.Palette
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {

    // ---------- state ----------
    private lateinit var sp: SharedPreferences
    private val h = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var bg: ImageView
    private lateinit var dim: View
    private lateinit var content: LinearLayout
    private lateinit var clock: TextClock
    private lateinit var date: TextView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var cover: ImageView
    private lateinit var bigRoot: FrameLayout
    private lateinit var bigArt: ImageView
    private lateinit var bigTitle: TextView
    private lateinit var bigArtist: TextView
    private lateinit var dock: LinearLayout
    private lateinit var wifiTile: TextView
    private lateinit var btTile: TextView
    private lateinit var vol: SeekBar
    private val glassViews = ArrayList<Glass>()
    private val playIcons = ArrayList<ImageView>()
    private val accentTexts = ArrayList<TextView>()
    private val matchDetail = ArrayList<TextView>()
    private var glassBmp: Bitmap? = null
    private var wallBmp: Bitmap? = null
    private var art: Bitmap? = null
    private var accent = Color.WHITE
    private var playing = false
    private var big = false
    private var sw = 1280
    private var sh = 800
    private var ctrl: MediaController? = null
    private var sessionsHooked = false
    private val arLocale: Locale = Locale.forLanguageTag("ar-u-nu-latn")

    private val fonts = listOf(
        "Thin" to "sans-serif-thin", "Light" to "sans-serif-light", "Regular" to "sans-serif",
        "Medium" to "sans-serif-medium", "Condensed" to "sans-serif-condensed", "Serif" to "serif",
        "Mono" to "monospace", "Casual" to "casual", "Cursive" to "cursive"
    )
    private val sizes = listOf(90, 110, 130, 150, 180)
    private val clockColors = listOf(
        "أبيض" to 0xFFFFFFFF, "تلقائي (حسب الخلفية)" to 0L, "ذهبي" to 0xFFFFD66BL,
        "نعناعي" to 0xFF7CF5C8L, "وردي" to 0xFFFF8FB8L, "سماوي" to 0xFF8CCBFFL
    )
    private val teams = listOf(
        "ريال مدريد" to 133738, "برشلونة" to 133739, "مان سيتي" to 133613, "باريس" to 133714
    )
    private val walls = listOf(
        longArrayOf(0xFF1B1464, 0xFF6A11CB, 0xFF2575FC),
        longArrayOf(0xFF0F2027, 0xFF2C5364, 0xFF00C9FF),
        longArrayOf(0xFF42275A, 0xFF734B6D, 0xFFFF6A88),
        longArrayOf(0xFF134E5E, 0xFF1D976C, 0xFFF9D423),
        longArrayOf(0xFF232526, 0xFF414345, 0xFFE96443),
        longArrayOf(0xFF0F0C29, 0xFF302B63, 0xFFFF00CC),
        longArrayOf(0xFF005C97, 0xFF363795, 0xFF00F5A0),
        longArrayOf(0xFF3A1C71, 0xFFD76D77, 0xFFFFAF7B),
        longArrayOf(0xFF000000, 0xFF1A1A2E, 0xFF16A085),
        longArrayOf(0xFF1D2B64, 0xFFF8CDDA, 0xFF8E54E9)
    )

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun match() = FrameLayout.LayoutParams(-1, -1)

    // ---------- lifecycle ----------
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        sp = getSharedPreferences("p", MODE_PRIVATE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(dm)
        sw = maxOf(dm.widthPixels, dm.heightPixels)
        sh = minOf(dm.widthPixels, dm.heightPixels)
        if (!sp.contains("favs")) sp.edit().putString("favs", defaultFavs()).apply()
        build()
        setContentView(root)
        applyWallpaper()
        styleClock()
        applyMeta(null)
        refreshDock()
        loadMatches()
        h.post(ticker)
        glassViews.forEachIndexed { i, g ->
            g.alpha = 0f
            g.translationY = dp(36).toFloat()
            g.animate().alpha(1f).translationY(0f).setStartDelay(120L * i).setDuration(550)
                .setInterpolator(DecelerateInterpolator(2f)).start()
        }
        if (!sp.getBoolean("welcomed", false)) showWelcome()
    }

    override fun onResume() {
        super.onResume()
        hideBars()
        hookSessions()
        refreshTiles()
    }

    override fun onWindowFocusChanged(f: Boolean) {
        super.onWindowFocusChanged(f)
        if (f) hideBars()
    }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        if (big) showBig(false)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (big) showBig(false)
    }

    private fun hideBars() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    private val ticker = object : Runnable {
        override fun run() {
            date.text = SimpleDateFormat("EEEE، d MMMM", arLocale).format(Date())
            h.postDelayed(this, 30000)
        }
    }

    // ---------- glass card ----------
    inner class Glass(c: Context) : FrameLayout(c) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bp = Paint(Paint.FILTER_BITMAP_FLAG)
        private val path = Path()
        private val loc = IntArray(2)
        private val r = dp(26).toFloat()
        init {
            setWillNotDraw(false)
            setPadding(dp(20), dp(16), dp(20), dp(16))
            glassViews.add(this)
        }
        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val hh = height.toFloat()
            path.reset()
            path.addRoundRect(0f, 0f, w, hh, r, r, Path.Direction.CW)
            c.save()
            c.clipPath(path)
            val g = glassBmp
            if (g != null) {
                getLocationOnScreen(loc)
                val src = Rect(
                    (loc[0].toFloat() / sw * g.width).toInt(), (loc[1].toFloat() / sh * g.height).toInt(),
                    ((loc[0] + width).toFloat() / sw * g.width).toInt(), ((loc[1] + height).toFloat() / sh * g.height).toInt()
                )
                c.drawBitmap(g, src, RectF(0f, 0f, w, hh), bp)
            }
            p.style = Paint.Style.FILL
            p.shader = LinearGradient(0f, 0f, 0f, hh, Color.argb(70, 255, 255, 255), Color.argb(28, 255, 255, 255), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, hh, p)
            p.shader = null
            c.restore()
            p.style = Paint.Style.STROKE
            p.strokeWidth = dp(1).toFloat()
            p.color = Color.argb(95, 255, 255, 255)
            c.drawRoundRect(0.5f, 0.5f, w - 0.5f, hh - 0.5f, r, r, p)
        }
    }

    private fun card(inner: View): Glass {
        val g = Glass(this)
        g.addView(inner, FrameLayout.LayoutParams(-1, -2))
        return g
    }

    private fun blur(s: Bitmap, r: Int): Bitmap {
        val w = s.width
        val hh = s.height
        var cur = IntArray(w * hh)
        s.getPixels(cur, 0, w, 0, 0, w, hh)
        repeat(3) {
            val tmp = IntArray(w * hh)
            for (y in 0 until hh) for (x in 0 until w) {
                var ra = 0; var ga = 0; var ba = 0
                for (k in -r..r) {
                    val c = cur[y * w + (x + k).coerceIn(0, w - 1)]
                    ra += (c shr 16) and 255; ga += (c shr 8) and 255; ba += c and 255
                }
                val n = 2 * r + 1
                tmp[y * w + x] = Color.rgb(ra / n, ga / n, ba / n)
            }
            val out = IntArray(w * hh)
            for (y in 0 until hh) for (x in 0 until w) {
                var ra = 0; var ga = 0; var ba = 0
                for (k in -r..r) {
                    val c = tmp[(y + k).coerceIn(0, hh - 1) * w + x]
                    ra += (c shr 16) and 255; ga += (c shr 8) and 255; ba += c and 255
                }
                val n = 2 * r + 1
                out[y * w + x] = Color.rgb(ra / n, ga / n, ba / n)
            }
            cur = out
        }
        return Bitmap.createBitmap(cur, w, hh, Bitmap.Config.ARGB_8888)
    }

    private fun makeGlass(src: Bitmap) {
        val gw = 128
        val gh = gw * sh / sw
        val small = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888)
        val c = Canvas(small)
        val sc = maxOf(gw / src.width.toFloat(), gh / src.height.toFloat())
        val dw = src.width * sc
        val dh = src.height * sc
        val pt = Paint(Paint.FILTER_BITMAP_FLAG)
        pt.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.5f) })
        c.drawBitmap(src, null, RectF((gw - dw) / 2, (gh - dh) / 2, (gw + dw) / 2, (gh + dh) / 2), pt)
        glassBmp = blur(small, 5)
        glassViews.forEach { it.invalidate() }
    }

    // ---------- UI build ----------
    private fun tv(text: String, sizeSp: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        return t
    }

    private fun press(v: View) {
        v.setOnTouchListener { x, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> x.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    x.animate().scaleX(1f).scaleY(1f).setDuration(280).setInterpolator(OvershootInterpolator(3f)).start()
            }
            false
        }
    }

    private fun pill(text: String, onClick: () -> Unit): TextView {
        val t = tv(text, 16f, Color.WHITE, true)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(20), dp(11), dp(20), dp(11))
        t.background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(Color.argb(70, 255, 255, 255)) }
        press(t)
        t.setOnClickListener { onClick() }
        return t
    }

    private fun build() {
        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        bg = ImageView(this)
        bg.scaleType = ImageView.ScaleType.CENTER_CROP
        dim = View(this)
        dim.setBackgroundColor(Color.argb(80, 0, 0, 0))
        root.addView(bg, match())
        root.addView(dim, match())

        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(32), dp(22), dp(32), dp(22))
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        content.addView(top, LinearLayout.LayoutParams(-1, 0, 1f))

        // left column: clock + date + matches
        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        clock = TextClock(this)
        clock.includeFontPadding = false
        clock.setOnLongClickListener { showCustom(); true }
        left.addView(clock, LinearLayout.LayoutParams(-2, -2))
        date = tv("", 22f)
        accentTexts.add(date)
        left.addView(date, LinearLayout.LayoutParams(-2, -2))
        left.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        val mInner = LinearLayout(this)
        mInner.orientation = LinearLayout.VERTICAL
        val mh = tv("المباريات القادمة", 15f, Color.WHITE, true)
        accentTexts.add(mh)
        mInner.addView(mh)
        teams.forEachIndexed { i, t ->
            val n = tv(t.first, 15f, Color.WHITE, true)
            n.setPadding(0, dp(8), 0, 0)
            val d = tv(sp.getString("m$i", "...") ?: "...", 13f, Color.argb(200, 255, 255, 255))
            matchDetail.add(d)
            mInner.addView(n)
            mInner.addView(d)
        }
        left.addView(card(mInner), LinearLayout.LayoutParams(-1, -2))
        top.addView(left, LinearLayout.LayoutParams(0, -1, 1.15f))

        // right column: now playing + quick settings
        val right = LinearLayout(this)
        right.orientation = LinearLayout.VERTICAL
        right.gravity = Gravity.BOTTOM
        right.setPadding(dp(24), 0, 0, 0)

        val np = LinearLayout(this)
        np.orientation = LinearLayout.HORIZONTAL
        np.gravity = Gravity.CENTER_VERTICAL
        cover = ImageView(this)
        cover.scaleType = ImageView.ScaleType.CENTER_CROP
        cover.setBackgroundColor(Color.argb(60, 255, 255, 255))
        cover.clipToOutline = true
        cover.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(18).toFloat()) }
        }
        press(cover)
        cover.setOnClickListener { if (art != null) showBig(true) else openSpotify() }
        np.addView(cover, LinearLayout.LayoutParams(dp(130), dp(130)))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(18), 0, 0, 0)
        title = tv("", 20f, Color.WHITE, true)
        title.maxLines = 2
        artist = tv("", 15f, Color.argb(200, 255, 255, 255))
        artist.maxLines = 1
        col.setOnClickListener { if (ctrl == null) openSpotify() }
        col.addView(title)
        col.addView(artist)
        col.addView(controls(46))
        np.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        right.addView(card(np), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })

        val qs = LinearLayout(this)
        qs.orientation = LinearLayout.VERTICAL
        val tiles = LinearLayout(this)
        tiles.orientation = LinearLayout.HORIZONTAL
        wifiTile = tile("📶", "Wi‑Fi", { toggleWifi() }, { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) })
        btTile = tile("🔵", "Bluetooth", { toggleBt() }, { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) })
        val wallTile = tile("🖼", "الخلفيات", { showWallDialog() }, null)
        val styleTile = tile("🎨", "الساعة", { showCustom() }, null)
        val settingsTile = tile("⚙️", "الإعدادات", { startActivity(Intent(Settings.ACTION_SETTINGS)) }, null)
        listOf(wifiTile, btTile, wallTile, styleTile, settingsTile).forEach {
            tiles.addView(it, LinearLayout.LayoutParams(0, dp(74), 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        }
        qs.addView(tiles)
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        vol = SeekBar(this)
        vol.max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        vol.progress = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        vol.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                if (u) am.setStreamVolume(AudioManager.STREAM_MUSIC, p, 0)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        qs.addView(vol, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        right.addView(card(qs), LinearLayout.LayoutParams(-1, -2))
        top.addView(right, LinearLayout.LayoutParams(0, -1, 1f))

        // dock
        dock = LinearLayout(this)
        dock.orientation = LinearLayout.HORIZONTAL
        dock.gravity = Gravity.CENTER
        content.addView(card(dock), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        root.addView(content, match())

        // big player
        bigRoot = FrameLayout(this)
        bigRoot.visibility = View.GONE
        bigArt = ImageView(this)
        bigArt.scaleType = ImageView.ScaleType.CENTER_CROP
        bigArt.setOnClickListener { showBig(false) }
        bigRoot.addView(bigArt, match())
        val shade = View(this)
        shade.background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(Color.argb(220, 0, 0, 0), Color.TRANSPARENT))
        bigRoot.addView(shade, FrameLayout.LayoutParams(-1, dp(260), Gravity.BOTTOM))
        val bl = LinearLayout(this)
        bl.orientation = LinearLayout.VERTICAL
        bl.setPadding(dp(48), 0, dp(48), dp(40))
        bigTitle = tv("", 34f, Color.WHITE, true)
        bigArtist = tv("", 20f, Color.argb(210, 255, 255, 255))
        bl.addView(bigTitle)
        bl.addView(bigArtist)
        bl.addView(controls(64))
        bigRoot.addView(bl, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        root.addView(bigRoot, match())
    }

    private fun controls(size: Int): LinearLayout {
        val l = LinearLayout(this)
        l.gravity = Gravity.CENTER_VERTICAL
        fun ib(res: Int, isPlay: Boolean, act: () -> Unit) {
            val v = ImageView(this)
            v.setImageResource(res)
            v.setPadding(dp(size / 6), dp(size / 6), dp(size / 6), dp(size / 6))
            v.setColorFilter(Color.WHITE)
            press(v)
            v.setOnClickListener { act() }
            if (isPlay) playIcons.add(v)
            l.addView(v, LinearLayout.LayoutParams(dp(size), dp(size)).apply { setMargins(0, dp(6), dp(10), 0) })
        }
        ib(R.drawable.ic_prev, false) { ctrl?.transportControls?.skipToPrevious() }
        ib(R.drawable.ic_play, true) { togglePlay() }
        ib(R.drawable.ic_next, false) { ctrl?.transportControls?.skipToNext() }
        return l
    }

    private fun tile(emoji: String, label: String, onClick: () -> Unit, onLong: (() -> Unit)?): TextView {
        val t = tv("$emoji\n$label", 13f)
        t.gravity = Gravity.CENTER
        t.background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.argb(40, 255, 255, 255)) }
        press(t)
        t.setOnClickListener { onClick() }
        if (onLong != null) t.setOnLongClickListener { onLong(); true }
        return t
    }

    private fun setTile(t: TextView, on: Boolean) {
        (t.background as GradientDrawable).setColor(if (on) Color.argb(120, Color.red(accent), Color.green(accent), Color.blue(accent)) else Color.argb(40, 255, 255, 255))
    }

    // ---------- quick settings ----------
    private fun toggleWifi() {
        try {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wm.isWifiEnabled = !wm.isWifiEnabled
        } catch (e: Exception) { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        h.postDelayed({ refreshTiles() }, 1500)
    }

    private fun toggleBt() {
        try {
            val a = BluetoothAdapter.getDefaultAdapter()
            @Suppress("DEPRECATION")
            if (a.isEnabled) a.disable() else a.enable()
        } catch (e: Exception) { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        h.postDelayed({ refreshTiles() }, 1500)
    }

    private fun refreshTiles() {
        try {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            setTile(wifiTile, wm.isWifiEnabled)
            setTile(btTile, BluetoothAdapter.getDefaultAdapter()?.isEnabled == true)
        } catch (e: Exception) { }
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        vol.progress = am.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    // ---------- wallpaper / accent ----------
    private fun makeWall(i: Int): Bitmap {
        val c3 = walls[i % walls.size]
        val w = 640
        val hh = 400
        val bmp = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), hh.toFloat(), c3[0].toInt(), c3[1].toInt(), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), hh.toFloat(), p)
        val a = c3[2].toInt() and 0xFFFFFF
        p.shader = RadialGradient(w * 0.82f, hh * 0.12f, w * 0.55f, a or 0xB0000000.toInt(), a, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), hh.toFloat(), p)
        val b2 = c3[1].toInt() and 0xFFFFFF
        p.shader = RadialGradient(w * 0.12f, hh * 0.95f, w * 0.5f, b2 or 0x90000000.toInt(), b2, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), hh.toFloat(), p)
        return bmp
    }

    private fun decodeUri(u: Uri): Bitmap? = try {
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (o.outWidth / s > 1600) s *= 2
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = s
        contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o2) }
    } catch (e: Exception) { null }

    private fun applyWallpaper() {
        val idx = sp.getInt("wall", 0)
        var b: Bitmap? = null
        if (idx == -1) sp.getString("uri", null)?.let { b = decodeUri(Uri.parse(it)) }
        wallBmp = b ?: makeWall(if (idx < 0) 0 else idx)
        if (art == null || !sp.getBoolean("coverBg", true)) showBackground(wallBmp!!)
    }

    private fun showBackground(b: Bitmap) {
        bg.setImageBitmap(b)
        makeGlass(b)
        setAccentFrom(b)
    }

    private fun setAccentFrom(b: Bitmap) {
        val pal = Palette.from(Bitmap.createScaledBitmap(b, 64, 64, true)).generate()
        var c = pal.getVibrantColor(pal.getLightVibrantColor(pal.getDominantColor(Color.WHITE)))
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[1] = minOf(hsv[1], 0.6f)
        hsv[2] = maxOf(hsv[2], 0.92f)
        c = Color.HSVToColor(hsv)
        accent = c
        accentTexts.forEach { it.setTextColor(c) }
        playIcons.forEach { it.setColorFilter(c) }
        vol.progressTintList = ColorStateList.valueOf(c)
        vol.thumbTintList = ColorStateList.valueOf(c)
        styleClock()
        refreshTiles()
    }

    private fun styleClock() {
        clock.typeface = Typeface.create(fonts[sp.getInt("font", 0)].second, Typeface.NORMAL)
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizes[sp.getInt("size", 2)].toFloat())
        val ci = sp.getInt("color", 0)
        clock.setTextColor(if (ci == 1) accent else clockColors[ci].second.toInt())
        val f = if (sp.getBoolean("h24", true)) "HH:mm" else "h:mm"
        clock.format24Hour = f
        clock.format12Hour = f
    }

    // ---------- dialogs ----------
    private fun showCustom() {
        val items = arrayOf(
            "الخط", "الحجم", "اللون", "نظام الساعة: " + if (sp.getBoolean("h24", true)) "24" else "12",
            "غلاف الأغنية كخلفية: " + if (sp.getBoolean("coverBg", true)) "نعم" else "لا", "الخلفيات"
        )
        AlertDialog.Builder(this).setTitle("تخصيص").setItems(items) { _, i ->
            when (i) {
                0 -> choose("الخط", fonts.map { it.first }, "font")
                1 -> choose("الحجم", sizes.map { "$it" }, "size")
                2 -> choose("اللون", clockColors.map { it.first }, "color")
                3 -> { sp.edit().putBoolean("h24", !sp.getBoolean("h24", true)).apply(); styleClock() }
                4 -> {
                    sp.edit().putBoolean("coverBg", !sp.getBoolean("coverBg", true)).apply()
                    val a = art
                    if (a != null && sp.getBoolean("coverBg", true)) showBackground(a) else wallBmp?.let { showBackground(it) }
                }
                5 -> showWallDialog()
            }
        }.show()
    }

    private fun choose(t: String, names: List<String>, key: String) {
        AlertDialog.Builder(this).setTitle(t).setItems(names.toTypedArray()) { _, i ->
            sp.edit().putInt(key, i).apply()
            styleClock()
        }.show()
    }

    private fun swatchRow(onPick: (Int) -> Unit): HorizontalScrollView {
        val hs = HorizontalScrollView(this)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(8), dp(10), dp(8), dp(10))
        walls.forEachIndexed { i, w ->
            val v = View(this)
            v.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(w[0].toInt(), w[1].toInt(), w[2].toInt())).apply { shape = GradientDrawable.OVAL }
            press(v)
            v.setOnClickListener { onPick(i) }
            row.addView(v, LinearLayout.LayoutParams(dp(52), dp(52)).apply { setMargins(dp(6), 0, dp(6), 0) })
        }
        val plus = tv("＋", 26f)
        plus.gravity = Gravity.CENTER
        plus.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(70, 255, 255, 255)) }
        plus.setOnClickListener { onPick(-1) }
        row.addView(plus, LinearLayout.LayoutParams(dp(52), dp(52)).apply { setMargins(dp(6), 0, dp(6), 0) })
        hs.addView(row)
        return hs
    }

    private fun pickWall(i: Int) {
        if (i == -1) {
            val it = Intent(Intent.ACTION_OPEN_DOCUMENT)
            it.addCategory(Intent.CATEGORY_OPENABLE)
            it.type = "image/*"
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            startActivityForResult(it, 7)
        } else {
            sp.edit().putInt("wall", i).apply()
            sp.edit().putBoolean("coverBg", false).apply()
            applyWallpaper()
            showBackground(wallBmp!!)
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(rq: Int, rs: Int, d: Intent?) {
        super.onActivityResult(rq, rs, d)
        val u = d?.data
        if (rq == 7 && rs == RESULT_OK && u != null) {
            try { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
            sp.edit().putString("uri", u.toString()).putInt("wall", -1).putBoolean("coverBg", false).apply()
            applyWallpaper()
            showBackground(wallBmp!!)
        }
    }

    private fun showWallDialog() {
        var dlg: AlertDialog? = null
        dlg = AlertDialog.Builder(this).setTitle("اختر خلفية").setView(swatchRow { dlg?.dismiss(); pickWall(it) }).create()
        dlg?.show()
    }

    private fun showWelcome() {
        val ov = FrameLayout(this)
        ov.setBackgroundColor(Color.argb(150, 0, 0, 0))
        ov.isClickable = true
        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.VERTICAL
        inner.gravity = Gravity.CENTER_HORIZONTAL
        inner.addView(tv("أهلاً بك في VIRO", 32f, Color.WHITE, true))
        inner.addView(tv("جهّز شاشتك بثلاث خطوات سريعة", 15f, Color.argb(200, 255, 255, 255)))
        val s1 = tv("١) اختر خلفيتك", 16f, Color.WHITE, true)
        s1.setPadding(0, dp(16), 0, 0)
        inner.addView(s1)
        inner.addView(swatchRow { pickWall(it) })
        val s2 = pill("٢) فعّل ربط Spotify (الوصول للإشعارات)") {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
        inner.addView(s2, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(14) })
        val s3 = pill("٣) اجعل VIRO الشاشة الرئيسية") {
            try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (e: Exception) { }
        }
        inner.addView(s3, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) })
        val go = pill("ابدأ") {
            sp.edit().putBoolean("welcomed", true).apply()
            root.removeView(ov)
        }
        inner.addView(go, LinearLayout.LayoutParams(dp(200), -2).apply { topMargin = dp(18) })
        val gl = card(inner)
        ov.addView(gl, FrameLayout.LayoutParams(dp(560), -2, Gravity.CENTER))
        root.addView(ov, match())
    }

    // ---------- apps dock ----------
    private fun defaultFavs(): String = listOf(
        "com.google.android.youtube", "com.spotify.music", "com.sec.android.app.myfiles",
        "com.google.android.apps.nbu.files", "com.android.settings"
    ).filter { packageManager.getLaunchIntentForPackage(it) != null }.joinToString(",")

    private fun favs(): MutableList<String> =
        (sp.getString("favs", "") ?: "").split(",").filter { it.isNotBlank() }.toMutableList()

    private fun launch(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) startActivity(i) else Toast.makeText(this, "التطبيق غير موجود", Toast.LENGTH_SHORT).show()
    }

    private fun refreshDock() {
        dock.removeAllViews()
        favs().forEach { pkg ->
            try {
                val v = ImageView(this)
                v.setImageDrawable(packageManager.getApplicationIcon(pkg))
                press(v)
                v.setOnClickListener { launch(pkg) }
                v.setOnLongClickListener {
                    val f = favs()
                    f.remove(pkg)
                    sp.edit().putString("favs", f.joinToString(",")).apply()
                    refreshDock()
                    true
                }
                dock.addView(v, LinearLayout.LayoutParams(dp(62), dp(62)).apply { setMargins(dp(14), dp(2), dp(14), dp(2)) })
            } catch (e: Exception) { }
        }
        val all = tv("⊞", 34f)
        all.gravity = Gravity.CENTER
        all.background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.argb(60, 255, 255, 255)) }
        press(all)
        all.setOnClickListener { showAllApps() }
        dock.addView(all, LinearLayout.LayoutParams(dp(62), dp(62)).apply { setMargins(dp(14), dp(2), dp(14), dp(2)) })
    }

    private fun showAllApps() {
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(q, 0)
            .map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
            .filter { it.second != packageName }.sortedBy { it.first.lowercase() }
        val lv = ListView(this)
        lv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, apps.map { it.first })
        val dlg = AlertDialog.Builder(this).setTitle("كل التطبيقات (ضغطة مطوّلة = إضافة للشريط)").setView(lv).create()
        lv.setOnItemClickListener { _, _, i, _ -> dlg.dismiss(); launch(apps[i].second) }
        lv.setOnItemLongClickListener { _, _, i, _ ->
            val f = favs()
            if (!f.contains(apps[i].second)) f.add(apps[i].second)
            sp.edit().putString("favs", f.joinToString(",")).apply()
            refreshDock()
            Toast.makeText(this, "انضاف: " + apps[i].first, Toast.LENGTH_SHORT).show()
            true
        }
        dlg.show()
    }

    // ---------- Spotify / media ----------
    private fun listenerOn(): Boolean =
        Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true

    private fun openSpotify() {
        if (!listenerOn()) startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        else launch("com.spotify.music")
    }

    private fun hint() = if (!listenerOn()) "اضغط لتفعيل ربط Spotify" else "اضغط لفتح Spotify"

    private val cb = object : MediaController.Callback() {
        override fun onMetadataChanged(m: MediaMetadata?) { applyMeta(m) }
        override fun onPlaybackStateChanged(s: PlaybackState?) { applyState(s) }
    }

    private fun hookSessions() {
        try {
            val msm = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val cn = ComponentName(this, NotifService::class.java)
            pickSession(msm.getActiveSessions(cn))
            if (!sessionsHooked) {
                msm.addOnActiveSessionsChangedListener({ l -> pickSession(l) }, cn)
                sessionsHooked = true
            }
        } catch (e: SecurityException) {
            applyMeta(null)
        }
    }

    private fun pickSession(l: List<MediaController>?) {
        val c = l?.firstOrNull { it.packageName == "com.spotify.music" } ?: l?.firstOrNull()
        if (c?.sessionToken == ctrl?.sessionToken) return
        ctrl?.unregisterCallback(cb)
        ctrl = c
        c?.registerCallback(cb)
        applyMeta(c?.metadata)
        applyState(c?.playbackState)
    }

    private fun applyMeta(m: MediaMetadata?) {
        title.text = m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "لا يوجد تشغيل"
        artist.text = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: hint()
        bigTitle.text = title.text
        bigArtist.text = artist.text
        val bmp = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (bmp != null) {
            art = bmp
            cover.setImageBitmap(bmp)
            bigArt.setImageBitmap(bmp)
            if (sp.getBoolean("coverBg", true)) showBackground(bmp)
        }
    }

    private fun applyState(s: PlaybackState?) {
        playing = s?.state == PlaybackState.STATE_PLAYING
        playIcons.forEach { it.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play) }
        if (s == null || s.state == PlaybackState.STATE_STOPPED || s.state == PlaybackState.STATE_NONE) {
            art = null
            cover.setImageDrawable(null)
            if (big) showBig(false)
            wallBmp?.let { showBackground(it) }
        }
    }

    private fun togglePlay() {
        val c = ctrl
        if (c == null) { openSpotify(); return }
        if (playing) c.transportControls.pause() else c.transportControls.play()
    }

    private fun showBig(on: Boolean) {
        big = on
        if (on) {
            bigRoot.alpha = 0f
            bigRoot.visibility = View.VISIBLE
            bigRoot.animate().alpha(1f).setDuration(350).start()
        } else {
            bigRoot.visibility = View.GONE
        }
        content.visibility = if (on) View.INVISIBLE else View.VISIBLE
        dim.visibility = if (on) View.INVISIBLE else View.VISIBLE
    }

    // ---------- matches (TheSportsDB free API) ----------
    private fun loadMatches() {
        Thread {
            val res = teams.map { t ->
                try {
                    val u = URL("https://www.thesportsdb.com/api/v1/json/3/eventsnext.php?id=" + t.second)
                    val con = u.openConnection() as HttpURLConnection
                    con.connectTimeout = 8000
                    con.readTimeout = 8000
                    val j = JSONObject(con.inputStream.bufferedReader().readText())
                    val ev = j.optJSONArray("events")?.optJSONObject(0)
                    if (ev == null) "لا توجد مباراة قريبة" else {
                        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                        f.timeZone = TimeZone.getTimeZone("UTC")
                        val d = f.parse(ev.optString("dateEvent") + " " + ev.optString("strTime"))
                        val out = if (d != null) SimpleDateFormat("EEE d/M  HH:mm", arLocale).format(d) else ev.optString("dateEvent")
                        ev.optString("strHomeTeam") + " × " + ev.optString("strAwayTeam") + "  •  " + out
                    }
                } catch (e: Exception) { null }
            }
            runOnUiThread {
                res.forEachIndexed { i, s ->
                    if (s != null) {
                        matchDetail[i].text = s
                        sp.edit().putString("m$i", s).apply()
                    }
                }
            }
        }.start()
        h.postDelayed({ loadMatches() }, 3 * 3600 * 1000L)
    }
}
