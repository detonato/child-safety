package com.example.childsafety

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Telephony
import android.telephony.SmsMessage
import androidx.core.app.NotificationCompat

class SmsReceiver : BroadcastReceiver() {

    companion object {
        const val TRIGGER_CHANNEL_ID = "ChildSafetyTriggerChannel"
        const val SECRET_CODE = "#حماية#"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val body = sms.displayMessageBody ?: ""
                val sender = sms.displayOriginatingAddress ?: ""

                // إذا كانت الرسالة تحتوي على الكود السري #حماية# أو #OPEN#
                if (body.contains(SECRET_CODE, ignoreCase = true) || body.contains("#OPEN#", ignoreCase = true)) {
                    wakeAndLaunchApp(context)
                    break
                }
            }
        }
    }

    private fun wakeAndLaunchApp(context: Context) {
        // 1. إيقاظ المعالج والشاشة فوراً
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "ChildSafety::SmsWakeLock"
        )
        wakeLock.acquire(30000) // إيقاظ لمدة 30 ثانية

        // 2. تشغيل الخدمة
        ChildSafetyService.startService(context)

        // 3. فتح شاشة التطبيق مباشرة فوق شاشة القفل (Full Screen Intent)
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("auto_connect", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            102,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TRIGGER_CHANNEL_ID,
                "تنبيهات الاستجابة الفورية",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, TRIGGER_CHANNEL_ID)
            .setContentTitle("طلب اتصال الأب")
            .setContentText("جاري فتح قناة الأمان تلقائياً...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(102, notification)

        // محاولة الفتح المباشر أيضاً
        try {
            context.startActivity(activityIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
