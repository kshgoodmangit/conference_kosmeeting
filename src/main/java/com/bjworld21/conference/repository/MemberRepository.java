package com.bjworld21.conference.repository;

import com.bjworld21.conference.entity.Member;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface MemberRepository {

    @Select("SELECT password FROM members WHERE conferenceSeq = #{conferenceSeq} AND seq = #{memberSeq}")
    String findCredential(@Param("conferenceSeq") long conferenceSeq, @Param("memberSeq") long memberSeq);

    String SELECT_COLUMNS = """
            SELECT m.seq, m.memberType,
            """ + MemberPersonalDataSql.EMAIL + """
            AS email,
            m.password,
            """ + MemberPersonalDataSql.FIRST_NAME + """
            AS firstName,
            """ + MemberPersonalDataSql.LAST_NAME + """
            AS lastName,
            m.institution, m.department, m.positionTitle, m.country,
            """ + MemberPersonalDataSql.MOBILE + """
            AS mobile,
            m.newsletter, m.createdAt, m.updatedAt
            FROM members m
            """;

    String FILTER_SCRIPT = """
            <where>
                AND m.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
                <if test='keyword != null and keyword != ""'>
                    AND (
                        LOWER(
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
                        OR LOWER(
            """ + MemberPersonalDataSql.EMAIL + """
                        ) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                        OR LOWER(m.memberType) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                        OR LOWER(m.institution) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                        OR LOWER(COALESCE(m.department, '')) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                        OR LOWER(COALESCE(m.positionTitle, '')) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                        OR LOWER(COALESCE(m.country, '')) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                        OR LOWER(
            """ + MemberPersonalDataSql.MOBILE + """
                        ) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                    )
                </if>
                <if test='memberType != null and memberType != ""'>
                    AND m.memberType = #{memberType}
                </if>
                <if test='hasPreRegistration != null'>
                    <choose>
                        <when test='hasPreRegistration'>
                            AND EXISTS (SELECT 1 FROM pre_registrations p WHERE p.memberSeq = m.seq AND p.conferenceSeq = m.conferenceSeq)
                        </when>
                        <otherwise>
                            AND NOT EXISTS (SELECT 1 FROM pre_registrations p WHERE p.memberSeq = m.seq AND p.conferenceSeq = m.conferenceSeq)
                        </otherwise>
                    </choose>
                </if>
                <if test='hasAbstractSubmission != null'>
                    <choose>
                        <when test='hasAbstractSubmission'>
                            AND EXISTS (SELECT 1 FROM abstract_submissions a WHERE a.memberSeq = m.seq AND a.conferenceSeq = m.conferenceSeq)
                        </when>
                        <otherwise>
                            AND NOT EXISTS (SELECT 1 FROM abstract_submissions a WHERE a.memberSeq = m.seq AND a.conferenceSeq = m.conferenceSeq)
                        </otherwise>
                    </choose>
                </if>
            </where>
            """;

    @Insert("""
            INSERT INTO members (
                conferenceSeq, memberType, email, password, firstName, lastName, institution, department,
                positionTitle, country, mobile, newsletter, createdAt, updatedAt
            ) VALUES (
                #{conferenceSeq,javaType=java.lang.Long}, #{member.memberType}, HEX(AES_ENCRYPT(#{member.email}, SHA2(#{dbEncString}, 512))), #{member.password},
                HEX(AES_ENCRYPT(#{member.firstName}, SHA2(#{dbEncString}, 512))),
                HEX(AES_ENCRYPT(#{member.lastName}, SHA2(#{dbEncString}, 512))),
                #{member.institution}, #{member.department}, #{member.positionTitle}, #{member.country},
                HEX(AES_ENCRYPT(#{member.mobile}, SHA2(#{dbEncString}, 512))),
                #{member.newsletter}, NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "member.seq")
    void insert(@Param("conferenceSeq") Long conferenceSeq,
                @Param("member") Member member,
                @Param("dbEncString") String dbEncString);

    @Select(SELECT_COLUMNS + """
            WHERE m.email = HEX(AES_ENCRYPT(#{email}, SHA2(#{dbEncString}, 512)))
              AND m.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            LIMIT 1
            """)
    Member findByEmail(@Param("conferenceSeq") Long conferenceSeq,
                       @Param("email") String email,
                       @Param("dbEncString") String dbEncString);

    @Select(SELECT_COLUMNS + """
            WHERE m.email = HEX(AES_ENCRYPT(#{email}, SHA2(#{dbEncString}, 512)))
              AND m.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND m.seq <> #{seq}
            LIMIT 1
            """)
    Member findByEmailExceptSeq(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("email") String email,
            @Param("seq") Long seq,
            @Param("dbEncString") String dbEncString
    );

    @Select(SELECT_COLUMNS + " WHERE m.seq = #{seq} AND m.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    Member findBySeq(@Param("conferenceSeq") Long conferenceSeq,
                     @Param("seq") Long seq,
                     @Param("dbEncString") String dbEncString);

    @Select("SELECT COUNT(*) FROM members WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    long count(@Param("conferenceSeq") Long conferenceSeq);

    @Select("""
            <script>
            """ + SELECT_COLUMNS + FILTER_SCRIPT + """
            ORDER BY m.createdAt DESC, m.seq DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Member> findPage(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("keyword") String keyword,
            @Param("memberType") String memberType,
            @Param("hasPreRegistration") Boolean hasPreRegistration,
            @Param("hasAbstractSubmission") Boolean hasAbstractSubmission,
            @Param("size") int size,
            @Param("offset") int offset,
            @Param("dbEncString") String dbEncString
    );

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM members m
            """ + FILTER_SCRIPT + """
            </script>
            """)
    long countByFilters(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("keyword") String keyword,
            @Param("memberType") String memberType,
            @Param("hasPreRegistration") Boolean hasPreRegistration,
            @Param("hasAbstractSubmission") Boolean hasAbstractSubmission,
            @Param("dbEncString") String dbEncString
    );

    @Select("""
            <script>
            """ + SELECT_COLUMNS + FILTER_SCRIPT + """
            ORDER BY m.createdAt DESC, m.seq DESC
            </script>
            """)
    List<Member> findAllForExport(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("keyword") String keyword,
            @Param("memberType") String memberType,
            @Param("hasPreRegistration") Boolean hasPreRegistration,
            @Param("hasAbstractSubmission") Boolean hasAbstractSubmission,
            @Param("dbEncString") String dbEncString
    );

    @Update("""
            UPDATE members
            SET memberType = #{member.memberType},
                email = HEX(AES_ENCRYPT(#{member.email}, SHA2(#{dbEncString}, 512))),
                firstName = HEX(AES_ENCRYPT(#{member.firstName}, SHA2(#{dbEncString}, 512))),
                lastName = HEX(AES_ENCRYPT(#{member.lastName}, SHA2(#{dbEncString}, 512))),
                institution = #{member.institution},
                department = #{member.department},
                positionTitle = #{member.positionTitle},
                country = #{member.country},
                mobile = HEX(AES_ENCRYPT(#{member.mobile}, SHA2(#{dbEncString}, 512))),
                newsletter = #{member.newsletter},
                updatedAt = NOW()
            WHERE seq = #{member.seq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    void update(@Param("conferenceSeq") Long conferenceSeq,
                @Param("member") Member member,
                @Param("dbEncString") String dbEncString);

    @Update("""
            UPDATE members
            SET password = #{member.password},
                updatedAt = NOW()
            WHERE seq = #{member.seq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    void updatePassword(@Param("conferenceSeq") Long conferenceSeq, @Param("member") Member member);

    @Delete("DELETE FROM members WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    void delete(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);
}
