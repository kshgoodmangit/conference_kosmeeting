package com.bjworld21.conference.controller;

import com.bjworld21.conference.service.BoardContentImageService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class BoardContentImageController {
    private final BoardContentImageService service;

    public BoardContentImageController(BoardContentImageService service) {
        this.service = service;
    }

    @PostMapping(value = "/admin/boards/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(@RequestPart("upload") MultipartFile file) {
        try {
            BoardContentImageService.StoredContentImage storedImage = service.store(file);
            String contextPath = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
            String imageUrl = contextPath + "/api/boards/images/" + storedImage.savedFilename();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("uploaded", 1);
            response.put("fileName", storedImage.originalFilename());
            response.put("url", imageUrl);
            response.put("width", storedImage.width());
            response.put("height", storedImage.height());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of(
                    "uploaded", 0,
                    "error", Map.of("message", exception.getMessage())
            ));
        }
    }

    @GetMapping("/boards/images/{yearMonth}/{savedFilename:.+}")
    @com.bjworld21.conference.config.IpAccessExempt
    public ResponseEntity<Resource> image(
            @PathVariable String yearMonth,
            @PathVariable String savedFilename
    ) {
        BoardContentImageService.StoredImageResource storedImage = service.load(yearMonth + "/" + savedFilename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(storedImage.contentType()))
                .contentLength(storedImage.fileSize())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .body(storedImage.resource());
    }
}
