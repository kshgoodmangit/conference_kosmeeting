package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.CommonCodeResponse;
import com.bjworld21.conference.dto.CommonCodeReorderItemRequest;
import com.bjworld21.conference.entity.CommonCode;
import com.bjworld21.conference.repository.CommonCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class CommonCodeServiceTest {

    @Mock
    private CommonCodeRepository commonCodeRepository;

    @InjectMocks
    private CommonCodeService commonCodeService;

    @Test
    void buildsCodeTreeFromParentSequence() {
        CommonCode group = commonCode(1L, 0L, "payment_status", "결제 상태", "Y");
        CommonCode child = commonCode(2L, 1L, "payment_status", "결제 완료", "Y");
        when(commonCodeRepository.findAllActive()).thenReturn(List.of(group, child));

        List<CommonCodeResponse> tree = commonCodeService.getCodeTree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getSeq()).isEqualTo(1L);
        assertThat(tree.get(0).getChildren())
                .extracting(CommonCodeResponse::getSeq)
                .containsExactly(2L);
    }

    @Test
    void usesProvidedGroupCodeWhenCreatingRootCode() {
        CommonCode saved = commonCode(7L, 0L, "abstract_category", "초록 분류", "Y");
        when(commonCodeRepository.findActiveRootByGroupCode("abstract_category")).thenReturn(null);
        doAnswer(invocation -> {
            CommonCode target = invocation.getArgument(0);
            target.setSeq(7L);
            return null;
        }).when(commonCodeRepository).insert(any(CommonCode.class));
        when(commonCodeRepository.findActiveBySeq(7L)).thenReturn(saved);

        CommonCodeResponse response = commonCodeService.create(
                0L,
                "abstract_category",
                "초록 분류",
                10,
                "Y",
                null,
                null,
                null,
                "Y",
                "N"
        );

        ArgumentCaptor<CommonCode> captor = ArgumentCaptor.forClass(CommonCode.class);
        verify(commonCodeRepository).insert(captor.capture());
        assertThat(captor.getValue().getGroupCode()).isEqualTo("abstract_category");
        assertThat(response.getGroupCode()).isEqualTo("abstract_category");
    }

    @Test
    void changesParentAndUpdatesGroupCode() {
        CommonCode movingCode = commonCode(2L, 1L, "group_a", "이동 코드", "Y");
        CommonCode newParent = commonCode(3L, 0L, "group_b", "새 상위 코드", "Y");
        when(commonCodeRepository.findActiveBySeq(2L)).thenReturn(movingCode);
        when(commonCodeRepository.findActiveBySeq(3L)).thenReturn(newParent);
        when(commonCodeRepository.findAllActive()).thenReturn(List.of(movingCode, newParent));

        CommonCodeResponse response = commonCodeService.update(
                2L,
                3L,
                "이동 코드",
                10,
                "Y",
                null,
                null,
                null,
                "Y",
                "N"
        );

        assertThat(response.getParentSeq()).isEqualTo(3L);
        assertThat(response.getGroupCode()).isEqualTo("group_b");
        verify(commonCodeRepository).update(movingCode);
    }

    @Test
    void rejectsSelectingSecondDepthCodeAsParent() {
        CommonCode movingCode = commonCode(2L, 1L, "group_a", "이동 코드", "Y");
        CommonCode descendant = commonCode(4L, 2L, "group_a", "하위 코드", "Y");
        when(commonCodeRepository.findActiveBySeq(2L)).thenReturn(movingCode);
        when(commonCodeRepository.findActiveBySeq(4L)).thenReturn(descendant);

        assertThatThrownBy(() -> commonCodeService.update(
                2L,
                4L,
                "이동 코드",
                10,
                "Y",
                null,
                null,
                null,
                "Y",
                "N"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("공통코드는 최대 2뎁스까지만 사용할 수 있습니다.");

        verify(commonCodeRepository, never()).update(movingCode);
    }

    @Test
    void rejectsCreatingThirdDepthCode() {
        CommonCode secondDepthParent = commonCode(2L, 1L, "group_a", "2뎁스 코드", "Y");
        when(commonCodeRepository.findActiveBySeq(2L)).thenReturn(secondDepthParent);

        assertThatThrownBy(() -> commonCodeService.create(
                2L,
                "group_a",
                "3뎁스 코드",
                10,
                "Y",
                null,
                null,
                null,
                "Y",
                "N"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("공통코드는 최대 2뎁스까지만 사용할 수 있습니다.");

        verify(commonCodeRepository, never()).insert(any(CommonCode.class));
    }

    @Test
    void reordersTreeAndAppliesNewRootGroupToMovedSubtree() {
        CommonCode rootA = commonCode(1L, 0L, "group_a", "그룹 A", "Y");
        CommonCode rootB = commonCode(2L, 0L, "group_b", "그룹 B", "Y");
        CommonCode movingCode = commonCode(3L, 1L, "group_a", "이동 코드", "Y");
        List<CommonCode> codes = List.of(rootA, rootB, movingCode);
        when(commonCodeRepository.findAllActive()).thenReturn(codes);

        commonCodeService.reorder(List.of(
                reorderItem(1L, 0L, 0),
                reorderItem(2L, 0L, 10),
                reorderItem(3L, 2L, 0)
        ));

        verify(commonCodeRepository).updateStructure(3L, 2L, "group_b", 0);
    }

    @Test
    void rejectsDeletingProtectedCode() {
        CommonCode protectedCode = commonCode(1L, 0L, "system", "시스템 코드", "N");
        when(commonCodeRepository.findActiveBySeq(1L)).thenReturn(protectedCode);

        assertThatThrownBy(() -> commonCodeService.delete(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("수정이 제한된 공통코드입니다.");

        verify(commonCodeRepository, never()).softDelete(1L);
    }

    @Test
    void rejectsDeletingCodeWithChildren() {
        CommonCode group = commonCode(1L, 0L, "payment_status", "결제 상태", "Y");
        when(commonCodeRepository.findActiveBySeq(1L)).thenReturn(group);
        when(commonCodeRepository.countActiveChildren(1L)).thenReturn(1L);

        assertThatThrownBy(() -> commonCodeService.delete(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("하위 코드가 있는 공통코드는 삭제할 수 없습니다.");

        verify(commonCodeRepository, never()).softDelete(1L);
    }

    private CommonCode commonCode(Long seq, Long parentSeq, String groupCode, String codeName, String isEditable) {
        return CommonCode.builder()
                .seq(seq)
                .groupCode(groupCode)
                .parentSeq(parentSeq)
                .codeName(codeName)
                .sortOrder(10)
                .isUsed("Y")
                .isEditable(isEditable)
                .isEtc("N")
                .isDelete("N")
                .build();
    }

    private CommonCodeReorderItemRequest reorderItem(Long seq, Long parentSeq, Integer sortOrder) {
        return CommonCodeReorderItemRequest.builder()
                .seq(seq)
                .parentSeq(parentSeq)
                .sortOrder(sortOrder)
                .build();
    }
}
