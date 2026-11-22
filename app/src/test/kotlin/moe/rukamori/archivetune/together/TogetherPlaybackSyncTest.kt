package moe.rukamori.archivetune.together

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TogetherPlaybackSyncTest {
    private fun stale(
        sent: Long,
        last: Long,
    ) = TogetherPlaybackSync.isStaleRoomState(sent, last, force = false)

    @Test
    fun `older update from the same host clock is stale`() {
        assertTrue(stale(sent = 100_000, last = 100_500))
    }

    @Test
    fun `newer update is applied`() {
        assertFalse(stale(sent = 101_000, last = 100_500))
    }

    @Test
    fun `host clock far behind the guest's own clock is not stale`() {
        // Guest phone up 5 days, host phone up 1 hour: host updates must still apply.
        assertFalse(stale(sent = 3_600_000, last = 432_000_000))
    }

    @Test
    fun `nothing applied yet means nothing is stale`() {
        assertFalse(stale(sent = 100_000, last = 0))
    }
}
