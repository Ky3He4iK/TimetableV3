package com.ky3he4ik

import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

import com.ky3he4ik.Common.sendMessage

val bot = Main()

fun main(args: Array<String>) {
    Threads.startDaemonThread("Time thread") { Threads.timeThread(); }
    BotConfig.isDebug = args.isNotEmpty()
    BotConfig.isOffline = true

    if (BotConfig.isOffline)
        while (true)
            bot.onCLImes(readLine() ?: return)
    else {
        init()
        run()
    }
}

fun run() {
    while (Common.work)
        Thread.sleep(1000)
    bot.db.writeAll()
}

fun init() {
    try {
        LOG.logLevel = if (BotConfig.isDebug) LOG.LogLevel.VERBOSE else LOG.LogLevel.INFO
        ApiContextInitializer.init()
        val telegramBotsApi = TelegramBotsApi()
        try {
            telegramBotsApi.registerBot(bot)
        } catch (e: TelegramApiException) {
            LOG.e("Main/init", e.message ?: "Error on init", e)

        }
    } catch (e: Exception) {
        LOG.e("Main/init", e.message ?: "Error on init", e)
    }
}

class Main : TelegramLongPollingBot() {
    var db = Database(if (BotConfig.isOffline) LoadType.READ.data else LoadType.CREATE.data)
    init {
        setDefaultKeyboard()
        Threads.startDaemonThread("Send thread") { Threads.sendThread(); }
        LOG.i("Main/Main/init", "Started")
        LOG.d("Main/Main/init_test", db.timetable.getTimetable(Type.CLASS.data, db.getClassInd("11е")))
    }

    override fun getBotUsername(): String = if (BotConfig.isDebug) BotConfig.TestUsername else BotConfig.ReleaseUsername

    override fun getBotToken(): String = if (BotConfig.isDebug) BotConfig.TestingToken else BotConfig.ReleaseToken

    override fun onUpdateReceived(update: Update?) {
        try {
            when {
                update == null -> return
                update.hasMessage() && update.message.hasText() -> onMessage(update.message)
                update.hasCallbackQuery() -> onCallbackQuery(update.callbackQuery)
            }
        } catch (e: Exception) {
            LOG.e("Main/onUpdate", e.message, e)
            try { // I put try..catch into try..catch. Now I can handle exceptions while handling exceptions (if handle == ignore)
                sendMessage(IOParams(Common.exceptionToString(e), inlineKeyboard = null, emergency = true))
                val chatId = when {
                    update?.message?.chatId != null -> update.message.chatId
                    update?.callbackQuery?.message?.chatId != null && update.callbackQuery?.message?.messageId != null ->
                        update.callbackQuery.message.chatId
                    else -> return
                }
                sendMessage(IOParams("Я упаль пытаясь обработать этот запрос =(", chatId = chatId, inlineKeyboard = null, emergency = true))
            } catch (e: Exception) { /* On a server, no one can hear you crash */ }
        }
        //TODO: add checking for stop and add/remove to group
    }

    fun sendMessage(mes: MessageToSend) {
        if (mes.text.isEmpty())
            return
        LOG.v("Main/Send_Edit", "Sending...")
        when {
            mes.text.length > 4094 -> {
                while (mes.text.length > 4094) {
                    val tearThere = findLastSeparator(mes.text)
                    val text = mes.text
                    mes.text = text.substring(0, tearThere)
                    sendMessage(mes)
                    mes.text = text.substring(tearThere)
                }
                sendMessage(mes)
            }
            BotConfig.isOffline -> println("${mes.action} to ${mes.chatId}. is markdown: ${mes.markdown}. is silent: " +
                    "${mes.silent}\n${mes.text}\n${kbdToStr(mes.inlineKeyboard)}")
            mes.action == TelegramAction.SEND -> {
                val msg = SendMessage().setChatId(mes.chatId).enableMarkdown(mes.markdown).setText(mes.text)
                        .setReplyMarkup(mes.inlineKeyboard)
                if (mes.silent)
                    msg.disableNotification()
                execute(msg)
            }
            mes.action == TelegramAction.EDIT ->
                execute(EditMessageText().setChatId(mes.chatId).enableMarkdown(mes.markdown).setText(mes.text)
                        .setMessageId(mes.messageId).setReplyMarkup(mes.inlineKeyboard))
            else ->
                LOG.e("Main/SendMes", "Unexpected situation: ${mes.chatId} ${mes.action} ${mes.text}")
                // Unexpected situation. Zero chances to salvation
        }
        Thread.sleep((1000 / 30.0).toLong()) // Avoiding flood limits
    }

    fun onCLImes(message: String) {
        //TODO: add processing as message
        val data = message.split('.').map(String::toInt)
        db.setUserState(-1, data)
        callbackQuery(-1, data, -1, messageText = message)
    }

    private fun setDefaultKeyboard() {
        Common.defaultKeyboard = InlineKeyboardMarkup().setKeyboard(listOf(
                listOf(InlineKeyboardButton("Расписание").setCallbackData("3.0.-1.-1.-1.-1.-1.-1"),
                        InlineKeyboardButton("Изменения").setCallbackData("4.0.-1.-1.-1.-1.-1.-1"),
                        InlineKeyboardButton("Свободные").setCallbackData("5.0.-1.-1.-1.-1.-1.-1")),
                listOf(InlineKeyboardButton("Звонки").setCallbackData("2.1.-1.-1.-1.-1.-1.-1"),
                        InlineKeyboardButton("Настройки").setCallbackData("6.0.-1.-1.-1.-1.-1.-1"),
                        InlineKeyboardButton("Прочее").setCallbackData("7.0.-1.-1.-1.-1.-1.-1"))
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

    private fun kbdToStr(kdb: InlineKeyboardMarkup?): String? {
        if (kdb == null)
            return null
        val sb = StringBuilder()
        for (row in kdb.keyboard) {
            sb.append("{ ")
            for (button in row)
                sb.append(button.text).append(" [").append(button.callbackData).append("]; ")
            sb.append("}\n")
        }
        return sb.toString()
    }

    private fun exceptionToString(e: Exception): String {
        val sb = StringBuilder().appendln(e.message ?: "An undefined error")
        e.stackTrace.forEach { sb.append("${it.fileName} ${it.className} ${it.methodName} ${it.lineNumber}\n") }
        return sb.toString()
    }
}
