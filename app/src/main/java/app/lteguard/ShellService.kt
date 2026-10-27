package app.lteguard

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** Runs inside the Shizuku user-service process (shell uid). */
class ShellService : IShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(cmd: String, timeoutSec: Int): String {
        return try {
            val full = "PATH=/system/bin:/system/xbin:/vendor/bin:\$PATH; " + cmd
            val p = ProcessBuilder("sh", "-c", full).redirectErrorStream(true).start()
            val sb = StringBuffer()
            val t = Thread {
                try {
                    BufferedReader(InputStreamReader(p.inputStream)).use { r ->
                        val buf = CharArray(4096)
                        while (true) {
                            val n = r.read(buf)
                            if (n < 0) break
                            if (sb.length < 400_000) sb.append(buf, 0, n)
                        }
                    }
                } catch (_: Exception) {
                }
            }
            t.start()
            val done = p.waitFor(timeoutSec.toLong(), TimeUnit.SECONDS)
            if (!done) {
                p.destroyForcibly()
                sb.append("\n__TIMEOUT")
            }
            t.join(1500)
            sb.toString() + "\n__EXIT=" + (if (done) p.exitValue() else -1)
        } catch (e: Exception) {
            "__ERR $e\n__EXIT=-2"
        }
    }
}
