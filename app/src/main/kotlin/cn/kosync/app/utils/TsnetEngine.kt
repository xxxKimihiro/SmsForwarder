package cn.kosync.app.utils

import android.content.Context
import cn.kosync.tsnet.Tsnetbind
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 可选内置 Tailscale（userspace）。不申请 VpnService。
 * 走独立 libtsnetbind.so，避免和 frpclib 的 gomobile libgojni 撞车。
 */
object TsnetEngine {
    private const val TAG = "TsnetEngine"
    private val io = Executors.newSingleThreadExecutor()
    private val starting = AtomicBoolean(false)

    private val loaded: Boolean by lazy {
        try {
            Tsnetbind.touch()
            true
        } catch (e: Throwable) {
            Log.e(TAG, "libtsnetbind.so 未编入: ${e.message}")
            false
        }
    }

    @Volatile
    var lastMessage: String = ""
        private set

    fun available(): Boolean = loaded

    fun isRunning(): Boolean = if (!available()) false else try {
        Tsnetbind.running()
    } catch (_: Exception) {
        false
    }

    fun selfIP(): String = if (!available()) "" else try {
        Tsnetbind.selfIP()
    } catch (_: Exception) {
        ""
    }

    fun socksPort(): Int = if (!available()) 0 else try {
        Tsnetbind.socksPort().toInt()
    } catch (_: Exception) {
        0
    }

    fun lastError(): String = if (!available()) "" else try {
        Tsnetbind.lastError()
    } catch (_: Exception) {
        ""
    }

    fun startAsync(context: Context, onDone: ((Boolean, String) -> Unit)? = null) {
        if (!HttpServerUtils.enableTsnet) {
            onDone?.invoke(false, "disabled")
            return
        }
        if (!available()) {
            val msg = "libtsnetbind.so 未编入，先运行 tsnetbind/build-so.sh"
            lastMessage = msg
            Log.e(TAG, msg)
            onDone?.invoke(false, msg)
            return
        }
        if (isRunning()) {
            onDone?.invoke(true, selfIP())
            return
        }
        if (!starting.compareAndSet(false, true)) {
            onDone?.invoke(isRunning(), lastMessage)
            return
        }
        val stateDir = File(context.filesDir, "tsnet").absolutePath
        val authKey = HttpServerUtils.tsnetAuthKey
        val hostname = HttpServerUtils.tsnetHostname.ifBlank { SettingUtils.extraDeviceMark.ifBlank { "kosync" } }
        val port = HttpServerUtils.serverPort.toLong()
        Log.i(TAG, "start hostname=$hostname port=$port authKey=${if (authKey.isBlank()) "empty" else "set"} stateDir=$stateDir")
        io.execute {
            try {
                val err = Tsnetbind.start(stateDir, authKey, hostname, port, port)
                if (!err.isNullOrEmpty()) {
                    lastMessage = err
                    Log.e(TAG, "start failed: $err")
                    onDone?.invoke(false, err)
                } else {
                    lastMessage = "up ${selfIP()} socks=${socksPort()}"
                    Log.i(TAG, lastMessage)
                    onDone?.invoke(true, lastMessage)
                }
            } catch (e: Exception) {
                lastMessage = e.message ?: "start error"
                Log.e(TAG, lastMessage)
                onDone?.invoke(false, lastMessage)
            } finally {
                starting.set(false)
            }
        }
    }

    fun stop() {
        try {
            if (available()) {
                Tsnetbind.stop()
            }
            lastMessage = "stopped"
            Log.i(TAG, "stopped")
        } catch (e: Exception) {
            lastMessage = e.message ?: "stop error"
            Log.e(TAG, lastMessage)
        }
    }

    fun applySocksProxy(request: com.xuexiang.xhttp2.request.BaseRequest<*>) {
        val port = socksPort()
        if (HttpServerUtils.enableTsnet && isRunning() && port > 0) {
            request.okproxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port)))
        }
    }

    fun probe(address: String, port: Int, timeoutMs: Int = PeerSyncLogic.PROBE_TIMEOUT_MS): Boolean {
        val socks = socksPort()
        if (HttpServerUtils.enableTsnet && isRunning() && socks > 0) {
            return try {
                Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socks))).use { socket ->
                    socket.connect(InetSocketAddress(address, port), timeoutMs)
                    true
                }
            } catch (e: Exception) {
                Log.d(TAG, "tsnet probe fail $address:$port ${e.message}")
                false
            }
        }
        return false
    }
}
