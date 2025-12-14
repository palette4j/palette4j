package io.github.palette4j.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Defines a set of supported HMAC (Hash-based Message Authentication Code) algorithms
 * and provides convenient, reusable methods for computing message authentication codes.
 * <p>
 * This enum acts as a thin, type-safe façade over the Java Cryptography Architecture (JCA),
 * encapsulating algorithm selection, {@link Mac} lifecycle management,
 * and common output encodings (hexadecimal and Base64).
 * </p>
 *
 * <h2>State</h2>
 * <ul>
 *   <li><b>JCA algorithm name</b> – the standard JCA identifier (e.g. {@code "HmacSHA256"})
 *       used to obtain a {@link Mac} instance.</li>
 *   <li><b>Thread-local MAC instance</b> – a {@link ThreadLocal} holding a {@link Mac}
 *       per thread, avoiding repeated instantiation while remaining safe for concurrent use.</li>
 * </ul>
 *
 * <h2>Behavior</h2>
 * <p>
 * Each enum constant represents a specific HMAC algorithm and provides methods to:
 * </p>
 * <ul>
 *   <li>Compute raw HMAC bytes from a secret key and message</li>
 *   <li>Encode the result as a hexadecimal string</li>
 *   <li>Encode the result as a Base64 string</li>
 * </ul>
 * <p>
 * Internally, MAC instances are reset and re-initialized on each invocation to ensure
 * correctness and isolation between calls.
 * </p>
 *
 * <h2>Supported algorithms</h2>
 * <ul>
 *   <li><b>{@link #SHA256}</b> – HMAC with SHA-256, widely used and suitable for most applications.</li>
 *   <li><b>{@link #SHA384}</b> – HMAC with SHA-384, offering increased security margin.</li>
 *   <li><b>{@link #SHA512}</b> – HMAC with SHA-512, providing the highest output size and entropy.</li>
 * </ul>
 *
 * <h2>Error handling</h2>
 * <p>
 * Unsupported algorithms result in an {@link IllegalStateException} during enum initialization,
 * as such a configuration error is considered unrecoverable at runtime.
 * </p>
 * <p>
 * Invalid keys supplied to HMAC operations result in an {@link IllegalArgumentException}.
 * </p>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * This enum is thread-safe. Each thread operates on its own {@link Mac}
 * instance via {@link ThreadLocal}, preventing shared mutable state and eliminating
 * the need for external synchronization.
 * </p>
 *
 * <h2>Security considerations</h2>
 * <ul>
 *   <li>HMAC provides message integrity and authenticity but does not provide encryption.</li>
 *   <li>Callers are responsible for secure key generation, storage, and rotation.</li>
 *   <li>String-based methods convert inputs to bytes using a specified {@link Charset};
 *       UTF-8 is used by default.</li>
 * </ul>
 *
 * <h2>Design intent</h2>
 * <p>
 * {@code HmacAlgorithm} exists to:
 * </p>
 * <ul>
 *   <li>Centralize and standardize HMAC usage across the codebase</li>
 *   <li>Prevent repeated, error-prone JCA setup code</li>
 *   <li>Expose a small, expressive API for common HMAC output formats</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * String signature =
 *     HmacAlgorithm.SHA256.hex("secret-key", "payload");
 *
 * String token =
 *     HmacAlgorithm.SHA512.base64("secret-key", "payload");
 * }</pre>
 */
public enum HmacAlgorithm {

    SHA256("HmacSHA256"),
    SHA384("HmacSHA384"),
    SHA512("HmacSHA512");

    private final String jcaName;
    private final ThreadLocal<Mac> macThreadLocal;

    HmacAlgorithm(String jcaName) {
        this.jcaName = jcaName;
        this.macThreadLocal = ThreadLocal.withInitial(() -> {
            try {
                return Mac.getInstance(jcaName);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("HMAC algorithm not supported: " + jcaName, e);
            }
        });
    }

    /**
     * Computes the HMAC (Hash-based Message Authentication Code) for a given message using a secret key.
     *
     * @param key     the secret key to use for the HMAC computation.
     * @param message the message to authenticate.
     * @return a byte array containing the HMAC result.
     */
    public byte[] hmac(byte[] key, byte[] message) {
        try {
            Mac mac = macThreadLocal.get();
            mac.reset();
            mac.init(new SecretKeySpec(key, jcaName));
            return mac.doFinal(message);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("Invalid HMAC key", e);
        }
    }

    /**
     * Computes the HMAC for a given message and returns it as a hexadecimal string.
     * This method uses UTF-8 encoding to convert the key and message strings to bytes.
     *
     * @param key     the secret key to use for the HMAC computation.
     * @param message the message to authenticate.
     * @return the HMAC result as a hexadecimal string.
     */
    public String hex(String key, String message) {
        return hex(key, message, StandardCharsets.UTF_8);
    }

    /**
     * Computes the HMAC for a given message and returns it as a hexadecimal string.
     *
     * @param key     the secret key to use for the HMAC computation.
     * @param message the message to authenticate.
     * @param charset the character set to use for converting the key and message to bytes.
     * @return the HMAC result as a hexadecimal string.
     */
    public String hex(String key, String message, Charset charset) {
        byte[] keyBytes = key.getBytes(charset);
        byte[] messageBytes = message.getBytes(charset);
        byte[] hmacBytes = hmac(keyBytes, messageBytes);
        return toHex(hmacBytes);
    }

    /**
     * Computes the HMAC for a given message and returns it as a Base64 encoded string.
     * This method uses UTF-8 encoding to convert the key and message strings to bytes.
     *
     * @param key     the secret key to use for the HMAC computation.
     * @param message the message to authenticate.
     * @return the HMAC result as a Base64 encoded string.
     */
    public String base64(String key, String message) {
        return base64(key, message, StandardCharsets.UTF_8);
    }

    /**
     * Computes the HMAC for a given message and returns it as a Base64 encoded string.
     *
     * @param key     the secret key to use for the HMAC computation.
     * @param message the message to authenticate.
     * @param charset the character set to use for converting the key and message to bytes.
     * @return the HMAC result as a Base64 encoded string.
     */
    public String base64(String key, String message, Charset charset) {
        byte[] keyBytes = key.getBytes(charset);
        byte[] messageBytes = message.getBytes(charset);

        byte[] hmacBytes = hmac(keyBytes, messageBytes);

        return java.util.Base64.getEncoder().encodeToString(hmacBytes);
    }


    private static String toHex(byte[] bytes) {
        char[] hexArray = "0123456789abcdef".toCharArray();
        char[] hexChars = new char[bytes.length * 2];

        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = hexArray[v >>> 4];
            hexChars[i * 2 + 1] = hexArray[v & 0x0F];
        }

        return new String(hexChars);
    }
}
