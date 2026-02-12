package oss;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class ObjectKeyBuilder {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private ObjectKeyBuilder() {}

    public static String buildRecycleKey(String fullpath, String filename) {
        String day = LocalDate.now().format(D);
        String h = sha1Hex(fullpath);
        return "recycle/" + day + "/" + h + "_" + safeName(filename);
    }

    private static String safeName(String s) {
        if (s == null) return "unknown";
        return s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String sha1Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] b = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(Math.abs(s.hashCode()));
        }
    }
}
