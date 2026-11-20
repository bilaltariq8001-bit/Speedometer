package com.speedometer.app.core.map

import android.content.Context
import com.speedometer.app.BuildConfig
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.offline.OfflineManager
import java.util.concurrent.TimeUnit

/**
 * One-time map setup shared by every map and by [OfflineMaps]:
 * - an HTTP client that names the app in its User-Agent and makes at most 4 requests at a time to the tile server
 *   (OpenFreeMap's terms for offline downloads),
 * - a large tile cache (everything the user has looked at stays drawable with no internet),
 * MapLibre follows the phone's connection by itself: with no internet it reads the saved map from the phone. It is never
 * forced offline by the app, because a brief "not validated yet" network state then made the style fail and the map blank.
 */
object MapSetup {
    /** Style for every map (vector tiles, sharp at any zoom). Offline areas are saved from this same style. */
    val styleUrl: String get() = BuildConfig.MAP_STYLE_URL

    private const val AMBIENT_CACHE_BYTES = 300L * 1024 * 1024
    private val userAgent = "Speedometer/${BuildConfig.VERSION_NAME} (Android; ${BuildConfig.APPLICATION_ID})"

    @Volatile private var done = false

    fun init(context: Context) {
        val app = context.applicationContext
        MapLibre.getInstance(app)
        if (done) return
        synchronized(this) {
            if (done) return
            done = true
            runCatching {
                val dispatcher = Dispatcher().apply { maxRequests = 8; maxRequestsPerHost = 4 }
                val client = OkHttpClient.Builder()
                    .dispatcher(dispatcher)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor { chain ->
                        chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
                    }
                    .build()
                HttpRequestUtil.setOkHttpClient(client)
            }
            runCatching {
                OfflineManager.getInstance(app).setMaximumAmbientCacheSize(
                    AMBIENT_CACHE_BYTES,
                    object : OfflineManager.FileSourceCallback {
                        override fun onSuccess() = Unit
                        override fun onError(message: String) = Unit
                    },
                )
            }
        }
    }
}
