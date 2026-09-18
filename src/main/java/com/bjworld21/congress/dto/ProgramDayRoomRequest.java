package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgramDayRoomRequest {
    private Long roomSeq;
    private String tabName;
    private Integer sortOrder;
    private Boolean enabled;
}
