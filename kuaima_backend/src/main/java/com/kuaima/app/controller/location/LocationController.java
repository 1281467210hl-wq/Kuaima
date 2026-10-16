package com.kuaima.app.controller.location;

import java.math.BigDecimal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.common.Result;
import com.kuaima.app.common.service.LocationReverseGeocodeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/location")
@RequiredArgsConstructor
@Tag(name = "位置服务", description = "坐标解析与位置服务")
public class LocationController {

    private final LocationReverseGeocodeService reverseGeocodeService;

    @GetMapping("/reverse-geocode")
    @Operation(summary = "坐标转具体地址", description = "输入 GCJ-02 经纬度，返回逆地理编码后的具体地址")
    public Result<String> reverseGeocode(
            @RequestParam BigDecimal latitude,
            @RequestParam BigDecimal longitude) {
        return Result.success(reverseGeocodeService.reverseGeocode(latitude, longitude));
    }
}
