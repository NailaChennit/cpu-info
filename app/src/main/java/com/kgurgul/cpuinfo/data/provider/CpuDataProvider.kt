package com.kgurgul.cpuinfo.data.provider

import android.content.Intent.getIntent
import android.os.Build
import timber.log.Timber
import java.io.File
import java.io.FileFilter
import java.io.RandomAccessFile
import java.util.regex.Pattern
import javax.inject.Inject


class CpuDataProvider @Inject constructor() {

    fun getAbi(): String {
        return if (Build.VERSION.SDK_INT >= 21) {
            Build.SUPPORTED_ABIS[0]
        } else {
            @Suppress("DEPRECATION")
            Build.CPU_ABI
        }
    }

    fun getNumberOfCores(): Int {
        return if (Build.VERSION.SDK_INT >= 17) {
            Runtime.getRuntime().availableProcessors()
        } else {
            getNumCoresLegacy()
        }
    }

    /**
     * Checking frequencies directories and return current value if exists (otherwise we can
     * assume that core is stopped - value -1)
     */
    fun getCurrentFreq(coreNumber: Int): Long {
        val currentFreqPath = "${CPU_INFO_DIR}cpu$coreNumber/cpufreq/cpuinfo_cur_freq"
        var aa: Long
        try {
            aa = RandomAccessFile(currentFreqPath, "r").use { it.readLine().toLong() / 1000 }
        } catch (e: Exception) {
<<<<<<< HEAD
            Timber.e(e)
            aa = -1
        }
        return aa
    }

    fun isonline(coreNumber: Int): Long {
        val isonlinepath = "${CPU_INFO_DIR}cpu$coreNumber/online"
        var isonline: Long
        try {
            isonline = RandomAccessFile(isonlinepath, "r").use { it.readLine().toLong()}
        } catch (e: Exception) {
            //Timber.e(e)
            isonline = -1
=======
            Timber.e("getCurrentFreq() - cannot read file")
            -1
>>>>>>> b78e001207cd95f49172d0d2df73530aefc3f906
        }
        return isonline
    }

    /**
     * Read max/min frequencies for specific [coreNumber]. Return [Pair] with min and max frequency
     * or [Pair] with -1.
     */
    fun getMinMaxFreq(coreNumber: Int): Pair<Long, Long> {
        val minPath = "${CPU_INFO_DIR}cpu$coreNumber/cpufreq/cpuinfo_min_freq"
        val maxPath = "${CPU_INFO_DIR}cpu$coreNumber/cpufreq/cpuinfo_max_freq"
        var aa :Pair<Long, Long>
        try {
            val minMhz = RandomAccessFile(minPath, "r").use { it.readLine().toLong() / 1000 }
            val maxMhz = RandomAccessFile(maxPath, "r").use { it.readLine().toLong() / 1000 }
            aa = Pair(minMhz, maxMhz)
        } catch (e: Exception) {
<<<<<<< HEAD
            //Timber.e(e)
            aa = Pair(-1, -1)
=======
            Timber.e("getMinMaxFreq() - cannot read file")
            Pair(-1, -1)
>>>>>>> b78e001207cd95f49172d0d2df73530aefc3f906
        }
        return aa
    }

    /**
     * Gets the number of cores available in this device, across all processors.
     * Requires: Ability to peruse the filesystem at "/sys/devices/system/cpu"
     *
     * @return The number of cores, or 1 if check fails
     */
    private fun getNumCoresLegacy(): Int {
        class CpuFilter : FileFilter {
            override fun accept(pathname: File): Boolean {
                // Check if filename is "cpu", followed by a single digit number
                if (Pattern.matches("cpu[0-9]+", pathname.name)) {
                    return true
                }
                return false
            }
        }
        return try {
            File(CPU_INFO_DIR).listFiles(CpuFilter())!!.size
        } catch (e: Exception) {
            1
        }
    }

    companion object {
        private const val CPU_INFO_DIR = "/sys/devices/system/cpu/"
    }
}