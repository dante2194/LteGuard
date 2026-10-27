package app.lteguard

import android.content.ComponentName
import android.content.Context
import android.os.IBinder
import android.content.ServiceConnection
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

object ShizukuBridge {
    private const val PKG = "moe.shizuku.privileged.api"
    private const val PERM_CODE = 4107

    @Volatile private var svc: IShellService? = null
    @Volatile private var binding = false
    private var inited = false

    private val args: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(App.ctx.packageName, ShellService::class.java.name))
            .processNameSuffix("shell")
            .daemon(false)
            .debuggable(false)
            .version(1)
    }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                svc = IShellService.Stub.asInterface(binder)
                AppLog.log("Shizuku shell service connected")
            }
            binding = false
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            svc = null
            binding = false
            AppLog.log("Shizuku shell service disconnected")
        }
    }

    fun init(ctx: Context) {
        if (inited) return
        inited = true
        Shizuku.addBinderReceivedListenerSticky(Shizuku.OnBinderReceivedListener {
            AppLog.log("Shizuku binder received")
        })
        Shizuku.addBinderDeadListener(Shizuku.OnBinderDeadListener {
            svc = null
            binding = false
            AppLog.log("Shizuku binder died - start Shizuku again")
        })
    }

    fun state(ctx: Context): ShizukuState {
        val installed = try {
            ctx.packageManager.getPackageInfo(PKG, 0)
            true
        } catch (e: Exception) {
            false
        }
        return try {
            if (!Shizuku.pingBinder()) {
                if (installed) ShizukuState.NOT_RUNNING else ShizukuState.NOT_INSTALLED
            } else if (Shizuku.isPreV11()) {
                ShizukuState.NOT_RUNNING
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                ShizukuState.READY
            } else {
                ShizukuState.NO_PERMISSION
            }
        } catch (e: Throwable) {
            if (installed) ShizukuState.NOT_RUNNING else ShizukuState.NOT_INSTALLED
        }
    }

    fun requestPermission() {
        try {
            Shizuku.requestPermission(PERM_CODE)
        } catch (e: Throwable) {
            AppLog.log("requestPermission failed: $e")
        }
    }

    private suspend fun ensureBound(): Boolean {
        if (svc?.asBinder()?.pingBinder() == true) return true
        svc = null
        if (state(App.ctx) != ShizukuState.READY) return false
        if (!binding) {
            binding = true
            try {
                Shizuku.bindUserService(args, conn)
            } catch (e: Throwable) {
                binding = false
                AppLog.log("bindUserService failed: $e")
                return false
            }
        }
        for (i in 1..80) {
            if (svc != null) return true
            delay(100)
        }
        binding = false
        return svc != null
    }

    /** Runs a shell command as the shell user. Returns output (ends with __EXIT=n) or null. */
    suspend fun exec(cmd: String, timeoutSec: Int = 20): String? = withContext(Dispatchers.IO) {
        if (!ensureBound()) return@withContext null
        try {
            svc?.exec(cmd, timeoutSec)
        } catch (e: Exception) {
            svc = null
            AppLog.log("shell exec failed: $e")
            null
        }
    }
}
