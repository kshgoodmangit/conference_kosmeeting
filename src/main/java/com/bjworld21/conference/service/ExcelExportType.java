package com.bjworld21.conference.service;

public enum ExcelExportType {
    ADMIN_ACCOUNTS("admin", "관리자 계정 관리"),
    MEMBERS("members", "회원 관리"),
    ABSTRACTS("abstracts", "초록 관리"),
    PRE_REGISTRATIONS("pre-registrations", "사전등록관리");

    private final String menuKey;
    private final String menuName;

    ExcelExportType(String menuKey, String menuName) {
        this.menuKey = menuKey;
        this.menuName = menuName;
    }

    public String getMenuKey() {
        return menuKey;
    }

    public String getMenuName() {
        return menuName;
    }
}
