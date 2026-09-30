package com.onrender.zipai.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CommonLayoutController {

    @GetMapping("/common/header.html")
    public String header() {
        return "common/header";
    }

    @GetMapping("/common/footer.html")
    public String footer() {
        return "common/footer";
    }
}
