package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgramRoomRequest {
    private String roomCode;
    private String roomName;
    private String location;
    private Integer sortOrder;
    private Boolean enabled;
}
