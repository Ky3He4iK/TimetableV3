package com.ky3he4ik

import java.net.URL


internal object Constants {
    const val ClassesListName = "classes"
    const val TeachersListName = "teachers"
    const val RoomsListName = "rooms"
    const val DaysListName = "days"
    const val bells = "1. 9:00 - 9:40\n2. 9:50 - 10:30\n3. 10:45 - 11:25\n4. 11:40 - 12:20\n5. 12:35 - 13:15\n" +
            "6. 13:35 - 14:15\n7. 14:35 - 15:15"

    val dayNames = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота")

    const val fatherInd = 351693351L
    val lessonTimes = arrayOf(arrayOf(9, 25), arrayOf(10, 20), arrayOf(11, 15), arrayOf(12, 10), arrayOf(13, 10),
            arrayOf(14, 10), arrayOf(15, 10))

    const val helpMes = "Тут будут отображаться все поддерживаемые команды\n" +
            "На данный момент я понимаю:" +
            "/ping - Проверить работоспособность бота\n" +
            "/menu - Если ты ничего не трогал, а оно само опять сломалось" +
            "/help - Это сообщение" //TODO: Сделать это

    val urlTimetable = URL("http://lyceum.urfu.ru/n/inc/tmtblAjax.php")
    val urlLists = URL("http://lyceum.urfu.ru/n/?p=tmtbl")
}
