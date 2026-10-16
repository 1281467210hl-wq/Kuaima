package com.kuaima.app.domain.wallet.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kuaima.app.domain.message.constant.BizType;
import com.kuaima.app.domain.message.constant.MessageType;
import com.kuaima.app.domain.message.service.MessageService;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.wallet.constant.WithDrawStatus;
import com.kuaima.app.domain.wallet.entity.Wallet;
import com.kuaima.app.domain.wallet.entity.WalletFlow;
import com.kuaima.app.domain.wallet.entity.WithDraw;
import com.kuaima.app.domain.wallet.repository.WalletFlowRespository;
import com.kuaima.app.domain.wallet.repository.WalletRespository;
import com.kuaima.app.domain.wallet.repository.WithDrawRespository;

import jakarta.persistence.EntityNotFoundException;

/**
 * 钱包账务服务：余额出入账 + 流水记录 + 提现申请/模拟打款。
 * 金额一律以元(BigDecimal，两位小数)存储；只有支付渠道边界转换为分。
 */
@Service
public class WalletService {

    public static final String DIR_INCOME = "income";
    public static final String DIR_OUTCOME = "outcome";
    public static final String BIZ_WAGE = "WAGE";
    public static final String BIZ_WITHDRAW = "WITHDRAW";
    public static final String BIZ_WITHDRAW_REFUND = "WITHDRAW_REFUND";
    /** 提现渠道占位：真实接入后替换为微信商家转账 */
    public static final String CHANNEL_MOCK = "mock";
    public static final String CHANNEL_WECHAT = "WECHAT";

    private final WalletRespository walletRepository;
    private final WalletFlowRespository flowRepository;
    private final WithDrawRespository withdrawRepository;
    private final MessageService messageService;

    public WalletService(WalletRespository walletRepository,
                         WalletFlowRespository flowRepository,
                         WithDrawRespository withdrawRepository,
                         MessageService messageService) {
        this.walletRepository = walletRepository;
        this.flowRepository = flowRepository;
        this.withdrawRepository = withdrawRepository;
        this.messageService = messageService;
    }

    /** 查询钱包，不存在则创建空钱包（余额 0） */
    @Transactional
    public Wallet getOrCreateWallet(Long userId) {
        return getOrCreateWallet(userId, UserRole.USER);
    }

    public Wallet getOrCreateWallet(Long userId, String role) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        return walletRepository.findFirstByUserIdAndRoleOrderByIdDesc(userId, role).orElseGet(() -> {
            Wallet wallet = new Wallet();
            wallet.setUserId(userId);
            wallet.setRole(role);
            wallet.setBalance(BigDecimal.ZERO);
            return walletRepository.save(wallet);
        });
    }

    /** 查询钱包（只读，不自动创建） */
    public Wallet getWallet(Long userId) {
        return walletRepository.findFirstByUserIdAndRoleOrderByIdDesc(userId, UserRole.USER)
                .orElseThrow(() -> new EntityNotFoundException("钱包不存在: " + userId));
    }

    /**
     * 钱包入账并记录流水。
     *
     * @param amount 入账金额(分)，必须大于 0
     */
    @Transactional
    public Wallet credit(Long userId, BigDecimal amount, String bizType, Long bizId, String remark) {
        amount = normalize(amount);
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("入账金额必须大于 0");
        }
        Wallet wallet = getOrCreateWallet(userId);
        wallet.setBalance(value(wallet.getBalance()).add(amount));
        walletRepository.save(wallet);
        saveFlow(userId, DIR_INCOME, bizType, amount, wallet.getBalance(), bizId, remark);
        return wallet;
    }

    /** 兼容旧调用方：传入的 Long 按元解释。 */
    public Wallet credit(Long userId, long amount, String bizType, Long bizId, String remark) {
        return credit(userId, BigDecimal.valueOf(amount), bizType, bizId, remark);
    }

    /**
     * 申请提现：校验余额后立即扣减，生成"申请中"提现单（模拟待打款）。
     */
    @Transactional
    public WithDraw applyWithdraw(Long userId, BigDecimal amount, String account, String remark) {
        amount = normalize(amount);
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("提现金额必须大于 0");
        }
        Wallet wallet = getOrCreateWallet(userId);
        if (value(wallet.getBalance()).compareTo(amount) < 0) {
            throw new IllegalStateException("余额不足，当前可用余额(元): " + wallet.getBalance());
        }
        wallet.setBalance(value(wallet.getBalance()).subtract(amount));
        walletRepository.save(wallet);

        WithDraw draw = new WithDraw();
        draw.setUserId(userId);
        draw.setAmount(amount);
        draw.setStatus(WithDrawStatus.PENDING);
        draw.setChannel(CHANNEL_MOCK);
        draw.setAccount(account);
        draw.setRemark(remark);
        draw.setApplyTime(LocalDateTime.now());
        withdrawRepository.save(draw);

        saveFlow(userId, DIR_OUTCOME, BIZ_WITHDRAW, amount, wallet.getBalance(), draw.getId(), "提现申请");
        return draw;
    }

    public WithDraw applyWithdraw(Long userId, long amount, String account, String remark) {
        return applyWithdraw(userId, BigDecimal.valueOf(amount), account, remark);
    }

    public Optional<WithDraw> findByIdempotencyKey(String key) {
        return withdrawRepository.findByIdempotencyKey(key);
    }

    /** 微信提现扣款事务：锁钱包、扣余额、建提现单和流水必须同时完成。 */
    @Transactional
    public WithDraw submitWechatWithdraw(Long userId, BigDecimal amount, String openid, String key) {
        Optional<WithDraw> old = withdrawRepository.findByIdempotencyKey(key);
        if (old.isPresent()) return old.get();
        amount = normalize(amount);
        List<Wallet> lockedWallets = walletRepository.findAllByUserIdAndRoleForUpdate(userId, UserRole.USER);
        Wallet wallet = lockedWallets.isEmpty()
                ? walletRepository.findFirstByUserIdAndRoleOrderByIdDesc(userId, UserRole.USER).orElseGet(() -> {
            Wallet created = new Wallet(); created.setUserId(userId); created.setRole(UserRole.USER); created.setBalance(BigDecimal.ZERO);
            return walletRepository.saveAndFlush(created);
        }) : lockedWallets.get(0);
        if (value(wallet.getBalance()).compareTo(amount) < 0) throw new IllegalArgumentException("钱包可提现余额不足");
        wallet.setBalance(value(wallet.getBalance()).subtract(amount)); walletRepository.saveAndFlush(wallet);
        String suffix = System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        WithDraw draw = new WithDraw(); draw.setUserId(userId); draw.setRole(UserRole.USER); draw.setAmount(amount);
        draw.setStatus(WithDrawStatus.PENDING); draw.setChannel(CHANNEL_WECHAT); draw.setAccount(openid);
        draw.setIdempotencyKey(key); draw.setMerchantBatchNo("WWB" + suffix); draw.setMerchantDetailNo("WWD" + suffix);
        draw.setApplyTime(LocalDateTime.now()); draw = withdrawRepository.saveAndFlush(draw);
        saveFlow(userId, DIR_OUTCOME, BIZ_WITHDRAW, amount, wallet.getBalance(), draw.getId(), "提现至微信零钱");
        return draw;
    }

    @Transactional
    public WithDraw acceptWechatWithdraw(Long id, String transferNo, String response) {
        WithDraw draw = lockedWithdraw(id); draw.setWechatTransferNo(transferNo); draw.setTransferResponse(response);
        return withdrawRepository.save(draw);
    }

    @Transactional
    public WithDraw succeedWechatWithdraw(Long id, String transferNo, String response) {
        WithDraw draw = lockedWithdraw(id);
        if (WithDrawStatus.SUCCESS.equals(draw.getStatus())) return draw;
        if (!WithDrawStatus.PENDING.equals(draw.getStatus())) throw new IllegalStateException("提现单状态不能标记成功");
        draw.setStatus(WithDrawStatus.SUCCESS); draw.setWechatTransferNo(transferNo); draw.setTransferResponse(response);
        draw.setPayTime(LocalDateTime.now()); return withdrawRepository.save(draw);
    }

    @Transactional
    public WithDraw failWechatWithdraw(Long id, String reason, String transferNo, String response) {
        WithDraw draw = lockedWithdraw(id);
        if (WithDrawStatus.FAILED.equals(draw.getStatus())) return draw;
        if (!WithDrawStatus.PENDING.equals(draw.getStatus())) throw new IllegalStateException("提现单状态不能标记失败");
        List<Wallet> lockedWallets = walletRepository.findAllByUserIdAndRoleForUpdate(draw.getUserId(), draw.getRole());
        Wallet wallet = lockedWallets.isEmpty()
                ? walletRepository.findFirstByUserIdAndRoleOrderByIdDesc(draw.getUserId(), draw.getRole())
                        .orElseThrow(() -> new IllegalStateException("钱包不存在"))
                : lockedWallets.get(0);
        wallet.setBalance(value(wallet.getBalance()).add(draw.getAmount())); walletRepository.saveAndFlush(wallet);
        draw.setStatus(WithDrawStatus.FAILED); draw.setRemark(reason); draw.setWechatTransferNo(transferNo);
        draw.setTransferResponse(response); withdrawRepository.saveAndFlush(draw);
        saveFlow(draw.getUserId(), DIR_INCOME, BIZ_WITHDRAW_REFUND, draw.getAmount(), wallet.getBalance(), draw.getId(), "微信提现失败退回");
        return draw;
    }

    /** 模拟打款成功：申请中 -> 已打款（真实渠道：微信商家转账到零工账户） */
    @Transactional
    public WithDraw mockPayout(Long withdrawId) {
        WithDraw draw = getWithdrawOrThrow(withdrawId);
        if (!WithDrawStatus.PENDING.equals(draw.getStatus())) {
            throw new IllegalStateException("仅申请中的提现单可以打款");
        }
        draw.setStatus(WithDrawStatus.SUCCESS);
        draw.setPayTime(LocalDateTime.now());
        return withdrawRepository.save(draw);
    }

    /** 模拟打款失败：申请中 -> 打款失败，并退回余额到钱包 */
    @Transactional
    public WithDraw mockPayoutFail(Long withdrawId, String reason) {
        WithDraw draw = getWithdrawOrThrow(withdrawId);
        if (!WithDrawStatus.PENDING.equals(draw.getStatus())) {
            throw new IllegalStateException("仅申请中的提现单可以标记失败");
        }
        draw.setStatus(WithDrawStatus.FAILED);
        draw.setRemark(reason);
        withdrawRepository.save(draw);

        Wallet wallet = getOrCreateWallet(draw.getUserId());
        wallet.setBalance(value(wallet.getBalance()).add(draw.getAmount()));
        walletRepository.save(wallet);
        saveFlow(draw.getUserId(), DIR_INCOME, BIZ_WITHDRAW_REFUND, draw.getAmount(),
                wallet.getBalance(), draw.getId(), "提现失败退回");
        // 打款失败：通知零工"已退回"
        messageService.sendToUser(draw.getUserId(), UserRole.USER, MessageType.WITHDRAW_FAIL, "提现打款失败",
                "您申请的提现 " + draw.getAmount().stripTrailingZeros().toPlainString() + " 元打款失败"
                        + (reason == null || reason.isBlank() ? "" : "（" + reason + "）")
                        + "，金额已退回钱包。",
                BizType.WITHDRAW, draw.getId());
        return draw;
    }

    /** 用户提现单列表 */
    public List<WithDraw> listWithdraws(Long userId) {
        return withdrawRepository.findByUserIdAndRoleOrderByIdDesc(userId, UserRole.USER);
    }

    /** 用户钱包流水 */
    public List<WalletFlow> listFlows(Long userId) {
        return flowRepository.findByUserIdAndRoleOrderByIdDesc(userId, UserRole.USER);
    }

    // ==================== 内部方法 ====================

    private void saveFlow(Long userId, String direction, String bizType,
                          BigDecimal amount, BigDecimal balanceAfter, Long bizId, String remark) {
        WalletFlow flow = new WalletFlow();
        flow.setUserId(userId);
        flow.setRole(UserRole.USER);
        flow.setDirection(direction);
        flow.setBizType(bizType);
        flow.setAmount(amount);
        flow.setBalanceAfter(balanceAfter);
        flow.setBizId(bizId);
        flow.setRemark(remark);
        flowRepository.save(flow);
    }

    private WithDraw getWithdrawOrThrow(Long id) {
        return withdrawRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("提现单不存在: " + id));
    }

    private WithDraw lockedWithdraw(Long id) {
        return withdrawRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("提现单不存在: " + id));
    }

    private BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal normalize(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }
}
