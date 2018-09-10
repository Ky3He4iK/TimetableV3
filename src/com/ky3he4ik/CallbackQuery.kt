package com.ky3he4ik

import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton

fun callbackQuery(userId: Long, data: List<Int>, chatId: Long, messageId: Int? = null, messageTest: String? = null) {
    var text: String
    var keyboard: InlineKeyboardMarkup? = Common.defaultKeyboard
    var markdown = false
    when (data[0]) {
        1 -> {
            val txt = StringBuilder("Выбери ")
            when (data[1]) {
                Type.CLASS.data -> {
                    txt.append("класс:\n")
                    for (it in 0 until Main.db.timetable.classCount)
                        txt.append("/c_").append(it + 1).append(" : ").append(Main.db.timetable.classNames[it])
                                .append('\n')
                }
                Type.TEACHER.data -> {
                    txt.append("учителя:\n")
                    for (it in 0 until Main.db.timetable.teacherNames.size)
                        txt.append("/t_").append(it + 1).append(" : ").append(Main.db.timetable.teacherNames[it])
                                .append('\n')
                }
                Type.ROOM.data -> {
                    txt.append("кабинет:\n")
                    for (it in 0 until Main.db.timetable.roomsCount)
                        txt.append("/r_").append(it + 1).append(" : ").append(Main.db.timetable.roomNames[it])
                                .append('\n')
                }
                else -> txt.append("Такого у меня нет")
            }
            Main.db.setUserState(userId, arrayListOf(data[2], data[3], -1, -1, -1, -1, -1, -1))
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
            val userSettings = Main.db.getUser(userId)?.settings
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
            text = Main.db.timetable.getTimetablePres(presentation, type, typeInd, dayInd)
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
            val userSettings = Main.db.getUser(userId)?.settings
            var presentation = data[1]
            if (presentation == 0)
                presentation = userSettings?.defaultPresentationChanges ?: Presentation.ALL_CLASSES.data
            text = if (presentation == Presentation.OTHER.data && data[5] != -1)
                Main.db.timetable.changes.getChanges(Main.db.timetable, data[5])
            else
                Main.db.timetable.changes.getChangesPres(presentation, Main.db.timetable, userSettings?.typeInd ?: 0)
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
            val userSettings = Main.db.getUser(userId)?.settings
            var presentation = data[1]
            if (presentation == 0)
                presentation = userSettings?.defaultPresentationRooms ?: Presentation.ALL_WEEK.data
            var dayInd = data[6]
            if (dayInd == -1)
                dayInd = 7
            text = Main.db.timetable.freeRooms.getFreeRoomsPresentation(presentation, Main.db.timetable, dayInd)
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
            val userSettings = Main.db.getUser(userId)?.settings
            if (userSettings == null)
                text = "Хмм... Я тебя не могу узнать. Нажми /start, дабы я смог вспомнить тебя"
            else {
                if (data[4] != -1 && data[5] != -1)
                    userSettings.type = data[4]
                userSettings.typeInd = data[5]
                text = userSettings.toString(Main.db)
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
                Main.db.getUser(userId)?.settings = userSettings
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
            Main.db.timetable.classNames.forEachIndexed { index, s -> sb.append("/c_").append(index + 1).append(" : ").append(s).append('\n') }
            text = sb.dropLast(1).toString()
            Main.db.setUserState(userId, ArrayList(data.subList(2, 4) + listOf(-1, -1, -1, -1, -1, -1)))
        }
        else -> text = "Ты действительно думаешь, что это была валидная кнопка?"
    }
    if (messageId == null)
        Common.sendMessage(text, chatId, inlineKeyboard = keyboard, markdown = markdown)
    else if (text != messageTest)
        Common.editMessage(text, chatId, inlineKeyboard = keyboard, messageId = messageId, markdown = markdown)
}

private fun arrayToString(array: Array<Int>): String {
    val nArr = Array(8) { if (it < array.size) array[it] else -1 }
    val sb = StringBuilder()
    nArr.forEach { sb.append(it).append('.') }
    return sb.dropLast(1).toString()
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
