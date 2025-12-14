import io.github.palette4j.util.Pair;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PairTest {

    @DisplayName("of() --- creates a Pair with non-null elements")
    @Test
    void of_createsPairWithNonNullElements() {
        Pair<String, Integer> p = Pair.of("a", 1);

        assertEquals("a", p.getFirst());
        assertEquals(1, p.getSecond());
    }

    @DisplayName("of() --- throws when first is null")
    @Test
    void of_throwsWhenFirstIsNull() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> Pair.of(null, "x"));
        assertTrue(ex.getMessage().toLowerCase().contains("first"));
    }

    @DisplayName("of() --- throws when second is null")
    @Test
    void of_throwsWhenSecondIsNull() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> Pair.of("x", null));
        assertTrue(ex.getMessage().toLowerCase().contains("second"));
    }

    @DisplayName("fromMap() --- returns empty list when map is null")
    @Test
    void fromMap_returnsEmptyList_whenMapIsNull() {
        List<Pair<String, Integer>> pairs = Pair.fromMap((Map<String, Integer>) null);
        assertNotNull(pairs);
        assertTrue(pairs.isEmpty());
    }

    @DisplayName("fromMap() --- returns empty list when map is empty")
    @Test
    void fromMap_returnsEmptyList_whenMapIsEmpty() {
        List<Pair<String, Integer>> pairs = Pair.fromMap(Collections.emptyMap());
        assertNotNull(pairs);
        assertTrue(pairs.isEmpty());
    }

    @DisplayName("fromMap() --- skips entries with null key or null value")
    @Test
    void fromMap_skipsEntriesWithNullKeyOrNullValue() {
        Map<String, Integer> map = new HashMap<>();
        map.put("a", 1);
        map.put(null, 2);
        map.put("c", null);

        List<Pair<String, Integer>> pairs = Pair.fromMap(map);

        assertEquals(1, pairs.size());
        assertEquals(Pair.of("a", 1), pairs.get(0));
    }

    @DisplayName("fromMap() --- throws exception when mapper bi function is null")
    @Test
    void fromMap_withMapper_throwsWhenMapperIsNull() {
        Map<String, Integer> map = new HashMap<>();
        map.put("a", 1);

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> Pair.fromMap(map, null));

        assertTrue(ex.getMessage().toLowerCase().contains("mapper"));
    }

    @DisplayName("fromMap() with valid mapper --- returns empty list if input mpa is null or empty")
    @Test
    void fromMap_withMapper_returnsEmptyList_whenMapIsNullOrEmpty() {
        BiFunction<String, Integer, Pair<String, String>> mapper =
                (k, v) -> Pair.of(k, "v=" + v);

        assertTrue(Pair.fromMap(null, mapper).isEmpty());
        assertTrue(Pair.fromMap(Collections.emptyMap(), mapper).isEmpty());
    }

    @DisplayName("fromMap() with valid mapper --- return non-empty list for non-empty input map")
    @Test
    void fromMap_withMapper_appliesMapperForNonNullEntries() {
        Map<String, Integer> map = new HashMap<>();
        map.put("a", 1);
        map.put("b", 2);
        List<Pair<String, String>> pairs =
                Pair.fromMap(map, (k, v) -> Pair.of(k.toUpperCase(), "v=" + v));

        assertEquals(2, pairs.size());
        assertTrue(pairs.contains(Pair.of("A", "v=1")));
        assertTrue(pairs.contains(Pair.of("B", "v=2")));
    }

    @DisplayName("fromMap() with valid mapper --- skip null keys and/or values during creating list of pairs")
    @Test
    void fromMap_withMapper_skipsEntriesWithNullKeyOrNullValue() {
        Map<String, Integer> map = new HashMap<>();
        map.put("a", 1);
        map.put(null, 2);
        map.put("c", null);

        List<Pair<String, String>> pairs =
                Pair.fromMap(map, (k, v) -> Pair.of(k, "v=" + v));

        assertEquals(1, pairs.size());
        assertEquals(Pair.of("a", "v=1"), pairs.get(0));
    }

    @DisplayName("fromMap() with valid mapper --- handles exceptions thrown by mapper")
    @Test
    void fromMap_withMapper_skipsEntryWhenMapperThrows() {
        Map<String, Integer> map = new HashMap<>();
        map.put("ok", 1);
        map.put("boom", 2);

        List<Pair<String, Integer>> pairs =
                Pair.fromMap(map, (k, v) -> {
                    if ("boom".equals(k)) throw new RuntimeException("fail");
                    return Pair.of(k, v);
                });

        assertEquals(1, pairs.size());
        assertEquals(Pair.of("ok", 1), pairs.get(0));
    }

    @DisplayName("fromMap() with valid mapper --- does not swallow null pair returned by mapper and doesn't add it to list")
    @Test
    void fromMap_withMapper_doesNotSwallowNullPairReturnedByMapper_itIsNotAddedAsNull() {
        Map<String, Integer> map = new HashMap<>();
        map.put("a", 1);

        List<Pair<String, Integer>> pairs =
                Pair.fromMap(map, (k, v) -> null);

        assertEquals(0, pairs.size());
    }

    @DisplayName("map() --- throws exception on null mapper function")
    @Test
    void map_throwsWhenMapperIsNull() {
        Pair<String, Integer> p = Pair.of("a", 1);

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> p.map(null));

        assertTrue(ex.getMessage().toLowerCase().contains("mapper"));
    }

    @DisplayName("map() --- applies mapper function to elements")
    @Test
    void map_appliesMapperToElements() {
        Pair<String, Integer> p = Pair.of("alice", 7);

        Pair<String, String> mapped =
                p.map((name, id) -> Pair.of(name.toUpperCase(), "ID-" + id));

        assertEquals(Pair.of("ALICE", "ID-7"), mapped);
    }

    @DisplayName("map() --- propagates exceptions from mapper")
    @Test
    void map_propagatesExceptionsFromMapper() {
        Pair<String, Integer> p = Pair.of("a", 1);

        RuntimeException ex =
                assertThrows(RuntimeException.class, () -> p.map((f, s) -> { throw new RuntimeException("boom"); }));

        assertEquals("boom", ex.getMessage());
    }


    @DisplayName("swap() --- throws exception on null other pair")
    @Test
    void swap_throwsWhenOtherIsNull() {
        Pair<String, Integer> p = Pair.of("a", 1);

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> p.swap(null));

        assertTrue(ex.getMessage().toLowerCase().contains("swapping"));
    }

    @DisplayName("swap() --- swaps elements with other pair")
    @Test
    void swap_returnsNewPairWithElementsSwapped_fromOtherPair() {
        Pair<String, Integer> other = Pair.of("x", 10);
        Pair<String, Integer> self = Pair.of("ignored", 999);

        Pair<Integer, String> swapped = self.swap(other);

        assertEquals(Pair.of(10, "x"), swapped);
    }

    @DisplayName("swap() --- calls mapper only for non-null entries")
    @Test
    void fromMap_withMapper_callsMapperOnlyForNonNullEntries() {
        Map<String, Integer> map = new HashMap<>();
        map.put("a", 1);
        map.put(null, 2);
        map.put("c", null);

        AtomicInteger calls = new AtomicInteger();

        List<Pair<String, Integer>> pairs =
                Pair.fromMap(map, (k, v) -> {
                    calls.incrementAndGet();
                    return Pair.of(k, v);
                });

        assertEquals(1, calls.get(), "Mapper must be called only for entries with non-null key and value");
        assertEquals(1, pairs.size());
        assertEquals(Pair.of("a", 1), pairs.get(0));
    }
}
