import io.github.palette4j.util.Localization;
import java.util.Locale;
import java.util.function.BiFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LocalizationTest {

    private final Locale originalDefaultLocale = Locale.getDefault();

    @AfterEach
    void restoreDefaultLocale() {
        Locale.setDefault(originalDefaultLocale);
    }

    @Test
    @DisplayName("embedded() --- uses default locale and formats as is")
    void embedded_withoutArgs_usesDefaultLocale_andFormatsAsIs() {
        Locale.setDefault(Locale.US);

        Localization loc = Localization.embedded("Hello");

        assertEquals(Locale.US, loc.getLocale());
        assertNull(loc.getLocalizationKey());
        assertEquals("Hello", loc.getEmbeddedMessage());
        assertArrayEquals(new Object[0], loc.getMessageArgs());
        assertEquals("Hello", loc.getEmbeddedLocalizedMessage());
    }

    @Test
    @DisplayName("embedded() --- uses default locale and formats with args")
    void embedded_withArgs_formatsEmbeddedMessage() {
        Locale.setDefault(Locale.US);

        Localization loc = Localization.embedded("Hello, {0}! Your id is {1}.", "Alice", 42);

        assertEquals("Hello, Alice! Your id is 42.", loc.getEmbeddedLocalizedMessage());
    }

    @Test
    @DisplayName("resolveExternally() --- throw IllegalArgumentException when resolver is null")
    void resolveExternally_throwsOnNullResolver() {
        Localization loc = Localization.embedded("fallback");

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> loc.resolveExternally(null));

        assertTrue(ex.getMessage().toLowerCase().contains("localizationresolver"));
    }

    @Test
    @DisplayName("resolveExternally() --- returns embedded message when resolver returns null")
    void resolveExternally_whenResolverReturnsNull_fallsBackToEmbedded_andFormats() {
        Localization loc = Localization.embedded("File {0} not found", "a.txt");

        BiFunction<Locale, String, String> resolver = (locale, key) -> null;

        assertEquals("File a.txt not found", loc.resolveExternally(resolver));
    }

    @Test
    @DisplayName("resolveExternally() --- returns resolved message when resolver returns template")
    void resolveExternally_whenResolverReturnsTemplate_formatsResolvedMessage() {
        Localization loc = Localization.builder()
                .locale(Locale.US)
                .localizationKey("user.welcome")
                .embeddedMessage("fallback {0}")
                .messageArgs("Alice")
                .build();

        BiFunction<Locale, String, String> resolver =
                (locale, key) -> "Welcome, {0}!";

        assertEquals("Welcome, Alice!", loc.resolveExternally(resolver));
    }

    @Test
    @DisplayName("resolveExternally() --- uses locale and key passed to resolver")
    void resolveExternally_whenResolverReturnsTemplate_usesLocaleAndKeyPassedToResolver() {
        Localization loc = Localization.builder()
                .locale(Locale.FRANCE)
                .localizationKey("k1")
                .embeddedMessage("fallback")
                .build();

        final Locale[] seenLocale = new Locale[1];
        final String[] seenKey = new String[1];

        BiFunction<Locale, String, String> resolver = (locale, key) -> {
            seenLocale[0] = locale;
            seenKey[0] = key;
            return null;
        };

        loc.resolveExternally(resolver);

        assertEquals(Locale.FRANCE, seenLocale[0]);
        assertEquals("k1", seenKey[0]);
    }

    @Test
    @DisplayName("getEmbeddedLocalizedMessage() --- returns null when embeddedMessage is null")
    void getEmbeddedLocalizedMessage_returnsNullWhenEmbeddedMessageIsNull() {
        Localization loc = Localization.builder()
                .locale(Locale.US)
                .localizationKey("k")
                .embeddedMessage(null)
                .build();

        assertNull(loc.getEmbeddedLocalizedMessage());
    }

    @Test
    @DisplayName("builder() --- locale string tag is valid")
    void builder_localeString_valid() {
        Localization loc = Localization.builder()
                .locale("en-US")
                .localizationKey("k")
                .embeddedMessage("Hi")
                .build();

        assertEquals(Locale.forLanguageTag("en-US"), loc.getLocale());
    }

    @Test
    @DisplayName("buidler() --- locale string tag is null")
    void builder_localeString_blank_throws() {
        assertThrows(IllegalArgumentException.class, () -> Localization.builder().locale(""));
        assertThrows(IllegalArgumentException.class, () -> Localization.builder().locale((String) null));
    }

    @Test
    @DisplayName("builder() --- localizationKey string tag is blank")
    void builder_localizationKey_blank_throws() {
        assertThrows(IllegalArgumentException.class, () -> Localization.builder().localizationKey(""));
        assertThrows(IllegalArgumentException.class, () -> Localization.builder().localizationKey(null));
    }

    @Test
    @DisplayName("builder() --- messageArgs array is defensively copied")
    void messageArgs_areDefensivelyCopied_inConstructorAndGetterReflectsInternalArray() {
        Object[] args = new Object[]{"Alice", 1};

        Localization loc = Localization.embedded("x {0} {1}", args);
        args[0] = "Bob";

        assertEquals("x Alice 1", loc.getEmbeddedLocalizedMessage());
        assertArrayEquals(new Object[]{"Alice", 1}, loc.getMessageArgs());
    }

    @Test
    @DisplayName("equals() and hashCode() --- work for the same content")
    void equalsAndHashCode_workForSameContent() {
        Localization a = Localization.builder()
                .locale(Locale.US)
                .localizationKey("k")
                .embeddedMessage("m {0}")
                .messageArgs("x")
                .build();

        Localization b = Localization.builder()
                .locale(Locale.US)
                .localizationKey("k")
                .embeddedMessage("m {0}")
                .messageArgs("x")
                .build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
