package com.amazon.tv.leanbacklauncher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.*
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.*
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.location.Location
import android.media.tv.TvContract
import android.net.Uri
import android.os.*
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
import androidx.core.text.isDigitsOnly
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
import com.amazon.tv.leanbacklauncher.animation.*
import com.amazon.tv.leanbacklauncher.animation.AnimatorLifecycle.OnAnimationFinishedListener
import com.amazon.tv.leanbacklauncher.animation.EditModeMassFadeAnimator.EditMode
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
import com.amazon.tv.leanbacklauncher.util.*
import com.amazon.tv.leanbacklauncher.wallpaper.LauncherWallpaper
import com.amazon.tv.leanbacklauncher.wallpaper.WallpaperInstaller
import com.amazon.tv.leanbacklauncher.widget.EditModeView
import com.amazon.tv.leanbacklauncher.widget.EditModeView.OnEditModeUninstallPressedListener
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import de.interaapps.localweather.LocalWeather
import de.interaapps.localweather.Weather
import de.interaapps.localweather.utils.Lang
import de.interaapps.localweather.utils.LocationFailedEnum
import de.interaapps.localweather.utils.Units
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileDescriptor
import java.io.PrintWriter
import java.lang.String.format
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt


class MainActivity : AppCompatActivity(), OnEditModeChangedListener,
    OnEditModeUninstallPressedListener {
    private val TAG by lazy { if (BuildConfig.DEBUG) ("[*]" + javaClass.simpleName).take(21) else javaClass.simpleName }
    private var mAccessibilityManager: AccessibilityManager? = null
    var isInEditMode = false
        private set
    private var mAppWidgetHost: AppWidgetHost? = null
    private var mAppWidgetHostView: AppWidgetHostView? = null
    private var mAppWidgetManager: AppWidgetManager? = null
    private var mContentResolver: ContentResolver? = null
    private var mDelayFirstRecommendationsVisible = true
    private val mEditModeAnimation = AnimatorLifecycle()
    var editModeView: EditModeView? = null
        private set
    private var mEventLogger: LeanbackLauncherEventLogger? = null
    private var mFadeDismissAndSummonAnimations = false
    private val mHandler: Handler = MainActivityMessageHandler(this)

    // animation params
    private val showcycle: Long = TimeUnit.SECONDS.toMillis(10)
    private val fadeindur: Long = 500
    private val fadeoutdur: Long = 1000

    // weather cache
    private val maxCacheAge = TimeUnit.MINUTES.toMillis(10)

    private class MainActivityMessageHandler constructor(private val activity: MainActivity) :
        Handler() {
        override fun handleMessage(msg: Message) {
            var z = true
            when (msg.what) {
                1, 2 -> {
                    val mainActivity = activity
                    if (msg.what != 1) {
                        z = false
                    }
                    mainActivity.mIsIdle = z
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

    var homeAdapter: HomeScreenAdapter? = null
        private set
    private var mHomeScreenView: HomeScreenView? = null
    private val mIdleListeners = ArrayList<IdleListener>()
    private var mIdlePeriod = 0
    private var mIsIdle = false
    private var mKeepUiReset = false
    private val mLaunchAnimation = AnimatorLifecycle()
    private var mList: VerticalGridView? = null
    private val mMoveTaskToBack = Runnable {
        if (!moveTaskToBack(true)) {
            mLaunchAnimation.reset()
        }
    }
    private val mNotifListener: NotificationRowListener = object : NotificationRowListener {
        private var mHandler: Handler? = null
        private val mSelectFirstRecommendationRunnable = Runnable {
            if (mNotificationsView!!.adapter != null && mNotificationsView!!.adapter!!.itemCount > 0) {
                mNotificationsView?.setSelectedPositionSmooth(0)
            }
        }

        override fun onBackgroundImageChanged(imageUri: String?, signature: String?) {
            wallpaperView?.onBackgroundImageChanged(imageUri, signature)
        }

        override fun onSelectedRecommendationChanged(position: Int) {
            if (mKeepUiReset && mAccessibilityManager?.isEnabled != true && position > 0) {
                if (this.mHandler == null) {
                    this.mHandler = Handler()
                }
                this.mHandler?.post(mSelectFirstRecommendationRunnable)
            }
        }
    }

    private var mNotificationsView: NotificationRowView? = null
    private var mPackageReplacedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val packageName = intent?.data
            packageName?.let {
                if (packageName.toString().contains(context?.packageName + ".recommendations")) {
                    if (BuildConfig.DEBUG) Log.d(
                        TAG,
                        "Recommendations Service updated, reconnecting."
                    )
                    homeAdapter?.onReconnectToRecommendationsService()
                }
            }
        }
    }

    private var mHomeRefreshReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getBooleanExtra("RefreshHome", false) == true) {
                if (BuildConfig.DEBUG) Log.d(TAG, "RESTART HOME")
                recreate()
            }
        }
    }

    private val mPauseAnimation = AnimatorLifecycle()
    private var mRecommendationsAdapter: NotificationsAdapter? = null
    private val mRefreshHomeAdapter = Runnable {
        homeAdapter?.refreshAdapterData()
    }
    private var mResetAfterIdleEnabled = false
    private var mResetPeriod = 0
    private var mScrollManager: HomeScrollManager? = null
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
    private var mShyMode = false
    private var mStartingEditMode = false
    private var mUninstallRequested = false
    private var mUserInteracted = false
    var wallpaperView: LauncherWallpaper? = null
        private set

    interface IdleListener {
        fun onIdleStateChange(z: Boolean)
        fun onVisibilityChange(z: Boolean)
    }

    private var localWeather: LocalWeather? = null

    companion object {
        const val PERMISSIONS_REQUEST_LOCATION = 99
        const val UNINSTALL_CODE = 321
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
            if (packageManager.queryIntentActivities(tvIntent, 1).size > 0) {
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
                    Uri.parse("package:$packageName")
                )
                try {
                    startActivityForResult(intent, 0)
                } catch (e: Exception) {
                }
            }
        }
        // network monitor (request from HomeScreenAdapter)
        Permission.isLocationPermissionGranted(this)
        // FIXME: focus issues
        // mAccessibilityManager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        editModeView = findViewById(R.id.edit_mode_view)
        editModeView?.setUninstallListener(this)
        wallpaperView = findViewById(R.id.background_container)
        mAppWidgetManager = AppWidgetManager.getInstance(appContext)
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS))
            mAppWidgetHost = AppWidgetHost(this, 123)
        mList = findViewById(R.id.main_list_view)
        mList?.let { mlv ->
            mlv.setHasFixedSize(true)
            mlv.windowAlignment = BaseGridView.WINDOW_ALIGN_LOW_EDGE
            mlv.windowAlignmentOffset =
                resources.getDimensionPixelOffset(R.dimen.home_screen_selected_row_alignment)
            mlv.windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
            mlv.itemAlignmentOffset = 0
            mlv.itemAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
            mScrollManager = HomeScrollManager(this, mlv)
            mScrollManager?.addHomeScrollListener(wallpaperView!!)
            homeAdapter =
                HomeScreenAdapter(this, mScrollManager!!, mRecommendationsAdapter, editModeView!!)
            homeAdapter?.setOnEditModeChangedListener(this)
            mlv.setItemViewCacheSize(homeAdapter!!.itemCount)
            mlv.adapter = homeAdapter
            mlv.setOnChildViewHolderSelectedListener(object : OnChildViewHolderSelectedListener() {
                override fun onChildViewHolderSelected(
                    parent: RecyclerView,
                    child: RecyclerView.ViewHolder?,
                    position: Int,
                    subposition: Int
                ) {
                    homeAdapter?.onChildViewHolderSelected(parent, child, position)
                }
            })
            mlv.setAnimateChildLayout(false)
            val notifIndex = homeAdapter?.getRowIndex(1) // RowType.NOTIFICATIONS
            if (notifIndex != null && notifIndex != -1) {
                mlv.selectedPosition = notifIndex
            }
            val recAdapter = homeAdapter?.recommendationsAdapter
            recAdapter?.let { adapter ->
                addIdleListener(adapter)
            }
            mlv.setOnHierarchyChangeListener(object : OnHierarchyChangeListener {
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
                                    recAdapter?.setNotificationRowViewFlipper(homeScreenMessaging)
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
            mlv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    mScrollManager?.onScrolled(dy, currentScrollPos)
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    mScrollManager?.onScrollStateChanged(newState)
                }
            })
        }
        mShyMode = true
        val z: Boolean = !mShyMode
        setShyMode(z, true)
        //if (BuildConfig.DEBUG) Log.d(TAG, "OnCreate setShyMode($z, true), init mShyMode=$mShyMode")
        mIdlePeriod = resources.getInteger(R.integer.idle_period)
        mResetPeriod = resources.getInteger(R.integer.reset_period)
        mFadeDismissAndSummonAnimations = resources.getBoolean(R.bool.app_launch_animation_fade)
        mKeepUiReset = true
        homeAdapter?.onInitUi()
        mEventLogger = LeanbackLauncherEventLogger.getInstance(appContext)
        val filter = IntentFilter()
        filter.addAction("android.intent.action.PACKAGE_REPLACED")
        filter.addAction("android.intent.action.PACKAGE_ADDED")
        filter.addDataScheme("package")
        registerReceiver(mPackageReplacedReceiver, filter)
        // regiser RefreshHome broadcast
        val filterHome =
            IntentFilter(this.javaClass.name) // ACTION com.amazon.tv.leanbacklauncher.MainActivity
        registerReceiver(mHomeRefreshReceiver, filterHome)

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
        Log.d(TAG, "onDestroy()")
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

    fun onBackgroundVisibleBehindChanged(visible: Boolean) {
        setShyMode(!visible, true)
    }

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
                Intent("android.intent.action.UNINSTALL_PACKAGE", Uri.parse("package:$packageName"))
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

    val isWeatherCacheValid: Boolean
        get() = File(JSONFILE).lastModified() + maxCacheAge > System.currentTimeMillis()

    private fun initializeWeather() {
        localWeather?.let { lw ->
            // lang
            val ul = Locale.getDefault().isO3Language
            when {
                ul.equals("rus", true) -> lw.lang = Lang.RUSSIAN
                ul.equals("ukr", true) -> lw.lang = Lang.UKRAINIAN
                ul.equals("ita", true) -> lw.lang = Lang.ITALIAN
                ul.equals("fra", true) -> lw.lang = Lang.FRENCH
                ul.equals("esp", true) -> lw.lang = Lang.SPANISH
                ul.equals("deu", true) -> lw.lang = Lang.GERMAN
                else -> lw.lang = Lang.ENGLISH
            }
            // units
            lw.unit = if (RowPreferences.isImperialUnits(this)) Units.IMPERIAL else Units.METRIC

            if (RowPreferences.isUseLocationEnabled(this) && !Util.isAmazonDev(this)) {
                lw.useCurrentLocation = true
                lw.updateCurrentLocation = true
                lw.updateLocationInterval = TimeUnit.MINUTES.toMillis(10) // FIXME: no updates
            } else {
                try {
                    lw.useCurrentLocation = false
                } catch (e: Exception) {
                    // AirLocation.stopLocationUpdates -> NullPointerException: Listener must not be null
                }
                RowPreferences.getUserLocation(this)?.let { userLoc ->
                    if (userLoc.isNotEmpty() && !RowPreferences.isUseLocationEnabled(this))
                        if (userLoc.isDigitsOnly()) { // assume city id, ex. 524901
                            //if (BuildConfig.DEBUG) Log.d(TAG, "fetchCurrentWeatherByCityId($userLoc)")
                            // CACHE
                            if (isWeatherCacheValid) {
                                readJsonWeather(JSONFILE)
                            } else
                                lw.fetchCurrentWeatherByCityId(userLoc)
                        } else if (userLoc.split(", ").size == 2 &&
                            userLoc.split(", ").first().toDoubleOrNull() != null &&
                            userLoc.split(", ").last().toDoubleOrNull() != null
                        ) { // assume coordinates, ex. 45.75 47.61
                            val lat = userLoc.split(", ").first().toDouble()
                            val lon = userLoc.split(", ").last().toDouble()
                            //if (BuildConfig.DEBUG) Log.d(TAG, "fetchCurrentWeatherByLocation($lat,$lon)")
                            // CACHE
                            if (isWeatherCacheValid)
                                readJsonWeather(JSONFILE)
                            else
                                lw.fetchCurrentWeatherByLocation(lat, lon)
                        } else {
                            //if (BuildConfig.DEBUG) Log.d(TAG, "fetchCurrentWeatherByCityName($userLoc)")
                            // CACHE
                            if (isWeatherCacheValid)
                                readJsonWeather(JSONFILE)
                            else
                                lw.fetchCurrentWeatherByCityName(userLoc)
                        }
                    else if (Util.isAmazonDev(this)) { // no user setting or Amazon, fallback to GeoIP
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val geoJson = URL("http://api.sypexgeo.net").readText()
                                if (geoJson.isNotEmpty()) {
                                    //if (BuildConfig.DEBUG) Log.d(TAG, "Use GeoIP as fallback, json:$geoJson")
                                    val mJsonResponse = JSONObject(geoJson)
                                    val mCityObj = mJsonResponse.getJSONObject("city")
                                    if (mCityObj.has("id") && !mCityObj.isNull("id")) {
                                        val mCode: Int = mCityObj.getInt("id")
                                        //if (BuildConfig.DEBUG) Log.d(TAG, "fetchCurrentWeatherByCityId($mCode)")
                                        // CACHE
                                        if (isWeatherCacheValid)
                                            withContext(Dispatchers.Main) {
                                                readJsonWeather(JSONFILE)
                                            }
                                        else
                                            lw.fetchCurrentWeatherByCityId(id = mCode.toString())
                                    } else if (
                                        mCityObj.has("lat") &&
                                        mCityObj.has("lon") &&
                                        !mCityObj.isNull("lat") &&
                                        !mCityObj.isNull("lon")
                                    ) {
                                        val lat: Double = mCityObj.getDouble("lat")
                                        val lon: Double = mCityObj.getDouble("lon")
                                        //if (BuildConfig.DEBUG) Log.d(TAG, "fetchCurrentWeatherByLocation($lat,$lon)")
                                        // CACHE
                                        if (isWeatherCacheValid)
                                            withContext(Dispatchers.Main) {
                                                readJsonWeather(JSONFILE)
                                            }
                                        else
                                            lw.fetchCurrentWeatherByLocation(lat, lon)
                                    }
                                }
                            } catch (e: Exception) {
                                // unused
                            }
                        }
                    } else
                        LauncherApp.toast(R.string.user_location_warning, true)
                }
            }

            lw.weatherCallback = object : LocalWeather.WeatherCallback {
                override fun onSuccess(weather: Weather) {
                    //if (BuildConfig.DEBUG) Log.d(TAG, "LocalWeather onSuccess() -> updateWeatherDetails()")
                    //updateWeatherDetails(weather)
                    // CACHE
                    writeJsonWeather(weather)
                    readJsonWeather(JSONFILE)
                }

                override fun onFailure(exception: Throwable?) {
                    Log.e(TAG, "Weather fetching exception ${exception!!.message!!}")
                    LauncherApp.toast("Weather error: ${exception!!.message!!}", true)
                }
            }
            // only used when useCurrentLocation is true
            lw.fetchCurrentLocation(object : LocalWeather.CurrentLocationCallback {
                override fun onSuccess(location: Location) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Location fetching success")
                    // CACHE
                    if (isWeatherCacheValid)
                        readJsonWeather(JSONFILE)
                    else
                        lw.fetchCurrentWeatherByLocation(location)
                }

                override fun onFailure(failed: LocationFailedEnum) {
                    Log.e(TAG, "Location fetching failed $failed")
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val geoJson = URL("http://api.sypexgeo.net").readText()
                            if (geoJson.isNotEmpty()) {
                                //if (BuildConfig.DEBUG) Log.d(TAG, "Use GeoIP as fallback, json:$geoJson")
                                val mJsonResponse = JSONObject(geoJson)
                                val mCityObj = mJsonResponse.getJSONObject("city")
                                if (mCityObj.has("id") && !mCityObj.isNull("id")) {
                                    val mCode: Int = mCityObj.getInt("id")
                                    //if (BuildConfig.DEBUG) Log.d(TAG, "fetchCurrentWeatherByCityId($mCode)")
                                    // CACHE
                                    if (isWeatherCacheValid)
                                        withContext(Dispatchers.Main) {
                                            readJsonWeather(JSONFILE)
                                        }
                                    else
                                        lw.fetchCurrentWeatherByCityId(id = mCode.toString())
                                } else if (
                                    mCityObj.has("lat") &&
                                    mCityObj.has("lon") &&
                                    !mCityObj.isNull("lat") &&
                                    !mCityObj.isNull("lon")
                                ) {
                                    val lat: Double = mCityObj.getDouble("lat")
                                    val lon: Double = mCityObj.getDouble("lon")
                                    //if (BuildConfig.DEBUG) Log.d(TAG, "fetchCurrentWeatherByLocation($lat,$lon)")
                                    // CACHE
                                    if (isWeatherCacheValid)
                                        withContext(Dispatchers.Main) {
                                            readJsonWeather(JSONFILE)
                                        }
                                    else
                                        lw.fetchCurrentWeatherByLocation(lat, lon)
                                }
                            }
                        } catch (e: Exception) {
                            // unused
                        }
                    }
                }
            })
        }
    }

    private fun writeJsonWeather(weather: Weather) {
        //val gsonWeather = Gson().toJson(weather)
        //File(cacheFile).writeText(gsonWeather)
        val gsonPrettyWeather = GsonBuilder().setPrettyPrinting().create().toJson(weather)
        //if (BuildConfig.DEBUG) Log.d(TAG, "writeJsonWeather()")
        File(JSONFILE).writeText(gsonPrettyWeather)
    }

    private fun readJsonWeather(f: String) {
        val gson = Gson()
        val bufferedReader: BufferedReader = File(f).bufferedReader()
        val input = bufferedReader.use { it.readText() }
        val cachedWeather = gson.fromJson(input, Weather::class.java)
        //if (BuildConfig.DEBUG) Log.d(TAG, "readJsonWeather -> updateWeatherDetails()")
        cachedWeather?.let {
            updateWeatherDetails(cachedWeather)
        }
    }

    private fun fadeIn(view: View, alpha: Float) { // in transition
        view.animate()
            .alpha(alpha).duration = fadeindur
    }

    private fun fadeOut(view: View, alpha: Float) { // out transition
        view.animate()
            .alpha(alpha).duration = fadeoutdur
    }

    private fun showLocation() {
        val weatherVG: ViewGroup? = findViewById(R.id.weather)
        val curLocTV: TextView? = findViewById(R.id.curLocation)

        lifecycleScope.launch(Dispatchers.Main) {
            weatherVG?.visibility = View.GONE
            curLocTV?.let {
                it.visibility = View.VISIBLE
                it.alpha = 0.0f
                fadeIn(it, 1.0f)
                delay(showcycle)
                // hide
                fadeOut(it, 0.0f)
                it.animate()?.apply {
                    setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            curLocTV.visibility = View.GONE
                            showWeather()
                        }
                    })
                }
            }
        }
    }

    fun showWeather() {
        val weatherVG: ViewGroup? = findViewById(R.id.weather)
        val detailsVG: ViewGroup? = findViewById(R.id.details)

        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                weatherVG?.visibility = View.VISIBLE
                detailsVG?.alpha = 0.0f
                detailsVG?.visibility = View.VISIBLE
            }
            //TODO exit from cycle when close
            while (true) {
                val animw = weatherVG?.animate() ?: break
                val animd = detailsVG?.animate() ?: break

                animw.duration = fadeindur
                animd.duration = fadeoutdur

                Handler(Looper.getMainLooper()).post {
                    animw.alpha(1.0f)
                    animd.alpha(0.0f)
                }
                withContext(Dispatchers.Main) {
                    weatherVG.alpha = 0.0f
                }
                animd.withEndAction {
                    Handler(Looper.getMainLooper()).postDelayed({
                        val animwn = weatherVG.animate() ?: return@postDelayed
                        val animdn = detailsVG.animate() ?: return@postDelayed

                        animwn.duration = fadeindur
                        animdn.duration = fadeoutdur

                        animwn.alpha(0.0f)
                        animdn.alpha(1.0f)

                        animwn.start()
                        animdn.start()
                    }, showcycle)
                }

                withContext(Dispatchers.Main) {
                    animw.start()
                    animd.start()
                }

                // wait animation
                delay(fadeindur)
                // wait pause
                delay(showcycle)
                // wait animation
                delay(fadeoutdur)
                // wait pause
                delay(showcycle)
            }
        }
    }

    private fun getCardinalDirection(angle: Double): String {
        // ("↑", "↗", "→", "↘", "↓", "↙", "←", "↖")
        val directions = if (localWeather!!.lang == Lang.RUSSIAN) listOf(
            "C",
            "СВ",
            "В",
            "ЮВ",
            "Ю",
            "ЮЗ",
            "З",
            "СЗ",
            "C"
        )
        else
            listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW", "N")
        return directions[(angle % 360 / 45).roundToInt()]
    }

    private fun updateWeatherDetails(weather: Weather) {
        val weatherVG: ViewGroup? = findViewById(R.id.weather)
        val detailsVG: ViewGroup? = findViewById(R.id.details)
        val curLocTV: TextView? = findViewById(R.id.curLocation)

        val tempunit = if (localWeather!!.unit == Units.METRIC) "℃" else "℉"

        // city info
        curLocTV?.let { loc ->
            val city = weather.name
            if (city.isNotEmpty()) loc.text = city else loc.text = ""
            loc.apply { // marque scroll
                ellipsize = TextUtils.TruncateAt.MARQUEE
                isSingleLine = true
                marqueeRepeatLimit = -1
                isSelected = true
                isFocusableInTouchMode = false
                isFocusable = false
            }
        }
        // weather info
        weatherVG?.let { group ->
            // icon
            val icon = findViewById<AppCompatImageView>(R.id.weather_icon)
            icon?.let {
                OpenWeatherIcons(this@MainActivity, weather.icons[0], it)
                //it.contentDescription = weather.descriptions[0]
                it.visibility = View.VISIBLE
            }
            // temperature
            val curTemp = findViewById<TextView>(R.id.curTemp)
            curTemp?.let { it.text = weather.temperature.toInt().toString() + tempunit }
            // initial visibility
            group.visibility = View.GONE
        }
        // weather details
        detailsVG?.let { details ->
            // hi / lo temp
            val hilo = findViewById<TextView>(R.id.hilotemp)
            hilo?.let {
                it.text = getString(
                    R.string.weather_hilotemp,
                    format(Locale.getDefault(), "%.0f", weather.minTemperature),
                    format(Locale.getDefault(), "%.0f", weather.maxTemperature),
                    tempunit
                )
            }
            // pressure
            val pressure = findViewById<TextView>(R.id.pressure)
            pressure?.let {
                it.text = if (localWeather!!.lang == Lang.RUSSIAN) getString(
                    R.string.weather_pressure,
                    format(Locale.getDefault(), "%.0f", weather.pressure / 1.333), // mmHg
                    getString(R.string.weather_pressure_mm)
                )
                else
                    getString(
                        R.string.weather_pressure,
                        weather.pressure.toInt().toString(), // hPa
                        getString(R.string.weather_pressure_hp)
                    )
            }
            // humidity
            val hum = findViewById<TextView>(R.id.humidity)
            hum?.let { it.text = getString(R.string.weather_humidity, weather.humidity.toInt()) }
            // wind
            val speedunit =
                if (localWeather!!.unit == Units.METRIC) getString(R.string.weather_speed_m) else getString(
                    R.string.weather_speed_i
                )
            val winddir = getCardinalDirection(weather.windAngle)
            val wind = findViewById<TextView>(R.id.wind)
            wind?.let {
                it.text = getString(
                    R.string.weather_wind,
                    format(Locale.getDefault(), "%.1f", weather.windSpeed),
                    speedunit,
                    winddir
                )
            }
            // desc
            val desc = findViewById<TextView>(R.id.wDescription)
            desc?.let {
                it.text = weather.descriptions[0]
            }
            desc?.apply { // marque scroll
                ellipsize = TextUtils.TruncateAt.MARQUEE
                isSingleLine = true
                marqueeRepeatLimit = -1
                isSelected = true
                isFocusableInTouchMode = false
                isFocusable = false
            }
            // initial visibility
            details.visibility = View.GONE
        }
        // show
        if (RowPreferences.showLocation(this)) {
            showLocation()
        } else
            showWeather()
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
        } catch (ignored: Throwable) {
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
        } catch (ignored: Throwable) {
        }
    }

    private fun dismissLauncher(): Boolean {
        if (mShyMode) {
            return false
        }
        mLaunchAnimation.init(
            LauncherDismissAnimator(
                mList,
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
                            MassSlideAnimator.Builder(mList)
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
                MassSlideAnimator.Builder(mList)
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
        mList?.selectedPosition = homeAdapter!!.getRowIndex(rowType)
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
        val currIndex = mList!!.selectedPosition
        var notifIndex = homeAdapter!!.getRowIndex(1) // Recomendations row
        mList?.adapter?.let {
            notifIndex = (it.itemCount - 1).coerceAtMost(notifIndex)
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "currIndex:$currIndex, notifIndex:$notifIndex")
        if (notifIndex != -1 && currIndex != notifIndex) {
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "resetLauncherState -> set focus to Recommendations row"
            )
            if (smooth) {
                mList?.setSelectedPositionSmooth(notifIndex)
            } else {
                mList?.selectedPosition = notifIndex
                val focusedChild = mList?.focusedChild
                focusedChild?.let { child ->
                    val focusedPosition = mList?.getChildAdapterPosition(child)
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
//                0, // SEARCH
                7, // FAVORITES
                9, // VIDEO
                8, // MUSIC
                4, // GAMES
                3, // APPS
            ) // 0, 3, 4, 7, 8, 9 - SEARCH, APPS, GAMES, FAVORITES, MUSIC, VIDEO as in RowType()
            for (type in rowTypes) {
                var rowIndex = homeAdapter?.getRowIndex(type) ?: -1
                mList?.adapter?.let {
                    rowIndex = (it.itemCount - 1).coerceAtMost(rowIndex)
                }
                if (rowIndex != -1) {
                    if (BuildConfig.DEBUG) Log.d(
                        TAG,
                        "resetLauncherState -> set focus to ${RowType.fromRowCode(type)} row"
                    )
                    if (smooth) {
                        mList?.setSelectedPositionSmooth(rowIndex)
                    } else {
                        mList?.selectedPosition = rowIndex
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
            } catch (ignored: Throwable) {
            }
            return false
        }

    override fun onStart() {
        var z = true
        super.onStart()
        mResetAfterIdleEnabled = false
        try {
            mAppWidgetHost?.startListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (isBackgroundVisibleBehind) {
            //if (BuildConfig.DEBUG) Log.d(TAG, "onStart: BackgroundVisibleBehind")
            z = false
        }
        setShyMode(z, true)
        //if (BuildConfig.DEBUG) Log.d(TAG, "onStart: setShyMode($z,changeWallpaper:true)")
        wallpaperView?.resetBackground()
        homeAdapter?.refreshAdapterData()
        if (mKeepUiReset) {
            resetLauncherState(false)
        }

        if (!mStartingEditMode) {
            if (!mLaunchAnimation.isInitialized) {
                mLaunchAnimation.init(
                    LauncherReturnAnimator(
                        mList,
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
        var z = true

        super.onResume()

        if (isBackgroundVisibleBehind) {
            //if (BuildConfig.DEBUG) Log.d(TAG, "onResume: BackgroundVisibleBehind")
            z = false
        }
        val shyChanged = setShyMode(z, true)
        //if (BuildConfig.DEBUG) Log.d(TAG, "onResume: setShyMode($z,changeWallpaper:true) shyChanged:$shyChanged")
        if (!getInstance(applicationContext)!!.checkIfResortingIsNeeded() || isInEditMode) {
            forceResort = false
        }
        homeAdapter?.sortRowsIfNeeded(forceResort)
        WallpaperInstaller.getInstance(this)?.installWallpaperIfNeeded()
        wallpaperView?.shynessMode = mShyMode
        if (shyChanged) {
            wallpaperView?.resetBackground()
        }
        if (mShyMode && mNotificationsView != null) {
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
        mPauseAnimation.init(LauncherPauseAnimator(mList), null, 0.toByte())
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
        //if (BuildConfig.DEBUG) Log.d(TAG, "onStop: setShyMode(false,changeWallpaper:false)")
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
        mLaunchAnimation.dump(prefix, writer, mList)
        homeAdapter?.dump(prefix, writer)
    }

    private val currentScrollPos: Int
        get() {
            var pos = 0
            var topView = -1
            var i = 0
            while (i < mList!!.childCount) {
                val v = mList!!.getChildAt(i)
                if (v == null || v.top > 0) {
                    i++
                } else {
                    topView = mList!!.getChildAdapterPosition(v)
                    if (v.measuredHeight > 0) {
                        pos = (homeAdapter!!.getScrollOffset(topView)
                            .toFloat() * (abs(v.top).toFloat() / v.measuredHeight.toFloat()) * -1.0f).toInt()
                    }
                    topView--
                    while (topView >= 0) {
                        pos -= homeAdapter!!.getScrollOffset(topView)
                        topView--
                    }
                    return pos
                }
            }
            topView--
            while (topView >= 0) {
                pos -= homeAdapter!!.getScrollOffset(topView)
                topView--
            }
            return pos
        }

    private fun onNotificationRowStateUpdate(state: Int) {
        //if (BuildConfig.DEBUG) Log.d(TAG, "onNotificationRowStateUpdate(state: " + state + "), active row position: " + mList!!.selectedPosition)
        if (state == 1 || state == 2) {
            if (!mUserInteracted) {
                val searchIndex = homeAdapter!!.getRowIndex(0)
                if (searchIndex != -1) {
                    // focus on Search in case no recs yet
                    mList?.selectedPosition = searchIndex
                    mList?.getChildAt(searchIndex)?.requestFocus()
                    //if (BuildConfig.DEBUG) Log.d(TAG, "select search row and requestFocus()")
                }
            }
        } else if (state == 0 && mList!!.selectedPosition <= homeAdapter!!.getRowIndex(1) && mNotificationsView!!.childCount > 0) {
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
            val selectItem = mList!!.focusedChild
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
                                icon?.breath()
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
                        icon?.breath()
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
            for (i in 0 until mList!!.childCount) {
                val activeFrame = mList?.getChildAt(i)
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
        val firstRun = PendingIntent.getActivity(this, 0, dummyIntent, 536870912) == null
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
                    mList,
                    view,
                    mLaunchAnimation.lastKnownEpicenter,
                    findViewById<View>(R.id.click_circle_layer) as ImageView,
                    color,
                    homeAdapter!!.rowHeaders,
                    mHomeScreenView
                )
            } else {
                LauncherLaunchAnimator(
                    mList,
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
        mList?.rootView?.viewTreeObserver?.addOnGlobalLayoutListener(object :
            OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                mList?.rootView?.viewTreeObserver?.removeOnGlobalLayoutListener(this)
                if (mLaunchAnimation.isScheduled) {
                    mLaunchAnimation.prime()
                }
            }
        })
        mList?.requestLayout()
    }

    private fun checkLaunchPointPositions() {
        if (!mLaunchAnimation.isRunning && checkViewHierarchy(mList)) {
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
