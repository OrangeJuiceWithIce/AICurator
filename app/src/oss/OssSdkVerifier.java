package oss;

public final class OssSdkVerifier {
    private OssSdkVerifier() {}

    public static void verifyPresent() {
        try {
            Class.forName("com.aliyun.oss.OSS");
            Class.forName("com.aliyun.oss.OSSClientBuilder");
            Class.forName("com.aliyun.oss.common.comm.SignVersion");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("未检测到 OSS Java SDK V1（com.aliyun.oss）。请确认已引入 aliyun-sdk-oss 3.17.4。");
        }
    }
}
