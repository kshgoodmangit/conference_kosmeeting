package com.bjworld21.congress.repository;

import com.bjworld21.congress.dto.PreRegistrationCategoryOptionResponse;
import com.bjworld21.congress.dto.PreRegistrationResponse;
import com.bjworld21.congress.dto.PreRegistrationSummary;
import com.bjworld21.congress.dto.MemberPreRegistrationResponse;
import com.bjworld21.congress.dto.PreRegistrationOptionData;
import com.bjworld21.congress.dto.RegistrationOptionData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PreRegistrationRepository {

    String SELECT_COLUMNS = """
            SELECT p.seq,
                   p.registrationNumber,
                   p.memberSeq,
                   p.conferenceSeq,
                   c.eventName,
                   m.memberType,
            """ + MemberPersonalDataSql.EMAIL + """
                   AS email,
            """ + MemberPersonalDataSql.FIRST_NAME + """
                   AS firstName,
            """ + MemberPersonalDataSql.LAST_NAME + """
                   AS lastName,
                   m.institution,
                   m.department,
                   m.positionTitle,
                   m.country,
            """ + MemberPersonalDataSql.MOBILE + """
                   AS mobile,
                   p.categorySeq,
                   p.categoryCode,
                   p.categoryName,
                   p.periodType,
                   p.currency,
                   p.feeAmount,
                   p.optionAmount,
                   COALESCE(p.totalAmount, p.feeAmount) AS totalAmount,
                   p.applicationStatus,
                   p.paymentStatus,
                   p.paymentMethod,
                   p.paymentTransactionId,
                   p.paidAmount,
                   p.paidAt,
                   p.privacyAgreed,
                   p.privacyAgreedAt,
                   p.termsAgreed,
                   p.termsAgreedAt,
                   p.cancelledAt,
                   p.adminMemo,
                   p.createdAt,
                   p.updatedAt
            FROM pre_registrations p
            INNER JOIN members m ON m.seq = p.memberSeq AND m.conferenceSeq = p.conferenceSeq
            LEFT JOIN conference_settings c ON c.seq = p.conferenceSeq
            """;

    String FILTER_SCRIPT = """
            <where>
                p.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
                <if test='keyword != null and keyword != ""'>
                    AND (
                        LOWER(p.registrationNumber) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                        OR LOWER(
            """ + MemberPersonalDataSql.EMAIL + """
                        ) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                        OR LOWER(
            """ + MemberPersonalDataSql.FIRST_NAME + """
                        ) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                        OR LOWER(
            """ + MemberPersonalDataSql.LAST_NAME + """
                        ) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                        OR LOWER(CONCAT(
            """ + MemberPersonalDataSql.FIRST_NAME + """
                            , ' ',
            """ + MemberPersonalDataSql.LAST_NAME + """
                        )) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                        OR LOWER(CONCAT(
            """ + MemberPersonalDataSql.LAST_NAME + """
                            ,
            """ + MemberPersonalDataSql.FIRST_NAME + """
                        )) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                        OR LOWER(m.institution) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                    )
                </if>
                <if test='categorySeq != null'>
                    AND p.categorySeq = #{categorySeq}
                </if>
                <if test='optionSeq != null'>
                    AND EXISTS (
                        SELECT 1
                        FROM pre_registration_option_items filterOption
                        WHERE filterOption.preRegistrationSeq = p.seq
                          AND filterOption.optionSeq = #{optionSeq}
                    )
                </if>
                <if test='periodType != null and periodType != ""'>
                    AND p.periodType = #{periodType}
                </if>
                <if test='applicationStatus != null and applicationStatus != ""'>
                    AND p.applicationStatus = #{applicationStatus}
                </if>
                <if test='paymentStatus != null and paymentStatus != ""'>
                    AND p.paymentStatus = #{paymentStatus}
                </if>
                <if test='dateFrom != null'>
                    AND p.createdAt &gt;= #{dateFrom}
                </if>
                <if test='dateTo != null'>
                    AND p.createdAt &lt; DATE_ADD(#{dateTo}, INTERVAL 1 DAY)
                </if>
            </where>
            """;

    @Select("""
            <script>
            """ + SELECT_COLUMNS + FILTER_SCRIPT + """
            ORDER BY p.createdAt DESC, p.seq DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<PreRegistrationResponse> findPage(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("keyword") String keyword,
            @Param("categorySeq") Long categorySeq,
            @Param("optionSeq") Long optionSeq,
            @Param("periodType") String periodType,
            @Param("applicationStatus") String applicationStatus,
            @Param("paymentStatus") String paymentStatus,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("size") int size,
            @Param("offset") int offset,
            @Param("dbEncString") String dbEncString
    );

    @Select("""
            <script>
            SELECT COUNT(*) AS totalCount,
                   COALESCE(SUM(CASE WHEN p.applicationStatus = 'SUBMITTED' THEN 1 ELSE 0 END), 0) AS submittedCount,
                   COALESCE(SUM(CASE WHEN p.applicationStatus = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelledCount,
                   COALESCE(SUM(CASE WHEN p.paymentStatus = 'PAID' THEN 1 ELSE 0 END), 0) AS paidCount,
                   COALESCE(SUM(CASE WHEN p.paymentStatus = 'UNPAID' THEN 1 ELSE 0 END), 0) AS unpaidCount,
                   COALESCE(SUM(CASE WHEN p.paymentStatus = 'PAID' AND p.currency = 'KRW' THEN COALESCE(p.paidAmount, p.totalAmount, p.feeAmount) ELSE 0 END), 0) AS totalPaidKrwAmount,
                   COALESCE(SUM(CASE WHEN p.paymentStatus = 'PAID' AND p.currency = 'USD' THEN COALESCE(p.paidAmount, p.totalAmount, p.feeAmount) ELSE 0 END), 0) AS totalPaidUsdAmount
            FROM pre_registrations p
            INNER JOIN members m ON m.seq = p.memberSeq
            """ + FILTER_SCRIPT + """
            </script>
            """)
    PreRegistrationSummary findSummary(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("keyword") String keyword,
            @Param("categorySeq") Long categorySeq,
            @Param("optionSeq") Long optionSeq,
            @Param("periodType") String periodType,
            @Param("applicationStatus") String applicationStatus,
            @Param("paymentStatus") String paymentStatus,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("dbEncString") String dbEncString
    );

    @Select(SELECT_COLUMNS + " WHERE p.seq = #{seq} AND p.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    PreRegistrationResponse findBySeq(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("seq") Long seq,
            @Param("dbEncString") String dbEncString
    );

    @Select("SELECT seq FROM pre_registrations WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} FOR UPDATE")
    Long lockRegistration(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);

    @Select("""
            SELECT o.seq AS optionSeq, o.optionName, o.description, #{currency} AS currency,
                   CASE #{currency} WHEN 'KRW' THEN o.krwPrice WHEN 'USD' THEN o.usdPrice END AS unitPrice,
                   o.maxPerPerson, o.enabled,
                   CASE WHEN o.capacity IS NULL THEN NULL ELSE o.capacity - COALESCE((
                       SELECT SUM(i.quantity) FROM pre_registration_option_items i
                       INNER JOIN pre_registrations p ON p.seq = i.preRegistrationSeq
                       WHERE i.optionSeq = o.seq AND p.conferenceSeq = o.conferenceSeq
                         AND p.applicationStatus = 'SUBMITTED'
                   ), 0) END AS remainingCapacity
            FROM registration_options o
            INNER JOIN conference_settings c ON c.seq = o.conferenceSeq
            WHERE o.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND CASE #{currency} WHEN 'KRW' THEN o.krwPrice WHEN 'USD' THEN o.usdPrice END IS NOT NULL
            ORDER BY o.sortOrder, o.seq
            """)
    List<PreRegistrationOptionData.CatalogItem> findAvailableOptions(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("currency") String currency
    );

    @Select("""
            SELECT o.seq AS optionSeq, o.optionName, o.description, #{currency} AS currency,
                   CASE #{currency} WHEN 'KRW' THEN o.krwPrice WHEN 'USD' THEN o.usdPrice END AS unitPrice,
                   o.maxPerPerson, o.enabled,
                   CASE WHEN o.capacity IS NULL THEN NULL ELSE o.capacity - COALESCE((
                       SELECT SUM(i.quantity) FROM pre_registration_option_items i
                       INNER JOIN pre_registrations p ON p.seq = i.preRegistrationSeq
                       WHERE i.optionSeq = o.seq AND p.conferenceSeq = o.conferenceSeq
                         AND p.applicationStatus = 'SUBMITTED'
                   ), 0) END AS remainingCapacity
            FROM registration_options o
            WHERE o.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND o.enabled = TRUE
              AND (o.saleStartsAt IS NULL OR o.saleStartsAt <= #{now})
              AND (o.saleEndsAt IS NULL OR o.saleEndsAt >= #{now})
              AND CASE #{currency} WHEN 'KRW' THEN o.krwPrice WHEN 'USD' THEN o.usdPrice END IS NOT NULL
            ORDER BY o.sortOrder, o.seq
            """)
    List<PreRegistrationOptionData.CatalogItem> findPublicAvailableOptions(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("currency") String currency,
            @Param("now") LocalDateTime now
    );

    @Select("SELECT * FROM registration_options WHERE seq = #{optionSeq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} FOR UPDATE")
    RegistrationOptionData.Option lockOption(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("optionSeq") Long optionSeq
    );

    @Select("""
            SELECT COALESCE(SUM(i.quantity), 0)
            FROM pre_registration_option_items i
            INNER JOIN pre_registrations p ON p.seq = i.preRegistrationSeq
            WHERE i.optionSeq = #{optionSeq} AND p.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND p.applicationStatus = 'SUBMITTED'
            """)
    int usedOptionQuantity(@Param("conferenceSeq") Long conferenceSeq, @Param("optionSeq") Long optionSeq);

    @Select("""
            SELECT COALESCE(SUM(i.quantity), 0)
            FROM pre_registration_option_items i
            INNER JOIN pre_registrations p ON p.seq = i.preRegistrationSeq
            WHERE i.optionSeq = #{optionSeq} AND p.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND p.applicationStatus = 'SUBMITTED'
              AND i.preRegistrationSeq != #{preRegistrationSeq}
            """)
    int usedOptionQuantityExcluding(@Param("conferenceSeq") Long conferenceSeq,
                                    @Param("optionSeq") Long optionSeq,
                                    @Param("preRegistrationSeq") Long preRegistrationSeq);

    @Select("""
            SELECT item.optionSeq
            FROM pre_registration_option_items item
            JOIN pre_registrations registration ON registration.seq = item.preRegistrationSeq
            WHERE item.preRegistrationSeq = #{seq}
              AND registration.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            ORDER BY item.optionSeq
            """)
    List<Long> findOptionSeqs(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);

    @Select("""
            SELECT i.seq, i.optionSeq, o.optionName, o.description AS optionDescription,
                   p.currency,
                   CASE p.currency WHEN 'KRW' THEN o.krwPrice WHEN 'USD' THEN o.usdPrice END AS unitPrice,
                   i.quantity,
                   CASE p.currency WHEN 'KRW' THEN o.krwPrice WHEN 'USD' THEN o.usdPrice END * i.quantity AS amount
            FROM pre_registration_option_items i
            INNER JOIN pre_registrations p ON p.seq = i.preRegistrationSeq
            INNER JOIN registration_options o ON o.seq = i.optionSeq AND o.conferenceSeq = p.conferenceSeq
            WHERE i.preRegistrationSeq = #{seq}
              AND p.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            ORDER BY o.sortOrder, o.seq
            """)
    List<PreRegistrationOptionData.Item> findOptionsByRegistrationSeq(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("seq") Long seq
    );

    @Insert("""
            INSERT INTO pre_registration_option_items
                (preRegistrationSeq, optionSeq, quantity, createdAt)
            VALUES (#{preRegistrationSeq}, #{item.optionSeq}, #{item.quantity}, NOW())
            """)
    int insertOption(@Param("preRegistrationSeq") Long preRegistrationSeq,
                     @Param("item") PreRegistrationOptionData.Item item);

    @Delete("DELETE FROM pre_registration_option_items WHERE preRegistrationSeq = #{preRegistrationSeq}")
    int deleteOptions(@Param("preRegistrationSeq") Long preRegistrationSeq);

    @Select("SELECT seq FROM members WHERE seq = #{memberSeq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} FOR UPDATE")
    Long lockMember(@Param("conferenceSeq") Long conferenceSeq, @Param("memberSeq") Long memberSeq);

    @Select("SELECT memberType FROM members WHERE seq = #{memberSeq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    String findMemberType(@Param("conferenceSeq") Long conferenceSeq, @Param("memberSeq") Long memberSeq);

    @Select("""
            SELECT rate.amount
            FROM registration_fee_rates rate
            JOIN registration_categories category ON category.seq = rate.categorySeq
            WHERE rate.categorySeq = #{categorySeq}
              AND category.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND rate.periodType = #{periodType}
              AND rate.currency = #{currency}
            """)
    java.math.BigDecimal findFee(@Param("conferenceSeq") Long conferenceSeq,
                                @Param("categorySeq") Long categorySeq,
                                @Param("periodType") String periodType, @Param("currency") String currency);

    @Select("""
            SELECT seq FROM pre_registrations
            WHERE memberSeq = #{memberSeq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} AND applicationStatus = 'SUBMITTED'
            LIMIT 1 FOR UPDATE
            """)
    Long findSubmittedSeqForUpdate(@Param("memberSeq") Long memberSeq, @Param("conferenceSeq") Long conferenceSeq);

    @Select("""
            SELECT seq FROM pre_registrations
            WHERE memberSeq = #{memberSeq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND applicationStatus = 'SUBMITTED'
            ORDER BY createdAt DESC, seq DESC
            LIMIT 1
            """)
    Long findSubmittedSeq(@Param("memberSeq") Long memberSeq, @Param("conferenceSeq") Long conferenceSeq);

    @Insert("""
            INSERT INTO pre_registrations (
                registrationNumber, memberSeq, conferenceSeq, categorySeq, categoryCode, categoryName,
                periodType, currency, feeAmount, optionAmount, totalAmount, applicationStatus, paymentStatus,
                privacyAgreed, termsAgreed, adminMemo, createdAt, updatedAt
            ) VALUES (
                #{registrationNumber}, #{memberSeq}, #{conferenceSeq,javaType=java.lang.Long}, #{categorySeq}, #{categoryCode}, #{categoryName},
                #{periodType}, #{currency}, #{feeAmount}, #{optionAmount}, #{totalAmount}, 'SUBMITTED', 'UNPAID',
                FALSE, FALSE, #{adminMemo}, NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    int insert(PreRegistrationResponse registration);

    @Insert("""
            INSERT INTO pre_registrations (
                registrationNumber, memberSeq, conferenceSeq, categorySeq, categoryCode, categoryName,
                periodType, currency, feeAmount, optionAmount, totalAmount, applicationStatus, paymentStatus,
                paymentMethod, paidAmount, paidAt,
                privacyAgreed, privacyAgreedAt, termsAgreed, termsAgreedAt, createdAt, updatedAt
            ) VALUES (
                #{registrationNumber}, #{memberSeq}, #{conferenceSeq,javaType=java.lang.Long}, #{categorySeq}, #{categoryCode}, #{categoryName},
                #{periodType}, #{currency}, #{feeAmount}, #{optionAmount}, #{totalAmount}, 'SUBMITTED', #{paymentStatus},
                #{paymentMethod}, #{paidAmount}, #{paidAt},
                TRUE, NOW(), TRUE, NOW(), NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    int insertPublic(PreRegistrationResponse registration);

    @Update("UPDATE pre_registrations SET registrationNumber = #{registrationNumber} WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    int assignRegistrationNumber(@Param("conferenceSeq") Long conferenceSeq,
                                 @Param("seq") Long seq,
                                 @Param("registrationNumber") String registrationNumber);

    @Select("""
            SELECT seq AS categorySeq, categoryCode, categoryName
            FROM registration_categories
            WHERE seq = #{categorySeq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND isDelete = 'N'
            """)
    PreRegistrationCategoryOptionResponse findCategoryBySeq(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("categorySeq") Long categorySeq
    );

    @Select("""
            SELECT seq AS categorySeq, categoryCode, categoryName
            FROM registration_categories
            WHERE seq = #{categorySeq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND isUsed = 'Y'
              AND isDelete = 'N'
            """)
    PreRegistrationCategoryOptionResponse findPublicCategoryBySeq(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("categorySeq") Long categorySeq
    );

    @Update("""
            UPDATE pre_registrations
            SET categorySeq = #{categorySeq},
                categoryCode = #{categoryCode},
                categoryName = #{categoryName},
                periodType = #{periodType},
                currency = #{currency},
                feeAmount = #{feeAmount},
                optionAmount = #{optionAmount},
                totalAmount = #{feeAmount} + #{optionAmount},
                applicationStatus = #{applicationStatus},
                cancelledAt = CASE
                    WHEN #{applicationStatus} = 'CANCELLED' THEN COALESCE(cancelledAt, NOW())
                    ELSE NULL
                END,
                adminMemo = #{adminMemo},
                updatedAt = NOW()
            WHERE seq = #{seq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    int update(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("seq") Long seq,
            @Param("categorySeq") Long categorySeq,
            @Param("categoryCode") String categoryCode,
            @Param("categoryName") String categoryName,
            @Param("periodType") String periodType,
            @Param("currency") String currency,
            @Param("feeAmount") java.math.BigDecimal feeAmount,
            @Param("optionAmount") java.math.BigDecimal optionAmount,
            @Param("applicationStatus") String applicationStatus,
            @Param("adminMemo") String adminMemo
    );

    @Delete("""
            DELETE FROM pre_registrations
            WHERE seq = #{seq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND paymentStatus != 'PAID'
            """)
    int delete(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);

    @Update("""
            UPDATE pre_registrations
            SET paymentStatus = 'REFUNDED',
                updatedAt = NOW()
            WHERE seq = #{seq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND paymentStatus = 'PAID'
            """)
    int cancelPayment(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);

    @Update("""
            UPDATE pre_registrations
            SET applicationStatus = 'CANCELLED',
                cancelledAt = NOW(),
                updatedAt = NOW()
            WHERE seq = #{seq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND memberSeq = #{memberSeq}
              AND applicationStatus = 'SUBMITTED'
              AND paymentStatus IN ('UNPAID', 'FAILED')
            """)
    int cancelByMember(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("seq") Long seq,
            @Param("memberSeq") Long memberSeq
    );

    @Update("""
            UPDATE pre_registrations
            SET paymentStatus = #{paymentStatus},
                paidAmount = CASE WHEN #{paymentStatus} = 'PAID'
                    THEN COALESCE(paidAmount, totalAmount) ELSE paidAmount END,
                paidAt = CASE WHEN #{paymentStatus} = 'PAID'
                    THEN COALESCE(paidAt, NOW()) ELSE paidAt END,
                updatedAt = NOW()
            WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND paymentStatus = #{previousStatus}
            """)
    int updatePaymentStatus(@Param("conferenceSeq") Long conferenceSeq,
                            @Param("seq") Long seq,
                            @Param("previousStatus") String previousStatus,
                            @Param("paymentStatus") String paymentStatus);

    @Select("""
            SELECT p.seq,
                   p.registrationNumber,
                   p.categoryName,
                   p.periodType,
                   p.currency,
                   COALESCE(p.totalAmount, p.feeAmount) AS feeAmount,
                   p.applicationStatus,
                   p.paymentStatus,
                   p.paidAmount,
                   p.createdAt
            FROM pre_registrations p
            WHERE p.memberSeq = #{memberSeq}
              AND p.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            ORDER BY p.createdAt DESC, p.seq DESC
            """)
    List<MemberPreRegistrationResponse> findByMemberSeq(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("memberSeq") Long memberSeq
    );

    @Select("""
            <script>
            """ + SELECT_COLUMNS + FILTER_SCRIPT + """
            ORDER BY p.createdAt DESC, p.seq DESC
            </script>
            """)
    List<PreRegistrationResponse> findAllForExport(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("keyword") String keyword,
            @Param("categorySeq") Long categorySeq,
            @Param("optionSeq") Long optionSeq,
            @Param("periodType") String periodType,
            @Param("applicationStatus") String applicationStatus,
            @Param("paymentStatus") String paymentStatus,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("dbEncString") String dbEncString
    );

    @Select("""
            SELECT seq AS categorySeq, categoryCode, categoryName
            FROM registration_categories
            WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND isDelete = 'N'
            ORDER BY sortOrder ASC, seq ASC
            """)
    List<PreRegistrationCategoryOptionResponse> findCategoryOptions(@Param("conferenceSeq") Long conferenceSeq);

    @Select("""
            SELECT o.seq AS optionSeq, o.optionName, c.eventName
            FROM registration_options o
            LEFT JOIN conference_settings c ON c.seq = o.conferenceSeq
            WHERE o.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            ORDER BY c.eventName, o.sortOrder, o.seq
            """)
    List<PreRegistrationOptionData.FilterOption> findFilterOptions(@Param("conferenceSeq") Long conferenceSeq);
}
