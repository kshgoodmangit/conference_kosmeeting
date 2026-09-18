package com.bjworld21.congress.repository;

import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class AdminAccessRequestRepositoryTest {
    @Test void listAndSummaryUseEffectiveStatusWithoutSiteRestriction() {
        var config = new Configuration();
        config.addMapper(AdminAccessRequestRepository.class);
        var params = Map.of("key", "test-key", "now", LocalDateTime.of(2026,9,16,12,0),
                "keyword", "운영", "status", "EXPIRED", "size", 20, "offset", 0);
        for (String statement : new String[]{"findPage", "summary"}) {
            var sql = config.getMappedStatement(AdminAccessRequestRepository.class.getName() + "." + statement).getBoundSql(params);
            assertThat(sql.getSql()).contains("r.expiresAt <= ?", "r.endDate < DATE(?)").doesNotContain("r.siteUrl = ?");
            assertThat(sql.getParameterMappings()).allMatch(mapping -> params.containsKey(mapping.getProperty()));
        }
    }
}
