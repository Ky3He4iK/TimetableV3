package com.ky3he4ik

import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

fun onMessage(message: Message) {
    onUserMes(message)
    LOG.d("Main/Message", "Message!")
    val cmd = extractCmd(message.text)
    val text: String
    var keyboard: InlineKeyboardMarkup? = Common.defaultKeyboard
    val userId = message.from.id.toLong()
    when {
        compareCommand(cmd, "ping") -> {
            Common.sendMessage(IOParams("Pong!", chatId = message.chatId, inlineKeyboard = null))
            return
        }
        compareCommand(cmd, "start") -> {
            if (bot.db.hasUser(userId)) {
                addUser(message)
                val data = arrayListOf(1, 0, 2, 0, -1, -1, -1, -1)
                keyboard = InlineKeyboardMarkup().setKeyboard(listOf(listOf(button("Дальше", arrayOf(1, 0, 6, 5)))))
                text = "Привет, " + message.from.firstName + "!\nЯ буду показывать тебе расписание, но сначала я должен узнать немного о тебе"
                bot.db.setUserState(userId, data)
            } else
                text = message.from.firstName + ", ты уже зарегистрирован"
        }
        compareCommand(cmd, "menu") -> {
            bot.db.setUserState(userId, arrayListOf(2, 0, -1, -1, -1, -1, -1, -1))
            text = "Чем могу быть полезен?"
        }
        compareCommand(cmd, "help") -> text = Constants.helpMes

        //sudo works only in private
        isAdmin(message.chatId) -> {
            when {
                compareCommand(cmd, "sudoWrite") -> {
                    bot.db.writeAll()
                    text = "OK"
                }
                compareCommand(cmd, "sudoUpdate") -> {
                    Common.sendMessage(IOParams("Starting update...", inlineKeyboard = null))
                    bot.db.update(full = message.text.contains(' '), fast = true)
                    text = "OK"
                }
                compareCommand(cmd, "sudoUpdateSlow") -> {
                    bot.db.update(full = message.text.contains(' '), fast = false)
                    text = "OK"
                }
                compareCommand(cmd, "sudoGet") -> {
                    val strBuilder = StringBuilder("feedback:\n")
                    for (fb in bot.db.feedbackArray)
                        strBuilder.append("${fb.internalId}. ${fb.userId} (@${bot.db.getUser(fb.userId)!!.username}; " +
                                "${bot.db.getUser(fb.userId)!!.firstName})\n${fb.text}\n\n")
                    text = strBuilder.dropLast(2).toString()
                }
                compareCommand(cmd, "sudoAns") -> {
                    val ta = message.text.split(' ', limit = 3)
                    Common.sendMessage(IOParams("Ответ на твой фидбек:\n${ta[2]}", bot.db.feedbackArray[ta[1].toInt()].userId, null))
                    bot.db.removeFeedback(ta[1].toInt())
                    text = "OK"
                }
                compareCommand(cmd, "sudoSay") -> {
                    val ta = message.text.split(' ', limit = 3)
                    Common.sendMessage(IOParams(ta[2], ta[1].toLong(), null))
                    text = "OK"
                }
                compareCommand(cmd, "sudoSend") -> {
                    val txt = message.text.substring(message.text.indexOf(' ') + 1)
                    bot.db.sendToAll(txt)
                    text = "OK"
                }
                compareCommand(cmd, "sudoStop") -> {
                    if (Common.emergencyMessageQueue.isNotEmpty() || Common.messageQueue.isNotEmpty())
                        text = "Queue to send is not empty"
                    else {
                        bot.db.writeAll()
                        Common.work = false
                        text = "OK"
                    }
                }
                compareCommand(cmd, "sudoUsers") -> text = bot.db.listUsers()
                else -> text = "/sudoUpdate [any]\n/sudoUpdateSlow [any]\n/sudoWrite\n/sudoGet\n" +
                        "/sudoAns <id> <text> - ans to feedback\n/sudoSay <id> <text> - say by id\n" +
                        "/sudoSend <text> - send to all\n/sudoStop\n/sudoUsers"
            }
            keyboard = null
        }
        cmd.matches(Regex("[rRtTcC]_\\d+")) -> {
            val txt = StringBuilder()
            val type: Int = when (cmd[0]) {
                'c', 'C' -> Type.CLASS.data
                'r', 'R' -> Type.ROOM.data
                't', 'T' -> Type.TEACHER.data
                else -> {
                    txt.append("Прости, я тебя не понимаю\n")
                    Type.OTHER.data
                }
            }
            val ind = cmd.drop(2).toIntOrNull()
            when {
                ind == null -> txt.append("Что-то пошло не так")
                !bot.db.timetable.has(type, ind - 1) -> txt.append("Таких у меня нет")
                else -> {
                    val currState = bot.db.getUser(userId)?.settings?.currentState?.toMutableList() ?: mutableListOf(2, 0, -1, -1, -1, -1, -1, -1)
                    currState[4] = type
                    currState[5] = ind - 1
                    callbackQuery(userId, currState, message.chatId)
                }
            }
            text = txt.toString()
        }
        bot.db.getUser(userId)?.settings?.currentState == arrayListOf(7, 3, -1, -1, -1, -1, -1, -1) -> {
            bot.db.addFeedback(userId, message.text)
            text = "Спасибо за отзыв! В скором времени ты получишь ответ от моего создателя"
        }
        cmd.matches(Regex("(-?[\\d]+\\.){7}-?[\\d]+")) -> {
            // I had a problem
            // So I used regexp
            // Now I have 2 problems
            callbackQuery(userId, message.text.split('.').map(String::toInt), message.chatId)
            return
        }
        else -> text = "Моя твоя не понимать (/menu)"
    }
    if (text.isNotEmpty())
        Common.sendMessage(IOParams(text, message.chatId, keyboard))
}

fun onCallbackQuery(callbackQuery: CallbackQuery) {
    try {
        val data = callbackQuery.data.split('.').map(String::toInt)
        val userId = callbackQuery.from.id.toLong()
        bot.db.setUserState(userId, data)
        callbackQuery(userId, data, callbackQuery.message.chatId, callbackQuery.message.messageId, callbackQuery.message.text)
    } catch (e: Exception) {
        LOG.e("Main/onCallback", e.message, e)
        Common.sendMessage(IOParams("Я упаль(\n${e.message}", inlineKeyboard = null, emergency = true))
        Common.sendMessage(IOParams("Что-то пошло не так, и оно упало", callbackQuery.from.id.toLong(), inlineKeyboard = null))
    }
}

fun callbackQuery(userId: Long, data: List<Int>, chatId: Long, messageId: Int? = null, messageText: String? = null) {
    var text: String
    var keyboard: InlineKeyboardMarkup? = Common.defaultKeyboard
    var markdown = false
    when (data[0]) {
        1 -> {
            val txt = StringBuilder("Выбери ")
            when (data[1]) {
                Type.CLASS.data -> {
                    txt.append("класс:\n")
                    for (it in 0 until bot.db.timetable.classCount)
                        txt.append("/c_").append(it + 1).append(" : ").append(bot.db.timetable.classNames[it]).append('\n')
                }
                Type.TEACHER.data -> {
                    txt.append("учителя:\n")
                    for (it in 0 until bot.db.timetable.teacherNames.size)
                        txt.append("/t_").append(it + 1).append(" : ").append(bot.db.timetable.teacherNames[it]).append('\n')
                }
                Type.ROOM.data -> {
                    txt.append("кабинет:\n")
                    for (it in 0 until bot.db.timetable.roomsCount)
                        txt.append("/r_").append(it + 1).append(" : ").append(bot.db.timetable.roomNames[it]).append('\n')
                }
                else -> txt.append("\nТакого у меня нет")
            }
            bot.db.setUserState(userId, arrayListOf(data[2], data[3], -1, -1, -1, -1, -1, -1))
            keyboard = InlineKeyboardMarkup().setKeyboard(listOf(listOf(
                    button("Класс", arrayOf(1, Type.CLASS.data, data[2], data[3])),
                    button("Учитель", arrayOf(1, Type.TEACHER.data, data[2], data[3])),
                    button("Кабинет", arrayOf(1, Type.ROOM.data, data[2], data[3]))
            )))
            text = txt.toString()
        }
        2 -> text = if (data[1] == 1) "Расписание звонков:\n" + Constants.bells else "Чем могу помочь?"
        3 -> {
            val userSettings = bot.db.getUser(userId)?.settings
            val type = normalize(data[4], -1, userSettings?.type ?: Type.CLASS.data)
            val typeInd = normalize(data[5], -1, userSettings?.typeInd ?: 0)
            val dayInd = normalize(data[6], -1, 7)
            val presentation = normalize(data[1], 0, userSettings?.defaultPresentation ?: Presentation.ALL_WEEK.data)

            text = bot.db.timetable.getTimetablePres(presentation, type, typeInd, dayInd)
            markdown = true
            val ending = arrayOf(type, typeInd, dayInd, data[1])
            val altEnding = arrayOf(-1, -1) + ending
            keyboard = InlineKeyboardMarkup().setKeyboard(listOf(
                    listOf(button("Сегодня", arrayOf(3, Presentation.TODAY.data) + altEnding),
                            button("Сейчас", arrayOf(3, Presentation.NEAR.data) + altEnding),
                            button("Завтра", arrayOf(3, Presentation.TOMORROW.data) + altEnding)),
                    listOf(button("На неделю", arrayOf(3, Presentation.ALL_WEEK.data) + altEnding),
                            button("Конкретный день", arrayOf(8, 0, 3, 7) + ending),
                            button("Поменять", arrayOf(1, 0, 3, data[1]) + ending)),
                    listOf(button("Сброс", arrayOf(3, 0) + ending),
                            button("Назад", arrayOf(2, 0) + ending))
            ))
        }
        4 -> {
            val userSettings = bot.db.getUser(userId)?.settings
            val presentation = normalize(data[1], 0, userSettings?.defaultPresentationRooms ?: Presentation.ALL_CLASSES.data)
            text = if (presentation == Presentation.OTHER.data && data[5] != -1)
                bot.db.timetable.changes.getChanges(bot.db.timetable, data[5])
            else
                bot.db.timetable.changes.getChangesPres(presentation, bot.db.timetable, userSettings?.typeInd ?: 0)
            var layer = listOf(button("Все классы", arrayOf(4, Presentation.ALL_CLASSES.data, -1, -1, -1, -1, -1, Presentation.ALL_CLASSES.data)),
                    button("Определённый класс", arrayOf(9, 0, 4, Presentation.OTHER.data, -1, -1, -1, Presentation.OTHER.data)),
                    button("\"Мой\" класс", arrayOf(4, Presentation.CURRENT_CLASS.data, -1, -1, -1, -1, -1, Presentation.CURRENT_CLASS.data)))
            if (userSettings?.type == Type.CLASS.data)
                layer = layer.dropLast(1)
            keyboard = InlineKeyboardMarkup().setKeyboard(listOf(layer, listOf(button())))
        }
        5 -> {
            val userSettings = bot.db.getUser(userId)?.settings
            val presentation = normalize(data[1], 0, userSettings?.defaultPresentationRooms ?: Presentation.ALL_WEEK.data)
            val dayInd = normalize(data[6], -1, 7)
            text = bot.db.timetable.freeRooms.getFreeRoomsPresentation(presentation, bot.db.timetable, dayInd)
            markdown = true
            keyboard = InlineKeyboardMarkup().setKeyboard(listOf(
                    listOf(button("Сегодня", arrayOf(5, Presentation.TODAY.data)),
                            button("Сейчас", arrayOf(5, Presentation.NEAR.data)),
                            button("Завтра", arrayOf(5, Presentation.TOMORROW.data))),
                    listOf(button("На неделю", arrayOf(5, Presentation.ALL_WEEK.data)),
                            button("Конкретный день", arrayOf(8, 0, 5, Presentation.OTHER.data))),
                    listOf(button("Назад", arrayOf(2, 0)))))
        }
        6 -> {
            var userSettings = bot.db.getUser(userId)?.settings
            if (userSettings == null)
                text = "Хмм... Я тебя не могу узнать. Нажми /start, дабы я смог вспомнить тебя"
            else {
                if (data[4] != -1 && data[5] != -1)
                    bot.db.updateUserSettings(userId, data[4], data[5])
                userSettings = bot.db.getUser(userId)!!.settings
                text = userSettings.toString(bot.db)
                keyboard = getSettingsKeyboard()
                when (data[1]) {
                    1 -> userSettings.notify = !userSettings.notify
                    2 -> {
                        if (data[7] != -1)
                            userSettings.defaultPresentation = data[7]
                        else {
                            text = "Выбирай!"
                            keyboard = InlineKeyboardMarkup().setKeyboard(listOf(
                                    listOf(button("Вся неделя", arrayOf(6, 2, -1, -1, -1, -1, -1, Presentation.ALL_WEEK.data)),
                                            button("Текущий день", arrayOf(6, 2, -1, -1, -1, -1, -1, Presentation.TODAY.data)),
                                            button("Следующий день", arrayOf(6, 2, -1, -1, -1, -1, -1, Presentation.TOMORROW.data))),
                                    listOf(button("Ближайший урок", arrayOf(6, 2, -1, -1, -1, -1, -1, Presentation.NEAR.data))),
                                    listOf(button())))
                        }
                    }
                    3 -> {
                        if (data[7] != -1)
                            userSettings.defaultPresentationChanges = data[7]
                        else {
                            text = "Выбирай!"
                            var layer = listOf(button("Все классы", arrayOf(6, 3, -1, -1, -1, -1, -1, Presentation.ALL_CLASSES.data)),
                                    button("\"Мой\" класс", arrayOf(6, 3, -1, -1, -1, -1, -1, Presentation.CURRENT_CLASS.data)))
                            if (userSettings.type != Type.CLASS.data)
                                layer = layer.dropLast(1)
                            keyboard = InlineKeyboardMarkup().setKeyboard(listOf(layer, listOf(button())))
                        }
                    }
                    4 -> {
                        if (data[7] != -1)
                            userSettings.defaultPresentationRooms = data[7]
                        else {
                            text = "Выбирай!"
                            keyboard = InlineKeyboardMarkup().setKeyboard(listOf(
                                    listOf(button("Вся неделя", arrayOf(6, 4, -1, -1, -1, -1, -1, Presentation.ALL_WEEK.data)),
                                            button("Текущий день", arrayOf(6, 4, -1, -1, -1, -1, -1, Presentation.TODAY.data)),
                                            button("Следующий день", arrayOf(6, 4, -1, -1, -1, -1, -1, Presentation.TOMORROW.data))),
                                    listOf(button("Ближайший урок", arrayOf(6, 4, -1, -1, -1, -1, -1, Presentation.NEAR.data))),
                                    listOf(button("Назад", arrayOf(2, 0)))))
                        }
                    }
                }
                bot.db.getUser(userId)?.settings = userSettings
            }
        }
        7 -> {
            keyboard = InlineKeyboardMarkup().setKeyboard(listOf(
                    listOf(button("Информация о боте", arrayOf(7, 1)),
                            button("Помощь", arrayOf(7, 2)),
                            button("Обратная связь", arrayOf(7, 3))),
                    listOf(button())))
            text = when (data[1]) {
                0 -> "Da-da?"
                1 -> "Этот бот просто берет расписание с сайта lyceum.urfu.ru и показывает его в другой, более удобной (надеюсь) форме\n" +
                        "Я вполне мог накосячить, так что буду рад найденным багам в фидбеке\nVersion 2.0.1 by @Ky3He4iK"
                2 -> Constants.helpMes
                3 -> "Хочешь сказать что-нибудь о боте? Или просто пообщаться со мной? Напиши что-нибудь"
                else -> "Эта кнопка не совсем рабочая, но все равно: Da-da?"
            }
        }
        8 -> {
            text = "Choose your day!"
            val d = (data.subList(2, 4) + listOf(-1, -1) + data.subList(4, 6)).toTypedArray()
            keyboard = InlineKeyboardMarkup().setKeyboard(listOf(
                    listOf(button("Пн", d + arrayOf(0, data[7])),
                            button("Вт", d + arrayOf(1, data[7])),
                            button("Ср", d + arrayOf(2, data[7]))),
                    listOf(button("Чт",d + arrayOf(3, data[7])),
                            button("Пт", d + arrayOf(4, data[7])),
                            button("Сб", d + arrayOf(5, data[7]))),
                    // listOf(button("Вс", d + arrayOf(6, data[7]))),
                    listOf(button("Вся неделя", d + arrayOf(7, data[7]))),
                    listOf(button())))
        }
        9 -> {
            keyboard = InlineKeyboardMarkup().setKeyboard(listOf(listOf(button("Обратно", data.subList(2, 4).toTypedArray()))))
            val sb = StringBuilder()
            bot.db.timetable.classNames.forEachIndexed { index, s -> sb.append("/c_").append(index + 1).append(" : ").append(s).append('\n') }
            text = sb.dropLast(1).toString()
            bot.db.setUserState(userId, ArrayList(data.subList(2, 4) + listOf(-1, -1, -1, -1, -1, -1)))
        }
        else -> text = "Ты действительно думаешь, что это была валидная кнопка?"
    }
    val params = IOParams(text, chatId, inlineKeyboard = keyboard, markdown = markdown)
    if (messageId == null)
        Common.sendMessage(params)
    else if (text != messageText)
        Common.editMessage(params, messageId)
}

private fun arrayToString(array: Array<Int>): String {
    val nArr = Array(8) { if (it < array.size) array[it] else -1 }
    val sb = StringBuilder()
    nArr.forEach { sb.append(it).append('.') }
    return sb.dropLast(1).toString()
}

private fun getSettingsKeyboard(): InlineKeyboardMarkup = InlineKeyboardMarkup().setKeyboard(listOf(
        listOf(button("Оповещения вкл/выкл", arrayOf(6, 1)),
                button("Изменить себя", arrayOf(1, 0, 6, 0)),
                button("Расписание по умолчанию", arrayOf(6, 2))),
        listOf(button("Изменения по умолчанию", arrayOf(6, 3)),
                button("Свободные кабинеты по умолчанию", arrayOf(6, 4))),
        listOf(button("Назад", arrayOf(2, 0)))))

private fun onUserMes(message: Message) 
        = bot.db.updateUserInfo(message.from.id.toLong(), message.from.userName, message.from.firstName, message.date)

private fun extractCmd(command: String): String {
    val spaceInd = command.indexOf(' ')
    return command.substring(if (command[0] == '/') 1 else 0, if (spaceInd != -1) spaceInd else command.length)
}

private fun compareCommand(cmd: String, pattern: String): Boolean =
        cmd.equals(pattern, ignoreCase = true) || cmd.equals("$pattern@$bot.botUsername", ignoreCase = true)

private fun addUser(message: Message) =
        bot.db.addUser(message.from.id.toLong(), message.from.userName, message.from.firstName, message.date)

private fun isAdmin(userId: Long): Boolean = userId == Constants.fatherInd

fun button(text: String = "Назад", callback: Array<Int> = arrayOf(2, 0)): InlineKeyboardButton
        = InlineKeyboardButton(text).setCallbackData(arrayToString(callback))

private fun normalize(value: Int, threshold: Int, newVal: Int): Int {
    if (value != threshold)
        return value
    return newVal
}
