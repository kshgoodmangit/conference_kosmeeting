package com.bjworld21.conference.dto;

import com.bjworld21.conference.entity.Country;
import com.bjworld21.conference.entity.ProgramDay;
import com.bjworld21.conference.entity.ProgramDayRoom;
import com.bjworld21.conference.entity.ProgramItem;
import com.bjworld21.conference.entity.ProgramItemPerson;
import com.bjworld21.conference.entity.ProgramRoom;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgramManagementResponse {
    private List<ProgramDay> days;
    private List<ProgramRoom> rooms;
    private List<ProgramDayRoom> dayRooms;
    private List<ProgramItem> items;
    private List<ProgramItemPerson> people;
    private List<Country> countries;
    private List<Long> matchedItemSeqs;
    private long matchedCount;
    private long matchedEnabledCount;
    private long matchedDisabledCount;
}
