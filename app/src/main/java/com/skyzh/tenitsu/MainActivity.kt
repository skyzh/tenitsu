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
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.support.design.widget.BottomNavigationView
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
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
import org.bytedeco.javacpp.opencv_core.*
import org.bytedeco.javacpp.opencv_imgproc.*
import kotlin.math.PI
import org.bytedeco.javacpp.indexer.*


private const val REQUEST_ENABLE_BT = 20

class MainActivity : AppCompatActivity(), CvCameraPreview.CvCameraViewListener {


    private val mOnNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.navigation_home -> {
                control_layout.visibility = LinearLayout.VISIBLE
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_dashboard -> {
                control_layout.visibility = LinearLayout.GONE
                return@OnNavigationItemSelectedListener true
            }
        }
        false
    }


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
        Forward, Left, Right, Backward, Stop, None, Auto
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
                    radioButton_auto.id -> Chassis.Auto
                    else -> Chassis.None
                }, when (it[1]) {
                    radioButton_bstop.id -> Chassis.Auto
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

    data class AutoLR(val l: Int, val r: Int)

    var pid = PID(15.0, 0.0, 5.0, -255.0, 255.0)

    fun calc_auto(): AutoLR {
        val forward = target.forward
        val rotate = target.angle
        val pid_result = pid.calc(rotate)
        val l = pid.clamp(pid_result + forward * 255, -255.0, 255.0)
        val r = pid.clamp(-pid_result + forward * 255, -255.0, 255.0)
        return AutoLR(l.toInt(), r.toInt())
    }

    fun get_message(data: Array<Chassis>): ByteArray {
        val auto = calc_auto()
        logToView("[CTR] PID ${auto}")
        val l = when (data[0]) {
            Chassis.Backward -> -255
            Chassis.Forward -> 255
            Chassis.Stop -> 0
            Chassis.None -> 0
            Chassis.Left -> -255
            Chassis.Right -> 255
            Chassis.Auto -> auto.l
            else -> 0
        }
        val r = when (data[0]) {
            Chassis.Backward -> -255
            Chassis.Forward -> 255
            Chassis.Stop -> 0
            Chassis.None -> 0
            Chassis.Left -> 255
            Chassis.Right -> -255
            Chassis.Auto -> auto.r
            else -> 0
        }
        val f = when (data[1]) {
            Chassis.Backward -> -1
            Chassis.Forward -> 1
            Chassis.Stop -> 0
            Chassis.Auto -> if (target.found) 1 else 0
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
        // autoDrive = AutoDrive(getSystemService(Context.SENSOR_SERVICE) as SensorManager)
        camera_view.setCvCameraViewListener(this)
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener)
    }

    private fun cameraView() = camera_view as CvCameraPreview

    var width: Int = 0
    var height: Int = 0

    override fun onCameraViewStarted(width: Int, height: Int) {
        this.width = height
        this.height = width
    }

    override fun onCameraViewStopped() {
    }

    var blurOut: Mat? = null
    var hslOut: Mat? = null
    var hMask: Mat? = null
    var lMask: Mat? = null
    var sMask: Mat? = null
    var filteredOut0: Mat? = null
    var filteredOut1: Mat? = null
    var filteredOut2: Mat? = null
    var hslChannels: MatVector? = null
    var finalOut: Mat? = null
    var contours: MatVector? = null

    var target: Target = Target(0.0, 0.0, false)
    var lst_detect : Long = 0

    override fun onCameraFrame(rgbaMat: Mat): Mat {
        if (blurOut == null) blurOut = Mat(rgbaMat.rows(), rgbaMat.cols())
        if (hslOut == null) hslOut = Mat(rgbaMat.rows(), rgbaMat.cols())
        if (hMask == null) hMask = Mat(rgbaMat.rows(), rgbaMat.cols())
        if (lMask == null) lMask = Mat(rgbaMat.rows(), rgbaMat.cols())
        if (sMask == null) sMask = Mat(rgbaMat.rows(), rgbaMat.cols())
        if (filteredOut0 == null) filteredOut0 = Mat(rgbaMat.rows(), rgbaMat.cols())
        if (filteredOut1 == null) filteredOut1 = Mat(rgbaMat.rows(), rgbaMat.cols())
        if (filteredOut2 == null) filteredOut2 = Mat(rgbaMat.rows(), rgbaMat.cols())
        if (hslChannels == null) hslChannels = MatVector()
        if (finalOut == null) finalOut = Mat(rgbaMat.rows(), rgbaMat.cols())
        if (contours == null) contours = MatVector()

        // filter image
        blur(rgbaMat, blurOut, Size(1, 1))
        blurOut!!.convertTo(blurOut, CV_8UC3)
        cvtColor(blurOut, hslOut, COLOR_BGR2HLS)
        if (switch_outdoor.isChecked) {
            inRange(hslOut,
                    Mat(1, 1, CV_32SC4, Scalar(0.0, 149.0, 0.0, 0.0)),
                    Mat(1, 1, CV_32SC4, Scalar(255.0, 255.0, 255.0, 0.0)),
                    filteredOut0)
        } else {
            inRange(hslOut,
                    Mat(1, 1, CV_32SC4, Scalar(32.280575539568343, 0.0, 82.55395683453237, 0.0)),
                    Mat(1, 1, CV_32SC4, Scalar(90.70288624787776, 198.7181663837012, 255.0, 0.0)),
                    filteredOut0)
        }
        filteredOut0!!.convertTo(filteredOut1, CV_8UC1)
        rgbaMat.copyTo(finalOut)

        // find tennis ball
        findContours(filteredOut0, contours, CV_RETR_EXTERNAL, CV_CHAIN_APPROX_SIMPLE)

        var filtered_centers = arrayListOf<Ball>()

        for (i in 0 until contours!!.size()) {
            val contour = contours!![i]
            val area = contourArea(contour)
            val length = arcLength(contour, true)
            val ratio = 4 * PI * area / length / length
            //drawContours(finalOut, contours, i.toInt(), Scalar(0.0, 255.0, 0.0, 3.0))
            if (ratio >= 0.4 && area >= 100) {
                val rect = boundingRect(contour)
                rectangle(finalOut, rect, Scalar(0.0, 255.0, 0.0, 3.0))
                filtered_centers.add(Ball(rect.x() + rect.width() / 2.0, rect.y() + rect.height() / 2.0))
            }
        }

        if (filtered_centers.size > 0) {
            filtered_centers.sortBy({ it.y })
            filtered_centers.reverse()
            val half_width = width / 2
            val target_x = filtered_centers[0].x
            val ang = Math.acos((target_x - half_width) / half_width) / Math.PI * 180.0
            target = Target(1.0, (90.0 - ang) / 180 * 72, true)
            logToView("[CV] Direction = ${target.angle}")
            lst_detect = SystemClock.uptimeMillis()
        } else {
            if (SystemClock.uptimeMillis() - lst_detect > 2000)
                target = Target(0.0, 0.0, false)
        }

        return finalOut!!
    }

    data class Ball(val x: Double, val y: Double)
    data class Target(val forward: Double, val angle: Double, val found: Boolean)

}

