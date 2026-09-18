package com.bjworld21.congress.repository;

import com.bjworld21.congress.entity.ConferenceSettings;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ConferenceSettingsRepository {

    @Select("SELECT * FROM conference_settings ORDER BY seq DESC LIMIT 1")
    ConferenceSettings findLatest();

    @Select("SELECT * FROM conference_settings ORDER BY seq DESC")
    List<ConferenceSettings> findAll();

    @Select("SELECT * FROM conference_settings WHERE seq = #{seq}")
    ConferenceSettings findBySeq(Long seq);

    @Select("SELECT * FROM conference_settings WHERE sitePath = #{sitePath}")
    ConferenceSettings findBySitePath(String sitePath);

    @Select("SELECT languageCode FROM conference_languages WHERE conferenceSeq = #{conferenceSeq} ORDER BY sortOrder, seq")
    List<String> findLanguages(Long conferenceSeq);

    @Select("SELECT COUNT(*) FROM menu_settings WHERE conferenceSeq = #{conferenceSeq} AND menuScope = 'user' AND menuType <> 'link' AND routePath = CONCAT('/', #{language})")
    long countLanguageRouteConflicts(@Param("conferenceSeq") Long conferenceSeq, @Param("language") String language);

    @org.apache.ibatis.annotations.Delete("DELETE FROM conference_languages WHERE conferenceSeq = #{conferenceSeq}")
    void deleteLanguages(Long conferenceSeq);

    @Insert("INSERT INTO conference_languages (conferenceSeq, languageCode, sortOrder) VALUES (#{conferenceSeq}, #{language}, #{sortOrder})")
    void insertLanguage(@Param("conferenceSeq") Long conferenceSeq, @Param("language") String language, @Param("sortOrder") int sortOrder);

    @Update("UPDATE conference_settings SET sitePath = #{sitePath}, defaultLanguage = #{defaultLanguage}, updatedAt = NOW() WHERE seq = #{seq}")
    void updatePublicSite(ConferenceSettings settings);

    @Select("SELECT popupLayoutNo FROM conference_settings WHERE seq = #{conferenceSeq}")
    Integer findPopupLayoutNo(@Param("conferenceSeq") Long conferenceSeq);

    @Insert("""
            INSERT INTO conference_settings (
                sitePath,
                defaultLanguage,
                eventName,
                eventStartDate,
                eventEndDate,
                earlyBirdStartDate,
                earlyBirdEndDate,
                regularStartDate,
                regularEndDate,
                registrationCurrency,
                abstractStartDate,
                abstractEndDate,
                presentationMaterialStartDate,
                presentationMaterialEndDate,
                venueAddress,
                createdAt,
                updatedAt
            ) VALUES (
                #{sitePath},
                #{defaultLanguage},
                #{eventName},
                #{eventStartDate},
                #{eventEndDate},
                #{earlyBirdStartDate},
                #{earlyBirdEndDate},
                #{regularStartDate},
                #{regularEndDate},
                #{registrationCurrency},
                #{abstractStartDate},
                #{abstractEndDate},
                #{presentationMaterialStartDate},
                #{presentationMaterialEndDate},
                #{venueAddress},
                NOW(),
                NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insert(ConferenceSettings conferenceSettings);

    @Update("""
            UPDATE conference_settings
            SET eventName = #{eventName},
                eventStartDate = #{eventStartDate},
                eventEndDate = #{eventEndDate},
                earlyBirdStartDate = #{earlyBirdStartDate},
                earlyBirdEndDate = #{earlyBirdEndDate},
                regularStartDate = #{regularStartDate},
                regularEndDate = #{regularEndDate},
                registrationCurrency = #{registrationCurrency},
                abstractStartDate = #{abstractStartDate},
                abstractEndDate = #{abstractEndDate},
                presentationMaterialStartDate = #{presentationMaterialStartDate},
                presentationMaterialEndDate = #{presentationMaterialEndDate},
                venueAddress = #{venueAddress},
                updatedAt = NOW()
            WHERE seq = #{seq}
            """)
    void update(ConferenceSettings conferenceSettings);

    @Update("""
            UPDATE conference_settings
            SET popupLayoutNo = #{popupLayoutNo},
                updatedAt = NOW()
            WHERE seq = #{conferenceSeq}
            """)
    int updatePopupLayoutNo(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("popupLayoutNo") int popupLayoutNo
    );
}
