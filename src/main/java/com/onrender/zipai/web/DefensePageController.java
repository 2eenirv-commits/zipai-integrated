package com.onrender.zipai.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DefensePageController {

    @GetMapping({"/defense/result", "/templates/defense/fraud_result.html"})
    public String fraudResult() {
        return "defense/fraud_result";
    }

    @GetMapping({"/defense/checklist", "/templates/defense/checklist.html"})
    public String checklist() {
        return "defense/checklist";
    }

    @GetMapping({"/defense/calculator", "/templates/defense/charter-rate-calculator.html"})
    public String calculator() {
        return "defense/charter-rate-calculator";
    }

    @GetMapping({"/defense/guide", "/templates/defense/contract-guide.html"})
    public String contractGuide() {
        return "defense/contract-guide";
    }
}
