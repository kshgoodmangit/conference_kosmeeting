package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.dto.*;
import com.bjworld21.congress.entity.*;
import com.bjworld21.congress.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AbstractProgramService {
    private final ProgramRepository programs;
    private final AbstractProgramRepository assignments;
    private final AbstractSubmissionRepository abstracts;
    private final CountryRepository countries;
    private final ProgramService programService;
    private final ObjectMapper objectMapper;
    private final PersonalDataProperties personalDataProperties;

    @Transactional(readOnly = true)
    public AbstractProgramResponse getData(Long conferenceSeq, int page, int size, String keyword, Long presentationTypeCode,
                                          Long categoryCode, String assignmentStatus) {
        if (!Set.of("free", "assigned", "all").contains(assignmentStatus)) {
            throw new IllegalArgumentException("유효한 배정 상태를 선택해주세요.");
        }
        String search = keyword == null ? "" : keyword.trim();
        if (search.length() > 200) throw new IllegalArgumentException("검색어는 200자 이하로 입력해주세요.");
        int pageSize = Math.max(1, Math.min(size, 50));
        long total = assignments.countCandidates(
                search, presentationTypeCode, categoryCode, assignmentStatus, dbEncString(), conferenceSeq
        );
        int totalPages = (int) Math.max(1, (total + pageSize - 1) / pageSize);
        int currentPage = Math.max(1, Math.min(page, totalPages));
        ProgramManagementResponse program = programService.getManagementData(conferenceSeq);
        Map<Long, AbstractSubmissionResponse> linked = assignments.findLinkedAbstracts(conferenceSeq).stream()
                .collect(Collectors.toMap(AbstractSubmissionResponse::getSeq, Function.identity()));
        Map<Long, ProgramAbstractSnapshot> snapshots = assignments.findSnapshots(conferenceSeq, dbEncString()).stream()
                .collect(Collectors.toMap(ProgramAbstractSnapshot::getProgramItemSeq, Function.identity()));
        Map<Long, List<ProgramItemPerson>> speakers = program.getPeople().stream()
                .filter(person -> "SPEAKER".equals(person.getRoleType()))
                .collect(Collectors.groupingBy(ProgramItemPerson::getProgramItemSeq));
        List<AbstractProgramResponse.Assignment> links = program.getItems().stream()
                .filter(item -> item.getAbstractSubmissionSeq() != null)
                .map(item -> {
                    AbstractSubmissionResponse submission = linked.get(item.getAbstractSubmissionSeq());
                    return new AbstractProgramResponse.Assignment(item.getSeq(), item.getAbstractSubmissionSeq(),
                            submission == null ? null : submission.getSubmissionNo(),
                            submission == null ? null : submission.getStatus(),
                            canRestore(item, speakers.getOrDefault(item.getSeq(), List.of()), snapshots.get(item.getSeq())));
                }).toList();
        return new AbstractProgramResponse(program, links, abstracts.findEnabledPresentationTypes(),
                abstracts.findEnabledCategories(), assignments.findCandidates(search, presentationTypeCode,
                categoryCode, assignmentStatus, pageSize, (long) (currentPage - 1) * pageSize,
                dbEncString(), conferenceSeq),
                currentPage, pageSize, total, totalPages);
    }

    @Transactional
    public ProgramItem assign(Long conferenceSeq, Long itemSeq, Long abstractSeq) {
        if (abstractSeq == null || abstractSeq < 1) throw new IllegalArgumentException("배정할 초록을 선택해주세요.");
        // All assignment writers lock the abstract first, then the program item.
        if (!"approved".equals(programs.lockAbstractStatus(conferenceSeq, abstractSeq))) {
            throw new IllegalArgumentException("채택된 초록만 배정할 수 있습니다. 초록 상태를 확인해주세요.");
        }
        ProgramItem item = requireItem(programs.lockItem(conferenceSeq, itemSeq, dbEncString()));
        requireEligible(conferenceSeq, item);
        if (item.getAbstractSubmissionSeq() != null) throw new IllegalStateException("이미 초록이 배정된 시간입니다. 새로고침 후 확인해주세요.");
        if (!programs.findOtherAbstractAssignments(conferenceSeq, abstractSeq, itemSeq).isEmpty()) {
            throw new IllegalStateException("이미 다른 프로그램에 배정된 초록입니다. 기존 배정을 해제한 후 다시 시도해주세요.");
        }
        AbstractSubmissionResponse submission = abstracts.findBySeq(
                conferenceSeq, abstractSeq, personalDataProperties.requireDbEncString()
        );
        if (submission == null) throw new IllegalArgumentException("초록 정보를 찾을 수 없습니다.");
        String title = requiredText(submission.getTitle(), 500, "초록 제목");
        List<ProgramItemPerson> newSpeakers = presentingSpeakers(abstractSeq);
        String display = newSpeakers.stream().map(p -> p.getPersonName() + " (" + p.getAffiliation() + ")")
                .collect(Collectors.joining("\n"));
        if (display.length() > 500) throw new IllegalArgumentException("발표자와 소속의 표시 내용이 500자를 초과합니다. 초록 정보를 확인해주세요.");

        ProgramAbstractSnapshot snapshot = new ProgramAbstractSnapshot();
        snapshot.setProgramItemSeq(itemSeq);
        snapshot.setAbstractSubmissionSeq(abstractSeq);
        snapshot.setOriginalTitle(item.getTitle());
        snapshot.setOriginalSpeakerText(item.getSpeakerText());
        snapshot.setOriginalSpeakersJson(encode(programs.lockSpeakers(itemSeq, dbEncString())));
        snapshot.setAssignedTitle(title);
        snapshot.setAssignedSpeakerText(display);
        snapshot.setAssignedSpeakersJson(encode(newSpeakers));
        assignments.deleteSnapshot(conferenceSeq, itemSeq);
        assignments.insertSnapshot(snapshot, dbEncString());
        item.setAbstractSubmissionSeq(abstractSeq);
        item.setTitle(title);
        item.setSpeakerText(display);
        programs.updateAbstractAssignment(conferenceSeq, item, dbEncString());
        replaceSpeakers(itemSeq, newSpeakers);
        return item;
    }

    @Transactional
    public ProgramItem release(Long conferenceSeq, Long itemSeq, Long abstractSeq, boolean restoreOriginal) {
        if (abstractSeq == null) throw new IllegalArgumentException("해제할 초록 정보가 필요합니다.");
        programs.lockAbstractStatus(conferenceSeq, abstractSeq);
        ProgramItem item = requireItem(programs.lockItem(conferenceSeq, itemSeq, dbEncString()));
        if (!Objects.equals(item.getAbstractSubmissionSeq(), abstractSeq)) {
            throw new IllegalStateException("배정 정보가 변경되었습니다. 새로고침 후 다시 확인해주세요.");
        }
        ProgramAbstractSnapshot snapshot = assignments.lockSnapshot(conferenceSeq, itemSeq, dbEncString());
        List<ProgramItemPerson> speakers = programs.lockSpeakers(itemSeq, dbEncString());
        if (restoreOriginal) {
            if (!canRestore(item, speakers, snapshot)) {
                throw new IllegalStateException("배정 후 제목 또는 발표자 정보가 변경되었습니다. 새로고침 후 현재 정보를 유지하여 해제해주세요.");
            }
            item.setTitle(snapshot.getOriginalTitle());
            item.setSpeakerText(snapshot.getOriginalSpeakerText());
            replaceSpeakers(itemSeq, decode(snapshot.getOriginalSpeakersJson()));
        }
        item.setAbstractSubmissionSeq(null);
        programs.updateAbstractAssignment(conferenceSeq, item, dbEncString());
        assignments.deleteSnapshot(conferenceSeq, itemSeq);
        return item;
    }

    private String dbEncString() {
        return personalDataProperties.requireDbEncString();
    }

    private ProgramItem requireItem(ProgramItem item) {
        if (item == null) throw new IllegalArgumentException("발표 시간 항목을 찾을 수 없습니다.");
        return item;
    }

    private void requireEligible(Long conferenceSeq, ProgramItem item) {
        ProgramDay day = programs.findDayBySeq(conferenceSeq, item.getProgramDaySeq());
        if (!"ABSTRACT_PRESENTATION".equals(item.getItemType()) || !Boolean.TRUE.equals(item.getEnabled())
                || day == null || !Boolean.TRUE.equals(day.getEnabled())) {
            throw new IllegalArgumentException("사용 중인 초록 발표 항목에만 배정할 수 있습니다.");
        }
        if ("ROOM".equals(item.getScopeType())) {
            ProgramRoom room = programs.findRoomBySeq(conferenceSeq, item.getRoomSeq());
            if (room == null || !Boolean.TRUE.equals(room.getEnabled())
                    || programs.countEnabledDayRoom(
                            conferenceSeq, item.getProgramDaySeq(), item.getRoomSeq()
                    ) == 0) {
                throw new IllegalArgumentException("해당 일자에 사용 중인 룸인지 확인해주세요.");
            }
        }
        if (item.getParentSeq() != null) {
            ProgramItem parent = programs.findItemBySeq(conferenceSeq, item.getParentSeq(), dbEncString());
            if (parent == null || !Boolean.TRUE.equals(parent.getEnabled()) || !"SESSION".equals(parent.getItemType())
                    || !Objects.equals(parent.getProgramDaySeq(), item.getProgramDaySeq())
                    || !Objects.equals(parent.getRoomSeq(), item.getRoomSeq())
                    || !Objects.equals(parent.getScopeType(), item.getScopeType())
                    || item.getStartTime().isBefore(parent.getStartTime()) || item.getEndTime().isAfter(parent.getEndTime())) {
                throw new IllegalArgumentException("상위 세션의 사용 여부와 발표 시간 범위를 확인해주세요.");
            }
        }
    }

    private List<ProgramItemPerson> presentingSpeakers(Long abstractSeq) {
        List<AbstractSubmissionAuthor> authors = abstracts.findAuthorsByAbstractSeq(abstractSeq, dbEncString()).stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsPresentingAuthor())).toList();
        if (authors.isEmpty()) throw new IllegalArgumentException("초록에 발표자가 지정되지 않았습니다. 초록 정보를 먼저 수정해주세요.");
        Map<Integer, AbstractSubmissionInstitution> institutions = abstracts.findInstitutionsByAbstractSeq(abstractSeq).stream()
                .collect(Collectors.toMap(AbstractSubmissionInstitution::getInstitutionNo, Function.identity(), (a, b) -> a));
        List<Country> countryList = countries.findUsed();
        List<ProgramItemPerson> result = new ArrayList<>();
        for (AbstractSubmissionAuthor author : authors) {
            AbstractSubmissionInstitution institution = institutions.get(author.getInstitutionNo());
            if (institution == null) throw new IllegalArgumentException("발표자의 소속 정보가 없습니다. 초록 정보를 먼저 수정해주세요.");
            String name = requiredText(author.getAuthorName(), 255, "발표자 이름");
            String affiliation = requiredText(institution.getInstitutionName(), 255, "발표자 소속");
            String countryName = institution.getCountry();
            Long countrySeq = countryList.stream().filter(c -> countryName != null &&
                    (countryName.equalsIgnoreCase(c.getCountryName()) || countryName.equalsIgnoreCase(c.getIsoAlpha2())
                            || countryName.equalsIgnoreCase(c.getIsoAlpha3()) || countryName.equals(c.getCountryNameKo())))
                    .map(Country::getSeq).findFirst().orElse(null);
            result.add(ProgramItemPerson.builder().roleType("SPEAKER").personName(name).affiliation(affiliation)
                    .countrySeq(countrySeq).sortOrder(result.size() * 10).enabled(true).build());
        }
        return result;
    }

    private String requiredText(String value, int max, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " 정보가 필요합니다.");
        if (value.trim().length() > max) throw new IllegalArgumentException(label + "은(는) " + max + "자 이하이어야 합니다.");
        return value.trim();
    }

    private void replaceSpeakers(Long itemSeq, List<ProgramItemPerson> people) {
        programs.deleteSpeakers(itemSeq);
        for (ProgramItemPerson person : people) {
            person.setSeq(null);
            person.setProgramItemSeq(itemSeq);
            programs.insertPerson(person, dbEncString());
        }
    }

    public record Speaker(Long countrySeq, String affiliation, String personName, Integer sortOrder, Boolean enabled) {}

    private String encode(List<ProgramItemPerson> people) {
        try {
            return objectMapper.writeValueAsString(people.stream()
                    .sorted(Comparator.comparing(ProgramItemPerson::getSortOrder, Comparator.nullsFirst(Integer::compareTo))
                            .thenComparing(ProgramItemPerson::getPersonName, Comparator.nullsFirst(String::compareTo)))
                    .map(p -> new Speaker(p.getCountrySeq(), p.getAffiliation(), p.getPersonName(), p.getSortOrder(), p.getEnabled())).toList());
        } catch (JsonProcessingException e) { throw new IllegalStateException("발표자 정보를 보관하지 못했습니다.", e); }
    }

    private List<ProgramItemPerson> decode(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Speaker>>() {}).stream()
                    .map(p -> ProgramItemPerson.builder().roleType("SPEAKER").countrySeq(p.countrySeq()).affiliation(p.affiliation())
                            .personName(p.personName()).sortOrder(p.sortOrder()).enabled(p.enabled()).build()).toList();
        } catch (JsonProcessingException e) { throw new IllegalStateException("배정 전 발표자 정보를 복원하지 못했습니다.", e); }
    }

    private boolean canRestore(ProgramItem item, List<ProgramItemPerson> people, ProgramAbstractSnapshot snapshot) {
        return snapshot != null && Objects.equals(item.getAbstractSubmissionSeq(), snapshot.getAbstractSubmissionSeq())
                && Objects.equals(item.getTitle(), snapshot.getAssignedTitle())
                && Objects.equals(item.getSpeakerText(), snapshot.getAssignedSpeakerText())
                && Objects.equals(encode(people), snapshot.getAssignedSpeakersJson());
    }
}
