package com.bjworld21.conference.repository;

import com.bjworld21.conference.entity.MaintenanceRequest;
import com.bjworld21.conference.entity.MaintenanceRequestAttachment;
import com.bjworld21.conference.entity.MaintenanceRequestNotification;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface MaintenanceRequestRepository {
    String SELECT_BASE = """
            SELECT r.seq, r.conferenceSeq, r.title, r.content, r.status,
                   r.requestedByAdminSeq,
                   CONVERT(AES_DECRYPT(UNHEX(requester.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS requestedByName,
                   r.assignedToAdminSeq,
                   CONVERT(AES_DECRYPT(UNHEX(assignee.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS assignedToName,
                   r.answerContent, r.answeredByAdminSeq,
                   CONVERT(AES_DECRYPT(UNHEX(answerer.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS answeredByName,
                   r.answeredAt, r.isDelete, r.createdAt, r.updatedAt,
                   (SELECT COUNT(*) FROM maintenance_request_attachments a
                    WHERE a.maintenanceRequestSeq = r.seq) AS attachmentCount
            FROM maintenance_requests r
            JOIN admin_accounts requester ON requester.seq = r.requestedByAdminSeq
            LEFT JOIN admin_accounts assignee ON assignee.seq = r.assignedToAdminSeq
            LEFT JOIN admin_accounts answerer ON answerer.seq = r.answeredByAdminSeq
            """;

    @Insert("""
            INSERT INTO maintenance_requests (
                conferenceSeq, title, content, status, requestedByAdminSeq,
                isDelete, createdAt, updatedAt
            ) VALUES (
                #{request.conferenceSeq}, #{request.title}, #{request.content}, 'REQUESTED',
                #{request.requestedByAdminSeq}, 'N', NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "request.seq")
    void insert(@Param("request") MaintenanceRequest request);

    @Update("""
            UPDATE maintenance_requests
            SET title = #{title}, content = #{content}, updatedAt = NOW()
            WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq} AND isDelete = 'N'
            """)
    int updateRequest(@Param("conferenceSeq") Long conferenceSeq,
                      @Param("seq") Long seq,
                      @Param("title") String title,
                      @Param("content") String content);

    @Select("""
            SELECT COUNT(*)
            FROM maintenance_requests r
            WHERE r.conferenceSeq = #{conferenceSeq} AND r.isDelete = 'N'
              AND (#{status} IS NULL OR r.status = #{status})
              AND (#{keyword} IS NULL
                   OR LOWER(r.title) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(r.content) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            """)
    long countPage(@Param("conferenceSeq") Long conferenceSeq,
                   @Param("keyword") String keyword,
                   @Param("status") String status);

    @Select(SELECT_BASE + """
            WHERE r.conferenceSeq = #{conferenceSeq} AND r.isDelete = 'N'
              AND (#{status} IS NULL OR r.status = #{status})
              AND (#{keyword} IS NULL
                   OR LOWER(r.title) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(r.content) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            ORDER BY r.seq DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<MaintenanceRequest> findPage(@Param("conferenceSeq") Long conferenceSeq,
                                      @Param("keyword") String keyword,
                                      @Param("status") String status,
                                      @Param("size") int size,
                                      @Param("offset") int offset,
                                      @Param("dbEncString") String dbEncString);

    @Select(SELECT_BASE + " WHERE r.seq = #{seq} AND r.conferenceSeq = #{conferenceSeq} AND r.isDelete = 'N'")
    MaintenanceRequest findBySeq(@Param("conferenceSeq") Long conferenceSeq,
                                 @Param("seq") Long seq,
                                 @Param("dbEncString") String dbEncString);

    @Select("""
            SELECT COUNT(*) FROM maintenance_requests
            WHERE conferenceSeq = #{conferenceSeq} AND isDelete = 'N' AND status = #{status}
              AND (#{keyword} IS NULL
                   OR LOWER(title) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(content) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            """)
    long countByStatus(@Param("conferenceSeq") Long conferenceSeq,
                       @Param("status") String status,
                       @Param("keyword") String keyword);

    @Update("""
            UPDATE maintenance_requests
            SET status = #{status}, answerContent = #{answerContent},
                assignedToAdminSeq = #{adminSeq}, answeredByAdminSeq = #{adminSeq},
                answeredAt = NOW(), updatedAt = NOW()
            WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq} AND isDelete = 'N'
            """)
    int updateAnswer(@Param("conferenceSeq") Long conferenceSeq,
                     @Param("seq") Long seq,
                     @Param("status") String status,
                     @Param("answerContent") String answerContent,
                     @Param("adminSeq") Long adminSeq);

    @Update("""
            UPDATE maintenance_requests SET isDelete = 'Y', updatedAt = NOW()
            WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq} AND isDelete = 'N'
            """)
    int softDelete(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);

    @Insert("""
            INSERT INTO maintenance_request_attachments (
                maintenanceRequestSeq, attachmentType, originalFilename, savedFilename,
                contentType, fileExtension, fileSize, sortOrder, uploadedByAdminSeq, createdAt
            ) VALUES (
                #{attachment.maintenanceRequestSeq}, #{attachment.attachmentType},
                #{attachment.originalFilename}, #{attachment.savedFilename}, #{attachment.contentType},
                #{attachment.fileExtension}, #{attachment.fileSize}, #{attachment.sortOrder},
                #{attachment.uploadedByAdminSeq}, NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "attachment.seq")
    void insertAttachment(@Param("attachment") MaintenanceRequestAttachment attachment);

    @Select("""
            SELECT seq, maintenanceRequestSeq, attachmentType, originalFilename, savedFilename,
                   contentType, fileExtension, fileSize, sortOrder, uploadedByAdminSeq, createdAt
            FROM maintenance_request_attachments
            WHERE maintenanceRequestSeq = #{requestSeq}
            ORDER BY attachmentType, sortOrder, seq
            """)
    List<MaintenanceRequestAttachment> findAttachments(@Param("requestSeq") Long requestSeq);

    @Select("""
            SELECT a.seq, a.maintenanceRequestSeq, a.attachmentType, a.originalFilename, a.savedFilename,
                   a.contentType, a.fileExtension, a.fileSize, a.sortOrder, a.uploadedByAdminSeq, a.createdAt
            FROM maintenance_request_attachments a
            JOIN maintenance_requests r ON r.seq = a.maintenanceRequestSeq
            WHERE a.seq = #{attachmentSeq} AND r.conferenceSeq = #{conferenceSeq} AND r.isDelete = 'N'
            """)
    MaintenanceRequestAttachment findAttachment(@Param("conferenceSeq") Long conferenceSeq,
                                                 @Param("attachmentSeq") Long attachmentSeq);

    @Insert("""
            INSERT INTO maintenance_request_notifications (
                maintenanceRequestSeq, recipientAdminSeq, notificationEvent, channel,
                sendStatus, attemptCount, createdAt, updatedAt
            ) VALUES (#{notification.maintenanceRequestSeq}, #{notification.recipientAdminSeq}, 'REQUEST_CREATED', 'EMAIL', 'READY', 0, NOW(), NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "notification.seq")
    void insertNotification(@Param("notification") MaintenanceRequestNotification notification);

    @Update("""
            UPDATE maintenance_request_notifications
            SET sendStatus = 'SENT', attemptCount = attemptCount + 1, sentAt = NOW(),
                failureReason = NULL, updatedAt = NOW()
            WHERE seq = #{seq}
            """)
    void markNotificationSent(@Param("seq") Long seq);

    @Update("""
            UPDATE maintenance_request_notifications
            SET sendStatus = 'FAILED', attemptCount = attemptCount + 1,
                failureReason = #{reason}, updatedAt = NOW()
            WHERE seq = #{seq}
            """)
    void markNotificationFailed(@Param("seq") Long seq, @Param("reason") String reason);

}
