package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.AbstractEvaluationItemListResponse;
import com.bjworld21.congress.dto.AbstractEvaluationItemRequest;
import com.bjworld21.congress.entity.AbstractEvaluationItem;
import com.bjworld21.congress.repository.AbstractEvaluationItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractEvaluationItemServiceTest {

    @Mock
    private AbstractEvaluationItemRepository repository;

    private AbstractEvaluationItemService service;

    @BeforeEach
    void setUp() {
        service = new AbstractEvaluationItemService(repository);
    }

    @Test
    void findAllReturnsSixPointPolicy() {
        when(repository.findAllActive(1L)).thenReturn(List.of(
                item(1L, "학술적 가치", "Y"),
                item(2L, "연구방법", "Y"),
                item(3L, "결과", "Y"),
                item(4L, "명확성", "Y"),
                item(5L, "미사용", "N")
        ));

        AbstractEvaluationItemListResponse response = service.findAll(1L);

        assertThat(response.getScaleMin()).isEqualTo(1);
        assertThat(response.getScaleMax()).isEqualTo(6);
        assertThat(response.getItems()).hasSize(5);
    }

    @Test
    void createNormalizesAndPersistsItem() {
        AbstractEvaluationItemRequest request = AbstractEvaluationItemRequest.builder()
                .itemName("  신규 평가항목  ")
                .description("  평가 설명  ")
                .sortOrder(50)
                .isUsed("Y")
                .score2Guide("낮음")
                .score4Guide("양호")
                .score5Guide("우수")
                .build();
        when(repository.countActiveByName(1L, "신규 평가항목", null)).thenReturn(0L);
        when(repository.findActiveBySeq(1L, 10L)).thenReturn(item(10L, "신규 평가항목", "Y"));
        doAnswer(invocation -> {
            AbstractEvaluationItem savedItem = invocation.getArgument(0);
            savedItem.setSeq(10L);
            return null;
        }).when(repository).insert(any(AbstractEvaluationItem.class));

        service.create(1L, request);

        ArgumentCaptor<AbstractEvaluationItem> captor = ArgumentCaptor.forClass(AbstractEvaluationItem.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().getScore1Guide()).isEqualTo("1점/매우미흡");
        assertThat(captor.getValue().getScore2Guide()).isEqualTo("낮음");
        assertThat(captor.getValue().getScore3Guide()).isEqualTo("3점/다소미흡");
        assertThat(captor.getValue().getScore4Guide()).isEqualTo("양호");
        assertThat(captor.getValue().getScore5Guide()).isEqualTo("우수");
        assertThat(captor.getValue().getScore6Guide()).isEqualTo("6점/매우우수");
    }

    @Test
    void deleteUsesLogicalDelete() {
        when(repository.findActiveBySeq(1L, 1L)).thenReturn(item(1L, "학술적 가치", "Y"));
        when(repository.softDelete(1L, 1L)).thenReturn(1);

        service.delete(1L, 1L);

        verify(repository).softDelete(1L, 1L);
    }

    private AbstractEvaluationItem item(Long seq, String name, String isUsed) {
        return AbstractEvaluationItem.builder()
                .seq(seq)
                .itemName(name)
                .sortOrder(seq.intValue() * 10)
                .isUsed(isUsed)
                .isDelete("N")
                .build();
    }
}
