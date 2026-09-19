package com.bjworld21.conference.repository;

import com.bjworld21.conference.entity.MailRecipientCandidate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MailRecipientGroupRepository {
    // EXISTS prevents multiple registrations or abstracts from multiplying recipients within a group.
    @Select("""
            SELECT
            """ + MemberPersonalDataSql.EMAIL + """
                   AS email,
                   NULLIF(TRIM(CONCAT_WS(' ',
            """ + MemberPersonalDataSql.FIRST_NAME + """
                       ,
            """ + MemberPersonalDataSql.LAST_NAME + """
                   )), '') AS fullName,
                   m.institution AS affiliation
            FROM members m
            WHERE m.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND
            """ + com.bjworld21.conference.service.RecipientGroups.MEMBER_PREDICATE + """
            ORDER BY m.seq
            """)
    List<MailRecipientCandidate> findGroup(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("group") String group,
            @Param("dbEncString") String dbEncString
    );

    @Select("""
            <script>
            SELECT seq AS contactSeq,
                   CONVERT(AES_DECRYPT(UNHEX(email), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS email,
                   CONVERT(AES_DECRYPT(UNHEX(normalizedEmail), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS normalizedEmail,
                   CONVERT(AES_DECRYPT(UNHEX(fullName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS fullName,
                   affiliation
            FROM mail_contacts WHERE addressBookSeq IN
            <foreach collection="bookSeqs" item="seq" open="(" separator="," close=")">#{seq}</foreach>
            ORDER BY seq
            </script>
            """)
    List<MailRecipientCandidate> findAddressBooks(@Param("bookSeqs") List<Long> bookSeqs,
                                                  @Param("dbEncString") String dbEncString);
}
