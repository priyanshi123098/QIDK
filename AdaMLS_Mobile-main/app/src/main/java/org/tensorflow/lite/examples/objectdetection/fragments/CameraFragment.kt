/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tensorflow.lite.examples.objectdetection.fragments

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import java.util.LinkedList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.tensorflow.lite.examples.objectdetection.ObjectDetectorHelper
import org.tensorflow.lite.examples.objectdetection.R
//import org.tensorflow.lite.examples.objectdetection.databinding.FragmentCameraBinding
import org.tensorflow.lite.task.vision.detector.Detection
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
//import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import org.tensorflow.lite.examples.objectdetection.MetricLogger
import android.graphics.Color
import android.content.Context
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import androidx.constraintlayout.motion.widget.Debug
import org.tensorflow.lite.examples.objectdetection.databinding.FragmentCameraBinding
import android.os.Process
import kotlin.math.min
import androidx.navigation.fragment.findNavController
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.LineData
//import org.tensorflow.lite.examples.objectdetection.fragments.PermissionsFragmentDirections
//

class MetricLogger(private val context: Context) {
    private var logFile: File? = null

    init {
        createLogFile()
    }

    fun logMetrics(batteryLevel: Int, cpuUsage: Float, batteryConsumption: Float, selectedModel: String) {
        val timestamp = getCurrentTimestamp()
        val logMessage = "$timestamp,$batteryLevel,$cpuUsage,$batteryConsumption,$selectedModel"
        writeToLogFile(logMessage)
    }

    private fun createLogFile() {
        val fileName = "metrics_log_${System.currentTimeMillis()}.csv"
        val directory = context.getExternalFilesDir(null)

        logFile = if (directory != null) {
            File(directory, fileName)
        } else {
            File(context.filesDir, fileName)
        }

        // Write header to the CSV file
        writeToLogFile("Timestamp,BatteryLevel,CPUUsage,BatteryConsumption,SelectedModel")
    }

    private fun writeToLogFile(message: String) {
        logFile?.let {
            try {
                FileWriter(it, true).use { writer ->
                    writer.append(message)
                    writer.appendLine()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun getCurrentTimestamp(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date())
    }
}


class CameraFragment : Fragment(), ObjectDetectorHelper.DetectorListener {

    private var CPU_Usage: Int = 0

    private val TAG = "ObjectDetection"

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var objectDetectorHelper: ObjectDetectorHelper
    private lateinit var bitmapBuffer: Bitmap
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var metricLogger: MetricLogger
    private lateinit var batteryLevelChart: LineChart
    private lateinit var cpuUsageChart: LineChart
    private lateinit var batteryConsumptionChart: LineChart
    private val batteryLevelEntries = ArrayList<Entry>()
    private val cpuUsageEntries = ArrayList<Entry>()
    private val batteryConsumptionEntries = ArrayList<Entry>()
    private var chartXValue = 0f

    private fun updateSelectedModel() {
        val selectedModel = getSelectedModel()
        fragmentCameraBinding.bottomSheetLayout.textViewSelectedModel.text = "Selected Model: $selectedModel"
    }

    private fun getSelectedModel(): String {
        return getModelBasedOnCriteria()
    }    

    private fun getModelBasedOnCriteria(): String {
        // val cpuUsage = getCpuUsage()
        // val batteryLevel = getBatteryLevel()
        // val batteryConsumption = getBatteryConsumption()

        // Log.d("DEBUG", "cpuUsage: $CPU_Usage, batteryLevel: $batteryLevel, batteryConsumption: $batteryConsumption")

        return when {
            CPU_Usage > 20 -> "EfficientDet Lite0"
            CPU_Usage > 15 -> "EfficientDet Lite1"
            CPU_Usage > 10 -> "EfficientDet Lite2"
            // batteryLevel > 80 -> "EfficientDet Lite0"
            // batteryLevel > 60 -> "EfficientDet Lite1"
            // batteryLevel > 40 -> "EfficientDet Lite2"
            // batteryConsumption > 80 -> "EfficientDet Lite0"
            // batteryConsumption > 60 -> "EfficientDet Lite1"
            // batteryConsumption > 40 -> "EfficientDet Lite2"
            else -> "MobileNet V1"
        }
    }

    private fun getCpuUsage(): Float {
        val pid = Process.myPid()
        val path = "/proc/$pid/stat"
        try {
            val statContent = File(path).readText()
            val parts = statContent.split(" ")
            val utime = parts[13].toLong()
            val stime = parts[14].toLong()
            val totalTime = utime + stime
            
            Thread.sleep(100) // Wait for 100ms
            
            val newStatContent = File(path).readText()
            val newParts = newStatContent.split(" ")
            val newUtime = newParts[13].toLong()
            val newStime = newParts[14].toLong()
            val newTotalTime = newUtime + newStime
            
            val cpuUsage = (newTotalTime - totalTime) / 1f
            return min(cpuUsage, 100f)
        } catch (e: Exception) {
            e.printStackTrace()
            return 0f
        }
    }

    private fun getBatteryLevel(): Int {
        val batteryManager = requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun getBatteryConsumption(): Float {
        val batteryLevel = getBatteryLevel()
        val cpuUsage = getCpuUsage()

        // This is a simplified calculation. You might want to implement a more sophisticated method
        return batteryLevel * cpuUsage / 100.0f
    }

    private fun updateCharts(batteryLevel: Int, cpuUsage: Float, batteryConsumption: Float) {
        chartXValue += 1f


        // Update battery level chart
        updateChart(batteryLevelChart, batteryLevelEntries, batteryLevel.toFloat(), "Battery Level", Color.BLUE)
        updateChart(cpuUsageChart, cpuUsageEntries, cpuUsage, "CPU Usage", Color.RED)
        updateChart(batteryConsumptionChart, batteryConsumptionEntries, batteryConsumption, "Battery Consumption", Color.GREEN)

        val selectedModel = getSelectedModel()
        metricLogger.logMetrics(batteryLevel, cpuUsage, batteryConsumption, selectedModel)
    }

    private fun updateChart(chart: LineChart, entries: ArrayList<Entry>, newValue: Float, label: String, color: Int) {
        entries.add(Entry(chartXValue, newValue))

        // Limit the number of visible entries
        val visibleRange = 60f // Show last 60 seconds of data
        if (entries.size > visibleRange) {
            entries.removeAt(0)
        }

        val dataSet = LineDataSet(entries, label).apply {
            setDrawCircles(false)
            lineWidth = 2f
            setColor(color)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        chart.data = LineData(dataSet)
        chart.apply {
            setVisibleXRangeMaximum(visibleRange)
            moveViewToX(chartXValue - visibleRange)
            description.isEnabled = false
            legend.isEnabled = true
            xAxis.setDrawLabels(false)
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false
        }

        chart.invalidate()
    }

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService
    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            findNavController().navigate(R.id.action_camera_to_permissions)
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

    }

    override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    private fun setupChart(chart: LineChart) {
        chart.apply {
            description.isEnabled = false
            setTouchEnabled(false)
            isDragEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)
            setDrawGridBackground(false)

            xAxis.apply {
                textColor = Color.WHITE
                setDrawGridLines(false)
                setDrawAxisLine(true)
                position = XAxis.XAxisPosition.BOTTOM
            }

            axisLeft.apply {
                textColor = Color.WHITE
                setDrawGridLines(true)
                setDrawAxisLine(true)
            }

            axisRight.isEnabled = false

            legend.apply {
                textColor = Color.WHITE
                verticalAlignment = Legend.LegendVerticalAlignment.TOP
                horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                orientation = Legend.LegendOrientation.VERTICAL
                setDrawInside(false)
            }

            data = LineData().apply {
                setValueTextColor(Color.WHITE)
            }
        }
    }

    private fun setupCharts() {
        setupChart(batteryLevelChart)
        setupChart(cpuUsageChart)
        setupChart(batteryConsumptionChart)
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        objectDetectorHelper = ObjectDetectorHelper(
            context = requireContext(),
            objectDetectorListener = this
        )

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }

        updateSelectedModel()

        // Attach listeners to UI control widgets
        initBottomSheetControls()
        metricLogger = MetricLogger(requireContext())
        batteryLevelChart = fragmentCameraBinding.bottomSheetLayout.batteryLevelChart
        cpuUsageChart = fragmentCameraBinding.bottomSheetLayout.cpuUsageChart
        batteryConsumptionChart = fragmentCameraBinding.bottomSheetLayout.batteryConsumptionChart

        setupCharts()
        startMetricUpdates()

        // Set up section switcher
        fragmentCameraBinding.bottomSheetLayout.statsIcon.setOnClickListener {
            showStatsSection()
        }
        fragmentCameraBinding.bottomSheetLayout.graphsIcon.setOnClickListener {
            showGraphsSection()
        }

        // Show stats section by default
        showStatsSection()
    }

    private fun showStatsSection() {
        fragmentCameraBinding.bottomSheetLayout.statsSection.visibility = View.VISIBLE
        fragmentCameraBinding.bottomSheetLayout.graphsSection.visibility = View.GONE
        fragmentCameraBinding.bottomSheetLayout.statsIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.white))
        fragmentCameraBinding.bottomSheetLayout.graphsIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.grey))
    }

    private fun showGraphsSection() {
        fragmentCameraBinding.bottomSheetLayout.statsSection.visibility = View.GONE
        fragmentCameraBinding.bottomSheetLayout.graphsSection.visibility = View.VISIBLE
        fragmentCameraBinding.bottomSheetLayout.statsIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.grey))
        fragmentCameraBinding.bottomSheetLayout.graphsIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.white))
    }

    private fun startMetricUpdates() {
        val handler = Handler(Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                val batteryLevel = getBatteryLevel()
                val cpuUsage = getCpuUsage()
                val batteryConsumption = getBatteryConsumption()
                val selectedModel = getSelectedModel()

                CPU_Usage = cpuUsage.toInt()

                fragmentCameraBinding.bottomSheetLayout.textViewBatteryLevel.text = "Battery Level: $batteryLevel%"
                fragmentCameraBinding.bottomSheetLayout.textViewCpuUsage.text = "CPU Usage: ${String.format("%.2f", cpuUsage)}%"
                fragmentCameraBinding.bottomSheetLayout.textViewBatteryConsumption.text = "Battery Consumption: ${String.format("%.2f", batteryConsumption)}%"
                fragmentCameraBinding.bottomSheetLayout.textViewSelectedModel.text = "Selected Model: $selectedModel"

                metricLogger.logMetrics(batteryLevel, cpuUsage, batteryConsumption, selectedModel)
                updateCharts(batteryLevel, cpuUsage, batteryConsumption)
                updateSelectedModel()

                handler.postDelayed(this, 1000) // Update every second
            }
        })
    }

    private fun initBottomSheetControls() {
        // When clicked, lower detection score threshold floor
        fragmentCameraBinding.bottomSheetLayout.thresholdMinus.setOnClickListener {
            if (objectDetectorHelper.threshold >= 0.1) {
                objectDetectorHelper.threshold -= 0.1f
                updateControlsUi()
            }
        }

        // When clicked, raise detection score threshold floor
        fragmentCameraBinding.bottomSheetLayout.thresholdPlus.setOnClickListener {
            if (objectDetectorHelper.threshold <= 0.8) {
                objectDetectorHelper.threshold += 0.1f
                updateControlsUi()
            }
        }

        // When clicked, reduce the number of objects that can be detected at a time
        fragmentCameraBinding.bottomSheetLayout.maxResultsMinus.setOnClickListener {
            if (objectDetectorHelper.maxResults > 1) {
                objectDetectorHelper.maxResults--
                updateControlsUi()
            }
        }

        // When clicked, increase the number of objects that can be detected at a time
        fragmentCameraBinding.bottomSheetLayout.maxResultsPlus.setOnClickListener {
            if (objectDetectorHelper.maxResults < 5) {
                objectDetectorHelper.maxResults++
                updateControlsUi()
            }
        }

        // When clicked, decrease the number of threads used for detection
        fragmentCameraBinding.bottomSheetLayout.threadsMinus.setOnClickListener {
            if (objectDetectorHelper.numThreads > 1) {
                objectDetectorHelper.numThreads--
                updateControlsUi()
            }
        }

        // When clicked, increase the number of threads used for detection
        fragmentCameraBinding.bottomSheetLayout.threadsPlus.setOnClickListener {
            if (objectDetectorHelper.numThreads < 4) {
                objectDetectorHelper.numThreads++
                updateControlsUi()
            }
        }

        // When clicked, change the underlying hardware used for inference. Current options are CPU
        // GPU, and NNAPI
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(0, false)
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    objectDetectorHelper.currentDelegate = p2
                    updateControlsUi()
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }

        // When clicked, change the underlying model used for object detection
        fragmentCameraBinding.bottomSheetLayout.spinnerModel.setSelection(0, false)
        fragmentCameraBinding.bottomSheetLayout.spinnerModel.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    objectDetectorHelper.currentModel = p2
                    updateControlsUi()
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }
    }

    // Update the values displayed in the bottom sheet. Reset detector.
    private fun updateControlsUi() {
        fragmentCameraBinding.bottomSheetLayout.maxResultsValue.text =
            objectDetectorHelper.maxResults.toString()
        fragmentCameraBinding.bottomSheetLayout.thresholdValue.text =
            String.format("%.2f", objectDetectorHelper.threshold)
        fragmentCameraBinding.bottomSheetLayout.threadsValue.text =
            objectDetectorHelper.numThreads.toString()

        // Needs to be cleared instead of reinitialized because the GPU
        // delegate needs to be initialized on the thread using it when applicable
        objectDetectorHelper.clearObjectDetector()
        fragmentCameraBinding.overlay.clear()
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider =
            cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector - makes assumption that we're only using the back camera
        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview =
            Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(cameraExecutor) { image ->
                        if (!::bitmapBuffer.isInitialized) {
                            // The image rotation and RGB image buffer are initialized only once
                            // the analyzer has started running
                            bitmapBuffer = Bitmap.createBitmap(
                              image.width,
                              image.height,
                              Bitmap.Config.ARGB_8888
                            )
                        }

                        detectObjects(image)
                    }
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun detectObjects(image: ImageProxy) {
        // Copy out RGB bits to the shared bitmap buffer
        image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }

        val imageRotation = image.imageInfo.rotationDegrees
        // Pass Bitmap and rotation to the object detector helper for processing and detection
        objectDetectorHelper.detect(bitmapBuffer, imageRotation)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = fragmentCameraBinding.viewFinder.display.rotation
    }

    // Update UI after objects have been detected. Extracts original image height/width
    // to scale and place bounding boxes properly through OverlayView
    override fun onResults(
      results: MutableList<Detection>?,
      inferenceTime: Long,
      imageHeight: Int,
      imageWidth: Int
    ) {
        activity?.runOnUiThread {
            fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
                            String.format("%d ms", inferenceTime)

            // Pass necessary information to OverlayView for drawing on the canvas
            fragmentCameraBinding.overlay.setResults(
                results ?: LinkedList<Detection>(),
                imageHeight,
                imageWidth
            )

            // Force a redraw
            fragmentCameraBinding.overlay.invalidate()
        }
    }

    override fun onError(error: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }
}
