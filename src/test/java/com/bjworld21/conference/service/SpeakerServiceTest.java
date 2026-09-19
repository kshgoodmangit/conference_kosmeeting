package com.bjworld21.conference.service;

import com.bjworld21.conference.config.UploadProperties;
import com.bjworld21.conference.dto.SpeakerRequest;
import com.bjworld21.conference.entity.CommonCode;
import com.bjworld21.conference.entity.Speaker;
import com.bjworld21.conference.repository.SpeakerRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpeakerServiceTest {
    @TempDir Path directory;
    @Mock SpeakerRepository repository;
    SpeakerService service;
    UploadStorage storage;

    @BeforeEach
    void setUp() {
        UploadProperties properties = new UploadProperties();
        properties.setBaseDirectory(directory.toString());
        storage = new UploadStorage(properties);
        service = new SpeakerService(repository, storage, PersonalDataTestSupport.properties());
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) finish(TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    @Test
    void createsWithoutImageAndTrimsOptionalFields() {
        allowType();
        doAnswer(invocation -> {
            Speaker value = invocation.getArgument(1);
            value.setSeq(1L);
            when(repository.findBySeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(value);
            return null;
        }).when(repository).insert(eq(1L), any(), eq(PersonalDataTestSupport.DB_ENC_STRING));
        SpeakerRequest request = request();
        request.setDisplayName("  연자 이름  ");
        request.setDepartment("  ");
        request.setEnabled(false);
        request.setFeatured(true);
        var result = service.create(1L, request, null);
        assertThat(result.displayName()).isEqualTo("연자 이름");
        assertThat(result.department()).isNull();
        assertThat(result.profileImageUrl()).isNull();
        assertThat(result.enabled()).isFalse();
        assertThat(result.featured()).isTrue();
    }

    @Test
    void rejectsUnrelatedTypeAndUnknownCountry() {
        when(repository.findTypes()).thenReturn(List.of());
        assertThatThrownBy(() -> service.create(1L, request(), null)).hasMessageContaining("연자 구분");
        allowType();
        SpeakerRequest request = request();
        request.setCountryCode("ZZ");
        assertThatThrownBy(() -> service.create(1L, request, null)).hasMessageContaining("국가");
        verify(repository, never()).insert(anyLong(), any(), anyString());
    }

    @Test
    void rejectsUnsafeHomepageUrl() {
        allowType();
        SpeakerRequest request = request();
        request.setHomepageUrl("javascript:alert(1)");
        assertThatThrownBy(() -> service.create(1L, request, null)).hasMessageContaining("https://");
        verify(repository, never()).insert(anyLong(), any(), anyString());
    }

    @Test
    void rejectsSpoofedAndOversizedImages() {
        allowType();
        assertThatThrownBy(() -> service.create(1L, request(), new MockMultipartFile("profileImage", "fake.png", "image/png", "not an image".getBytes())))
                .hasMessageContaining("이미지 파일");
        assertThatThrownBy(() -> service.create(1L, request(), new MockMultipartFile("profileImage", "large.jpg", "image/jpeg", new byte[2 * 1024 * 1024 + 1])))
                .hasMessageContaining("2MB");
        verify(repository, never()).insert(anyLong(), any(), anyString());
    }

    @Test
    void rejectsImageWithMismatchedExtension() throws Exception {
        allowType();
        assertThatThrownBy(() -> service.create(1L, request(), new MockMultipartFile("profileImage", "wrong.jpg", "image/jpeg", png().getBytes())))
                .hasMessageContaining("일치하지 않습니다");
    }

    @Test
    void replacementKeepsOldFileUntilCommitAndUsesMonthlyRelativePath() throws Exception {
        Speaker speaker = existingWithImage();
        String previous = speaker.getProfileImageSaveFilename();
        service.update(1L, 1L, request(), png());
        String saved = speaker.getProfileImageSaveFilename();
        assertThat(saved).matches("\\d{6}/[0-9a-f-]+\\.png");
        assertThat(storage.resolve(UploadStorage.SPEAKERS, saved)).exists();
        assertThat(storage.resolve(UploadStorage.SPEAKERS, previous)).exists();
        finish(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(storage.resolve(UploadStorage.SPEAKERS, previous)).doesNotExist();
        assertThat(storage.resolve(UploadStorage.SPEAKERS, saved)).exists();
    }

    @Test
    void failedDatabaseUpdateRemovesNewFileAndPreservesOldFile() throws Exception {
        Speaker speaker = existingWithImage();
        String previous = speaker.getProfileImageSaveFilename();
        doThrow(new IllegalStateException("DB failure")).when(repository).update(
                eq(1L), any(), eq(PersonalDataTestSupport.DB_ENC_STRING)
        );
        assertThatThrownBy(() -> service.update(1L, 1L, request(), png())).hasMessage("DB failure");
        String saved = speaker.getProfileImageSaveFilename();
        finish(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(storage.resolve(UploadStorage.SPEAKERS, saved)).doesNotExist();
        assertThat(storage.resolve(UploadStorage.SPEAKERS, previous)).exists();
    }

    @Test
    void editingWithoutImageRetainsItAndExplicitRemovalClearsIt() throws Exception {
        Speaker speaker = existingWithImage();
        String previous = speaker.getProfileImageSaveFilename();
        service.update(1L, 1L, request(), null);
        assertThat(speaker.getProfileImageSaveFilename()).isEqualTo(previous);
        SpeakerRequest remove = request();
        remove.setRemoveProfileImage(true);
        service.update(1L, 1L, remove, null);
        assertThat(speaker.getProfileImageSaveFilename()).isNull();
        assertThat(speaker.getProfileImageOriFilename()).isNull();
        finish(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(storage.resolve(UploadStorage.SPEAKERS, previous)).doesNotExist();
    }

    @Test
    void deleteOnlyRemovesFileAfterCommit() throws Exception {
        Speaker speaker = existingWithImage();
        Path path = storage.resolve(UploadStorage.SPEAKERS, speaker.getProfileImageSaveFilename());
        service.delete(1L, 1L);
        verify(repository).delete(1L, 1L);
        assertThat(path).exists();
        finish(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(path).doesNotExist();
    }

    @Test
    void imageAccessRejectsTraversalAndAbsolutePaths() {
        Speaker speaker = new Speaker();
        speaker.setProfileImageSaveFilename("../outside.png");
        when(repository.findBySeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(speaker);
        assertThatThrownBy(() -> service.image(1L, 1L)).isInstanceOf(IllegalArgumentException.class);
        speaker.setProfileImageSaveFilename(directory.resolve("outside.png").toAbsolutePath().toString());
        assertThatThrownBy(() -> service.image(1L, 1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void paginationClampsDeletedLastPageAndPassesFilters() {
        when(repository.count(1L, "Alice", 5L, false, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(11L);
        when(repository.countEnabled(1L, "Alice", 5L, false, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(0L);
        when(repository.countFeatured(1L, "Alice", 5L, false, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(7L);
        when(repository.findPage(1L, "Alice", 5L, false, 10, 10L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(List.of());
        var result = service.list(1L, 999, 10, " Alice ", 5L, false);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.totalCount()).isEqualTo(11);
        assertThat(result.enabledCount()).isZero();
        assertThat(result.featuredCount()).isEqualTo(7);
    }

    private void allowType() {
        when(repository.findTypes()).thenReturn(List.of(CommonCode.builder().seq(5L).codeName("Invited").build()));
    }

    private SpeakerRequest request() {
        SpeakerRequest request = new SpeakerRequest();
        request.setSpeakerTypeCode(5L);
        request.setDisplayName("연자");
        request.setAffiliation("대학교");
        return request;
    }

    private Speaker existingWithImage() throws Exception {
        Speaker speaker = new Speaker();
        speaker.setSeq(1L);
        speaker.setSpeakerTypeCode(5L);
        speaker.setProfileImageOriFilename("old.png");
        speaker.setProfileImageSaveFilename("202601/old.png");
        Path path = storage.resolve(UploadStorage.SPEAKERS, speaker.getProfileImageSaveFilename());
        Files.createDirectories(path.getParent());
        Files.write(path, png().getBytes());
        when(repository.findBySeqForUpdate(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(speaker);
        lenient().when(repository.findBySeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(speaker);
        return speaker;
    }

    private MockMultipartFile png() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new MockMultipartFile("profileImage", "profile.png", "image/png", bytes.toByteArray());
    }

    private void finish(int status) {
        TransactionSynchronizationManager.getSynchronizations().forEach(callback -> callback.afterCompletion(status));
        TransactionSynchronizationManager.clearSynchronization();
    }
}
