package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.RegistrationFeeCategoryRequest;
import com.bjworld21.conference.dto.RegistrationFeeCategoryResponse;
import com.bjworld21.conference.entity.RegistrationCategory;
import com.bjworld21.conference.repository.RegistrationFeeRepository;
import com.bjworld21.conference.repository.ConferenceSettingsRepository;
import com.bjworld21.conference.entity.ConferenceSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationFeeServiceTest {

    @Mock
    private RegistrationFeeRepository repository;
    @Mock
    private ConferenceSettingsRepository conferenceSettingsRepository;

    private RegistrationFeeService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationFeeService(repository, conferenceSettingsRepository);
    }

    @Test
    void saveAllUpdatesRatesAndSoftDeletesRemovedCategory() {
        RegistrationFeeCategoryResponse piPhd = response(1L, "PI_PHD", "PI / Ph.D.");
        RegistrationFeeCategoryResponse student = response(2L, "STUDENT", "Student");
        when(repository.findAllActiveWithFees(1L))
                .thenReturn(List.of(piPhd, student))
                .thenReturn(List.of(piPhd));

        RegistrationFeeCategoryRequest request = RegistrationFeeCategoryRequest.builder()
                .seq(1L)
                .categoryCode("PI_PHD")
                .categoryName("PI / Ph.D.")
                .sortOrder(10)
                .isUsed("Y")
                .earlyBirdUsdFee(new BigDecimal("350.00"))
                .earlyBirdKrwFee(new BigDecimal("480000"))
                .regularUsdFee(new BigDecimal("450.00"))
                .regularKrwFee(new BigDecimal("620000"))
                .build();

        service.saveAll(1L, List.of(request), List.of(2L));

        verify(repository).upsertRate(1L, "EARLY_BIRD", "USD", new BigDecimal("350.00"));
        verify(repository).upsertRate(1L, "EARLY_BIRD", "KRW", new BigDecimal("480000"));
        verify(repository).upsertRate(1L, "REGULAR", "USD", new BigDecimal("450.00"));
        verify(repository).upsertRate(1L, "REGULAR", "KRW", new BigDecimal("620000"));
        verify(repository).softDeleteCategory(2L, 1L);
    }

    @Test
    void saveAllCreatesCategoryAndBothRates() {
        when(repository.findAllActiveWithFees(1L))
                .thenReturn(List.of())
                .thenReturn(List.of(response(10L, "NURSE", "Nurse")));
        when(repository.countByCategoryCode("NURSE", 1L)).thenReturn(0L);
        doAnswer(invocation -> {
            RegistrationCategory category = invocation.getArgument(0);
            category.setSeq(10L);
            return null;
        }).when(repository).insertCategory(any(RegistrationCategory.class));

        RegistrationFeeCategoryRequest request = RegistrationFeeCategoryRequest.builder()
                .categoryCode("nurse")
                .categoryName("Nurse")
                .isUsed("Y")
                .earlyBirdUsdFee(new BigDecimal("200.00"))
                .earlyBirdKrwFee(new BigDecimal("270000"))
                .regularUsdFee(new BigDecimal("300.00"))
                .regularKrwFee(new BigDecimal("410000"))
                .build();

        service.saveAll(1L, List.of(request), List.of());

        verify(repository).upsertRate(10L, "EARLY_BIRD", "USD", new BigDecimal("200.00"));
        verify(repository).upsertRate(10L, "EARLY_BIRD", "KRW", new BigDecimal("270000"));
        verify(repository).upsertRate(10L, "REGULAR", "USD", new BigDecimal("300.00"));
        verify(repository).upsertRate(10L, "REGULAR", "KRW", new BigDecimal("410000"));
    }

    @Test
    void activeCategoryRequiresBothFees() {
        RegistrationFeeCategoryRequest request = RegistrationFeeCategoryRequest.builder()
                .categoryCode("STUDENT")
                .categoryName("Student")
                .isUsed("Y")
                .earlyBirdUsdFee(new BigDecimal("250.00"))
                .earlyBirdKrwFee(new BigDecimal("340000"))
                .regularUsdFee(new BigDecimal("350.00"))
                .build();

        assertThatThrownBy(() -> service.saveAll(1L, List.of(request), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("USD와 KRW 등록비");
    }

    private RegistrationFeeCategoryResponse response(Long seq, String code, String name) {
        return RegistrationFeeCategoryResponse.builder()
                .seq(seq)
                .categoryCode(code)
                .categoryName(name)
                .sortOrder(seq.intValue() * 10)
                .isUsed("Y")
                .build();
    }
}
