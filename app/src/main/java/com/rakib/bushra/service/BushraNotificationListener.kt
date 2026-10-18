package com.rakib.bushra.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** আসা নোটিফিকেশন মনে রাখে, যাতে বুশরা "কী মেসেজ এল?" প্রশ্নের উত্তর দিতে পারে */
class BushraNotificationListener : NotificationListenerService() {

    companion object {
        private val items = ArrayList<String>()
        private val fmt = SimpleDateFormat("HH:mm", Locale.US)

        @Synchronized
        private fun add(line: String) {
            items.add(0, line)
            while (items.size > 40) items.removeAt(items.size - 1)
        }

        @Synchronized
        fun recent(count: Int): List<String> = items.take(count)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName) return
        val extras = sbn.notification.extras
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return
        val app = try {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: Exception) {
            sbn.packageName
        }
        add("${fmt.format(Date(sbn.postTime))} $app: $title — ${text.take(160)}")
    }
}
