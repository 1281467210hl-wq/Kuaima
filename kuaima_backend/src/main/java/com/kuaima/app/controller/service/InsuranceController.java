package com.kuaima.app.controller.service;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.common.Result;
import com.kuaima.app.domain.insurance.entity.Insurance;
import com.kuaima.app.domain.insurance.entity.InsuranceProduct;
import com.kuaima.app.domain.insurance.repository.InsuranceProductRepository;
import com.kuaima.app.domain.insurance.repository.InsuranceRepository;

import jakarta.persistence.EntityNotFoundException;

/**
 * 保险服务。
 */
@RestController
@RequestMapping("/insurance")
@Tag(name = "保险", description = "用工保险管理")
public class InsuranceController {

    private final InsuranceRepository insuranceRepository;
    private final InsuranceProductRepository insuranceProductRepository;

    public InsuranceController(InsuranceRepository insuranceRepository,
                               InsuranceProductRepository insuranceProductRepository) {
        this.insuranceRepository = insuranceRepository;
        this.insuranceProductRepository = insuranceProductRepository;
    }

    /** 可选保险产品：GET /insurance/products */
    @Operation(summary = "可选保险产品", description = "返回后台已启用的保险产品，按 id 倒序；tiers 解析为档位数组，不含告知书附件地址")
    @GetMapping("/products")
    public Result<List<JSONObject>> listProducts() {
        return Result.success(insuranceProductRepository.findByEnabledTrueOrderByIdDesc().stream()
                .map(this::productView)
                .toList());
    }

    /** 保险记录列表：GET /insurance?userId=1 */
    @Operation(summary = "保险记录列表", description = "按用户 id 查询保险记录，按 id 倒序；userId 必填")
    @GetMapping
    public Result<List<Insurance>> listInsurance(@RequestParam Long userId) {
        return Result.success(insuranceRepository.findByUserIdOrderByIdDesc(userId));
    }

    /** 购买保险：POST /insurance */
    @Operation(summary = "购买保险", description = "body：{\"userId\":1,\"userType\":\"WORKER\",\"orderId\":1,\"type\":\"意外险\",\"amount\":100,\"premium\":10}；字段均可选，缺省 userType=WORKER、type=意外险、amount/premium=0；创建即生效：status=ACTIVE，startTime=当前时间")
    @PostMapping
    @Transactional
    public Result<Insurance> buyInsurance(@RequestBody Map<String, Object> body) {
        Insurance insurance = new Insurance();
        insurance.setUserId(body.get("userId") != null ? Long.valueOf(body.get("userId").toString()) : null);
        insurance.setUserType(body.get("userType") != null ? body.get("userType").toString() : "WORKER");
        insurance.setOrderId(body.get("orderId") != null ? Long.valueOf(body.get("orderId").toString()) : null);
        insurance.setType(body.get("type") != null ? body.get("type").toString() : "意外险");
        insurance.setAmount(body.get("amount") != null ? Double.valueOf(body.get("amount").toString()) : 0.0);
        insurance.setPremium(body.get("premium") != null ? Double.valueOf(body.get("premium").toString()) : 0.0);
        insurance.setStatus("ACTIVE");
        insurance.setStartTime(new Timestamp(System.currentTimeMillis()));
        return Result.success(insuranceRepository.save(insurance));
    }

    /** 保险详情：GET /insurance/{id} */
    @Operation(summary = "保险详情", description = "按 id 查询保险记录；不存在抛出 EntityNotFoundException")
    @GetMapping("/{id}")
    public Result<Insurance> getInsurance(@PathVariable Long id) {
        return Result.success(insuranceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("保险记录不存在: " + id)));
    }

    private JSONObject productView(InsuranceProduct product) {
        JSONObject obj = new JSONObject();
        obj.put("id", product.getId());
        obj.put("carrier", product.getCarrier());
        obj.put("code", product.getCode());
        JSONArray tiers = new JSONArray();
        for (Object item : parseTiers(product.getTiers())) {
            if (!(item instanceof JSONObject tier)) continue;
            JSONObject view = new JSONObject();
            // 告知书存放在私有 Bucket，客户端只拿方案数据，不暴露对象地址
            for (String key : List.of("amount", "amountText", "death", "deathText", "medical", "medicalText",
                    "points", "pointsText", "notice")) {
                view.put(key, tier.get(key));
            }
            tiers.add(view);
        }
        obj.put("tiers", tiers);
        return obj;
    }

    private JSONArray parseTiers(String tiers) {
        if (tiers == null || tiers.isBlank()) return new JSONArray();
        try {
            return JSON.parseArray(tiers);
        } catch (RuntimeException e) {
            return new JSONArray();
        }
    }
}
