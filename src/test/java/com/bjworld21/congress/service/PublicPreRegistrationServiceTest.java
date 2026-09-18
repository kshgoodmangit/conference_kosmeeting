package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.dto.PaymentCheckoutRequest;
import com.bjworld21.congress.dto.PaymentCheckoutResponse;
import com.bjworld21.congress.dto.PaymentGatewaySummaryResponse;
import com.bjworld21.congress.dto.PreRegistrationCategoryOptionResponse;
import com.bjworld21.congress.dto.PreRegistrationOptionData;
import com.bjworld21.congress.dto.PreRegistrationResponse;
import com.bjworld21.congress.dto.PublicPreRegistrationData;
import com.bjworld21.congress.dto.RegistrationFeeCategoryResponse;
import com.bjworld21.congress.dto.RegistrationOptionData;
import com.bjworld21.congress.entity.ConferenceSettings;
import com.bjworld21.congress.entity.Member;
import com.bjworld21.congress.payment.PaymentGatewayService;
import com.bjworld21.congress.repository.ConferenceSettingsRepository;
import com.bjworld21.congress.repository.MemberRepository;
import com.bjworld21.congress.repository.PreRegistrationRepository;
import com.bjworld21.congress.repository.RegistrationFeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicPreRegistrationServiceTest {
    @Mock PreRegistrationRepository registrations;
    @Mock RegistrationFeeRepository fees;
    @Mock ConferenceSettingsRepository conferences;
    @Mock MemberRepository members;
    @Mock PaymentGatewayService payments;

    private PublicPreRegistrationService service;
    private PersonalDataProperties personalData;

    @BeforeEach
    void setUp() {
        personalData = PersonalDataTestSupport.properties();
        Clock clock = Clock.fixed(Instant.parse("2026-09-14T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new PublicPreRegistrationService(
                registrations, fees, conferences, members, personalData, payments, clock
        );
        org.mockito.Mockito.lenient().when(conferences.findBySeq(1L)).thenReturn(ConferenceSettings.builder()
                .seq(1L).eventName("APDRC8")
                .earlyBirdStartDate(LocalDate.of(2026, 9, 1))
                .earlyBirdEndDate(LocalDate.of(2026, 9, 30))
                .regularStartDate(LocalDate.of(2026, 10, 1))
                .regularEndDate(LocalDate.of(2026, 10, 31))
                .build());
        org.mockito.Mockito.lenient().when(members.findBySeq(1L, 7L, personalData.requireDbEncString())).thenReturn(Member.builder()
                .seq(7L).memberType("international").email("jane@example.com")
                .firstName("Jane").lastName("Doe").institution("ICMS")
                .country("United States").mobile("+1 202 555 0100").build());
        org.mockito.Mockito.lenient().when(payments.summary()).thenReturn(new PaymentGatewaySummaryResponse(
                true, "paygate", "PayGate", "", List.of(
                new PaymentGatewaySummaryResponse.RouteSummary(
                        "international-card", "International card", true, "****", "104", "USD", "US"
                ),
                new PaymentGatewaySummaryResponse.RouteSummary(
                        "domestic-card", "Domestic card", true, "****", "card", "KRW", "KR"
                )
        )));
    }

    @ParameterizedTest
    @CsvSource({
            "international, South Korea, KRW, 150000",
            "international, ' south korea ', KRW, 150000",
            "international, Republic of Korea, KRW, 150000",
            "international, 'Korea, Republic of', KRW, 150000",
            "international, KR, KRW, 150000",
            "international, KOR, KRW, 150000",
            "international, 대한민국, KRW, 150000",
            "international, United States, USD, 120",
            "international, North Korea, USD, 120",
            "international, , USD, 120",
            "domestic, Japan, USD, 120",
            "domestic, , KRW, 150000"
    })
    void newFormUsesCountryForFeesOptionsAndPaymentAvailability(
            String memberType, String country, String currency, BigDecimal fee
    ) {
        member().setMemberType(memberType);
        member().setCountry(country);
        stubFormFees();

        PublicPreRegistrationData.Form form = service.form(1L, 7L);

        assertThat(form.currency()).isEqualTo(currency);
        assertThat(form.categories()).singleElement()
                .satisfies(category -> assertThat(category.feeAmount()).isEqualByComparingTo(fee));
        assertThat(form.paymentAvailable()).isTrue();
        assertThat(form.member().country()).isEqualTo(
                country == null ? ("domestic".equals(memberType) ? "South Korea" : "") : country.trim());
        verify(registrations).findPublicAvailableOptions(eq(1L), eq(currency), any(LocalDateTime.class));
    }

    @Test
    void koreanMemberCannotUseInternationalGatewayAsFallbackWhenDomesticGatewayIsUnavailable() {
        member().setCountry("South Korea");
        stubFormFees();
        when(payments.summary()).thenReturn(new PaymentGatewaySummaryResponse(
                true, "paygate", "PayGate", "", List.of(
                new PaymentGatewaySummaryResponse.RouteSummary(
                        "international-card", "International card", true, "****", "104", "USD", "US"))));

        PublicPreRegistrationData.Form form = service.form(1L, 7L);

        assertThat(form.currency()).isEqualTo("KRW");
        assertThat(form.paymentAvailable()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"USD, South Korea, 120, UNPAID", "KRW, United States, 150000, UNPAID",
            "USD, South Korea, 120, PAID", "KRW, United States, 150000, PAID"})
    void existingFormKeepsSavedCurrencyAndAmountAfterCountryChange(
            String currency, String country, BigDecimal fee, String paymentStatus
    ) {
        member().setCountry(country);
        stubFormFees();
        when(registrations.findSubmittedSeq(7L, 1L)).thenReturn(42L);
        when(registrations.findBySeq(1L, 42L, personalData.requireDbEncString())).thenReturn(
                PreRegistrationResponse.builder().seq(42L).categorySeq(3L).currency(currency)
                        .feeAmount(new BigDecimal("100")).totalAmount(new BigDecimal("100"))
                        .paymentStatus(paymentStatus).build());

        PublicPreRegistrationData.Form form = service.form(1L, 7L);

        assertThat(form.currency()).isEqualTo(currency);
        assertThat(form.registration().currency()).isEqualTo(currency);
        assertThat(form.registration().totalAmount()).isEqualByComparingTo("100");
        assertThat(form.categories()).singleElement()
                .satisfies(category -> assertThat(category.feeAmount()).isEqualByComparingTo(fee));
        assertThat(form.paymentAvailable()).isTrue();
        verify(registrations).findPublicAvailableOptions(eq(1L), eq(currency), any(LocalDateTime.class));
    }

    @Test
    void formUsesCurrentPeriodSavedCurrencyAndOnlyEnabledFees() {
        when(registrations.findSubmittedSeq(7L, 1L)).thenReturn(42L);
        when(registrations.findBySeq(1L, 42L, personalData.requireDbEncString())).thenReturn(
                PreRegistrationResponse.builder()
                        .seq(42L).registrationNumber("PR-2026-000042").categorySeq(3L).categoryName("Member")
                        .periodType("EARLY_BIRD").currency("USD").feeAmount(new BigDecimal("120.00"))
                        .optionAmount(new BigDecimal("70.00")).totalAmount(new BigDecimal("190.00"))
                        .applicationStatus("SUBMITTED").paymentStatus("UNPAID").build()
        );
        when(registrations.findOptionsByRegistrationSeq(1L, 42L)).thenReturn(List.of(
                PreRegistrationOptionData.Item.builder()
                        .optionSeq(9L).optionName("Gala Ticket").quantity(1).build()
        ));
        when(fees.findAllActiveWithFees(1L)).thenReturn(List.of(
                RegistrationFeeCategoryResponse.builder().seq(3L).categoryCode("MEMBER")
                        .categoryName("Member").isUsed("Y").earlyBirdUsdFee(new BigDecimal("120.00")).build(),
                RegistrationFeeCategoryResponse.builder().seq(4L).categoryCode("HIDDEN")
                        .categoryName("Hidden").isUsed("N").earlyBirdUsdFee(new BigDecimal("1.00")).build()
        ));
        when(registrations.findPublicAvailableOptions(eq(1L), eq("USD"), any(LocalDateTime.class)))
                .thenReturn(List.of());

        PublicPreRegistrationData.Form form = service.form(1L, 7L);

        assertThat(form.periodType()).isEqualTo("EARLY_BIRD");
        assertThat(form.currency()).isEqualTo("USD");
        assertThat(form.categories()).singleElement().satisfies(category -> {
            assertThat(category.categoryName()).isEqualTo("Member");
            assertThat(category.feeAmount()).isEqualByComparingTo("120.00");
        });
        assertThat(form.paymentAvailable()).isTrue();
        assertThat(form.registration().options()).singleElement().satisfies(option -> {
            assertThat(option.getOptionName()).isEqualTo("Gala Ticket");
            assertThat(option.getQuantity()).isEqualTo(1);
        });
    }

    @Test
    void formKeepsTheCurrentMembersOptionVisibleWhenItIsSoldOut() {
        when(registrations.findSubmittedSeq(7L, 1L)).thenReturn(42L);
        when(registrations.findBySeq(1L, 42L, personalData.requireDbEncString())).thenReturn(
                PreRegistrationResponse.builder()
                        .seq(42L).categorySeq(3L).categoryName("Member").currency("USD")
                        .feeAmount(new BigDecimal("120.00")).paymentStatus("UNPAID").build()
        );
        when(registrations.findOptionsByRegistrationSeq(1L, 42L)).thenReturn(List.of(
                PreRegistrationOptionData.Item.builder()
                        .optionSeq(9L).optionName("Gala Ticket").quantity(2).build()
        ));
        when(fees.findAllActiveWithFees(1L)).thenReturn(List.of(
                RegistrationFeeCategoryResponse.builder().seq(3L).categoryName("Member").isUsed("Y")
                        .earlyBirdUsdFee(new BigDecimal("120.00")).build()
        ));
        when(registrations.findPublicAvailableOptions(eq(1L), eq("USD"), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        PreRegistrationOptionData.CatalogItem.builder()
                                .optionSeq(9L).optionName("Gala Ticket").remainingCapacity(0).build(),
                        PreRegistrationOptionData.CatalogItem.builder()
                                .optionSeq(10L).optionName("Workshop").remainingCapacity(0).build()
                ));

        PublicPreRegistrationData.Form form = service.form(1L, 7L);

        assertThat(form.options()).singleElement()
                .satisfies(option -> assertThat(option.getOptionSeq()).isEqualTo(9L));
    }

    @ParameterizedTest
    @CsvSource({
            "international, South Korea, KRW, 150000, 25000",
            "international, United States, USD, 120, 25",
            "domestic, Japan, USD, 120, 25",
            "domestic, , KRW, 150000, 25000"
    })
    void createRecalculatesFeeAndOptionsInCountryCurrencyAndStoresConsentOnTheServer(
            String memberType, String country, String currency, BigDecimal fee, BigDecimal optionPrice
    ) {
        member().setMemberType(memberType);
        member().setCountry(country);
        when(registrations.lockMember(1L, 7L)).thenReturn(7L);
        when(registrations.findSubmittedSeqForUpdate(7L, 1L)).thenReturn(null);
        when(registrations.findPublicCategoryBySeq(1L, 3L)).thenReturn(
                PreRegistrationCategoryOptionResponse.builder()
                        .categorySeq(3L).categoryCode("MEMBER").categoryName("Member").build()
        );
        when(registrations.findFee(1L, 3L, "EARLY_BIRD", currency)).thenReturn(fee);
        RegistrationOptionData.Option option = new RegistrationOptionData.Option();
        option.setSeq(9L);
        option.setConferenceSeq(1L);
        option.setOptionName("Gala Ticket");
        option.setKrwPrice(new BigDecimal("25000"));
        option.setUsdPrice(new BigDecimal("25"));
        option.setMaxPerPerson(2);
        option.setEnabled(true);
        when(registrations.lockOption(1L, 9L)).thenReturn(option);
        when(registrations.insertOption(eq(42L), any())).thenReturn(1);
        doAnswer(invocation -> {
            PreRegistrationResponse value = invocation.getArgument(0);
            assertThat(value.getCurrency()).isEqualTo(currency);
            assertThat(value.getFeeAmount()).isEqualByComparingTo(fee);
            assertThat(value.getTotalAmount()).isEqualByComparingTo(fee.add(optionPrice));
            assertThat(value.getPrivacyAgreed()).isTrue();
            assertThat(value.getTermsAgreed()).isTrue();
            assertThat(value.getPaymentStatus()).isEqualTo("UNPAID");
            value.setSeq(42L);
            return 1;
        }).when(registrations).insertPublic(any());
        when(registrations.assignRegistrationNumber(eq(1L), eq(42L), any())).thenReturn(1);
        PublicPreRegistrationData.Request request = new PublicPreRegistrationData.Request();
        request.setCategorySeq(3L);
        request.setPrivacyAgreed(true);
        request.setTermsAgreed(true);
        request.setOptions(List.of(new PreRegistrationOptionData.Selection(9L, 1)));

        PublicPreRegistrationData.Registration created = service.create(1L, 7L, request);

        assertThat(created.registrationNumber()).isEqualTo("PR-2026-000042");
        assertThat(created.periodType()).isEqualTo("EARLY_BIRD");
        assertThat(created.currency()).isEqualTo(currency);
        assertThat(created.options()).singleElement().satisfies(item -> {
            assertThat(item.getCurrency()).isEqualTo(currency);
            assertThat(item.getUnitPrice()).isEqualByComparingTo(optionPrice);
        });
        verify(registrations).insertPublic(any());
    }

    @Test
    void createRejectsMissingConsentBeforeWriting() {
        PublicPreRegistrationData.Request request = new PublicPreRegistrationData.Request();
        request.setCategorySeq(3L);
        request.setPrivacyAgreed(true);
        request.setTermsAgreed(false);

        assertThatThrownBy(() -> service.create(1L, 7L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be accepted");
        verify(registrations, never()).insertPublic(any());
    }

    @Test
    void createRejectsClosedRegistrationPeriodBeforeWriting() {
        when(registrations.lockMember(1L, 7L)).thenReturn(7L);
        when(registrations.findSubmittedSeqForUpdate(7L, 1L)).thenReturn(null);
        when(conferences.findBySeq(1L)).thenReturn(ConferenceSettings.builder()
                .seq(1L)
                .regularStartDate(LocalDate.of(2026, 10, 1))
                .regularEndDate(LocalDate.of(2026, 10, 31))
                .build());
        PublicPreRegistrationData.Request request = new PublicPreRegistrationData.Request();
        request.setCategorySeq(3L);
        request.setPrivacyAgreed(true);
        request.setTermsAgreed(true);

        assertThatThrownBy(() -> service.create(1L, 7L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Online registration is currently closed.");
        verify(registrations, never()).insertPublic(any());
    }

    @ParameterizedTest
    @CsvSource({"USD, South Korea, 120, 50, 170", "KRW, United States, 150000, 50000, 200000"})
    void updateKeepsSavedCurrencyAfterCountryChangesAndRecalculatesTheTotal(
            String currency, String country, BigDecimal fee, BigDecimal optionAmount, BigDecimal total
    ) {
        member().setCountry(country);
        when(registrations.findSubmittedSeq(7L, 1L)).thenReturn(42L);
        when(registrations.lockRegistration(1L, 42L)).thenReturn(42L);
        when(registrations.findBySeq(1L, 42L, personalData.requireDbEncString())).thenReturn(
                PreRegistrationResponse.builder()
                        .seq(42L).memberSeq(7L).paymentStatus("UNPAID").applicationStatus("SUBMITTED")
                        .currency(currency)
                        .build()
        );
        when(registrations.findPublicCategoryBySeq(1L, 3L)).thenReturn(
                PreRegistrationCategoryOptionResponse.builder()
                        .categorySeq(3L).categoryCode("MEMBER").categoryName("Member").build()
        );
        when(registrations.findFee(1L, 3L, "EARLY_BIRD", currency)).thenReturn(fee);
        RegistrationOptionData.Option option = new RegistrationOptionData.Option();
        option.setSeq(9L);
        option.setConferenceSeq(1L);
        option.setOptionName("Gala Ticket");
        option.setUsdPrice(new BigDecimal("25.00"));
        option.setKrwPrice(new BigDecimal("25000"));
        option.setMaxPerPerson(2);
        option.setCapacity(10);
        option.setEnabled(true);
        when(registrations.lockOption(1L, 9L)).thenReturn(option);
        when(registrations.usedOptionQuantityExcluding(1L, 9L, 42L)).thenReturn(8);
        when(registrations.update(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(registrations.insertOption(eq(42L), any())).thenReturn(1);
        PublicPreRegistrationData.Request request = new PublicPreRegistrationData.Request();
        request.setCategorySeq(3L);
        request.setOptions(List.of(new PreRegistrationOptionData.Selection(9L, 2)));

        PublicPreRegistrationData.Registration updated = service.update(1L, 7L, request);

        assertThat(updated.paymentStatus()).isEqualTo("UNPAID");
        assertThat(updated.currency()).isEqualTo(currency);
        assertThat(updated.optionAmount()).isEqualByComparingTo(optionAmount);
        assertThat(updated.totalAmount()).isEqualByComparingTo(total);
        assertThat(updated.options()).singleElement()
                .satisfies(item -> assertThat(item.getQuantity()).isEqualTo(2));
        verify(registrations).usedOptionQuantityExcluding(1L, 9L, 42L);
        verify(registrations).deleteOptions(42L);
    }

    @Test
    void updateRejectsAPaidRegistration() {
        when(registrations.findSubmittedSeq(7L, 1L)).thenReturn(42L);
        when(registrations.lockRegistration(1L, 42L)).thenReturn(42L);
        when(registrations.findBySeq(1L, 42L, personalData.requireDbEncString())).thenReturn(
                PreRegistrationResponse.builder().seq(42L).memberSeq(7L).paymentStatus("PAID").build()
        );
        PublicPreRegistrationData.Request request = new PublicPreRegistrationData.Request();
        request.setCategorySeq(3L);

        assertThatThrownBy(() -> service.update(1L, 7L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only registrations awaiting payment can be changed.");
        verify(registrations, never()).update(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"international, KRW, domestic-card", "domestic, USD, international-card"})
    void checkoutUsesSavedCurrencyAndAmountRegardlessOfMemberType(
            String memberType, String currency, String route
    ) {
        stubCheckoutRegistration(memberType, currency);
        when(payments.prepareCheckout(any())).thenAnswer(invocation -> {
            PaymentCheckoutRequest request = invocation.getArgument(0);
            assertThat(request.getMemberType()).isEqualTo(memberType);
            assertThat(request.getRoute()).isEqualTo(route);
            assertThat(request.getAmount()).isEqualByComparingTo("150000");
            assertThat(request.getOrderNumber()).isEqualTo("PR-2026-000042");
            return checkoutResponse(route, currency, request.getAmount());
        });

        PaymentCheckoutResponse checkout = service.checkout(1L, 7L);

        assertThat(checkout.route()).isEqualTo(route);
        assertThat(checkout.currency()).isEqualTo(currency);
    }

    @ParameterizedTest
    @CsvSource({"USD, 150000", "KRW, 1"})
    void checkoutRejectsGatewayCurrencyOrAmountMismatch(String currency, BigDecimal amount) {
        stubCheckoutRegistration("international", "KRW");
        when(payments.prepareCheckout(any())).thenReturn(checkoutResponse("domestic-card", currency, amount));

        assertThatThrownBy(() -> service.checkout(1L, 7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match the registration");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CAD", ""})
    void checkoutRejectsUnsupportedSavedCurrencyBeforeOpeningGateway(String currency) {
        stubCheckoutRegistration("international", currency);

        assertThatThrownBy(() -> service.checkout(1L, 7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The registration currency is not supported.");
        verify(payments, never()).prepareCheckout(any());
    }

    @Test
    void checkoutRejectsClosedRegistrationPeriodBeforeOpeningGateway() {
        when(conferences.findBySeq(1L)).thenReturn(ConferenceSettings.builder()
                .seq(1L)
                .earlyBirdEndDate(LocalDate.of(2026, 9, 13))
                .regularStartDate(LocalDate.of(2026, 10, 1))
                .build());

        assertThatThrownBy(() -> service.checkout(1L, 7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Online registration is currently closed.");
        verify(payments, never()).prepareCheckout(any());
    }

    private Member member() {
        return members.findBySeq(1L, 7L, personalData.requireDbEncString());
    }

    private void stubFormFees() {
        when(fees.findAllActiveWithFees(1L)).thenReturn(List.of(
                RegistrationFeeCategoryResponse.builder().seq(3L).categoryName("Member").isUsed("Y")
                        .earlyBirdKrwFee(new BigDecimal("150000")).earlyBirdUsdFee(new BigDecimal("120")).build()));
    }

    private void stubCheckoutRegistration(String memberType, String currency) {
        when(registrations.findSubmittedSeq(7L, 1L)).thenReturn(42L);
        when(registrations.findBySeq(1L, 42L, personalData.requireDbEncString())).thenReturn(
                PreRegistrationResponse.builder().seq(42L).memberSeq(7L).memberType(memberType)
                        .registrationNumber("PR-2026-000042").currency(currency)
                        .totalAmount(new BigDecimal("150000")).paymentStatus("UNPAID")
                        .eventName("APDRC8").firstName("Jane").lastName("Doe").email("jane@example.com").build());
    }

    private PaymentCheckoutResponse checkoutResponse(String route, String currency, BigDecimal amount) {
        return new PaymentCheckoutResponse("paygate", "PayGate", route, "", "", "",
                "PR-2026-000042", currency, amount, Map.of(), List.of(), "");
    }

    @Test
    void cancelChangesOnlyTheLoggedInMembersUnpaidRegistration() {
        when(registrations.findSubmittedSeq(7L, 1L)).thenReturn(42L);
        when(registrations.lockRegistration(1L, 42L)).thenReturn(42L);
        when(registrations.findBySeq(1L, 42L, personalData.requireDbEncString())).thenReturn(
                PreRegistrationResponse.builder()
                        .seq(42L).memberSeq(7L).paymentStatus("UNPAID")
                        .build()
        );
        when(registrations.cancelByMember(1L, 42L, 7L)).thenReturn(1);

        service.cancel(1L, 7L);

        verify(registrations).cancelByMember(1L, 42L, 7L);
    }

    @Test
    void cancelRejectsPaidRegistration() {
        when(registrations.findSubmittedSeq(7L, 1L)).thenReturn(42L);
        when(registrations.lockRegistration(1L, 42L)).thenReturn(42L);
        when(registrations.findBySeq(1L, 42L, personalData.requireDbEncString())).thenReturn(
                PreRegistrationResponse.builder()
                        .seq(42L).memberSeq(7L).paymentStatus("PAID")
                        .build()
        );

        assertThatThrownBy(() -> service.cancel(1L, 7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Paid registrations");
        verify(registrations, never()).cancelByMember(any(), any(), any());
    }
}
