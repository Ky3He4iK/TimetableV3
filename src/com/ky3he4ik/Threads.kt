package com.ky3he4ik

import java.time.LocalDateTime
import kotlin.concurrent.thread
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

internal object Threads {
    fun startDaemonThread(threadName: String, threadAction: () -> Unit) {
        thread(isDaemon = true, name = threadName) { threadAction(); }
    }

    fun timeThread() {
        while (true) {
            try {
                var hour = LocalDateTime.now().hour + 3
                var day = LocalDateTime.now().dayOfWeek.value
                if (hour > 23) {
                    hour -= 24
                    day = (day + 1) % 7
                }
                if (day > 0)
                    day--
                Common.currentLesson = 7
                for (it in 0 until Constants.lessonTimes.size)
                    if (hour < Constants.lessonTimes[it][0] ||
                            (hour == Constants.lessonTimes[it][0] && LocalDateTime.now().minute < Constants.lessonTimes[it][1]))
                        Common.currentLesson =  it
                Common.currentDay = day
                LOG.d("TimeThr/info", "$day/$hour; ${Common.currentDay}/${Common.currentLesson}")
                Thread.sleep(60 * 1000L)
            } catch (e: Exception) {
                LOG.e("TimeThr/info", e.message, e)
            }
        }
    }

    fun sendThread() {
        while (true) {
            try {
                if (Common.emergencyMessageQueue.isNotEmpty()) {
                    try {
                        bot.sendMessage(Common.emergencyMessageQueue.first)
                    } catch (e: TelegramApiException) {
                        LOG.e("SendThr/failEmergency", "${Common.emergencyMessageQueue.first.text} ${e.message}", e)
                    }
                    Common.emergencyMessageQueue.removeFirst()
                } else if (Common.messageQueue.isNotEmpty()) {
                    try {
                        bot.sendMessage(Common.messageQueue.first)
                    } catch (e: TelegramApiException) {
                        LOG.e("SendThr/fail", "${Common.messageQueue.first.text} ${e.message}", e)
                    }
                    Common.messageQueue.removeFirst()
                }
            } catch (e: Exception) {
                LOG.e("SendThr/full_fail", e.message, e)
            }
            Thread.sleep(1)
        }
    }

    fun updatingThread() {
        var counter = 0
        while (Common.work) {
            try {
                Thread.sleep(30 * 60 * 1000L) // every 30 min
                bot.db.update(false, counter % 8 == 0)
                // Following statistics, only 12.5% of this updates are full

                LOG.i("UpdatingThr", "Updated ${counter % 8 == 0}")
                counter++
            } catch (e: Exception) {
                LOG.e("UpdatingThr/fail", e.message, e)
            }
        }
    }
}