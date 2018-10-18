package com.ky3he4ik

import com.ky3he4ik.Timetable.TT.TimetableDay.TimetableLesson.TimetableClass.TimetableCell
import org.json.JSONObject

object TimetableBuilder {
    /* Loading from site */
    private const val ClassesLisInd = 0
    private const val TeachersListInd = 1
    private const val RoomsListInd = 2
    private const val DaysListInd = 3
    private const val quote = '"'

    private var fast = false
    private var token = ""
    private var lists = HashMap<Int, ArrayList<ListObject>>()
    private var defaults = ArrayList<Int>(4)

    private data class ListObject(val index: String, val name: String)

    private fun getToken() {
        var page = IO.get(Constants.urlLists, fast)
        page = page.substring(page.indexOf("<script>var tmToken=\"") + "<script>var tmToken=\"".length)
        token = page.substring(0, page.indexOf("\"</script>"))
    }

    private fun getLists(): HashMap<Int, ArrayList<ListObject>> {
        val answer = HashMap<Int, ArrayList<ListObject>>()

        val page = IO.get(Constants.urlLists, fast)
        val array = page.substring(page.indexOf("<div class=\"tmtbl\""), 
                page.indexOf("<script>var tmToken=")).split("tmtbl")
        answer[ClassesLisInd] = getListsSub(array[3])
        answer[TeachersListInd] = getListsSub(array[4])

        val tmpStr = array[5].substring(0, array[5].lastIndexOf("</option>") + "</option>".length) +
                "<option value=${quote}F$quote>Каф. ин. яз</option><option value=${quote}T$quote>ACCESS DENIED</option>"

        answer[RoomsListInd] = getListsSub(tmpStr)
        answer[DaysListInd] = getListsSub(array[6], true)
        return answer
    }

    private fun getListsSub(string: String, sortByInd: Boolean = false): ArrayList<ListObject> {
        val answer = ArrayList<ListObject>()
        val arr = string.split("</option>").dropLast(1)
        for (it in arr) {
            val tmp = it.substring(it.indexOf("value=$quote") + "value=$quote".length)
            val tmpI = tmp.substring(0, tmp.indexOf(quote))
            answer.add(ListObject(tmpI, it.substring(it.lastIndexOf(">") + 1)))
        }
        if (sortByInd)
            answer.sortBy(ListObject::index)
        else
            answer.sortBy(ListObject::name)
        return answer
    }

    private fun setClasses(timetable: Timetable) {
        lists[ClassesLisInd]?.forEachIndexed {index, it ->
            for (day in 0 until 6)
                setClass(timetable, index, it.index, day)
        }
    }

    private fun setClass(timetable: Timetable, classInd: Int, classYear: String, dayInd: Int) {
        val page = IO.get(Constants.urlTimetable, mapOf(
                "tmToc" to token,
                "tmrType" to "0",
                "tmrClass" to classYear,
                "tmrTeach" to "0",
                "tmrRoom" to "0",
                "tmrDay" to dayInd + 1
        ), fast)
        if (page == "Err\n")
            LOG.w("TTBuilder/setClass", "Something bad was happened with $classInd at $dayInd")
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
                    timetable.timetable[dayInd, lessonNum, classIndInt].groups.add(setClassLessonGroup(lesData, it, classIndInt))
            }
        } else {
            timetable.timetable[dayInd, lessonNum, classIndInt].groups.add(setClassLessonGroup(
                    lessonData.substring(lessonData.indexOf("<td colspan=2>") + "<td colspan=2>".length,
                            lessonData.indexOf("</td></tr>")), 0, classIndInt))
        }
    }

    private fun setClassLessonGroup(lessonData: String, groupInd: Int, classInd: Int): TimetableCell {
        val subject: String
        var roomInd: Int
        if (lessonData.contains(' ')) {
            val tmpArr = lessonData.split(' ')
            roomInd = findInd(lists[RoomsListInd]!!, tmpArr[1])
            if (roomInd == -1)
                roomInd = defaults[2]
            subject = tmpArr[0]
        } else {
            roomInd = if (lessonData == "Физкультура") defaults[0] else defaults[1]
            subject = lessonData
        }
        return TimetableCell(classInd, roomInd, defaults[3], arrayListOf(subject), groupInd)
    }

    private fun setTeachers(timetable: Timetable) {
        for (it in 0 until lists[TeachersListInd]!!.size)
            setTeacher(timetable, it)
    }

    private fun setTeacher(timetable: Timetable, teacherInd: Int) {
        val page = IO.get(Constants.urlTimetable, mapOf(
                "tmToc" to token,
                "tmrType" to "1",
                "tmrClass" to "0",
                "tmrTeach" to teacherInd,
                "tmrRoom" to "0",
                "tmrDay" to "0"
        ), fast)
        if (page == "Err\n")
            LOG.w("TTBuilder/setTeacher", "Something bad was happened with $teacherInd (${timetable.teacherNames[teacherInd]})")
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
                lessonNum >= timetable.timetable[dayInd].lessons.size ||
                classInd >= timetable.timetable[dayInd].lessons[lessonNum].classes.size)
            LOG.e("TTBuilder/setTDS", "Houston, we’ve had a problem ($dayInd $lessonNum $classInd)")

        val groupInd = if (groupNum == 2
                && timetable.timetable[dayInd, lessonNum, classInd].groups.size != 1) 1 else 0
        val timetableCell = timetable.timetable[dayInd, lessonNum, classInd, groupInd]
        timetableCell.teacherInd = teacherInd
        if (!timetableCell.subjects.contains(subject))
            timetableCell.subjects.add(subject)

        timetable.timetable[dayInd, lessonNum, classInd, groupInd] = timetableCell
    }

    private fun getRawChanges(fast: Boolean): String {
        val page = IO.get(Constants.urlTimetable, mapOf(
                "tmToc" to token,
                "tmrType" to "1",
                "tmrClass" to "0",
                "tmrTeach" to "0",
                "tmrRoom" to "0",
                "tmrDay" to "0"
        ), fast)
        if (page == "Err\n") {
            LOG.w("TTBuilder/getRChanges", "Can't get raw changes")
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
        timetable.timetable.days.forEach { d -> d.lessons.forEach { l -> l.classes.forEach { c -> c.groups.sortBy { it.groupInd } } } }
    }

    /* Loading from file */
    fun load(filename: String): Timetable? {
        try {
            val jsonObject = JSONObject(IO.read(filename))
            val timetable = Timetable(jsonObject.getInt("daysCount"), jsonObject.getInt("lessonsCount"),
                    jsonObject.getInt("classCount"), jsonObject.getInt("roomsCount"), jsonObject.getInt("trap"))
            jsonObject.getJSONArray("classNames").forEach { timetable.classNames.add(it.toString()) }
            jsonObject.getJSONArray("teacherNames").forEach { timetable.teacherNames.add(it.toString()) }
            jsonObject.getJSONArray("roomNames").forEach { timetable.roomNames.add(it.toString()) }
            jsonObject.getJSONArray("roomInd").forEach { timetable.roomInd.add(it.toString()) }
            jsonObject.getJSONArray("dayNames").forEach { timetable.dayNames.add(it.toString()) }

            val ttObj = jsonObject.getJSONObject("timetable")
            val days = ttObj.getJSONArray("days")
            for (dayInd in 0 until days.length()) {
                val lessons = days.getJSONObject(dayInd).getJSONArray("lessons")
                for (lessonInd in 0 until lessons.length()) {
                    val classes = lessons.getJSONObject(lessonInd).getJSONArray("classes")
                    for (classInd in 0 until classes.length()) {
                        val groups = classes.getJSONObject(classInd).getJSONArray("groups")
                        for (it in 0 until groups.length()) {
                            val jo = groups.getJSONObject(it)
                            val subjects = ArrayList<String>()
                            jo.getJSONArray("subjects").forEach { subjects.add(it.toString()) }
                            timetable.timetable[dayInd, lessonInd, classInd].groups.add(
                                    Timetable.TT.TimetableDay.TimetableLesson.TimetableClass.TimetableCell(
                                            classInd = jo.getInt ("classInd"), roomInd = jo.getInt("roomInd"),
                                            teacherInd = jo.getInt("teacherInd"), groupInd = jo.getInt("groupInd"),
                                            subjects = subjects))
                        }

                    }
                }
            }

            val changesObj = jsonObject.getJSONObject("changes")
            val changes = Timetable.Changes(timetable.classCount, changesObj.getInt("dayInd"))
            val hasChanges = changesObj.getJSONArray("hasChanges")
            changes.hasChanges = Array(timetable.classCount) { hasChanges.getBoolean(it) }
            val changesArr = changesObj.getJSONArray("changes")
            for (it in 0 until changesArr.length()) {
                val cc = changesArr.getJSONObject(it)
                val changeData = ArrayList<String>()
                cc.getJSONArray("changeData").forEach { changeData.add(it.toString()) }
                changes.changes.add(Timetable.Changes.ChangesClass(classInd = cc.getInt("classInd"),
                        changeData = changeData))
            }
            changes.changes.forEachIndexed { index, changesClass -> changes.changeIndexes[changesClass.classInd] = index }
            timetable.changes = changes
            timetable.freeRooms.setAll(timetable)
            return timetable
        } catch (e: NullPointerException) {
            LOG.e("TTBuilder/load", "Wrong JSON format", e)
            return null
        }
    }

    fun createTimetable(fast: Boolean = BotConfig.isDebug): Timetable {
        LOG.i("TTBuilder/createTT", "Creating timetable. Smaller IO delay: $fast")
        this.fast = fast
        getToken()
        lists = getLists()
        defaults = arrayListOf(findInd(lists[RoomsListInd]!!, "S"),
                findInd(lists[RoomsListInd]!!, "F"),
                findInd(lists[RoomsListInd]!!, "T"),
                findName(lists[TeachersListInd]!!, "Сотрудник И. С."))

        val timetable = Timetable(6, 7, classCount = lists[ClassesLisInd]!!.size,
                roomsCount = lists[RoomsListInd]!!.size, trap = defaults[2])
        copyList(lists[ClassesLisInd]!!, timetable.classNames)
        copyList(lists[TeachersListInd]!!, timetable.teacherNames)
        copyList(lists[DaysListInd]!!, timetable.dayNames)
        copyList(lists[RoomsListInd]!!, timetable.roomNames)
        for (obj in lists[RoomsListInd]!!)
            timetable.roomInd.add(obj.name)

        setClasses(timetable)
        sortGroups(timetable)
        setTeachers(timetable)
        timetable.freeRooms.setAll(timetable)
        timetable.changes = getChanges(timetable, fast)

        return timetable
    }

    fun getChanges(timetable: Timetable, fast: Boolean = BotConfig.isDebug): Timetable.Changes {
        getToken()
        var rawChanges = getRawChanges(fast)
        val changes = Timetable.Changes(timetable.classCount)
        if (rawChanges.isEmpty() || rawChanges == "<h3>ИЗМЕНЕНИЯ В РАСПИСАНИИ НА</h3></details>")
            return changes
        changes.dayInd = getDayByChanges(rawChanges
                .substring(rawChanges.indexOf("НА ") + "НА ".length))
        if (changes.dayInd == -1) {
            LOG.w("TTBuilder/getChanges", "Changes' day ind is -1!")
            return changes
        }
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
