package com.skyzh.tenitsu

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import kotlinx.android.synthetic.main.activity_detect.*
import org.bytedeco.javacpp.opencv_core.Mat
import org.bytedeco.javacpp.opencv_imgproc.*


class DetectActivity : AppCompatActivity(), CvCameraPreview.CvCameraViewListener {

    private fun cameraView() = camera_view as CvCameraPreview

    override fun onCameraViewStarted(width: Int, height: Int) {
    }

    override fun onCameraViewStopped() {

    }

    override fun onCameraFrame(rgbaMat: Mat): Mat {
        return rgbaMat
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detect)
    }
}
