package com.ky3he4ik

import com.google.gson.internal.LinkedTreeMap
import java.io.File

data class User(val id: Long, var username: String, var firstName: String, var internalId: Int, var lastAccess: Int = 0,
                var settings: Settings = Settings()) {

    data class Settings(var type: Int = Type.CLASS.data, var typeInd: Int = 0, var notify: Boolean = true,
                        var currentState: List<Int> = listOf(2, 0, -1, -1, -1, -1, -1, -1),
                        var defaultPresentation: Int = Presentation.ALL_WEEK.data,
                        var defaultPresentationChanges: Int = Presentation.ALL_CLASSES.data,
                        var defaultPresentationRooms: Int = Presentation.ALL_WEEK.data) {

        fun toString(db: Database): String {
            val typeName = when (type) {
                Type.CLASS.data -> "ученик "
                Type.TEACHER.data -> "преподаватель, "
                Type.ROOM.data -> "кабинет №"
                else -> "bug#"
            }
            val typeIndStr = if (typeInd >= 0) db.get(type, typeInd) else typeInd.toString()
            return "Ты: $typeName}$typeIndStr\n${if (notify) "П" else "Не п"}" +
                    "олучаешь увебомления об изменениях\nВывод по умолчанию:\n- Расписание: " +
                    "${presentationToString(defaultPresentation)}\n- Изменения: " +
                    "${presentationToString(defaultPresentationChanges)}\n- Свободные кабинеты: " +
                    "${presentationToString(defaultPresentationRooms)}\nВыбирай, что хочешь изменить:"
        }

        private fun presentationToString(presentation: Int): String {
            return when (presentation) {
                Presentation.CURRENT_CLASS.data -> "для своего класса"
                Presentation.ALL_CLASSES.data -> "для всех"
                Presentation.ALL_WEEK.data -> "на неделю"
                Presentation.NEAR.data -> "ближайший урок"
                Presentation.TOMORROW.data -> "на завтра"
                Presentation.TODAY.data -> "на сегодня"
                else -> "как получится"
            }
        }
    }

    fun reSet(type: Int, typeInd: Int) {
        settings.type = type
        settings.typeInd = typeInd
    }
}

data class Feedback(val userId: Long, val text: String, var internalId: Int,
                    var condition: Int = FeedbackType.UNREAD.data)

class Database(loadType: Int = LoadType.READ.data) {
    private val filesDir = "data"
    private val timetableFile = "timetable.bv3.json"
    private val feedbackFile = "feedback.bv3.json"
    private val usersFile = "users.bv3.json"

    private lateinit var users: HashMap<Long, User>
    lateinit var timetable: Timetable
    lateinit var feedbackArray: ArrayList<Feedback>

    init {
        when (loadType) {
            LoadType.READ.data -> load(true)
            LoadType.CREATE.data -> create()
            else -> throw RuntimeException("$loadType is not supported")
        }
        Threads.startDaemonThread("Updating thread") { Threads.updatingThread() }
    }

    private fun load(createAtFallback: Boolean = false): Boolean {
        try {
            timetable = TimetableBuilder.load(getFilename(filesDir, timetableFile))!!
            feedbackArray = IO.readJSONArray(getFilename(filesDir, feedbackFile))
            users = HashMap(IO.readJSON<LinkedTreeMap<Long, User>>(getFilename(filesDir, usersFile)).toMap())
            LOG.i("Db/loading", "Loaded from local files")
            return true
        } catch (e: Exception) {
            LOG.e("Db/loading", "Failed to load from disk", e)
        }
        try {
            LOG.w("Db/loading/fromBkp", "NOT IMPLEMENTED. Just skipping")
//            TODO: implement
//            return true
        } catch (e: Exception) {
            LOG.e("Db/loading", "Failed to load from disk", e)
        }


        if (createAtFallback)
            create()
        return false
    }

    private fun create() {
        users = HashMap()
        feedbackArray = ArrayList()
        timetable = TimetableBuilder.createTimetable()
        writeBkp()
        LOG.i("Db/creating", "Created&fetched")
    }

    fun setUserState(userId: Long, newState: List<Int>) {
        users[userId]?.settings?.currentState = newState
    }

    fun hasUser(userId: Long): Boolean = users.containsKey(userId)

    fun updateUserSettings(userId: Long, type: Int, typeInd: Int): String {
        if (typeInd == -1 || !hasUser(userId))
            return "Я не могу тебя узнать. Может еще разок?"
        val oldValues = Pair(users[userId]!!.settings.type, users[userId]!!.settings.typeInd)
        users[userId]?.reSet(type,typeInd)
        return when (type) {
            Type.CLASS.data -> if (timetable.classNames[typeInd] == "11е")
                "Добро пожаловать в 11е"
            else
                "Теперь ты учишься в ${get(type, typeInd)}"
            Type.TEACHER.data -> "Теперь Вы - представитель почётной проффессии - педагог ${get(type, typeInd)}"
            Type.ROOM.data ->  "Теперь ты - представитель в телеграмме комнаты №${get(type, typeInd)}"
            //TODO: replace by room names
            else -> {
                users[userId]?.reSet(oldValues.first, oldValues.second)
                "Что-то я таких не знаю"
            }
        }
    }

    fun getInd(string: String, type: Int): Int = when (type) {
        Type.CLASS.data -> getClassInd(string)
        Type.ROOM.data -> getRoomInd(string)
        Type.TEACHER.data -> getTeacherInd(string)
        Type.DAY.data -> getDayInd(string)
        else -> -1
    }

    fun get(type: Int, typeInd: Int): String = when (type) {
        Type.CLASS.data -> timetable.classNames[typeInd]
        Type.ROOM.data -> timetable.roomInd[typeInd]
        Type.TEACHER.data -> timetable.teacherNames[typeInd]
        Type.DAY.data -> timetable.dayNames[typeInd]
        else -> typeInd.toString()
    }

    fun removeFeedback(feedbackInd: Int) {
        if (feedbackInd < 0 || feedbackInd >= feedbackArray.size)
            throw IndexOutOfBoundsException("$feedbackInd does not match")
        feedbackArray.removeAt(feedbackInd)
        for (it in 0 until feedbackArray.size)
            feedbackArray[it].internalId = it
    }

    fun updateUserInfo(userId: Long, username: String, firstName: String, lastAccess: Int) {
        if (users.containsKey(userId)) {
            val user = getUser(userId)!!
            user.username = username
            user.firstName = firstName
            user.lastAccess = lastAccess
            users[userId] = user
        } else
            addUser(userId, username, firstName, lastAccess)
    }

    fun writeBkp() {
        val cTime = Common.getCurrTime() + '_'
        IO.writeJSON(getFilename(filesDir, cTime + timetableFile), timetable)
        IO.writeJSON(getFilename(filesDir, cTime + feedbackFile), feedbackArray)
        IO.writeJSON(getFilename(filesDir, cTime + usersFile), users)
        LOG.v("Db/writeBkp", "Wrote to $filesDir/$cTime")
    }

    fun writeAll() {
        IO.writeJSON(getFilename(filesDir, timetableFile), timetable)
        IO.writeJSON(getFilename(filesDir, feedbackFile), feedbackArray)
        IO.writeJSON(getFilename(filesDir, usersFile), users)
    }

    fun addFeedback(userId: Long, text: String) {
        feedbackArray.add(Feedback(userId, text, feedbackArray.size))
        Common.sendMessage(IOParams("FEEDBACK!", inlineKeyboard = null))
    }

    fun update(fast: Boolean, full: Boolean = true) {
        val oldChanges = timetable.changes
        if (full)
            timetable = TimetableBuilder.createTimetable(fast)
        else
            timetable.changes = TimetableBuilder.getChanges(timetable, fast)
        writeBkp()
        notifyChanges(oldChanges, timetable.changes)
    }

    fun getUser(userId: Long): User? = users[userId]

    fun sendToAll(text: String) {
        users.values.forEach { u -> Common.sendMessage(IOParams(text, u.id, inlineKeyboard = null)) }
    }

    fun addUser(userId: Long, username: String, firstName: String, lastAccess: Int) {
        users[userId] = User(userId, username, firstName, users.size, lastAccess)
    }

    fun listUsers(): String {
        val res = StringBuilder()
        for (entry in users)
            res.append("${entry.key}: @${entry.value.username} ${entry.value.firstName}\n")
        return res.dropLast(1).toString()
    }

    private fun notifyChanges(oldChanges: Timetable.Changes, newChanges: Timetable.Changes) {
        if (newChanges.dayInd !in 0..5)
            return
        val difference = newChanges.difference(oldChanges)
        if (difference.isEmpty())
            return
        val diffInd = HashSet<Int>(difference.size)
        for (it in difference)
            diffInd.add(it.classInd)
        val condition: ((Map.Entry<Long, User>) -> Boolean) = {
            it.value.settings.notify && (it.value.settings.type != Type.CLASS.data || it.value.settings.typeInd in diffInd)
        }
        users.filter(condition).forEach {
            val settings = it.value.settings
            val classInd = if (settings.type == Type.CLASS.data)
                    settings.typeInd
                else
                    -1
            val mes = newChanges.getChangesPres(settings.defaultPresentationChanges, timetable, classInd) +
                    "\n\nУведомления об изменениях можно отключить в настройках"
            Common.sendMessage(IOParams(mes, inlineKeyboard = null, chatId = it.key), true)
        }
    }

    private fun getSomethingInd(string: String, array: ArrayList<String>): Int {
        val ind = string.toIntOrNull()
        if (ind != null && ind in 0 until array.size)
            return ind
        var res = array.indexOf(string)
        if (res != -1)
            return res
        res = string.length
        val stringLower = string.toLowerCase()
        for (it in 0 until array.size)
            if (res <= array[it].length && array[it].substring(0, res).toLowerCase() == stringLower)
                return it
        return -1
    }

    private fun getDayInd(day: String): Int {
        //TODO: add another kinds of input string
        return getSomethingInd(day, timetable.dayNames)
    }

    private fun getRoomInd(room: String): Int {
        val ind = getSomethingInd(room, timetable.roomNames)
        return if (ind == -1) getSomethingInd(room, timetable.roomInd) else ind
    }

    private fun getClassInd(classStr: String): Int = getSomethingInd(classStr, timetable.classNames)

    private fun getTeacherInd(teacher: String): Int = getSomethingInd(teacher, timetable.teacherNames)

    private fun getFilename(folder: String, file: String): String = folder + File.separator + file
}
