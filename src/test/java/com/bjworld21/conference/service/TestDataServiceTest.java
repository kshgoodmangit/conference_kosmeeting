package com.bjworld21.conference.service;

import com.bjworld21.conference.config.PersonalDataProperties;
import com.bjworld21.conference.config.UploadProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestDataServiceTest {

    @Test
    void abstractSamplePlansMatchWorkflowStateAndDecisionDistribution() {
        List<Long> presentationTypes = List.of(10L, 20L);
        List<TestDataService.AbstractSamplePlan> plans = IntStream.rangeClosed(1, 216)
                .mapToObj(index -> TestDataService.abstractSamplePlan(index, presentationTypes))
                .toList();
        Map<String, Long> statusCounts = plans.stream()
                .map(TestDataService.AbstractSamplePlan::status)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        assertThat(statusCounts).containsEntry("draft", 18L)
                .containsEntry("submitted", 27L)
                .containsEntry("under_review", 81L)
                .containsEntry("approved", 45L)
                .containsEntry("rejected", 45L);
        assertThat(plans).filteredOn(TestDataService.AbstractSamplePlan::forcedDecision).hasSize(18);
        assertThat(plans).filteredOn(plan -> "approved".equals(plan.status()))
                .allSatisfy(plan -> assertThat(plan.acceptedPresentationTypeCode()).isNotNull());
        assertThat(plans).filteredOn(plan -> "rejected".equals(plan.status()))
                .allSatisfy(plan -> assertThat(plan.acceptedPresentationTypeCode()).isNull());
        assertThat(plans).filteredOn(plan -> plan.forcedDecision())
                .allSatisfy(plan -> assertThat(plan.decisionReason()).contains("강제"));
        assertThat(IntStream.range(0, plans.size())
                .filter(offset -> {
                    TestDataService.AbstractSamplePlan plan = plans.get(offset);
                    return "approved".equals(plan.status())
                            && !plan.presentationTypeCode().equals(plan.acceptedPresentationTypeCode());
                })
                .count()).isEqualTo(18L);
    }

    @Test
    void normalFinalDecisionsUseAtLeastTwoReviewersAndForcedDecisionsUseOne() {
        List<Long> presentationTypes = List.of(10L, 20L);
        Random random = new Random(1234L);

        for (int index = 1; index <= 216; index++) {
            TestDataService.AbstractSamplePlan plan = TestDataService.abstractSamplePlan(index, presentationTypes);
            int reviewerCount = TestDataService.reviewerCount(plan, 3, random);
            if (plan.forcedDecision()) {
                assertThat(reviewerCount).isEqualTo(1);
            } else if ("approved".equals(plan.status()) || "rejected".equals(plan.status())) {
                assertThat(reviewerCount).isBetween(2, 3);
            } else if ("draft".equals(plan.status()) || "submitted".equals(plan.status())) {
                assertThat(reviewerCount).isZero();
            } else {
                assertThat(reviewerCount).isBetween(1, 3);
            }
        }
    }

    @Test
    void dailyAbstractsUseTheSameWorkflowPlanAcrossDates() {
        List<Long> presentationTypes = List.of(10L, 20L);
        List<Integer> sampleIndexes = IntStream.range(0, 24)
                .boxed()
                .flatMap(dayOffset -> IntStream.rangeClosed(
                                1,
                                DailyDashboardTestDataService.dailyAbstractDesiredCount(
                                        DailyDashboardTestDataService.START_DATE.plusDays(dayOffset)
                                )
                        )
                        .mapToObj(index -> DailyDashboardTestDataService.dailyAbstractSampleIndex(
                                DailyDashboardTestDataService.START_DATE.plusDays(dayOffset),
                                index
                        )))
                .toList();
        Map<String, Long> statusCounts = sampleIndexes.stream()
                .map(index -> TestDataService.abstractSamplePlan(index, presentationTypes).status())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        long forcedDecisionCount = sampleIndexes.stream()
                .map(index -> TestDataService.abstractSamplePlan(index, presentationTypes))
                .filter(TestDataService.AbstractSamplePlan::forcedDecision)
                .count();

        assertThat(sampleIndexes).containsExactlyElementsOf(
                IntStream.rangeClosed(1, sampleIndexes.size()).boxed().toList()
        );
        assertThat(statusCounts).containsKeys(
                "draft", "submitted", "under_review", "approved", "rejected"
        );
        assertThat(forcedDecisionCount).isPositive();
    }

    @Test
    void createPopupsCopiesOneBlankImageAndBuildsEighteenRows(@TempDir Path tempDirectory) throws Exception {
        UploadProperties properties = new UploadProperties();
        properties.setBaseDirectory(tempDirectory.toString());
        UploadStorage uploadStorage = new UploadStorage(
                properties,
                Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneId.of("Asia/Seoul"))
        );
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                contains("conference_settings"), eq(Integer.class), eq(1L)
        )).thenReturn(1);
        PersonalDataProperties personalDataProperties = new PersonalDataProperties();
        personalDataProperties.setDbEncString("test-encryption-key");
        TestDataService service = new TestDataService(
                jdbcTemplate,
                mock(PasswordEncoder.class),
                uploadStorage,
                new ByteArrayResource(new byte[]{1, 2, 3}),
                personalDataProperties
        );

        TestDataService.CreationResult result = service.createPopups(1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object[]>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(jdbcTemplate).batchUpdate(contains("INSERT INTO popups"), rowsCaptor.capture());
        List<Object[]> rows = rowsCaptor.getValue();

        assertThat(result.createdCount()).isEqualTo(18);
        assertThat(rows).hasSize(18);
        assertThat((String) rows.get(0)[6])
                .contains("<img src=\"/api/popups/images/202608/")
                .contains("테스트 팝업 01");
        List<Path> storedFiles;
        try (var files = Files.walk(tempDirectory)) {
            storedFiles = files.filter(Files::isRegularFile).toList();
        }
        assertThat(storedFiles).hasSize(1);
        assertThat(storedFiles.get(0).getFileName().toString()).matches("[0-9a-f-]{36}\\.png");
        assertThat(Files.size(storedFiles.get(0))).isGreaterThan(0L);
    }
}
