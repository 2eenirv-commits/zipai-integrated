package com.onrender.zipai.dto.happyhousing;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HappyHousingDiagnoseResponse {

    private String result;
    private String title;

    private int passedCount;
    private int unknownCount;
    private int failedCount;

    private List<EligibilityCheckResult> checks;
}
