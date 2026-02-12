package config;

public class OssConfig {
    public String endpoint;         // 用户提供
    public String region;           // cn-hangzhou
    public String bucket;           // ai-curator
    public String accessKeyId;      // 永久AK
    public String accessKeySecret;  // 永久SK
    public boolean useCname;        // endpoint 是否 CNAME

    public boolean isComplete() {
        return notBlank(endpoint) && notBlank(region) && notBlank(bucket)
                && notBlank(accessKeyId) && notBlank(accessKeySecret);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
