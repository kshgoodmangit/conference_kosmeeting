package com.bjworld21.congress.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuHtmlHistory {
    private Long seq;
    private Long menuSeq;
    private Long revisionNo;
    private String menuHtml;
    private String operationType;
    private Long restoredFromSeq;
    private String changeMemo;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
}
