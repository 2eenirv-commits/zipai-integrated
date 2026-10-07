package com.onrender.zipai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private final SocialLoginSuccessHandler socialLoginSuccessHandler;
    private final GuestReadOnlyFilter guestReadOnlyFilter;

    public SecurityConfig(SocialLoginSuccessHandler socialLoginSuccessHandler,
                          GuestReadOnlyFilter guestReadOnlyFilter) {
        this.socialLoginSuccessHandler = socialLoginSuccessHandler;
        this.guestReadOnlyFilter = guestReadOnlyFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/**").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/**").permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                .anyRequest().denyAll()
            )
            .oauth2Login(oauth -> oauth
                .loginPage("/member/login")
                .successHandler(socialLoginSuccessHandler)
                .failureUrl("/member/login?oauthError=failed")
            )
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            .addFilterBefore(guestReadOnlyFilter, AuthorizationFilter.class);

        return http.build();
    }
}
