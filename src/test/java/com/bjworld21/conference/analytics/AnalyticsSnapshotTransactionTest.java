package com.bjworld21.conference.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsSnapshotTransactionTest {
    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement
    static class Config {}

    static class Transactions extends AbstractPlatformTransactionManager {
        final AtomicInteger begun=new AtomicInteger();
        final AtomicInteger committed=new AtomicInteger();
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object transaction,TransactionDefinition definition) {
            assertTrue(definition.isReadOnly());
            assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ,definition.getIsolationLevel());
            assertEquals(60,definition.getTimeout());begun.incrementAndGet();
        }
        protected void doCommit(DefaultTransactionStatus status) { committed.incrementAndGet(); }
        protected void doRollback(DefaultTransactionStatus status) {}
    }

    @Test void uncachedReadsOpenSnapshotTransactionsAndValidationStaysOutsideThem() throws Exception {
        var repository=mock(AnalyticsRepository.class);var transactions=new Transactions();
        when(repository.conferenceExists(1)).thenAnswer(call->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());return true;
        });
        when(repository.totals(anyLong(),any(),any())).thenAnswer(call->{
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());return Map.of();
        });
        try(var context=new AnnotationConfigApplicationContext()) {
            context.register(Config.class);
            context.registerBean("transactionManager",Transactions.class,()->transactions);
            context.registerBean(AnalyticsRepository.class,()->repository);
            context.registerBean(AnalyticsSnapshotReader.class);
            context.registerBean(AnalyticsService.class);
            context.refresh();
            var service=context.getBean(AnalyticsService.class);service.aggregateDirtyDays();
            var date=LocalDate.of(2026,9,12);
            service.overview(1,date,date);
            assertEquals(2,transactions.begun.get()); // report and realtime
            service.overview(1,date,date);
            assertEquals(4,transactions.begun.get()); // report and realtime are intentionally uncached
            var executor=Executors.newSingleThreadExecutor();
            try {
                executor.submit(()->context.getBean(AnalyticsSnapshotReader.class).overview(1,date,date,true)).get(5,TimeUnit.SECONDS);
            } finally {executor.shutdownNow();}
            assertEquals(5,transactions.begun.get());assertEquals(5,transactions.committed.get());
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        }
    }
}
