package com.skyzh.tenitsu

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.BottomNavigationView
import android.support.v7.app.AppCompatActivity
import android.util.Log
import io.reactivex.Single
import kotlinx.android.synthetic.main.activity_main.*

private const val REQUEST_ENABLE_BT = 20

class MainActivity : AppCompatActivity() {
    /*


    private val mOnNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.navigation_home -> {

                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_dashboard -> {

                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_notifications -> {

                return@OnNavigationItemSelectedListener true
            }
        }
        false
    }
    */

    private val mBluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val BluetoothAdapter.isDisabled: Boolean
        get() = !isEnabled

    private fun scanLeDevice(): Single<BluetoothDevice> {
        return Single.create<BluetoothDevice> { emitter ->
            run {
                mBluetoothAdapter?.startLeScan { device, rssi, scanRecord ->
                    emitter.onSuccess(device)
                }
            }
        }
    }

    private var logs: String = ""

    private fun logToView(message: String) {
        logs += message
        textView.text = logs
    }

    private fun buttonConnect(): Single<Boolean> {
        return Single.create<Boolean> { emitter ->
            run {
                button_connect.setOnClickListener {
                    emitter.onSuccess(true)
                }
            }
        }
    }

    private fun buttonClear(): Single<Boolean> {
        return Single.create<Boolean> { emitter ->
            run {
                button_clear.setOnClickListener {
                    emitter.onSuccess(true);
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener)

        mBluetoothAdapter?.takeIf { it.isDisabled }?.apply {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }

        buttonConnect()
                .map { d ->
                    logToView("Scan started...")
                    button_connect.isEnabled = false
                    d
                }
                .flatMap { scanLeDevice() }
                .map { device -> device.name }
                .subscribe { device -> logToView("Found device: " + device) }

        buttonClear()
                .subscribe { d ->
                    logs = ""
                    textView.text = ""
                }
    }
}
