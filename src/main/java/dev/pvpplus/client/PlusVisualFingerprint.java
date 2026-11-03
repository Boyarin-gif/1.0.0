package dev.pvpplus.client;

import dev.pvpplus.PvpPlus;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Deterministic SHA-256 of the shipped Plus Visuals files (not the whole modpack). */
public final class PlusVisualFingerprint {
    private static final String VALUE = calculate();

    private PlusVisualFingerprint() {}

    public static String value() { return VALUE; }
    public static boolean available() { return !"unavailable".equals(VALUE); }

    private static String calculate() {
        try {
            URI uri = PvpPlus.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path location = Path.of(uri);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (Files.isDirectory(location)) {
                try (var paths = Files.walk(location)) {
                    for (Path file : paths.filter(Files::isRegularFile)
                            .sorted(Comparator.comparing(p -> location.relativize(p).toString())).toList()) {
                        update(digest, location.relativize(file).toString().replace('\\', '/'), Files.newInputStream(file));
                    }
                }
            } else {
                try (ZipFile zip = new ZipFile(location.toFile())) {
                    var entries = zip.stream().filter(e -> !e.isDirectory())
                            .sorted(Comparator.comparing(ZipEntry::getName)).toList();
                    for (ZipEntry entry : entries) update(digest, entry.getName(), zip.getInputStream(entry));
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ignored) {
            // Never claim compatibility when the local mod files could not be fingerprinted.
            return "unavailable";
        }
    }

    private static void update(MessageDigest digest, String name, InputStream input) throws Exception {
        try (input) {
            digest.update(name.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
            digest.update((byte) 0);
        }
    }
}
