package com.bjworld21.conference.repository;

import com.bjworld21.conference.entity.MenuSettings;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.time.LocalDate;

@Mapper
public interface MenuSettingsRepository {

    @Select("SELECT * FROM menu_settings WHERE (menuScope = 'admin' AND conferenceSeq IS NULL) OR (menuScope = 'user' AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}) ORDER BY menuScope ASC, sortOrder ASC, seq ASC")
    List<MenuSettings> findAll(@Param("conferenceSeq") Long conferenceSeq);

    @Select("""
            SELECT *
            FROM menu_settings
            WHERE menuScope = #{menuScope}
              AND ((#{menuScope} = 'admin' AND conferenceSeq IS NULL) OR (#{menuScope} = 'user' AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}))
              AND enabled = TRUE
              AND (useStartDate IS NULL OR useStartDate <= #{today})
              AND (useEndDate IS NULL OR useEndDate >= #{today})
            ORDER BY sortOrder ASC, seq ASC
            """)
    List<MenuSettings> findActiveByScope(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("menuScope") String menuScope,
            @Param("today") LocalDate today
    );

    @Select("SELECT * FROM menu_settings WHERE seq = #{seq} AND ((menuScope = 'admin' AND conferenceSeq IS NULL) OR (menuScope = 'user' AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}))")
    MenuSettings findBySeq(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);

    @Select("""
            SELECT * FROM menu_settings WHERE seq = #{seq}
              AND ((menuScope = 'admin' AND conferenceSeq IS NULL)
                OR (menuScope = 'user' AND conferenceSeq = #{conferenceSeq})) FOR UPDATE
            """)
    MenuSettings findBySeqForUpdate(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);

    @Update("""
            UPDATE menu_settings SET menuHtml = #{menuHtml}, htmlRevisionNo = #{htmlRevisionNo}, updatedAt = NOW()
            WHERE seq = #{seq}
              AND ((menuScope = 'admin' AND conferenceSeq IS NULL)
                OR (menuScope = 'user' AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}))
            """)
    void updateHtml(MenuSettings menu);

    @Select("SELECT * FROM menu_settings WHERE menuScope = #{menuScope} AND menuKey = #{menuKey} AND ((#{menuScope} = 'admin' AND conferenceSeq IS NULL) OR (#{menuScope} = 'user' AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}))")
    MenuSettings findByScopeAndKey(@Param("conferenceSeq") Long conferenceSeq,
                                   @Param("menuScope") String menuScope, @Param("menuKey") String menuKey);

    @Select("SELECT * FROM menu_settings WHERE menuScope = #{menuScope} AND routePath = #{routePath} AND ((#{menuScope} = 'admin' AND conferenceSeq IS NULL) OR (#{menuScope} = 'user' AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}))")
    MenuSettings findByScopeAndRoutePath(@Param("conferenceSeq") Long conferenceSeq,
                                         @Param("menuScope") String menuScope, @Param("routePath") String routePath);

    @Select("SELECT COUNT(*) FROM menu_settings WHERE menuScope = #{menuScope} AND parentKey = #{parentKey} AND ((#{menuScope} = 'admin' AND conferenceSeq IS NULL) OR (#{menuScope} = 'user' AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}))")
    long countChildren(@Param("conferenceSeq") Long conferenceSeq,
                       @Param("menuScope") String menuScope, @Param("parentKey") String parentKey);

    @Select("SELECT COUNT(*) FROM menu_settings WHERE (menuScope = 'admin' AND conferenceSeq IS NULL) OR (menuScope = 'user' AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long})")
    long count(@Param("conferenceSeq") Long conferenceSeq);

    @Insert("""
            INSERT INTO menu_settings (
                conferenceSeq,
                menuScope,
                menuKey,
                parentKey,
                menuName,
                menuPath,
                menuType,
                pathType,
                routePath,
                depth,
                boardSeq,
                linkUrl,
                targetType,
                authRequired,
                navigationVisible,
                menuHtml,
                htmlRevisionNo,
                sortOrder,
                useStartDate,
                useEndDate,
                enabled,
                createdAt,
                updatedAt
            ) VALUES (
                CASE WHEN #{menuScope} = 'user' THEN #{conferenceSeq,javaType=java.lang.Long} ELSE NULL END,
                #{menuScope},
                #{menuKey},
                #{parentKey},
                #{menuName},
                #{menuPath},
                #{menuType},
                #{pathType},
                #{routePath},
                #{depth},
                #{boardSeq},
                #{linkUrl},
                #{targetType},
                #{authRequired},
                #{navigationVisible},
                #{menuHtml},
                #{htmlRevisionNo},
                #{sortOrder},
                #{useStartDate},
                #{useEndDate},
                #{enabled},
                NOW(),
                NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insert(MenuSettings menuSettings);

    @Update("""
            UPDATE menu_settings
            SET menuName = #{menuName},
                menuPath = #{menuPath},
                menuType = #{menuType},
                pathType = #{pathType},
                routePath = #{routePath},
                depth = #{depth},
                boardSeq = #{boardSeq},
                linkUrl = #{linkUrl},
                targetType = #{targetType},
                authRequired = #{authRequired},
                navigationVisible = #{navigationVisible},
                menuHtml = #{menuHtml},
                htmlRevisionNo = #{htmlRevisionNo},
                useStartDate = #{useStartDate},
                useEndDate = #{useEndDate},
                enabled = #{enabled},
                updatedAt = NOW()
            WHERE seq = #{seq}
              AND (conferenceSeq IS NULL OR conferenceSeq = #{conferenceSeq,javaType=java.lang.Long})
            """)
    void update(MenuSettings menuSettings);

    @Update("""
            UPDATE menu_settings
            SET parentKey = #{parentKey},
                depth = #{depth},
                sortOrder = #{sortOrder},
                updatedAt = NOW()
            WHERE seq = #{seq}
              AND (conferenceSeq IS NULL OR conferenceSeq = #{conferenceSeq,javaType=java.lang.Long})
            """)
    void updateStructure(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("seq") Long seq,
            @Param("parentKey") String parentKey,
            @Param("depth") Integer depth,
            @Param("sortOrder") Integer sortOrder
    );

    @Delete("DELETE FROM menu_settings WHERE seq = #{seq} AND (conferenceSeq IS NULL OR conferenceSeq = #{conferenceSeq,javaType=java.lang.Long})")
    void deleteBySeq(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);
}
