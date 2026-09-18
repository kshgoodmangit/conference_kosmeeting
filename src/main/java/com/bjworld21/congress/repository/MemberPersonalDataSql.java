package com.bjworld21.congress.repository;

public final class MemberPersonalDataSql {
    public static final String EMAIL = """
            CONVERT(AES_DECRYPT(UNHEX(m.email), SHA2(#{dbEncString}, 512)) USING utf8mb4)
            """;
    public static final String FIRST_NAME = """
            CONVERT(AES_DECRYPT(UNHEX(m.firstName), SHA2(#{dbEncString}, 512)) USING utf8mb4)
            """;
    public static final String LAST_NAME = """
            CONVERT(AES_DECRYPT(UNHEX(m.lastName), SHA2(#{dbEncString}, 512)) USING utf8mb4)
            """;
    public static final String MOBILE = """
            CONVERT(AES_DECRYPT(UNHEX(m.mobile), SHA2(#{dbEncString}, 512)) USING utf8mb4)
            """;

    private MemberPersonalDataSql() {
    }
}
