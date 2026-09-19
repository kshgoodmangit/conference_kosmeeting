package com.bjworld21.conference.repository;

import com.bjworld21.conference.entity.MailAddressBook;
import com.bjworld21.conference.entity.MailContact;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface MailAddressBookRepository {

    @Select("""
            SELECT b.*, COUNT(c.seq) AS contactCount
            FROM mail_address_books b
            LEFT JOIN mail_contacts c ON c.addressBookSeq = b.seq
            WHERE #{keyword} = '' OR LOWER(b.addressBookName) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
            GROUP BY b.seq
            ORDER BY b.addressBookName, b.seq
            """)
    List<MailAddressBook> findAll(@Param("keyword") String keyword);

    @Select("SELECT * FROM mail_address_books WHERE seq = #{seq}")
    MailAddressBook findBySeq(Long seq);

    @Insert("""
            INSERT INTO mail_address_books (addressBookName, description, createdBy, createdAt, updatedAt)
            VALUES (#{addressBookName}, #{description}, #{createdBy}, NOW(), NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insert(MailAddressBook addressBook);

    @Update("""
            UPDATE mail_address_books
            SET addressBookName = #{addressBookName}, description = #{description}, updatedAt = NOW()
            WHERE seq = #{seq}
            """)
    void update(MailAddressBook addressBook);

    @Select("SELECT COUNT(*) FROM mail_campaign_recipient_sources WHERE sourceType = 'ADDRESS_BOOK' AND addressBookSeq = #{seq}")
    int countCampaignSources(Long seq);

    @Delete("DELETE FROM mail_address_books WHERE seq = #{seq}")
    int delete(Long seq);

    @Select("""
            SELECT c.seq, c.addressBookSeq,
                   CONVERT(AES_DECRYPT(UNHEX(c.email), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS email,
                   CONVERT(AES_DECRYPT(UNHEX(c.normalizedEmail), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS normalizedEmail,
                   CONVERT(AES_DECRYPT(UNHEX(c.fullName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS fullName,
                   c.affiliation, c.country,
                   CONVERT(AES_DECRYPT(UNHEX(c.phoneNumber), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS phoneNumber,
                   c.memo, c.createdAt, c.updatedAt
            FROM mail_contacts c
            WHERE c.addressBookSeq = #{addressBookSeq}
              AND (#{keyword} = ''
                   OR LOWER(CONVERT(AES_DECRYPT(UNHEX(c.email), SHA2(#{dbEncString}, 512)) USING utf8mb4)) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(COALESCE(CONVERT(AES_DECRYPT(UNHEX(c.fullName), SHA2(#{dbEncString}, 512)) USING utf8mb4), '')) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            ORDER BY CONVERT(AES_DECRYPT(UNHEX(c.fullName), SHA2(#{dbEncString}, 512)) USING utf8mb4),
                     CONVERT(AES_DECRYPT(UNHEX(c.email), SHA2(#{dbEncString}, 512)) USING utf8mb4)
            """)
    List<MailContact> findContacts(@Param("addressBookSeq") Long addressBookSeq, @Param("keyword") String keyword,
                                   @Param("dbEncString") String dbEncString);

    @Select("""
            SELECT seq, addressBookSeq,
                   CONVERT(AES_DECRYPT(UNHEX(email), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS email,
                   CONVERT(AES_DECRYPT(UNHEX(normalizedEmail), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS normalizedEmail,
                   CONVERT(AES_DECRYPT(UNHEX(fullName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS fullName,
                   affiliation, country,
                   CONVERT(AES_DECRYPT(UNHEX(phoneNumber), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS phoneNumber,
                   memo, createdAt, updatedAt
            FROM mail_contacts
            WHERE addressBookSeq = #{addressBookSeq}
              AND normalizedEmail = HEX(AES_ENCRYPT(#{normalizedEmail}, SHA2(#{dbEncString}, 512)))
            """)
    MailContact findContactByNormalizedEmail(@Param("addressBookSeq") Long addressBookSeq,
                                             @Param("normalizedEmail") String normalizedEmail,
                                             @Param("dbEncString") String dbEncString);

    @Insert("""
            INSERT INTO mail_contacts (
                addressBookSeq, email, normalizedEmail, fullName, affiliation, country, phoneNumber,
                memo, createdAt, updatedAt
            ) VALUES (
                #{contact.addressBookSeq},
                HEX(AES_ENCRYPT(#{contact.email}, SHA2(#{dbEncString}, 512))),
                HEX(AES_ENCRYPT(#{contact.normalizedEmail}, SHA2(#{dbEncString}, 512))),
                HEX(AES_ENCRYPT(#{contact.fullName}, SHA2(#{dbEncString}, 512))),
                #{contact.affiliation}, #{contact.country},
                HEX(AES_ENCRYPT(#{contact.phoneNumber}, SHA2(#{dbEncString}, 512))),
                #{contact.memo}, NOW(), NOW()
            )
            ON DUPLICATE KEY UPDATE
                seq = LAST_INSERT_ID(seq), email = VALUES(email), fullName = VALUES(fullName),
                affiliation = VALUES(affiliation), country = VALUES(country), phoneNumber = VALUES(phoneNumber),
                memo = VALUES(memo), updatedAt = NOW()
            """)
    @Options(useGeneratedKeys = true, keyProperty = "contact.seq")
    void upsertContact(@Param("contact") MailContact contact, @Param("dbEncString") String dbEncString);

    @Insert("""
            <script>
            INSERT INTO mail_contacts (
                addressBookSeq, email, normalizedEmail, fullName, affiliation, phoneNumber,
                createdAt, updatedAt
            ) VALUES
            <foreach collection="contacts" item="contact" separator=",">
                (#{contact.addressBookSeq},
                 HEX(AES_ENCRYPT(#{contact.email}, SHA2(#{dbEncString}, 512))),
                 HEX(AES_ENCRYPT(#{contact.normalizedEmail}, SHA2(#{dbEncString}, 512))),
                 HEX(AES_ENCRYPT(#{contact.fullName}, SHA2(#{dbEncString}, 512))),
                 #{contact.affiliation}, HEX(AES_ENCRYPT(#{contact.phoneNumber}, SHA2(#{dbEncString}, 512))),
                 NOW(), NOW())
            </foreach>
            ON DUPLICATE KEY UPDATE
                email = VALUES(email), fullName = VALUES(fullName),
                affiliation = COALESCE(VALUES(affiliation), affiliation),
                phoneNumber = COALESCE(VALUES(phoneNumber), phoneNumber), updatedAt = NOW()
            </script>
            """)
    void upsertImportedContacts(@Param("contacts") List<MailContact> contacts,
                                @Param("dbEncString") String dbEncString);

    @Select("""
            <script>
            SELECT seq,
                   CONVERT(AES_DECRYPT(UNHEX(normalizedEmail), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS normalizedEmail
            FROM mail_contacts
            WHERE addressBookSeq = #{addressBookSeq}
              AND normalizedEmail IN
            <foreach collection="normalizedEmails" item="normalizedEmail" open="(" separator="," close=")">
                HEX(AES_ENCRYPT(#{normalizedEmail}, SHA2(#{dbEncString}, 512)))
            </foreach>
            </script>
            """)
    List<MailContact> findContactRefsByNormalizedEmails(@Param("addressBookSeq") Long addressBookSeq,
                                                        @Param("normalizedEmails") List<String> normalizedEmails,
                                                        @Param("dbEncString") String dbEncString);

    @Delete("DELETE FROM mail_contacts WHERE addressBookSeq = #{addressBookSeq} AND seq = #{contactSeq}")
    int deleteContact(@Param("addressBookSeq") Long addressBookSeq, @Param("contactSeq") Long contactSeq);
}
