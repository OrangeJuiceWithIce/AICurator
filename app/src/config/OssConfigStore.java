package config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

public final class OssConfigStore {

    private static final Path FILE = Paths.get("config").resolve("oss.properties");

    private OssConfigStore() {}

    public static OssConfig load() {
        OssConfig c = new OssConfig();
        c.region = "cn-hangzhou";
        c.bucket = "ai-curator";
        c.endpoint = "https://oss-cn-hangzhou.aliyuncs.com";
        c.useCname = false;
        c.accessKeyId = "";
        c.accessKeySecret = "";

        if (!Files.exists(FILE)) return c;

        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception ignored) {}

        c.endpoint = p.getProperty("endpoint", c.endpoint).trim();
        c.region   = p.getProperty("region", c.region).trim();
        c.bucket   = p.getProperty("bucket", c.bucket).trim();
        c.accessKeyId     = p.getProperty("accessKeyId", "").trim();
        c.accessKeySecret = p.getProperty("accessKeySecret", "").trim();
        c.useCname = "1".equals(p.getProperty("useCname", c.useCname ? "1" : "0").trim());

        return c;
    }

    public static void save(OssConfig c) throws IOException {
        Properties p = new Properties();
        p.setProperty("endpoint", safe(c.endpoint));
        p.setProperty("region", safe(c.region));
        p.setProperty("bucket", safe(c.bucket));
        p.setProperty("accessKeyId", safe(c.accessKeyId));
        p.setProperty("accessKeySecret", safe(c.accessKeySecret));
        p.setProperty("useCname", c.useCname ? "1" : "0");

        Files.createDirectories(FILE.getParent());
        try (OutputStream out = Files.newOutputStream(FILE,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            p.store(new OutputStreamWriter(out, StandardCharsets.UTF_8), "OSS config");
        }
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }
}