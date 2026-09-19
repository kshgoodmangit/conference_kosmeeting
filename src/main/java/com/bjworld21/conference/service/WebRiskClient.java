package com.bjworld21.conference.service;

import java.util.Set;

public interface WebRiskClient {
    Set<String> findThreatTypes(String url);
}
