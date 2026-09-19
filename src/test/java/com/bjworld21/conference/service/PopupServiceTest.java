package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.PopupResponse;
import com.bjworld21.conference.entity.Popup;
import com.bjworld21.conference.repository.PopupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.bjworld21.conference.config.WebRiskProperties;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PopupServiceTest {
    @Test
    void sanitizesRichTextAndUsesItsFirstImageAsThumbnail() {
        PopupRepository repository = mock(PopupRepository.class);
        UploadStorage uploadStorage = mock(UploadStorage.class);
        WebRiskLinkValidator webRiskLinkValidator = mock(WebRiskLinkValidator.class);
        AtomicReference<Popup> storedPopup = new AtomicReference<>();
        doAnswer(invocation -> {
            Popup popup = invocation.getArgument(0);
            popup.setSeq(1L);
            storedPopup.set(popup);
            return null;
        }).when(repository).insert(any(Popup.class));
        when(repository.findBySeq(1L, 1L)).thenAnswer(invocation -> storedPopup.get());

        PopupService service = new PopupService(repository, uploadStorage, new CmsHtmlSanitizer(), webRiskLinkValidator);
        PopupResponse response = service.create(
                1L,
                "안내 팝업",
                null,
                true,
                null,
                null,
                "<p>일반 텍스트</p><script>alert(1)</script>"
                        + "<img src=\"https://example.com/popup.png\" onerror=\"alert(2)\">",
                null
        );

        assertThat(response.getContent()).contains("일반 텍스트", "https://example.com/popup.png");
        assertThat(response.getContent()).doesNotContain("script", "onerror");
        assertThat(response.getImageUrl()).isEqualTo("https://example.com/popup.png");
        assertThat(response.getWebRiskWarning()).isNull();
        verify(webRiskLinkValidator).validate(response.getContent(), null);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void savesAndReturnsWarningWhenWebRiskFails(boolean updating) {
        PopupRepository repository = mock(PopupRepository.class);
        WebRiskClient client = mock(WebRiskClient.class);
        when(client.findThreatTypes("https://example.com/"))
                .thenThrow(new WebRiskUnavailableException("API unavailable"));
        WebRiskLinkValidator validator = new WebRiskLinkValidator(new WebRiskProperties(), client);
        PopupService service = new PopupService(repository, mock(UploadStorage.class), new CmsHtmlSanitizer(), validator);
        AtomicReference<Popup> stored = new AtomicReference<>(Popup.builder().seq(1L).conferenceSeq(1L).build());
        when(repository.findBySeq(1L, 1L)).thenAnswer(invocation -> stored.get());
        doAnswer(invocation -> {
            Popup popup = invocation.getArgument(0);
            popup.setSeq(1L);
            stored.set(popup);
            return null;
        }).when(repository).insert(any(Popup.class));

        PopupResponse response = updating
                ? service.update(1L, 1L, "Popup", "https://example.com/", true, null, null, "", null)
                : service.create(1L, "Popup", "https://example.com/", true, null, null, "", null);

        assertThat(response.getSeq()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("Popup");
        assertThat(response.getWebRiskWarning()).contains("API 검증에 실패", "링크 안전성을 확인하지 못했습니다");
        if (updating) {
            verify(repository).update(any(Popup.class));
        } else {
            verify(repository).insert(any(Popup.class));
        }
        assertThat(service.getBySeq(1L, 1L).getWebRiskWarning()).isNull();
    }
}
