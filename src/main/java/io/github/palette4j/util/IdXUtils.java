package io.github.palette4j.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import static io.github.palette4j.util.EntropyGenerator.HEX_16;
import static io.github.palette4j.util.EntropyGenerator.HEX_32;
import static io.github.palette4j.util.EntropyGenerator.NANO_ID;
import static io.github.palette4j.util.EntropyGenerator.ULID;
import static io.github.palette4j.util.EntropyGenerator.UUID;


/**
 * Provides utility functions for generating, resolving, extracting, and injecting
 * well-known identifiers defined by {@link IdX}.
 * <p>
 * This class centralizes the logic required to work with identifiers across different
 * representations and sources (e.g. HTTP headers, internal maps, MDC fields), including:
 * </p>
 * <ul>
 *   <li>Generation of identifiers using {@link EntropyGenerator} strategies</li>
 *   <li>Alias-based resolution of identifier types from arbitrary keys</li>
 *   <li>Extraction of identifiers from maps with normalization and case-insensitivity</li>
 *   <li>Injection of identifiers into maps using either plain or header key formats</li>
 * </ul>
 *
 * <h2>State</h2>
 * <ul>
 *   <li><b>Alias registry</b> – a static mapping from normalized aliases to {@link IdX} types.</li>
 *   <li><b>Normalization pattern</b> – removes separators such as hyphens, underscores and whitespace
 *       to enable tolerant key matching.</li>
 *   <li><b>Key format transforms</b> – functions producing either header keys or plain keys.</li>
 * </ul>
 *
 * <h2>Normalization and aliasing</h2>
 * <p>
 * Key matching is tolerant and case-insensitive. Input keys are normalized by:
 * </p>
 * <ul>
 *   <li>trimming</li>
 *   <li>removing {@code -}, {@code _}, and whitespace</li>
 *   <li>lowercasing using {@link Locale#ROOT}</li>
 * </ul>
 * <p>
 * After normalization, keys are resolved through an internal registry that maps
 * multiple aliases (e.g. {@code requestId}, {@code x-request-id}) to the same {@link IdX} type.
 * </p>
 *
 * <h2>Identifier generation</h2>
 * <p>
 * Identifiers may be generated either by specifying an {@link EntropyGenerator} directly or
 * by specifying an {@link IdX} type:
 * </p>
 * <ul>
 *   <li>{@link #generate(EntropyGenerator)} – uses the provided generator or defaults to UUID.</li>
 *   <li>{@link #generate(IdX)} – selects a generator based on the identifier type
 *       (e.g. trace/span hex, request ULID, message Nano ID).</li>
 * </ul>
 *
 * <h2>Map extraction</h2>
 * <p>
 * Extraction methods scan an input map and resolve entries by matching keys against the alias registry:
 * </p>
 * <ul>
 *   <li>{@link #getFromMap(IdX, Map)} – finds the first value matching the requested identifier type.</li>
 *   <li>{@link #getAllFromMapAsPlain(Map)} – returns all recognized identifiers using plain keys.</li>
 *   <li>{@link #getAllFromMapAsHeaders(Map)} – returns all recognized identifiers using header keys.</li>
 * </ul>
 * <p>
 * Unknown keys are ignored. If the input is {@code null} or empty, extraction returns
 * {@code null} (single value) or an empty map (bulk extraction).
 * </p>
 *
 * <h2>Map injection</h2>
 * <p>
 * Injection methods write identifier values into a target map either:
 * </p>
 * <ul>
 *   <li><b>conditionally</b> – using {@code putIfAbsent...} methods which do not overwrite existing keys</li>
 *   <li><b>unconditionally</b> – using {@code put...} methods which overwrite existing values</li>
 * </ul>
 * <p>
 * Keys written to the target map can be normalized to either plain or header form.
 * Injection skips invalid inputs (blank keys, blank values, unknown identifier keys).
 * </p>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * This class is thread-safe for typical usage. The alias registry is populated during
 * class initialization and then treated as read-only thereafter.
 * </p>
 * <p>
 * Note: The underlying registry is a mutable {@link HashMap}. It is safe in practice
 * because it is only written during static initialization; callers should not attempt to mutate it.
 * </p>
 *
 * <h2>Design intent</h2>
 * <p>
 * {@code IdXUtils} exists to:
 * </p>
 * <ul>
 *   <li>Prevent duplication of ID propagation logic across filters/interceptors/services</li>
 *   <li>Make identifier extraction robust against differing key formats</li>
 *   <li>Centralize generation policies per identifier type</li>
 *   <li>Provide a consistent bridge between internal keys and external header names</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Map<String, String> headers = Map.of(
 *     "X-Request-Id", "req-123",
 *     "x-trace-id", "abcd..."
 * );
 *
 * // Extract known IDs as plain keys
 * Map<String, String> ids = IdXUtils.getAllFromMapAsPlain(headers);
 *
 * // Ensure correlation id exists as header key
 * Map<String, String> out = new HashMap<>(headers);
 * IdXUtils.putIfAbsentToMapAsHeader(IdX.CORRELATION_ID, out);
 * }</pre>
 */
public class IdXUtils {

    private static final Map<String, IdX> IDX_ALIAS_REGISTRY;
    private static final Pattern NORMALIZE_PATTERN = Pattern.compile("[-_\\s]+");
    private static final Function<IdX, String> AS_HEADER = IdX::header;
    private static final Function<IdX, String> AS_PLAIN = IdX::plain;

    private IdXUtils() {
    }

    static {
        Map<String, IdX> idxMap = new HashMap<>();

        registerAliasForType(idxMap, IdX.REQUEST_ID, "requestId", "x-request-id", "X-Request-Id");
        registerAliasForType(idxMap, IdX.CORRELATION_ID, "correlationId", "x-correlation-id", "X-Correlation-Id");
        registerAliasForType(idxMap, IdX.TRACE_ID, "traceId", "x-trace-id", "X-Trace-Id");
        registerAliasForType(idxMap, IdX.SPAN_ID, "spanId", "x-span-id", "X-Span-Id");
        registerAliasForType(idxMap, IdX.TRANSACTION_ID, "transactionId", "x-transaction-id", "X-Transaction-Id");
        registerAliasForType(idxMap, IdX.USER_ID, "userId", "x-user-id", "X-User-Id");
        registerAliasForType(idxMap, IdX.TENANT_ID, "tenantId", "x-tenant-id", "X-Tenant-Id");
        registerAliasForType(idxMap, IdX.OPERATION_ID, "operationId", "x-operation-id", "X-Operation-Id");
        registerAliasForType(idxMap, IdX.PARENT_ID, "parentId", "x-parent-id", "X-Parent-Id");
        registerAliasForType(idxMap, IdX.MESSAGE_ID, "messageId", "x-message-id", "X-Message-Id");
        registerAliasForType(idxMap, IdX.EVENT_ID, "eventId", "x-event-id", "X-Event-Id");
        registerAliasForType(idxMap, IdX.CLIENT_ID, "clientId", "x-client-id", "X-Client-Id");
        registerAliasForType(idxMap, IdX.DEVICE_ID, "deviceId", "x-device-id", "X-Device-Id");
        registerAliasForType(idxMap, IdX.APPLICATION_ID, "applicationId", "x-application-id", "X-Application-Id");

        IDX_ALIAS_REGISTRY = java.util.Collections.unmodifiableMap(idxMap);
    }

    /**
     * Generates a unique identifier string using the specified {@link EntropyGenerator}.
     * If the provided generator is {@code null}, this method defaults to generating a standard UUID.
     *
     * @param generator the {@link EntropyGenerator} to use for creating the identifier.
     *                  If {@code null}, {@link EntropyGenerator#UUID} will be used as a default.
     * @return a newly generated unique identifier as a {@link String}.
     */
    public static String generate(EntropyGenerator generator) {
        return (generator == null) ? UUID.generate() : generator.generate();
    }

    /**
     * Generates a unique identifier string tailored to the specified {@link IdX} type.
     * <p>
     * The generation mechanism is determined by the provided {@code type}, ensuring that
     * the generated ID conforms to the expected format for that specific identifier type.
     * For example, a {@link IdX#TRACE_ID} might generate a 32-character hex string, while
     * a {@link IdX#REQUEST_ID} might generate a ULID. If the type does not have a specific
     * generator, it defaults to generating a standard UUID.
     *
     * @param type the {@link IdX} type that specifies the kind of identifier to generate.
     * @return a newly generated unique identifier as a {@link String}, formatted according
     * to the conventions of the specified {@code type}.
     */
    public static String generate(IdX type) {
        return generateByType(type);
    }

    /**
     * Retrieves the value for a specific {@link IdX} type from a source map.
     * <p>
     * This method searches the provided map for a key that matches any of the registered aliases
     * for the given {@code type}. For example, if {@code type} is {@link IdX#REQUEST_ID}, it will
     * look for keys like "requestId" or "x-request-id". The search is case-insensitive and
     * ignores hyphens, underscores, and whitespace in the keys.
     *
     * @param type   the {@link IdX} type of the identifier to retrieve.
     * @param source the map from which to extract the identifier value.
     * @return the corresponding identifier value as a {@link String}, or {@code null} if the
     * map is {@code null}, empty, or no matching key is found.
     */
    public static String getFromMap(IdX type, Map<String, String> source) {
        return extractIdxValueFromMapping(type, source);
    }

    /**
     * Extracts all recognized identifier key-value pairs from a source map and returns them
     * in a new map with standardized plain keys.
     * <p>
     * This method iterates through the {@code source} map, identifies keys that correspond to
     * any registered {@link IdX} alias (e.g., "x-request-id", "requestId"), and copies the
     * corresponding entries into a new map. The keys in the new map are normalized to their
     * plain format (e.g., "requestId").
     *
     * @param source the map from which to extract identifier key-value pairs.
     * @return a new {@link Map} containing the recognized identifiers with plain keys.
     * Returns an empty map if the source is {@code null}, empty, or contains no
     * recognizable identifier keys.
     */
    public static Map<String, String> getAllFromMapAsPlain(Map<String, String> source) {
        return extractAllFromMapping(source, AS_PLAIN);
    }

    /**
     * Extracts all recognized identifier key-value pairs from a source map and returns them
     * in a new map with standardized header keys.
     * <p>
     * This method iterates through the {@code source} map, identifies keys that correspond to
     * any registered {@link IdX} alias (e.g., "x-request-id", "requestId"), and copies the
     * corresponding entries into a new map. The keys in the new map are normalized to their
     * header format (e.g., "x-request-id").
     *
     * @param source the map from which to extract identifier key-value pairs.
     * @return a new {@link Map} containing the recognized identifiers with header keys.
     * Returns an empty map if the source is {@code null}, empty, or contains no
     * recognizable identifier keys.
     */
    public static Map<String, String> getAllFromMapAsHeaders(Map<String, String> source) {
        return extractAllFromMapping(source, AS_HEADER);
    }

    /**
     * Generates and adds a new identifier to a map using a plain key if the key is not already present.
     * <p>
     * This method first generates a new identifier appropriate for the given {@link IdX} type.
     * It then attempts to add this identifier to the {@code target} map using the plain format
     * of the identifier's key (e.g., "requestId"). The entry is only added if no key corresponding
     * to the specified {@link IdX} type already exists in the map.
     *
     * @param type   the {@link IdX} type for which to generate and add an identifier.
     * @param target the map into which the new identifier will be placed if absent.
     */
    public static void putIfAbsentToMapAsPlain(IdX type, Map<String, String> target) {
        injectIntoMapping(type, generateByType(type), target, AS_PLAIN, false);
    }

    /**
     * Generates and adds a new identifier to a map using a header key if the key is not already present.
     * <p>
     * This method first generates a new identifier appropriate for the given {@link IdX} type.
     * It then attempts to add this identifier to the {@code target} map using the header format
     * of the identifier's key (e.g., "x-request-id"). The entry is only added if no key corresponding
     * to the specified {@link IdX} type already exists in the map.
     *
     * @param type   the {@link IdX} type for which to generate and add an identifier.
     * @param target the map into which the new identifier will be placed if absent.
     */
    public static void putIfAbsentToMapAsHeader(IdX type, Map<String, String> target) {
        injectIntoMapping(type, generateByType(type), target, AS_HEADER, false);
    }

    /**
     * Adds a given identifier value to a map using a plain key if the key is not already present.
     * <p>
     * This method attempts to add the provided {@code value} to the {@code target} map. The key
     * used is the plain format of the identifier's type (e.g., "requestId"). The entry is only
     * added if no key corresponding to the specified {@link IdX} type already exists in the map.
     *
     * @param type   the {@link IdX} type of the identifier, which determines the key.
     * @param value  the identifier value to be added to the map.
     * @param target the map into which the new identifier will be placed if absent.
     */
    public static void putIfAbsentToMapAsHeader(IdX type, String value, Map<String, String> target) {
        injectIntoMapping(type, value, target, AS_HEADER, false);
    }

    /**
     * Copies all recognized identifier key-value pairs from a source map to a target map,
     * using plain keys, if they are not already present.
     * <p>
     * This method iterates through the {@code source} map, identifies keys that correspond to
     * any registered {@link IdX} alias, and adds the corresponding entries to the {@code target}
     * map. The keys in the {@code target} map are normalized to their plain format (e.g., "requestId").
     * An entry is only added if no key corresponding to that {@link IdX} type already exists in the target.
     *
     * @param source the map from which to extract identifier key-value pairs.
     * @param target the map into which the new identifiers will be placed if absent.
     * @return the modified {@code target} map.
     */
    public static Map<String, String> putAllIfAbsentToMapAsPlain(Map<String, String> source, Map<String, String> target) {
        return injectAllIntoMapping(source, target, AS_PLAIN, false);
    }

    /**
     * Copies all recognized identifier key-value pairs from a source map to a target map,
     * using header keys, if they are not already present.
     * <p>
     * This method iterates through the {@code source} map, identifies keys that correspond to
     * any registered {@link IdX} alias, and adds the corresponding entries to the {@code target}
     * map. The keys in the {@code target} map are normalized to their header format (e.g., "x-request-id").
     * An entry is only added if no key corresponding to that {@link IdX} type already exists in the target.
     *
     * @param source the map from which to extract identifier key-value pairs.
     * @param target the map into which the new identifiers will be placed if absent.
     * @return the modified {@code target} map.
     */
    public static Map<String, String> putAllIfAbsentToMapAsHeaders(Map<String, String> source, Map<String, String> target) {
        return injectAllIntoMapping(source, target, AS_HEADER, false);
    }

    /**
     * Generates and adds a new identifier to a map using a plain key, overwriting any existing value.
     * <p>
     * This method generates a new identifier appropriate for the given {@link IdX} type and
     * unconditionally places it into the {@code target} map. The key used is the plain format
     * of the identifier's type (e.g., "requestId"). If a key corresponding to the specified
     * {@link IdX} type already exists, its value will be replaced.
     *
     * @param type   the {@link IdX} type for which to generate and add an identifier.
     * @param target the map into which the new identifier will be placed.
     */
    public static void putToMapAsPlain(IdX type, Map<String, String> target) {
        injectIntoMapping(type, generateByType(type), target, AS_PLAIN, true);
    }

    /**
     * Generates and adds a new identifier to a map using a header key, overwriting any existing value.
     * <p>
     * This method generates a new identifier appropriate for the given {@link IdX} type and
     * unconditionally places it into the {@code target} map. The key used is the header format
     * of the identifier's type (e.g., "x-request-id"). If a key corresponding to the specified
     * {@link IdX} type already exists, its value will be replaced.
     *
     * @param type   the {@link IdX} type for which to generate and add an identifier.
     * @param target the map into which the new identifier will be placed.
     */
    public static void putToMapAsHeader(IdX type, Map<String, String> target) {
        injectIntoMapping(type, generateByType(type), target, AS_HEADER, true);
    }

    /**
     * Adds a given identifier value to a map using a plain key, overwriting any existing value.
     * <p>
     * This method unconditionally places the provided {@code value} into the {@code target} map.
     * The key used is the plain format of the identifier's type (e.g., "requestId"). If a key
     * corresponding to the specified {@link IdX} type already exists, its value will be replaced.
     *
     * @param type   the {@link IdX} type of the identifier, which determines the key.
     * @param value  the identifier value to be added to the map.
     * @param target the map into which the new identifier will be placed.
     */
    public static void putToMapAsPlain(IdX type, String value, Map<String, String> target) {
        injectIntoMapping(type, value, target, AS_PLAIN, true);
    }

    /**
     * Adds a given identifier value to a map using a header key, overwriting any existing value.
     * <p>
     * This method unconditionally places the provided {@code value} into the {@code target} map.
     * The key used is the header format of the identifier's type (e.g., "x-request-id"). If a key
     * corresponding to the specified {@link IdX} type already exists, its value will be replaced.
     *
     * @param type   the {@link IdX} type of the identifier, which determines the key.
     * @param value  the identifier value to be added to the map.
     * @param target the map into which the new identifier will be placed.
     */
    public static void putToMapAsHeader(IdX type, String value, Map<String, String> target) {
        injectIntoMapping(type, value, target, AS_HEADER, true);
    }

    /**
     * Copies all recognized identifier key-value pairs from a source map to a target map,
     * using plain keys, and overwrites any existing values.
     * <p>
     * This method iterates through the {@code source} map, identifies keys that correspond to
     * any registered {@link IdX} alias, and adds the corresponding entries to the {@code target}
     * map. The keys in the {@code target} map are normalized to their plain format (e.g., "requestId").
     * If a key corresponding to an {@link IdX} type already exists in the target, its value will be replaced.
     *
     * @param source the map from which to extract identifier key-value pairs.
     * @param target the map into which the new identifiers will be placed.
     * @return the modified {@code target} map.
     */
    public static Map<String, String> putAllToMapAsPlain(Map<String, String> source, Map<String, String> target) {
        return injectAllIntoMapping(source, target, AS_PLAIN, true);
    }

    /**
     * Copies all recognized identifier key-value pairs from a source map to a target map,
     * using header keys, and overwrites any existing values.
     * <p>
     * This method iterates through the {@code source} map, identifies keys that correspond to
     * any registered {@link IdX} alias, and adds the corresponding entries to the {@code target}
     * map. The keys in the {@code target} map are normalized to their header format (e.g., "x-request-id").
     * If a key corresponding to an {@link IdX} type already exists in the target, its value will be replaced.
     *
     * @param source the map from which to extract identifier key-value pairs.
     * @param target the map into which the new identifiers will be placed.
     * @return the modified {@code target} map.
     */
    public static Map<String, String> putAllToMapAsHeaders(Map<String, String> source, Map<String, String> target) {
        return injectAllIntoMapping(source, target, AS_HEADER, true);
    }


    private static Map<String, String> injectAllIntoMapping(Map<String, String> source, Map<String, String> target, Function<IdX, String> transform, boolean overwriteExisting) {
        if (target == null) {
            throw new IllegalArgumentException("target map must not be null");
        }

        if (source == null || source.isEmpty()) {
            return target;
        }

        for (Map.Entry<String, String> entry : source.entrySet()) {
            injectIntoMapping(entry.getKey(), entry.getValue(), target, transform, overwriteExisting);
        }

        return target;
    }

    private static void injectIntoMapping(IdX type, String value, Map<String, String> target, Function<IdX, String> transform, boolean overwriteExisting) {
        if (type == null) {
            return;
        }
        injectIntoMapping(type.plain(), value, target, transform, overwriteExisting);
    }

    private static void injectIntoMapping(String key, String value, Map<String, String> target, Function<IdX, String> transform, boolean overwriteExisting) {
        if (target == null) {
            return;
        }

        if (key == null || key.trim().isEmpty() || value == null || value.trim().isEmpty()) {
            return;
        }

        IdX idX = resolveIdxType(key);
        if (idX == null) {
            return;
        }

        String transformedKey = transform.apply(idX);

        if (overwriteExisting) {
            target.put(transformedKey, value);
        } else {
            target.putIfAbsent(transformedKey, value);
        }
    }

    private static Map<String, String> extractAllFromMapping(Map<String, String> source, Function<IdX, String> transform) {
        Map<String, String> target = new HashMap<>();
        if (source == null || source.isEmpty()) {
            return target;
        }

        for (Map.Entry<String, String> entry : source.entrySet()) {
            IdX idX = resolveIdxType(entry.getKey());
            if (idX != null) {
                target.put(transform.apply(idX), entry.getValue());
            }
        }

        return target;
    }

    private static String extractIdxValueFromMapping(IdX type, Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }

        for (Map.Entry<String, String> entry : source.entrySet()) {
            IdX idX = resolveIdxType(entry.getKey());
            if (idX == type) {
                return entry.getValue();
            }
        }

        return null;
    }

    private static IdX resolveIdxType(String str) {
        if (str == null || str.trim().isEmpty()) {
            return null;
        }
        return IDX_ALIAS_REGISTRY.get(normalize(str));
    }

    private static void registerAliasForType(Map<String, IdX> registry, IdX type, String... aliases) {
        for (String alias : aliases) {
            registry.putIfAbsent(normalize(alias), type);
        }
    }

    private static String normalize(String str) {
        return (str != null && !str.trim().isEmpty())
                ? NORMALIZE_PATTERN.matcher(str.trim()).replaceAll("").toLowerCase(Locale.ROOT)
                : "";
    }

    private static String generateByType(IdX type) {
        switch (type) {
            case TRACE_ID:
                return HEX_32.generate();

            case SPAN_ID:
            case PARENT_ID:
                return HEX_16.generate();

            case REQUEST_ID:
            case TRANSACTION_ID:
            case OPERATION_ID:
            case EVENT_ID:
                return ULID.generate();

            case CORRELATION_ID:
            case MESSAGE_ID:
                return NANO_ID.generate();

            default:
                return UUID.generate();
        }
    }
}
