package com.bjworld21.conference.service;

import com.bjworld21.conference.config.PromotionalMailProperties;
import com.bjworld21.conference.config.UploadProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailContentImageServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void storesDetectedPngAndLoadsItAgain() throws Exception {
        PromotionalMailProperties properties = properties();
        MailContentImageService service = new MailContentImageService(properties, uploadStorage());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), "png", output);
        MockMultipartFile file = new MockMultipartFile(
                "upload", "본문 이미지.png", "application/octet-stream", output.toByteArray()
        );

        MailContentImageService.StoredContentImage stored = service.store(file);
        MailContentImageService.StoredImageResource loaded = service.load(stored.savedFilename());

        assertThat(stored.savedFilename()).matches("[0-9]{6}/.+\\.png");
        assertThat(temporaryDirectory.resolve("mail").resolve(stored.savedFilename())).isRegularFile();
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.width()).isEqualTo(2);
        assertThat(stored.height()).isEqualTo(3);
        assertThat(loaded.resource().exists()).isTrue();
        assertThat(loaded.contentType()).isEqualTo("image/png");
    }

    @Test
    void rejectsNonImageContentEvenWhenFilenameLooksLikeImage() {
        MailContentImageService service = new MailContentImageService(properties(), uploadStorage());
        MockMultipartFile file = new MockMultipartFile(
                "upload", "fake.png", "image/png", "not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JPG, PNG, GIF");
    }

    private PromotionalMailProperties properties() {
        PromotionalMailProperties properties = new PromotionalMailProperties();
        properties.setMaxContentImageBytes(5L * 1024L * 1024L);
        return properties;
    }

    private UploadStorage uploadStorage() {
        UploadProperties properties = new UploadProperties();
        properties.setBaseDirectory(temporaryDirectory.toString());
        return new UploadStorage(properties);
    }
}
