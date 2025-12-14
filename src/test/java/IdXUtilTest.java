import io.github.palette4j.util.EntropyGenerator;
import io.github.palette4j.util.IdX;
import io.github.palette4j.util.IdXUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IdXUtilTest {


    private static boolean isLowerHex(String s) {
        return s != null && s.matches("^[0-9a-f]+$");
    }

    private static boolean isNanoId(String s) {
        return s != null && s.matches("^[0-9a-zA-Z]+$");
    }

    private static boolean isUlid(String s) {
        return s != null && s.matches("^[0-9A-HJKMNP-TV-Z]{26}$");
    }


    @DisplayName("getFromMap() --- resolves aliases in case insensitive and separator insensitive")
    @Test
    void getFromMap_resolvesAliases_caseInsensitive_andSeparatorInsensitive() {
        Map<String, String> src = new HashMap<>();
        src.put("X-Request-Id", "r1");
        src.put("x_trace_id", "t1");
        src.put("  x span id  ", "s1");

        assertEquals("r1", IdXUtils.getFromMap(IdX.REQUEST_ID, src));
        assertEquals("t1", IdXUtils.getFromMap(IdX.TRACE_ID, src));
        assertEquals("s1", IdXUtils.getFromMap(IdX.SPAN_ID, src));
    }

    @DisplayName("getFromMap() --- returns null when source map is null or empty")
    @Test
    void getFromMap_returnsNull_whenSourceNullOrEmpty() {
        assertNull(IdXUtils.getFromMap(IdX.REQUEST_ID, null));
        assertNull(IdXUtils.getFromMap(IdX.REQUEST_ID, Collections.emptyMap()));
    }

    @DisplayName("getAllFromMapAsPlain() --- returns recognized ids with plain keys")
    @Test
    void getAllFromMapAsPlain_returnsRecognizedIdsWithPlainKeys() {
        Map<String, String> src = new HashMap<>();
        src.put("X-Request-Id", "r1");
        src.put("x-trace-id", "t1");
        src.put("unrelated", "zzz");

        Map<String, String> out = IdXUtils.getAllFromMapAsPlain(src);

        assertEquals("r1", out.get("requestId"));
        assertEquals("t1", out.get("traceId"));
        assertFalse(out.containsKey("unrelated"));
        assertEquals(2, out.size());
    }

    @DisplayName("getAllFromMapAsHeaders() --- returns recognized ids with header keys")
    @Test
    void getAllFromMapAsHeaders_returnsRecognizedIdsWithHeaderKeys() {
        Map<String, String> src = new HashMap<>();
        src.put("requestId", "r1");
        src.put("traceId", "t1");

        Map<String, String> out = IdXUtils.getAllFromMapAsHeaders(src);

        assertEquals("r1", out.get("X-Request-Id"));
        assertEquals("t1", out.get("X-Trace-Id"));
        assertEquals(2, out.size());
    }

    @DisplayName("getAllFromMap() --- returns empty map when source map is null or empty")
    @Test
    void getAllFromMap_returnsEmptyMap_whenSourceNullOrEmpty() {
        assertTrue(IdXUtils.getAllFromMapAsPlain(null).isEmpty());
        assertTrue(IdXUtils.getAllFromMapAsPlain(Collections.emptyMap()).isEmpty());

        assertTrue(IdXUtils.getAllFromMapAsHeaders(null).isEmpty());
        assertTrue(IdXUtils.getAllFromMapAsHeaders(Collections.emptyMap()).isEmpty());
    }

    @DisplayName("putIfAbsentToMapAsPlain() --- does not overwrite existing values")
    @Test
    void putIfAbsentToMapAsPlain_doesNotOverwriteExistingValue() {
        Map<String, String> target = new HashMap<>();
        target.put("requestId", "existing");

        IdXUtils.putIfAbsentToMapAsPlain(IdX.REQUEST_ID, target);

        assertEquals("existing", target.get("requestId"));
    }

    @DisplayName("putToMapAsPlain() --- overwrites existing values")
    @Test
    void putToMapAsPlain_overwritesExistingValue() {
        Map<String, String> target = new HashMap<>();
        target.put("requestId", "existing");

        IdXUtils.putToMapAsPlain(IdX.REQUEST_ID, target);

        assertNotNull(target.get("requestId"));
        assertNotEquals("existing", target.get("requestId"));
    }

    @DisplayName("putIfAbsentToMapAsHeader() --- does not overwrite existing values")
    @Test
    void putIfAbsentToMapAsHeader_doesNotOverwriteExistingValue() {
        Map<String, String> target = new HashMap<>();
        target.put("X-Request-Id", "existing");

        IdXUtils.putIfAbsentToMapAsHeader(IdX.REQUEST_ID, target);

        assertEquals("existing", target.get("X-Request-Id"));
    }

    @DisplayName("putToMapAsHeader() --- overwrites existing values")
    @Test
    void putToMapAsHeader_overwritesExistingValue() {
        Map<String, String> target = new HashMap<>();
        target.put("X-Request-Id", "existing");

        IdXUtils.putToMapAsHeader(IdX.REQUEST_ID, target);

        assertNotNull(target.get("X-Request-Id"));
        assertNotEquals("existing", target.get("X-Request-Id"));
    }

    @DisplayName("putToMapAsPlain() with provided value --- uses plain key and overwrites value")
    @Test
    void putToMapAsPlain_withProvidedValue_usesPlainKey_andOverwrites() {
        Map<String, String> target = new HashMap<>();
        target.put("requestId", "old");

        IdXUtils.putToMapAsPlain(IdX.REQUEST_ID, "new", target);

        assertEquals("new", target.get("requestId"));
    }

    @DisplayName("putToMapAsHeader() with provided value --- uses header key and overwrites value")
    @Test
    void putToMapAsHeader_withProvidedValue_usesHeaderKey_andOverwrites() {
        Map<String, String> target = new HashMap<>();
        target.put("X-Request-Id", "old");

        IdXUtils.putToMapAsHeader(IdX.REQUEST_ID, "new", target);

        assertEquals("new", target.get("X-Request-Id"));
    }

    @DisplayName("putAllIfAbsentToMapAsPlain() --- inject recognized identifiers without overwriting the existing values")
    @Test
    void putAllIfAbsentToMapAsPlain_injectsRecognizedValues_withoutOverwriting() {
        Map<String, String> source = new HashMap<>();
        source.put("X-Request-Id", "r1");
        source.put("x-trace-id", "t1");

        Map<String, String> target = new HashMap<>();
        target.put("requestId", "existing");

        IdXUtils.putAllIfAbsentToMapAsPlain(source, target);

        assertEquals("existing", target.get("requestId"));
        assertEquals("t1", target.get("traceId"));
    }

    @DisplayName("putAllToMapAsHeaders() --- inject recognized identifiers and overwrites the existing values")
    @Test
    void putAllToMapAsHeaders_injectsRecognizedValues_andOverwrites() {
        Map<String, String> source = new HashMap<>();
        source.put("requestId", "r1");
        source.put("traceId", "t1");

        Map<String, String> target = new HashMap<>();
        target.put("X-Request-Id", "existing");

        IdXUtils.putAllToMapAsHeaders(source, target);

        assertEquals("r1", target.get("X-Request-Id")); // overwritten
        assertEquals("t1", target.get("X-Trace-Id"));
    }

    @DisplayName("putAll() --- throws exception when target map is null")
    @Test
    void putAll_throwsWhenTargetIsNull() {
        Map<String, String> source = new HashMap<>();
        source.put("requestId", "r1");

        assertThrows(IllegalArgumentException.class,
                () -> IdXUtils.putAllIfAbsentToMapAsPlain(source, null));

        assertThrows(IllegalArgumentException.class,
                () -> IdXUtils.putAllToMapAsHeaders(source, null));
    }

    @DisplayName("generate() --- generates default UUID format on null entropy generator")
    @Test
    void generate_withNullEntropyGenerator_defaultsToUuidFormat() {
        String id = IdXUtils.generate((EntropyGenerator) null);

        assertNotNull(id);
        assertEquals(36, id.length(), "UUID string form is 36 chars");
        assertTrue(id.contains("-"), "UUID must contain hyphens in standard form");
    }

    @DisplayName("generateByIdType() --- produces expected identifier string representations")
    @Test
    void generateByIdType_producesExpectedFormats() {
        String trace = IdXUtils.generate(IdX.TRACE_ID);
        assertEquals(32, trace.length());
        assertTrue(isLowerHex(trace));

        String span = IdXUtils.generate(IdX.SPAN_ID);
        assertEquals(16, span.length());
        assertTrue(isLowerHex(span));

        String parent = IdXUtils.generate(IdX.PARENT_ID);
        assertEquals(16, parent.length());
        assertTrue(isLowerHex(parent));

        Set<IdX> ulidTypes = new HashSet<>();
        ulidTypes.add(IdX.REQUEST_ID);
        ulidTypes.add(IdX.TRANSACTION_ID);
        ulidTypes.add(IdX.OPERATION_ID);
        ulidTypes.add(IdX.EVENT_ID);

        for (IdX t : ulidTypes) {
            String v = IdXUtils.generate(t);
            assertEquals(26, v.length(), t + " must be ULID-length");
            assertTrue(isUlid(v), t + " must match ULID alphabet");
        }

        Set<IdX> nanoTypes = new HashSet<>();
        nanoTypes.add(IdX.CORRELATION_ID);
        nanoTypes.add(IdX.MESSAGE_ID);

        for (IdX t : nanoTypes) {
            String v = IdXUtils.generate(t);
            assertEquals(21, v.length(), t + " must be NanoID-length");
            assertTrue(isNanoId(v), t + " must match NanoID alphabet");
        }

        String user = IdXUtils.generate(IdX.USER_ID);
        assertEquals(36, user.length());
        assertTrue(user.contains("-"));
    }
}
