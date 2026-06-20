package com.speedmonitor

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.*
import android.view.*
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.sin

class SpeedService : Service(), LocationListener {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_UPDATE_LIMIT = "ACTION_UPDATE_LIMIT"
        const val EXTRA_SPEED_LIMIT = "speed_limit"
        const val CHANNEL_ID = "SpeedMonitorChannel"
        const val NOTIF_ID = 1
        var isRunning = false
    }

    private lateinit var locationManager: LocationManager
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    private var speedLimit = 80
    private var currentSpeed = 0f
    private var isBeeping = false
    private var beepThread: Thread? = null
    private var audioTrack: AudioTrack? = null

    private lateinit var tvOverlaySpeed: TextView
    private lateinit var tvOverlayLimit: TextView
    private lateinit var tvOverlayStatus: TextView

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                speedLimit = intent.getIntExtra(EXTRA_SPEED_LIMIT, 80)
                startForegroundNotification()
                startLocationUpdates()
                showOverlay()
                isRunning = true
            }
            ACTION_STOP -> {
                stopSelf()
            }
            ACTION_UPDATE_LIMIT -> {
                speedLimit = intent.getIntExtra(EXTRA_SPEED_LIMIT, 80)
                updateOverlayLimit()
                checkSpeedAlert()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SpeedMonitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoreo de velocidad en tiempo real"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val notifIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notifIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚗 SpeedMonitor Activo")
            .setContentText("Monitoreando velocidad • Límite: $speedLimit km/h")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIF_ID, notification)
    }

    private fun startLocationUpdates() {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                500L,    // 500ms update interval
                0f,      // 0 meters minimum distance
                this
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun showOverlay() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_speed, null)

        tvOverlaySpeed = overlayView!!.findViewById(R.id.tvOverlaySpeed)
        tvOverlayLimit = overlayView!!.findViewById(R.id.tvOverlayLimit)
        tvOverlayStatus = overlayView!!.findViewById(R.id.tvOverlayStatus)

        val closeBtn = overlayView!!.findViewById<ImageButton>(R.id.btnOverlayClose)
        closeBtn.setOnClickListener { stopSelf() }

        tvOverlayLimit.text = "Límite: $speedLimit km/h"
        tvOverlaySpeed.text = "0"
        tvOverlayStatus.text = "GPS buscando..."

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 120
        }

        // Make overlay draggable
        overlayView!!.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                event ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (initialTouchX - event.rawX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(overlayView, params)
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(overlayView, params)
    }

    override fun onLocationChanged(location: Location) {
        if (location.hasSpeed()) {
            currentSpeed = location.speed * 3.6f // m/s to km/h
        } else {
            currentSpeed = 0f
        }
        updateOverlaySpeed()
        checkSpeedAlert()
        updateNotification()
    }

    private fun updateOverlaySpeed() {
        val speedInt = currentSpeed.toInt()
        Handler(Looper.getMainLooper()).post {
            tvOverlaySpeed.text = speedInt.toString()
            tvOverlayStatus.text = if (currentSpeed > speedLimit) {
                "⚠️ VELOCIDAD EXCEDIDA"
            } else {
                "✅ Velocidad normal"
            }

            // Change color based on speed
            val color = when {
                currentSpeed > speedLimit -> Color.parseColor("#FF3333")
                currentSpeed > speedLimit * 0.85f -> Color.parseColor("#FF8C00")
                else -> Color.parseColor("#00E676")
            }
            tvOverlaySpeed.setTextColor(color)
        }
    }

    private fun updateOverlayLimit() {
        Handler(Looper.getMainLooper()).post {
            tvOverlayLimit.text = "Límite: $speedLimit km/h"
        }
    }

    private fun checkSpeedAlert() {
        if (currentSpeed > speedLimit) {
            if (!isBeeping) startBeeping()
        } else {
            if (isBeeping) stopBeeping()
        }
    }

    private fun startBeeping() {
        isBeeping = true
        beepThread = Thread {
            while (isBeeping) {
                playDoubleBeep()
                Thread.sleep(800) // Gap between double-beep cycles
            }
        }
        beepThread?.start()
    }

    private fun stopBeeping() {
        isBeeping = false
        beepThread?.interrupt()
        beepThread = null
        audioTrack?.stop()
    }

    private fun playDoubleBeep() {
        try {
            playTone(880, 120)  // First beep
            Thread.sleep(100)
            playTone(880, 120)  // Second beep
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun playTone(frequencyHz: Int, durationMs: Int) {
        val sampleRate = 44100
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val angle = 2.0 * Math.PI * i / (sampleRate.toDouble() / frequencyHz)
            // Apply fade in/out envelope to avoid clicks
            val envelope = when {
                i < numSamples * 0.1 -> i / (numSamples * 0.1)
                i > numSamples * 0.9 -> (numSamples - i) / (numSamples * 0.1)
                else -> 1.0
            }
            samples[i] = (sin(angle) * 32767 * 0.7 * envelope).toInt().toShort()
        }

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBufferSize, numSamples * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack = track
        track.write(samples, 0, numSamples)
        track.play()

        Thread.sleep(durationMs.toLong())
        track.stop()
        track.release()
    }

    private fun updateNotification() {
        val speedInt = currentSpeed.toInt()
        val alert = if (currentSpeed > speedLimit) " ⚠️ ALERTA" else ""
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚗 SpeedMonitor$alert")
            .setContentText("Velocidad: $speedInt km/h  |  Límite: $speedLimit km/h")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopBeeping()
        try {
            locationManager.removeUpdates(this)
        } catch (e: Exception) { /* ignore */ }
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Unused LocationListener methods
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {
        Handler(Looper.getMainLooper()).post {
            tvOverlayStatus.text = "❌ GPS desactivado"
        }
    }
}
