package com.linlibang.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 配置类
 * 读取 application.yml 中的 aliyun.oss 配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssConfig {

    /** 地域节点，如 oss-cn-hangzhou.aliyuncs.com */
    private String endpoint;

    /** AccessKey ID */
    private String accessKeyId;

    /** AccessKey Secret */
    private String accessKeySecret;

    /** Bucket 名称 */
    private String bucketName;

    /** 访问域名，用于拼接完整文件URL */
    private String baseUrl;

    /** 上传文件大小限制（MB） */
    private Integer maxSize;
}
