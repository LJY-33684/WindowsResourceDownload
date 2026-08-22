package link.mczihan.androidResourceDownload.feature.downloads

import link.mczihan.androidResourceDownload.domain.model.DownloadStatus
import link.mczihan.androidResourceDownload.domain.model.DownloadTask

internal class DownloadSpeedEstimator(
    private val staleAfterMillis: Long = 2_000L,
) {
    private data class Sample(
        val downloadedBytes: Long,
        val observedAt: Long,
    )

    private val samples = mutableMapOf<String, Sample>()
    private val speeds = mutableMapOf<String, Long>()

    /**
     * Called once per second. Speed = bytes downloaded in the last ~1 second.
     */
    fun update(tasks: List<DownloadTask>, observedAt: Long): Map<String, Long> {
        val runningIds = tasks.asSequence()
            .filter { it.status == DownloadStatus.RUNNING }
            .mapTo(mutableSetOf(), DownloadTask::id)
        samples.keys.retainAll(runningIds)
        speeds.keys.retainAll(runningIds)

        tasks.filter { it.status == DownloadStatus.RUNNING }.forEach { task ->
            val previous = samples[task.id]
            if (previous == null) {
                samples[task.id] = Sample(task.downloadedBytes, observedAt)
                speeds[task.id] = 0L
            } else {
                val bytesDelta = task.downloadedBytes - previous.downloadedBytes
                speeds[task.id] = if (bytesDelta >= 0L) bytesDelta else 0L
                samples[task.id] = Sample(task.downloadedBytes, observedAt)
            }
        }
        return snapshot(observedAt)
    }

    fun snapshot(observedAt: Long): Map<String, Long> {
        samples.forEach { (taskId, sample) ->
            if (observedAt - sample.observedAt >= staleAfterMillis) speeds[taskId] = 0L
        }
        return speeds.toMap()
    }

    fun clear() {
        samples.clear()
        speeds.clear()
    }
}
