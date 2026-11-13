package com.speedometer.app.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.speedometer.app.core.trip.SpeedUnit
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class Vehicle { Car, Motorbike, Bicycle, Walking, Train, Boat }

/** The four Limit Guard switches on the Settings screen. */
enum class GuardOption(val default: Boolean) {
    AlertTones(true), SpokenZoneWarnings(true), VibrateOnAlert(true), KeepScreenOn(true),
}

/** The live meters the user can choose between. */
const val DEFAULT_RANGE_KMH = 200f
const val DEFAULT_SPEED_LIMIT_KMH = 120
const val MIN_SPEED_LIMIT_KMH = 10
const val MAX_SPEED_LIMIT_KMH = 300

enum class MeterStyle(val generic: Boolean = true) {
    Dial, Led, Bar, Rings, Graph, GForce, Neon,

    /** Vehicle clusters, chosen from Vehicle meters rather than Gauge style. */
    Sports(generic = false), Superbike(generic = false),
}

enum class ScreenOrientation { Portrait, Landscape }

data class Settings(
    val onboardingDone: Boolean = false,
    val language: String = "English",
    val vehicle: Vehicle = Vehicle.Car,
    val unit: SpeedUnit = SpeedUnit.Kmh,
    val autoDetect: Boolean = false,
    val locationAsked: Boolean = false,
    val meterStyle: MeterStyle = MeterStyle.Dial,
    val notificationAsked: Boolean = false,
    val screenOrientation: ScreenOrientation = ScreenOrientation.Portrait,
    /** True once location permission has been granted at some point, to tell a reset from a first run. */
    val locationGrantedBefore: Boolean = false,
    /** The user-made vehicle model (make and model, top speed in km/h); [customActive] says whether the meters use its range. */
    val customName: String? = null,
    val customMaxKmh: Int? = null,
    val customActive: Boolean = false,
    val guardOptions: Map<GuardOption, Boolean> = GuardOption.entries.associateWith { it.default },
    /** The speed limit alarm the user sets in Settings: on or off, the limit in km/h, and how far over it the alarm starts. */
    val speedLimitOn: Boolean = false,
    val speedLimitKmh: Int = DEFAULT_SPEED_LIMIT_KMH,
    val limitToleranceKmh: Int = 0,
) {
    val mph: Boolean get() = unit == SpeedUnit.Mph

    /** Top of the meters in km/h: the custom model's top speed when it is active, else 200. */
    val rangeKmh: Float get() = if (customActive) customMaxKmh?.toFloat() ?: DEFAULT_RANGE_KMH else DEFAULT_RANGE_KMH
    fun option(o: GuardOption) = guardOptions[o] ?: o.default
}

/** Single source of truth for user options. */
interface SettingsRepository {
    val settings: Flow<Settings>
    suspend fun setLanguage(language: String)
    suspend fun setVehicle(vehicle: Vehicle)
    suspend fun setUnit(unit: SpeedUnit)
    suspend fun setAutoDetect(on: Boolean)
    suspend fun setLocationAsked()
    suspend fun setMeterStyle(style: MeterStyle)
    suspend fun setScreenOrientation(orientation: ScreenOrientation)
    suspend fun setNotificationAsked()
    suspend fun setLocationGranted()
    suspend fun saveCustomModel(name: String, maxKmh: Int)
    suspend fun useCustomModel(on: Boolean)
    suspend fun setGuardOption(option: GuardOption, on: Boolean)
    suspend fun setSpeedLimitOn(on: Boolean)
    suspend fun setSpeedLimitKmh(kmh: Int)
    suspend fun setLimitTolerance(kmh: Int)
    suspend fun finishOnboarding()
}

private val Context.store: DataStore<Preferences> by preferencesDataStore("settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val analytics: com.speedometer.app.core.analytics.Analytics,
) : SettingsRepository {
    private object K {
        val done = booleanPreferencesKey("onboarding_done")
        val language = stringPreferencesKey("language")
        val vehicle = stringPreferencesKey("vehicle")
        val unit = stringPreferencesKey("unit")
        val auto = booleanPreferencesKey("auto_detect")
        val asked = booleanPreferencesKey("location_asked")
        val meter = stringPreferencesKey("meter_style")
        val screenOrientation = stringPreferencesKey("screen_orientation")
        val notifAsked = booleanPreferencesKey("notification_asked")
        val locGranted = booleanPreferencesKey("location_granted_before")
        val customName = stringPreferencesKey("custom_name")
        val customMax = androidx.datastore.preferences.core.intPreferencesKey("custom_max_kmh")
        val customOn = booleanPreferencesKey("custom_on")
        val limitOn = booleanPreferencesKey("speed_limit_on")
        val limitKmh = androidx.datastore.preferences.core.intPreferencesKey("speed_limit_kmh")
        val limitTol = androidx.datastore.preferences.core.intPreferencesKey("limit_tolerance_kmh")
        fun guard(o: GuardOption) = booleanPreferencesKey("guard_" + o.name)
    }

    override val settings: Flow<Settings> = context.store.data.map { p ->
        Settings(
            onboardingDone = p[K.done] ?: false,
            language = p[K.language] ?: "English",
            vehicle = p[K.vehicle]?.let { v -> Vehicle.entries.firstOrNull { it.name == v } } ?: Vehicle.Car,
            unit = p[K.unit]?.let { u -> SpeedUnit.entries.firstOrNull { it.name == u } } ?: SpeedUnit.Kmh,
            autoDetect = p[K.auto] ?: false,
            locationAsked = p[K.asked] ?: false,
            notificationAsked = p[K.notifAsked] ?: false,
            locationGrantedBefore = p[K.locGranted] ?: false,
            customName = p[K.customName],
            customMaxKmh = p[K.customMax],
            customActive = p[K.customOn] == true,
            meterStyle = p[K.meter]?.let { m -> MeterStyle.entries.firstOrNull { it.name == m } } ?: MeterStyle.Dial,
            screenOrientation = p[K.screenOrientation]?.let { value ->
                ScreenOrientation.entries.firstOrNull { it.name == value }
            } ?: ScreenOrientation.Portrait,
            guardOptions = GuardOption.entries.associateWith { p[K.guard(it)] ?: it.default },
            speedLimitOn = p[K.limitOn] ?: false,
            speedLimitKmh = (p[K.limitKmh] ?: DEFAULT_SPEED_LIMIT_KMH).coerceIn(MIN_SPEED_LIMIT_KMH, MAX_SPEED_LIMIT_KMH),
            limitToleranceKmh = p[K.limitTol] ?: 0,
        )
    }

    override suspend fun setLanguage(language: String) { context.store.edit { it[K.language] = language } }
    override suspend fun setVehicle(vehicle: Vehicle) {
        context.store.edit { it[K.vehicle] = vehicle.name }
        analytics.event("vehicle_changed", "vehicle" to vehicle.name)
    }
    override suspend fun setUnit(unit: SpeedUnit) { context.store.edit { it[K.unit] = unit.name } }
    override suspend fun setAutoDetect(on: Boolean) { context.store.edit { it[K.auto] = on } }
    override suspend fun setLocationAsked() { context.store.edit { it[K.asked] = true } }
    override suspend fun setMeterStyle(style: MeterStyle) {
        context.store.edit { it[K.meter] = style.name }
        analytics.event("meter_style_changed", "style" to style.name)
    }
    override suspend fun setScreenOrientation(orientation: ScreenOrientation) {
        context.store.edit { it[K.screenOrientation] = orientation.name }
    }
    override suspend fun setNotificationAsked() { context.store.edit { it[K.notifAsked] = true } }
    override suspend fun setLocationGranted() { context.store.edit { it[K.locGranted] = true } }
    override suspend fun saveCustomModel(name: String, maxKmh: Int) {
        context.store.edit { it[K.customName] = name; it[K.customMax] = maxKmh }
    }
    override suspend fun useCustomModel(on: Boolean) { context.store.edit { it[K.customOn] = on } }
    override suspend fun setGuardOption(option: GuardOption, on: Boolean) { context.store.edit { it[K.guard(option)] = on } }
    override suspend fun setSpeedLimitOn(on: Boolean) { context.store.edit { it[K.limitOn] = on } }
    override suspend fun setSpeedLimitKmh(kmh: Int) {
        val limit = kmh.coerceIn(MIN_SPEED_LIMIT_KMH, MAX_SPEED_LIMIT_KMH)
        context.store.edit { it[K.limitKmh] = limit }
        analytics.event("speed_limit_changed", "limit_kmh" to limit)
    }
    override suspend fun setLimitTolerance(kmh: Int) { context.store.edit { it[K.limitTol] = kmh.coerceIn(0, 20) } }
    override suspend fun finishOnboarding() { context.store.edit { it[K.done] = true } }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds abstract fun settings(impl: DataStoreSettingsRepository): SettingsRepository
}
