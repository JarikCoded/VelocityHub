package de.velocityhub.test;

import de.velocityhub.util.ComponentUtil;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ComponentUtil}.
 */
class ComponentUtilTest {

    @Test
    void parse_null_returnsEmptyComponent() {
        Component result = ComponentUtil.parse(null);
        assertNotNull(result);
        assertEquals(Component.empty(), result);
    }

    @Test
    void parse_empty_returnsEmptyComponent() {
        Component result = ComponentUtil.parse("");
        assertNotNull(result);
        assertEquals(Component.empty(), result);
    }

    @Test
    void parse_legacyColorCode_parsesSuccessfully() {
        // Should not throw
        Component result = ComponentUtil.parse("&aHello World");
        assertNotNull(result);
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(result);
        assertEquals("Hello World", plain);
    }

    @Test
    void parse_hexColorCode_parsesSuccessfully() {
        // &#FF5500 hex format
        Component result = ComponentUtil.parse("&#FF5500Hello");
        assertNotNull(result);
    }

    @Test
    void parse_miniMessageFormat_parsesSuccessfully() {
        Component result = ComponentUtil.parse("<red>Hello</red>");
        assertNotNull(result);
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(result);
        assertEquals("Hello", plain);
    }

    @Test
    void stripFormatting_removesColorCodes() {
        String result = ComponentUtil.stripFormatting("&aHello &bWorld");
        assertEquals("Hello World", result);
    }

    @Test
    void replace_substitutesPlaceholders() {
        String result = ComponentUtil.replace(
                "Hello %player%, welcome to %server%!",
                "player", "Steve",
                "server", "lobby-1");
        assertEquals("Hello Steve, welcome to lobby-1!", result);
    }

    @Test
    void replace_null_returnsEmpty() {
        String result = ComponentUtil.replace(null, "player", "Steve");
        assertEquals("", result);
    }

    @Test
    void replace_missingPair_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                ComponentUtil.replace("Hello %player%", "player"));
    }

    @Test
    void parseLegacy_parsesBoldCode() {
        Component result = ComponentUtil.parseLegacy("&lBold Text");
        assertNotNull(result);
    }

    @Test
    void parseMiniMessage_parsesGradient() {
        Component result = ComponentUtil.parseMiniMessage(
                "<gradient:red:blue>Gradient</gradient>");
        assertNotNull(result);
    }
}
