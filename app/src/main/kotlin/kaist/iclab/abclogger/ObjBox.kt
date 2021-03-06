package kaist.iclab.abclogger

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import kaist.iclab.abclogger.collector.Base
import kaist.iclab.abclogger.collector.MyObjectBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File
import java.lang.Exception
import java.util.concurrent.atomic.AtomicReference


object ObjBox {
    val boxStore: AtomicReference<BoxStore> = AtomicReference()

    private fun notifyFlushProgress(context: Context) {
        val ntf = Notifications.build(
                context = context,
                channelId = Notifications.CHANNEL_ID_PROGRESS,
                title = context.getString(R.string.ntf_title_flush),
                text = context.getString(R.string.ntf_text_flush),
                progress = 0,
                indeterminate = true
        )
        NotificationManagerCompat.from(context).notify(Notifications.ID_FLUSH_PROGRESS, ntf)
    }

    private fun notifyFlushComplete(context: Context) {
        val ntf = Notifications.build(
                context = context,
                channelId = Notifications.CHANNEL_ID_PROGRESS,
                title = context.getString(R.string.ntf_title_flush),
                text = context.getString(R.string.ntf_text_flush_complete)
        )
        NotificationManagerCompat.from(context).notify(Notifications.ID_FLUSH_PROGRESS, ntf)
    }

    private fun notifyFlushError(context: Context, throwable: Throwable?) {
        val ntf = Notifications.build(
                context = context,
                channelId = Notifications.CHANNEL_ID_PROGRESS,
                title = context.getString(R.string.ntf_title_flush),
                text = listOfNotNull(
                        context.getString(R.string.ntf_text_flush_failed),
                        ABCException.wrap(throwable).toString(context)
                ).joinToString(": ")
        )
        NotificationManagerCompat.from(context).notify(Notifications.ID_FLUSH_PROGRESS, ntf)
    }

    private suspend fun buildStore(context: Context): BoxStore = withContext(Dispatchers.IO) {
        val store = (1..500).firstNotNullResult { multiple ->
            try {
                val tempStore = MyObjectBox.builder()
                        .androidContext(context.applicationContext)
                        .maxSizeInKByte(BuildConfig.DB_MAX_SIZE * multiple) //3 GB
                        .name("${BuildConfig.DB_NAME}-${Prefs.dbVersion}")
                        .build()
                Prefs.maxDbSize = BuildConfig.DB_MAX_SIZE * multiple
                tempStore
            } catch (e: Exception) {
                null
            }
        } ?: throw RuntimeException("DB size is too large!!")

        Prefs.dbVersion += 1
        return@withContext store
    }

    suspend fun bind(context: Context) = withContext(Dispatchers.IO) {
        boxStore.set(buildStore(context))
    }

    suspend fun flush(context: Context, showProgress: Boolean = false) = withContext(Dispatchers.IO) {
        if (showProgress) notifyFlushProgress(context)
        try {
            val oldStore = boxStore.getAndSet(buildStore(context))
            oldStore?.close()
            oldStore?.deleteAllFiles()
            if (showProgress) notifyFlushComplete(context)
        } catch (e: Exception) {
            if(showProgress) notifyFlushError(context, e)
        }
    }

    fun size(context: Context): Long {
        val baseDir = File(context.filesDir, "objectbox")
        return FileUtils.sizeOfDirectory(baseDir)
    }

    fun maxSizeInBytes() = Prefs.maxDbSize * 1000L

    inline fun <reified T> boxFor() = boxStore.get()?.boxFor<T>()

    inline fun <reified T : Base> putSync(entity: T?): Long {
        entity ?: return -1L
        if (boxStore.get()?.isClosed != false) return -1

        if (BuildConfig.DEBUG) AppLog.d(entity::class.java.name, entity)
        return boxFor<T>()?.put(entity) ?: -1
    }

    inline fun <reified T : Base> putSync(entities: Collection<T>?) {
        if (entities.isNullOrEmpty()) return
        if (boxStore.get()?.isClosed != false) return

        if (BuildConfig.DEBUG) AppLog.d(entities::class.java.name, entities)
        boxFor<T>()?.put(entities)
    }

    suspend inline fun <reified T : Base> put(entity: T?): Long = withContext<Long>(Dispatchers.IO) {
        entity ?: return@withContext -1
        if (boxStore.get()?.isClosed != false) return@withContext -1

        if (BuildConfig.DEBUG) AppLog.d(entity::class.java.name, entity)
        return@withContext boxFor<T>()?.put(entity) ?: -1
    }

    suspend inline fun <reified T : Base> put(entities: Collection<T>?) = withContext(Dispatchers.IO) {
        if (entities.isNullOrEmpty()) return@withContext
        if (boxStore.get()?.isClosed != false) return@withContext

        if (BuildConfig.DEBUG) AppLog.d(entities::class.java.name, entities)
        boxFor<T>()?.put(entities)
    }
}





