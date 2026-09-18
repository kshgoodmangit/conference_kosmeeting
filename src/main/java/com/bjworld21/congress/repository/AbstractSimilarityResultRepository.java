package com.bjworld21.congress.repository;

import com.bjworld21.congress.entity.AbstractSimilarityResult;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AbstractSimilarityResultRepository {

    @Select("""
            SELECT r.seq,
                   r.jobSeq,
                   r.sourceAbstractSeq,
                   r.targetAbstractSeq,
                   target.submissionNo AS targetSubmissionNo,
                   target.title AS targetTitle,
                   r.overallSimilarity,
                   r.titleSimilarity,
                   r.objectiveSimilarity,
                   r.methodsSimilarity,
                   r.resultsSimilarity,
                   r.conclusionsSimilarity,
                   r.highestSection,
                   r.highestSimilarity,
                   r.modelName,
                   r.scoringVersion,
                   r.sourceContentHash,
                   r.targetContentHash,
                   r.analyzedAt
            FROM abstract_similarity_results r
            JOIN abstract_submissions target ON target.seq = r.targetAbstractSeq
            JOIN abstract_submissions source ON source.seq = r.sourceAbstractSeq
            WHERE r.sourceAbstractSeq = #{sourceAbstractSeq}
              AND source.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND target.conferenceSeq = source.conferenceSeq
            ORDER BY r.overallSimilarity DESC, r.targetAbstractSeq ASC
            """)
    List<AbstractSimilarityResult> findBySourceAbstractSeq(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("sourceAbstractSeq") Long sourceAbstractSeq
    );

    @Delete("""
            DELETE result
            FROM abstract_similarity_results result
            JOIN abstract_submissions source ON source.seq = result.sourceAbstractSeq
            WHERE source.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    void deleteAll(@Param("conferenceSeq") Long conferenceSeq);

    @Insert("""
            <script>
            INSERT INTO abstract_similarity_results (
                jobSeq, sourceAbstractSeq, targetAbstractSeq, overallSimilarity,
                titleSimilarity, objectiveSimilarity, methodsSimilarity,
                resultsSimilarity, conclusionsSimilarity,
                highestSection, highestSimilarity, modelName, scoringVersion,
                sourceContentHash, targetContentHash, analyzedAt
            ) VALUES
            <foreach collection="results" item="item" separator=",">
                (
                    #{item.jobSeq}, #{item.sourceAbstractSeq}, #{item.targetAbstractSeq}, #{item.overallSimilarity},
                    #{item.titleSimilarity,jdbcType=DECIMAL},
                    #{item.objectiveSimilarity,jdbcType=DECIMAL},
                    #{item.methodsSimilarity,jdbcType=DECIMAL},
                    #{item.resultsSimilarity,jdbcType=DECIMAL},
                    #{item.conclusionsSimilarity,jdbcType=DECIMAL},
                    #{item.highestSection,jdbcType=VARCHAR},
                    #{item.highestSimilarity,jdbcType=DECIMAL},
                    #{item.modelName}, #{item.scoringVersion},
                    #{item.sourceContentHash}, #{item.targetContentHash}, NOW()
                )
            </foreach>
            </script>
            """)
    void insertBatch(@Param("results") List<AbstractSimilarityResult> results);
}
