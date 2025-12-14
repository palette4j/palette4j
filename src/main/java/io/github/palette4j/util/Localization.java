package io.github.palette4j.util;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Represents a fully self-contained localization request (i18n/l10n) that can be resolved into a
 * user-facing message.
 * <p>
 * This type is intentionally <em>immutable</em> and acts as a data carrier for all inputs required to
 * produce a localized string, while remaining independent from the underlying translation storage
 * (e.g. {@code .properties} bundles, a database, a remote service, etc.). The translation lookup is
 * delegated to a caller-supplied resolver function.
 * </p>
 *
 * <h2>State</h2>
 * <ul>
 *   <li><b>Target {@link Locale}</b> – desired language/region (e.g. {@code en-US}, {@code fr-FR}).</li>
 *   <li><b>Localization key</b> – a stable string key used to retrieve a translated message
 *       (e.g. {@code "error.file.not.found"}).</li>
 *   <li><b>Embedded fallback message</b> – a default message used when the key cannot be resolved
 *       (e.g. {@code "File {0} was not found."}).</li>
 *   <li><b>Message arguments</b> – optional arguments to be interpolated into the resolved template.</li>
 * </ul>
 *
 * <h2>Behavior</h2>
 * The primary entry point is {@link #resolveExternally(BiFunction)}.
 * <p>
 * The method accepts a {@link BiFunction} resolver that is responsible for
 * mapping a pair {@code (Locale, localizationKey)} to a message template. The returned template is
 * then formatted with {@code messageArgs}. If the resolver returns {@code null} (or otherwise cannot
 * resolve the key), the instance falls back to {@code embeddedMessage} and formats it instead.
 * </p>
 *
 * <h2>Construction</h2>
 * Instances are created via:
 * <ul>
 *   <li>Convenience factory methods such as {@code embedded(...)} for ad-hoc, non-translated or
 *       fallback-only messages.</li>
 *   <li>{@link #builder()} for explicit, readable construction when multiple optional fields are involved.</li>
 * </ul>
 * <p>
 * The constructor is private to enforce controlled creation and enable consistent validation.
 * </p>
 *
 * <h2>Thread-safety</h2>
 * Because instances are immutable, they are inherently thread-safe and may be reused across threads.
 * <p>
 * <h2>Design intent</h2>
 * <p>
 * {@code Localization} is designated to separate concerns of:
 * </p>
 * <ul>
 *   <li><b>What to say</b> is captured by this class (locale, key, embedded(default) message, args).</li>
 *   <li><b>How to translate</b> is provided externally (resolver implementation).</li>
 * </ul>
 * <p>
 * <h2>Example</h2>
 * <pre>{@code
 * Localization loc = Localization.builder()
 *     .locale("en-US")
 *     .localizationKey("user.welcome")
 *     .embeddedMessage("Welcome, {0}!")
 *     .messageArgs("Alice")
 *     .build();
 *
 * String text = loc.resolveExternally((locale, key) -> messageSourceLookup(locale, key));
 * }</pre>
 */
public class Localization {

    /**
     * Builder class for creating {@link Localization} instances.
     */
    public static class Builder {
        private Locale locale;
        private String messageCode;
        private String embeddedMessage;
        private Object[] messageArgs;

        /**
         * Sets the locale for the localization.
         *
         * @param locale The {@link Locale} to be used for localization.
         * @return This {@link Builder} instance for method chaining.
         */
        public Builder locale(final Locale locale) {
            this.locale = locale;
            return this;
        }

        /**
         * Sets the locale for the localization using a BCP 47 language tag string.
         *
         * @param localeTag The language tag string (e.g., "en-US") to create the {@link Locale} from.
         *                  It must not be null or empty.
         * @return This {@link Builder} instance for method chaining.
         * @throws IllegalArgumentException if the {@code localeTag} is null or empty.
         */
        public Builder locale(final String localeTag) {
            if (localeTag == null || localeTag.isEmpty()) {
                throw new IllegalArgumentException("Locale tag must not be null or empty");
            }
            this.locale = Locale.forLanguageTag(localeTag);
            return this;
        }

        /**
         * Sets the localization key used to look up the localized message.
         * <p>
         * This key is used by an external resolver to find the appropriate translated string
         * for the specified {@link Locale}.
         *
         * @param messageCode The localization key. It must not be null or empty.
         * @return This {@link Builder} instance for method chaining.
         * @throws IllegalArgumentException if the {@code messageCode} is null or empty.
         */
        public Builder localizationKey(final String messageCode) {
            if (messageCode == null || messageCode.isEmpty()) {
                throw new IllegalArgumentException("Message code must not be null or empty");
            }
            this.messageCode = messageCode;
            return this;
        }

        /**
         * Sets the embedded message to be used as a fallback if the localization key cannot be resolved.
         * <p>
         * This message can be a literal string or a format pattern compatible with {@link MessageFormat}.
         * It will be used when an external resolver fails to find a message for the given {@code localizationKey},
         * or when no external resolver is used.
         *
         * @param embeddedMessage The message string.
         * @return This {@link Builder} instance for method chaining.
         */
        public Builder embeddedMessage(final String embeddedMessage) {
            this.embeddedMessage = embeddedMessage;
            return this;
        }

        /**
         * Sets the arguments to be used for formatting the message.
         * <p>
         * These arguments will be inserted into the message string (either the resolved localized message
         * or the embedded fallback message) using {@link MessageFormat}. The order of arguments
         * corresponds to the format specifiers (e.g., {@code {0}}, {@code {1}}) in the message pattern.
         *
         * @param messageArgs The variable-length array of objects to be used as message arguments.
         * @return This {@link Builder} instance for method chaining.
         */
        public Builder messageArgs(final Object... messageArgs) {
            this.messageArgs = Arrays.copyOf(messageArgs, messageArgs.length);
            return this;
        }

        /**
         * Builds the final {@link Localization} instance using the provided settings.
         *
         * @return The built {@link Localization} instance.
         */
        public Localization build() {
            return new Localization(locale, messageCode, embeddedMessage, messageArgs);
        }
    }
    private final Locale locale;
    private final String localizationKey;
    private final String embeddedMessage;
    private final Object[] messageArgs;

    private Localization(Locale locale, String localizationKey, String embeddedMessage, Object[] messageArgs) {
        this.locale = locale;
        this.localizationKey = localizationKey;
        this.embeddedMessage = embeddedMessage;
        this.messageArgs = (messageArgs != null) ? Arrays.copyOf(messageArgs, messageArgs.length) : new Object[0];
    }

    /**
     * Creates a simple {@link Localization} instance with only an embedded message,
     * using the default system {@link Locale} and no message arguments.
     * <p>
     * This is a convenience factory method for creating ad-hoc localized messages or messages
     * that serve as a fallback.
     *
     * @param message The embedded message string. This string can be a literal message
     *                or a format pattern compatible with {@link MessageFormat}.
     * @return A new {@link Localization} instance configured with the provided embedded message.
     */
    public static Localization embedded(final String message) {
        return new Localization(Locale.getDefault(), null, message, new Object[0]);
    }

    /**
     * Creates a {@link Localization} instance with an embedded message and arguments for formatting,
     * using the default system {@link Locale}.
     * <p>
     * This factory method is useful for creating simple, ad-hoc localized messages that require formatting,
     * or for providing a fallback message with parameters.
     *
     * @param message     The embedded message string, which can be a format pattern
     *                    compatible with {@link MessageFormat}.
     * @param messageArgs The arguments to be inserted into the message string during formatting.
     * @return A new {@link Localization} instance configured with the provided embedded message and arguments.
     */
    public static Localization embedded(final String message, final Object... messageArgs) {
        return new Localization(Locale.getDefault(), null, message, messageArgs);
    }

    /**
     * Creates a new {@link Builder} instance for constructing {@link Localization} objects.
     * <p>
     * This static factory method is the entry point for using the builder pattern
     * to create a {@link Localization} instance in a fluent and readable manner.
     *
     * @return a new {@link Builder} instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Resolves the locale and the localization key contained within {@link Localization} to a localized message
     * using a provided external resolver.
     * If the external resolver cannot find a message for the given key, this method
     * falls back to using the embedded message ({@link Localization#getEmbeddedLocalizedMessage()}).
     * The message arguments are then used to format the resulting message string.
     *
     * @param localizationResolver A {@link BiFunction} that accepts a {@link Locale} and a localization key {@link String},
     *                             and returns the corresponding localized message string. This resolver must not be null.
     * @return The formatted, localized message string. If the key is not found by the resolver,
     * the formatted embedded message is returned. Returns null if the key is not resolved and
     * no embedded message is available.
     * @throws IllegalArgumentException if the localizationResolver is null.
     */
    public String resolveExternally(BiFunction<Locale, String, String> localizationResolver) {
        if (localizationResolver == null) {
            throw new IllegalArgumentException("localizationResolver BiFunction cannot be null");
        }

        String localizedMessage = localizationResolver.apply(locale, localizationKey);
        if (localizedMessage == null) {
            return getEmbeddedLocalizedMessage();
        } else {
            return MessageFormat.format(localizedMessage, messageArgs);
        }
    }

    /**
     * Formats the embedded message with the provided message arguments.
     * This method uses {@link MessageFormat#format(String, Object...)} to insert the arguments
     * into the embedded message string. This serves as a fallback when external localization is not available
     * or not used.
     *
     * @return The formatted embedded message as a {@link String}. Returns {@code null} if the embedded message is not set.
     */
    public String getEmbeddedLocalizedMessage() {
        if (embeddedMessage != null) {
            return MessageFormat.format(embeddedMessage, messageArgs);
        }
        return null;
    }

    public Locale getLocale() {
        return locale;
    }

    public String getLocalizationKey() {
        return localizationKey;
    }

    public String getEmbeddedMessage() {
        return embeddedMessage;
    }

    public Object[] getMessageArgs() {
        return Arrays.copyOf(messageArgs, messageArgs.length);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Localization)) return false;
        Localization that = (Localization) o;
        return Objects.equals(locale, that.locale) && Objects.equals(localizationKey, that.localizationKey) && Objects.equals(embeddedMessage, that.embeddedMessage) && Objects.deepEquals(messageArgs, that.messageArgs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locale, localizationKey, embeddedMessage, Arrays.hashCode(messageArgs));
    }

    @Override
    public String toString() {
        return "Localization{" + "locale=" + locale + ", localizationKey='" + localizationKey + '\'' + ", message='" + embeddedMessage + '\'' + ", messageArgs=" + Arrays.toString(messageArgs) + '}';
    }
}
