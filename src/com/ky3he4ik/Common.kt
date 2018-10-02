package com.ky3he4ik

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import java.util.*


data class MessageToSend(var text: String, val chatId: Long,
                         val inlineKeyboard: InlineKeyboardMarkup?, val messageId: Int = -1,
                         val silent: Boolean = false, val markdown: Boolean, val action: TelegramAction)

data class IOParams(val text: String, val chatId: Long = BotConfig.fatherInd,
                    val inlineKeyboard: InlineKeyboardMarkup? = Common.defaultKeyboard, val markdown: Boolean = false,
                    val emergency: Boolean = false)

//messageId - for edit; silent - for send
internal object Common {
    var currentLesson = 0
    var currentDay = 0
    val messageQueue = LinkedList<MessageToSend>()
    val emergencyMessageQueue = LinkedList<MessageToSend>()
    var defaultKeyboard: InlineKeyboardMarkup? = null
    var work: Boolean = true

    fun sendMessage(params: IOParams, silent: Boolean = false) =
        (if (params.emergency) emergencyMessageQueue else messageQueue).add(MessageToSend(text = params.text, chatId = params.chatId,
                inlineKeyboard = params.inlineKeyboard, silent = silent, markdown = params.markdown, action = TelegramAction.SEND))
    fun editMessage(params: IOParams, messageId: Int) =
        (if (params.emergency) emergencyMessageQueue else messageQueue).add(MessageToSend(text = params.text, chatId = params.chatId,
                inlineKeyboard = params.inlineKeyboard, messageId = messageId, markdown = params.markdown, action = TelegramAction.EDIT))

    fun sendMessage(text: String, chatId: Long = BotConfig.fatherInd,
                    inlineKeyboard: InlineKeyboardMarkup? = defaultKeyboard, silent: Boolean = false,
                    markdown: Boolean = false, emergency: Boolean = false) =
            (if (emergency) emergencyMessageQueue else messageQueue).add(MessageToSend(text = text, chatId = chatId,
                    inlineKeyboard = inlineKeyboard, silent = silent, markdown = markdown, action = TelegramAction.SEND))

    fun editMessage(text: String, chatId: Long,
                    inlineKeyboard: InlineKeyboardMarkup? = defaultKeyboard, messageId: Int,
                    markdown: Boolean = false, emergency: Boolean = false) =
            (if (emergency) emergencyMessageQueue else messageQueue).add(MessageToSend(text = text, chatId = chatId,
                    inlineKeyboard = inlineKeyboard, messageId = messageId, markdown = markdown, action = TelegramAction.EDIT))

    fun exceptionToString(e: Exception): String {
        val sb = StringBuilder().appendln(e.message ?: "An defined error")
        e.stackTrace.forEach {sb.append("${it.fileName} ${it.className} ${it.methodName} ${it.lineNumber}\n") }
        return sb.toString()
    }

    fun log(tag: String, message: String) {
        println("${Constants.logBoundaryOpen}\n$tag: $message\n${Constants.logBoundaryClose}")
    }

    fun log(tag: String, message: String?, e: Exception) {
        println("${Constants.logBoundaryOpen}\n$tag: $message")
        e.printStackTrace()
        Thread.sleep(10)
        println("\n${Constants.logBoundaryClose}")
        //TODO: timestamp in logs
    }
}
