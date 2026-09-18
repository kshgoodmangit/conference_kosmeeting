package com.bjworld21.congress.service;

import com.bjworld21.congress.entity.AbstractSimilarityResult;
import com.bjworld21.congress.repository.AbstractSimilarityResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Consumer;

@Service
public class AbstractSimilarityResultStorageService {
    private static final int INSERT_BATCH_SIZE = 200;

    private final AbstractSimilarityResultRepository resultRepository;

    public AbstractSimilarityResultStorageService(AbstractSimilarityResultRepository resultRepository) {
        this.resultRepository = resultRepository;
    }

    public List<AbstractSimilarityResult> findBySourceAbstractSeq(Long conferenceSeq, Long sourceAbstractSeq) {
        return resultRepository.findBySourceAbstractSeq(conferenceSeq, sourceAbstractSeq);
    }

    @Transactional
    public void replaceAll(Long conferenceSeq, List<AbstractSimilarityResult> results) {
        replaceAll(conferenceSeq, results, progress -> {
        });
    }

    @Transactional
    public void replaceAll(
            Long conferenceSeq,
            List<AbstractSimilarityResult> results,
            Consumer<StorageProgress> progressConsumer
    ) {
        resultRepository.deleteAll(conferenceSeq);
        for (int start = 0; start < results.size(); start += INSERT_BATCH_SIZE) {
            int end = Math.min(start + INSERT_BATCH_SIZE, results.size());
            resultRepository.insertBatch(results.subList(start, end));
            progressConsumer.accept(new StorageProgress(end, results.size()));
        }
        if (results.isEmpty()) {
            progressConsumer.accept(new StorageProgress(0, 0));
        }
    }

    public record StorageProgress(int processedCount, int totalCount) {
    }
}
