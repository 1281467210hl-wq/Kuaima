package com.kuaima.app.admin.controller;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.common.Result;
import com.kuaima.app.admin.entity.Agreement;
import com.kuaima.app.admin.repository.AgreementRepository;
import com.kuaima.app.common.service.OssStorageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/public/agreements")
@RequiredArgsConstructor
@Tag(name = "公开协议", description = "登录前读取后台配置的启用协议")
public class PublicAgreementController {
    private final AgreementRepository agreements;
    private final OssStorageService storage;

    @GetMapping
    @Operation(summary = "按类型查询最新启用协议", description = "type 需使用后台协议类型，如“用户服务协议”“隐私协议”；未配置时 data 为 null")
    public Result<AgreementView> latest(@RequestParam String type) {
        Optional<Agreement> result = agreements
                .findFirstByTypeAndEnabledOrderByUpdatedAtDescIdDesc(type, true);
        return Result.success(result.map(this::view).orElse(null));
    }

    private AgreementView view(Agreement agreement) {
        return new AgreementView(
                agreement.getId(),
                agreement.getName(),
                agreement.getType(),
                agreement.getContentMode(),
                agreement.getContent(),
                agreement.getFileName(),
                agreement.getFileObjectKey() == null ? null : storage.inlinePreviewUrl(agreement.getFileObjectKey()),
                agreement.getUpdatedAt());
    }

    public record AgreementView(
            Long id,
            String name,
            String type,
            String contentMode,
            String content,
            String fileName,
            String fileUrl,
            LocalDateTime updatedAt) {
    }
}
