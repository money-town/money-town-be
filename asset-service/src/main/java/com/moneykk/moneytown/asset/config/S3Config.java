package com.moneykk.moneytown.asset.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** S3 클라이언트 설정 */
@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client(
            @Value("${aws.region}") String region
    ) {
        // 파일 업로드와 삭제에 사용
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(
            @Value("${aws.region}") String region
    ) {
        // 임시 다운로드 URL 발급에 사용
        return S3Presigner.builder()
                .region(Region.of(region))
                .build();
    }
}