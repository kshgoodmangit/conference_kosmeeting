package com.bjworld21.conference.repository;

import com.bjworld21.conference.dto.AbstractProgramCandidate;
import com.bjworld21.conference.dto.AbstractSubmissionResponse;
import com.bjworld21.conference.entity.ProgramAbstractSnapshot;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AbstractProgramRepository {
    String FILTER = """
            FROM abstract_submissions s
            WHERE s.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND s.status = 'approved'
              AND (#{presentationTypeCode} IS NULL OR s.acceptedPresentationTypeCode = #{presentationTypeCode})
              AND (#{categoryCode} IS NULL OR s.categoryCode = #{categoryCode})
              AND (#{assignmentStatus} = 'all'
                OR (#{assignmentStatus} = 'free' AND NOT EXISTS (SELECT 1 FROM program_items p WHERE p.abstractSubmissionSeq=s.seq))
                OR (#{assignmentStatus} = 'assigned' AND EXISTS (SELECT 1 FROM program_items p WHERE p.abstractSubmissionSeq=s.seq)))
              AND (#{keyword} = '' OR s.submissionNo LIKE CONCAT('%', #{keyword}, '%')
                OR s.title LIKE CONCAT('%', #{keyword}, '%')
                OR EXISTS (SELECT 1 FROM abstract_submission_authors a
                    LEFT JOIN abstract_submission_institutions i ON i.abstractSeq=a.abstractSeq AND i.institutionNo=a.institutionNo
                    WHERE a.abstractSeq=s.seq AND a.isPresentingAuthor=TRUE
                    AND (CONVERT(AES_DECRYPT(UNHEX(a.authorName), SHA2(#{dbEncString}, 512)) USING utf8mb4)
                        LIKE CONCAT('%', #{keyword}, '%') OR i.institutionName LIKE CONCAT('%', #{keyword}, '%'))))
            """;

    @Select("SELECT COUNT(*) " + FILTER)
    long countCandidates(@Param("keyword") String keyword, @Param("presentationTypeCode") Long presentationTypeCode,
                         @Param("categoryCode") Long categoryCode, @Param("assignmentStatus") String assignmentStatus,
                         @Param("dbEncString") String dbEncString,
                         @Param("conferenceSeq") Long conferenceSeq);

    @Select("""
            SELECT s.seq, s.submissionNo, s.title, s.acceptedPresentationTypeCode, s.categoryCode,
              (SELECT codeName FROM common_codes WHERE seq=s.acceptedPresentationTypeCode) AS acceptedPresentationTypeName,
              (SELECT codeName FROM common_codes WHERE seq=s.categoryCode) AS categoryName,
              (SELECT GROUP_CONCAT(CONVERT(AES_DECRYPT(UNHEX(a.authorName), SHA2(#{dbEncString}, 512)) USING utf8mb4)
                     ORDER BY a.authorOrder, a.seq SEPARATOR ', ')
                 FROM abstract_submission_authors a WHERE a.abstractSeq=s.seq AND a.isPresentingAuthor=TRUE) AS presenterName,
              (SELECT GROUP_CONCAT(DISTINCT i.institutionName ORDER BY i.institutionName SEPARATOR ', ')
                 FROM abstract_submission_authors a
                 JOIN abstract_submission_institutions i ON i.abstractSeq=a.abstractSeq AND i.institutionNo=a.institutionNo
                 WHERE a.abstractSeq=s.seq AND a.isPresentingAuthor=TRUE) AS affiliation,
              (SELECT COUNT(*) FROM abstract_submission_authors a WHERE a.abstractSeq=s.seq AND a.isPresentingAuthor=TRUE) AS presenterCount,
              (SELECT COUNT(*) FROM program_items p WHERE p.abstractSubmissionSeq=s.seq) AS assignmentCount
            """ + FILTER + " ORDER BY s.submissionNo, s.seq LIMIT #{size} OFFSET #{offset}")
    List<AbstractProgramCandidate> findCandidates(@Param("keyword") String keyword,
            @Param("presentationTypeCode") Long presentationTypeCode, @Param("categoryCode") Long categoryCode,
            @Param("assignmentStatus") String assignmentStatus, @Param("size") int size, @Param("offset") long offset,
            @Param("dbEncString") String dbEncString, @Param("conferenceSeq") Long conferenceSeq);

    @Select("SELECT seq, submissionNo, status FROM abstract_submissions WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} AND seq IN (SELECT abstractSubmissionSeq FROM program_items WHERE abstractSubmissionSeq IS NOT NULL)")
    List<AbstractSubmissionResponse> findLinkedAbstracts(@Param("conferenceSeq") Long conferenceSeq);

    @Select("""
            SELECT snapshot.seq, snapshot.programItemSeq, snapshot.abstractSubmissionSeq, snapshot.originalTitle,
                   CONVERT(AES_DECRYPT(UNHEX(snapshot.originalSpeakerText), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS originalSpeakerText,
                   CASE
                       WHEN JSON_TYPE(snapshot.originalSpeakersJson) = 'STRING'
                       THEN CONVERT(AES_DECRYPT(
                           UNHEX(JSON_UNQUOTE(snapshot.originalSpeakersJson)),
                           SHA2(#{dbEncString}, 512)
                       ) USING utf8mb4)
                       ELSE CONVERT(snapshot.originalSpeakersJson USING utf8mb4)
                   END AS originalSpeakersJson,
                   snapshot.assignedTitle,
                   CONVERT(AES_DECRYPT(UNHEX(snapshot.assignedSpeakerText), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS assignedSpeakerText,
                   CASE
                       WHEN JSON_TYPE(snapshot.assignedSpeakersJson) = 'STRING'
                       THEN CONVERT(AES_DECRYPT(
                           UNHEX(JSON_UNQUOTE(snapshot.assignedSpeakersJson)),
                           SHA2(#{dbEncString}, 512)
                       ) USING utf8mb4)
                       ELSE CONVERT(snapshot.assignedSpeakersJson USING utf8mb4)
                   END AS assignedSpeakersJson
            FROM program_abstract_snapshots snapshot
            JOIN program_items item ON item.seq = snapshot.programItemSeq
            JOIN program_days day ON day.seq = item.programDaySeq
            WHERE day.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    List<ProgramAbstractSnapshot> findSnapshots(@Param("conferenceSeq") Long conferenceSeq,
                                                @Param("dbEncString") String dbEncString);

    @Select("""
            SELECT snapshot.seq, snapshot.programItemSeq, snapshot.abstractSubmissionSeq, snapshot.originalTitle,
                   CONVERT(AES_DECRYPT(UNHEX(snapshot.originalSpeakerText), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS originalSpeakerText,
                   CASE
                       WHEN JSON_TYPE(snapshot.originalSpeakersJson) = 'STRING'
                       THEN CONVERT(AES_DECRYPT(
                           UNHEX(JSON_UNQUOTE(snapshot.originalSpeakersJson)),
                           SHA2(#{dbEncString}, 512)
                       ) USING utf8mb4)
                       ELSE CONVERT(snapshot.originalSpeakersJson USING utf8mb4)
                   END AS originalSpeakersJson,
                   snapshot.assignedTitle,
                   CONVERT(AES_DECRYPT(UNHEX(snapshot.assignedSpeakerText), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS assignedSpeakerText,
                   CASE
                       WHEN JSON_TYPE(snapshot.assignedSpeakersJson) = 'STRING'
                       THEN CONVERT(AES_DECRYPT(
                           UNHEX(JSON_UNQUOTE(snapshot.assignedSpeakersJson)),
                           SHA2(#{dbEncString}, 512)
                       ) USING utf8mb4)
                       ELSE CONVERT(snapshot.assignedSpeakersJson USING utf8mb4)
                   END AS assignedSpeakersJson
            FROM program_abstract_snapshots snapshot
            JOIN program_items item ON item.seq = snapshot.programItemSeq
            JOIN program_days day ON day.seq = item.programDaySeq
            WHERE snapshot.programItemSeq=#{seq}
              AND day.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            FOR UPDATE
            """)
    ProgramAbstractSnapshot lockSnapshot(@Param("conferenceSeq") Long conferenceSeq,
                                         @Param("seq") Long seq,
                                         @Param("dbEncString") String dbEncString);

    @Insert("""
            INSERT INTO program_abstract_snapshots
              (programItemSeq, abstractSubmissionSeq, originalTitle, originalSpeakerText, originalSpeakersJson,
               assignedTitle, assignedSpeakerText, assignedSpeakersJson)
            VALUES (#{snapshot.programItemSeq}, #{snapshot.abstractSubmissionSeq}, #{snapshot.originalTitle},
                    HEX(AES_ENCRYPT(#{snapshot.originalSpeakerText}, SHA2(#{dbEncString}, 512))),
                    JSON_QUOTE(HEX(AES_ENCRYPT(#{snapshot.originalSpeakersJson}, SHA2(#{dbEncString}, 512)))),
                    #{snapshot.assignedTitle},
                    HEX(AES_ENCRYPT(#{snapshot.assignedSpeakerText}, SHA2(#{dbEncString}, 512))),
                    JSON_QUOTE(HEX(AES_ENCRYPT(#{snapshot.assignedSpeakersJson}, SHA2(#{dbEncString}, 512)))))
            """)
    void insertSnapshot(
            @Param("snapshot") ProgramAbstractSnapshot snapshot,
            @Param("dbEncString") String dbEncString
    );

    @Delete("""
            DELETE FROM program_abstract_snapshots
            WHERE programItemSeq=#{seq}
              AND EXISTS (
                  SELECT 1
                  FROM program_items item
                  JOIN program_days day ON day.seq = item.programDaySeq
                  WHERE item.seq = programItemSeq
                    AND day.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              )
            """)
    void deleteSnapshot(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);
}
