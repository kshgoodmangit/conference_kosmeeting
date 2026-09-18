package com.bjworld21.congress.repository;

import com.bjworld21.congress.dto.RegistrationFeeCategoryResponse;
import com.bjworld21.congress.entity.RegistrationCategory;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface RegistrationFeeRepository {

    @Select("""
            SELECT c.seq,
                   c.categoryCode,
                   c.categoryName,
                   c.description,
                   c.sortOrder,
                   c.isUsed,
                   MAX(CASE WHEN r.periodType = 'EARLY_BIRD' AND r.currency = 'USD' THEN r.amount END) AS earlyBirdUsdFee,
                   MAX(CASE WHEN r.periodType = 'EARLY_BIRD' AND r.currency = 'KRW' THEN r.amount END) AS earlyBirdKrwFee,
                   MAX(CASE WHEN r.periodType = 'REGULAR' AND r.currency = 'USD' THEN r.amount END) AS regularUsdFee,
                   MAX(CASE WHEN r.periodType = 'REGULAR' AND r.currency = 'KRW' THEN r.amount END) AS regularKrwFee,
                   c.updatedAt
            FROM registration_categories c
            LEFT JOIN registration_fee_rates r ON r.categorySeq = c.seq
            WHERE c.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND c.isDelete = 'N'
            GROUP BY c.seq, c.categoryCode, c.categoryName, c.description,
                     c.sortOrder, c.isUsed, c.updatedAt
            ORDER BY c.sortOrder ASC, c.seq ASC
            """)
    List<RegistrationFeeCategoryResponse> findAllActiveWithFees(@Param("conferenceSeq") Long conferenceSeq);

    @Select("SELECT * FROM registration_categories WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} AND isDelete = 'N'")
    RegistrationCategory findActiveBySeq(
            @Param("seq") Long seq,
            @Param("conferenceSeq") Long conferenceSeq
    );

    @Select("SELECT COUNT(*) FROM registration_categories WHERE categoryCode = #{categoryCode} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    long countByCategoryCode(
            @Param("categoryCode") String categoryCode,
            @Param("conferenceSeq") Long conferenceSeq
    );

    @Insert("""
            INSERT INTO registration_categories (
                conferenceSeq, categoryCode, categoryName, description, sortOrder,
                isUsed, isDelete, createdAt, updatedAt
            ) VALUES (
                #{conferenceSeq,javaType=java.lang.Long}, #{categoryCode}, #{categoryName}, #{description}, #{sortOrder},
                #{isUsed}, 'N', NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insertCategory(RegistrationCategory category);

    @Update("""
            UPDATE registration_categories
            SET categoryName = #{categoryName},
                description = #{description},
                sortOrder = #{sortOrder},
                isUsed = #{isUsed},
                updatedAt = NOW()
            WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} AND isDelete = 'N'
            """)
    int updateCategory(RegistrationCategory category);

    @Update("""
            UPDATE registration_categories
            SET isDelete = 'Y',
                isUsed = 'N',
                updatedAt = NOW()
            WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} AND isDelete = 'N'
            """)
    int softDeleteCategory(
            @Param("seq") Long seq,
            @Param("conferenceSeq") Long conferenceSeq
    );

    @Insert("""
            INSERT INTO registration_fee_rates (
                categorySeq, periodType, currency, amount, createdAt, updatedAt
            ) VALUES (
                #{categorySeq}, #{periodType}, #{currency}, #{amount}, NOW(), NOW()
            )
            ON DUPLICATE KEY UPDATE
                amount = #{amount},
                updatedAt = NOW()
            """)
    void upsertRate(
            @Param("categorySeq") Long categorySeq,
            @Param("periodType") String periodType,
            @Param("currency") String currency,
            @Param("amount") BigDecimal amount
    );
}
