package com.kuaima.app.common.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.kuaima.app.admin.interceptor.AdminLogInterceptor;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${kuaima.upload.dir:./uploads/}")
    private String uploadDir;

    @Autowired
    private AdminLogInterceptor adminLogInterceptor;

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 将 fastjson2 置于最前，优先用于业务接口的 JSON 请求/响应转换
        // Fastjson2HttpMessageConverter.supports() 已排除 io.swagger.v3.* / org.springdoc.* 包
        // 这些对象会自动回退到默认的 Jackson converter 处理
        converters.add(0, new Fastjson2HttpMessageConverter());
    }

    /** CORS：允许 admin-web(5173) 和移动端 H5 跨域调用 */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /** 静态资源映射：/uploads/** → 本地上传目录（绝对路径，避免依赖 JVM 工作目录） */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = java.nio.file.Path.of(uploadDir).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) location = location + "/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }

    /** 注册操作日志拦截器，覆盖所有 /admin/** 接口 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminLogInterceptor)
                .addPathPatterns("/admin/**");
    }
}
