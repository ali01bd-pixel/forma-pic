package com.ali.formapic.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ali.formapic.domain.model.AspectRatioOption
import com.ali.formapic.domain.model.CameraMode
import com.ali.formapic.domain.model.GridOverlayType
import com.ali.formapic.domain.model.RenderingPreference
import com.ali.formapic.domain.model.VideoQualityOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "forma_pic_settings")

data class FormaPicSettings(
    val defaultCameraMode: CameraMode = CameraMode.AI_AUTO,
    val aspectRatio: AspectRatioOption = AspectRatioOption.RATIO_4_3,
    val videoQuality: VideoQualityOption = VideoQualityOption.FHD_1080P,
    val videoFrameRate: Int = 30,
    val filenamePrefix: String = "FORMA_",
    val saveSubfolder: String = "DCIM/FORMA_PIC",
    val shutterSoundEnabled: Boolean = true,
    val gridOverlay: GridOverlayType = GridOverlayType.RULE_OF_THIRDS,
    val showHorizonLevel: Boolean = true,
    val timerSeconds: Int = 0, // 0, 3, 5, 10
    val burstShotCount: Int = 5, // 3, 5, 10
    val processingStrength: Float = 0.80f, // 0.0 to 1.0
    val renderingPreference: RenderingPreference = RenderingPreference.NATURAL,
    val saveRawAndJpeg: Boolean = true,
    val keepOriginalWhenEdited: Boolean = true,
    val frontCameraMirror: Boolean = true,
    val geotaggingEnabled: Boolean = false,
    val recordAudioInVideo: Boolean = true,
    val highEfficiencyHeif: Boolean = false
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val DEFAULT_MODE = stringPreferencesKey("default_camera_mode")
        val ASPECT_RATIO = stringPreferencesKey("aspect_ratio")
        val VIDEO_QUALITY = stringPreferencesKey("video_quality")
        val VIDEO_FPS = intPreferencesKey("video_fps")
        val FILENAME_PREFIX = stringPreferencesKey("filename_prefix")
        val SHUTTER_SOUND = booleanPreferencesKey("shutter_sound")
        val GRID_OVERLAY = stringPreferencesKey("grid_overlay")
        val SHOW_LEVEL = booleanPreferencesKey("show_horizon_level")
        val TIMER_SECONDS = intPreferencesKey("timer_seconds")
        val BURST_COUNT = intPreferencesKey("burst_count")
        val PROCESSING_STRENGTH = floatPreferencesKey("processing_strength")
        val RENDERING_PREF = stringPreferencesKey("rendering_preference")
        val SAVE_RAW_JPEG = booleanPreferencesKey("save_raw_and_jpeg")
        val KEEP_ORIGINAL = booleanPreferencesKey("keep_original_when_edited")
        val FRONT_MIRROR = booleanPreferencesKey("front_camera_mirror")
        val GEOTAGGING = booleanPreferencesKey("geotagging_enabled")
        val RECORD_AUDIO = booleanPreferencesKey("record_audio_in_video")
        val HEIF_ENABLED = booleanPreferencesKey("high_efficiency_heif")
    }

    val settingsFlow: Flow<FormaPicSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            FormaPicSettings(
                defaultCameraMode = prefs[Keys.DEFAULT_MODE]?.let {
                    runCatching { CameraMode.valueOf(it) }.getOrNull()
                } ?: CameraMode.AI_AUTO,
                aspectRatio = prefs[Keys.ASPECT_RATIO]?.let {
                    runCatching { AspectRatioOption.valueOf(it) }.getOrNull()
                } ?: AspectRatioOption.RATIO_4_3,
                videoQuality = prefs[Keys.VIDEO_QUALITY]?.let {
                    runCatching { VideoQualityOption.valueOf(it) }.getOrNull()
                } ?: VideoQualityOption.FHD_1080P,
                videoFrameRate = prefs[Keys.VIDEO_FPS] ?: 30,
                filenamePrefix = prefs[Keys.FILENAME_PREFIX] ?: "FORMA_",
                shutterSoundEnabled = prefs[Keys.SHUTTER_SOUND] ?: true,
                gridOverlay = prefs[Keys.GRID_OVERLAY]?.let {
                    runCatching { GridOverlayType.valueOf(it) }.getOrNull()
                } ?: GridOverlayType.RULE_OF_THIRDS,
                showHorizonLevel = prefs[Keys.SHOW_LEVEL] ?: true,
                timerSeconds = prefs[Keys.TIMER_SECONDS] ?: 0,
                burstShotCount = prefs[Keys.BURST_COUNT] ?: 5,
                processingStrength = prefs[Keys.PROCESSING_STRENGTH] ?: 0.80f,
                renderingPreference = prefs[Keys.RENDERING_PREF]?.let {
                    runCatching { RenderingPreference.valueOf(it) }.getOrNull()
                } ?: RenderingPreference.NATURAL,
                saveRawAndJpeg = prefs[Keys.SAVE_RAW_JPEG] ?: true,
                keepOriginalWhenEdited = prefs[Keys.KEEP_ORIGINAL] ?: true,
                frontCameraMirror = prefs[Keys.FRONT_MIRROR] ?: true,
                geotaggingEnabled = prefs[Keys.GEOTAGGING] ?: false,
                recordAudioInVideo = prefs[Keys.RECORD_AUDIO] ?: true,
                highEfficiencyHeif = prefs[Keys.HEIF_ENABLED] ?: false
            )
        }

    suspend fun updateDefaultMode(mode: CameraMode) {
        context.dataStore.edit { it[Keys.DEFAULT_MODE] = mode.name }
    }

    suspend fun updateAspectRatio(ratio: AspectRatioOption) {
        context.dataStore.edit { it[Keys.ASPECT_RATIO] = ratio.name }
    }

    suspend fun updateVideoQuality(quality: VideoQualityOption) {
        context.dataStore.edit { it[Keys.VIDEO_QUALITY] = quality.name }
    }

    suspend fun updateVideoFps(fps: Int) {
        context.dataStore.edit { it[Keys.VIDEO_FPS] = fps }
    }

    suspend fun updateFilenamePrefix(prefix: String) {
        val sanitized = prefix.replace(Regex("[^A-Za-z0-9_-]"), "").ifBlank { "FORMA_" }
        context.dataStore.edit { it[Keys.FILENAME_PREFIX] = sanitized }
    }

    suspend fun updateShutterSound(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHUTTER_SOUND] = enabled }
    }

    suspend fun updateGridOverlay(grid: GridOverlayType) {
        context.dataStore.edit { it[Keys.GRID_OVERLAY] = grid.name }
    }

    suspend fun updateHorizonLevel(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_LEVEL] = enabled }
    }

    suspend fun updateTimerSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.TIMER_SECONDS] = seconds }
    }

    suspend fun updateBurstCount(count: Int) {
        context.dataStore.edit { it[Keys.BURST_COUNT] = count }
    }

    suspend fun updateProcessingStrength(strength: Float) {
        context.dataStore.edit { it[Keys.PROCESSING_STRENGTH] = strength.coerceIn(0f, 1f) }
    }

    suspend fun updateRenderingPreference(pref: RenderingPreference) {
        context.dataStore.edit { it[Keys.RENDERING_PREF] = pref.name }
    }

    suspend fun updateSaveRawAndJpeg(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SAVE_RAW_JPEG] = enabled }
    }

    suspend fun updateKeepOriginal(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_ORIGINAL] = enabled }
    }

    suspend fun updateFrontMirror(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FRONT_MIRROR] = enabled }
    }

    suspend fun updateGeotagging(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GEOTAGGING] = enabled }
    }

    suspend fun updateRecordAudio(enabled: Boolean) {
        context.dataStore.edit { it[Keys.RECORD_AUDIO] = enabled }
    }

    suspend fun updateHeifEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HEIF_ENABLED] = enabled }
    }
}
