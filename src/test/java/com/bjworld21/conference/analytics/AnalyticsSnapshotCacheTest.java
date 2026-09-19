package com.bjworld21.conference.analytics;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.ArrayDeque;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

class AnalyticsSnapshotCacheTest {
    static class TestClock extends Clock {
        Instant now=Instant.parse("2026-09-13T14:59:59Z");
        void advance(long seconds) { now=now.plusSeconds(seconds); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    final TestClock clock=new TestClock();
    final ArrayDeque<Runnable> queue=new ArrayDeque<>();
    final AnalyticsSnapshotCache<String,Integer> cache=new AnalyticsSnapshotCache<>(clock,queue::add);
    final AtomicInteger calls=new AtomicInteger();
    final Supplier<Integer> loader=calls::incrementAndGet;

    @Test void coldThenWarmThenExpiryRefreshIsSingleFlight() {
        assertEquals(1,cache.get("1:30:board",loader));
        assertEquals(1,cache.get("1:30:board",loader));
        clock.advance(120);
        for(int i=0;i<20;i++)assertEquals(1,cache.get("1:30:board",loader));
        assertEquals(1,queue.size());assertEquals(1,calls.get());
        queue.remove().run();
        assertEquals(2,cache.get("1:30:board",loader));
    }

    @Test void dirtyCommitsCoalesceAndCannotPostponeRefreshForever() {
        cache.get("1",loader);
        for(int i=0;i<3;i++) {
            clock.advance(10);cache.markStale("1"::equals);
            assertEquals(1,cache.get("1",loader));
        }
        assertEquals(1,queue.size());
        queue.remove().run();assertEquals(2,cache.get("1",loader));
    }

    @Test void conferencePeriodViewAndMidnightKeysAreIndependent() {
        assertEquals(1,cache.get("1:09-13:board",loader));
        assertEquals(2,cache.get("2:09-13:board",loader));
        assertEquals(3,cache.get("1:09-13:details",loader));
        clock.advance(2); // crosses Seoul midnight
        assertEquals(4,cache.get("1:09-14:board",loader));
        cache.markStale(key->key.startsWith("1:"));clock.advance(30);
        assertEquals(2,cache.get("2:09-13:board",loader));
        assertTrue(queue.isEmpty());
        assertEquals(1,cache.get("1:09-13:board",loader));assertEquals(1,queue.size());
    }

    @Test void failedRefreshRetriesButNeverServesPastHardLimit() {
        cache.get("1",loader);clock.advance(120);
        Supplier<Integer> broken=()->{calls.incrementAndGet();throw new IllegalStateException("offline");};
        assertEquals(1,cache.get("1",broken));queue.remove().run();
        assertEquals(1,cache.get("1",broken));assertTrue(queue.isEmpty());
        clock.advance(30);assertEquals(1,cache.get("1",broken));queue.remove().run();
        clock.advance(150);
        assertThrows(IllegalStateException.class,()->cache.get("1",broken));
        int failedCalls=calls.get();
        assertThrows(IllegalStateException.class,()->cache.get("1",broken));
        assertEquals(failedCalls,calls.get());
        clock.advance(30);assertEquals(failedCalls+1,cache.get("1",loader));
    }

    @Test void coldFailureHasBackoffAndRecovers() {
        assertThrows(IllegalStateException.class,()->cache.get("1",()->{throw new IllegalStateException();}));
        assertThrows(IllegalStateException.class,()->cache.get("1",loader));
        assertEquals(0,calls.get());clock.advance(30);
        assertEquals(1,cache.get("1",loader));
    }

    @Test void executorRejectionKeepsBoundedStaleAndRetries() {
        var rejected=new AnalyticsSnapshotCache<String,Integer>(clock,task->{throw new RejectedExecutionException();});
        assertEquals(1,rejected.get("1",loader));clock.advance(120);
        assertEquals(1,rejected.get("1",loader));clock.advance(180);
        assertEquals(2,rejected.get("1",loader)); // hard expiry loads on caller
    }

    @Test void updateDuringReadIsNotLostAndAgeStartsBeforeSql() {
        cache.get("1",()->{cache.markStale("1"::equals);clock.advance(40);return loader.get();});
        assertEquals(1,cache.get("1",loader));assertEquals(1,queue.size());
    }

    @Test void boundedCacheEvictsOnlyLeastRecentlyUsedIdleEntry() {
        for(int i=0;i<100;i++)cache.get(""+i,loader);
        assertEquals(1,cache.get("0",loader));cache.get("100",loader);
        assertEquals(1,cache.get("0",loader));assertEquals(102,cache.get("1",loader));
    }

    @Test void hardExpiryTakesOverQueuedRefreshWithoutDuplicateCalculation() {
        cache.get("1",loader);clock.advance(120);assertEquals(1,cache.get("1",loader));
        clock.advance(180);assertEquals(2,cache.get("1",loader));
        queue.remove().run();assertEquals(2,calls.get());
        assertEquals(2,cache.get("1",loader));
    }

    @Test void clearDuringRefreshCannotRepublishOldEntry() {
        cache.get("1",loader);clock.advance(120);cache.get("1",loader);
        cache.clear();assertEquals(2,cache.get("1",loader));
        queue.remove().run();assertEquals(2,cache.get("1",loader));
    }

    @Test void simultaneousColdRequestsShareCalculationAndDoNotBlockOtherKeys() throws Exception {
        var executor=Executors.newFixedThreadPool(8);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var concurrent=new AnalyticsSnapshotCache<String,Integer>(Clock.systemUTC(),Runnable::run);
        Supplier<Integer> slow=()->{
            calls.incrementAndGet();entered.countDown();
            try { if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("timeout"); }
            catch(InterruptedException exception){Thread.currentThread().interrupt();throw new IllegalStateException(exception);}
            return 42;
        };
        try {
            var first=executor.submit(()->concurrent.get("1",slow));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            var followers=new java.util.ArrayList<Future<Integer>>();
            for(int i=0;i<6;i++)followers.add(executor.submit(()->concurrent.get("1",slow)));
            assertEquals(9,concurrent.get("2",()->9));
            release.countDown();assertEquals(42,first.get(5,TimeUnit.SECONDS));
            for(var future:followers)assertEquals(42,future.get(5,TimeUnit.SECONDS));
            assertEquals(1,calls.get());
        } finally { release.countDown();executor.shutdownNow(); }
    }
}
