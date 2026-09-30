package com.onrender.zipai.dto.happyhousing.notice;

import java.time.LocalDate;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class HousingNoticeMatchRequest {
    private String applicantType;
    private LocalDate birthDate;
    private String homeless;
    private String category;
    private Long monthlyIncome;
    private Integer householdSize;
    private Long totalAssets;
    private Long carValue;
    private String connection;
}
