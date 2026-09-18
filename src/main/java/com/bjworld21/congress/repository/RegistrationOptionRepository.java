package com.bjworld21.congress.repository;

import com.bjworld21.congress.dto.RegistrationOptionData.*;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface RegistrationOptionRepository {
    String FILTER = " WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} AND (#{keyword} = '' OR LOCATE(#{keyword}, optionName) > 0) "
            + "AND (#{status} = '' OR enabled = (#{status} = 'Y')) ";

    @Select("SELECT COUNT(*) AS totalCount, COALESCE(SUM(enabled = 1),0) AS enabledCount, "
            + "COALESCE(SUM(enabled = 0),0) AS disabledCount FROM registration_options" + FILTER)
    Summary summary(@Param("conferenceSeq") Long conferenceSeq, @Param("keyword") String keyword, @Param("status") String status);

    @Select("SELECT * FROM registration_options" + FILTER + "ORDER BY sortOrder, seq LIMIT #{size} OFFSET #{offset}")
    List<Option> page(@Param("conferenceSeq") Long conferenceSeq, @Param("keyword") String keyword,
                      @Param("status") String status, @Param("size") int size, @Param("offset") int offset);

    @Select("SELECT * FROM registration_options WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} AND seq = #{seq}")
    Option find(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);

    @Insert("""
        INSERT INTO registration_options (conferenceSeq, optionName, description, krwPrice, usdPrice,
            saleStartsAt, saleEndsAt, changeEndsAt, capacity, maxPerPerson, enabled, sortOrder, versionNo)
        VALUES (#{conferenceSeq,javaType=java.lang.Long}, #{optionName}, #{description}, #{krwPrice}, #{usdPrice},
            #{saleStartsAt}, #{saleEndsAt}, #{changeEndsAt}, #{capacity}, #{maxPerPerson}, #{enabled}, #{sortOrder}, 0)
        """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    int insert(Option option);

    @Update("""
        UPDATE registration_options SET optionName=#{optionName}, description=#{description},
            krwPrice=#{krwPrice}, usdPrice=#{usdPrice}, saleStartsAt=#{saleStartsAt}, saleEndsAt=#{saleEndsAt},
            changeEndsAt=#{changeEndsAt}, capacity=#{capacity}, maxPerPerson=#{maxPerPerson},
            enabled=#{enabled}, sortOrder=#{sortOrder}, versionNo=versionNo+1
        WHERE conferenceSeq=#{conferenceSeq,javaType=java.lang.Long} AND seq=#{seq} AND versionNo=#{versionNo}
        """)
    int update(Option option);

    @Select("SELECT COUNT(*) FROM pre_registration_option_items WHERE optionSeq = #{seq}")
    int countSelections(@Param("seq") Long seq);

    @Delete("DELETE FROM registration_options WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} AND seq = #{seq}")
    int delete(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);
}
