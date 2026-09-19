package com.bjworld21.conference.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuSettings {
    private String languageCode;
    private Boolean translationReady;
    private Long seq;
    private Long conferenceSeq;
    private String menuScope;
    private String menuKey;
    private String parentKey;
    private String menuName;
    private String menuPath;
    private String menuType;
    private String pathType;
    private String routePath;
    private Integer depth;
    private Long boardSeq;
    private String linkUrl;
    private String targetType;
    private Boolean authRequired;
    private Boolean navigationVisible;
    private String menuHtml;
    private Long htmlRevisionNo;
    private Integer sortOrder;
    private LocalDate useStartDate;
    private LocalDate useEndDate;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
