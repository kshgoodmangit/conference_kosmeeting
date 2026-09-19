package com.bjworld21.conference.repository;

import com.bjworld21.conference.dto.MailHistoryData.Filter;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class MailHistoryRepositoryTest {
    @Test void emailQueriesGroupNormalizedAddressesAndFilterTheMatchingRecipient() {
        Configuration configuration = new Configuration(); configuration.addMapper(MailHistoryRepository.class);
        String prefix = MailHistoryRepository.class.getName() + ".";
        Filter filter = new Filter(); filter.setConferenceSeq(2L); filter.setExactEmail("person+tag@example.org"); filter.setStatus("EXCLUDED");
        for (String statement : List.of("emails", "emailSummary", "emailHistories")) {
            var bound = configuration.getMappedStatement(prefix + statement).getBoundSql(Map.of("f", filter, "key", "test", "size", 20, "offset", 0));
            assertThat(bound.getSql()).contains("h.conferenceSeq=?", "h.sourceMenu=?", "h.createdAt >=", "h.createdAt <", "r.normalizedEmail=HEX(AES_ENCRYPT(", "COALESCE(s.deliveryStatus,r.recipientStatus)=?");
            assertThat(bound.getSql()).doesNotContain("person+tag", "OR EXISTS (SELECT 1 FROM mail_campaign_recipients");
            assertThat(bound.getParameterMappings()).extracting("property").contains("f.conferenceSeq", "f.exactEmail", "f.keyword", "f.status");
            if (statement.equals("emails")) assertThat(bound.getSql()).contains("GROUP BY r.normalizedEmail", "MAX(r.seq)", "LIMIT ? OFFSET ?");
            if (statement.equals("emailSummary")) assertThat(bound.getSql()).contains("COUNT(DISTINCT r.normalizedEmail)", "COUNT(*) AS historyCount");
        }
        String lookup = configuration.getMappedStatement(prefix + "recipientEmail").getBoundSql(Map.of("conferenceSeq", 2L, "recipientSeq", 41L, "key", "test")).getSql();
        assertThat(lookup).contains("h.conferenceSeq=? AND r.seq=?", "UNHEX(r.normalizedEmail)");
    }

    @Test void parsesAllStatementsAndBindsScopedSourcesAndHistoryFilters() {
        Configuration configuration = new Configuration(); configuration.addMapper(MailHistoryRepository.class);
        String prefix = MailHistoryRepository.class.getName() + ".";
        for (String menu : List.of("pre-registrations", "abstracts", "oral-accepted-abstracts", "poster-accepted-abstracts", "speakers", "sponsorship")) {
            String sql = configuration.getMappedStatement(prefix + "sources").getBoundSql(Map.of("conferenceSeq", 1L, "menu", menu, "seqs", List.of(2L, 3L), "dbEncString", "test")).getSql();
            assertThat(sql).contains("s.conferenceSeq=?", "s.seq IN").doesNotContain("${");
            assertThat(sql).contains(switch(menu) { case "speakers" -> "FROM speakers"; case "sponsorship" -> "FROM sponsorship_applications"; case "pre-registrations" -> "pre_registrations"; default -> "abstract_submissions"; });
        }
        Filter filter = new Filter(); filter.setConferenceSeq(1L);
        for (String statement : List.of("page", "summary")) {
            String sql = configuration.getMappedStatement(prefix + statement).getBoundSql(Map.of("f", filter, "key", "test", "size", 20, "offset", 0)).getSql();
            assertThat(sql).contains("h.conferenceSeq=?", "h.sourceMenu=?", "h.createdAt >=", "h.createdAt <", "r.jobSeq=h.jobSeq");
        }
        String jobSql = configuration.getMappedStatement(prefix + "insertJob").getBoundSql(Map.of()).getSql();
        assertThat(jobSql).contains("jobType,status", "'HISTORY','SAVED'").doesNotContain("QUEUED");
    }
}
