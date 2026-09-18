package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.entity.ExcelDownloadLog;
import com.bjworld21.congress.repository.ExcelDownloadLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExcelDownloadAuditLogWriter {
    private final ExcelDownloadLogRepository repository;
    private final PersonalDataProperties personalDataProperties;

    public ExcelDownloadAuditLogWriter(
            ExcelDownloadLogRepository repository,
            PersonalDataProperties personalDataProperties
    ) {
        this.repository = repository;
        this.personalDataProperties = personalDataProperties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long start(ExcelDownloadLog log) {
        repository.insert(log, personalDataProperties.requireDbEncString());
        if (log.getSeq() == null) {
            throw new IllegalStateException("엑셀 다운로드 이력을 생성하지 못했습니다.");
        }
        return log.getSeq();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(long seq, ExcelExportResult result) {
        int updated = repository.markSuccess(seq, result.rowCount(), result.filename(), result.content().length);
        if (updated != 1) {
            throw new IllegalStateException("엑셀 다운로드 성공 이력을 저장하지 못했습니다.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(long seq, String failureCode, String failureMessage) {
        int updated = repository.markFailed(seq, failureCode, failureMessage);
        if (updated != 1) {
            throw new IllegalStateException("엑셀 다운로드 실패 이력을 저장하지 못했습니다.");
        }
    }
}
