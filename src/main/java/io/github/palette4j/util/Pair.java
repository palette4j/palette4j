package io.github.palette4j.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Represents an immutable, non-null pair of two related values.
 * <p>
 * This class is a small, generic utility intended to model a logical association
 * between two values without introducing a dedicated domain type. It is particularly
 * useful for temporary data transport, functional transformations, and intermediate
 * representations (e.g. when converting collections or mapping results).
 * </p>
 *
 * <h2>State</h2>
 * <ul>
 *   <li><b>First element</b> – the left component of the pair; guaranteed to be non-null.</li>
 *   <li><b>Second element</b> – the right component of the pair; guaranteed to be non-null.</li>
 * </ul>
 *
 * <h2>Immutability</h2>
 * <p>
 * {@code Pair} instances are immutable. Both elements are assigned at construction time
 * and cannot be modified thereafter. This makes instances inherently thread-safe
 * and safe to reuse across execution contexts.
 * </p>
 *
 * <h2>Behavior</h2>
 * <p>
 * In addition to simple value access, this class provides utility operations commonly
 * required when working with paired data:
 * </p>
 * <ul>
 *   <li>Static factory methods for safe and explicit construction</li>
 *   <li>Conversion of {@link Map} entries into lists of pairs</li>
 *   <li>Functional transformation of a pair into another pair</li>
 *   <li>Element swapping</li>
 * </ul>
 * <p>
 * All operations are defensive with respect to {@code null} inputs and clearly define
 * their failure or skipping semantics.
 * </p>
 *
 * <h2>Null-handling semantics</h2>
 * <ul>
 *   <li>{@code Pair} elements are never {@code null}; construction fails fast otherwise.</li>
 *   <li>Map conversion utilities silently ignore entries with {@code null} keys or values.</li>
 *   <li>Exceptions thrown during mapping operations are caught and ignored, causing the
 *       corresponding entry to be skipped.</li>
 * </ul>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * This class is thread-safe due to its immutability. No synchronization is required
 * for concurrent access.
 * </p>
 *
 * <h2>Design intent</h2>
 * <p>
 * {@code Pair} exists to:
 * </p>
 * <ul>
 *   <li>Provide a lightweight alternative to ad-hoc tuple-like structures</li>
 *   <li>Support functional-style transformations and intermediate results</li>
 *   <li>Avoid over-modeling when a full domain object would be unnecessary</li>
 * </ul>
 * <p>
 * It is not intended to replace expressive domain types where semantic meaning
 * and invariants matter.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Pair<String, Integer> p = Pair.of("Alice", 42);
 *
 * Pair<Integer, String> swapped = p.swap(p);
 *
 * Pair<String, String> mapped =
 *     p.map((name, id) -> Pair.of(name, "ID-" + id));
 *
 * Map<String, Integer> source = Map.of("a", 1, "b", 2);
 * List<Pair<String, Integer>> pairs = Pair.fromMap(source);
 * }</pre>
 */
public final class Pair<F, S> {

    private final F first;
    private final S second;

    /**
     * A static factory method for creating a {@link Pair} instance.
     *
     * @param <F>    the type of the first element.
     * @param <S>    the type of the second element.
     * @param first  the first element of the pair; must not be null.
     * @param second the second element of the pair; must not be null.
     * @return a new {@code Pair} containing the provided elements.
     * @throws IllegalArgumentException if either {@code first} or {@code second} is null.
     */
    public static <F, S> Pair<F, S> of(F first, S second) {
        return new Pair<>(first, second);
    }

    /**
     * Converts a {@link Map} into a {@link List} of {@link Pair}s using a custom mapping function.
     * This method iterates over the entries of the given map and applies the provided mapper function
     * to each key-value pair to create a {@code Pair}.
     * <p>
     * Entries with null keys or null values are ignored.
     * <p>
     * Any exceptions thrown by the mapper function during the conversion of an entry are caught and ignored,
     * effectively skipping that entry, thus, only successfully created {@link Pair}'s will be added
     * to the resulting {@link List}.
     * <p>
     * Behaves similarly in case if {@code mapper} returns null: it will be ignored and not added to the resulting list.
     *
     * @param <F>    the type of the first element in the resulting pairs.
     * @param <S>    the type of the second element in the resulting pairs.
     * @param <K>    the type of the keys in the input map.
     * @param <V>    the type of the values in the input map.
     * @param map    the {@code Map} to be converted. If null or empty, an empty list is returned.
     * @param mapper a {@link BiFunction} that takes a key and a value from the map and returns a {@code Pair<F, S>}.
     *               This function must not be null.
     * @return a {@code List} of {@code Pair}s resulting from the mapping. Returns an empty list if the input map
     *         is null, empty, or if all its entries have null keys/values or cause exceptions during mapping.
     * @throws IllegalArgumentException if the {@code mapper} function is null.
     */
    public static <F, S, K, V> List<Pair<F, S>> fromMap(Map<K, V> map, BiFunction<K, V, Pair<F, S>> mapper) {
        if (mapper == null) {
            throw new IllegalArgumentException("Mapper function cannot be null");
        }

        if (map == null || map.isEmpty()) {
            return Collections.emptyList();
        }

        List<Pair<F, S>> pairs = new ArrayList<>(map.size());
        map.forEach((k, v) -> {
            if (k != null && v != null) {
                try {
                    Pair<F, S> pair = mapper.apply(k, v);
                    if (pair != null) {
                        pairs.add(pair);
                    }
                } catch (Exception ignore) {
                }
            }
        });

        return pairs;
    }

    /**
     * Converts a {@link Map} into a {@link List} of {@link Pair}s.
     * Each entry in the map is converted into a {@code Pair}, where the map's key becomes the first element
     * and the map's value becomes the second element of the pair. Entries with null keys or null values are ignored.
     *
     * @param <K> the type of the keys in the map, which will be the type of the first element in the pairs.
     * @param <V> the type of the values in the map, which will be the type of the second element in the pairs.
     * @param map the {@code Map} to convert into a list of pairs.
     * @return a {@code List} of {@code Pair}s representing the entries of the map. Returns an empty list
     *         if the map is null, empty, or contains only entries with null keys or values.
     */
    public static <K, V> List<Pair<K, V>> fromMap(Map<K, V> map) {
        if (map == null || map.isEmpty()) {
            return Collections.emptyList();
        }

        List<Pair<K, V>> pairs = new ArrayList<>(map.size());
        map.forEach((f, s) -> {
            if (f != null && s != null) {
                pairs.add(Pair.of(f, s));
            }
        });

        return pairs;
    }

    private Pair(F first, S second) {
        if (first == null) {
            throw new IllegalArgumentException("First element of pair cannot be null");
        } else if (second == null) {
            throw new IllegalArgumentException("Second element of pair cannot be null");
        }
        this.first = first;
        this.second = second;
    }

    /**
     * Transforms this pair into a new pair by applying a mapping function.
     * This method allows for converting a {@code Pair<F, S>} into a {@code Pair<K, V>}
     * using the provided mapper function.
     *
     * @param <K>    the type of the first element of the new pair
     * @param <V>    the type of the second element of the new pair
     * @param mapper a {@link BiFunction} that takes the first and second elements of this pair
     *               and returns a new {@code Pair<K, V>}. Must not be null.
     * @return a new {@code Pair<K, V>} as a result of applying the mapper function.
     * @throws IllegalArgumentException if the mapper function is null,
     *                                  or if either {@code first} element or {@code second} element is null.
     */
    public <K, V> Pair<K, V> map(BiFunction<F, S, Pair<K, V>> mapper) {
        if (mapper == null) {
            throw new IllegalArgumentException("Mapper must not be null");
        }
        return mapper.apply(first, second);
    }

    /**
     * Creates a new Pair by swapping the elements of the provided pair.
     * This method takes a {@code Pair<F, S>} and returns a new {@code Pair<S, F>}
     * where the first and second elements are swapped.
     *
     * @param other The pair whose elements are to be swapped. Must not be null.
     * @return A new {@code Pair} instance with the elements of the input pair swapped.
     * @throws IllegalArgumentException if the input pair is null.
     */
    public Pair<S, F> swap(Pair<F, S> other) {
        if (other == null) {
            throw new IllegalArgumentException("Pair submitted for swapping is null!");
        }
        return Pair.of(other.second, other.first);
    }

    public F getFirst() {
        return first;
    }

    public S getSecond() {
        return second;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pair)) return false;
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return Objects.equals(first, pair.first) && Objects.equals(second, pair.second);
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }

    @Override
    public String toString() {
        return "Pair{" +
                "first=" + first +
                ", second=" + second +
                '}';
    }
}
