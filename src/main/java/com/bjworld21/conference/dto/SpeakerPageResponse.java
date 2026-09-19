package com.bjworld21.conference.dto;

import java.util.List;

public record SpeakerPageResponse(List<SpeakerResponse> items, int page, int size, long totalCount,
                                  int totalPages, long enabledCount, long featuredCount) {
}
