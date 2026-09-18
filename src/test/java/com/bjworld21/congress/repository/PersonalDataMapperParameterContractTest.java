package com.bjworld21.congress.repository;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalDataMapperParameterContractTest {
    private static final Pattern PLACEHOLDER_ROOT = Pattern.compile("#\\{\\s*([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern FOREACH_ITEM = Pattern.compile("(?:item|index)\\s*=\\s*[\"']([^\"']+)[\"']");

    private static final List<Class<?>> PERSONAL_DATA_MAPPERS = List.of(
            AbstractProgramRepository.class,
            AbstractReviewAssignmentRepository.class,
            AbstractSubmissionRepository.class,
            AdminAbstractReviewResultRepository.class,
            AdminAccessLogRepository.class,
            AdminAccountRepository.class,
            AdminIpAllowlistRepository.class,
            BoardPostRepository.class,
            ExcelDownloadLogRepository.class,
            FreeRecipientRepository.class,
            MailAddressBookRepository.class,
            MailCampaignRepository.class,
            MailEventRepository.class,
            MailInternalRecipientRepository.class,
            MailRecipientGroupRepository.class,
            MemberRepository.class,
            PreRegistrationRepository.class,
            ProgramRepository.class,
            ReviewerRepository.class,
            SmsCampaignRepository.class,
            SocietyMemberRepository.class,
            SpeakerRepository.class,
            SponsorshipApplicationRepository.class
    );

    @Test
    void everyEncryptionSqlDeclaresAndUsesOnlyAvailableMyBatisParameters() {
        for (Class<?> mapper : PERSONAL_DATA_MAPPERS) {
            for (Method method : mapper.getDeclaredMethods()) {
                String sql = sql(method);
                if (!sql.contains("#{dbEncString}")) {
                    continue;
                }

                Set<String> available = parameterNames(method);
                assertThat(available)
                        .as("%s.%s must declare @Param(\"dbEncString\")", mapper.getSimpleName(), method.getName())
                        .contains("dbEncString");

                Matcher foreachMatcher = FOREACH_ITEM.matcher(sql);
                while (foreachMatcher.find()) {
                    available.add(foreachMatcher.group(1));
                }

                Matcher placeholderMatcher = PLACEHOLDER_ROOT.matcher(sql);
                while (placeholderMatcher.find()) {
                    String root = placeholderMatcher.group(1);
                    assertThat(available)
                            .as("%s.%s references unavailable MyBatis parameter '%s'",
                                    mapper.getSimpleName(), method.getName(), root)
                            .contains(root);
                }
            }
        }
    }

    private Set<String> parameterNames(Method method) {
        Set<String> result = new LinkedHashSet<>();
        for (Parameter parameter : method.getParameters()) {
            Param annotation = parameter.getAnnotation(Param.class);
            if (annotation != null) {
                result.add(annotation.value());
            }
        }
        return result;
    }

    private String sql(Method method) {
        if (method.isAnnotationPresent(Select.class)) {
            return String.join("\n", method.getAnnotation(Select.class).value());
        }
        if (method.isAnnotationPresent(Insert.class)) {
            return String.join("\n", method.getAnnotation(Insert.class).value());
        }
        if (method.isAnnotationPresent(Update.class)) {
            return String.join("\n", method.getAnnotation(Update.class).value());
        }
        if (method.isAnnotationPresent(Delete.class)) {
            return String.join("\n", method.getAnnotation(Delete.class).value());
        }
        return "";
    }
}
