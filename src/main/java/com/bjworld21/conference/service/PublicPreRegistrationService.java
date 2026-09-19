package com.bjworld21.conference.service;

import com.bjworld21.conference.config.PersonalDataProperties;
import com.bjworld21.conference.dto.PaymentCheckoutRequest;
import com.bjworld21.conference.dto.PaymentCheckoutResponse;
import com.bjworld21.conference.dto.PreRegistrationCategoryOptionResponse;
import com.bjworld21.conference.dto.PreRegistrationOptionData;
import com.bjworld21.conference.dto.PreRegistrationResponse;
import com.bjworld21.conference.dto.PublicPreRegistrationData;
import com.bjworld21.conference.dto.RegistrationFeeCategoryResponse;
import com.bjworld21.conference.dto.RegistrationOptionData;
import com.bjworld21.conference.entity.ConferenceSettings;
import com.bjworld21.conference.entity.Member;
import com.bjworld21.conference.payment.PaymentGatewayService;
import com.bjworld21.conference.repository.ConferenceSettingsRepository;
import com.bjworld21.conference.repository.MemberRepository;
import com.bjworld21.conference.repository.PreRegistrationRepository;
import com.bjworld21.conference.repository.RegistrationFeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class PublicPreRegistrationService {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    private final PreRegistrationRepository registrations;
    private final RegistrationFeeRepository fees;
    private final ConferenceSettingsRepository conferences;
    private final MemberRepository members;
    private final PersonalDataProperties personalData;
    private final PaymentGatewayService payments;
    private final Clock clock;

    @Autowired
    public PublicPreRegistrationService(
            PreRegistrationRepository registrations,
            RegistrationFeeRepository fees,
            ConferenceSettingsRepository conferences,
            MemberRepository members,
            PersonalDataProperties personalData,
            PaymentGatewayService payments,
            Clock clock
    ) {
        this.registrations = registrations;
        this.fees = fees;
        this.conferences = conferences;
        this.members = members;
        this.personalData = personalData;
        this.payments = payments;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PublicPreRegistrationData.Form form(Long conferenceSeq, Long memberSeq) {
        ConferenceSettings conference = requireConference(conferenceSeq);
        Member member = requireMember(conferenceSeq, memberSeq);
        PublicPreRegistrationData.Registration existing = currentRegistration(conferenceSeq, memberSeq);
        Period period = currentPeriod(conference);
        String currency = existing == null ? memberCurrency(member) : registrationCurrency(existing.currency());
        List<PublicPreRegistrationData.Category> categories = fees.findAllActiveWithFees(conferenceSeq).stream()
                .filter(category -> "Y".equalsIgnoreCase(category.getIsUsed()))
                .map(category -> toCategory(category, period.type(), currency))
                .filter(category -> category.feeAmount() != null)
                .toList();
        Set<Long> selectedOptionSeqs = new HashSet<>();
        if (existing != null) existing.options().forEach(option -> selectedOptionSeqs.add(option.getOptionSeq()));
        LocalDateTime now = LocalDateTime.now(clock);
        List<PreRegistrationOptionData.CatalogItem> options = registrations
                .findPublicAvailableOptions(conferenceSeq, currency, now).stream()
                .filter(option -> option.getRemainingCapacity() == null || option.getRemainingCapacity() > 0
                        || selectedOptionSeqs.contains(option.getOptionSeq()))
                .toList();
        boolean paymentAvailable = paymentAvailable(currency);
        String message = existing != null && "PAID".equals(existing.paymentStatus())
                ? "Your registration and payment are complete."
                : period.type() == null
                ? "Online registration is currently closed."
                : existing != null && paymentAvailable
                ? "Your registration is saved. Continue with payment."
                : existing != null
                ? "Your registration is saved, but credit card payment is not currently available."
                : categories.isEmpty() ? "Registration fees have not been configured."
                : paymentAvailable ? "" : "Credit card payment is not currently available.";

        return new PublicPreRegistrationData.Form(
                new PublicPreRegistrationData.Member(
                        member.getMemberType(), member.getEmail(), member.getFirstName(), member.getLastName(),
                        member.getInstitution(), memberCountry(member), member.getMobile()
                ),
                period.type(), period.label(), period.start(), period.end(), currency,
                categories, options, existing,
                period.type() != null && !categories.isEmpty(), paymentAvailable, message
        );
    }

    @Transactional
    public PublicPreRegistrationData.Registration create(
            Long conferenceSeq,
            Long memberSeq,
            PublicPreRegistrationData.Request request
    ) {
        if (request == null || request.getCategorySeq() == null || request.getCategorySeq() <= 0) {
            throw new IllegalArgumentException("Please select a registration category.");
        }
        if (!Boolean.TRUE.equals(request.getPrivacyAgreed()) || !Boolean.TRUE.equals(request.getTermsAgreed())) {
            throw new IllegalArgumentException("Privacy policy and registration terms must be accepted.");
        }
        if (registrations.lockMember(conferenceSeq, memberSeq) == null) {
            throw new IllegalArgumentException("Member information could not be found.");
        }
        ConferenceSettings conference = requireConference(conferenceSeq);
        if (registrations.findSubmittedSeqForUpdate(memberSeq, conferenceSeq) != null) {
            throw new IllegalStateException("A submitted registration already exists.");
        }

        Member member = requireMember(conferenceSeq, memberSeq);
        Period period = currentPeriod(conference);
        if (period.type() == null) {
            throw new IllegalStateException("Online registration is currently closed.");
        }
        String currency = memberCurrency(member);
        PreRegistrationCategoryOptionResponse category = registrations
                .findPublicCategoryBySeq(conferenceSeq, request.getCategorySeq());
        if (category == null) {
            throw new IllegalArgumentException("The selected registration category is not available.");
        }
        BigDecimal fee = registrations.findFee(conferenceSeq, category.getCategorySeq(), period.type(), currency);
        if (fee == null) {
            throw new IllegalArgumentException("The registration fee is not configured.");
        }
        validateAmount(fee, "Registration fee");

        List<PreRegistrationOptionData.Item> options = resolveOptions(
                conferenceSeq, currency, request.getOptions(), LocalDateTime.now(clock), null);
        BigDecimal optionAmount = options.stream().map(PreRegistrationOptionData.Item::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = fee.add(optionAmount);
        validateAmount(totalAmount, "Total amount");
        boolean free = totalAmount.signum() == 0;

        PreRegistrationResponse registration = PreRegistrationResponse.builder()
                .registrationNumber("TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 26))
                .memberSeq(memberSeq)
                .conferenceSeq(conferenceSeq)
                .categorySeq(category.getCategorySeq())
                .categoryCode(category.getCategoryCode())
                .categoryName(category.getCategoryName())
                .periodType(period.type())
                .currency(currency)
                .feeAmount(fee)
                .optionAmount(optionAmount)
                .totalAmount(totalAmount)
                .applicationStatus("SUBMITTED")
                .paymentStatus(free ? "PAID" : "UNPAID")
                .paymentMethod(free ? "FREE" : null)
                .paidAmount(free ? BigDecimal.ZERO : null)
                .paidAt(free ? LocalDateTime.now(clock) : null)
                .privacyAgreed(true)
                .termsAgreed(true)
                .build();
        if (registrations.insertPublic(registration) != 1 || registration.getSeq() == null) {
            throw new IllegalStateException("Registration could not be saved.");
        }
        String number = "PR-" + LocalDate.now(clock).getYear() + "-"
                + String.format(Locale.ROOT, "%06d", registration.getSeq());
        if (registrations.assignRegistrationNumber(conferenceSeq, registration.getSeq(), number) != 1) {
            throw new IllegalStateException("Registration number could not be issued.");
        }
        for (PreRegistrationOptionData.Item option : options) {
            if (registrations.insertOption(registration.getSeq(), option) != 1) {
                throw new IllegalStateException("Registration option could not be saved.");
            }
        }
        registration.setRegistrationNumber(number);
        return toRegistration(registration, options);
    }

    @Transactional
    public PublicPreRegistrationData.Registration update(
            Long conferenceSeq,
            Long memberSeq,
            PublicPreRegistrationData.Request request
    ) {
        if (request == null || request.getCategorySeq() == null || request.getCategorySeq() <= 0) {
            throw new IllegalArgumentException("Please select a registration category.");
        }
        Long seq = registrations.findSubmittedSeq(memberSeq, conferenceSeq);
        if (seq == null || registrations.lockRegistration(conferenceSeq, seq) == null) {
            throw new IllegalStateException("Registration could not be found.");
        }
        PreRegistrationResponse existing = registrations.findBySeq(
                conferenceSeq, seq, personalData.requireDbEncString());
        if (existing == null || !memberSeq.equals(existing.getMemberSeq())) {
            throw new IllegalStateException("Registration could not be found.");
        }
        if (!"UNPAID".equals(existing.getPaymentStatus())) {
            throw new IllegalStateException("Only registrations awaiting payment can be changed.");
        }

        Period period = currentPeriod(requireConference(conferenceSeq));
        if (period.type() == null) {
            throw new IllegalStateException("Online registration is currently closed.");
        }
        // Profile changes must not change the currency of an existing registration.
        String currency = registrationCurrency(existing.getCurrency());
        PreRegistrationCategoryOptionResponse category = registrations
                .findPublicCategoryBySeq(conferenceSeq, request.getCategorySeq());
        if (category == null) {
            throw new IllegalArgumentException("The selected registration category is not available.");
        }
        BigDecimal fee = registrations.findFee(conferenceSeq, category.getCategorySeq(), period.type(), currency);
        if (fee == null) {
            throw new IllegalArgumentException("The registration fee is not configured.");
        }
        validateAmount(fee, "Registration fee");
        List<PreRegistrationOptionData.Item> options = resolveOptions(
                conferenceSeq, currency, request.getOptions(), LocalDateTime.now(clock), seq);
        BigDecimal optionAmount = options.stream().map(PreRegistrationOptionData.Item::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        validateAmount(fee.add(optionAmount), "Total amount");

        if (registrations.update(
                conferenceSeq, seq, category.getCategorySeq(), category.getCategoryCode(), category.getCategoryName(),
                period.type(), currency, fee, optionAmount, "SUBMITTED", existing.getAdminMemo()) != 1) {
            throw new IllegalStateException("Registration could not be updated.");
        }
        registrations.deleteOptions(seq);
        for (PreRegistrationOptionData.Item option : options) {
            if (registrations.insertOption(seq, option) != 1) {
                throw new IllegalStateException("Registration options could not be updated.");
            }
        }

        existing.setCategorySeq(category.getCategorySeq());
        existing.setCategoryCode(category.getCategoryCode());
        existing.setCategoryName(category.getCategoryName());
        existing.setPeriodType(period.type());
        existing.setCurrency(currency);
        existing.setFeeAmount(fee);
        existing.setOptionAmount(optionAmount);
        existing.setTotalAmount(fee.add(optionAmount));
        if (existing.getTotalAmount().signum() == 0) {
            if (registrations.updatePaymentStatus(conferenceSeq, seq, "UNPAID", "PAID") != 1) {
                throw new IllegalStateException("Registration payment status could not be updated.");
            }
            existing.setPaymentStatus("PAID");
        }
        return toRegistration(existing, options);
    }

    @Transactional(readOnly = true)
    public PaymentCheckoutResponse checkout(Long conferenceSeq, Long memberSeq) {
        // A saved unpaid registration must not reopen the payment flow after registration closes.
        if (currentPeriod(requireConference(conferenceSeq)).type() == null) {
            throw new IllegalStateException("Online registration is currently closed.");
        }
        Long seq = registrations.findSubmittedSeq(memberSeq, conferenceSeq);
        if (seq == null) {
            throw new IllegalStateException("Please complete online registration first.");
        }
        PreRegistrationResponse registration = registrations.findBySeq(
                conferenceSeq, seq, personalData.requireDbEncString());
        if (registration == null || !memberSeq.equals(registration.getMemberSeq())) {
            throw new IllegalStateException("Registration could not be found.");
        }
        if ("PAID".equals(registration.getPaymentStatus())) {
            throw new IllegalStateException("This registration is already paid.");
        }
        if (registration.getTotalAmount() == null || registration.getTotalAmount().signum() <= 0) {
            throw new IllegalStateException("The payment amount is invalid.");
        }

        PaymentCheckoutRequest request = new PaymentCheckoutRequest();
        request.setMemberType(registration.getMemberType());
        request.setRoute(paymentRoute(registration.getCurrency()));
        request.setAmount(registration.getTotalAmount());
        request.setProductName(limit(registration.getEventName() + " Online Registration", 100));
        request.setOrderNumber(registration.getRegistrationNumber());
        request.setBuyerName(limit(registration.getFirstName() + " " + registration.getLastName(), 100));
        request.setBuyerEmail(registration.getEmail());
        request.setBuyerPhone(registration.getMobile());
        PaymentCheckoutResponse checkout = payments.prepareCheckout(request);
        if (!registration.getCurrency().equals(checkout.currency())
                || registration.getTotalAmount().compareTo(checkout.amount()) != 0) {
            throw new IllegalStateException("The configured payment currency or amount does not match the registration.");
        }
        return checkout;
    }

    @Transactional
    public void cancel(Long conferenceSeq, Long memberSeq) {
        Long seq = registrations.findSubmittedSeq(memberSeq, conferenceSeq);
        if (seq == null) {
            throw new IllegalStateException("A registration available for cancellation could not be found.");
        }
        registrations.lockRegistration(conferenceSeq, seq);
        PreRegistrationResponse registration = registrations.findBySeq(
                conferenceSeq, seq, personalData.requireDbEncString());
        if (registration == null || !memberSeq.equals(registration.getMemberSeq())) {
            throw new IllegalStateException("Registration could not be found.");
        }
        if (!Set.of("UNPAID", "FAILED").contains(registration.getPaymentStatus())) {
            throw new IllegalStateException("Paid registrations cannot be cancelled here. Please contact the secretariat.");
        }
        if (registrations.cancelByMember(conferenceSeq, seq, memberSeq) != 1) {
            throw new IllegalStateException("The registration status changed and could not be cancelled.");
        }
    }

    private List<PreRegistrationOptionData.Item> resolveOptions(
            Long conferenceSeq,
            String currency,
            List<PreRegistrationOptionData.Selection> requested,
            LocalDateTime now,
            Long excludedRegistrationSeq
    ) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        Set<Long> seen = new HashSet<>();
        List<PreRegistrationOptionData.Item> result = new ArrayList<>();
        for (PreRegistrationOptionData.Selection selection : requested.stream()
                .sorted(Comparator.comparing(PreRegistrationOptionData.Selection::optionSeq,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList()) {
            if (selection == null || selection.optionSeq() == null || selection.optionSeq() <= 0
                    || selection.quantity() == null || selection.quantity() <= 0) {
                throw new IllegalArgumentException("Please select valid registration options and quantities.");
            }
            if (!seen.add(selection.optionSeq())) {
                throw new IllegalArgumentException("The same option cannot be selected more than once.");
            }
            RegistrationOptionData.Option option = registrations.lockOption(conferenceSeq, selection.optionSeq());
            BigDecimal price = validateOption(
                    option, conferenceSeq, currency, selection.quantity(), now, excludedRegistrationSeq);
            BigDecimal amount = price.multiply(BigDecimal.valueOf(selection.quantity()));
            validateAmount(amount, "Option amount");
            result.add(PreRegistrationOptionData.Item.builder()
                    .optionSeq(option.getSeq())
                    .optionName(option.getOptionName())
                    .optionDescription(option.getDescription() == null ? "" : option.getDescription())
                    .currency(currency)
                    .unitPrice(price)
                    .quantity(selection.quantity())
                    .amount(amount)
                    .build());
        }
        return result;
    }

    private BigDecimal validateOption(
            RegistrationOptionData.Option option,
            Long conferenceSeq,
            String currency,
            int quantity,
            LocalDateTime now,
            Long excludedRegistrationSeq
    ) {
        if (option == null || !conferenceSeq.equals(option.getConferenceSeq()) || !Boolean.TRUE.equals(option.getEnabled())) {
            throw new IllegalArgumentException("The selected registration option is not available.");
        }
        if ((option.getSaleStartsAt() != null && now.isBefore(option.getSaleStartsAt()))
                || (option.getSaleEndsAt() != null && now.isAfter(option.getSaleEndsAt()))) {
            throw new IllegalStateException(option.getOptionName() + " is outside its application period.");
        }
        if (option.getMaxPerPerson() == null || quantity > option.getMaxPerPerson()) {
            throw new IllegalArgumentException(option.getOptionName() + " exceeds the maximum quantity per person.");
        }
        int used = excludedRegistrationSeq == null
                ? registrations.usedOptionQuantity(conferenceSeq, option.getSeq())
                : registrations.usedOptionQuantityExcluding(conferenceSeq, option.getSeq(), excludedRegistrationSeq);
        if (option.getCapacity() != null && used + quantity > option.getCapacity()) {
            throw new IllegalStateException(option.getOptionName() + " has insufficient remaining capacity.");
        }
        BigDecimal price = "KRW".equals(currency) ? option.getKrwPrice() : option.getUsdPrice();
        if (price == null) {
            throw new IllegalArgumentException(option.getOptionName() + " is not available in " + currency + ".");
        }
        return price;
    }

    private PublicPreRegistrationData.Registration currentRegistration(Long conferenceSeq, Long memberSeq) {
        Long seq = registrations.findSubmittedSeq(memberSeq, conferenceSeq);
        if (seq == null) {
            return null;
        }
        return toRegistration(
                registrations.findBySeq(conferenceSeq, seq, personalData.requireDbEncString()),
                registrations.findOptionsByRegistrationSeq(conferenceSeq, seq)
        );
    }

    private PublicPreRegistrationData.Registration toRegistration(
            PreRegistrationResponse registration,
            List<PreRegistrationOptionData.Item> options
    ) {
        if (registration == null) {
            return null;
        }
        return new PublicPreRegistrationData.Registration(
                registration.getSeq(), registration.getRegistrationNumber(), registration.getCategorySeq(),
                registration.getCategoryName(), registration.getPeriodType(), registration.getCurrency(),
                registration.getFeeAmount(), defaultAmount(registration.getOptionAmount()),
                registration.getTotalAmount() == null ? registration.getFeeAmount() : registration.getTotalAmount(),
                options == null ? List.of() : options,
                registration.getApplicationStatus(), registration.getPaymentStatus()
        );
    }

    private PublicPreRegistrationData.Category toCategory(
            RegistrationFeeCategoryResponse category,
            String period,
            String currency
    ) {
        BigDecimal amount = switch ((period == null ? "" : period) + ":" + currency) {
            case "EARLY_BIRD:KRW" -> category.getEarlyBirdKrwFee();
            case "EARLY_BIRD:USD" -> category.getEarlyBirdUsdFee();
            case "REGULAR:KRW" -> category.getRegularKrwFee();
            case "REGULAR:USD" -> category.getRegularUsdFee();
            default -> null;
        };
        return new PublicPreRegistrationData.Category(
                category.getSeq(), category.getCategoryCode(), category.getCategoryName(),
                category.getDescription(), amount
        );
    }

    private boolean paymentAvailable(String currency) {
        String route = paymentRoute(currency);
        var summary = payments.summary();
        return summary.enabled() && summary.routes().stream().anyMatch(item -> item.configured()
                && route.equals(item.route()) && currency.equals(item.currency()));
    }

    private Period currentPeriod(ConferenceSettings conference) {
        LocalDate today = LocalDate.now(clock);
        if (within(today, conference.getEarlyBirdStartDate(), conference.getEarlyBirdEndDate())) {
            return new Period("EARLY_BIRD", "Early-bird", conference.getEarlyBirdStartDate(), conference.getEarlyBirdEndDate());
        }
        if (within(today, conference.getRegularStartDate(), conference.getRegularEndDate())) {
            return new Period("REGULAR", "Regular", conference.getRegularStartDate(), conference.getRegularEndDate());
        }
        return new Period(null, "Closed", null, null);
    }

    private boolean within(LocalDate today, LocalDate start, LocalDate end) {
        return (start != null || end != null)
                && (start == null || !today.isBefore(start))
                && (end == null || !today.isAfter(end));
    }

    private ConferenceSettings requireConference(Long conferenceSeq) {
        ConferenceSettings conference = conferences.findBySeq(conferenceSeq);
        if (conference == null) {
            throw new IllegalArgumentException("Conference settings could not be found.");
        }
        return conference;
    }

    private Member requireMember(Long conferenceSeq, Long memberSeq) {
        Member member = members.findBySeq(conferenceSeq, memberSeq, personalData.requireDbEncString());
        if (member == null) {
            throw new IllegalArgumentException("Member information could not be found.");
        }
        return member;
    }

    private String memberCountry(Member member) {
        String country = member.getCountry() == null ? "" : member.getCountry().trim();
        // Legacy domestic signups do not store a country.
        return country.isEmpty() && "domestic".equals(member.getMemberType()) ? "South Korea" : country;
    }

    private String memberCurrency(Member member) {
        return switch (memberCountry(member).toLowerCase(Locale.ROOT)) {
            case "south korea", "republic of korea", "korea, republic of", "kr", "kor", "대한민국" -> "KRW";
            default -> "USD";
        };
    }

    private String registrationCurrency(String currency) {
        if ("KRW".equals(currency) || "USD".equals(currency)) {
            return currency;
        }
        throw new IllegalStateException("The registration currency is not supported.");
    }

    private String paymentRoute(String currency) {
        return "KRW".equals(registrationCurrency(currency)) ? "domestic-card" : "international-card";
    }

    private void validateAmount(BigDecimal amount, String label) {
        if (amount == null || amount.signum() < 0 || amount.compareTo(MAX_AMOUNT) > 0
                || amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(label + " is invalid.");
        }
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String limit(String value, int max) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private record Period(String type, String label, LocalDate start, LocalDate end) {
    }
}
