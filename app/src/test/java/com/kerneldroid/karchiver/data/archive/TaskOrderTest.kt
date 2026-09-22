package com.kerneldroid.karchiver.data.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskOrderTest {

    private fun task(
        id: Long,
        status: TaskStatus,
        createdAt: Long,
        finishedAt: Long? = null
    ) = ArchiveTask(
        id = id,
        kind = TaskKind.EXTRACT,
        title = "task-$id",
        subtitle = "sub-$id",
        status = status,
        done = 0L,
        total = 0L,
        createdAtMillis = createdAt,
        finishedAtMillis = finishedAt
    )

    @Test
    fun runningBeforeQueuedBeforeFinished() {
        val list = listOf(
            task(3L, TaskStatus.DONE, 1L, 100L),
            task(1L, TaskStatus.RUNNING, 1L),
            task(2L, TaskStatus.QUEUED, 1L)
        )
        assertEquals(listOf(1L, 2L, 3L), TaskManager.orderTasks(list).map { it.id })
    }

    @Test
    fun finishedNewestFirst() {
        val list = listOf(
            task(1L, TaskStatus.DONE, 1L, 100L),
            task(2L, TaskStatus.FAILED, 2L, 300L),
            task(3L, TaskStatus.CANCELLED, 3L, 200L)
        )
        assertEquals(listOf(2L, 3L, 1L), TaskManager.orderTasks(list).map { it.id })
    }

    @Test
    fun activeOldestFirst() {
        val list = listOf(
            task(5L, TaskStatus.QUEUED, 30L),
            task(1L, TaskStatus.RUNNING, 10L),
            task(3L, TaskStatus.RUNNING, 20L),
            task(4L, TaskStatus.QUEUED, 5L)
        )
        assertEquals(listOf(1L, 3L, 4L, 5L), TaskManager.orderTasks(list).map { it.id })
    }

    @Test
    fun fractionIsNullWhenTotalIsZero() {
        assertNull(task(1L, TaskStatus.RUNNING, 1L).fraction)
    }
}
