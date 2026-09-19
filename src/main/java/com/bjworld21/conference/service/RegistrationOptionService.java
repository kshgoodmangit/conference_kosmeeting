package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.RegistrationOptionData.*;
import com.bjworld21.conference.repository.ConferenceSettingsRepository;
import com.bjworld21.conference.repository.RegistrationOptionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Service
public class RegistrationOptionService {
    private final RegistrationOptionRepository repository;
    private final ConferenceSettingsRepository conferences;

    public RegistrationOptionService(RegistrationOptionRepository repository, ConferenceSettingsRepository conferences) {
        this.repository = repository;
        this.conferences = conferences;
    }

    @Transactional(readOnly = true)
    public Page page(Long conferenceSeq, int requestedPage, String keyword, String status) {
        requireConference(conferenceSeq);
        String search = text(keyword, 100, false);
        String filter = status == null ? "" : status;
        if (!Set.of("", "Y", "N").contains(filter)) throw invalid("사용 여부를 확인해 주세요.");
        Summary summary = repository.summary(conferenceSeq, search, filter);
        int pages = Math.max(1, (int) Math.ceil(summary.getTotalCount() / 10.0));
        int page = Math.min(Math.max(1, requestedPage), pages);
        return new Page(repository.page(conferenceSeq, search, filter, 10, (page - 1) * 10), page, pages, summary);
    }

    @Transactional
    public Option save(Long conferenceSeq, Long seq, Option option) {
        requireConference(conferenceSeq);
        if (option == null) throw invalid("옵션 정보를 입력해 주세요.");
        if (seq != null && repository.find(conferenceSeq, seq) == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 학회의 옵션을 찾을 수 없습니다.");
        option.setOptionName(text(option.getOptionName(), 100, true));
        option.setDescription(text(option.getDescription(), 1000, false));
        validatePrice(option.getKrwPrice(), 0, "KRW");
        validatePrice(option.getUsdPrice(), 2, "USD");
        if (option.getKrwPrice() == null && option.getUsdPrice() == null) throw invalid("가격을 한 통화 이상 입력해 주세요. 무료는 0을 입력하세요.");
        if (option.getMaxPerPerson() == null || option.getMaxPerPerson() < 1) throw invalid("1인당 최대 수량은 1 이상이어야 합니다.");
        if (option.getCapacity() != null && option.getCapacity() < 0) throw invalid("정원은 0 이상이어야 합니다.");
        if (option.getSortOrder() == null || option.getSortOrder() < 0) throw invalid("표시 순서는 0 이상이어야 합니다.");
        if (option.getEnabled() == null) throw invalid("사용 여부를 선택해 주세요.");
        validateDate(option.getSaleStartsAt());
        validateDate(option.getSaleEndsAt());
        validateDate(option.getChangeEndsAt());
        if (reversed(option.getSaleStartsAt(), option.getSaleEndsAt())) throw invalid("신청 마감은 시작보다 빠를 수 없습니다.");
        if (reversed(option.getSaleStartsAt(), option.getChangeEndsAt())) throw invalid("변경 마감은 신청 시작보다 빠를 수 없습니다.");
        option.setConferenceSeq(conferenceSeq);
        option.setSeq(seq);
        if (seq == null) {
            option.setVersionNo(0);
            repository.insert(option);
        } else {
            if (option.getVersionNo() == null || option.getVersionNo() < 0) throw invalid("수정 버전이 없습니다. 다시 조회해 주세요.");
            if (repository.update(option) != 1)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "다른 관리자가 수정했습니다. 다시 조회해 주세요.");
        }
        return repository.find(conferenceSeq, option.getSeq());
    }

    @Transactional
    public void delete(Long conferenceSeq, Long seq) {
        requireConference(conferenceSeq);
        if (seq == null || repository.find(conferenceSeq, seq) == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 학회의 옵션을 찾을 수 없습니다.");
        if (repository.countSelections(seq) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "사전등록 내역에서 사용 중인 옵션은 삭제할 수 없습니다. 옵션을 '사용 안 함'으로 변경해 주세요.");
        try {
            if (repository.delete(conferenceSeq, seq) != 1)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 학회의 옵션을 찾을 수 없습니다.");
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "사전등록 내역에서 사용 중인 옵션은 삭제할 수 없습니다. 옵션을 '사용 안 함'으로 변경해 주세요.", exception);
        }
    }

    private void requireConference(Long seq) {
        if (seq == null || seq <= 0 || conferences.findBySeq(seq) == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "학회 설정을 찾을 수 없습니다.");
    }

    private String text(String value, int max, boolean required) {
        String normalized = value == null ? "" : value.strip();
        if (required && normalized.isEmpty()) throw invalid("옵션명을 입력해 주세요.");
        if (normalized.length() > max || normalized.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r'))
            throw invalid("입력 길이 또는 제어문자를 확인해 주세요.");
        return normalized;
    }

    private void validatePrice(BigDecimal price, int scale, String currency) {
        if (price != null && (price.signum() < 0 || price.compareTo(new BigDecimal("9999999999.99")) > 0
                || price.stripTrailingZeros().scale() > scale)) throw invalid(currency + " 가격의 범위와 소수 자릿수를 확인해 주세요.");
    }

    private void validateDate(LocalDateTime date) {
        if (date != null && (date.getYear() < 1000 || date.getYear() > 9999)) throw invalid("날짜 범위를 확인해 주세요.");
    }

    private boolean reversed(LocalDateTime start, LocalDateTime end) { return start != null && end != null && end.isBefore(start); }
    private IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
