package com.bjworld21.conference.service;

import com.bjworld21.conference.config.PersonalDataProperties;
import com.bjworld21.conference.dto.SpeakerPageResponse;
import com.bjworld21.conference.dto.SpeakerRequest;
import com.bjworld21.conference.dto.SpeakerResponse;
import com.bjworld21.conference.entity.Speaker;
import com.bjworld21.conference.repository.SpeakerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class SpeakerService {
    private static final Logger log = LoggerFactory.getLogger(SpeakerService.class);
    private final SpeakerRepository repository;
    private final UploadStorage storage;
    private final PersonalDataProperties personalDataProperties;

    public SpeakerService(SpeakerRepository repository, UploadStorage storage,
                          PersonalDataProperties personalDataProperties) {
        this.repository = repository;
        this.storage = storage;
        this.personalDataProperties = personalDataProperties;
    }

    public record SpeakerType(Long seq, String codeName) { }

    public List<SpeakerType> types() {
        return repository.findTypes().stream().map(code -> new SpeakerType(code.getSeq(), code.getCodeName())).toList();
    }

    @Transactional(readOnly = true)
    public SpeakerPageResponse list(Long conferenceSeq, int page, int size, String keyword, Long typeCode, Boolean enabled) {
        int safeSize = Math.max(1, Math.min(size, 100));
        String search = keyword == null ? "" : keyword.trim();
        if (search.length() > 200) throw new IllegalArgumentException("검색어는 200자 이하로 입력해주세요.");
        long count = repository.count(conferenceSeq, search, typeCode, enabled, dbEncString());
        int totalPages = Math.max(1, (int) Math.ceil((double) count / safeSize));
        int safePage = Math.max(1, Math.min(page, totalPages));
        return new SpeakerPageResponse(repository.findPage(conferenceSeq, search, typeCode, enabled, safeSize, (long) (safePage - 1) * safeSize, dbEncString())
                .stream().map(SpeakerResponse::from).toList(), safePage, safeSize, count, totalPages,
                repository.countEnabled(conferenceSeq, search, typeCode, enabled, dbEncString()),
                repository.countFeatured(conferenceSeq, search, typeCode, enabled, dbEncString()));
    }

    public SpeakerResponse get(Long conferenceSeq, Long seq) {
        return SpeakerResponse.from(require(repository.findBySeq(conferenceSeq, seq, dbEncString())));
    }

    @Transactional
    public SpeakerResponse create(Long conferenceSeq, SpeakerRequest request, MultipartFile image) {
        Speaker speaker = new Speaker();
        speaker.setConferenceSeq(conferenceSeq);
        apply(speaker, request);
        replaceImage(speaker, request, image);
        repository.insert(conferenceSeq, speaker, dbEncString());
        return get(conferenceSeq, speaker.getSeq());
    }

    @Transactional
    public SpeakerResponse update(Long conferenceSeq, Long seq, SpeakerRequest request, MultipartFile image) {
        Speaker speaker = require(repository.findBySeqForUpdate(conferenceSeq, seq, dbEncString()));
        apply(speaker, request);
        replaceImage(speaker, request, image);
        repository.update(conferenceSeq, speaker, dbEncString());
        return get(conferenceSeq, seq);
    }

    @Transactional
    public void delete(Long conferenceSeq, Long seq) {
        Speaker speaker = require(repository.findBySeqForUpdate(conferenceSeq, seq, dbEncString()));
        repository.delete(conferenceSeq, seq);
        scheduleFileCleanup(null, speaker.getProfileImageSaveFilename());
    }

    public Resource image(Long conferenceSeq, Long seq) {
        Speaker speaker = require(repository.findBySeq(conferenceSeq, seq, dbEncString()));
        return image(speaker);
    }

    public Resource publicImage(Long conferenceSeq, Long seq) {
        Speaker speaker = require(repository.findBySeq(conferenceSeq, seq, dbEncString()));
        if (!Boolean.TRUE.equals(speaker.getEnabled())) throw notFound("등록된 프로필 이미지가 없습니다.");
        return image(speaker);
    }

    private Resource image(Speaker speaker) {
        if (speaker.getProfileImageSaveFilename() == null) throw notFound("등록된 프로필 이미지가 없습니다.");
        Path path = storage.resolve(UploadStorage.SPEAKERS, speaker.getProfileImageSaveFilename());
        if (!Files.isRegularFile(path)) throw notFound("프로필 이미지 파일을 찾을 수 없습니다.");
        try {
            return new UrlResource(path.toUri());
        } catch (IOException exception) {
            throw new IllegalStateException("프로필 이미지를 읽을 수 없습니다.", exception);
        }
    }

    private Speaker require(Speaker speaker) {
        if (speaker == null) throw notFound("존재하지 않는 연자입니다.");
        return speaker;
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private void apply(Speaker speaker, SpeakerRequest request) {
        // 사용 중지된 기존 구분은 다른 정보 수정 시 유지할 수 있습니다.
        if (!Objects.equals(speaker.getSpeakerTypeCode(), request.getSpeakerTypeCode())
                && repository.findTypes().stream().noneMatch(code -> Objects.equals(code.getSeq(), request.getSpeakerTypeCode()))) {
            throw new IllegalArgumentException("유효한 연자 구분을 선택해주세요.");
        }
        String country = optional(request.getCountryCode());
        if (country != null) {
            country = country.toUpperCase(Locale.ROOT);
            if (repository.findCountry(country) == null) throw new IllegalArgumentException("유효한 국가를 선택해주세요.");
        }
        String homepage = optional(request.getHomepageUrl());
        if (homepage != null) {
            try {
                URI uri = new URI(homepage);
                if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null || uri.getUserInfo() != null) {
                    throw new IllegalArgumentException("홈페이지는 http:// 또는 https:// 주소를 입력해주세요.");
                }
            } catch (URISyntaxException exception) {
                throw new IllegalArgumentException("홈페이지 URL 형식이 올바르지 않습니다.");
            }
        }
        speaker.setSpeakerTypeCode(request.getSpeakerTypeCode());
        speaker.setDisplayName(request.getDisplayName().trim());
        speaker.setDisplayNameKo(optional(request.getDisplayNameKo()));
        speaker.setAffiliation(request.getAffiliation().trim());
        speaker.setDepartment(optional(request.getDepartment()));
        speaker.setPositionTitle(optional(request.getPositionTitle()));
        speaker.setCountryCode(country);
        speaker.setBiography(optional(request.getBiography()));
        speaker.setHomepageUrl(homepage);
        speaker.setContactEmail(optional(request.getContactEmail()));
        speaker.setFeatured(request.getFeatured());
        speaker.setEnabled(request.getEnabled());
        speaker.setSortOrder(request.getSortOrder());
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void replaceImage(Speaker speaker, SpeakerRequest request, MultipartFile image) {
        boolean hasImage = image != null && !image.isEmpty();
        if (image != null && image.isEmpty()) throw new IllegalArgumentException("빈 이미지 파일은 업로드할 수 없습니다.");
        if (hasImage && request.isRemoveProfileImage()) throw new IllegalArgumentException("이미지 교체와 삭제는 동시에 할 수 없습니다.");
        if (!hasImage && !request.isRemoveProfileImage()) return;
        String previous = speaker.getProfileImageSaveFilename();
        String saved = null;
        if (hasImage) {
            String original = image.getOriginalFilename() == null ? "" : image.getOriginalFilename().replace('\\', '/');
            original = original.substring(original.lastIndexOf('/') + 1);
            if (original.length() > 255) throw new IllegalArgumentException("이미지 파일명은 255자 이하로 지정해주세요.");
            String extension = original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            validateImage(image, extension);
            var target = storage.monthlyTarget(UploadStorage.SPEAKERS, UUID.randomUUID() + "." + extension);
            try {
                Files.createDirectories(target.path().getParent());
                image.transferTo(target.path());
            } catch (IOException exception) {
                deleteFile(target.relativePath());
                throw new IllegalStateException("프로필 이미지 저장에 실패했습니다.", exception);
            }
            saved = target.relativePath();
            speaker.setProfileImageOriFilename(original);
        } else {
            speaker.setProfileImageOriFilename(null);
        }
        speaker.setProfileImageSaveFilename(saved);
        scheduleFileCleanup(saved, previous);
    }

    private void validateImage(MultipartFile file, String extension) {
        if (file.getSize() > 2L * 1024 * 1024) throw new IllegalArgumentException("프로필 이미지는 2MB 이하만 업로드할 수 있습니다.");
        if (!Set.of("jpg", "jpeg", "png").contains(extension)) throw new IllegalArgumentException("프로필 이미지는 JPG 또는 PNG만 업로드할 수 있습니다.");
        try (var input = file.getInputStream(); ImageInputStream stream = ImageIO.createImageInputStream(input)) {
            if (stream == null) throw new IllegalArgumentException("이미지 파일을 확인해주세요.");
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IllegalArgumentException("올바른 이미지 파일이 아닙니다.");
            var reader = readers.next();
            try {
                reader.setInput(stream);
                String expected = extension.equals("png") ? "png" : "jpeg";
                if (!expected.equalsIgnoreCase(reader.getFormatName())) throw new IllegalArgumentException("이미지 확장자와 파일 형식이 일치하지 않습니다.");
                if ((long) reader.getWidth(0) * reader.getHeight(0) > 16_000_000) throw new IllegalArgumentException("이미지 해상도는 1,600만 픽셀 이하로 줄여주세요.");
                reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("손상된 이미지 파일입니다.", exception);
        }
    }

    private void scheduleFileCleanup(String newFile, String oldFile) {
        // DB 커밋 전에는 기존 파일을 유지하고, 롤백 시 새 파일만 정리합니다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) deleteFile(oldFile);
                else if (status == STATUS_ROLLED_BACK) deleteFile(newFile);
            }
        });
    }

    private void deleteFile(String saved) {
        if (saved == null || saved.isBlank()) return;
        try {
            Files.deleteIfExists(storage.resolve(UploadStorage.SPEAKERS, saved));
        } catch (IOException | IllegalArgumentException exception) {
            log.warn("연자 이미지 파일 정리에 실패했습니다: {}", saved, exception);
        }
    }
    private String dbEncString() {
        return personalDataProperties.requireDbEncString();
    }
}
