package com.bjworld21.conference.service;

import com.bjworld21.conference.config.UploadProperties;
import com.bjworld21.conference.dto.SponsorTypeResponse;
import com.bjworld21.conference.entity.CommonCode;
import com.bjworld21.conference.repository.SponsorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SponsorServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Mock
    private SponsorRepository repository;

    private SponsorService service;

    @BeforeEach
    void setUp() {
        UploadProperties properties = new UploadProperties();
        properties.setBaseDirectory(temporaryDirectory.toString());
        service = new SponsorService(repository, new UploadStorage(properties));
    }

    @Test
    void sponsorTypesReturnActiveChildrenLoadedByRepository() {
        when(repository.findActiveSponsorTypes()).thenReturn(List.of(
                CommonCode.builder().seq(10L).codeName("골드").sortOrder(10).build(),
                CommonCode.builder().seq(11L).codeName("실버").sortOrder(20).build()
        ));

        List<SponsorTypeResponse> result = service.findSponsorTypes();

        assertThat(result).extracting(SponsorTypeResponse::getCodeName)
                .containsExactly("골드", "실버");
    }

    @Test
    void createRejectsCodeOutsideSponsorTypeChildren() {
        when(repository.findActiveSponsorType(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.create(
                1L,
                99L,
                "테스트 스폰서",
                "https://example.com",
                null,
                null,
                true,
                0,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효한 스폰서 구분을 선택해주세요.");
    }

    @Test
    void createRejectsEndDateBeforeStartDate() {
        when(repository.findActiveSponsorType(10L)).thenReturn(
                CommonCode.builder().seq(10L).parentSeq(9L).codeName("골드").build()
        );

        assertThatThrownBy(() -> service.create(
                1L,
                10L,
                "테스트 스폰서",
                null,
                LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 1),
                true,
                0,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용 종료일은 시작일보다 빠를 수 없습니다.");
    }

    @Test
    void createRejectsNonHttpLink() {
        when(repository.findActiveSponsorType(10L)).thenReturn(
                CommonCode.builder().seq(10L).parentSeq(9L).codeName("골드").build()
        );

        assertThatThrownBy(() -> service.create(
                1L,
                10L,
                "테스트 스폰서",
                "javascript:alert(1)",
                null,
                null,
                true,
                0,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http:// 또는 https://");
    }
}
