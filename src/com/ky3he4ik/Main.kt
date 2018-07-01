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
    while (Common.work)
        Thread.sleep(1000)
}

class Main : TelegramLongPollingBot() {
    //private val LOGTAG = "MAIN"
    private val db: Database
    init {
        thread(isDaemon = true, name = "Send thread") {
            while (true) {
                try {
                    if (Common.emergencyMessageQueue.isNotEmpty()) {
                        sendMessage(Common.emergencyMessageQueue.first)
                        Common.emergencyMessageQueue.removeFirst()
                        Thread.sleep((1000 / 30.0).toLong())
                    } else if (Common.messageQueue.isNotEmpty()) {
                        sendMessage(Common.messageQueue.first)
                        Common.messageQueue.removeFirst()
                        Thread.sleep((1000 / 30.0).toLong())
                    }
                } catch (e: TelegramApiException) {
                    println(e.message)
                    e.printStackTrace()
                }
            }
        }
        setDefaultKeyBoard()
        db = Database()
    }

    override fun getBotUsername(): String = if (BotConfig.isDebug) BotConfig.TestUsername else BotConfig.ReleaseUsername
    override fun getBotToken(): String = if (BotConfig.isDebug) BotConfig.TestingToken else BotConfig.ReleaseToken
    override fun onUpdateReceived(update: Update?) {
        if (update == null)
            return
        if (update.hasMessage()) {
            onMessage(update.message)
        } else if (update.hasCallbackQuery()) {
            onCallbackQuery(update.callbackQuery)
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
            compareCommand(cmd, "help") -> text = BotConfig.helpMes

            //sudo works only in private
            compareCommand(cmd, "sudo") && isAdmin(message.chatId) -> {
                text = "/sudoUpdate [any]\n/sudoUpdateSlow [any]\n/sudoWrite\n/sudoGet\n" +
                        "/sudoAns <id> <text> - ans to feedback\n/sudoSay <id> <text> - say by id\n" +
                        "/sudoSend <text> - send to all\n/sudoStop"
            }
            compareCommand(cmd, "sudoWrite") && isAdmin(message.chatId) -> {
                db.writeAll()
                text = "OK"
            }
            compareCommand(cmd, "sudoUpdate") && isAdmin(message.chatId) -> {
                db.update(full = message.text.contains(' '), fast = true)
                text = "OK"
            }
            compareCommand(cmd, "sudoUpdateSlow") && isAdmin(message.chatId) -> {
                db.update(full = message.text.contains(' '), fast = false)
                text = "OK"
            }
            compareCommand(cmd, "sudoGet") && isAdmin(message.chatId) -> {
                val strBuilder = StringBuilder("feedback:\n")
                db.feedbackArray.forEach { strBuilder.append("${it.internalId}. ${it.userId} " +
                        "(@${db.getUser(it.userId)!!.username}; ${db.getUser(it.userId)!!.firstname})\n${it.text}\n\n")}
                text = strBuilder.substring(0, strBuilder.length - 2)
                keyboard = null
            }
            compareCommand(cmd, "sudoAns") && isAdmin(message.chatId) -> {
                val ta = message.text.split(' ')
                val feedback = db.feedbackArray[ta[1].toInt()]
                val txt = message.text.substring(ta[0].length + ta[1].length + 2)
                Common.sendMessage(txt, feedback.userId, null)
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
            }
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
                    !db.timetable.has(type, ind) -> txt.append("Таких у меня нет")
                    else -> {
                        val currState = db.getUser(message.from.id.toLong())!!.settings.currentState
                        currState[4] = type
                        currState[5] = ind
                        TODO("callback(user_id=msg.from_user.id, db=db, data=data)")
                    }
                }
                text = txt.toString()
            }
            db.getUser(message.from.id.toLong())!!.settings.currentState == arrayListOf(7, 3, -1, -1, -1, -1, -1, -1)
            -> {
                db.addFeedback(message.from.id.toLong(), message.text)
                text = "Спасибо за отзыв! В скором времени ты получишь ответ от моего создателя"
            }
            else -> text = "Прости, но я тебя не понимаю"
        }
        Common.sendMessage(text, message.chatId, keyboard)

        TODO("not implemented")
    }
    private fun onCallbackQuery(callbackQuery: CallbackQuery) {
        TODO("not implemented")
    }
    private fun sendMessage(mes: MessageToSend) {
        if (mes.text.isEmpty())
            return
        when {
            mes.text.length > 4094 -> {
                val msg = mes
                while (mes.text.length > 4094) {
                    val li = findLastSep(mes.text)
                    msg.text = mes.text.substring(0, li)
                    sendMessage(msg)
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
                sendMessage(msg)
            }
            else -> {
                val msg = EditMessageText().setChatId(mes.chatId).enableMarkdown(mes.markdown).setText(mes.text)
                        .setMessageId(mes.messageId).setReplyMarkup(mes.inlineKeyboard)
                editMessageText(msg)
            }
        }
    }

    private fun setDefaultKeyBoard() {
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
        return command.substring(if (command[0] == '/') 1 else 0, if (spaceInd == -1) spaceInd else command.length)
    }
    private fun compareCommand(cmd: String, pattern: String): Boolean =
            cmd.equals(pattern, ignoreCase = true) || cmd.equals(pattern + botUsername, ignoreCase = true)
    private fun isAdmin(userId: Long): Boolean = userId == Constants.fatherInd
    private fun findLastSep(text: String): Int {
        val str = text.substring(0, 4094)
        var endPos = str.lastIndexOf('\n')
        endPos = if (endPos == -1) str.lastIndexOf(' ') else endPos
        endPos = if (endPos < 3800) 4094 else endPos
        return endPos
    }
    private fun onUserMes(message: Message) {
        db.updateUserInfo(message.from.id.toLong(), message.from.userName, message.from.firstName, message.date)
        println("Message!")
    }

}
/*
        @self.bot.message_handler(content_types=['text'], func=lambda message: message.text[0] == '/')
        def _reply(message):
            self.on_user_message(message.from_user.id, message)
            try:
                Message_handler.message(message, self.db)
            except BaseException as e:
                self.bot.send_message(message.chat.id, "Что-то полшо не так")
                self.write_error(e, message)

        @self.bot.message_handler()
        def _reply_default(message):
            self.on_user_message(message.from_user.id, message)
            try:
                Message_handler.message(message, self.db)
            except BaseException as e:
                self.bot.send_message(message.chat.id, "Что-то пошло не так")
                self.write_error(e, message)

        @self.bot.callback_query_handler(func=lambda call: True)
        def test_callback(call):
            common.logger.info(call)
            try:
                Message_handler.callback(user_id=call.from_user.id, data=self.extract_data_from_text(call.data),
                                         mes_id=call.message.message_id, db=self.db)
            except BaseException as e:
                self.write_error(e)
                self.bot.send_message(call.from_user.id, "Чак Норрис, перелогинься. Ты заставляешь падать ̶м̶о̶и̶ "
                                                         "̶л̶у̶ч̶ш̶и̶е̶ ̶к̶о̶с̶т̶ы̶л̶и̶ мой почти идеальный код")


    def write_error(self, err, mess=None):
        self.send_to_father("An exception occupied!")
        logging.error(err, exc_info=True)
        common.logger.error(err)
        print(str(err), err.args, err.__traceback__)
        f = open("data/Error-bot-" + datetime.datetime.today().strftime("%y%m%d-%Hh") + '.log', 'a')
        text = 'unknown message' if mess is None else (str(mess.text) + '\n' + str(mess.chat.id) + ':' +
                                                       str(mess.from_user.username))
        f.write(datetime.datetime.today().strftime("%M:%S-%f") + str(err) + ' ' + str(err.args) + '\n' + text + '\n\n')
        f.close()

    @staticmethod
    def extract_data_from_text(text):
        return [int(s) for s in text.split('.')]

 */