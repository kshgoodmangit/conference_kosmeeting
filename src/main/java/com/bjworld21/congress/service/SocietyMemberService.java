package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.dto.SocietyMemberData.*;
import com.bjworld21.congress.repository.SocietyMemberRepository;
import com.bjworld21.congress.repository.RegistrationFeeRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Set;

@Service
public class SocietyMemberService {
    public static final Set<String> TYPES = Set.of("정회원", "준회원", "기타");
    private final SocietyMemberRepository repository;
    private final RegistrationFeeRepository fees;
    private final PersonalDataProperties personalDataProperties;

    public SocietyMemberService(SocietyMemberRepository repository, RegistrationFeeRepository fees,
                                PersonalDataProperties personalDataProperties) {
        this.repository = repository;
        this.fees = fees;
        this.personalDataProperties = personalDataProperties;
    }

    @Transactional(readOnly = true)
    public Page page(int page, int size, String keyword, String memberType) {
        keyword = text(keyword, 255, "검색어", false);
        memberType = text(memberType, 10, "회원구분", false);
        if (!memberType.isEmpty()) validateType(memberType);
        size = Math.max(1, Math.min(size, 100));
        Summary summary = repository.summary(keyword, memberType, dbEncString());
        int totalPages = (int) Math.max(1, Math.ceil((double) summary.getTotalCount() / size));
        page = Math.max(1, Math.min(page, totalPages));
        return new Page(repository.page(keyword, memberType, size, (page - 1) * size, dbEncString()), summary, page, size, totalPages);
    }

    @Transactional
    public Member save(Long seq, Member request) {
        validate(request);
        request.setSeq(seq);
        if (seq != null && repository.findBySeq(seq, dbEncString()) == null) throw notFound();
        try {
            if (seq == null) repository.insert(request, dbEncString());
            else repository.update(request, dbEncString());
        } catch (DuplicateKeyException exception) {
            throw new DuplicateKeyException("이미 등록된 면허번호입니다.", exception);
        }
        return repository.findBySeq(request.getSeq(), dbEncString());
    }

    @Transactional
    public void delete(Long seq) {
        if (repository.delete(seq) != 1) throw notFound();
    }

    public List<FeeMapping> mappings(Long conferenceSeq) {
        return repository.mappings(conferenceSeq);
    }

    @Transactional
    public List<FeeMapping> saveMappings(Long conferenceSeq, List<FeeMapping> mappings) {
        if (mappings == null || mappings.size() != 3 || mappings.stream().anyMatch(m -> m == null || m.getMemberType() == null)
                || !mappings.stream().map(FeeMapping::getMemberType).collect(java.util.stream.Collectors.toSet()).equals(TYPES)) {
            throw new IllegalArgumentException("정회원, 준회원, 기타의 등록비 연결을 모두 입력해 주세요.");
        }
        for (FeeMapping mapping : mappings) {
            if (mapping.getCategorySeq() != null) {
                var category = fees.findActiveBySeq(mapping.getCategorySeq(), conferenceSeq);
                if (category == null || !"Y".equals(category.getIsUsed()))
                    throw new IllegalArgumentException("사용 중인 등록 구분만 연결할 수 있습니다.");
            }
            repository.upsertMapping(conferenceSeq, mapping);
        }
        return repository.mappings(conferenceSeq);
    }

    @Transactional(readOnly = true)
    public FeeQuote quote(Long conferenceSeq, QuoteRequest request) {
        if (request == null) throw new IllegalArgumentException("학회회원 확인 정보가 필요합니다.");
        String license = text(request.licenseNumber(), 50, "면허번호", true);
        String name = text(request.fullName(), 100, "이름", true);
        if (!Set.of("EARLY_BIRD", "REGULAR").contains(request.periodType() == null ? "" : request.periodType())
                || !Set.of("KRW", "USD").contains(request.currency() == null ? "" : request.currency()))
            throw new IllegalArgumentException("등록 기간과 통화를 확인해 주세요.");
        Member member = repository.findByLicense(license, dbEncString());
        if (member == null || !member.getFullName().equals(name))
            throw new IllegalArgumentException("면허번호와 이름이 일치하는 학회회원을 찾을 수 없습니다.");
        FeeQuote quote = repository.quote(conferenceSeq, member.getMemberType(), request.periodType(), request.currency());
        if (quote == null) throw new IllegalArgumentException("해당 회원구분의 등록 구분 연결 및 기간별 등록비를 먼저 설정해 주세요.");
        return quote;
    }

    public void validate(Member member) {
        if (member == null) throw new IllegalArgumentException("학회회원 정보가 필요합니다.");
        member.setLicenseNumber(text(member.getLicenseNumber(), 50, "면허번호", true));
        member.setFullName(text(member.getFullName(), 100, "이름", true));
        member.setAffiliation(text(member.getAffiliation(), 255, "소속", false));
        member.setMemberType(text(member.getMemberType(), 10, "회원구분", true));
        validateType(member.getMemberType());
    }

    private void validateType(String type) {
        if (!TYPES.contains(type)) throw new IllegalArgumentException("회원구분은 정회원, 준회원, 기타만 입력할 수 있습니다.");
    }

    private String text(String value, int max, String label, boolean required) {
        String result = value == null ? "" : value.strip();
        if (required && result.isEmpty()) throw new IllegalArgumentException(label + "은(는) 필수입니다.");
        if (result.length() > max || result.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(label + "은(는) 제어문자 없이 " + max + "자 이내로 입력해 주세요.");
        return result;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "학회회원 정보를 찾을 수 없습니다.");
    }
    private String dbEncString() {
        return personalDataProperties.requireDbEncString();
    }
}
