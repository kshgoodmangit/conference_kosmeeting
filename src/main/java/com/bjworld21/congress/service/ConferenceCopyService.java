package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.ConferenceSettingsResponse;
import com.bjworld21.congress.dto.ConferenceSettingsSaveAllRequest;
import org.jsoup.Jsoup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;

@Service
public class ConferenceCopyService {
    private static final Set<String> SECTIONS = Set.of("fees", "options", "menus", "review", "program", "promotion", "speakers");
    private final JdbcTemplate jdbc;
    private final ConferenceSettingsService conferences;
    private final UploadStorage storage;

    public ConferenceCopyService(JdbcTemplate jdbc, ConferenceSettingsService conferences, UploadStorage storage) {
        this.jdbc = jdbc;
        this.conferences = conferences;
        this.storage = storage;
    }

    public record Request(ConferenceSettingsSaveAllRequest settings, Set<String> sections) {}
    public record Result(ConferenceSettingsResponse conference, Map<String, Integer> copiedCounts, Set<String> skippedFiles) {}

    @Transactional
    public Result copy(long sourceSeq, Request request) {
        if (request == null || request.settings() == null || request.sections() == null
                || !SECTIONS.containsAll(request.sections())) throw new IllegalArgumentException("복사할 행사 정보와 항목을 확인해 주세요.");
        var source = conferences.getSettings(sourceSeq);
        if (source.getSeq() == null) throw new IllegalArgumentException("복사할 원본 학회가 없습니다.");
        var settings = request.settings();
        if (source.getEventStartDate() == null || settings.getEventStartDate() == null || settings.getEventEndDate() == null)
            throw new IllegalArgumentException("원본과 새 행사의 개최일을 설정해 주세요.");
        if (settings.getRegistrationCurrency() == null) settings.setRegistrationCurrency(source.getRegistrationCurrency());
        var saved = conferences.saveConfigured(null, settings);
        var job = new CopyJob(source, saved);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) job.removeCreatedFiles();
                }
            });
        }
        try {
            var sections = request.sections();
            if (sections.contains("fees")) {
                var categories = job.rows("registration_categories", "conferenceSeq = ? AND isDelete = 'N'", sourceSeq, row -> {});
                for (var entry : categories.entrySet()) job.rows("registration_fee_rates", "categorySeq = ?", entry.getKey(),
                        row -> row.put("categorySeq", entry.getValue()));
            }
            if (sections.contains("options")) job.rows("registration_options", "conferenceSeq = ?", sourceSeq,
                    row -> row.put("versionNo", 1));
            if (sections.contains("review")) job.rows("abstract_evaluation_items", "conferenceSeq = ? AND isDelete = 'N'", sourceSeq, row -> {});
            if (sections.contains("speakers")) job.rows("speakers", "conferenceSeq = ?", sourceSeq,
                    row -> job.file(row, "profileImageSaveFilename", UploadStorage.SPEAKERS));
            if (sections.contains("promotion")) {
                job.rows("sponsors", "conferenceSeq = ?", sourceSeq, row -> {
                    job.file(row, "logoSaveFilename", UploadStorage.SPONSORS);
                    if (row.get("logoSaveFilename") == null) row.clear();
                });
                job.rows("popups", "conferenceSeq = ?", sourceSeq, row -> job.file(row, "popupImageSaveFilename", UploadStorage.POPUPS));
            }
            if (sections.contains("menus")) {
                var menus = job.rows("menu_settings", "conferenceSeq = ? AND menuScope = 'user'", sourceSeq,
                        row -> row.put("htmlRevisionNo", 1));
                for (var entry : menus.entrySet()) job.rows("menu_translations", "menuSeq = ?", entry.getKey(), row -> {
                    row.put("menuSeq", entry.getValue()); row.put("htmlRevisionNo", 1);
                });
                var posts = job.rows("board_posts", "conferenceSeq = ?", sourceSeq, row -> row.put("viewCount", 0));
                for (var entry : posts.entrySet()) job.rows("board_attachments", "boardPostSeq = ?", entry.getKey(), row -> {
                    row.put("boardPostSeq", entry.getValue()); row.put("downloadCount", 0);
                    job.file(row, "savedFilename", UploadStorage.BOARDS);
                    if (row.get("savedFilename") == null) row.clear();
                });
            }
            if (sections.contains("program")) job.program();
            return new Result(saved, job.counts, job.skippedFiles);
        } catch (RuntimeException exception) {
            job.removeCreatedFiles();
            throw exception;
        }
    }

    private final class CopyJob {
        final ConferenceSettingsResponse source;
        final ConferenceSettingsResponse target;
        final long days;
        final Map<String, Integer> counts = new LinkedHashMap<>();
        final Map<String, String> files = new HashMap<>();
        final Set<String> skippedFiles = new LinkedHashSet<>();
        final List<Path> createdFiles = new ArrayList<>();

        CopyJob(ConferenceSettingsResponse source, ConferenceSettingsResponse target) {
            this.source = source; this.target = target;
            days = ChronoUnit.DAYS.between(source.getEventStartDate(), target.getEventStartDate());
        }

        Map<Long, Long> rows(String table, String where, Object value, Consumer<Map<String, Object>> transform) {
            var result = new LinkedHashMap<Long, Long>();
            for (var original : jdbc.queryForList("SELECT * FROM " + table + " WHERE " + where + " ORDER BY seq", value)) {
                long oldSeq = ((Number) original.get("seq")).longValue();
                var row = new LinkedHashMap<>(original);
                row.remove("seq"); row.remove("createdAt"); row.remove("updatedAt"); row.remove("conferenceScopeSeq");
                if (row.containsKey("conferenceSeq")) row.put("conferenceSeq", target.getSeq());
                row.replaceAll((key, item) -> shift(item, days));
                for (String field : List.of("menuHtml", "content", "linkUrl", "routePath")) {
                    if (row.get(field) instanceof String text) row.put(field, rewrite(text));
                }
                transform.accept(row);
                if (row.isEmpty()) continue;
                result.put(oldSeq, insert(table, row));
            }
            counts.merge(table, result.size(), Integer::sum);
            return result;
        }

        void program() {
            var rooms = rows("program_rooms", "conferenceSeq = ?", source.getSeq(), row -> {});
            var programDays = rows("program_days", "conferenceSeq = ?", source.getSeq(), row -> {
                LocalDate date = row.get("eventDate") instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) row.get("eventDate");
                if (date.isBefore(target.getEventStartDate()) || date.isAfter(target.getEventEndDate()))
                    throw new IllegalArgumentException("프로그램을 복사하려면 원본 프로그램 일자가 새 행사 기간에 모두 포함되어야 합니다.");
            });
            var items = new LinkedHashMap<Long, Long>();
            var parents = new LinkedHashMap<Long, Long>();
            for (var day : programDays.entrySet()) {
                rows("program_day_rooms", "programDaySeq = ?", day.getKey(), row -> {
                    row.put("programDaySeq", day.getValue()); row.put("roomSeq", remap(rooms, row.get("roomSeq")));
                });
                var copied = rows("program_items", "programDaySeq = ?", day.getKey(), row -> {
                    // Submitted abstracts and their assignment history belong to the original conference.
                    row.put("programDaySeq", day.getValue()); row.put("roomSeq", remap(rooms, row.get("roomSeq")));
                    row.put("abstractSubmissionSeq", null); row.put("parentSeq", null);
                });
                items.putAll(copied);
                for (var row : jdbc.queryForList("SELECT seq, parentSeq FROM program_items WHERE programDaySeq = ? AND parentSeq IS NOT NULL", day.getKey()))
                    parents.put(((Number) row.get("seq")).longValue(), ((Number) row.get("parentSeq")).longValue());
            }
            parents.forEach((child, parent) -> jdbc.update("UPDATE program_items SET parentSeq = ? WHERE seq = ?", remap(items, parent), remap(items, child)));
            for (var item : items.entrySet()) rows("program_item_people", "programItemSeq = ?", item.getKey(), row -> row.put("programItemSeq", item.getValue()));
        }

        String rewrite(String text) {
            String rewritten = text.replaceAll("/" + java.util.regex.Pattern.quote(source.getSitePath()) + "(?=[/\\\"'?#\\s]|$)", "/" + target.getSitePath())
                    .replaceAll("/api/public/" + source.getSeq() + "(?=[/?\\\"']|$)", "/api/public/" + target.getSeq());
            if (!rewritten.contains("<")) return rewritten;
            var document = Jsoup.parseBodyFragment(rewritten);
            document.outputSettings().prettyPrint(false);
            for (var image : document.select("img[src]")) {
                String url = image.attr("src");
                for (String menu : List.of(UploadStorage.BOARDS, UploadStorage.POPUPS, UploadStorage.MAIL)) {
                    String prefix = "/api/" + menu + "/images/";
                    if (url.startsWith(prefix)) {
                        String copied = copyFile(menu, url.substring(prefix.length()));
                        if (copied == null) image.remove(); else image.attr("src", prefix + copied);
                    }
                }
            }
            return document.body().html();
        }

        void file(Map<String, Object> row, String field, String menu) {
            if (row.get(field) instanceof String path && !path.isBlank()) row.put(field, copyFile(menu, path));
        }

        String copyFile(String menu, String savedPath) {
            return files.computeIfAbsent(menu + "/" + savedPath, ignored -> {
                Path original = storage.resolve(menu, savedPath);
                if (!Files.isRegularFile(original)) {
                    skippedFiles.add(menu + "/" + savedPath);
                    return null;
                }
                String name = original.getFileName().toString();
                String extension = name.contains(".") ? name.substring(name.lastIndexOf('.')) : "";
                var destination = storage.monthlyTarget(menu, UUID.randomUUID() + extension);
                try {
                    Files.createDirectories(destination.path().getParent());
                    createdFiles.add(destination.path());
                    Files.copy(original, destination.path());
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException("원본 첨부파일을 복사하지 못했습니다: " + menu + "/" + savedPath, exception);
                }
                return destination.relativePath();
            });
        }

        void removeCreatedFiles() {
            for (Path path : createdFiles) try { Files.deleteIfExists(path); }
            catch (java.io.IOException exception) { org.slf4j.LoggerFactory.getLogger(ConferenceCopyService.class).warn("복사 임시파일 정리 실패: {}", path, exception); }
        }
    }

    static Object shift(Object value, long days) {
        if (value instanceof java.sql.Date date) return java.sql.Date.valueOf(date.toLocalDate().plusDays(days));
        if (value instanceof Timestamp time) return Timestamp.valueOf(time.toLocalDateTime().plusDays(days));
        if (value instanceof LocalDate date) return date.plusDays(days);
        if (value instanceof LocalDateTime time) return time.plusDays(days);
        return value;
    }

    static Long remap(Map<Long, Long> keys, Object value) {
        if (value == null) return null;
        Long result = keys.get(((Number) value).longValue());
        if (result == null) throw new IllegalArgumentException("원본 행사의 연결 정보가 올바르지 않습니다.");
        return result;
    }

    private long insert(String table, Map<String, Object> row) {
        String sql = "INSERT INTO " + table + " (`" + String.join("`, `", row.keySet()) + "`) VALUES ("
                + String.join(",", Collections.nCopies(row.size(), "?")) + ")";
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            int index = 1;
            for (Object value : row.values()) statement.setObject(index++, value);
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
}
