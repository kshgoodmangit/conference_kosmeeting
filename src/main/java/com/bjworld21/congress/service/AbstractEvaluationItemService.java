package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.AbstractEvaluationItemListResponse;
import com.bjworld21.congress.dto.AbstractEvaluationItemRequest;
import com.bjworld21.congress.dto.AbstractEvaluationItemResponse;
import com.bjworld21.congress.entity.AbstractEvaluationItem;
import com.bjworld21.congress.repository.AbstractEvaluationItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AbstractEvaluationItemService {
    public static final int SCALE_MIN = 1;
    public static final int SCALE_MAX = 6;
    private static final String[] DEFAULT_SCORE_GUIDES = {
            "",
            "1점/매우미흡",
            "2점/미흡",
            "3점/다소미흡",
            "4점/양호",
            "5점/우수",
            "6점/매우우수"
    };
    private final AbstractEvaluationItemRepository repository;

    public AbstractEvaluationItemService(AbstractEvaluationItemRepository repository) {
        this.repository = repository;
    }

    public AbstractEvaluationItemListResponse findAll(Long conferenceSeq) {
        return buildListResponse(repository.findAllActive(conferenceSeq));
    }

    public AbstractEvaluationItemListResponse findUsed(Long conferenceSeq) {
        return buildListResponse(repository.findAllUsed(conferenceSeq));
    }

    public AbstractEvaluationItemResponse create(Long conferenceSeq, AbstractEvaluationItemRequest request) {
        AbstractEvaluationItem item = fromRequest(conferenceSeq, request, null);
        validateDuplicateName(conferenceSeq, item.getItemName(), null);
        repository.insert(item);
        return toResponse(findItem(conferenceSeq, item.getSeq()));
    }

    public AbstractEvaluationItemResponse update(Long conferenceSeq, Long seq, AbstractEvaluationItemRequest request) {
        AbstractEvaluationItem existing = findItem(conferenceSeq, seq);
        AbstractEvaluationItem item = fromRequest(conferenceSeq, request, existing.getSeq());
        validateDuplicateName(conferenceSeq, item.getItemName(), existing.getSeq());
        if (repository.update(item) == 0) {
            throw new IllegalArgumentException("평가항목을 저장하지 못했습니다.");
        }
        return toResponse(findItem(conferenceSeq, seq));
    }

    public void delete(Long conferenceSeq, Long seq) {
        findItem(conferenceSeq, seq);
        if (repository.softDelete(conferenceSeq, seq) == 0) {
            throw new IllegalArgumentException("평가항목을 삭제하지 못했습니다.");
        }
    }

    private AbstractEvaluationItem fromRequest(Long conferenceSeq, AbstractEvaluationItemRequest request, Long seq) {
        if (request == null) {
            throw new IllegalArgumentException("평가항목 정보가 필요합니다.");
        }

        String itemName = normalizeRequired(request.getItemName(), "평가항목 이름은 필수입니다.", 100);
        Integer sortOrder = request.getSortOrder() != null ? request.getSortOrder() : 0;
        if (sortOrder < 0) {
            throw new IllegalArgumentException("정렬순서는 0 이상이어야 합니다.");
        }

        boolean isCreate = seq == null;
        return AbstractEvaluationItem.builder()
                .seq(seq)
                .conferenceSeq(conferenceSeq)
                .itemName(itemName)
                .description(normalizeOptional(request.getDescription(), 1000, "평가기준은 1,000자 이하여야 합니다."))
                .sortOrder(sortOrder)
                .isUsed(normalizeYn(request.getIsUsed()))
                .score1Guide(normalizeScoreGuide(request.getScore1Guide(), 1, isCreate))
                .score2Guide(normalizeScoreGuide(request.getScore2Guide(), 2, isCreate))
                .score3Guide(normalizeScoreGuide(request.getScore3Guide(), 3, isCreate))
                .score4Guide(normalizeScoreGuide(request.getScore4Guide(), 4, isCreate))
                .score5Guide(normalizeScoreGuide(request.getScore5Guide(), 5, isCreate))
                .score6Guide(normalizeScoreGuide(request.getScore6Guide(), 6, isCreate))
                .isDelete("N")
                .build();
    }

    private AbstractEvaluationItem findItem(Long conferenceSeq, Long seq) {
        AbstractEvaluationItem item = seq != null ? repository.findActiveBySeq(conferenceSeq, seq) : null;
        if (item == null) {
            throw new IllegalArgumentException("존재하지 않는 평가항목입니다.");
        }
        return item;
    }

    private void validateDuplicateName(Long conferenceSeq, String itemName, Long excludeSeq) {
        if (repository.countActiveByName(conferenceSeq, itemName, excludeSeq) > 0) {
            throw new IllegalArgumentException("이미 등록된 평가항목 이름입니다.");
        }
    }

    private AbstractEvaluationItemListResponse buildListResponse(List<AbstractEvaluationItem> items) {
        return AbstractEvaluationItemListResponse.builder()
                .scaleMin(SCALE_MIN)
                .scaleMax(SCALE_MAX)
                .scaleLabel("각 항목 1~6점")
                .items(items.stream().map(this::toResponse).toList())
                .build();
    }

    private AbstractEvaluationItemResponse toResponse(AbstractEvaluationItem item) {
        return AbstractEvaluationItemResponse.builder()
                .seq(item.getSeq())
                .itemName(item.getItemName())
                .description(item.getDescription())
                .sortOrder(item.getSortOrder())
                .isUsed(item.getIsUsed())
                .score1Guide(item.getScore1Guide())
                .score2Guide(item.getScore2Guide())
                .score3Guide(item.getScore3Guide())
                .score4Guide(item.getScore4Guide())
                .score5Guide(item.getScore5Guide())
                .score6Guide(item.getScore6Guide())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private String normalizeRequired(String value, String message, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("평가항목 이름은 100자 이하여야 합니다.");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeScoreGuide(String value, int score, boolean useDefault) {
        String normalized = normalizeOptional(value, 500, score + "점 기준은 500자 이하여야 합니다.");
        return normalized == null && useDefault ? DEFAULT_SCORE_GUIDES[score] : normalized;
    }

    private String normalizeYn(String value) {
        String normalized = value == null || value.isBlank() ? "Y" : value.trim().toUpperCase();
        if (!"Y".equals(normalized) && !"N".equals(normalized)) {
            throw new IllegalArgumentException("사용 여부는 Y 또는 N이어야 합니다.");
        }
        return normalized;
    }
}
