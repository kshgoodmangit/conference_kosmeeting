package com.bjworld21.congress.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.personal-data")
public class PersonalDataProperties {
    private String dbEncString;

    public String getDbEncString() {
        return dbEncString;
    }

    public void setDbEncString(String dbEncString) {
        this.dbEncString = dbEncString;
    }

    public String requireDbEncString() {
        if (dbEncString == null || dbEncString.isBlank()) {
            throw new IllegalStateException("개인정보 암호화 키가 설정되지 않았습니다.");
        }
        return dbEncString;
    }
}
