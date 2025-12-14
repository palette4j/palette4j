package io.github.palette4j.statistics;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * Maintains rolling summary statistics over a fixed time window using
 * time-partitioned buckets.
 * <p>
 * This class provides an efficient, lock-free mechanism for aggregating numeric
 * observations (count, sum, min, max, mean) over a sliding time window.
 * Values are recorded into discrete time buckets, and snapshots are computed
 * by aggregating only those buckets that fall within the active window.
 * </p>
 *
 * <h2>State</h2>
 * <ul>
 *   <li><b>Window duration</b> – the total length of time for which values are retained.</li>
 *   <li><b>Bucket size</b> – the duration represented by a single bucket, derived from
 *       the window size and configured bucket count.</li>
 *   <li><b>Bucket ring</b> – a fixed-size array of buckets reused in a circular manner.</li>
 *   <li><b>Clock</b> – a {@link Clock} used as the authoritative time source.</li>
 * </ul>
 *
 * <h2>Behavior</h2>
 * <p>
 * Incoming values are assigned to a bucket based on the current timestamp.
 * Each bucket represents a fixed time interval aligned to the bucket size.
 * </p>
 * <ul>
 *   <li>If a bucket is detected to be outdated, it is reset atomically before use.</li>
 *   <li>Bucket reuse is performed safely using CAS-based coordination.</li>
 *   <li>Statistics are accumulated using contention-friendly primitives.</li>
 * </ul>
 *
 * <h2>Snapshot semantics</h2>
 * <p>
 * Calling {@link #snapshot()} produces a point-in-time, immutable view of the
 * statistics accumulated within the configured time window ({@link Snapshot}).
 * </p>
 * <ul>
 *   <li>Only buckets whose start time falls within the active window are considered.</li>
 *   <li>Empty buckets are ignored.</li>
 *   <li>The snapshot is computed without stopping concurrent writers.</li>
 * </ul>
 *
 * <h2>Concurrency model</h2>
 * <p>
 * This class is designed for high-concurrency environments:
 * </p>
 * <ul>
 *   <li>Recording operations are lock-free.</li>
 *   <li>Min/max updates use CAS loops over raw IEEE-754 bit representations.</li>
 *   <li>Bucket rotation is coordinated via {@link AtomicLongFieldUpdater}.</li>
 * </ul>
 * <p>
 * Snapshots may reflect minor, transient inconsistencies due to concurrent updates,
 * which is acceptable for monitoring and statistical use cases.
 * </p>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * This class is thread-safe. Multiple threads may concurrently call
 * {@link #record(double)} and {@link #snapshot()} without external synchronization.
 * </p>
 *
 * <h2>Design intent</h2>
 * <p>
 * {@code TimeWindowSummary} is intended for lightweight, real-time metrics such as:
 * </p>
 * <ul>
 *   <li>Latency and duration tracking</li>
 *   <li>Throughput and rate measurements</li>
 *   <li>Operational health indicators</li>
 * </ul>
 * <p>
 * It deliberately avoids heavyweight data structures (e.g. histograms or percentiles)
 * in favor of predictable memory usage and low overhead.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * TimeWindowSummary summary =
 *     new TimeWindowSummary(Duration.ofMinutes(1), 12);
 *
 * summary.record(120);
 * summary.record(80);
 *
 * TimeWindowSummary.Snapshot snap = summary.snapshot();
 * double avg = snap.getMean();
 * }</pre>
 */
public class TimeWindowSummary {

    /**
     * Represents an immutable, point-in-time view of aggregated statistics
     * computed by {@link TimeWindowSummary}.
     * <p>
     * A snapshot captures the state of the rolling window at the moment it is taken,
     * including count, sum, minimum, maximum, and arithmetic mean of all values
     * recorded within the active time window.
     * </p>
     *
     * <h2>State</h2>
     * <ul>
     *   <li><b>Count</b> – total number of recorded values.</li>
     *   <li><b>Sum</b> – sum of all recorded values.</li>
     *   <li><b>Minimum</b> – smallest recorded value.</li>
     *   <li><b>Maximum</b> – largest recorded value.</li>
     *   <li><b>Mean</b> – arithmetic mean ({@code sum / count}).</li>
     * </ul>
     *
     * <h2>Empty-window semantics</h2>
     * <p>
     * If no values were recorded within the time window:
     * </p>
     * <ul>
     *   <li>{@code count} is {@code 0}</li>
     *   <li>{@code sum} is {@code 0.0}</li>
     *   <li>{@code min}, {@code max}, and {@code mean} are {@link Double#NaN}</li>
     * </ul>
     * <p>
     * This explicitly signals the absence of data and avoids misleading sentinel values.
     * </p>
     *
     * <h2>Immutability</h2>
     * <p>
     * {@code Snapshot} instances are immutable and thread-safe.
     * All values are computed eagerly during snapshot creation and never change thereafter.
     * </p>
     *
     * <h2>Design intent</h2>
     * <p>
     * {@code Snapshot} serves as a stable data transfer object suitable for:
     * </p>
     * <ul>
     *   <li>Metrics reporting</li>
     *   <li>Logging and diagnostics</li>
     *   <li>Health checks and dashboards</li>
     * </ul>
     * <p>
     * It intentionally contains no references to internal buckets or mutable state.
     * </p>
     */
    public static final class Snapshot {
        private final long count;
        private final double sum;
        private final double min;
        private final double max;
        private final double mean;

        private Snapshot(long count, double sum, double min, double max) {
            this.count = count;
            this.sum = sum;
            this.min = (count == 0) ? Double.NaN : min;
            this.max = (count == 0) ? Double.NaN : max;
            this.mean = (count == 0) ? Double.NaN : sum / count;
        }

        public long getCount() {
            return count;
        }

        public double getSum() {
            return sum;
        }

        public double getMin() {
            return min;
        }

        public double getMax() {
            return max;
        }

        public double getMean() {
            return mean;
        }
    }


    private static final class Bucket {

        final LongAdder count = new LongAdder();
        final DoubleAdder sum = new DoubleAdder();
        final AtomicLong minBits = new AtomicLong(Double.doubleToRawLongBits(Double.POSITIVE_INFINITY));
        final AtomicLong maxBits = new AtomicLong(Double.doubleToRawLongBits(Double.NEGATIVE_INFINITY));
        volatile long windowStartMillis;

        Bucket(long startMillis) {
            this.windowStartMillis = startMillis;
        }

        void resetTo(long startMillis) {
            this.windowStartMillis = startMillis;
            count.reset();
            sum.reset();
            minBits.set(Double.doubleToRawLongBits(Double.POSITIVE_INFINITY));
            maxBits.set(Double.doubleToRawLongBits(Double.NEGATIVE_INFINITY));
        }

        void record(double value) {
            count.increment();
            sum.add(value);
            updateMin(value);
            updateMax(value);
        }

        double min() {
            return Double.longBitsToDouble(minBits.get());
        }

        double max() {
            return Double.longBitsToDouble(maxBits.get());
        }

        private void updateMin(double value) {
            long minBits = Double.doubleToRawLongBits(value);
            while (true) {
                long currentBits = this.minBits.get();
                double current = Double.longBitsToDouble(currentBits);
                if (value >= current) {
                    return;
                }
                if (this.minBits.compareAndSet(currentBits, minBits)) {
                    return;
                }
            }
        }

        private void updateMax(double value) {
            long maxBits = Double.doubleToRawLongBits(value);
            while (true) {
                long currentBits = this.maxBits.get();
                double current = Double.longBitsToDouble(currentBits);
                if (value <= current) {
                    return;
                }
                if (this.maxBits.compareAndSet(currentBits, maxBits)) {
                    return;
                }
            }
        }

        boolean isEmpty() {
            return count.sum() == 0L;
        }

    }


    private static final AtomicLongFieldUpdater<Bucket> BUCKET_WINDOW_START_UPDATER =
            AtomicLongFieldUpdater.newUpdater(Bucket.class, "windowStartMillis");

    private final long windowMillis;
    private final long bucketSizeMillis;
    private final Bucket[] buckets;
    private final Clock clock;

    public TimeWindowSummary(Duration window, int bucketCount, Clock clock) {

        if (window == null) {
            throw new IllegalArgumentException("window must not be null");
        }

        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }

        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be > 0");
        }
        if (bucketCount <= 0) {
            throw new IllegalArgumentException("bucketCount must be > 0");
        }

        this.windowMillis = window.toMillis();
        this.bucketSizeMillis = Math.max(1L, windowMillis / bucketCount);
        this.clock = clock;
        this.buckets = new Bucket[bucketCount];

        long alignedMillis = alignMillisToBucketStart(clock.millis());

        long oldStart = alignedMillis - windowMillis - bucketSizeMillis;
        for (int i = 0; i < bucketCount; i++) {
            buckets[i] = new Bucket(oldStart);
        }
    }

    public TimeWindowSummary(Duration window, int bucketCount) {
        this(window, bucketCount, Clock.systemUTC());
    }

    public TimeWindowSummary(Duration window) {
        this(window, 10, Clock.systemUTC());
    }

    /**
     * Records a value. This value will be placed in the {@link TimeWindowSummary}s' bucket
     * corresponding to the current time.
     * <p>
     * If the bucket is outdated, it will be reset before the new value is recorded.
     *
     * @param value the value to record
     */
    public void record(double value) {
        long nowMillis = clock.millis();
        int index = calculateBucketIndex(nowMillis);
        Bucket bucket = buckets[index];

        long alignedStart = alignMillisToBucketStart(nowMillis);
        long currentStart = bucket.windowStartMillis;

        if (alignedStart - currentStart >= bucketSizeMillis) {
            if (BUCKET_WINDOW_START_UPDATER.compareAndSet(bucket, currentStart, alignedStart)) {
                bucket.resetTo(alignedStart);
            }
        }

        bucket.record(value);
    }

    /**
     * Takes a snapshot of the current statistics for the {@link TimeWindowSummary}.
     * <p>
     * The snapshot includes the count, sum, min, max, and mean of all values recorded
     * within the configured time window up to the current time. Buckets that are
     * partially or fully outside the time window are excluded from the calculation.
     *
     * @return a {@link Snapshot} of the current statistics.
     */
    public Snapshot snapshot() {
        long now = clock.millis();
        long cutoff = now - windowMillis;

        long totalCount = 0L;
        double totalSum = 0.0;
        double totalMin = Double.POSITIVE_INFINITY;
        double totalMax = Double.NEGATIVE_INFINITY;

        for (Bucket bucket : buckets) {
            long bucketStart = bucket.windowStartMillis;
            if (bucketStart < cutoff) {
                continue;
            }

            long c = bucket.count.sum();
            if (c == 0L) {
                continue;
            }

            double s = bucket.sum.sum();
            double bMin = bucket.min();
            double bMax = bucket.max();

            totalCount += c;
            totalSum += s;
            if (bMin < totalMin) {
                totalMin = bMin;
            }
            if (bMax > totalMax) {
                totalMax = bMax;
            }
        }

        return new Snapshot(totalCount, totalSum, totalMin, totalMax);
    }

    private long alignMillisToBucketStart(long timestampMillis) {
        return (timestampMillis / bucketSizeMillis) * bucketSizeMillis;
    }

    private int calculateBucketIndex(long timestampMillis) {
        long bucketNumber = timestampMillis / bucketSizeMillis;
        int bound = buckets.length;
        long index = bucketNumber % bound;
        return (int) (index < 0 ? index + bound : index);
    }
}
