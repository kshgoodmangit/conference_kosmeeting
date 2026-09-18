package com.bjworld21.congress.analytics;

import com.maxmind.db.Reader;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.InetAddress;
import java.nio.file.*;

@Component
public class AnalyticsCountryResolver {
    private final String path;
    private Reader reader;
    private long lastCheck;
    private long modified;
    public AnalyticsCountryResolver(@Value("${app.analytics.country-database:}") String path) {this.path=path;}
    public synchronized String country(String ip) {
        try {
            if(path.isBlank())return null;
            if(System.currentTimeMillis()-lastCheck>60000) {
                lastCheck=System.currentTimeMillis();
                Path file=Path.of(path);
                long next=Files.getLastModifiedTime(file).toMillis();
                if(reader==null || next!=modified) {
                    Reader replacement=new Reader(file.toFile(),Reader.FileMode.MEMORY);
                    if(reader!=null)reader.close();
                    reader=replacement;modified=next;
                }
            }
            InetAddress address=InetAddress.getByName(ip);
            if(address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress())return null;
            if(reader==null)return null;
            var row=reader.get(address,java.util.Map.class);
            if(row!=null && row.get("country") instanceof java.util.Map<?,?> country
                    && country.get("iso_code") instanceof String code && code.matches("[A-Z]{2}"))return code;
        } catch(Exception ignored) {
            // Missing DB or unmapped/private IPs remain UNKNOWN; collection must still work.
        }
        return null;
    }
    @PreDestroy public synchronized void close() throws java.io.IOException {if(reader!=null)reader.close();}
}
