package com.campus.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MinioConfig {

    @Bean({"minioClient", "internalMinioClient"})
    @Primary
    public MinioClient internalMinioClient(CampusProperties properties) {
        CampusProperties.Minio minio = properties.getMinio();
        return buildClient(minio.getEffectiveInternalEndpoint(), minio);
    }

    @Bean
    public MinioClient publicMinioClient(CampusProperties properties) {
        CampusProperties.Minio minio = properties.getMinio();
        return buildClient(minio.getEffectivePublicEndpoint(), minio);
    }

    @Bean
    public MinioClient uploadMinioClient(CampusProperties properties) {
        CampusProperties.Minio minio = properties.getMinio();
        return buildClient(minio.getEffectiveUploadEndpoint(), minio);
    }

    private MinioClient buildClient(String endpoint, CampusProperties.Minio minio) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
    }
}
