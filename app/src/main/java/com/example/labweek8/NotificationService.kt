package com.example.labweek8

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class NotificationService : Service() {

    // Notification builder untuk menampilkan notifikasi di foreground
    private lateinit var notificationBuilder: NotificationCompat.Builder

    // Handler untuk mengatur thread eksekusi
    private lateinit var serviceHandler: Handler

    // Tidak menggunakan komunikasi dua arah → return null
    override fun onBind(intent: Intent): IBinder? = null

    // Dipanggil saat service pertama kali dibuat
    override fun onCreate() {
        super.onCreate()

        // Inisialisasi notifikasi
        notificationBuilder = startForegroundService()

        // Buat thread baru untuk menjalankan proses notifikasi
        val handlerThread = HandlerThread("SecondThread").apply { start() }
        serviceHandler = Handler(handlerThread.looper)
    }

    // Membuat dan memulai foreground service dengan konfigurasi notifikasi
    private fun startForegroundService(): NotificationCompat.Builder {
        val pendingIntent = getPendingIntent()
        val channelId = createNotificationChannel()
        val notificationBuilder = getNotificationBuilder(pendingIntent, channelId)

        // Jalankan foreground service
        startForeground(NOTIFICATION_ID, notificationBuilder.build())
        return notificationBuilder
    }

    // PendingIntent untuk membuka MainActivity saat notifikasi diklik
    private fun getPendingIntent(): PendingIntent {
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_IMMUTABLE else 0

        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            flag
        )
    }

    // Membuat Notification Channel (API 26+)
    private fun createNotificationChannel(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "001"
            val channelName = "001 Channel"
            val channelPriority = NotificationManager.IMPORTANCE_DEFAULT

            val channel = NotificationChannel(channelId, channelName, channelPriority)

            val service = requireNotNull(
                ContextCompat.getSystemService(this, NotificationManager::class.java)
            )
            service.createNotificationChannel(channel)
            channelId
        } else {
            ""
        }

    // Builder notifikasi
    private fun getNotificationBuilder(
        pendingIntent: PendingIntent,
        channelId: String
    ): NotificationCompat.Builder =
        NotificationCompat.Builder(this, channelId)
            .setContentTitle("Second worker process is done")
            .setContentText("Check it out!")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setTicker("Second worker process is done, check it out!")
            .setOngoing(true)

    // ✅ Tambahkan di bawah getNotificationBuilder() dan sebelum companion object

    // Dipanggil saat service dimulai (setelah startForeground() dijalankan)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val returnValue = super.onStartCommand(intent, flags, startId)

        // Ambil ID channel yang dikirim dari MainActivity
        val Id = intent?.getStringExtra(EXTRA_ID)
            ?: throw IllegalStateException("Channel ID must be provided")

        // Jalankan proses di thread berbeda
        serviceHandler.post {
            // Hitung mundur dari 10 ke 0 di notifikasi
            countDownFromTenToZero(notificationBuilder)

            // Kirim hasil selesai ke MainActivity melalui LiveData
            notifyCompletion(Id)

            // Tutup notifikasi foreground
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return returnValue
    }

    // Fungsi untuk update teks notifikasi dari 10 ke 0
    private fun countDownFromTenToZero(notificationBuilder: NotificationCompat.Builder) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        for (i in 10 downTo 0) {
            Thread.sleep(1000L)
            notificationBuilder
                .setContentText("$i seconds until last warning")
                .setSilent(true)

            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
        }
    }

    // Update LiveData di main thread setelah countdown selesai
    private fun notifyCompletion(Id: String) {
        Handler(Looper.getMainLooper()).post {
            mutableID.value = Id
        }
    }

    companion object {
        const val NOTIFICATION_ID = 0xCA7
        const val EXTRA_ID = "Id"

        private val mutableID = MutableLiveData<String>()
        val trackingCompletion: LiveData<String> = mutableID
    }
}
