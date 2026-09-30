package com.onrender.zipai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private final SocialLoginSuccessHandler socialLoginSuccessHandler;

    public SecurityConfig(SocialLoginSuccessHandler socialLoginSuccessHandler) {
        this.socialLoginSuccessHandler = socialLoginSuccessHandler;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            )
            .oauth2Login(oauth -> oauth
                .loginPage("/member/login")
                .successHandler(socialLoginSuccessHandler)
                .failureUrl("/member/login?oauthError=failed")
            )
            .csrf(csrf -> csrf.disable());

        return http.build();
    }
}
