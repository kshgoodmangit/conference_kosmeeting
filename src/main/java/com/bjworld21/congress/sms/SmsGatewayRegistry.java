package com.bjworld21.congress.sms;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SmsGatewayRegistry {
    private final Map<String, SmsGatewayAdapter> adapters = new HashMap<>();
    private final SmsGatewayProperties properties;
    public SmsGatewayRegistry(List<SmsGatewayAdapter> adapters, SmsGatewayProperties properties) {
        this.properties = properties;
        for (SmsGatewayAdapter adapter : adapters) {
            if (adapter.providerKey() == null || adapter.providerKey().isBlank()
                    || this.adapters.putIfAbsent(adapter.providerKey(), adapter) != null) {
                throw new IllegalStateException("문자 발송 공급자 코드가 없거나 중복되었습니다.");
            }
        }
    }
    public SmsGatewayAdapter requireEnabledAdapter() {
        if (!properties.isEnabled()) throw new IllegalStateException("실제 문자 발송이 비활성화되어 있습니다.");
        SmsGatewayAdapter adapter = adapters.get(properties.getProvider());
        if (adapter == null) throw new IllegalStateException("문자 발송 공급자가 연결되지 않았습니다.");
        return adapter;
    }
}
