package com.onrender.zipai.web;

import com.onrender.zipai.domain.ZipaiUser;
import com.onrender.zipai.service.FraudDiagnosisService;
import com.onrender.zipai.service.ZipaiAuthService;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fraud-diagnoses")
public class FraudDiagnosisController {
    private final FraudDiagnosisService diagnoses;
    private final ZipaiAuthService auth;

    public FraudDiagnosisController(FraudDiagnosisService diagnoses, ZipaiAuthService auth) {
        this.diagnoses = diagnoses;
        this.auth = auth;
    }

    @GetMapping("/latest")
    public Map<String, Object> latest(HttpSession session) {
        ZipaiUser user = auth.required(session);
        return diagnoses.latest(user.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> save(@RequestBody Map<String, Object> body, HttpSession session) {
        ZipaiUser user = auth.required(session);
        return diagnoses.save(user.getId(), body);
    }
}
