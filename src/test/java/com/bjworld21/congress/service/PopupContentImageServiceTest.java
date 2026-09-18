package com.bjworld21.congress.service;

import com.bjworld21.congress.config.BoardProperties;
import com.bjworld21.congress.config.UploadProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PopupContentImageServiceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void storesPopupEditorImageInMonthlyPopupDirectoryAndLoadsIt() throws Exception {
        PopupContentImageService service = new PopupContentImageService(new BoardProperties(), uploadStorage());
        MockMultipartFile file = new MockMultipartFile(
                "upload",
                "popup.png",
                "image/png",
                pngBytes()
        );

        PopupContentImageService.StoredContentImage stored = service.store(file);
        PopupContentImageService.StoredImageResource loaded = service.load(stored.savedFilename());

        assertThat(stored.savedFilename()).matches("[0-9]{6}/.+\\.png");
        assertThat(tempDirectory.resolve("popups").resolve(stored.savedFilename())).isRegularFile();
        assertThat(stored.width()).isEqualTo(2);
        assertThat(stored.height()).isEqualTo(2);
        assertThat(loaded.contentType()).isEqualTo("image/png");
    }

    @Test
    void rejectsNonImagePayload() {
        PopupContentImageService service = new PopupContentImageService(new BoardProperties(), uploadStorage());
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
