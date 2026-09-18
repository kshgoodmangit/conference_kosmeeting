package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PersonalDataProperties;

import com.bjworld21.congress.dto.MemberListResponse;
import com.bjworld21.congress.dto.MemberDetailResponse;
import com.bjworld21.congress.dto.MemberPageResponse;
import com.bjworld21.congress.dto.MemberRegisterRequest;
import com.bjworld21.congress.dto.MemberRegisterResponse;
import com.bjworld21.congress.entity.Member;
import com.bjworld21.congress.repository.MemberRepository;
import com.bjworld21.congress.repository.AbstractSubmissionRepository;
import com.bjworld21.congress.repository.PreRegistrationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class MemberService {
    private static final String[] DUMMY_COUNTRIES = {
            "United States", "United Kingdom", "Canada", "Australia", "Germany",
            "France", "Japan", "China", "India", "South Korea"
    };
    
    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PreRegistrationRepository preRegistrationRepository;

    @Autowired
    private AbstractSubmissionRepository abstractSubmissionRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PersonalDataProperties personalDataProperties;
    
    public MemberRegisterResponse register(Long conferenceSeq, MemberRegisterRequest request) {
        // 1. Validate request
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        
        if (request.getPassword() == null || request.getPassword().length() < 8 || request.getPassword().length() > 16) {
            throw new IllegalArgumentException("Password must be 8-16 characters");
        }
        
        if (request.getFirstName() == null || request.getFirstName().trim().isEmpty()) {
            throw new IllegalArgumentException("First name is required");
        }

        if (request.getLastName() == null || request.getLastName().trim().isEmpty()) {
            throw new IllegalArgumentException("Last name is required");
        }

        if (request.getInstitution() == null || request.getInstitution().trim().isEmpty()) {
            throw new IllegalArgumentException("Institution is required");
        }

        if (request.getMobile() == null || request.getMobile().trim().isEmpty()) {
            throw new IllegalArgumentException("Mobile is required");
        }
        validatePersonalDataLengths(request);
        
        // 2. Check if email already exists
        String normalizedEmail = normalizeEmail(request.getEmail());
        Member existingMember = memberRepository.findByEmail(conferenceSeq, normalizedEmail, dbEncString());
        if (existingMember != null) {
            throw new IllegalArgumentException("Email already registered");
        }
        
        // 3. Validate member type specific fields
        if ("international".equals(request.getMemberType())) {
            if (request.getCountry() == null || request.getCountry().isEmpty()) {
                throw new IllegalArgumentException("Country is required for international members");
            }
        } else if (!"domestic".equals(request.getMemberType())) {
            throw new IllegalArgumentException("Invalid member type");
        }
        
        // 4. Create and save member
        Member member = Member.builder()
                .conferenceSeq(conferenceSeq)
                .memberType(request.getMemberType())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .institution(request.getInstitution().trim())
                .department(trimToNull(request.getDepartment()))
                .positionTitle(trimToNull(request.getPositionTitle()))
                .country(request.getCountry())
                .mobile(request.getMobile().trim())
                .newsletter(request.getNewsletter() != null ? request.getNewsletter() : false)
                .build();
        
        memberRepository.insert(conferenceSeq, member, dbEncString());
        
        // 5. Return response
        return MemberRegisterResponse.builder()
                .seq(member.getSeq())
                .email(member.getEmail())
                .firstName(member.getFirstName())
                .lastName(member.getLastName())
                .memberType(member.getMemberType())
                .message("Member registered successfully")
                .build();
    }

    public MemberRegisterResponse update(Long conferenceSeq, Long seq, MemberRegisterRequest request) {
        Member member = memberRepository.findBySeq(conferenceSeq, seq, dbEncString());
        if (member == null) {
            throw new IllegalArgumentException("Member not found");
        }

        validateMemberRequest(request, false);

        String normalizedEmail = normalizeEmail(request.getEmail());
        Member existingMember = memberRepository.findByEmailExceptSeq(conferenceSeq, normalizedEmail, seq, dbEncString());
        if (existingMember != null) {
            throw new IllegalArgumentException("Email already registered");
        }

        member.setMemberType(request.getMemberType());
        member.setEmail(normalizedEmail);
        member.setFirstName(request.getFirstName().trim());
        member.setLastName(request.getLastName().trim());
        member.setInstitution(request.getInstitution().trim());
        member.setDepartment(trimToNull(request.getDepartment()));
        member.setPositionTitle(trimToNull(request.getPositionTitle()));
        member.setCountry("international".equals(request.getMemberType()) ? request.getCountry() : null);
        member.setMobile(request.getMobile().trim());
        member.setNewsletter(request.getNewsletter() != null ? request.getNewsletter() : false);

        memberRepository.update(conferenceSeq, member, dbEncString());

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            member.setPassword(passwordEncoder.encode(request.getPassword()));
            memberRepository.updatePassword(conferenceSeq, member);
        }

        return MemberRegisterResponse.builder()
                .seq(member.getSeq())
                .email(member.getEmail())
                .firstName(member.getFirstName())
                .lastName(member.getLastName())
                .memberType(member.getMemberType())
                .message("Member updated successfully")
                .build();
    }

    public void delete(Long conferenceSeq, Long seq) {
        Member member = memberRepository.findBySeq(conferenceSeq, seq, dbEncString());
        if (member == null) {
            throw new IllegalArgumentException("Member not found");
        }

        memberRepository.delete(conferenceSeq, seq);
    }

    private void validateMemberRequest(MemberRegisterRequest request, boolean passwordRequired) {
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }

        if (passwordRequired && (request.getPassword() == null || request.getPassword().length() < 8 || request.getPassword().length() > 16)) {
            throw new IllegalArgumentException("Password must be 8-16 characters");
        }

        if (!passwordRequired && request.getPassword() != null && !request.getPassword().isBlank() && (request.getPassword().length() < 8 || request.getPassword().length() > 16)) {
            throw new IllegalArgumentException("Password must be 8-16 characters");
        }

        if (request.getFirstName() == null || request.getFirstName().trim().isEmpty()) {
            throw new IllegalArgumentException("First name is required");
        }

        if (request.getLastName() == null || request.getLastName().trim().isEmpty()) {
            throw new IllegalArgumentException("Last name is required");
        }

        if (request.getInstitution() == null || request.getInstitution().trim().isEmpty()) {
            throw new IllegalArgumentException("Institution is required");
        }

        if (request.getMobile() == null || request.getMobile().trim().isEmpty()) {
            throw new IllegalArgumentException("Mobile is required");
        }
        validatePersonalDataLengths(request);

        if ("international".equals(request.getMemberType())) {
            if (request.getCountry() == null || request.getCountry().isEmpty()) {
                throw new IllegalArgumentException("Country is required for international members");
            }
        } else if (!"domestic".equals(request.getMemberType())) {
            throw new IllegalArgumentException("Invalid member type");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeEmail(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String dbEncString() {
        return personalDataProperties.requireDbEncString();
    }

    private void validatePersonalDataLengths(MemberRegisterRequest request) {
        validateUtf8Length(request.getEmail().trim(), 255, "Email");
        validateUtf8Length(request.getFirstName().trim(), 400, "First name");
        validateUtf8Length(request.getLastName().trim(), 400, "Last name");
        validateUtf8Length(request.getMobile().trim(), 120, "Mobile");
    }

    private void validateUtf8Length(String value, int maxBytes, String label) {
        if (value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException(label + " is too long");
        }
    }

    public MemberPageResponse findPage(
            Long conferenceSeq,
            int page,
            int size,
            String keyword,
            String memberType,
            Boolean hasPreRegistration,
            Boolean hasAbstractSubmission
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String normalizedMemberType = normalizeMemberType(memberType);
        long totalCount = memberRepository.countByFilters(
                conferenceSeq,
                normalizedKeyword,
                normalizedMemberType,
                hasPreRegistration,
                hasAbstractSubmission,
                dbEncString()
        );
        int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / safeSize));
        int adjustedPage = Math.min(safePage, totalPages);
        int offset = (adjustedPage - 1) * safeSize;

        List<MemberListResponse> items = memberRepository.findPage(
                        conferenceSeq,
                        normalizedKeyword,
                        normalizedMemberType,
                        hasPreRegistration,
                        hasAbstractSubmission,
                        safeSize,
                        offset,
                        dbEncString()
                ).stream()
                .map(this::toListResponse)
                .toList();

        long internationalCount = "domestic".equals(normalizedMemberType)
                ? 0
                : memberRepository.countByFilters(conferenceSeq, normalizedKeyword, "international", hasPreRegistration, hasAbstractSubmission, dbEncString());
        long domesticCount = "international".equals(normalizedMemberType)
                ? 0
                : memberRepository.countByFilters(conferenceSeq, normalizedKeyword, "domestic", hasPreRegistration, hasAbstractSubmission, dbEncString());
        long preRegistrationCount = Boolean.FALSE.equals(hasPreRegistration)
                ? 0
                : memberRepository.countByFilters(conferenceSeq, normalizedKeyword, normalizedMemberType, true, hasAbstractSubmission, dbEncString());
        long abstractSubmissionCount = Boolean.FALSE.equals(hasAbstractSubmission)
                ? 0
                : memberRepository.countByFilters(conferenceSeq, normalizedKeyword, normalizedMemberType, hasPreRegistration, true, dbEncString());

        return MemberPageResponse.builder()
                .items(items)
                .page(adjustedPage)
                .size(safeSize)
                .totalCount(totalCount)
                .internationalCount(internationalCount)
                .domesticCount(domesticCount)
                .preRegistrationCount(preRegistrationCount)
                .abstractSubmissionCount(abstractSubmissionCount)
                .totalPages(totalPages)
                .build();
    }

    public MemberDetailResponse findDetail(Long conferenceSeq, Long seq) {
        Member member = memberRepository.findBySeq(conferenceSeq, seq, dbEncString());
        if (member == null) {
            throw new IllegalArgumentException("Member not found");
        }

        return MemberDetailResponse.builder()
                .member(toListResponse(member))
                .preRegistrations(preRegistrationRepository.findByMemberSeq(conferenceSeq, seq))
                .abstractSubmissions(abstractSubmissionRepository.findByMemberSeq(conferenceSeq, seq))
                .build();
    }

    public int createDummyMembers(Long conferenceSeq, int count) {
        if (count < 1 || count > 500) {
            throw new IllegalArgumentException("Dummy member count must be between 1 and 500");
        }

        int insertedCount = 0;
        long nextSeedNumber = memberRepository.count(conferenceSeq) + 1;
        String encodedPassword = passwordEncoder.encode("Password123!");

        while (insertedCount < count) {
            String email = "dummy.member%06d@example.com".formatted(nextSeedNumber);
            nextSeedNumber++;

            if (memberRepository.findByEmail(conferenceSeq, email, dbEncString()) != null) {
                continue;
            }

            boolean international = nextSeedNumber % 2 == 0;
            Member member = Member.builder()
                    .conferenceSeq(conferenceSeq)
                    .memberType(international ? "international" : "domestic")
                    .email(email)
                    .password(encodedPassword)
                    .firstName("Dummy")
                    .lastName("Member %06d".formatted(nextSeedNumber - 1))
                    .institution("ICMS Test Institution")
                    .department("Research Department")
                    .positionTitle(nextSeedNumber % 3 == 0 ? "Professor" : "Researcher")
                    .country(international ? DUMMY_COUNTRIES[(int) ((nextSeedNumber - 2) % DUMMY_COUNTRIES.length)] : null)
                    .mobile(international
                            ? "+1-202-%03d-%04d".formatted(nextSeedNumber % 1000, (nextSeedNumber * 7) % 10000)
                            : "010-%04d-%04d".formatted(nextSeedNumber % 10000, (nextSeedNumber * 7) % 10000))
                    .newsletter(nextSeedNumber % 3 == 0)
                    .build();

            memberRepository.insert(conferenceSeq, member, dbEncString());
            insertedCount++;
        }

        return insertedCount;
    }

    public ExcelExportResult createMembersXlsx(
            Long conferenceSeq,
            String keyword,
            String memberType,
            Boolean hasPreRegistration,
            Boolean hasAbstractSubmission
    ) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String normalizedMemberType = normalizeMemberType(memberType);
        List<MemberListResponse> members = memberRepository.findAllForExport(
                        conferenceSeq,
                        normalizedKeyword,
                        normalizedMemberType,
                        hasPreRegistration,
                        hasAbstractSubmission,
                        dbEncString()
                ).stream()
                .map(this::toListResponse)
                .toList();

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
                addZipEntry(zip, "[Content_Types].xml", contentTypesXml());
                addZipEntry(zip, "_rels/.rels", rootRelationshipsXml());
                addZipEntry(zip, "xl/workbook.xml", workbookXml());
                addZipEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelationshipsXml());
                addZipEntry(zip, "xl/styles.xml", stylesXml());
                addZipEntry(zip, "xl/worksheets/sheet1.xml", worksheetXml(members));
            }
            return new ExcelExportResult(outputStream.toByteArray(), members.size(), "members.xlsx");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create members xlsx", e);
        }
    }

    private String normalizeMemberType(String memberType) {
        if (memberType == null || memberType.isBlank()) {
            return null;
        }

        String normalized = memberType.trim().toLowerCase();
        if (!"international".equals(normalized) && !"domestic".equals(normalized)) {
            throw new IllegalArgumentException("Invalid member type");
        }
        return normalized;
    }

    private MemberListResponse toListResponse(Member member) {
        return MemberListResponse.builder()
                .seq(member.getSeq())
                .memberType(member.getMemberType())
                .email(member.getEmail())
                .firstName(member.getFirstName())
                .lastName(member.getLastName())
                .institution(member.getInstitution())
                .department(member.getDepartment())
                .positionTitle(member.getPositionTitle())
                .country(member.getCountry())
                .mobile(member.getMobile())
                .newsletter(member.getNewsletter())
                .createdAt(member.getCreatedAt())
                .updatedAt(member.getUpdatedAt())
                .build();
    }

    private void addZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String contentTypesXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                </Types>
                """;
    }

    private String rootRelationshipsXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
                """;
    }

    private String workbookXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="Members" sheetId="1" r:id="rId1"/>
                  </sheets>
                </workbook>
                """;
    }

    private String workbookRelationshipsXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                </Relationships>
                """;
    }

    private String stylesXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <fonts count="2">
                    <font><sz val="11"/><name val="Calibri"/></font>
                    <font><b/><sz val="11"/><name val="Calibri"/></font>
                  </fonts>
                  <fills count="2">
                    <fill><patternFill patternType="none"/></fill>
                    <fill><patternFill patternType="gray125"/></fill>
                  </fills>
                  <borders count="1"><border/></borders>
                  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                  <cellXfs count="2">
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
                  </cellXfs>
                </styleSheet>
                """;
    }

    private String worksheetXml(List<MemberListResponse> members) {
        String[] headers = {"회원번호", "회원구분", "이메일", "이름", "성", "소속 기관", "부서/학과", "직책", "국가", "모바일", "뉴스레터", "가입일", "수정일"};
        StringBuilder rows = new StringBuilder();
        rows.append("<row r=\"1\">");
        for (int columnIndex = 0; columnIndex < headers.length; columnIndex++) {
            rows.append(cell(columnIndex, 1, headers[columnIndex], 1));
        }
        rows.append("</row>");

        for (int index = 0; index < members.size(); index++) {
            MemberListResponse member = members.get(index);
            int rowNumber = index + 2;
            rows.append("<row r=\"").append(rowNumber).append("\">")
                    .append(cell(0, rowNumber, member.getSeq(), 0))
                    .append(cell(1, rowNumber, member.getMemberType(), 0))
                    .append(cell(2, rowNumber, member.getEmail(), 0))
                    .append(cell(3, rowNumber, member.getFirstName(), 0))
                    .append(cell(4, rowNumber, member.getLastName(), 0))
                    .append(cell(5, rowNumber, member.getInstitution(), 0))
                    .append(cell(6, rowNumber, member.getDepartment(), 0))
                    .append(cell(7, rowNumber, member.getPositionTitle(), 0))
                    .append(cell(8, rowNumber, member.getCountry(), 0))
                    .append(cell(9, rowNumber, member.getMobile(), 0))
                    .append(cell(10, rowNumber, Boolean.TRUE.equals(member.getNewsletter()) ? "Y" : "N", 0))
                    .append(cell(11, rowNumber, member.getCreatedAt(), 0))
                    .append(cell(12, rowNumber, member.getUpdatedAt(), 0))
                    .append("</row>");
        }

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <cols>
                    <col min="1" max="1" width="12" customWidth="1"/>
                    <col min="2" max="2" width="16" customWidth="1"/>
                    <col min="3" max="3" width="32" customWidth="1"/>
                    <col min="4" max="5" width="18" customWidth="1"/>
                    <col min="6" max="8" width="24" customWidth="1"/>
                    <col min="9" max="10" width="18" customWidth="1"/>
                    <col min="11" max="11" width="12" customWidth="1"/>
                    <col min="12" max="13" width="24" customWidth="1"/>
                  </cols>
                  <sheetData>
                %s
                  </sheetData>
                </worksheet>
                """.formatted(rows);
    }

    private String cell(int columnIndex, int rowNumber, Object value, int styleIndex) {
        return "<c r=\"" + cellReference(columnIndex, rowNumber) + "\" t=\"inlineStr\" s=\"" + styleIndex + "\"><is><t>" +
                xmlValue(value) +
                "</t></is></c>";
    }

    private String cellReference(int columnIndex, int rowNumber) {
        StringBuilder columnName = new StringBuilder();
        int value = columnIndex;

        do {
            columnName.insert(0, (char) ('A' + (value % 26)));
            value = value / 26 - 1;
        } while (value >= 0);

        return columnName + String.valueOf(rowNumber);
    }

    private String xmlValue(Object value) {
        if (value == null) {
            return "";
        }

        String text = String.valueOf(value);
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}

