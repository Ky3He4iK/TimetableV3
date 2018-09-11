package com.ky3he4ik

import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.TelegramBotsApi
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.api.objects.CallbackQuery
import org.telegram.telegrambots.api.objects.Message
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.exceptions.TelegramApiException

import java.time.LocalDateTime
import kotlin.concurrent.thread

fun main(args: Array<String>) {
    BotConfig.isDebug = true
    thread(isDaemon = true, name = "Time thread") {
        while (true) {
            try {
                var hour = LocalDateTime.now().hour + 3
                var day = LocalDateTime.now().dayOfWeek.value
                if (hour > 23) {
                    hour -= 24
                    day = (day + 1) % 7
                }
                if (day > 0)
                    day--
                Common.currentLesson = 7
                for (it in 0 until Constants.lessonTimes.size)
                    if (hour < Constants.lessonTimes[it][0] ||
                            (hour == Constants.lessonTimes[it][0] && LocalDateTime.now().minute < Constants.lessonTimes[it][1]))
                        Common.currentLesson =  it
                Common.currentDay = day
                println("$day $hour")
                Thread.sleep(60 * 1000L)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    main(args.isNotEmpty())
}

fun main(debug: Boolean) {
    BotConfig.isDebug = debug
    try {
        ApiContextInitializer.init()
        val telegramBotsApi = TelegramBotsApi()
        try {
            telegramBotsApi.registerBot(Main())
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    while (Common.work)
        Thread.sleep(1000)
    Main.db.writeAll()
}

class Main : TelegramLongPollingBot() {
    init {
        setDefaultKeyboard()
        db = Database()
        thread(isDaemon = true, name = "Send thread") {
            while (true) {
                try {
                    if (Common.emergencyMessageQueue.isNotEmpty()) {
                        try {
                            sendMessage(Common.emergencyMessageQueue.first)
                        } catch (e: TelegramApiException) {
                            println("${Common.emergencyMessageQueue.first.text} ${e.message}")
                            e.printStackTrace()
                        }
                        Common.emergencyMessageQueue.removeFirst()
                    } else if (Common.messageQueue.isNotEmpty()) {
                        try {
                            sendMessage(Common.messageQueue.first)
                        } catch (e: TelegramApiException) {
                            println("${Common.messageQueue.first.text} ${e.message}")
                            e.printStackTrace()
                        }
                        Common.messageQueue.removeFirst()
                    }
                } catch (e: Exception) {
                    println(e.message)
                    e.printStackTrace()
                }
                Thread.sleep(1)
            }
        }
        println("Started")
        println(db.timetable.getTimetable(Type.CLASS.data, db.getClassInd("11е")))
    }

    override fun getBotUsername(): String = if (BotConfig.isDebug) BotConfig.TestUsername else BotConfig.ReleaseUsername

    override fun getBotToken(): String = if (BotConfig.isDebug) BotConfig.TestingToken else BotConfig.ReleaseToken

    override fun onUpdateReceived(update: Update?) {
        try {
            if (update == null)
                return
            if (update.hasMessage() && update.message.hasText())
                onMessage(update.message)
            else if (update.hasCallbackQuery())
                onCallbackQuery(update.callbackQuery)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                Common.sendMessage(Common.exceptionToString(e), inlineKeyboard = null, emergency = true)
                val text = "Shit happens. " + (e.message ?: "Unknown shit")
                if (update?.message?.chatId != null)
                    Common.sendMessage(text, chatId = update.message.chatId,
                            inlineKeyboard = null, emergency = true)
                if (update?.callbackQuery?.message?.chatId != null && update.callbackQuery?.message?.messageId != null)
                    Common.editMessage(text, chatId = update.callbackQuery.message.chatId,
                            messageId = update.callbackQuery.message.messageId, inlineKeyboard = null, emergency = true)
            } catch (e: Exception) { /* On a server, nobody can hear you fail */ }
        }
        //TODO: add checking for stop and add/remove to group
    }

    private fun onMessage(message: Message) {
        onUserMes(message)
        val cmd = extractCmd(message.text)
        val text: String
        var keyboard: InlineKeyboardMarkup? = Common.defaultKeyboard
        when {
            compareCommand(cmd, "ping") -> {
                Common.sendMessage("Pong!", message.chatId, null)
                return
            }
            compareCommand(cmd, "start") -> {
                if (db.hasUser(message.from.id.toLong())) {
                    db.addUser(message)
                    keyboard = InlineKeyboardMarkup().setKeyboard(listOf(listOf(InlineKeyboardButton("Дальше")
                            .setCallbackData("1.0.6.5.-1.-1.-1.-1"))))
                    text = "Привет, " + message.from.firstName + "!\nЯ буду показывать тебе расписание, " +
                            "но сначала я должен узнать немного о тебе"
                    db.setUserState(message.from.id.toLong(), arrayListOf(1, 0, 2, 0, -1, -1, -1, -1))
                } else
                    text = message.from.firstName + ", ты уже зарегистрирован"
            }
            compareCommand(cmd, "menu") -> {
                db.setUserState(message.from.id.toLong(), arrayListOf(2, 0, -1, -1, -1, -1, -1, -1))
                text = "Чем могу быть полезен?"
            }
            compareCommand(cmd, "help") -> text = Constants.helpMes

            //sudo works only in private
            compareCommand(cmd, "sudo") && isAdmin(message.chatId) -> {
                text = "/sudoUpdate [any]\n/sudoUpdateSlow [any]\n/sudoWrite\n/sudoGet\n" +
                        "/sudoAns <id> <text> - ans to feedback\n/sudoSay <id> <text> - say by id\n" +
                        "/sudoSend <text> - send to all\n/sudoStop"
                keyboard = null
            }
            compareCommand(cmd, "sudoWrite") && isAdmin(message.chatId) -> {
                db.writeAll()
                text = "OK"
                keyboard = null
            }
            compareCommand(cmd, "sudoUpdate") && isAdmin(message.chatId) -> {
                Common.sendMessage("Starting update...", inlineKeyboard = null)
                db.update(full = message.text.contains(' '), fast = true)
                text = "OK"
                keyboard = null
            }
            compareCommand(cmd, "sudoUpdateSlow") && isAdmin(message.chatId) -> {
                db.update(full = message.text.contains(' '), fast = false)
                text = "OK"
                keyboard = null
            }
            compareCommand(cmd, "sudoGet") && isAdmin(message.chatId) -> {
                val strBuilder = StringBuilder("feedback:\n")
                db.feedbackArray.forEach { strBuilder.append("${it.internalId}. ${it.userId} " +
                        "(@${db.getUser(it.userId)!!.username}; ${db.getUser(it.userId)!!.firstName})\n${it.text}\n\n")}
                text = strBuilder.substring(0, strBuilder.length - 2)
                keyboard = null
            }
            compareCommand(cmd, "sudoAns") && isAdmin(message.chatId) -> {
                val ta = message.text.split(' ')
                val feedback = db.feedbackArray[ta[1].toInt()]
                val txt = message.text.substring(ta[0].length + ta[1].length + 2)
                Common.sendMessage("Ответ на твой фидбек:\n$txt", feedback.userId, null)
                db.removeFeedback(ta[1].toInt())
                text = "OK"
                keyboard = null
            }
            compareCommand(cmd, "sudoSay") && isAdmin(message.chatId) -> {
                val ta = message.text.split(' ')
                val userId = ta[1].toLong()
                val txt = message.text.substring(ta[0].length + ta[1].length + 2)
                Common.sendMessage(txt, userId, null)
                text = "OK"
                keyboard = null
            }
            compareCommand(cmd, "sudoSend") && isAdmin(message.chatId) -> {
                val txt = message.text.substring(message.text.indexOf(' ') + 1)
                db.sendToAll(txt)
                text = "OK"
                keyboard = null
            }
            compareCommand(cmd, "sudoStop") && isAdmin(message.chatId) -> {
                if (Common.emergencyMessageQueue.isNotEmpty() || Common.messageQueue.isNotEmpty())
                    text = "Queue to send is not empty"
                else {
                    db.writeAll()
                    Common.work = false
                    text = "OK"
                }
                keyboard = null
            }

            cmd.matches(Regex("[rRtTcC]_\\d+")) ||
                    cmd.startsWith("c_", ignoreCase = true) || cmd.startsWith("t_", ignoreCase = true) ||
                    cmd.startsWith("r_", ignoreCase = true) -> {
                val txt = StringBuilder()
                val type: Int = when (cmd[0]) {
                    'c' -> Type.CLASS.data
                    'r' -> Type.ROOM.data
                    't' -> Type.TEACHER.data
                    else -> {
                        txt.append("Прости, я тебя не понимаю\n")
                        Type.OTHER.data
                    }
                }
                val ind = cmd.substring(2).toIntOrNull()
                when {
                    ind == null -> txt.append("Что-то пошло не так")
                    !db.timetable.has(type, ind - 1) -> txt.append("Таких у меня нет")
                    else -> {
                        val currState = db.getUser(message.from.id.toLong())?.settings?.currentState?.toMutableList()
                                ?: mutableListOf(2, 0, -1, -1, -1, -1, -1, -1)
                        currState[4] = type
                        currState[5] = ind - 1
                        callbackQuery(message.from.id.toLong(), currState, message.chatId)
                    }
                }
                text = txt.toString()
            }
            db.hasUser(message.from.id.toLong()) && db.getUser(message.from.id.toLong())!!.settings.currentState == arrayListOf(7, 3, -1, -1, -1, -1, -1, -1) -> {
                db.addFeedback(message.from.id.toLong(), message.text)
                text = "Спасибо за отзыв! В скором времени ты получишь ответ от моего создателя"
            }
            cmd.matches(Regex("(-?[\\d]+\\.){7}-?[\\d]+")) -> {
                // I had a problem
                // So I used regexp
                // Now I have 2 problems
                callbackQuery(message.from.id.toLong(), message.text.split('.').map(String::toInt), message.chatId)
                return
            }
            else -> text = "Моя твоя не понимать (/menu)"
        }
        if (text.isNotEmpty())
            Common.sendMessage(text, message.chatId, keyboard)
    }

    private fun onCallbackQuery(callbackQuery: CallbackQuery) {
        try {
            val data = callbackQuery.data.split('.').map(String::toInt)
            db.setUserState(callbackQuery.from.id.toLong(), data)
            val userId = callbackQuery.from.id.toLong()
            callbackQuery(userId, data, callbackQuery.message.chatId, callbackQuery.message.messageId, callbackQuery.message.text)
        } catch (e: Exception) {
            println(e.message)
            e.printStackTrace()
            Common.sendMessage("Я упаль(\n${e.message}", inlineKeyboard = null, emergency = true)
            Common.sendMessage("Что-то пошло не так, и оно упало", callbackQuery.from.id.toLong(), inlineKeyboard = null)
        }
    }

    private fun sendMessage(mes: MessageToSend) {
        if (mes.text.isEmpty())
            return
        println("Sending...")
        when {
            mes.text.length > 4094 -> {
                while (mes.text.length > 4094) {
                    val li = findLastSep(mes.text)
                    mes.text = mes.text.substring(0, li)
                    sendMessage(mes)
                    mes.text = mes.text.substring(li)
                }
                sendMessage(mes)
                return
            }
            mes.isSend -> {
                val msg = SendMessage().setChatId(mes.chatId).enableMarkdown(mes.markdown).setText(mes.text)
                        .setReplyMarkup(mes.inlineKeyboard)
                if (mes.silent)
                    msg.disableNotification()
                sendApiMethod(msg)
                Thread.sleep((1000 / 30.0).toLong())
            }
            else -> {
                val msg = EditMessageText().setChatId(mes.chatId).enableMarkdown(mes.markdown).setText(mes.text)
                        .setMessageId(mes.messageId).setReplyMarkup(mes.inlineKeyboard)
                sendApiMethod(msg)
                Thread.sleep((1000 / 30.0).toLong())
            }
        }
    }

    private fun setDefaultKeyboard() {
        val row1 = listOf(InlineKeyboardButton("Расписание").setCallbackData("3.0.-1.-1.-1.-1.-1.-1"),
                InlineKeyboardButton("Изменения").setCallbackData("4.0.-1.-1.-1.-1.-1.-1"),
                InlineKeyboardButton("Свободные").setCallbackData("5.0.-1.-1.-1.-1.-1.-1"))
        val row2 = listOf(InlineKeyboardButton("Звонки").setCallbackData("2.1.-1.-1.-1.-1.-1.-1"),
                InlineKeyboardButton("Настройки").setCallbackData("6.0.-1.-1.-1.-1.-1.-1"),
                InlineKeyboardButton("Прочее").setCallbackData("7.0.-1.-1.-1.-1.-1.-1"))
        Common.defaultKeyboard = InlineKeyboardMarkup().setKeyboard(listOf(row1, row2))
    }

    private fun extractCmd(command: String): String {
        val spaceInd = command.indexOf(' ')
        return command.substring(if (command[0] == '/') 1 else 0, if (spaceInd != -1) spaceInd else command.length)
    }

    private fun compareCommand(cmd: String, pattern: String): Boolean =
            cmd.equals(pattern, ignoreCase = true) || cmd.equals("$pattern@$botUsername", ignoreCase = true)

    private fun isAdmin(userId: Long): Boolean = userId == Constants.fatherInd

    private fun findLastSep(text: String): Int {
        val str = text.substring(0, 4094)
        var endPos = str.lastIndexOf('\n')
        if (endPos == -1)
            endPos = str.lastIndexOf(' ')
        if (endPos < 3800)
            endPos = 4094
        return endPos
    }

    private fun onUserMes(message: Message) {
        db.updateUserInfo(message.from.id.toLong(), message.from.userName, message.from.firstName, message.date)
        println("Message!")
    }

    companion object {
        lateinit var db: Database
    }
}
