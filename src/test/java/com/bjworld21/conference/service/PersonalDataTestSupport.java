package com.bjworld21.conference.service;

import com.bjworld21.conference.config.PersonalDataProperties;

final class PersonalDataTestSupport {
    static final String DB_ENC_STRING = "test-encryption-key";

    private PersonalDataTestSupport() {
    }

    static PersonalDataProperties properties() {
        PersonalDataProperties properties = new PersonalDataProperties();
        properties.setDbEncString(DB_ENC_STRING);
        return properties;
    }
}
