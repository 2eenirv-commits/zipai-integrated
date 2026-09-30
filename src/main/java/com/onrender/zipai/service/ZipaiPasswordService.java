package com.onrender.zipai.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ZipaiPasswordService {
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public String encode(String raw) {
        return encoder.encode(raw);
    }

    public boolean matches(String raw, String encoded) {
        return encoded != null && encoder.matches(raw, encoded);
    }
}
