package dev.callseal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Everything that is not the authorization server.
 *
 * /v1/**   bank-side API, needs a bearer token from /oauth2/token
 * /demo/** customer-side endpoints for the demo bank-app page. DEMO ONLY: no customer
 *          authentication. A real deployment sits behind the bank app's own session.
 */
@Configuration
public class ApiSecurityConfig {

    @Bean
    @Order(2)
    SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/v1/call-intents").hasAuthority("SCOPE_calls.write")
                        .requestMatchers("/v1/**").hasAuthority("SCOPE_calls.read")
                        .requestMatchers(
                                "/demo/**",
                                "/", "/index.html", "/bank-app.html", "/voice-agent.html", "/js/**",
                                "/.well-known/callseal-jwks.json",
                                "/actuator/health", "/actuator/prometheus",
                                "/error").permitAll()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
