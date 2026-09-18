package com.bjworld21.congress.repository;

import com.bjworld21.congress.entity.MenuHtmlHistory;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface MenuHtmlHistoryRepository {
    @Select("""
            SELECT seq, menuSeq, revisionNo, operationType, restoredFromSeq,
                   changeMemo, createdBy, createdByName, createdAt
            FROM menu_html_histories WHERE menuSeq = #{menuSeq} AND languageCode = 'en'
            ORDER BY revisionNo DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<MenuHtmlHistory> findPage(@Param("menuSeq") Long menuSeq, @Param("limit") int limit,
                                  @Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM menu_html_histories WHERE menuSeq = #{menuSeq} AND languageCode = 'en'")
    long count(@Param("menuSeq") Long menuSeq);

    @Select("SELECT * FROM menu_html_histories WHERE menuSeq = #{menuSeq} AND languageCode = 'en' AND seq = #{seq}")
    MenuHtmlHistory findBySeq(@Param("menuSeq") Long menuSeq, @Param("seq") Long seq);

    @Select("SELECT seq, menuSeq, languageCode, revisionNo, operationType, restoredFromSeq, changeMemo, createdBy, createdByName, createdAt FROM menu_html_histories WHERE menuSeq=#{menuSeq} AND languageCode=#{language} ORDER BY revisionNo DESC LIMIT #{limit} OFFSET #{offset}")
    List<MenuHtmlHistory> findLanguagePage(@Param("menuSeq") Long menuSeq, @Param("language") String language, @Param("limit") int limit, @Param("offset") long offset);
    @Select("SELECT COUNT(*) FROM menu_html_histories WHERE menuSeq=#{menuSeq} AND languageCode=#{language}")
    long countLanguage(@Param("menuSeq") Long menuSeq, @Param("language") String language);
    @Select("SELECT COUNT(*) FROM menu_html_histories WHERE menuSeq=#{menuSeq}")
    long countAllLanguages(Long menuSeq);
    @Select("SELECT * FROM menu_html_histories WHERE menuSeq=#{menuSeq} AND languageCode=#{language} AND seq=#{seq}")
    MenuHtmlHistory findLanguageHistory(@Param("menuSeq") Long menuSeq, @Param("language") String language, @Param("seq") Long seq);

    @Insert("""
            INSERT INTO menu_html_histories
                (menuSeq, languageCode, revisionNo, menuHtml, operationType, restoredFromSeq,
                 changeMemo, createdBy, createdByName, createdAt)
            VALUES (#{menuSeq}, COALESCE(#{languageCode}, 'en'), #{revisionNo}, #{menuHtml}, #{operationType}, #{restoredFromSeq},
                    #{changeMemo}, #{createdBy}, #{createdByName}, CURRENT_TIMESTAMP(6))
            """)
    void insert(MenuHtmlHistory history);
}
