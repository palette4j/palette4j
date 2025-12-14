package io.github.palette4j.util;

import io.github.palette4j.spi.ObjectValidator;
import java.util.Map;
import java.util.Objects;

/**
 * Defines a set of reusable validation predicates for {@link Map} instances.
 * <p>
 * This enum provides a focused collection of map-specific validation rules,
 * each expressed as a boolean predicate implementing {@link ObjectValidator}.
 * The validators are intended to be composed and reused as part of higher-level
 * validation logic rather than used as a standalone validation framework.
 * </p>
 *
 * <h2>State</h2>
 * <p>
 * This enum is stateless. Each constant represents a pure validation rule and
 * does not retain or mutate any internal state.
 * </p>
 *
 * <h2>Behavior</h2>
 * <p>
 * Each enum constant implements {@link ObjectValidator#isTrueFor(Object)} and evaluates a specific
 * condition against a supplied map instance.
 * </p>
 * <ul>
 *   <li>All validators explicitly define their behavior for {@code null} and empty maps.</li>
 *   <li>Validation methods are <em>null-safe</em> and do not throw {@link NullPointerException}
 *       when presented with a {@code null} map.</li>
 *   <li>The input map is never modified.</li>
 * </ul>
 *
 * <h2>Provided validators</h2>
 * <ul>
 *   <li><b>{@link #MAP_IS_BLANK}</b> – evaluates to {@code true} when the map is {@code null}
 *       or contains no entries.</li>
 *   <li><b>{@link #MAP_CONTAINS_NULL_KEYS}</b> – evaluates to {@code true} when the map is not
 *       blank and contains at least one {@code null} key.</li>
 *   <li><b>{@link #MAP_CONTAINS_NULL_VALUES}</b> – evaluates to {@code true} when the map is not
 *       blank and contains at least one {@code null} value.</li>
 * </ul>
 *
 * <h2>Composition</h2>
 * <p>
 * Validators may be composed using logical combinators (such as {@code not()} and {@code and(...)})
 * provided by {@link ObjectValidator}. This enables expressive, declarative validation rules
 * without duplicating null or emptiness checks.
 * </p>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * This enum is thread-safe. All validators are stateless and may be safely reused
 * concurrently across multiple threads.
 * </p>
 *
 * <h2>Design intent</h2>
 * <p>
 * {@code MapValidator} exists to:
 * </p>
 * <ul>
 *   <li>Centralize common map validation rules in a consistent, type-safe form</li>
 *   <li>Promote reuse and readability over ad-hoc conditional checks</li>
 *   <li>Encourage declarative validation through composable predicates</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Map<?, ?> attributes = loadAttributes();
 *
 * if (MapValidator.MAP_IS_BLANK.isTrueFor(attributes)) {
 *     throw new IllegalArgumentException("Attributes must not be empty");
 * }
 *
 * if (MapValidator.MAP_CONTAINS_NULL_KEYS.isTrueFor(attributes)) {
 *     throw new IllegalArgumentException("Attributes must not contain null keys");
 * }
 *
 * if (MapValidator.MAP_CONTAINS_NULL_VALUES.isTrueFor(attributes)) {
 *     throw new IllegalArgumentException("Attributes must not contain null values");
 * }
 * }</pre>
 */
public enum MapValidator implements ObjectValidator<Map<?, ?>> {

    MAP_IS_BLANK {
        /**
         * Checks if the given map is null or empty.
         *
         * @param obj the map to check.
         * @return {@code true} if the map is null or empty, {@code false} otherwise.
         */
        @Override
        public boolean isTrueFor(final Map<?, ?> obj) {
            return obj == null || obj.isEmpty();
        }
    },

    MAP_CONTAINS_NULL_KEYS {
        /**
         * Checks if the given map is not null or empty and contains at least one null key.
         *
         * @param obj the map to check.
         * @return {@code true} if the map is not blank and contains at least one null key, {@code false} otherwise.
         */
        @Override
        public boolean isTrueFor(final Map<?, ?> obj) {
            return MAP_IS_BLANK.not().and(m -> m.keySet().stream().anyMatch(Objects::isNull)).isTrueFor(obj);
        }
    },

    MAP_CONTAINS_NULL_VALUES {
        /**
         * Checks if the given map is not null or empty and contains at least one null value.
         *
         * @param obj the map to check.
         * @return {@code true} if the map is not blank and contains at least one null value, {@code false} otherwise.
         */
        @Override
        public boolean isTrueFor(final Map<?, ?> obj) {
            return MAP_IS_BLANK.not().and(m -> m.values().stream().anyMatch(Objects::isNull)).isTrueFor(obj);
        }
    }
}
