package com.bjworld21.conference.service;

import com.bjworld21.conference.config.BoardProperties;
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

class BoardContentImageServiceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void storesDetectedImageAndLoadsIt() throws Exception {
        BoardProperties properties = new BoardProperties();
        BoardContentImageService service = new BoardContentImageService(properties, uploadStorage());
        MockMultipartFile file = new MockMultipartFile(
                "upload",
                "program.png",
                "image/png",
                pngBytes()
        );

        BoardContentImageService.StoredContentImage stored = service.store(file);
        BoardContentImageService.StoredImageResource loaded = service.load(stored.savedFilename());

        assertThat(stored.savedFilename()).matches("[0-9]{6}/.+\\.png");
        assertThat(tempDirectory.resolve("boards").resolve(stored.savedFilename())).isRegularFile();
        assertThat(stored.width()).isEqualTo(2);
        assertThat(stored.height()).isEqualTo(2);
        assertThat(loaded.contentType()).isEqualTo("image/png");
        assertThat(loaded.fileSize()).isPositive();
    }

    @Test
    void rejectsNonImagePayloadEvenWhenExtensionLooksSafe() {
        BoardProperties properties = new BoardProperties();
        BoardContentImageService service = new BoardContentImageService(properties, uploadStorage());
        MockMultipartFile file = new MockMultipartFile(
                "upload",
                "fake.png",
                "image/png",
                "<script>alert('x')</script>".getBytes()
        );

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미지");
    }

    private UploadStorage uploadStorage() {
        UploadProperties properties = new UploadProperties();
        properties.setBaseDirectory(tempDirectory.toString());
        return new UploadStorage(properties);
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
