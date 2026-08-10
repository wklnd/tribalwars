package com.twlan.backend.web;

import com.twlan.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthService authService;

    public WebConfig(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins("http://localhost:5173").allowedHeaders("*").allowedMethods("*");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
                if ("OPTIONS".equals(request.getMethod())) {
                    return true;
                }
                String world = request.getHeader("X-World-Id");
                if (world != null && !world.isBlank()) {
                    try {
                        WorldContext.set(Long.parseLong(world.trim()));
                    } catch (NumberFormatException e) {
                        WorldContext.clear();
                    }
                }
                VillageContext.clear();
                String village = request.getHeader("X-Village-Id");
                if (village != null && !village.isBlank()) {
                    try {
                        VillageContext.set(Long.parseLong(village.trim()));
                    } catch (NumberFormatException ignored) {
                        // no such village: the first one is used
                    }
                }
                String auth = request.getHeader("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) {
                    authService.resolve(auth.substring(7).trim()).ifPresent(AccountContext::set);
                }
                if (AccountContext.get() == null && !isPublic(request)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Please log in.\"}");
                    return false;
                }
                if (request.getRequestURI().startsWith("/api/admin") && !AccountContext.get().isAdmin()) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Admins only.\"}");
                    return false;
                }
                return true;
            }

            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
                WorldContext.clear();
                VillageContext.clear();
                AccountContext.clear();
            }
        }).addPathPatterns("/api/**");
    }

    // Register, login and the world list: the start page shows these before anyone is logged in.
    private static boolean isPublic(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        return (path.equals("/api/auth/register") || path.equals("/api/auth/login")) && "POST".equals(method)
                || path.equals("/api/worlds") && "GET".equals(method);
    }
}
