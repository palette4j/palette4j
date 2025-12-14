package io.github.palette4j.exception;

import io.github.palette4j.spi.Taggable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Base class for runtime exceptions that can be enriched with structured, key–value tags.
 * <p>
 * This exception type extends {@link RuntimeException} and implements {@link Taggable},
 * allowing callers to attach arbitrary contextual metadata to an exception instance.
 * The attached tags are automatically incorporated into the exception message and
 * localized message representations
 * (mostly as a defence measure against unpredictable message generation through exception hierarchy).
 * </p>
 *
 * <h2>State</h2>
 * <ul>
 *   <li><b>Tags</b> – an ordered map of string keys to arbitrary values providing
 *       additional diagnostic or business context.</li>
 * </ul>
 *
 * <h2>Behavior</h2>
 * <p>
 * Tags may be added incrementally using {@link #addTag(String, Object)} and are stored
 * internally in insertion order.
 * </p>
 * <ul>
 *   <li>Blank keys are ignored and not added.</li>
 *   <li>{@code null} values are stored explicitly as the string {@code "null"}.</li>
 *   <li>The original exception message is preserved but augmented at render time.</li>
 * </ul>
 *
 * <h2>Message rendering</h2>
 * <p>
 * This class overrides both {@link #getMessage()} and {@link #getLocalizedMessage()}
 * to return a formatted message that includes all registered tags.
 * </p>
 * <p>
 * Tags are prepended to the message in the following format:
 * </p>
 * <pre>{@code
 * [key1=value1 key2=value2]: original message
 * }</pre>
 * <p>
 * Spaces in tag values are replaced with underscores to improve log parsing and
 * compatibility with structured logging formats.
 * </p>
 *
 * <h2>Immutability and mutability</h2>
 * <p>
 * While the exception instance itself is mutable with respect to its tags,
 * the tag map is encapsulated:
 * </p>
 * <ul>
 *   <li>Tags may only be modified via {@link #addTag(String, Object)}.</li>
 *   <li>{@link #getTags()} returns a defensive copy to prevent external mutation.</li>
 * </ul>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * This class is <strong>not thread-safe</strong>. It is intended to be constructed,
 * tagged, and thrown within a single execution thread.
 * </p>
 *
 * <h2>Design intent</h2>
 * <p>
 * {@code TaggableRuntimeException} exists to:
 * </p>
 * <ul>
 *   <li>Attach structured context to exceptions without creating numerous custom fields</li>
 *   <li>Improve log readability and correlation through tagged messages</li>
 *   <li>Serve as a common base for domain- or infrastructure-specific exception hierarchies</li>
 * </ul>
 * <p>
 * It is particularly well-suited for integration with structured logging,
 * observability pipelines, and error reporting systems.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * throw new ValidationException("Invalid request")
 *     .addTag("field", "email")
 *     .addTag("reason", "missing");
 * }</pre>
 */
public abstract class TaggableRuntimeException extends RuntimeException implements Taggable {

    private final Map<String, Object> tags = new LinkedHashMap<>();


    public TaggableRuntimeException(final String message) {
        super(message);
    }

    public TaggableRuntimeException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public TaggableRuntimeException(final Throwable cause) {
        super(cause);
    }

    /**
     * Adds a key-value pair as a tag to this exception, providing more context.
     * If the key is blank, the tag is not added. If the value is null, it will be stored as the string "null".
     *
     * @param key   The key identifying the tag.
     * @param value The value associated with the tag.
     * @return This exception instance to allow for fluent method chaining.
     */
    @Override
    public TaggableRuntimeException addTag(final String key, final Object value) {
        if (key.trim().isEmpty()) {
            return this;
        }
        tags.put(key, (value != null) ? value : "null");
        return this;
    }

    /**
     * Retrieves a copy of the tags associated with this exception.
     * The returned map is a defensive copy, so modifications to it will not affect the exception's internal state.
     *
     * @return A new {@link Map} containing the key-value pairs of tags.
     */
    @Override
    public Map<String, Object> getTags() {
        return new LinkedHashMap<>(tags);
    }

    @Override
    public String getLocalizedMessage() {
        return getMessageWithTags();
    }

    @Override
    public String getMessage() {
        return getMessageWithTags();
    }

    /**
     * Constructs a detailed exception message that includes all associated tags.
     * The tags are prepended to the original message, formatted as {@code [key=value]}.
     * Spaces within tag values are replaced with underscores for better log parsing.
     *
     * @return A formatted string containing the tags followed by the original exception message.
     *         If no tags are present, it returns the original message from {@code super.getMessage()}.
     */
    protected String getMessageWithTags() {
        StringBuilder sb = new StringBuilder();
        if (!tags.isEmpty()) {
            sb.append("[");
            tags.forEach((k, v) -> sb.append(k).append("=").append(v.toString().replace(" ", "_")).append(" "));
            sb.setLength(sb.length() - 1);
            sb.append("]: ");
        }
        sb.append(super.getMessage());
        return sb.toString().trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaggableRuntimeException)) return false;
        TaggableRuntimeException that = (TaggableRuntimeException) o;
        return Objects.equals(tags, that.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(tags);
    }

    @Override
    public String toString() {
        return "TaggableRuntimeException{" +
                "tags=" + tags +
                '}';
    }
}
