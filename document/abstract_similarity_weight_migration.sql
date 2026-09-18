-- AI 유사도 분석 가중치 설정 기능 배포 전에 1회 실행합니다.
-- 대상 DB: MariaDB 10.6

ALTER TABLE abstract_similarity_jobs
    ADD COLUMN IF NOT EXISTS titleWeight INT NOT NULL DEFAULT 10 COMMENT '제목 유사도 가중치(%)',
    ADD COLUMN IF NOT EXISTS objectiveWeight INT NOT NULL DEFAULT 20 COMMENT 'Objective 유사도 가중치(%)',
    ADD COLUMN IF NOT EXISTS methodsWeight INT NOT NULL DEFAULT 20 COMMENT 'Methods 유사도 가중치(%)',
    ADD COLUMN IF NOT EXISTS resultsWeight INT NOT NULL DEFAULT 30 COMMENT 'Results 유사도 가중치(%)',
    ADD COLUMN IF NOT EXISTS conclusionsWeight INT NOT NULL DEFAULT 20 COMMENT 'Conclusions 유사도 가중치(%)';

ALTER TABLE abstract_similarity_results
    ADD COLUMN IF NOT EXISTS jobSeq BIGINT NULL COMMENT '분석 작업 기본키';

CREATE INDEX IF NOT EXISTS idx_similarity_result_jobSeq
    ON abstract_similarity_results (jobSeq);
