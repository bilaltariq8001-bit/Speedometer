package com.speedometer.app.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlin.math.abs
import com.speedometer.app.core.trip.RoutePoint
import com.speedometer.app.core.trip.RouteCodec
import com.speedometer.app.core.trip.TripState
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** A saved trip. [route] is a [RouteCodec] blob, empty for trips saved before routes were recorded. */
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startMs: Long,
    val durationSec: Int,
    val distanceKm: Float,
    val maxKmh: Float,
    val avgKmh: Float,
    val score: Int,
    val hardBrakes: Int,
    val secondsOver: Int,
    val peakGuard: Int,
    val route: ByteArray = ByteArray(0),
    /** The vehicle used for the trip (a [com.speedometer.app.core.data.Vehicle] name). Trips saved before this was recorded read as Car. */
    val vehicle: String = "Car",
) {
    fun routePoints(): List<RoutePoint> = RouteCodec.decode(route)
}

/** A user-set speed limit around a point. Guard uses it over the map limit. */
@Entity(tableName = "zones")
data class ZoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val limitKmh: Int,
    val lat: Double,
    val lon: Double,
    val radiusM: Int,
    val enabled: Boolean = true,
    val schedule: String = "always",
)

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startMs DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun get(id: Long): TripEntity?

    @Insert
    suspend fun insert(trip: TripEntity): Long

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun delete(id: Long)
}

/** A place the user saved from the Nav screen: a destination or an important point. */
@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lat: Double,
    val lon: Double,
    /** "destination" or "point". */
    val kind: String,
)

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY id DESC")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Insert
    suspend fun insert(place: PlaceEntity): Long

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM places WHERE kind = :kind")
    suspend fun deleteKind(kind: String)
}

@Dao
interface ZoneDao {
    @Query("SELECT * FROM zones ORDER BY id")
    fun observeAll(): Flow<List<ZoneEntity>>

    @Insert
    suspend fun insert(zone: ZoneEntity): Long

    @Query("UPDATE zones SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM zones WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [TripEntity::class, ZoneEntity::class, PlaceEntity::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trips(): TripDao
    abstract fun zones(): ZoneDao
    abstract fun places(): PlaceDao
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE trips ADD COLUMN vehicle TEXT NOT NULL DEFAULT 'Car'")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE zones ADD COLUMN schedule TEXT NOT NULL DEFAULT 'always'")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS places (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                "lat REAL NOT NULL, lon REAL NOT NULL, kind TEXT NOT NULL)",
        )
    }
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE trips ADD COLUMN route BLOB NOT NULL DEFAULT x''")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS zones (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                "limitKmh INTEGER NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, radiusM INTEGER NOT NULL, " +
                "enabled INTEGER NOT NULL)",
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "speedometer.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()

    @Provides fun tripDao(db: AppDatabase): TripDao = db.trips()
    @Provides fun zoneDao(db: AppDatabase): ZoneDao = db.zones()
    @Provides fun placeDao(db: AppDatabase): PlaceDao = db.places()
}

/** Saved trips on the phone. No account, no server. */
@Singleton
class SavedTripsRepository @Inject constructor(
    private val dao: TripDao,
    @ApplicationContext private val context: Context,
) {
    val trips: Flow<List<TripEntity>> = dao.observeAll()

    suspend fun get(id: Long): TripEntity? = dao.get(id)

    /**
     * One-time clean-up of the made-up trips that earlier test builds put in the database. They are found by their
     * exact titles, so trips you recorded yourself (named like "Trip 20 Oct, 6:34 PM") are never touched.
     */
    suspend fun removeLegacyDemoTrips() {
        val prefs = context.getSharedPreferences(CLEANUP_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_TRIPS, false)) return
        val list = try { dao.observeAll().first() } catch (e: Exception) { return }
        list.filter { it.title in LEGACY_DEMO_TRIPS }.forEach { dao.delete(it.id) }
        prefs.edit().putBoolean(KEY_TRIPS, true).apply()
    }

    /** Saves the trip and returns its id. */
    suspend fun save(state: TripState, startedAtMs: Long, title: String, route: List<RoutePoint>, vehicle: String = "Car"): Long {
        return dao.insert(
            TripEntity(
                title = title, startMs = startedAtMs, durationSec = state.durationSec, distanceKm = state.distanceKm,
                maxKmh = state.maxKmh, avgKmh = state.avgKmh, score = state.score, hardBrakes = state.hardBrakes,
                secondsOver = state.secondsOver, peakGuard = state.peakGuard, route = RouteCodec.encode(route), vehicle = vehicle,
            ),
        )
    }

    suspend fun delete(id: Long) = dao.delete(id)
}

/** Saved destinations and important points. Stays on the phone. */
@Singleton
class PlacesRepository @Inject constructor(private val dao: PlaceDao) {
    val places: Flow<List<PlaceEntity>> = dao.observeAll()
    suspend fun add(name: String, lat: Double, lon: Double, kind: String) { dao.insert(PlaceEntity(name = name, lat = lat, lon = lon, kind = kind)) }
    suspend fun delete(id: Long) = dao.delete(id)

    /** Kinds: "destination" (the one you are heading to), "home", "work" (one each) and "point" (any number). */
    suspend fun saveSingle(kind: String, name: String, lat: Double, lon: Double) {
        dao.deleteKind(kind)
        dao.insert(PlaceEntity(name = name, lat = lat, lon = lon, kind = kind))
    }

    suspend fun clearDestination() = dao.deleteKind("destination")
}

@Singleton
class ZonesRepository @Inject constructor(
    private val dao: ZoneDao,
    @ApplicationContext private val context: Context,
) {
    val zones: Flow<List<ZoneEntity>> = dao.observeAll()

    /** One-time clean-up of the made-up zones from earlier test builds, matched by name and exact position. */
    suspend fun removeLegacyDemoZones() {
        val prefs = context.getSharedPreferences(CLEANUP_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ZONES, false)) return
        val list = try { dao.observeAll().first() } catch (e: Exception) { return }
        list.filter { z -> LEGACY_DEMO_ZONES.any { it.first == z.name && abs(it.second - z.lat) < 1e-6 && abs(it.third - z.lon) < 1e-6 } }
            .forEach { dao.delete(it.id) }
        prefs.edit().putBoolean(KEY_ZONES, true).apply()
    }

    suspend fun add(name: String, limitKmh: Int, lat: Double, lon: Double, radiusM: Int, schedule: String = "always") {
        dao.insert(ZoneEntity(name = name, limitKmh = limitKmh, lat = lat, lon = lon, radiusM = radiusM, schedule = schedule))
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)
    suspend fun delete(id: Long) = dao.delete(id)
}

private const val CLEANUP_PREFS = "demo_cleanup"
private const val KEY_TRIPS = "trips_v1"
private const val KEY_ZONES = "zones_v1"

/** Titles of the made-up trips that earlier test builds added. */
private val LEGACY_DEMO_TRIPS = setOf(
    "Market to Home", "Office to Market", "Home to Office", "Evening Errands", "Highway to Lahore Road", "School Run",
    "Late Night Run", "Weekend Scenic Drive", "Airport Drop", "Gym and Back", "Family Visit", "Rainy Day Commute",
    "Margalla Hills Road", "Long Drive to Murree", "Morning Commute", "Downtown Delivery",
)

/** Name, latitude and longitude of the made-up zones that earlier test builds added (new and older sets). */
private val LEGACY_DEMO_ZONES = listOf(
    Triple("Hira School gate", 33.7208, 73.0553), Triple("Narrow lane, Sector F", 33.7108, 73.0364),
    Triple("Hospital road", 33.7050, 73.0530), Triple("Market area", 33.7077, 73.0507),
    Triple("Hira School gate", 33.6844, 73.0479), Triple("Narrow lane, Sector F", 33.6900, 73.0500),
    Triple("Hospital road", 33.6950, 73.0550), Triple("Market area", 33.7010, 73.0650),
    Triple("School Zone", 33.6844, 73.0479), Triple("Downtown", 33.6900, 73.0500),
)
