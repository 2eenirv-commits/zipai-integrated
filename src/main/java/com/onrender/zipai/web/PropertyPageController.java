package com.onrender.zipai.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PropertyPageController {
    @GetMapping("/properties/favorites")
    public String favorites() { return "property/favorites"; }

    @GetMapping("/properties/register")
    public String register() { return "property/register"; }
}
