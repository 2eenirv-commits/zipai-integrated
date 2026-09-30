package com.onrender.zipai.dto.happyhousing;

import java.time.LocalDate;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class HappyHousingDiagnoseRequest {

    private String applicantType;
    private LocalDate birthDate;
    private LocalDate noticeDate;

    private String homeless;
    private String category;

    private Long monthlyIncome;
    private Long totalAssets;
    private Long carValue;

    private String connection;
}
