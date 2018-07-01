package com.ky3he4ik

import org.telegram.telegrambots.api.objects.Message

data class User(val id: Long, var username: String, var firstname: String, var internalId: Int, var lastAccess: Int = 0,
                val settings: Settings = Settings()) {
    data class Settings(var type: Int = Type.CLASS.data, var typeInd: Int = 10, var notify: Boolean = true,
                        var currentState: ArrayList<Int> = ArrayList(),
                        var defaultPresentation: Int = Presentation.ALL_WEEK.data,
                        var defaultPresentationChanges: Int = Presentation.ALL_CLASSES.data,
                        var defaultPresentationRooms: Int = Presentation.ALL_WEEK.data)

    fun reSet(type: Int, typeInd: Int) {
        settings.type = type
        settings.typeInd = typeInd
    }
}

data class Feedback(val userId: Long, val text: String, var internalId: Int,
                    var condition: Int = FeedbackType.UNREAD.data)

class Database(loadType: Int = LoadType.READ.data) {
    private val users: HashMap<Long, User>

    var timetable: Timetable
    val feedbackArray: ArrayList<Feedback>

    init {
        when (loadType) {
            LoadType.READ.data -> {
                timetable = IO.readJSON("data/timetable.bv3.json")
                feedbackArray = IO.readJSON("data/feedback.bv3.json")
                users = IO.readJSON("data/users.bv3.json")
                println("Loaded from local files")
            }
            LoadType.CREATE.data -> {
                users = HashMap()
                feedbackArray = ArrayList()
                timetable = TimetableBuilder.createTimetable()
            }
            else -> throw RuntimeException("$loadType is not supported")
        }
    }

    fun setUserState(userId: Long, newState: ArrayList<Int>) {
        if (hasUser(userId))
            users[userId]!!.settings.currentState = newState
    }
    fun hasUser(userId: Long): Boolean = users.containsKey(userId)
    fun addUser(message: Message)
            = addUser(message.from.id.toLong(), message.from.userName, message.from.firstName, message.date)
    fun updateUserSettings(userId: Long, type: Int, typeInd: Int): String {
        if (typeInd == -1)
            return "Я не могу тебя узнать. Может еще разок?"
        val oldValues = Pair(users[userId]!!.settings.type, users[userId]!!.settings.typeInd)
        users[userId]!!.reSet(type,typeInd)
        return when (type) {
            Type.CLASS.data -> if (timetable.classNames[typeInd] == "11е") "Добро пожаловать в 11е"
                else "Теперь ты учишься в ${timetable.classNames[typeInd]}"
            Type.TEACHER.data ->
                "Теперь Вы - представитель почётной проффессии - педагог ${timetable.teacherNames[typeInd]}"
            Type.ROOM.data ->  "Теперь ты - представитель в телеграмме комнаты №${timetable.roomInd[typeInd]}"
            //TODO: replace by room names

            else -> {
                users[userId]!!.reSet(oldValues.first, oldValues.second)
                "Что-то я таких не знаю"
            }
        }
    }
    fun update(fast: Boolean = false) {
        timetable = TimetableBuilder.createTimetable(fast)
        writeAll()
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
        else -> ""
    }
    fun removeFeedback(feedbackInd: Int) {
        if (feedbackInd < 0 || feedbackInd >= feedbackArray.size)
            throw RuntimeException("$feedbackInd does not match")
        feedbackArray.removeAt(feedbackInd)
        for (it in 0 until feedbackArray.size)
            feedbackArray[it].internalId = it
    }
    fun updateUserInfo(userId: Long, username: String, firstName: String, lastAccess: Int) {
        if (users.containsKey(userId)) {
            val user = getUser(userId)!!
            user.username = username
            user.firstname = firstName
            user.lastAccess = lastAccess
            users[userId] = user
        } else
            addUser(userId, username, firstName, lastAccess)


    }
    fun writeAll() {
        IO.writeJSON("data/timetable.bv3.json", timetable)
        IO.writeJSON("data/feedbackArray.bv3.json", feedbackArray)
        IO.writeJSON("data/users.bv3.json", users)
    }
    fun addFeedback(userId: Long, text: String) {
        feedbackArray.add(Feedback(userId, text, feedbackArray.size))
        Common.sendMessage("FEEDBACK!", chatId = Constants.fatherInd, inlineKeyboard = null)
    }
    fun update(fast: Boolean, full: Boolean = true) {
        val oldChanges = timetable.changes
        if (full)
            timetable = TimetableBuilder.createTimetable(fast)
        else
            timetable.changes = TimetableBuilder.getChanges(timetable, fast)
        notifyChanges(oldChanges, timetable.changes)
    }
    fun getUser(userId: Long): User? = users[userId]
    fun sendToAll(text: String) {
        users.forEach { _, u -> Common.sendMessage(text, u.id, inlineKeyboard = null) }
    }

    private fun notifyChanges(oldChanges: Timetable.Changes, newChanges: Timetable.Changes): Boolean {
        TODO()
    }
    private fun findInArray(string: String, array: ArrayList<String>): Int {
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
    private fun getSomethingInd(string: String, array: ArrayList<String>): Int {
        val ind = string.toIntOrNull()
        return if (ind == null || ind < 0 || ind >= array.size) findInArray(string, array)
        else ind
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
    private fun addUser(userId: Long, username: String, firstname: String, lastAccess: Int) {
        users[userId] = User(userId, username, firstname, users.size, lastAccess)
    }
}
