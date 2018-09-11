package com.ky3he4ik

import com.google.gson.internal.LinkedTreeMap
import org.telegram.telegrambots.api.objects.Message
import kotlin.concurrent.thread

data class User(val id: Long, var username: String, var firstName: String, var internalId: Int, var lastAccess: Int = 0,
                var settings: Settings = Settings()) {
    data class Settings(var type: Int = Type.CLASS.data, var typeInd: Int = 0, var notify: Boolean = true,
                        var currentState: List<Int> = listOf(2, 0, -1, -1, -1, -1, -1, -1),
                        var defaultPresentation: Int = Presentation.ALL_WEEK.data,
                        var defaultPresentationChanges: Int = Presentation.ALL_CLASSES.data,
                        var defaultPresentationRooms: Int = Presentation.ALL_WEEK.data) {
        fun toString(db: Database): String {
            return "Ты: ${when (type) {
                Type.CLASS.data -> "ученик "
                Type.TEACHER.data -> "преподаватель, "
                Type.ROOM.data -> "кабинет №"
                else -> "bug#"
            }}${if (typeInd >= 0) db.get(type, typeInd) else typeInd.toString()}\n${if (notify) "П" else "Не п"}" +
                    "олучаешь увебомления об изменениях\nВывод по умолчанию:\n- Расписание: " +
                    "${presentationToString(defaultPresentation)}\n- Изменения: " +
                    "${presentationToString(defaultPresentationChanges)}\n- Свободные кабинеты: " +
                    "${presentationToString(defaultPresentationRooms)}\nВыбирай, что хочешь изменить"
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
    private val timetableFile = "data/timetable.bv3.json"
    private val feedbackFile = "data/feedback.bv3.json"
    private val usersFile = "data/users.bv3.json"

    private lateinit var users: HashMap<Long, User>
    lateinit var timetable: Timetable
    lateinit var feedbackArray: ArrayList<Feedback>

    init {
        var lT = loadType
        if (lT == LoadType.READ.data && (!IO.exists(timetableFile) || !IO.exists(usersFile) || !IO.exists(feedbackFile)))
            lT = LoadType.CREATE.data
        when (lT) {
            LoadType.READ.data -> {
                if (!load())
                    create()
            }
            LoadType.CREATE.data ->
                create()
            else -> throw RuntimeException("$loadType is not supported")
        }
        thread(isDaemon = true, name = "Updating thread") {
            var counter = 0
            while (Common.work) {
                try {
                    Thread.sleep(30 * 60 * 1000L) // every 30 min
                    update(false, counter % 8 == 0)
                    println("Updated")
                    counter++
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun load(): Boolean {
        timetable = TimetableBuilder.load(timetableFile) ?: TimetableBuilder.createTimetable()
        feedbackArray = IO.readJSONArray(feedbackFile)
        users = HashMap(IO.readJSON<LinkedTreeMap<Long, User>>(usersFile).toMap())
        println("Loaded from local files")
        return true
    }

    private fun create() {
        users = HashMap()
        feedbackArray = ArrayList()
        timetable = TimetableBuilder.createTimetable()
        writeAll()
        println("Created/fetched")
    }

    fun setUserState(userId: Long, newState: List<Int>) {
        users[userId]?.settings?.currentState = newState
    }

    fun hasUser(userId: Long): Boolean = users.containsKey(userId)

    fun addUser(message: Message) = addUser(message.from.id.toLong(), message.from.userName, message.from.firstName, message.date)

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
            throw RuntimeException("$feedbackInd does not match")
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

    fun writeAll() {
        IO.writeJSON(timetableFile, timetable)
        IO.writeJSON(feedbackFile, feedbackArray)
        IO.writeJSON(usersFile, users)
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
        writeAll()
        notifyChanges(oldChanges, timetable.changes)
    }

    fun getUser(userId: Long): User? = users[userId]

    fun sendToAll(text: String) {
        users.forEach { _, u -> Common.sendMessage(text, u.id, inlineKeyboard = null) }
    }

    private fun <E> ArrayList<E>.notEquals(al: ArrayList<E>): Boolean {
        if (al.size != size)
            return false
        forEachIndexed { index, e -> if (e != al[index]) return false }
        return true
    }

    private fun notifyChanges(oldChanges: Timetable.Changes, newChanges: Timetable.Changes) {
        if (newChanges.dayInd == -1 || newChanges.dayInd == 7)
            return
        var diffInd = HashSet<Int>().toMutableSet()
        if (oldChanges.dayInd != newChanges.dayInd || oldChanges.dayInd !in 0..5)
            diffInd = newChanges.changeIndexes.keys
        else {
            newChanges.changes.forEach {
                if (it.classInd == -1 || !oldChanges.hasChanges[it.classInd] || oldChanges.changes[oldChanges.changeIndexes[it.classInd]!!].changeData.notEquals(it.changeData))
                    diffInd.add(it.classInd)
            }
        }
        if (diffInd.isEmpty())
            return
        users.filter { it.value.settings.notify && (it.value.settings.type != Type.CLASS.data ||
                it.value.settings.typeInd in diffInd) }.keys.forEach {
            val set = getUser(it)!!.settings
            val classInd = if (set.type == Type.CLASS.data) set.typeInd else -1
            Common.sendMessage(text = "${timetable.changes.getChangesPres(set.defaultPresentationChanges, timetable, classInd)}\n\n" +
                    "Уведомления об изменениях можно отключить в настройках", silent = true, inlineKeyboard = null, chatId = it)
        }
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

    fun getClassInd(classStr: String): Int = getSomethingInd(classStr, timetable.classNames)

    private fun getTeacherInd(teacher: String): Int = getSomethingInd(teacher, timetable.teacherNames)

    private fun addUser(userId: Long, username: String, firstName: String, lastAccess: Int) {
        users[userId] = User(userId, username, firstName, users.size, lastAccess)
    }
}
