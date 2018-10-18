package com.ky3he4ik

import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup

import com.ky3he4ik.Common.sendMessage
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

val bot = Main()

fun main(args: Array<String>) {
    Threads.startDaemonThread("Time thread") { Threads.timeThread(); }
    BotConfig.isDebug = args.isNotEmpty()
    LOG.logLevel = LOG.LogLevel.VERBOSE //TODO: set log level from args
    init()
    run()
}

fun run() {
    while (Common.work)
        Thread.sleep(1000)
    bot.db.writeAll()
}

fun init() {
    try {
        Constants.generateBells()
        ApiContextInitializer.init()
        TelegramBotsApi().registerBot(bot)
    } catch (e: Exception) {
        LOG.e("Main/init", e.message ?: "Error on init 2", e)
    }
}

class Main : TelegramLongPollingBot() {
    var db = Database(LoadType.READ.data)
    init {
        setDefaultKeyboard()
        Threads.startDaemonThread("Send thread") { Threads.sendThread(); }
        LOG.i("Main/Main/init", "Started")
        LOG.v("Main/Main/init_test", db.timetable.getTimetable(Type.CLASS.data, db.getInd("11е", Type.CLASS.data )))
    }

    override fun getBotUsername(): String = if (BotConfig.isDebug) BotConfig.TestUsername else BotConfig.ReleaseUsername

    override fun getBotToken(): String = if (BotConfig.isDebug) BotConfig.TestingToken else BotConfig.ReleaseToken

    override fun onUpdateReceived(update: Update?) {
        try {
            when {
                update == null -> return
                update.hasMessage() && update.message.hasText() -> onMessage(update.message)
                update.hasCallbackQuery() -> onCallbackQuery(update.callbackQuery)
                //TODO: add checking for stop and add/remove to group
            }
        } catch (e: Exception) {
            LOG.e("Main/onUpdate", e.message, e)
            try {
                sendMessage(IOParams(exceptionToString(e), inlineKeyboard = null, emergency = true))
                val chatId = when {
                    update?.message?.chatId != null -> update.message.chatId
                    update?.callbackQuery?.message?.chatId != null && update.callbackQuery?.message?.messageId != null ->
                        update.callbackQuery.message.chatId
                    else -> return
                }
                sendMessage(IOParams("Я упаль пытаясь обработать этот запрос =(", chatId = chatId,
                        inlineKeyboard = null, emergency = true))
            } catch (e: Exception) { /* On a server, no one can hear you crash */ }
        }
    }

    fun sendMessage(mes: MessageToSend) {
        if (mes.text.isEmpty())
            return
        LOG.v("Main/Send_Edit", "Sending to ${mes.chatId}...")
        while (mes.text.length > 4094) {
            val tearThere = findLastSeparator(mes.text)
            val text = mes.text
            mes.text = text.substring(0, tearThere)
            sendMessage(mes)
            mes.text = text.substring(tearThere)
        }
        when (mes.action) {
            TelegramAction.SEND -> {
                val msg = SendMessage().setChatId(mes.chatId).enableMarkdown(mes.markdown).setText(mes.text)
                        .setReplyMarkup(mes.inlineKeyboard)
                if (mes.silent)
                    msg.disableNotification()
                execute(msg)
            }
            TelegramAction.EDIT ->
                execute(EditMessageText().setChatId(mes.chatId).enableMarkdown(mes.markdown).setText(mes.text)
                        .setMessageId(mes.messageId).setReplyMarkup(mes.inlineKeyboard))
        }
        Thread.sleep((1000 / 30.0).toLong()) // Avoiding flood limits
    }

    private fun setDefaultKeyboard() {
        Common.defaultKeyboard = InlineKeyboardMarkup().setKeyboard(listOf(
                listOf(button("Расписание", arrayOf(3, 0)),
                        button("Изменения", arrayOf(4, 0)),
                        button("Свободные", arrayOf(5, 0))),
                listOf(button("Звонки", arrayOf(2, 1)),
                        button("Настройки", arrayOf(6, 0)),
                        button("Прочее", arrayOf(7, 0)))
        ))
    }

    private fun findLastSeparator(text: String): Int {
        val str = text.substring(0, 4094)
        var endPos = str.lastIndexOf('\n')
        if (endPos == -1)
            endPos = str.lastIndexOf(' ')
        if (endPos < 3800)
            endPos = 4094
        return endPos
    }

    private fun exceptionToString(e: Exception): String {
        val sb = StringBuilder().appendln(e.message ?: "An undefined error")
        e.stackTrace.forEach { sb.append("${it.fileName} ${it.className} ${it.methodName} ${it.lineNumber}\n") }
        return sb.toString()
    }
}
