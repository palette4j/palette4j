package io.github.palette4j.exception;

/**
 * Centralized catalog of application error codes used for representing
 * both domain-level and technical failures in a uniform, machine-readable way.
 * <p>
 * {@code ErrorCode} serves as the canonical contract between:
 * <ul>
 *     <li>Domain and application layers</li>
 *     <li>Exception hierarchy</li>
 *     <li>REST API error responses</li>
 *     <li>Logging, metrics, and observability pipelines</li>
 * </ul>
 *</p>
 *
 * <h2>HTTP Mapping</h2>
 * Each error code carries an associated HTTP status code intended for use by
 * REST controllers and global exception handlers (e.g. {@code @ControllerAdvice}).
 * This ensures consistent API responses across the application.
 *
 * <h2>Error Categories</h2>
 * Errors are grouped via {@link ErrorCategory} to enable:
 * <ul>
 *     <li>Structured logging and alerting</li>
 *     <li>Metrics aggregation (e.g. by category)</li>
 *     <li>Conditional handling (retry, escalation, circuit-breaking)</li>
 * </ul>
 *
 * <h2>Retry Semantics</h2>
 * The {@code retriable} flag indicates whether the failure is considered
 * transient and may be retried safely (e.g. integration timeouts, temporary
 * unavailability) versus deterministic failures (e.g. validation or business
 * rule violations).
 *
 * <h2>Design intent</h2>
 * <ul>
 *     <li><b>Semantic precision</b> – each value represents a well-defined failure class</li>
 *     <li><b>HTTP alignment</b> – every error maps to an explicit HTTP status code</li>
 *     <li><b>Retry awareness</b> – transient vs non-transient failures are explicitly marked</li>
 *     <li><b>DDD compatibility</b> – business errors are clearly separated from technical ones</li>
 * </ul>
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * throw new BusinessException(
 *     ErrorCode.BUSINESS_RULE_VIOLATION,
 *     "APN cannot be assigned in the current state"
 * );
 * }</pre>
 *
 * <pre>{@code
 * if (errorCode.isRetriable()) {
 *     retryScheduler.scheduleRetry(...);
 * }
 * }</pre>
 *
 * <p>
 * A special {@link #N_A} value exists as a defensive fallback and should only be
 * used when the actual failure reason cannot be determined.
 *
 * @see ErrorCategory
 */
public enum ErrorCode {

    /* =========================
     * Validation / Input Errors
     * ========================= */
    VALIDATION_FAILED(400, ErrorCategory.VALIDATION, false),
    MALFORMED_REQUEST(400, ErrorCategory.VALIDATION, false),
    MISSING_REQUIRED_FIELD(400, ErrorCategory.VALIDATION, false),
    VALUE_NOT_PROVIDED(400, ErrorCategory.VALIDATION, false),
    CONVERSION_ERROR(400, ErrorCategory.VALIDATION, false),
    PAYLOAD_TOO_LARGE(413, ErrorCategory.VALIDATION, false),
    UNSUPPORTED_MEDIA_TYPE(415, ErrorCategory.VALIDATION, false),

    /* =========================
     * Security Errors
     * ========================= */
    AUTHENTICATION_FAILED(401, ErrorCategory.SECURITY, false),
    AUTHORIZATION_FAILED(403, ErrorCategory.SECURITY, false),

    /* =========================
     * Resource Errors
     * ========================= */
    RESOURCE_NOT_FOUND(404, ErrorCategory.RESOURCE, false),
    RESOURCE_ALREADY_EXISTS(409, ErrorCategory.RESOURCE, false),
    METHOD_NOT_ALLOWED(405, ErrorCategory.RESOURCE, false),
    PRECONDITION_FAILED(412, ErrorCategory.RESOURCE, false),
    NO_CONTENT(204, ErrorCategory.RESOURCE, false),

    /* =========================
     * Business / Domain Errors
     * ========================= */
    BUSINESS_RULE_VIOLATION(409, ErrorCategory.BUSINESS, false),
    INVALID_STATE_TRANSITION(409, ErrorCategory.BUSINESS, false),
    STATE_INCONSISTENCY(409, ErrorCategory.BUSINESS, false),

    /* =========================
     * Persistence Errors
     * ========================= */
    DATA_INTEGRITY_VIOLATION(409, ErrorCategory.PERSISTENCE, false),
    CONSTRAINT_VIOLATION(409, ErrorCategory.PERSISTENCE, false),
    OPTIMISTIC_LOCK_FAILURE(409, ErrorCategory.PERSISTENCE, true),
    PERSISTENCE_ERROR(500, ErrorCategory.PERSISTENCE, true),

    /* =========================
     * Integration / External
     * ========================= */
    THIRD_PARTY_ERROR(502, ErrorCategory.INTEGRATION, true),
    UNEXPECTED_RESPONSE(502, ErrorCategory.INTEGRATION, true),
    COMMUNICATION_ERROR(502, ErrorCategory.INTEGRATION, true),
    EXTERNAL_TIMEOUT(504, ErrorCategory.INTEGRATION, true),
    DEPENDENCY_FAILURE(424, ErrorCategory.INTEGRATION, true),

    /* =========================
     * Availability / Retry
     * ========================= */
    RATE_LIMITED(429, ErrorCategory.AVAILABILITY, true),
    SERVICE_UNAVAILABLE(503, ErrorCategory.AVAILABILITY, true),
    RETRY_EXHAUSTED(503, ErrorCategory.AVAILABILITY, true),
    CIRCUIT_BREAKER_OPEN(503, ErrorCategory.AVAILABILITY, true),
    TIMEOUT(504, ErrorCategory.AVAILABILITY, true),

    /* =========================
     * Infrastructure / Platform
     * ========================= */
    CONFIGURATION_ERROR(500, ErrorCategory.PLATFORM, false),
    INITIALIZATION_ERROR(500, ErrorCategory.PLATFORM, false),
    IO_ERROR(500, ErrorCategory.PLATFORM, true),
    SERIALIZATION_ERROR(500, ErrorCategory.PLATFORM, false),
    INTERNAL_ERROR(500, ErrorCategory.PLATFORM, false),

    /* =========================
     * Fallback
     * ========================= */
    N_A(500, ErrorCategory.UNKNOWN, false);


    private final int httpCode;
    private final ErrorCategory category;
    private final boolean retryable;

    ErrorCode(int httpCode, ErrorCategory category, boolean retryable) {
        this.httpCode = httpCode;
        this.category = category;
        this.retryable = retryable;
    }

    public int getHttpCode() {
        return httpCode;
    }

    public ErrorCategory getErrorCategory() {
        return category;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
