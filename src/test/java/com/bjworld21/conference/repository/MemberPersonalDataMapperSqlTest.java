package com.bjworld21.conference.repository;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MemberPersonalDataMapperSqlTest {

    @Test
    void memberListQueryDecryptsProtectedColumnsAndSupportsKeywordSearch() throws Exception {
        Configuration configuration = configuration(MemberRepository.class);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("keyword", "kim");
        parameters.put("memberType", "domestic");
        parameters.put("hasPreRegistration", null);
        parameters.put("hasAbstractSubmission", null);
        parameters.put("size", 10);
        parameters.put("offset", 0);
        parameters.put("dbEncString", "test-encryption-key");

        BoundSql boundSql = configuration
                .getMappedStatement(MemberRepository.class.getName() + ".findPage")
                .getBoundSql(parameters);

        assertThat(boundSql.getSql())
                .contains("AES_DECRYPT(UNHEX(m.email)")
                .contains("AES_DECRYPT(UNHEX(m.firstName)")
                .contains("AES_DECRYPT(UNHEX(m.lastName)")
                .contains("AES_DECRYPT(UNHEX(m.mobile)")
                .contains("LIKE LOWER(CONCAT('%', ?, '%'))")
                .doesNotContain("CASE WHEN")
                .doesNotContain("personalDataEncrypted");
        assertThat(boundSql.getParameterMappings())
                .anyMatch(mapping -> "dbEncString".equals(mapping.getProperty()));

        MappedStatement mappedStatement = configuration
                .getMappedStatement(MemberRepository.class.getName() + ".findPage");
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        assertThatCode(() -> configuration
                .newParameterHandler(mappedStatement, parameters, boundSql)
                .setParameters(preparedStatement))
                .doesNotThrowAnyException();
        verify(preparedStatement, atLeastOnce()).setString(anyInt(), eq("test-encryption-key"));
    }

    @Test
    void memberEmailLookupAssumesEncryptedStorageWithoutPlaintextFallback() {
        Configuration configuration = configuration(MemberRepository.class);
        BoundSql boundSql = configuration
                .getMappedStatement(MemberRepository.class.getName() + ".findByEmail")
                .getBoundSql(Map.of(
                        "email", "member@example.com",
                        "dbEncString", "test-encryption-key"
                ));

        assertThat(boundSql.getSql())
                .contains("m.email = HEX(AES_ENCRYPT(")
                .doesNotContain("personalDataEncrypted")
                .doesNotContain("LOWER(m.email)");
    }

    @Test
    void allMemberConsumersBuildQueriesWithDecryption() {
        assertDecryptionSql(AbstractSubmissionRepository.class, "findBySeq", Map.of("seq", 1L), "memberEmail");
        assertDecryptionSql(PreRegistrationRepository.class, "findBySeq", Map.of("seq", 1L), "AS email");
        assertDecryptionSql(MailInternalRecipientRepository.class, "search",
                Map.of("keyword", "kim", "category", "MEMBER", "limit", 10), "AS fullName");
        assertDecryptionSql(MailRecipientGroupRepository.class, "findGroup",
                Map.of("group", "ALL_MEMBERS"), "AS email");
        assertDecryptionSql(SmsCampaignRepository.class, "group",
                Map.of("group", "ALL_MEMBERS"), "AS phoneNumber");
    }

    private void assertDecryptionSql(
            Class<?> mapperType,
            String methodName,
            Map<String, Object> parameters,
            String expectedAlias
    ) {
        Configuration configuration = configuration(mapperType);
        Map<String, Object> boundParameters = new HashMap<>(parameters);
        boundParameters.put("dbEncString", "test-encryption-key");
        BoundSql boundSql = configuration
                .getMappedStatement(mapperType.getName() + "." + methodName)
                .getBoundSql(boundParameters);

        assertThat(boundSql.getSql())
                .contains("AES_DECRYPT")
                .contains(expectedAlias);
        assertThat(boundSql.getParameterMappings())
                .anyMatch(mapping -> "dbEncString".equals(mapping.getProperty()));
    }

    private Configuration configuration(Class<?> mapperType) {
        Configuration configuration = new Configuration();
        configuration.addMapper(mapperType);
        return configuration;
    }
}
