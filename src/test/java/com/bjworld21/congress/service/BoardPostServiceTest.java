package com.bjworld21.congress.service;

import com.bjworld21.congress.config.BoardProperties;
import com.bjworld21.congress.config.UploadProperties;
import com.bjworld21.congress.dto.BoardPostRequest;
import com.bjworld21.congress.entity.BoardAttachment;
import com.bjworld21.congress.entity.BoardPost;
import com.bjworld21.congress.repository.BoardPostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoardPostServiceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void acceptsYoutubeIframeAsNoticeContent() {
        BoardPostRepository repository = mock(BoardPostRepository.class);
        BoardPost post = BoardPost.builder()
                .seq(10L)
                .boardSeq(BoardPostService.NOTICE_BOARD_SEQ)
                .status("DRAFT")
                .build();
        BoardPostRequest request = BoardPostRequest.builder()
                .title("YouTube 공지")
                .content("<iframe src=\"https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ\"></iframe>")
                .status("DRAFT")
                .build();
        when(repository.findBySeq(1L, post.getBoardSeq(), post.getSeq(), PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(post);
        when(repository.update(post)).thenReturn(1);
        when(repository.findAttachments(post.getSeq())).thenReturn(List.of());

        service(repository, uploadStorage()).update(1L, post.getBoardSeq(), post.getSeq(), request, 1L);

        assertThat(post.getContent())
                .contains("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ");
        verify(repository).update(post);
    }

    @Test
    void allowsPublicDownloadForPublishedPostWithinPublicationPeriod() throws Exception {
        BoardPostRepository repository = mock(BoardPostRepository.class);
        UploadStorage uploadStorage = uploadStorage();
        UploadStorage.StoredTarget target = uploadStorage.monthlyTarget(UploadStorage.BOARDS, "attachment.pdf");
        Files.createDirectories(target.path().getParent());
        Files.writeString(target.path(), "test attachment");

        BoardPost post = BoardPost.builder()
                .seq(10L)
                .boardSeq(BoardPostService.NOTICE_BOARD_SEQ)
                .status("PUBLISHED")
                .publishedAt(LocalDateTime.now().minusMinutes(1))
                .build();
        BoardAttachment attachment = BoardAttachment.builder()
                .seq(20L)
                .boardPostSeq(post.getSeq())
                .originalFilename("notice.pdf")
                .savedFilename(target.relativePath())
                .contentType("application/pdf")
                .fileSize(Files.size(target.path()))
                .build();
        when(repository.findBySeq(1L, post.getBoardSeq(), post.getSeq(), PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(post);
        when(repository.findAttachment(attachment.getSeq())).thenReturn(attachment);

        BoardPostService.AttachmentDownload download = service(repository, uploadStorage)
                .downloadPublicAttachment(1L, post.getBoardSeq(), post.getSeq(), attachment.getSeq());

        assertThat(download.originalFilename()).isEqualTo("notice.pdf");
        assertThat(download.resource().exists()).isTrue();
        verify(repository).incrementDownloadCount(attachment.getSeq());
    }

    @Test
    void rejectsPublicDownloadForDraftPost() {
        BoardPostRepository repository = mock(BoardPostRepository.class);
        UploadStorage uploadStorage = uploadStorage();
        BoardPost post = BoardPost.builder()
                .seq(10L)
                .boardSeq(BoardPostService.NOTICE_BOARD_SEQ)
                .status("DRAFT")
                .build();
        when(repository.findBySeq(1L, post.getBoardSeq(), post.getSeq(), PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(post);

        assertThatThrownBy(() -> service(repository, uploadStorage)
                .downloadPublicAttachment(1L, post.getBoardSeq(), post.getSeq(), 20L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("공개되지 않은 게시글");
        verify(repository, never()).findAttachment(20L);
    }

    private BoardPostService service(BoardPostRepository repository, UploadStorage uploadStorage) {
        return new BoardPostService(
                repository,
                new CmsHtmlSanitizer(),
                new BoardProperties(),
                uploadStorage,
                PersonalDataTestSupport.properties()
        );
    }

    private UploadStorage uploadStorage() {
        UploadProperties properties = new UploadProperties();
        properties.setBaseDirectory(tempDirectory.toString());
        return new UploadStorage(properties);
    }
}
