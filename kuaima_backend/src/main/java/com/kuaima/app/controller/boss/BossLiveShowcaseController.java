package com.kuaima.app.controller.boss;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.common.Result;
import com.kuaima.app.domain.recruit.entity.LiveShowcase;
import com.kuaima.app.domain.recruit.repository.LiveShowcaseRepository;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.security.model.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;

/**
 * 老板端-直播橱窗（仅当前登录老板自己的数据）。
 */
@RestController
@RequestMapping("/boss")
@Tag(name = "老板-直播橱窗", description = "橱窗列表/新建/删除/状态")
public class BossLiveShowcaseController {

    private static final Set<String> ALLOWED_STATUS = Set.of("PENDING", "LIVE", "ENDED");
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final LiveShowcaseRepository showcaseRepository;

    public BossLiveShowcaseController(LiveShowcaseRepository showcaseRepository) {
        this.showcaseRepository = showcaseRepository;
    }

    @GetMapping("/live-showcases")
    @Operation(summary = "橱窗列表（当前老板）")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  Authentication authentication) {
        Long bossId = requireBossId(authentication);
        String st = StringUtils.hasText(status) && !"all".equalsIgnoreCase(status) ? status.trim().toUpperCase() : null;
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Page<LiveShowcase> result = showcaseRepository.search(st, bossId, kw,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "id")));
        return Result.success(result.getContent().stream().map(this::showcaseRow).collect(Collectors.toList()),
                page, result.getTotalElements());
    }

    @PostMapping("/live-showcases")
    @Operation(summary = "新建橱窗", description = "body: {title(必填), anchorName, douyinAccount}")
    public Result<Map<String, Object>> create(@RequestBody Map<String, String> body, Authentication authentication) {
        Long bossId = requireBossId(authentication);
        String title = trim(body.get("title"));
        if (title == null) {
            throw new IllegalArgumentException("请输入橱窗名称");
        }
        LiveShowcase s = new LiveShowcase();
        s.setBossId(bossId);
        s.setTitle(title);
        s.setAnchorName(trim(body.get("anchorName")));
        s.setDouyinAccount(trim(body.get("douyinAccount")));
        s.setStatus("PENDING");
        return Result.success(showcaseRow(showcaseRepository.save(s)));
    }

    @PutMapping("/live-showcases/{id}/status")
    @Operation(summary = "橱窗状态流转", description = "body: {status: PENDING|LIVE|ENDED}")
    public Result<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body,
                                                    Authentication authentication) {
        LiveShowcase s = requireOwned(id, authentication);
        String status = trim(body.get("status"));
        if (status == null || !ALLOWED_STATUS.contains(status.toUpperCase())) {
            throw new IllegalArgumentException("非法状态，仅支持 PENDING/LIVE/ENDED");
        }
        s.setStatus(status.toUpperCase());
        if ("LIVE".equals(s.getStatus()) && s.getLiveTime() == null) {
            s.setLiveTime(LocalDateTime.now());
        }
        return Result.success(showcaseRow(showcaseRepository.save(s)));
    }

    @DeleteMapping("/live-showcases/{id}")
    @Operation(summary = "删除橱窗")
    public Result<Void> delete(@PathVariable Long id, Authentication authentication) {
        showcaseRepository.delete(requireOwned(id, authentication));
        return Result.success();
    }

    /* ==================== 视图与工具 ==================== */

    private Map<String, Object> showcaseRow(LiveShowcase s) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", s.getId());
        row.put("name", s.getTitle());
        row.put("anchor", s.getAnchorName());
        row.put("account", s.getDouyinAccount());
        row.put("createTime", s.getLiveTime() != null ? s.getLiveTime().format(MINUTE)
                : (s.getTimestamp() != null ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(s.getTimestamp()) : null));
        row.put("status", s.getStatus());
        row.put("statusName", statusName(s.getStatus()));
        return row;
    }

    private String statusName(String status) {
        return switch (status == null ? "" : status) {
            case "LIVE" -> "直播中";
            case "ENDED" -> "已结束";
            default -> "待开播";
        };
    }

    private LiveShowcase requireOwned(Long id, Authentication authentication) {
        Long bossId = requireBossId(authentication);
        LiveShowcase s = showcaseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("橱窗不存在: " + id));
        if (!bossId.equals(s.getBossId())) {
            throw new ForbiddenBusinessException("无权操作该橱窗");
        }
        return s;
    }

    private Long requireBossId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser
                && UserRole.BOSS.equals(loginUser.role()) && loginUser.id() != null) {
            return loginUser.id();
        }
        throw new ForbiddenBusinessException("当前登录账号不是有效的老板账号");
    }

    private String trim(String v) {
        return v == null || v.trim().isEmpty() ? null : v.trim();
    }
}
