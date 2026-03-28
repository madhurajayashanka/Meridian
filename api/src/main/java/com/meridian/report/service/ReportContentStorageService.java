package com.meridian.report.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class ReportContentStorageService {

    private static final String LOCAL_PREFIX = "local://report/";

    private final Path baseDir;

    public ReportContentStorageService(
        @Value("${report.content.dir:/tmp/meridian-reports}") String baseDir
    ) {
        this.baseDir = Paths.get(baseDir);
    }

    public String storeReportContent(UUID reportId, String content) {
        try {
            Files.createDirectories(baseDir);
            Path reportPath = baseDir.resolve(reportId + ".md");
            Files.writeString(reportPath, content, StandardCharsets.UTF_8);
            return LOCAL_PREFIX + reportPath.getFileName();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store report content for " + reportId, e);
        }
    }

    public Optional<String> readReportContent(String s3Key) {
        if (s3Key == null || !s3Key.startsWith(LOCAL_PREFIX)) {
            return Optional.empty();
        }

        try {
            String filename = s3Key.substring(LOCAL_PREFIX.length());
            Path reportPath = baseDir.resolve(filename).normalize();
            if (!reportPath.startsWith(baseDir.normalize()) || !Files.exists(reportPath)) {
                return Optional.empty();
            }
            return Optional.of(Files.readString(reportPath, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("Failed to read report content for key {}", s3Key, e);
            return Optional.empty();
        }
    }
}
