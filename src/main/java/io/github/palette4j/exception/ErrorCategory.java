package io.github.palette4j.exception;

/**
 * High-level classification of application errors used to group related
 * {@link ErrorCode} values by their functional and architectural nature.
 * <p>
 * {@code ErrorCategory} provides an orthogonal dimension to error handling,
 * allowing the system to reason about failures beyond individual error codes.
 *
 * <p>
 * <h2>State and Semantics</h2>
 * <ul>
 *     <li><b>VALIDATION</b> – malformed input or client-side contract violations</li>
 *     <li><b>SECURITY</b> – authentication and authorization failures</li>
 *     <li><b>RESOURCE</b> – missing, conflicting, or inaccessible resources</li>
 *     <li><b>BUSINESS</b> – domain invariant violations and invalid state transitions</li>
 *     <li><b>PERSISTENCE</b> – database and storage-related failures</li>
 *     <li><b>INTEGRATION</b> – failures in communication with external systems</li>
 *     <li><b>AVAILABILITY</b> – temporary service unavailability and rate limiting</li>
 *     <li><b>PLATFORM</b> – internal infrastructure, configuration, or runtime errors</li>
 *     <li><b>UNKNOWN</b> – unclassified or unexpected failure conditions</li>
 * </ul>
 * </p>
 * <h2>Design intent</h2>
 * Categories allow:
 * <ul>
 *     <li>Clear separation between business and technical failures</li>
 *     <li>Consistent logging, alerting, and monitoring strategies</li>
 *     <li>Targeted operational responses (e.g. retry, circuit breaking)</li>
 * </ul>
 *
 * <p>
 * Categories are especially useful when:
 * <ul>
 *     <li>Tagging metrics (e.g. Micrometer / Prometheus)</li>
 *     <li>Filtering logs by failure type</li>
 *     <li>Routing alerts to different operational teams</li>
 * </ul>
 *
 * <p>
 * {@code ErrorCategory} is intentionally coarse-grained and stable, while
 * {@link ErrorCode} is expected to evolve as the application grows.
 *
 * @see ErrorCode
 */
public enum ErrorCategory {
    VALIDATION,
    SECURITY,
    RESOURCE,
    BUSINESS,
    PERSISTENCE,
    INTEGRATION,
    AVAILABILITY,
    PLATFORM,
    UNKNOWN
}
