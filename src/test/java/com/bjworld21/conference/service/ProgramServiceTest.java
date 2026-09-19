package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.ConferenceSettingsResponse;
import com.bjworld21.conference.dto.ProgramDayRequest;
import com.bjworld21.conference.dto.ProgramItemRequest;
import com.bjworld21.conference.dto.ProgramItemPersonRequest;
import com.bjworld21.conference.entity.Country;
import com.bjworld21.conference.entity.ProgramDay;
import com.bjworld21.conference.entity.ProgramItem;
import com.bjworld21.conference.entity.ProgramItemPerson;
import com.bjworld21.conference.entity.ProgramRoom;
import com.bjworld21.conference.repository.ProgramRepository;
import com.bjworld21.conference.repository.CountryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramServiceTest {

    @Mock
    private ProgramRepository repository;

    @Mock
    private CountryRepository countryRepository;

    @Mock
    private ConferenceSettingsService conferenceSettingsService;

    private ProgramService service;

    @BeforeEach
    void setUp() {
        service = new ProgramService(
                repository, countryRepository, conferenceSettingsService, PersonalDataTestSupport.properties()
        );
    }

    @Test
    void programDayMustBeInsideConferencePeriod() {
        when(conferenceSettingsService.getSettings(1L)).thenReturn(ConferenceSettingsResponse.builder()
                .eventStartDate(LocalDate.of(2027, 5, 9))
                .eventEndDate(LocalDate.of(2027, 5, 12))
                .build());

        ProgramDayRequest request = ProgramDayRequest.builder()
                .eventDate(LocalDate.of(2027, 5, 8))
                .dayNumber(1)
                .build();

        assertThatThrownBy(() -> service.createDay(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("행사 시작일보다 빠를 수 없습니다");
    }

    @Test
    void searchMatchesPeopleAcrossDaysAndKeepsEditingContext() {
        ProgramItem first = ProgramItem.builder().seq(10L).programDaySeq(1L).title("Opening")
                .itemType("CEREMONY").enabled(true).build();
        ProgramItem second = ProgramItem.builder().seq(20L).programDaySeq(2L).title("Disease models")
                .itemType("PRESENTATION").enabled(false).build();
        when(repository.findAllItems(eq(1L), anyString())).thenReturn(List.of(first, second));
        when(repository.findAllPeople(eq(1L), anyString())).thenReturn(List.of(
                ProgramItemPerson.builder().programItemSeq(20L).personName("홍길동")
                        .affiliation("DGIST").enabled(true).build()));

        var result = service.searchManagementData(1L, " dgist ", "PRESENTATION", false);
        assertThat(result.getMatchedItemSeqs()).containsExactly(20L);
        assertThat(result.getMatchedCount()).isEqualTo(1);
        assertThat(result.getMatchedEnabledCount()).isZero();
        assertThat(result.getMatchedDisabledCount()).isEqualTo(1);
        assertThat(result.getItems()).containsExactly(first, second);
        assertThat(service.searchManagementData(1L, "홍길동", "", true).getMatchedCount()).isZero();
        assertThat(service.searchManagementData(1L, "opening", "", null).getMatchedItemSeqs()).containsExactly(10L);
        var reset = service.searchManagementData(1L, "", "", null);
        assertThat(reset.getMatchedCount()).isEqualTo(2);
        assertThat(reset.getMatchedEnabledCount()).isEqualTo(1);
    }

    @Test
    void roomProgramRequiresRoomAssignedToDay() {
        when(repository.findDayBySeq(1L, 1L)).thenReturn(programDay());
        when(repository.findRoomBySeq(1L, 10L)).thenReturn(ProgramRoom.builder().seq(10L).roomName("Main Hall").build());
        when(repository.countEnabledDayRoom(1L, 1L, 10L)).thenReturn(0L);

        ProgramItemRequest request = baseItemRequest()
                .roomSeq(10L)
                .scopeType("ROOM")
                .build();

        assertThatThrownBy(() -> service.createItem(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("해당 일자에 배정되어 있지 않습니다");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRESENTATION", "ABSTRACT_PRESENTATION"})
    void childPresentationMustStayInsideParentSessionTime(String presentationType) {
        when(repository.findDayBySeq(1L, 1L)).thenReturn(programDay());
        when(repository.findRoomBySeq(1L, 10L)).thenReturn(ProgramRoom.builder().seq(10L).roomName("Main Hall").build());
        when(repository.countEnabledDayRoom(1L, 1L, 10L)).thenReturn(1L);
        when(repository.findItemBySeq(1L, 100L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(ProgramItem.builder()
                .seq(100L)
                .programDaySeq(1L)
                .roomSeq(10L)
                .scopeType("ROOM")
                .itemType("SESSION")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .build());

        ProgramItemRequest request = baseItemRequest()
                .roomSeq(10L)
                .scopeType("ROOM")
                .itemType(presentationType)
                .parentSeq(100L)
                .startTime(LocalTime.of(8, 50))
                .endTime(LocalTime.of(9, 20))
                .build();

        assertThatThrownBy(() -> service.createItem(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("상위 세션 시간 안에");
    }

    @Test
    void createItemStoresStructuredSpeakerAndLegacyDisplayText() {
        when(repository.findDayBySeq(1L, 1L)).thenReturn(programDay());
        when(countryRepository.findBySeq(20L)).thenReturn(Country.builder()
                .seq(20L)
                .countryName("Japan")
                .build());
        doAnswer(invocation -> {
            ProgramItem item = invocation.getArgument(0);
            item.setSeq(200L);
            return null;
        }).when(repository).insertItem(any(ProgramItem.class), eq(PersonalDataTestSupport.DB_ENC_STRING));
        when(repository.findItemBySeq(1L, 200L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(ProgramItem.builder().seq(200L).build());

        ProgramItemRequest request = baseItemRequest()
                .scopeType("ALL_ROOMS")
                .people(List.of(ProgramItemPersonRequest.builder()
                        .roleType("SPEAKER")
                        .countrySeq(20L)
                        .affiliation("Nagoya University")
                        .personName("Shizue Ohsawa")
                        .sortOrder(0)
                        .enabled(true)
                        .build()))
                .build();

        service.createItem(1L, request);

        var itemCaptor = org.mockito.ArgumentCaptor.forClass(ProgramItem.class);
        verify(repository).insertItem(itemCaptor.capture(), eq(PersonalDataTestSupport.DB_ENC_STRING));
        assertThat(itemCaptor.getValue().getSpeakerText()).isEqualTo("Shizue Ohsawa (Nagoya University)");

        var personCaptor = org.mockito.ArgumentCaptor.forClass(ProgramItemPerson.class);
        verify(repository).insertPerson(personCaptor.capture(), eq(PersonalDataTestSupport.DB_ENC_STRING));
        assertThat(personCaptor.getValue().getProgramItemSeq()).isEqualTo(200L);
        assertThat(personCaptor.getValue().getRoleType()).isEqualTo("SPEAKER");
        assertThat(personCaptor.getValue().getCountrySeq()).isEqualTo(20L);
    }

    @Test
    void abstractPresentationCanBeScheduledBeforeAnAbstractIsAssigned() {
        when(repository.findDayBySeq(1L, 1L)).thenReturn(programDay());
        when(repository.findItemBySeq(1L, 100L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(ProgramItem.builder()
                .seq(100L).programDaySeq(1L).scopeType("ALL_ROOMS").itemType("SESSION")
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0)).build());

        service.createItem(1L, baseItemRequest().scopeType("ALL_ROOMS")
                .itemType("ABSTRACT_PRESENTATION").parentSeq(100L).build());

        var captor = org.mockito.ArgumentCaptor.forClass(ProgramItem.class);
        verify(repository).insertItem(captor.capture(), eq(PersonalDataTestSupport.DB_ENC_STRING));
        assertThat(captor.getValue().getItemType()).isEqualTo("ABSTRACT_PRESENTATION");
        assertThat(captor.getValue().getParentSeq()).isEqualTo(100L);
        assertThat(captor.getValue().getAbstractSubmissionSeq()).isNull();
    }

    @Test
    void existingPresentationCanBecomeAnAbstractPresentationWithoutChangingItsSchedule() {
        when(repository.findDayBySeq(1L, 1L)).thenReturn(programDay());
        when(repository.lockItem(1L, 200L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(ProgramItem.builder()
                .seq(200L).itemType("PRESENTATION").build());
        when(repository.findChildren(1L, 200L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(List.of());

        service.updateItem(1L, 200L, baseItemRequest()
                .scopeType("ALL_ROOMS").itemType("ABSTRACT_PRESENTATION").build());

        var captor = org.mockito.ArgumentCaptor.forClass(ProgramItem.class);
        verify(repository).updateItem(eq(1L), captor.capture(), eq(PersonalDataTestSupport.DB_ENC_STRING));
        assertThat(captor.getValue().getItemType()).isEqualTo("ABSTRACT_PRESENTATION");
        assertThat(captor.getValue().getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(captor.getValue().getEndTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(captor.getValue().getAbstractSubmissionSeq()).isNull();
    }

    @Test
    void staleProgramEditCannotRemoveAnAssignment() {
        when(repository.lockItem(1L, 200L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(ProgramItem.builder().seq(200L).abstractSubmissionSeq(90L).build());
        assertThatThrownBy(() -> service.updateItem(1L, 200L, baseItemRequest().build()))
                .hasMessageContaining("초록 편성 메뉴에서 배정을 해제");
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).updateItem(anyLong(), any(), anyString());
    }

    @Test
    void manualProgramLinkAlsoRejectsDuplicateAbstract() {
        when(repository.lockAbstractStatus(1L, 90L)).thenReturn("approved");
        when(repository.findDayBySeq(1L, 1L)).thenReturn(programDay());
        when(repository.countAbstractSubmission(1L, 90L)).thenReturn(1L);
        when(repository.findOtherAbstractAssignments(1L, 90L, null)).thenReturn(List.of(200L));
        assertThatThrownBy(() -> service.createItem(1L, baseItemRequest().scopeType("ALL_ROOMS").abstractSubmissionSeq(90L).build()))
                .hasMessageContaining("이미 다른 프로그램");
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).insertItem(any(), anyString());
    }

    private ProgramDay programDay() {
        return ProgramDay.builder()
                .seq(1L)
                .eventDate(LocalDate.of(2027, 5, 9))
                .dayNumber(1)
                .build();
    }

    private ProgramItemRequest.ProgramItemRequestBuilder baseItemRequest() {
        return ProgramItemRequest.builder()
                .programDaySeq(1L)
                .itemType("OTHER")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(9, 30))
                .title("Program")
                .rowStyle("DEFAULT");
    }
}
