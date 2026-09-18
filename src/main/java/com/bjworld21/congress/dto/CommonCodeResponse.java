package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommonCodeResponse {
    private Long seq;
    private String groupCode;
    private Long parentSeq;
    private String codeName;
    private Integer sortOrder;
    private String isUsed;
    private String codeEtc1;
    private String codeEtc2;
    private String codeEtc3;
    private String isEditable;
    private String isEtc;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<CommonCodeResponse> children;
}
