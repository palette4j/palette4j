package io.github.palette4j.spi;

import java.util.function.Supplier;

@FunctionalInterface
public interface ObjectValidator<T> {

    boolean isTrueFor(T obj);

    /**
     * Attests that the given object is valid according to this validator. If the object is not valid,
     * a {@link RuntimeException} provided by the given supplier is thrown.
     *
     * @param obj the object to be validated
     * @param exceptionSupplier the supplier which will provide the exception to be thrown if validation fails
     * @throws RuntimeException if the object is not valid, as supplied by the {@code exceptionSupplier}
     * @throws IllegalArgumentException if the validation fails and the {@code exceptionSupplier} is null
     */
    default void attest(T obj, Supplier<? extends RuntimeException> exceptionSupplier) {
        if (!isTrueFor(obj)) {
            if (exceptionSupplier == null) {
                throw new IllegalArgumentException("Exception supplier is null");
            }
            throw exceptionSupplier.get();
        }
    }

    /**
     * Returns a composed validator that represents a short-circuiting logical AND of this validator and another.
     * When evaluating the composed validator, if this validator is {@code false}, then the {@code other}
     * validator is not evaluated.
     *
     * @param other the other validator to be combined with this validator. Must not be null.
     * @return a new {@code ObjectValidator} that represents the logical AND of this validator and the other one
     * @throws IllegalArgumentException if the other validator is null
     */
    default ObjectValidator<T> and(ObjectValidator<? super T> other) {
        if (other == null) {
            throw new IllegalArgumentException("Other validator is null");
        }
        return obj -> isTrueFor(obj) && other.isTrueFor(obj);
    }

    /**
     * Returns a composed validator that represents a short-circuiting logical OR of this validator and another.
     * When evaluating the composed validator, if this validator is {@code true}, then the {@code other}
     * validator is not evaluated.
     *
     * @param other the other validator to be combined with this validator. Must not be null.
     * @return a new {@code ObjectValidator} that represents the logical OR of this validator and the other one
     * @throws IllegalArgumentException if the other validator is null
     */
    default ObjectValidator<T> or(ObjectValidator<? super T> other) {
        if (other == null) {
            throw new IllegalArgumentException("Other validator is null");
        }
        return obj -> isTrueFor(obj) || other.isTrueFor(obj);
    }
    /**
     * Returns a validator that represents the logical negation of this validator.
     *
     * @return a new {@code ObjectValidator} that represents the logical negation of this validator
     */
    default ObjectValidator<T> not() {
        return obj -> !isTrueFor(obj);
    }
}
