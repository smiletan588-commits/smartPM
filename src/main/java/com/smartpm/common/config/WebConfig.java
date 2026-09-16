package com.smartpm.common.config;

import com.smartpm.common.interceptor.LoginInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.Arrays;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;

    @Value("${smartpm.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 全局 CORS — 允许前端跨域访问。
     * 注意: allowCredentials=true 时不能与 origins="*" 同时使用，
     * 故用 allowedOriginPatterns 匹配所有来源。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(parseAllowedOrigins())
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/user/register", "/api/user/login");
    }

    /**
     * SPA 路由回退 — Vue Router History 模式需要。
     * 对非 API、非静态资源（无文件后缀）的 GET 请求，若静态目录中无对应文件，
     * 统一返回 index.html，让 Vue Router 接管前端路由。
     *
     * 规则：
     *  - /api/** 被 Controller 优先匹配，不会进入此处理器
     *  - /assets/xxx.js 等有后缀的请求，static 目录中有实体文件则直接返回
     *  - /dashboard、/project/123 无后缀且无实体文件 → 回退到 index.html
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource resource = location.createRelative(resourcePath);
                        if (resource.exists() && resource.isReadable()) {
                            return resource;
                        }
                        // 非 API 路径且不是静态资源（无文件后缀），回退到 index.html
                        if (!resourcePath.startsWith("api/") && !resourcePath.contains(".")) {
                            Resource index = location.createRelative("index.html");
                            if (index.exists() && index.isReadable()) {
                                return index;
                            }
                        }
                        return null;
                    }
                });
    }

    private String[] parseAllowedOrigins() {
        return Arrays.stream(allowedOrigins.split(",")).map(String::trim)
                .filter(value -> !value.isEmpty()).toArray(String[]::new);
    }
}
