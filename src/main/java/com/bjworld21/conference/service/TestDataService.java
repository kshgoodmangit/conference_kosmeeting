package com.bjworld21.conference.service;

import com.bjworld21.conference.config.PersonalDataProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * 관리자 테스트 데이터 전용 서비스입니다.
 * 기능 제거 시 TestDataController, TestDataPage와 testdata 리소스를 함께 삭제합니다.
 */
@Service
public class TestDataService {
    private static final int MEMBER_COUNT = 316;
    private static final int PRE_REGISTRATION_COUNT = 276;
    private static final int ABSTRACT_COUNT = 216;
    private static final int POPUP_COUNT = 18;
    private static final String POPUP_BLANK_IMAGE_RESOURCE = "testdata/popup-blank.png";
    private static final String SPEAKER_IMAGE_RESOURCE_ROOT = "testdata/speakers/";
    private static final DateTimeFormatter BATCH_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

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
            "KAIST", "University of Tokyo", "National University of Singapore",
            "University of Oxford", "Stanford University"
    };
    private static final String[] COUNTRIES = {
            "United States", "United Kingdom", "Canada", "Australia",
            "Germany", "France", "Japan", "Singapore"
    };
    private static final String[] ABSTRACT_TOPICS = {
            "Clinical Outcomes", "Digital Health", "Precision Medicine", "Public Health",
            "Medical Education", "Healthcare Policy", "Artificial Intelligence", "Patient Safety"
    };
    private static final List<SpeakerSample> SPEAKER_SAMPLES = List.of(
            new SpeakerSample("KEYNOTE", "Dr. Minseo Kim", "김민서", "Seoul National University Hospital",
                    "Department of Internal Medicine", "Professor", "KR",
                    "Clinical researcher leading multicenter studies in precision medicine and patient outcomes.",
                    "minseo-kim", true, 10, "01-minseo-kim.jpg"),
            new SpeakerSample("KEYNOTE", "Prof. James Wilson", null, "University of Oxford",
                    "Nuffield Department of Medicine", "Professor", "GB",
                    "International expert in translational medicine and evidence-based clinical practice.",
                    "james-wilson", true, 20, "02-james-wilson.jpg"),
            new SpeakerSample("INVITED", "Dr. Yuki Tanaka", null, "The University of Tokyo",
                    "Graduate School of Medical Science", "Associate Professor", "JP",
                    "Researcher focused on digital health, medical data science, and clinical decision support.",
                    "yuki-tanaka", false, 10, "03-yuki-tanaka.jpg"),
            new SpeakerSample("INVITED", "Dr. Jiwoo Park", "박지우", "Yonsei University College of Medicine",
                    "Department of Preventive Medicine", "Assistant Professor", "KR",
                    "Public-health researcher studying population health, prevention, and healthcare policy.",
                    "jiwoo-park", false, 20, "04-jiwoo-park.jpg"),
            new SpeakerSample("INVITED", "Prof. Emily Chen", null, "National University of Singapore",
                    "Saw Swee Hock School of Public Health", "Professor", "SG",
                    "Global-health specialist working on epidemiology and health-system resilience.",
                    "emily-chen", true, 30, "05-emily-chen.jpg"),
            new SpeakerSample("SPECIAL", "Dr. Daniel Smith", null, "Johns Hopkins University",
                    "Biomedical Informatics", "Research Director", "US",
                    "Biomedical informatics leader applying artificial intelligence to patient safety.",
                    "daniel-smith", false, 10, "06-daniel-smith.jpg")
    );

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final UploadStorage uploadStorage;
    private final Resource popupBlankImageResource;
    private final PersonalDataProperties personalDataProperties;

    public TestDataService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            UploadStorage uploadStorage,
            @Value("classpath:" + POPUP_BLANK_IMAGE_RESOURCE) Resource popupBlankImageResource,
            PersonalDataProperties personalDataProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.uploadStorage = uploadStorage;
        this.popupBlankImageResource = popupBlankImageResource;
        this.personalDataProperties = personalDataProperties;
    }

    @Transactional
    public CreationResult createMembers(Long conferenceSeq) {
        requireConference(conferenceSeq);
        String batchId = newBatchId();
        Random random = new Random(batchId.hashCode());
        LocalDateTime now = LocalDateTime.now();
        String password = passwordEncoder.encode("Testdata12#$");
        String dbEncString = personalDataProperties.requireDbEncString();
        List<Object[]> rows = new ArrayList<>(MEMBER_COUNT);

        for (int index = 1; index <= MEMBER_COUNT; index++) {
            boolean international = index % 3 == 0;
            LocalDateTime createdAt = randomPastDate(now, random, 180);
            rows.add(new Object[]{
                    conferenceSeq,
                    international ? "international" : "domestic",
                    "testdata.%s.%03d@example.test".formatted(batchId, index),
                    dbEncString,
                    password,
                    FIRST_NAMES[(index - 1) % FIRST_NAMES.length],
                    dbEncString,
                    LAST_NAMES[(index * 3 - 1) % LAST_NAMES.length],
                    dbEncString,
                    INSTITUTIONS[(index - 1) % INSTITUTIONS.length],
                    index % 4 == 0 ? "Research Center" : "Department of Medicine",
                    index % 5 == 0 ? "Professor" : index % 3 == 0 ? "Researcher" : "Student",
                    international ? COUNTRIES[(index - 1) % COUNTRIES.length] : null,
                    international
                            ? "+1-202-555-%04d".formatted(index)
                            : "010-%04d-%04d".formatted(1000 + index, 5000 + index),
                    dbEncString,
                    index % 3 == 0,
                    Timestamp.valueOf(createdAt),
                    Timestamp.valueOf(createdAt)
            });
        }

        jdbcTemplate.batchUpdate("""
                INSERT INTO members (
                    conferenceSeq, memberType, email, password, firstName, lastName, institution,
                    department, positionTitle, country, mobile, newsletter, createdAt, updatedAt
                ) VALUES (
                    ?, ?, HEX(AES_ENCRYPT(?, SHA2(?, 512))), ?,
                    HEX(AES_ENCRYPT(?, SHA2(?, 512))),
                    HEX(AES_ENCRYPT(?, SHA2(?, 512))),
                    ?, ?, ?, ?, HEX(AES_ENCRYPT(?, SHA2(?, 512))), ?, ?, ?
                )
                """, rows);

        return new CreationResult("members", batchId, MEMBER_COUNT, 0, 0, 0, 0);
    }

    @Transactional
    public CreationResult createPreRegistrations(Long conferenceSeq) {
        requireConference(conferenceSeq);
        List<MemberSeed> members = requireMembers(conferenceSeq, PRE_REGISTRATION_COUNT);
        List<RegistrationRate> rates = findRegistrationRates(conferenceSeq);
        Map<String, List<RegistrationOptionSeed>> optionsByCurrency = findRegistrationOptions(conferenceSeq);
        Map<Long, Integer> remainingByOption = new HashMap<>();
        optionsByCurrency.values().stream().flatMap(List::stream).forEach(option ->
                remainingByOption.put(option.seq(), option.remainingCapacity()));

        String batchId = newBatchId();
        Random random = new Random(batchId.hashCode());
        LocalDateTime now = LocalDateTime.now();
        String[] paymentMethods = {"CARD", "BANK_TRANSFER", "VIRTUAL_ACCOUNT"};
        List<Object[]> rows = new ArrayList<>(PRE_REGISTRATION_COUNT);
        Map<String, List<RegistrationOptionSelection>> selectionsByRegistrationNo = new HashMap<>();

        for (int index = 1; index <= PRE_REGISTRATION_COUNT; index++) {
            MemberSeed member = members.get(index - 1);
            String currency = currencyForMemberType(member.memberType());
            List<RegistrationRate> memberRates = rates.stream()
                    .filter(rate -> currency.equals(rate.currency()))
                    .toList();
            if (memberRates.isEmpty()) {
                throw new IllegalStateException(currency + " 통화로 사용할 수 있는 사전등록 등록비가 없습니다.");
            }
            RegistrationRate rate = memberRates.get((index - 1) % memberRates.size());
            LocalDateTime createdAt = randomPastDate(now, random, 120);
            boolean cancelled = index % 13 == 0;
            String applicationStatus = cancelled ? "CANCELLED" : "SUBMITTED";
            String paymentStatus = paymentStatus(index, cancelled);
            boolean paymentRecorded = "PAID".equals(paymentStatus) || "REFUNDED".equals(paymentStatus);
            String paymentMethod = "UNPAID".equals(paymentStatus)
                    ? null
                    : paymentMethods[(index - 1) % paymentMethods.length];
            LocalDateTime paidAt = paymentRecorded ? createdAt.plusHours(2 + index % 48) : null;
            LocalDateTime cancelledAt = cancelled ? createdAt.plusDays(3 + index % 10) : null;
            String registrationNumber = "TDREG%s%03d".formatted(batchId, index);
            List<RegistrationOptionSelection> selections = selectRegistrationOptions(
                    optionsByCurrency.getOrDefault(currency, List.of()), remainingByOption, index, !cancelled
            );
            BigDecimal optionAmount = selections.stream()
                    .map(RegistrationOptionSelection::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalAmount = rate.amount().add(optionAmount);
            selectionsByRegistrationNo.put(registrationNumber, selections);

            rows.add(new Object[]{
                    registrationNumber,
                    member.seq(),
                    conferenceSeq,
                    rate.categorySeq(),
                    rate.categoryCode(),
                    rate.categoryName(),
                    rate.periodType(),
                    currency,
                    rate.amount(),
                    optionAmount,
                    totalAmount,
                    applicationStatus,
                    paymentStatus,
                    paymentMethod,
                    paymentRecorded ? "TDPAY-%s-%03d".formatted(batchId, index) : null,
                    paymentRecorded ? totalAmount : null,
                    timestamp(paidAt),
                    true,
                    Timestamp.valueOf(createdAt),
                    true,
                    Timestamp.valueOf(createdAt),
                    timestamp(cancelledAt),
                    "[TESTDATA:%s] 자동 생성 사전등록".formatted(batchId),
                    Timestamp.valueOf(createdAt),
                    Timestamp.valueOf(createdAt)
            });
        }

        jdbcTemplate.batchUpdate("""
                INSERT INTO pre_registrations (
                    registrationNumber, memberSeq, conferenceSeq, categorySeq, categoryCode, categoryName,
                    periodType, currency, feeAmount, optionAmount, totalAmount, applicationStatus, paymentStatus,
                    paymentMethod, paymentTransactionId, paidAmount, paidAt,
                    privacyAgreed, privacyAgreedAt, termsAgreed, termsAgreedAt,
                    cancelledAt, adminMemo, createdAt, updatedAt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, rows);

        List<CreatedRegistration> createdRegistrations = jdbcTemplate.query("""
                SELECT seq, registrationNumber
                FROM pre_registrations
                WHERE conferenceSeq = ? AND registrationNumber LIKE ?
                ORDER BY registrationNumber
                """, (rs, rowNum) -> new CreatedRegistration(
                rs.getLong("seq"), rs.getString("registrationNumber")
        ), conferenceSeq, "TDREG" + batchId + "%");
        if (createdRegistrations.size() != PRE_REGISTRATION_COUNT) {
            throw new IllegalStateException("생성한 사전등록을 다시 조회하지 못했습니다.");
        }
        List<Object[]> optionRows = new ArrayList<>();
        for (CreatedRegistration registration : createdRegistrations) {
            for (RegistrationOptionSelection selection
                    : selectionsByRegistrationNo.getOrDefault(registration.registrationNumber(), List.of())) {
                optionRows.add(new Object[]{registration.seq(), selection.optionSeq(), selection.quantity()});
            }
        }
        if (!optionRows.isEmpty()) {
            jdbcTemplate.batchUpdate("""
                    INSERT INTO pre_registration_option_items
                        (preRegistrationSeq, optionSeq, quantity, createdAt)
                    VALUES (?, ?, ?, NOW())
                    """, optionRows);
        }

        return new CreationResult(
                "pre-registrations", batchId, PRE_REGISTRATION_COUNT, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, optionRows.size(), 0
        );
    }

    @Transactional
    public CreationResult createAbstracts(Long conferenceSeq, long assignedByAdminSeq) {
        requireConference(conferenceSeq);
        List<MemberSeed> members = requireMembers(conferenceSeq, ABSTRACT_COUNT);
        List<Long> presentationTypes = requireCommonCodes("ABSTRACT_PRESENTATION_TYPES", "초록 발표형식");
        List<Long> categories = requireCommonCodes("ABSTRACT_CATEGORY", "초록 분류");
        List<Long> reviewers = requireReviewers(conferenceSeq);
        List<Long> evaluationItems = requireEvaluationItems(conferenceSeq);
        String batchId = newBatchId();
        String submissionPrefix = "TDABS" + batchId;
        Random random = new Random(batchId.hashCode());
        LocalDateTime now = LocalDateTime.now();
        List<Object[]> abstractRows = new ArrayList<>(ABSTRACT_COUNT);
        Map<String, Integer> indexBySubmissionNo = new HashMap<>();
        Map<String, AbstractSamplePlan> planBySubmissionNo = new HashMap<>();
        int draftCount = 0;
        int submittedCount = 0;
        int underReviewCount = 0;
        int approvedCount = 0;
        int rejectedCount = 0;
        int normalDecisionCount = 0;
        int forcedDecisionCount = 0;
        int changedPresentationTypeCount = 0;

        for (int index = 1; index <= ABSTRACT_COUNT; index++) {
            MemberSeed member = members.get(index - 1);
            String submissionNo = "%s%03d".formatted(submissionPrefix, index);
            AbstractSamplePlan plan = abstractSamplePlan(index, presentationTypes);
            String status = plan.status();
            LocalDateTime createdAt = randomPastDate(now, random, 90);
            LocalDateTime submittedAt = "draft".equals(status)
                    ? null
                    : createdAt.plusHours(1 + index % 12);
            LocalDateTime decisionAt = isFinalAbstractStatus(status)
                    ? createdAt.plusDays(plan.forcedDecision() ? 3 : 7 + index % 5)
                    : null;
            boolean aiUsage = index % 4 == 0;

            indexBySubmissionNo.put(submissionNo, index);
            planBySubmissionNo.put(submissionNo, plan);
            switch (status) {
                case "draft" -> draftCount++;
                case "submitted" -> submittedCount++;
                case "under_review" -> underReviewCount++;
                case "approved" -> approvedCount++;
                case "rejected" -> rejectedCount++;
                default -> throw new IllegalStateException("지원하지 않는 초록 테스트 상태입니다: " + status);
            }
            if (isFinalAbstractStatus(status)) {
                if (plan.forcedDecision()) {
                    forcedDecisionCount++;
                } else {
                    normalDecisionCount++;
                }
                if ("approved".equals(status)
                        && !plan.presentationTypeCode().equals(plan.acceptedPresentationTypeCode())) {
                    changedPresentationTypeCount++;
                }
            }
            abstractRows.add(new Object[]{
                    conferenceSeq,
                    member.seq(),
                    "admin",
                    assignedByAdminSeq,
                    submissionNo,
                    plan.presentationTypeCode(),
                    plan.acceptedPresentationTypeCode(),
                    categories.get((index - 1) % categories.size()),
                    "[TEST %03d] %s Study".formatted(index, ABSTRACT_TOPICS[(index - 1) % ABSTRACT_TOPICS.length]),
                    "This study evaluates clinically meaningful outcomes using a structured multicenter dataset.",
                    "Participants were assessed with standardized methods and predefined statistical analyses.",
                    "The generated test cohort showed diverse response patterns across the primary endpoints.",
                    "The findings support further validation and provide data for workflow verification.",
                    aiUsage,
                    aiUsage ? "ChatGPT test data generation" : null,
                    aiUsage && index % 8 == 0,
                    true,
                    180 + index % 241,
                    status,
                    isFinalAbstractStatus(status) ? assignedByAdminSeq : null,
                    timestamp(decisionAt),
                    plan.decisionReason(),
                    plan.forcedDecision(),
                    timestamp(submittedAt),
                    timestamp(decisionAt),
                    Timestamp.valueOf(createdAt),
                    Timestamp.valueOf(decisionAt != null
                            ? decisionAt
                            : submittedAt != null ? submittedAt : createdAt)
            });
        }

        jdbcTemplate.batchUpdate("""
                INSERT INTO abstract_submissions (
                    conferenceSeq, memberSeq, submissionSource, createdByAdminSeq,
                    submissionNo, presentationTypeCode, acceptedPresentationTypeCode, categoryCode, title,
                    objectiveText, methodsText, resultsText, conclusionsText,
                    aiUsage, aiVersionInfo, aiDataAnalysisUsed, plagiarismPolicyConfirmed,
                    wordCount, status, decisionByAdminSeq, decisionAt, decisionReason, forcedDecision,
                    submittedAt, reviewedAt, createdAt, updatedAt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, abstractRows);

        List<CreatedAbstract> createdAbstracts = jdbcTemplate.query("""
                SELECT seq, submissionNo, memberSeq, createdAt
                FROM abstract_submissions
                WHERE conferenceSeq = ? AND submissionNo LIKE ?
                ORDER BY submissionNo
                """, (rs, rowNum) -> new CreatedAbstract(
                rs.getLong("seq"),
                rs.getString("submissionNo"),
                rs.getLong("memberSeq"),
                rs.getTimestamp("createdAt").toLocalDateTime()
        ), conferenceSeq, submissionPrefix + "%");
        if (createdAbstracts.size() != ABSTRACT_COUNT) {
            throw new IllegalStateException("생성한 초록을 다시 조회하지 못했습니다.");
        }

        Map<Long, MemberSeed> memberBySeq = new HashMap<>();
        members.forEach(member -> memberBySeq.put(member.seq(), member));
        List<Object[]> institutionRows = new ArrayList<>(ABSTRACT_COUNT);
        List<Object[]> authorRows = new ArrayList<>(ABSTRACT_COUNT);
        List<Object[]> assignmentRows = new ArrayList<>(ABSTRACT_COUNT * 2);

        for (CreatedAbstract createdAbstract : createdAbstracts) {
            int index = indexBySubmissionNo.get(createdAbstract.submissionNo());
            MemberSeed member = memberBySeq.get(createdAbstract.memberSeq());
            institutionRows.add(new Object[]{
                    createdAbstract.seq(), 1, member.country(), member.institution(), member.department()
            });
            authorRows.add(new Object[]{
                    createdAbstract.seq(), 1, member.fullName(), personalDataProperties.requireDbEncString(), 1, true, true,
                    member.email(), personalDataProperties.requireDbEncString(), member.country(), member.mobile(), personalDataProperties.requireDbEncString()
            });

            AbstractSamplePlan plan = planBySubmissionNo.get(createdAbstract.submissionNo());
            int reviewerCount = reviewerCount(plan, reviewers.size(), random);
            List<Long> shuffledReviewers = new ArrayList<>(reviewers);
            Collections.shuffle(shuffledReviewers, random);
            for (int reviewerIndex = 0; reviewerIndex < reviewerCount; reviewerIndex++) {
                String assignmentStatus = assignmentStatus(plan, index, reviewerIndex);
                LocalDateTime assignedAt = createdAbstract.createdAt().plusDays(1);
                LocalDateTime acceptedAt = hasReached(assignmentStatus, "accepted") ? assignedAt.plusHours(8) : null;
                LocalDateTime startedAt = hasReached(assignmentStatus, "in_review") ? assignedAt.plusDays(1) : null;
                LocalDateTime completedAt = "completed".equals(assignmentStatus) ? assignedAt.plusDays(3 + reviewerIndex) : null;
                LocalDateTime declinedAt = "declined".equals(assignmentStatus) ? assignedAt.plusHours(12) : null;
                LocalDateTime cancelledAt = "cancelled".equals(assignmentStatus) ? assignedAt.plusDays(1) : null;
                LocalDateTime assignmentUpdatedAt = latestAssignmentTime(
                        assignedAt, acceptedAt, startedAt, completedAt, declinedAt, cancelledAt
                );

                assignmentRows.add(new Object[]{
                        conferenceSeq,
                        createdAbstract.seq(), shuffledReviewers.get(reviewerIndex), assignedByAdminSeq,
                        assignmentStatus, Timestamp.valueOf(assignedAt.plusDays(14)), Timestamp.valueOf(assignedAt),
                        timestamp(acceptedAt), timestamp(startedAt), timestamp(completedAt), timestamp(declinedAt),
                        declinedAt == null ? null : "테스트 데이터: 심사 일정 중복",
                        timestamp(cancelledAt), Timestamp.valueOf(assignedAt), Timestamp.valueOf(assignmentUpdatedAt)
                });
            }
        }

        jdbcTemplate.batchUpdate("""
                INSERT INTO abstract_submission_institutions (
                    abstractSeq, institutionNo, country, institutionName, department
                ) VALUES (?, ?, ?, ?, ?)
                """, institutionRows);
        jdbcTemplate.batchUpdate("""
                INSERT INTO abstract_submission_authors (
                    abstractSeq, authorOrder, authorName, institutionNo,
                    isPresentingAuthor, isCorrespondingAuthor, email, country, mobilePhoneNumber
                ) VALUES (?, ?, HEX(AES_ENCRYPT(?, SHA2(?, 512))), ?, ?, ?,
                          HEX(AES_ENCRYPT(?, SHA2(?, 512))), ?, HEX(AES_ENCRYPT(?, SHA2(?, 512))))
                """, authorRows);
        jdbcTemplate.batchUpdate("""
                INSERT INTO abstract_review_assignments (
                    conferenceSeq, abstractSeq, reviewerSeq, assignedByAdminSeq, status, dueAt, assignedAt,
                    acceptedAt, startedAt, completedAt, declinedAt, declineReason, cancelledAt,
                    createdAt, updatedAt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, assignmentRows);

        List<CreatedAssignment> assignments = jdbcTemplate.query("""
                SELECT assignment.seq, assignment.status, assignment.assignedAt,
                       assignment.startedAt, assignment.completedAt, submission.submissionNo,
                       submission.status AS abstractStatus
                FROM abstract_review_assignments assignment
                JOIN abstract_submissions submission ON submission.seq = assignment.abstractSeq
                WHERE assignment.conferenceSeq = ?
                  AND submission.conferenceSeq = ?
                  AND submission.submissionNo LIKE ?
                ORDER BY assignment.seq
                """, (rs, rowNum) -> new CreatedAssignment(
                rs.getLong("seq"),
                rs.getString("status"),
                toLocalDateTime(rs.getTimestamp("assignedAt")),
                toLocalDateTime(rs.getTimestamp("startedAt")),
                toLocalDateTime(rs.getTimestamp("completedAt")),
                rs.getString("submissionNo"),
                rs.getString("abstractStatus")
        ), conferenceSeq, conferenceSeq, submissionPrefix + "%");

        List<Object[]> reviewRows = new ArrayList<>();
        int completedReviewCount = 0;
        for (CreatedAssignment assignment : assignments) {
            int index = indexBySubmissionNo.get(assignment.submissionNo());
            if ("completed".equals(assignment.status())) {
                completedReviewCount++;
                reviewRows.add(new Object[]{
                        assignment.seq(), "submitted", recommendation(assignment.abstractStatus(), index),
                        "연구 목적과 결과가 명확하며 테스트 심사를 완료했습니다.",
                        "[TESTDATA:%s] 관리자 확인용 심사 의견".formatted(batchId),
                        timestamp(assignment.completedAt()), timestamp(assignment.startedAt()),
                        timestamp(assignment.completedAt())
                });
            } else if ("in_review".equals(assignment.status())) {
                reviewRows.add(new Object[]{
                        assignment.seq(), "draft", null, null,
                        "[TESTDATA:%s] 작성 중인 심사".formatted(batchId),
                        null, timestamp(assignment.startedAt()), timestamp(assignment.startedAt())
                });
            }
        }

        if (!reviewRows.isEmpty()) {
            jdbcTemplate.batchUpdate("""
                    INSERT INTO abstract_reviews (
                        assignmentSeq, status, recommendation, overallComment,
                        confidentialComment, submittedAt, createdAt, updatedAt
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, reviewRows);
        }

        List<CreatedReview> reviews = jdbcTemplate.query("""
                SELECT review.seq, review.status, review.createdAt,
                       submission.status AS abstractStatus, submission.submissionNo
                FROM abstract_reviews review
                JOIN abstract_review_assignments assignment ON assignment.seq = review.assignmentSeq
                JOIN abstract_submissions submission ON submission.seq = assignment.abstractSeq
                WHERE assignment.conferenceSeq = ?
                  AND submission.conferenceSeq = ?
                  AND submission.submissionNo LIKE ?
                ORDER BY review.seq
                """, (rs, rowNum) -> new CreatedReview(
                rs.getLong("seq"),
                rs.getString("status"),
                rs.getString("abstractStatus"),
                rs.getString("submissionNo"),
                rs.getTimestamp("createdAt").toLocalDateTime()
        ), conferenceSeq, conferenceSeq, submissionPrefix + "%");

        List<Object[]> scoreRows = new ArrayList<>();
        for (CreatedReview review : reviews) {
            int index = indexBySubmissionNo.get(review.submissionNo());
            int itemCount = "submitted".equals(review.status())
                    ? evaluationItems.size()
                    : Math.min(2, evaluationItems.size());
            for (int itemIndex = 0; itemIndex < itemCount; itemIndex++) {
                scoreRows.add(new Object[]{
                        review.seq(), evaluationItems.get(itemIndex),
                        score(review.abstractStatus(), index, itemIndex),
                        "submitted".equals(review.status()) ? "테스트 평가 의견" : "작성 중",
                        Timestamp.valueOf(review.createdAt()), Timestamp.valueOf(review.createdAt())
                });
            }
        }
        if (!scoreRows.isEmpty()) {
            jdbcTemplate.batchUpdate("""
                    INSERT INTO abstract_review_scores (
                        reviewSeq, evaluationItemSeq, score, itemComment, createdAt, updatedAt
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """, scoreRows);
        }

        List<Object[]> submissionNumberRows = createdAbstracts.stream()
                .map(createdAbstract -> new Object[]{
                        AbstractSubmissionService.buildSubmissionNo(createdAbstract.seq()),
                        createdAbstract.seq(), conferenceSeq
                })
                .toList();
        jdbcTemplate.batchUpdate("""
                UPDATE abstract_submissions
                SET submissionNo = ?, updatedAt = NOW()
                WHERE seq = ? AND conferenceSeq = ?
                """, submissionNumberRows);

        return new CreationResult(
                "abstracts", batchId, ABSTRACT_COUNT, assignments.size(),
                reviews.size(), completedReviewCount, scoreRows.size(),
                draftCount, submittedCount, underReviewCount, approvedCount, rejectedCount,
                normalDecisionCount, forcedDecisionCount, changedPresentationTypeCount, 0, 0
        );
    }

    @Transactional
    public CreationResult createPopups(Long conferenceSeq) {
        requireConference(conferenceSeq);
        String batchId = newBatchId();
        UploadStorage.StoredTarget blankImage = storePopupBlankImage();
        scheduleRollbackCleanup(blankImage.path());
        String imageUrl = "/api/popups/images/" + blankImage.relativePath().replace('\\', '/');
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<Object[]> rows = new ArrayList<>(POPUP_COUNT);

        for (int index = 1; index <= POPUP_COUNT; index++) {
            LocalDate useStartDate;
            LocalDate useEndDate;
            switch (index % 3) {
                case 1 -> {
                    useStartDate = today.minusDays(index);
                    useEndDate = today.plusDays(30L + index);
                }
                case 2 -> {
                    useStartDate = today.plusDays(index);
                    useEndDate = today.plusDays(30L + index);
                }
                default -> {
                    useStartDate = today.minusDays(60L + index);
                    useEndDate = today.minusDays(1);
                }
            }

            LocalDateTime createdAt = now.minusDays(POPUP_COUNT - index).minusHours(index);
            String content = """
                    <p><img src="%s" alt="테스트 팝업 기본 이미지"></p>
                    <p><strong>테스트 팝업 %02d</strong></p>
                    <p>팝업 노출 기간과 사용 상태를 확인하기 위한 테스트 데이터입니다.</p>
                    """.formatted(imageUrl, index);
            rows.add(new Object[]{
                    conferenceSeq,
                    "[TESTDATA:%s] 테스트 팝업 %02d".formatted(batchId, index),
                    index % 4 == 0 ? "https://example.com" : null,
                    index % 5 != 0,
                    useStartDate,
                    useEndDate,
                    content,
                    null,
                    null,
                    Timestamp.valueOf(createdAt),
                    Timestamp.valueOf(createdAt)
            });
        }

        try {
            jdbcTemplate.batchUpdate("""
                    INSERT INTO popups (
                        conferenceSeq, title, linkUrl, enabled, useStartDate, useEndDate, content,
                        popupImageOriFilename, popupImageSaveFilename, createdAt, updatedAt
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, rows);
        } catch (RuntimeException exception) {
            deleteQuietly(blankImage.path());
            throw exception;
        }

        return new CreationResult("popups", batchId, POPUP_COUNT, 0, 0, 0, 0);
    }

    @Transactional
    public CreationResult createSpeakers(Long conferenceSeq) {
        requireConference(conferenceSeq);
        Map<String, Long> typeSeqByCode = requireSpeakerTypes();
        requireSpeakerCountries();
        String dbEncString = personalDataProperties.requireDbEncString();
        String batchId = newBatchId();
        int createdCount = 0;
        int skippedCount = 0;

        for (SpeakerSample sample : SPEAKER_SAMPLES) {
            String contactEmail = "test.speaker.%s.c%d@example.test".formatted(sample.emailKey(), conferenceSeq);
            Integer existingCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM speakers
                    WHERE conferenceSeq = ?
                      AND CONVERT(AES_DECRYPT(UNHEX(contactEmail), SHA2(?, 512)) USING utf8mb4) = ?
                    """, Integer.class, conferenceSeq, dbEncString, contactEmail);
            if (existingCount != null && existingCount > 0) {
                skippedCount++;
                continue;
            }

            UploadStorage.StoredTarget image = storeSpeakerImage(sample.imageFilename());
            scheduleRollbackCleanup(image.path());
            try {
                jdbcTemplate.update("""
                        INSERT INTO speakers (
                            conferenceSeq, speakerTypeCode, displayName, displayNameKo, affiliation, department,
                            positionTitle, countryCode, biography, profileImageOriFilename, profileImageSaveFilename,
                            homepageUrl, contactEmail, featured, enabled, sortOrder, createdAt, updatedAt
                        ) VALUES (
                            ?, ?, HEX(AES_ENCRYPT(?, SHA2(?, 512))),
                            CASE WHEN ? IS NULL THEN NULL ELSE HEX(AES_ENCRYPT(?, SHA2(?, 512))) END,
                            ?, ?, ?, ?, ?, ?, ?, NULL,
                            HEX(AES_ENCRYPT(?, SHA2(?, 512))), ?, TRUE, ?, NOW(), NOW()
                        )
                        """,
                        conferenceSeq, typeSeqByCode.get(sample.typeCode()), sample.displayName(), dbEncString,
                        sample.displayNameKo(), sample.displayNameKo(), dbEncString,
                        sample.affiliation(), sample.department(), sample.positionTitle(), sample.countryCode(),
                        sample.biography(), sample.imageFilename(), image.relativePath(), contactEmail, dbEncString,
                        sample.featured(), sample.sortOrder());
                createdCount++;
            } catch (RuntimeException exception) {
                deleteQuietly(image.path());
                throw exception;
            }
        }

        return new CreationResult(
                "speakers", batchId, createdCount, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, skippedCount
        );
    }

    private UploadStorage.StoredTarget storeSpeakerImage(String resourceFilename) {
        UploadStorage.StoredTarget storedTarget = uploadStorage.monthlyTarget(
                UploadStorage.SPEAKERS,
                UUID.randomUUID() + ".jpg"
        );
        Resource resource = new ClassPathResource(SPEAKER_IMAGE_RESOURCE_ROOT + resourceFilename);
        try {
            Files.createDirectories(storedTarget.path().getParent());
            try (var inputStream = resource.getInputStream()) {
                Files.copy(inputStream, storedTarget.path());
            }
            return storedTarget;
        } catch (IOException exception) {
            deleteQuietly(storedTarget.path());
            throw new IllegalStateException("초청연자 테스트 이미지를 저장하지 못했습니다.", exception);
        }
    }

    private UploadStorage.StoredTarget storePopupBlankImage() {
        UploadStorage.StoredTarget storedTarget = uploadStorage.monthlyTarget(
                UploadStorage.POPUPS,
                UUID.randomUUID() + ".png"
        );
        try {
            Files.createDirectories(storedTarget.path().getParent());
            try (var inputStream = popupBlankImageResource.getInputStream()) {
                Files.copy(inputStream, storedTarget.path());
            }
            return storedTarget;
        } catch (IOException exception) {
            deleteQuietly(storedTarget.path());
            throw new IllegalStateException("팝업 기본 이미지를 저장하지 못했습니다.", exception);
        }
    }

    private void deleteQuietly(java.nio.file.Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 원래 생성 실패 원인을 유지합니다.
        }
    }

    private List<MemberSeed> requireMembers(Long conferenceSeq, int count) {
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
                ORDER BY CASE WHEN CONVERT(AES_DECRYPT(UNHEX(email), SHA2(?, 512)) USING utf8mb4)
                                   LIKE 'testdata.%@example.test' THEN 0 ELSE 1 END,
                         seq DESC
                LIMIT ?
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
                conferenceSeq, dbEncString, count);
        if (members.size() < count) {
            throw new IllegalStateException("회원 데이터가 %d명 이상 필요합니다. 먼저 회원생성을 실행하세요.".formatted(count));
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
                  AND rate.currency IN ('KRW', 'USD')
                ORDER BY rate.currency, rate.periodType, category.sortOrder, category.seq
                """, (rs, rowNum) -> new RegistrationRate(
                rs.getLong("seq"),
                rs.getString("categoryCode"),
                rs.getString("categoryName"),
                rs.getString("periodType"),
                rs.getString("currency"),
                rs.getBigDecimal("amount")
        ), conferenceSeq);
        if (rates.isEmpty()) {
            throw new IllegalStateException("사용 가능한 사전등록 구분과 등록비가 없습니다.");
        }
        Set<String> currencies = new HashSet<>();
        rates.forEach(rate -> currencies.add(rate.currency()));
        for (String currency : currencies) {
            Set<String> periods = new HashSet<>();
            rates.stream().filter(rate -> currency.equals(rate.currency()))
                    .forEach(rate -> periods.add(rate.periodType()));
            if (!periods.containsAll(Set.of("EARLY_BIRD", "REGULAR"))) {
                throw new IllegalStateException(currency + " 통화의 얼리버드와 일반 등록비가 모두 설정되어 있어야 합니다.");
            }
        }
        return rates;
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

    private void scheduleRollbackCleanup(java.nio.file.Path path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteQuietly(path);
                }
            }
        });
    }

    private Map<String, Long> requireSpeakerTypes() {
        Map<String, Long> result = new HashMap<>();
        jdbcTemplate.query("""
                SELECT child.seq, child.codeEtc1
                FROM common_codes child
                JOIN common_codes root ON root.seq = child.parentSeq
                WHERE root.groupCode = 'SPEAKER_TYPE' AND root.parentSeq = 0
                  AND root.isUsed = 'Y' AND root.isDelete = 'N'
                  AND child.groupCode = 'SPEAKER_TYPE'
                  AND child.isUsed = 'Y' AND child.isDelete = 'N'
                ORDER BY child.sortOrder, child.seq
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                result.put(rs.getString("codeEtc1"), rs.getLong("seq")));
        for (String code : List.of("KEYNOTE", "INVITED", "SPECIAL")) {
            if (!result.containsKey(code)) {
                throw new IllegalStateException("사용 가능한 초청연자 구분 코드가 없습니다: " + code);
            }
        }
        return result;
    }

    private void requireSpeakerCountries() {
        List<String> requiredCodes = SPEAKER_SAMPLES.stream().map(SpeakerSample::countryCode).distinct().toList();
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM countries WHERE isoAlpha2 IN ('KR', 'GB', 'JP', 'SG', 'US')
                """, Integer.class);
        if (count == null || count != requiredCodes.size()) {
            throw new IllegalStateException("초청연자 테스트 데이터에 필요한 국가 코드가 없습니다.");
        }
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
        List<RegistrationOptionSelection> selected = new ArrayList<>(desiredCount);
        for (int offset = 0; offset < options.size() && selected.size() < desiredCount; offset++) {
            RegistrationOptionSeed option = options.get((index + offset - 1) % options.size());
            int remaining = remainingByOption.getOrDefault(option.seq(), option.remainingCapacity());
            if (remaining == 0) {
                continue;
            }
            selected.add(new RegistrationOptionSelection(option.seq(), 1, option.unitPrice()));
            if (consumesCapacity && remaining > 0) {
                remainingByOption.put(option.seq(), remaining - 1);
            }
        }
        return selected;
    }

    private String currencyForMemberType(String memberType) {
        return "domestic".equals(memberType) ? "KRW" : "USD";
    }

    private Map<String, List<RegistrationOptionSeed>> findRegistrationOptions(Long conferenceSeq) {
        List<RegistrationOptionSeed> options = jdbcTemplate.query("""
                SELECT optionRow.seq,
                       currencyRow.currency,
                       CASE currencyRow.currency
                           WHEN 'KRW' THEN optionRow.krwPrice
                           WHEN 'USD' THEN optionRow.usdPrice
                       END AS unitPrice,
                       CASE WHEN optionRow.capacity IS NULL THEN -1
                            ELSE GREATEST(optionRow.capacity - COALESCE(used.quantity, 0), 0)
                       END AS remainingCapacity
                FROM registration_options optionRow
                JOIN (
                    SELECT 'KRW' AS currency
                    UNION ALL SELECT 'USD'
                ) currencyRow
                LEFT JOIN (
                    SELECT item.optionSeq, SUM(item.quantity) AS quantity
                    FROM pre_registration_option_items item
                    JOIN pre_registrations registration ON registration.seq = item.preRegistrationSeq
                    WHERE registration.conferenceSeq = ?
                      AND registration.applicationStatus = 'SUBMITTED'
                    GROUP BY item.optionSeq
                ) used ON used.optionSeq = optionRow.seq
                WHERE optionRow.conferenceSeq = ?
                  AND optionRow.enabled = TRUE
                  AND optionRow.maxPerPerson >= 1
                  AND CASE currencyRow.currency
                          WHEN 'KRW' THEN optionRow.krwPrice
                          WHEN 'USD' THEN optionRow.usdPrice
                      END IS NOT NULL
                ORDER BY optionRow.sortOrder, optionRow.seq, currencyRow.currency
                """, (rs, rowNum) -> new RegistrationOptionSeed(
                rs.getLong("seq"),
                rs.getString("currency"),
                rs.getBigDecimal("unitPrice"),
                rs.getInt("remainingCapacity")
        ), conferenceSeq, conferenceSeq);
        Map<String, List<RegistrationOptionSeed>> optionsByCurrency = new HashMap<>();
        for (RegistrationOptionSeed option : options) {
            optionsByCurrency.computeIfAbsent(option.currency(), ignored -> new ArrayList<>()).add(option);
        }
        return optionsByCurrency;
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
        if (reviewers.isEmpty()) {
            throw new IllegalStateException("활성 심사자가 없습니다. 먼저 심사자 계정을 생성하세요.");
        }
        return reviewers;
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

    private String paymentStatus(int index, boolean cancelled) {
        if (cancelled) {
            return index % 2 == 0 ? "REFUNDED" : "UNPAID";
        }
        return switch (index % 10) {
            case 6, 7 -> "UNPAID";
            case 8 -> "FAILED";
            default -> "PAID";
        };
    }

    static AbstractSamplePlan abstractSamplePlan(int index, List<Long> presentationTypes) {
        if (presentationTypes == null || presentationTypes.isEmpty()) {
            throw new IllegalArgumentException("초록 발표형식이 필요합니다.");
        }
        int series = index % 24;
        String status = switch (series) {
            case 0, 6, 12, 18, 22 -> "approved";
            case 1, 7, 13, 19, 23 -> "rejected";
            case 3, 9, 15 -> "submitted";
            case 4, 10 -> "draft";
            default -> "under_review";
        };
        boolean forcedDecision = series == 22 || series == 23;
        Long presentationTypeCode = presentationTypes.get((index - 1) % presentationTypes.size());
        Long acceptedPresentationTypeCode = null;
        if ("approved".equals(status)) {
            boolean changePresentationType = presentationTypes.size() > 1 && index % 12 == 0;
            acceptedPresentationTypeCode = changePresentationType
                    ? presentationTypes.get(index % presentationTypes.size())
                    : presentationTypeCode;
        }
        String decisionReason = null;
        if (isFinalAbstractStatus(status)) {
            decisionReason = forcedDecision
                    ? "테스트 데이터: 심사 미완료 상태의 강제 %s 표본".formatted("approved".equals(status) ? "승인" : "반려")
                    : "테스트 데이터: 심사 결과에 따른 최종 %s".formatted("approved".equals(status) ? "승인" : "반려");
        }
        return new AbstractSamplePlan(
                status,
                forcedDecision,
                presentationTypeCode,
                acceptedPresentationTypeCode,
                decisionReason
        );
    }

    private static boolean isFinalAbstractStatus(String status) {
        return "approved".equals(status) || "rejected".equals(status);
    }

    static int reviewerCount(AbstractSamplePlan plan, int reviewerSize, Random random) {
        if (reviewerSize < AbstractDecisionService.REQUIRED_REVIEWER_COUNT) {
            throw new IllegalStateException("정상 심사 완료 표본을 만들려면 활성 심사자가 2명 이상 필요합니다.");
        }
        if ("draft".equals(plan.status()) || "submitted".equals(plan.status())) {
            return 0;
        }
        if (plan.forcedDecision()) {
            return 1;
        }
        if (isFinalAbstractStatus(plan.status())) {
            int maximum = Math.min(3, reviewerSize);
            return AbstractDecisionService.REQUIRED_REVIEWER_COUNT
                    + random.nextInt(maximum - AbstractDecisionService.REQUIRED_REVIEWER_COUNT + 1);
        }
        return 1 + random.nextInt(Math.min(3, reviewerSize));
    }

    private String assignmentStatus(AbstractSamplePlan plan, int abstractIndex, int reviewerIndex) {
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

    private String recommendation(String abstractStatus, int index) {
        if ("approved".equals(abstractStatus)) {
            return "accept";
        }
        if ("rejected".equals(abstractStatus)) {
            return "reject";
        }
        String[] recommendations = {"accept", "reject"};
        return recommendations[index % recommendations.length];
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

    private LocalDateTime randomPastDate(LocalDateTime now, Random random, int maxDays) {
        return now.minusDays(random.nextInt(maxDays + 1)).minusMinutes(random.nextInt(24 * 60));
    }

    private LocalDateTime latestAssignmentTime(LocalDateTime... values) {
        LocalDateTime latest = null;
        for (LocalDateTime value : values) {
            if (value != null && (latest == null || value.isAfter(latest))) {
                latest = value;
            }
        }
        return latest;
    }

    private String newBatchId() {
        return LocalDateTime.now().format(BATCH_TIME_FORMAT)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private String defaultCountry(String country) {
        return country == null || country.isBlank() ? "South Korea" : country;
    }

    public record CreationResult(
            String target,
            String batchId,
            int createdCount,
            int assignmentCount,
            int reviewCount,
            int completedReviewCount,
            int scoreCount,
            int draftCount,
            int submittedCount,
            int underReviewCount,
            int approvedCount,
            int rejectedCount,
            int normalDecisionCount,
            int forcedDecisionCount,
            int changedPresentationTypeCount,
            int optionItemCount,
            int skippedCount
    ) {
        public CreationResult(
                String target,
                String batchId,
                int createdCount,
                int assignmentCount,
                int reviewCount,
                int completedReviewCount,
                int scoreCount
        ) {
            this(target, batchId, createdCount, assignmentCount, reviewCount, completedReviewCount, scoreCount,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
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

    private record RegistrationOptionSelection(long optionSeq, int quantity, BigDecimal amount) {
    }

    private record CreatedRegistration(long seq, String registrationNumber) {
    }

    private record SpeakerSample(
            String typeCode,
            String displayName,
            String displayNameKo,
            String affiliation,
            String department,
            String positionTitle,
            String countryCode,
            String biography,
            String emailKey,
            boolean featured,
            int sortOrder,
            String imageFilename
    ) {
    }

    private record CreatedAbstract(long seq, String submissionNo, long memberSeq, LocalDateTime createdAt) {
    }

    private record CreatedAssignment(
            long seq,
            String status,
            LocalDateTime assignedAt,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            String submissionNo,
            String abstractStatus
    ) {
    }

    private record CreatedReview(
            long seq,
            String status,
            String abstractStatus,
            String submissionNo,
            LocalDateTime createdAt
    ) {
    }

    record AbstractSamplePlan(
            String status,
            boolean forcedDecision,
            Long presentationTypeCode,
            Long acceptedPresentationTypeCode,
            String decisionReason
    ) {
    }
}
