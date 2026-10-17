package com.kuaima.app.controller.user;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import com.kuaima.app.admin.entity.Certification;
import com.kuaima.app.admin.repository.CertificationRepository;
import com.kuaima.app.common.Result;
import com.kuaima.app.domain.user.entity.CreditFlow;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.CreditFlowRepository;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.service.CertificationService;
import com.kuaima.app.domain.user.service.CreditRuleNames;
import com.kuaima.app.security.model.LoginUser;
import com.kuaima.app.common.ForbiddenBusinessException;

import jakarta.persistence.EntityNotFoundException;

@RestController
@RequestMapping("/user")
@Tag(name = "用户中心", description = "用户资料、身份认证、信用分")
public class UserController {

    private final UserRepository userRepository;
    private final CreditFlowRepository creditFlowRepository;
    private final CertificationRepository certificationRepository;
    private final CertificationService certificationService;

    public UserController(UserRepository userRepository,
                          CreditFlowRepository creditFlowRepository,
                          CertificationRepository certificationRepository,
                          CertificationService certificationService) {
        this.userRepository = userRepository;
        this.creditFlowRepository = creditFlowRepository;
        this.certificationRepository = certificationRepository;
        this.certificationService = certificationService;
    }

    /** 获取用户完整资料：GET /user/{id} */
    @Operation(summary = "获取用户资料", description = "返回 User 实体完整信息（含 nickname、avatar、phone、certStatus、creditScore、companyName 等）")
    @GetMapping("/{id}")
    public Result<User> getProfile(@PathVariable Long id) {
        return Result.success(getUserOrThrow(id));
    }

    /**
     * 修改个人资料：PUT /user/{id}
     * 仅更新传入的非空字段（nickname, avatar, phone, email, age, gender, city, skills, remark）
     */
    @Operation(summary = "修改用户资料", description = "仅更新传入的非空字段（nickname, avatar, phone, email, age, gender, city, skills, remark）")
    @PutMapping("/{id}")
    @Transactional
    public Result<User> updateProfile(@PathVariable Long id, @RequestBody User body) {
        User user = getUserOrThrow(id);
        if (body == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (StringUtils.hasText(body.getNickname())) {
            user.setNickname(body.getNickname());
        }
        if (StringUtils.hasText(body.getAvatar())) {
            user.setAvatar(body.getAvatar());
        }
        if (StringUtils.hasText(body.getPhone())) {
            user.setPhone(body.getPhone());
        }
        if (StringUtils.hasText(body.getEmail())) {
            user.setEmail(body.getEmail());
        }
        if (body.getAge() != null) {
            user.setAge(body.getAge());
        }
        if (StringUtils.hasText(body.getGender())) {
            user.setGender(body.getGender());
        }
        if (StringUtils.hasText(body.getCity())) {
            user.setCity(body.getCity());
        }
        if (StringUtils.hasText(body.getSkills())) {
            user.setSkills(body.getSkills());
        }
        if (StringUtils.hasText(body.getRemark())) {
            user.setRemark(body.getRemark());
        }
        return Result.success(userRepository.save(user));
    }

    /**
     * 提交实名认证：POST /user/{id}/realname
     * body: { "realName": "张三", "idCard": "110101199001011234" }
     * 设置 certStatus=待审核, certType=REALNAME, 并落库 realName / idCard
     */
    @Operation(summary = "提交实名认证", description = "请求体含 realName 和 idCard。设置 User.certType=REALNAME、certStatus=待审核，并写入 Certification 审核记录")
    @PostMapping("/{id}/realname")
    @Transactional
    public Result<User> submitRealname(@PathVariable Long id, @RequestBody Map<String, String> body,
                                       Authentication authentication) {
        if (body == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String realName = body.get("realName");
        String idCard = body.get("idCard");
        if (!(authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser)
                || loginUser.id() == null || !id.equals(loginUser.id())) {
            throw new ForbiddenBusinessException("只能提交当前登录用户的实名认证");
        }
        return Result.success(certificationService.submitRealname(id, realName, idCard));
    }

    /**
     * 认证审核记录：GET /user/{id}/certifications
     * 查询 CertificationRepository，返回该用户的认证审核轨迹
     */
    @Operation(summary = "认证审核历史", description = "查询 CertificationRepository，返回该用户的认证审核记录列表（按 id 倒序）")
    @GetMapping("/{id}/certifications")
    public Result<List<Certification>> listCertifications(@PathVariable Long id) {
        // 校验用户存在
        getUserOrThrow(id);
        return Result.success(certificationRepository.findByUserIdOrderByIdDesc(id));
    }

    /**
     * 信用分概览：GET /user/{id}/credit
     * 返回 creditScore + 最近 10 条信用流水
     */
    @Operation(summary = "信用分与近期流水", description = "返回 creditScore 和最近 10 条 CreditFlow 信用流水")
    @GetMapping("/{id}/credit")
    public Result<Map<String, Object>> getCredit(@PathVariable Long id) {
        User user = getUserOrThrow(id);
        boolean boss = UserRole.isBossIdentity(user);
        String scoreType = boss ? "BOSS_CREDIT" : "WORKER_STAR";
        List<CreditFlow> recentFlows = creditFlowRepository.findByUserIdAndScoreTypeOrderByTimestampDesc(id, scoreType)
                .stream().limit(10).toList();
        Map<String, Object> data = new HashMap<>();
        int score = boss ? (user.getCreditScore() == null ? 0 : user.getCreditScore())
                : (user.getStarScore() == null ? 0 : user.getStarScore());
        data.put("score", score);
        data.put("creditScore", boss ? score : user.getCreditScore());
        data.put("starScore", boss ? user.getStarScore() : score);
        data.put("scoreType", scoreType);
        data.put("recentFlows", recentFlows.stream().map(this::creditFlowView).toList());
        data.put("details", recentFlows.stream().map(flow -> Map.of(
                "id", flow.getId(),
                "title", flow.getReason() == null ? "信用分变动" : flow.getReason(),
                "time", flow.getTimestamp(),
                "value", flow.getDelta()
        )).toList());
        return Result.success(data);
    }

    /**
     * 信用流水分页查询：GET /user/{id}/credit/flows?page=0&size=10
     */
    @Operation(summary = "信用流水分页查询", description = "分页返回 CreditFlow 列表（userId、delta、reason、bizType、timestamp），按 id 倒序")
    @GetMapping("/{id}/credit/flows")
    public Result<List<Map<String, Object>>> listCreditFlows(@PathVariable Long id,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        // 校验用户存在
        getUserOrThrow(id);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id"));
        Page<CreditFlow> result = creditFlowRepository.findByUserIdOrderByTimestampDesc(id, pageable);
        return Result.success(result.getContent().stream().map(this::creditFlowView).toList(),
                result.getNumber(), result.getTotalElements());
    }

    private Map<String, Object> creditFlowView(CreditFlow flow) {
        Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("id", flow.getId());
        view.put("bizNo", flow.getBizNo());
        view.put("userId", flow.getUserId());
        view.put("delta", flow.getDelta());
        view.put("reason", flow.getReason());
        view.put("bizType", flow.getBizType());
        view.put("scoreType", flow.getScoreType());
        view.put("beforeScore", flow.getBeforeScore());
        view.put("afterScore", flow.getAfterScore());
        view.put("ruleCode", CreditRuleNames.display(flow.getRuleCode(), flow.getDelta()));
        view.put("timestamp", flow.getTimestamp());
        return view;
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在: " + id));
    }
}
