package com.ky3he4ik

import com.ky3he4ik.Timetable.TT.TimetableDay.TimetableLesson.TimetableClass.TimetableCell

data class Timetable(val daysCount: Int, val lessonsCount: Int, val classCount: Int, val roomsCount: Int, val trap: Int = -1) {
    class TT(daysCount: Int, lessonsCount: Int, classCount: Int) {
        val days = Array(daysCount) { TimetableDay(lessonsCount, classCount) }

        class TimetableDay(lessonsCount: Int, classCount: Int) {
            val lessons = Array(lessonsCount) { TimetableLesson(classCount) }

            class TimetableLesson(classCount: Int) {
                val classes = Array(classCount) { TimetableClass() }

                 class TimetableClass(val groups: ArrayList<TimetableCell> = ArrayList()) {
                    data class TimetableCell(val classInd: Int, val roomInd: Int, var teacherInd: Int,
                                             val subjects: ArrayList<String>, val groupInd: Int)
                }
            }
        }
    }

    data class FreeRooms(val daysCount: Int, val lessonsCount: Int, val roomsCount: Int) {
        private val days = Array(daysCount) { FreeRoomsDay(lessonsCount) }

        class FreeRoomsDay(lessonsCount: Int) {
            val lessons = Array(lessonsCount) { FreeRoomsLesson() }

            class FreeRoomsLesson {
                var rooms = ArrayList<Int>()
            }
        }

        fun setAll(timetable: Timetable) {
            for (dayInd in 0 until daysCount)
                for (lessonNum in 0 until lessonsCount) {
                    val isBusy = Array(roomsCount) { false }
                    for (classCells in timetable.timetable.days[dayInd].lessons[lessonNum].classes)
                        for (group in classCells.groups)
                            isBusy[group.roomInd] = true
                    val freeRooms = ArrayList<Int>()
                    isBusy.forEachIndexed { index, b -> if (!b) freeRooms.add(index) }
                    days[dayInd].lessons[lessonNum].rooms = freeRooms
                }
        }

        fun getFreeRooms(timetable: Timetable, dayInd: Int = 7, lessonInd: Int = -1): String {
            if (dayInd == 7) {
                val sb = StringBuilder()
                Array(6) { getFreeRooms(timetable, it) }.forEach { sb.append(it).append("\n\n") }
                return sb.substring(0, sb.length - 2)
            }
            if (lessonInd == -1) {
                val sb = StringBuilder(timetable.dayNames[dayInd]).append('\n')
                Array(7) { getFreeRooms(timetable, dayInd, it) }.forEach { sb.append(it).append("\n") }
                return sb.substring(0, sb.length - 1)
            }
            val sb =  StringBuilder().append(lessonInd + 1).append(". ")
            days[dayInd].lessons[lessonInd].rooms.forEach {
                sb.append(timetable.roomNames[it]).append(", ")
            }
            return sb.substring(0, sb.length - 2)
        }

        fun getFreeRoomsToday(timetable: Timetable): String =
                getFreeRooms(timetable, Common.currentDay % 6)

        fun getFreeRoomsTomorrow(timetable: Timetable): String =
                getFreeRooms(timetable, if (Common.currentDay == 6) 0 else (Common.currentDay + 1) % 6)

        fun getFreeRoomsNear(timetable: Timetable): String {
            val curDay: Int
            val curLes: Int
            when {
                Common.currentDay > 5 -> {
                    curDay = 0
                    curLes = 0
                }
                Common.currentLesson > 6 -> {
                    curDay = (Common.currentDay + 1) % 6
                    curLes = 0
                }
                else -> {
                    curDay = Common.currentDay
                    curLes = Common.currentLesson
                }
            }
            return getFreeRooms(timetable, curDay, curLes)
        }

        fun getFreeRoomsPresentation(presentation: Int, timetable: Timetable, dayInd: Int = 7): String =
                when (presentation) {
                Presentation.ALL_WEEK.data -> getFreeRooms(timetable)
                Presentation.NEAR.data -> getFreeRoomsNear(timetable)
                Presentation.TOMORROW.data -> getFreeRoomsTomorrow(timetable)
                Presentation.TODAY.data -> getFreeRoomsToday(timetable)
                else -> getFreeRooms(timetable, dayInd)
        }
    }

    data class Changes(val classCount: Int, var dayInd: Int = -1) {
        var hasChanges = Array(classCount) { false }
        val changes = ArrayList<ChangesClass>()
        val changeIndexes = HashMap<Int, Int>()

        data class ChangesClass(val classInd: Int, val changeData: ArrayList<String>)

        fun getChanges(timetable: Timetable, classInd: Int = -1, inline: Boolean = false): String {
            if (dayInd == -1 || dayInd == 7)
                return "Нет данных об изменениях"
            if (classInd == -1) {
                val answer = StringBuilder()
                for (it in changes) {
                    answer.append(timetable.classNames[it.classInd]).append(':')
                    for (i in it.changeData)
                        answer.append(i).append('\n')
                }
                var endStr = timetable.dayNames[dayInd] + ":\n" + answer
                if (!inline)
                    endStr = "Изменения на $endStr"
                return endStr
            }
            if (hasChanges[classInd]) {
                if (changeIndexes.keys.contains(classInd)) {
                    val changeCell = changes[changeIndexes[classInd]!!]
                    val answer = StringBuilder()
                    for (it in changeCell.changeData)
                        answer.append(it).append('\n')
                    if (inline)
                        return timetable.dayNames[dayInd] + ":\n" + answer
                    return "Изменения на ${timetable.dayNames[dayInd]} для ${timetable.classNames[classInd]}:\n$answer"
                } else {
                    Common.sendMessage("$classInd (${timetable.classNames[classInd]}) - класс Шредингера в плане изменений")
                    return "Не все идет по плану. Эта ситуация - яркий пример подобного"
                }
            }
            return "Нету изменений для " + timetable.classNames[classInd]
        }

        fun getChangesPres(presentation: Int, timetable: Timetable, classInd: Int = -1, inline: Boolean = false): String
                = when (presentation) {
            Presentation.ALL_CLASSES.data -> getChanges(timetable, inline = inline)
            Presentation.CURRENT_CLASS.data -> getChanges(timetable, classInd, inline)
            else -> "Что-то пошло не так"
        }
    }

    val classNames = ArrayList<String>()
    val teacherNames = ArrayList<String>()
    val roomNames = ArrayList<String>()
    val roomInd = ArrayList<String>()
    val dayNames = ArrayList<String>()
    val timetable = TT(daysCount, lessonsCount, classCount)
    val freeRooms = FreeRooms(daysCount, lessonsCount, roomsCount)
    var changes = Changes(classCount)

    fun findDay(dayName: String): Int = find(dayNames, dayName)

    fun findRoom(roomName: String): Int {
        val ind = find(roomNames, roomName)
        return if (ind == -1) find(roomInd, roomName) else ind
    }

    fun findTeacher(teacherName: String): Int = find(teacherNames, teacherName)

    fun findClass(className: String): Int = find(classNames, className)

    fun getCellByClass(dayInd: Int, lessonNum: Int, classInd: Int, groupInd: Int): TimetableCell =
            timetable.days[dayInd].lessons[lessonNum].classes[classInd].groups[groupInd]

    fun getTimetable(type: Int, typeInd: Int, dayInd: Int = 7): String {
        if (typeInd == -1)
            return "Что-то пошло не так"
        if (type == Type.ROOM.data && typeInd == trap)
            return "ACCESS DENIED"
        val timetable = getTimetableMain(type, typeInd, dayInd)
        var text = getTimetableTitle(type, typeInd, dayInd) + '\n' + if (timetable == "") "Нету расписания\n" else timetable
        if (type == Type.CLASS.data && changes.hasChanges[typeInd])
            text += "\nЕсть изменения\n" + changes.getChanges(this, typeInd, inline = true)
        else if (changes.changes.size != 0)
            text += "Есть изменения."
        return text
    }

    fun getTimetablePres(presentation: Int, type: Int, typeInd: Int, dayInd: Int = 7): String {
        return when (presentation) {
            Presentation.TODAY.data -> getTimetableToday(type, typeInd)
            Presentation.TOMORROW.data -> getTimetableTomorrow(type, typeInd)
            Presentation.NEAR.data -> getTimetableNear(type, typeInd)
            Presentation.ALL_WEEK.data -> getTimetable(type, typeInd)
            else -> getTimetable(type, typeInd, dayInd)
        }
    }

    fun getTimetableToday(type: Int, typeInd: Int): String = getTimetable(type, typeInd, Common.currentDay % 6)

    fun getTimetableTomorrow(type: Int, typeInd: Int): String =
            getTimetable(type, typeInd, if (Common.currentDay == 6) 0 else (Common.currentDay + 1) % 6)

    fun getTimetableNear(type: Int, typeInd: Int): String {
        val curDay: Int
        val curLes: Int
        when {
            Common.currentDay > 5 -> {
                curDay = 0
                curLes = 0
            }
            Common.currentLesson > 6 -> {
                curDay = (Common.currentDay + 1) % 6
                curLes = 0
            }
            else -> {
                curDay = Common.currentDay
                curLes = Common.currentLesson
            }
        }
        val title = when(type) {
            Type.CLASS.data -> classNames[typeInd]
            Type.ROOM.data -> roomNames[typeInd]
            Type.TEACHER.data -> teacherNames[typeInd]
            else -> "Что-то (или что-то) неизвестное"
        } + ". ${dayNames[curDay]}. ${curLes + 1}-й урок\n"
        return title + getTimetableLesson(type, typeInd, curDay, curLes)
    }

    fun has(type: Int, ind: Int): Boolean {
        return ind >= 0 && when(type) {
            Type.CLASS.data -> ind < classCount
            Type.ROOM.data -> ind < roomsCount
            Type.TEACHER.data -> ind < teacherNames.size
            else -> false
        }
    }

    private fun groupToStr(type: Int, timetableCell: TimetableCell): String {
        val classStr = if (type == Type.CLASS.data) "" else "${classNames[timetableCell.classInd]} "
        val groupStr = if (timetableCell.groupInd == 0) "" else "(${timetableCell.groupInd}) "

        val subjects = StringBuilder()
        timetableCell.subjects.forEach { subjects.append(it).append('|') }
        val teacherStr = if (type == Type.TEACHER.data) " - " else " ${teacherNames[timetableCell.teacherInd]} "
        val roomStr = when {
            (timetableCell.roomInd == trap) -> "IN A TRAP!"
            type == Type.ROOM.data -> ""
            else -> "в " + roomInd[timetableCell.roomInd] + ' '
        }
        return classStr + groupStr + subjects.dropLast(1).toString() + teacherStr + roomStr
    }

    private fun getTimetableLesson(type: Int, typeInd: Int, dayInd: Int, lessonInd: Int): String {
        val answer = StringBuilder().append(lessonInd + 1).append(": ")
        val lessons = ArrayList<TimetableCell>()
        if (type == Type.CLASS.data)
            lessons.addAll(timetable.days[dayInd].lessons[lessonInd].classes[typeInd].groups)
        else
            for (classes in timetable.days[dayInd].lessons[lessonInd].classes)
                for (group in classes.groups)
                    if ((type == Type.TEACHER.data && typeInd == group.teacherInd) ||
                            (type == Type.ROOM.data && typeInd == group.roomInd))
                        lessons.add(group)
        if (lessons.size == 0)
            answer.append(0.toChar()).append("--------------------\n")
        else {
            lessons.sortBy { it.groupInd }
            lessons.forEach { answer.append(groupToStr(type, it)).append("\n  ") }
        }
        return answer.dropWhile { it == ' ' }.dropLastWhile { it == ' ' }.toString()
    }

    private fun getTimetableMain(type: Int, typeInd: Int, dayInd: Int): String {
        if (dayInd == 7) {
            val sb = StringBuilder()
            for (it in 0 until 6)
                sb.append(getTimetableMain(type, typeInd, it)).append('\n')
            return sb.toString()
        }
        val ans = StringBuilder(dayNames[dayInd]).append('\n')
        var hasLesson = false
        val timetableArr = ArrayList<String>()
        var lastLesson = -1
        for (lesson in 0 until 7) {
            val timetableStr = getTimetableLesson(type, typeInd, dayInd, lesson)
            if (!timetableStr.contains(0.toChar())) {
                hasLesson = true
                lastLesson = lesson
            }
            timetableArr.add(timetableStr)
        }
        if (hasLesson) {
            timetableArr.subList(0, lastLesson + 1).forEach { ans.append(it) }
            return ans.toString()
        }
        return ""
    }

    private fun getTimetableTitle(type: Int, typeInd: Int, dayInd: Int): String = "Расписание для " + when (type) {
        Type.CLASS.data -> classNames[typeInd]
        Type.TEACHER.data -> teacherNames[typeInd]
        Type.ROOM.data -> if (typeInd == trap) "███" else roomInd[typeInd]
        else -> "чего-то"
    } + " на " + if (dayInd == 7) "всю неделю" else dayNames[dayInd] + "\n\n"

    private fun find(array: ArrayList<String>, value: String): Int {
        for (it in 0 until array.size)
            if (array[it].equals(value, ignoreCase = true))
                return it
        return -1
    }
}
