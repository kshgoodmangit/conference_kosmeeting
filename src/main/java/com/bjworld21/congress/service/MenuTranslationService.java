package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.MenuSettingsResponse;
import com.bjworld21.congress.entity.MenuSettings;
import com.bjworld21.congress.entity.MenuTranslation;
import com.bjworld21.congress.repository.MenuSettingsRepository;
import com.bjworld21.congress.repository.MenuTranslationRepository;
import com.bjworld21.congress.repository.MenuHtmlHistoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Language content shares the menu row lock, so revisions are isolated and serialized. */
@Service
public class MenuTranslationService {
    private final MenuTranslationRepository translations;
    private final MenuSettingsRepository menus;
    private final MenuHtmlHistoryRepository histories;
    private final MenuHtmlHistoryService historyService;
    private final ConferenceSettingsService conferences;
    private final CmsHtmlSanitizer sanitizer;

    public MenuTranslationService(MenuTranslationRepository translations, MenuSettingsRepository menus,
            MenuHtmlHistoryRepository histories, MenuHtmlHistoryService historyService,
            ConferenceSettingsService conferences, CmsHtmlSanitizer sanitizer) {
        this.translations=translations; this.menus=menus; this.histories=histories;
        this.historyService=historyService; this.conferences=conferences; this.sanitizer=sanitizer;
    }

    public String requireLanguage(Long conferenceSeq, String language) {
        String normalized = ConferenceSettingsService.normalizeLanguage(language);
        var settings=conferences.getSettings(conferenceSeq);
        if (settings.getSeq()==null || settings.getSupportedLanguages()==null
                || !settings.getSupportedLanguages().contains(normalized))
            throw new IllegalArgumentException("학회에서 지원하지 않는 언어입니다.");
        return normalized;
    }

    public List<MenuSettings> localize(Long conferenceSeq, List<MenuSettings> source, String language) {
        String code=requireLanguage(conferenceSeq,language);
        Map<Long,MenuTranslation> byMenu=new HashMap<>();
        translations.findAll(conferenceSeq,code).forEach(t->byMenu.put(t.getMenuSeq(),t));
        for (MenuSettings menu: source) if ("user".equals(menu.getMenuScope())) apply(menu,byMenu.get(menu.getSeq()),code);
        return source;
    }

    public void validatePagePath(Long conferenceSeq, String routePath) {
        if (routePath == null) return;
        if (conferences.getSettings(conferenceSeq).getSupportedLanguages().stream()
                .anyMatch(language -> routePath.equalsIgnoreCase("/" + language)))
            throw new IllegalArgumentException("지원 언어 코드는 페이지 경로로 사용할 수 없습니다.");
    }

    private void apply(MenuSettings menu, MenuTranslation translation, String language) {
        menu.setLanguageCode(language);
        menu.setTranslationReady(translation != null);
        menu.setMenuName(translation==null ? menu.getMenuKey() : translation.getMenuName());
        menu.setMenuHtml(translation==null ? null : translation.getMenuHtml());
        menu.setHtmlRevisionNo(translation==null ? 0L : translation.getHtmlRevisionNo());
    }

    @Transactional
    public void save(Long conferenceSeq, Long menuSeq, String language, String name, String html,
                     boolean htmlChanged, String memo, Long adminSeq) {
        String code=requireLanguage(conferenceSeq,language);
        MenuSettings menu=requireMenu(conferenceSeq,menuSeq);
        if (!"user".equals(menu.getMenuScope())) throw new IllegalArgumentException("사용자 메뉴만 번역할 수 있습니다.");
        if(name==null || name.isBlank()) throw new IllegalArgumentException("메뉴명은 필수입니다.");
        var previous=translations.find(menuSeq,code);
        String nextHtml=htmlChanged ? normalize(html) : previous==null ? null : previous.getMenuHtml();
        long revision=previous==null ? 0L : previous.getHtmlRevisionNo();
        boolean record=previous==null || !Objects.equals(normalize(previous.getMenuHtml()),nextHtml);
        MenuHtmlHistoryService.normalizeMemo(memo);
        if(record) {
            revision++;
            menu.setLanguageCode(code); menu.setMenuHtml(nextHtml); menu.setHtmlRevisionNo(revision);
            historyService.record(menu,previous==null ? "INITIAL" : "SAVE",null,memo,adminSeq);
        }
        translations.save(MenuTranslation.builder().menuSeq(menuSeq).languageCode(code)
                .menuName(name.trim()).menuHtml(nextHtml).htmlRevisionNo(revision).build());
        // Keep the legacy English columns synchronized for older integrations only.
        if ("en".equals(code)) {
            menu.setMenuName(name.trim()); menu.setMenuHtml(nextHtml); menu.setHtmlRevisionNo(revision);
            menus.update(menu);
        }
    }

    @Transactional(readOnly = true)
    public MenuHtmlHistoryService.HistoryPage list(Long conferenceSeq,Long menuSeq,String language,int page,int size) {
        String code=requireLanguage(conferenceSeq,language); requireReadMenu(conferenceSeq,menuSeq);
        if(page<0 || size<1 || size>100) throw new IllegalArgumentException("올바른 이력 페이지를 요청해 주세요.");
        var current=translations.find(menuSeq,code);
        return new MenuHtmlHistoryService.HistoryPage(histories.findLanguagePage(menuSeq,code,size,(long)page*size),
                histories.countLanguage(menuSeq,code),page,size,current==null?0:current.getHtmlRevisionNo());
    }

    @Transactional(readOnly = true)
    public MenuHtmlHistoryService.HistoryDetail detail(Long conferenceSeq,Long menuSeq,String language,Long historySeq) {
        String code=requireLanguage(conferenceSeq,language); requireReadMenu(conferenceSeq,menuSeq);
        var history=histories.findLanguageHistory(menuSeq,code,historySeq);
        if(history==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"해당 언어의 HTML 이력이 없습니다.");
        var current=translations.find(menuSeq,code);
        return new MenuHtmlHistoryService.HistoryDetail(history,sanitizer.sanitize(history.getMenuHtml()),
                current==null?null:current.getMenuHtml(),current==null?0:current.getHtmlRevisionNo());
    }

    @Transactional
    public MenuSettingsResponse restore(Long conferenceSeq,Long menuSeq,String language,Long historySeq,Long adminSeq,String memo) {
        String code=requireLanguage(conferenceSeq,language);
        MenuSettings menu=requireMenu(conferenceSeq,menuSeq);
        if (!"user".equals(menu.getMenuScope())) throw new IllegalArgumentException("사용자 메뉴만 번역할 수 있습니다.");
        var history=histories.findLanguageHistory(menuSeq,code,historySeq);
        var current=translations.find(menuSeq,code);
        if(history==null || current==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"해당 언어의 HTML 이력이 없습니다.");
        MenuHtmlHistoryService.normalizeMemo(memo);
        if(!Objects.equals(normalize(current.getMenuHtml()),normalize(history.getMenuHtml()))) {
            current.setMenuHtml(history.getMenuHtml()); current.setHtmlRevisionNo(current.getHtmlRevisionNo()+1);
            apply(menu,current,code);
            historyService.record(menu,"RESTORE",historySeq,memo,adminSeq);
            translations.save(current);
            if("en".equals(code)) menus.updateHtml(menu);
        }
        return MenuSettingsResponse.builder().seq(menuSeq).languageCode(code).translationReady(true)
                .menuHtml(current.getMenuHtml()).htmlRevisionNo(current.getHtmlRevisionNo()).build();
    }

    public boolean hasHistory(Long menuSeq) { return histories.countAllLanguages(menuSeq)>0; }

    public void deleteForMenu(Long menuSeq) { translations.deleteForMenu(menuSeq); }

    /** For a future conference copy workflow after its menu IDs have been remapped. */
    @Transactional
    public void copyTranslations(Long sourceConferenceSeq, Long sourceMenuSeq,
                                 Long targetConferenceSeq, Long targetMenuSeq, Long adminSeq) {
        var source=requireMenu(sourceConferenceSeq,sourceMenuSeq);
        var target=requireMenu(targetConferenceSeq,targetMenuSeq);
        if (!"user".equals(source.getMenuScope()) || !"user".equals(target.getMenuScope())
                || sourceConferenceSeq.equals(targetConferenceSeq))
            throw new IllegalArgumentException("다른 학회의 사용자 메뉴로만 번역을 복사할 수 있습니다.");
        for (String language: conferences.getSettings(targetConferenceSeq).getSupportedLanguages()) {
            var translation=translations.find(sourceMenuSeq,language);
            if (translation == null) continue;
            if (translations.find(targetMenuSeq,language) != null)
                throw new IllegalArgumentException("대상 메뉴에 이미 작성된 번역이 있습니다.");
            save(targetConferenceSeq,targetMenuSeq,language,translation.getMenuName(),translation.getMenuHtml(),
                    true,"학회 메뉴 번역 복사",adminSeq);
        }
    }

    private MenuSettings requireMenu(Long conferenceSeq,Long menuSeq) {
        var menu=menus.findBySeqForUpdate(conferenceSeq,menuSeq);
        if(menu==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"메뉴를 찾을 수 없습니다.");
        return menu;
    }
    private void requireReadMenu(Long conferenceSeq,Long menuSeq) {
        var menu=menus.findBySeq(conferenceSeq,menuSeq);
        if(menu==null || !"user".equals(menu.getMenuScope()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"사용자 메뉴를 찾을 수 없습니다.");
    }
    private String normalize(String html) { return html==null || html.isBlank()?null:html.trim(); }
}
