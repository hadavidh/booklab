package com.booklab.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String storageRoot;

    public WebConfig(@Value("${app.storage.root}") String storageRoot) {
        this.storageRoot = storageRoot;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path rootPath = Paths.get(storageRoot).toAbsolutePath().normalize();
        String location = rootPath.toUri().toString(); // file:///C:/.../storage/
        registry.addResourceHandler("/storage/**")
                .addResourceLocations(location);
    }
}