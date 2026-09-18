package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.dto.PreRegistrationCategoryOptionResponse;
import com.bjworld21.congress.dto.PreRegistrationPageResponse;
import com.bjworld21.congress.dto.PreRegistrationResponse;
import com.bjworld21.congress.dto.PreRegistrationSummary;
import com.bjworld21.congress.dto.PreRegistrationUpdateRequest;
import com.bjworld21.congress.dto.PreRegistrationCreateRequest;
import com.bjworld21.congress.dto.PreRegistrationOptionData;
import com.bjworld21.congress.dto.RegistrationOptionData;
import com.bjworld21.congress.repository.PreRegistrationRepository;
import com.bjworld21.congress.repository.ConferenceSettingsRepository;
import com.bjworld21.congress.entity.ConferenceSettings;
import com.bjworld21.congress.util.XlsxWorkbookWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;

@Service
public class PreRegistrationAdminService {
    private static final Set<String> PERIOD_TYPES = Set.of("EARLY_BIRD", "REGULAR");
    private static final Set<String> APPLICATION_STATUSES = Set.of("SUBMITTED", "CANCELLED");
    private static final Set<String> PAYMENT_STATUSES = Set.of("UNPAID", "PAID", "REFUNDED", "FAILED");
    private static final DateTimeFormatter EXPORT_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PreRegistrationRepository repository;
    private final SocietyMemberService societyMembers;
    private final ConferenceSettingsRepository conferences;
    private final PersonalDataProperties personalDataProperties;

    public PreRegistrationAdminService(PreRegistrationRepository repository, SocietyMemberService societyMembers,
                                       ConferenceSettingsRepository conferences,
                                       PersonalDataProperties personalDataProperties) {
        this.repository = repository;
        this.societyMembers = societyMembers;
        this.conferences = conferences;
        this.personalDataProperties = personalDataProperties;
    }

    public PreRegistrationPageResponse findPage(
            Long conferenceSeq,
            Integer requestedPage,
            Integer requestedSize,
            String keyword,
            Long categorySeq,
            Long optionSeq,
            String periodType,
            String applicationStatus,
            String paymentStatus,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        FilterValues filters = normalizeFilters(
                keyword,
                categorySeq,
                optionSeq,
                periodType,
                applicationStatus,
                paymentStatus,
                dateFrom,
                dateTo
        );
        int size = Math.min(Math.max(requestedSize == null ? 20 : requestedSize, 1), 100);
        int page = Math.max(requestedPage == null ? 1 : requestedPage, 1);

        PreRegistrationSummary summary = repository.findSummary(
                conferenceSeq,
                filters.keyword(),
                filters.categorySeq(),
                filters.optionSeq(),
                filters.periodType(),
                filters.applicationStatus(),
                filters.paymentStatus(),
                filters.dateFrom(),
                filters.dateTo(),
                personalDataProperties.requireDbEncString()
        );
        if (summary == null) {
            summary = PreRegistrationSummary.builder().build();
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) summary.getTotalCount() / size));
        int safePage = Math.min(page, totalPages);
        int offset = (safePage - 1) * size;
        List<PreRegistrationResponse> items = repository.findPage(
                conferenceSeq,
                filters.keyword(),
                filters.categorySeq(),
                filters.optionSeq(),
                filters.periodType(),
                filters.applicationStatus(),
                filters.paymentStatus(),
                filters.dateFrom(),
                filters.dateTo(),
                size,
                offset,
                personalDataProperties.requireDbEncString()
        );

        return PreRegistrationPageResponse.builder()
                .items(items)
                .page(safePage)
                .size(size)
                .totalPages(totalPages)
                .totalCount(summary.getTotalCount())
                .submittedCount(summary.getSubmittedCount())
                .cancelledCount(summary.getCancelledCount())
                .paidCount(summary.getPaidCount())
                .unpaidCount(summary.getUnpaidCount())
                .totalPaidKrwAmount(defaultAmount(summary.getTotalPaidKrwAmount()))
                .totalPaidUsdAmount(defaultAmount(summary.getTotalPaidUsdAmount()))
                .build();
    }

    public PreRegistrationResponse findDetail(Long conferenceSeq, Long seq) {
        if (seq == null || seq <= 0) {
            throw new IllegalArgumentException("올바른 사전등록 ID가 필요합니다.");
        }
        PreRegistrationResponse response = repository.findBySeq(
                conferenceSeq, seq, personalDataProperties.requireDbEncString()
        );
        if (response == null) {
            throw new PreRegistrationNotFoundException("사전등록 내역을 찾을 수 없습니다.");
        }
        List<PreRegistrationOptionData.Item> options = repository.findOptionsByRegistrationSeq(conferenceSeq, seq);
        response.setOptions(options == null ? List.of() : options);
        return response;
    }

    public List<PreRegistrationOptionData.CatalogItem> findAvailableOptions(Long conferenceSeq, Long preRegistrationSeq, String currency) {
        Set<Long> selectedOptionSeqs = Set.of();
        if (preRegistrationSeq != null) {
            PreRegistrationResponse registration = findDetail(conferenceSeq, preRegistrationSeq);
            if (!conferenceSeq.equals(registration.getConferenceSeq())) {
                throw new IllegalArgumentException("사전등록 내역과 학회가 일치하지 않습니다.");
            }
            selectedOptionSeqs = registration.getOptions().stream()
                    .map(PreRegistrationOptionData.Item::getOptionSeq)
                    .collect(java.util.stream.Collectors.toSet());
        }
        Set<Long> retainedOptionSeqs = selectedOptionSeqs;
        return repository.findAvailableOptions(conferenceSeq, normalizeCurrency(currency)).stream()
                .filter(item -> item.getRemainingCapacity() == null || item.getRemainingCapacity() > 0
                        || retainedOptionSeqs.contains(item.getOptionSeq()))
                .toList();
    }

    @Transactional
    public PreRegistrationResponse cancelPayment(Long conferenceSeq, Long seq) {
        repository.lockRegistration(conferenceSeq, seq);
        PreRegistrationResponse registration = findDetail(conferenceSeq, seq);
        if (!"PAID".equals(registration.getPaymentStatus())) {
            throw new IllegalArgumentException("결제 완료 상태의 사전등록만 결제 취소할 수 있습니다.");
        }

        if (repository.cancelPayment(conferenceSeq, seq) != 1) {
            throw new IllegalStateException("결제 상태가 변경되어 결제 취소 처리에 실패했습니다.");
        }
        return findDetail(conferenceSeq, seq);
    }

    @Transactional
    public PreRegistrationResponse update(Long conferenceSeq, Long seq, PreRegistrationUpdateRequest request) {
        repository.lockRegistration(conferenceSeq, seq);
        PreRegistrationResponse existing = findDetail(conferenceSeq, seq);
        PreRegistrationResponse values = validateRegistration(conferenceSeq, request);
        String paymentStatus = request.getPaymentStatus() == null ? existing.getPaymentStatus()
                : normalizeRequiredEnum(request.getPaymentStatus(), PAYMENT_STATUSES, "결제 상태");
        applyMemberFee(conferenceSeq, values, existing.getMemberSeq());
        boolean replaceOptions = request.getOptions() != null || !values.getCurrency().equals(existing.getCurrency());
        if (request.getOptions() == null && replaceOptions) {
            request.setOptions(existing.getOptions().stream()
                    .map(option -> new PreRegistrationOptionData.Selection(option.getOptionSeq(), option.getQuantity())).toList());
        }
        if (!replaceOptions && !existing.getOptions().isEmpty() && !existing.getCurrency().equals(values.getCurrency())) {
            throw new IllegalArgumentException("옵션이 포함된 사전등록은 통화를 변경할 수 없습니다.");
        }
        List<PreRegistrationOptionData.Item> optionItems = existing.getOptions();
        BigDecimal optionAmount = defaultAmount(existing.getOptionAmount());
        if (replaceOptions) {
            ConferenceSettings conference = request.getOptions().isEmpty() ? null : requireConference(existing.getConferenceSeq());
            lockOptionChanges(conferenceSeq, seq, request.getOptions());
            optionItems = resolveOptions(conferenceSeq, conference, request.getOptions(), values.getCurrency(), seq);
            optionAmount = optionItems.stream().map(PreRegistrationOptionData.Item::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        BigDecimal updatedTotal = values.getFeeAmount().add(optionAmount);
        validateStoredAmount(updatedTotal, "총 신청 금액");
        if (("PAID".equals(existing.getPaymentStatus()) || "REFUNDED".equals(existing.getPaymentStatus())
                || existing.getPaidAmount() != null || existing.getPaidAt() != null)
                && (!existing.getCurrency().equals(values.getCurrency())
                || defaultAmount(existing.getTotalAmount()).compareTo(updatedTotal) != 0)) {
            throw new IllegalArgumentException("결제 이력이 있는 사전등록은 신청 금액이나 통화를 변경할 수 없습니다.");
        }
        if ("CANCELLED".equals(existing.getApplicationStatus()) && "SUBMITTED".equals(values.getApplicationStatus())) {
            existing.setCurrency(values.getCurrency());
            validateReactivation(existing, optionItems);
        }
        int updated = repository.update(
                conferenceSeq, seq, values.getCategorySeq(), values.getCategoryCode(), values.getCategoryName(),
                values.getPeriodType(), values.getCurrency(), values.getFeeAmount(), optionAmount,
                values.getApplicationStatus(), values.getAdminMemo()
        );
        if (updated != 1) {
            throw new PreRegistrationNotFoundException("사전등록 내역을 찾을 수 없습니다.");
        }
        if (replaceOptions) {
            repository.deleteOptions(seq);
            for (PreRegistrationOptionData.Item optionItem : optionItems) {
                if (repository.insertOption(seq, optionItem) != 1) {
                    throw new IllegalStateException("사전등록 옵션 저장에 실패했습니다.");
                }
            }
        }
        if (!java.util.Objects.equals(paymentStatus, existing.getPaymentStatus())
                && repository.updatePaymentStatus(conferenceSeq, seq, existing.getPaymentStatus(), paymentStatus) != 1) {
            throw new IllegalStateException("결제 상태가 변경되어 저장하지 못했습니다. 다시 조회해 주세요.");
        }
        return findDetail(conferenceSeq, seq);
    }

    @Transactional
    public PreRegistrationResponse create(Long conferenceSeq, PreRegistrationCreateRequest request) {
        if (request == null || request.getMemberSeq() == null || request.getMemberSeq() <= 0) {
            throw new IllegalArgumentException("신청 회원을 선택해 주세요.");
        }
        if (repository.lockMember(conferenceSeq, request.getMemberSeq()) == null) {
            throw new IllegalArgumentException("선택한 회원을 찾을 수 없습니다.");
        }
        ConferenceSettings conference = requireConference(conferenceSeq);
        request.setConferenceSeq(conference.getSeq());
        if (repository.findSubmittedSeqForUpdate(request.getMemberSeq(), conference.getSeq()) != null) {
            throw new IllegalStateException("선택한 학회에 이미 신청 완료된 사전등록 내역이 있는 회원입니다.");
        }
        request.setCurrency(memberCurrency(conferenceSeq, request.getMemberSeq()));
        request.setApplicationStatus("SUBMITTED");
        if (trimToNull(request.getSocietyLicenseNumber()) != null || trimToNull(request.getSocietyMemberName()) != null) {
            var quote = societyMembers.quote(conferenceSeq, new com.bjworld21.congress.dto.SocietyMemberData.QuoteRequest(
                    request.getSocietyLicenseNumber(), request.getSocietyMemberName(), request.getPeriodType(), request.getCurrency()));
            if (!quote.getCategorySeq().equals(request.getCategorySeq()) || request.getFeeAmount() == null
                    || quote.getAmount().compareTo(request.getFeeAmount()) != 0) {
                throw new IllegalArgumentException("학회회원 등록비가 변경되었거나 일치하지 않습니다. 학회회원 확인을 다시 진행해 주세요.");
            }
            request.setCategorySeq(quote.getCategorySeq());
            request.setFeeAmount(quote.getAmount());
        }
        PreRegistrationResponse registration = validateRegistration(conferenceSeq, request);
        applyMemberFee(conferenceSeq, registration, request.getMemberSeq());
        registration.setMemberSeq(request.getMemberSeq());
        registration.setConferenceSeq(conference.getSeq());
        List<PreRegistrationOptionData.Item> optionItems = resolveOptions(
                conferenceSeq, conference, request.getOptions(), registration.getCurrency(), null);
        BigDecimal optionAmount = optionItems.stream().map(PreRegistrationOptionData.Item::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = registration.getFeeAmount().add(optionAmount);
        validateStoredAmount(totalAmount, "총 신청 금액");
        registration.setOptionAmount(optionAmount);
        registration.setTotalAmount(totalAmount);
        // 생성 키를 얻기 위한 임시번호는 같은 트랜잭션 안에서 최종 등록번호로 교체한다.
        registration.setRegistrationNumber("TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 26));
        if (repository.insert(registration) != 1 || registration.getSeq() == null) {
            throw new IllegalStateException("사전등록 저장에 실패했습니다.");
        }
        String number = "PR-" + LocalDate.now().getYear() + "-" + String.format(Locale.ROOT, "%06d", registration.getSeq());
        if (repository.assignRegistrationNumber(conferenceSeq, registration.getSeq(), number) != 1) {
            throw new IllegalStateException("사전등록번호 발급에 실패했습니다.");
        }
        for (PreRegistrationOptionData.Item optionItem : optionItems) {
            if (repository.insertOption(registration.getSeq(), optionItem) != 1) {
                throw new IllegalStateException("사전등록 옵션 저장에 실패했습니다.");
            }
        }
        return findDetail(conferenceSeq, registration.getSeq());
    }

    private String memberCurrency(Long conferenceSeq, Long memberSeq) {
        String type = repository.findMemberType(conferenceSeq, memberSeq);
        if ("domestic".equals(type)) return "KRW";
        if ("international".equals(type)) return "USD";
        throw new IllegalArgumentException("신청 회원의 국내·해외 구분을 확인해 주세요.");
    }

    private void applyMemberFee(Long conferenceSeq, PreRegistrationResponse values, Long memberSeq) {
        String currency = memberCurrency(conferenceSeq, memberSeq);
        BigDecimal fee = repository.findFee(conferenceSeq, values.getCategorySeq(), values.getPeriodType(), currency);
        if (fee == null) throw new IllegalArgumentException("선택한 등록 구분과 기간의 " + currency + " 등록비가 설정되지 않았습니다.");
        validateStoredAmount(fee, "등록비");
        values.setCurrency(currency);
        values.setFeeAmount(fee);
    }

    private List<PreRegistrationOptionData.Item> resolveOptions(
            Long conferenceSeq, ConferenceSettings conference, List<PreRegistrationOptionData.Selection> requested, String currency,
            Long excludedPreRegistrationSeq) {
        if (requested == null || requested.isEmpty()) return List.of();
        List<PreRegistrationOptionData.Selection> selections = requested.stream()
                .sorted(Comparator.comparing(PreRegistrationOptionData.Selection::optionSeq,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        var seen = new HashSet<Long>();
        var result = new ArrayList<PreRegistrationOptionData.Item>();
        for (PreRegistrationOptionData.Selection selection : selections) {
            if (selection == null || selection.optionSeq() == null || selection.optionSeq() <= 0
                    || selection.quantity() == null || selection.quantity() <= 0) {
                throw new IllegalArgumentException("옵션과 수량을 올바르게 입력해 주세요.");
            }
            if (!seen.add(selection.optionSeq())) throw new IllegalArgumentException("같은 옵션을 중복 선택할 수 없습니다.");
            RegistrationOptionData.Option option = repository.lockOption(conferenceSeq, selection.optionSeq());
            BigDecimal unitPrice = validateOption(
                    option, conferenceSeq, currency, selection.quantity(), excludedPreRegistrationSeq);
            BigDecimal amount = unitPrice.multiply(BigDecimal.valueOf(selection.quantity()));
            validateStoredAmount(amount, "옵션 금액");
            result.add(PreRegistrationOptionData.Item.builder()
                    .optionSeq(option.getSeq()).optionName(option.getOptionName())
                    .optionDescription(option.getDescription() == null ? "" : option.getDescription())
                    .currency(currency).unitPrice(unitPrice)
                    .quantity(selection.quantity()).amount(amount).build());
        }
        return result;
    }

    private BigDecimal validateOption(RegistrationOptionData.Option option, Long conferenceSeq, String currency,
                                      int quantity, Long excludedPreRegistrationSeq) {
        if (option == null || !conferenceSeq.equals(option.getConferenceSeq()))
            throw new IllegalArgumentException("선택한 학회의 옵션이 아닙니다.");
        if (option.getMaxPerPerson() == null || quantity > option.getMaxPerPerson())
            throw new IllegalArgumentException(option.getOptionName() + " 옵션은 1인당 최대 " + option.getMaxPerPerson() + "개까지 신청할 수 있습니다.");
        BigDecimal price = "KRW".equals(currency) ? option.getKrwPrice() : "USD".equals(currency) ? option.getUsdPrice() : null;
        if (price == null) throw new IllegalArgumentException(option.getOptionName() + " 옵션은 선택한 통화를 제공하지 않습니다.");
        int used = excludedPreRegistrationSeq == null
                ? repository.usedOptionQuantity(conferenceSeq, option.getSeq())
                : repository.usedOptionQuantityExcluding(conferenceSeq, option.getSeq(), excludedPreRegistrationSeq);
        if (option.getCapacity() != null && used + quantity > option.getCapacity())
            throw new IllegalStateException(option.getOptionName() + " 옵션의 잔여 정원이 부족합니다.");
        return price;
    }

    private void validateReactivation(PreRegistrationResponse registration,
                                      List<PreRegistrationOptionData.Item> optionItems) {
        for (PreRegistrationOptionData.Item item : optionItems.stream()
                .sorted(Comparator.comparing(PreRegistrationOptionData.Item::getOptionSeq)).toList()) {
            RegistrationOptionData.Option option = repository.lockOption(registration.getConferenceSeq(), item.getOptionSeq());
            validateOption(option, registration.getConferenceSeq(), registration.getCurrency(), item.getQuantity(), registration.getSeq());
        }
    }

    private void lockOptionChanges(Long conferenceSeq, Long preRegistrationSeq,
                                   List<PreRegistrationOptionData.Selection> requested) {
        Set<Long> optionSeqs = new HashSet<>(repository.findOptionSeqs(conferenceSeq, preRegistrationSeq));
        if (requested != null) {
            requested.stream().filter(java.util.Objects::nonNull)
                    .map(PreRegistrationOptionData.Selection::optionSeq)
                    .filter(java.util.Objects::nonNull)
                    .forEach(optionSeqs::add);
        }
        optionSeqs.stream().sorted().forEach(optionSeq -> repository.lockOption(conferenceSeq, optionSeq));
    }

    private ConferenceSettings requireConference(Long conferenceSeq) {
        if (conferenceSeq == null || conferenceSeq <= 0) throw new IllegalArgumentException("학회를 선택해 주세요.");
        ConferenceSettings conference = conferences.findBySeq(conferenceSeq);
        if (conference == null) throw new IllegalArgumentException("선택한 학회를 찾을 수 없습니다.");
        return conference;
    }

    private void validateStoredAmount(BigDecimal amount, String label) {
        if (amount == null || amount.signum() < 0 || amount.compareTo(new BigDecimal("9999999999.99")) > 0
                || amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(label + "의 범위와 소수 자릿수를 확인해 주세요.");
        }
    }

    private PreRegistrationResponse validateRegistration(Long conferenceSeq, PreRegistrationUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("수정할 사전등록 정보가 필요합니다.");
        }

        Long categorySeq = request.getCategorySeq();
        if (categorySeq == null || categorySeq <= 0) {
            throw new IllegalArgumentException("등록 구분을 선택해 주세요.");
        }
        PreRegistrationCategoryOptionResponse category = repository.findCategoryBySeq(conferenceSeq, categorySeq);
        if (category == null) {
            throw new IllegalArgumentException("선택한 등록 구분을 찾을 수 없습니다.");
        }

        String periodType = normalizeRequiredEnum(request.getPeriodType(), PERIOD_TYPES, "등록 기간");
        String applicationStatus = normalizeRequiredEnum(
                request.getApplicationStatus(),
                APPLICATION_STATUSES,
                "신청 상태"
        );
        String currency = normalizeCurrency(request.getCurrency());
        BigDecimal feeAmount = request.getFeeAmount();
        if (feeAmount == null || feeAmount.signum() < 0) {
            throw new IllegalArgumentException("신청 금액은 0 이상이어야 합니다.");
        }
        if (feeAmount.compareTo(new BigDecimal("9999999999.99")) > 0
                || feeAmount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("신청 금액은 9,999,999,999.99 이하, 소수점 둘째 자리까지 입력해 주세요.");
        }
        String adminMemo = trimToNull(request.getAdminMemo());
        if (adminMemo != null && adminMemo.length() > 1000) {
            throw new IllegalArgumentException("관리자 메모는 1,000자 이내로 입력해 주세요.");
        }

        return PreRegistrationResponse.builder()
                .categorySeq(categorySeq).categoryCode(category.getCategoryCode()).categoryName(category.getCategoryName())
                .periodType(periodType).currency(currency).feeAmount(feeAmount)
                .applicationStatus(applicationStatus).adminMemo(adminMemo).build();
    }

    @Transactional
    public void delete(Long conferenceSeq, Long seq) {
        repository.lockRegistration(conferenceSeq, seq);
        PreRegistrationResponse registration = findDetail(conferenceSeq, seq);
        if ("PAID".equals(registration.getPaymentStatus())) {
            throw new IllegalArgumentException("결제 완료 건은 결제 취소 후 삭제할 수 있습니다.");
        }
        repository.findOptionSeqs(conferenceSeq, seq)
                .forEach(optionSeq -> repository.lockOption(conferenceSeq, optionSeq));
        if (repository.delete(conferenceSeq, seq) != 1) {
            throw new IllegalStateException("사전등록 상태가 변경되어 삭제하지 못했습니다.");
        }
    }

    public List<PreRegistrationCategoryOptionResponse> findCategoryOptions(Long conferenceSeq) {
        return repository.findCategoryOptions(conferenceSeq);
    }

    public List<PreRegistrationOptionData.FilterOption> findFilterOptions(Long conferenceSeq) {
        return repository.findFilterOptions(conferenceSeq);
    }

    public ExcelExportResult createExcel(
            Long conferenceSeq,
            String keyword,
            Long categorySeq,
            Long optionSeq,
            String periodType,
            String applicationStatus,
            String paymentStatus,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        FilterValues filters = normalizeFilters(
                keyword,
                categorySeq,
                optionSeq,
                periodType,
                applicationStatus,
                paymentStatus,
                dateFrom,
                dateTo
        );
        List<List<Object>> rows = repository.findAllForExport(
                        conferenceSeq,
                        filters.keyword(),
                        filters.categorySeq(),
                        filters.optionSeq(),
                        filters.periodType(),
                        filters.applicationStatus(),
                        filters.paymentStatus(),
                        filters.dateFrom(),
                        filters.dateTo(),
                        personalDataProperties.requireDbEncString()
                ).stream()
                .map(this::toExcelRow)
                .toList();

        byte[] content = XlsxWorkbookWriter.createWorkbook(
                "사전등록",
                List.of(
                        "등록번호", "신청일시", "이름", "이메일", "회원유형", "소속", "부서", "직위",
                        "국가", "휴대전화", "등록구분", "등록기간", "기본 등록비", "옵션 금액", "총 신청금액", "통화", "신청상태",
                        "결제상태", "결제수단", "결제금액", "결제일시", "거래번호"
                ),
                rows
        );
        return new ExcelExportResult(content, rows.size(), "pre-registrations.xlsx");
    }

    private FilterValues normalizeFilters(
            String keyword,
            Long categorySeq,
            Long optionSeq,
            String periodType,
            String applicationStatus,
            String paymentStatus,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        if (dateFrom != null && dateTo != null && dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("조회 종료일은 시작일보다 빠를 수 없습니다.");
        }
        return new FilterValues(
                trimToNull(keyword),
                categorySeq != null && categorySeq > 0 ? categorySeq : null,
                optionSeq != null && optionSeq > 0 ? optionSeq : null,
                normalizeEnum(periodType, PERIOD_TYPES, "등록 기간"),
                normalizeEnum(applicationStatus, APPLICATION_STATUSES, "신청 상태"),
                normalizeEnum(paymentStatus, PAYMENT_STATUSES, "결제 상태"),
                dateFrom,
                dateTo
        );
    }

    private String normalizeEnum(String value, Set<String> allowedValues, String label) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!allowedValues.contains(normalized)) {
            throw new IllegalArgumentException(label + " 값이 올바르지 않습니다.");
        }
        return normalized;
    }

    private String normalizeRequiredEnum(String value, Set<String> allowedValues, String label) {
        String normalized = normalizeEnum(value, allowedValues, label);
        if (normalized == null) {
            throw new IllegalArgumentException(label + "을(를) 선택해 주세요.");
        }
        return normalized;
    }

    private String normalizeCurrency(String value) {
        String normalized = trimToNull(value);
        if (normalized == null || !normalized.matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("통화 코드는 영문 3자리로 입력해 주세요.");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private List<Object> toExcelRow(PreRegistrationResponse item) {
        return List.of(
                value(item.getRegistrationNumber()),
                formatDateTime(item.getCreatedAt()),
                value(item.getFirstName()) + " " + value(item.getLastName()),
                value(item.getEmail()),
                value(item.getMemberType()),
                value(item.getInstitution()),
                value(item.getDepartment()),
                value(item.getPositionTitle()),
                value(item.getCountry()),
                value(item.getMobile()),
                value(item.getCategoryName()),
                value(item.getPeriodType()),
                defaultAmount(item.getFeeAmount()),
                defaultAmount(item.getOptionAmount()),
                defaultAmount(item.getTotalAmount()),
                value(item.getCurrency()),
                value(item.getApplicationStatus()),
                value(item.getPaymentStatus()),
                value(item.getPaymentMethod()),
                defaultAmount(item.getPaidAmount()),
                formatDateTime(item.getPaidAt()),
                value(item.getPaymentTransactionId())
        );
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.format(EXPORT_DATE_TIME);
    }

    private record FilterValues(
            String keyword,
            Long categorySeq,
            Long optionSeq,
            String periodType,
            String applicationStatus,
            String paymentStatus,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
    }

    public static class PreRegistrationNotFoundException extends RuntimeException {
        public PreRegistrationNotFoundException(String message) {
            super(message);
        }
    }
}
