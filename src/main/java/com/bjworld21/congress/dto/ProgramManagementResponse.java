package com.bjworld21.congress.dto;

import com.bjworld21.congress.entity.Country;
import com.bjworld21.congress.entity.ProgramDay;
import com.bjworld21.congress.entity.ProgramDayRoom;
import com.bjworld21.congress.entity.ProgramItem;
import com.bjworld21.congress.entity.ProgramItemPerson;
import com.bjworld21.congress.entity.ProgramRoom;
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
