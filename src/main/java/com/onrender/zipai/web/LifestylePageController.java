package com.onrender.zipai.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LifestylePageController {

    @GetMapping({
            "/ai/lifestyle-analysis",
            "/ai/lifestyle-analysis.html",
            "/templates/ai/lifestyle-analysis.html",
            "/lifestyle-analysis"
    })
    public String lifestyleAnalysis() {
        return "ai/lifestyle-analysis";
    }
}
