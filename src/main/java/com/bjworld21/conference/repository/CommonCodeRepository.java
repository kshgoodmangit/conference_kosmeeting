package com.bjworld21.conference.repository;

import com.bjworld21.conference.entity.CommonCode;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CommonCodeRepository {

    @Select("""
            SELECT *
            FROM common_codes
            WHERE isDelete = 'N'
            ORDER BY parentSeq ASC, sortOrder ASC, seq ASC
            """)
    List<CommonCode> findAllActive();

    @Select("SELECT * FROM common_codes WHERE seq = #{seq} AND isDelete = 'N'")
    CommonCode findActiveBySeq(@Param("seq") Long seq);

    @Select("""
            SELECT *
            FROM common_codes
            WHERE parentSeq = 0
              AND groupCode = #{groupCode}
              AND isDelete = 'N'
            LIMIT 1
            """)
    CommonCode findActiveRootByGroupCode(@Param("groupCode") String groupCode);

    @Select("""
            SELECT COUNT(*)
            FROM common_codes
            WHERE parentSeq = #{parentSeq} AND isDelete = 'N'
            """)
    long countActiveChildren(@Param("parentSeq") Long parentSeq);

    @Insert("""
            INSERT INTO common_codes (
                groupCode,
                parentSeq,
                codeName,
                sortOrder,
                isUsed,
                codeEtc1,
                codeEtc2,
                codeEtc3,
                isEditable,
                isEtc,
                isDelete,
                createdAt,
                updatedAt
            ) VALUES (
                #{groupCode},
                #{parentSeq},
                #{codeName},
                #{sortOrder},
                #{isUsed},
                #{codeEtc1},
                #{codeEtc2},
                #{codeEtc3},
                #{isEditable},
                #{isEtc},
                'N',
                NOW(),
                NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insert(CommonCode commonCode);

    @Update("""
            UPDATE common_codes
            SET groupCode = #{groupCode},
                parentSeq = #{parentSeq},
                codeName = #{codeName},
                sortOrder = #{sortOrder},
                isUsed = #{isUsed},
                codeEtc1 = #{codeEtc1},
                codeEtc2 = #{codeEtc2},
                codeEtc3 = #{codeEtc3},
                isEditable = #{isEditable},
                isEtc = #{isEtc},
                updatedAt = NOW()
            WHERE seq = #{seq} AND isDelete = 'N'
            """)
    int update(CommonCode commonCode);

    @Update("""
            UPDATE common_codes
            SET groupCode = #{groupCode},
                updatedAt = NOW()
            WHERE seq = #{seq} AND isDelete = 'N'
            """)
    int updateGroupCode(@Param("seq") Long seq, @Param("groupCode") String groupCode);

    @Update("""
            UPDATE common_codes
            SET parentSeq = #{parentSeq},
                groupCode = #{groupCode},
                sortOrder = #{sortOrder},
                updatedAt = NOW()
            WHERE seq = #{seq} AND isDelete = 'N'
            """)
    int updateStructure(
            @Param("seq") Long seq,
            @Param("parentSeq") Long parentSeq,
            @Param("groupCode") String groupCode,
            @Param("sortOrder") Integer sortOrder
    );

    @Update("""
            UPDATE common_codes
            SET isDelete = 'Y',
                isUsed = 'N',
                updatedAt = NOW()
            WHERE seq = #{seq} AND isDelete = 'N'
            """)
    int softDelete(@Param("seq") Long seq);
}
