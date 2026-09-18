package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PromotionalMailProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class MailContentImageService {
    private static final long MAX_PIXELS = 25_000_000L;
    private static final int MAX_DIMENSION = 10_000;
    private static final Pattern SAVED_FILENAME_PATTERN = Pattern.compile(
            "[0-9]{6}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|gif)"
    );
    private static final Map<String, ImageType> IMAGE_TYPES = Map.of(
            "JPEG", new ImageType("jpg", "image/jpeg"),
            "JPG", new ImageType("jpg", "image/jpeg"),
            "PNG", new ImageType("png", "image/png"),
            "GIF", new ImageType("gif", "image/gif")
    );

    private final PromotionalMailProperties properties;
    private final UploadStorage uploadStorage;

    public MailContentImageService(PromotionalMailProperties properties, UploadStorage uploadStorage) {
        this.properties = properties;
        this.uploadStorage = uploadStorage;
    }

    public StoredContentImage store(MultipartFile file) {
        validateBasicFile(file);
        DetectedImage detectedImage = detectImage(file);
        UploadStorage.StoredTarget storedTarget = uploadStorage.monthlyTarget(
                UploadStorage.MAIL,
                UUID.randomUUID() + "." + detectedImage.type().extension()
        );
        String savedFilename = storedTarget.relativePath();
        Path target = storedTarget.path();

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            return new StoredContentImage(
                    savedFilename,
                    StringUtils.cleanPath(file.getOriginalFilename() == null ? savedFilename : file.getOriginalFilename()),
                    detectedImage.type().contentType(),
                    file.getSize(),
                    detectedImage.width(),
                    detectedImage.height()
            );
        } catch (IOException exception) {
            deleteQuietly(target);
            throw new IllegalStateException("본문 이미지를 저장하지 못했습니다.", exception);
        } catch (RuntimeException exception) {
            deleteQuietly(target);
            throw exception;
        }
    }

    public StoredImageResource load(String savedFilename) {
        String normalizedFilename = savedFilename == null
                ? ""
                : savedFilename.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (!SAVED_FILENAME_PATTERN.matcher(normalizedFilename).matches()) {
            throw new IllegalArgumentException("올바르지 않은 이미지 경로입니다.");
        }

        Path target = uploadStorage.resolve(UploadStorage.MAIL, normalizedFilename);
        if (!Files.isRegularFile(target)) {
            throw new IllegalArgumentException("본문 이미지를 찾을 수 없습니다.");
        }

        try {
            String extension = normalizedFilename.substring(normalizedFilename.lastIndexOf('.') + 1);
            String contentType = "jpg".equals(extension) ? "image/jpeg" : "image/" + extension;
            return new StoredImageResource(new UrlResource(target.toUri()), contentType, Files.size(target));
        } catch (MalformedURLException exception) {
            throw new IllegalStateException("본문 이미지 경로를 읽을 수 없습니다.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("본문 이미지 정보를 읽을 수 없습니다.", exception);
        }
    }

    private void validateBasicFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 이미지를 선택해 주세요.");
        }
        if (file.getSize() > properties.getMaxContentImageBytes()) {
            throw new IllegalArgumentException("본문 이미지는 파일당 5MB를 초과할 수 없습니다.");
        }
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        if (originalFilename.contains("..")) {
            throw new IllegalArgumentException("올바르지 않은 이미지 파일명입니다.");
        }
    }

    private DetectedImage detectImage(MultipartFile file) {
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(file.getInputStream())) {
            if (imageInput == null) {
                throw new IllegalArgumentException("이미지 파일을 읽을 수 없습니다.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("JPG, PNG, GIF 이미지만 업로드할 수 있습니다.");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                ImageType type = IMAGE_TYPES.get(reader.getFormatName().toUpperCase(Locale.ROOT));
                if (type == null) {
                    throw new IllegalArgumentException("JPG, PNG, GIF 이미지만 업로드할 수 있습니다.");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) {
                    throw new IllegalArgumentException("이미지 해상도는 최대 2,500만 픽셀까지 등록할 수 있습니다.");
                }
                return new DetectedImage(type, width, height);
            } finally {
                reader.dispose();
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalArgumentException("손상되었거나 지원하지 않는 이미지입니다.", exception);
        }
    }

    private void deleteQuietly(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // 저장 실패 원인을 유지하고 임시 파일 정리는 다음 운영 정리 작업에 맡긴다.
        }
    }

    private record ImageType(String extension, String contentType) {
    }

    private record DetectedImage(ImageType type, int width, int height) {
    }

    public record StoredContentImage(
            String savedFilename,
            String originalFilename,
            String contentType,
            long fileSize,
            int width,
            int height
    ) {
    }

    public record StoredImageResource(Resource resource, String contentType, long fileSize) {
    }
}
