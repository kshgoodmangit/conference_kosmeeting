package com.bjworld21.congress.repository;

import com.bjworld21.congress.dto.BoardCategoryResponse;
import com.bjworld21.congress.entity.BoardAttachment;
import com.bjworld21.congress.entity.BoardPost;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface BoardPostRepository {

    @Select("""
            SELECT COUNT(*)
            FROM board_posts post
            WHERE post.boardSeq = #{boardSeq}
              AND post.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND (#{status} = '' OR post.status = #{status})
              AND (
                  #{keyword} = ''
                  OR LOWER(post.title) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                  OR LOWER(post.content) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              )
            """)
    long countPage(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("boardSeq") Long boardSeq,
            @Param("status") String status,
            @Param("keyword") String keyword
    );

    @Select("""
            SELECT post.*,
                   category.codeName AS categoryName,
                   category.codeEtc1 AS categoryCode,
                   CONVERT(AES_DECRYPT(UNHEX(creator.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS createdByName,
                   CONVERT(AES_DECRYPT(UNHEX(updater.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS updatedByName,
                   (SELECT COUNT(*)
                    FROM board_attachments attachment
                    WHERE attachment.boardPostSeq = post.seq) AS attachmentCount
            FROM board_posts post
            LEFT JOIN common_codes category
              ON category.seq = post.categorySeq
             AND category.isDelete = 'N'
            LEFT JOIN admin_accounts creator ON creator.seq = post.createdBy
            LEFT JOIN admin_accounts updater ON updater.seq = post.updatedBy
            WHERE post.boardSeq = #{boardSeq}
              AND post.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND (#{status} = '' OR post.status = #{status})
              AND (
                  #{keyword} = ''
                  OR LOWER(post.title) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                  OR LOWER(post.content) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              )
            ORDER BY
                CASE WHEN post.boardSeq = 1 THEN post.isPinned ELSE FALSE END DESC,
                CASE WHEN post.boardSeq = 3 THEN post.sortOrder ELSE NULL END ASC,
                post.createdAt DESC,
                post.seq DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<BoardPost> findPage(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("boardSeq") Long boardSeq,
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("size") int size,
            @Param("offset") int offset,
            @Param("dbEncString") String dbEncString
    );

    @Select("""
            SELECT post.*,
                   category.codeName AS categoryName,
                   category.codeEtc1 AS categoryCode,
                   CONVERT(AES_DECRYPT(UNHEX(creator.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS createdByName,
                   CONVERT(AES_DECRYPT(UNHEX(updater.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS updatedByName,
                   (SELECT COUNT(*)
                    FROM board_attachments attachment
                    WHERE attachment.boardPostSeq = post.seq) AS attachmentCount
            FROM board_posts post
            LEFT JOIN common_codes category
              ON category.seq = post.categorySeq
             AND category.isDelete = 'N'
            LEFT JOIN admin_accounts creator ON creator.seq = post.createdBy
            LEFT JOIN admin_accounts updater ON updater.seq = post.updatedBy
            WHERE post.boardSeq = #{boardSeq}
              AND post.seq = #{seq}
              AND post.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    BoardPost findBySeq(@Param("conferenceSeq") Long conferenceSeq,
                        @Param("boardSeq") Long boardSeq, @Param("seq") Long seq,
                        @Param("dbEncString") String dbEncString);

    @Select("""
            SELECT post.*,
                   CONVERT(AES_DECRYPT(UNHEX(creator.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS createdByName,
                   (SELECT COUNT(*)
                    FROM board_attachments attachment
                    WHERE attachment.boardPostSeq = post.seq) AS attachmentCount
            FROM board_posts post
            LEFT JOIN admin_accounts creator ON creator.seq = post.createdBy
            WHERE post.boardSeq = 1
              AND post.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND post.status = 'PUBLISHED'
              AND post.publishedAt <= NOW()
              AND (post.publishEndAt IS NULL OR post.publishEndAt > NOW())
              AND post.seq = #{seq}
            """)
    BoardPost findPublishedNoticeBySeq(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("seq") Long seq,
            @Param("dbEncString") String dbEncString
    );

    @Update("""
            UPDATE board_posts
            SET viewCount = COALESCE(viewCount, 0) + 1
            WHERE boardSeq = 1
              AND seq = #{seq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND status = 'PUBLISHED'
              AND publishedAt <= NOW()
              AND (publishEndAt IS NULL OR publishEndAt > NOW())
            """)
    int incrementPublishedNoticeViewCount(@Param("conferenceSeq") Long conferenceSeq,
                                          @Param("seq") Long seq);

    @Select("""
            SELECT post.*,
                   CONVERT(AES_DECRYPT(UNHEX(creator.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS createdByName,
                   (SELECT COUNT(*)
                    FROM board_attachments attachment
                    WHERE attachment.boardPostSeq = post.seq) AS attachmentCount
            FROM board_posts post
            LEFT JOIN admin_accounts creator ON creator.seq = post.createdBy
            WHERE post.boardSeq = 1
              AND post.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND post.status = 'PUBLISHED'
              AND post.publishedAt <= NOW()
              AND (post.publishEndAt IS NULL OR post.publishEndAt > NOW())
            ORDER BY
                CASE WHEN #{pinnedFirst} = TRUE THEN post.isPinned ELSE FALSE END DESC,
                post.createdAt DESC,
                post.seq DESC
            LIMIT #{size}
            """)
    List<BoardPost> findPublishedNotices(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("size") int size,
            @Param("pinnedFirst") boolean pinnedFirst,
            @Param("dbEncString") String dbEncString
    );

    @Select("""
            SELECT COUNT(*)
            FROM board_posts post
            WHERE post.boardSeq = 1
              AND post.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND post.status = 'PUBLISHED'
              AND post.publishedAt <= NOW()
              AND (post.publishEndAt IS NULL OR post.publishEndAt > NOW())
            """)
    long countPublishedNotices(@Param("conferenceSeq") Long conferenceSeq);

    @Select("""
            SELECT post.*,
                   CONVERT(AES_DECRYPT(UNHEX(creator.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS createdByName,
                   (SELECT COUNT(*)
                    FROM board_attachments attachment
                    WHERE attachment.boardPostSeq = post.seq) AS attachmentCount
            FROM board_posts post
            LEFT JOIN admin_accounts creator ON creator.seq = post.createdBy
            WHERE post.boardSeq = 1
              AND post.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND post.status = 'PUBLISHED'
              AND post.publishedAt <= NOW()
              AND (post.publishEndAt IS NULL OR post.publishEndAt > NOW())
            ORDER BY
                post.isPinned DESC,
                post.createdAt DESC,
                post.seq DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<BoardPost> findPublishedNoticePage(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("size") int size,
            @Param("offset") int offset,
            @Param("dbEncString") String dbEncString
    );

    @Select("""
            SELECT COUNT(*)
            FROM board_posts post
            LEFT JOIN common_codes category
              ON category.seq = post.categorySeq
             AND category.isDelete = 'N'
            WHERE post.boardSeq = 3
              AND post.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND post.status = 'PUBLISHED'
              AND post.publishedAt <= NOW()
              AND (post.publishEndAt IS NULL OR post.publishEndAt > NOW())
              AND (#{categoryCode} = '' OR category.codeEtc1 = #{categoryCode})
            """)
    long countPublishedFaqs(@Param("conferenceSeq") Long conferenceSeq,
                            @Param("categoryCode") String categoryCode);

    @Select("""
            SELECT post.*,
                   category.codeName AS categoryName,
                   category.codeEtc1 AS categoryCode
            FROM board_posts post
            LEFT JOIN common_codes category
              ON category.seq = post.categorySeq
             AND category.isDelete = 'N'
            WHERE post.boardSeq = 3
              AND post.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND post.status = 'PUBLISHED'
              AND post.publishedAt <= NOW()
              AND (post.publishEndAt IS NULL OR post.publishEndAt > NOW())
              AND (#{categoryCode} = '' OR category.codeEtc1 = #{categoryCode})
            ORDER BY post.sortOrder ASC, post.createdAt DESC, post.seq DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<BoardPost> findPublishedFaqPage(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("categoryCode") String categoryCode,
            @Param("size") int size,
            @Param("offset") int offset
    );

    @Insert("""
            INSERT INTO board_posts (
                conferenceSeq, boardSeq, categorySeq, title, content, status, isPinned,
                sortOrder, viewCount, publishedAt, publishEndAt,
                createdBy, updatedBy, createdAt, updatedAt
            ) VALUES (
                #{conferenceSeq,javaType=java.lang.Long}, #{boardSeq}, #{categorySeq}, #{title}, #{content}, #{status}, #{isPinned},
                #{sortOrder}, 0, #{publishedAt}, #{publishEndAt},
                #{createdBy}, #{updatedBy}, NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insert(BoardPost post);

    @Update("""
            UPDATE board_posts
            SET categorySeq = #{categorySeq},
                title = #{title},
                content = #{content},
                status = #{status},
                isPinned = #{isPinned},
                sortOrder = #{sortOrder},
                publishedAt = #{publishedAt},
                publishEndAt = #{publishEndAt},
                updatedBy = #{updatedBy},
                updatedAt = NOW()
            WHERE seq = #{seq}
              AND boardSeq = #{boardSeq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    int update(BoardPost post);

    @Delete("DELETE FROM board_posts WHERE seq = #{seq} AND boardSeq = #{boardSeq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    int delete(@Param("conferenceSeq") Long conferenceSeq,
               @Param("boardSeq") Long boardSeq, @Param("seq") Long seq);

    @Select("""
            SELECT seq,
                   codeEtc1 AS categoryCode,
                   codeName AS categoryName,
                   sortOrder
            FROM common_codes
            WHERE groupCode = 'FAQ_CATEGORY'
              AND parentSeq <> 0
              AND isUsed = 'Y'
              AND isDelete = 'N'
            ORDER BY sortOrder, seq
            """)
    List<BoardCategoryResponse> findFaqCategories();

    @Select("""
            SELECT COUNT(*)
            FROM common_codes
            WHERE seq = #{categorySeq}
              AND groupCode = 'FAQ_CATEGORY'
              AND parentSeq <> 0
              AND isUsed = 'Y'
              AND isDelete = 'N'
            """)
    int countActiveFaqCategory(@Param("categorySeq") Long categorySeq);

    @Select("""
            SELECT *
            FROM board_attachments
            WHERE boardPostSeq = #{boardPostSeq}
            ORDER BY sortOrder, seq
            """)
    List<BoardAttachment> findAttachments(@Param("boardPostSeq") Long boardPostSeq);

    @Select("SELECT * FROM board_attachments WHERE seq = #{seq}")
    BoardAttachment findAttachment(@Param("seq") Long seq);

    @Select("SELECT COUNT(*) FROM board_attachments WHERE boardPostSeq = #{boardPostSeq}")
    int countAttachments(@Param("boardPostSeq") Long boardPostSeq);

    @Select("SELECT COALESCE(SUM(fileSize), 0) FROM board_attachments WHERE boardPostSeq = #{boardPostSeq}")
    long sumAttachmentBytes(@Param("boardPostSeq") Long boardPostSeq);

    @Select("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM board_attachments WHERE boardPostSeq = #{boardPostSeq}")
    int nextAttachmentSortOrder(@Param("boardPostSeq") Long boardPostSeq);

    @Insert("""
            INSERT INTO board_attachments (
                boardPostSeq, originalFilename, savedFilename, contentType,
                fileSize, sortOrder, downloadCount, createdBy, createdAt
            ) VALUES (
                #{boardPostSeq}, #{originalFilename}, #{savedFilename}, #{contentType},
                #{fileSize}, #{sortOrder}, 0, #{createdBy}, NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insertAttachment(BoardAttachment attachment);

    @Update("UPDATE board_attachments SET downloadCount = downloadCount + 1 WHERE seq = #{seq}")
    void incrementDownloadCount(@Param("seq") Long seq);

    @Delete("DELETE FROM board_attachments WHERE seq = #{seq} AND boardPostSeq = #{boardPostSeq}")
    int deleteAttachment(@Param("boardPostSeq") Long boardPostSeq, @Param("seq") Long seq);
}
