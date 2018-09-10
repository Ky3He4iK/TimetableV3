package com.ky3he4ik

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import java.io.StringReader


object TimetableBuilder {
    /* Loading from site */
    private var fast = false
    private var token = ""
    private var lists = HashMap<String, ArrayList<ListObject>>()
    private var defaults = ArrayList<Int>(4)

    private data class ListObject(val index: String, val name: String)

    private fun getToken() {
        var page = IO.get(BotConfig.urlLists, fast)
        page = page.substring(page.indexOf("<script>var tmToken=\"") + "<script>var tmToken=\"".length)
        token = page.substring(0, page.indexOf("\"</script>"))
    }

    private fun getLists(): HashMap<String, ArrayList<ListObject>> {
        val answer = HashMap<String, ArrayList<ListObject>>()

        val page = IO.get(BotConfig.urlLists, fast)
        val array = page.substring(page.indexOf("<div class=\"tmtbl\""), page.indexOf("<script>var tmToken=")).split("tmtbl")
        answer[Constants.ClassesListName] = getListsSub(array[3])
        answer[Constants.TeachersListName] = getListsSub(array[4])

        val tmpStr = array[5].substring(0, array[5].lastIndexOf("</option>") + "</option>".length) +
                "<option value='${'F'}'>${"Каф. ин. яз"}</option><option value='${'T'}'>${"ACCESS DENIED"}</option>"

        answer[Constants.RoomsListName] = getListsSub(tmpStr)
        answer[Constants.DaysListName] = getListsSub(array[6], true)
        return answer
    }

    private fun getListsSub(string: String, sortByInd: Boolean = false): ArrayList<ListObject> {
        val answer = ArrayList<ListObject>()
        val arr = string.split("</option>").dropLast(1)
        for (it in arr) {
            val tmp = it.substring(it.indexOf("value='") + "value='".length)
            val tmpI = tmp.substring(0, tmp.indexOf("'"))
            answer.add(ListObject(tmpI, it.substring(it.lastIndexOf(">") + 1)))
        }
        if (sortByInd)
            answer.sortBy { it.index }
        else
            answer.sortBy { it.name }
        return answer
    }

    private fun setClasses(timetable: Timetable) {
        for (it in 0 until lists[Constants.ClassesListName]!!.size)
            for (day in 0 until 6)
                setClass(timetable, it, lists[Constants.ClassesListName]!![it].index, day)
    }

    private fun setClass(timetable: Timetable, classInd: Int, classYear: String, dayInd: Int) {
        val page = IO.get(BotConfig.urlTimetable, mapOf(
                "tmToc" to token,
                "tmrType" to "0",
                "tmrClass" to classYear,
                "tmrTeach" to "0",
                "tmrRoom" to "0",
                "tmrDay" to (dayInd + 1).toString()
        ), fast)
        if (page == "Err\n")
            print("Something bad was happened with $classInd at $dayInd")
        else
            for (lesson in page.substring(page.indexOf("<tr>") + "<tr>".length).split("<tr>"))
                setClassLesson(timetable, classInd, dayInd, lesson)
    }

    private fun setClassLesson(timetable: Timetable, classIndInt: Int, dayInd: Int, lessonData: String) {
        if (!lessonData.contains("<td>"))
            return
        val lessonNum = lessonData.substring("<td>".length, "<td>".length + 1).toInt() - 1
        if (lessonData.contains("width")) {
            val tmpArr = lessonData.split("<td width=47%>")
            for (it in 1 until tmpArr.size) {
                val lesData = tmpArr[it].substring(0, tmpArr[it].indexOf("</td>"))
                if (!lesData.contains("&times"))
                    timetable.timetable.days[dayInd].lessons[lessonNum].classes[classIndInt].groups.add(
                            setClassLessonGroup(lesData, it, classIndInt))
            }
        } else {
            timetable.timetable.days[dayInd].lessons[lessonNum].classes[classIndInt].groups.add(setClassLessonGroup(
                    lessonData.substring(lessonData.indexOf("<td colspan=2>") + "<td colspan=2>".length,
                            lessonData.indexOf("</td></tr>")), 0, classIndInt))
        }
    }

    private fun setClassLessonGroup(lessonData: String, groupInd: Int, classInd: Int): Timetable.TT.TimetableDay.TimetableLesson.TimetableClass.TimetableCell {
        val subject: String
        var roomInd: Int
        if (lessonData.contains(' ')) {
            val tmpArr = lessonData.split(' ')
            roomInd = findInd(lists[Constants.RoomsListName]!!, tmpArr[1])
            if (roomInd == -1)
                roomInd = defaults[2]
            subject = tmpArr[0]
        } else {
            roomInd = if (lessonData == "Физкультура") defaults[0] else defaults[1]
            subject = lessonData
        }
        return Timetable.TT.TimetableDay.TimetableLesson.TimetableClass.TimetableCell(classInd, roomInd, defaults[3], arrayListOf(subject), groupInd)
    }

    private fun setTeachers(timetable: Timetable) {
        for (it in 0 until lists[Constants.TeachersListName]!!.size)
            setTeacher(timetable, it)
    }

    private fun setTeacher(timetable: Timetable, teacherInd: Int) {
        val page = IO.get(BotConfig.urlTimetable, mapOf(
                "tmToc" to token,
                "tmrType" to "1",
                "tmrClass" to "0",
                "tmrTeach" to teacherInd.toString(),
                "tmrRoom" to "0",
                "tmrDay" to "0"
        ), fast)
        if (page == "Err\n")
            println("Something bad was happened with $teacherInd (${timetable.teacherNames[teacherInd]})")
        else if (page.contains("Уроков не найдено"))
            return
        for (dayInfo in page.substring(page.indexOf("<h3>") + "<h3>".length, page.indexOf("<details><summary>")).split("<h3>"))
            setTeacherDay(timetable, teacherInd, dayInfo)
    }

    private fun setTeacherDay(timetable: Timetable, teacherInd: Int, dayInfo: String) {
        val tmpArr = dayInfo.split("<tr>")
        val dayInd = timetable.findDay(tmpArr[0].substring(0, tmpArr[0].indexOf("</h3>")))
        for (lessonData in tmpArr.subList(1, tmpArr.size))
            setTeacherDaySub(timetable, lessonData, teacherInd,  dayInd)
    }

    private fun setTeacherDaySub(timetable: Timetable, lessonData: String, teacherInd: Int, dayInd: Int) {
        val grInfo = lessonData[lessonData.lastIndexOf("</td>") - 1]
        if (grInfo == ';')
            return
        val groupNum =  if (grInfo.isDigit()) grInfo.toInt() - '0'.toInt() else 0
        val lesData = lessonData.substring(0, lessonData.lastIndexOf("</td>") - "</td>".length)
        if (!lesData.contains("<td>"))
            return
        val lessonNum = lessonData.substring("<td>".length, "<td>".length + 1).toInt() - 1
        val tmpArr = lessonData.substring(lessonData.indexOf("</td>") + "</td><td>".length)
                .split("</td><td>")
        val subject = tmpArr[0]
        val classInd = timetable.findClass(tmpArr[1])

        if (dayInd < 0 || lessonNum < 0 || dayInd >= timetable.timetable.days.size ||
                lessonNum >= timetable.timetable.days[dayInd].lessons.size ||
                classInd >= timetable.timetable.days[dayInd].lessons[lessonNum].classes.size)
            println("Houston, we’ve had a problem ($dayInd $lessonNum $classInd)")

        val groupInd = if (groupNum == 2
                && timetable.timetable.days[dayInd].lessons[lessonNum].classes[classInd].groups.size != 1) 1 else 0
        val timetableCell = timetable.getCellByClass(dayInd, lessonNum, classInd, groupInd)
        timetableCell.teacherInd = teacherInd
        if (!timetableCell.subjects.contains(subject))
            timetableCell.subjects.add(subject)

        timetable.timetable.days[dayInd].lessons[lessonNum].classes[classInd].groups[groupInd] = timetableCell
    }

    private fun getRawChanges(fast: Boolean): String {
        val page = IO.get(BotConfig.urlTimetable, mapOf(
                "tmToc" to token,
                "tmrType" to "1",
                "tmrClass" to "0",
                "tmrTeach" to "0",
                "tmrRoom" to "0",
                "tmrDay" to "0"
        ), fast)
        if (page == "Err\n") {
            println("Can't get raw changes")
            return ""
        }
        return page.substring(page.indexOf("</summary>") + "</summary>".length)
    }

    private fun getDayByChanges(day: String): Int {
        if (day.isEmpty())
            return -1
        val dayStr = day[0].isUpperCase().toString() + day.substring(1, day.length - 1).toLowerCase()
        for (it in 0 until Constants.dayNames.size)
            if (dayStr == Constants.dayNames[it].substring(0, Constants.dayNames[it].length))
                return it
        return 7
    }

    private fun setChangesSub(changes: Timetable.Changes, lesCh: String, classInd: Int) {
        if (classInd != 1) {
            changes.hasChanges[classInd] = true
            val tmpArr =  ArrayList<String>()
            for (it in lesCh.split("<p>"))
                if (it.length >= 2)
                    tmpArr.add(it.substring(0, it.indexOf("</p>")))
            changes.changeIndexes[classInd] = changes.changes.size
            changes.changes.add(Timetable.Changes.ChangesClass(classInd, tmpArr))
        }
    }

    private fun findInd(list: ArrayList<ListObject>, ind: String): Int { //TODO: Replace by binSearch
        for (it in 0 until list.size)
            if (list[it].index == ind)
                return it
        return -1
    }

    private fun findName(list: ArrayList<ListObject>, name: String): Int { //TODO: Replace by binSearch
        for (it in 0 until list.size)
            if (list[it].name == name)
                return it
        return -1
    }

    private fun copyList(from: ArrayList<ListObject>, to: ArrayList<String>) {
        to.clear()
        for (obj in from)
            to.add(obj.name)
    }

    private fun sortGroups(timetable: Timetable) {
        timetable.timetable.days.forEach { d ->
            d.lessons.forEach { l ->
                l.classes.forEach { c ->
                    c.groups.sortBy {
                        it.groupInd
                    }
                }
            }
        }
    }

    /* Loading from file */
    fun load(filename: String): Timetable? {
        try {
            val jsonObject = Klaxon().parseJsonObject(StringReader(IO.read(filename)))
            val timetable = Timetable(jsonObject.int("daysCount")!!, jsonObject.int("lessonsCount")!!,
                    jsonObject.int("classCount")!!, jsonObject.int("roomsCount")!!, jsonObject.int("trap")!!)
            timetable.classNames = ArrayList(jsonObject.array<String>("classNames")!!)
            timetable.teacherNames = ArrayList(jsonObject.array<String>("teacherNames")!!)
            timetable.roomNames = ArrayList(jsonObject.array<String>("roomNames")!!)
            timetable.roomInd = ArrayList(jsonObject.array<String>("roomInd")!!)
            timetable.dayNames = ArrayList(jsonObject.array<String>("dayNames")!!)

            val ttObj = jsonObject.obj("timetable")!!
            ttObj.array<JsonObject>("days")!!.forEachIndexed { dayInd, lessons ->
                lessons.array<JsonObject>("lessons")!!.forEachIndexed { lessonInd, classes ->
                    classes.array<JsonObject>("classes")!!.forEachIndexed { classInd, groups ->
                        groups.array<JsonObject>("groups")!!.forEach {
                            val group = Timetable.TT.TimetableDay.TimetableLesson.TimetableClass.TimetableCell(
                                    classInd = it.int("classInd")!!, roomInd = it.int("roomInd")!!,
                                    teacherInd = it.int("teacherInd")!!, groupInd = it.int("groupInd")!!,
                                    subjects = ArrayList(it.array<String>("subjects")!!))
                            timetable.timetable.days[dayInd].lessons[lessonInd].classes[classInd].groups.add(group)
                        }
                        timetable.timetable.days[dayInd].lessons[lessonInd].classes[classInd].groups.sortBy { it.groupInd }
                    }
                }
            }

            val changesObj = jsonObject.obj("changes")!!
            val changes = Timetable.Changes(timetable.classCount, changesObj.int("dayInd")!!)
            changes.hasChanges = changesObj.array<Boolean>("hasChanges")!!.toTypedArray()

            changesObj.array<JsonObject>("changes")!!.forEach {
                changes.changes.add(Timetable.Changes.ChangesClass(classInd = it.int("classInd")!!,
                        changeData = ArrayList(it.array<String>("changeData")!!)))
            }
            changes.changes.forEachIndexed { index, changesClass -> changes.changeIndexes[changesClass.classInd] = index }
            timetable.changes = changes
            timetable.freeRooms.setAll(timetable)
            return timetable
        } catch (e: NullPointerException) {
            println("Wrong JSON format")
            e.printStackTrace()
            return null
        }
    }

    fun createTimetable(fast: Boolean = BotConfig.isDebug): Timetable {
        println("Creating timetable $fast")
        this.fast = fast
        getToken()
        lists = getLists()
        defaults = arrayListOf(findInd(lists[Constants.RoomsListName]!!, "S"),
                findInd(lists[Constants.RoomsListName]!!, "F"),
                findInd(lists[Constants.RoomsListName]!!, "T"),
                findName(lists[Constants.TeachersListName]!!, "Сотрудник И. С."))

        val timetable = Timetable(6, 7, classCount = lists[Constants.ClassesListName]!!.size,
                roomsCount = lists[Constants.RoomsListName]!!.size, trap = defaults[2])
        copyList(lists[Constants.ClassesListName]!!, timetable.classNames)
        copyList(lists[Constants.TeachersListName]!!, timetable.teacherNames)
        copyList(lists[Constants.DaysListName]!!, timetable.dayNames)
        copyList(lists[Constants.RoomsListName]!!, timetable.roomNames)
        for (obj in lists[Constants.RoomsListName]!!)
            timetable.roomInd.add(obj.name)

        setClasses(timetable)
        sortGroups(timetable)
        setTeachers(timetable)
        timetable.freeRooms.setAll(timetable)
        timetable.changes = getChanges(timetable, fast)

        return timetable
    }

    fun getChanges(timetable: Timetable, fast: Boolean = BotConfig.isDebug): Timetable.Changes {
        var rawChanges = getRawChanges(fast)
        val changes = Timetable.Changes(timetable.classCount)
        changes.dayInd = getDayByChanges(rawChanges
                .substring(rawChanges.indexOf("НА ") + "НА ".length))
        rawChanges = rawChanges.substring(rawChanges.indexOf("</h3>") + "<h3>".length)
                .replace("&nbsp;&mdash;", "-")
        for (lesCh in rawChanges.split("<h6>")) {
            if (lesCh.isEmpty())
                continue
            if (lesCh.contains("</h6>"))
                setChangesSub(changes, lesCh.substring(lesCh.indexOf("<p>")),
                        timetable.findClass(lesCh.substring(0, lesCh.indexOf("<h6>"))))
        }
        return changes
    }
}
