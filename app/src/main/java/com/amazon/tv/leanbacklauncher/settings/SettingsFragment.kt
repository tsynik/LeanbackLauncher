package com.amazon.tv.leanbacklauncher.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.isDigitsOnly
import androidx.fragment.app.Fragment
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat
import androidx.leanback.preference.LeanbackSettingsFragmentCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.*
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences
import com.amazon.tv.firetv.leanbacklauncher.util.FireTVUtils
import com.amazon.tv.firetv.leanbacklauncher.util.SharedPreferencesUtil
import com.amazon.tv.leanbacklauncher.BuildConfig
import com.amazon.tv.leanbacklauncher.LauncherApp
import com.amazon.tv.leanbacklauncher.MainActivity.Companion.JSONFILE
import com.amazon.tv.leanbacklauncher.R
import com.amazon.tv.leanbacklauncher.UpdateActivity
import com.amazon.tv.leanbacklauncher.apps.AppsManager
import com.amazon.tv.leanbacklauncher.util.Permission
import com.amazon.tv.leanbacklauncher.util.Updater
import com.amazon.tv.leanbacklauncher.util.Util
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class SettingsFragment : LeanbackSettingsFragmentCompat() {
    companion object {
        private val TAG by lazy {
            if (BuildConfig.DEBUG) ("[*]" + SettingsFragment::class.java.simpleName).take(
                21
            ) else SettingsFragment::class.java.simpleName
        }
        var needRestartHome = false
    }

    override fun onPreferenceStartInitialScreen() {
        needRestartHome = false // reset state
        startPreferenceFragment(LauncherSettingsFragment())
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat?,
        pref: Preference
    ): Boolean {
        val args: Bundle = pref.extras
        val f: Fragment = childFragmentManager.fragmentFactory.instantiate(
            requireActivity().classLoader, pref.fragment
        )
        f.arguments = args
        f.setTargetFragment(caller, 0)
        if (f is PreferenceFragmentCompat
            || f is PreferenceDialogFragmentCompat
        ) {
            startPreferenceFragment(f)
        } else {
            startImmersiveFragment(f)
        }
        return true
    }

    override fun onPreferenceStartScreen(
        caller: PreferenceFragmentCompat?,
        pref: PreferenceScreen
    ): Boolean {
        val fragment: Fragment = LauncherSettingsFragment()
        val args = Bundle(1)
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.key)
        fragment.arguments = args
        startPreferenceFragment(fragment)
        return true
    }

    override fun onStop() {
        super.onStop()
        if (needRestartHome) {
            if (BuildConfig.DEBUG) Log.d(TAG, "onStop() send refresh HOME broadcast")
            Util.refreshHome(LauncherApp.context)
        }
    }
}

/**
 * The fragment that is embedded in SettingsFragment
 */
class LauncherSettingsFragment : LeanbackPreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Load the prefs from an XML resource
        setPreferencesFromResource(R.xml.prefs, rootKey)

        findPreference<Preference>("version")?.apply {
            this.summary = "${getString(R.string.app_name)} v" + BuildConfig.VERSION_NAME
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ps = findPreference<PreferenceScreen>("root_prefs")
            ps?.removePreferenceRecursively("rec_sources")
        }
    }
}

/**
 * The fragment that is defined in prefs.xml
 */
class VersionPreferenceFragment : LeanbackPreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Load the prefs from an XML resource
        setPreferencesFromResource(R.xml.version_prefs, rootKey)

        findPreference<Preference>("update")?.let {
            lifecycleScope.launch(Dispatchers.IO) {
                if (Updater.check()) {
                    withContext(Dispatchers.Main) {
                        it.summary = getString(
                            R.string.update_app_found,
                            "${getString(R.string.app_name)} v" + Updater.getVersion()
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        it.summary = getString(R.string.update_no_updates)
                    }
                }
            }
        }
    }

    // https://developer.android.com/topic/libraries/architecture/coroutines
    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        if (preference?.key == "update") {
            lifecycleScope.launch(Dispatchers.IO) {
                if (Updater.check())
                    startActivity(Intent(activity, UpdateActivity::class.java))
                else
                    withContext(Dispatchers.Main) {
                        LauncherApp.toast(getString(R.string.update_no_updates))
                    }
            }
            return true
        } else if (preference?.key == "app_link") {
            if (!preference.summary.isNullOrEmpty())
                openBrowser(preference.summary.toString())
            return true
        }
        return super.onPreferenceTreeClick(preference)
    }

    private fun openBrowser(url: String) {
        if (url.isNotEmpty()) {
            val intent = Intent()
            intent.action = Intent.ACTION_VIEW
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            // pass the url to intent data
            intent.data = Uri.parse(url)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                LauncherApp.toast(R.string.failed_launch)
            }
        }
    }
}

/**
 * The fragment that is defined in prefs.xml
 */
class HomePreferenceFragment : LeanbackPreferenceFragmentCompat() {
    private val appContext = LauncherApp.context
    private val sortingMode = AppsManager.getSavedSortingMode(appContext)
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Load the prefs from an XML resource
        setPreferencesFromResource(R.xml.home_prefs, rootKey)
        findPreference<Preference>("apps_order")?.apply {
            this.summary =
                if (sortingMode === AppsManager.SortingMode.FIXED) getString(R.string.fixed_order_action_description)
                else getString(R.string.recency_order_action_description)
        }
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        if (preference?.key == "apps_order") {
            val curMode = AppsManager.getSavedSortingMode(appContext)
            if (curMode === AppsManager.SortingMode.FIXED) {
                AppsManager.saveSortingMode(appContext, AppsManager.SortingMode.RECENCY)
                preference.summary = getString(R.string.recency_order_action_description)
            } else {
                AppsManager.saveSortingMode(appContext, AppsManager.SortingMode.FIXED)
                preference.summary = getString(R.string.fixed_order_action_description)

            }
            return true
        }
        return super.onPreferenceTreeClick(preference)
    }
}

/**
 * The fragment that is defined in prefs.xml
 */
class HiddenPreferenceFragment : LeanbackPreferenceFragmentCompat() {
    private var screen: PreferenceScreen? = null

    companion object {
        private const val KEY_ID_ALL_APP = "show_all_apps"
        private const val HIDDEN_CAT_KEY = "hidden_apps_category"
    }

    private fun loadHiddenApps() {
        val prefUtil = SharedPreferencesUtil.instance(requireContext())
        val packages: List<String> = ArrayList(prefUtil!!.hiddenApps())
        val prefs = ArrayList<Preference>()

        val showAllAppsPref = SwitchPreference(context)
        showAllAppsPref.key = KEY_ID_ALL_APP
        showAllAppsPref.title = getString(R.string.show_all_apps)
        screen?.addPreference(showAllAppsPref) // show all apps switch

        val pm = requireActivity().packageManager
        for (pkg in packages) {
            if (pkg.isNotEmpty()) {
                try {
                    val packageInfo = pm.getPackageInfo(pkg, 0)
                    val hidden: Boolean = prefUtil.isHidden(pkg)
                    if (hidden) { // show only hidden apps
                        val icon =
                            pm.getApplicationIcon(packageInfo.applicationInfo) // ?: pm.getApplicationBanner(packageInfo.applicationInfo)
                        val appPreference = Preference(context)
                        appPreference.key = packageInfo.packageName
                        appPreference.title = pm.getApplicationLabel(packageInfo.applicationInfo)
                        appPreference.icon = icon
                        prefs.add(appPreference)
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    e.printStackTrace()
                }
            }
        }

        val hiddenCategory = PreferenceCategory(context)
        hiddenCategory.key = HIDDEN_CAT_KEY
        hiddenCategory.title = getString(R.string.hidden_applications_desc)
        screen?.addPreference(hiddenCategory)
        if (prefs.isNotEmpty()) {
            for (pref in prefs) {
                hiddenCategory.addPreference(pref)
            }
        } else {
            val emptyPref = Preference(context)
            emptyPref.key = "empty"
            emptyPref.title = getString(R.string.hidden_applications_empty)
            emptyPref.isEnabled = false
            emptyPref.isSelectable = false
            hiddenCategory.addPreference(emptyPref)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        screen = preferenceManager.createPreferenceScreen(context)
        screen?.title = getString(R.string.hidden_applications_title)
        loadHiddenApps()
        preferenceScreen = screen
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        val context = requireContext()
        val prefUtil = SharedPreferencesUtil.instance(context)

        return if (preference is SwitchPreference) { // show all apps switch
            if (preference.key == KEY_ID_ALL_APP) {
                // refresh home broadcast
                SettingsFragment.needRestartHome = true
            }
            true
        } else { // hidden apps list
            prefUtil?.unhide(preference?.key)
            screen?.findPreference<PreferenceCategory>(HIDDEN_CAT_KEY)
                ?.removePreference(preference)
            true
        }
    }
}

/**
 * The fragment that is defined in prefs.xml
 */
class RecommendationsPreferenceFragment : LeanbackPreferenceFragmentCompat(),
    RecommendationsPreferenceManager.LoadRecommendationPackagesCallback {
    private var mIdToPackageMap: HashMap<Long, String> = hashMapOf()
    private var mPreferenceManager: RecommendationsPreferenceManager? = null
    private var screen: PreferenceScreen? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mPreferenceManager = RecommendationsPreferenceManager(requireContext())
    }

    override fun onResume() {
        super.onResume()
        mPreferenceManager?.loadRecommendationsPackages(this)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        screen = preferenceManager.createPreferenceScreen(context)
        screen?.title = getString(R.string.recommendation_blacklist_action_title)
        preferenceScreen = screen
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        var recIdFromKey: Long = -1L
        if (preference!!.key.isDigitsOnly())
            recIdFromKey = preference.key.toLong()
        if (preference is CheckBoxPreference)
            mPreferenceManager?.savePackageBlacklisted(
                mIdToPackageMap[recIdFromKey],
                !preference.isChecked
            )
        return true
    }

    override fun onRecommendationPackagesLoaded(info: List<RecommendationsPreferenceManager.PackageInfo>?) {
        mIdToPackageMap = HashMap()
        val recs = ArrayList<Preference>()
        var recId: Long = 100000 // for shared prefs map
        if (info != null) {
            for (packageInfo in info) {
                val banner: Drawable? = if (packageInfo.banner != null) {
                    packageInfo.banner
                } else {
                    buildBannerFromIcon(packageInfo.icon)
                }
                val recPreference = CheckBoxPreference(context)
                recPreference.key = recId.toString()
                recPreference.title = packageInfo.appTitle
                recPreference.icon = banner
                recPreference.isChecked = !packageInfo.blacklisted
                recs.add(recPreference)
                mIdToPackageMap[recId] = packageInfo.packageName!!
                recId++
            }
        }
        if (recs.isNotEmpty()) {
            for (rec in recs) {
                screen?.addPreference(rec)
            }
        } else {
            val emptyPref = Preference(context)
            emptyPref.key = "empty"
            emptyPref.title = getString(R.string.rec_soures_empty)
            emptyPref.isEnabled = false
            emptyPref.isSelectable = false
            screen?.addPreference(emptyPref)
        }
    }

    private fun buildBannerFromIcon(icon: Drawable?): Drawable {
        val resources = resources
        val bannerWidth = resources.getDimensionPixelSize(R.dimen.preference_item_banner_width)
        val bannerHeight =
            resources.getDimensionPixelSize(R.dimen.preference_item_banner_height)
        val iconSize = resources.getDimensionPixelSize(R.dimen.preference_item_icon_size)
        val bitmap = Bitmap.createBitmap(bannerWidth, bannerHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(
            ResourcesCompat.getColor(
                resources,
                R.color.preference_item_banner_background,
                null
            )
        )
        icon?.let {
            it.setBounds(
                (bannerWidth - iconSize) / 2,
                (bannerHeight - iconSize) / 2,
                (bannerWidth + iconSize) / 2,
                (bannerHeight + iconSize) / 2
            )
            it.draw(canvas)
        }
        return BitmapDrawable(resources, bitmap)
    }
}

/**
 * The fragment that is defined in home_prefs.xml
 */
class BannersPreferenceFragment : LeanbackPreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Load the prefs from an XML resource
        setPreferencesFromResource(R.xml.banners_prefs, rootKey)
        findPreference<EditTextPreference>(getString(R.string.pref_banner_focus_frame_color))?.apply {
            val color = hexStringColor(RowPreferences.getFrameColor(context))
            this.summary = "$color (#AARRGGBB)"
        }
    }

    private fun hexStringColor(color: Int): String {
        return String.format("#%08X", -0x1 and color)
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        // refresh home broadcast
        SettingsFragment.needRestartHome = true
        return super.onPreferenceTreeClick(preference)
    }
}

/**
 * The fragment that is defined in home_prefs.xml
 */
class AppRowsPreferenceFragment : LeanbackPreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Load the prefs from an XML resource
        setPreferencesFromResource(R.xml.rows_prefs, rootKey)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ps = findPreference<PreferenceScreen>("rows_prefs")
            ps?.removePreferenceRecursively(getString(R.string.pref_enable_recommendations_row))
        }
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        val ctx = requireContext()
        if (preference?.key == getString(R.string.pref_enable_recommendations_row)) {
            val enabled = (preference as SwitchPreference).isChecked
            RowPreferences.setRecommendationsEnabled(ctx, enabled)
            if (enabled && FireTVUtils.isAmazonNotificationsEnabled(ctx)) {
                LauncherApp.toast(getString(R.string.recs_warning_sale))
            }
            // refresh home broadcast
            SettingsFragment.needRestartHome = true
            return true
        }
        // refresh home broadcast
        SettingsFragment.needRestartHome = true
        return super.onPreferenceTreeClick(preference)
    }
}

/**
 * The fragment that is defined in home_prefs.xml
 */
class WallpaperFragment : LeanbackPreferenceFragmentCompat() {

    override fun onResume() {
        super.onResume()
        // check permissions
        Permission.isReadStoragePermissionGranted(requireActivity())
        findPreference<Preference>("select_wallpaper")?.apply {
            this.summary = getWallpaperDesc(context)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Load the prefs from an XML resource
        setPreferencesFromResource(R.xml.wallpaper_prefs, rootKey)

        findPreference<Preference>("select_wallpaper")?.apply {
            this.summary = getWallpaperDesc(context)
        }
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        return when (preference?.key) {
            "reset_wallpaper" -> {
                resetWallpaper()
                fragmentManager?.popBackStack()
                true
            }
            else -> super.onPreferenceTreeClick(preference)
        }
    }

    private fun resetWallpaper(): Boolean {
        val context = requireContext()
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        pref.edit().remove("wallpaper_image").apply()
        val file = File(context.getExternalFilesDir(null), "background.jpg")
        if (file.exists()) {
            try {
                file.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // refresh home broadcast
        SettingsFragment.needRestartHome = true
        return true
    }

    private fun getWallpaperDesc(context: Context): String {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val imagePath = pref.getString("wallpaper_image", "")
        return if (!imagePath.isNullOrEmpty()) {
            imagePath
        } else {
            val file = File(context.getExternalFilesDir(null), "background.jpg")
            if (file.canRead()) {
                file.toString()
            }
            getString(R.string.select_wallpaper_action_desc)
        }
    }
}

/**
 * The fragment that is defined in wallpaper_prefs.xml
 */
class FileListFragment : LeanbackPreferenceFragmentCompat() {

    private var screen: PreferenceScreen? = null

    companion object {
        private var rootPath: String? = null
        private var dirName: String? = null

        /* Action ID definition */
        private const val KEY_BACK = 1
        private const val KEY_DIR = 2
        private const val KEY_SELECT = 3
    }

    override fun onResume() {
        super.onResume()
        if (fragmentManager?.backStackEntryCount == 3) // 3 on root dir
            rootPath = Environment.getExternalStorageDirectory().absolutePath
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        screen = preferenceManager.createPreferenceScreen(context)
        screen?.title = getString(R.string.select_wallpaper_action_title)
        preferenceScreen = screen

        if (rootPath.isNullOrEmpty())
            rootPath = Environment.getExternalStorageDirectory().absolutePath

        loadFileList()
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        val ctx = requireContext()
        val fm = fragmentManager

        when (preference?.key) {
            KEY_BACK.toString() -> {
                dirName?.let { rootPath = rootPath?.removeSuffix(it) }
                fm?.popBackStack()
                return true
            }
            KEY_DIR.toString() -> {
                rootPath = File(rootPath, preference.title.toString()).absolutePath
                dirName = preference.title.toString()
                val curFragContainerId = (requireView().parent as ViewGroup).id
                fm?.beginTransaction()?.replace(curFragContainerId, FileListFragment())
                    ?.addToBackStack(null)?.commit()
                return true
            }
            KEY_SELECT.toString() -> {
                val name = preference.title
                val file = File(rootPath, name.toString())
                if (file.canRead())
                    setWallpaper(ctx, file.path.toString())
                else
                    LauncherApp.toast(getString(R.string.file_no_access))
                dirName?.let { rootPath = rootPath?.removeSuffix(it) }
                fm?.popBackStack()
                return true
            }
            else -> return super.onPreferenceTreeClick(preference)
        }
    }

    private fun loadFileList() {
        var dir: File? = null
        var folders: ArrayList<File> = ArrayList()
        var images: ArrayList<File> = ArrayList()
        if (BuildConfig.DEBUG) Log.d("*****", "loadFileList($rootPath)")
        rootPath?.let { dir = File(it) }
        dir?.let {
            folders = dirReader(it)
            images = imageReader(it)
        }
        val prefs = ArrayList<Preference>()

        // back
        val backPref = Preference(context)
        backPref.key = KEY_BACK.toString()
        backPref.title = getString(R.string.goback)
        backPref.icon =
            ResourcesCompat.getDrawable(resources, R.drawable.ic_twotone_arrow_back_24, null)
        prefs.add(backPref)

        // directories
        if (folders.size > 0)
            folders.forEach {
                val dirPref = Preference(context)
                dirPref.key = KEY_DIR.toString()
                dirPref.title = it.name
                dirPref.icon =
                    ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.ic_twotone_folder_24,
                        null
                    )
                prefs.add(dirPref)
            }

        // images
        if (images.size > 0)
            images.forEach {
                val imagePref = Preference(context)
                imagePref.key = KEY_SELECT.toString()
                imagePref.title = it.name
                try {
                    val bannerWidth =
                        resources.getDimensionPixelSize(R.dimen.preference_item_banner_width)
                    val bannerHeight =
                        resources.getDimensionPixelSize(R.dimen.preference_item_banner_height)
                    Glide.with(requireContext())
                        .asDrawable()
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .load(Uri.fromFile(it))
                        .into(object : CustomTarget<Drawable?>(bannerWidth, bannerHeight) {
                            override fun onResourceReady(
                                resource: Drawable,
                                transition: Transition<in Drawable?>?
                            ) {
                                imagePref.icon = resource
                            }

                            override fun onLoadCleared(placeholder: Drawable?) {
                            }
                        })
                } catch (e: java.lang.Exception) {
                }
                prefs.add(imagePref)
            }

        // apply
        if (prefs.isNotEmpty()) {
            for (pref in prefs) {
                screen?.addPreference(pref)
            }
        }
    }

    private fun imageReader(root: File): ArrayList<File> {
        val imageList: ArrayList<File> = ArrayList()
        val images = root.listFiles { file ->
            (!file.isDirectory &&
                    file.name.endsWith(".jpeg") ||
                    file.name.endsWith(".jpg") ||
                    file.name.endsWith(".png"))
        }
        if (!images.isNullOrEmpty()) {
            for (image in images) {
                imageList.add(image.absoluteFile)
            }
        }
        return imageList
    }

    private fun dirReader(root: File): ArrayList<File> {
        val dirList: ArrayList<File> = ArrayList()
        val dirs = root.listFiles { file ->
            file.isDirectory && !file.name.startsWith(".")
        }
        if (!dirs.isNullOrEmpty())
            for (dir in dirs) {
                dirList.add(dir.absoluteFile)
            }
        return dirList
    }

    private fun setWallpaper(context: Context?, image: String): Boolean {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        if (image.isNotEmpty()) pref.edit().putString("wallpaper_image", image).apply()
        // refresh home broadcast
        SettingsFragment.needRestartHome = true
        return true
    }
}

/**
 * The fragment that is defined in prefs.xml
 */
class WeatherPreferenceFragment : LeanbackPreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Load the prefs from an XML resource
        setPreferencesFromResource(R.xml.weather_prefs, rootKey)
        //val ps = findPreference<PreferenceScreen>("weather_prefs")
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        // clean cached weather
        val cache = File(JSONFILE)
        if (cache.exists())
            cache.delete()
        // refresh home broadcast
        SettingsFragment.needRestartHome = true
        return super.onPreferenceTreeClick(preference)
    }
}