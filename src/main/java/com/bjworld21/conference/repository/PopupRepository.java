package com.bjworld21.conference.repository;

import com.bjworld21.conference.entity.Popup;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PopupRepository {

    @Select("""
            SELECT * FROM popups
            WHERE conferenceSeq = #{conferenceSeq}
              AND enabled = TRUE
              AND (useStartDate IS NULL OR useStartDate <= #{today})
              AND (useEndDate IS NULL OR useEndDate >= #{today})
            ORDER BY createdAt DESC, seq DESC
            """)
    List<Popup> findVisible(@Param("conferenceSeq") Long conferenceSeq,
                            @Param("today") java.time.LocalDate today);

    @Select("""
            SELECT COUNT(*)
            FROM popups
            WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND (
                #{keyword} IS NULL OR #{keyword} = ''
                OR LOWER(title) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(linkUrl) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(popupImageOriFilename) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
            )
            """)
    long countByKeyword(@Param("conferenceSeq") Long conferenceSeq, @Param("keyword") String keyword);

    @Select("""
            SELECT COUNT(*)
            FROM popups
            WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND enabled = TRUE
              AND (
                #{keyword} IS NULL OR #{keyword} = ''
                OR LOWER(title) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(linkUrl) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(popupImageOriFilename) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              )
            """)
    long countEnabledByKeyword(@Param("conferenceSeq") Long conferenceSeq, @Param("keyword") String keyword);

    @Select("""
            SELECT *
            FROM popups
            WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND (
                #{keyword} IS NULL OR #{keyword} = ''
                OR LOWER(title) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(linkUrl) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(popupImageOriFilename) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
            )
            ORDER BY createdAt DESC, seq DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<Popup> findPage(@Param("conferenceSeq") Long conferenceSeq,
                         @Param("keyword") String keyword, @Param("size") int size, @Param("offset") int offset);

    @Select("SELECT * FROM popups WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    Popup findBySeq(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);

    @Insert("""
            INSERT INTO popups (
                conferenceSeq, title, linkUrl, enabled, useStartDate, useEndDate, content,
                popupImageOriFilename, popupImageSaveFilename, createdAt, updatedAt
            ) VALUES (
                #{conferenceSeq,javaType=java.lang.Long}, #{title}, #{linkUrl}, #{enabled}, #{useStartDate}, #{useEndDate}, #{content},
                #{popupImageOriFilename}, #{popupImageSaveFilename}, NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insert(Popup popup);

    @Update("""
            UPDATE popups
            SET title = #{title},
                linkUrl = #{linkUrl},
                enabled = #{enabled},
                useStartDate = #{useStartDate},
                useEndDate = #{useEndDate},
                content = #{content},
                popupImageOriFilename = #{popupImageOriFilename},
                popupImageSaveFilename = #{popupImageSaveFilename},
                updatedAt = NOW()
            WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    void update(Popup popup);

    @Delete("DELETE FROM popups WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    void delete(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);
}
