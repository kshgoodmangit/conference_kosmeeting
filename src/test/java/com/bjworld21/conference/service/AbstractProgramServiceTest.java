package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.*;
import com.bjworld21.conference.entity.*;
import com.bjworld21.conference.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbstractProgramServiceTest {
    @Mock ProgramRepository programs;
    @Mock AbstractProgramRepository assignments;
    @Mock AbstractSubmissionRepository abstracts;
    @Mock CountryRepository countries;
    @Mock ProgramService programService;
    AbstractProgramService service;

    @BeforeEach
    void setUp() {
        service = new AbstractProgramService(
                programs, assignments, abstracts, countries, programService, new ObjectMapper(),
                PersonalDataTestSupport.properties()
        );
    }

    @Test
    void assignmentUsesActualPresenterAndPreservesScheduleAndOtherRoles() {
        ProgramItem slot = eligibleSlot();
        stubSubmission();
        when(programs.lockSpeakers(10L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(List.of(ProgramItemPerson.builder()
                .roleType("SPEAKER").personName("TBD").sortOrder(0).enabled(true).build()));

        ProgramItem result = service.assign(1L, 10L, 90L);

        assertThat(result.getTitle()).isEqualTo("Accepted abstract");
        assertThat(result.getSpeakerText()).isEqualTo("Presenter (University)");
        assertThat(result.getStartTime()).isEqualTo(LocalTime.of(16, 30));
        assertThat(result.getEndTime()).isEqualTo(LocalTime.of(16, 35));
        assertThat(result.getRoomSeq()).isEqualTo(3L);
        assertThat(result.getChairText()).isEqualTo("Existing chair");
        assertThat(result.getAbstractSubmissionSeq()).isEqualTo(90L);
        verify(programs).updateAbstractAssignment(1L, slot, PersonalDataTestSupport.DB_ENC_STRING);
        verify(programs, never()).updateItem(anyLong(), any(), anyString());
        verify(programs, never()).deletePeopleByItem(anyLong());
        var person = ArgumentCaptor.forClass(ProgramItemPerson.class);
        verify(programs).insertPerson(person.capture(), eq(PersonalDataTestSupport.DB_ENC_STRING));
        assertThat(person.getValue().getPersonName()).isEqualTo("Presenter");
        assertThat(person.getValue().getCountrySeq()).isEqualTo(5L);
        var snapshot = ArgumentCaptor.forClass(ProgramAbstractSnapshot.class);
        verify(assignments).insertSnapshot(snapshot.capture(), eq(PersonalDataTestSupport.DB_ENC_STRING));
        assertThat(snapshot.getValue().getOriginalTitle()).isEqualTo("Flash Talk Speaker 1 (TBD)");
        assertThat(snapshot.getValue().getOriginalSpeakersJson()).contains("TBD");
        var order = inOrder(programs);
        order.verify(programs).lockAbstractStatus(1L, 90L);
        order.verify(programs).lockItem(1L, 10L, PersonalDataTestSupport.DB_ENC_STRING);
    }

    @ParameterizedTest
    @ValueSource(strings = {"submitted", "rejected", "withdrawn"})
    void onlyApprovedAbstractsCanBeAssigned(String status) {
        when(programs.lockAbstractStatus(1L, 90L)).thenReturn(status);
        assertThatThrownBy(() -> service.assign(1L, 10L, 90L)).hasMessageContaining("채택된 초록만");
        verify(programs, never()).lockItem(anyLong(), anyLong(), anyString());
        verifyNoInteractions(assignments);
    }

    @Test
    void concurrentAssignmentCannotOverwriteOccupiedSlot() {
        eligibleSlot().setAbstractSubmissionSeq(91L);
        assertThatThrownBy(() -> service.assign(1L, 10L, 90L)).hasMessageContaining("이미 초록이 배정된 시간");
        verifyNoInteractions(assignments);
    }

    @Test
    void sameAbstractCannotBeAssignedTwiceIncludingInactivePrograms() {
        eligibleSlot();
        when(programs.findOtherAbstractAssignments(1L, 90L, 10L)).thenReturn(List.of(11L));
        assertThatThrownBy(() -> service.assign(1L, 10L, 90L)).hasMessageContaining("이미 다른 프로그램");
        verifyNoInteractions(assignments);
    }

    @Test
    void inactiveDayCannotReceiveAssignment() {
        ProgramItem slot = slot();
        when(programs.lockAbstractStatus(1L, 90L)).thenReturn("approved");
        when(programs.lockItem(1L, 10L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(slot);
        when(programs.findDayBySeq(1L, 1L)).thenReturn(ProgramDay.builder().seq(1L).enabled(false).build());
        assertThatThrownBy(() -> service.assign(1L, 10L, 90L)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(assignments);
    }

    @Test
    void inactiveParentSessionCannotReceiveAssignment() {
        eligibleSlot().setParentSeq(100L);
        when(programs.findItemBySeq(1L, 100L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(ProgramItem.builder().seq(100L).enabled(false).build());
        assertThatThrownBy(() -> service.assign(1L, 10L, 90L)).hasMessageContaining("상위 세션");
        verifyNoInteractions(assignments);
    }

    @Test
    void missingPresenterIsNotReplacedWithMainAuthor() {
        eligibleSlot();
        when(abstracts.findBySeq(1L, 90L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(AbstractSubmissionResponse.builder().title("Title").mainAuthorName("Main author").build());
        assertThatThrownBy(() -> service.assign(1L, 10L, 90L)).hasMessageContaining("발표자가 지정되지 않았습니다");
        verifyNoInteractions(assignments);
    }

    @Test
    void releaseRestoresOriginalTitleAndStructuredSpeakers() {
        ProgramItem slot = assignedSlot();
        ProgramAbstractSnapshot snapshot = snapshot();
        snapshot.setOriginalSpeakersJson("[{\"countrySeq\":5,\"affiliation\":\"Original university\",\"personName\":\"TBD\",\"sortOrder\":10,\"enabled\":true}]");
        when(assignments.lockSnapshot(1L, 10L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(snapshot);

        service.release(1L, 10L, 90L, true);

        assertThat(slot.getAbstractSubmissionSeq()).isNull();
        assertThat(slot.getTitle()).isEqualTo("Flash Talk Speaker 1 (TBD)");
        assertThat(slot.getStartTime()).isEqualTo(LocalTime.of(16, 30));
        assertThat(slot.getChairText()).isEqualTo("Existing chair");
        var person = ArgumentCaptor.forClass(ProgramItemPerson.class);
        verify(programs).insertPerson(person.capture(), eq(PersonalDataTestSupport.DB_ENC_STRING));
        assertThat(person.getValue().getPersonName()).isEqualTo("TBD");
        assertThat(person.getValue().getRoleType()).isEqualTo("SPEAKER");
        verify(assignments).deleteSnapshot(1L, 10L);
    }

    @Test
    void manualTitleChangesAreNotOverwrittenByStaleRestoreRequest() {
        assignedSlot().setTitle("Manually edited title");
        when(assignments.lockSnapshot(1L, 10L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(snapshot());
        assertThatThrownBy(() -> service.release(1L, 10L, 90L, true)).hasMessageContaining("변경되었습니다");
        verify(programs, never()).updateAbstractAssignment(anyLong(), any(), anyString());
        verify(programs, never()).deleteSpeakers(anyLong());
    }

    @Test
    void legacyAssignmentCanBeReleasedWithoutDeletingCurrentSpeakerDetails() {
        ProgramItem slot = assignedSlot();
        service.release(1L, 10L, 90L, false);
        assertThat(slot.getAbstractSubmissionSeq()).isNull();
        assertThat(slot.getTitle()).isEqualTo("Accepted abstract");
        assertThat(slot.getSpeakerText()).isEqualTo("Presenter (University)");
        verify(programs, never()).deleteSpeakers(anyLong());
    }

    @Test
    void staleReleaseCannotRemoveDifferentAssignment() {
        assignedSlot().setAbstractSubmissionSeq(91L);
        assertThatThrownBy(() -> service.release(1L, 10L, 90L, false)).hasMessageContaining("배정 정보가 변경");
        verify(programs, never()).updateAbstractAssignment(anyLong(), any(), anyString());
        verifyNoInteractions(assignments);
    }

    @Test
    void lastPageIsClampedWhenAssignmentRemovesLastSearchResult() {
        when(assignments.countCandidates("", null, null, "free", PersonalDataTestSupport.DB_ENC_STRING, 1L)).thenReturn(6L);
        when(programService.getManagementData(1L)).thenReturn(ProgramManagementResponse.builder()
                .items(List.of()).people(List.of()).build());
        AbstractProgramResponse result = service.getData(1L, 2, 6, "", null, null, "free");
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
        verify(assignments).findCandidates("", null, null, "free", 6, 0L, PersonalDataTestSupport.DB_ENC_STRING, 1L);
    }

    private ProgramItem slot() {
        return ProgramItem.builder().seq(10L).programDaySeq(1L).roomSeq(3L).scopeType("ROOM")
                .itemType("ABSTRACT_PRESENTATION").enabled(true).title("Flash Talk Speaker 1 (TBD)")
                .chairText("Existing chair").startTime(LocalTime.of(16, 30)).endTime(LocalTime.of(16, 35)).build();
    }

    private ProgramItem eligibleSlot() {
        ProgramItem slot = slot();
        when(programs.lockAbstractStatus(1L, 90L)).thenReturn("approved");
        when(programs.lockItem(1L, 10L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(slot);
        when(programs.findDayBySeq(1L, 1L)).thenReturn(ProgramDay.builder().seq(1L).enabled(true).build());
        when(programs.findRoomBySeq(1L, 3L)).thenReturn(ProgramRoom.builder().seq(3L).enabled(true).build());
        when(programs.countEnabledDayRoom(1L, 1L, 3L)).thenReturn(1L);
        return slot;
    }

    private void stubSubmission() {
        when(abstracts.findBySeq(1L, 90L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(AbstractSubmissionResponse.builder().title("Accepted abstract").build());
        when(abstracts.findAuthorsByAbstractSeq(90L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(List.of(
                AbstractSubmissionAuthor.builder().authorName("Main author").isPresentingAuthor(false).build(),
                AbstractSubmissionAuthor.builder().authorName("Presenter").institutionNo(1).isPresentingAuthor(true).build()));
        when(abstracts.findInstitutionsByAbstractSeq(90L)).thenReturn(List.of(
                AbstractSubmissionInstitution.builder().institutionNo(1).institutionName("University").country("KR").build()));
        when(countries.findUsed()).thenReturn(List.of(Country.builder().seq(5L).isoAlpha2("KR").build()));
    }

    private ProgramItem assignedSlot() {
        ProgramItem slot = slot();
        slot.setAbstractSubmissionSeq(90L);
        slot.setTitle("Accepted abstract");
        slot.setSpeakerText("Presenter (University)");
        when(programs.lockItem(1L, 10L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(slot);
        return slot;
    }

    private ProgramAbstractSnapshot snapshot() {
        ProgramAbstractSnapshot snapshot = new ProgramAbstractSnapshot();
        snapshot.setAbstractSubmissionSeq(90L);
        snapshot.setOriginalTitle("Flash Talk Speaker 1 (TBD)");
        snapshot.setOriginalSpeakersJson("[]");
        snapshot.setAssignedTitle("Accepted abstract");
        snapshot.setAssignedSpeakerText("Presenter (University)");
        snapshot.setAssignedSpeakersJson("[]");
        return snapshot;
    }
}
