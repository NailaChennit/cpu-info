package com.kgurgul.cpuinfo.domain.observable

import com.kgurgul.cpuinfo.data.provider.CpuDataNativeProvider
import com.kgurgul.cpuinfo.data.provider.CpuDataProvider
import com.kgurgul.cpuinfo.domain.ImmutableInteractor
import com.kgurgul.cpuinfo.domain.model.CpuData
import com.kgurgul.cpuinfo.utils.DispatchersProvider
import com.kgurgul.cpuinfo.utils.Utils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import javax.inject.Inject


class ObservableCpuData @Inject constructor(
        dispatchersProvider: DispatchersProvider,
        private val cpuDataProvider: CpuDataProvider,
        private val cpuDataNativeProvider: CpuDataNativeProvider
) : ImmutableInteractor<Unit, CpuData>() {

    override val dispatcher = dispatchersProvider.io

    public override fun createObservable(params: Unit) = flow {
        while (true) {

            val processorName = cpuDataNativeProvider.getCpuName()
            val abi = cpuDataProvider.getAbi()
            val coreNumber = cpuDataProvider.getNumberOfCores()
            val hasArmNeon = cpuDataNativeProvider.hasArmNeon()
            val frequencies = mutableListOf<CpuData.Frequency>()
            val l1dCaches = cpuDataNativeProvider.getL1dCaches()
                    ?.joinToString(separator = "\n") { Utils.humanReadableByteCount(it.toLong()) }
                    ?: ""
            val l1iCaches = cpuDataNativeProvider.getL1iCaches()
                    ?.joinToString(separator = "\n") { Utils.humanReadableByteCount(it.toLong()) }
                    ?: ""
            val l2Caches = cpuDataNativeProvider.getL2Caches()
                    ?.joinToString(separator = "\n") { Utils.humanReadableByteCount(it.toLong()) }
                    ?: ""
            val l3Caches = cpuDataNativeProvider.getL3Caches()
                    ?.joinToString(separator = "\n") { Utils.humanReadableByteCount(it.toLong()) }
                    ?: ""
            val l4Caches = cpuDataNativeProvider.getL4Caches()
                    ?.joinToString(separator = "\n") { Utils.humanReadableByteCount(it.toLong()) }
                    ?: ""
            val cpusfreq: ArrayList<Float> = ArrayList()
            for (i in 0 until coreNumber) {
                val (min, max) = cpuDataProvider.getMinMaxFreq(i)
                val current = cpuDataProvider.getCurrentFreq(i)
                cpusfreq.add(current.toFloat())
                frequencies.add(CpuData.Frequency(min, max, current))
            }
            emit(CpuData(
                    processorName, abi, coreNumber, hasArmNeon, frequencies,
                    l1dCaches, l1iCaches, l2Caches, l3Caches, l4Caches
            ))
            delay(REFRESH_DELAY)
        }
    }

    companion object {
        private const val REFRESH_DELAY = 1000L
    }
}


