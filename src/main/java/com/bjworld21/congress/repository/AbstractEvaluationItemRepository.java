package com.bjworld21.congress.repository;

import com.bjworld21.congress.entity.AbstractEvaluationItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AbstractEvaluationItemRepository {

    @Select("""
            SELECT *
            FROM abstract_evaluation_items
            WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND isDelete = 'N'
            ORDER BY sortOrder ASC, seq ASC
            """)
    List<AbstractEvaluationItem> findAllActive(@Param("conferenceSeq") Long conferenceSeq);

    @Select("""
            SELECT *
            FROM abstract_evaluation_items
            WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND isDelete = 'N' AND isUsed = 'Y'
            ORDER BY sortOrder ASC, seq ASC
            """)
    List<AbstractEvaluationItem> findAllUsed(@Param("conferenceSeq") Long conferenceSeq);

    @Select("SELECT * FROM abstract_evaluation_items WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} AND isDelete = 'N'")
    AbstractEvaluationItem findActiveBySeq(@Param("conferenceSeq") Long conferenceSeq,
                                            @Param("seq") Long seq);

    @Select("""
            SELECT COUNT(*)
            FROM abstract_evaluation_items
            WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND isDelete = 'N'
              AND LOWER(itemName) = LOWER(#{itemName})
              AND (#{excludeSeq} IS NULL OR seq <> #{excludeSeq})
            """)
    long countActiveByName(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("itemName") String itemName,
            @Param("excludeSeq") Long excludeSeq
    );

    @Insert("""
            INSERT INTO abstract_evaluation_items (
                conferenceSeq, itemName, description, sortOrder, isUsed,
                score1Guide, score2Guide, score3Guide, score4Guide, score5Guide, score6Guide,
                isDelete, createdAt, updatedAt
            ) VALUES (
                #{conferenceSeq,javaType=java.lang.Long}, #{itemName}, #{description}, #{sortOrder}, #{isUsed},
                #{score1Guide}, #{score2Guide}, #{score3Guide}, #{score4Guide}, #{score5Guide}, #{score6Guide},
                'N', NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insert(AbstractEvaluationItem item);

    @Update("""
            UPDATE abstract_evaluation_items
            SET itemName = #{itemName},
                description = #{description},
                sortOrder = #{sortOrder},
                isUsed = #{isUsed},
                score1Guide = #{score1Guide},
                score2Guide = #{score2Guide},
                score3Guide = #{score3Guide},
                score4Guide = #{score4Guide},
                score5Guide = #{score5Guide},
                score6Guide = #{score6Guide},
                updatedAt = NOW()
            WHERE seq = #{seq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND isDelete = 'N'
            """)
    int update(AbstractEvaluationItem item);

    @Update("""
            UPDATE abstract_evaluation_items
            SET isDelete = 'Y',
                isUsed = 'N',
                updatedAt = NOW()
            WHERE seq = #{seq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND isDelete = 'N'
            """)
    int softDelete(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);
}
