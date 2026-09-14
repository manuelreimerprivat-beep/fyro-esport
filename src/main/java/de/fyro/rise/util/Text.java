package de.fyro.rise.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

public final class Text {
    private static final Component PREFIX = Component.text("[FYRO: RISE] ", NamedTextColor.GOLD);

    private Text() {}

    public static void send(CommandSender target, String message) {
        target.sendMessage(PREFIX.append(Component.text(message, NamedTextColor.GRAY)));
    }

    public static void success(CommandSender target, String message) {
        target.sendMessage(PREFIX.append(Component.text(message, NamedTextColor.GREEN)));
    }

    public static void error(CommandSender target, String message) {
        target.sendMessage(PREFIX.append(Component.text(message, NamedTextColor.RED)));
    }

    public static Component name(String value) {
        return Component.text(value, NamedTextColor.GOLD);
    }
}
