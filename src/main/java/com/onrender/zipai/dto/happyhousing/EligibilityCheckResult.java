package com.onrender.zipai.dto.happyhousing;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EligibilityCheckResult {

    private String type;
    private String state;
    private String message;
}
