package com.bjworld21.conference.repository;

import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractSimilarityConferenceScopeTest {
    @Test
    void embeddingQueryRequiresTheOwningAbstractConference() {
        Configuration configuration = new Configuration();
        configuration.addMapper(AbstractEmbeddingRepository.class);
        var statement = configuration.getMappedStatement(
                AbstractEmbeddingRepository.class.getName() + ".findByConferenceSeq"
        );
        var boundSql = statement.getBoundSql(Map.of("conferenceSeq", 7L));

        assertThat(boundSql.getSql()).contains(
                "JOIN abstract_submissions s ON s.seq = e.abstractSeq",
                "WHERE s.conferenceSeq = ?"
        );
        assertThat(boundSql.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("conferenceSeq");
    }

    @Test
    void comparisonCandidatesAndCountRequireConferenceAndExcludeDrafts() {
        Configuration configuration = new Configuration();
        configuration.addMapper(AbstractSubmissionRepository.class);
        for (String method : new String[]{"findSimilarityCandidates", "countSimilarityCandidates"}) {
            var boundSql = configuration.getMappedStatement(
                    AbstractSubmissionRepository.class.getName() + "." + method
            ).getBoundSql(Map.of("conferenceSeq", 7L));

            assertThat(boundSql.getSql()).contains("WHERE conferenceSeq = ?", "AND status <> 'draft'");
            assertThat(boundSql.getParameterMappings())
                    .extracting(ParameterMapping::getProperty)
                    .containsExactly("conferenceSeq");
        }
    }
}
