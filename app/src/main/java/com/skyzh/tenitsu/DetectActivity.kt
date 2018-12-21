package com.skyzh.tenitsu

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import kotlinx.android.synthetic.main.activity_detect.*
import org.bytedeco.javacpp.opencv_core.*
import org.bytedeco.javacpp.opencv_imgproc.*


class DetectActivity : AppCompatActivity(), CvCameraPreview.CvCameraViewListener {

    private fun cameraView() = camera_view as CvCameraPreview

    override fun onCameraViewStarted(width: Int, height: Int) {
    }

    override fun onCameraViewStopped() {

    }

    override fun onCameraFrame(rgbaMat: Mat): Mat {
        var blurOut = Mat()
        blur(rgbaMat, blurOut, Size(3, 3))
        var hslOut = Mat()
        cvtColor(blurOut, hslOut, COLOR_BGR2HLS)
        val hslFrom = arrayListOf(32.0, 0.0, 82.0)
        val hslTo = arrayListOf(55.0, 198.0, 255.0)
        val hslChannels = MatVector()
        val hMask = Mat()
        val lMask = Mat()
        val sMask = Mat()
        val filteredOut0 = Mat()
        val filteredOut1 = Mat()
        val filteredOut2 = Mat()
        split(hslOut, hslChannels)
        threshold(hslChannels.get(0), hMask, hslFrom[0], hslTo[0], THRESH_BINARY)
        threshold(hslChannels.get(1), lMask, hslFrom[1], hslTo[1], THRESH_BINARY)
        threshold(hslChannels.get(2), sMask, hslFrom[2], hslTo[2], THRESH_BINARY)
        bitwise_and(hMask, lMask, filteredOut0)
        bitwise_and(filteredOut0, sMask, filteredOut1)
        filteredOut1.convertTo(filteredOut2, CV_8UC1)
        var finalOut = Mat()
        rgbaMat.copyTo(finalOut, filteredOut2)
        return finalOut
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detect)
        camera_view.setCvCameraViewListener(this)
    }
}
