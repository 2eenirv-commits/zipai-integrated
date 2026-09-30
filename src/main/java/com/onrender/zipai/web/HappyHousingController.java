package com.onrender.zipai.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.onrender.zipai.dto.happyhousing.HappyHousingDiagnoseRequest;
import com.onrender.zipai.dto.happyhousing.HappyHousingDiagnoseResponse;
import com.onrender.zipai.dto.happyhousing.HappyHousingRuleResponse;
import com.onrender.zipai.service.HappyHousingService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class HappyHousingController {

    private final HappyHousingService happyHousingService;

    @GetMapping("/board/happy-housing")
    public String happyHousing() {
        return "board/happy-housing";
    }

    @GetMapping("/api/happy-housing/rules/current")
    @ResponseBody
    public HappyHousingRuleResponse currentRule(
            @RequestParam String applicantType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate noticeDate) {

        return happyHousingService.getRule(applicantType, noticeDate);
    }

    @PostMapping("/api/happy-housing/diagnose")
    @ResponseBody
    public HappyHousingDiagnoseResponse diagnose(
            @RequestBody HappyHousingDiagnoseRequest request) {

        return happyHousingService.diagnose(request);
    }
}
