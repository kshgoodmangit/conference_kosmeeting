package com.bjworld21.congress.repository;

import com.bjworld21.congress.entity.SponsorshipApplication;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SponsorshipApplicationRepository {

    String SELECT_COLUMNS = """
            SELECT s.seq, s.companyKrName, s.companyEnName,
                   CONVERT(AES_DECRYPT(UNHEX(s.ceoName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS ceoName,
                   s.businessNumber, s.zonecode, s.address, s.addressDetail,
                   s.sponsorshipType, s.sponsorshipAmount,
                   s.businessLicenseOriFilename, s.businessLicenseSaveFilename,
                   CONVERT(AES_DECRYPT(UNHEX(s.contactPersonName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS contactPersonName,
                   s.contactPersonPosition, s.contactPersonDepartment,
                   CONVERT(AES_DECRYPT(UNHEX(s.contactPersonPhone), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS contactPersonPhone,
                   CONVERT(AES_DECRYPT(UNHEX(s.contactPersonMobile), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS contactPersonMobile,
                   CONVERT(AES_DECRYPT(UNHEX(s.contactPersonEmail), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS contactPersonEmail,
                   CONVERT(AES_DECRYPT(UNHEX(s.faxNumber), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS faxNumber,
                   s.isDeposited, s.depositDate, s.expectedDepositDate,
                   CONVERT(AES_DECRYPT(UNHEX(s.taxInvoiceRecipient), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS taxInvoiceRecipient,
                   CONVERT(AES_DECRYPT(UNHEX(s.taxInvoiceEmail), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS taxInvoiceEmail,
                   s.taxInvoiceIssueDate, s.taxInvoiceType, s.remarks, s.createdAt, s.updatedAt
            FROM sponsorship_applications s
            """;

    String FILTER = """
            WHERE (
                #{keyword} IS NULL OR #{keyword} = ''
                OR LOWER(s.companyKrName) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(COALESCE(s.companyEnName, '')) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(s.businessNumber) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(CONVERT(AES_DECRYPT(UNHEX(s.contactPersonName), SHA2(#{dbEncString}, 512)) USING utf8mb4))
                    LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(CONVERT(AES_DECRYPT(UNHEX(s.contactPersonEmail), SHA2(#{dbEncString}, 512)) USING utf8mb4))
                    LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(s.sponsorshipType) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
            )
              AND s.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """;

    @Select("SELECT COUNT(*) FROM sponsorship_applications s " + FILTER)
    long countByKeyword(@Param("conferenceSeq") Long conferenceSeq,
                        @Param("keyword") String keyword, @Param("dbEncString") String dbEncString);

    @Select("SELECT COUNT(*) FROM sponsorship_applications s " + FILTER + " AND s.isDeposited = #{deposited}")
    long countByDeposited(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("keyword") String keyword,
            @Param("deposited") boolean deposited,
            @Param("dbEncString") String dbEncString
    );

    @Select("SELECT COALESCE(SUM(s.sponsorshipAmount), 0) FROM sponsorship_applications s " + FILTER)
    long sumAmountByKeyword(@Param("conferenceSeq") Long conferenceSeq,
                            @Param("keyword") String keyword, @Param("dbEncString") String dbEncString);

    @Select(SELECT_COLUMNS + FILTER + " ORDER BY s.createdAt DESC, s.seq DESC LIMIT #{size} OFFSET #{offset}")
    List<SponsorshipApplication> findPage(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("keyword") String keyword,
            @Param("size") int size,
            @Param("offset") int offset,
            @Param("dbEncString") String dbEncString
    );

    @Select(SELECT_COLUMNS + " WHERE s.seq = #{seq} AND s.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    SponsorshipApplication findBySeq(@Param("conferenceSeq") Long conferenceSeq,
                                     @Param("seq") Long seq, @Param("dbEncString") String dbEncString);

    @Select(SELECT_COLUMNS + " WHERE s.businessNumber = #{businessNumber} AND s.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    SponsorshipApplication findByBusinessNumber(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("businessNumber") String businessNumber,
            @Param("dbEncString") String dbEncString
    );

    @Select(SELECT_COLUMNS + " WHERE s.businessNumber = #{businessNumber} AND s.seq <> #{seq} AND s.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    SponsorshipApplication findByBusinessNumberExceptSeq(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("businessNumber") String businessNumber,
            @Param("seq") Long seq,
            @Param("dbEncString") String dbEncString
    );

    @Insert("""
            INSERT INTO sponsorship_applications (
                conferenceSeq, companyKrName, companyEnName, ceoName, businessNumber, zonecode,
                address, addressDetail, sponsorshipType, sponsorshipAmount,
                businessLicenseOriFilename, businessLicenseSaveFilename,
                contactPersonName, contactPersonPosition, contactPersonDepartment,
                contactPersonPhone, contactPersonMobile, contactPersonEmail, faxNumber,
                isDeposited, depositDate, expectedDepositDate,
                taxInvoiceRecipient, taxInvoiceEmail, taxInvoiceIssueDate, taxInvoiceType,
                remarks, createdAt, updatedAt
            ) VALUES (
                #{conferenceSeq,javaType=java.lang.Long}, #{item.companyKrName}, #{item.companyEnName},
                HEX(AES_ENCRYPT(#{item.ceoName}, SHA2(#{dbEncString}, 512))),
                #{item.businessNumber}, #{item.zonecode}, #{item.address}, #{item.addressDetail},
                #{item.sponsorshipType}, #{item.sponsorshipAmount},
                #{item.businessLicenseOriFilename}, #{item.businessLicenseSaveFilename},
                HEX(AES_ENCRYPT(#{item.contactPersonName}, SHA2(#{dbEncString}, 512))),
                #{item.contactPersonPosition}, #{item.contactPersonDepartment},
                HEX(AES_ENCRYPT(#{item.contactPersonPhone}, SHA2(#{dbEncString}, 512))),
                HEX(AES_ENCRYPT(#{item.contactPersonMobile}, SHA2(#{dbEncString}, 512))),
                HEX(AES_ENCRYPT(#{item.contactPersonEmail}, SHA2(#{dbEncString}, 512))),
                HEX(AES_ENCRYPT(#{item.faxNumber}, SHA2(#{dbEncString}, 512))),
                #{item.isDeposited}, #{item.depositDate}, #{item.expectedDepositDate},
                HEX(AES_ENCRYPT(#{item.taxInvoiceRecipient}, SHA2(#{dbEncString}, 512))),
                HEX(AES_ENCRYPT(#{item.taxInvoiceEmail}, SHA2(#{dbEncString}, 512))),
                #{item.taxInvoiceIssueDate}, #{item.taxInvoiceType}, #{item.remarks}, NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "item.seq")
    void insert(@Param("conferenceSeq") Long conferenceSeq,
                @Param("item") SponsorshipApplication application, @Param("dbEncString") String dbEncString);

    @Update("""
            UPDATE sponsorship_applications
            SET companyKrName = #{item.companyKrName}, companyEnName = #{item.companyEnName},
                ceoName = HEX(AES_ENCRYPT(#{item.ceoName}, SHA2(#{dbEncString}, 512))),
                businessNumber = #{item.businessNumber}, zonecode = #{item.zonecode},
                address = #{item.address}, addressDetail = #{item.addressDetail},
                sponsorshipType = #{item.sponsorshipType}, sponsorshipAmount = #{item.sponsorshipAmount},
                businessLicenseOriFilename = #{item.businessLicenseOriFilename},
                businessLicenseSaveFilename = #{item.businessLicenseSaveFilename},
                contactPersonName = HEX(AES_ENCRYPT(#{item.contactPersonName}, SHA2(#{dbEncString}, 512))),
                contactPersonPosition = #{item.contactPersonPosition},
                contactPersonDepartment = #{item.contactPersonDepartment},
                contactPersonPhone = HEX(AES_ENCRYPT(#{item.contactPersonPhone}, SHA2(#{dbEncString}, 512))),
                contactPersonMobile = HEX(AES_ENCRYPT(#{item.contactPersonMobile}, SHA2(#{dbEncString}, 512))),
                contactPersonEmail = HEX(AES_ENCRYPT(#{item.contactPersonEmail}, SHA2(#{dbEncString}, 512))),
                faxNumber = HEX(AES_ENCRYPT(#{item.faxNumber}, SHA2(#{dbEncString}, 512))),
                isDeposited = #{item.isDeposited}, depositDate = #{item.depositDate},
                expectedDepositDate = #{item.expectedDepositDate},
                taxInvoiceRecipient = HEX(AES_ENCRYPT(#{item.taxInvoiceRecipient}, SHA2(#{dbEncString}, 512))),
                taxInvoiceEmail = HEX(AES_ENCRYPT(#{item.taxInvoiceEmail}, SHA2(#{dbEncString}, 512))),
                taxInvoiceIssueDate = #{item.taxInvoiceIssueDate}, taxInvoiceType = #{item.taxInvoiceType},
                remarks = #{item.remarks}, updatedAt = NOW()
            WHERE seq = #{item.seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    void update(@Param("conferenceSeq") Long conferenceSeq,
                @Param("item") SponsorshipApplication application, @Param("dbEncString") String dbEncString);

    @Delete("DELETE FROM sponsorship_applications WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    void delete(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);
}
