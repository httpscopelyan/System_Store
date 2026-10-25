package br.com.scopel.pfv.beta.update;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Versão semântica (semver.org): MAJOR.MINOR.PATCH[-pré-release][+build].
 * Metadados de build são ignorados na comparação; uma pré-release é sempre
 * menor que a versão final correspondente (1.2.0-beta &lt; 1.2.0).
 */
public record SemVer(long major, long minor, long patch, List<String> preRelease)
        implements Comparable<SemVer> {

    private static final Pattern PATTERN = Pattern.compile(
            "^v?(\\d+)\\.(\\d+)\\.(\\d+)"
                    + "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?"
                    + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$");

    public static SemVer parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Versão ausente.");
        }
        Matcher m = PATTERN.matcher(text.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Versão inválida (esperado X.Y.Z): '" + text + "'");
        }
        try {
            List<String> pre = m.group(4) == null ? List.of() : List.of(m.group(4).split("\\."));
            return new SemVer(Long.parseLong(m.group(1)), Long.parseLong(m.group(2)),
                    Long.parseLong(m.group(3)), pre);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Versão fora do intervalo: '" + text + "'");
        }
    }

    public static Optional<SemVer> tryParse(String text) {
        try {
            return Optional.of(parse(text));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public boolean isNewerThan(SemVer other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(SemVer o) {
        int c = Long.compare(major, o.major);
        if (c != 0) return c;
        c = Long.compare(minor, o.minor);
        if (c != 0) return c;
        c = Long.compare(patch, o.patch);
        if (c != 0) return c;
        if (preRelease.isEmpty() || o.preRelease.isEmpty()) {
            // Sem pré-release (versão final) é maior que com pré-release.
            return Boolean.compare(preRelease.isEmpty(), o.preRelease.isEmpty());
        }
        int n = Math.min(preRelease.size(), o.preRelease.size());
        for (int i = 0; i < n; i++) {
            c = compareIdentifier(preRelease.get(i), o.preRelease.get(i));
            if (c != 0) return c;
        }
        return Integer.compare(preRelease.size(), o.preRelease.size());
    }

    private static int compareIdentifier(String a, String b) {
        boolean aNum = a.chars().allMatch(Character::isDigit);
        boolean bNum = b.chars().allMatch(Character::isDigit);
        if (aNum && bNum) return new BigInteger(a).compareTo(new BigInteger(b));
        if (aNum) return -1; // numérico < alfanumérico
        if (bNum) return 1;
        return a.compareTo(b);
    }

    @Override
    public String toString() {
        String base = major + "." + minor + "." + patch;
        return preRelease.isEmpty() ? base : base + "-" + String.join(".", preRelease);
    }
}
