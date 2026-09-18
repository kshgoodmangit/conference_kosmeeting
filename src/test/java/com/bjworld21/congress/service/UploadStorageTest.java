package com.bjworld21.congress.service;

import com.bjworld21.congress.config.UploadProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadStorageTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsMenuAndYearMonthRelativeTarget() {
        UploadProperties properties = new UploadProperties();
        properties.setBaseDirectory(temporaryDirectory.toString());
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-25T00:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        UploadStorage storage = new UploadStorage(properties, clock);

        UploadStorage.StoredTarget target = storage.monthlyTarget(UploadStorage.BOARDS, "sample.pdf");

        assertThat(target.relativePath()).isEqualTo("202608/sample.pdf");
        assertThat(target.path()).isEqualTo(
                temporaryDirectory.resolve("boards").resolve("202608").resolve("sample.pdf").toAbsolutePath()
        );
    }

    @Test
    void rejectsPathTraversal() {
        UploadProperties properties = new UploadProperties();
        properties.setBaseDirectory(temporaryDirectory.toString());
        UploadStorage storage = new UploadStorage(properties);

        assertThatThrownBy(() -> storage.resolve(UploadStorage.MAIL, "../../outside.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("저장 파일 경로");
    }
}
