package io.github.palette4j.util;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Defines a set of strategies for generating high-entropy, unique string identifiers.
 * <p>
 * This enum acts as a unified façade over multiple identifier generation algorithms,
 * each optimized for different trade-offs such as readability, compactness,
 * lexicographical ordering, entropy size, and URL safety.
 * </p>
 * <p>
 * All generators are <em>stateless</em> and rely on a shared {@link SecureRandom}
 * instance or time-based components to produce identifiers with a low probability of collision.
 * </p>
 *
 * <h2>State</h2>
 * <ul>
 *   <li><b>Entropy source</b> – a shared {@link SecureRandom} instance used for
 *       cryptographically strong random number generation.</li>
 *   <li><b>Encodings</b> – predefined alphabets for ULID and Nano ID generation, chosen to
 *       avoid ambiguous characters and improve human usability.</li>
 * </ul>
 *
 * <h2>Behavior</h2>
 * <p>
 * Each enum constant represents a distinct identifier generation strategy and implements
 * the {@link #generate()} method.
 * </p>
 * <ul>
 *   <li>Generators may rely purely on randomness (e.g. HEX, Nano ID).</li>
 *   <li>Generators may combine randomness with time-based components (e.g. ULID).</li>
 *   <li>Generators may encode binary identifiers into compact, transport-safe string formats
 *       (e.g. Base64 URL encoding).</li>
 * </ul>
 *
 * <h2>Provided strategies</h2>
 * <ul>
 *   <li><b>{@link #UUID}</b> – Standard RFC 4122 UUID represented as a 36-character string.</li>
 *   <li><b>{@link #BASE_62_UUID}</b> – Base64 URL-safe encoding of a UUID, producing a shorter,
 *       filename- and URL-safe identifier without padding.</li>
 *   <li><b>{@link #ULID}</b> – Universally Unique Lexicographically Sortable Identifier, providing
 *       time-based ordering while preserving randomness.</li>
 *   <li><b>{@link #HEX_16}</b> – 16-character hexadecimal string (64 bits of entropy).</li>
 *   <li><b>{@link #HEX_32}</b> – 32-character hexadecimal string (128 bits of entropy).</li>
 *   <li><b>{@link #NANO_ID}</b> – Compact, URL-friendly identifier with a fixed length and
 *       alphanumeric alphabet.</li>
 * </ul>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * This enum is thread-safe. All generators are stateless and rely on thread-safe components
 * such as {@link SecureRandom}. Generated identifiers may be safely created
 * concurrently across multiple threads.
 * </p>
 *
 * <h2>Design intent</h2>
 * <p>
 * {@code EntropyGenerator} centralizes identifier generation logic to:
 * </p>
 * <ul>
 *   <li>Ensure consistent entropy and encoding choices across the codebase</li>
 *   <li>Allow easy substitution of identifier formats without affecting callers</li>
 *   <li>Provide a single, discoverable API for generating unique identifiers</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * String requestId = EntropyGenerator.ULID.generate();
 * String token     = EntropyGenerator.NANO_ID.generate();
 * }</pre>
 */
public enum EntropyGenerator {
    UUID {
        /**
         * Generates a standard UUID (Universally Unique Identifier) as a string.
         *
         * @return A string representation of a randomly generated UUID.
         */
        @Override
        public String generate() {
            return java.util.UUID.randomUUID().toString();
        }
    },

    BASE_62_UUID {
        /**
         * Generates a compact, URL-safe, Base64-encoded representation of a UUID.
         * This is shorter than the standard UUID string format and safe for use in URLs and filenames.
         *
         * @return A string representing the Base64 URL-encoded UUID, without padding.
         */
        @Override
        public String generate() {
            java.util.UUID uuid = java.util.UUID.randomUUID();
            ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
            bb.putLong(uuid.getMostSignificantBits());
            bb.putLong(uuid.getLeastSignificantBits());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bb.array());
        }
    },

    ULID {
        /**
         * Generates a ULID (Universally Unique Lexicographically Sortable Identifier).
         * The generated ULID is a 26-character string that is time-sortable due to its
         * timestamp component and includes a random component to prevent collisions.
         *
         * @return A string representation of the generated ULID.
         */
        @Override
        public String generate() {
            long time = Instant.now().toEpochMilli();
            char[] encoding = ULID_ENCODING.toCharArray();
            byte[] random = new byte[10];
            RANDOM.nextBytes(random);

            char[] chars = new char[26];

            for (int i = 9; i >= 0; i--) {
                chars[i] = encoding[(int) (time & 31)];
                time >>>= 5;
            }

            long r0 = ((random[0] & 0xFFL) << 32)
                    | ((random[1] & 0xFFL) << 24)
                    | ((random[2] & 0xFFL) << 16)
                    | ((random[3] & 0xFFL) << 8)
                    | (random[4] & 0xFFL);
            long r1 = ((random[5] & 0xFFL) << 32)
                    | ((random[6] & 0xFFL) << 24)
                    | ((random[7] & 0xFFL) << 16)
                    | ((random[8] & 0xFFL) << 8)
                    | (random[9] & 0xFFL);

            for (int i = 0; i < 8; i++) {
                chars[10 + i] = encoding[(int) ((r0 >>> (35 - (i * 5))) & 31)];
                chars[18 + i] = encoding[(int) ((r1 >>> (35 - (i * 5))) & 31)];
            }
            return new String(chars);
        }
    },

    HEX_16 {
        /**
         * Generates a 16-character hexadecimal string from 8 random bytes.
         * This provides 64 bits of entropy.
         *
         * @return A 16-character hexadecimal string.
         */
        @Override
        public String generate() {
            byte[] buf = new byte[8];
            RANDOM.nextBytes(buf);
            StringBuilder sb = new StringBuilder(16);
            for (byte b : buf) sb.append(String.format("%02x", b));
            return sb.toString();
        }
    },

    HEX_32 {
        /**
         * Generates a 32-character hexadecimal string from 16 random bytes.
         * This provides 128 bits of entropy.
         *
         * @return A 32-character hexadecimal string.
         */
        @Override
        public String generate() {
            byte[] buf = new byte[16];
            RANDOM.nextBytes(buf);
            StringBuilder sb = new StringBuilder(32);
            for (byte b : buf) sb.append(String.format("%02x", b));
            return sb.toString();
        }
    },

    NANO_ID {
        /**
         * Generates a Nano ID, a tiny, URL-friendly, unique string identifier.
         * The generated ID is 21 characters long and uses an alphabet of '0-9a-zA-Z'.
         *
         * @return A string representation of the generated Nano ID.
         */
        @Override
        public String generate() {
            int size = 21;
            StringBuilder sb = new StringBuilder(size);
            for (int i = 0; i < size; i++) {
                sb.append(NANO_ID_ENCODING.charAt(RANDOM.nextInt(NANO_ID_ENCODING.length())));
            }
            return sb.toString();
        }
    }
    ;


    private static final String ULID_ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final String NANO_ID_ENCODING = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    public abstract String generate();
}
