package org.cottoevia;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/*
 * Package moved from org.cottoevia to org.cottoevia during the project rename.
 *
 * Removed unused imports that were present in the previous version:
 *   ServletContextInitializer, ServletListenerRegistrationBean,
 *   SessionAuthenticationStrategy, HttpSessionSecurityContextRepository,
 *   SecurityContextRepository, CookieCsrfTokenRepository,
 *   CsrfAuthenticationStrategy, CsrfTokenRepository,
 *   HttpSessionEventPublisher, EnableRedisHttpSession,
 *   EnableRedisIndexedHttpSession, HttpSessionListener
 * None of these were referenced anywhere in the class body — they were
 * either leftovers from an earlier draft or speculative additions that
 * were never wired in. Removing them does not change behaviour at all;
 * it only removes dead weight. If Redis-backed sessions are reintroduced
 * later, spring.session.store-type=redis in application.properties is
 * what activates that — these annotations are not required for it to work.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    /*
     * Intercepts every request and looks for the Authorization: Basic ... header
     * Decodes the Base64 string to extract username:password
     * Authenticates against the configured UserDetailsService
     * If valid, populates the SecurityContext with the authenticated user
     * If invalid or missing, returns 401 Unauthorized
     *
     * GET /catalog and /catalog.html are public — the menu is meant to be
     * browsable without logging in. Everything else still requires auth.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(configurer ->
                        configurer
                                .requestMatchers(HttpMethod.GET, "/catalog.html", "/catalog").permitAll()
                                .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                /*
                 * needed for frontend implementation otherwise
                 * each static page would need to embed an input type hidden containing
                 * the csrf token (synchronizer token pattern)
                 */
                // .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService inMemoryUserDetailsService() {
        UserDetails user1 = User.builder().username("bklvsc")
                .password(passwordEncoder().encode("1234")).build();

        UserDetails user2 = User.builder().username("giorgio")
                .password(passwordEncoder().encode("1234")).build();

        return new InMemoryUserDetailsManager(user1, user2);
    }
}
