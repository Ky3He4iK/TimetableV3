package com.ky3he4ik

import java.lang.StringBuilder
import java.net.URL


internal object Constants {
    private val bellsTimes = listOf(
            Pair(9, 0) to Pair(9, 40),
            Pair(9, 50) to Pair(10, 30),
            Pair(10, 45) to Pair(11, 25),
            Pair(11, 40) to Pair(12, 20),
            Pair(12, 35) to Pair(13, 15),
            Pair(13, 35) to Pair(14, 15),
            Pair(14, 35) to Pair(15, 15)
    )

    var bells = "1. 9:00 - 9:40\n2. 9:50 - 10:30\n3. 10:45 - 11:25\n4. 11:40 - 12:20\n5. 12:35 - 13:15\n" +
            "6. 13:35 - 14:15\n7. 14:35 - 15:15"

    val dayNames = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота")

    const val fatherInd = 351693351L
    val lessonTimes = arrayOf(arrayOf(9, 25), arrayOf(10, 20), arrayOf(11, 15), arrayOf(12, 10), arrayOf(13, 10),
            arrayOf(14, 10), arrayOf(15, 10))

    const val helpMes = "Тут будут отображаться все поддерживаемые команды\n" +
            "На данный момент я понимаю:" +
            "/ping - Проверить работоспособность бота\n" +
            "/menu - Если ты ничего не трогал, а оно само опять сломалось" +
            "/help - Это сообщение" //TODO: Write a normal message there

    val urlTimetable = URL("http://lyceum.urfu.ru/n/inc/tmtblAjax.php")
    val urlLists = URL("http://lyceum.urfu.ru/n/?p=tmtbl")

    fun generateBells() {
        val sb = StringBuilder()
        bellsTimes.forEachIndexed { index, pair ->
            sb.append("${index + 1}. ${pair.first.first}:${pair.first.second} - ${pair.second.first}:${pair.second.second}\n")
        }
        bells = sb.toString()
    }
}
