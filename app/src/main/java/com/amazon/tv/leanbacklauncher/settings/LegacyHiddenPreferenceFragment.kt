package com.amazon.tv.leanbacklauncher.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import com.amazon.tv.firetv.leanbacklauncher.util.SharedPreferencesUtil.Companion.instance
import com.amazon.tv.leanbacklauncher.BuildConfig
import com.amazon.tv.leanbacklauncher.R
import com.amazon.tv.leanbacklauncher.util.Util.refreshHome
import java.util.*

class LegacyHiddenPreferenceFragment : GuidedStepSupportFragment() {
    private var mActionToPackageMap: HashMap<Long, String>? = null

    companion object {
        private const val ACTION_ID_ALL_APPS = -1L
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance {
        return Guidance(getString(R.string.hidden_applications_title),getString(R.string.hidden_applications_desc), getString(R.string.settings_dialog_title), ResourcesCompat.getDrawable(resources, R.drawable.ic_settings_home, null))
    }

    override fun onResume() {
        super.onResume()
        loadHiddenApps()
    }

    private fun buildBannerFromIcon(icon: Drawable?): Drawable {
        val resources = resources
        val bannerWidth = resources.getDimensionPixelSize(R.dimen.preference_item_banner_width)
        val bannerHeight = resources.getDimensionPixelSize(R.dimen.preference_item_banner_height)
        val iconSize = resources.getDimensionPixelSize(R.dimen.preference_item_icon_size)
        val bitmap = Bitmap.createBitmap(bannerWidth, bannerHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(ResourcesCompat.getColor(resources, R.color.preference_item_banner_background, null))
        icon?.let {
            it.setBounds((bannerWidth - iconSize) / 2, (bannerHeight - iconSize) / 2, (bannerWidth + iconSize) / 2, (bannerHeight + iconSize) / 2)
            it.draw(canvas)
        }
        return BitmapDrawable(resources, bitmap)
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        val util = instance(requireActivity())
        if (action.id == ACTION_ID_ALL_APPS) {
            if (BuildConfig.DEBUG) Log.d("******", "ACTION_ID_ALL_APPS, ${action.isChecked}")
            util?.showAllApps(action.isChecked)
            // refresh home broadcast
            val activity = requireActivity()
            refreshHome(activity)
        } else {
            if (action.isChecked) {
                util?.hide(mActionToPackageMap!![action.id])
            } else {
                util?.unhide(mActionToPackageMap!![action.id])
            }
        }
    }

    private fun loadHiddenApps() {
        val util = instance(requireActivity())
        val packages: List<String> = ArrayList(util!!.hidden_apps())
        if (isAdded) {
            mActionToPackageMap = HashMap()
            val actions = ArrayList<GuidedAction>()
            actions.add(GuidedAction.Builder(activity)
                    .id(ACTION_ID_ALL_APPS)
                    .title(getString(R.string.show_all_apps))
                    .checked(util.isAllAppsShown())
                    .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                    .build()
            )
            var actionId: Long = 0
            val pm = activity!!.packageManager
            for (pkg in packages) {
                pkg?.let {
                    var hidden: Boolean
                    try {
                        val packageInfo = pm.getPackageInfo(pkg, 0)
                        var banner = pm.getApplicationBanner(packageInfo.applicationInfo)
                        banner = banner
                                ?: buildBannerFromIcon(pm.getApplicationIcon(packageInfo.applicationInfo))
                        hidden = util.isHidden(pkg)
                        if (hidden) // show only hidden apps
                            actions.add(GuidedAction.Builder(activity)
                                    .id(actionId)
                                    .title(pm.getApplicationLabel(packageInfo.applicationInfo))
                                    .icon(banner)
                                    .checkSetId(-1)
                                    .checked(hidden)
                                    .build()
                            )
                        mActionToPackageMap!![actionId] = packageInfo.packageName
                        actionId++
                    } catch (e: PackageManager.NameNotFoundException) {
                        e.printStackTrace()
                    }
                }
            }
            setActions(actions)
        }
    }
}