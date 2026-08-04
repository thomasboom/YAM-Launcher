package eu.ottop.yamlauncher.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import neth.iecal.curbox.api.ICurboxApi
import org.json.JSONObject

/** Client for Curbox's local, permission-gated AIDL API. */
class CurboxApiClient(
    context: Context,
    private val onConnected: () -> Unit
) : ServiceConnection {

    private val appContext = context.applicationContext
    private val logger = Logger.getInstance(appContext)

    @Volatile
    private var api: ICurboxApi? = null
    private var isBound = false

    fun connect(): Boolean {
        if (isBound) return true
        val curboxPackage = findCurboxPackage(appContext) ?: return false
        return try {
            isBound = appContext.bindService(
                Intent(ACTION_BIND).setPackage(curboxPackage),
                this,
                Context.BIND_AUTO_CREATE
            )
            isBound
        } catch (e: Exception) {
            logger.e("CurboxApiClient", "Failed to bind to Curbox", e)
            false
        }
    }

    fun disconnect() {
        if (!isBound) return
        try {
            appContext.unbindService(this)
        } catch (e: Exception) {
            logger.e("CurboxApiClient", "Failed to unbind from Curbox", e)
        } finally {
            api = null
            isBound = false
        }
    }

    suspend fun getTodayScreenTimeMinutes(): Long? = withContext(Dispatchers.IO) {
        val service = api ?: return@withContext null
        try {
            if (!service.isGranted) return@withContext null
            val response = service.query(STATE_SCREEN_TIME_TODAY) ?: return@withContext null
            JSONObject(response).optString(FIELD_SCREEN_TIME).toLongOrNull()
        } catch (e: Exception) {
            logger.e("CurboxApiClient", "Failed to query screen time", e)
            null
        }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        api = ICurboxApi.Stub.asInterface(service)
        onConnected()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        api = null
    }

    companion object {
        private const val ACTION_BIND = "neth.iecal.curbox.api.BIND"
        const val ACTION_REQUEST_PERMISSION = "neth.iecal.curbox.api.REQUEST_PERMISSION"
        private const val STATE_SCREEN_TIME_TODAY = "SCREENTIME_TODAY"
        private const val FIELD_SCREEN_TIME = "screentime"

        fun findCurboxPackage(context: Context): String? {
            return context.packageManager.resolveService(Intent(ACTION_BIND), 0)?.serviceInfo?.packageName
        }

        fun createPermissionIntent(context: Context): Intent? {
            val curboxPackage = findCurboxPackage(context) ?: return null
            return Intent(ACTION_REQUEST_PERMISSION).setPackage(curboxPackage)
        }
    }
}
