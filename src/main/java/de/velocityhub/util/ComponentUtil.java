package de.velocityhub.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for converting various text formats to Adventure {@link Component}s.
 *
 * <p>Supports:
 * <ul>
 *   <li>MiniMessage tags ({@code <red>}, {@code <gradient:...>}, etc.)</li>
 *   <li>Legacy colour codes ({@code &a}, {@code &l}, etc.)</li>
 *   <li>HEX colour codes in the legacy format ({@code &#RRGGBB} or {@code &x&R&R&G&G&B&B})</li>
 * </ul>
 * </p>
 */
public final class ComponentUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder()
                    .character('&')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();

    /** Pattern matching {@code &#RRGGBB} hex color codes. */
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private ComponentUtil() {}

    /**
     * Converts a string to a {@link Component}, automatically detecting
     * whether it uses MiniMessage syntax or legacy colour codes.
     *
     * <p>If the string contains {@code <} followed by any letter (MiniMessage
     * tags), it is parsed via MiniMessage. Otherwise it is treated as a legacy
     * colour-coded string.</p>
     *
     * @param text the input text, possibly {@code null}
     * @return the resulting component (empty component if {@code text} is null)
     */
    public static Component parse(String text) {
        if (text == null || text.isEmpty()) return Component.empty();

        // Convert &#RRGGBB to &x&R&R&G&G&B&B for legacy serializer compatibility
        text = convertHexCodes(text);

        // Use MiniMessage if the text contains MiniMessage-style tags
        if (text.contains("<") && text.matches(".*<[a-zA-Z/].*")) {
            return MINI_MESSAGE.deserialize(text);
        }

        return LEGACY.deserialize(text);
    }

    /**
     * Parses a legacy-formatted string (using {@code &} codes) into a
     * {@link Component}.
     *
     * @param text legacy text
     * @return the resulting component
     */
    public static Component parseLegacy(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        return LEGACY.deserialize(convertHexCodes(text));
    }

    /**
     * Parses a MiniMessage-formatted string into a {@link Component}.
     *
     * @param text MiniMessage text
     * @return the resulting component
     */
    public static Component parseMiniMessage(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        return MINI_MESSAGE.deserialize(text);
    }

    /**
     * Converts a {@link Component} back to a legacy colour-coded string.
     *
     * @param component the component to serialise
     * @return legacy string
     */
    public static String toLegacy(Component component) {
        return LEGACY.serialize(component);
    }

    /**
     * Strips all formatting (colours, decorations) from the given text.
     *
     * @param text raw text (legacy or MiniMessage)
     * @return plain text without any formatting
     */
    public static String stripFormatting(String text) {
        if (text == null) return "";
        Component component = parse(text);
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(component);
    }

    /**
     * Replaces common placeholders in a string with the supplied values.
     *
     * <p>Placeholders use the {@code %key%} syntax.</p>
     *
     * @param text         the template string
     * @param replacements alternating key-value pairs ({@code key1, val1, key2, val2, …})
     * @return the string with replacements applied
     */
    public static String replace(String text, String... replacements) {
        if (text == null) return "";
        if (replacements.length % 2 != 0) {
            throw new IllegalArgumentException("replacements must be provided in key-value pairs");
        }
        for (int i = 0; i < replacements.length; i += 2) {
            text = text.replace("%" + replacements[i] + "%", replacements[i + 1]);
        }
        return text;
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    /**
     * Converts {@code &#RRGGBB} codes to the Bukkit/legacy
     * {@code &x&R&R&G&G&B&B} format accepted by the legacy serializer.
     */
    private static String convertHexCodes(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("&x");
            for (char c : hex.toCharArray()) {
                replacement.append('&').append(c);
            }
            matcher.appendReplacement(sb, replacement.toString());
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
