package oss;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import config.OssConfig;

public final class OssClientFactory {

    private OssClientFactory() {}

    public static OSS build(OssConfig cfg) {
        DefaultCredentialProvider provider =
                new DefaultCredentialProvider(cfg.accessKeyId, cfg.accessKeySecret);

        ClientBuilderConfiguration c = new ClientBuilderConfiguration();
        c.setSignatureVersion(SignVersion.V4);
        c.setSupportCname(cfg.useCname);

        return OSSClientBuilder.create()
                .endpoint(cfg.endpoint)
                .credentialsProvider(provider)
                .clientConfiguration(c)
                .region(cfg.region) // V4 必须
                .build();
    }
}
