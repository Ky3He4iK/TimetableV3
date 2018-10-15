package com.ky3he4ik

import com.ky3he4ik.Timetable.TT.TimetableDay.TimetableLesson.TimetableClass.TimetableCell

data class Timetable(val daysCount: Int, val lessonsCount: Int, val classCount: Int, val roomsCount: Int, val trap: Int = -1) {
    class TT(daysCount: Int, lessonsCount: Int, classCount: Int) {
        val days = Array(daysCount) { TimetableDay(lessonsCount, classCount) }

        operator fun get(dayInd: Int, lessonNum: Int, classInd: Int, groupInd: Int): TimetableCell =
                days[dayInd].lessons[lessonNum].classes[classInd].groups[groupInd]

        operator fun set(dayInd: Int, lessonNum: Int, classInd: Int, groupInd: Int, value: TimetableCell) {
            days[dayInd].lessons[lessonNum].classes[classInd].groups[groupInd] = value
        }

        operator fun get(dayInd: Int, lessonNum: Int, classInd: Int): TimetableDay.TimetableLesson.TimetableClass =
                days[dayInd].lessons[lessonNum].classes[classInd]

        operator fun get(dayInd: Int, lessonNum: Int): TimetableDay.TimetableLesson =
                days[dayInd].lessons[lessonNum]

        operator fun get(dayInd: Int): TimetableDay =
                days[dayInd]

        class TimetableDay(lessonsCount: Int, classCount: Int) {
            val lessons = Array(lessonsCount) { TimetableLesson(classCount) }

            class TimetableLesson(classCount: Int) {
                val classes = Array(classCount) { TimetableClass() }

                 class TimetableClass {
                     val groups: ArrayList<TimetableCell> = ArrayList()
                     data class TimetableCell(val classInd: Int, val roomInd: Int, var teacherInd: Int,
                                              val subjects: ArrayList<String>, val groupInd: Int)
                }
            }
        }
    }

    class FreeRooms(daysCount: Int, lessonsCount: Int) {
        private val days = Array(daysCount) { FreeRoomsDay(lessonsCount) }

        class FreeRoomsDay(lessonsCount: Int) {
            val lessons = Array(lessonsCount) { FreeRoomsLesson() }

            class FreeRoomsLesson {
                var rooms = ArrayList<Int>()
            }
        }

        fun setAll(timetable: Timetable) {
            for (dayInd in 0 until timetable.daysCount)
                for (lessonNum in 0 until timetable.lessonsCount) {
                    val isBusy = Array(timetable.roomsCount) { false }
                    for (classCells in timetable.timetable.days[dayInd].lessons[lessonNum].classes)
                        for (group in classCells.groups)
                            isBusy[group.roomInd] = true
                    val freeRooms = ArrayList<Int>()
                    isBusy.forEachIndexed { index, b -> if (!b) freeRooms.add(index) }
                    days[dayInd].lessons[lessonNum].rooms = freeRooms
                }
        }

        private fun getFreeRooms(timetable: Timetable, dayInd: Int = 7, lessonInd: Int = -1): String {
            val sb = StringBuilder()
            return when {
                dayInd == 7 -> {
                    for (it in 0 until 6)
                        sb.append(getFreeRooms(timetable, it)).append("\n\n")
                    sb.dropLast(2).toString()
                }
                lessonInd == -1 -> {
                    sb.append(timetable.dayNames[dayInd]).append('\n')
                    for (it in 0 until 7)
                        sb.append(getFreeRooms(timetable, dayInd, it)).append("\n")
                    sb.dropLast(1).toString()
                }
                else -> {
                    sb.append(lessonInd + 1).append(". ")
                    for (it in days[dayInd].lessons[lessonInd].rooms)
                        sb.append(timetable.roomNames[it]).append(", ")
                    sb.dropLast(2).toString()
                }
            }
        }

        private fun getFreeRoomsToday(timetable: Timetable): String =
                getFreeRooms(timetable, Common.currentDay % 6)

        private fun getFreeRoomsTomorrow(timetable: Timetable): String =
                getFreeRooms(timetable, if (Common.currentDay == 6) 0 else (Common.currentDay + 1) % 6)

        private fun getFreeRoomsNear(timetable: Timetable): String {
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

    class Changes(classCount: Int, var dayInd: Int = -1) {
        var hasChanges = Array(classCount) { false }
        val changes = ArrayList<ChangesClass>()
        val changeIndexes = HashMap<Int, Int>()

        data class ChangesClass(val classInd: Int, val changeData: ArrayList<String>) {
            override operator fun equals(other: Any?) : Boolean {
                if (other == null || other !is ChangesClass)
                    return false
                if (classInd == other.classInd && changeData == other.changeData)
                    return true
                return false
            }

            override fun hashCode(): Int {
                return changeData.hashCode() * classInd;
            }
        }

        /**
         * Returns sub-array with different changes from this class
         */
        fun difference(oldChanges: Changes): ArrayList<ChangesClass> {
            if (oldChanges.dayInd != dayInd || oldChanges.dayInd !in 0..5)
                return changes
            val difference =  ArrayList<ChangesClass>()
            for (change in changes) {
                val changeInd = oldChanges.changeIndexes[change.classInd] ?: 0
                if (!oldChanges.hasChanges[change.classInd]
                        || oldChanges.changes[changeInd].changeData != change.changeData)
                    difference.add(change)
            }
            return difference
        }

        private fun getChangesAll(timetable: Timetable, inline: Boolean): String {
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

        private fun getChangesClass(timetable: Timetable, classInd: Int, inline: Boolean): String {
            if (changeIndexes.keys.contains(classInd)) {
                val changeCell = changes[changeIndexes[classInd]!!]
                val answer = StringBuilder()
                for (it in changeCell.changeData)
                    answer.append(it).append('\n')
                if (inline)
                    return timetable.dayNames[dayInd] + ":\n" + answer
                return "Изменения на ${timetable.dayNames[dayInd]} для ${timetable.classNames[classInd]}:\n$answer"
            } else {
                Common.sendMessage(IOParams("$classInd (${timetable.classNames[classInd]}) - класс Шредингера в плане изменений"))
                return "Не все идет по плану. Эта ситуация - яркий пример подобного"
            }
        }

        fun getChanges(timetable: Timetable, classInd: Int = -1, inline: Boolean = false): String {
            return when {
                dayInd == -1 || dayInd == 7 -> "Нет данных об изменениях"
                classInd == -1 -> getChangesAll(timetable, inline)
                hasChanges[classInd] -> getChangesClass(timetable, classInd, inline)
                else -> "Нету изменений для ${timetable.classNames[classInd]}"
            }
        }

        fun getChangesPres(presentation: Int, timetable: Timetable, classInd: Int = -1, inline: Boolean = false): String
                = when (presentation) {
            Presentation.ALL_CLASSES.data -> getChanges(timetable, inline = inline)
            Presentation.CURRENT_CLASS.data -> getChanges(timetable, classInd, inline)
            else -> "Что-то пошло не так"
        }
    }

    var classNames = ArrayList<String>()
    var teacherNames = ArrayList<String>()
    var roomNames = ArrayList<String>()
    var roomInd = ArrayList<String>()
    var dayNames = ArrayList<String>()
    val timetable = TT(daysCount, lessonsCount, classCount)
    val freeRooms = FreeRooms(daysCount, lessonsCount)
    var changes = Changes(classCount)

    fun findDay(dayName: String): Int = find(dayNames, dayName)

    fun findRoom(roomName: String): Int {
        val ind = find(roomNames, roomName)
        return if (ind == -1) find(roomInd, roomName) else ind
    }

    fun findTeacher(teacherName: String): Int = find(teacherNames, teacherName)

    fun findClass(className: String): Int = find(classNames, className)

    fun getTimetable(type: Int, typeInd: Int, dayInd: Int = 7): String {
        var ti = typeInd
        if (ti == -1)
            ti = 0
        if (type == Type.ROOM.data && ti == trap)
            return "ACCESS DENIED"
        val timetable = getTimetableMain(type, ti, dayInd)
        var text = getTimetableTitle(type, ti, dayInd) + '\n' + if (timetable == "") "Нету расписания\n" else timetable
        if (type == Type.CLASS.data && changes.hasChanges[ti])
            text += "\nЕсть изменения\n" + changes.getChanges(this, ti, inline = true)
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

    private fun getTimetableToday(type: Int, typeInd: Int): String = getTimetable(type, typeInd, Common.currentDay % 6)

    private fun getTimetableTomorrow(type: Int, typeInd: Int): String =
            getTimetable(type, typeInd, if (Common.currentDay == 6) 0 else (Common.currentDay + 1) % 6)

    private fun getTimetableNear(type: Int, typeInd: Int): String {
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
            else -> "Что-то (или кто-то) неизвестное"
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
