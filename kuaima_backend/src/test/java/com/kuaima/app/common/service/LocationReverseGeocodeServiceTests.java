package com.kuaima.app.common.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.alibaba.fastjson2.JSONObject;

class LocationReverseGeocodeServiceTests {

    @Test
    void reverseGeocode_shouldReturnFormattedAddress() {
        LocationReverseGeocodeService service = new LocationReverseGeocodeService("test-key") {
            @Override
            protected JSONObject get(String url) {
                JSONObject response = new JSONObject();
                response.put("status", "1");
                JSONObject regeocode = new JSONObject();
                regeocode.put("formatted_address", "上海市松江区洞照路 88 号");
                response.put("regeocode", regeocode);
                return response;
            }
        };

        String address = service.reverseGeocode(
                new BigDecimal("31.032500"), new BigDecimal("121.223400"));

        assertEquals("上海市松江区洞照路 88 号", address);
    }

    @Test
    void reverseGeocode_shouldRejectInvalidCoordinate() {
        LocationReverseGeocodeService service = new LocationReverseGeocodeService("test-key");

        assertThrows(IllegalArgumentException.class,
                () -> service.reverseGeocode(new BigDecimal("91"), new BigDecimal("121")));
    }

    @Test
    void reverseGeocode_shouldRequireConfiguredKey() {
        LocationReverseGeocodeService service = new LocationReverseGeocodeService("");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.reverseGeocode(new BigDecimal("31"), new BigDecimal("121")));

        assertEquals("地图服务未配置，请设置 AMAP_WEB_SERVICE_KEY", exception.getMessage());
    }
}
