import io.github.palette4j.statistics.TimeWindowSummary;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TimeWindowSummaryTest {

    private static final class MutableClock extends Clock {
        private final ZoneId zone;
        private long millis;

        private MutableClock(ZoneId zone, long initialMillis) {
            this.zone = zone;
            this.millis = initialMillis;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(zone, millis);
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public long millis() {
            return millis;
        }

        static MutableClock startingAt(long initialMillis) {
            return new MutableClock(ZoneId.of("UTC"), initialMillis);
        }

        void advanceMillis(long delta) {
            this.millis += delta;
        }

        void setMillis(long millis) {
            this.millis = millis;
        }
    }


    @DisplayName("ctor() --- throws exception on null time window duration parameter")
    @Test
    void ctor_rejectsNullWindow() {
        MutableClock clock = MutableClock.startingAt(0);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new TimeWindowSummary(null, 10, clock)
        );
        assertTrue(ex.getMessage().toLowerCase().contains("window"));
    }

    @DisplayName("ctor() --- throws exception on null clock parameter")
    @Test
    void ctor_rejectsNullClock() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new TimeWindowSummary(Duration.ofSeconds(1), 10, null)
        );
        assertTrue(ex.getMessage().toLowerCase().contains("clock"));
    }

    @DisplayName("ctor() --- throws exception on zero or negative time window duration")
    @Test
    void ctor_rejectsNonPositiveWindow() {
        MutableClock clock = MutableClock.startingAt(0);

        assertThrows(IllegalArgumentException.class,
                () -> new TimeWindowSummary(Duration.ZERO, 10, clock));

        assertThrows(IllegalArgumentException.class,
                () -> new TimeWindowSummary(Duration.ofMillis(-1), 10, clock));
    }

    @DisplayName("ctor() --- throws exception on zero or negative bucket count")
    @Test
    void ctor_rejectsNonPositiveBucketCount() {
        MutableClock clock = MutableClock.startingAt(0);

        assertThrows(IllegalArgumentException.class,
                () -> new TimeWindowSummary(Duration.ofSeconds(1), 0, clock));

        assertThrows(IllegalArgumentException.class,
                () -> new TimeWindowSummary(Duration.ofSeconds(1), -1, clock));
    }

    @DisplayName("snapshot() --- return zeros and NaNs when no data has been recorded yet")
    @Test
    void snapshot_whenNoData_returnsZerosAndNaNs() {
        MutableClock clock = MutableClock.startingAt(0);
        TimeWindowSummary summary = new TimeWindowSummary(Duration.ofSeconds(1), 10, clock);

        TimeWindowSummary.Snapshot snap = summary.snapshot();

        assertEquals(0L, snap.getCount());
        assertEquals(0.0, snap.getSum(), 0.0);
        assertTrue(Double.isNaN(snap.getMin()));
        assertTrue(Double.isNaN(snap.getMax()));
        assertTrue(Double.isNaN(snap.getMean()));
    }

    @DisplayName("record() --- aggregates recorded values within the same bucket")
    @Test
    void record_andSnapshot_aggregateWithinSameBucket() {
        MutableClock clock = MutableClock.startingAt(0);
        TimeWindowSummary summary = new TimeWindowSummary(Duration.ofMillis(1000), 10, clock);

        clock.setMillis(0);
        summary.record(1.0);

        clock.advanceMillis(50);
        summary.record(3.0);

        TimeWindowSummary.Snapshot snap = summary.snapshot();

        assertEquals(2L, snap.getCount());
        assertEquals(4.0, snap.getSum(), 0.0);
        assertEquals(1.0, snap.getMin(), 0.0);
        assertEquals(3.0, snap.getMax(), 0.0);
        assertEquals(2.0, snap.getMean(), 0.0);
    }

    @DisplayName("record() --- aggregates recorded values correctly across multiple buckets")
    @Test
    void snapshot_aggregatesAcrossMultipleBucketsWithinWindow() {
        MutableClock clock = MutableClock.startingAt(0);
        TimeWindowSummary summary = new TimeWindowSummary(Duration.ofMillis(1000), 10, clock);

        clock.setMillis(0);
        summary.record(1.0);
        clock.setMillis(150);
        summary.record(5.0);
        clock.setMillis(250);
        summary.record(2.0);

        TimeWindowSummary.Snapshot snap = summary.snapshot();

        assertEquals(3L, snap.getCount());
        assertEquals(8.0, snap.getSum(), 0.0);
        assertEquals(1.0, snap.getMin(), 0.0);
        assertEquals(5.0, snap.getMax(), 0.0);
        assertEquals(8.0 / 3.0, snap.getMean(), 1e-12);
    }

    @DisplayName("snapshot() --- excludes buckets correctly outside window cutoff")
    @Test
    void snapshot_excludesBucketsOutsideWindowCutoff() {
        MutableClock clock = MutableClock.startingAt(0);
        TimeWindowSummary summary = new TimeWindowSummary(Duration.ofMillis(300), 3, clock);

        clock.setMillis(0);
        summary.record(1.0);
        clock.setMillis(100);
        summary.record(2.0);
        clock.setMillis(200);
        summary.record(3.0);

        clock.setMillis(350);
        TimeWindowSummary.Snapshot snap = summary.snapshot();

        assertEquals(2L, snap.getCount());
        assertEquals(5.0, snap.getSum(), 0.0);
        assertEquals(2.0, snap.getMin(), 0.0);
        assertEquals(3.0, snap.getMax(), 0.0);
        assertEquals(2.5, snap.getMean(), 0.0);
    }

    @DisplayName("record() --- resets buckets correctly when time wraps around ring")
    @Test
    void record_reusesAndResetsBucket_whenTimeWrapsAroundRing() {
        MutableClock clock = MutableClock.startingAt(0);
        TimeWindowSummary summary = new TimeWindowSummary(Duration.ofMillis(300), 3, clock);

        clock.setMillis(0);
        summary.record(1.0);

        clock.setMillis(300);
        summary.record(10.0);

        TimeWindowSummary.Snapshot snap = summary.snapshot();

        assertEquals(1L, snap.getCount(), "Old bucket content must not leak after wrap/reset");
        assertEquals(10.0, snap.getSum(), 0.0);
        assertEquals(10.0, snap.getMin(), 0.0);
        assertEquals(10.0, snap.getMax(), 0.0);
        assertEquals(10.0, snap.getMean(), 0.0);
    }

    @DisplayName("snapshot() --- ignores empty buckets")
    @Test
    void snapshot_ignoresEmptyBuckets() {

        MutableClock clock = MutableClock.startingAt(0);
        TimeWindowSummary summary = new TimeWindowSummary(Duration.ofMillis(500), 5, clock);

        clock.setMillis(0);
        summary.record(7.0);

        clock.setMillis(350);
        summary.record(2.0);

        TimeWindowSummary.Snapshot snap = summary.snapshot();

        assertEquals(2L, snap.getCount());
        assertEquals(9.0, snap.getSum(), 0.0);
        assertEquals(2.0, snap.getMin(), 0.0);
        assertEquals(7.0, snap.getMax(), 0.0);
        assertEquals(4.5, snap.getMean(), 0.0);
    }
}
