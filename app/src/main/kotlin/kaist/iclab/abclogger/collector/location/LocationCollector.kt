package kaist.iclab.abclogger.collector.location

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import kaist.iclab.abclogger.*
import kaist.iclab.abclogger.collector.BaseCollector
import kaist.iclab.abclogger.collector.BaseStatus
import kaist.iclab.abclogger.collector.fill
import kaist.iclab.abclogger.collector.setStatus
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class LocationCollector(val context: Context) : BaseCollector {
    data class Status(override val hasStarted: Boolean? = null,
                      override val lastTime: Long? = null) : BaseStatus() {
        override fun info(): String = ""
    }

    private val client : FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val receiver : BroadcastReceiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != ACTION_LOCATION_UPDATE || !LocationResult.hasResult(intent)) return

                LocationResult.extractResult(intent)?.lastLocation?.let { loc ->
                    LocationEntity(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            altitude = loc.altitude,
                            accuracy = loc.accuracy,
                            speed = loc.speed
                    ).fill(timeMillis = loc.time)
                }?.also { entity ->
                    GlobalScope.launch {
                        ObjBox.put(entity)
                        setStatus(Status(lastTime = System.currentTimeMillis()))
                    }
                }
            }
        }
    }

    private val intent : PendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE_LOCATION_UPDATE, Intent(ACTION_LOCATION_UPDATE), PendingIntent.FLAG_UPDATE_CURRENT
    )

    private val filter = IntentFilter().apply {
        addAction(ACTION_LOCATION_UPDATE)
    }

    private val request = LocationRequest.create()
            .setInterval(1000 * 60 * 3)
            .setSmallestDisplacement(5.0F)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)

    override suspend fun onStart() {
        context.safeRegisterReceiver(receiver, filter)
        client.requestLocationUpdates(request, intent)
    }

    override suspend fun onStop() {
        context.safeUnregisterReceiver(receiver)
        client.removeLocationUpdates(intent)
    }

    override suspend fun checkAvailability(): Boolean = context.checkPermission(requiredPermissions)

    override val requiredPermissions: List<String>
        get() = listOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
        )

    override val newIntentForSetUp: Intent?
        get() = null

    companion object {
        private const val ACTION_LOCATION_UPDATE = "${BuildConfig.APPLICATION_ID}.ACTION_LOCATION_UPDATE"
        private const val REQUEST_CODE_LOCATION_UPDATE = 0xff
    }
}