package com.onrender.zipai.dto.happyhousing.notice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class HousingNoticeMatchCheckResponse {
    private String type;
    private String state;
    private String message;
}
