package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.FreeRecipientData.*;
import com.bjworld21.conference.repository.FreeRecipientRepository;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FreeRecipientServiceTest {
    private final FreeRecipientRepository repository = mock(FreeRecipientRepository.class);
    private final FreeRecipientService service = new FreeRecipientService(
            repository, PersonalDataTestSupport.properties()
    );
    private Recipient recipient() {
        Recipient r = new Recipient(); r.setFullName(" 홍  길동 "); r.setPhoneNumber("+82 (10) 0000-9001");
        r.setEmail("TEST@EXAMPLE.COM"); return r;
    }
    @Test void normalizesMatchingIdentityAndDefaultsWithoutCollectingBirthDate() {
        Recipient r = recipient(); service.validate(r);
        assertThat(r.getFullName()).isEqualTo("홍 길동");
        assertThat(r.getNormalizedPhone()).isEqualTo("01000009001");
        assertThat(r.getEmail()).isEqualTo("test@example.com");
        assertThat(r.getRecipientType()).isEqualTo("기타"); assertThat(r.getIsUsed()).isEqualTo("Y");
        assertThat(FreeRecipientService.normalizePhone("0082 10-0000-9001")).isEqualTo(r.getNormalizedPhone());
        assertThat(FreeRecipientService.normalizePhone("010.0000.9001")).isEqualTo(r.getNormalizedPhone());
        assertThat(FreeRecipientService.normalizePhone("+82 (0)10-0000-9001")).isEqualTo(r.getNormalizedPhone());
        assertThat(FreeRecipientService.normalizePhone("0044 20 7946 0000")).isEqualTo(FreeRecipientService.normalizePhone("+44 20 7946 0000"));
    }
    @Test void rejectsMissingInvalidAndOverlongFields() {
        Recipient r = recipient(); r.setPhoneNumber("010ABC12345");
        assertThatThrownBy(() -> service.validate(r)).isInstanceOf(IllegalArgumentException.class);
        r.setPhoneNumber("123"); assertThatThrownBy(() -> service.validate(r)).hasMessageContaining("8~15");
        r.setPhoneNumber("01000009001"); r.setEmail("not-email"); assertThatThrownBy(() -> service.validate(r)).hasMessageContaining("이메일");
        r.setEmail(""); r.setIsUsed("X"); assertThatThrownBy(() -> service.validate(r)).hasMessageContaining("사용 여부");
        r.setIsUsed("N"); r.setFullName(" "); assertThatThrownBy(() -> service.validate(r)).hasMessageContaining("이름");
        r.setFullName("샘플"); r.setAdminMemo("a".repeat(1001)); assertThatThrownBy(() -> service.validate(r)).hasMessageContaining("1,000".replace(",", ""));
        verifyNoInteractions(repository);
    }
    @Test void summaryAndPageUseSameFiltersAndClampOutOfRangePage() {
        Summary summary = new Summary(); summary.setTotalCount(23); summary.setActiveCount(20); summary.setInactiveCount(3);
        when(repository.summary(
                1L, "010-0000", "0100000", "초청", "Y", PersonalDataTestSupport.DB_ENC_STRING
        )).thenReturn(summary);
        Page result = service.page(1L, 999, 20, " 010-0000 ", "초청", "Y");
        assertThat(result.page()).isEqualTo(2); assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.summary().getTotalCount()).isEqualTo(23);
        verify(repository).page(
                1L, "010-0000", "0100000", "초청", "Y", 20, 20,
                PersonalDataTestSupport.DB_ENC_STRING
        );
    }
    @Test void updatesCannotCreateMissingRecordsAndMissingDeleteReturns404() {
        assertThatThrownBy(() -> service.save(1L, 123L, recipient())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> service.delete(1L, 123L)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(repository, never()).update(anyLong(), any(), anyString());
        verify(repository, never()).insert(anyLong(), any(), anyString());
    }
}
