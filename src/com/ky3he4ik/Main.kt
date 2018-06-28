package com.ky3he4ik

import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.TelegramBotsApi
import org.telegram.telegrambots.api.objects.CallbackQuery
import org.telegram.telegrambots.api.objects.Message
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.exceptions.TelegramApiException
import org.telegram.telegrambots.logging.BotLogger
import org.telegram.telegrambots.logging.BotsFileHandler

import java.io.IOException
import java.time.LocalDateTime
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import kotlin.concurrent.thread

fun main(args: Array<String>) {
    BotConfig.isDebug = true
    val timetable = TimetableBuilder.createTimetable(true)
    IO.writeJSON("timetable.json", timetable)
    thread(isDaemon = true, name = "Time thread") { while (true) {
        try {
            var hour = LocalDateTime.now().hour + 3
            var day = LocalDateTime.now().dayOfWeek.value
            if (hour > 23) {
                hour -= 24
                day = (day + 1) % 7
            }
            Common.currentLesson = getLesson(hour, LocalDateTime.now().minute)
            Common.currentDay = day
            println("$day $hour")
            Thread.sleep(60 * 1000)
        } catch (e: Exception) {
            println(e.message)
            e.printStackTrace()
        }
    }
    }
//    main(args.isNotEmpty())
}

fun getLesson(hour: Int, minutes: Int): Int {
    for (it in 0 until Constants.lessonTimes.size)
        if (hour < Constants.lessonTimes[it][0] ||
                (hour == Constants.lessonTimes[it][0] && minutes < Constants.lessonTimes[it][1]))
            return it
    return 7
}


fun main(debug: Boolean) {
    val logTag = "INIT"
    // BotConfig.isDebug = true
    BotConfig.isDebug = debug
    BotLogger.setLevel(Level.ALL)
    BotLogger.registerLogger(ConsoleHandler())
    try {
        BotLogger.registerLogger(BotsFileHandler())
    } catch (e: IOException) {
        BotLogger.severe(logTag, e)
    }

    try {
        ApiContextInitializer.init()
        val telegramBotsApi = TelegramBotsApi()
        try {
            telegramBotsApi.registerBot(Main())
        } catch (e: TelegramApiException) {
            BotLogger.error(logTag, e)
        }
    } catch (e: Exception) {
        BotLogger.error(logTag, e)
    }
}

class Main : TelegramLongPollingBot() {
    //private val LOGTAG = "MAIN"

    override fun getBotUsername(): String {
        return if (BotConfig.isDebug) BotConfig.TestingUsername else BotConfig.ReleaseUsername
    }

    override fun getBotToken(): String {
        return if (BotConfig.isDebug) BotConfig.TestingToken else BotConfig.ReleaseToken
    }

    override fun onUpdateReceived(update: Update?) {
        if (update == null)
            return
        if (update.hasMessage()) {
            onMessage(update.message)
        } else if (update.hasCallbackQuery()) {
            onCallbackQuery(update.callbackQuery)
        }
        TODO("not implemented")
    }

    private fun onMessage(message: Message) {
        TODO("not implemented")
    }

    private fun onCallbackQuery(callbackQuery: CallbackQuery) {
        TODO("not implemented")
    }
}
