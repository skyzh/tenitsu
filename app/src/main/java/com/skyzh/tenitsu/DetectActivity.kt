package com.skyzh.tenitsu

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import kotlinx.android.synthetic.main.activity_detect.*
import org.bytedeco.javacpp.opencv_core.*
import org.bytedeco.javacpp.opencv_imgproc.*
import kotlin.math.PI
import org.bytedeco.javacpp.indexer.*


class DetectActivity : AppCompatActivity(), CvCameraPreview.CvCameraViewListener {

    private fun cameraView() = camera_view as CvCameraPreview

    override fun onCameraViewStarted(width: Int, height: Int) {
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
        blur(rgbaMat, blurOut, Size(5, 5))
        blurOut!!.convertTo(blurOut, CV_8UC3)
        cvtColor(blurOut, hslOut, COLOR_BGR2HLS)
        inRange(hslOut,
                Mat(1, 1, CV_32SC4, Scalar(32.280575539568343, 0.0, 82.55395683453237, 0.0)),
                Mat(1, 1, CV_32SC4, Scalar(90.70288624787776, 198.7181663837012, 255.0, 0.0)),
                filteredOut0)
        filteredOut0!!.convertTo(filteredOut1, CV_8UC1)
        rgbaMat.copyTo(finalOut)

        // find tennis ball
        findContours(filteredOut0, contours, CV_RETR_EXTERNAL, CV_CHAIN_APPROX_SIMPLE)

        for (i in 0 until contours!!.size()) {
            val contour = contours!![i]
            val area = contourArea(contour)
            val length = arcLength(contour, true)
            val ratio = 4 * PI * area / length / length
            //drawContours(finalOut, contours, i.toInt(), Scalar(0.0, 255.0, 0.0, 3.0))
            if (ratio >= 0.4 && area >= 100) {
                Log.w("DATA", ratio.toString())
                val rect = boundingRect(contour)
                rectangle(finalOut, rect, Scalar(0.0, 0.0, 255.0, 3.0))
            }
        }

        val indexer: UByteIndexer = hslOut!!.createIndexer()
        val y: Long = 640 / 2
        val x: Long = 480 / 2
        Log.w("DATA", "${indexer.get(y, x, 0).toString()} ${indexer.get(y, x, 1).toString()} ${indexer.get(y, x, 2).toString()}")

        return finalOut!!
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detect)
        camera_view.setCvCameraViewListener(this)
    }
}
