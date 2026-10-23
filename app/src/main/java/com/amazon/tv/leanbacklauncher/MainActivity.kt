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
import android.location.Location
import android.media.tv.TvContract
import android.os.Build
import android.os.Bundle
import android.os.Handler
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
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences.getWeatherApiKey
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
import com.amazon.tv.leanbacklauncher.util.Permission
import com.amazon.tv.leanbacklauncher.util.TvSearchIconLoader
import com.amazon.tv.leanbacklauncher.util.TvSearchSuggestionsLoader
import com.amazon.tv.leanbacklauncher.util.Util
import com.amazon.tv.leanbacklauncher.wallpaper.LauncherWallpaper
import com.amazon.tv.leanbacklauncher.wallpaper.WallpaperInstaller
import com.amazon.tv.leanbacklauncher.widget.EditModeView
import com.amazon.tv.leanbacklauncher.widget.EditModeView.OnEditModeUninstallPressedListener
import com.google.gson.GsonBuilder
import de.interaapps.localweather.LocalWeather
import de.interaapps.localweather.Weather
import de.interaapps.localweather.utils.Lang
import de.interaapps.localweather.utils.LocationFailedEnum
import de.interaapps.localweather.utils.Units
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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


class MainActivity : AppCompatActivity(), OnEditModeChangedListener,
    OnEditModeUninstallPressedListener {

    companion object {
        private const val TAG = "MainActivity"
        private const val FIRST_POSITION = 0
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
    private val gson by lazy { GsonBuilder().setPrettyPrinting().create() }
    private val maxCacheAge = TimeUnit.MINUTES.toMillis(30) // 30 minutes

    // Weather Animation constants
    private val showCycleDur: Long = TimeUnit.SECONDS.toMillis(10) // 10 seconds
    private val fadeInDur: Long = 300L // milliseconds
    private val fadeOutDur: Long = 500L
    private var weatherAnimationJob: Job? = null

    // Core components
    private val mHandler: Handler = MainActivityMessageHandler(this)
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
                mHandler = mHandler ?: Handler()
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

    private class MainActivityMessageHandler(activity: MainActivity) : Handler() {
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
                        //if (BuildConfig.DEBUG) Log.d(TAG, "msg(6) addWidget(refresh: true)")
                        return
                    }

                    7 -> {
                        activity.checkLaunchPointPositions()
                        //if (BuildConfig.DEBUG) Log.d(TAG, "msg(7) checkLaunchPointPositions()")
                        return
                    }

                    else -> TODO()
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
        // overlay permissions request on M+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:$packageName".toUri()
                )
                try {
                    startActivityForResult(intent, 0)
                } catch (_: Exception) {
                }
            }
        }
        // network monitor (request from HomeScreenAdapter)
        Permission.isLocationPermissionGranted(this)

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

            val notifIndex = homeAdapter?.getRowIndex(1) // RowType.NOTIFICATIONS
            if (notifIndex != null && notifIndex != -1) {
                selectedPosition = notifIndex
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

        // LocalWeather https://github.com/interaapps/LocalWeather-Android
        if (RowPreferences.isWeatherEnabled(this)) {
            localWeather = LocalWeather(
                this@MainActivity,
                getWeatherApiKey(this)
            )
            // initializeWeather() // already in addWidget()
        }
    }

    public override fun onDestroy() {
        if (BuildConfig.DEBUG) Log.d(TAG, "onDestroy()")
        mHandler.removeMessages(3)
        super.onDestroy()
        homeAdapter?.let {
            it.onStopUi()
            it.unregisterReceivers()
        }
        getInstance(applicationContext)?.onDestroy()
        unregisterReceiver(mPackageReplacedReceiver)
        unregisterReceiver(mHomeRefreshReceiver)
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
        if (RowPreferences.isWeatherEnabled(this))
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
        if (RowPreferences.isWeatherEnabled(this))
            localWeather?.onRequestPermissionResult(requestCode, permissions, grantResults)
        when (requestCode) {
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

    private fun initializeWeather() {
        localWeather?.let { lw ->
            setupWeatherDefaults(lw)

            if (shouldUseCurrentLocation()) {
                setupLocationBasedWeather(lw)
            } else {
                setupManualLocationWeather(lw)
            }

            setupWeatherCallbacks(lw)
        }
    }

    private fun setupWeatherDefaults(lw: LocalWeather) {
        val ul = Locale.getDefault().isO3Language
        lw.lang = when {
            ul.equals("rus", true) -> Lang.RUSSIAN
            ul.equals("ukr", true) -> Lang.UKRAINIAN
            ul.equals("ita", true) -> Lang.ITALIAN
            ul.equals("fra", true) -> Lang.FRENCH
            ul.equals("esp", true) -> Lang.SPANISH
            ul.equals("deu", true) -> Lang.GERMAN
            else -> Lang.ENGLISH
        }
        lw.unit = if (RowPreferences.isImperialUnits(this)) Units.IMPERIAL else Units.METRIC
    }

    private fun shouldUseCurrentLocation(): Boolean {
        return RowPreferences.isUseLocationEnabled(this) // && !Util.isAmazonDev(this)
    }

    private fun setupLocationBasedWeather(lw: LocalWeather) {
        if (Util.isAmazonDev(this)) {
            lw.useCurrentLocation = false
            fetchGeoIPFallback(lw)
        } else {
            lw.useCurrentLocation = true
            lw.updateCurrentLocation = true
            lw.updateLocationInterval = TimeUnit.MINUTES.toMillis(10) // FIXME: no updates
        }
    }

    private fun setupManualLocationWeather(lw: LocalWeather) {
        try {
            lw.useCurrentLocation = false
            RowPreferences.getUserLocation(this)?.takeIf { it.isNotBlank() }?.let { userLoc ->
                when {
                    userLoc.isDigitsOnly() -> handleCityIdWeather(lw, userLoc)
                    isCoordinateLocation(userLoc) -> handleCoordinateWeather(lw, userLoc)
                    else -> handleCityNameWeather(lw, userLoc)
                }
            } ?: run {
//                if (Util.isAmazonDev(this)) {
//                    fetchGeoIPFallback(lw)
//                } else {
                LauncherApp.toast(R.string.user_location_warning, true)
//                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up manual location weather", e)
        }
    }

    private fun isCoordinateLocation(location: String): Boolean {
        val parts = location.split(", ")
        return parts.size == 2 &&
                parts.first().toDoubleOrNull() != null &&
                parts.last().toDoubleOrNull() != null
    }

    private fun handleCityIdWeather(lw: LocalWeather, cityId: String) {
        if (isWeatherCacheValid) {
            readJsonWeather(JSONFILE)
        } else {
            lw.fetchCurrentWeatherByCityId(cityId)
        }
    }

    private fun handleCoordinateWeather(lw: LocalWeather, coords: String) {
        val parts = coords.split(", ")
        val lat = parts.first().toDouble()
        val lon = parts.last().toDouble()

        if (isWeatherCacheValid) {
            readJsonWeather(JSONFILE)
        } else {
            lw.fetchCurrentWeatherByLocation(lat, lon)
        }
    }

    private fun handleCityNameWeather(lw: LocalWeather, cityName: String) {
        if (isWeatherCacheValid) {
            readJsonWeather(JSONFILE)
        } else {
            lw.fetchCurrentWeatherByCityName(cityName)
        }
    }

    private fun fetchGeoIPFallback(lw: LocalWeather) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geoJson = URL("http://api.sypexgeo.net").readText()
                if (geoJson.isNotEmpty()) {
                    val mJsonResponse = JSONObject(geoJson)
                    val mCityObj = mJsonResponse.getJSONObject("city")

                    when {
                        mCityObj.has("id") && !mCityObj.isNull("id") -> {
                            val mCode = mCityObj.getInt("id").toString()
                            if (isWeatherCacheValid) {
                                withContext(Dispatchers.Main) { readJsonWeather(JSONFILE) }
                            } else {
                                lw.fetchCurrentWeatherByCityId(mCode)
                            }
                        }

                        mCityObj.has("lat") && mCityObj.has("lon") -> {
                            val lat = mCityObj.getDouble("lat")
                            val lon = mCityObj.getDouble("lon")
                            if (isWeatherCacheValid) {
                                withContext(Dispatchers.Main) { readJsonWeather(JSONFILE) }
                            } else {
                                lw.fetchCurrentWeatherByLocation(lat, lon)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "GeoIP fallback failed", e)
            }
        }
    }

    private fun setupWeatherCallbacks(lw: LocalWeather) {
        lw.weatherCallback = object : LocalWeather.WeatherCallback {
            override fun onSuccess(weather: Weather) {
                writeJsonWeather(weather)
                readJsonWeather(JSONFILE)
            }

            override fun onFailure(exception: Throwable?) {
                Log.e(TAG, "Weather fetching failed: ${exception?.message}")
                LauncherApp.toast("Weather error: ${exception?.message}", true)
            }
        }

        lw.fetchCurrentLocation(object : LocalWeather.CurrentLocationCallback {
            override fun onSuccess(location: Location) {
                if (isWeatherCacheValid) {
                    readJsonWeather(JSONFILE)
                } else {
                    lw.fetchCurrentWeatherByLocation(location)
                }
            }

            override fun onFailure(failed: LocationFailedEnum) {
                Log.e(TAG, "Location fetching failed: $failed")
                fetchGeoIPFallback(lw)
            }
        })
    }

    private val isWeatherCacheValid: Boolean
        get() = File(JSONFILE).let { it.exists() && it.lastModified() + maxCacheAge > System.currentTimeMillis() }


    private fun writeJsonWeather(weather: Weather) {
        try {
            // Log.d(TAG, "writeJsonWeather JSON: ${gson.toJson(weather)}")
            File(JSONFILE).writeText(gson.toJson(weather))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write weather cache", e)
        }
    }

    private fun readJsonWeather(filePath: String) {
        try {
            val cachedWeather = gson.fromJson(
                File(filePath).bufferedReader().use { it.readText() },
                Weather::class.java
            )
            cachedWeather?.let { updateWeatherDetails(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read weather cache", e)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateWeatherDetails(weather: Weather) {
        // Get views
        val weatherVG = findViewById<ViewGroup?>(R.id.weather)
        val detailsVG = findViewById<ViewGroup?>(R.id.details)
        val curLocTV = findViewById<TextView?>(R.id.curLocation)?.apply {
            setupMarquee()
        }
        // Set Location Info
        curLocTV?.text = weather.name.ifEmpty { "" }
        // Set Weather Info
        weatherVG?.let { group ->
            findViewById<AppCompatImageView>(R.id.weather_icon)?.let { icon ->
                OpenWeatherIcons(this, weather.icons[0], icon)
                icon.visibility = View.VISIBLE
            }

            findViewById<TextView>(R.id.curTemp)?.text =
                "${weather.temperature.toInt()}${getTempUnit()}"

            group.visibility = View.GONE
        }
        // Set Weather details
        detailsVG?.let { details ->
            findViewById<TextView>(R.id.hilotemp)?.text = getHiLoTempText(weather)
            findViewById<TextView>(R.id.pressure)?.text = getPressureText(weather)
            findViewById<TextView>(R.id.humidity)?.text =
                getString(R.string.weather_humidity, weather.humidity.toInt())
            findViewById<TextView>(R.id.wind)?.text = getWindText(weather)
            findViewById<TextView>(R.id.wDescription)?.apply {
                text = weather.descriptions[0]
                setupMarquee()
            }

            details.visibility = View.GONE
        }
        // Show
        if (RowPreferences.showLocation(this)) {
            showLocation(weatherVG, detailsVG, curLocTV)
        } else {
            showWeather(weatherVG, detailsVG)
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

    private fun getTempUnit(): String = if (localWeather?.unit == Units.METRIC) "℃" else "℉"

    private fun getHiLoTempText(weather: Weather): String {
        return getString(
            R.string.weather_hilotemp,
            format(Locale.getDefault(), "%.0f", weather.minTemperature),
            format(Locale.getDefault(), "%.0f", weather.maxTemperature),
            getTempUnit()
        )
    }

    private fun getPressureText(weather: Weather): String {
        return if (localWeather?.lang == Lang.RUSSIAN) {
            getString(
                R.string.weather_pressure,
                format(Locale.getDefault(), "%.0f", weather.pressure / 1.333),
                getString(R.string.weather_pressure_mm)
            )
        } else {
            getString(
                R.string.weather_pressure,
                weather.pressure.toInt().toString(),
                getString(R.string.weather_pressure_hp)
            )
        }
    }

    private fun getWindText(weather: Weather): String {
        val speedUnit = if (localWeather?.unit == Units.METRIC) {
            getString(R.string.weather_speed_m)
        } else {
            getString(R.string.weather_speed_i)
        }
        val windDir = getCardinalDirection(weather.windAngle)

        return getString(
            R.string.weather_wind,
            format(Locale.getDefault(), "%.1f", weather.windSpeed),
            speedUnit,
            windDir
        )
    }

    private fun getCardinalDirection(angle: Double): String {
        val directions = if (localWeather?.lang == Lang.RUSSIAN)
            listOf("C", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ", "C")
        else
            listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW", "N")
        return directions[(angle % 360 / 45).roundToInt()]
    }

    private fun showLocation(
        weatherView: ViewGroup?,
        detailsView: ViewGroup?,
        curLocView: TextView?
    ) {
        weatherView?.visibility = View.GONE
        curLocView?.run {
            cancelWeatherAnimations()
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(fadeInDur * 2)
                .withEndAction {
                    animate()
                        .alpha(0f)
                        .setDuration(fadeOutDur)
                        .withEndAction {
                            visibility = View.GONE
                            showWeather(weatherView, detailsView)
                        }
                        .start()
                }
                .start()
        }
    }

    private fun showWeather(weatherView: ViewGroup?, detailsView: ViewGroup?) {
        cancelWeatherAnimations()

        // Initial state
        weatherView?.apply {
            alpha = 0f
            visibility = View.VISIBLE
        }
        detailsView?.apply {
            alpha = 0f
            visibility = View.VISIBLE
        }

        weatherAnimationJob = lifecycleScope.launch {
            while (isActive) {
                // 1. Fade in weather
                weatherView?.run {
                    animate()
                        .alpha(1f)
                        .setDuration(fadeInDur)
                        .withEndAction { /* no-op */ }
                        .start()
                }
                delay(fadeInDur)
                // 2. Show weather for 10 seconds
                delay(showCycleDur)
                // 3. Cross-fade to details
                weatherView?.run {
                    animate()
                        .alpha(0f)
                        .setDuration(fadeOutDur)
                        .start()
                }
                detailsView?.run {
                    animate()
                        .alpha(1f)
                        .setDuration(fadeInDur)
                        .start()
                }
                delay(maxOf(fadeOutDur, fadeInDur))
                // 4. Show details for 10 seconds
                delay(showCycleDur)
                // 5. Cross-fade back to weather
                detailsView?.run {
                    animate()
                        .alpha(0f)
                        .setDuration(fadeOutDur)
                        .start()
                }
                weatherView?.run {
                    animate()
                        .alpha(1f)
                        .setDuration(fadeInDur)
                        .start()
                }
                delay(maxOf(fadeOutDur, fadeInDur))
            }
        }
    }

    fun cancelWeatherAnimations() {
        weatherAnimationJob?.cancel()
        weatherAnimationJob = null

        // Immediately reset views to default state
        findViewById<ViewGroup?>(R.id.weather)?.apply {
            animate().cancel()
            alpha = 1f
        }
        findViewById<ViewGroup?>(R.id.details)?.apply {
            animate().cancel()
            alpha = 0f
        }
        findViewById<TextView>(R.id.curLocation)?.apply {
            animate().cancel()
            alpha = 0f
        }
    }

    private fun setShyMode(shy: Boolean, changeWallpaper: Boolean): Boolean {
        var changed = false
        if (mShyMode != shy) {
            mShyMode = shy
            changed = true
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
        var notifIndex = homeAdapter!!.getRowIndex(1) // 1 - Recommendations row
        mListView?.adapter?.let {
            notifIndex = (it.itemCount - 1).coerceAtMost(notifIndex)
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "currIndex:$currIndex, notifIndex:$notifIndex")
        if (notifIndex != -1 && currIndex != notifIndex) {
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "resetLauncherState -> set focus to Recommendations row"
            )
            if (smooth) {
                mListView?.setSelectedPositionSmooth(notifIndex)
            } else {
                mListView?.selectedPosition = notifIndex
                val focusedChild = mListView?.focusedChild
                focusedChild?.let { child ->
                    val focusedPosition = mListView?.getChildAdapterPosition(child)
                    if (focusedPosition == notifIndex) {
                        child.clearFocus()
                    }
                }
            }
            if (!(mShyMode || mNotificationsView == null)) {
                mNotificationsView?.setIgnoreNextActivateBackgroundChange()
            }
        } else if (notifIndex == -1) { // focus on 1st Apps cat (FAV|VIDEO|MUSIC|GAMES|APPS) in case No Notifications row
            val rowTypes = intArrayOf(
                7, // FAVORITES
                9, // VIDEO
                8, // MUSIC
                4, // GAMES
                3, // APPS
            ) // 0, 3, 4, 7, 8, 9 - SEARCH, APPS, GAMES, FAVORITES, MUSIC, VIDEO as in RowType()
            for (type in rowTypes) {
                var rowIndex = homeAdapter?.getRowIndex(type) ?: -1
                mListView?.adapter?.let {
                    rowIndex = (it.itemCount - 1).coerceAtMost(rowIndex)
                }
                if (rowIndex != -1) {
                    if (BuildConfig.DEBUG) Log.d(
                        TAG,
                        "resetLauncherState -> set focus to ${RowType.fromRowCode(type)} row"
                    )
                    if (smooth) {
                        mListView?.setSelectedPositionSmooth(rowIndex)
                    } else {
                        mListView?.selectedPosition = rowIndex
                    }
                    break
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

        val shyChanged = setShyMode(!isBackgroundVisibleBehind, true)

        if (!getInstance(applicationContext)!!.checkIfResortingIsNeeded() || isInEditMode) {
            forceResort = false
        }
        homeAdapter?.sortRowsIfNeeded(forceResort)
        WallpaperInstaller.getInstance(this)?.installWallpaperIfNeeded()
        wallpaperView?.shynessMode = mShyMode
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
        super.onPause()
        mResetAfterIdleEnabled = false
        mLaunchAnimation.cancel()
        cancelWeatherAnimations()
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
        } else if (state == 0 && mListView!!.selectedPosition <= homeAdapter!!.getRowIndex(1) && mNotificationsView!!.isNotEmpty()) {
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
                    val typeface = ResourcesCompat.getFont(this, R.font.sfuidisplay_thin)
                    val clockView: TextView? = findViewById<View>(R.id.clock) as ClockView?
                    typeface?.let {
                        clockView?.typeface = typeface
                    }
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
                    // weather widget update
                    initializeWeather()
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
        if (applicationContext.resources.getBoolean(R.bool.full_screen_settings_enabled)) {
            val intent = Intent(this@MainActivity, LegacyHomeScreenSettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
        } else if (applicationContext.resources.getBoolean(R.bool.side_panel_settings_enabled)) {
            val intent = Intent(this@MainActivity, SettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
        }
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
        val firstRun = PendingIntent.getActivity(this, 0, dummyIntent, FLAG_NO_CREATE) == null
        if (firstRun) {
            (getSystemService(ALARM_SERVICE) as AlarmManager)[AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 864000000000L] =
                PendingIntent.getActivity(this, 0, dummyIntent, 0)
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
