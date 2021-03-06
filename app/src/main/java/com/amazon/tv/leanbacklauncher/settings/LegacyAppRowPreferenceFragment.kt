package com.amazon.tv.leanbacklauncher.settings

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import com.amazon.tv.firetv.leanbacklauncher.apps.AppCategory
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences.areFavoritesEnabled
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences.areInputsEnabled
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences.areRecommendationsEnabled
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences.getAllAppsConstraints
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences.getAppsMax
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences.getEnabledCategories
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences.getFavoriteRowConstraints
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences.getRowConstraints
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences.setAllAppsMax
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences.setAppsMax
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences.setFavoriteRowMax
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences.setFavoriteRowMin
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences.setFavoritesEnabled
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences.setGamesEnabled
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences.setInputsEnabled
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences.setMusicEnabled
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences.setRecommendationsEnabled
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences.setRowMax
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences.setRowMin
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences.setVideosEnabled
import com.amazon.tv.firetv.leanbacklauncher.util.FireTVUtils.isLocalNotificationsEnabled
import com.amazon.tv.leanbacklauncher.R
import com.amazon.tv.leanbacklauncher.util.Util.refreshHome
import java.util.*

/**
 * Created by rockon999 on 3/25/18.
 */
class LegacyAppRowPreferenceFragment : GuidedStepSupportFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance {
        return Guidance(
                getString(R.string.edit_row_title),  // title
                getString(R.string.select_app_customize_rows_title),  // description
                getString(R.string.settings_dialog_title),  // breadcrumb (home_screen_order_action_title)
                ResourcesCompat.getDrawable(resources, R.drawable.ic_settings_home, null))
    }

    override fun onResume() {
        super.onResume()
        updateActions()
        startup++
    }

    override fun onPause() {
        super.onPause()
        startup = 0
    }

    private var musicIndex = 0
    private var gameIndex = 0
    private var videoIndex = 0
    private var favIndex = 0

    override fun onGuidedActionClicked(action: GuidedAction) {
        val id = action.id
        val catId = (id - 1) / 2
        val subId = ((id - 1) % 2).toInt()
        var enabled: Boolean
        val activity: Activity? = activity
        val categories = getEnabledCategories(activity!!)
        var value: Int

//        Log.d("******", "onGuidedActionClicked(id: $id) catId: $catId subId: $subId (favIndex: $favIndex videoIndex: $videoIndex musicIndex: $musicIndex gameIndex: $gameIndex)")

        if (catId == favIndex.toLong()) {
            when (subId) {
                0 -> {
                    enabled = areFavoritesEnabled(activity)
                    setFavoritesEnabled(activity, !enabled)
                }
                1 -> {
                    try {
                        value = action.description.toString().toInt()
                    } catch (nfe: NumberFormatException) {
                        value = 1
                    }
                    setFavoriteRowMax(activity, value)
                }
                2 -> {
                    try {
                        value = action.description.toString().toInt()
                    } catch (nfe: NumberFormatException) {
                        value = 1
                    }
                    setFavoriteRowMin(activity, value)
                }
            }
        } else if (catId == musicIndex.toLong()) {
            when (subId) {
                0 -> {
                    enabled = categories.contains(AppCategory.MUSIC)
                    setMusicEnabled(activity, !enabled)
                }
                1 -> {
                    try {
                        value = action.description.toString().toInt()
                    } catch (nfe: NumberFormatException) {
                        value = 1
                    }
                    setRowMax(AppCategory.MUSIC, activity, value)
                }
                2 -> {
                    try {
                        value = action.description.toString().toInt()
                    } catch (nfe: NumberFormatException) {
                        value = 1
                    }
                    setRowMin(AppCategory.MUSIC, activity, value)
                }
            }
        } else if (catId == videoIndex.toLong()) {
            when (subId) {
                0 -> {
                    enabled = categories.contains(AppCategory.VIDEO)
                    setVideosEnabled(activity, !enabled)
                }
                1 -> {
                    try {
                        value = action.description.toString().toInt()
                    } catch (nfe: NumberFormatException) {
                        value = 1
                    }
                    setRowMax(AppCategory.VIDEO, activity, value)
                }
                2 -> {
                    try {
                        value = action.description.toString().toInt()
                    } catch (nfe: NumberFormatException) {
                        value = 1
                    }
                    setRowMin(AppCategory.VIDEO, activity, value)
                }
            }
        } else if (catId == gameIndex.toLong()) {
            when (subId) {
                0 -> {
                    enabled = categories.contains(AppCategory.GAME)
                    setGamesEnabled(activity, !enabled)
                }
                1 -> {
                    try {
                        value = action.description.toString().toInt()
                    } catch (nfe: NumberFormatException) {
                        value = 1
                    }
                    setRowMax(AppCategory.GAME, activity, value)
                }
                2 -> {
                    try {
                        value = action.description.toString().toInt()
                    } catch (nfe: NumberFormatException) {
                        value = 1
                    }
                    setRowMin(AppCategory.GAME, activity, value)
                }
            }
        } else if (id == ACTION_ID_APPS_MAX.toLong()) {
            try {
                value = action.description.toString().toInt()
            } catch (nfe: NumberFormatException) {
                value = 1
            }
            setAllAppsMax(activity, value)
        } else if (id == ACTION_ID_APPS_ROW.toLong()) {
            try {
                value = action.description.toString().toInt()
            } catch (nfe: NumberFormatException) {
                value = 1
            }
            setAppsMax(activity, value)
        } else if (id == ACTION_ID_RECOMENDATIONS.toLong()) { // RECOMENDATIONS
            enabled = areRecommendationsEnabled(activity)
            setRecommendationsEnabled(activity, !enabled)
            if (!enabled && isLocalNotificationsEnabled(activity)) {
                Toast.makeText(activity, activity.getString(R.string.recs_warning_sale), Toast.LENGTH_LONG).show()
            }
        } else if (id == ACTION_ID_INPUTS.toLong()) { // INPUTS
            enabled = areInputsEnabled(activity)
            setInputsEnabled(activity, !enabled)
        }
        updateActions()
    }

    private fun updateActions() {

        val actions = ArrayList<GuidedAction>()
        val activity: Activity? = activity
        val categories = getEnabledCategories(activity!!)
        var statelabel: String
        var index = 0
        var state: Boolean

        // RECOMENDATIONS
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            state = areRecommendationsEnabled(activity)
            statelabel = if (state) getString(R.string.v7_preference_on) else getString(R.string.v7_preference_off)
            actions.add(GuidedAction.Builder(activity)
                    .id(ACTION_ID_RECOMENDATIONS.toLong())
                    .title(R.string.recs_row_title)
                    .description(statelabel)
                    .build()
            )
        }
        // INPUTS
        state = areInputsEnabled(activity)
        statelabel = if (state) getString(R.string.v7_preference_on) else getString(R.string.v7_preference_off)
        actions.add(GuidedAction.Builder(activity)
                .id(ACTION_ID_INPUTS.toLong())
                .title(R.string.inputs_row_title)
                .description(statelabel)
                .build()
        )
        // FAV
        state = areFavoritesEnabled(activity)
        statelabel = if (state) getString(R.string.v7_preference_on) else getString(R.string.v7_preference_off)
        actions.add(GuidedAction.Builder(activity)
                .id((++index).toLong())
                .title(R.string.favorites_row_title)
                .description(statelabel)
                .build()
        )
        var constraints = getFavoriteRowConstraints(activity)
        actions.add(GuidedAction.Builder(activity)
                .id((++index).toLong())
                .title(R.string.max_favorites_rows_title)
                .description(constraints[1].toString())
                .descriptionEditable(true)
                .descriptionEditInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED)
                .build()
        )
        favIndex = (index - 1) / 2
        // VIDEO
        state = categories.contains(AppCategory.VIDEO)
        statelabel = if (state) getString(R.string.v7_preference_on) else getString(R.string.v7_preference_off)
        actions.add(GuidedAction.Builder(activity)
                .id((++index).toLong())
                .title(R.string.videos_row_title)
                .description(statelabel)
                .build()
        )
        constraints = getRowConstraints(AppCategory.VIDEO, activity)
        actions.add(GuidedAction.Builder(activity)
                .id((++index).toLong())
                .title(R.string.max_videos_rows_title)
                .description(constraints[1].toString())
                .descriptionEditable(true)
                .descriptionEditInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED)
                .build()
        )
        videoIndex = (index - 1) / 2
        // MUSIC
        state = categories.contains(AppCategory.MUSIC)
        statelabel = if (state) getString(R.string.v7_preference_on) else getString(R.string.v7_preference_off)
        actions.add(GuidedAction.Builder(activity)
                .id((++index).toLong())
                .title(R.string.music_row_title)
                .description(statelabel)
                .build()
        )
        constraints = getRowConstraints(AppCategory.MUSIC, activity)
        actions.add(GuidedAction.Builder(activity)
                .id((++index).toLong())
                .title(R.string.max_music_rows_title)
                .description(constraints[1].toString())
                .descriptionEditable(true)
                .descriptionEditInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED)
                .build()
        )
        musicIndex = (index - 1) / 2
        // GAME
        state = categories.contains(AppCategory.GAME)
        statelabel = if (state) getString(R.string.v7_preference_on) else getString(R.string.v7_preference_off)
        actions.add(GuidedAction.Builder(activity)
                .id((++index).toLong())
                .title(R.string.games_row_title)
                .description(statelabel)
                .build()
        )
        constraints = getRowConstraints(AppCategory.GAME, activity)
        actions.add(GuidedAction.Builder(activity)
                .id((++index).toLong())
                .title(R.string.max_games_rows_title)
                .description(constraints[1].toString())
                .descriptionEditable(true)
                .descriptionEditInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED)
                .build()
        )
        gameIndex = (index - 1) / 2
        // ALL APPS
        // actions.add(new GuidedAction.Builder(activity).id(ACTION_ID_APPS).title(R.string.apps_row_title).build());
        constraints = getAllAppsConstraints(activity)
        val maxapps = getAppsMax(activity)
        actions.add(GuidedAction.Builder(activity)
                .id(ACTION_ID_APPS_MAX.toLong())
                .title(R.string.max_apps_rows_title)
                .description(constraints[1].toString())
                .descriptionEditable(true)
                .descriptionEditInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED)
                .build()
        )
        // Max Apps per row
        actions.add(GuidedAction.Builder(activity)
                .id(ACTION_ID_APPS_ROW.toLong())
                .title(R.string.max_apps_title)
                .description(maxapps.toString())
                .descriptionEditable(true)
                .descriptionEditInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED)
                .build()
        )
        setActions(actions) // APPLY

        // REFRESH HOME BC
        if (startup > 0) {
            val activity = requireActivity()
            refreshHome(activity)
        }
    }

    companion object {
        private const val ACTION_ID_APPS = 50
        private const val ACTION_ID_APPS_ROW = 51
        private const val ACTION_ID_APPS_MAX = 52
        private const val ACTION_ID_RECOMENDATIONS = 100
        private const val ACTION_ID_INPUTS = 200
        private var startup = 0
    }
}