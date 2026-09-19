package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.CommonCodeResponse;
import com.bjworld21.conference.dto.CommonCodeReorderItemRequest;
import com.bjworld21.conference.entity.CommonCode;
import com.bjworld21.conference.repository.CommonCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class CommonCodeService {
    private final CommonCodeRepository commonCodeRepository;

    public CommonCodeService(CommonCodeRepository commonCodeRepository) {
        this.commonCodeRepository = commonCodeRepository;
    }

    public List<CommonCodeResponse> getCodeTree() {
        List<CommonCode> codes = commonCodeRepository.findAllActive();
        Map<Long, CommonCodeResponse> nodesBySeq = new LinkedHashMap<>();
        List<CommonCodeResponse> roots = new ArrayList<>();

        for (CommonCode code : codes) {
            CommonCodeResponse response = toResponse(code);
            response.setChildren(new ArrayList<>());
            nodesBySeq.put(code.getSeq(), response);
        }

        for (CommonCode code : codes) {
            CommonCodeResponse node = nodesBySeq.get(code.getSeq());
            if (code.getParentSeq() == null || code.getParentSeq() == 0) {
                roots.add(node);
                continue;
            }

            CommonCodeResponse parent = nodesBySeq.get(code.getParentSeq());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }

        return roots;
    }

    public CommonCodeResponse create(
            Long parentSeq,
            String groupCode,
            String codeName,
            Integer sortOrder,
            String isUsed,
            String codeEtc1,
            String codeEtc2,
            String codeEtc3,
            String isEditable,
            String isEtc
    ) {
        Long normalizedParentSeq = normalizeParentSeq(parentSeq);
        CommonCode parent = findParent(normalizedParentSeq);
        validateChildParentDepth(parent);
        String normalizedGroupCode = parent != null
                ? normalizeRequired(parent.getGroupCode(), "상위 코드의 그룹 코드가 올바르지 않습니다.")
                : normalizeRequired(groupCode, "최상위 코드의 그룹 코드는 필수입니다.");

        if (parent == null && commonCodeRepository.findActiveRootByGroupCode(normalizedGroupCode) != null) {
            throw new IllegalArgumentException("이미 등록된 그룹 코드입니다.");
        }

        CommonCode commonCode = CommonCode.builder()
                .groupCode(normalizedGroupCode)
                .parentSeq(normalizedParentSeq)
                .codeName(normalizeRequired(codeName, "코드명은 필수입니다."))
                .sortOrder(sortOrder != null ? sortOrder : 0)
                .isUsed(normalizeYn(isUsed, "사용 여부", "Y"))
                .codeEtc1(normalizeOptional(codeEtc1))
                .codeEtc2(normalizeOptional(codeEtc2))
                .codeEtc3(normalizeOptional(codeEtc3))
                .isEditable(normalizeYn(isEditable, "수정 가능 여부", "Y"))
                .isEtc(normalizeYn(isEtc, "기타 코드 여부", "N"))
                .isDelete("N")
                .build();

        commonCodeRepository.insert(commonCode);
        return toResponse(commonCodeRepository.findActiveBySeq(commonCode.getSeq()));
    }

    @Transactional
    public CommonCodeResponse update(
            Long seq,
            Long parentSeq,
            String codeName,
            Integer sortOrder,
            String isUsed,
            String codeEtc1,
            String codeEtc2,
            String codeEtc3,
            String isEditable,
            String isEtc
    ) {
        CommonCode commonCode = getEditableCode(seq);
        String previousGroupCode = commonCode.getGroupCode();

        if (commonCode.getParentSeq() == null || commonCode.getParentSeq() == 0) {
            if (parentSeq != null && parentSeq != 0) {
                throw new IllegalArgumentException("최상위 코드의 상위 코드는 변경할 수 없습니다.");
            }
        } else {
            Long normalizedParentSeq = normalizeParentSeq(parentSeq != null ? parentSeq : commonCode.getParentSeq());
            if (normalizedParentSeq == 0) {
                throw new IllegalArgumentException("하위 코드는 최상위 코드로 변경할 수 없습니다.");
            }

            CommonCode parent = findParent(normalizedParentSeq);
            validateChildParentDepth(parent);
            validateParentChange(commonCode, parent);
            commonCode.setParentSeq(parent.getSeq());
            commonCode.setGroupCode(normalizeRequired(
                    parent.getGroupCode(),
                    "상위 코드의 그룹 코드가 올바르지 않습니다."
            ));
        }

        commonCode.setCodeName(normalizeRequired(codeName, "코드명은 필수입니다."));
        commonCode.setSortOrder(sortOrder != null ? sortOrder : 0);
        commonCode.setIsUsed(normalizeYn(isUsed, "사용 여부", "Y"));
        commonCode.setCodeEtc1(normalizeOptional(codeEtc1));
        commonCode.setCodeEtc2(normalizeOptional(codeEtc2));
        commonCode.setCodeEtc3(normalizeOptional(codeEtc3));
        commonCode.setIsEditable(normalizeYn(isEditable, "수정 가능 여부", "Y"));
        commonCode.setIsEtc(normalizeYn(isEtc, "기타 코드 여부", "N"));

        commonCodeRepository.update(commonCode);
        if (!Objects.equals(previousGroupCode, commonCode.getGroupCode())) {
            updateDescendantGroupCodes(commonCode.getSeq(), commonCode.getGroupCode());
        }
        return toResponse(commonCodeRepository.findActiveBySeq(seq));
    }

    public void delete(Long seq) {
        CommonCode commonCode = getEditableCode(seq);
        if (commonCodeRepository.countActiveChildren(commonCode.getSeq()) > 0) {
            throw new IllegalArgumentException("하위 코드가 있는 공통코드는 삭제할 수 없습니다.");
        }
        commonCodeRepository.softDelete(commonCode.getSeq());
    }

    @Transactional
    public List<CommonCodeResponse> reorder(List<CommonCodeReorderItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("정렬할 공통코드가 없습니다.");
        }

        List<CommonCode> codes = commonCodeRepository.findAllActive();
        if (items.size() != codes.size()) {
            throw new IllegalArgumentException("공통코드 정렬 정보가 전체 목록과 일치하지 않습니다.");
        }

        Map<Long, CommonCode> codesBySeq = new LinkedHashMap<>();
        for (CommonCode code : codes) {
            codesBySeq.put(code.getSeq(), code);
        }

        Map<Long, Long> requestedParentSeqs = new LinkedHashMap<>();
        Map<Long, Integer> requestedSortOrders = new LinkedHashMap<>();
        for (CommonCodeReorderItemRequest item : items) {
            if (item == null || item.getSeq() == null || !codesBySeq.containsKey(item.getSeq())) {
                throw new IllegalArgumentException("존재하지 않는 공통코드가 정렬 요청에 포함되어 있습니다.");
            }
            if (requestedParentSeqs.containsKey(item.getSeq())) {
                throw new IllegalArgumentException("중복된 공통코드 정렬 요청이 포함되어 있습니다.");
            }

            CommonCode code = codesBySeq.get(item.getSeq());
            Long requestedParentSeq = normalizeParentSeq(item.getParentSeq());
            boolean rootCode = code.getParentSeq() == null || code.getParentSeq() == 0;
            if (rootCode && requestedParentSeq != 0) {
                throw new IllegalArgumentException("최상위 코드는 하위 코드로 이동할 수 없습니다.");
            }
            if (!rootCode && requestedParentSeq == 0) {
                throw new IllegalArgumentException("하위 코드는 최상위 코드로 이동할 수 없습니다.");
            }
            if (!rootCode && !"Y".equals(code.getIsEditable())
                    && !Objects.equals(code.getParentSeq(), requestedParentSeq)) {
                throw new IllegalArgumentException("수정이 제한된 공통코드는 다른 상위 코드로 이동할 수 없습니다.");
            }
            if (requestedParentSeq != 0 && !codesBySeq.containsKey(requestedParentSeq)) {
                throw new IllegalArgumentException("상위 공통코드가 존재하지 않습니다.");
            }
            if (requestedParentSeq != 0) {
                CommonCode requestedParent = codesBySeq.get(requestedParentSeq);
                if (requestedParent.getParentSeq() != null && requestedParent.getParentSeq() != 0) {
                    throw new IllegalArgumentException("공통코드는 최대 2뎁스까지만 사용할 수 있습니다.");
                }
            }

            requestedParentSeqs.put(item.getSeq(), requestedParentSeq);
            requestedSortOrders.put(item.getSeq(), item.getSortOrder() != null ? item.getSortOrder() : 0);
        }

        Map<Long, String> requestedGroupCodes = new LinkedHashMap<>();
        for (CommonCode code : codes) {
            requestedGroupCodes.put(code.getSeq(), resolveRequestedGroupCode(
                    code,
                    codesBySeq,
                    requestedParentSeqs,
                    new HashSet<>()
            ));
        }

        for (CommonCodeReorderItemRequest item : items) {
            commonCodeRepository.updateStructure(
                    item.getSeq(),
                    requestedParentSeqs.get(item.getSeq()),
                    requestedGroupCodes.get(item.getSeq()),
                    requestedSortOrders.get(item.getSeq())
            );
        }

        return getCodeTree();
    }

    private CommonCode getEditableCode(Long seq) {
        CommonCode commonCode = seq != null ? commonCodeRepository.findActiveBySeq(seq) : null;
        if (commonCode == null) {
            throw new IllegalArgumentException("존재하지 않는 공통코드입니다.");
        }
        if (!"Y".equals(commonCode.getIsEditable())) {
            throw new IllegalArgumentException("수정이 제한된 공통코드입니다.");
        }
        return commonCode;
    }

    private Long normalizeParentSeq(Long parentSeq) {
        if (parentSeq == null) {
            return 0L;
        }
        if (parentSeq < 0) {
            throw new IllegalArgumentException("상위 코드가 올바르지 않습니다.");
        }
        return parentSeq;
    }

    private CommonCode findParent(Long parentSeq) {
        if (parentSeq == 0) {
            return null;
        }
        CommonCode parent = commonCodeRepository.findActiveBySeq(parentSeq);
        if (parent == null) {
            throw new IllegalArgumentException("상위 공통코드가 존재하지 않습니다.");
        }
        return parent;
    }

    private void validateChildParentDepth(CommonCode parent) {
        if (parent != null && parent.getParentSeq() != null && parent.getParentSeq() != 0) {
            throw new IllegalArgumentException("공통코드는 최대 2뎁스까지만 사용할 수 있습니다.");
        }
    }

    private void validateParentChange(CommonCode commonCode, CommonCode parent) {
        Set<Long> visited = new HashSet<>();
        CommonCode current = parent;

        while (current != null) {
            if (current.getSeq().equals(commonCode.getSeq())) {
                throw new IllegalArgumentException("자기 자신이나 하위 코드를 상위 코드로 선택할 수 없습니다.");
            }
            if (!visited.add(current.getSeq())) {
                throw new IllegalArgumentException("공통코드 순환 참조는 허용되지 않습니다.");
            }
            if (current.getParentSeq() == null || current.getParentSeq() == 0) {
                return;
            }

            current = commonCodeRepository.findActiveBySeq(current.getParentSeq());
            if (current == null) {
                throw new IllegalArgumentException("상위 공통코드 구조가 올바르지 않습니다.");
            }
        }
    }

    private void updateDescendantGroupCodes(Long rootSeq, String groupCode) {
        Map<Long, List<CommonCode>> childrenByParent = new LinkedHashMap<>();
        for (CommonCode code : commonCodeRepository.findAllActive()) {
            childrenByParent.computeIfAbsent(code.getParentSeq(), ignored -> new ArrayList<>()).add(code);
        }

        Deque<Long> pendingParents = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        pendingParents.add(rootSeq);
        visited.add(rootSeq);
        while (!pendingParents.isEmpty()) {
            Long parentSeq = pendingParents.removeFirst();
            for (CommonCode child : childrenByParent.getOrDefault(parentSeq, List.of())) {
                if (!visited.add(child.getSeq())) {
                    throw new IllegalArgumentException("공통코드 순환 참조는 허용되지 않습니다.");
                }
                commonCodeRepository.updateGroupCode(child.getSeq(), groupCode);
                pendingParents.addLast(child.getSeq());
            }
        }
    }

    private String resolveRequestedGroupCode(
            CommonCode code,
            Map<Long, CommonCode> codesBySeq,
            Map<Long, Long> requestedParentSeqs,
            Set<Long> lineage
    ) {
        if (!lineage.add(code.getSeq())) {
            throw new IllegalArgumentException("공통코드 순환 참조는 허용되지 않습니다.");
        }

        try {
            Long parentSeq = requestedParentSeqs.get(code.getSeq());
            if (parentSeq == null) {
                throw new IllegalArgumentException("공통코드 정렬 정보가 누락되었습니다.");
            }
            if (parentSeq == 0) {
                return normalizeRequired(code.getGroupCode(), "최상위 코드의 그룹 코드가 올바르지 않습니다.");
            }

            CommonCode parent = codesBySeq.get(parentSeq);
            if (parent == null) {
                throw new IllegalArgumentException("상위 공통코드가 존재하지 않습니다.");
            }
            return resolveRequestedGroupCode(parent, codesBySeq, requestedParentSeqs, lineage);
        } finally {
            lineage.remove(code.getSeq());
        }
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeYn(String value, String fieldName, String defaultValue) {
        String normalized = value == null ? defaultValue : value.trim().toUpperCase(Locale.ROOT);
        if (!"Y".equals(normalized) && !"N".equals(normalized)) {
            throw new IllegalArgumentException(fieldName + "는 Y 또는 N만 입력할 수 있습니다.");
        }
        return normalized;
    }

    private CommonCodeResponse toResponse(CommonCode commonCode) {
        return CommonCodeResponse.builder()
                .seq(commonCode.getSeq())
                .groupCode(commonCode.getGroupCode())
                .parentSeq(commonCode.getParentSeq())
                .codeName(commonCode.getCodeName())
                .sortOrder(commonCode.getSortOrder())
                .isUsed(commonCode.getIsUsed())
                .codeEtc1(commonCode.getCodeEtc1())
                .codeEtc2(commonCode.getCodeEtc2())
                .codeEtc3(commonCode.getCodeEtc3())
                .isEditable(commonCode.getIsEditable())
                .isEtc(commonCode.getIsEtc())
                .createdAt(commonCode.getCreatedAt())
                .updatedAt(commonCode.getUpdatedAt())
                .build();
    }
}
