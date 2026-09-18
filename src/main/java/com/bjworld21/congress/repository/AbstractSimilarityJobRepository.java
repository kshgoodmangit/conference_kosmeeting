package com.bjworld21.congress.repository;

import com.bjworld21.congress.entity.AbstractSimilarityJob;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AbstractSimilarityJobRepository {

    @Select("""
            SELECT seq, conferenceSeq, status, phase, progressPercent, message,
                   processedCount, totalCount, abstractCount,
                   updatedAbstractCount, generatedEmbeddingCount, similarityResultCount,
                   titleWeight, objectiveWeight, methodsWeight, resultsWeight, conclusionsWeight,
                   requestedByAdminSeq, activeKey, startedAt, completedAt, createdAt, updatedAt
            FROM abstract_similarity_jobs
            WHERE seq = #{seq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    AbstractSimilarityJob findBySeq(@Param("conferenceSeq") Long conferenceSeq,
                                    @Param("seq") Long seq);

    @Select("""
            SELECT seq, conferenceSeq, status, phase, progressPercent, message,
                   processedCount, totalCount, abstractCount,
                   updatedAbstractCount, generatedEmbeddingCount, similarityResultCount,
                   titleWeight, objectiveWeight, methodsWeight, resultsWeight, conclusionsWeight,
                   requestedByAdminSeq, activeKey, startedAt, completedAt, createdAt, updatedAt
            FROM abstract_similarity_jobs
            WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND activeKey = 1
            ORDER BY seq DESC
            LIMIT 1
            """)
    AbstractSimilarityJob findActive(@Param("conferenceSeq") Long conferenceSeq);

    @Insert("""
            INSERT INTO abstract_similarity_jobs (
                conferenceSeq, status, phase, progressPercent, message,
                processedCount, totalCount, abstractCount,
                updatedAbstractCount, generatedEmbeddingCount, similarityResultCount,
                titleWeight, objectiveWeight, methodsWeight, resultsWeight, conclusionsWeight,
                requestedByAdminSeq, activeKey, createdAt, updatedAt
            ) VALUES (
                #{conferenceSeq,javaType=java.lang.Long}, 'QUEUED', 'PREPARING', 0, '분석 작업을 준비하고 있습니다.',
                0, 0, 0, 0, 0, 0,
                #{titleWeight}, #{objectiveWeight}, #{methodsWeight}, #{resultsWeight}, #{conclusionsWeight},
                #{requestedByAdminSeq}, 1, NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insert(AbstractSimilarityJob job);

    @Update("""
            UPDATE abstract_similarity_jobs
            SET status = 'RUNNING',
                phase = #{phase},
                progressPercent = #{progressPercent},
                message = #{message},
                processedCount = #{processedCount},
                totalCount = #{totalCount},
                abstractCount = #{abstractCount},
                startedAt = COALESCE(startedAt, NOW()),
                updatedAt = NOW()
            WHERE seq = #{seq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND status IN ('QUEUED', 'RUNNING')
            """)
    void updateProgress(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("seq") Long seq,
            @Param("phase") String phase,
            @Param("progressPercent") int progressPercent,
            @Param("message") String message,
            @Param("processedCount") int processedCount,
            @Param("totalCount") int totalCount,
            @Param("abstractCount") int abstractCount
    );

    @Update("""
            UPDATE abstract_similarity_jobs
            SET status = 'COMPLETED',
                phase = 'COMPLETED',
                progressPercent = 100,
                message = '유사도 분석을 완료했습니다.',
                processedCount = #{similarityResultCount},
                totalCount = #{similarityResultCount},
                abstractCount = #{abstractCount},
                updatedAbstractCount = #{updatedAbstractCount},
                generatedEmbeddingCount = #{generatedEmbeddingCount},
                similarityResultCount = #{similarityResultCount},
                activeKey = NULL,
                completedAt = NOW(),
                updatedAt = NOW()
            WHERE seq = #{seq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    void complete(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("seq") Long seq,
            @Param("abstractCount") int abstractCount,
            @Param("updatedAbstractCount") int updatedAbstractCount,
            @Param("generatedEmbeddingCount") int generatedEmbeddingCount,
            @Param("similarityResultCount") int similarityResultCount
    );

    @Update("""
            UPDATE abstract_similarity_jobs
            SET status = 'FAILED',
                message = #{message},
                activeKey = NULL,
                completedAt = NOW(),
                updatedAt = NOW()
            WHERE seq = #{seq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    void fail(@Param("conferenceSeq") Long conferenceSeq,
              @Param("seq") Long seq,
              @Param("message") String message);

    @Update("""
            UPDATE abstract_similarity_jobs
            SET status = 'FAILED',
                message = '서버 중단으로 이전 분석 작업을 완료하지 못했습니다.',
                activeKey = NULL,
                completedAt = NOW(),
                updatedAt = NOW()
            WHERE activeKey = 1
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND status IN ('QUEUED', 'RUNNING')
              AND updatedAt < DATE_SUB(NOW(), INTERVAL 1 HOUR)
            """)
    void failStaleActive(@Param("conferenceSeq") Long conferenceSeq);
}
