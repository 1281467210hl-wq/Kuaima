package com.kuaima.app.admin.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

import com.kuaima.app.admin.service.AdminRecruitService;
import com.kuaima.app.common.Result;
import com.kuaima.app.domain.recruit.entity.LiveShowcase;
import com.kuaima.app.domain.recruit.repository.LiveShowcaseRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;

/**
 * 后台-智能招聘/直播橱窗（平台全局视角）。
 */
@RestController
@RequestMapping("/admin/live-showcases")
@Tag(name = "后台-直播橱窗", description = "直播带岗橱窗的列表/新建/编辑/状态流转/删除")
public class AdminLiveShowcaseController {

    private static final Set<String> ALLOWED_STATUS = Set.of("PENDING", "LIVE", "ENDED");

    private final LiveShowcaseRepository showcaseRepository;
    private final AdminRecruitService recruitService;

    public AdminLiveShowcaseController(LiveShowcaseRepository showcaseRepository,
                                       AdminRecruitService recruitService) {
        this.showcaseRepository = showcaseRepository;
        this.recruitService = recruitService;
    }

    @GetMapping
    @Operation(summary = "橱窗列表", description = "keyword 匹配标题/主播/抖音号/老板名；status: PENDING/LIVE/ENDED；可按 bossId 过滤")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) Long bossId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        Page<LiveShowcase> showcases = showcaseRepository.search(
                blankToNullUpper(status), bossId, blankToNull(keyword),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "id")));
        List<Map<String, Object>> rows = showcases.getContent().stream()
                .map(recruitService::showcaseRow).toList();
        return Result.success(rows, page, showcases.getTotalElements());
    }

    @GetMapping("/{id}")
    @Operation(summary = "橱窗详情")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(recruitService.showcaseRow(requireShowcase(id)));
    }

    @PostMapping
    @Operation(summary = "新建橱窗", description = "body: {title(必填), anchorName, douyinAccount, bossId?, liveTime?, status?}；传 bossId 时自动补齐 bossName")
    public Result<Map<String, Object>> create(@RequestBody Map<String, String> body) {
        String title = body.get("title");
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("请填写直播标题");
        }
        LiveShowcase showcase = new LiveShowcase();
        showcase.setTitle(title.trim());
        showcase.setAnchorName(trimOrNull(body.get("anchorName")));
        showcase.setDouyinAccount(trimOrNull(body.get("douyinAccount")));
        applyBoss(showcase, body.get("bossId"), body.get("bossName"));
        showcase.setLiveTime(parseDateTime(body.get("liveTime")));
        String status = blankToNullUpper(body.get("status"));
        showcase.setStatus(status == null ? "PENDING" : requireStatus(status));
        return Result.success(recruitService.showcaseRow(showcaseRepository.save(showcase)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑橱窗", description = "body 同新建，均可选，仅覆盖传入字段")
    public Result<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        LiveShowcase showcase = requireShowcase(id);
        if (body.containsKey("title")) {
            String title = trimOrNull(body.get("title"));
            if (title == null) {
                throw new IllegalArgumentException("直播标题不能为空");
            }
            showcase.setTitle(title);
        }
        if (body.containsKey("anchorName")) {
            showcase.setAnchorName(trimOrNull(body.get("anchorName")));
        }
        if (body.containsKey("douyinAccount")) {
            showcase.setDouyinAccount(trimOrNull(body.get("douyinAccount")));
        }
        if (body.containsKey("liveTime")) {
            showcase.setLiveTime(parseDateTime(body.get("liveTime")));
        }
        if (body.containsKey("bossId") || body.containsKey("bossName")) {
            applyBoss(showcase, body.get("bossId"), body.get("bossName"));
        }
        if (body.containsKey("status")) {
            String status = blankToNullUpper(body.get("status"));
            if (status != null) {
                showcase.setStatus(requireStatus(status));
            }
        }
        return Result.success(recruitService.showcaseRow(showcaseRepository.save(showcase)));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "更新橱窗状态", description = "body: {status: PENDING|LIVE|ENDED}")
    public Result<Map<String, Object>> updateStatus(@PathVariable Long id,
                                                    @RequestBody Map<String, String> body) {
        LiveShowcase showcase = requireShowcase(id);
        String status = blankToNullUpper(body.get("status"));
        if (status == null) {
            throw new IllegalArgumentException("请传入状态 PENDING/LIVE/ENDED");
        }
        showcase.setStatus(requireStatus(status));
        return Result.success(recruitService.showcaseRow(showcaseRepository.save(showcase)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除橱窗")
    public Result<Void> delete(@PathVariable Long id) {
        showcaseRepository.delete(requireShowcase(id));
        return Result.success();
    }

    private void applyBoss(LiveShowcase showcase, String bossIdText, String bossNameText) {
        if (StringUtils.hasText(bossIdText)) {
            Long bossId;
            try {
                bossId = Long.valueOf(bossIdText.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("bossId 必须为数字: " + bossIdText);
            }
            showcase.setBossId(bossId);
            String name = recruitService.bossName(bossId);
            showcase.setBossName(StringUtils.hasText(name) ? name : trimOrNull(bossNameText));
        } else if (StringUtils.hasText(bossNameText)) {
            showcase.setBossName(bossNameText.trim());
        }
    }

    private LiveShowcase requireShowcase(Long id) {
        return showcaseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("直播橱窗不存在: " + id));
    }

    private String requireStatus(String status) {
        if (!ALLOWED_STATUS.contains(status)) {
            throw new IllegalArgumentException("非法状态，仅支持 PENDING/LIVE/ENDED");
        }
        return status;
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim().replace('T', ' ');
        try {
            if (text.length() <= 16) {
                return LocalDateTime.parse(text + ":00", AdminRecruitService.MINUTE_FORMAT);
            }
            return LocalDateTime.parse(text, AdminRecruitService.TIME_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("时间格式应为 yyyy-MM-dd HH:mm[:ss]: " + value);
        }
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private String blankToNullUpper(String s) {
        String value = blankToNull(s);
        return value == null || "all".equalsIgnoreCase(value) ? null : value.toUpperCase();
    }

    private String trimOrNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
