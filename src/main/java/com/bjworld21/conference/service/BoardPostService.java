package com.bjworld21.conference.service;

import com.bjworld21.conference.config.BoardProperties;
import com.bjworld21.conference.config.PersonalDataProperties;
import com.bjworld21.conference.dto.BoardAttachmentResponse;
import com.bjworld21.conference.dto.BoardCategoryResponse;
import com.bjworld21.conference.dto.BoardPostPageResponse;
import com.bjworld21.conference.dto.BoardPostRequest;
import com.bjworld21.conference.dto.BoardPostResponse;
import com.bjworld21.conference.entity.BoardAttachment;
import com.bjworld21.conference.entity.BoardPost;
import com.bjworld21.conference.repository.BoardPostRepository;
import org.jsoup.Jsoup;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class BoardPostService {
    public static final long NOTICE_BOARD_SEQ = 1L;
    public static final long RESOURCE_BOARD_SEQ = 2L;
    public static final long FAQ_BOARD_SEQ = 3L;

    private static final Set<Long> SUPPORTED_BOARD_SEQS = Set.of(
            NOTICE_BOARD_SEQ,
            RESOURCE_BOARD_SEQ,
            FAQ_BOARD_SEQ
    );
    private static final Set<String> POST_STATUSES = Set.of("DRAFT", "PUBLISHED", "HIDDEN");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip",
            "png", "jpg", "jpeg", "gif", "webp", "txt", "hwp", "hwpx"
    );

    private final BoardPostRepository repository;
    private final CmsHtmlSanitizer htmlSanitizer;
    private final BoardProperties properties;
    private final UploadStorage uploadStorage;
    private final PersonalDataProperties personalDataProperties;

    public BoardPostService(
            BoardPostRepository repository,
            CmsHtmlSanitizer htmlSanitizer,
            BoardProperties properties,
            UploadStorage uploadStorage,
            PersonalDataProperties personalDataProperties
    ) {
        this.repository = repository;
        this.htmlSanitizer = htmlSanitizer;
        this.properties = properties;
        this.uploadStorage = uploadStorage;
        this.personalDataProperties = personalDataProperties;
    }

    public BoardPostPageResponse findPage(
            Long conferenceSeq,
            Long boardSeq,
            int page,
            int size,
            String keyword,
            String status
    ) {
        validateBoardSeq(boardSeq);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String normalizedStatus = normalizeStatusFilter(status);
        long totalCount = repository.countPage(conferenceSeq, boardSeq, normalizedStatus, normalizedKeyword);
        int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / safeSize));
        int adjustedPage = Math.min(safePage, totalPages);
        int offset = (adjustedPage - 1) * safeSize;

        return BoardPostPageResponse.builder()
                .items(repository.findPage(conferenceSeq, boardSeq, normalizedStatus, normalizedKeyword, safeSize, offset, dbEncString())
                        .stream()
                        .map(post -> toResponse(post, false))
                        .toList())
                .page(adjustedPage)
                .size(safeSize)
                .totalCount(totalCount)
                .totalPages(totalPages)
                .build();
    }

    public BoardPostResponse get(Long conferenceSeq, Long boardSeq, Long seq) {
        return toResponse(requirePost(conferenceSeq, boardSeq, seq), true);
    }

    public List<BoardPostResponse> findPublishedNotices(Long conferenceSeq, int size, boolean pinnedFirst) {
        return repository.findPublishedNotices(
                        conferenceSeq,
                        Math.min(Math.max(size, 1), 100),
                        pinnedFirst,
                        dbEncString()
                ).stream()
                .map(post -> toResponse(post, false))
                .toList();
    }

    public BoardPostResponse getPublishedNotice(Long conferenceSeq, Long seq) {
        repository.incrementPublishedNoticeViewCount(conferenceSeq, seq);
        BoardPost post = repository.findPublishedNoticeBySeq(conferenceSeq, seq, dbEncString());
        return post == null ? null : toResponse(post, true);
    }

    public BoardPostPageResponse findPublishedNoticePage(Long conferenceSeq, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        long totalCount = repository.countPublishedNotices(conferenceSeq);
        int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / safeSize));
        int safePage = Math.min(Math.max(page, 1), totalPages);

        return BoardPostPageResponse.builder()
                .items(repository.findPublishedNoticePage(conferenceSeq, safeSize, (safePage - 1) * safeSize, dbEncString())
                        .stream()
                        .map(post -> toResponse(post, false))
                        .toList())
                .page(safePage)
                .size(safeSize)
                .totalCount(totalCount)
                .totalPages(totalPages)
                .build();
    }

    public BoardPostPageResponse findPublishedFaqPage(Long conferenceSeq, int page, int size, String categoryCode) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedCategoryCode = categoryCode == null ? "" : categoryCode.trim();
        long totalCount = repository.countPublishedFaqs(conferenceSeq, normalizedCategoryCode);
        int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / safeSize));
        int safePage = Math.min(Math.max(page, 1), totalPages);

        return BoardPostPageResponse.builder()
                .items(repository.findPublishedFaqPage(conferenceSeq, normalizedCategoryCode, safeSize, (safePage - 1) * safeSize)
                        .stream()
                        .map(post -> toResponse(post, false))
                        .toList())
                .page(safePage)
                .size(safeSize)
                .totalCount(totalCount)
                .totalPages(totalPages)
                .build();
    }

    @Transactional
    public BoardPostResponse create(Long conferenceSeq, Long boardSeq, BoardPostRequest request, Long adminSeq) {
        validateBoardSeq(boardSeq);
        BoardPost post = BoardPost.builder()
                .conferenceSeq(conferenceSeq)
                .boardSeq(boardSeq)
                .createdBy(adminSeq)
                .updatedBy(adminSeq)
                .build();
        applyRequest(post, request, null);
        repository.insert(post);
        return get(conferenceSeq, boardSeq, post.getSeq());
    }

    @Transactional
    public BoardPostResponse update(Long conferenceSeq, Long boardSeq, Long seq, BoardPostRequest request, Long adminSeq) {
        BoardPost post = requirePost(conferenceSeq, boardSeq, seq);
        post.setUpdatedBy(adminSeq);
        applyRequest(post, request, post.getStatus());
        if (repository.update(post) == 0) {
            throw new IllegalArgumentException("존재하지 않는 게시글입니다.");
        }
        return get(conferenceSeq, boardSeq, seq);
    }

    @Transactional
    public void delete(Long conferenceSeq, Long boardSeq, Long seq) {
        BoardPost post = requirePost(conferenceSeq, boardSeq, seq);
        List<BoardAttachment> attachments = repository.findAttachments(post.getSeq());
        if (repository.delete(conferenceSeq, boardSeq, seq) == 0) {
            throw new IllegalArgumentException("존재하지 않는 게시글입니다.");
        }
        attachments.forEach(attachment -> deleteStoredFile(attachment.getSavedFilename()));
    }

    public List<BoardCategoryResponse> findFaqCategories() {
        return repository.findFaqCategories();
    }

    public BoardAttachmentResponse addAttachment(
            Long conferenceSeq,
            Long boardSeq,
            Long postSeq,
            MultipartFile file,
            Long adminSeq
    ) {
        BoardPost post = requirePost(conferenceSeq, boardSeq, postSeq);
        validateAttachment(file, post.getSeq());

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename()
        );
        String extension = extension(originalFilename);
        UploadStorage.StoredTarget storedTarget = uploadStorage.monthlyTarget(
                UploadStorage.BOARDS,
                UUID.randomUUID() + "." + extension
        );
        String savedFilename = storedTarget.relativePath();

        try {
            Path target = storedTarget.path();
            Files.createDirectories(target.getParent());
            file.transferTo(target);

            BoardAttachment attachment = BoardAttachment.builder()
                    .boardPostSeq(post.getSeq())
                    .originalFilename(originalFilename)
                    .savedFilename(savedFilename)
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .sortOrder(repository.nextAttachmentSortOrder(post.getSeq()))
                    .createdBy(adminSeq)
                    .build();
            repository.insertAttachment(attachment);
            return toAttachmentResponse(boardSeq, repository.findAttachment(attachment.getSeq()));
        } catch (IOException exception) {
            deleteStoredFile(savedFilename);
            throw new IllegalStateException("첨부파일 저장에 실패했습니다.", exception);
        } catch (RuntimeException exception) {
            deleteStoredFile(savedFilename);
            throw exception;
        }
    }

    public void deleteAttachment(Long conferenceSeq, Long boardSeq, Long postSeq, Long attachmentSeq) {
        BoardPost post = requirePost(conferenceSeq, boardSeq, postSeq);
        BoardAttachment attachment = requireAttachment(post.getSeq(), attachmentSeq);
        if (repository.deleteAttachment(post.getSeq(), attachmentSeq) == 0) {
            throw new IllegalArgumentException("존재하지 않는 첨부파일입니다.");
        }
        deleteStoredFile(attachment.getSavedFilename());
    }

    public AttachmentDownload downloadAttachment(Long conferenceSeq, Long boardSeq, Long postSeq, Long attachmentSeq) {
        BoardPost post = requirePost(conferenceSeq, boardSeq, postSeq);
        return downloadAttachment(post, attachmentSeq);
    }

    public AttachmentDownload downloadPublicAttachment(Long conferenceSeq, Long boardSeq, Long postSeq, Long attachmentSeq) {
        BoardPost post = requirePost(conferenceSeq, boardSeq, postSeq);
        LocalDateTime now = LocalDateTime.now();
        if (!"PUBLISHED".equals(post.getStatus())
                || post.getPublishedAt() == null
                || post.getPublishedAt().isAfter(now)
                || (post.getPublishEndAt() != null && !post.getPublishEndAt().isAfter(now))) {
            throw new IllegalArgumentException("공개되지 않은 게시글의 첨부파일입니다.");
        }
        return downloadAttachment(post, attachmentSeq);
    }

    private AttachmentDownload downloadAttachment(BoardPost post, Long attachmentSeq) {
        BoardAttachment attachment = requireAttachment(post.getSeq(), attachmentSeq);
        try {
            Path target = uploadStorage.resolve(UploadStorage.BOARDS, attachment.getSavedFilename());
            if (!Files.isRegularFile(target)) {
                throw new IllegalArgumentException("첨부파일을 찾을 수 없습니다.");
            }
            Resource resource = new UrlResource(target.toUri());
            repository.incrementDownloadCount(attachment.getSeq());
            return new AttachmentDownload(
                    attachment.getOriginalFilename(),
                    attachment.getContentType(),
                    attachment.getFileSize(),
                    resource
            );
        } catch (MalformedURLException exception) {
            throw new IllegalStateException("첨부파일을 읽을 수 없습니다.", exception);
        }
    }

    private void applyRequest(BoardPost post, BoardPostRequest request, String currentStatus) {
        if (request == null) {
            throw new IllegalArgumentException("게시글 정보가 필요합니다.");
        }

        String title = request.getTitle() == null ? "" : request.getTitle().trim();
        if (title.isEmpty()) {
            throw new IllegalArgumentException(post.getBoardSeq() == FAQ_BOARD_SEQ
                    ? "FAQ 질문은 필수입니다."
                    : "게시글 제목은 필수입니다.");
        }
        if (title.length() > 500) {
            throw new IllegalArgumentException("게시글 제목은 500자를 초과할 수 없습니다.");
        }

        String sanitizedContent = htmlSanitizer.sanitize(request.getContent());
        var sanitizedDocument = Jsoup.parseBodyFragment(sanitizedContent);
        String plainContent = sanitizedDocument.text().replace("\u00a0", "").trim();
        boolean hasEmbeddedContent = !sanitizedDocument.select("img, iframe").isEmpty();
        if ((post.getBoardSeq() == NOTICE_BOARD_SEQ || post.getBoardSeq() == FAQ_BOARD_SEQ)
                && plainContent.isEmpty() && !hasEmbeddedContent) {
            throw new IllegalArgumentException(post.getBoardSeq() == FAQ_BOARD_SEQ
                    ? "FAQ 답변은 필수입니다."
                    : "게시글 본문은 필수입니다.");
        }

        Long categorySeq = null;
        if (post.getBoardSeq() == FAQ_BOARD_SEQ) {
            categorySeq = request.getCategorySeq();
            if (categorySeq == null || repository.countActiveFaqCategory(categorySeq) == 0) {
                throw new IllegalArgumentException("FAQ 구분을 선택해 주세요.");
            }
        }

        String status = normalizeStatus(request.getStatus(), currentStatus);
        LocalDateTime publishedAt = request.getPublishedAt();
        if ("PUBLISHED".equals(status) && publishedAt == null) {
            publishedAt = post.getPublishedAt() != null ? post.getPublishedAt() : LocalDateTime.now();
        }
        LocalDateTime publishEndAt = request.getPublishEndAt();
        if (publishedAt != null && publishEndAt != null && !publishEndAt.isAfter(publishedAt)) {
            throw new IllegalArgumentException("공개 종료일시는 공개 시작일시보다 늦어야 합니다.");
        }

        post.setCategorySeq(categorySeq);
        post.setTitle(title);
        post.setContent(sanitizedContent);
        post.setStatus(status);
        post.setIsPinned(post.getBoardSeq() == NOTICE_BOARD_SEQ && Boolean.TRUE.equals(request.getIsPinned()));
        post.setSortOrder(post.getBoardSeq() == FAQ_BOARD_SEQ && request.getSortOrder() != null
                ? request.getSortOrder()
                : 0);
        post.setPublishedAt(publishedAt);
        post.setPublishEndAt(publishEndAt);
    }

    private void validateAttachment(MultipartFile file, Long postSeq) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new IllegalArgumentException("첨부할 파일을 선택해 주세요.");
        }
        if (repository.countAttachments(postSeq) >= properties.getMaxAttachmentCount()) {
            throw new IllegalArgumentException("첨부파일은 최대 " + properties.getMaxAttachmentCount() + "개까지 등록할 수 있습니다.");
        }
        if (repository.sumAttachmentBytes(postSeq) + file.getSize() > properties.getMaxAttachmentTotalBytes()) {
            throw new IllegalArgumentException("첨부파일 전체 크기는 10MB를 초과할 수 없습니다.");
        }

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename()
        );
        if (originalFilename.isBlank() || originalFilename.contains("..")
                || !ALLOWED_EXTENSIONS.contains(extension(originalFilename))) {
            throw new IllegalArgumentException("허용되지 않는 첨부파일 형식입니다.");
        }

        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        if (contentType.contains("executable")
                || contentType.contains("x-msdownload")
                || contentType.contains("javascript")
                || contentType.contains("html")) {
            throw new IllegalArgumentException("위험한 형식의 첨부파일은 등록할 수 없습니다.");
        }
    }

    private BoardPost requirePost(Long conferenceSeq, Long boardSeq, Long seq) {
        validateBoardSeq(boardSeq);
        if (seq == null) {
            throw new IllegalArgumentException("게시글 ID가 필요합니다.");
        }
        BoardPost post = repository.findBySeq(conferenceSeq, boardSeq, seq, dbEncString());
        if (post == null) {
            throw new IllegalArgumentException("존재하지 않는 게시글입니다.");
        }
        return post;
    }

    private BoardAttachment requireAttachment(Long postSeq, Long attachmentSeq) {
        BoardAttachment attachment = repository.findAttachment(attachmentSeq);
        if (attachment == null || !postSeq.equals(attachment.getBoardPostSeq())) {
            throw new IllegalArgumentException("존재하지 않는 첨부파일입니다.");
        }
        return attachment;
    }

    private void validateBoardSeq(Long boardSeq) {
        if (boardSeq == null || !SUPPORTED_BOARD_SEQS.contains(boardSeq)) {
            throw new IllegalArgumentException("지원하지 않는 게시판입니다.");
        }
    }

    private String normalizeStatus(String value, String fallback) {
        String status = value == null || value.isBlank()
                ? (fallback == null || fallback.isBlank() ? "DRAFT" : fallback)
                : value.trim().toUpperCase(Locale.ROOT);
        if (!POST_STATUSES.contains(status)) {
            throw new IllegalArgumentException("올바르지 않은 게시 상태입니다.");
        }
        return status;
    }

    private String normalizeStatusFilter(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return normalizeStatus(value, null);
    }

    private String extension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void deleteStoredFile(String savedFilename) {
        if (savedFilename == null || savedFilename.isBlank()) {
            return;
        }
        try {
            Path target = uploadStorage.resolve(UploadStorage.BOARDS, savedFilename);
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // DB 정리 성공 여부가 남은 파일 정리에 의해 영향을 받지 않도록 합니다.
        }
    }

    private BoardPostResponse toResponse(BoardPost post, boolean includeAttachments) {
        List<BoardAttachmentResponse> attachments = includeAttachments
                ? repository.findAttachments(post.getSeq()).stream()
                        .map(attachment -> toAttachmentResponse(post.getBoardSeq(), attachment))
                        .toList()
                : List.of();

        return BoardPostResponse.builder()
                .seq(post.getSeq())
                .boardSeq(post.getBoardSeq())
                .categorySeq(post.getCategorySeq())
                .categoryName(post.getCategoryName())
                .categoryCode(post.getCategoryCode())
                .title(post.getTitle())
                .content(post.getContent())
                .status(post.getStatus())
                .isPinned(post.getIsPinned())
                .sortOrder(post.getSortOrder())
                .viewCount(post.getViewCount())
                .publishedAt(post.getPublishedAt())
                .publishEndAt(post.getPublishEndAt())
                .createdBy(post.getCreatedBy())
                .createdByName(post.getCreatedByName())
                .updatedBy(post.getUpdatedBy())
                .updatedByName(post.getUpdatedByName())
                .attachmentCount(post.getAttachmentCount() != null ? post.getAttachmentCount() : attachments.size())
                .attachments(attachments)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

    private BoardAttachmentResponse toAttachmentResponse(Long boardSeq, BoardAttachment attachment) {
        return BoardAttachmentResponse.builder()
                .seq(attachment.getSeq())
                .boardPostSeq(attachment.getBoardPostSeq())
                .originalFilename(attachment.getOriginalFilename())
                .contentType(attachment.getContentType())
                .fileSize(attachment.getFileSize())
                .sortOrder(attachment.getSortOrder())
                .downloadCount(attachment.getDownloadCount())
                .downloadUrl("/api/boards/" + boardSeq + "/posts/"
                        + attachment.getBoardPostSeq() + "/attachments/" + attachment.getSeq())
                .createdAt(attachment.getCreatedAt())
                .build();
    }

    public record AttachmentDownload(
            String originalFilename,
            String contentType,
            long fileSize,
            Resource resource
    ) {
    }
    private String dbEncString() {
        return personalDataProperties.requireDbEncString();
    }
}
