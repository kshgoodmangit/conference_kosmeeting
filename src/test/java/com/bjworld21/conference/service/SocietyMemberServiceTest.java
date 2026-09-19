package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.SocietyMemberData.*;
import com.bjworld21.conference.entity.RegistrationCategory;
import com.bjworld21.conference.repository.SocietyMemberRepository;
import com.bjworld21.conference.repository.RegistrationFeeRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SocietyMemberServiceTest {
    private final SocietyMemberRepository repository = mock(SocietyMemberRepository.class);
    private final RegistrationFeeRepository fees = mock(RegistrationFeeRepository.class);
    private final SocietyMemberService service = new SocietyMemberService(
            repository, fees, PersonalDataTestSupport.properties()
    );
    @Test
    void quoteRequiresMatchingNameAndConfiguredRate() {
        Member member = new Member(); member.setFullName("홍길동"); member.setMemberType("정회원");
        when(repository.findByLicense("00123", PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(member);
        assertThatThrownBy(() -> service.quote(1L, new QuoteRequest("00123", "다른이름", "REGULAR", "KRW"))).hasMessageContaining("일치");
        assertThatThrownBy(() -> service.quote(1L, new QuoteRequest("00123", "홍길동", "REGULAR", "KRW"))).hasMessageContaining("먼저 설정");
        FeeQuote quote = new FeeQuote(); quote.setAmount(new BigDecimal("50000")); quote.setCategorySeq(3L);
        when(repository.quote(1L, "정회원", "REGULAR", "KRW")).thenReturn(quote);
        assertThat(service.quote(1L, new QuoteRequest(" 00123 ", " 홍길동 ", "REGULAR", "KRW")).getAmount()).isEqualByComparingTo("50000");
        assertThatThrownBy(() -> service.quote(1L, new QuoteRequest("999", "홍길동", "REGULAR", "KRW"))).hasMessageContaining("일치");
        assertThatThrownBy(() -> service.quote(1L, new QuoteRequest("00123", "홍길동", "REGULAR", "EUR"))).hasMessageContaining("통화");
    }
    @Test
    void summaryUsesAllFilteredRowsAndClampsPage() {
        Summary summary = new Summary(); summary.setTotalCount(21); summary.setRegularCount(21);
        when(repository.summary("병원", "정회원", PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(summary);
        when(repository.page("병원", "정회원", 20, 20, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(List.of());
        Page page = service.page(99, 20, " 병원 ", "정회원");
        assertThat(page.page()).isEqualTo(2); assertThat(page.summary().getTotalCount()).isEqualTo(21);
    }
    @Test
    void mappingRejectsMissingDuplicateOrDisabledCategories() {
        FeeMapping regular = new FeeMapping(); regular.setMemberType("정회원"); regular.setCategorySeq(3L);
        FeeMapping associate = new FeeMapping(); associate.setMemberType("준회원");
        FeeMapping other = new FeeMapping(); other.setMemberType("기타");
        assertThatThrownBy(() -> service.saveMappings(1L, List.of(regular))).hasMessageContaining("모두");
        assertThatThrownBy(() -> service.saveMappings(1L, List.of(regular, regular, other))).hasMessageContaining("모두");
        when(fees.findActiveBySeq(3L, 1L)).thenReturn(RegistrationCategory.builder().seq(3L).isUsed("N").build());
        assertThatThrownBy(() -> service.saveMappings(1L, List.of(regular, associate, other))).hasMessageContaining("사용 중");
        verify(repository, never()).upsertMapping(anyLong(), any());
    }
}
