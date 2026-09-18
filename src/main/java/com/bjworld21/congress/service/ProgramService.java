package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.dto.ConferenceSettingsResponse;
import com.bjworld21.congress.dto.ProgramDayRequest;
import com.bjworld21.congress.dto.ProgramDayRoomRequest;
import com.bjworld21.congress.dto.ProgramItemRequest;
import com.bjworld21.congress.dto.ProgramItemPersonRequest;
import com.bjworld21.congress.dto.ProgramManagementResponse;
import com.bjworld21.congress.dto.ProgramRoomRequest;
import com.bjworld21.congress.entity.ProgramDay;
import com.bjworld21.congress.entity.ProgramDayRoom;
import com.bjworld21.congress.entity.ProgramItem;
import com.bjworld21.congress.entity.ProgramItemPerson;
import com.bjworld21.congress.entity.ProgramRoom;
import com.bjworld21.congress.repository.CountryRepository;
import com.bjworld21.congress.repository.ProgramRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class ProgramService {
    private static final Set<String> SCOPE_TYPES = Set.of("ROOM", "ALL_ROOMS");
    private static final Set<String> ITEM_TYPES = Set.of(
            "SESSION", "PRESENTATION", "ABSTRACT_PRESENTATION", "PLENARY", "CEREMONY", "BREAK",
            "MEAL", "REGISTRATION", "SOCIAL", "OTHER"
    );
    private static final Set<String> ROW_STYLES = Set.of("DEFAULT", "SECTION", "HIGHLIGHT", "MUTED");
    private static final Set<String> ROLE_TYPES = Set.of("ORGANIZER", "SPEAKER", "CHAIR");

    private final ProgramRepository programRepository;
    private final CountryRepository countryRepository;
    private final ConferenceSettingsService conferenceSettingsService;
    private final PersonalDataProperties personalDataProperties;

    public ProgramService(
            ProgramRepository programRepository,
            CountryRepository countryRepository,
            ConferenceSettingsService conferenceSettingsService,
            PersonalDataProperties personalDataProperties
    ) {
        this.programRepository = programRepository;
        this.countryRepository = countryRepository;
        this.conferenceSettingsService = conferenceSettingsService;
        this.personalDataProperties = personalDataProperties;
    }

    public ProgramManagementResponse getManagementData(Long conferenceSeq) {
        return ProgramManagementResponse.builder()
                .days(programRepository.findAllDays(conferenceSeq))
                .rooms(programRepository.findAllRooms(conferenceSeq))
                .dayRooms(programRepository.findAllDayRooms(conferenceSeq))
                .items(programRepository.findAllItems(conferenceSeq, dbEncString()))
                .people(programRepository.findAllPeople(conferenceSeq, dbEncString()))
                .countries(countryRepository.findUsed())
                .build();
    }

    public ProgramManagementResponse searchManagementData(Long conferenceSeq, String keyword, String itemType, Boolean enabled) {
        ProgramManagementResponse data = getManagementData(conferenceSeq);
        String query = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String type = itemType == null ? "" : itemType.trim();
        if (!type.isEmpty() && !ITEM_TYPES.contains(type)) {
            throw new IllegalArgumentException("지원하지 않는 프로그램 유형입니다.");
        }
        Set<Long> matchingPeople = new HashSet<>();
        for (ProgramItemPerson person : data.getPeople()) {
            if (Boolean.TRUE.equals(person.getEnabled()) && containsKeyword(query, person.getPersonName(), person.getAffiliation())) {
                matchingPeople.add(person.getProgramItemSeq());
            }
        }
        // 모든 일자의 검색 결과를 집계한다. 편집용 원본과 세션 관계는 별도로 유지한다.
        List<ProgramItem> matches = data.getItems().stream()
                .filter(item -> type.isEmpty() || type.equals(item.getItemType()))
                .filter(item -> enabled == null || enabled.equals(item.getEnabled()))
                .filter(item -> query.isEmpty() || matchingPeople.contains(item.getSeq())
                        || containsKeyword(query, item.getTitle(), item.getSubtitle(), item.getNotes(),
                        item.getOrganizerText(), item.getSpeakerText(), item.getChairText()))
                .toList();
        data.setMatchedItemSeqs(matches.stream().map(ProgramItem::getSeq).toList());
        data.setMatchedCount(matches.size());
        data.setMatchedEnabledCount(matches.stream().filter(item -> Boolean.TRUE.equals(item.getEnabled())).count());
        data.setMatchedDisabledCount(data.getMatchedCount() - data.getMatchedEnabledCount());
        return data;
    }

    private boolean containsKeyword(String query, String... values) {
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return false;
    }

    public ProgramDay createDay(Long conferenceSeq, ProgramDayRequest request) {
        ProgramDay day = normalizeDay(conferenceSeq, null, request);
        programRepository.insertDay(day);
        return programRepository.findDayBySeq(conferenceSeq, day.getSeq());
    }

    public ProgramDay updateDay(Long conferenceSeq, Long seq, ProgramDayRequest request) {
        findDay(conferenceSeq, seq);
        ProgramDay day = normalizeDay(conferenceSeq, seq, request);
        programRepository.updateDay(day);
        return programRepository.findDayBySeq(conferenceSeq, seq);
    }

    @Transactional
    public void deleteDay(Long conferenceSeq, Long seq) {
        findDay(conferenceSeq, seq);
        programRepository.deleteDay(conferenceSeq, seq);
    }

    public ProgramRoom createRoom(Long conferenceSeq, ProgramRoomRequest request) {
        ProgramRoom room = normalizeRoom(conferenceSeq, null, request);
        programRepository.insertRoom(room);
        return programRepository.findRoomBySeq(conferenceSeq, room.getSeq());
    }

    public ProgramRoom updateRoom(Long conferenceSeq, Long seq, ProgramRoomRequest request) {
        findRoom(conferenceSeq, seq);
        ProgramRoom room = normalizeRoom(conferenceSeq, seq, request);
        programRepository.updateRoom(room);
        return programRepository.findRoomBySeq(conferenceSeq, seq);
    }

    @Transactional
    public void deleteRoom(Long conferenceSeq, Long seq) {
        ProgramRoom room = findRoom(conferenceSeq, seq);
        if (programRepository.countDayRoomsByRoom(conferenceSeq, seq) > 0
                || programRepository.countItemsByRoom(conferenceSeq, seq) > 0) {
            throw new IllegalArgumentException("'" + room.getRoomName() + "' 룸은 일자 또는 프로그램에서 사용 중이어서 삭제할 수 없습니다.");
        }
        programRepository.deleteRoom(conferenceSeq, seq);
    }

    @Transactional
    public List<ProgramDayRoom> replaceDayRooms(
            Long conferenceSeq,
            Long programDaySeq,
            List<ProgramDayRoomRequest> requests
    ) {
        findDay(conferenceSeq, programDaySeq);
        List<ProgramDayRoomRequest> safeRequests = requests == null ? List.of() : requests;
        Set<Long> requestedRoomSeqs = new HashSet<>();

        for (ProgramDayRoomRequest request : safeRequests) {
            if (request == null || request.getRoomSeq() == null) {
                throw new IllegalArgumentException("배정할 룸을 선택해주세요.");
            }
            if (!requestedRoomSeqs.add(request.getRoomSeq())) {
                throw new IllegalArgumentException("같은 룸을 한 일자에 중복 배정할 수 없습니다.");
            }
            findRoom(conferenceSeq, request.getRoomSeq());
            validateSortOrder(request.getSortOrder(), "룸 표시 순서");
            validateLength(request.getTabName(), 255, "룸 탭 표시명");
        }

        for (ProgramDayRoom existing : programRepository.findDayRoomsByDay(conferenceSeq, programDaySeq)) {
            if (!requestedRoomSeqs.contains(existing.getRoomSeq())
                    && programRepository.countItemsByDayAndRoom(
                            conferenceSeq, programDaySeq, existing.getRoomSeq()
                    ) > 0) {
                ProgramRoom room = findRoom(conferenceSeq, existing.getRoomSeq());
                throw new IllegalArgumentException("'" + room.getRoomName() + "' 룸에 프로그램이 등록되어 있어 배정을 해제할 수 없습니다.");
            }
        }

        programRepository.deleteDayRoomsByDay(conferenceSeq, programDaySeq);
        for (ProgramDayRoomRequest request : safeRequests) {
            programRepository.insertDayRoom(ProgramDayRoom.builder()
                    .programDaySeq(programDaySeq)
                    .roomSeq(request.getRoomSeq())
                    .tabName(normalizeOptional(request.getTabName()))
                    .sortOrder(defaultSortOrder(request.getSortOrder()))
                    .enabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled())
                    .build());
        }
        return programRepository.findDayRoomsByDay(conferenceSeq, programDaySeq);
    }

    @Transactional
    public ProgramItem createItem(Long conferenceSeq, ProgramItemRequest request) {
        if (request != null && request.getAbstractSubmissionSeq() != null
                && !"approved".equals(programRepository.lockAbstractStatus(
                        conferenceSeq, request.getAbstractSubmissionSeq()
                ))) {
            throw new IllegalArgumentException("채택된 초록만 프로그램에 연결할 수 있습니다.");
        }
        ProgramItem item = normalizeItem(conferenceSeq, null, request);
        List<ProgramItemPerson> people = normalizePeople(request == null ? null : request.getPeople());
        applyLegacyRoleText(item, request == null ? null : request.getPeople(), people);
        programRepository.insertItem(item, dbEncString());
        replacePeople(item.getSeq(), request == null ? null : request.getPeople(), people);
        return programRepository.findItemBySeq(conferenceSeq, item.getSeq(), dbEncString());
    }

    @Transactional
    public ProgramItem updateItem(Long conferenceSeq, Long seq, ProgramItemRequest request) {
        String status = request != null && request.getAbstractSubmissionSeq() != null
                ? programRepository.lockAbstractStatus(conferenceSeq, request.getAbstractSubmissionSeq()) : null;
        ProgramItem existing = programRepository.lockItem(conferenceSeq, seq, dbEncString());
        if (existing == null) throw new IllegalArgumentException("존재하지 않는 프로그램 항목입니다.");
        if (existing.getAbstractSubmissionSeq() != null
                && (request == null || !Objects.equals(existing.getAbstractSubmissionSeq(), request.getAbstractSubmissionSeq()))) {
            throw new IllegalArgumentException("연결된 초록은 초록 편성 메뉴에서 배정을 해제한 후 변경해주세요.");
        }
        if (request != null && request.getAbstractSubmissionSeq() != null && existing.getAbstractSubmissionSeq() == null
                && !"approved".equals(status)) {
            throw new IllegalArgumentException("채택된 초록만 프로그램에 연결할 수 있습니다.");
        }
        ProgramItem item = normalizeItem(conferenceSeq, seq, request);
        List<ProgramItemPerson> people = normalizePeople(request == null ? null : request.getPeople());
        applyLegacyRoleText(item, request == null ? null : request.getPeople(), people);
        validateChildren(conferenceSeq, item);
        programRepository.updateItem(conferenceSeq, item, dbEncString());
        replacePeople(seq, request == null ? null : request.getPeople(), people);
        return programRepository.findItemBySeq(conferenceSeq, seq, dbEncString());
    }

    @Transactional
    public void deleteItem(Long conferenceSeq, Long seq) {
        findItem(conferenceSeq, seq);
        programRepository.deleteItem(conferenceSeq, seq);
    }

    private ProgramDay normalizeDay(Long conferenceSeq, Long seq, ProgramDayRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("프로그램 일자 정보가 필요합니다.");
        }
        LocalDate eventDate = request.getEventDate();
        Integer dayNumber = request.getDayNumber();
        if (eventDate == null) {
            throw new IllegalArgumentException("프로그램 진행 일자는 필수입니다.");
        }
        if (dayNumber == null || dayNumber < 1) {
            throw new IllegalArgumentException("행사 일차는 1 이상이어야 합니다.");
        }
        validateSortOrder(request.getSortOrder(), "일자 표시 순서");
        validateLength(request.getDayTitle(), 255, "일자별 제목");
        validateLength(request.getTheme(), 255, "일자별 주제");
        validateEventDate(conferenceSeq, eventDate);

        if (programRepository.findDayByDate(conferenceSeq, eventDate, seq) != null) {
            throw new IllegalArgumentException("이미 등록된 프로그램 일자입니다.");
        }
        if (programRepository.findDayByNumber(conferenceSeq, dayNumber, seq) != null) {
            throw new IllegalArgumentException("이미 등록된 행사 일차입니다.");
        }

        return ProgramDay.builder()
                .seq(seq)
                .conferenceSeq(conferenceSeq)
                .eventDate(eventDate)
                .dayNumber(dayNumber)
                .dayTitle(normalizeOptional(request.getDayTitle()))
                .theme(normalizeOptional(request.getTheme()))
                .sortOrder(defaultSortOrder(request.getSortOrder()))
                .enabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled())
                .build();
    }

    private ProgramRoom normalizeRoom(Long conferenceSeq, Long seq, ProgramRoomRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("프로그램 룸 정보가 필요합니다.");
        }
        String roomCode = normalizeRequired(request.getRoomCode(), "룸 코드는 필수입니다.").toUpperCase(Locale.ROOT);
        String roomName = normalizeRequired(request.getRoomName(), "룸 이름은 필수입니다.");
        if (!roomCode.matches("[A-Z0-9_-]{1,50}")) {
            throw new IllegalArgumentException("룸 코드는 영문, 숫자, 하이픈, 밑줄만 사용할 수 있습니다.");
        }
        validateLength(roomName, 255, "룸 이름");
        validateLength(request.getLocation(), 500, "룸 위치 또는 설명");
        validateSortOrder(request.getSortOrder(), "룸 표시 순서");
        if (programRepository.findRoomByCode(conferenceSeq, roomCode, seq) != null) {
            throw new IllegalArgumentException("이미 사용 중인 룸 코드입니다.");
        }

        return ProgramRoom.builder()
                .seq(seq)
                .conferenceSeq(conferenceSeq)
                .roomCode(roomCode)
                .roomName(roomName)
                .location(normalizeOptional(request.getLocation()))
                .sortOrder(defaultSortOrder(request.getSortOrder()))
                .enabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled())
                .build();
    }

    private ProgramItem normalizeItem(Long conferenceSeq, Long seq, ProgramItemRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("프로그램 항목 정보가 필요합니다.");
        }
        ProgramDay day = findDay(conferenceSeq, request.getProgramDaySeq());
        String scopeType = normalizeChoice(request.getScopeType(), "ROOM", SCOPE_TYPES, "룸 적용 범위");
        String itemType = normalizeChoice(request.getItemType(), null, ITEM_TYPES, "프로그램 유형");
        String rowStyle = normalizeChoice(request.getRowStyle(), "DEFAULT", ROW_STYLES, "행 스타일");
        LocalTime startTime = request.getStartTime();
        LocalTime endTime = request.getEndTime();
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("프로그램 시작 시간과 종료 시간은 필수입니다.");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("프로그램 종료 시간은 시작 시간보다 늦어야 합니다.");
        }

        Long roomSeq = request.getRoomSeq();
        if ("ROOM".equals(scopeType)) {
            if (roomSeq == null) {
                throw new IllegalArgumentException("룸별 프로그램은 룸을 선택해야 합니다.");
            }
            findRoom(conferenceSeq, roomSeq);
            if (programRepository.countEnabledDayRoom(conferenceSeq, day.getSeq(), roomSeq) == 0) {
                throw new IllegalArgumentException("선택한 룸은 해당 일자에 배정되어 있지 않습니다.");
            }
        } else {
            roomSeq = null;
        }

        String title = normalizeRequired(request.getTitle(), "프로그램명은 필수입니다.");
        validateLength(title, 500, "프로그램명");
        validateLength(request.getSubtitle(), 500, "부제목");
        validateLength(request.getOrganizerText(), 500, "세션 기획자");
        validateLength(request.getSpeakerText(), 500, "발표자");
        validateLength(request.getChairText(), 500, "좌장 또는 진행자");
        validateLength(request.getNotes(), 1000, "비고");
        validateSortOrder(request.getSortOrder(), "프로그램 표시 순서");

        if (request.getAbstractSubmissionSeq() != null
                && programRepository.countAbstractSubmission(
                        conferenceSeq, request.getAbstractSubmissionSeq()
                ) == 0) {
            throw new IllegalArgumentException("연결할 초록을 찾을 수 없습니다.");
        }
        if (request.getAbstractSubmissionSeq() != null
                && !programRepository.findOtherAbstractAssignments(
                        conferenceSeq, request.getAbstractSubmissionSeq(), seq
                ).isEmpty()) {
            throw new IllegalArgumentException("이미 다른 프로그램에 배정된 초록입니다.");
        }

        ProgramItem item = ProgramItem.builder()
                .seq(seq)
                .programDaySeq(day.getSeq())
                .roomSeq(roomSeq)
                .parentSeq(request.getParentSeq())
                .abstractSubmissionSeq(request.getAbstractSubmissionSeq())
                .scopeType(scopeType)
                .itemType(itemType)
                .startTime(startTime)
                .endTime(endTime)
                .title(title)
                .subtitle(normalizeOptional(request.getSubtitle()))
                .organizerText(normalizeOptional(request.getOrganizerText()))
                .speakerText(normalizeOptional(request.getSpeakerText()))
                .chairText(normalizeOptional(request.getChairText()))
                .notes(normalizeOptional(request.getNotes()))
                .rowStyle(rowStyle)
                .sortOrder(defaultSortOrder(request.getSortOrder()))
                .enabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled())
                .build();

        if ("SESSION".equals(item.getItemType()) && item.getParentSeq() != null) {
            throw new IllegalArgumentException("세션은 다른 세션의 하위 항목으로 지정할 수 없습니다.");
        }
        validateParent(conferenceSeq, item);
        return item;
    }

    private void validateParent(Long conferenceSeq, ProgramItem item) {
        if (item.getParentSeq() == null) {
            return;
        }
        if (Objects.equals(item.getSeq(), item.getParentSeq())) {
            throw new IllegalArgumentException("프로그램 항목을 자기 자신의 하위로 지정할 수 없습니다.");
        }
        ProgramItem parent = findItem(conferenceSeq, item.getParentSeq());
        if (!"SESSION".equals(parent.getItemType()) || parent.getParentSeq() != null) {
            throw new IllegalArgumentException("상위 항목은 최상위 세션이어야 합니다.");
        }
        if (!Objects.equals(parent.getProgramDaySeq(), item.getProgramDaySeq())
                || !Objects.equals(parent.getScopeType(), item.getScopeType())
                || !Objects.equals(parent.getRoomSeq(), item.getRoomSeq())) {
            throw new IllegalArgumentException("하위 발표는 상위 세션과 같은 일자와 룸에 있어야 합니다.");
        }
        if (item.getStartTime().isBefore(parent.getStartTime()) || item.getEndTime().isAfter(parent.getEndTime())) {
            throw new IllegalArgumentException("하위 발표 시간은 상위 세션 시간 안에 있어야 합니다.");
        }
    }

    private void validateChildren(Long conferenceSeq, ProgramItem item) {
        List<ProgramItem> children = programRepository.findChildren(
                conferenceSeq, item.getSeq(), dbEncString()
        );
        if (children.isEmpty()) {
            return;
        }
        if (!"SESSION".equals(item.getItemType())) {
            throw new IllegalArgumentException("하위 발표가 있는 항목은 세션 유형을 변경할 수 없습니다.");
        }
        for (ProgramItem child : children) {
            if (!Objects.equals(child.getProgramDaySeq(), item.getProgramDaySeq())
                    || !Objects.equals(child.getScopeType(), item.getScopeType())
                    || !Objects.equals(child.getRoomSeq(), item.getRoomSeq())
                    || child.getStartTime().isBefore(item.getStartTime())
                    || child.getEndTime().isAfter(item.getEndTime())) {
                throw new IllegalArgumentException("세션 변경 후 범위를 벗어나는 하위 발표가 있습니다.");
            }
        }
    }

    private List<ProgramItemPerson> normalizePeople(List<ProgramItemPersonRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream().map(request -> {
            if (request == null) {
                throw new IllegalArgumentException("프로그램 담당자 정보가 올바르지 않습니다.");
            }
            String roleType = normalizeChoice(request.getRoleType(), null, ROLE_TYPES, "담당자 역할");
            String personName = normalizeRequired(request.getPersonName(), "프로그램 담당자 이름은 필수입니다.");
            validateLength(personName, 255, "프로그램 담당자 이름");
            validateLength(request.getAffiliation(), 255, "프로그램 담당자 소속");
            validateSortOrder(request.getSortOrder(), "프로그램 담당자 표시 순서");
            if (request.getCountrySeq() != null && countryRepository.findBySeq(request.getCountrySeq()) == null) {
                throw new IllegalArgumentException("선택한 국가 정보를 찾을 수 없습니다.");
            }
            return ProgramItemPerson.builder()
                    .roleType(roleType)
                    .countrySeq(request.getCountrySeq())
                    .affiliation(normalizeOptional(request.getAffiliation()))
                    .personName(personName)
                    .sortOrder(defaultSortOrder(request.getSortOrder()))
                    .enabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled())
                    .build();
        }).toList();
    }

    private void replacePeople(
            Long programItemSeq,
            List<ProgramItemPersonRequest> requests,
            List<ProgramItemPerson> people
    ) {
        if (requests == null) {
            return;
        }
        programRepository.deletePeopleByItem(programItemSeq);
        for (ProgramItemPerson person : people) {
            person.setProgramItemSeq(programItemSeq);
            programRepository.insertPerson(person, dbEncString());
        }
    }

    private void applyLegacyRoleText(
            ProgramItem item,
            List<ProgramItemPersonRequest> requests,
            List<ProgramItemPerson> people
    ) {
        if (requests == null) {
            return;
        }
        item.setOrganizerText(formatPeopleForLegacyColumn(people, "ORGANIZER"));
        item.setSpeakerText(formatPeopleForLegacyColumn(people, "SPEAKER"));
        item.setChairText(formatPeopleForLegacyColumn(people, "CHAIR"));
    }

    private String formatPeopleForLegacyColumn(List<ProgramItemPerson> people, String roleType) {
        String value = people.stream()
                .filter(person -> roleType.equals(person.getRoleType()) && Boolean.TRUE.equals(person.getEnabled()))
                .sorted((left, right) -> Integer.compare(left.getSortOrder(), right.getSortOrder()))
                .map(person -> person.getAffiliation() == null
                        ? person.getPersonName()
                        : person.getPersonName() + " (" + person.getAffiliation() + ")")
                .reduce((left, right) -> left + "\n" + right)
                .orElse(null);
        if (value != null && value.length() > 500) {
            throw new IllegalArgumentException("역할별 담당자 표시 문구는 합계 500자 이하로 입력해주세요.");
        }
        return value;
    }

    private void validateEventDate(Long conferenceSeq, LocalDate eventDate) {
        ConferenceSettingsResponse settings = conferenceSettingsService.getSettings(conferenceSeq);
        if (settings.getEventStartDate() != null && eventDate.isBefore(settings.getEventStartDate())) {
            throw new IllegalArgumentException("프로그램 일자는 행사 시작일보다 빠를 수 없습니다.");
        }
        if (settings.getEventEndDate() != null && eventDate.isAfter(settings.getEventEndDate())) {
            throw new IllegalArgumentException("프로그램 일자는 행사 종료일보다 늦을 수 없습니다.");
        }
    }

    private ProgramDay findDay(Long conferenceSeq, Long seq) {
        ProgramDay day = seq == null ? null : programRepository.findDayBySeq(conferenceSeq, seq);
        if (day == null) {
            throw new IllegalArgumentException("존재하지 않는 프로그램 일자입니다.");
        }
        return day;
    }

    private ProgramRoom findRoom(Long conferenceSeq, Long seq) {
        ProgramRoom room = seq == null ? null : programRepository.findRoomBySeq(conferenceSeq, seq);
        if (room == null) {
            throw new IllegalArgumentException("존재하지 않는 프로그램 룸입니다.");
        }
        return room;
    }

    private ProgramItem findItem(Long conferenceSeq, Long seq) {
        ProgramItem item = seq == null ? null : programRepository.findItemBySeq(
                conferenceSeq, seq, dbEncString()
        );
        if (item == null) {
            throw new IllegalArgumentException("존재하지 않는 프로그램 항목입니다.");
        }
        return item;
    }

    private String normalizeChoice(String value, String defaultValue, Set<String> allowed, String label) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            normalized = defaultValue;
        }
        if (normalized == null || !allowed.contains(normalized.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("유효한 " + label + "을(를) 선택해주세요.");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private void validateLength(String value, int maxLength, String label) {
        if (value != null && value.trim().length() > maxLength) {
            throw new IllegalArgumentException(label + "은(는) " + maxLength + "자 이하로 입력해주세요.");
        }
    }

    private void validateSortOrder(Integer sortOrder, String label) {
        if (sortOrder != null && sortOrder < 0) {
            throw new IllegalArgumentException(label + "는 0 이상이어야 합니다.");
        }
    }

    private int defaultSortOrder(Integer sortOrder) {
        return sortOrder == null ? 0 : sortOrder;
    }

    private String dbEncString() {
        return personalDataProperties.requireDbEncString();
    }
}
