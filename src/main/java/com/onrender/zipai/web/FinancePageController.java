package com.onrender.zipai.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FinancePageController {

    @GetMapping("/board/finance-policy")
    public String financePolicy() {
        return "board/finance-policy";
    }

    @GetMapping("/board/trend1")
    public String financeTrend() {
        return "board/trend1";
    }

    @GetMapping("/board/trend2")
    public String financeLoanFinder() {
        return "board/trend2";
    }

    @GetMapping("/board/trend3")
    public String financeCalculator() {
        return "board/trend3";
    }

    @GetMapping("/board/trend4")
    public String financeGuide() {
        return "board/trend4";
    }
}
