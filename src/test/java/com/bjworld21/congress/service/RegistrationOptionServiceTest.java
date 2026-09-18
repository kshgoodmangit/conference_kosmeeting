package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.RegistrationOptionData.*;
import com.bjworld21.congress.entity.ConferenceSettings;
import com.bjworld21.congress.repository.ConferenceSettingsRepository;
import com.bjworld21.congress.repository.RegistrationOptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegistrationOptionServiceTest {
    RegistrationOptionRepository repository;
    ConferenceSettingsRepository conferences;
    RegistrationOptionService service;

    @BeforeEach void setup() {
        repository = mock(RegistrationOptionRepository.class);
        conferences = mock(ConferenceSettingsRepository.class);
        service = new RegistrationOptionService(repository, conferences);
        when(conferences.findBySeq(1L)).thenReturn(new ConferenceSettings());
    }
    private Option option() {
        Option option = new Option();
        option.setOptionName("  연회  "); option.setKrwPrice(BigDecimal.ZERO);
        option.setMaxPerPerson(1); option.setEnabled(true); option.setSortOrder(0); option.setVersionNo(0);
        return option;
    }
    @Test void zeroPriceAndAbsentCurrencyRemainDifferentAndClientIdsAreIgnored() {
        Option option = option(); option.setConferenceSeq(999L); option.setSeq(999L);
        when(repository.insert(any())).thenAnswer(invocation -> { Option saved = invocation.getArgument(0); saved.setSeq(2L); return 1; });
        when(repository.find(1L, 2L)).thenReturn(option);
        Option result = service.save(1L, null, option);
        assertEquals(1L, result.getConferenceSeq()); assertEquals(2L, result.getSeq());
        assertEquals("연회", result.getOptionName()); assertEquals(BigDecimal.ZERO, result.getKrwPrice());
        assertNull(result.getUsdPrice());
    }
    @Test void requiresAtLeastOneCurrencyAndValidPrecision() {
        Option option = option(); option.setKrwPrice(null);
        assertThrows(IllegalArgumentException.class, () -> service.save(1L, null, option));
        option.setUsdPrice(new BigDecimal("1.001"));
        assertThrows(IllegalArgumentException.class, () -> service.save(1L, null, option));
        option.setUsdPrice(null); option.setKrwPrice(new BigDecimal("1.1"));
        assertThrows(IllegalArgumentException.class, () -> service.save(1L, null, option));
        option.setKrwPrice(new BigDecimal("-1"));
        assertThrows(IllegalArgumentException.class, () -> service.save(1L, null, option));
        verify(repository, never()).insert(any());
    }
    @Test void rejectsInvalidCapacityAndDates() {
        Option option = option(); option.setCapacity(-1);
        assertThrows(IllegalArgumentException.class, () -> service.save(1L, null, option));
        option.setCapacity(null); option.setMaxPerPerson(0);
        assertThrows(IllegalArgumentException.class, () -> service.save(1L, null, option));
        option.setMaxPerPerson(1); option.setSaleStartsAt(LocalDateTime.of(2026, 9, 10, 0, 0));
        option.setSaleEndsAt(option.getSaleStartsAt().minusDays(1));
        assertThrows(IllegalArgumentException.class, () -> service.save(1L, null, option));
    }
    @Test void otherConferenceOptionCannotBeUpdated() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.save(1L, 5L, option()));
        assertEquals(404, error.getStatusCode().value()); verify(repository, never()).update(any());
    }
    @Test void staleVersionReturnsConflict() {
        when(repository.find(1L, 5L)).thenReturn(option());
        when(repository.update(any())).thenReturn(0);
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.save(1L, 5L, option()));
        assertEquals(409, error.getStatusCode().value());
    }
    @Test void listUsesSameFiltersForTotalsAndClampsPage() {
        Summary summary = new Summary(); summary.setTotalCount(11); summary.setEnabledCount(11);
        when(repository.summary(1L, "연회", "Y")).thenReturn(summary);
        when(repository.page(1L, "연회", "Y", 10, 10)).thenReturn(List.of());
        Page result = service.page(1L, 99, " 연회 ", "Y");
        assertEquals(2, result.page()); assertEquals(11, result.summary().getTotalCount());
        verify(repository).page(1L, "연회", "Y", 10, 10);
    }
    @Test void deletesOptionBelongingToConference() {
        when(repository.find(1L, 5L)).thenReturn(option());
        when(repository.delete(1L, 5L)).thenReturn(1);

        service.delete(1L, 5L);

        verify(repository).countSelections(5L);
        verify(repository).delete(1L, 5L);
    }
    @Test void referencedOptionCannotBeDeleted() {
        when(repository.find(1L, 5L)).thenReturn(option());
        when(repository.countSelections(5L)).thenReturn(1);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.delete(1L, 5L));

        assertEquals(409, error.getStatusCode().value());
        verify(repository, never()).delete(anyLong(), anyLong());
    }
    @Test void concurrentReferenceCreationReturnsConflict() {
        when(repository.find(1L, 5L)).thenReturn(option());
        when(repository.delete(1L, 5L)).thenThrow(new DataIntegrityViolationException("foreign key"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.delete(1L, 5L));

        assertEquals(409, error.getStatusCode().value());
    }
}
