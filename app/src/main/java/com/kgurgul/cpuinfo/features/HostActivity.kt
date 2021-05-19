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
import android.app.ActivityManager
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
import androidx.core.content.ContextCompat.getSystemService
import androidx.databinding.DataBindingUtil
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.kgurgul.cpuinfo.R
import com.kgurgul.cpuinfo.data.provider.CpuDataProvider
import com.kgurgul.cpuinfo.databinding.ActivityHostLayoutBinding
import com.kgurgul.cpuinfo.utils.runOnApiAbove
import dagger.hilt.android.AndroidEntryPoint
import java.io.*
import java.text.SimpleDateFormat
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
            println("permission granted already")
            val fileWriter = File("/storage/emulated/0/Android/media/cpusfreq.csv")

            if(!fileWriter.exists()) {
                var headers = "";
                for (i in 0 until coreNumber) {
                    headers += "Cpu$i,"
                }
                headers += "active_process_cgroup"
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
                println(e)
            }
            return "Executed"
        }
    }

    fun getContext(): Context {
        return this.getApplicationContext();
    }
    @Throws(IOException::class)
    fun get_cgroup_type(path: String?): String {
        println(path)
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
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @Throws(IOException::class)
    fun get_cpu_details() {
        val coreNumber = cpuDataProvider.getNumberOfCores()
        while (true) {
            //SystemClock.sleep(5000000000)
            val cpusfreq: ArrayList<Float> = ArrayList()
            for (i in 0 until coreNumber) {
                val current = cpuDataProvider.getCurrentFreq(i)
                cpusfreq.add(current.toFloat())
            }
            // Write in csv
            try {
                val fileWriter = File("/storage/emulated/0/Android/media/cpusfreq.csv")
                var info = ""
                for (freq in cpusfreq) {
                    info += "$freq,"
                }

                this.get_current_process()
                //this.get_cgroup_type("/proc/9377/cgroup")
                val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                val runningAppProcessInfo = activityManager.runningAppProcesses
                println(runningAppProcessInfo.size.toString() + "siiiiize")
                for (i in runningAppProcessInfo.indices) {
                    val cgroup = this.get_cgroup_type(String.format("/proc/%d/cgroup", 8494))
                    //info += "${runningAppProcessInfo[i].processName}::${cgroup}||"
                    println(cgroup)
                    println(runningAppProcessInfo[i].pid)
                }
                // get active processes
                fileWriter.appendText("\n")
                fileWriter.appendText(info)
                println("Write CSV successfully!")
            } catch (e: Exception) {
                println("Writing CSV error!")
                e.printStackTrace()}
            // end of writing :
        break
        }
    }

    @SuppressLint("WrongConstant")
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun get_current_process() {
        val now = System.currentTimeMillis()
        val sysusagestats = this.getSystemService(USAGE_STATS_SERVICE)
        val usagestats = sysusagestats as UsageStatsManager
        val list_process: Iterator<*> = usagestats.queryUsageStats(4, 0, now).iterator()
        println(list_process)
        while (list_process.hasNext()) {
            val var15 = list_process.next() as UsageStats
            if (now - var15.lastTimeUsed < 1000){
                println(var15.packageName)
            }
        }



    }

}