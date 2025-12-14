package io.github.palette4j.util;

/**
 * Defines a canonical set of identifier types used for request correlation, tracing,
 * and operational context propagation.
 * <p>
 * Each enum constant represents a well-known identifier (e.g. request id, correlation id,
 * trace id) and provides two standardized representations:
 * </p>
 * <ul>
 *   <li><b>Plain name</b> – a stable, code-friendly key (e.g. {@code requestId}).</li>
 *   <li><b>Header name</b> – a stable HTTP header key (e.g. {@code X-Request-Id}).</li>
 * </ul>
 *
 * <h2>State</h2>
 * <ul>
 *   <li><b>Plain key</b> – lower camel-case identifier suitable for maps, DTOs and MDC fields.</li>
 *   <li><b>Header key</b> – HTTP header representation suitable for request/response propagation.</li>
 * </ul>
 *
 * <h2>Behavior</h2>
 * <p>
 * This enum is a pure metadata container and does not generate identifiers by itself.
 * It is typically used together with helper utilities (e.g. {@code IdXUtils}) to:
 * </p>
 * <ul>
 *   <li>Normalize and resolve identifier aliases from incoming sources</li>
 *   <li>Extract identifiers from maps/headers in a consistent way</li>
 *   <li>Write identifiers back using a chosen key format</li>
 * </ul>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * This enum is thread-safe and immutable.
 * </p>
 *
 * <h2>Design intent</h2>
 * <p>
 * {@code IdX} exists to provide a single source of truth for identifier naming,
 * reducing duplication and preventing drift between:
 * </p>
 * <ul>
 *   <li>internal representations (plain keys)</li>
 *   <li>external representations (HTTP headers)</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * String headerName = IdX.REQUEST_ID.header(); // "X-Request-Id"
 * String plainName  = IdX.REQUEST_ID.plain();  // "requestId"
 * }</pre>
 */
public enum IdX {
    REQUEST_ID("requestId", "X-Request-Id"),
    CORRELATION_ID("correlationId", "X-Correlation-Id"),
    TRACE_ID("traceId", "X-Trace-Id"),
    SPAN_ID("spanId", "X-Span-Id"),
    TRANSACTION_ID("transactionId", "X-Transaction-Id"),
    USER_ID("userId", "X-User-Id"),
    TENANT_ID("tenantId", "X-Tenant-Id"),
    OPERATION_ID("operationId", "X-Operation-Id"),
    PARENT_ID("parentId", "X-Parent-Id"),
    MESSAGE_ID("messageId", "X-Message-Id"),
    EVENT_ID("eventId", "X-Event-Id"),
    CLIENT_ID("clientId", "X-Client-Id"),
    DEVICE_ID("deviceId", "X-Device-Id"),
    APPLICATION_ID("applicationId", "X-Application-Id")
    ;


    IdX(String plain, String header) {
        this.plain = plain;
        this.header = header;
    }

    private final String plain;
    private final String header;

    public String plain() {
        return plain;
    }
    public String header() {
        return header;
    }
}
