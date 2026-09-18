package com.bjworld21.congress.entity;
import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MenuTranslation {
    private Long seq;
    private Long menuSeq;
    private String languageCode;
    private String menuName;
    private String menuHtml;
    private Long htmlRevisionNo;
}
