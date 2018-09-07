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
            compareCommand(cmd, "help") -> text = BotConfig.helpMes

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
                Common.sendMessage("Starting update...")
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

    private fun callbackQuery(userId: Long, data: List<Int>, chatId: Long, messageId: Int? = null, messageTest: String? = null) {
        var text: String
        var keyboard: InlineKeyboardMarkup? = Common.defaultKeyboard
        var markdown = false
        when (data[0]) {
            1 -> {
                val txt = StringBuilder("Выбери ")
                when (data[1]) {
                    Type.CLASS.data -> {
                        txt.append("класс:\n")
                        for (it in 0 until db.timetable.classCount)
                            txt.append("/c_").append(it + 1).append(" : ").append(db.timetable.classNames[it])
                                    .append('\n')
                    }
                    Type.TEACHER.data -> {
                        txt.append("учителя:\n")
                        for (it in 0 until db.timetable.teacherNames.size)
                            txt.append("/t_").append(it + 1).append(" : ").append(db.timetable.teacherNames[it])
                                    .append('\n')
                    }
                    Type.ROOM.data -> {
                        txt.append("кабинет:\n")
                        for (it in 0 until db.timetable.roomsCount)
                            txt.append("/r_").append(it + 1).append(" : ").append(db.timetable.roomNames[it])
                                    .append('\n')
                    }
                    else -> txt.append("Такого у меня нет")
                }
                db.setUserState(userId, arrayListOf(data[2], data[3], -1, -1, -1, -1, -1, -1))
                keyboard = InlineKeyboardMarkup().setKeyboard(listOf(listOf(
                        InlineKeyboardButton("Класс").setCallbackData("1." + Type.CLASS.data.toString() + '.' +
                                data[2].toString() + '.' + data[3].toString() + ".-1.-1.-1.-1"),
                        InlineKeyboardButton("Учитель").setCallbackData("1." + Type.TEACHER.data.toString() +
                                '.' + data[2].toString() + '.' + data[3].toString() + ".-1.-1.-1.-1"),
                        InlineKeyboardButton("Кабинет").setCallbackData("1." + Type.ROOM.data.toString() + '.' +
                                data[2].toString() + '.' + data[3].toString() + ".-1.-1.-1.-1")
                )))
                text = txt.toString()
            }
            2 -> text = if (data[1] == 1) "Расписание звонков:\n" + Constants.bells else "Чем могу помочь?"
            3 -> {
                val userSettings = db.getUser(userId)?.settings
                var type = data[4]
                var typeInd = data[5]
                var dayInd = data[6]
                if (dayInd == -1)
                    dayInd = 7
                if (typeInd == -1)
                    typeInd = userSettings?.typeInd ?: 0
                if (type == -1)
                    type = userSettings?.type ?: Type.CLASS.data
                var presentation = data[1]
                if (presentation == 0)
                    presentation = userSettings?.defaultPresentation ?: Presentation.ALL_WEEK.data
                text = db.timetable.getTimetablePres(presentation, type, typeInd, dayInd)
                markdown = true
                val ending = arrayOf(type, typeInd, dayInd, data[1])
                val altEnding = arrayOf(-1, -1) + ending
                keyboard = InlineKeyboardMarkup().setKeyboard(listOf(
                        listOf(InlineKeyboardButton("Сегодня").setCallbackData(arrayToString(arrayOf(
                                3, Presentation.TODAY.data) + altEnding)),
                                InlineKeyboardButton("Сейчас").setCallbackData(arrayToString(arrayOf(
                                        3, Presentation.NEAR.data) + altEnding)),
                                InlineKeyboardButton("Завтра").setCallbackData(arrayToString(arrayOf(
                                        3, Presentation.TOMORROW.data) + altEnding))),
                        listOf(InlineKeyboardButton("На неделю").setCallbackData(arrayToString(arrayOf(
                                3, Presentation.ALL_WEEK.data) + altEnding)),
                                InlineKeyboardButton("Конкретный день").setCallbackData(arrayToString(arrayOf(
                                        8, 0, 3, 7) + ending)),
                                InlineKeyboardButton("Поменять").setCallbackData(arrayToString(arrayOf(
                                        1, 0, 3, data[1]) + ending))),
                        listOf(InlineKeyboardButton("Сброс").setCallbackData(arrayToString(arrayOf(3, 0))),
                                InlineKeyboardButton("Назад").setCallbackData(arrayToString(arrayOf(2, 0))))))
            }
            4 -> {
                val userSettings = db.getUser(userId)?.settings
                var presentation = data[1]
                if (presentation == 0)
                    presentation = userSettings?.defaultPresentationChanges ?: Presentation.ALL_CLASSES.data
                text = if (presentation == Presentation.OTHER.data && data[5] != -1)
                    db.timetable.changes.getChanges(db.timetable, data[5])
                else
                    db.timetable.changes.getChangesPres(presentation, db.timetable, userSettings?.typeInd ?: 0)
                var layer = listOf(
                        InlineKeyboardButton("Все классы").setCallbackData(arrayToString(arrayOf(
                                4, Presentation.ALL_CLASSES.data, -1, -1, -1, -1, -1, Presentation.ALL_CLASSES.data))),
                        InlineKeyboardButton("Определённый класс").setCallbackData(arrayToString(arrayOf(
                                9, 0, 4, Presentation.OTHER.data, -1, -1, -1, Presentation.OTHER.data))),
                        InlineKeyboardButton("\"Мой\" класс").setCallbackData(arrayToString(arrayOf(
                                4, Presentation.CURRENT_CLASS.data, -1, -1, -1, -1, -1, Presentation.CURRENT_CLASS.data))))
                if (userSettings?.type == Type.CLASS.data)
                    layer = layer.dropLast(1)
                keyboard = InlineKeyboardMarkup().setKeyboard(listOf(layer,
                        listOf(InlineKeyboardButton("Назад").setCallbackData(arrayToString(arrayOf(2, 0))))))
            }
            5 -> {
                val userSettings = db.getUser(userId)?.settings
                var presentation = data[1]
                if (presentation == 0)
                    presentation = userSettings?.defaultPresentationRooms ?: Presentation.ALL_WEEK.data
                var dayInd = data[6]
                if (dayInd == -1)
                    dayInd = 7
                text = db.timetable.freeRooms.getFreeRoomsPresentation(presentation, db.timetable, dayInd)
                markdown = true
                keyboard = InlineKeyboardMarkup().setKeyboard(listOf(
                        listOf(InlineKeyboardButton("Сегодня").setCallbackData(arrayToString(arrayOf(
                                5, Presentation.TODAY.data))),
                                InlineKeyboardButton("Сейчас").setCallbackData(arrayToString(arrayOf(
                                        5, Presentation.NEAR.data))),
                                InlineKeyboardButton("Завтра").setCallbackData(arrayToString(arrayOf(
                                        5, Presentation.TOMORROW.data)))),
                        listOf(InlineKeyboardButton("На неделю").setCallbackData(arrayToString(arrayOf(
                                5, Presentation.ALL_WEEK.data))),
                                InlineKeyboardButton("Конкретный день").setCallbackData(arrayToString(arrayOf(
                                        8, 0, 5, Presentation.OTHER.data)))),
                        listOf(InlineKeyboardButton("Назад").setCallbackData(arrayToString(arrayOf(2, 0))))))
            }
            6 -> {
                val userSettings = db.getUser(userId)?.settings
                if (userSettings == null)
                    text = "Хмм... Я тебя не могу узнать. Нажми /start, дабы я смог вспомнить тебя"
                else {
                    if (data[4] != -1 && data[5] != -1)
                        userSettings.type = data[4]
                    userSettings.typeInd = data[5]
                    text = userSettings.toString(db)
                    keyboard = getSettingsKeyboard()
                    when (data[1]) {
                        0, 1 -> {
                            if (data[1] == 1)
                                userSettings.notify = !userSettings.notify
                        }
                        2 -> if (data[7] != -1)
                            userSettings.defaultPresentation = data[7]
                        else {
                            text = "Выбирай!"
                            keyboard = InlineKeyboardMarkup().setKeyboard(listOf(
                                    listOf(InlineKeyboardButton("Вся неделя").setCallbackData(arrayToString(arrayOf(
                                            6, 2, -1, -1, -1, -1, -1, Presentation.ALL_WEEK.data))),
                                            InlineKeyboardButton("Текущий день").setCallbackData(arrayToString(arrayOf(
                                                    6, 2, -1, -1, -1, -1, -1, Presentation.TODAY.data))),
                                            InlineKeyboardButton("Следующий день").setCallbackData(arrayToString(arrayOf(
                                                    6, 2, -1, -1, -1, -1, -1, Presentation.TOMORROW.data)))),
                                    listOf(InlineKeyboardButton("Ближайший урок").setCallbackData(arrayToString(arrayOf(
                                            6, 2, -1, -1, -1, -1, -1, Presentation.NEAR.data)))),
                                    listOf(InlineKeyboardButton("Назад").setCallbackData(arrayToString(arrayOf(2, 0))))))
                        }
                        3 -> if (data[7] != -1)
                            userSettings.defaultPresentationChanges = data[7]
                        else {
                            text = "Выбирай!"
                            var layer = listOf(InlineKeyboardButton("Все классы").setCallbackData(arrayToString(arrayOf(
                                    6, 3, -1, -1, -1, -1, -1, Presentation.ALL_CLASSES.data))),
                                    InlineKeyboardButton("\"Мой\" класс").setCallbackData(arrayToString(arrayOf(
                                            6, 3, -1, -1, -1, -1, -1, Presentation.CURRENT_CLASS.data))))
                            if (userSettings.type != Type.CLASS.data)
                                layer = layer.dropLast(1)
                            keyboard = InlineKeyboardMarkup().setKeyboard(listOf(
                                    layer,
                                    listOf(InlineKeyboardButton("Назад").setCallbackData(arrayToString(arrayOf(2, 0))))))
                        }
                        4 -> if (data[7] != -1)
                            userSettings.defaultPresentationRooms = data[7]
                        else {
                            text = "Выбирай!"
                            keyboard = InlineKeyboardMarkup().setKeyboard(listOf(
                                    listOf(InlineKeyboardButton("Вся неделя").setCallbackData(arrayToString(arrayOf(
                                            6, 4, -1, -1, -1, -1, -1, Presentation.ALL_WEEK.data))),
                                            InlineKeyboardButton("Текущий день").setCallbackData(arrayToString(arrayOf(
                                                    6, 4, -1, -1, -1, -1, -1, Presentation.TODAY.data))),
                                            InlineKeyboardButton("Следующий день").setCallbackData(arrayToString(arrayOf(
                                                    6, 4, -1, -1, -1, -1, -1, Presentation.TOMORROW.data)))),
                                    listOf(InlineKeyboardButton("Ближайший урок").setCallbackData(arrayToString(arrayOf(
                                            6, 4, -1, -1, -1, -1, -1, Presentation.NEAR.data)))),
                                    listOf(InlineKeyboardButton("Назад").setCallbackData(arrayToString(arrayOf(2, 0))))))
                        }
                    }
                    db.getUser(userId)?.settings = userSettings
                }
            }
            7 -> {
                keyboard = InlineKeyboardMarkup().setKeyboard(listOf(
                        listOf(InlineKeyboardButton("Информация о боте").setCallbackData(arrayToString(arrayOf(
                                7, 1))),
                                InlineKeyboardButton("Помощь").setCallbackData(arrayToString(arrayOf(
                                        7, 2))),
                                InlineKeyboardButton("Обратная связь").setCallbackData(arrayToString(arrayOf(
                                        7, 3)))),
                        listOf(InlineKeyboardButton("Назад").setCallbackData(arrayToString(arrayOf(2, 0))))))
                text = when (data[1]) {
                    0 -> "Da-da?"
                    1 -> "Этот бот просто берет расписание с сайта lyceum.urfu.ru и показывает его в другой, " +
                            "более удобной (надеюсь) форме\nTODO: Сделать нормальный текст тут"
                    2 -> BotConfig.helpMes
                    3 -> "Хочешь сказать что-нибудь о боте? Или просто пообщаться со мной? Напиши что-нибудь"
                    else -> "Эта кнопка не совсем рабочая, но все равно: Da-da?"
                }
            }
            8 -> {
                text = "Choose your day!"
                val d = (data.subList(2, 4) + listOf(-1, -1) + data.subList(4, 6)).toTypedArray()
                keyboard = InlineKeyboardMarkup().setKeyboard(listOf(
                        listOf(InlineKeyboardButton("Пн").setCallbackData(arrayToString(d + arrayOf(
                                0, data[7]))),
                                InlineKeyboardButton("Вт").setCallbackData(arrayToString(d + arrayOf(
                                        1, data[7]))),
                                InlineKeyboardButton("Ср").setCallbackData(arrayToString(d + arrayOf(
                                        2, data[7])))),
                        listOf(InlineKeyboardButton("Чт").setCallbackData(arrayToString(d + arrayOf(
                                3, data[7]))),
                                InlineKeyboardButton("Пт").setCallbackData(arrayToString(d + arrayOf(
                                        4, data[7]))),
                                InlineKeyboardButton("Сб").setCallbackData(arrayToString(d + arrayOf(
                                        5, data[7])))),
                        listOf(InlineKeyboardButton("Вся неделя").setCallbackData(arrayToString(d + arrayOf(
                                7, data[7])))),
                        listOf(InlineKeyboardButton("Назад").setCallbackData(arrayToString(arrayOf(2, 0))))))
            }
            9 -> {
                keyboard = InlineKeyboardMarkup().setKeyboard(listOf(
                        listOf(InlineKeyboardButton("Обратно").setCallbackData(arrayToString(data.subList(2, 4).toTypedArray())))))
                val sb = StringBuilder()
                db.timetable.classNames.forEachIndexed { index, s -> sb.append("/c_").append(index + 1).append(" : ").append(s).append('\n') }
                text = sb.dropLast(1).toString()
                db.setUserState(userId, ArrayList(data.subList(2, 4) + listOf(-1, -1, -1, -1, -1, -1)))
            }
            else -> text = "Ты действительно думаешь, что это была валидная кнопка?"
        }
        if (messageId == null)
            Common.sendMessage(text, chatId, inlineKeyboard = keyboard, markdown = markdown)
        else if (text != messageTest)
            Common.editMessage(text, chatId, inlineKeyboard = keyboard, messageId = messageId, markdown = markdown)
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

    private fun arrayToString(array: Array<Int>): String {
        val nArr = Array(8) { if (it < array.size) array[it] else -1 }
        val sb = StringBuilder()
        nArr.forEach { sb.append(it).append('.') }
        return sb.dropLast(1).toString()
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

    private fun getSettingsKeyboard(): InlineKeyboardMarkup = InlineKeyboardMarkup().setKeyboard(listOf(
                listOf(InlineKeyboardButton("Оповещения вкл/выкл").setCallbackData(arrayToString(arrayOf(
                        6, 1))),
                        InlineKeyboardButton("Изменить себя").setCallbackData(arrayToString(arrayOf(
                                1, 0, 6, 0))),
                        InlineKeyboardButton("Расписание по умолчанию").setCallbackData(arrayToString(arrayOf(
                                6, 2)))),
                listOf(InlineKeyboardButton("Изменения по умолчанию").setCallbackData(arrayToString(arrayOf(
                        6, 3))),
                        InlineKeyboardButton("Свободные кабинеты по умолчанию").setCallbackData(arrayToString(arrayOf(
                                6, 4)))),
                listOf(InlineKeyboardButton("Назад").setCallbackData(arrayToString(arrayOf(2, 0))))))

    companion object {
        lateinit var db: Database
    }
}
