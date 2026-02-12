package oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.PutObjectRequest;
import config.OssConfig;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class OssRecycleService {

    private OssRecycleService() {}

    /**
     * ✅ 测试连接：不仅检查 bucket 是否存在，还会真正 PutObject 一次（并清理测试对象）
     * 这样“测试通过”就等价于“删除前上传一定可用”。
     */
    public static void testConnection(OssConfig cfg) {
        OssSdkVerifier.verifyPresent();
        if (cfg == null || !cfg.isComplete()) {
            throw new RuntimeException("OSS 配置不完整：endpoint/region/bucket/accessKeyId/accessKeySecret 必填。");
        }

        OSS oss = null;
        try {
            oss = OssClientFactory.build(cfg);

            boolean ok = oss.doesBucketExist(cfg.bucket);
            if (!ok) throw new RuntimeException("Bucket 不存在或无权限: " + cfg.bucket);

            // ✅ 验证 PutObject 权限：写一个很小的测试对象
            String testKey = "aicurator-test/put_test_" + System.currentTimeMillis() + ".txt";
            byte[] data = "ok".getBytes(StandardCharsets.UTF_8);
            oss.putObject(cfg.bucket, testKey, new ByteArrayInputStream(data));

            // 可选：删除测试对象，避免污染 bucket
            try { oss.deleteObject(cfg.bucket, testKey); } catch (Exception ignored) {}

        } finally {
            if (oss != null) oss.shutdown();
        }
    }

    /** 上传成功返回 objectKey（用于日志记录，供“撤回”恢复） */
    public static String uploadToRecycle(OssConfig cfg, File localFile, String fullpath) {
        OssSdkVerifier.verifyPresent();
        if (cfg == null || !cfg.isComplete()) {
            throw new RuntimeException("OSS 未配置，请先在“OSS设置”里填写并测试。");
        }
        if (localFile == null || !localFile.exists() || !localFile.isFile()) {
            throw new RuntimeException("本地文件不存在或不是普通文件");
        }

        String objectKey = ObjectKeyBuilder.buildRecycleKey(fullpath, localFile.getName());

        OSS oss = null;
        try {
            oss = OssClientFactory.build(cfg);
            PutObjectRequest req = new PutObjectRequest(cfg.bucket, objectKey, localFile);
            oss.putObject(req);

            // ✅ 强校验：上传后立刻确认对象存在（避免“请求返回但实际没落库”）
            boolean exists = oss.doesObjectExist(cfg.bucket, objectKey);
            if (!exists) {
                throw new RuntimeException("上传返回但对象不存在：bucket=" + cfg.bucket + ", key=" + objectKey);
            }

            return objectKey;
        } finally {
            if (oss != null) oss.shutdown();
        }
    }

    /**
     * ✅ 撤回恢复：把 OSS 上 objectKey 下载回原路径 destFullpath
     * @param deleteRemoteAfter  恢复成功后是否删除云端备份（建议默认否）
     */
    public static void restoreToOriginal(OssConfig cfg, String objectKey, String destFullpath, boolean deleteRemoteAfter) {
        OssSdkVerifier.verifyPresent();
        if (cfg == null || !cfg.isComplete()) throw new RuntimeException("OSS 配置不完整");
        if (objectKey == null || objectKey.trim().isEmpty()) throw new RuntimeException("objectKey 为空");
        if (destFullpath == null || destFullpath.trim().isEmpty()) throw new RuntimeException("destFullpath 为空");

        OSS oss = null;
        try {
            oss = OssClientFactory.build(cfg);

            if (!oss.doesObjectExist(cfg.bucket, objectKey)) {
                throw new RuntimeException("OSS 上不存在该对象：bucket=" + cfg.bucket + ", key=" + objectKey);
            }

            File dest = new File(destFullpath);
            Path parent = dest.toPath().getParent();
            if (parent != null) Files.createDirectories(parent);

            // ✅ 下载到原路径
            oss.getObject(new GetObjectRequest(cfg.bucket, objectKey), dest);

            // 可选：恢复成功后删除云端备份
            if (deleteRemoteAfter) {
                oss.deleteObject(cfg.bucket, objectKey);
            }

        } catch (Exception e) {
            // 保持对外一致：用 RuntimeException 抛出去，让 UI 捕获并提示
            throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException(e);
        } finally {
            if (oss != null) oss.shutdown();
        }
    }
}
