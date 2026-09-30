package com.onrender.zipai.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MemberPageController {

    @GetMapping("/member/signup")
    public String signupPage() {
        return "member/signup";
    }

    @GetMapping("/member/login")
    public String loginPage() {
        return "member/login";
    }

    @GetMapping("/member/social-profile")
    public String socialProfilePage() {
        return "member/social-profile";
    }

    @GetMapping("/member/mypage")
    public String mypage() {
        return "member/mypage";
    }
}
