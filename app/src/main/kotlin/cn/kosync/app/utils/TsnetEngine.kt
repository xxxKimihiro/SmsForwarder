package cn.kosync.app.utils

import android.content.Context
import java.io.File
import java.lang.reflect.Method
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 可选内置 Tailscale（userspace）。不申请 VpnService。
 * 运行时反射调用 tsnetbind AAR；未编进 AAR 时 available()=false。
 */
object TsnetEngine {
    private const val TAG = "TsnetEngine"
    private val io = Executors.newSingleThreadExecutor()
    private val starting = AtomicBoolean(false)

    @Volatile
    var lastMessage: String = ""
        private set

    fun available(): Boolean = nativeClass() != null

    fun isRunning(): Boolean = invokeBool("running", "Running")

    fun selfIP(): String = invokeString("selfIP", "SelfIP")

    fun socksPort(): Int = invokeInt("socksPort", "SocksPort")

    fun lastError(): String = invokeString("lastError", "LastError")

    fun startAsync(context: Context, onDone: ((Boolean, String) -> Unit)? = null) {
        if (!HttpServerUtils.enableTsnet) {
            onDone?.invoke(false, "disabled")
            return
        }
        if (!available()) {
            val msg = "tsnetbind.aar 未编入，先运行 tsnetbind/build-aar.sh"
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
        val port = HttpServerUtils.serverPort
        io.execute {
            try {
                val err = invokeStart(stateDir, authKey, hostname, port, port)
                if (err != null) {
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
            invokeVoid("stop", "Stop")
            lastMessage = "stopped"
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

    private fun nativeClass(): Class<*>? {
        val names = arrayOf(
            "cn.kosync.tsnet.tsnetbind.Tsnetbind",
            "cn.kosync.tsnet.Tsnetbind",
            "tsnetbind.Tsnetbind"
        )
        for (name in names) {
            try {
                return Class.forName(name)
            } catch (_: ClassNotFoundException) {
            }
        }
        return null
    }

    private fun method(vararg names: String, types: Array<out Class<*>> = emptyArray()): Method? {
        val cls = nativeClass() ?: return null
        for (name in names) {
            try {
                return cls.getMethod(name, *types)
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun invokeStart(stateDir: String, authKey: String, hostname: String, advertisePort: Int, localPort: Int): String? {
        // gomobile maps Go int -> Java long, and exports camelCase: start(...)
        val str = String::class.java
        val longT = java.lang.Long.TYPE
        val intT = java.lang.Integer.TYPE
        val m = method("start", "Start", types = arrayOf(str, str, str, longT, longT))
            ?: method("start", "Start", types = arrayOf(str, str, str, intT, intT))
            ?: return "start() not found"
        return try {
            val params = m.parameterTypes
            if (params.size >= 5 && params[3] == java.lang.Long.TYPE) {
                m.invoke(null, stateDir, authKey, hostname, advertisePort.toLong(), localPort.toLong())
            } else {
                m.invoke(null, stateDir, authKey, hostname, advertisePort, localPort)
            }
            null
        } catch (e: Exception) {
            e.cause?.message ?: e.message
        }
    }

    private fun invokeVoid(vararg names: String) {
        method(*names)?.invoke(null)
    }

    private fun invokeBool(vararg names: String): Boolean {
        return try {
            method(*names)?.invoke(null) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun invokeString(vararg names: String): String {
        return try {
            method(*names)?.invoke(null) as? String ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun invokeInt(vararg names: String): Int {
        return try {
            (method(*names)?.invoke(null) as? Number)?.toInt() ?: 0
        } catch (_: Exception) {
            0
        }
    }
}
