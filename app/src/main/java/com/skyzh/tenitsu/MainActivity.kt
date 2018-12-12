package com.skyzh.tenitsu

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationProvider
import android.os.Bundle
import android.support.design.widget.BottomNavigationView
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Button
import com.polidea.rxandroidble2.NotificationSetupMode
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.timerTask
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import com.polidea.rxandroidble2.internal.RxBleLog
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.*
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.toObservable
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
            logs = logs.dropLast(Math.max(logs.size - 20, 0))
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

    private fun switchTransfer(): Observable<Boolean> {
        return Observable.create<Boolean> { emitter ->
            run {
                switch_transfer.setOnCheckedChangeListener { _, isChecked -> emitter.onNext(isChecked) }
            }
        }
    }

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

    fun serial(connection: RxBleConnection): Maybe<BluetoothGattCharacteristic> {
        return characteristic(connection, SerialPortUUID).doOnSuccess { _ -> logToView("connection established") }
    }

    fun command(connection: RxBleConnection): Maybe<BluetoothGattCharacteristic> {
        return characteristic(connection, CommandUUID)
    }

    fun transmit(device: RxBleDevice) = run {
        device.establishConnection(false).subscribe { connection ->
            command(connection)
                    .toObservable()
                    // .flatMap { cmd -> connection.writeCharacteristic(cmd, InitialCommand.toByteArray()).toObservable() }
                    .doOnNext { _ -> logToView("command channel open") }
                    .flatMap { serial(connection).toObservable() }
                    .subscribe { serial ->
                        run {
                            logToView("transferring in progress...")

                            connection.setupNotification(serial, NotificationSetupMode.COMPAT)
                                    .doOnNext { _ -> logToView("notification has been set up") }
                                    .flatMap { notificationObservable -> notificationObservable }
                                    .subscribe({ bytes -> logToView(bytes.toString(Charset.forName("UTF-8"))) }, Throwable::printStackTrace)


                            switchTransfer().switchMap { isChecked ->
                                if (isChecked) Observable.interval(1, TimeUnit.SECONDS)
                                else Observable.empty()
                            }

                                    .switchMap { _ -> connection.writeCharacteristic(serial, "aaa".toByteArray()).toObservable() }
                                    .subscribe({ d -> logToView("data transferred") }, Throwable::printStackTrace)
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

        val btn_connect = buttonClick(button_connect)
                .subscribe { _ ->
                    run {
                        button_connect.isEnabled = false
                        logToView("Scan started...")
                        mBluetoothAdapter?.startLeScan({ _, _, _ -> {} })
                        rxBleClient!!.scanBleDevices(ScanSettings.Builder().build())
                                .distinct { d -> d.bleDevice.macAddress }
                                .doOnNext { d ->
                                    logToView("Device found: " + {
                                        if (d.bleDevice.name != null) d.bleDevice.name else d.bleDevice.macAddress
                                    }())
                                }
                                .filter { d -> if (d.bleDevice.name == null) false else (d.bleDevice.name == "Tennis") }
                                .firstElement()
                                .subscribe({ scanResult ->
                                    val device = scanResult.bleDevice
                                    runOnUiThread {
                                        logToView("Bluetooth device found, ready to connect")
                                    }
                                    transmit(device)

                                }, Throwable::printStackTrace)
                    }
                }

        val btn_clear = buttonClick(button_clear)
                .subscribe { _ ->
                    runOnUiThread {
                        logs = arrayListOf("")
                        textView.text = ""
                    }
                }
    }
}
