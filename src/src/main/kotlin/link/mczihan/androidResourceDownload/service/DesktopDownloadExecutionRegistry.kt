package link.mczihan.androidResourceDownload.service

import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap

/**
 * Desktop DownloadExecutionRegistry. Tracks running download jobs by taskId and ownerId.
 */
class DesktopDownloadExecutionRegistry {
    private val taskJobs = ConcurrentHashMap<String, Job>()
    private val ownerJobs = ConcurrentHashMap<String, MutableSet<String>>()

    fun register(taskId: String, ownerId: String, job: Job) {
        taskJobs[taskId] = job
        ownerJobs.getOrPut(ownerId) { ConcurrentHashMap.newKeySet() }.add(taskId)
    }

    fun unregister(taskId: String, ownerId: String) {
        taskJobs.remove(taskId)
        ownerJobs[ownerId]?.remove(taskId)
    }

    fun cancelTask(taskId: String) {
        taskJobs[taskId]?.cancel()
    }

    suspend fun cancelTaskAndJoin(taskId: String) {
        taskJobs[taskId]?.let { job ->
            job.cancel()
            runCatching { job.join() }
        }
    }

    fun cancelOwner(ownerId: String) {
        ownerJobs[ownerId]?.forEach { taskId -> taskJobs[taskId]?.cancel() }
    }

    suspend fun cancelOwnerAndJoin(ownerId: String) {
        val jobs = ownerJobs[ownerId]?.mapNotNull { taskJobs[it] } ?: emptyList()
        jobs.forEach { it.cancel() }
        jobs.forEach { runCatching { it.join() } }
    }

    fun isRunning(taskId: String): Boolean = taskJobs[taskId]?.isActive == true
}
