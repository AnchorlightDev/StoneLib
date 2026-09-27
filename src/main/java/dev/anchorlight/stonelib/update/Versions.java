package dev.anchorlight.stonelib.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Compares release versions the way people write them: {@code v1.2.0}, {@code 1.2}, {@code 1.10.0},
 * {@code 2.0.0-beta.1}.
 *
 * <p>A leading {@code v} is ignored, missing numeric parts count as zero ({@code 1.2 == 1.2.0}),
 * numbers compare numerically ({@code 1.10 > 1.9}), and a pre-release suffix sorts before the
 * release it precedes ({@code 2.0.0-beta < 2.0.0}). Build metadata after {@code +} is ignored.
 */
public final class Versions {

    private Versions() {
        throw new IllegalStateException("Utility class shouldn't be instantiated");
    }

    /** Negative, zero or positive as {@code a} is older than, equal to or newer than {@code b}. */
    public static int compare(String a, String b) {
        Parsed left = parse(a);
        Parsed right = parse(b);
        int length = Math.max(left.numbers.size(), right.numbers.size());
        for (int i = 0; i < length; i++) {
            long x = i < left.numbers.size() ? left.numbers.get(i) : 0;
            long y = i < right.numbers.size() ? right.numbers.get(i) : 0;
            if (x != y) {
                return Long.compare(x, y);
            }
        }
        if (left.preRelease.isEmpty() != right.preRelease.isEmpty()) {
            return left.preRelease.isEmpty() ? 1 : -1;
        }
        return left.preRelease.compareTo(right.preRelease);
    }

    private record Parsed(List<Long> numbers, String preRelease) {
    }

    private static Parsed parse(String raw) {
        String version = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (version.startsWith("v")) {
            version = version.substring(1);
        }
        int plus = version.indexOf('+');
        if (plus >= 0) {
            version = version.substring(0, plus);
        }
        String pre = "";
        int dash = version.indexOf('-');
        if (dash >= 0) {
            pre = version.substring(dash + 1);
            version = version.substring(0, dash);
        }
        List<Long> numbers = new ArrayList<>();
        for (String part : version.split("\\.")) {
            String digits = part.replaceAll("\\D.*$", "");
            if (digits.isEmpty()) {
                break;
            }
            try {
                numbers.add(Long.parseLong(digits));
            } catch (NumberFormatException ex) {
                break;
            }
        }
        return new Parsed(numbers, pre);
    }
}
