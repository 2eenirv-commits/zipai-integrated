package com.onrender.zipai.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class SafetyPageController {

    @GetMapping("/safe/safety")
    public String safety() {
        return "safe/safety";
    }

    @GetMapping("/safe/public-safety")
    public String publicSafety() {
        return "safe/public_safety";
    }

    @GetMapping("/safe/risk")
    public RedirectView risk() {
        return new RedirectView("/safe/public-safety");
    }

    @GetMapping({"/safe/map", "/safe/safety-map"})
    public RedirectView safetyMap() {
        return new RedirectView("/safe/safety#safety-map-section");
    }

    @GetMapping({"/safe/search", "/safe/safety-search"})
    public RedirectView safetySearch() {
        return new RedirectView("/safe/safety#safety-search");
    }
}
