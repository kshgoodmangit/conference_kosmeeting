package com.bjworld21.conference.service;

import com.bjworld21.conference.config.UploadProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@Component
public class UploadStorage {
    public static final String BOARDS = "boards";
    public static final String ABSTRACTS = "abstracts";
    public static final String MAIL = "mail";
    public static final String POPUPS = "popups";
    public static final String SPONSORS = "sponsors";
    public static final String SPEAKERS = "speakers";
    public static final String SPONSORSHIPS = "sponsorships";
    public static final String MAINTENANCE = "maintenance";

    private static final Set<String> SUPPORTED_MENUS = Set.of(
            BOARDS,
            ABSTRACTS,
            MAIL,
            POPUPS,
            SPONSORS,
            SPEAKERS,
            SPONSORSHIPS,
            MAINTENANCE
    );
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private final Path baseDirectory;
    private final Clock clock;

    @Autowired
    public UploadStorage(UploadProperties properties) {
        this(properties, Clock.systemDefaultZone());
    }

    UploadStorage(UploadProperties properties, Clock clock) {
        if (properties == null || properties.getBaseDirectory() == null
                || properties.getBaseDirectory().isBlank()) {
            throw new IllegalArgumentException("업로드 기준 경로가 필요합니다.");
        }
        this.baseDirectory = Paths.get(properties.getBaseDirectory()).toAbsolutePath().normalize();
        this.clock = clock;
    }

    public StoredTarget monthlyTarget(String menu, String filename) {
        if (filename == null || filename.isBlank() || Paths.get(filename).getNameCount() != 1) {
            throw new IllegalArgumentException("올바르지 않은 저장 파일명입니다.");
        }
        String relativePath = YearMonth.now(clock).format(MONTH_FORMATTER) + "/" + filename;
        return new StoredTarget(relativePath, resolve(menu, relativePath));
    }

    public Path resolve(String menu, String savedPath) {
        Path menuDirectory = menuDirectory(menu);
        if (savedPath == null || savedPath.isBlank()) {
            throw new IllegalArgumentException("저장 파일 경로가 필요합니다.");
        }

        Path relativePath = Paths.get(savedPath.replace('\\', '/')).normalize();
        if (relativePath.isAbsolute() || relativePath.startsWith("..")) {
            throw new IllegalArgumentException("올바르지 않은 저장 파일 경로입니다.");
        }

        Path target = menuDirectory.resolve(relativePath).normalize();
        if (!target.startsWith(menuDirectory)) {
            throw new IllegalArgumentException("올바르지 않은 저장 파일 경로입니다.");
        }
        return target;
    }

    Path baseDirectory() {
        return baseDirectory;
    }

    private Path menuDirectory(String menu) {
        if (!SUPPORTED_MENUS.contains(menu)) {
            throw new IllegalArgumentException("지원하지 않는 업로드 메뉴입니다.");
        }
        Path menuDirectory = baseDirectory.resolve(menu).normalize();
        if (!menuDirectory.startsWith(baseDirectory)) {
            throw new IllegalArgumentException("올바르지 않은 업로드 메뉴 경로입니다.");
        }
        return menuDirectory;
    }

    public record StoredTarget(String relativePath, Path path) {
    }
}
