package com.bjworld21.conference.service;

import com.bjworld21.conference.config.PersonalDataProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 대시보드 최근 7일 차트를 채우기 위한 임시 테스트 데이터 전용 서비스입니다.
 * 기능 제거 시 이 파일과 DailyDashboardTestDataController,
 * DailyDashboardTestDataScheduler 및 TestDataPage의 DAILY_TEST_DATA_ACTIONS를 삭제합니다.
 */
@Service
public class DailyDashboardTestDataService {
    public static final String SCHEDULE_ZONE = "Asia/Seoul";
    public static final LocalDate START_DATE = LocalDate.of(2026, 8, 26);

    private static final int MEMBER_MIN = 10;
    private static final int MEMBER_MAX = 15;
    private static final int PRE_REGISTRATION_MIN = 8;
    private static final int PRE_REGISTRATION_MAX = 13;
    private static final int ABSTRACT_MIN = 6;
    private static final int ABSTRACT_MAX = 9;
    private static final DateTimeFormatter DATE_KEY_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private static final String[] FIRST_NAMES = {
            "Minjun", "Seojun", "Jiwoo", "Seoyeon", "Hyunwoo", "Jiwon",
            "Emma", "Olivia", "Liam", "Noah", "Ava", "Sophia"
    };
    private static final String[] LAST_NAMES = {
            "Kim", "Lee", "Park", "Choi", "Jung", "Kang",
            "Smith", "Johnson", "Brown", "Taylor", "Wilson", "Martin"
    };
    private static final String[] INSTITUTIONS = {
            "Seoul National University", "Yonsei University", "Korea University",
            "KAIST", "University of Tokyo", "National University of Singapore"
    };
    private static final String[] ABSTRACT_TOPICS = {
            "Clinical Outcomes", "Digital Health", "Precision Medicine",
            "Public Health", "Medical Education", "Patient Safety"
    };

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final PersonalDataProperties personalDataProperties;

    public DailyDashboardTestDataService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            PersonalDataProperties personalDataProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.personalDataProperties = personalDataProperties;
    }

    @Transactional
    public DailyCreationResult createMembersFromStartDate(Long conferenceSeq) {
        requireConference(conferenceSeq);
        var range = conferenceRange(conferenceSeq);
        return createMembers(conferenceSeq, range.startDate(), range.endDate());
    }

    @Transactional
    public DailyCreationResult createPreRegistrationsFromStartDate(Long conferenceSeq) {
        requireConference(conferenceSeq);
        var range = conferenceRange(conferenceSeq);
        return createPreRegistrations(conferenceSeq, range.startDate(), range.endDate());
    }

    @Transactional
    public DailyCreationResult createAbstractsFromStartDate(Long conferenceSeq) {
        requireConference(conferenceSeq);
        var range = conferenceRange(conferenceSeq);
        return createAbstracts(conferenceSeq, range.startDate(), range.endDate(), requireAssigningAdmin());
    }

    @Transactional
    public DailyCreationResult createAbstractsFromStartDate(Long conferenceSeq, long createdByAdminSeq) {
        requireConference(conferenceSeq);
        var range = conferenceRange(conferenceSeq);
        return createAbstracts(conferenceSeq, range.startDate(), range.endDate(), createdByAdminSeq);
    }

    /** 행사 시작 60일 전부터 전날까지, 오늘 이전의 누락분만 자동 보충합니다. */
    @Transactional
    public DailySetCreationResult createScheduledSet(Long conferenceSeq) {
        requireConference(conferenceSeq);
        var range = conferenceRange(conferenceSeq);
        LocalDate endDate = today().isBefore(range.endDate()) ? today() : range.endDate();
        if (endDate.isBefore(range.startDate())) return new DailySetCreationResult(
                result("members", range.startDate(), range.endDate(), 0, 0),
                result("pre-registrations", range.startDate(), range.endDate(), 0, 0),
                result("abstracts", range.startDate(), range.endDate(), 0, 0));
        DailyCreationResult members = createMembers(conferenceSeq, range.startDate(), endDate);
        DailyCreationResult preRegistrations = createPreRegistrations(conferenceSeq, range.startDate(), endDate);
        DailyCreationResult abstracts = createAbstracts(conferenceSeq, range.startDate(), endDate, requireAssigningAdmin());
        return new DailySetCreationResult(members, preRegistrations, abstracts);
    }

    private DailyCreationResult createMembers(Long conferenceSeq, LocalDate startDate, LocalDate endDate) {
        validateRange(startDate, endDate);
        String password = passwordEncoder.encode("Testdata12#$");
        String dbEncString = personalDataProperties.requireDbEncString();
        int createdCount = 0;
        int desiredCount = 0;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            int count = desiredCount(date, "members", MEMBER_MIN, MEMBER_MAX);
            desiredCount += count;
            String dateKey = date.format(DATE_KEY_FORMAT);
            for (int index = 1; index <= count; index++) {
                boolean international = index % 3 == 0;
                LocalDateTime createdAt = sampleTime(date, index);
                String email = dailyMemberEmail(conferenceSeq, dateKey, index);
                migrateLegacyDailyMemberEmail(conferenceSeq, dateKey, index, email, dbEncString);
                createdCount += jdbcTemplate.update("""
                        INSERT IGNORE INTO members (
                            conferenceSeq, memberType, email, password, firstName, lastName, institution,
                            department, positionTitle, country, mobile, newsletter,
                            createdAt, updatedAt
                        ) VALUES (
                            ?, ?, HEX(AES_ENCRYPT(?, SHA2(?, 512))), ?,
                            HEX(AES_ENCRYPT(?, SHA2(?, 512))),
                            HEX(AES_ENCRYPT(?, SHA2(?, 512))),
                            ?, ?, ?, ?, HEX(AES_ENCRYPT(?, SHA2(?, 512))), ?, ?, ?
                        )
                        """,
                        conferenceSeq,
                        international ? "international" : "domestic",
                        email,
                        dbEncString,
                        password,
                        FIRST_NAMES[(index - 1) % FIRST_NAMES.length],
                        dbEncString,
                        LAST_NAMES[(index + date.getDayOfMonth()) % LAST_NAMES.length],
                        dbEncString,
                        INSTITUTIONS[(index - 1) % INSTITUTIONS.length],
                        index % 4 == 0 ? "Research Center" : "Department of Medicine",
                        index % 5 == 0 ? "Professor" : "Researcher",
                        international ? "United States" : null,
                        international
                                ? "+1-202-%03d-%04d".formatted(date.getDayOfMonth(), 1000 + index)
                                : "010-%04d-%04d".formatted(date.getMonthValue() * 100 + date.getDayOfMonth(), 6000 + index),
                        dbEncString,
                        index % 2 == 0,
                        Timestamp.valueOf(createdAt),
                        Timestamp.valueOf(createdAt)
                );
            }
        }

        return result("members", startDate, endDate, createdCount, desiredCount);
    }

    private DailyCreationResult createPreRegistrations(Long conferenceSeq, LocalDate startDate, LocalDate endDate) {
        validateRange(startDate, endDate);
        List<RegistrationRate> rates = findRegistrationRates(conferenceSeq);
        Map<String, List<RegistrationOptionSeed>> optionsByCurrency = findRegistrationOptions(conferenceSeq);
        Map<Long, Integer> remainingByOption = new HashMap<>();
        optionsByCurrency.values().stream().flatMap(List::stream).forEach(option ->
                remainingByOption.put(option.seq(), option.remainingCapacity()));
        int createdCount = 0;
        int desiredCount = 0;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            List<MemberSeed> members = requireDailyMembers(conferenceSeq, date);
            int count = desiredCount(date, "pre-registrations", PRE_REGISTRATION_MIN, PRE_REGISTRATION_MAX);
            desiredCount += count;
            String dateKey = date.format(DATE_KEY_FORMAT);
            for (int index = 1; index <= count; index++) {
                MemberSeed member = members.get((index - 1) % members.size());
                String currency = currencyForMemberType(member.memberType());
                List<RegistrationRate> memberRates = rates.stream()
                        .filter(rate -> currency.equals(rate.currency()))
                        .toList();
                if (memberRates.isEmpty()) {
                    throw new IllegalStateException(currency + " 통화로 사용할 수 있는 사전등록 등록비가 없습니다.");
                }
                RegistrationRate rate = memberRates.get((index - 1) % memberRates.size());
                LocalDateTime createdAt = sampleTime(date, index);
                boolean cancelled = index % 11 == 0;
                String applicationStatus = cancelled ? "CANCELLED" : "SUBMITTED";
                String paymentStatus = cancelled
                        ? "REFUNDED"
                        : index % 7 == 0 ? "FAILED" : index % 4 == 0 ? "UNPAID" : "PAID";
                boolean paid = "PAID".equals(paymentStatus) || "REFUNDED".equals(paymentStatus);
                String registrationNumber = dailyRegistrationNumber(conferenceSeq, dateKey, index);
                migrateLegacyDailyRegistrationNumber(conferenceSeq, dateKey, index, registrationNumber);

                createdCount += jdbcTemplate.update("""
                        INSERT IGNORE INTO pre_registrations (
                            registrationNumber, memberSeq, conferenceSeq, categorySeq, categoryCode, categoryName,
                            periodType, currency, feeAmount, optionAmount, totalAmount, applicationStatus, paymentStatus,
                            paymentMethod, paymentTransactionId, paidAmount, paidAt,
                            privacyAgreed, privacyAgreedAt, termsAgreed, termsAgreedAt,
                            cancelledAt, adminMemo, createdAt, updatedAt
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        registrationNumber,
                        member.seq(),
                        conferenceSeq,
                        rate.categorySeq(),
                        rate.categoryCode(),
                        rate.categoryName(),
                        rate.periodType(),
                        currency,
                        rate.amount(),
                        BigDecimal.ZERO,
                        rate.amount(),
                        applicationStatus,
                        paymentStatus,
                        paid ? "CARD" : null,
                        paid ? "TDDP-%d-%s-%03d".formatted(conferenceSeq, dateKey, index) : null,
                        paid ? rate.amount() : null,
                        paid ? Timestamp.valueOf(createdAt) : null,
                        true,
                        Timestamp.valueOf(createdAt),
                        true,
                        Timestamp.valueOf(createdAt),
                        cancelled ? Timestamp.valueOf(createdAt) : null,
                        "[DAILY_TESTDATA:%s:%03d] 대시보드 추이 샘플".formatted(dateKey, index),
                        Timestamp.valueOf(createdAt),
                        Timestamp.valueOf(createdAt)
                );

                Long registrationSeq = jdbcTemplate.queryForObject("""
                        SELECT seq FROM pre_registrations
                        WHERE conferenceSeq = ? AND registrationNumber = ?
                        """, Long.class, conferenceSeq, registrationNumber);
                if (registrationSeq == null) {
                    throw new IllegalStateException("생성한 일별 사전등록을 다시 조회하지 못했습니다.");
                }
                jdbcTemplate.update("""
                        UPDATE pre_registrations
                        SET memberSeq = ?, categorySeq = ?, categoryCode = ?, categoryName = ?,
                            periodType = ?, currency = ?, feeAmount = ?, applicationStatus = ?, paymentStatus = ?,
                            paymentMethod = ?, paymentTransactionId = ?, paidAmount = ?, paidAt = ?,
                            privacyAgreed = TRUE, privacyAgreedAt = ?, termsAgreed = TRUE, termsAgreedAt = ?,
                            cancelledAt = ?, adminMemo = ?, createdAt = ?, updatedAt = ?
                        WHERE seq = ? AND conferenceSeq = ?
                        """,
                        member.seq(), rate.categorySeq(), rate.categoryCode(), rate.categoryName(),
                        rate.periodType(), currency, rate.amount(), applicationStatus, paymentStatus,
                        paid ? "CARD" : null,
                        paid ? "TDDP-%d-%s-%03d".formatted(conferenceSeq, dateKey, index) : null,
                        paid ? rate.amount() : null,
                        paid ? Timestamp.valueOf(createdAt) : null,
                        Timestamp.valueOf(createdAt), Timestamp.valueOf(createdAt),
                        cancelled ? Timestamp.valueOf(createdAt) : null,
                        "[DAILY_TESTDATA:%s:%03d] 대시보드 추이 샘플".formatted(dateKey, index),
                        Timestamp.valueOf(createdAt), Timestamp.valueOf(createdAt),
                        registrationSeq, conferenceSeq);
                Integer optionCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM pre_registration_option_items WHERE preRegistrationSeq = ?",
                        Integer.class,
                        registrationSeq
                );
                if (optionCount == null || optionCount == 0) {
                    List<RegistrationOptionSelection> selections = selectRegistrationOptions(
                            optionsByCurrency.getOrDefault(currency, List.of()), remainingByOption,
                            index + date.getDayOfYear(), !cancelled
                    );
                    for (RegistrationOptionSelection selection : selections) {
                        jdbcTemplate.update("""
                                INSERT INTO pre_registration_option_items
                                    (preRegistrationSeq, optionSeq, quantity, createdAt)
                                VALUES (?, ?, ?, ?)
                                """, registrationSeq, selection.optionSeq(), selection.quantity(), Timestamp.valueOf(createdAt));
                    }
                }
                jdbcTemplate.update("""
                        UPDATE pre_registrations registration
                        SET optionAmount = COALESCE((
                                SELECT SUM(item.quantity * CASE registration.currency
                                    WHEN 'KRW' THEN optionRow.krwPrice
                                    WHEN 'USD' THEN optionRow.usdPrice
                                END)
                                FROM pre_registration_option_items item
                                JOIN registration_options optionRow ON optionRow.seq = item.optionSeq
                                WHERE item.preRegistrationSeq = registration.seq
                            ), 0),
                            totalAmount = feeAmount + COALESCE((
                                SELECT SUM(item.quantity * CASE registration.currency
                                    WHEN 'KRW' THEN optionRow.krwPrice
                                    WHEN 'USD' THEN optionRow.usdPrice
                                END)
                                FROM pre_registration_option_items item
                                JOIN registration_options optionRow ON optionRow.seq = item.optionSeq
                                WHERE item.preRegistrationSeq = registration.seq
                            ), 0),
                            paidAmount = CASE WHEN paymentStatus IN ('PAID', 'REFUNDED')
                                THEN feeAmount + COALESCE((
                                    SELECT SUM(item.quantity * CASE registration.currency
                                        WHEN 'KRW' THEN optionRow.krwPrice
                                        WHEN 'USD' THEN optionRow.usdPrice
                                    END)
                                    FROM pre_registration_option_items item
                                    JOIN registration_options optionRow ON optionRow.seq = item.optionSeq
                                    WHERE item.preRegistrationSeq = registration.seq
                                ), 0)
                                ELSE paidAmount END,
                            updatedAt = ?
                        WHERE registration.seq = ? AND registration.conferenceSeq = ?
                        """, Timestamp.valueOf(createdAt), registrationSeq, conferenceSeq);
            }
        }

        return result("pre-registrations", startDate, endDate, createdCount, desiredCount);
    }

    private DailyCreationResult createAbstracts(
            Long conferenceSeq,
            LocalDate startDate,
            LocalDate endDate,
            long createdByAdminSeq
    ) {
        validateRange(startDate, endDate);
        List<Long> presentationTypes = requireCommonCodes("ABSTRACT_PRESENTATION_TYPES", "초록 발표형식");
        List<Long> categories = requireCommonCodes("ABSTRACT_CATEGORY", "초록 분류");
        List<Long> reviewers = requireReviewers(conferenceSeq);
        boolean withReviews = reviewers.size() >= AbstractDecisionService.REQUIRED_REVIEWER_COUNT;
        List<Long> evaluationItems = withReviews ? requireEvaluationItems(conferenceSeq) : List.of();
        long assignedByAdminSeq = createdByAdminSeq;
        Map<String, DailyAbstractSeed> abstractByTitle = new HashMap<>();
        jdbcTemplate.query("""
                SELECT seq, title, status, presentationTypeCode, createdAt
                FROM abstract_submissions
                WHERE conferenceSeq = ?
                  AND title LIKE '[DAILY_TESTDATA:%'
                """, rs -> {
            DailyAbstractSeed abstractSeed = new DailyAbstractSeed(
                    rs.getLong("seq"),
                    rs.getString("title"),
                    rs.getString("status"),
                    rs.getLong("presentationTypeCode"),
                    rs.getTimestamp("createdAt").toLocalDateTime()
            );
            abstractByTitle.put(abstractSeed.title(), abstractSeed);
        }, conferenceSeq);
        int createdCount = 0;
        int desiredCount = 0;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            List<MemberSeed> members = requireDailyMembers(conferenceSeq, date);
            int count = desiredCount(date, "abstracts", ABSTRACT_MIN, ABSTRACT_MAX);
            desiredCount += count;
            String dateKey = date.format(DATE_KEY_FORMAT);
            for (int index = 1; index <= count; index++) {
                int sampleIndex = dailyAbstractSampleIndex(startDate, date, index);
                TestDataService.AbstractSamplePlan plan = TestDataService.abstractSamplePlan(
                        sampleIndex,
                        presentationTypes
                );
                if (!withReviews) plan = unreviewedPlan(sampleIndex, plan.presentationTypeCode());
                String title = "[DAILY_TESTDATA:%s:%03d] %s Study"
                        .formatted(dateKey, index, ABSTRACT_TOPICS[(index - 1) % ABSTRACT_TOPICS.length]);
                DailyAbstractSeed abstractSeed = abstractByTitle.get(title);
                if (abstractSeed == null) {
                    MemberSeed member = members.get((index - 1) % members.size());
                    abstractSeed = createDailyAbstract(
                            conferenceSeq,
                            dateKey,
                            index,
                            title,
                            member,
                            categories,
                            assignedByAdminSeq,
                            plan
                    );
                    abstractByTitle.put(title, abstractSeed);
                    createdCount++;
                }

                DailyAbstractSeed workflowSeed = new DailyAbstractSeed(
                        abstractSeed.seq(),
                        abstractSeed.title(),
                        plan.status(),
                        plan.presentationTypeCode(),
                        abstractSeed.createdAt()
                );
                LocalDateTime reviewedAt = ensureReviewWorkflow(
                        conferenceSeq,
                        workflowSeed,
                        sampleIndex,
                        reviewers,
                        evaluationItems,
                        assignedByAdminSeq,
                        plan
                );
                updateWorkflowMetadata(
                        conferenceSeq,
                        workflowSeed,
                        assignedByAdminSeq,
                        reviewedAt,
                        plan
                );
            }
        }

        return result("abstracts", startDate, endDate, createdCount, desiredCount);
    }

    private DailyAbstractSeed createDailyAbstract(
            Long conferenceSeq,
            String dateKey,
            int index,
            String title,
            MemberSeed member,
            List<Long> categories,
            long createdByAdminSeq,
            TestDataService.AbstractSamplePlan plan
    ) {
        String temporarySubmissionNo = "TDDA%s%s%03d".formatted(conferenceKey(conferenceSeq), dateKey, index);
        LocalDateTime createdAt = sampleTime(LocalDate.parse(dateKey, DATE_KEY_FORMAT), index);
        jdbcTemplate.update("""
                INSERT INTO abstract_submissions (
                    conferenceSeq, memberSeq, submissionSource, createdByAdminSeq,
                    submissionNo, presentationTypeCode, acceptedPresentationTypeCode, categoryCode, title,
                    objectiveText, methodsText, resultsText, conclusionsText,
                    aiUsage, aiVersionInfo, aiDataAnalysisUsed, plagiarismPolicyConfirmed,
                    wordCount, status, submittedAt, reviewedAt, createdAt, updatedAt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                conferenceSeq,
                member.seq(),
                "admin",
                createdByAdminSeq,
                temporarySubmissionNo,
                plan.presentationTypeCode(),
                null,
                categories.get((index - 1) % categories.size()),
                title,
                "This daily sample evaluates clinically meaningful outcomes.",
                "A standardized method was used for dashboard verification.",
                "The generated cohort showed representative response patterns.",
                "The findings provide sample data for recent activity trends.",
                false,
                null,
                false,
                true,
                180 + index,
                plan.status(),
                "draft".equals(plan.status()) ? null : Timestamp.valueOf(createdAt),
                null,
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(createdAt)
        );

        Long abstractSeq = jdbcTemplate.queryForObject(
                "SELECT seq FROM abstract_submissions WHERE conferenceSeq = ? AND submissionNo = ?",
                Long.class,
                conferenceSeq,
                temporarySubmissionNo
        );
        if (abstractSeq == null) {
            throw new IllegalStateException("생성한 일별 초록을 다시 조회하지 못했습니다.");
        }
        jdbcTemplate.update("""
                UPDATE abstract_submissions
                SET submissionNo = ?, updatedAt = ?
                WHERE seq = ? AND conferenceSeq = ?
                """, AbstractSubmissionService.buildSubmissionNo(abstractSeq), Timestamp.valueOf(createdAt),
                abstractSeq, conferenceSeq);
        jdbcTemplate.update("""
                INSERT INTO abstract_submission_institutions (
                    abstractSeq, institutionNo, country, institutionName, department, createdAt, updatedAt
                ) VALUES (?, 1, ?, ?, ?, ?, ?)
                """, abstractSeq, member.country(), member.institution(), member.department(),
                Timestamp.valueOf(createdAt), Timestamp.valueOf(createdAt));
        jdbcTemplate.update("""
                INSERT INTO abstract_submission_authors (
                    abstractSeq, authorOrder, authorName, institutionNo,
                    isPresentingAuthor, isCorrespondingAuthor, email, country,
                    mobilePhoneNumber, createdAt, updatedAt
                ) VALUES (?, 1, HEX(AES_ENCRYPT(?, SHA2(?, 512))), 1, TRUE, TRUE,
                          HEX(AES_ENCRYPT(?, SHA2(?, 512))), ?, HEX(AES_ENCRYPT(?, SHA2(?, 512))), ?, ?)
                """, abstractSeq, member.fullName(), personalDataProperties.requireDbEncString(),
                member.email(), personalDataProperties.requireDbEncString(), member.country(),
                member.mobile(), personalDataProperties.requireDbEncString(),
                Timestamp.valueOf(createdAt), Timestamp.valueOf(createdAt));
        return new DailyAbstractSeed(
                abstractSeq,
                title,
                plan.status(),
                plan.presentationTypeCode(),
                createdAt
        );
    }

    private LocalDateTime ensureReviewWorkflow(
            Long conferenceSeq,
            DailyAbstractSeed abstractSeed,
            int abstractIndex,
            List<Long> reviewers,
            List<Long> evaluationItems,
            long assignedByAdminSeq,
            TestDataService.AbstractSamplePlan plan
    ) {
        if ("draft".equals(plan.status()) || "submitted".equals(plan.status())) {
            deleteReviewWorkflow(conferenceSeq, abstractSeed.seq());
            return null;
        }

        int reviewerCount = TestDataService.reviewerCount(
                plan,
                reviewers.size(),
                new Random(abstractSeed.seq())
        );
        int reviewerOffset = Math.floorMod(Long.hashCode(abstractSeed.seq()), reviewers.size());
        List<Long> desiredAssignmentSeqs = new ArrayList<>(reviewerCount);
        for (int reviewerIndex = 0; reviewerIndex < reviewerCount; reviewerIndex++) {
            long reviewerSeq = reviewers.get((reviewerOffset + reviewerIndex) % reviewers.size());
            String assignmentStatus = assignmentStatus(plan, abstractIndex, reviewerIndex);
            long assignmentSeq = ensureAssignment(
                    conferenceSeq,
                    abstractSeed,
                    reviewerSeq,
                    assignedByAdminSeq,
                    assignmentStatus,
                    reviewerIndex
            );
            desiredAssignmentSeqs.add(assignmentSeq);
            if (hasReached(assignmentStatus, "in_review")) {
                ensureReview(
                        conferenceSeq,
                        assignmentSeq,
                        abstractSeed,
                        abstractIndex,
                        reviewerIndex,
                        evaluationItems,
                        "completed".equals(assignmentStatus)
                );
            } else {
                deleteReview(conferenceSeq, assignmentSeq);
            }
        }
        cancelOtherAssignments(conferenceSeq, abstractSeed, desiredAssignmentSeqs);

        if (isFinalAbstractStatus(plan.status())) {
            LocalDateTime reviewedAt = reviewTime(abstractSeed.createdAt(), desiredAssignmentSeqs.size());
            jdbcTemplate.update("""
                    UPDATE abstract_submissions
                    SET reviewedAt = ?, updatedAt = ?
                    WHERE seq = ? AND conferenceSeq = ?
                    """, Timestamp.valueOf(reviewedAt), Timestamp.valueOf(reviewedAt),
                    abstractSeed.seq(), conferenceSeq);
            return reviewedAt;
        }
        return null;
    }

    private void updateWorkflowMetadata(
            Long conferenceSeq,
            DailyAbstractSeed abstractSeed,
            long adminSeq,
            LocalDateTime reviewedAt,
            TestDataService.AbstractSamplePlan plan
    ) {
        boolean finalStatus = isFinalAbstractStatus(plan.status());
        jdbcTemplate.update("""
                UPDATE abstract_submissions
                SET submissionSource = 'admin',
                    createdByAdminSeq = ?,
                    presentationTypeCode = ?,
                    acceptedPresentationTypeCode = ?,
                    status = ?,
                    submittedAt = ?,
                    decisionByAdminSeq = ?,
                    decisionAt = ?,
                    decisionReason = ?,
                    forcedDecision = ?,
                    reviewedAt = ?,
                    updatedAt = ?
                WHERE seq = ? AND conferenceSeq = ?
                """, adminSeq, plan.presentationTypeCode(), plan.acceptedPresentationTypeCode(), plan.status(),
                "draft".equals(plan.status()) ? null : Timestamp.valueOf(abstractSeed.createdAt()),
                finalStatus ? adminSeq : null, timestamp(finalStatus ? reviewedAt : null), plan.decisionReason(),
                finalStatus && plan.forcedDecision(),
                timestamp(finalStatus ? reviewedAt : null),
                Timestamp.valueOf(finalStatus && reviewedAt != null ? reviewedAt : abstractSeed.createdAt()),
                abstractSeed.seq(), conferenceSeq);
    }

    private long ensureAssignment(
            Long conferenceSeq,
            DailyAbstractSeed abstractSeed,
            long reviewerSeq,
            long assignedByAdminSeq,
            String assignmentStatus,
            int reviewerIndex
    ) {
        List<Long> assignmentSeqs = jdbcTemplate.queryForList("""
                SELECT seq
                FROM abstract_review_assignments
                WHERE conferenceSeq = ? AND abstractSeq = ? AND reviewerSeq = ?
                """, Long.class, conferenceSeq, abstractSeed.seq(), reviewerSeq);
        LocalDateTime assignedAt = assignmentTime(abstractSeed.createdAt(), reviewerIndex);
        LocalDateTime acceptedAt = hasReached(assignmentStatus, "accepted")
                ? assignedAt.plusSeconds(1)
                : null;
        LocalDateTime startedAt = hasReached(assignmentStatus, "in_review")
                ? assignedAt.plusSeconds(2)
                : null;
        LocalDateTime completedAt = "completed".equals(assignmentStatus)
                ? assignedAt.plusSeconds(3)
                : null;
        LocalDateTime updatedAt = completedAt != null
                ? completedAt
                : startedAt != null ? startedAt : acceptedAt != null ? acceptedAt : assignedAt;

        if (assignmentSeqs.isEmpty()) {
            jdbcTemplate.update("""
                    INSERT INTO abstract_review_assignments (
                        conferenceSeq, abstractSeq, reviewerSeq, assignedByAdminSeq, status, dueAt, assignedAt,
                        acceptedAt, startedAt, completedAt, declinedAt, declineReason, cancelledAt,
                        createdAt, updatedAt
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, ?, ?)
                    """, conferenceSeq, abstractSeed.seq(), reviewerSeq, assignedByAdminSeq, assignmentStatus,
                    Timestamp.valueOf(assignedAt.plusDays(14)), Timestamp.valueOf(assignedAt),
                    timestamp(acceptedAt), timestamp(startedAt), timestamp(completedAt),
                    Timestamp.valueOf(assignedAt), Timestamp.valueOf(updatedAt));
            assignmentSeqs = jdbcTemplate.queryForList("""
                    SELECT seq
                    FROM abstract_review_assignments
                    WHERE conferenceSeq = ? AND abstractSeq = ? AND reviewerSeq = ?
                    """, Long.class, conferenceSeq, abstractSeed.seq(), reviewerSeq);
        } else {
            jdbcTemplate.update("""
                    UPDATE abstract_review_assignments
                    SET assignedByAdminSeq = ?, status = ?, dueAt = ?, assignedAt = ?,
                        acceptedAt = ?, startedAt = ?, completedAt = ?, declinedAt = NULL,
                        declineReason = NULL, cancelledAt = NULL, updatedAt = ?
                    WHERE seq = ? AND conferenceSeq = ?
                    """, assignedByAdminSeq, assignmentStatus, Timestamp.valueOf(assignedAt.plusDays(14)),
                    Timestamp.valueOf(assignedAt), timestamp(acceptedAt), timestamp(startedAt),
                    timestamp(completedAt), Timestamp.valueOf(updatedAt),
                    assignmentSeqs.get(0), conferenceSeq);
        }
        if (assignmentSeqs.isEmpty()) {
            throw new IllegalStateException("생성한 일별 초록 심사 배정을 다시 조회하지 못했습니다.");
        }
        return assignmentSeqs.get(0);
    }

    private void ensureReview(
            Long conferenceSeq,
            long assignmentSeq,
            DailyAbstractSeed abstractSeed,
            int abstractIndex,
            int reviewerIndex,
            List<Long> evaluationItems,
            boolean completed
    ) {
        LocalDateTime reviewCreatedAt = assignmentTime(abstractSeed.createdAt(), reviewerIndex).plusSeconds(2);
        LocalDateTime submittedAt = completed ? reviewCreatedAt.plusSeconds(1) : null;
        List<Long> reviewSeqs = jdbcTemplate.queryForList("""
                SELECT seq
                FROM abstract_reviews
                WHERE assignmentSeq = ?
                  AND EXISTS (SELECT 1 FROM abstract_review_assignments assignment
                              WHERE assignment.seq = abstract_reviews.assignmentSeq
                                AND assignment.conferenceSeq = ?)
                """, Long.class, assignmentSeq, conferenceSeq);

        if (reviewSeqs.isEmpty()) {
            jdbcTemplate.update("""
                    INSERT INTO abstract_reviews (
                        assignmentSeq, status, recommendation, overallComment,
                        confidentialComment, submittedAt, createdAt, updatedAt
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, assignmentSeq, completed ? "submitted" : "draft",
                    completed ? recommendation(abstractSeed.status(), abstractIndex + reviewerIndex) : null,
                    completed ? "연구 목적과 결과가 명확하며 일별 테스트 심사를 완료했습니다." : null,
                    completed ? "[DAILY_TESTDATA] 관리자 확인용 심사 의견" : "[DAILY_TESTDATA] 작성 중인 심사",
                    timestamp(submittedAt), Timestamp.valueOf(reviewCreatedAt),
                    Timestamp.valueOf(submittedAt == null ? reviewCreatedAt : submittedAt));
            reviewSeqs = jdbcTemplate.queryForList("""
                    SELECT seq
                    FROM abstract_reviews
                    WHERE assignmentSeq = ?
                      AND EXISTS (SELECT 1 FROM abstract_review_assignments assignment
                                  WHERE assignment.seq = abstract_reviews.assignmentSeq
                                    AND assignment.conferenceSeq = ?)
                    """, Long.class, assignmentSeq, conferenceSeq);
        } else {
            jdbcTemplate.update("""
                    UPDATE abstract_reviews
                    SET status = ?, recommendation = ?, overallComment = ?,
                        confidentialComment = ?, submittedAt = ?, updatedAt = ?
                    WHERE seq = ?
                      AND EXISTS (SELECT 1 FROM abstract_review_assignments assignment
                                  WHERE assignment.seq = abstract_reviews.assignmentSeq
                                    AND assignment.conferenceSeq = ?)
                    """, completed ? "submitted" : "draft",
                    completed ? recommendation(abstractSeed.status(), abstractIndex + reviewerIndex) : null,
                    completed ? "연구 목적과 결과가 명확하며 일별 테스트 심사를 완료했습니다." : null,
                    completed ? "[DAILY_TESTDATA] 관리자 확인용 심사 의견" : "[DAILY_TESTDATA] 작성 중인 심사",
                    timestamp(submittedAt), Timestamp.valueOf(submittedAt == null ? reviewCreatedAt : submittedAt),
                    reviewSeqs.get(0), conferenceSeq);
        }
        if (reviewSeqs.isEmpty()) {
            throw new IllegalStateException("생성한 일별 초록 심사 결과를 다시 조회하지 못했습니다.");
        }

        long reviewSeq = reviewSeqs.get(0);
        int itemCount = completed ? evaluationItems.size() : Math.min(2, evaluationItems.size());
        List<Long> expectedEvaluationItems = evaluationItems.subList(0, itemCount);
        removeUnexpectedScores(reviewSeq, expectedEvaluationItems);
        for (int itemIndex = 0; itemIndex < itemCount; itemIndex++) {
            int reviewScore = score(abstractSeed.status(), abstractIndex + reviewerIndex, itemIndex);
            jdbcTemplate.update("""
                    INSERT INTO abstract_review_scores (
                        reviewSeq, evaluationItemSeq, score, itemComment, createdAt, updatedAt
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        score = VALUES(score),
                        itemComment = VALUES(itemComment),
                        updatedAt = VALUES(updatedAt)
                    """, reviewSeq, evaluationItems.get(itemIndex), reviewScore,
                    completed ? "일별 테스트 평가 의견" : "작성 중",
                    Timestamp.valueOf(reviewCreatedAt),
                    Timestamp.valueOf(submittedAt == null ? reviewCreatedAt : submittedAt));
        }
    }

    private void deleteReviewWorkflow(Long conferenceSeq, long abstractSeq) {
        jdbcTemplate.update(
                "DELETE FROM abstract_review_assignments WHERE conferenceSeq = ? AND abstractSeq = ?",
                conferenceSeq, abstractSeq
        );
    }

    private void deleteReview(Long conferenceSeq, long assignmentSeq) {
        jdbcTemplate.update(
                """
                DELETE FROM abstract_reviews
                WHERE assignmentSeq = ?
                  AND EXISTS (SELECT 1 FROM abstract_review_assignments assignment
                              WHERE assignment.seq = abstract_reviews.assignmentSeq
                                AND assignment.conferenceSeq = ?)
                """,
                assignmentSeq, conferenceSeq
        );
    }

    private void cancelOtherAssignments(
            Long conferenceSeq,
            DailyAbstractSeed abstractSeed,
            List<Long> desiredAssignmentSeqs
    ) {
        List<Long> activeAssignmentSeqs = jdbcTemplate.queryForList("""
                SELECT seq
                FROM abstract_review_assignments
                WHERE conferenceSeq = ? AND abstractSeq = ?
                  AND status IN ('assigned', 'accepted', 'in_review', 'completed')
                """, Long.class, conferenceSeq, abstractSeed.seq());
        LocalDateTime cancelledAt = abstractSeed.createdAt().plusSeconds(1);
        for (Long assignmentSeq : activeAssignmentSeqs) {
            if (desiredAssignmentSeqs.contains(assignmentSeq)) {
                continue;
            }
            jdbcTemplate.update("""
                    UPDATE abstract_review_assignments
                    SET status = 'cancelled', cancelledAt = ?, completedAt = NULL, updatedAt = ?
                    WHERE seq = ? AND conferenceSeq = ?
                    """, Timestamp.valueOf(cancelledAt), Timestamp.valueOf(cancelledAt),
                    assignmentSeq, conferenceSeq);
        }
    }

    private void removeUnexpectedScores(long reviewSeq, List<Long> expectedEvaluationItems) {
        List<Long> existingEvaluationItems = jdbcTemplate.queryForList("""
                SELECT evaluationItemSeq
                FROM abstract_review_scores
                WHERE reviewSeq = ?
                """, Long.class, reviewSeq);
        for (Long evaluationItemSeq : existingEvaluationItems) {
            if (expectedEvaluationItems.contains(evaluationItemSeq)) {
                continue;
            }
            jdbcTemplate.update("""
                    DELETE FROM abstract_review_scores
                    WHERE reviewSeq = ? AND evaluationItemSeq = ?
                    """, reviewSeq, evaluationItemSeq);
        }
    }

    private LocalDateTime assignmentTime(LocalDateTime createdAt, int reviewerIndex) {
        return createdAt.plusSeconds(2L + reviewerIndex * 5L);
    }

    private LocalDateTime reviewTime(LocalDateTime createdAt, int reviewerCount) {
        return assignmentTime(createdAt, Math.max(0, reviewerCount - 1)).plusSeconds(3);
    }

    private List<MemberSeed> requireDailyMembers(Long conferenceSeq, LocalDate date) {
        String emailPattern = "dashboard.testdata.c%d.%s.%%@example.test"
                .formatted(conferenceSeq, date.format(DATE_KEY_FORMAT));
        String dbEncString = personalDataProperties.requireDbEncString();
        List<MemberSeed> members = jdbcTemplate.query("""
                SELECT seq, memberType,
                       CONVERT(AES_DECRYPT(UNHEX(email), SHA2(?, 512)) USING utf8mb4) AS email,
                       CONVERT(AES_DECRYPT(UNHEX(firstName), SHA2(?, 512)) USING utf8mb4) AS firstName,
                       CONVERT(AES_DECRYPT(UNHEX(lastName), SHA2(?, 512)) USING utf8mb4) AS lastName,
                       institution, department, country,
                       CONVERT(AES_DECRYPT(UNHEX(mobile), SHA2(?, 512)) USING utf8mb4) AS mobile
                FROM members
                WHERE conferenceSeq = ?
                  AND CONVERT(AES_DECRYPT(UNHEX(email), SHA2(?, 512)) USING utf8mb4) LIKE ?
                ORDER BY CONVERT(AES_DECRYPT(UNHEX(email), SHA2(?, 512)) USING utf8mb4)
                """, (rs, rowNum) -> new MemberSeed(
                rs.getLong("seq"),
                rs.getString("memberType"),
                rs.getString("email"),
                rs.getString("firstName"),
                rs.getString("lastName"),
                rs.getString("institution"),
                rs.getString("department"),
                defaultCountry(rs.getString("country")),
                rs.getString("mobile")
        ), dbEncString, dbEncString, dbEncString, dbEncString,
                conferenceSeq, dbEncString, emailPattern, dbEncString);
        if (members.isEmpty()) {
            throw new IllegalStateException("%s 일별 회원 데이터가 없습니다. 회원데이터생성을 먼저 실행하세요.".formatted(date));
        }
        return members;
    }

    private List<RegistrationRate> findRegistrationRates(Long conferenceSeq) {
        List<RegistrationRate> rates = jdbcTemplate.query("""
                SELECT category.seq, category.categoryCode, category.categoryName,
                       rate.periodType, rate.currency, rate.amount
                FROM registration_categories category
                JOIN registration_fee_rates rate ON rate.categorySeq = category.seq
                WHERE category.isUsed = 'Y' AND category.isDelete = 'N'
                  AND category.conferenceSeq = ?
                ORDER BY rate.periodType, category.sortOrder, category.seq
                """, (rs, rowNum) -> new RegistrationRate(
                rs.getLong("seq"),
                rs.getString("categoryCode"),
                rs.getString("categoryName"),
                rs.getString("periodType"),
                rs.getString("currency"),
                rs.getBigDecimal("amount")
        ), conferenceSeq);
        for (String currency : Set.of("KRW", "USD")) {
            Set<String> periods = new HashSet<>();
            rates.stream().filter(rate -> currency.equals(rate.currency()))
                    .forEach(rate -> periods.add(rate.periodType()));
            if (!periods.containsAll(Set.of("EARLY_BIRD", "REGULAR"))) {
                throw new IllegalStateException(currency + " 통화의 얼리버드와 일반 등록비가 모두 설정되어 있어야 합니다.");
            }
        }
        return rates;
    }

    private Map<String, List<RegistrationOptionSeed>> findRegistrationOptions(Long conferenceSeq) {
        List<RegistrationOptionSeed> options = jdbcTemplate.query("""
                SELECT optionRow.seq, currencyRow.currency,
                       CASE currencyRow.currency WHEN 'KRW' THEN optionRow.krwPrice ELSE optionRow.usdPrice END AS unitPrice,
                       CASE WHEN optionRow.capacity IS NULL THEN -1
                            ELSE GREATEST(optionRow.capacity - COALESCE(used.quantity, 0), 0)
                       END AS remainingCapacity
                FROM registration_options optionRow
                JOIN (SELECT 'KRW' AS currency UNION ALL SELECT 'USD') currencyRow
                LEFT JOIN (
                    SELECT item.optionSeq, SUM(item.quantity) AS quantity
                    FROM pre_registration_option_items item
                    JOIN pre_registrations registration ON registration.seq = item.preRegistrationSeq
                    WHERE registration.conferenceSeq = ? AND registration.applicationStatus = 'SUBMITTED'
                    GROUP BY item.optionSeq
                ) used ON used.optionSeq = optionRow.seq
                WHERE optionRow.conferenceSeq = ? AND optionRow.enabled = TRUE
                  AND optionRow.maxPerPerson >= 1
                  AND CASE currencyRow.currency WHEN 'KRW' THEN optionRow.krwPrice ELSE optionRow.usdPrice END IS NOT NULL
                ORDER BY optionRow.sortOrder, optionRow.seq, currencyRow.currency
                """, (rs, rowNum) -> new RegistrationOptionSeed(
                rs.getLong("seq"), rs.getString("currency"), rs.getBigDecimal("unitPrice"),
                rs.getInt("remainingCapacity")
        ), conferenceSeq, conferenceSeq);
        Map<String, List<RegistrationOptionSeed>> result = new HashMap<>();
        options.forEach(option -> result.computeIfAbsent(option.currency(), ignored -> new ArrayList<>()).add(option));
        return result;
    }

    private List<RegistrationOptionSelection> selectRegistrationOptions(
            List<RegistrationOptionSeed> options,
            Map<Long, Integer> remainingByOption,
            int index,
            boolean consumesCapacity
    ) {
        if (options.isEmpty() || index % 3 == 0) {
            return List.of();
        }
        int desiredCount = index % 7 == 0 ? Math.min(2, options.size()) : 1;
        List<RegistrationOptionSelection> result = new ArrayList<>();
        for (int offset = 0; offset < options.size() && result.size() < desiredCount; offset++) {
            RegistrationOptionSeed option = options.get((index + offset - 1) % options.size());
            int remaining = remainingByOption.getOrDefault(option.seq(), option.remainingCapacity());
            if (remaining == 0) {
                continue;
            }
            result.add(new RegistrationOptionSelection(option.seq(), 1));
            if (consumesCapacity && remaining > 0) {
                remainingByOption.put(option.seq(), remaining - 1);
            }
        }
        return result;
    }

    public record DateRange(LocalDate startDate, LocalDate endDate) {}

    public DateRange conferenceRange(Long conferenceSeq) {
        requireConference(conferenceSeq);
        LocalDate eventStart = jdbcTemplate.queryForObject("SELECT eventStartDate FROM conference_settings WHERE seq = ?", LocalDate.class, conferenceSeq);
        if (eventStart == null) throw new IllegalArgumentException("학회 행사 시작일을 먼저 설정해 주세요.");
        return new DateRange(eventStart.minusDays(60), eventStart.minusDays(1));
    }

    private void requireConference(Long conferenceSeq) {
        if (conferenceSeq == null || conferenceSeq <= 0) {
            throw new IllegalArgumentException("학회 정보가 필요합니다.");
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM conference_settings WHERE seq = ?",
                Integer.class,
                conferenceSeq
        );
        if (count == null || count == 0) {
            throw new IllegalArgumentException("선택한 학회 정보를 찾을 수 없습니다.");
        }
    }

    private String dailyMemberEmail(Long conferenceSeq, String dateKey, int index) {
        return "dashboard.testdata.c%d.%s.%03d@example.test".formatted(conferenceSeq, dateKey, index);
    }

    private String dailyRegistrationNumber(Long conferenceSeq, String dateKey, int index) {
        return "TDDR%s%s%03d".formatted(conferenceKey(conferenceSeq), dateKey, index);
    }

    private String conferenceKey(Long conferenceSeq) {
        return Long.toString(conferenceSeq, Character.MAX_RADIX);
    }

    private void migrateLegacyDailyMemberEmail(
            Long conferenceSeq,
            String dateKey,
            int index,
            String newEmail,
            String dbEncString
    ) {
        Integer currentCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM members
                WHERE conferenceSeq = ?
                  AND email = HEX(AES_ENCRYPT(?, SHA2(?, 512)))
                """, Integer.class, conferenceSeq, newEmail, dbEncString);
        if (currentCount != null && currentCount > 0) {
            return;
        }
        String legacyEmail = "dashboard.testdata.%s.%03d@example.test".formatted(dateKey, index);
        jdbcTemplate.update("""
                UPDATE members
                SET email = HEX(AES_ENCRYPT(?, SHA2(?, 512))), updatedAt = updatedAt
                WHERE conferenceSeq = ?
                  AND email = HEX(AES_ENCRYPT(?, SHA2(?, 512)))
                """, newEmail, dbEncString, conferenceSeq, legacyEmail, dbEncString);
    }

    private void migrateLegacyDailyRegistrationNumber(
            Long conferenceSeq,
            String dateKey,
            int index,
            String newNumber
    ) {
        Integer currentCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pre_registrations
                WHERE conferenceSeq = ? AND registrationNumber = ?
                """, Integer.class, conferenceSeq, newNumber);
        if (currentCount != null && currentCount > 0) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE pre_registrations
                SET registrationNumber = ?
                WHERE conferenceSeq = ? AND registrationNumber = ?
                """, newNumber, conferenceSeq, "TDDR%s%03d".formatted(dateKey, index));
    }

    private String currencyForMemberType(String memberType) {
        return "domestic".equals(memberType) ? "KRW" : "USD";
    }

    private List<Long> requireCommonCodes(String groupCode, String label) {
        List<Long> codes = jdbcTemplate.queryForList("""
                SELECT code.seq
                FROM common_codes code
                JOIN common_codes root
                  ON root.seq = code.parentSeq
                 AND root.parentSeq = 0
                 AND root.groupCode = ?
                 AND root.isUsed = 'Y'
                 AND root.isDelete = 'N'
                WHERE code.groupCode = ?
                  AND code.isUsed = 'Y'
                  AND code.isDelete = 'N'
                ORDER BY code.sortOrder, code.seq
                """, Long.class, groupCode, groupCode);
        if (codes.isEmpty()) {
            throw new IllegalStateException("사용 가능한 %s 코드가 없습니다.".formatted(label));
        }
        return codes;
    }

    private List<Long> requireReviewers(Long conferenceSeq) {
        List<Long> reviewers = jdbcTemplate.queryForList("""
                SELECT reviewer.seq
                FROM reviewers reviewer
                JOIN admin_accounts account ON account.seq = reviewer.adminSeq
                WHERE reviewer.isUsed = 'Y'
                  AND reviewer.isDelete = 'N'
                  AND reviewer.conferenceSeq = ?
                  AND account.role = 'reviewer'
                  AND account.status = 'active'
                ORDER BY reviewer.seq
                """, Long.class, conferenceSeq);
        return reviewers;
    }

    static TestDataService.AbstractSamplePlan unreviewedPlan(int index, Long presentationTypeCode) {
        return new TestDataService.AbstractSamplePlan(index % 5 == 0 ? "draft" : "submitted", false,
                presentationTypeCode, null, null);
    }

    private List<Long> requireEvaluationItems(Long conferenceSeq) {
        List<Long> items = jdbcTemplate.queryForList("""
                SELECT seq
                FROM abstract_evaluation_items
                WHERE conferenceSeq = ? AND isUsed = 'Y' AND isDelete = 'N'
                ORDER BY sortOrder, seq
                """, Long.class, conferenceSeq);
        if (items.isEmpty()) {
            throw new IllegalStateException("사용 가능한 초록 평가항목이 없습니다.");
        }
        return items;
    }

    private long requireAssigningAdmin() {
        List<Long> admins = jdbcTemplate.queryForList("""
                SELECT seq
                FROM admin_accounts
                WHERE role = 'admin' AND status = 'active'
                ORDER BY seq
                LIMIT 1
                """, Long.class);
        if (admins.isEmpty()) {
            throw new IllegalStateException("심사자를 배정할 활성 관리자 계정이 없습니다.");
        }
        return admins.get(0);
    }

    private static int desiredCount(LocalDate date, String target, int min, int max) {
        long seed = date.toEpochDay() * 31L + target.hashCode();
        return min + new Random(seed).nextInt(max - min + 1);
    }

    private LocalDateTime sampleTime(LocalDate date, int index) {
        return date.atStartOfDay().plusSeconds(index * 3L);
    }

    static int dailyAbstractSampleIndex(LocalDate date, int index) {
        return dailyAbstractSampleIndex(START_DATE, date, index);
    }

    static int dailyAbstractSampleIndex(LocalDate startDate, LocalDate date, int index) {
        if (date == null || date.isBefore(startDate)) {
            throw new IllegalArgumentException("일별 초록 날짜는 시작일 이후여야 합니다.");
        }
        int dailyCount = dailyAbstractDesiredCount(date);
        if (index < 1 || index > dailyCount) {
            throw new IllegalArgumentException("일별 초록 순번이 해당 날짜의 생성 건수 범위를 벗어났습니다.");
        }
        int sampleIndex = index;
        for (LocalDate cursor = startDate; cursor.isBefore(date); cursor = cursor.plusDays(1)) {
            sampleIndex = Math.addExact(sampleIndex, dailyAbstractDesiredCount(cursor));
        }
        return sampleIndex;
    }

    static int dailyAbstractDesiredCount(LocalDate date) {
        return desiredCount(date, "abstracts", ABSTRACT_MIN, ABSTRACT_MAX);
    }

    private String assignmentStatus(
            TestDataService.AbstractSamplePlan plan,
            int abstractIndex,
            int reviewerIndex
    ) {
        if (plan.forcedDecision()) {
            return "in_review";
        }
        if (isFinalAbstractStatus(plan.status())) {
            return "completed";
        }
        String[] progressStatuses = {"assigned", "accepted", "in_review"};
        return progressStatuses[(abstractIndex + reviewerIndex) % progressStatuses.length];
    }

    private boolean hasReached(String status, String target) {
        List<String> progress = List.of("assigned", "accepted", "in_review", "completed");
        return progress.indexOf(status) >= progress.indexOf(target);
    }

    private boolean isFinalAbstractStatus(String status) {
        return "approved".equals(status) || "rejected".equals(status);
    }

    private String recommendation(String abstractStatus, int index) {
        if ("approved".equals(abstractStatus)) {
            return "accept";
        }
        if ("rejected".equals(abstractStatus)) {
            return "reject";
        }
        return null;
    }

    private int score(String abstractStatus, int abstractIndex, int itemIndex) {
        if ("approved".equals(abstractStatus)) {
            return 4 + (abstractIndex + itemIndex) % 3;
        }
        if ("rejected".equals(abstractStatus)) {
            return 1 + (abstractIndex + itemIndex) % 3;
        }
        return 2 + (abstractIndex + itemIndex) % 5;
    }

    private Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of(SCHEDULE_ZONE));
    }

    private void validateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalStateException("일별 테스트 데이터 시작일이 종료일보다 늦습니다.");
        }
    }

    private DailyCreationResult result(
            String target,
            LocalDate startDate,
            LocalDate endDate,
            int createdCount,
            int desiredCount
    ) {
        return new DailyCreationResult(
                target,
                startDate,
                endDate,
                createdCount,
                desiredCount - createdCount,
                desiredCount
        );
    }

    private String defaultCountry(String country) {
        return country == null || country.isBlank() ? "South Korea" : country;
    }

    public record DailyCreationResult(
            String target,
            LocalDate startDate,
            LocalDate endDate,
            int createdCount,
            int skippedCount,
            int desiredCount
    ) {
    }

    public record DailySetCreationResult(
            DailyCreationResult members,
            DailyCreationResult preRegistrations,
            DailyCreationResult abstracts
    ) {
    }

    private record MemberSeed(
            long seq,
            String memberType,
            String email,
            String firstName,
            String lastName,
            String institution,
            String department,
            String country,
            String mobile
    ) {
        private String fullName() {
            return firstName + " " + lastName;
        }
    }

    private record RegistrationRate(
            long categorySeq,
            String categoryCode,
            String categoryName,
            String periodType,
            String currency,
            BigDecimal amount
    ) {
    }

    private record RegistrationOptionSeed(
            long seq,
            String currency,
            BigDecimal unitPrice,
            int remainingCapacity
    ) {
    }

    private record RegistrationOptionSelection(long optionSeq, int quantity) {
    }

    private record DailyAbstractSeed(
            long seq,
            String title,
            String status,
            long presentationTypeCode,
            LocalDateTime createdAt
    ) {
    }
}
