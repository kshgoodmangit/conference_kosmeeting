package com.bjworld21.congress.repository;

import com.bjworld21.congress.entity.ProgramDay;
import com.bjworld21.congress.entity.ProgramDayRoom;
import com.bjworld21.congress.entity.ProgramItem;
import com.bjworld21.congress.entity.ProgramItemPerson;
import com.bjworld21.congress.entity.ProgramRoom;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ProgramRepository {

    String ITEM_COLUMNS = """
            SELECT p.seq, p.programDaySeq, p.roomSeq, p.parentSeq, p.abstractSubmissionSeq,
                   p.scopeType, p.itemType, p.startTime, p.endTime, p.title, p.subtitle,
                   CONVERT(AES_DECRYPT(UNHEX(p.organizerText), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS organizerText,
                   CONVERT(AES_DECRYPT(UNHEX(p.speakerText), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS speakerText,
                   CONVERT(AES_DECRYPT(UNHEX(p.chairText), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS chairText,
                   p.notes, p.rowStyle, p.sortOrder, p.enabled, p.createdAt, p.updatedAt
            FROM program_items p
            """;

    String PERSON_COLUMNS = """
            SELECT person.seq, person.programItemSeq, person.roleType, person.countrySeq, person.affiliation,
                   CONVERT(AES_DECRYPT(UNHEX(person.personName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS personName,
                   person.sortOrder, person.enabled, person.createdAt, person.updatedAt
            FROM program_item_people person
            """;

    @Select("SELECT * FROM program_days WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} ORDER BY sortOrder ASC, eventDate ASC, seq ASC")
    List<ProgramDay> findAllDays(@Param("conferenceSeq") Long conferenceSeq);

    @Select("SELECT * FROM program_days WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    ProgramDay findDayBySeq(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);

    @Select("SELECT * FROM program_days WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} AND eventDate = #{eventDate} AND (#{excludeSeq} IS NULL OR seq <> #{excludeSeq}) LIMIT 1")
    ProgramDay findDayByDate(@Param("conferenceSeq") Long conferenceSeq,
                             @Param("eventDate") LocalDate eventDate,
                             @Param("excludeSeq") Long excludeSeq);

    @Select("SELECT * FROM program_days WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} AND dayNumber = #{dayNumber} AND (#{excludeSeq} IS NULL OR seq <> #{excludeSeq}) LIMIT 1")
    ProgramDay findDayByNumber(@Param("conferenceSeq") Long conferenceSeq,
                               @Param("dayNumber") Integer dayNumber,
                               @Param("excludeSeq") Long excludeSeq);

    @Insert("""
            INSERT INTO program_days (
                conferenceSeq, eventDate, dayNumber, dayTitle, theme, sortOrder, enabled, createdAt, updatedAt
            ) VALUES (
                #{conferenceSeq,javaType=java.lang.Long}, #{eventDate}, #{dayNumber}, #{dayTitle}, #{theme}, #{sortOrder}, #{enabled}, NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insertDay(ProgramDay day);

    @Update("""
            UPDATE program_days
            SET eventDate = #{eventDate},
                dayNumber = #{dayNumber},
                dayTitle = #{dayTitle},
                theme = #{theme},
                sortOrder = #{sortOrder},
                enabled = #{enabled},
                updatedAt = NOW()
            WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    int updateDay(ProgramDay day);

    @Delete("DELETE FROM program_days WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    int deleteDay(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);

    @Select("SELECT * FROM program_rooms WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} ORDER BY sortOrder ASC, roomName ASC, seq ASC")
    List<ProgramRoom> findAllRooms(@Param("conferenceSeq") Long conferenceSeq);

    @Select("SELECT * FROM program_rooms WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    ProgramRoom findRoomBySeq(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);

    @Select("SELECT * FROM program_rooms WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} AND roomCode = #{roomCode} AND (#{excludeSeq} IS NULL OR seq <> #{excludeSeq}) LIMIT 1")
    ProgramRoom findRoomByCode(@Param("conferenceSeq") Long conferenceSeq,
                               @Param("roomCode") String roomCode,
                               @Param("excludeSeq") Long excludeSeq);

    @Insert("""
            INSERT INTO program_rooms (
                conferenceSeq, roomCode, roomName, location, sortOrder, enabled, createdAt, updatedAt
            ) VALUES (
                #{conferenceSeq,javaType=java.lang.Long}, #{roomCode}, #{roomName}, #{location}, #{sortOrder}, #{enabled}, NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insertRoom(ProgramRoom room);

    @Update("""
            UPDATE program_rooms
            SET roomCode = #{roomCode},
                roomName = #{roomName},
                location = #{location},
                sortOrder = #{sortOrder},
                enabled = #{enabled},
                updatedAt = NOW()
            WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    int updateRoom(ProgramRoom room);

    @Delete("DELETE FROM program_rooms WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    int deleteRoom(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);

    @Select("SELECT COUNT(*) FROM program_day_rooms WHERE roomSeq = #{roomSeq} AND EXISTS (SELECT 1 FROM program_rooms r WHERE r.seq = roomSeq AND r.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long})")
    long countDayRoomsByRoom(@Param("conferenceSeq") Long conferenceSeq, @Param("roomSeq") Long roomSeq);

    @Select("SELECT COUNT(*) FROM program_items WHERE roomSeq = #{roomSeq} AND EXISTS (SELECT 1 FROM program_rooms r WHERE r.seq = roomSeq AND r.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long})")
    long countItemsByRoom(@Param("conferenceSeq") Long conferenceSeq, @Param("roomSeq") Long roomSeq);

    @Select("SELECT dr.* FROM program_day_rooms dr JOIN program_days d ON d.seq = dr.programDaySeq WHERE d.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} ORDER BY dr.programDaySeq ASC, dr.sortOrder ASC, dr.seq ASC")
    List<ProgramDayRoom> findAllDayRooms(@Param("conferenceSeq") Long conferenceSeq);

    @Select("SELECT dr.* FROM program_day_rooms dr JOIN program_days d ON d.seq = dr.programDaySeq WHERE dr.programDaySeq = #{programDaySeq} AND d.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} ORDER BY dr.sortOrder ASC, dr.seq ASC")
    List<ProgramDayRoom> findDayRoomsByDay(@Param("conferenceSeq") Long conferenceSeq,
                                           @Param("programDaySeq") Long programDaySeq);

    @Select("SELECT COUNT(*) FROM program_day_rooms dr JOIN program_days d ON d.seq = dr.programDaySeq JOIN program_rooms r ON r.seq = dr.roomSeq WHERE dr.programDaySeq = #{programDaySeq} AND dr.roomSeq = #{roomSeq} AND dr.enabled = TRUE AND d.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} AND r.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    long countEnabledDayRoom(@Param("conferenceSeq") Long conferenceSeq,
                             @Param("programDaySeq") Long programDaySeq,
                             @Param("roomSeq") Long roomSeq);

    @Select("SELECT COUNT(*) FROM program_items p JOIN program_days d ON d.seq = p.programDaySeq WHERE p.programDaySeq = #{programDaySeq} AND p.roomSeq = #{roomSeq} AND d.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    long countItemsByDayAndRoom(@Param("conferenceSeq") Long conferenceSeq,
                                @Param("programDaySeq") Long programDaySeq,
                                @Param("roomSeq") Long roomSeq);

    @Delete("DELETE FROM program_day_rooms WHERE programDaySeq = #{programDaySeq} AND EXISTS (SELECT 1 FROM program_days d WHERE d.seq = programDaySeq AND d.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long})")
    void deleteDayRoomsByDay(@Param("conferenceSeq") Long conferenceSeq,
                             @Param("programDaySeq") Long programDaySeq);

    @Insert("""
            INSERT INTO program_day_rooms (
                programDaySeq, roomSeq, tabName, sortOrder, enabled, createdAt, updatedAt
            ) VALUES (
                #{programDaySeq}, #{roomSeq}, #{tabName}, #{sortOrder}, #{enabled}, NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insertDayRoom(ProgramDayRoom dayRoom);

    @Select(ITEM_COLUMNS + " WHERE EXISTS (SELECT 1 FROM program_days d WHERE d.seq = p.programDaySeq AND d.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}) ORDER BY p.programDaySeq ASC, p.startTime ASC, p.sortOrder ASC, p.seq ASC")
    List<ProgramItem> findAllItems(@Param("conferenceSeq") Long conferenceSeq,
                                   @Param("dbEncString") String dbEncString);

    @Select(ITEM_COLUMNS + " WHERE p.seq = #{seq} AND EXISTS (SELECT 1 FROM program_days d WHERE d.seq = p.programDaySeq AND d.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long})")
    ProgramItem findItemBySeq(@Param("conferenceSeq") Long conferenceSeq,
                              @Param("seq") Long seq,
                              @Param("dbEncString") String dbEncString);

    @Select(ITEM_COLUMNS + " WHERE p.seq = #{seq} AND EXISTS (SELECT 1 FROM program_days d WHERE d.seq = p.programDaySeq AND d.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}) FOR UPDATE")
    ProgramItem lockItem(@Param("conferenceSeq") Long conferenceSeq,
                         @Param("seq") Long seq,
                         @Param("dbEncString") String dbEncString);

    @Select("SELECT status FROM abstract_submissions WHERE seq=#{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} FOR UPDATE")
    String lockAbstractStatus(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);

    @Select("SELECT p.seq FROM program_items p JOIN program_days d ON d.seq = p.programDaySeq WHERE p.abstractSubmissionSeq=#{abstractSeq} AND (#{excludeSeq} IS NULL OR p.seq != #{excludeSeq}) AND d.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} FOR UPDATE")
    List<Long> findOtherAbstractAssignments(@Param("conferenceSeq") Long conferenceSeq,
                                            @Param("abstractSeq") Long abstractSeq,
                                            @Param("excludeSeq") Long excludeSeq);

    @Select(PERSON_COLUMNS + " WHERE person.programItemSeq=#{seq} AND person.roleType='SPEAKER' ORDER BY person.sortOrder, person.seq FOR UPDATE")
    List<ProgramItemPerson> lockSpeakers(@Param("seq") Long seq, @Param("dbEncString") String dbEncString);

    @Delete("DELETE FROM program_item_people WHERE programItemSeq=#{seq} AND roleType='SPEAKER'")
    void deleteSpeakers(@Param("seq") Long seq);

    @Update("UPDATE program_items SET abstractSubmissionSeq=#{item.abstractSubmissionSeq}, title=#{item.title}, "
            + "speakerText=HEX(AES_ENCRYPT(#{item.speakerText}, SHA2(#{dbEncString}, 512))), "
            + "updatedAt=NOW() WHERE seq=#{item.seq} AND EXISTS (SELECT 1 FROM program_days d WHERE d.seq = programDaySeq AND d.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long})")
    int updateAbstractAssignment(@Param("conferenceSeq") Long conferenceSeq,
                                 @Param("item") ProgramItem item,
                                 @Param("dbEncString") String dbEncString);

    @Select(ITEM_COLUMNS + " WHERE p.parentSeq = #{parentSeq} AND EXISTS (SELECT 1 FROM program_days d WHERE d.seq = p.programDaySeq AND d.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}) ORDER BY p.startTime ASC, p.sortOrder ASC, p.seq ASC")
    List<ProgramItem> findChildren(@Param("conferenceSeq") Long conferenceSeq,
                                   @Param("parentSeq") Long parentSeq,
                                   @Param("dbEncString") String dbEncString);

    @Select("SELECT COUNT(*) FROM abstract_submissions WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    long countAbstractSubmission(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);

    @Insert("""
            INSERT INTO program_items (
                programDaySeq, roomSeq, parentSeq, abstractSubmissionSeq,
                scopeType, itemType, startTime, endTime, title, subtitle,
                organizerText, speakerText, chairText, notes, rowStyle,
                sortOrder, enabled, createdAt, updatedAt
            ) VALUES (
                #{item.programDaySeq}, #{item.roomSeq}, #{item.parentSeq}, #{item.abstractSubmissionSeq},
                #{item.scopeType}, #{item.itemType}, #{item.startTime}, #{item.endTime}, #{item.title}, #{item.subtitle},
                HEX(AES_ENCRYPT(#{item.organizerText}, SHA2(#{dbEncString}, 512))),
                HEX(AES_ENCRYPT(#{item.speakerText}, SHA2(#{dbEncString}, 512))),
                HEX(AES_ENCRYPT(#{item.chairText}, SHA2(#{dbEncString}, 512))),
                #{item.notes}, #{item.rowStyle}, #{item.sortOrder}, #{item.enabled}, NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "item.seq")
    void insertItem(@Param("item") ProgramItem item, @Param("dbEncString") String dbEncString);

    @Update("""
            UPDATE program_items
            SET programDaySeq = #{item.programDaySeq}, roomSeq = #{item.roomSeq}, parentSeq = #{item.parentSeq},
                abstractSubmissionSeq = #{item.abstractSubmissionSeq}, scopeType = #{item.scopeType},
                itemType = #{item.itemType}, startTime = #{item.startTime}, endTime = #{item.endTime},
                title = #{item.title}, subtitle = #{item.subtitle},
                organizerText = HEX(AES_ENCRYPT(#{item.organizerText}, SHA2(#{dbEncString}, 512))),
                speakerText = HEX(AES_ENCRYPT(#{item.speakerText}, SHA2(#{dbEncString}, 512))),
                chairText = HEX(AES_ENCRYPT(#{item.chairText}, SHA2(#{dbEncString}, 512))),
                notes = #{item.notes}, rowStyle = #{item.rowStyle}, sortOrder = #{item.sortOrder},
                enabled = #{item.enabled},
                updatedAt = NOW()
            WHERE seq = #{item.seq}
              AND EXISTS (SELECT 1 FROM program_days d WHERE d.seq = programDaySeq AND d.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long})
            """)
    int updateItem(@Param("conferenceSeq") Long conferenceSeq,
                   @Param("item") ProgramItem item,
                   @Param("dbEncString") String dbEncString);

    @Delete("DELETE FROM program_items WHERE seq = #{seq} AND EXISTS (SELECT 1 FROM program_days d WHERE d.seq = programDaySeq AND d.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long})")
    int deleteItem(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);

    @Select(PERSON_COLUMNS + " WHERE EXISTS (SELECT 1 FROM program_items p JOIN program_days d ON d.seq = p.programDaySeq WHERE p.seq = person.programItemSeq AND d.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}) ORDER BY person.programItemSeq ASC, person.roleType ASC, person.sortOrder ASC, person.seq ASC")
    List<ProgramItemPerson> findAllPeople(@Param("conferenceSeq") Long conferenceSeq,
                                          @Param("dbEncString") String dbEncString);

    @Delete("DELETE FROM program_item_people WHERE programItemSeq = #{programItemSeq}")
    void deletePeopleByItem(@Param("programItemSeq") Long programItemSeq);

    @Insert("""
            INSERT INTO program_item_people (
                programItemSeq, roleType, countrySeq, affiliation,
                personName, sortOrder, enabled, createdAt, updatedAt
            ) VALUES (
                #{person.programItemSeq}, #{person.roleType}, #{person.countrySeq}, #{person.affiliation},
                HEX(AES_ENCRYPT(#{person.personName}, SHA2(#{dbEncString}, 512))),
                #{person.sortOrder}, #{person.enabled}, NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "person.seq")
    void insertPerson(@Param("person") ProgramItemPerson person, @Param("dbEncString") String dbEncString);
}
