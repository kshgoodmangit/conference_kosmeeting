package com.bjworld21.congress.repository;

import com.bjworld21.congress.dto.MailInternalRecipientResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MailInternalRecipientRepository {

    @Select("""
            SELECT recipientType, referenceSeq, email, fullName, affiliation, detail
            FROM (
                SELECT 'MEMBER' AS recipientType,
                       m.seq AS referenceSeq,
            """ + MemberPersonalDataSql.EMAIL + """
                       AS email,
                       NULLIF(TRIM(CONCAT_WS(' ',
            """ + MemberPersonalDataSql.FIRST_NAME + """
                           ,
            """ + MemberPersonalDataSql.LAST_NAME + """
                       )), '') AS fullName,
                       m.institution AS affiliation,
                       CASE m.memberType
                           WHEN 'domestic' THEN '국내회원'
                           WHEN 'international' THEN '해외회원'
                           ELSE '회원'
                       END AS detail
                FROM members m
                WHERE (#{category} = 'ALL' OR #{category} = 'MEMBER')
                  AND m.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
                  AND (
                      LOWER(
            """ + MemberPersonalDataSql.EMAIL + """
                      ) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                      OR LOWER(
            """ + MemberPersonalDataSql.FIRST_NAME + """
                      ) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                      OR LOWER(
            """ + MemberPersonalDataSql.LAST_NAME + """
                      ) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                      OR LOWER(CONCAT_WS(' ',
            """ + MemberPersonalDataSql.FIRST_NAME + """
                          ,
            """ + MemberPersonalDataSql.LAST_NAME + """
                      )) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                      OR LOWER(COALESCE(m.institution, '')) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                  )
                UNION ALL
                SELECT 'ADMIN' AS recipientType,
                       a.seq AS referenceSeq,
                       CONVERT(AES_DECRYPT(UNHEX(a.email), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS email,
                       CONVERT(AES_DECRYPT(UNHEX(a.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS fullName,
                       NULL AS affiliation,
                       CASE a.role
                           WHEN 'reviewer' THEN '심사위원'
                           ELSE '관리자'
                       END AS detail
                FROM admin_accounts a
                WHERE a.status = 'active'
                  AND (a.role <> 'reviewer' OR EXISTS (
                      SELECT 1 FROM reviewers reviewer
                      WHERE reviewer.adminSeq = a.seq
                        AND reviewer.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
                        AND reviewer.isDelete = 'N'
                  ))
                  AND (#{category} = 'ALL' OR #{category} = 'ADMIN')
                  AND (
                      LOWER(CONVERT(AES_DECRYPT(UNHEX(a.email), SHA2(#{dbEncString}, 512)) USING utf8mb4)) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                      OR LOWER(CONVERT(AES_DECRYPT(UNHEX(a.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4)) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                  )
                UNION ALL
                SELECT 'ADDRESS_BOOK' AS recipientType,
                       c.seq AS referenceSeq,
                       CONVERT(AES_DECRYPT(UNHEX(c.email), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS email,
                       CONVERT(AES_DECRYPT(UNHEX(c.fullName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS fullName,
                       c.affiliation,
                       b.addressBookName AS detail
                FROM mail_contacts c
                INNER JOIN mail_address_books b ON b.seq = c.addressBookSeq
                WHERE (#{category} = 'ALL' OR #{category} = 'ADDRESS_BOOK')
                  AND (
                      LOWER(CONVERT(AES_DECRYPT(UNHEX(c.email), SHA2(#{dbEncString}, 512)) USING utf8mb4)) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                      OR LOWER(COALESCE(CONVERT(AES_DECRYPT(UNHEX(c.fullName), SHA2(#{dbEncString}, 512)) USING utf8mb4), '')) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                      OR LOWER(COALESCE(c.affiliation, '')) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                      OR LOWER(b.addressBookName) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                  )
            ) internalRecipients
            ORDER BY fullName, email
            LIMIT #{limit}
            """)
    List<MailInternalRecipientResponse> search(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("limit") int limit,
            @Param("dbEncString") String dbEncString
    );
}
