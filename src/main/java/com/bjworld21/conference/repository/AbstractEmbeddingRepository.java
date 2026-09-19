package com.bjworld21.conference.repository;

import com.bjworld21.conference.entity.AbstractEmbedding;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AbstractEmbeddingRepository {

    @Select("""
            SELECT seq, abstractSeq, sectionType, modelName, modelRevision,
                   dimension, embedding, contentHash, createdAt, updatedAt
            FROM abstract_embeddings
            """)
    List<AbstractEmbedding> findAll();

    @Insert("""
            INSERT INTO abstract_embeddings (
                abstractSeq, sectionType, modelName, modelRevision,
                dimension, embedding, contentHash, createdAt, updatedAt
            ) VALUES (
                #{abstractSeq}, #{sectionType}, #{modelName}, #{modelRevision},
                #{dimension}, #{embedding,jdbcType=BLOB}, #{contentHash}, NOW(), NOW()
            )
            ON DUPLICATE KEY UPDATE
                modelName = VALUES(modelName),
                modelRevision = VALUES(modelRevision),
                dimension = VALUES(dimension),
                embedding = VALUES(embedding),
                contentHash = VALUES(contentHash),
                updatedAt = NOW()
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void upsert(AbstractEmbedding embedding);

    @Delete("""
            DELETE FROM abstract_embeddings
            WHERE abstractSeq = #{abstractSeq}
              AND sectionType = #{sectionType}
            """)
    void deleteSection(
            @Param("abstractSeq") Long abstractSeq,
            @Param("sectionType") String sectionType
    );
}
