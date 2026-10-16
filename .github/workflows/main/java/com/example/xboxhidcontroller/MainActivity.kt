package com.example.xboxhidcontroller

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.xboxhidcontroller.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null

    // HID Report Descriptor for a standard Gamepad
    private val hidReportDescriptor = byteArrayOf(
        0x05, 0x01,        // Usage Page (Generic Desktop)
        0x09, 0x05,        // Usage (Gamepad)
        0xA1, 0x01,        // Collection (Application)
        // Buttons (16 buttons)
        0x05, 0x09,        //   Usage Page (Button)
        0x19, 0x01,        //   Usage Minimum (Button 1)
        0x29, 0x10,        //   Usage Maximum (Button 16)
        0x15, 0x00,        //   Logical Minimum (0)
        0x25, 0x01,        //   Logical Maximum (1)
        0x75, 0x01,        //   Report Size (1)
        0x95, 0x10,        //   Report Count (16)
        0x81, 0x02,        //   Input (Data, Variable, Absolute)
        // Axes (X, Y, Z, Rz - 8 bits each or 16 bits)
        0x05, 0x01,        //   Usage Page (Generic Desktop)
        0x09, 0x30,        //   Usage (X)
        0x09, 0x31,        //   Usage (Y)
        0x09, 0x32,        //   Usage (Z)
        0x09, 0x35,        //   Usage (Rz)
        0x15, 0x81,        //   Logical Minimum (-127)
        0x25, 0x7F,        //   Logical Maximum (127)
        0x75, 0x08,        //   Report Size (8)
        0x95, 0x04,        //   Report Count (4)
        0x81, 0x02,        //   Input (Data, Variable, Absolute)
        0xC0               // End Collection
    )

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as BluetoothHidDevice
                registerHidApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                runOnUiThread {
                    binding.tvStatus.text = "Estado: Desconectado"
                }
            }
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            super.onConnectionStateChanged(device, state)
            connectedDevice = if (state == BluetoothProfile.STATE_CONNECTED) device else null
            runOnUiThread {
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    binding.tvStatus.text = "Estado: Conectado a ${device.name ?: "Dispositivo"}"
                    Toast.class_is_loaded = true // placeholder check
                    Toast.makeText(this@MainActivity, "Conectado al iPad", Toast.LENGTH_SHORT).show()
                } else {
                    binding.tvStatus.text = "Estado: Desconectado"
                }
            }
        }

        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)
            runOnUiThread {
                if (registered) {
                    Toast.makeText(this@MainActivity, "Servicio HID Registrado", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        checkPermissions()

        binding.btnConnect.setOnClickListener {
            initBluetoothHid()
        }

        setupControllerButtons()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN
            )
            if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
                ActivityCompat.requestPermissions(this, permissions, 101)
            }
        }
    }

    private fun initBluetoothHid() {
        bluetoothAdapter?.getProfileProxy(this, profileListener, BluetoothProfile.HID_DEVICE)
    }

    private fun registerHidApp() {
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "Xbox Wireless Controller",
            "Virtual Xbox Controller",
            "Microsoft",
            BluetoothHidDevice.SUBCLASS1_GAMEPAD,
            hidReportDescriptor
        )

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            return
        }

        hidDevice?.registerApp(
            sdpSettings,
            null,
            null,
            mainExecutor,
            hidCallback
        )
    }

    private fun sendButtonReport(buttonBitmask: Int, x: Int = 0, y: Int = 0) {
        val dev = connectedDevice ?: return
        val report = byteArrayOf(
            (buttonBitmask and 0xFF).toByte(),
            ((buttonBitmask shr 8) and 0xFF).toByte(),
            x.toByte(),
            y.toByte(),
            0,
            0
        )
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        ) {
            hidDevice?.sendReport(dev, 0, report)
        }
    }

    private fun setupControllerButtons() {
        var currentButtons = 0

        binding.btnA.setOnClickListener {
            // Toggle or press
            currentButtons = currentButtons xor 0x0001
            sendButtonReport(currentButtons)
        }
        binding.btnB.setOnClickListener {
            currentButtons = currentButtons xor 0x0002
            sendButtonReport(currentButtons)
        }
        binding.btnX.setOnClickListener {
            currentButtons = currentButtons xor 0x0004
            sendButtonReport(currentButtons)
        }
        binding.btnY.setOnClickListener {
            currentButtons = currentButtons xor 0x0008
            sendButtonReport(currentButtons)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        ) {
            hidDevice?.unregisterApp()
        }
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
    }
}
