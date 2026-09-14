package de.fyro.rise.util;

import java.util.regex.Pattern;

public final class GameRules {
    private static final Pattern GUILD_NAME = Pattern.compile("[A-Za-zÄÖÜäöüß0-9 _-]{3,20}");

    private GameRules() {}

    public static boolean validGuildName(String value) {
        return value != null && GUILD_NAME.matcher(value.trim()).matches();
    }

    public static boolean validTransfer(double amount, double balance) {
        return Double.isFinite(amount) && amount > 0 && Double.isFinite(balance) && balance >= amount;
    }

    public static double roundedMoney(double amount) {
        if (!Double.isFinite(amount)) return 0;
        return Math.round(Math.max(0, amount) * 100.0) / 100.0;
    }
}
