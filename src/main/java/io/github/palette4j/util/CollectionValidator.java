package io.github.palette4j.util;

import io.github.palette4j.spi.ObjectValidator;
import java.util.Collection;
import java.util.Objects;


/**
 * Defines a set of reusable validation predicates for {@link Collection} instances.
 * <p>
 * This enum provides a small, focused catalog of collection-related validation rules,
 * each expressed as a boolean predicate implementing {@link ObjectValidator}.
 * The validators are intended to be composable building blocks for higher-level
 * validation logic rather than standalone validation frameworks.
 * </p>
 *
 * <h2>State</h2>
 * <p>
 * This enum is stateless. Each constant represents a pure validation rule and does not
 * maintain or mutate any internal state.
 * </p>
 *
 * <h2>Behavior</h2>
 * <p>
 * Each enum constant implements {@link ObjectValidator#isTrueFor(Object)}} and evaluates a specific
 * condition against a provided collection instance.
 * </p>
 * <ul>
 *   <li>Validation methods are <em>null-safe</em> and explicitly define their behavior
 *       when a {@code null} collection is supplied.</li>
 *   <li>Implementations do not modify the input collection.</li>
 *   <li>Validation results depend solely on the input argument.</li>
 * </ul>
 *
 * <h2>Provided validators</h2>
 * <ul>
 *   <li><b>{@link #COLLECTION_IS_BLANK}</b> – evaluates to {@code true} when the collection
 *       is {@code null} or contains no elements.</li>
 *   <li><b>{@link #COLLECTION_CONTAINS_NULL_ELEMENTS}</b> – evaluates to {@code true} when
 *       the collection contains at least one {@code null} element.</li>
 * </ul>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * This enum is thread-safe. All validators are stateless and may be safely reused
 * across threads without synchronization.
 * </p>
 *
 * <h2>Design intent</h2>
 * <p>
 * {@code CollectionValidator} exists to:
 * </p>
 * <ul>
 *   <li>Encapsulate common collection validation rules in a type-safe, discoverable form</li>
 *   <li>Promote reuse and consistency across validation logic</li>
 *   <li>Avoid scattering ad-hoc null and emptiness checks throughout the codebase</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Collection<?> values = fetchValues();
 *
 * if (CollectionValidator.COLLECTION_IS_BLANK.isTrueFor(values)) {
 *     throw new IllegalArgumentException("Values must not be empty");
 * }
 *
 * if (CollectionValidator.COLLECTION_CONTAINS_NULL_ELEMENTS.isTrueFor(values)) {
 *     throw new IllegalArgumentException("Values must not contain null elements");
 * }
 * }</pre>
 */
public enum CollectionValidator implements ObjectValidator<Collection<?>> {

    COLLECTION_IS_BLANK {
        /**
         * Checks if the given collection is null or empty.
         *
         * @param obj the collection to check.
         * @return {@code true} if the collection is null or has no elements,
         * {@code false} otherwise.
         */
        @Override
        public boolean isTrueFor(final Collection<?> obj) {
            return obj == null || obj.isEmpty();
        }
    },

    COLLECTION_CONTAINS_NULL_ELEMENTS {
        /**
         * Checks if the given collection contains any null elements.
         *
         * @param obj the collection to check.
         * @return {@code true} if the collection is not null and contains at least one null element,
         * {@code false} otherwise.
         */
        @Override
        public boolean isTrueFor(final Collection<?> obj) {
            return obj != null && obj.stream().anyMatch(Objects::isNull);
        }
    }
}
