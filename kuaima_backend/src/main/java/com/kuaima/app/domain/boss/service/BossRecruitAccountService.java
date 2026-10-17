package com.kuaima.app.domain.boss.service;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.kuaima.app.common.BusinessHttpException;
import com.kuaima.app.domain.boss.entity.BossRecruitAccount;
import com.kuaima.app.domain.boss.model.BossProfileModels.AddedAccountView;
import com.kuaima.app.domain.boss.model.BossProfileModels.AddAccountRequest;
import com.kuaima.app.domain.boss.model.BossProfileModels.AddAccountResponse;
import com.kuaima.app.domain.boss.repository.BossRecruitAccountRepository;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.service.SmsService;

import jakarta.persistence.EntityNotFoundException;

@Service
public class BossRecruitAccountService {
    private static final String PHONE_PATTERN = "^1[3-9]\\d{9}$";

    private final BossRecruitAccountRepository accountRepository;
    private final UserRepository userRepository;
    private final SmsService smsService;
    private final PasswordEncoder passwordEncoder;

    public BossRecruitAccountService(BossRecruitAccountRepository accountRepository,
                                     UserRepository userRepository,
                                     SmsService smsService,
                                     PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.smsService = smsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AddAccountResponse add(Long ownerUserId, AddAccountRequest request) {
        validate(request);
        User owner = userRepository.findByIdForUpdate(ownerUserId)
                .orElseThrow(() -> new EntityNotFoundException("当前老板账号不存在"));
        if (!UserRole.isBossIdentity(owner)) {
            throw new BusinessHttpException(HttpStatus.FORBIDDEN, "当前登录账号不是老板账号");
        }
        String phone = request.phone().trim();
        if (phone.equals(owner.getPhone())) {
            throw new BusinessHttpException(HttpStatus.CONFLICT, "不能添加当前账号");
        }

        List<User> users = userRepository.findByPhone(phone);
        User target = users.stream().filter(user -> UserRole.BOSS.equals(user.getRole())).findFirst().orElse(null);
        if (target == null && !users.isEmpty()) {
            throw new BusinessHttpException(HttpStatus.FORBIDDEN, "该手机号已注册为零工账号，不能添加为老板账号");
        }
        if (target != null && target.getId().equals(ownerUserId)) {
            throw new BusinessHttpException(HttpStatus.CONFLICT, "不能添加当前账号");
        }
        if (target != null && accountRepository.existsByOwnerUserIdAndTargetUserId(ownerUserId, target.getId())) {
            throw new BusinessHttpException(HttpStatus.CONFLICT, "该账号已添加");
        }
        String smsCode = request.smsCode().trim();
        SmsService.VerifyResult verification = smsService.verifyCodeResult(phone, smsCode);
        if (verification == SmsService.VerifyResult.NOT_FOUND) {
            throw new BusinessHttpException(HttpStatus.NOT_FOUND, "短信验证码不存在");
        }
        if (verification == SmsService.VerifyResult.INVALID || verification == SmsService.VerifyResult.EXPIRED_OR_USED) {
            throw new BusinessHttpException(HttpStatus.UNPROCESSABLE_ENTITY, "验证码错误、已过期或已使用");
        }

        boolean newlyRegistered = target == null;
        if (newlyRegistered) {
            target = createBoss(phone);
            createDefaultAccount(target);
        }
        BossRecruitAccount binding = createBinding(ownerUserId, target);
        return toResponse(binding, target, newlyRegistered, currentAccountId(ownerUserId));
    }

    private void validate(AddAccountRequest request) {
        if (request == null || !StringUtils.hasText(request.phone())
                || !request.phone().trim().matches(PHONE_PATTERN)) {
            throw new IllegalArgumentException("手机号格式错误");
        }
        if (!StringUtils.hasText(request.smsCode())
                || !request.smsCode().trim().matches("\\d{6}")) {
            throw new IllegalArgumentException("短信验证码格式错误");
        }
        if (!Boolean.TRUE.equals(request.agreementAccepted())) {
            throw new IllegalArgumentException("未同意用户协议和隐私协议");
        }
    }

    private User createBoss(String phone) {
        User user = new User();
        // 用户名不落手机号，登录/绑定均以手机号查询，避免审计和日志间接暴露敏感信息。
        user.setUsername(uniqueUsername());
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setRole(UserRole.BOSS);
        user.setPhone(phone);
        user.setNickname("用户");
        user.setStatus("正常");
        return userRepository.saveAndFlush(user);
    }

    private String uniqueUsername() {
        return "boss_" + UUID.randomUUID().toString().replace("-", "");
    }

    private void createDefaultAccount(User target) {
        BossRecruitAccount account = new BossRecruitAccount();
        account.setOwnerUserId(target.getId());
        account.setTargetUserId(target.getId());
        account.setName(displayName(target));
        account.setAvatar(target.getAvatar());
        account.setAuthorizationType(authorizationType(target));
        account.setCurrent(true);
        accountRepository.save(account);
    }

    private BossRecruitAccount createBinding(Long ownerUserId, User target) {
        BossRecruitAccount account = new BossRecruitAccount();
        account.setOwnerUserId(ownerUserId);
        account.setTargetUserId(target.getId());
        account.setName(displayName(target));
        account.setAvatar(target.getAvatar());
        account.setAuthorizationType(authorizationType(target));
        account.setCurrent(false);
        try {
            return accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessHttpException(HttpStatus.CONFLICT, "该账号已添加");
        }
    }

    private AddAccountResponse toResponse(BossRecruitAccount account, User target, boolean newlyRegistered,
                                          Long currentAccountId) {
        AddedAccountView view = new AddedAccountView(account.getId(), account.getName(), account.getAvatar(),
                maskPhone(target.getPhone()), account.getAuthorizationType(), false, newlyRegistered);
        return new AddAccountResponse(view, currentAccountId);
    }

    private Long currentAccountId(Long ownerUserId) {
        return accountRepository.findByOwnerUserIdAndCurrentTrue(ownerUserId)
                .or(() -> accountRepository.findByOwnerUserIdAndTargetUserId(ownerUserId, ownerUserId))
                .map(BossRecruitAccount::getId)
                .orElseThrow(() -> new IllegalStateException("当前老板账号缺少可切换账号"));
    }

    private String displayName(User user) {
        if (StringUtils.hasText(user.getCompanyName())) return user.getCompanyName();
        if (StringUtils.hasText(user.getRealName())) return user.getRealName();
        if (StringUtils.hasText(user.getNickname())) return user.getNickname();
        return "用户";
    }

    private String authorizationType(User user) {
        return UserRole.hasApprovedEnterprise(user) ? "ENTERPRISE" : "PERSONAL";
    }

    public static String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) return "";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
