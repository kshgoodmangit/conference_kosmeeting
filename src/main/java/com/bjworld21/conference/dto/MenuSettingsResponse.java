package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuSettingsResponse {
    private String languageCode;
    private Boolean translationReady;
    private Long seq;
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
    private List<MenuSettingsResponse> children;
}
