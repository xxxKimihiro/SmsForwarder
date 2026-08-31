package cn.kosync.app.utils

object PeerSyncLogic {
    const val DEFAULT_PORT = 5000
    const val DEFAULT_LIMIT = 200
    const val MAX_LIMIT = 500
    const val PROBE_TIMEOUT_MS = 2000

    fun buildPeerId(deviceMark: String, msgId: Long): String {
        val mark = deviceMark.trim().ifEmpty { "unknown" }
        return "$mark:$msgId"
    }

    fun normalizeLimit(limit: Int): Int {
        if (limit <= 0) return DEFAULT_LIMIT
        return minOf(limit, MAX_LIMIT)
    }

    fun isBlankPeerId(peerId: String?): Boolean = peerId.isNullOrBlank()

    fun isDuplicate(existingId: Long?): Boolean = existingId != null && existingId > 0

    fun buildBaseUrl(address: String, port: Int): String {
        val host = if (address.contains(":") && !address.startsWith("[")) "[$address]" else address
        return "http://$host:$port"
    }

    fun isValidHost(host: String): Boolean {
        if (host.isBlank()) return false
        val trimmed = host.trim()
        if (trimmed.contains(".")) {
            val chunkIPv4 = "([\\d]|[1-9][\\d]|1[\\d][\\d]|2[0-4][\\d]|25[0-5])"
            if (Regex("^($chunkIPv4\\.){3}$chunkIPv4$").matches(trimmed)) return true
            val domain = Regex("^(?=^.{3,255}$)(?:(?:(?:[a-zA-Z\\d]|[a-zA-Z\\d][a-zA-Z\\d\\-]*[a-zA-Z\\d])\\.){1,126}(?:[A-Za-z\\d]|[A-Za-z\\d][A-Za-z\\d\\-]*[A-Za-z\\d]))$")
            if (domain.matches(trimmed)) return true
        }
        if (trimmed.contains(":")) {
            return trimmed.startsWith("[") && trimmed.endsWith("]") || trimmed.contains(":")
        }
        return trimmed.matches(Regex("^[A-Za-z0-9]([A-Za-z0-9\\-]{0,61}[A-Za-z0-9])?$"))
    }

    fun isValidPort(port: Int): Boolean = port in 1..65535

    fun nextCursor(items: List<Pair<Long, Long>>): Pair<Long, Long>? {
        if (items.isEmpty()) return null
        return items.maxWithOrNull(compareBy<Pair<Long, Long>> { it.first }.thenBy { it.second })
    }
}
