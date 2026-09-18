package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.dto.AbstractCategoryResponse;
import com.bjworld21.congress.dto.AbstractAiOptionResponse;
import com.bjworld21.congress.dto.AbstractPresentationTypeResponse;
import com.bjworld21.congress.dto.AbstractSubmissionAiScope;
import com.bjworld21.congress.dto.AbstractSubmissionAiTool;
import com.bjworld21.congress.dto.AbstractSubmissionAuthor;
import com.bjworld21.congress.dto.AbstractSubmissionInstitution;
import com.bjworld21.congress.dto.AbstractSubmissionMetaResponse;
import com.bjworld21.congress.dto.AbstractSubmissionPageResponse;
import com.bjworld21.congress.dto.AbstractSubmissionRequest;
import com.bjworld21.congress.dto.AbstractSubmissionResponse;
import com.bjworld21.congress.dto.MemberAbstractSubmissionResponse;
import com.bjworld21.congress.entity.Member;
import com.bjworld21.congress.entity.AbstractSubmissionAttachment;
import com.bjworld21.congress.repository.AbstractSubmissionRepository;
import com.bjworld21.congress.repository.ConferenceSettingsRepository;
import com.bjworld21.congress.repository.MemberRepository;
import com.bjworld21.congress.util.XlsxWorkbookWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

@Service
public class AbstractSubmissionService {
    private static final String AI_TOOL_GROUP_CODE = "ABSTRACT_AI_TOOLS";
    private static final String AI_SCOPE_GROUP_CODE = "ABSTRACT_AI_SCOPES";
    private static final Set<String> VALID_STATUS = Set.of("draft", "submitted", "under_review", "approved", "rejected");
    private static final DateTimeFormatter EXPORT_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AbstractSubmissionRepository abstractSubmissionRepository;
    private final ConferenceSettingsRepository conferenceSettingsRepository;
    private final MemberRepository memberRepository;
    private final UploadStorage uploadStorage;
    private final PersonalDataProperties personalDataProperties;
    private final Clock clock;
    private final AbstractTitleSimilarityService abstractTitleSimilarityService;

    @Autowired
    public AbstractSubmissionService(
            AbstractSubmissionRepository abstractSubmissionRepository,
            ConferenceSettingsRepository conferenceSettingsRepository,
            MemberRepository memberRepository,
            UploadStorage uploadStorage,
            PersonalDataProperties personalDataProperties,
            Clock clock,
            AbstractTitleSimilarityService abstractTitleSimilarityService
    ) {
        this.abstractSubmissionRepository = abstractSubmissionRepository;
        this.conferenceSettingsRepository = conferenceSettingsRepository;
        this.memberRepository = memberRepository;
        this.uploadStorage = uploadStorage;
        this.personalDataProperties = personalDataProperties;
        this.clock = clock;
        this.abstractTitleSimilarityService = abstractTitleSimilarityService;
    }

    public AbstractSubmissionMetaResponse getMeta() {
        List<AbstractPresentationTypeResponse> presentationTypes = abstractSubmissionRepository.findEnabledPresentationTypes();
        List<AbstractCategoryResponse> categories = abstractSubmissionRepository.findEnabledCategories();
        List<AbstractAiOptionResponse> aiTools = abstractSubmissionRepository.findEnabledAiOptions(AI_TOOL_GROUP_CODE);
        List<AbstractAiOptionResponse> aiScopes = abstractSubmissionRepository.findEnabledAiOptions(AI_SCOPE_GROUP_CODE);
        return AbstractSubmissionMetaResponse.builder()
                .presentationTypes(presentationTypes)
                .categories(categories)
                .aiTools(aiTools)
                .aiScopes(aiScopes)
                .build();
    }

    public AbstractSubmissionPageResponse findPage(Long conferenceSeq, int page, int size, String keyword) {
        return findPage(conferenceSeq, page, size, keyword, null, null, null, "");
    }

    public AbstractSubmissionPageResponse findPage(
            Long conferenceSeq,
            int page,
            int size,
            String keyword,
            Long presentationTypeCode,
            Long acceptedPresentationTypeCode,
            Long categoryCode,
            String status
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String normalizedStatus = normalizeSearchStatus(status);
        long totalCount = abstractSubmissionRepository.countByKeyword(
                conferenceSeq,
                normalizedKeyword, presentationTypeCode, acceptedPresentationTypeCode, categoryCode, normalizedStatus,
                dbEncString()
        );
        int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / safeSize));
        int adjustedPage = Math.min(safePage, totalPages);
        int offset = (adjustedPage - 1) * safeSize;

        List<AbstractSubmissionResponse> items = abstractSubmissionRepository.findPage(
                conferenceSeq,
                normalizedKeyword, presentationTypeCode, acceptedPresentationTypeCode,
                categoryCode, normalizedStatus, safeSize, offset, dbEncString()
        );

        return AbstractSubmissionPageResponse.builder()
                .items(items)
                .page(adjustedPage)
                .size(safeSize)
                .totalCount(totalCount)
                .totalPages(totalPages)
                .summary(abstractSubmissionRepository.summarizeByKeyword(
                        conferenceSeq, normalizedKeyword, presentationTypeCode,
                        acceptedPresentationTypeCode, categoryCode, normalizedStatus, dbEncString()
                ))
                .build();
    }

    public ExcelExportResult exportXlsx(Long conferenceSeq, String keyword) {
        return exportXlsx(conferenceSeq, keyword, null, null, null, "");
    }

    public ExcelExportResult exportXlsx(
            Long conferenceSeq,
            String keyword,
            Long presentationTypeCode,
            Long acceptedPresentationTypeCode,
            Long categoryCode,
            String status
    ) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String normalizedStatus = normalizeSearchStatus(status);
        List<AbstractSubmissionResponse> items = new ArrayList<>();

        int page = 1;
        int totalPages;
        do {
            AbstractSubmissionPageResponse response = findPage(
                    conferenceSeq, page, 100, normalizedKeyword, presentationTypeCode,
                    acceptedPresentationTypeCode, categoryCode, normalizedStatus
            );
            items.addAll(response.getItems());
            totalPages = response.getTotalPages();
            page += 1;
        } while (page <= totalPages);

        List<String> headers = List.of(
                "\uC811\uC218\uBC88\uD638",
                "\uD68C\uC6D0\uBA85",
                "\uD68C\uC6D0\uC774\uBA54\uC77C",
                "\uBC1C\uD45C\uD615\uC2DD",
                "\uCD5C\uC885 \uBC1C\uD45C\uD615\uC2DD",
                "\uBD84\uB958",
                "\uC81C\uBAA9",
                "\uC0C1\uD0DC",
                "\uC8FC\uC694\uC800\uC790",
                "\uC800\uC790\uC218",
                "\uAE30\uAD00\uC218",
                "Objective",
                "Methods",
                "Results",
                "Conclusions",
                "AI \uC0AC\uC6A9",
                "AI \uBC84\uC804/\uBAA8\uB378",
                "AI \uC0AC\uC6A9 \uB3C4\uAD6C",
                "AI \uD65C\uC6A9 \uBC94\uC704",
                "AI \uB370\uC774\uD130 \uBD84\uC11D/\uACB0\uACFC \uD574\uC11D \uC0AC\uC6A9",
                "AI/\uD45C\uC808 \uC815\uCC45 \uD655\uC778",
                "\uC6CC\uB4DC\uCE74\uC6B4\uD2B8",
                "\uC81C\uCD9C\uC77C",
                "\uC2EC\uC0AC\uC77C",
                "\uB4F1\uB85D\uC77C",
                "\uC218\uC815\uC77C"
        );

        List<List<Object>> rows = new ArrayList<>();
        for (AbstractSubmissionResponse item : items) {
            item.setAiTools(abstractSubmissionRepository.findAiToolsByAbstractSeq(item.getSeq()));
            item.setAiScopes(abstractSubmissionRepository.findAiScopesByAbstractSeq(item.getSeq()));
            rows.add(Arrays.asList(
                    emptyIfNull(item.getSubmissionNo()),
                    emptyIfNull(item.getMemberFullName()),
                    emptyIfNull(item.getMemberEmail()),
                    item.getPresentationTypeName() == null ? String.valueOf(item.getPresentationTypeCode()) : item.getPresentationTypeName(),
                    item.getAcceptedPresentationTypeCode() == null
                            ? ""
                            : (item.getAcceptedPresentationTypeName() == null
                                ? String.valueOf(item.getAcceptedPresentationTypeCode())
                                : item.getAcceptedPresentationTypeName()),
                    item.getCategoryName() == null ? String.valueOf(item.getCategoryCode()) : item.getCategoryName(),
                    emptyIfNull(item.getTitle()),
                    emptyIfNull(item.getStatus()),
                    emptyIfNull(item.getMainAuthorName()),
                    item.getAuthorCount() == null ? 0 : item.getAuthorCount(),
                    item.getInstitutionCount() == null ? 0 : item.getInstitutionCount(),
                    emptyIfNull(item.getObjectiveText()),
                    emptyIfNull(item.getMethodsText()),
                    emptyIfNull(item.getResultsText()),
                    emptyIfNull(item.getConclusionsText()),
                    booleanLabel(item.getAiUsage()),
                    emptyIfNull(item.getAiVersionInfo()),
                    formatAiTools(item.getAiTools()),
                    formatAiScopes(item.getAiScopes()),
                    booleanLabel(item.getAiDataAnalysisUsed()),
                    booleanLabel(item.getPlagiarismPolicyConfirmed()),
                    item.getWordCount() == null ? 0 : item.getWordCount(),
                    formatDateTime(item.getSubmittedAt()),
                    formatDateTime(item.getReviewedAt()),
                    formatDateTime(item.getCreatedAt()),
                    formatDateTime(item.getUpdatedAt())
            ));
        }

        byte[] content = XlsxWorkbookWriter.createWorkbook("\uCD08\uB85D \uBAA9\uB85D", headers, rows);
        return new ExcelExportResult(content, rows.size(), "abstracts-" + LocalDate.now() + ".xlsx");
    }

    public AbstractSubmissionResponse getBySeq(Long conferenceSeq, Long seq) {
        AbstractSubmissionResponse response = abstractSubmissionRepository.findBySeq(conferenceSeq, seq, dbEncString());
        if (response == null) {
            throw new IllegalArgumentException("Abstract not found.");
        }
        response.setInstitutions(abstractSubmissionRepository.findInstitutionsByAbstractSeq(seq));
        response.setAuthors(abstractSubmissionRepository.findAuthorsByAbstractSeq(seq, dbEncString()));
        response.setAiTools(abstractSubmissionRepository.findAiToolsByAbstractSeq(seq));
        response.setAiScopes(abstractSubmissionRepository.findAiScopesByAbstractSeq(seq));
        response.setAttachments(abstractSubmissionRepository.findAttachmentsByAbstractSeq(seq));
        return response;
    }

    public List<MemberAbstractSubmissionResponse> findByMember(Long conferenceSeq, Long memberSeq) {
        return abstractSubmissionRepository.findByMemberSeq(conferenceSeq, memberSeq);
    }

    public AbstractSubmissionResponse getByMember(Long conferenceSeq, Long seq, Long memberSeq) {
        AbstractSubmissionResponse response = getBySeq(conferenceSeq, seq);
        if (!"member".equals(response.getSubmissionSource()) || !java.util.Objects.equals(memberSeq, response.getMemberSeq())) {
            throw new IllegalArgumentException("조회할 수 있는 회원 초록을 찾을 수 없습니다.");
        }
        return response;
    }

    public AbstractSubmissionAttachment requireAttachment(Long conferenceSeq, Long abstractSeq, Long attachmentSeq) {
        getBySeq(conferenceSeq, abstractSeq);
        AbstractSubmissionAttachment attachment = abstractSeq == null || attachmentSeq == null
                ? null
                : abstractSubmissionRepository.findAttachment(abstractSeq, attachmentSeq);
        if (attachment == null) {
            throw new IllegalArgumentException("첨부파일을 찾을 수 없습니다.");
        }
        return attachment;
    }

    public Resource getAttachmentResource(AbstractSubmissionAttachment attachment) {
        try {
            Path target = uploadStorage.resolve(UploadStorage.ABSTRACTS, attachment.getSaveFilename());
            if (!Files.isRegularFile(target)) {
                throw new IllegalArgumentException("저장된 첨부파일을 찾을 수 없습니다.");
            }
            return new UrlResource(target.toUri());
        } catch (MalformedURLException exception) {
            throw new IllegalStateException("첨부파일을 읽을 수 없습니다.", exception);
        }
    }

    @Transactional
    public AbstractSubmissionResponse create(Long conferenceSeq, AbstractSubmissionRequest request) {
        return createByAdmin(conferenceSeq, request, null);
    }

    @Transactional
    public AbstractSubmissionResponse createByAdmin(Long conferenceSeq, AbstractSubmissionRequest request, Long adminSeq) {
        Member member = resolveMember(conferenceSeq, request);
        request.setConferenceSeq(conferenceSeq);
        request.setMemberSeq(member.getSeq());
        request.setSubmissionSource("admin");
        request.setCreatedByAdminSeq(adminSeq);
        validateInitialStatus(request);
        return persistCreate(conferenceSeq, request);
    }

    @Transactional
    public AbstractSubmissionResponse createByMember(Long conferenceSeq, AbstractSubmissionRequest request, Long memberSeq) {
        Member member = memberSeq == null ? null : memberRepository.findBySeq(conferenceSeq, memberSeq, dbEncString());
        if (member == null) {
            throw new IllegalArgumentException("회원 정보를 찾을 수 없습니다.");
        }
        requireMemberSubmissionPeriod(conferenceSeq);
        request.setMemberSeq(member.getSeq());
        request.setConferenceSeq(conferenceSeq);
        request.setMemberId(member.getEmail());
        request.setSubmissionSource("member");
        request.setCreatedByAdminSeq(null);
        validateInitialStatus(request);
        return persistCreate(conferenceSeq, request);
    }

    private AbstractSubmissionResponse persistCreate(Long conferenceSeq, AbstractSubmissionRequest request) {
        validate(request);

        request.setSeq(null);
        request.setSubmissionNo(generateTemporarySubmissionNo());
        request.setWordCount(calculateWordCount(request));
        applySubmissionDefaults(request, true);

        abstractSubmissionRepository.insert(request);
        String finalSubmissionNo = buildSubmissionNo(request.getSeq());
        abstractSubmissionRepository.updateSubmissionNo(conferenceSeq, request.getSeq(), finalSubmissionNo);
        request.setSubmissionNo(finalSubmissionNo);

        saveChildren(request);
        refreshTitleSimilarityIfSubmitted(conferenceSeq, request.getSeq(), request.getStatus());
        return getBySeq(conferenceSeq, request.getSeq());
    }

    private void validateInitialStatus(AbstractSubmissionRequest request) {
        if (request.getStatus() != null
                && !request.getStatus().isBlank()
                && !Set.of("draft", "submitted").contains(request.getStatus())) {
            throw new IllegalArgumentException("초록 등록 시 상태는 임시저장 또는 제출완료만 선택할 수 있습니다.");
        }
    }

    @Transactional
    public AbstractSubmissionResponse update(Long conferenceSeq, Long seq, AbstractSubmissionRequest request) {
        AbstractSubmissionResponse existing = abstractSubmissionRepository.findBySeq(conferenceSeq, seq, dbEncString());
        if (existing == null) {
            throw new IllegalArgumentException("Abstract not found.");
        }
        if ("member".equals(existing.getSubmissionSource())) {
            throw new IllegalArgumentException("회원이 접수한 초록 내용은 관리자가 수정할 수 없습니다.");
        }

        Member member = resolveMember(conferenceSeq, request);
        request.setMemberSeq(member.getSeq());
        validate(request);

        request.setSeq(seq);
        request.setConferenceSeq(conferenceSeq);
        request.setSubmissionNo(existing.getSubmissionNo());
        request.setSubmittedAt(existing.getSubmittedAt());
        request.setReviewedAt(existing.getReviewedAt());
        request.setStatus(existing.getStatus());
        request.setWordCount(calculateWordCount(request));
        applySubmissionDefaults(request, false);

        abstractSubmissionRepository.update(request);
        abstractSubmissionRepository.deleteInstitutions(seq);
        abstractSubmissionRepository.deleteAuthors(seq);
        abstractSubmissionRepository.deleteAiTools(seq);
        abstractSubmissionRepository.deleteAiScopes(seq);
        saveChildren(request);
        refreshTitleSimilarityIfSubmitted(conferenceSeq, seq, request.getStatus());
        return getBySeq(conferenceSeq, seq);
    }

    @Transactional
    public AbstractSubmissionResponse updateByMember(Long conferenceSeq, Long seq, Long memberSeq, AbstractSubmissionRequest request) {
        AbstractSubmissionResponse existing = abstractSubmissionRepository.findBySeq(conferenceSeq, seq, dbEncString());
        if (existing == null || !"member".equals(existing.getSubmissionSource())
                || memberSeq == null || !memberSeq.equals(existing.getMemberSeq())) {
            throw new IllegalArgumentException("수정할 수 있는 회원 초록을 찾을 수 없습니다.");
        }
        if (!Set.of("draft", "submitted").contains(existing.getStatus())) {
            throw new IllegalArgumentException("심사가 시작된 초록은 수정할 수 없습니다.");
        }
        if (request.getStatus() != null && !Set.of("draft", "submitted").contains(request.getStatus())) {
            throw new IllegalArgumentException("회원 초록은 임시저장 또는 제출완료 상태로만 저장할 수 있습니다.");
        }
        requireMemberSubmissionPeriod(conferenceSeq);

        request.setMemberSeq(existing.getMemberSeq());
        request.setMemberId(existing.getMemberEmail());
        request.setSeq(seq);
        request.setConferenceSeq(conferenceSeq);
        request.setSubmissionNo(existing.getSubmissionNo());
        request.setStatus("draft".equals(existing.getStatus()) && "submitted".equals(request.getStatus())
                ? "submitted" : existing.getStatus());
        request.setSubmittedAt(existing.getSubmittedAt());
        request.setReviewedAt(existing.getReviewedAt());
        request.setWordCount(calculateWordCount(request));
        validate(request);
        applySubmissionDefaults(request, false);

        abstractSubmissionRepository.update(request);
        abstractSubmissionRepository.deleteInstitutions(seq);
        abstractSubmissionRepository.deleteAuthors(seq);
        abstractSubmissionRepository.deleteAiTools(seq);
        abstractSubmissionRepository.deleteAiScopes(seq);
        saveChildren(request);
        refreshTitleSimilarityIfSubmitted(conferenceSeq, seq, request.getStatus());
        return getBySeq(conferenceSeq, seq);
    }

    private void refreshTitleSimilarityIfSubmitted(Long conferenceSeq, Long abstractSeq, String status) {
        if (!"draft".equals(status)) {
            abstractTitleSimilarityService.refreshAfterSave(conferenceSeq, abstractSeq);
        }
    }

    private void requireMemberSubmissionPeriod(Long conferenceSeq) {
        // User dates are inclusive in Seoul time; administrator create/update intentionally bypass this guard.
        var conference = conferenceSettingsRepository.findBySeq(conferenceSeq);
        LocalDate today = LocalDate.now(clock);
        if (conference == null
                || (conference.getAbstractStartDate() == null && conference.getAbstractEndDate() == null)
                || (conference.getAbstractStartDate() != null && today.isBefore(conference.getAbstractStartDate()))
                || (conference.getAbstractEndDate() != null && today.isAfter(conference.getAbstractEndDate()))) {
            throw new IllegalStateException("Abstract submission is currently closed.");
        }
    }

    @Transactional
    public void deleteDraftByMember(Long conferenceSeq, Long seq, Long memberSeq) {
        requireMemberSubmissionPeriod(conferenceSeq);
        // Public users may permanently remove only their own draft. Submitted/reviewed rows stay as records.
        String status = memberSeq == null ? null
                : abstractSubmissionRepository.findMemberStatusForUpdate(conferenceSeq, seq, memberSeq);
        if (status == null) {
            throw new IllegalArgumentException("삭제할 수 있는 회원 초록을 찾을 수 없습니다.");
        }
        if (!"draft".equals(status)) {
            throw new IllegalStateException("Only draft abstracts can be deleted.");
        }

        abstractSubmissionRepository.deleteInstitutions(seq);
        abstractSubmissionRepository.deleteAuthors(seq);
        abstractSubmissionRepository.deleteAiTools(seq);
        abstractSubmissionRepository.deleteAiScopes(seq);
        abstractSubmissionRepository.delete(conferenceSeq, seq);
    }

    @Transactional
    public void delete(Long conferenceSeq, Long seq) {
        AbstractSubmissionResponse existing = abstractSubmissionRepository.findBySeq(conferenceSeq, seq, dbEncString());
        if (existing == null) {
            throw new IllegalArgumentException("Abstract not found.");
        }
        if ("member".equals(existing.getSubmissionSource())) {
            throw new IllegalArgumentException("회원이 접수한 초록은 관리자가 삭제할 수 없습니다.");
        }

        abstractSubmissionRepository.deleteInstitutions(seq);
        abstractSubmissionRepository.deleteAuthors(seq);
        abstractSubmissionRepository.deleteAiTools(seq);
        abstractSubmissionRepository.deleteAiScopes(seq);
        abstractSubmissionRepository.delete(conferenceSeq, seq);
    }

    private void saveChildren(AbstractSubmissionRequest request) {
        List<AbstractSubmissionInstitution> institutions = request.getInstitutions() == null ? List.of() : request.getInstitutions();
        List<AbstractSubmissionAuthor> authors = request.getAuthors() == null ? List.of() : request.getAuthors();
        List<AbstractSubmissionAiTool> aiTools = request.getAiTools() == null ? List.of() : request.getAiTools();
        List<AbstractSubmissionAiScope> aiScopes = request.getAiScopes() == null ? List.of() : request.getAiScopes();

        for (AbstractSubmissionInstitution institution : institutions) {
            institution.setSeq(null);
            institution.setAbstractSeq(request.getSeq());
            abstractSubmissionRepository.insertInstitution(institution);
        }

        for (AbstractSubmissionAuthor author : authors) {
            author.setSeq(null);
            author.setAbstractSeq(request.getSeq());
            abstractSubmissionRepository.insertAuthor(author, dbEncString());
        }

        for (AbstractSubmissionAiTool aiTool : aiTools) {
            aiTool.setSeq(null);
            aiTool.setAbstractSeq(request.getSeq());
            abstractSubmissionRepository.insertAiTool(aiTool);
        }

        for (AbstractSubmissionAiScope aiScope : aiScopes) {
            aiScope.setSeq(null);
            aiScope.setAbstractSeq(request.getSeq());
            abstractSubmissionRepository.insertAiScope(aiScope);
        }
    }

    private String normalizeSearchStatus(String status) {
        String normalized = status == null ? "" : status.trim();
        if (!normalized.isEmpty() && !VALID_STATUS.contains(normalized)) {
            throw new IllegalArgumentException("Invalid abstract status.");
        }
        return normalized;
    }

    private void validate(AbstractSubmissionRequest request) {
        if (request.getMemberSeq() == null) {
            throw new IllegalArgumentException("Member id is required.");
        }
        if (request.getPresentationTypeCode() == null) {
            throw new IllegalArgumentException("Presentation type is required.");
        }
        if (abstractSubmissionRepository.countEnabledPresentationTypeByCode(request.getPresentationTypeCode()) == 0) {
            throw new IllegalArgumentException("Invalid abstract presentation type.");
        }
        if (request.getCategoryCode() == null) {
            throw new IllegalArgumentException("Category is required.");
        }
        if (abstractSubmissionRepository.countEnabledCategoryByCode(request.getCategoryCode()) == 0) {
            throw new IllegalArgumentException("Invalid abstract category.");
        }
        if (isBlank(request.getTitle())) {
            throw new IllegalArgumentException("Title is required.");
        }
        if (request.getTitle().trim().length() > 500) {
            throw new IllegalArgumentException("Title must be 500 characters or less.");
        }
        validateEnglishText(request.getTitle(), "Title");

        validateTextLength(request.getObjectiveText(), "Objective");
        validateTextLength(request.getMethodsText(), "Methods");
        validateTextLength(request.getResultsText(), "Results");
        validateTextLength(request.getConclusionsText(), "Conclusions");
        validateEnglishText(request.getObjectiveText(), "Objective");
        validateEnglishText(request.getMethodsText(), "Methods");
        validateEnglishText(request.getResultsText(), "Results");
        validateEnglishText(request.getConclusionsText(), "Conclusions");
        validateTextLength(request.getAiVersionInfo(), "AI version info");

        validateAiDisclosure(request);

        if (request.getStatus() != null && !VALID_STATUS.contains(request.getStatus())) {
            throw new IllegalArgumentException("Invalid abstract status.");
        }

        List<AbstractSubmissionInstitution> institutions = request.getInstitutions() == null ? List.of() : request.getInstitutions();
        if (institutions.isEmpty()) {
            throw new IllegalArgumentException("At least one institution is required.");
        }

        List<AbstractSubmissionAuthor> authors = request.getAuthors() == null ? List.of() : request.getAuthors();
        if (authors.isEmpty()) {
            throw new IllegalArgumentException("At least one author is required.");
        }

        boolean hasPresentingAuthor = false;
        for (AbstractSubmissionInstitution institution : institutions) {
            if (institution.getInstitutionNo() == null) {
                throw new IllegalArgumentException("Institution number is required.");
            }
            if (institution.getInstitutionNo() < 1) {
                throw new IllegalArgumentException("Institution number must be greater than zero.");
            }
            if (isBlank(institution.getCountry())) {
                throw new IllegalArgumentException("Institution country is required.");
            }
            if (isBlank(institution.getInstitutionName())) {
                throw new IllegalArgumentException("Institution name is required.");
            }
            validateTextLength(institution.getDepartment(), "Institution department");
        }

        for (AbstractSubmissionAuthor author : authors) {
            if (author.getAuthorOrder() == null) {
                throw new IllegalArgumentException("Author order is required.");
            }
            if (author.getAuthorOrder() < 1) {
                throw new IllegalArgumentException("Author order must be greater than zero.");
            }
            if (isBlank(author.getAuthorName())) {
                throw new IllegalArgumentException("Author name is required.");
            }
            if (author.getInstitutionNo() == null) {
                throw new IllegalArgumentException("Author institution number is required.");
            }
            if (Boolean.TRUE.equals(author.getIsPresentingAuthor())) {
                hasPresentingAuthor = true;
            }
            validateTextLength(author.getEmail(), "Author email");
            validateTextLength(author.getCountry(), "Author country");
            validateTextLength(author.getOfficeCountryCode(), "Office country code");
            validateTextLength(author.getOfficePhoneNumber(), "Office phone number");
            validateTextLength(author.getMobileCountryCode(), "Mobile country code");
            validateTextLength(author.getMobilePhoneNumber(), "Mobile phone number");
        }

        if (!hasPresentingAuthor) {
            throw new IllegalArgumentException("At least one presenting author is required.");
        }

        List<Integer> institutionNumbers = institutions.stream()
                .map(AbstractSubmissionInstitution::getInstitutionNo)
                .distinct()
                .toList();

        for (AbstractSubmissionAuthor author : authors) {
            if (!institutionNumbers.contains(author.getInstitutionNo())) {
                throw new IllegalArgumentException("Author institution number must match an institution entry.");
            }
        }
    }

    private Member resolveMember(Long conferenceSeq, AbstractSubmissionRequest request) {
        if (request == null || isBlank(request.getMemberId())) {
            throw new IllegalArgumentException("Member id is required.");
        }

        String normalizedMemberId = request.getMemberId().trim().toLowerCase(Locale.ROOT);
        Member member = memberRepository.findByEmail(conferenceSeq, normalizedMemberId, dbEncString());
        if (member == null) {
            throw new IllegalArgumentException("Member not found.");
        }
        request.setMemberId(normalizedMemberId);
        return member;
    }

    private void applySubmissionDefaults(AbstractSubmissionRequest request, boolean isCreate) {
        request.setAiUsage(Boolean.TRUE.equals(request.getAiUsage()));
        request.setAiDataAnalysisUsed(Boolean.TRUE.equals(request.getAiDataAnalysisUsed()));
        request.setPlagiarismPolicyConfirmed(Boolean.TRUE.equals(request.getPlagiarismPolicyConfirmed()));
        request.setStatus(request.getStatus() == null || request.getStatus().isBlank() ? "draft" : request.getStatus());

        if (!Boolean.TRUE.equals(request.getAiUsage())) {
            request.setAiVersionInfo(null);
            request.setAiDataAnalysisUsed(false);
            request.setAiTools(List.of());
            request.setAiScopes(List.of());
        }

        if (isCreate) {
            request.setSubmittedAt("submitted".equals(request.getStatus()) ? LocalDateTime.now() : null);
            request.setReviewedAt(null);
        } else {
            if ("submitted".equals(request.getStatus()) && request.getSubmittedAt() == null) {
                request.setSubmittedAt(LocalDateTime.now());
            }
            if (("approved".equals(request.getStatus()) || "rejected".equals(request.getStatus())) && request.getReviewedAt() == null) {
                request.setReviewedAt(LocalDateTime.now());
            }
        }
    }

    private Integer calculateWordCount(AbstractSubmissionRequest request) {
        StringBuilder builder = new StringBuilder();
        appendIfPresent(builder, request.getObjectiveText());
        appendIfPresent(builder, request.getMethodsText());
        appendIfPresent(builder, request.getResultsText());
        appendIfPresent(builder, request.getConclusionsText());

        String text = builder.toString().trim();
        if (text.isEmpty()) {
            return 0;
        }

        return (int) java.util.Arrays.stream(text.split("\\s+"))
                .filter(token -> !token.isBlank())
                .count();
    }

    private void appendIfPresent(StringBuilder builder, String text) {
        if (text != null && !text.isBlank()) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(text.trim());
        }
    }

    private void validateTextLength(String value, String fieldName) {
        if (value != null && value.length() > 1000) {
            throw new IllegalArgumentException(fieldName + " must be 1000 characters or less.");
        }
    }

    private String dbEncString() {
        return personalDataProperties.requireDbEncString();
    }

    private void validateEnglishText(String value, String fieldName) {
        if (value == null) {
            return;
        }
        boolean containsInvalidCharacter = value.codePoints().anyMatch(codePoint ->
                codePoint != '\n'
                        && codePoint != '\r'
                        && codePoint != '\t'
                        && (codePoint < 0x20 || codePoint > 0x7e)
        );
        if (containsInvalidCharacter) {
            throw new IllegalArgumentException(fieldName + " must contain English characters only.");
        }
    }

    private void validateAiDisclosure(AbstractSubmissionRequest request) {
        if (request.getAiUsage() == null) {
            throw new IllegalArgumentException("AI usage selection is required.");
        }
        if (!Boolean.TRUE.equals(request.getPlagiarismPolicyConfirmed())) {
            throw new IllegalArgumentException("AI accuracy, plagiarism, and policy compliance confirmation is required.");
        }

        List<AbstractSubmissionAiTool> aiTools = request.getAiTools() == null ? List.of() : request.getAiTools();
        List<AbstractSubmissionAiScope> aiScopes = request.getAiScopes() == null ? List.of() : request.getAiScopes();

        if (!Boolean.TRUE.equals(request.getAiUsage())) {
            return;
        }
        if (isBlank(request.getAiVersionInfo())) {
            throw new IllegalArgumentException("AI version information is required when AI was used.");
        }
        if (aiTools.isEmpty()) {
            throw new IllegalArgumentException("At least one AI tool is required when AI was used.");
        }
        if (aiScopes.isEmpty()) {
            throw new IllegalArgumentException("At least one AI usage scope is required when AI was used.");
        }

        Set<Long> toolCodes = new HashSet<>();
        for (AbstractSubmissionAiTool aiTool : aiTools) {
            if (aiTool.getAiToolCode() == null
                    || abstractSubmissionRepository.countEnabledAiOption(AI_TOOL_GROUP_CODE, aiTool.getAiToolCode()) == 0) {
                throw new IllegalArgumentException("Invalid AI tool.");
            }
            if (!toolCodes.add(aiTool.getAiToolCode())) {
                throw new IllegalArgumentException("AI tools cannot be duplicated.");
            }

            boolean isEtc = abstractSubmissionRepository.countEnabledEtcAiOption(
                    AI_TOOL_GROUP_CODE,
                    aiTool.getAiToolCode()
            ) > 0;
            if (isEtc) {
                if (isBlank(aiTool.getOtherToolName()) || isBlank(aiTool.getOtherProviderName())) {
                    throw new IllegalArgumentException("Other AI tool name and provider are required.");
                }
                validateMaxLength(aiTool.getOtherToolName(), "Other AI tool name", 255);
                validateMaxLength(aiTool.getOtherProviderName(), "Other AI tool provider", 255);
            } else {
                aiTool.setOtherToolName(null);
                aiTool.setOtherProviderName(null);
            }
        }

        Set<Long> scopeCodes = new HashSet<>();
        for (AbstractSubmissionAiScope aiScope : aiScopes) {
            if (aiScope.getAiScopeCode() == null
                    || abstractSubmissionRepository.countEnabledAiOption(AI_SCOPE_GROUP_CODE, aiScope.getAiScopeCode()) == 0) {
                throw new IllegalArgumentException("Invalid AI usage scope.");
            }
            if (!scopeCodes.add(aiScope.getAiScopeCode())) {
                throw new IllegalArgumentException("AI usage scopes cannot be duplicated.");
            }

            boolean isEtc = abstractSubmissionRepository.countEnabledEtcAiOption(
                    AI_SCOPE_GROUP_CODE,
                    aiScope.getAiScopeCode()
            ) > 0;
            if (isEtc) {
                if (isBlank(aiScope.getOtherScopeText())) {
                    throw new IllegalArgumentException("Other AI usage scope is required.");
                }
                validateMaxLength(aiScope.getOtherScopeText(), "Other AI usage scope", 500);
            } else {
                aiScope.setOtherScopeText(null);
            }
        }

        if (Boolean.TRUE.equals(request.getAiDataAnalysisUsed()) && isBlank(request.getMethodsText())) {
            throw new IllegalArgumentException("Methods are required when AI was used for data analysis or result interpretation.");
        }
    }

    private void validateMaxLength(String value, String fieldName, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be " + maxLength + " characters or less.");
        }
    }

    private String formatAiTools(List<AbstractSubmissionAiTool> aiTools) {
        if (aiTools == null || aiTools.isEmpty()) {
            return "";
        }
        return aiTools.stream()
                .map(aiTool -> "Y".equals(aiTool.getIsEtc())
                        ? aiTool.getOtherToolName() + " (" + aiTool.getOtherProviderName() + ")"
                        : emptyIfNull(aiTool.getAiToolName()))
                .filter(value -> !value.isBlank())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private String formatAiScopes(List<AbstractSubmissionAiScope> aiScopes) {
        if (aiScopes == null || aiScopes.isEmpty()) {
            return "";
        }
        return aiScopes.stream()
                .map(aiScope -> "Y".equals(aiScope.getIsEtc())
                        ? emptyIfNull(aiScope.getOtherScopeText())
                        : emptyIfNull(aiScope.getAiScopeName()))
                .filter(value -> !value.isBlank())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private String booleanLabel(Boolean value) {
        return Boolean.TRUE.equals(value) ? "\uC608" : "\uC544\uB2C8\uC624";
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.format(EXPORT_DATE_TIME_FORMAT);
    }

    private String generateTemporarySubmissionNo() {
        return "TMP-" + System.currentTimeMillis();
    }

    static String buildSubmissionNo(Long seq) {
        return "APDRC8-Abstract-" + String.format("%04d", seq);
    }
}
