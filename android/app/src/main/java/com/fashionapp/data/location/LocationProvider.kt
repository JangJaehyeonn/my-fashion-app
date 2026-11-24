package com.fashionapp.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.round

data class Coordinates(val lat: Double, val lon: Double)

/** Device location for the home weather; an interface so the home ViewModel is unit-testable. */
interface DeviceLocation {
    fun hasPermission(): Boolean
    var isPromptShown: Boolean
    suspend fun currentCoordinates(): Coordinates?
}

/**
 * 날씨 조회용 기기 위치. 정밀 위치는 필요 없어 대략적인 위치(ACCESS_COARSE_LOCATION)만 쓰고,
 * 서버로 보내기 전에 소수점 둘째 자리(약 1km)로 반올림한다. 권한이 없거나 위치를 못 얻으면 null.
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : DeviceLocation {
    companion object {
        // 서버(AI 서버) 기본값과 같은 서울시청 좌표 — 권한 거부·위치 실패 시 대체값
        val SEOUL = Coordinates(37.5665, 126.9780)

        private const val PREFS = "location"
        private const val KEY_PROMPT_SHOWN = "prompt_shown"
        private const val MAX_LAST_KNOWN_AGE_MS = 30 * 60 * 1000L
        private const val CURRENT_LOCATION_TIMEOUT_MS = 5_000L
    }

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    // 위치 권한 안내를 한 번 보여줬는지 — 거부한 사용자에게 앱을 켤 때마다 다시 묻지 않기 위함
    override var isPromptShown: Boolean
        get() = prefs.getBoolean(KEY_PROMPT_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_PROMPT_SHOWN, value).apply()

    @SuppressLint("MissingPermission")
    override suspend fun currentCoordinates(): Coordinates? {
        if (!hasPermission()) return null
        val location = runCatching { recentLastKnown() ?: requestCurrent() }.getOrNull() ?: return null
        return Coordinates(roundCoord(location.latitude), roundCoord(location.longitude))
    }

    @SuppressLint("MissingPermission")
    private fun recentLastKnown(): Location? =
        locationManager.getProviders(true)
            .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < MAX_LAST_KNOWN_AGE_MS }
            .maxByOrNull { it.time }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrent(): Location? {
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return null
        }
        return withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    locationManager, provider, signal, ContextCompat.getMainExecutor(context)
                ) { location -> if (cont.isActive) cont.resume(location) }
            }
        }
    }

    private fun roundCoord(value: Double) = round(value * 100) / 100
}
