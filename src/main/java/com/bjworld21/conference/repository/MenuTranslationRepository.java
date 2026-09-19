package com.bjworld21.conference.repository;
import com.bjworld21.conference.entity.MenuTranslation;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface MenuTranslationRepository {
    @Select("SELECT t.* FROM menu_translations t JOIN menu_settings m ON m.seq=t.menuSeq WHERE m.conferenceSeq=#{conferenceSeq} AND m.menuScope='user' AND t.languageCode=#{language}")
    List<MenuTranslation> findAll(@Param("conferenceSeq") Long conferenceSeq, @Param("language") String language);
    @Select("SELECT * FROM menu_translations WHERE menuSeq=#{menuSeq} AND languageCode=#{language}")
    MenuTranslation find(@Param("menuSeq") Long menuSeq, @Param("language") String language);
    @Insert("INSERT INTO menu_translations(menuSeq,languageCode,menuName,menuHtml,htmlRevisionNo) VALUES(#{menuSeq},#{languageCode},#{menuName},#{menuHtml},#{htmlRevisionNo}) ON DUPLICATE KEY UPDATE menuName=VALUES(menuName),menuHtml=VALUES(menuHtml),htmlRevisionNo=VALUES(htmlRevisionNo),updatedAt=NOW()")
    void save(MenuTranslation translation);
    @Delete("DELETE FROM menu_translations WHERE menuSeq=#{menuSeq}")
    void deleteForMenu(Long menuSeq);
}
