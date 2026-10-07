package com.onrender.zipai.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class BoardPageController {

    @GetMapping("/board/customer-center")
    public String customerCenter() {
        return "board/customer-center";
    }

    @GetMapping("/board/community")
    public String community() {
        return "board/community";
    }

    @GetMapping("/board/community-detail")
    public String communityDetail() {
        return "board/community-detail";
    }

    @GetMapping("/terms")
    public String terms() {
        return "common/terms";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin/admin";
    }

    @GetMapping("/portfolio/admin")
    public String portfolioAdmin() {
        return "admin/admin";
    }
}
