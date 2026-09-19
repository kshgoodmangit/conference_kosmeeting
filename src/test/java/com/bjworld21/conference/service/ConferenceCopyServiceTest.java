package com.bjworld21.conference.service;

import com.bjworld21.conference.config.UploadProperties;
import com.bjworld21.conference.dto.ConferenceSettingsResponse;
import com.bjworld21.conference.dto.ConferenceSettingsSaveAllRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConferenceCopyServiceTest {
    @TempDir Path directory;

    @Test
    void copiesCategoriesAndRemapsFeesWithoutCopyingDeletedCategoriesOrChangingSource() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var conferences = mock(ConferenceSettingsService.class);
        var properties = new UploadProperties(); properties.setBaseDirectory(directory.toString());
        var service = new ConferenceCopyService(jdbc, conferences, new UploadStorage(properties));
        var settings = ConferenceSettingsSaveAllRequest.builder().published(true).eventName("135")
                .eventStartDate(LocalDate.of(2026,3,3)).eventEndDate(LocalDate.of(2026,3,6)).build();
        when(conferences.getSettings(1L)).thenReturn(ConferenceSettingsResponse.builder().seq(1L).sitePath("2026_136")
                .eventStartDate(LocalDate.of(2027,5,9)).registrationCurrency("USD").build());
        when(conferences.saveConfigured(null, settings)).thenReturn(ConferenceSettingsResponse.builder().seq(2L).sitePath("2026_135")
                .eventStartDate(settings.getEventStartDate()).eventEndDate(settings.getEventEndDate()).build());
        var original = new LinkedHashMap<String,Object>(); original.put("seq",10L); original.put("conferenceSeq",1L); original.put("categoryCode","REGULAR");
        when(jdbc.queryForList("SELECT * FROM registration_categories WHERE conferenceSeq = ? AND isDelete = 'N' ORDER BY seq", 1L)).thenReturn(List.of(original));
        var fee = new LinkedHashMap<String,Object>(); fee.put("seq",20L); fee.put("categorySeq",10L); fee.put("currency","KRW");
        when(jdbc.queryForList("SELECT * FROM registration_fee_rates WHERE categorySeq = ? ORDER BY seq",10L)).thenReturn(List.of(fee));
        List<String> sqls = new ArrayList<>(); List<List<Object>> inserts = new ArrayList<>();
        var sequence = new AtomicLong(100);
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(call -> {
            Connection connection = mock(Connection.class); PreparedStatement statement = mock(PreparedStatement.class);
            List<Object> values = new ArrayList<>();
            when(connection.prepareStatement(anyString(), anyInt())).thenAnswer(c -> { sqls.add(c.getArgument(0)); return statement; });
            doAnswer(c -> { values.add(c.getArgument(1)); return null; }).when(statement).setObject(anyInt(), any());
            call.<PreparedStatementCreator>getArgument(0).createPreparedStatement(connection);
            call.<KeyHolder>getArgument(1).getKeyList().add(Map.of("seq",sequence.incrementAndGet())); inserts.add(values); return 1;
        });
        var result = service.copy(1, new ConferenceCopyService.Request(settings, Set.of("fees")));
        assertThat(result.conference().getSeq()).isEqualTo(2L);
        verify(conferences).saveConfigured(isNull(), argThat(request -> Boolean.FALSE.equals(request.getPublished())));
        assertThat(result.copiedCounts()).containsEntry("registration_categories",1).containsEntry("registration_fee_rates",1);
        assertThat(sqls).allMatch(sql -> !sql.contains("`seq`") && sql.startsWith("INSERT INTO "));
        assertThat(inserts.get(0)).containsExactly(2L,"REGULAR");
        assertThat(inserts.get(1)).containsExactly(101L,"KRW");
        assertThat(original).containsEntry("conferenceSeq",1L).containsEntry("seq",10L);
    }

    @Test
    void shiftsDatesAndTimesAndRejectsReferencesOutsideCopiedConference() {
        assertThat(ConferenceCopyService.shift(LocalDate.of(2027,5,9), -432)).isEqualTo(LocalDate.of(2026,3,3));
        assertThat(ConferenceCopyService.shift(Timestamp.valueOf("2027-05-09 12:30:00"),-432))
                .isEqualTo(Timestamp.valueOf("2026-03-03 12:30:00"));
        assertThat(ConferenceCopyService.remap(Map.of(1L,9L),1L)).isEqualTo(9L);
        assertThatThrownBy(() -> ConferenceCopyService.remap(Map.of(1L,9L),2L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void copiesImagesIntoIndependentMonthlyFilesAndReportsMissingSources() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var original = directory.resolve("speakers/202609/source.png");
        Files.createDirectories(original.getParent());
        Files.write(original, new byte[]{1, 2, 3});
        when(jdbc.queryForList("SELECT * FROM speakers WHERE conferenceSeq = ? ORDER BY seq", 1L))
                .thenReturn(List.of(
                        Map.of("seq", 10L, "conferenceSeq", 1L, "profileImageSaveFilename", "202609/source.png"),
                        Map.of("seq", 11L, "conferenceSeq", 1L, "profileImageSaveFilename", "202609/missing.png")));
        var inserted = new ArrayList<Object>();
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(call -> {
            var connection = mock(Connection.class);
            var statement = mock(PreparedStatement.class);
            when(connection.prepareStatement(anyString(), anyInt())).thenReturn(statement);
            doAnswer(value -> { inserted.add(value.getArgument(1)); return null; })
                    .when(statement).setObject(anyInt(), any());
            call.<PreparedStatementCreator>getArgument(0).createPreparedStatement(connection);
            call.<KeyHolder>getArgument(1).getKeyList().add(Map.of("seq", 100L + inserted.size()));
            return 1;
        });
        var result = copySpeakers(jdbc);
        assertThat(result.copiedCounts()).containsEntry("speakers", 2);
        assertThat(result.skippedFiles()).containsExactly("speakers/202609/missing.png");
        assertThat(inserted).containsNull().doesNotContain("202609/source.png", "202609/missing.png");
        String saved = inserted.stream().filter(String.class::isInstance).map(String.class::cast).findFirst().orElseThrow();
        assertThat(saved).matches("[0-9]{6}/[0-9a-f-]{36}\\.png");
        assertThat(Files.readAllBytes(directory.resolve("speakers").resolve(saved))).containsExactly(1, 2, 3);
        assertThat(Files.readAllBytes(original)).containsExactly(1, 2, 3);
    }

    @Test
    void cleansUpNewFilesWhenDatabaseInsertFailsAndKeepsOriginal() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var original = directory.resolve("speakers/202609/source.png");
        Files.createDirectories(original.getParent());
        Files.write(original, new byte[]{1, 2, 3});
        when(jdbc.queryForList("SELECT * FROM speakers WHERE conferenceSeq = ? ORDER BY seq", 1L))
                .thenReturn(List.of(Map.of("seq", 10L, "conferenceSeq", 1L,
                        "profileImageSaveFilename", "202609/source.png")));
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("test insert failure"));
        assertThatThrownBy(() -> copySpeakers(jdbc)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        try (var paths = Files.walk(directory)) {
            assertThat(paths.filter(Files::isRegularFile).toList()).containsExactly(original);
        }
    }

    private ConferenceCopyService.Result copySpeakers(JdbcTemplate jdbc) {
        var conferences = mock(ConferenceSettingsService.class);
        var properties = new UploadProperties();
        properties.setBaseDirectory(directory.toString());
        var settings = ConferenceSettingsSaveAllRequest.builder().published(true).eventName("135")
                .eventStartDate(LocalDate.of(2026, 3, 3)).eventEndDate(LocalDate.of(2026, 3, 6)).build();
        when(conferences.getSettings(1L)).thenReturn(ConferenceSettingsResponse.builder()
                .seq(1L).sitePath("2026_136").eventStartDate(LocalDate.of(2027, 5, 9)).build());
        when(conferences.saveConfigured(null, settings)).thenReturn(ConferenceSettingsResponse.builder()
                .seq(2L).sitePath("2026_135").eventStartDate(settings.getEventStartDate()).build());
        return new ConferenceCopyService(jdbc, conferences, new UploadStorage(properties))
                .copy(1L, new ConferenceCopyService.Request(settings, Set.of("speakers")));
    }

    @Test
    void pastConferenceRangeAndWorkflowHaveNoFixedDateBoundary() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM conference_settings WHERE seq = ?",Integer.class,3L)).thenReturn(1);
        when(jdbc.queryForObject("SELECT eventStartDate FROM conference_settings WHERE seq = ?",LocalDate.class,3L))
                .thenReturn(LocalDate.of(2025,10,3));
        var service = new DailyDashboardTestDataService(jdbc,null,null);
        var range = service.conferenceRange(3L);
        assertThat(range.startDate()).isEqualTo(LocalDate.of(2025,8,4));
        assertThat(range.endDate()).isEqualTo(LocalDate.of(2025,10,2));
        assertThat(DailyDashboardTestDataService.dailyAbstractSampleIndex(range.startDate(),range.startDate(),1)).isEqualTo(1);
        assertThat(DailyDashboardTestDataService.dailyAbstractSampleIndex(range.startDate(),range.startDate().plusDays(1),1))
                .isEqualTo(1 + DailyDashboardTestDataService.dailyAbstractDesiredCount(range.startDate()));
        assertThat(DailyDashboardTestDataService.unreviewedPlan(1,10L).status()).isEqualTo("submitted");
        assertThat(DailyDashboardTestDataService.unreviewedPlan(5,10L).status()).isEqualTo("draft");
        assertThat(DailyDashboardTestDataService.unreviewedPlan(1,10L).acceptedPresentationTypeCode()).isNull();
    }
}
