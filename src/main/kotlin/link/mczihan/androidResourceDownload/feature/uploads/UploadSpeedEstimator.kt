package link.mczihan.androidResourceDownload.feature.uploads

import link.mczihan.androidResourceDownload.domain.model.UploadStatus
import link.mczihan.androidResourceDownload.domain.model.UploadTask

internal class UploadSpeedEstimator(
    private val staleAfterMillis: Long = 2_000L,
) {
    private data class Sample(
        val uploadedBytes: Long,
        val observedAt: Long,
    )

    private val samples = mutableMapOf<String, Sample>()
    private val speeds = mutableMapOf<String, Long>()

    fun update(tasks: List<UploadTask>, observedAt: Long): Map<String, Long> {
        val runningIds = tasks.asSequence()
            .filter { it.status == UploadStatus.RUNNING && !it.committing }
            .mapTo(mutableSetOf(), UploadTask::id)
        samples.keys.retainAll(runningIds)
        speeds.keys.retainAll(runningIds)

        tasks.filter { it.status == UploadStatus.RUNNING && !it.committing }.forEach { task ->
            val previous = samples[task.id]
            if (previous == null) {
                samples[task.id] = Sample(task.uploadedBytes, observedAt)
                speeds[task.id] = 0L
            } else {
                val bytesDelta = task.uploadedBytes - previous.uploadedBytes
                speeds[task.id] = if (bytesDelta >= 0L) bytesDelta else 0L
                samples[task.id] = Sample(task.uploadedBytes, observedAt)
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
