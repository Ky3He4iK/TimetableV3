package com.ky3he4ik

internal object LOG {
    private const val logBoundaryOpen = "------LOG PART------"
    private const val logBoundaryClose = "____END LOG PART____"

    enum class LogLevel(val lvl: Int) {
        VERBOSE(0),
        DEBUG(3),
        INFO(6),
        WARNING(9),
        ERROR(12),
        SILENT(100)
    }

    var logLevel = LogLevel.INFO

    fun e(tag: String, message: String?, e: Exception) {
        println("$logBoundaryOpen\n$tag: $message")
        e.printStackTrace()
        Thread.sleep(10)
        println("\n$logBoundaryClose")
        //TODO: timestamp in logs
        //TODO: colored messages
    }

    fun e(tag: String, message: String) = write(tag, message, LogLevel.ERROR)

    fun w(tag: String, message: String) = write(tag, message, LogLevel.WARNING)

    fun i(tag: String, message: String) = write(tag, message, LogLevel.INFO)

    fun d(tag: String, message: String) = write(tag, message, LogLevel.DEBUG)

    fun v(tag: String, message: String) = write(tag, message, LogLevel.VERBOSE)

    fun extremelyImportant(tag: String, message: String) = write(tag, message, LogLevel.SILENT)

    private fun write(tag: String, message: String, level: LogLevel) {
        if (level.lvl >= logLevel.lvl)
            println("$logBoundaryOpen\n$tag: $message\n$logBoundaryClose")
    }
}
