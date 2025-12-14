package io.github.palette4j.exception;

import io.github.palette4j.util.Localization;
import java.util.Locale;
import java.util.Objects;


/**
 * Base class for runtime exceptions that support both structured tagging and
 * locale-aware message representation.
 * <p>
 * This exception extends {@link TaggableRuntimeException} by optionally associating
 * a {@link Localization} object with the exception. When present, the localization
 * information is used to produce a localized message via {@link #getLocalizedMessage()},
 * while still preserving tag-enriched message formatting inherited from the base class.
 * </p>
 *
 * <h2>State</h2>
 * <ul>
 *   <li><b>Localization</b> – optional {@link Localization} metadata describing how the
 *       exception message should be localized.</li>
 *   <li><b>Tags</b> – inherited structured key–value tags providing additional context.</li>
 * </ul>
 *
 * <h2>Behavior</h2>
 * <p>
 * The localized message resolution follows a clear precedence:
 * </p>
 * <ol>
 *   <li>If a {@link Localization} is present, its embedded localized message is returned.</li>
 *   <li>If no localization is present, the tag-enriched message from the superclass
 *       is returned.</li>
 * </ol>
 * <p>
 * This allows exceptions to carry both a human-readable, localized message and
 * machine-friendly structured metadata.
 * </p>
 *
 * <h2>Message rendering</h2>
 * <p>
 * Unlike {@link #getMessage()}, which always reflects the tag-enriched message,
 * {@link #getLocalizedMessage()} may return a locale-specific representation
 * derived from {@link Localization}.
 * </p>
 * <p>
 * Tags added via {@link #addTag(String, Object)} are still available for logging
 * and inspection, even when a localized message is used.
 * </p>
 *
 * <h2>Null and fallback semantics</h2>
 * <ul>
 *   <li>Localization is optional and may be {@code null}.</li>
 *   <li>If localization is absent, locale and message resolution fall back to
 *       the superclass behavior and system default {@link Locale}.</li>
 *   <li>Localization keys may be {@code null} when no localization is associated.</li>
 * </ul>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * This class is <strong>not thread-safe</strong>, as it inherits mutable tagging
 * behavior from {@link TaggableRuntimeException}. Instances are intended to be
 * created, enriched, and thrown within a single execution thread.
 * </p>
 *
 * <h2>Design intent</h2>
 * <p>
 * {@code LocalizedRuntimeException} exists to:
 * </p>
 * <ul>
 *   <li>Bridge exception handling with internationalization (i18n)</li>
 *   <li>Allow exceptions to expose both localized user-facing messages and
 *       structured diagnostic context</li>
 *   <li>Serve as a common base for domain and infrastructure exceptions that
 *       must be rendered differently for users and logs</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * throw new BusinessRuleViolationException(
 *     "Invalid account state",
 *     Localization.builder()
 *         .locale("en-US")
 *         .localizationKey("error.account.invalid_state")
 *         .embeddedMessage("Account is in an invalid state")
 *         .build()
 * ).addTag("accountId", accountId);
 * }</pre>
 */
public abstract class LocalizedRuntimeException extends TaggableRuntimeException {

    private final Localization localization;


    public LocalizedRuntimeException(final String message) {
        super(message);
        this.localization = null;
    }

    public LocalizedRuntimeException(final String message, final Throwable cause) {
        super(message, cause);
        this.localization = null;
    }

    public LocalizedRuntimeException(final Throwable cause) {
        super(cause);
        this.localization = null;
    }

    public LocalizedRuntimeException(final String message, final Localization localization) {
        super(message);
        this.localization = localization;
    }

    public LocalizedRuntimeException(final String message, final Throwable cause, final Localization localization) {
        super(message, cause);
        this.localization = localization;
    }

    public LocalizedRuntimeException(final Throwable cause, final Localization localization) {
        super(cause);
        this.localization = localization;
    }

    @Override
    public String getLocalizedMessage() {
        if (localization != null) {
            return localization.getEmbeddedLocalizedMessage();
        } else {
            return super.getLocalizedMessage();
        }
    }

    @Override
    public LocalizedRuntimeException addTag(String key, Object value) {
        super.addTag(key, value);
        return this;
    }

    public Localization getLocalization() {
        return localization;
    }

    /**
     * Gets the locale associated with this exception's {@link Localization}.
     * <p>
     * If the exception was created with specific localization information, this method returns the locale
     * from that information. Otherwise, it falls back to the default locale of the system.
     *
     * @return The {@link Locale} for this exception, or the default system {@link Locale} if no specific localization is set.
     */
    public Locale getLocale() {
        return (localization != null) ? localization.getLocale() : Locale.getDefault();
    }

    /**
     * Gets the localization key for this exception's {@link Localization}.
     * <p>
     * If the exception was created with specific localization information, this method returns the key
     * used for message localization. This key can be used to look up the message template in a resource bundle.
     *
     * @return The localization key as a {@link String}, or {@code null} if no specific localization is set.
     */
    public String getLocalizationKey() {
        return (localization != null) ? localization.getLocalizationKey() : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LocalizedRuntimeException)) return false;
        if (!super.equals(o)) return false;
        LocalizedRuntimeException that = (LocalizedRuntimeException) o;
        return Objects.equals(localization, that.localization);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), localization);
    }

    @Override
    public String toString() {
        return "LocalizedRuntimeException{" +
                "localization=" + localization +
                '}';
    }
}
