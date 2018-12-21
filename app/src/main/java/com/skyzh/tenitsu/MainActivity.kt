package com.skyzh.tenitsu

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Point
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Switch
import com.polidea.rxandroidble2.NotificationSetupMode
import kotlinx.android.synthetic.main.activity_main.*
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.*
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.*
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit



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

    val transmissionBuilder = TransmissionBuilder()

    private val mBluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val BluetoothAdapter.isDisabled: Boolean
        get() = !isEnabled

    private var logs: List<String> = arrayListOf("")

    private fun logToView(message: String) {
        runOnUiThread {
            logs = arrayListOf(message) + logs
            logs = logs.dropLast(Math.max(logs.size - 15, 0))
            if (logs.isNotEmpty()) textView.text = logs.reduce({ a, b -> a + "\n" + b }) else textView.text = ""
            Log.i("message", message)
        }
    }

    private fun buttonClick(button: Button): Observable<Boolean> {
        return Observable.create<Boolean> { emitter ->
            run {
                button.setOnClickListener {
                    emitter.onNext(true)
                }
            }
        }
    }

    private fun switchChanged(switch: Switch) = Observable.create<Boolean> { emitter ->
        emitter.onNext(switch.isChecked)
        switch.setOnCheckedChangeListener { _, isChecked -> emitter.onNext(isChecked) }
        emitter.setCancellable { switch.setOnCheckedChangeListener(null) };
    }


    private fun radioChanged(radioGroup: RadioGroup) = Observable.create<Int> { emitter ->
        emitter.onNext(radioGroup.checkedRadioButtonId);
        radioGroup.setOnCheckedChangeListener { _, checkedId -> emitter.onNext(checkedId) }
        emitter.setCancellable { radioGroup.setOnCheckedChangeListener(null) };
    }

    enum class Chassis {
        Forward, Left, Right, Backward, Stop, None
    }

    fun driveStatusChanged() = Observable.combineLatest(
            arrayOf(radioChanged(radioGroupC), radioChanged(radioGroupB)),
            {
                arrayOf(when (it[0]) {
                    radioButton_stop.id -> Chassis.Stop
                    radioButton_forward.id -> Chassis.Forward
                    radioButton_left.id -> Chassis.Left
                    radioButton_right.id -> Chassis.Right
                    radioButton_backward.id -> Chassis.Backward
                    else -> Chassis.None
                }, when (it[1]) {
                    radioButton_bstop.id -> Chassis.Stop
                    radioButton_bforward.id -> Chassis.Forward
                    radioButton_bbackward.id -> Chassis.Backward
                    else -> Chassis.None
                })
            })


    private val rxBleClient: RxBleClient? by lazy(LazyThreadSafetyMode.NONE) {
        RxBleClient.create(this)
    }

    val SerialPortUUID = "0000dfb1-0000-1000-8000-00805f9b34fb"
    val CommandUUID = "0000dfb2-0000-1000-8000-00805f9b34fb"
    val ModelNumberStringUUID = "00002a24-0000-1000-8000-00805f9b34fb"
    val InitialCommand = "AT+PASSWOR=DFRobot\r\nAT+CURRUART=115200\r\n"

    fun characteristic(connection: RxBleConnection, uuid: String): Maybe<BluetoothGattCharacteristic> {
        return connection.discoverServices().toObservable()
                .flatMap { services -> services.bluetoothGattServices.toObservable() }
                .flatMap { service -> service.characteristics.toObservable() }
                .filter { characteristic -> characteristic.uuid.toString() == SerialPortUUID }
                .firstElement()
    }

    fun serial(connection: RxBleConnection): Maybe<BluetoothGattCharacteristic> =
            characteristic(connection, SerialPortUUID).doOnSuccess { _ -> logToView("[B] serial connection established") }

    fun command(connection: RxBleConnection): Maybe<BluetoothGattCharacteristic> =
            characteristic(connection, CommandUUID)

    fun get_message(data: Array<Chassis>): ByteArray {
        val l = when (data[0]) {
            Chassis.Backward -> -255
            Chassis.Forward -> 255
            Chassis.Stop -> 0
            Chassis.None -> 0
            Chassis.Left -> -255
            Chassis.Right -> 255
        }
        val r = when (data[0]) {
            Chassis.Backward -> -255
            Chassis.Forward -> 255
            Chassis.Stop -> 0
            Chassis.None -> 0
            Chassis.Left -> 255
            Chassis.Right -> -255
        }
        val f = when (data[1]) {
            Chassis.Backward -> -1
            Chassis.Forward -> 1
            Chassis.Stop -> 0
            else -> 0
        }
        return transmissionBuilder.build_message(l, r, f)
    }

    fun write_data(connection: RxBleConnection, serial: BluetoothGattCharacteristic, data: Array<Chassis>) = connection.writeCharacteristic(
            serial, get_message(data)
    ).toObservable()

    fun hex(data: ByteArray) = data.map { String.format("%02X", it) }.reduceRight { a, b -> a + b }

    fun transmit(device: RxBleDevice) =
            device.establishConnection(false).flatMap { connection ->
                serial(connection).toObservable()
                        .flatMap { serial ->
                            logToView("[B] transmit in progress...")
                            runOnUiThread { switch_connect.isEnabled = true }

                            Observable.merge(
                                    connection.setupNotification(serial, NotificationSetupMode.COMPAT)
                                            .doOnNext { _ -> logToView("[B] receive notification has been set up") }
                                            .flatMap { notificationObservable -> notificationObservable }
                                            .doOnNext { bytes -> logToView(bytes.toString(Charset.forName("UTF-8"))) }

                                    ,
                                    switchChanged(switch_transfer).switchMap { isChecked ->
                                        if (isChecked) Observable.interval(100, TimeUnit.MILLISECONDS).flatMap { driveStatusChanged() }
                                        else Observable.empty()
                                    }
                                            .flatMap { data -> write_data(connection, serial, data) }
                                            .doOnNext { bytes -> logToView("[B] transferred: ${hex(bytes)}") }
                            )
                        }
            }

    fun connect(): Disposable {
        val sub = switchChanged(switch_connect)
                .switchMap {
                    when (it) {
                        true -> {
                            logToView("[B] scan started...")
                            switch_connect.isEnabled = false
                            mBluetoothAdapter?.startLeScan { _, _, _ -> run {} }
                            rxBleClient!!.scanBleDevices(ScanSettings.Builder().build())
                                    .distinct { d -> d.bleDevice.macAddress }
                                    .doOnNext { d ->
                                        logToView("[B] device found: ${d.bleDevice.name
                                                ?: d.bleDevice.macAddress}")
                                    }
                                    .filter { d -> if (d.bleDevice.name == null) false else (d.bleDevice.name == "Tennis") }
                                    .firstElement()
                                    .toObservable()
                                    .flatMap { scanResult ->
                                        val device = scanResult.bleDevice
                                        runOnUiThread {
                                            logToView("[B] BLE device found, ready to connect")
                                        }
                                        transmit(device)
                                    }
                        }
                        false -> {
                            logToView("[B] disconnected")
                            Observable.empty()
                        }
                    }
                }
        return sub.subscribeBy(onError = {
            runOnUiThread {
                logToView("[B] disconnected due to error: ${it.message}")
                switch_connect.isChecked = false
                switch_connect.isEnabled = true
                connect()
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener)

        mBluetoothAdapter?.takeIf { it.isDisabled }?.apply {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }

        if (
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CAMERA), 4)
        }

        connect()

        val btn_sub = buttonClick(button_clear).subscribe { _ ->
            logs = arrayListOf("")
            logToView("")
        }

        val btn_camera = buttonClick(button_camera).subscribe {_ ->
            startActivity(Intent(this@MainActivity, DetectActivity::class.java))
        }
    }

}

