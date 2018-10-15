package com.ky3he4ik

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import java.text.SimpleDateFormat
import java.util.*


data class MessageToSend(var text: String, val chatId: Long,
                         val inlineKeyboard: InlineKeyboardMarkup?, val messageId: Int = -1,
                         val silent: Boolean = false, val markdown: Boolean, val action: TelegramAction) {

    constructor(ioParams: IOParams, silent: Boolean = false, messageId: Int = -1, action: TelegramAction):
            this(text = ioParams.text, chatId = ioParams.chatId, inlineKeyboard = ioParams.inlineKeyboard,
                    silent = silent, messageId = messageId, markdown = ioParams.markdown, action = action)
}

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

    fun sendMessage(params: IOParams, silent: Boolean = false) {
        val mes = MessageToSend(params, silent = silent, action = TelegramAction.SEND)
        if (params.emergency)
            emergencyMessageQueue.add(mes)
        else
            messageQueue.add(mes)
    }

    fun editMessage(params: IOParams, messageId: Int) {
        val mes = MessageToSend(params, messageId = messageId, action = TelegramAction.EDIT)
        if (params.emergency)
            emergencyMessageQueue.add(mes)
        else
            messageQueue.add(mes)
    }

    fun getCurrTime(): String = SimpleDateFormat("yyyy-MM-dd_hh-mm-ss").format(Date())
}
