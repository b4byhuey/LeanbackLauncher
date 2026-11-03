@file:Suppress("DEPRECATION")

package com.amazon.tv.leanbacklauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.LoaderManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_NO_CREATE
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.Loader
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.location.Geocoder
import android.location.Location
import android.media.tv.TvContract
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.OnHierarchyChangeListener
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.accessibility.AccessibilityManager
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.res.ResourcesCompat
import androidx.core.net.toUri
import androidx.core.text.isDigitsOnly
import androidx.core.view.isNotEmpty
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.OnChildViewHolderSelectedListener
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.amazon.tv.firetv.leanbacklauncher.apps.AppInfoActivity
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences
import com.amazon.tv.firetv.leanbacklauncher.apps.RowType
import com.amazon.tv.firetv.tvrecommendations.NotificationListenerMonitor
import com.amazon.tv.leanbacklauncher.SearchOrbView.SearchLaunchListener
import com.amazon.tv.leanbacklauncher.animation.AnimatorLifecycle
import com.amazon.tv.leanbacklauncher.animation.AnimatorLifecycle.OnAnimationFinishedListener
import com.amazon.tv.leanbacklauncher.animation.EditModeMassFadeAnimator
import com.amazon.tv.leanbacklauncher.animation.EditModeMassFadeAnimator.EditMode
import com.amazon.tv.leanbacklauncher.animation.ForwardingAnimatorSet
import com.amazon.tv.leanbacklauncher.animation.LauncherDismissAnimator
import com.amazon.tv.leanbacklauncher.animation.LauncherLaunchAnimator
import com.amazon.tv.leanbacklauncher.animation.LauncherPauseAnimator
import com.amazon.tv.leanbacklauncher.animation.LauncherReturnAnimator
import com.amazon.tv.leanbacklauncher.animation.MassSlideAnimator
import com.amazon.tv.leanbacklauncher.animation.NotificationLaunchAnimator
import com.amazon.tv.leanbacklauncher.animation.ParticipatesInLaunchAnimation
import com.amazon.tv.leanbacklauncher.apps.AppsManager.Companion.getInstance
import com.amazon.tv.leanbacklauncher.apps.BannerView
import com.amazon.tv.leanbacklauncher.apps.OnEditModeChangedListener
import com.amazon.tv.leanbacklauncher.clock.ClockView
import com.amazon.tv.leanbacklauncher.logging.LeanbackLauncherEventLogger
import com.amazon.tv.leanbacklauncher.notifications.HomeScreenView
import com.amazon.tv.leanbacklauncher.notifications.NotificationCardView
import com.amazon.tv.leanbacklauncher.notifications.NotificationRowView
import com.amazon.tv.leanbacklauncher.notifications.NotificationRowView.NotificationRowListener
import com.amazon.tv.leanbacklauncher.notifications.NotificationsAdapter
import com.amazon.tv.leanbacklauncher.settings.LegacyHomeScreenSettingsActivity
import com.amazon.tv.leanbacklauncher.settings.SettingsActivity
import com.amazon.tv.leanbacklauncher.util.OpenWeatherIcons
import com.amazon.tv.leanbacklauncher.util.Partner
import com.amazon.tv.leanbacklauncher.util.TvSearchIconLoader
import com.amazon.tv.leanbacklauncher.util.TvSearchSuggestionsLoader
import com.amazon.tv.leanbacklauncher.util.Util
import com.amazon.tv.leanbacklauncher.wallpaper.LauncherWallpaper
import com.amazon.tv.leanbacklauncher.wallpaper.WallpaperInstaller
import com.amazon.tv.leanbacklauncher.widget.EditModeView
import com.amazon.tv.leanbacklauncher.widget.EditModeView.OnEditModeUninstallPressedListener
import de.interaapps.localweather.LocalWeather
import de.interaapps.localweather.utils.LocationFailedEnum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileDescriptor
import java.io.PrintWriter
import java.lang.String.format
import java.lang.ref.WeakReference
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt


@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity(), OnEditModeChangedListener,
    OnEditModeUninstallPressedListener {

    companion object {
        private const val TAG = "MainActivity"
        private const val FIRST_POSITION = 0
        private const val WHEEL_NAV_INTERVAL_MS = 120L
        private const val PREF_OPEN_METEO_JSON = "open_meteo_weather_json"
        private const val PREF_OPEN_METEO_TIME = "open_meteo_weather_time"
        private const val PREF_OPEN_METEO_CONFIG = "open_meteo_weather_config"
        private const val DEFAULT_LAUNCHER_PROMPT_DELAY_MS = 3_000L
        private const val UNINSTALL_CODE = 321
        const val PERMISSIONS_REQUEST_LOCATION = 99
        val JSONFILE = LauncherApp.context.cacheDir?.absolutePath + "/weather.json"

        fun isMediaKey(keyCode: Int): Boolean {
            return when (keyCode) {
                KeyEvent.KEYCODE_HEADSETHOOK,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_STOP,
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_REWIND,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                KeyEvent.KEYCODE_MUTE,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_RECORD -> true

                else -> false
            }
        }

        private fun getBoundsOnScreen(v: View, epicenter: Rect) {
            val location = IntArray(2)
            v.getLocationOnScreen(location)
            epicenter.left = location[0]
            epicenter.top = location[1]
            epicenter.right = epicenter.left + (v.width.toFloat() * v.scaleX).roundToInt()
            epicenter.bottom = epicenter.top + (v.height.toFloat() * v.scaleY).roundToInt()
        }
    }


    // Weather
    private var localWeather: LocalWeather? = null

    // Weather constants
    private val maxCacheAge = TimeUnit.MINUTES.toMillis(30) // 30 minutes

    // Core components
    private val mHandler: Handler = MainActivityMessageHandler(this)

    // Dialog "jadikan default launcher": ditampilkan 30 detik setelah launcher terbuka.
    // Handler terpisah karena onStop() memanggil mHandler.removeCallbacksAndMessages(null).
    private val defaultLauncherHandler = Handler(Looper.getMainLooper())
    private val defaultLauncherPrompt = Runnable { showDefaultLauncherDialog() }
    private val mIdleListeners = mutableListOf<IdleListener>()
    private val mNotifListener = NotificationListenerImpl()
    private val mPackageReplacedReceiver = PackageReplacedReceiver()
    private val mHomeRefreshReceiver = HomeRefreshReceiver()

    // Views
    var editModeView: EditModeView? = null
        private set
    var wallpaperView: LauncherWallpaper? = null
        private set
    private var mListView: VerticalGridView? = null
    private var mHomeScreenView: HomeScreenView? = null
    private var mNotificationsView: NotificationRowView? = null
    private var mAppWidgetHostView: AppWidgetHostView? = null

    // Adapters
    var homeAdapter: HomeScreenAdapter? = null
        private set
    private var mRecommendationsAdapter: NotificationsAdapter? = null

    // Services
    private var mAppWidgetHost: AppWidgetHost? = null
    private var mAppWidgetManager: AppWidgetManager? = null
    private var mContentResolver: ContentResolver? = null
    private var mEventLogger: LeanbackLauncherEventLogger? = null
    private var mAccessibilityManager: AccessibilityManager? = null
    private var mScrollManager: HomeScrollManager? = null

    // State variables
    var isInEditMode = false
        private set
    private var mDelayFirstRecommendationsVisible = true
    private var mFadeDismissAndSummonAnimations = false
    private var mIsIdle = false
    private var mKeepUiReset = false
    private var mUserInteracted = false
    private var mShyMode = false
    private var mStartingEditMode = false
    private var mUninstallRequested = false
    private var mResetAfterIdleEnabled = false

    private val mIdlePeriod: Int by lazy {
        resources.getInteger(R.integer.idle_period)
    }
    private val mResetPeriod: Int by lazy {
        resources.getInteger(R.integer.reset_period)
    }

    // Animations
    private val mEditModeAnimation = AnimatorLifecycle()
    private val mLaunchAnimation = AnimatorLifecycle()
    private val mPauseAnimation = AnimatorLifecycle()
    private val mMoveTaskToBack = Runnable {
        if (!moveTaskToBack(true)) {
            mLaunchAnimation.reset()
        }
    }

    private val mRefreshHomeAdapter = Runnable {
        homeAdapter?.refreshAdapterData()
    }

    interface IdleListener {
        fun onIdleStateChange(z: Boolean)
        fun onVisibilityChange(z: Boolean)
    }

    // Inner classes for better organization
    private inner class NotificationListenerImpl : NotificationRowListener {
        private var mHandler: Handler? = null
        private val mSelectFirstRecommendationRunnable = Runnable {
            mNotificationsView?.takeIf { (it.adapter?.itemCount ?: 0) > 0 }
                ?.setSelectedPositionSmooth(FIRST_POSITION)
        }

        override fun onBackgroundImageChanged(imageUri: String?, signature: String?) {
            wallpaperView?.onBackgroundImageChanged(imageUri, signature)
        }

        override fun onSelectedRecommendationChanged(position: Int) {
            if (mKeepUiReset && mAccessibilityManager?.isEnabled != true && position > FIRST_POSITION) {
                mHandler = mHandler ?: Handler(Looper.getMainLooper())
                mHandler?.post(mSelectFirstRecommendationRunnable)
            }
        }
    }

    private inner class PackageReplacedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.data?.toString()?.takeIf {
                it.contains("${context?.packageName}.recommendations")
            }?.let {
                Log.d(TAG, "Recommendations Service updated, reconnecting.")
                homeAdapter?.onReconnectToRecommendationsService()
            }
        }
    }

    private inner class HomeRefreshReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getBooleanExtra("RefreshHome", false) == true) {
                Log.d(TAG, "RESTART HOME")
                recreate()
            }
        }
    }

    private class MainActivityMessageHandler(activity: MainActivity) : Handler(Looper.getMainLooper()) {
        private val activityRef = WeakReference(activity)
        override fun handleMessage(msg: Message) {
            activityRef.get()?.let { activity ->
                var idle = true
                when (msg.what) {
                    1, 2 -> {
                        val mainActivity = activity
                        if (msg.what != 1) {
                            idle = false
                        }
                        mainActivity.mIsIdle = idle
                        var i = 0
                        while (i < activity.mIdleListeners.size) {
                            activity.mIdleListeners[i].onIdleStateChange(activity.mIsIdle)
                            i++
                        }
                        // When the launcher has been idle, return the viewport to Search
                        // so the header and top of the home screen remain visible instead
                        // of leaving an app tile row focused near the bottom edge.
                        if (idle && !activity.isInEditMode) {
                            val searchIndex = activity.homeAdapter?.getRowIndex(0) ?: -1
                            if (searchIndex != -1 &&
                                activity.mListView?.selectedPosition != searchIndex
                            ) {
                                activity.resetLauncherState(true)
                            }
                        }
                        return
                    }

                    3 -> {
                        if (activity.mResetAfterIdleEnabled) {
                            activity.mKeepUiReset = true
                            activity.resetLauncherState(true)
                            //if (BuildConfig.DEBUG) Log.d(TAG, "msg(3) resetLauncherState(smooth: true)")
                            return
                        }
                        return
                    }

                    4 -> {
                        activity.onNotificationRowStateUpdate(msg.arg1)
                        //if (BuildConfig.DEBUG) Log.d(TAG, "msg(4) onNotificationRowStateUpdate(${msg.arg1})")
                        return
                    }

                    5 -> {
                        activity.homeAdapter?.onUiVisible()
                        //if (BuildConfig.DEBUG) Log.d(TAG, "msg(5) onUiVisible()")
                        return
                    }

                    6 -> {
                        activity.addWidget(true)
                        // addWidget(true) replaces the clock/weather layout. Restore weather only
                        // after the replacement view has been inflated and attached.
                        if (activity.weatherInitialized && RowPreferences.isWeatherEnabled(activity)) {
                            activity.scheduleWeatherUiRestore()
                        }
                        //if (BuildConfig.DEBUG) Log.d(TAG, "msg(6) addWidget(refresh: true)")
                        return
                    }

                    7 -> {
                        activity.checkLaunchPointPositions()
                        //if (BuildConfig.DEBUG) Log.d(TAG, "msg(7) checkLaunchPointPositions()")
                        return
                    }

                    else -> return
                }
            }
        }
    }

    private val mSearchIconCallbacks: LoaderManager.LoaderCallbacks<Drawable> =
        object : LoaderManager.LoaderCallbacks<Drawable> {
            override fun onCreateLoader(id: Int, args: Bundle?): Loader<Drawable> {
                return TvSearchIconLoader(this@MainActivity.applicationContext)
            }

            override fun onLoadFinished(loader: Loader<Drawable>, data: Drawable?) {
                homeAdapter?.onSearchIconUpdate(data)
            }

            override fun onLoaderReset(loader: Loader<Drawable>) {
                homeAdapter?.onSearchIconUpdate(null)
            }
        }

    private val mSearchSuggestionsCallbacks: LoaderManager.LoaderCallbacks<Array<String>> =
        object : LoaderManager.LoaderCallbacks<Array<String>> {
            override fun onCreateLoader(id: Int, args: Bundle?): Loader<Array<String>> {
                return TvSearchSuggestionsLoader(this@MainActivity.applicationContext)
            }

            override fun onLoadFinished(loader: Loader<Array<String>>, data: Array<String>?) {
                homeAdapter?.onSuggestionsUpdate(data)
            }

            override fun onLoaderReset(loader: Loader<Array<String>>) {
                homeAdapter?.onSuggestionsUpdate(emptyArray())
            }
        }

    @SuppressLint("WrongConstant")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.amazon.tv.leanbacklauncher.settings.LauncherLanguage.ensureSupported()
        mContentResolver = contentResolver

        if (mRecommendationsAdapter == null) {
            mRecommendationsAdapter = NotificationsAdapter(this)
        }
        val appContext = applicationContext
        setContentView(R.layout.activity_main)

        if (Partner.get(this).showLiveTvOnStartUp() && checkFirstRunAfterBoot()) {
            val tvIntent = Intent("android.intent.action.VIEW", TvContract.buildChannelUri(0))
            tvIntent.putExtra("com.google.android.leanbacklauncher.extra.TV_APP_ON_BOOT", true)
            if (packageManager.queryIntentActivities(tvIntent, 1).isNotEmpty()) {
                startActivity(tvIntent)
                finish()
            }
        }
        // android O fix bug orientation
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.O) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        // FIXME: focus issues
        // mAccessibilityManager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        editModeView = findViewById<EditModeView?>(R.id.edit_mode_view)?.apply {
            setUninstallListener(this@MainActivity)
        }

        wallpaperView = findViewById(R.id.background_container)
        mAppWidgetManager = AppWidgetManager.getInstance(appContext)

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS))
            mAppWidgetHost = AppWidgetHost(this, 123)



        mListView = findViewById(R.id.main_list_view)
        mListView?.apply {
            setHasFixedSize(true)
            windowAlignment = BaseGridView.WINDOW_ALIGN_LOW_EDGE
            windowAlignmentOffset =
                resources.getDimensionPixelOffset(R.dimen.home_screen_selected_row_alignment)
            windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
            itemAlignmentOffset = 0
            itemAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED

            mScrollManager = HomeScrollManager(this@MainActivity, this).apply {
                addHomeScrollListener(wallpaperView!!)
            }
            homeAdapter = HomeScreenAdapter(
                this@MainActivity,
                mScrollManager!!,
                mRecommendationsAdapter,
                editModeView!!
            ).apply {
                setOnEditModeChangedListener(this@MainActivity)
            }
            setItemViewCacheSize(homeAdapter!!.itemCount)
            adapter = homeAdapter

            // Keep the launcher at the top on first launch; selecting Notifications
            // makes the recommendations/suggestions row pull the viewport downward.
            val searchIndex = homeAdapter?.getRowIndex(0) // RowType.SEARCH
            if (searchIndex != null && searchIndex != -1) {
                selectedPosition = searchIndex
            }
            val rAdapter = homeAdapter?.recommendationsAdapter?.apply {
                addIdleListener(this)
            }
            setAnimateChildLayout(false)
            setOnChildViewHolderSelectedListener(object : OnChildViewHolderSelectedListener() {
                override fun onChildViewHolderSelected(
                    parent: RecyclerView,
                    child: RecyclerView.ViewHolder?,
                    position: Int,
                    subposition: Int
                ) {
                    homeAdapter?.onChildViewHolderSelected(parent, child, position)
                }
            })
            setOnHierarchyChangeListener(object : OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View, child: View) {
                    var tag = 0
                    if (child.tag is Int) {
                        tag = child.tag as Int
                    }
                    when (tag) {
                        0 -> {
                            if (child is SearchOrbView) {
                                child.setLaunchListener(object : SearchLaunchListener {
                                    override fun onSearchLaunched() {
                                        setShyMode(shy = true, changeWallpaper = true)
                                    }
                                })
                            }
                            addWidget(false)
                        }

                        1, 2 -> {
                            mHomeScreenView = child.findViewById(R.id.home_screen_messaging)
                            mHomeScreenView?.let {
                                val homeScreenMessaging = it.homeScreenMessaging
                                if (tag == 1) {
                                    rAdapter?.setNotificationRowViewFlipper(homeScreenMessaging)
                                    mNotificationsView = it.notificationRow
                                    mNotificationsView?.setListener(mNotifListener)
                                }
                                homeScreenMessaging.setListener { state ->
                                    mHandler.sendMessageDelayed(
                                        mHandler.obtainMessage(4, state, 0),
                                        500
                                    )
                                    if (state == 0 && mDelayFirstRecommendationsVisible) {
                                        mDelayFirstRecommendationsVisible = false
                                        mHandler.sendEmptyMessageDelayed(5, 1500)
                                    }
                                }
                            }
                        }
                    }
                    if (child is IdleListener && !mIdleListeners.contains(child)) {
                        addIdleListener(child as IdleListener)
                    }
                }

                override fun onChildViewRemoved(parent: View, child: View) {}
            })
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    mScrollManager?.onScrolled(dy, currentScrollPos)
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    mScrollManager?.onScrollStateChanged(newState)
                }
            })
        }

        mShyMode = true
        setShyMode(shy = !mShyMode, changeWallpaper = true)

        mFadeDismissAndSummonAnimations = resources.getBoolean(R.bool.app_launch_animation_fade)
        mKeepUiReset = true
        homeAdapter?.onInitUi()
        mEventLogger = LeanbackLauncherEventLogger.getInstance(appContext)

        // register package change receiver
        val filter = IntentFilter().apply {
            addAction("android.intent.action.PACKAGE_REPLACED")
            addAction("android.intent.action.PACKAGE_ADDED")
            addDataScheme("package")
        }
        registerReceiver(mPackageReplacedReceiver, filter)
        // regiser RefreshHome broadcast ACTION com.amazon.tv.leanbacklauncher.MainActivity
        registerReceiver(mHomeRefreshReceiver, IntentFilter(this.javaClass.name))

        loaderManager.initLoader(0, null, mSearchIconCallbacks)
        loaderManager.initLoader(1, null, mSearchSuggestionsCallbacks)

        // start notification listener monitor
        if (RowPreferences.areRecommendationsEnabled(this) && LauncherApp.inForeground)
            startService(Intent(this, NotificationListenerMonitor::class.java))

        // fix int options migrate
        RowPreferences.fixRowPrefs()

        // Weather data also powers the native launcher header, independently of the legacy widget.
        // LocalWeather supplies coordinates only; all forecast data comes from Open-Meteo.
        localWeather = LocalWeather(this@MainActivity, "")
        // Let the launcher finish its first layout pass before starting location/network work.
        scheduleWeatherInitialization()
    }

    public override fun onDestroy() {
        if (BuildConfig.DEBUG) Log.d(TAG, "onDestroy()")
        mHandler.removeMessages(3)
        weatherHandler.removeCallbacksAndMessages(null)
        weatherInitScheduled = false
        weatherGeneration++
        defaultLauncherHandler.removeCallbacks(defaultLauncherPrompt)
        super.onDestroy()
        homeAdapter?.let {
            it.onStopUi()
            it.unregisterReceivers()
        }
        wallpaperView?.releaseVideoPlayer()
        getInstance(applicationContext)?.onDestroy()
        unregisterReceiver(mPackageReplacedReceiver)
        unregisterReceiver(mHomeRefreshReceiver)
    }

    private fun showDefaultLauncherDialog() {
        if (isFinishing || isDestroyed || !hasWindowFocus()) return
        if (com.amazon.tv.leanbacklauncher.util.DefaultLauncher.isDefault(this)) return
        com.amazon.tv.leanbacklauncher.util.DefaultLauncher.promptShown = true
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.default_launcher_title)
            .setMessage(R.string.default_launcher_message)
            .setPositiveButton(R.string.default_launcher_action) { _, _ ->
                com.amazon.tv.leanbacklauncher.util.DefaultLauncher.request(this)
            }
            .setNegativeButton(R.string.default_launcher_later, null)
            .show()
    }

    override fun onUserInteraction() {
        mHandler.removeMessages(3)
        mKeepUiReset = false
        if (hasWindowFocus()) {
            mHandler.removeMessages(1)
            mUserInteracted = true
            if (mIsIdle) {
                mHandler.sendEmptyMessage(2)
            }
            mHandler.sendEmptyMessageDelayed(1, mIdlePeriod.toLong())
        }
        mHandler.sendEmptyMessageDelayed(3, mResetPeriod.toLong())
    }

    private fun addIdleListener(listener: IdleListener) {
        mIdleListeners.add(listener)
        listener.onVisibilityChange(true)
        listener.onIdleStateChange(mIsIdle)
    }

    private var mLastWheelNavTime = 0L

    /**
     * Mouse wheel di home menggeser pilihan antar-row.
     *
     * Catatan penting:
     * - Activity hanya memanggil onUserInteraction() untuk sentuhan/tombol, TIDAK untuk wheel.
     *   Tanpa itu timer idle/reset launcher tidak ikut di-reset saat user hanya memakai wheel,
     *   jadi di sini dipanggil manual.
     * - Navigasi fokus D-pad dilakukan ViewRootImpl, bukan dispatchKeyEvent(), sehingga KeyEvent
     *   sintetis dari Activity tidak menggeser row. Karena itu posisi pilihan diubah langsung.
     * - Wheel throttle agar mouse/touchpad resolusi tinggi tidak melompati banyak row sekaligus.
     */
    private fun handleMouseWheel(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked != android.view.MotionEvent.ACTION_SCROLL) return false

        val vertical = ev.getAxisValue(android.view.MotionEvent.AXIS_VSCROLL)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "wheel: source=0x${ev.source.toString(16)} vscroll=$vertical editMode=$isInEditMode")
        }
        if (vertical == 0f || isInEditMode) return false

        val list = mListView ?: return false
        val itemCount = list.adapter?.itemCount ?: return false
        if (itemCount <= 0) return false

        onUserInteraction()

        val now = SystemClock.uptimeMillis()
        if (now - mLastWheelNavTime < WHEEL_NAV_INTERVAL_MS) return true
        mLastWheelNavTime = now

        val direction = if (vertical < 0f) 1 else -1
        val currentPosition = list.selectedPosition
            .takeIf { it != RecyclerView.NO_POSITION }
            ?: FIRST_POSITION
        val targetPosition = (currentPosition + direction).coerceIn(0, itemCount - 1)

        // Setelah klik mouse atau saat baru dibuka, grid bisa belum punya fokus; tanpa fokus
        // row baru terpilih tapi tidak aktif (tampilan tetap seperti row tidak terfokus).
        if (!list.hasFocus()) list.requestFocus()
        if (targetPosition != currentPosition) {
            list.setSelectedPositionSmooth(targetPosition)
        }
        return true
    }

    override fun dispatchGenericMotionEvent(ev: android.view.MotionEvent): Boolean {
        return if (handleMouseWheel(ev)) true else super.dispatchGenericMotionEvent(ev)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        when {
            isInEditMode -> {
                editModeView?.onBackPressed()
            }

            mLaunchAnimation.isRunning -> {
                mLaunchAnimation.cancel()
            }

            mLaunchAnimation.isPrimed -> {
                mLaunchAnimation.reset()
            }

            else -> {
                if (mLaunchAnimation.isFinished) {
                    mLaunchAnimation.reset()
                }
                dismissLauncher()
            }
        }
    }

//    override fun onBackgroundVisibleBehindChanged(visible: Boolean) {
//        setShyMode(shy = !visible, changeWallpaper = true)
//    }

    override fun onEditModeChanged(z: Boolean) {
        if (isInEditMode == z) {
            return
        }
        if (mAccessibilityManager?.isEnabled == true) {
            setEditMode(editMode = z, useAnimation = false)
        } else {
            setEditMode(editMode = z, useAnimation = true)
        }
    }

    override fun onUninstallPressed(packageName: String?) {
        if (packageName != null && !mUninstallRequested) {
            mUninstallRequested = true
            val uninstallIntent =
                Intent("android.intent.action.UNINSTALL_PACKAGE", "package:$packageName".toUri())
            uninstallIntent.putExtra("android.intent.extra.RETURN_RESULT", true)
            startActivityForResult(uninstallIntent, UNINSTALL_CODE)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        localWeather?.onActivityResult(requestCode, resultCode, data)
        if (requestCode == UNINSTALL_CODE && resultCode != 0) {
            if (resultCode == -1) {
                editModeView?.uninstallComplete()
            } else if (resultCode == 1) {
                editModeView?.uninstallFailure()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        localWeather?.onRequestPermissionResult(requestCode, permissions, grantResults)
        when (requestCode) {
            com.amazon.tv.leanbacklauncher.channels.ChannelsAdapter.REQUEST_CODE -> homeAdapter?.refreshChannels()
            PERMISSIONS_REQUEST_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Agree location permission")
                    recreate()
                } else {
                    Log.i(TAG, "Not agree location permission")
                    LauncherApp.toast(R.string.location_note, true)
                }
            }
        }
    }

    private val weatherPrefs by lazy { androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext) }
    private val weatherHandler = Handler(Looper.getMainLooper())
    private var weatherLoading = false
    private var weatherInitialized = false
    private var weatherInitScheduled = false
    private var weatherGeneration = 0
    private val weatherInitialize = Runnable {
        weatherInitScheduled = false
        if (!isDestroyed && !weatherInitialized) {
            weatherInitialized = true
            initializeWeather()
        }
    }

    private fun scheduleWeatherInitialization() {
        if (weatherInitialized || weatherInitScheduled || isFinishing || isDestroyed) return
        weatherInitScheduled = true
        window.decorView.post {
            if (weatherInitScheduled && !isDestroyed) {
                weatherHandler.postDelayed(weatherInitialize, 350L)
            }
        }
    }
    private var weatherSignature = ""
    private val weatherRefresh = Runnable { refreshWeather(true) }

    private fun weatherConfigKey(): String =
        if (RowPreferences.isUseLocationEnabled(this)) "current"
        else "manual:" + RowPreferences.getUserLocation(this).orEmpty().trim().lowercase(Locale.ROOT)

    private fun initializeWeather() {
        weatherSignature = weatherConfigKey()
        if (!RowPreferences.isWeatherEnabled(this)) {
            weatherViewGone()
            weatherHandler.removeCallbacks(weatherRefresh)
            return
        }
        val cached = readWeatherCache()
        if (cached != null) {
            Log.i(TAG, "Weather cache restored")
            updateWeatherDetails(cached)
            // Search/clock widget is inflated lazily after MainActivity.onCreate().
            scheduleWeatherUiRestore()
        } else {
            Log.i(TAG, "No usable weather cache; requesting forecast")
            weatherViewGone()
        }
        scheduleWeatherRefresh()
        if (cached == null || weatherCacheExpired()) refreshWeather(true)
    }

    private fun weatherCacheExpired(): Boolean {
        val time = weatherPrefs.getLong(PREF_OPEN_METEO_TIME, 0L)
        return time <= 0L || System.currentTimeMillis() - time >= maxCacheAge
    }

    private fun readWeatherCache(): JSONObject? {
        return try {
            if (weatherPrefs.getString(PREF_OPEN_METEO_CONFIG, null) != weatherConfigKey()) {
                null
            } else {
                val raw = weatherPrefs.getString(PREF_OPEN_METEO_JSON, null)
                raw?.let { JSONObject(it).takeIf { json -> json.has("current") && json.has("daily") } }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read Open-Meteo cache", e)
            null
        }
    }

    private fun refreshWeather(force: Boolean) {
        if (!RowPreferences.isWeatherEnabled(this) || weatherLoading) return
        if (!force && !weatherCacheExpired()) return
        weatherSignature = weatherConfigKey()
        weatherLoading = true
        if (RowPreferences.isUseLocationEnabled(this)) {
            val provider = localWeather
            if (provider == null) { weatherLoading = false; return }
            provider.useCurrentLocation = true
            provider.updateCurrentLocation = false
            provider.fetchCurrentLocation(object : LocalWeather.CurrentLocationCallback {
                override fun onSuccess(location: Location) {
                    if (!isDestroyed) loadOpenMeteo(location.latitude, location.longitude, null)
                }
                override fun onFailure(failed: LocationFailedEnum) {
                    if (!isDestroyed) loadIpWeather()
                }
            })
        } else {
            val query = RowPreferences.getUserLocation(this).orEmpty().trim()
            if (query.isBlank()) {
                weatherLoading = false
                LauncherApp.toast(R.string.user_location_warning, true)
                return
            }
            val generation = ++weatherGeneration
            lifecycleScope.launch {
                val location = withContext(Dispatchers.IO) { OpenMeteoWeather.resolve(query) }
                if (isDestroyed || generation != weatherGeneration) return@launch
                if (location == null) {
                    weatherLoading = false
                    LauncherApp.toast(getString(R.string.weather_error, "Location not found"), true)
                } else loadOpenMeteo(location.latitude, location.longitude, location.name)
            }
        }
    }

    private fun loadIpWeather() {
        val generation = ++weatherGeneration
        lifecycleScope.launch {
            val location = withContext(Dispatchers.IO) { OpenMeteoWeather.ipLocation() }
            if (isDestroyed || generation != weatherGeneration) return@launch
            if (location == null) {
                weatherLoading = false
                Log.e(TAG, "IP location fallback failed")
            } else loadOpenMeteo(location.latitude, location.longitude, location.name)
        }
    }

    private fun loadOpenMeteo(latitude: Double, longitude: Double, name: String?) {
        val generation = ++weatherGeneration
        lifecycleScope.launch {
            try {
                val weatherResult = withContext(Dispatchers.IO) {
                    val forecast = OpenMeteoWeather.forecast(latitude, longitude)
                    val resolvedName = name?.takeIf { it.isNotBlank() } ?: runCatching {
                        Geocoder(this@MainActivity, Locale.getDefault())
                            .getFromLocation(latitude, longitude, 1)
                            ?.firstOrNull()
                            ?.let { address ->
                                listOfNotNull(
                                    address.locality,
                                    address.subAdminArea,
                                    address.adminArea
                                ).map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString(", ")
                            }
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                    forecast to resolvedName
                }
                if (isDestroyed || generation != weatherGeneration) return@launch
                val forecast = weatherResult.first
                val locationName = weatherResult.second
                forecast.put("latitude", latitude)
                forecast.put("longitude", longitude)
                locationName?.let { forecast.put("name", it) }
                    ?: weatherPrefs.getString("launcher_detected_city", null)?.let { forecast.put("name", it) }
                val json = forecast
                val raw = json.toString()
                weatherPrefs.edit().putString(PREF_OPEN_METEO_JSON, raw)
                    .putLong(PREF_OPEN_METEO_TIME, System.currentTimeMillis())
                    .putString(PREF_OPEN_METEO_CONFIG, weatherSignature).apply()
                // Keep the legacy filename because PrayerTimesOverlay reads its city name.
                File(JSONFILE).writeText(raw)
                json.optString("name").takeIf { it.isNotBlank() }?.let {
                    weatherPrefs.edit().putString("launcher_detected_city", it).apply()
                }
                updateWeatherDetails(json)
                scheduleWeatherUiRestore()
                findViewById<com.amazon.tv.leanbacklauncher.wallpaper.PrayerTimesOverlay>(R.id.prayer_times_overlay)
                    ?.refreshForWeatherUpdate()
                weatherLoading = false
                scheduleWeatherRefresh()
            } catch (e: Exception) {
                if (isDestroyed || generation != weatherGeneration) return@launch
                weatherLoading = false
                Log.e(TAG, "Open-Meteo request failed; retaining previous forecast", e)
                scheduleWeatherRefresh()
            }
        }
    }

    private fun scheduleWeatherRefresh() {
        weatherHandler.removeCallbacks(weatherRefresh)
        val saved = weatherPrefs.getLong(PREF_OPEN_METEO_TIME, 0L)
        val elapsed = (System.currentTimeMillis() - saved).coerceAtLeast(0L)
        val delay = if (saved == 0L) maxCacheAge else (maxCacheAge - elapsed).coerceAtLeast(60_000L)
        weatherHandler.postDelayed(weatherRefresh, delay)
    }

    private var weatherUiRestoreAttempts = 0
    private val weatherUiRestore = object : Runnable {
        override fun run() {
            if (isDestroyed || !RowPreferences.isWeatherEnabled(this@MainActivity)) return
            val cached = readWeatherCache()
            val weatherView = findViewById<ViewGroup?>(R.id.weather)
            if (cached != null && weatherView != null) {
                updateWeatherDetails(cached)
                Log.d(TAG, "Weather UI restored after widget inflation")
            } else if (weatherUiRestoreAttempts++ < 30) {
                weatherHandler.postDelayed(this, 500L)
            } else {
                Log.w(TAG, "Weather UI view was not inflated; retaining cached forecast")
            }
        }
    }

    private fun scheduleWeatherUiRestore() {
        weatherHandler.removeCallbacks(weatherUiRestore)
        weatherUiRestoreAttempts = 0
        weatherHandler.post(weatherUiRestore)
    }

    private fun weatherViewGone() {
        findViewById<ViewGroup?>(R.id.weather)?.visibility = View.GONE
        findViewById<ViewGroup?>(R.id.details)?.visibility = View.GONE
    }

    @SuppressLint("SetTextI18n")
    private fun updateWeatherDetails(json: JSONObject) {
        if (!RowPreferences.isWeatherEnabled(this)) return
        val current = json.optJSONObject("current") ?: return
        val daily = json.optJSONObject("daily") ?: return
        val imperial = RowPreferences.isImperialUnits(this)
        val celsius = current.optDouble("temperature_2m", Double.NaN)
        if (celsius.isNaN()) return
        val low = daily.optJSONArray("temperature_2m_min")?.optDouble(0, celsius) ?: celsius
        val high = daily.optJSONArray("temperature_2m_max")?.optDouble(0, celsius) ?: celsius
        fun temperature(value: Double): String {
            val shown = if (imperial) value * 9.0 / 5.0 + 32.0 else value
            return format(Locale.getDefault(), "%.0f", shown)
        }
        val unit = if (imperial) "℉" else "℃"
        val speed = current.optDouble("wind_speed_10m", 0.0).let { if (imperial) it / 1.609344 else it }
        val speedUnit = if (imperial) getString(R.string.weather_speed_i) else getString(R.string.weather_speed_m)
        val pressure = current.optDouble("pressure_msl", current.optDouble("surface_pressure", 0.0))
        val code = current.optInt("weather_code", -1)
        findViewById<AppCompatImageView?>(R.id.weather_icon)?.let {
            OpenWeatherIcons(this, weatherIcon(code, current.optInt("is_day", 1) == 1), it)
            it.visibility = View.VISIBLE
        }
        findViewById<TextView?>(R.id.location_name)?.apply {
            val locationName = json.optString("name").takeIf { it.isNotBlank() && it != "null" }
                ?: weatherPrefs.getString("launcher_detected_city", null)
            text = locationName.orEmpty()
            visibility = if (locationName.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        findViewById<TextView?>(R.id.curTemp)?.text = temperature(celsius) + unit
        findViewById<TextView?>(R.id.hilotemp)?.text =
            getString(R.string.weather_hilotemp, temperature(low), temperature(high), unit)
        findViewById<TextView?>(R.id.pressure)?.text =
            getString(R.string.weather_pressure, format(Locale.getDefault(), "%.0f", pressure), getString(R.string.weather_pressure_hp))
        findViewById<TextView?>(R.id.humidity)?.text =
            getString(R.string.weather_humidity, current.optInt("relative_humidity_2m", 0))
        findViewById<TextView?>(R.id.wind)?.text = getString(
            R.string.weather_wind, format(Locale.getDefault(), "%.1f", speed), speedUnit,
            windDirection(current.optDouble("wind_direction_10m", 0.0))
        )
        findViewById<TextView?>(R.id.wDescription)?.apply {
            text = weatherDescription(code)
            setupMarquee()
        }
        showWeatherCard(findViewById(R.id.weather), findViewById(R.id.details))
    }

    private fun windDirection(degrees: Double): String {
        val labels = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return labels[((degrees % 360.0 + 360.0) % 360.0 / 45.0).roundToInt() % 8]
    }

    private fun weatherIcon(code: Int, day: Boolean): String {
        val base = when (code) {
            0 -> "01"; 1, 2 -> "02"; 3 -> "04"; 45, 48 -> "50"
            in 51..57, in 61..67, in 80..82 -> "09"
            in 71..77, 85, 86 -> "13"; in 95..99 -> "11"; else -> "03"
        }
        return base + if (day) "d" else "n"
    }

    private fun weatherDescription(code: Int): String = when (code) {
        0 -> "Clear sky"; 1 -> "Mainly clear"; 2 -> "Partly cloudy"; 3 -> "Overcast"
        45, 48 -> "Fog"; in 51..57 -> "Drizzle"; in 61..67 -> "Rain"
        in 71..77, 85, 86 -> "Snow"; in 80..82 -> "Rain showers"
        in 95..99 -> "Thunderstorm"; else -> "—"
    }

    private fun showWeatherCard(weatherView: ViewGroup?, detailsView: ViewGroup?) {
        listOf(weatherView, detailsView).forEach { view ->
            view?.apply { animate().cancel(); alpha = 1f; visibility = View.VISIBLE }
        }
    }

    private fun TextView.setupMarquee() {
        ellipsize = TextUtils.TruncateAt.MARQUEE
        isSingleLine = true
        marqueeRepeatLimit = -1
        isSelected = true
        isFocusableInTouchMode = false
        isFocusable = false
    }

    private fun setShyMode(shy: Boolean, changeWallpaper: Boolean): Boolean {
        var changed = false
        if (mShyMode != shy) {
            mShyMode = shy
            changed = true
            findViewById<View>(R.id.home_scrim)?.animate()?.alpha(if (shy) 0f else 1f)?.setDuration(300)?.start()
            if (mShyMode) {
                //if (BuildConfig.DEBUG) Log.d(TAG, "setShyMode(shy:$shy,changeWallpaper:$changeWallpaper) -> convertFromTranslucent() [mShyMode=$mShyMode]")
                convertFromTranslucent()
            } else {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    //if (BuildConfig.DEBUG) Log.d(TAG, "setShyMode(shy:$shy,changeWallpaper:$changeWallpaper) convertToTranslucent() [mShyMode=$mShyMode]")
                    convertToTranslucent() // convertToTranslucent(null, null);
                }
            }
        }
        if (changeWallpaper && wallpaperView?.shynessMode != shy) {
            wallpaperView?.shynessMode = mShyMode
            if (mShyMode && mNotificationsView != null) {
                //if (BuildConfig.DEBUG) Log.d(TAG, "setShyMode(shy:$shy,changeWallpaper:$changeWallpaper) refreshSelectedBackground() [mShyMode=$mShyMode]")
                mNotificationsView?.refreshSelectedBackground()
            }
        }
        return changed
    }

    private fun convertFromTranslucent() {
        try {
            val convertFromTranslucent =
                Activity::class.java.getDeclaredMethod("convertFromTranslucent")
            convertFromTranslucent.isAccessible = true
            convertFromTranslucent.invoke(this@MainActivity)
        } catch (_: Throwable) {
        }
    }

    private fun convertToTranslucent() {
        try {
            var translucentConversionListenerClazz: Class<*>? = null
            for (clazz in Activity::class.java.declaredClasses) {
                if (clazz.simpleName.contains("TranslucentConversionListener")) {
                    translucentConversionListenerClazz = clazz
                }
            }
            val convertToTranslucent = Activity::class.java.getDeclaredMethod(
                "convertToTranslucent",
                translucentConversionListenerClazz,
                ActivityOptions::class.java
            )
            convertToTranslucent.isAccessible = true
            convertToTranslucent.invoke(this@MainActivity, null, null)
        } catch (_: Throwable) {
        }
    }

    private fun dismissLauncher(): Boolean {
        if (mShyMode) {
            return false
        }
        mLaunchAnimation.init(
            LauncherDismissAnimator(
                mListView,
                mFadeDismissAndSummonAnimations,
                homeAdapter!!.rowHeaders
            ), mMoveTaskToBack, 0.toByte()
        )
        mLaunchAnimation.start()
        return true
    }

    public override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        var exitingEditMode = false
        if (isInEditMode) {
            if (Util.isInTouchExploration(applicationContext)) {
                setTitle(R.string.app_label)
            }
            setEditMode(editMode = false, useAnimation = true)
            exitingEditMode = true
        }
        if (mLaunchAnimation.isRunning) {
            mLaunchAnimation.cancel()
            return
        }
        if (mLaunchAnimation.isPrimed) {
            mLaunchAnimation.reset()
        }
        if (mLaunchAnimation.isFinished) {
            mLaunchAnimation.reset()
        }
        if (!exitingEditMode) {
            intent?.extras?.let {
                if (it.getBoolean("extra_start_customize_apps")) {
                    startEditMode(3)
                } else if (it.getBoolean("extra_start_customize_games")) {
                    startEditMode(4)
                }
            }
            if (!mStartingEditMode) {
                if (!hasWindowFocus() || intent?.getBooleanExtra(
                        "com.android.systemui.recents.tv.RecentsTvActivity.RECENTS_HOME_INTENT_EXTRA",
                        false
                    ) == true
                ) {
                    if (!mLaunchAnimation.isScheduled) {
                        resetLauncherState(false)
                        mLaunchAnimation.init(
                            MassSlideAnimator.Builder(mListView)
                                .setDirection(MassSlideAnimator.Direction.SLIDE_IN)
                                .setFade(mFadeDismissAndSummonAnimations)
                                .build(), mRefreshHomeAdapter, 32.toByte()
                        )
                    }
                } else if (!dismissLauncher()) {
                    resetLauncherState(true)
                }
            }
        } else if (!mLaunchAnimation.isInitialized && !mLaunchAnimation.isScheduled) {
            resetLauncherState(false)
            mLaunchAnimation.init(
                MassSlideAnimator.Builder(mListView)
                    .setDirection(MassSlideAnimator.Direction.SLIDE_IN)
                    .setFade(mFadeDismissAndSummonAnimations)
                    .build(), mRefreshHomeAdapter, 32.toByte()
            )
        }
    }

    private fun startEditMode(rowType: Int) {
        if (Util.isInTouchExploration(applicationContext)) {
            setTitle(if (rowType == 3) R.string.title_app_edit_mode else R.string.title_game_edit_mode)
        }
        mStartingEditMode = true
        homeAdapter?.resetRowPositions(false)
        mLaunchAnimation.cancel()
        mListView?.selectedPosition = homeAdapter!!.getRowIndex(rowType)
        homeAdapter?.prepareEditMode(rowType)
    }

    private fun resetLauncherState(smooth: Boolean) {
        if (BuildConfig.DEBUG) Log.d(TAG, "resetLauncherState(smooth:$smooth)")
        mScrollManager?.onScrolled(0, 0)
        mUserInteracted = false
        homeAdapter?.resetRowPositions(smooth)

        if (isInEditMode) {
            setEditMode(editMode = false, useAnimation = smooth)
        }
        val currIndex = mListView!!.selectedPosition
        val searchIndex = homeAdapter!!.getRowIndex(0) // 0 - Search row
        if (BuildConfig.DEBUG) Log.d(TAG, "currIndex:$currIndex, searchIndex:$searchIndex")
        if (searchIndex != -1 && currIndex != searchIndex) {
            if (BuildConfig.DEBUG) Log.d(TAG, "resetLauncherState -> set focus to Search row")
            if (smooth) {
                mListView?.setSelectedPositionSmooth(searchIndex)
            } else {
                mListView?.selectedPosition = searchIndex
                val focusedChild = mListView?.focusedChild
                focusedChild?.let { child ->
                    if (mListView?.getChildAdapterPosition(child) == searchIndex) {
                        child.clearFocus()
                    }
                }
            }
        }
        mLaunchAnimation.cancel()
    }

    private val isBackgroundVisibleBehind: Boolean
        get() {
            try {
                val isBackgroundVisibleBehind =
                    Activity::class.java.getDeclaredMethod("isBackgroundVisibleBehind")
                isBackgroundVisibleBehind.isAccessible = true
                return isBackgroundVisibleBehind.invoke(this@MainActivity) as Boolean
            } catch (_: Throwable) {
            }
            return false
        }

    override fun onStart() {
        super.onStart()
        mResetAfterIdleEnabled = false
        try {
            mAppWidgetHost?.startListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        setShyMode(!isBackgroundVisibleBehind, true)

        wallpaperView?.resetBackground()
        homeAdapter?.refreshAdapterData()
        if (mKeepUiReset) {
            resetLauncherState(false)
        }

        if (!mStartingEditMode) {
            if (!mLaunchAnimation.isInitialized) {
                mLaunchAnimation.init(
                    LauncherReturnAnimator(
                        mListView,
                        mLaunchAnimation.lastKnownEpicenter,
                        homeAdapter!!.rowHeaders,
                        mHomeScreenView
                    ), mRefreshHomeAdapter, 32.toByte()
                )
            }
            mLaunchAnimation.schedule<LauncherReturnAnimator>()
        }
        mStartingEditMode = false
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {}

    override fun onResume() {
        var forceResort = true

        super.onResume()
        if (RowPreferences.isWeatherEnabled(this)) {
            if (!weatherInitialized) {
                scheduleWeatherInitialization()
            } else {
                // Recreate the launcher's clock/weather header after returning from
                // another Activity. updateWeatherDetails() then paints it from cache;
                // a network refresh is still only requested when the cache has expired.
                if (mAppWidgetHostView == null) addWidget(true)
                window.decorView.post {
                    if (!isDestroyed && RowPreferences.isWeatherEnabled(this)) {
                        scheduleWeatherUiRestore()
                    }
                }
                if (weatherCacheExpired()) refreshWeather(true)
            }
        } else {
            weatherViewGone()
        }

        val shyChanged = setShyMode(!isBackgroundVisibleBehind, true)

        if (!getInstance(applicationContext)!!.checkIfResortingIsNeeded() || isInEditMode) {
            forceResort = false
        }
        homeAdapter?.sortRowsIfNeeded(forceResort)
        WallpaperInstaller.getInstance(this)?.installWallpaperIfNeeded()
        wallpaperView?.shynessMode = mShyMode
        wallpaperView?.onHostResume()
        if (shyChanged) {
            wallpaperView?.resetBackground()
        }
        if (mShyMode) {
            mNotificationsView?.refreshSelectedBackground()
        }
        if (!mHandler.hasMessages(6)) {
            mHandler.sendEmptyMessage(6)
        }
        homeAdapter?.animateSearchIn()
        for (i in mIdleListeners.indices) {
            mIdleListeners[i].onVisibilityChange(true)
        }
        mResetAfterIdleEnabled = true
        mHandler.removeMessages(3)
        mHandler.removeMessages(1)
        if (mIsIdle) {
            mHandler.sendEmptyMessage(2)
        } else {
            mHandler.sendEmptyMessageDelayed(1, mIdlePeriod.toLong())
        }
        mHandler.sendEmptyMessageDelayed(3, mResetPeriod.toLong())
        mHandler.sendEmptyMessageDelayed(7, 2000)

        defaultLauncherHandler.removeCallbacks(defaultLauncherPrompt)
        if (!com.amazon.tv.leanbacklauncher.util.DefaultLauncher.promptShown &&
            !com.amazon.tv.leanbacklauncher.util.DefaultLauncher.isDefault(this)
        ) {
            defaultLauncherHandler.postDelayed(defaultLauncherPrompt, DEFAULT_LAUNCHER_PROMPT_DELAY_MS)
        }

        if (mLaunchAnimation.isFinished) {
            mLaunchAnimation.reset()
        }
        if (mLaunchAnimation.isInitialized) {
            mLaunchAnimation.reset()
        }
        if (mLaunchAnimation.isScheduled) {
            primeAnimationAfterLayout()
        }
        mPauseAnimation.reset()

        if (isInEditMode) {
            if (mEditModeAnimation.isInitialized)
                mEditModeAnimation.reset()  // FIXME: added
            mEditModeAnimation.init(
                EditModeMassFadeAnimator(this, EditMode.ENTER),
                null,
                0.toByte()
            )
            mEditModeAnimation.start()
        }
        mUninstallRequested = false

        overridePendingTransition(R.anim.home_fade_in_top, R.anim.home_fade_out_bottom)

        if (!(homeAdapter == null || homeAdapter!!.isUiVisible || mDelayFirstRecommendationsVisible)) {
            homeAdapter?.onUiVisible()
        }
    }

    override fun onEnterAnimationComplete() {
        if (mLaunchAnimation.isScheduled || mLaunchAnimation.isPrimed) {
            mLaunchAnimation.start()
        }
    }

    override fun onPause() {
        // Pause background UI work, but keep the weather header visibility intact.
        // The Activity may pause briefly during launcher transitions; hiding the views
        // here leaves them gone until a later resume/rebuild even though the cache is valid.
        weatherHandler.removeCallbacks(weatherUiRestore)
        wallpaperView?.onHostPause()
        super.onPause()
        defaultLauncherHandler.removeCallbacks(defaultLauncherPrompt)
        mResetAfterIdleEnabled = false
        mLaunchAnimation.cancel()
        mHandler.removeMessages(1)
        mHandler.removeMessages(6)
        mHandler.removeMessages(7)
        for (i in mIdleListeners.indices) {
            mIdleListeners[i].onVisibilityChange(false)
        }
        if (isInEditMode) {
            if (mEditModeAnimation.isInitialized)
                mEditModeAnimation.reset() // FIXME: added
            mEditModeAnimation.init(
                EditModeMassFadeAnimator(this, EditMode.EXIT),
                null,
                0.toByte()
            )
            mEditModeAnimation.start()
        }
        mPauseAnimation.init(LauncherPauseAnimator(mListView), null, 0.toByte())
        mPauseAnimation.start()
        if (homeAdapter != null && homeAdapter!!.isUiVisible) {
            homeAdapter?.onUiInvisible()
            mDelayFirstRecommendationsVisible = false
        }
    }

    override fun onStop() {
        mResetAfterIdleEnabled = true
        try {
            mAppWidgetHost?.stopListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mHandler.removeCallbacksAndMessages(null)
        mHandler.sendEmptyMessageDelayed(3, mResetPeriod.toLong())

        if (isInEditMode) {
            setEditMode(editMode = false, useAnimation = false)
        }
        setShyMode(shy = false, changeWallpaper = false)

        homeAdapter?.sortRowsIfNeeded(false)
        mLaunchAnimation.reset()
        super.onStop()
    }

    override fun dump(
        prefix: String,
        fd: FileDescriptor?,
        writer: PrintWriter,
        args: Array<String>?
    ) {
        super.dump(prefix, fd, writer, args)
        getInstance(applicationContext)?.dump(prefix, writer)
        mLaunchAnimation.dump(prefix, writer, mListView)
        homeAdapter?.dump(prefix, writer)
    }

    private val currentScrollPos: Int
        get() {
            var position = 0
            var topView = -1
            var rowIndex = 0
            while (rowIndex < mListView!!.childCount) {
                val child = mListView!!.getChildAt(rowIndex)
                if (child == null || child.top > 0) {
                    rowIndex++
                } else {
                    topView = mListView!!.getChildAdapterPosition(child)
                    if (child.measuredHeight > 0) {
                        position = (homeAdapter!!.getScrollOffset(topView)
                            .toFloat() * (abs(child.top).toFloat() / child.measuredHeight.toFloat()) * -1.0f).toInt()
                    }
                    topView--
                    while (topView >= 0) {
                        position -= homeAdapter!!.getScrollOffset(topView)
                        topView--
                    }
                    return position
                }
            }
            return 0 // position
        }

    private fun onNotificationRowStateUpdate(state: Int) {
        //if (BuildConfig.DEBUG) Log.d(TAG, "onNotificationRowStateUpdate(state: " + state + "), active row position: " + mList!!.selectedPosition)
        if (state == 1 || state == 2) {
            if (!mUserInteracted) {
                val searchIndex = homeAdapter!!.getRowIndex(0)
                if (searchIndex != -1) {
                    // focus on Search in case no recs yet
                    mListView?.selectedPosition = searchIndex
                    mListView?.getChildAt(searchIndex)?.requestFocus()
                    //if (BuildConfig.DEBUG) Log.d(TAG, "select search row and requestFocus()")
                }
            }
        } else if (state == 0 && mListView!!.selectedPosition == homeAdapter!!.getRowIndex(1) && mNotificationsView!!.isNotEmpty()) {
            // focus on Recomendations
            mNotificationsView?.selectedPosition = 0
            mNotificationsView?.getChildAt(0)?.requestFocus()
            //if (BuildConfig.DEBUG) Log.d(TAG, "select recs row and focus on 1st")
        }
    }

    override fun onSearchRequested(): Boolean {
        setShyMode(shy = true, changeWallpaper = true)
        return super.onSearchRequested()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (mLaunchAnimation.isPrimed || mLaunchAnimation.isRunning || mEditModeAnimation.isPrimed || mEditModeAnimation.isRunning) {
            when (keyCode) {
                KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_BACK -> super.onKeyDown(keyCode, event)
                else -> true
            }
        } else if (mShyMode || !isMediaKey(event.keyCode)) {
            super.onKeyDown(keyCode, event)
        } else {
            true
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO) {
            val selectItem = mListView!!.focusedChild
            if (selectItem is ActiveFrame) {
                val v = selectItem.mRow
                val child = v?.focusedChild // TODO
                if (child is BannerView) {
                    val holder = child.viewHolder
                    if (holder != null) { // holder == null when the holder is an input
                        val pkg = holder.packageName
                        val intent = Intent(this, AppInfoActivity::class.java)
                        val bundle = Bundle()
                        bundle.putString("pkg", pkg)
                        intent.putExtras(bundle)
                        startActivity(intent)
                    }
                }
            }
            return true
        }
        return if (mShyMode || !isMediaKey(event.keyCode)) {
            super.onKeyUp(keyCode, event)
        } else when (keyCode) {
            KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                setShyMode(shy = true, changeWallpaper = true)
                true
            }

            else -> true
        }
    }

    private fun addWidget(refresh: Boolean) {
        val wrapper: ViewGroup? = findViewById<View>(R.id.widget_wrapper) as? LinearLayout?
        wrapper?.let { wrap ->
            if (refresh || mAppWidgetHostView == null) {
                wrap.removeAllViews()
                var success = false
                var appWidgetId = Util.getWidgetId(this)
                val appWidgetComp = Partner.get(this).widgetComponentName
                if (appWidgetComp != null) {
                    for (appWidgetInfo in mAppWidgetManager!!.installedProviders) {
                        if (appWidgetComp == appWidgetInfo.provider) {
                            success = appWidgetId != 0
                            if (success && appWidgetComp != Util.getWidgetComponentName(this)) {
                                clearWidget(appWidgetId)
                                success = false
                            }
                            if (!success) {
                                val width = resources.getDimension(R.dimen.widget_width).toInt()
                                val height =
                                    resources.getDimension(R.dimen.widget_height).toInt()
                                val options = Bundle()
                                options.putInt("appWidgetMinWidth", width)
                                options.putInt("appWidgetMaxWidth", width)
                                options.putInt("appWidgetMinHeight", height)
                                options.putInt("appWidgetMaxHeight", height)
                                appWidgetId = mAppWidgetHost?.allocateAppWidgetId() ?: 0
                                success = mAppWidgetManager?.bindAppWidgetIdIfAllowed(
                                    appWidgetId,
                                    appWidgetInfo.provider,
                                    options
                                ) == true
                            }
                            if (success) {
                                mAppWidgetHostView =
                                    mAppWidgetHost?.createView(this, appWidgetId, appWidgetInfo)
                                mAppWidgetHostView?.setAppWidget(appWidgetId, appWidgetInfo)
                                wrap.addView(mAppWidgetHostView)
                                Util.setWidget(this, appWidgetId, appWidgetInfo.provider)
                            }
                        }
                    }
                }
                if (!success) {
                    clearWidget(appWidgetId)
                    // clock
                    wrap.addView(LayoutInflater.from(this).inflate(R.layout.clock, wrap, false))
                    findViewById<TextView>(R.id.location_name)?.isSelected = true
                    val typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                    val clockView: TextView? = findViewById<View>(R.id.clock) as ClockView?
                    clockView?.typeface = typeface
                    // settings
                    val settingsVG: ViewGroup? =
                        findViewById<View>(R.id.settings) as LinearLayout?
                    settingsVG?.let { group ->
                        val sel = findViewById<ImageView>(R.id.settings_selection_circle)
                        sel?.setColorFilter(
                            RowPreferences.getFrameColor(this),
                            PorterDuff.Mode.SRC_ATOP
                        )
                        val icon = findViewById<ImageView>(R.id.settings_icon)
                        group.setOnClickListener {
                            startSettings()
                        }
                        group.setOnFocusChangeListener { _, hasFocus ->
                            if (hasFocus) {
                                sel?.alpha = 1.0f
                                icon?.clearAnimation()
                                icon?.alpha = 1.0f
                            } else {
                                //sel?.alpha = 0.0f
                                sel?.animate()?.apply {
                                    interpolator = LinearInterpolator()
                                    duration = 500
                                    alpha(0.0f)
                                    start()
                                }
                                icon?.alpha = 1.0f
                            }
                        }
                        sel?.alpha = 0.0f
                        icon?.alpha = 0.0f
                        icon?.animate()?.apply {
                            interpolator = LinearInterpolator()
                            duration = 500
                            alpha(1.0f)
                            start()
                        }
                    }
                    // Weather/location is initialized once for the native launcher header.
                    return
                }
                return
            }
            val parent = mAppWidgetHostView!!.parent as ViewGroup
            if (parent !== wrap) {
                parent.removeView(mAppWidgetHostView)
                wrap.removeAllViews()
                wrap.addView(mAppWidgetHostView)
            }
        }
    }

    private fun startSettings() {
        // Satu pintu pengaturan: halaman "Sesuaikan TV Anda" (berisi wallpaper, waktu salat, dan semua pengaturan launcher).
        startActivity(Intent(this@MainActivity, com.amazon.tv.leanbacklauncher.settings.TvOsCustomizationActivity::class.java))
    }

    private fun clearWidget(appWidgetId: Int) {
        if (appWidgetId != 0) {
            mAppWidgetHost?.deleteAppWidgetId(appWidgetId)
        }
        Util.clearWidget(this)
    }

    val editModeWallpaper: View
        get() = findViewById(R.id.edit_mode_background)

    private fun setEditMode(editMode: Boolean, useAnimation: Boolean) {
        var alpha = 1.0f
        mEditModeAnimation.reset()
        if (useAnimation) {
            mEditModeAnimation.init(
                EditModeMassFadeAnimator(
                    this,
                    if (editMode) EditMode.ENTER else EditMode.EXIT
                ), null, 0.toByte()
            )
            mEditModeAnimation.start()
        } else {
            val launcherWallpaper = wallpaperView
            val f: Float = if (editMode) {
                0.0f
            } else {
                1.0f
            }
            launcherWallpaper?.alpha = f
            val editModeWallpaper = editModeWallpaper
            if (!editMode) {
                alpha = 0.0f
            }
            editModeWallpaper.alpha = alpha
            editModeWallpaper.visibility = View.VISIBLE
            homeAdapter?.setRowAlphas(if (editMode) 0 else 1)
        }
        // FIXME: wrong focus and useless with no DIM
        if (!editMode && isInEditMode) {
            for (i in 0 until mListView!!.childCount) {
                val activeFrame = mListView?.getChildAt(i)
                if (activeFrame is ActiveFrame) {
                    for (j in 0 until activeFrame.childCount) {
                        val activeItemsRow = activeFrame.getChildAt(j)
                        if (activeItemsRow is EditableAppsRowView) {
                            activeItemsRow.editMode = false
                        }
                    }
                }
            }
        }
        isInEditMode = editMode
    }

    @SuppressLint("WrongConstant")
    private fun checkFirstRunAfterBoot(): Boolean {
        val dummyIntent = Intent("android.intent.category.LEANBACK_LAUNCHER")
        dummyIntent.setClass(this, DummyActivity::class.java)
        val firstRun = PendingIntent.getActivity(this, 0, dummyIntent, FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) == null
        if (firstRun) {
            (getSystemService(ALARM_SERVICE) as AlarmManager)[AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 864000000000L] =
                PendingIntent.getActivity(this, 0, dummyIntent, PendingIntent.FLAG_IMMUTABLE)
        }
        return firstRun
    }

    fun beginLaunchAnimation(
        view: View,
        translucent: Boolean,
        color: Int,
        onCompleteCallback: Runnable
    ) {
        if (!mLaunchAnimation.isPrimed && !mLaunchAnimation.isRunning && !mLaunchAnimation.isFinished) {
            getBoundsOnScreen(view, mLaunchAnimation.lastKnownEpicenter)
            if (translucent) {
                onCompleteCallback.run()
                return
            }
            val animation: ForwardingAnimatorSet = if (view is NotificationCardView) {
                NotificationLaunchAnimator(
                    mListView,
                    view,
                    mLaunchAnimation.lastKnownEpicenter,
                    findViewById<View>(R.id.click_circle_layer) as ImageView,
                    color,
                    homeAdapter!!.rowHeaders,
                    mHomeScreenView
                )
            } else {
                LauncherLaunchAnimator(
                    mListView,
                    view,
                    mLaunchAnimation.lastKnownEpicenter,
                    findViewById<View>(R.id.click_circle_layer) as ImageView,
                    color,
                    homeAdapter!!.rowHeaders,
                    mHomeScreenView
                )
            }
            mLaunchAnimation.init(animation, onCompleteCallback, 0.toByte())
            mLaunchAnimation.start()
        }
    }

    val isLaunchAnimationInProgress: Boolean
        get() = mLaunchAnimation.isPrimed || mLaunchAnimation.isRunning

    val isEditAnimationInProgress: Boolean
        get() = mEditModeAnimation.isPrimed || mEditModeAnimation.isRunning

    fun includeInLaunchAnimation(target: View?) {
        mLaunchAnimation.include(target)
    }

    fun includeInEditAnimation(target: View?) {
        mEditModeAnimation.include(target)
    }

    fun excludeFromLaunchAnimation(target: View?) {
        mLaunchAnimation.exclude(target)
    }

    fun excludeFromEditAnimation(target: View?) {
        mEditModeAnimation.exclude(target)
    }

    fun setOnLaunchAnimationFinishedListener(l: OnAnimationFinishedListener?) {
        mLaunchAnimation.setOnAnimationFinishedListener(l)
    }

    private fun primeAnimationAfterLayout() {
        mListView?.rootView?.viewTreeObserver?.addOnGlobalLayoutListener(object :
            OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                mListView?.rootView?.viewTreeObserver?.removeOnGlobalLayoutListener(this)
                if (mLaunchAnimation.isScheduled) {
                    mLaunchAnimation.prime()
                }
            }
        })
        mListView?.requestLayout()
    }

    private fun checkLaunchPointPositions() {
        if (!mLaunchAnimation.isRunning && checkViewHierarchy(mListView)) {
//            val buf = StringWriter()
//            buf.append("Caught partially animated state; resetting...\n")
//            mLaunchAnimation.dump("", PrintWriter(buf), mList)
//            Log.w(TAG, "Animations:$buf")
            mLaunchAnimation.reset()
        }
    }

    private fun checkViewHierarchy(view: View?): Boolean {
        if (view is ParticipatesInLaunchAnimation && view.translationY != 0.0f) {
            return true
        }
        if (view is ViewGroup) {
            val n = view.childCount
            for (i in 0 until n) {
                if (checkViewHierarchy(view.getChildAt(i))) {
                    return true
                }
            }
        }
        return false
    }

}
