package com.bjworld21.congress.service;

import java.util.Set;

public interface WebRiskClient {
    Set<String> findThreatTypes(String url);
}
