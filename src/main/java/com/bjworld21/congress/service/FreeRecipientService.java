package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.dto.FreeRecipientData.*;
import com.bjworld21.congress.repository.FreeRecipientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class FreeRecipientService {
    public static final Set<String> TYPES = Set.of("초청", "임원", "연자", "기타");
    private static final Pattern EMAIL = Pattern.compile("^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$", Pattern.CASE_INSENSITIVE);
    private final FreeRecipientRepository repository;
    private final PersonalDataProperties personalDataProperties;
    public FreeRecipientService(FreeRecipientRepository repository, PersonalDataProperties personalDataProperties) {
        this.repository = repository;
        this.personalDataProperties = personalDataProperties;
    }

    @Transactional(readOnly = true)
    public Page page(Long conferenceSeq, int page, int size, String keyword, String recipientType, String isUsed) {
        keyword = text(keyword, 255, "검색어", false);
        recipientType = text(recipientType, 10, "대상 구분", false);
        isUsed = text(isUsed, 1, "사용 여부", false);
        if (!recipientType.isEmpty()) validateType(recipientType);
        if (!isUsed.isEmpty()) validateUsed(isUsed);
        String phoneKeyword = keyword.matches("[+0-9() .-]+") ? canonicalPhoneDigits(keyword) : "";
        size = Math.max(1, Math.min(size, 100));
        Summary summary = repository.summary(conferenceSeq, keyword, phoneKeyword, recipientType, isUsed, dbEncString());
        int pages = (int) Math.max(1, Math.ceil((double) summary.getTotalCount() / size));
        page = Math.max(1, Math.min(page, pages));
        return new Page(repository.page(conferenceSeq, keyword, phoneKeyword, recipientType, isUsed, size, (page - 1) * size, dbEncString()), summary, page, size, pages);
    }
    public Recipient get(Long conferenceSeq, Long seq) {
        Recipient result = repository.findBySeq(conferenceSeq, seq, dbEncString());
        if (result == null) throw notFound();
        return result;
    }
    @Transactional
    public Recipient save(Long conferenceSeq, Long seq, Recipient request) {
        validate(request);
        request.setSeq(seq);
        if (seq == null) repository.insert(conferenceSeq, request, dbEncString());
        else { get(conferenceSeq, seq); repository.update(conferenceSeq, request, dbEncString()); }
        return get(conferenceSeq, request.getSeq());
    }
    @Transactional
    public void delete(Long conferenceSeq, Long seq) { if (repository.delete(conferenceSeq, seq) != 1) throw notFound(); }

    public void validate(Recipient recipient) {
        if (recipient == null) throw new IllegalArgumentException("무료 대상자 정보가 필요합니다.");
        recipient.setFullName(Normalizer.normalize(text(recipient.getFullName(), 100, "이름", true), Normalizer.Form.NFC).replaceAll("\\s+", " "));
        recipient.setAffiliation(text(recipient.getAffiliation(), 255, "소속", false));
        recipient.setPosition(text(recipient.getPosition(), 100, "직책", false));
        recipient.setPhoneNumber(text(recipient.getPhoneNumber(), 30, "연락처", true));
        recipient.setNormalizedPhone(normalizePhone(recipient.getPhoneNumber()));
        recipient.setEmail(text(recipient.getEmail(), 255, "이메일", false).toLowerCase(Locale.ROOT));
        if (!recipient.getEmail().isEmpty() && !EMAIL.matcher(recipient.getEmail()).matches())
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다.");
        String type = text(recipient.getRecipientType(), 10, "대상 구분", false);
        recipient.setRecipientType(type.isEmpty() ? "기타" : type); validateType(recipient.getRecipientType());
        String used = text(recipient.getIsUsed(), 1, "사용 여부", false);
        recipient.setIsUsed(used.isEmpty() ? "Y" : used); validateUsed(recipient.getIsUsed());
        recipient.setAdminMemo(text(recipient.getAdminMemo(), 1000, "관리자 메모", false));
    }
    public static String normalizePhone(String value) {
        if (!value.matches("\\+?[0-9() .-]+")) throw new IllegalArgumentException("연락처에는 숫자, 공백, +, -, 괄호, 점만 입력해 주세요.");
        String digits = canonicalPhoneDigits(value);
        if (digits.length() < 8 || digits.length() > 15) throw new IllegalArgumentException("연락처는 국가번호를 포함해 8~15자리 숫자로 입력해 주세요.");
        return digits;
    }
    private static String canonicalPhoneDigits(String value) {
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.startsWith("00")) digits = digits.substring(2);
        if (digits.startsWith("82")) {
            digits = digits.substring(2);
            if (!digits.startsWith("0")) digits = "0" + digits;
        }
        return digits;
    }
    private void validateType(String value) { if (!TYPES.contains(value)) throw new IllegalArgumentException("대상 구분은 초청, 임원, 연자, 기타만 입력할 수 있습니다."); }
    private void validateUsed(String value) { if (!Set.of("Y", "N").contains(value)) throw new IllegalArgumentException("사용 여부는 Y 또는 N으로 입력해 주세요."); }
    private String text(String value, int max, String label, boolean required) {
        String result = value == null ? "" : value.strip();
        if (required && result.isEmpty()) throw new IllegalArgumentException(label + "은(는) 필수입니다.");
        if (result.length() > max || result.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(label + "은(는) 제어문자 없이 " + max + "자 이내로 입력해 주세요.");
        return result;
    }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "무료 대상자를 찾을 수 없습니다."); }
    private String dbEncString() {
        return personalDataProperties.requireDbEncString();
    }
}
