package com.kuaima.app.common.service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.alibaba.fastjson2.JSONObject;

@Service
public class LocationReverseGeocodeService {
    private static final String REGEO_URL = "https://restapi.amap.com/v3/geocode/regeo";

    private final String key;
    private final HttpClient httpClient;

    public LocationReverseGeocodeService(
            @Value("${location.amap.web-service-key:}") String key) {
        this.key = key;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public String reverseGeocode(BigDecimal latitude, BigDecimal longitude) {
        validate(latitude, longitude);
        if (!StringUtils.hasText(key)) {
            throw new IllegalStateException("地图服务未配置，请设置 AMAP_WEB_SERVICE_KEY");
        }

        String location = longitude.stripTrailingZeros().toPlainString()
                + "," + latitude.stripTrailingZeros().toPlainString();
        String url = REGEO_URL
                + "?location=" + enc(location)
                + "&extensions=base"
                + "&radius=0"
                + "&key=" + enc(key);
        JSONObject response = get(url);
        if (!"1".equals(response.getString("status"))) {
            throw new IllegalStateException("位置解析失败，请稍后重试");
        }
        JSONObject regeocode = response.getJSONObject("regeocode");
        String address = regeocode == null ? null : regeocode.getString("formatted_address");
        if (!StringUtils.hasText(address)) {
            throw new IllegalStateException("位置解析失败，请稍后重试");
        }
        return address;
    }

    private void validate(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null
                || latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException("经纬度不合法");
        }
    }

    protected JSONObject get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return JSONObject.parseObject(response.body());
        } catch (Exception exception) {
            throw new IllegalStateException("位置解析失败，请稍后重试", exception);
        }
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
