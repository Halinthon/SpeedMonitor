package com.speedmonitor

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvCurrentSpeed: TextView
    private lateinit var tvSpeedLimit: TextView
    private lateinit var btnStartService: Button
    private lateinit var btnStopService: Button
    private lateinit var btnSetLimit: Button
    private lateinit var sbSpeedLimit: SeekBar
    private lateinit var tvStatus: TextView

    companion object {
        const val PERM_REQUEST_CODE = 100
        const val OVERLAY_REQUEST_CODE = 101
        const val PREFS_NAME = "SpeedMonitorPrefs"
        const val KEY_SPEED_LIMIT = "speed_limit"
        const val DEFAULT_SPEED_LIMIT = 80
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        tvCurrentSpeed = findViewById(R.id.tvCurrentSpeed)
        tvSpeedLimit = findViewById(R.id.tvSpeedLimit)
        btnStartService = findViewById(R.id.btnStartService)
        btnStopService = findViewById(R.id.btnStopService)
        btnSetLimit = findViewById(R.id.btnSetLimit)
        sbSpeedLimit = findViewById(R.id.sbSpeedLimit)
        tvStatus = findViewById(R.id.tvStatus)

        val savedLimit = prefs.getInt(KEY_SPEED_LIMIT, DEFAULT_SPEED_LIMIT)
        sbSpeedLimit.max = 200
        sbSpeedLimit.progress = savedLimit
        tvSpeedLimit.text = "Límite: $savedLimit km/h"

        sbSpeedLimit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val limit = if (progress < 20) 20 else progress
                tvSpeedLimit.text = "Límite: $limit km/h"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnSetLimit.setOnClickListener {
            val newLimit = if (sbSpeedLimit.progress < 20) 20 else sbSpeedLimit.progress
            prefs.edit().putInt(KEY_SPEED_LIMIT, newLimit).apply()
            // Notify the running service
            val intent = Intent(this, SpeedService::class.java)
            intent.action = SpeedService.ACTION_UPDATE_LIMIT
            intent.putExtra(SpeedService.EXTRA_SPEED_LIMIT, newLimit)
            startService(intent)
            Toast.makeText(this, "Límite actualizado: $newLimit km/h", Toast.LENGTH_SHORT).show()
        }

        btnStartService.setOnClickListener {
            checkPermissionsAndStart()
        }

        btnStopService.setOnClickListener {
            stopSpeedService()
        }

        // Register broadcast receiver for speed updates
        updateStatus("Listo para iniciar")
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val deniedPerms = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (deniedPerms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, deniedPerms.toTypedArray(), PERM_REQUEST_CODE)
        } else {
            checkOverlayPermission()
        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Permiso de superposición")
                .setMessage("SpeedMonitor necesita permiso para mostrar la ventana flotante sobre otras apps. Por favor actívalo en la siguiente pantalla.")
                .setPositiveButton("Ir a configuración") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivityForResult(intent, OVERLAY_REQUEST_CODE)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        } else {
            startSpeedService()
        }
    }

    private fun startSpeedService() {
        val speedLimit = prefs.getInt(KEY_SPEED_LIMIT, DEFAULT_SPEED_LIMIT)
        val intent = Intent(this, SpeedService::class.java)
        intent.action = SpeedService.ACTION_START
        intent.putExtra(SpeedService.EXTRA_SPEED_LIMIT, speedLimit)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateStatus("✅ Servicio activo — monitoreando velocidad")
        btnStartService.isEnabled = false
        btnStopService.isEnabled = true
    }

    private fun stopSpeedService() {
        val intent = Intent(this, SpeedService::class.java)
        intent.action = SpeedService.ACTION_STOP
        startService(intent)
        updateStatus("⏹ Servicio detenido")
        btnStartService.isEnabled = true
        btnStopService.isEnabled = false
    }

    private fun updateStatus(msg: String) {
        tvStatus.text = msg
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                checkOverlayPermission()
            } else {
                Toast.makeText(this, "Se necesitan permisos de ubicación para funcionar", Toast.LENGTH_LONG).show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                startSpeedService()
            } else {
                Toast.makeText(this, "Permiso de superposición denegado. La miniventana no funcionará.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val isRunning = SpeedService.isRunning
        btnStartService.isEnabled = !isRunning
        btnStopService.isEnabled = isRunning
        if (isRunning) updateStatus("✅ Servicio activo — monitoreando velocidad")
    }
}
