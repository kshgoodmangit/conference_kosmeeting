package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.PreRegistrationResponse;
import com.bjworld21.conference.dto.PreRegistrationSummary;
import com.bjworld21.conference.dto.PreRegistrationCategoryOptionResponse;
import com.bjworld21.conference.dto.PreRegistrationUpdateRequest;
import com.bjworld21.conference.dto.PreRegistrationCreateRequest;
import com.bjworld21.conference.dto.PreRegistrationOptionData;
import com.bjworld21.conference.dto.RegistrationOptionData;
import com.bjworld21.conference.repository.PreRegistrationRepository;
import com.bjworld21.conference.repository.ConferenceSettingsRepository;
import com.bjworld21.conference.entity.ConferenceSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class PreRegistrationAdminServiceTest {

    @Mock
    private PreRegistrationRepository repository;

    @Mock
    private SocietyMemberService societyMembers;

    @Mock
    private ConferenceSettingsRepository conferences;

    private PreRegistrationAdminService service;

    @BeforeEach
    void setUp() {
        service = new PreRegistrationAdminService(
                repository, societyMembers, conferences, PersonalDataTestSupport.properties()
        );
        org.mockito.Mockito.lenient().when(repository.findMemberType(eq(1L), any())).thenReturn("international");
        org.mockito.Mockito.lenient().when(repository.findFee(eq(1L), eq(3L), eq("REGULAR"), eq("USD")))
                .thenReturn(new BigDecimal("450.00"));
    }

    private PreRegistrationCreateRequest createRequest() {
        PreRegistrationCreateRequest request = new PreRegistrationCreateRequest();
        request.setMemberSeq(7L);
        request.setConferenceSeq(1L);
        request.setCategorySeq(3L);
        request.setPeriodType("regular");
        request.setCurrency("usd");
        request.setFeeAmount(new BigDecimal("450.00"));
        request.setAdminMemo("  관리자 접수  ");
        return request;
    }

    private void stubValidCategory() {
        when(repository.findCategoryBySeq(1L, 3L)).thenReturn(PreRegistrationCategoryOptionResponse.builder()
                .categorySeq(3L).categoryCode("PI_PHD").categoryName("PI / Ph.D.").build());
    }

    private void stubValidConference() {
        when(conferences.findBySeq(1L)).thenReturn(ConferenceSettings.builder()
                .seq(1L).eventName("APDRC8").registrationCurrency("USD").build());
    }

    @Test
    void availableOptionsShowsActualRemainingCapacityAndRetainsSelectedSoldOutOption() {
        PreRegistrationOptionData.Item selected = PreRegistrationOptionData.Item.builder()
                .optionSeq(10L).quantity(2).build();
        when(repository.findBySeq(1L, 42L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(PreRegistrationResponse.builder()
                .seq(42L).conferenceSeq(1L).build());
        when(repository.findOptionsByRegistrationSeq(1L, 42L)).thenReturn(List.of(selected));
        when(repository.findAvailableOptions(1L, "USD")).thenReturn(List.of(
                PreRegistrationOptionData.CatalogItem.builder().optionSeq(10L).remainingCapacity(0).build(),
                PreRegistrationOptionData.CatalogItem.builder().optionSeq(11L).remainingCapacity(0).build(),
                PreRegistrationOptionData.CatalogItem.builder().optionSeq(12L).remainingCapacity(5).build()
        ));

        List<PreRegistrationOptionData.CatalogItem> result = service.findAvailableOptions(1L, 42L, "USD");

        assertThat(result).extracting(PreRegistrationOptionData.CatalogItem::getOptionSeq)
                .containsExactly(10L, 12L);
        assertThat(result.get(0).getRemainingCapacity()).isZero();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"domestic,KRW,EARLY_BIRD,120000", "international,USD,EARLY_BIRD,120", "domestic,KRW,REGULAR,150000", "international,USD,REGULAR,150"})
    void createUsesConfiguredMemberAndPeriodFee(String memberType, String currency, String period, String amount) {
        when(repository.lockMember(1L, 7L)).thenReturn(7L);
        when(repository.findSubmittedSeqForUpdate(7L, 1L)).thenReturn(null);
        when(repository.findMemberType(1L, 7L)).thenReturn(memberType);
        stubValidConference();
        stubValidCategory();
        when(repository.findFee(1L, 3L, period, currency)).thenReturn(new BigDecimal(amount));
        doAnswer(invocation -> {
            PreRegistrationResponse value = invocation.getArgument(0);
            assertThat(value.getCurrency()).isEqualTo(currency);
            assertThat(value.getFeeAmount()).isEqualByComparingTo(amount);
            assertThat(value.getTotalAmount()).isEqualByComparingTo(amount);
            value.setSeq(42L);
            return 1;
        }).when(repository).insert(any());
        when(repository.assignRegistrationNumber(eq(1L), eq(42L), anyString())).thenReturn(1);
        when(repository.findBySeq(1L, 42L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(PreRegistrationResponse.builder().seq(42L).build());
        PreRegistrationCreateRequest request = createRequest();
        request.setPeriodType(period);
        request.setFeeAmount(BigDecimal.ONE);
        service.create(1L, request);
    }

    @Test
    void updateDomesticMemberUsesConfiguredKrwFee() {
        when(repository.findBySeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(PreRegistrationResponse.builder()
                .seq(1L).memberSeq(7L).currency("KRW").paymentStatus("UNPAID").build());
        when(repository.findMemberType(1L, 7L)).thenReturn("domestic");
        stubValidCategory();
        when(repository.findFee(1L, 3L, "REGULAR", "KRW")).thenReturn(new BigDecimal("150000"));
        when(repository.update(eq(1L), eq(1L), eq(3L), anyString(), anyString(), eq("REGULAR"), eq("KRW"),
                eq(new BigDecimal("150000")), eq(BigDecimal.ZERO), eq("SUBMITTED"), any())).thenReturn(1);
        service.update(1L, 1L, PreRegistrationUpdateRequest.builder().categorySeq(3L).periodType("REGULAR")
                .currency("USD").feeAmount(BigDecimal.ONE).applicationStatus("SUBMITTED").build());
        verify(repository).update(eq(1L), eq(1L), eq(3L), anyString(), anyString(), eq("REGULAR"), eq("KRW"),
                eq(new BigDecimal("150000")), eq(BigDecimal.ZERO), eq("SUBMITTED"), any());
    }

    @Test
    void createGeneratesNumberAndSavesMemberReference() {
        when(repository.lockMember(1L, 7L)).thenReturn(7L);
        when(repository.findSubmittedSeqForUpdate(7L, 1L)).thenReturn(null);
        stubValidConference();
        stubValidCategory();
        doAnswer(invocation -> {
            PreRegistrationResponse registration = invocation.getArgument(0);
            assertThat(registration.getMemberSeq()).isEqualTo(7L);
            assertThat(registration.getCategoryCode()).isEqualTo("PI_PHD");
            assertThat(registration.getCurrency()).isEqualTo("USD");
            assertThat(registration.getApplicationStatus()).isEqualTo("SUBMITTED");
            assertThat(registration.getAdminMemo()).isEqualTo("관리자 접수");
            assertThat(registration.getRegistrationNumber()).hasSize(30).startsWith("TMP-");
            registration.setSeq(42L);
            return 1;
        }).when(repository).insert(any());
        String number = "PR-" + LocalDate.now().getYear() + "-000042";
        when(repository.assignRegistrationNumber(1L, 42L, number)).thenReturn(1);
        when(repository.findBySeq(1L, 42L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(PreRegistrationResponse.builder()
                .seq(42L).registrationNumber(number).paymentStatus("UNPAID")
                .privacyAgreed(false).termsAgreed(false).build());

        PreRegistrationResponse created = service.create(1L, createRequest());

        assertThat(created.getRegistrationNumber()).isEqualTo(number);
        verify(repository).findSubmittedSeqForUpdate(7L, 1L);
        verify(repository).assignRegistrationNumber(1L, 42L, number);
    }

    @Test
    void createRequiresExistingMember() {
        when(repository.lockMember(1L, 7L)).thenReturn(null);
        assertThatThrownBy(() -> service.create(1L, createRequest()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("회원");
        verify(repository, never()).insert(any());
    }

    @Test
    void societyFeeIsRecheckedAndRejectsClientAmountOverride() {
        when(repository.lockMember(1L, 7L)).thenReturn(7L);
        when(repository.findSubmittedSeqForUpdate(7L, 1L)).thenReturn(null);
        stubValidConference();
        var quote = new com.bjworld21.conference.dto.SocietyMemberData.FeeQuote();
        quote.setCategorySeq(3L); quote.setAmount(new BigDecimal("100.00"));
        when(societyMembers.quote(eq(1L), any())).thenReturn(quote);
        PreRegistrationCreateRequest request = createRequest();
        request.setSocietyLicenseNumber("00123"); request.setSocietyMemberName("홍길동");
        assertThatThrownBy(() -> service.create(1L, request)).hasMessageContaining("등록비가 변경");
        verify(repository, never()).insert(any());
        verify(societyMembers).quote(eq(1L), any());
    }

    @Test
    void societyFeeIsSavedAfterServerVerification() {
        when(repository.lockMember(1L, 7L)).thenReturn(7L);
        when(repository.findSubmittedSeqForUpdate(7L, 1L)).thenReturn(null);
        stubValidConference();
        stubValidCategory();
        var quote = new com.bjworld21.conference.dto.SocietyMemberData.FeeQuote();
        quote.setCategorySeq(3L); quote.setAmount(new BigDecimal("450.00"));
        when(societyMembers.quote(eq(1L), any())).thenReturn(quote);
        doAnswer(invocation -> {
            PreRegistrationResponse value = invocation.getArgument(0);
            assertThat(value.getCategorySeq()).isEqualTo(3L);
            assertThat(value.getFeeAmount()).isEqualByComparingTo("450.00");
            value.setSeq(42L); return 1;
        }).when(repository).insert(any());
        when(repository.assignRegistrationNumber(eq(1L), eq(42L), anyString())).thenReturn(1);
        when(repository.findBySeq(1L, 42L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(PreRegistrationResponse.builder().seq(42L).build());
        PreRegistrationCreateRequest request = createRequest();
        request.setSocietyLicenseNumber("00123"); request.setSocietyMemberName("홍길동");
        request.setPeriodType("REGULAR"); request.setCurrency("USD");
        assertThat(service.create(1L, request).getSeq()).isEqualTo(42L);
        verify(societyMembers).quote(1L, new com.bjworld21.conference.dto.SocietyMemberData.QuoteRequest("00123", "홍길동", "REGULAR", "USD"));
    }

    @Test
    void createRejectsDuplicateSubmittedRegistration() {
        when(repository.lockMember(1L, 7L)).thenReturn(7L);
        stubValidConference();
        when(repository.findSubmittedSeqForUpdate(7L, 1L)).thenReturn(9L);
        assertThatThrownBy(() -> service.create(1L, createRequest()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("이미 신청 완료");
        verify(repository, never()).insert(any());
    }

    @Test
    void createRejectsMissingMemberSelection() {
        PreRegistrationCreateRequest request = createRequest();
        request.setMemberSeq(null);
        assertThatThrownBy(() -> service.create(1L, request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void createRejectsAmountOutsideDatabasePrecision() {
        when(repository.lockMember(1L, 7L)).thenReturn(7L);
        when(repository.findSubmittedSeqForUpdate(7L, 1L)).thenReturn(null);
        stubValidConference();
        stubValidCategory();
        PreRegistrationCreateRequest request = createRequest();
        request.setFeeAmount(new BigDecimal("450.001"));
        assertThatThrownBy(() -> service.create(1L, request)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("소수점 둘째");
        request.setFeeAmount(new BigDecimal("10000000000.00"));
        assertThatThrownBy(() -> service.create(1L, request)).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).insert(any());
    }

    @Test
    void createFailsWhenNumberCannotBeAssigned() {
        when(repository.lockMember(1L, 7L)).thenReturn(7L);
        when(repository.findSubmittedSeqForUpdate(7L, 1L)).thenReturn(null);
        stubValidConference();
        stubValidCategory();
        doAnswer(invocation -> { ((PreRegistrationResponse) invocation.getArgument(0)).setSeq(42L); return 1; })
                .when(repository).insert(any());
        when(repository.assignRegistrationNumber(eq(1L), eq(42L), anyString())).thenReturn(0);
        assertThatThrownBy(() -> service.create(1L, createRequest())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("등록번호");
        verify(repository, never()).findBySeq(anyLong(), anyLong(), anyString());
    }

    @Test
    void createCalculatesOptionPriceAndStoresSelectionOnServer() {
        when(repository.lockMember(1L, 7L)).thenReturn(7L);
        when(repository.findSubmittedSeqForUpdate(7L, 1L)).thenReturn(null);
        stubValidConference();
        stubValidCategory();
        RegistrationOptionData.Option option = new RegistrationOptionData.Option();
        option.setSeq(11L);
        option.setConferenceSeq(1L);
        option.setOptionName("Workshop");
        option.setDescription("Hands-on session");
        option.setUsdPrice(new BigDecimal("25.00"));
        option.setEnabled(true);
        option.setCapacity(30);
        option.setMaxPerPerson(2);
        when(repository.lockOption(1L, 11L)).thenReturn(option);
        when(repository.usedOptionQuantity(1L, 11L)).thenReturn(10);
        doAnswer(invocation -> {
            PreRegistrationResponse value = invocation.getArgument(0);
            assertThat(value.getConferenceSeq()).isEqualTo(1L);
            assertThat(value.getOptionAmount()).isEqualByComparingTo("50.00");
            assertThat(value.getTotalAmount()).isEqualByComparingTo("500.00");
            value.setSeq(42L);
            return 1;
        }).when(repository).insert(any());
        when(repository.assignRegistrationNumber(eq(1L), eq(42L), anyString())).thenReturn(1);
        when(repository.insertOption(eq(42L), any())).thenReturn(1);
        when(repository.findBySeq(1L, 42L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(PreRegistrationResponse.builder().seq(42L).build());
        PreRegistrationCreateRequest request = createRequest();
        request.setOptions(List.of(new PreRegistrationOptionData.Selection(11L, 2)));

        service.create(1L, request);

        verify(repository).insertOption(eq(42L), any());
    }

    @Test
    void createRejectsOptionFromAnotherConference() {
        when(repository.lockMember(1L, 7L)).thenReturn(7L);
        when(repository.findSubmittedSeqForUpdate(7L, 1L)).thenReturn(null);
        stubValidConference();
        stubValidCategory();
        RegistrationOptionData.Option option = new RegistrationOptionData.Option();
        option.setSeq(11L);
        option.setConferenceSeq(2L);
        when(repository.lockOption(1L, 11L)).thenReturn(option);
        PreRegistrationCreateRequest request = createRequest();
        request.setOptions(List.of(new PreRegistrationOptionData.Selection(11L, 1)));

        assertThatThrownBy(() -> service.create(1L, request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("선택한 학회");
        verify(repository, never()).insert(any());
    }

    @Test
    void createRejectsOptionWhenCapacityIsExhausted() {
        when(repository.lockMember(1L, 7L)).thenReturn(7L);
        when(repository.findSubmittedSeqForUpdate(7L, 1L)).thenReturn(null);
        stubValidConference();
        stubValidCategory();
        RegistrationOptionData.Option option = new RegistrationOptionData.Option();
        option.setSeq(11L);
        option.setConferenceSeq(1L);
        option.setOptionName("Workshop");
        option.setUsdPrice(new BigDecimal("25.00"));
        option.setEnabled(true);
        option.setCapacity(30);
        option.setMaxPerPerson(1);
        when(repository.lockOption(1L, 11L)).thenReturn(option);
        when(repository.usedOptionQuantity(1L, 11L)).thenReturn(30);
        PreRegistrationCreateRequest request = createRequest();
        request.setOptions(List.of(new PreRegistrationOptionData.Selection(11L, 1)));

        assertThatThrownBy(() -> service.create(1L, request))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("잔여 정원");
        verify(repository, never()).insert(any());
    }

    @Test
    void findPageReturnsFilteredSummaryAndRows() {
        PreRegistrationSummary summary = PreRegistrationSummary.builder()
                .totalCount(21)
                .submittedCount(19)
                .cancelledCount(2)
                .paidCount(10)
                .unpaidCount(9)
                .totalPaidKrwAmount(new BigDecimal("150000"))
                .totalPaidUsdAmount(new BigDecimal("3500.00"))
                .build();
        PreRegistrationResponse item = PreRegistrationResponse.builder()
                .seq(1L)
                .registrationNumber("PR-2026-000001")
                .build();

        when(repository.findSummary(1L, "kim", null, 55L, "EARLY_BIRD", "SUBMITTED", "PAID", null, null, PersonalDataTestSupport.DB_ENC_STRING))
                .thenReturn(summary);
        when(repository.findPage(1L, "kim", null, 55L, "EARLY_BIRD", "SUBMITTED", "PAID", null, null, 20, 20, PersonalDataTestSupport.DB_ENC_STRING))
                .thenReturn(List.of(item));

        var response = service.findPage(1L, 2, 20, "  kim  ", null, 55L, "early_bird", "submitted", "paid", null, null);

        assertThat(response.getPage()).isEqualTo(2);
        assertThat(response.getTotalPages()).isEqualTo(2);
        assertThat(response.getTotalCount()).isEqualTo(21);
        assertThat(response.getPaidCount()).isEqualTo(10);
        assertThat(response.getTotalPaidKrwAmount()).isEqualByComparingTo("150000");
        assertThat(response.getTotalPaidUsdAmount()).isEqualByComparingTo("3500.00");
        assertThat(response.getItems()).containsExactly(item);
    }

    @Test
    void findPageRejectsReversedDateRangeBeforeQuerying() {
        assertThatThrownBy(() -> service.findPage(
                1L,
                1,
                20,
                "",
                null,
                null,
                "",
                "",
                "",
                LocalDate.of(2026, 8, 25),
                LocalDate.of(2026, 8, 24)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("종료일");

        verifyNoInteractions(repository);
    }

    @Test
    void findDetailReportsMissingRegistration() {
        when(repository.findBySeq(1L, 99L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(null);

        assertThatThrownBy(() -> service.findDetail(1L, 99L))
                .isInstanceOf(PreRegistrationAdminService.PreRegistrationNotFoundException.class)
                .hasMessageContaining("찾을 수 없습니다");
    }

    @Test
    void cancelPaymentChangesPaidRegistrationToRefunded() {
        PreRegistrationResponse paid = PreRegistrationResponse.builder()
                .seq(1L)
                .paymentStatus("PAID")
                .build();
        PreRegistrationResponse refunded = PreRegistrationResponse.builder()
                .seq(1L)
                .paymentStatus("REFUNDED")
                .build();
        when(repository.findBySeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(paid, refunded);
        when(repository.cancelPayment(1L, 1L)).thenReturn(1);

        PreRegistrationResponse response = service.cancelPayment(1L, 1L);

        assertThat(response.getPaymentStatus()).isEqualTo("REFUNDED");
        verify(repository).cancelPayment(1L, 1L);
    }

    @Test
    void cancelPaymentRejectsUnpaidRegistration() {
        when(repository.findBySeq(1L, 2L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(PreRegistrationResponse.builder()
                .seq(2L)
                .paymentStatus("UNPAID")
                .build());

        assertThatThrownBy(() -> service.cancelPayment(1L, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결제 완료 상태");
    }

    @Test
    void updateChangesRegistrationFieldsAndRefreshesCategorySnapshot() {
        PreRegistrationUpdateRequest request = PreRegistrationUpdateRequest.builder()
                .categorySeq(3L)
                .periodType("regular")
                .currency("usd")
                .feeAmount(new BigDecimal("450.00"))
                .applicationStatus("cancelled")
                .adminMemo("  요청 확인  ")
                .build();
        PreRegistrationResponse before = PreRegistrationResponse.builder().seq(1L).build();
        PreRegistrationResponse after = PreRegistrationResponse.builder()
                .seq(1L)
                .categorySeq(3L)
                .categoryCode("PI_PHD")
                .categoryName("PI / Ph.D.")
                .applicationStatus("CANCELLED")
                .build();

        when(repository.findBySeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(before, after);
        when(repository.findCategoryBySeq(1L, 3L)).thenReturn(PreRegistrationCategoryOptionResponse.builder()
                .categorySeq(3L)
                .categoryCode("PI_PHD")
                .categoryName("PI / Ph.D.")
                .build());
        when(repository.update(
                1L, 1L, 3L, "PI_PHD", "PI / Ph.D.", "REGULAR", "USD",
                new BigDecimal("450.00"), BigDecimal.ZERO, "CANCELLED", "요청 확인"
        )).thenReturn(1);

        PreRegistrationResponse response = service.update(1L, 1L, request);

        assertThat(response.getCategoryCode()).isEqualTo("PI_PHD");
        assertThat(response.getApplicationStatus()).isEqualTo("CANCELLED");
        verify(repository).update(
                1L, 1L, 3L, "PI_PHD", "PI / Ph.D.", "REGULAR", "USD",
                new BigDecimal("450.00"), BigDecimal.ZERO, "CANCELLED", "요청 확인"
        );
    }

    @Test
    void updateRejectsMissingCategory() {
        when(repository.findBySeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(PreRegistrationResponse.builder().seq(1L).build());
        when(repository.findCategoryBySeq(1L, 99L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(1L, 1L, PreRegistrationUpdateRequest.builder()
                .categorySeq(99L)
                .periodType("REGULAR")
                .currency("USD")
                .feeAmount(BigDecimal.ZERO)
                .applicationStatus("SUBMITTED")
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("등록 구분");
    }

    @Test
    void updateRejectsAmountChangeAfterPayment() {
        when(repository.findFee(1L, 3L, "REGULAR", "USD")).thenReturn(new BigDecimal("451.00"));
        PreRegistrationResponse paid = PreRegistrationResponse.builder()
                .seq(1L).currency("USD").feeAmount(new BigDecimal("450.00"))
                .optionAmount(new BigDecimal("50.00")).totalAmount(new BigDecimal("500.00"))
                .paymentStatus("PAID").build();
        when(repository.findBySeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(paid);
        stubValidCategory();

        assertThatThrownBy(() -> service.update(1L, 1L, PreRegistrationUpdateRequest.builder()
                .categorySeq(3L).periodType("REGULAR").currency("USD")
                .feeAmount(new BigDecimal("451.00")).applicationStatus("SUBMITTED").build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("결제 이력");
        verify(repository, never()).update(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(),
                any(), any(), anyString(), any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"UNPAID,PAID", "PAID,REFUNDED", "PAID,UNPAID", "UNPAID,FAILED", "FAILED,PAID", "REFUNDED,PAID"})
    void updateChangesPaymentStatus(String previousStatus, String nextStatus) {
        PreRegistrationResponse before = PreRegistrationResponse.builder()
                .seq(1L).memberSeq(7L).currency("USD").paymentStatus(previousStatus)
                .applicationStatus("SUBMITTED").feeAmount(new BigDecimal("450.00"))
                .totalAmount(new BigDecimal("450.00")).build();
        PreRegistrationResponse after = PreRegistrationResponse.builder()
                .seq(1L).paymentStatus(nextStatus).build();
        when(repository.findBySeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(before, after);
        stubValidCategory();
        when(repository.update(eq(1L), eq(1L), eq(3L), anyString(), anyString(), eq("REGULAR"), eq("USD"),
                eq(new BigDecimal("450.00")), eq(BigDecimal.ZERO), eq("SUBMITTED"), any())).thenReturn(1);
        when(repository.updatePaymentStatus(1L, 1L, previousStatus, nextStatus)).thenReturn(1);
        PreRegistrationUpdateRequest request = PreRegistrationUpdateRequest.builder()
                .categorySeq(3L).periodType("REGULAR").currency("USD").feeAmount(new BigDecimal("450.00"))
                .applicationStatus("SUBMITTED").paymentStatus(nextStatus).build();

        assertThat(service.update(1L, 1L, request).getPaymentStatus()).isEqualTo(nextStatus);
        var order = org.mockito.Mockito.inOrder(repository);
        order.verify(repository).lockRegistration(1L, 1L);
        order.verify(repository).findBySeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING);
        verify(repository).updatePaymentStatus(1L, 1L, previousStatus, nextStatus);
    }

    @Test
    void updateRejectsInvalidPaymentStatusBeforeWriting() {
        when(repository.findBySeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(PreRegistrationResponse.builder().seq(1L).build());
        stubValidCategory();
        assertThatThrownBy(() -> service.update(1L, 1L, PreRegistrationUpdateRequest.builder()
                .categorySeq(3L).periodType("REGULAR").currency("USD").feeAmount(BigDecimal.ONE)
                .applicationStatus("SUBMITTED").paymentStatus("INVALID").build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("결제 상태");
        verify(repository, never()).updatePaymentStatus(any(), any(), any(), any());
        verify(repository, never()).update(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void previousPaymentRecordStillProtectsAmountAfterStatusCorrection() {
        when(repository.findBySeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(PreRegistrationResponse.builder()
                .seq(1L).memberSeq(7L).currency("USD").paymentStatus("UNPAID")
                .paidAmount(new BigDecimal("400.00")).totalAmount(new BigDecimal("400.00")).build());
        stubValidCategory();
        assertThatThrownBy(() -> service.update(1L, 1L, PreRegistrationUpdateRequest.builder()
                .categorySeq(3L).periodType("REGULAR").currency("USD").feeAmount(new BigDecimal("450.00"))
                .applicationStatus("SUBMITTED").paymentStatus("PAID").build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("결제 이력");
        verify(repository, never()).updatePaymentStatus(any(), any(), any(), any());
    }

    @Test
    void updateReplacesOptionsUsingCurrentPrice() {
        PreRegistrationOptionData.Item oldItem = PreRegistrationOptionData.Item.builder()
                .seq(1L).optionSeq(11L).optionName("Workshop").currency("USD")
                .unitPrice(new BigDecimal("25.00")).quantity(1).amount(new BigDecimal("25.00")).build();
        PreRegistrationOptionData.Item newItem = PreRegistrationOptionData.Item.builder()
                .seq(2L).optionSeq(11L).optionName("Workshop").currency("USD")
                .unitPrice(new BigDecimal("30.00")).quantity(2).amount(new BigDecimal("60.00")).build();
        PreRegistrationResponse before = PreRegistrationResponse.builder()
                .seq(1L).conferenceSeq(1L).currency("USD").feeAmount(new BigDecimal("450.00"))
                .optionAmount(new BigDecimal("25.00")).totalAmount(new BigDecimal("475.00"))
                .paymentStatus("UNPAID").applicationStatus("SUBMITTED").build();
        PreRegistrationResponse after = PreRegistrationResponse.builder()
                .seq(1L).conferenceSeq(1L).currency("USD").feeAmount(new BigDecimal("450.00"))
                .optionAmount(new BigDecimal("60.00")).totalAmount(new BigDecimal("510.00"))
                .paymentStatus("UNPAID").applicationStatus("SUBMITTED").build();
        when(repository.findBySeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(before, after);
        when(repository.findOptionsByRegistrationSeq(1L, 1L)).thenReturn(List.of(oldItem), List.of(newItem));
        stubValidCategory();
        stubValidConference();
        when(repository.findOptionSeqs(1L, 1L)).thenReturn(List.of(11L));
        RegistrationOptionData.Option option = new RegistrationOptionData.Option();
        option.setSeq(11L); option.setConferenceSeq(1L); option.setOptionName("Workshop");
        option.setUsdPrice(new BigDecimal("30.00")); option.setMaxPerPerson(2); option.setCapacity(30);
        option.setEnabled(false);
        when(repository.lockOption(1L, 11L)).thenReturn(option);
        when(repository.usedOptionQuantityExcluding(1L, 11L, 1L)).thenReturn(10);
        when(repository.update(1L, 1L, 3L, "PI_PHD", "PI / Ph.D.", "REGULAR", "USD",
                new BigDecimal("450.00"), new BigDecimal("60.00"), "SUBMITTED", null)).thenReturn(1);
        when(repository.insertOption(eq(1L), any())).thenReturn(1);
        PreRegistrationUpdateRequest request = PreRegistrationUpdateRequest.builder()
                .categorySeq(3L).periodType("REGULAR").currency("USD")
                .feeAmount(new BigDecimal("450.00")).applicationStatus("SUBMITTED")
                .options(List.of(new PreRegistrationOptionData.Selection(11L, 2))).build();

        PreRegistrationResponse result = service.update(1L, 1L, request);

        assertThat(result.getTotalAmount()).isEqualByComparingTo("510.00");
        verify(repository).deleteOptions(1L);
        verify(repository).insertOption(eq(1L), any());
    }

    @Test
    void deleteRemovesRegistrationWhenPaymentIsNotPaid() {
        when(repository.findBySeq(1L, 3L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(PreRegistrationResponse.builder()
                .seq(3L)
                .paymentStatus("REFUNDED")
                .build());
        when(repository.delete(1L, 3L)).thenReturn(1);

        service.delete(1L, 3L);

        verify(repository).delete(1L, 3L);
    }

    @Test
    void deleteRejectsPaidRegistration() {
        when(repository.findBySeq(1L, 4L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(PreRegistrationResponse.builder()
                .seq(4L)
                .paymentStatus("PAID")
                .build());

        assertThatThrownBy(() -> service.delete(1L, 4L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결제 취소 후 삭제");
    }
}
