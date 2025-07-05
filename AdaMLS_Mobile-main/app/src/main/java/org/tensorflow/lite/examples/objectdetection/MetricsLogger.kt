package org.tensorflow.lite.examples.objectdetection

import android.content.Context
import android.graphics.Color
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.util.*

class MetricLogger(private val context: Context) {
    private val batteryLevelEntries = ArrayList<Entry>()
    private val cpuUsageEntries = ArrayList<Entry>()
    private val batteryConsumptionEntries = ArrayList<Entry>()

    private var timeCounter = 0f

    fun logMetrics(
        batteryLevel: Int,
        cpuUsage: Float,
        batteryConsumption: Float,
        selectedModel: String
    ) {
        timeCounter += 1f

        batteryLevelEntries.add(Entry(timeCounter, batteryLevel.toFloat()))
        cpuUsageEntries.add(Entry(timeCounter, cpuUsage))
        batteryConsumptionEntries.add(Entry(timeCounter, batteryConsumption))

        // Keep only the last 50 entries for each metric
        if (batteryLevelEntries.size > 50) batteryLevelEntries.removeAt(0)
        if (cpuUsageEntries.size > 50) cpuUsageEntries.removeAt(0)
        if (batteryConsumptionEntries.size > 50) batteryConsumptionEntries.removeAt(0)
    }

    fun updateCharts(
        batteryLevelChart: LineChart,
        cpuUsageChart: LineChart,
        batteryConsumptionChart: LineChart
    ) {
        updateChart(batteryLevelChart, batteryLevelEntries, "Battery Level", Color.BLUE)
        updateChart(cpuUsageChart, cpuUsageEntries, "CPU Usage", Color.RED)
        updateChart(batteryConsumptionChart, batteryConsumptionEntries, "Battery Consumption", Color.GREEN)
    }

    private fun updateChart(chart: LineChart, entries: List<Entry>, label: String, color: Int) {
        val dataSet = LineDataSet(entries, label)
        dataSet.color = color
        dataSet.setDrawCircles(false)
        dataSet.setDrawValues(false)
        dataSet.lineWidth = 2f

        val lineData = LineData(dataSet)
        chart.data = lineData
        chart.description.isEnabled = false
        chart.setTouchEnabled(false)
        chart.setDrawGridBackground(false)
        chart.xAxis.isEnabled = false
        chart.axisLeft.isEnabled = true
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = true

        chart.invalidate()
    }
}
