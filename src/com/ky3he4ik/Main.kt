package com.ky3he4ik

import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.TelegramBotsApi
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.exceptions.TelegramApiException
import org.telegram.telegrambots.logging.BotLogger
import org.telegram.telegrambots.logging.BotsFileHandler
import java.io.IOException
import java.util.logging.ConsoleHandler
import java.util.logging.Level

fun main(args: Array<String>) {
    val logTag = "INIT"

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
            // Register long polling bots. They work regardless type of TelegramBotsApi we are creating
            telegramBotsApi.registerBot(Main())
        } catch (e: TelegramApiException) {
            BotLogger.error(logTag, e)
        }

    } catch (e: Exception) {
        BotLogger.error(logTag, e)
    }

}

class Main : TelegramLongPollingBot(){
    private val LOGTAG = "MAIN"

    override fun getBotUsername(): String {
        return "" // TODO
    }

    override fun getBotToken(): String {
        return "" // TODO
    }

    override fun onUpdateReceived(p0: Update?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
