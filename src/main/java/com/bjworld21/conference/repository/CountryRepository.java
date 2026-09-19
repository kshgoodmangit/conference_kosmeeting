package com.bjworld21.conference.repository;

import com.bjworld21.conference.entity.Country;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CountryRepository {

    @Select("""
            SELECT COUNT(*)
            FROM countries
            WHERE (
                #{keyword} IS NULL OR #{keyword} = ''
                OR LOWER(isoAlpha2) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(isoAlpha3) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR isoNumeric LIKE CONCAT('%', #{keyword}, '%')
                OR LOWER(countryName) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(COALESCE(countryNameKo, '')) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR COALESCE(dialCode, '') LIKE CONCAT('%', #{keyword}, '%')
            )
            """)
    long countByKeyword(@Param("keyword") String keyword);

    @Select("""
            SELECT COUNT(*)
            FROM countries
            WHERE COALESCE(isUsed, 'Y') = #{isUsed}
              AND (
                #{keyword} IS NULL OR #{keyword} = ''
                OR LOWER(isoAlpha2) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(isoAlpha3) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR isoNumeric LIKE CONCAT('%', #{keyword}, '%')
                OR LOWER(countryName) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(COALESCE(countryNameKo, '')) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR COALESCE(dialCode, '') LIKE CONCAT('%', #{keyword}, '%')
              )
            """)
    long countByKeywordAndIsUsed(@Param("keyword") String keyword, @Param("isUsed") String isUsed);

    @Select("""
            SELECT seq, isoAlpha2, isoAlpha3, isoNumeric, countryName, countryNameKo, dialCode,
                   COALESCE(isUsed, 'Y') AS isUsed
            FROM countries
            WHERE (
                #{keyword} IS NULL OR #{keyword} = ''
                OR LOWER(isoAlpha2) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(isoAlpha3) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR isoNumeric LIKE CONCAT('%', #{keyword}, '%')
                OR LOWER(countryName) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(COALESCE(countryNameKo, '')) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR COALESCE(dialCode, '') LIKE CONCAT('%', #{keyword}, '%')
            )
            ORDER BY countryName ASC, isoAlpha2 ASC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<Country> findPage(@Param("keyword") String keyword, @Param("size") int size, @Param("offset") int offset);

    @Select("""
            SELECT seq, isoAlpha2, isoAlpha3, isoNumeric, countryName, countryNameKo, dialCode,
                   COALESCE(isUsed, 'Y') AS isUsed
            FROM countries
            WHERE isUsed = 'Y'
            ORDER BY countryName ASC, isoAlpha2 ASC
            """)
    List<Country> findUsed();

    @Select("""
            SELECT seq, isoAlpha2, isoAlpha3, isoNumeric, countryName, countryNameKo, dialCode,
                   COALESCE(isUsed, 'Y') AS isUsed
            FROM countries
            WHERE seq = #{seq}
            """)
    Country findBySeq(@Param("seq") Long seq);

    @Update("UPDATE countries SET isUsed = #{isUsed} WHERE seq = #{seq}")
    int updateIsUsed(@Param("seq") Long seq, @Param("isUsed") String isUsed);
}
