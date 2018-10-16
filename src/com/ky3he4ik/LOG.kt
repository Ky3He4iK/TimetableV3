package com.ky3he4ik

internal object LOG {
    private const val logBoundaryOpen = "------LOG PART------"
    private const val logBoundaryClose = "____END LOG PART____"
    private const val esc = "\u001B"
    private const val bold = "[1;"
    private const val normal = "[0;"
    private const val endEsc = "m"

    enum class LogLevel(val lvl: Int, val color: Color) {
        VERBOSE(0, Color.BLACK),
        DEBUG(3, Color.GRAY),
        INFO(6, Color.WHITE),
        WARNING(9, Color.MAGENTA),
        ERROR(12, Color.RED),
        SILENT(100, Color.RED)
    }

    enum class Color(val code: String, val isBold: Boolean) {
        RED("31", true),
        MAGENTA("35", true),
        WHITE("30", false),
        GRAY("38", false),
        BLACK("37", false)
    }

    var logLevel = LogLevel.INFO

    fun e(tag: String, message: String?, e: Exception) {
        println("$logBoundaryOpen\n$tag: $message")
        e.printStackTrace()
        Thread.sleep(10)
        println("\n$logBoundaryClose")
        //TODO: timestamp in logs
    }

    fun e(tag: String, message: String) = write(tag, message, LogLevel.ERROR)

    fun w(tag: String, message: String) = write(tag, message, LogLevel.WARNING)

    fun i(tag: String, message: String) = write(tag, message, LogLevel.INFO)

    fun d(tag: String, message: String) = write(tag, message, LogLevel.DEBUG)

    fun v(tag: String, message: String) = write(tag, message, LogLevel.VERBOSE)

    fun extremelyImportant(tag: String, message: String) = write(tag, message, LogLevel.SILENT)

    private fun write(tag: String, message: String, level: LogLevel) {
        if (level.lvl >= logLevel.lvl)
            println("${LOG.toColoredText(logBoundaryOpen, logLevel.color)}\n$tag: $message\n" +
                    LOG.toColoredText(logBoundaryClose, logLevel.color))
    }

    private fun toColoredText(text: String, color: Color): String
            = "$esc${if (color.isBold) bold else normal}${color.code}$endEsc$text"
}
