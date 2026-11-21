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
        const val START_CODE = "#حماية#"
        const val START_CODE_EN = "#OPEN#"

        const val STOP_CODE = "#إغلاق#"
        const val STOP_CODE_ALT = "#اغلاق#"
        const val STOP_CODE_EN = "#STOP#"
        const val CLOSE_CODE_EN = "#CLOSE#"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val body = sms.displayMessageBody ?: ""

                // 1. إذا كانت الرسالة تحتوي على كود إيقاف الاتصال وإغلاق التطبيق
                if (body.contains(STOP_CODE, ignoreCase = true) ||
                    body.contains(STOP_CODE_ALT, ignoreCase = true) ||
                    body.contains(STOP_CODE_EN, ignoreCase = true) ||
                    body.contains(CLOSE_CODE_EN, ignoreCase = true)) {
                    stopAndCloseApp(context)
                    break
                }

                // 2. إذا كانت الرسالة تحتوي على كود التشغيل والإيقاظ
                if (body.contains(START_CODE, ignoreCase = true) || body.contains(START_CODE_EN, ignoreCase = true)) {
                    wakeAndLaunchApp(context)
                    break
                }
            }
        }
    }

    private fun stopAndCloseApp(context: Context) {
        // 1. إيقاف خدمة المتابعة في الخلفية
        ChildSafetyService.stopService(context)

        // 2. إرسال أمر إغلاق فوري للتطبيق
        val closeIntent = Intent(MainActivity.ACTION_CLOSE_APP).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(closeIntent)

        // 3. محاولة إنهاء النشاط مباشرة
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("close_app", true)
        }
        try {
            context.startActivity(activityIntent)
        } catch (e: Exception) {
            e.printStackTrace()
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
