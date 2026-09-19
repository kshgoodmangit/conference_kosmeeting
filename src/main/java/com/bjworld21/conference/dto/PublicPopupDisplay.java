package com.bjworld21.conference.dto;

import java.util.List;

public record PublicPopupDisplay(int layoutNo, List<Item> items) {
    public record Item(Long seq, String title, String contentHtml, String imageUrl, String linkUrl) { }
}
