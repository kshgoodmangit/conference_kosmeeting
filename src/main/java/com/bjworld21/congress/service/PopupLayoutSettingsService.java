package com.bjworld21.congress.service;

import com.bjworld21.congress.repository.ConferenceSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PopupLayoutSettingsService {
    private static final int MIN_LAYOUT_NO = 1;
    private static final int MAX_LAYOUT_NO = 8;

    private final ConferenceSettingsRepository conferenceSettingsRepository;

    public PopupLayoutSettingsService(ConferenceSettingsRepository conferenceSettingsRepository) {
        this.conferenceSettingsRepository = conferenceSettingsRepository;
    }

    public int getPopupLayoutNo(Long conferenceSeq) {
        validateConferenceSeq(conferenceSeq);

        Integer popupLayoutNo = conferenceSettingsRepository.findPopupLayoutNo(conferenceSeq);
        if (popupLayoutNo == null) {
            throw new IllegalArgumentException("학회 설정을 찾을 수 없습니다.");
        }
        validateLayoutNo(popupLayoutNo);
        return popupLayoutNo;
    }

    @Transactional
    public int updatePopupLayoutNo(Long conferenceSeq, Integer popupLayoutNo) {
        validateConferenceSeq(conferenceSeq);
        validateLayoutNo(popupLayoutNo);

        int updatedCount = conferenceSettingsRepository.updatePopupLayoutNo(conferenceSeq, popupLayoutNo);
        if (updatedCount == 0) {
            throw new IllegalArgumentException("수정할 학회 설정을 찾을 수 없습니다.");
        }
        return popupLayoutNo;
    }

    private void validateConferenceSeq(Long conferenceSeq) {
        if (conferenceSeq == null || conferenceSeq <= 0) {
            throw new IllegalArgumentException("학회 정보가 올바르지 않습니다.");
        }
    }

    private void validateLayoutNo(Integer popupLayoutNo) {
        if (popupLayoutNo == null || popupLayoutNo < MIN_LAYOUT_NO || popupLayoutNo > MAX_LAYOUT_NO) {
            throw new IllegalArgumentException("팝업 레이아웃은 1번부터 8번까지 선택할 수 있습니다.");
        }
    }
}
