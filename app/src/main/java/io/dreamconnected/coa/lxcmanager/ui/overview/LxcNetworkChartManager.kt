package io.dreamconnected.coa.lxcmanager.ui.overview

import android.annotation.SuppressLint
import android.graphics.Color
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LxcNetworkChartManager {
    companion object {
        var lineNameOne: String? = null
        var lineNameTwo: String? = null

        // 保存x轴时间和数据
        @SuppressLint("ConstantLocale")
        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        private val xTimeList = mutableListOf<String>()
        private val dataList1 = mutableListOf<Float>()
        private val dataList2 = mutableListOf<Float>()
        private var maxCount = 5

        fun initData(count: Int, initData1: FloatArray, initData2: FloatArray) {
            maxCount = count
            xTimeList.clear()
            dataList1.clear()
            dataList2.clear()
            val now = System.currentTimeMillis()
            val interval = 5000L
            for (i in 0 until count) {
                xTimeList.add(timeFormat.format(Date(now - (count - 1 - i) * interval)))
                dataList1.add(initData1[i])
                dataList2.add(initData2[i])
            }
        }

        // 新增：动态更新数据
        fun addEntry(newValue1: Float, newValue2: Float) {
            if (dataList1.size >= maxCount) {
                dataList1.removeAt(0)
                dataList2.removeAt(0)
                xTimeList.removeAt(0)
            }
            dataList1.add(newValue1)
            dataList2.add(newValue2)
            xTimeList.add(timeFormat.format(Date()))
        }

        fun initDoubleLineChart(
            mLineChart: LineChart
        ): LineData {
            // y轴的数据
            val yValues1 = ArrayList<Entry>()
            val yValues2 = ArrayList<Entry>()
            for (i in xTimeList.indices) {
                yValues1.add(Entry(i.toFloat(), dataList1[i]))
                yValues2.add(Entry(i.toFloat(), dataList2[i]))
            }

            val dataSet = LineDataSet(yValues1, lineNameOne).apply {
                lineWidth = 1.75f
                circleRadius = 2f
                color = Color.rgb(89, 194, 230)
                setCircleColor(Color.rgb(89, 194, 230))
                highLightColor = Color.GREEN
                isHighlightEnabled = true
                valueTextColor = Color.rgb(89, 194, 230)
                valueTextSize = 8f
            }

            val dataSet1 = LineDataSet(yValues2, lineNameTwo).apply {
                lineWidth = 1.75f
                circleRadius = 2f
                color = Color.rgb(252, 76, 122)
                setCircleColor(Color.rgb(252, 76, 122))
                highLightColor = Color.GREEN
                isHighlightEnabled = true
                valueTextColor = Color.rgb(252, 76, 122)
                valueTextSize = 8f
            }

            val dataSets = ArrayList<ILineDataSet>()
            dataSets.add(dataSet)
            dataSets.add(dataSet1)

            val lineData = LineData(dataSets)

            // 设置 x 轴的值
            mLineChart.xAxis.valueFormatter = MyValueFormatter(xTimeList)
            mLineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM

            return lineData
        }

        class MyValueFormatter(private val xValues: List<String>) : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                val index = value.toInt()
                return if (index in xValues.indices) xValues[index] else ""
            }
        }

        /**
         * @Description:初始化图表的样式
         */
        fun initDataStyle(lineChart: LineChart, lineData: LineData?) {
            //设置点击折线点时，显示其数值
            lineChart.setDrawBorders(false) //在折线图上添加边框
            //lineChart.setDescription("时间/数据"); //数据描述
            lineChart.setDrawGridBackground(false) //表格颜色
            lineChart.setGridBackgroundColor(Color.GRAY and 0x70FFFFFF)
            lineChart.setTouchEnabled(true) //可点击
            lineChart.setDragEnabled(true) //可拖拽
            lineChart.setScaleEnabled(true) //可缩放
            lineChart.setPinchZoom(false)
            //lineChart.setBackgroundColor(Color.WHITE) //设置背景颜色

            lineChart.setData(lineData)

            val mLegend = lineChart.legend
            mLegend.form = Legend.LegendForm.SQUARE //样式
            mLegend.formSize = 6f //字体
            mLegend.textColor = Color.GRAY //颜色
            lineChart.setVisibleXRange(0f, 4f) //x轴可显示的坐标范围
            val xAxis = lineChart.xAxis //x轴的标示
            xAxis.position = XAxis.XAxisPosition.BOTTOM //x轴位置
            xAxis.textColor = Color.GRAY //字体的颜色
            xAxis.setTextSize(10f) //字体大小
            xAxis.gridColor = Color.GRAY //网格线颜色
            xAxis.setDrawGridLines(false) //不显示网格线
            val axisLeft = lineChart.axisLeft //y轴左边标示
            val axisRight = lineChart.axisRight //y轴右边标示
            axisLeft.textColor = Color.GRAY //字体颜色
            axisLeft.setTextSize(10f) //字体大小
            //axisLeft.setAxisMaxValue(800f); //最大值
            axisLeft.setLabelCount(5, true) //显示格数
            axisLeft.gridColor = Color.GRAY //网格线颜色

            axisRight.setDrawAxisLine(false)
            axisRight.setDrawGridLines(false)
            axisRight.setDrawLabels(false)

            //设置动画效果
            //lineChart.animateY(2000, Easing.Linear)
            //lineChart.animateX(2000, Easing.Linear)
            lineChart.invalidate()
            //lineChart.animateX(2500);  //立即执行动画
        }
        /**
         * @param name
         * @Description:设置折线的名称
         */
        fun setLineName1(name: String?) {
            lineNameOne = name
        }

        /**
         * @param name
         * @Description:设置另一条折线的名称
         */
        fun setLineName2(name: String?) {
            lineNameTwo = name
        }
    }
}