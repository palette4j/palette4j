package io.github.palette4j.exception;


import io.github.palette4j.util.Localization;

/**
 * Base class for runtime exceptions that carry a structured error code,
 * optional localization, and contextual tags.
 * <p>
 * This exception represents the most information-rich layer of the exception hierarchy.
 * It combines:
 * </p>
 * <ul>
 *   <li>Structured tagging (via {@link TaggableRuntimeException})</li>
 *   <li>Optional localization (via {@link LocalizedRuntimeException})</li>
 *   <li>A mandatory semantic {@link ErrorCode} describing the nature of the failure</li>
 * </ul>
 *
 * <h2>State</h2>
 * <ul>
 *   <li><b>Error code</b> – a domain- or infrastructure-level {@link ErrorCode} describing
 *       the failure condition.</li>
 *   <li><b>Error category</b> – derived from the error code and representing a broader
 *       classification of the error.</li>
 *   <li><b>Localization</b> – optional localization metadata for user-facing message rendering.</li>
 *   <li><b>Tags</b> – inherited structured key–value metadata for diagnostics and correlation.</li>
 * </ul>
 *
 * <h2>Behavior</h2>
 * <p>
 * This class overrides {@link #getMessage()} to prepend structured error information
 * to the exception message in a stable, machine- and human-readable format:
 * </p>
 * <pre>{@code
 * CATEGORY::ERROR_CODE --- message
 * }</pre>
 * <p>
 * The remainder of the message (including tags and localization-aware content)
 * is delegated to the superclass implementation.
 * </p>
 *
 * <h2>Error semantics</h2>
 * <ul>
 *   <li>{@link #getErrorCode()} always returns a non-null value, defaulting to
 *       {@link ErrorCode#N_A} when no explicit code is provided.</li>
 *   <li>{@link #getErrorCategory()} always returns a non-null value, defaulting to
 *       {@link ErrorCategory#UNKNOWN} when no explicit category is available.</li>
 *   <li>{@link #isRetryable()} exposes retry semantics directly from the error code.</li>
 * </ul>
 *
 * <h2>Message resolution</h2>
 * <p>
 * Message resolution follows the layered model:
 * </p>
 * <ol>
 *   <li>Error category and code prefix</li>
 *   <li>Tag-enriched message from {@link TaggableRuntimeException}</li>
 *   <li>Localized or non-localized message content from {@link LocalizedRuntimeException}</li>
 * </ol>
 * <p>
 * This ensures that every rendered message contains a stable, parsable prefix
 * while still supporting localization and rich context.
 * </p>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * This class is <strong>not thread-safe</strong>, as it inherits mutable tagging
 * behavior from its superclasses. Instances are intended to be created, enriched,
 * and thrown within a single execution thread.
 * </p>
 *
 * <h2>Design intent</h2>
 * <p>
 * {@code DetailedRuntimeException} exists to:
 * </p>
 * <ul>
 *   <li>Provide a single, canonical base for application-level exceptions</li>
 *   <li>Standardize error representation across logs, APIs, and user interfaces</li>
 *   <li>Enable consistent error categorization, retry policies, and localization</li>
 * </ul>
 * <p>
 * Concrete exception types should extend this class and supply domain-appropriate
 * {@link ErrorCode} values.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * throw new PersistenceException(
 *     "Failed to save entity",
 *     ErrorCode.PERSISTENCE_ERROR,
 *     Localization.builder()
 *         .locale("en-US")
 *         .localizationKey("error.persistence.failed")
 *         .embeddedMessage("Unable to save data")
 *         .build()
 * ).addTag("entityId", entityId)
 *  .addTag("table", "orders");
 * }</pre>
 */
public abstract class DetailedRuntimeException extends LocalizedRuntimeException {

    private final ErrorCode errorCode;


    public DetailedRuntimeException(final String message, final ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public DetailedRuntimeException(final String message, final Throwable cause, final ErrorCode errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public DetailedRuntimeException(final Throwable cause, final ErrorCode errorCode) {
        super(cause);
        this.errorCode = errorCode;
    }

    public DetailedRuntimeException(final String message, final ErrorCode errorCode, final Localization localization) {
        super(message, localization);
        this.errorCode = errorCode;
    }

    public DetailedRuntimeException(final String message, final Throwable cause, final ErrorCode errorCode, final Localization localization) {
        super(message, cause, localization);
        this.errorCode = errorCode;
    }

    public DetailedRuntimeException(final Throwable cause, final ErrorCode errorCode, final Localization localization) {
        super(cause, localization);
        this.errorCode = errorCode;
    }


    @Override
    public String getMessage() {
        return new StringBuilder()
                .append(getErrorCategory())
                .append("::")
                .append(getErrorCode())
                .append(" --- ")
                .append(super.getMessage())
                .toString()
                .trim();
    }

    @Override
    public DetailedRuntimeException addTag(String key, Object value) {
        super.addTag(key, value);
        return this;
    }

    public ErrorCode getErrorCode() {
        return (errorCode != null) ? errorCode : ErrorCode.N_A;
    }

    public ErrorCategory getErrorCategory() {
        return (errorCode != null) ? errorCode.getErrorCategory() : ErrorCategory.UNKNOWN;
    }

    public boolean isRetryable() {
        return getErrorCode().isRetryable();
    }
}
