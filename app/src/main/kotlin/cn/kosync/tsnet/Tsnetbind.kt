package cn.kosync.tsnet

/**
 * JNI 入口，对应 tsnetbind/libtsnetbind.so（与 frpclib 的 libgojni 分开）。
 */
object Tsnetbind {
    init {
        System.loadLibrary("tsnetbind")
    }

    @JvmStatic
    fun touch() {
    }

    @JvmStatic
    external fun lastError(): String

    @JvmStatic
    external fun running(): Boolean

    @JvmStatic
    external fun selfIP(): String

    @JvmStatic
    external fun socksPort(): Long

    /** 成功返回 null；失败返回错误信息。 */
    @JvmStatic
    external fun start(
        stateDir: String,
        authKey: String,
        hostname: String,
        advertisePort: Long,
        localPort: Long
    ): String?

    @JvmStatic
    external fun stop()
}
