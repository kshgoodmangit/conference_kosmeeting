package com.bjworld21.congress.analytics;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Bounded, per-key single-flight cache. No SQL or future wait holds the metadata monitor. */
final class AnalyticsSnapshotCache<K,V> {
    private static final int CAPACITY=100;
    private final LinkedHashMap<K,Entry<V>> entries=new LinkedHashMap<>(16,0.75f,true);
    private final Clock clock;
    private final Executor executor;
    private final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(getClass());

    private static final class Entry<V> {
        V value;
        Instant startedAt=Instant.MIN;
        Instant retryAt=Instant.MIN;
        long revision;
        long loadedRevision;
        RuntimeException failure;
        CompletableFuture<V> inFlight;
        boolean loading;
    }

    AnalyticsSnapshotCache(Clock clock, Executor executor) { this.clock=clock;this.executor=executor; }

    synchronized void clear() { entries.clear(); }

    synchronized void markStale(Predicate<K> predicate) {
        entries.forEach((key,entry)->{ if(predicate.test(key))entry.revision++; });
    }

    V get(K key, Supplier<V> loader) {
        Entry<V> entry;
        CompletableFuture<V> flight;
        boolean launch=false;
        boolean serveStale;
        V previous;
        synchronized(this) {
            entry=entries.get(key);
            if(entry==null) {
                if(entries.size()>=CAPACITY) {
                    var iterator=entries.entrySet().iterator();
                    while(iterator.hasNext()) {
                        if(iterator.next().getValue().inFlight==null) { iterator.remove();break; }
                    }
                    if(entries.size()>=CAPACITY)throw new RejectedExecutionException("Analytics cache is busy");
                }
                entry=new Entry<>();entries.put(key,entry);
            }
            Instant now=clock.instant();
            // Limits are measured from transaction start, never extended by failed refreshes.
            serveStale=entry.value!=null && now.isBefore(entry.startedAt.plusSeconds(300));
            long refreshSeconds=entry.revision==entry.loadedRevision?120:30;
            if(serveStale && now.isBefore(entry.startedAt.plusSeconds(refreshSeconds)))return entry.value;
            if(entry.inFlight==null && now.isBefore(entry.retryAt)) {
                if(serveStale)return entry.value;
                throw entry.failure;
            }
            previous=entry.value;
            if(entry.inFlight==null) { entry.inFlight=new CompletableFuture<>();launch=true; }
            else if(!serveStale && !entry.loading)launch=true; // Take over a queued refresh at hard expiry.
            flight=entry.inFlight;
        }
        if(launch) {
            var target=entry;
            if(serveStale) {
                try { executor.execute(()->load(target,flight,loader)); }
                catch(RejectedExecutionException exception) {
                    synchronized(this) { if(!target.loading)fail(target,flight,exception); }
                }
            } else load(target,flight,loader);
        }
        if(serveStale)return previous;
        try { return flight.join(); }
        catch(CompletionException exception) {
            if(exception.getCause() instanceof RuntimeException failure)throw failure;
            throw exception;
        }
    }

    private void load(Entry<V> entry, CompletableFuture<V> flight, Supplier<V> loader) {
        Instant started=clock.instant();
        long revision;
        synchronized(this) {
            if(entry.inFlight!=flight || entry.loading)return;
            entry.loading=true;revision=entry.revision;
        }
        try {
            V value=loader.get();
            synchronized(this) {
                entry.value=value;entry.startedAt=started;entry.loadedRevision=revision;
                entry.retryAt=Instant.MIN;entry.failure=null;
                flight.complete(value);entry.inFlight=null;entry.loading=false;
            }
        } catch(RuntimeException exception) { fail(entry,flight,exception); }
    }

    private synchronized void fail(Entry<V> entry, CompletableFuture<V> flight, RuntimeException exception) {
        if(entry.inFlight!=flight)return;
        entry.failure=exception;entry.retryAt=clock.instant().plusSeconds(30);
        flight.completeExceptionally(exception);entry.inFlight=null;entry.loading=false;
        log.warn("Analytics snapshot refresh failed; retry after 30 seconds",exception);
    }
}
