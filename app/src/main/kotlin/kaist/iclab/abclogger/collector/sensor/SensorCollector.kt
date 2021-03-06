package kaist.iclab.abclogger.collector.sensor

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import kaist.iclab.abclogger.Prefs
import kaist.iclab.abclogger.ObjBox
import kaist.iclab.abclogger.collector.BaseCollector
import kaist.iclab.abclogger.collector.BaseStatus
import kaist.iclab.abclogger.collector.fill
import kaist.iclab.abclogger.collector.setStatus
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class SensorCollector(val context: Context) : BaseCollector, SensorEventListener {
    data class Status(override val hasStarted: Boolean? = null,
                      override val lastTime: Long? = null,
                      val isProximityAvailable: Boolean? = null,
                      val isLightAvailable: Boolean? = null) : BaseStatus() {
        override fun info(): String =
                "Proximity: ${if(isProximityAvailable == true) "On" else "Off"}; Light: ${if(isLightAvailable == true) "On" else "Off"}"
    }

    private val sensorManager : SensorManager by lazy { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private val subject : PublishSubject<SensorEntity> = PublishSubject.create<SensorEntity>()
    private val disposables: CompositeDisposable = CompositeDisposable()

    private fun accuracyToString(accuracy: Int) = when(accuracy) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "HIGH"
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "MEDIUM"
        else -> "LOW"
    }

    override suspend fun onStart() {
        disposables.clear()
        sensorManager.unregisterListener(this)

        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        lightSensor?.let { sensorManager.registerListener(this, it, TimeUnit.SECONDS.toMicros(1).toInt()) }
        proximitySensor?.let { sensorManager.registerListener(this, it, TimeUnit.SECONDS.toMicros(1).toInt()) }

        Prefs.statusSensor = Prefs.statusSensor?.copy(
                isLightAvailable = lightSensor != null,
                isProximityAvailable = proximitySensor != null
        )

        val disposable = subject.buffer(
                10, TimeUnit.SECONDS
        ).subscribeOn(
                Schedulers.io()
        ).subscribe { entities ->
            GlobalScope.launch {
                ObjBox.put(entities)
                setStatus(Status(lastTime = System.currentTimeMillis()))
            }
        }
        disposables.addAll(disposable)
    }

    override suspend fun onStop() {
        disposables.clear()

        sensorManager.unregisterListener(this)
    }

    override suspend fun checkAvailability(): Boolean = true

    override val requiredPermissions: List<String>
        get() = listOf()

    override val newIntentForSetUp: Intent?
        get() = null

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }

    override fun onSensorChanged(event: SensorEvent?) {
        val timestamp = System.currentTimeMillis()
        event?.let { e ->
            val entity = when(e.sensor.type) {
                Sensor.TYPE_PROXIMITY -> {
                    SensorEntity(
                            type = "PROXIMITY",
                            accuracy = accuracyToString(e.accuracy),
                            firstValue = e.values?.firstOrNull() ?: Float.MIN_VALUE
                    )
                }
                Sensor.TYPE_LIGHT -> {
                    SensorEntity(
                            type = "LIGHT",
                            accuracy = accuracyToString(e.accuracy),
                            firstValue = e.values?.firstOrNull() ?: Float.MIN_VALUE
                    )
                }
                else -> null
            }?.fill(timeMillis = timestamp) ?: return@let
            subject.onNext(entity)
        }
    }
}