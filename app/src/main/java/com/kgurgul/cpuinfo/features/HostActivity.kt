/*
 * Copyright 2017 KG Soft
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kgurgul.cpuinfo.features

import android.Manifest
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.kgurgul.cpuinfo.R
import com.kgurgul.cpuinfo.data.provider.CpuDataProvider
import com.kgurgul.cpuinfo.databinding.ActivityHostLayoutBinding
import com.kgurgul.cpuinfo.utils.runOnApiAbove
import dagger.hilt.android.AndroidEntryPoint
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList


/**
 * Base activity which is a host for whole application.
 *
 * @author kgurgul
 */
@AndroidEntryPoint
class HostActivity : AppCompatActivity() {
    private var context: Context? = null
    var appContext: Context? = null
    lateinit var file: File
    private lateinit var record_cpu_freq_async_task: HostActivity.RecordCpuFreqAsyncTask

    private lateinit var navController: NavController
    private lateinit var binding: ActivityHostLayoutBinding
    private var cpuDataProvider: CpuDataProvider = CpuDataProvider()


    override fun onCreate(savedInstanceState: Bundle?) {
        // adb shell pm grant com.kgurgul.cpuinfo.debug android.permission.QUERY_ALL_PACKAGES
        // adb shell pm grant com.kgurgul.cpuinfo.debug android.permission.PACKAGE_USAGE_STATS
        // adb shell pm grant com.kgurgul.cpuinfo.debug android.permission.DUMP
        setTheme(R.style.AppThemeBase)
        super.onCreate(savedInstanceState)
        // init async task
        this.record_cpu_freq_async_task = RecordCpuFreqAsyncTask(this)
        // GET number of cores
        val cpuDataProvider = CpuDataProvider()
        val coreNumber = cpuDataProvider.getNumberOfCores()
        // ASK FOR PERMISSIOOOON
        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
            } else {
                ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            }
        } else {
            //println("permission granted already")
            val fileWriter = File("/storage/emulated/0/Android/media/cpusfreq.csv")

            if(!fileWriter.exists()) {
                var headers = "Date,";
                for (i in 0 until coreNumber) {
                    var minmaxfreq = cpuDataProvider.getMinMaxFreq(i)
                    val stringminmaxfreq = minmaxfreq.toString().replace(',', '|')
                    headers += "Cpu$i $stringminmaxfreq,"
                }
                headers += "active_processes,"
                fileWriter.writeText(headers)
            }
        }

        binding = DataBindingUtil.setContentView(this, R.layout.activity_host_layout)
        navController = findNavController(R.id.nav_host_fragment)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            setToolbarTitleAndElevation(destination.label.toString())
        }
        setSupportActionBar(binding.toolbar)
        binding.bottomNavigation.apply {
            setupWithNavController(navController)
            setOnNavigationItemReselectedListener {
                // Do nothing - TODO: scroll to top
            }
        }
        runOnApiAbove(Build.VERSION_CODES.M) {
            // Processes cannot be listed above M
            val menu = binding.bottomNavigation.menu
            menu.findItem(R.id.processes).isVisible = false
        }
    }

    override fun onSupportNavigateUp() = navController.navigateUp()

    /**
     * Set toolbar title and manage elevation in case of L+ devices and TabLayout
     */
    @SuppressLint("NewApi")
    private fun setToolbarTitleAndElevation(title: String) {
        binding.toolbar.title = title
        runOnApiAbove(Build.VERSION_CODES.KITKAT_WATCH) {
            if (navController.currentDestination?.id == R.id.hardware) {
                binding.toolbar.elevation = 0f
            } else {
                binding.toolbar.elevation = resources.getDimension(R.dimen.elevation_height)
            }
        }
    }

    /*override fun onPause() {
        // TODO Auto-generated method stub
        super.onPause()
        this.record_cpu_freq_async_task = RecordCpuFreqAsyncTask(this)
        this.record_cpu_freq_async_task.execute()
    }*/

    override fun onResume() {
        // TODO Auto-generated method stub
        super.onResume()
        //this.record_cpu_freq_async_task.cancel(true)
        this.record_cpu_freq_async_task = RecordCpuFreqAsyncTask(this)
        this.record_cpu_freq_async_task.execute()
    }

    class RecordCpuFreqAsyncTask constructor(val activity: HostActivity?) : AsyncTask<Void?, Void?, String>(){
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun doInBackground(vararg p0: Void?): String {
            try {
                    activity?.get_cpu_details()

            } catch (e: InterruptedException) {
                // We were cancelled; stop sleeping!
                //println(e)
            }
            return "Executed"
        }
    }

    fun getContext(): Context {
        return this.getApplicationContext();
    }
    @Throws(IOException::class)
    fun get_cgroup_type(path: String?): String {
        var reader: BufferedReader? = null
        var  cgroup_type: String = ""
        try {
            reader = BufferedReader(FileReader(path))
            val lines = reader.readLines()
            lines.forEach {
                if ("cpuset" in it){

                    val regex = "cpuset:/(.+)".toRegex()
                    val matchResult = regex.find(it)
                    if (matchResult != null) {
                        cgroup_type  = matchResult.groupValues[1]
                    }
                }
            }
            return cgroup_type
        } finally {
            reader?.close()
        }
    }
    @SuppressLint("NewApi")
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @Throws(IOException::class)
    fun get_cpu_details() {
        val coreNumber = cpuDataProvider.getNumberOfCores()
        var current_process: String
        while (true) {
            SystemClock.sleep(10000)
            val cpusfreq: ArrayList<Float> = ArrayList()
            var current : Long
            for (i in 0 until coreNumber) {
                if (cpuDataProvider.isonline(i).toInt() == 1){
                    current = cpuDataProvider.getCurrentFreq(i)
                }else{
                    current = -1
                }
                cpusfreq.add(current.toFloat())
            }
            // Write in csv
            try {
                val fileWriter = File("/storage/emulated/0/Android/media/cpusfreq.csv")
                val currentDateTime = this.getCurrentTimestamp()
                var info = "$currentDateTime,"
                for (freq in cpusfreq) {
                    info += "$freq,"
                }
                current_process = this.get_current_process()
                info += "$current_process,"
                // check if the phone is locked
                /*val myKM = this.getSystemService(KEYGUARD_SERVICE) as KeyguardManager
                if (myKM.isKeyguardLocked()) {
                    //println("the phone is locked")
                } else {
                    fileWriter.appendText("\n")
                    fileWriter.appendText(info)
                    println(info)
                    //println("Write CSV successfully!")
                }*/
                fileWriter.appendText("\n")
                fileWriter.appendText(info)
                println(info)
            } catch (e: Exception) {
                //println("Writing CSV error!")
                e.printStackTrace()}
        }
    }

    @SuppressLint("NewApi")
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun get_current_process() : String {
        //val active_process: ArrayList<String> = ArrayList()
        var max_time : Long
        var last_process : String
        val now = System.currentTimeMillis()
        val sysusagestats = this.getSystemService(USAGE_STATS_SERVICE)
        val usagestats = sysusagestats as UsageStatsManager
        val list_process: Iterator<*> = usagestats.queryUsageStats(4, 0, now).iterator()
        println(list_process)
        var process = list_process.next() as UsageStats
        max_time = process.lastTimeUsed
        last_process = process.packageName
        while (list_process.hasNext()) {
            process = list_process.next() as UsageStats
            if (max_time < process.lastTimeUsed){
                max_time = process.lastTimeUsed
                //active_process.add(process.packageName)
                last_process = process.packageName
            }
           }
        return last_process
        }

    private fun getCurrentDateTime(): String  {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
             (LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).toString()
        } else {
            val SDFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")
            (SDFormat.format(Date())).toString()
        }
    }
    private fun getCurrentTimestamp(): String  {
        return System.currentTimeMillis().toString()
    }


}