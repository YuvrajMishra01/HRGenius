package com.hrgenius.config;

import java.io.IOException;
import java.net.InetAddress;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Phase 22: the H2 console (dev profile only) grants full SQL access to the
 * database, so it must never be reachable from other machines. This guard
 * restricts /h2-console/* to loopback addresses; anything else gets a 404
 * (resource-hiding rather than confirming the console exists).
 *
 * Registered only when h2.console.enabled=true (the dev profile); the oracle
 * profile disables the console and therefore never registers this filter.
 */
@Configuration
public class H2ConsoleGuardConfig {

    private static boolean isLoopback(HttpServletRequest request) {
        try {
            InetAddress remote = InetAddress.getByName(request.getRemoteAddr());
            return remote.isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }

    @Bean
    @ConditionalOnProperty(name = "spring.h2.console.enabled", havingValue = "true")
    public FilterRegistrationBean<Filter> h2ConsoleLoopbackGuard() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter((request, response, chain) -> {
            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse res = (HttpServletResponse) response;
            if (!isLoopback(req)) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            chain.doFilter(request, response);
        });
        registration.addUrlPatterns("/h2-console/*");
        registration.setOrder(Integer.MIN_VALUE); // before everything, incl. security
        return registration;
    }
}
