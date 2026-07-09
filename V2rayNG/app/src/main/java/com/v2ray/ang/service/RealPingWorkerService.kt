package com.v2ray.ang.service

import android.content.Context
import android.util.Log
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.RealPingEvent
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SpeedtestManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class RealPingWorkerService(
    private val context: Context,
    private val guids: List<String>,
    private val onEvent: (RealPingEvent) -> Unit = {}
) {
    private val job = SupervisorJob()
    private val concurrency = SettingsManager.getRealPingConcurrency()
    private val dispatcher = Executors.newFixedThreadPool(concurrency).asCoroutineDispatcher()
    private val scope = CoroutineScope(job + dispatcher + CoroutineName("RealPingBatchWorker"))

    private val runningCount = AtomicInteger(0)
    private val totalCount = AtomicInteger(0)

    fun start() {
        Log.e("PING_DIAGNOSTIC", "Worker started! Total guids to test: ${guids.size}")

        val jobs = guids.map { guid ->
            totalCount.incrementAndGet()
            scope.launch {
                runningCount.incrementAndGet()
                try {
                    val result = startRealPing(guid)
                    Log.e("PING_DIAGNOSTIC", "Result for guid $guid is: $result")
                    onEvent(RealPingEvent.Result(guid, result))
                } catch (e: Throwable) {
                    Log.e("PING_DIAGNOSTIC", "CRASH in worker launch for guid $guid", e)
                } finally {
                    val count = totalCount.decrementAndGet()
                    val left = runningCount.decrementAndGet()
                    onEvent(RealPingEvent.Progress("$left / $count"))
                }
            }
        }

        scope.launch {
            try {
                joinAll(*jobs.toTypedArray())
                Log.e("PING_DIAGNOSTIC", "All jobs finished successfully!")
                onEvent(RealPingEvent.Finish("0"))
            } catch (e: CancellationException) {
                Log.e("PING_DIAGNOSTIC", "Jobs cancelled!", e)
                onEvent(RealPingEvent.Finish("-1"))
            } finally {
                close()
            }
        }
    }

    fun cancel() {
        job.cancel()
    }

    private fun close() {
        try {
            dispatcher.close()
        } catch (_: Throwable) {
            // ignore
        }
    }

    private fun startRealPing(guid: String): Long {
        val retFailure = -1L

        val config = MmkvManager.decodeServerConfig(guid)
        if (config == null) {
            Log.e("PING_DIAGNOSTIC", "Exit 1: Config is NULL for guid: $guid")
            return retFailure
        }

        // РАЗРЕШАЕМ ПИНГОВАТЬ CUSTOM КОНФИГИ (убираем блокировку Exit 2 для типа CUSTOM)
        if (config.configType == EConfigType.POLICYGROUP || config.configType == EConfigType.PROXYCHAIN) {
            Log.e("PING_DIAGNOSTIC", "Exit 2: Complex type (${config.configType}) for guid: $guid")
            return retFailure
        }

        val url = config.server.orEmpty()
        val portStr = config.serverPort.orEmpty()
        val port = portStr.toIntOrNull() ?: 443

        if (url.isEmpty()) {
            Log.e("PING_DIAGNOSTIC", "Exit 3: Server address is empty for guid: $guid")
            return retFailure
        }

        // Получаем выбранный в настройках метод пинга
        val pingMethod = MmkvManager.decodeSettingsString("pref_ping_method") ?: "0"
        Log.e("PING_DIAGNOSTIC", "Pinging server: $url:$port using method: $pingMethod")

        return when (pingMethod) {
            "1" -> {
                // Режим: TCP Port Ping (Direct - прямое подключение к порту)
                SpeedtestManager.socketConnectTime(url, port, 1500)
            }
            "2" -> {
                // Режим: ICMP Ping (Direct - системный пинг до IP)
                executeIcmpPing(url)
            }
            else -> {
                // Режим: Real Ping (via Proxy)
                val configResult = CoreConfigManager.getV2rayConfig4Speedtest(context, guid)
                if (!configResult.status) {
                    Log.e("PING_DIAGNOSTIC", "Real Ping failed: configResult.status is false, error: ${configResult.errorMessage}")
                    return retFailure
                }
                CoreNativeManager.measureOutboundDelay(configResult.content, SettingsManager.getDelayTestUrl())
            }
        }
    }

    private fun executeIcmpPing(host: String): Long {
        return try {
            val start = System.currentTimeMillis()
            val process = Runtime.getRuntime().exec("ping -c 1 -W 1.5 $host")
            val exitValue = process.waitFor()
            if (exitValue == 0) {
                System.currentTimeMillis() - start
            } else {
                -1L
            }
        } catch (e: Exception) {
            -1L
        }
    }
}