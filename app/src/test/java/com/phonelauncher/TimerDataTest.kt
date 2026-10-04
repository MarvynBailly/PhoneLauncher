package com.phonelauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerDataTest {

    private val min = 60_000L
    private fun m(n: Long) = n * min
    private fun seg(a: Long, b: Long) = TimeSegment(m(a), m(b))
    private fun running(start: Long, segs: List<TimeSegment> = emptyList(), id: String = "t", parent: String? = null) =
        TimerEntry(id = id, name = id, parentId = parent, segments = segs, startedAt = m(start), isRunning = true)
    private fun paused(segs: List<TimeSegment>, id: String = "p", name: String = id, parent: String? = null) =
        TimerEntry(id = id, name = name, parentId = parent, segments = segs, startedAt = 0, isRunning = false)

    // excludeIntervals

    @Test fun screenOffTimeIsKept() {
        // Running since 0, locked until 60, nothing else opened.
        val t = running(0).excludeIntervals(emptyList(), now = m(60))
        assertEquals(m(60), t.elapsed(m(60)))
        assertTrue(t.isRunning)
    }

    @Test fun appTimeInTheMiddleIsRemoved() {
        val t = running(0).excludeIntervals(listOf(seg(20, 30)), now = m(60))
        assertEquals(listOf(seg(0, 20)), t.segments)
        assertEquals(m(30), t.startedAt)
        assertEquals(m(50), t.elapsed(m(60)))
    }

    @Test fun appTimeAtBothEndsIsRemoved() {
        val t = running(10).excludeIntervals(listOf(seg(0, 15), seg(50, 60)), now = m(60))
        assertEquals(listOf(seg(15, 50)), t.segments)
        assertEquals(m(35), t.elapsed(m(60)))
    }

    @Test fun overlappingAppIntervalsAreRemovedOnce() {
        val t = running(0).excludeIntervals(listOf(seg(30, 40), seg(10, 25), seg(20, 35)), now = m(60))
        assertEquals(listOf(seg(0, 10)), t.segments)
        assertEquals(m(10 + 20), t.elapsed(m(60)))
    }

    @Test fun existingSegmentsAreKept() {
        val t = running(100, segs = listOf(seg(0, 50))).excludeIntervals(listOf(seg(110, 120)), now = m(130))
        assertEquals(listOf(seg(0, 50), seg(100, 110)), t.segments)
        assertEquals(m(50 + 10 + 10), t.elapsed(m(130)))
    }

    @Test fun pausedTimerIsUnchanged() {
        val p = paused(listOf(seg(0, 10)))
        assertEquals(p, p.excludeIntervals(listOf(seg(0, 100)), now = m(100)))
    }

    // withEditedTime

    @Test fun editingEndPastMidnightRollsToNextDay() {
        // 23:00 -> 01:00 entered as 01:00 on the same date.
        val s = TimeSegment(m(23 * 60), m(23 * 60 + 30)).withEditedTime(editingStart = false, newMs = m(60))
        assertEquals(m(120), s.endMs - s.startMs)
    }

    @Test fun editingStartAfterEndMovesToPreviousDay() {
        val s = TimeSegment(m(60), m(120)).withEditedTime(editingStart = true, newMs = m(23 * 60))
        assertEquals(m(180), s.endMs - s.startMs)
    }

    @Test fun normalEditIsUnchanged() {
        val s = TimeSegment(m(60), m(120)).withEditedTime(editingStart = false, newMs = m(90))
        assertEquals(TimeSegment(m(60), m(90)), s)
    }

    // trackedTime

    @Test fun parentAndSubTimerAreNotDoubleCounted() {
        val timers = listOf(
            paused(listOf(seg(0, 60)), id = "parent"),
            paused(listOf(seg(10, 40)), id = "child", parent = "parent"),
            paused(listOf(seg(100, 130)), id = "other"),
        )
        assertEquals(m(60 + 30), trackedTime(timers, now = m(200)))
    }

    @Test fun phoneUsageTimersAreExcluded() {
        val timers = listOf(
            paused(listOf(seg(0, 30)), id = "work"),
            paused(listOf(seg(40, 50)), id = "pu", name = PHONE_USAGE_TIMER_NAME),
            paused(listOf(seg(40, 50)), id = "yt", name = "YouTube", parent = "pu"),
        )
        assertEquals(m(30), trackedTime(timers, now = m(100)))
    }

    @Test fun runningTimerCountsUntilNow() {
        assertEquals(m(25), trackedTime(listOf(running(75)), now = m(100)))
        assertEquals(0L, trackedTime(emptyList(), now = m(100)))
    }

    // carryRunningTimers

    @Test fun runningTimersSurviveResetAndRestartAtDayStart() {
        val timers = listOf(
            running(0, segs = listOf(seg(0, 0)), id = "late"),
            paused(listOf(seg(0, 10)), id = "done"),
        )
        val carried = carryRunningTimers(timers, dayStartMs = m(300))
        assertEquals(1, carried.size)
        assertEquals("late", carried[0].id)
        assertEquals(m(300), carried[0].startedAt)
        assertTrue(carried[0].segments.isEmpty())
    }

    @Test fun parentOfRunningSubTimerIsKeptPaused() {
        val timers = listOf(
            paused(listOf(seg(0, 10)), id = "parent"),
            running(400, id = "child", parent = "parent"),
        )
        val carried = carryRunningTimers(timers, dayStartMs = m(300)).associateBy { it.id }
        assertEquals(setOf("parent", "child"), carried.keys)
        assertFalse(carried.getValue("parent").isRunning)
        assertTrue(carried.getValue("parent").segments.isEmpty())
        assertEquals(m(400), carried.getValue("child").startedAt)
    }
}
