package com.bjworld21.congress.repository;

import com.bjworld21.congress.entity.MenuHtmlHistory;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface MenuHtmlHistoryRepository {
    @Select("""
            SELECT seq, menuSeq, revisionNo, operationType, restoredFromSeq,
                   changeMemo, createdBy, createdByName, createdAt
            FROM menu_html_histories WHERE menuSeq = #{menuSeq}
            ORDER BY revisionNo DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<MenuHtmlHistory> findPage(@Param("menuSeq") Long menuSeq, @Param("limit") int limit,
                                  @Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM menu_html_histories WHERE menuSeq = #{menuSeq}")
    long count(@Param("menuSeq") Long menuSeq);

    @Select("SELECT * FROM menu_html_histories WHERE menuSeq = #{menuSeq} AND seq = #{seq}")
    MenuHtmlHistory findBySeq(@Param("menuSeq") Long menuSeq, @Param("seq") Long seq);

    @Insert("""
            INSERT INTO menu_html_histories
                (menuSeq, revisionNo, menuHtml, operationType, restoredFromSeq,
                 changeMemo, createdBy, createdByName, createdAt)
            VALUES (#{menuSeq}, #{revisionNo}, #{menuHtml}, #{operationType}, #{restoredFromSeq},
                    #{changeMemo}, #{createdBy}, #{createdByName}, CURRENT_TIMESTAMP(6))
            """)
    void insert(MenuHtmlHistory history);
}
