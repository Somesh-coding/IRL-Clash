package com.irlclash;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
@Configuration public class WebConfig implements WebMvcConfigurer {
 @Value("${app.allowed-origin:http://localhost:5173}") String origin;
 public void addCorsMappings(CorsRegistry r){r.addMapping("/api/**").allowedOrigins(origin).allowedMethods("GET","POST","OPTIONS").allowedHeaders("*");}
}
