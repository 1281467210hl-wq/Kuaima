package com.kuaima.app.domain.boss.service;

import java.sql.Date;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.domain.boss.constant.BossStatus;
import com.kuaima.app.domain.boss.constant.BossType;
import com.kuaima.app.domain.boss.entity.BaseOrderItem;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.entity.BossAddress;
import com.kuaima.app.domain.boss.model.BossOrderQuery;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.boss.repository.BossOrderSpecifications;
import com.kuaima.app.domain.boss.repository.BossAddressRepository;
import com.kuaima.app.domain.boss.repository.BossRecruitSettingsRepository;
import com.kuaima.app.domain.boss.entity.BossRecruitSettings;
import com.kuaima.app.domain.message.constant.BizType;
import com.kuaima.app.domain.message.constant.MessageType;
import com.kuaima.app.domain.message.service.MessageService;
import com.kuaima.app.domain.user.constant.CertificationStatus;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.user.service.CertificationService;
import com.kuaima.app.domain.user.service.CreditScoreService;
import com.kuaima.app.domain.wallet.entity.Settlement;
import com.kuaima.app.domain.wallet.constant.SettlementStatus;
import com.kuaima.app.domain.wallet.repository.SettlementRespository;
import com.kuaima.app.domain.coupon.service.BossCouponService;
import com.kuaima.app.admin.entity.AdminSetting;
import com.kuaima.app.admin.repository.AdminSettingRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
public class BossOrderService {

    private final BossOrderRespository orderRepository;
    private final BaseOrderItemRespository itemRepository;
    private final UserRepository userRepository;
    private final MessageService messageService;
    private final SettlementRespository settlementRespository;
    private final CertificationService certificationService;
    private final BossRecruitSettingsRepository recruitSettingsRepository;
    private final BossCouponService bossCouponService;
    private CreditScoreService creditScoreService;
    private final AdminSettingRepository adminSettingRepository;
    private final RecruitRuleSettings recruitRules;
    private final BossAddressRepository bossAddressRepository;

    public BossOrderService(BossOrderRespository orderRepository,
                            BaseOrderItemRespository itemRepository,
                            UserRepository userRepository,
                            MessageService messageService,
                            SettlementRespository settlementRespository,
                            CertificationService certificationService,
                            BossRecruitSettingsRepository recruitSettingsRepository,
                            BossCouponService bossCouponService) {
        this(orderRepository, itemRepository, userRepository, messageService, settlementRespository,
                certificationService, recruitSettingsRepository, bossCouponService, null);
    }

    public BossOrderService(BossOrderRespository orderRepository,
                            BaseOrderItemRespository itemRepository,
                            UserRepository userRepository,
                            MessageService messageService,
                            SettlementRespository settlementRespository,
                            CertificationService certificationService,
                            BossRecruitSettingsRepository recruitSettingsRepository,
                            BossCouponService bossCouponService,
                            AdminSettingRepository adminSettingRepository) {
        this(orderRepository, itemRepository, userRepository, messageService, settlementRespository,
                certificationService, recruitSettingsRepository, bossCouponService, adminSettingRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public BossOrderService(BossOrderRespository orderRepository,
                            BaseOrderItemRespository itemRepository,
                            UserRepository userRepository,
                            MessageService messageService,
                            SettlementRespository settlementRespository,
                            CertificationService certificationService,
                            BossRecruitSettingsRepository recruitSettingsRepository,
                            BossCouponService bossCouponService,
                            AdminSettingRepository adminSettingRepository,
                            BossAddressRepository bossAddressRepository) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
        this.messageService = messageService;
        this.settlementRespository = settlementRespository;
        this.certificationService = certificationService;
        this.recruitSettingsRepository = recruitSettingsRepository;
        this.bossCouponService = bossCouponService;
        this.adminSettingRepository = adminSettingRepository;
        this.recruitRules = new RecruitRuleSettings(adminSettingRepository);
        this.bossAddressRepository = bossAddressRepository;
    }

    public BossOrderService(BossOrderRespository orderRepository,
                            BaseOrderItemRespository itemRepository,
                            UserRepository userRepository,
                            MessageService messageService,
                            SettlementRespository settlementRespository) {
        this(orderRepository, itemRepository, userRepository, messageService,
                settlementRespository, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setCreditScoreService(CreditScoreService creditScoreService) {
        this.creditScoreService = creditScoreService;
    }

    // ==================== 订单管理 ====================

    /** 发布招工订单，初始状态为"待审核"，admin 审核通过后变为"招工中" */
    @Transactional
    public BossOrder createOrder(BossOrder order) {
        if (!StringUtils.hasText(order.getOrderTitle())) {
            throw new IllegalArgumentException("订单标题不能为空");
        }
        validateRecruitmentMedia(order);
        applyGlobalRecruitDefaults(order);
        if (!StringUtils.hasText(order.getType())) throw new IllegalArgumentException("招工类型不能为空");
        if (!BossType.isValid(order.getType())) {
            throw new IllegalArgumentException("招工类型不合法: " + order.getType());
        }
        if (!StringUtils.hasText(order.getPostion())) {
            throw new IllegalArgumentException("招聘岗位不能为空");
        }
        if (order.getOrderNum() == null || order.getOrderNum() <= 0) {
            throw new IllegalArgumentException("招工人数必须大于 0");
        }
        if (order.getDuration() == null || order.getDuration() <= 0) {
            throw new IllegalArgumentException("工作时长必须大于 0");
        }
        if (order.getSalary() == null || order.getSalary().signum() <= 0) {
            throw new IllegalArgumentException("工资必须大于 0");
        }
        if (order.getAddressId() != null) {
            applySavedAddress(order, order.getAddressId());
        }
        validateOrderCoordinatesRequired(order.getLongitude(), order.getLatitude());
        checkTimeRange(order.getStartTime(), order.getEndTime());
        validateRecruitRules(order);
        normalizeRestSettings(order);
        applyRecruitSettings(order, order);
        // 试工时间仅月结类型有效
        if (!BossType.MONTH.equals(order.getType())) {
            order.setTrialDuration(null);
        }
        assignBusinessOrderNo(order);
        // 后台关闭「招工审核」时，发布即上架，等同于审核通过
        boolean auditRequired = recruitRules.jobAuditEnabled();
        order.setOrderStatus(auditRequired ? BossStatus.ORDER_PENDING_AUDIT : BossStatus.ORDER_RECRUITING);
        BossOrder saved = orderRepository.save(order);
        if (bossCouponService != null) bossCouponService.redeem(saved.getCreateBy(), order.getUserCouponId(), saved);
        if (!auditRequired) broadcastPublished(saved);
        return saved;
    }

    /** 生成按日递增的对外订单号，例如 DD202408210001。 */
    private synchronized void assignBusinessOrderNo(BossOrder order) {
        if (order.getOrderNo() != null && !order.getOrderNo().isBlank()) return;
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String prefix = "DD" + date;
        int next = orderRepository.findTopByOrderNoStartingWithOrderByOrderNoDesc(prefix)
                .map(existing -> {
                    String value = existing.getOrderNo();
                    try { return Integer.parseInt(value.substring(prefix.length())) + 1; }
                    catch (RuntimeException ignored) { return 1; }
                }).orElse(1);
        order.setOrderNo(prefix + String.format("%04d", next));
    }

    /** 招工详情媒体只允许保存上传接口返回的 objectKey，禁止提交签名 URL 或任意路径。 */
    private void validateRecruitmentMedia(BossOrder order) {
        List<String> images = java.util.Arrays.stream(String.valueOf(order.getImageKeys() == null ? "" : order.getImageKeys()).split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        if (images.size() > 9) throw new IllegalArgumentException("招工详情图片最多上传9张");
        if (images.stream().anyMatch(key -> !key.startsWith("insurance/recruitment/"))) {
            throw new IllegalArgumentException("招工详情图片地址无效");
        }
        if (StringUtils.hasText(order.getVideoKey())
                && !order.getVideoKey().startsWith("insurance/recruitment/")) {
            throw new IllegalArgumentException("招工详情视频地址无效");
        }
        order.setImageKeys(images.isEmpty() ? null : String.join(",", images));
    }

    /** admin 审核通过：订单从"待审核"变为"招工中"，并发岗位广播 */
    @Transactional
    public BossOrder auditPass(Long id) {
        BossOrder order = getOrderOrThrow(id);
        if (!BossStatus.ORDER_PENDING_AUDIT.equals(order.getOrderStatus())
                && !BossStatus.ORDER_AUDIT_REJECT.equals(order.getOrderStatus())) {
            throw new IllegalStateException("仅待审核或审核拒绝的订单可以重新审核通过");
        }
        order.setOrderStatus(BossStatus.ORDER_RECRUITING);
        BossOrder saved = orderRepository.save(order);
        broadcastPublished(saved);
        return saved;
    }

    /** 岗位上架（审核通过或免审核发布）后，广播给全部员工。 */
    private void broadcastPublished(BossOrder saved) {
        messageService.broadcastToUsers(MessageType.ORDER_PUBLISH, "新岗位发布",
                jobSummary(saved) + "，快来报名吧！", BizType.ORDER, saved.getId(),
                java.util.Map.of("orderTitle", String.valueOf(saved.getOrderTitle()), "position", String.valueOf(saved.getPostion()), "salary", String.valueOf(saved.getSalary())));
    }

    /** 后台「招工规则」：折算日薪不低于最低日薪；开工日到最后工作日（含首尾）不超过最长有效期。 */
    private void validateRecruitRules(BossOrder order) {
        recruitRules.minDailyWage().ifPresent(min -> {
            if (order.getSalary() != null && order.getSalary().compareTo(min) < 0) {
                throw new IllegalArgumentException("日薪不能低于平台最低标准 " + min.stripTrailingZeros().toPlainString() + " 元");
            }
        });
        recruitRules.maxJobValidityDays().ifPresent(maxDays -> {
            long days = workDays(order.getStartTime(), order.getEndTime());
            if (days > maxDays) {
                throw new IllegalArgumentException("招工有效期不能超过 " + maxDays + " 天");
            }
        });
    }

    /** 跨夜班收工时间落在次日，不额外计入一个工作日。 */
    private long workDays(java.util.Date start, java.util.Date end) {
        if (start == null || end == null) return 0;
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Shanghai");
        java.time.LocalDateTime from = start.toInstant().atZone(zone).toLocalDateTime();
        java.time.LocalDateTime to = end.toInstant().atZone(zone).toLocalDateTime();
        LocalDate lastDay = to.toLocalTime().isAfter(from.toLocalTime()) ? to.toLocalDate() : to.toLocalDate().minusDays(1);
        if (lastDay.isBefore(from.toLocalDate())) lastDay = from.toLocalDate();
        return java.time.temporal.ChronoUnit.DAYS.between(from.toLocalDate(), lastDay) + 1;
    }

    /** admin 审核拒绝 */
    @Transactional
    public BossOrder auditReject(Long id, String reason) {
        BossOrder order = getOrderOrThrow(id);
        if (!BossStatus.ORDER_PENDING_AUDIT.equals(order.getOrderStatus())
                && !BossStatus.ORDER_AUDIT_REJECT.equals(order.getOrderStatus())) {
            throw new IllegalStateException("仅待审核或审核拒绝的订单可以审核拒绝");
        }
        order.setOrderStatus(BossStatus.ORDER_AUDIT_REJECT);
        order.setOrderRemark(StringUtils.hasText(reason) ? ("[审核拒绝] " + reason) : order.getOrderRemark());
        return orderRepository.save(order);
    }

    /** 修改订单，仅"待审核"或"招工中"状态可修改；传入字段非空才会更新 */
    @Transactional
    public BossOrder updateOrder(Long id, BossOrder update) {
        BossOrder order = getOrderOrThrow(id);
        if (order.getEnterpriseId() == null && update.getEnterpriseId() != null) {
            order.setEnterpriseId(update.getEnterpriseId());
        }
        if (!BossStatus.ORDER_RECRUITING.equals(order.getOrderStatus())
                && !BossStatus.ORDER_PENDING_AUDIT.equals(order.getOrderStatus())) {
            throw new IllegalStateException("仅待审核或招工中的订单可以修改");
        }
        if (StringUtils.hasText(update.getOrderTitle())) {
            order.setOrderTitle(update.getOrderTitle());
        }
        if (StringUtils.hasText(update.getType())) {
            if (!BossType.isValid(update.getType())) {
                throw new IllegalArgumentException("招工类型不合法: " + update.getType());
            }
            order.setType(update.getType());
            // 改为非月结时清空试工时间
            if (!BossType.MONTH.equals(update.getType())) {
                order.setTrialDuration(null);
            }
        }
        if (StringUtils.hasText(update.getPostion())) {
            order.setPostion(update.getPostion());
        }
        if (StringUtils.hasText(update.getOrderContent())) {
            order.setOrderContent(update.getOrderContent());
        }
        if (StringUtils.hasText(update.getOrderRemark())) {
            order.setOrderRemark(update.getOrderRemark());
        }
        boolean locationChanged = update.getAddressId() != null || StringUtils.hasText(update.getAddress());
        if (update.getAddressId() != null) {
            applySavedAddress(order, update.getAddressId());
        } else if (StringUtils.hasText(update.getAddress())) {
            if (update.getLongitude() == null || update.getLatitude() == null) {
                throw new IllegalArgumentException("修改工作地址时必须同时提供经度和纬度");
            }
            order.setAddress(update.getAddress());
        }
        if (StringUtils.hasText(update.getTags())) {
            order.setTags(update.getTags());
        }
        if (update.getImageKeys() != null) {
            order.setImageKeys(update.getImageKeys());
        }
        if (update.getVideoKey() != null) {
            order.setVideoKey(update.getVideoKey());
        }
        validateRecruitmentMedia(order);
        applyFilterFields(order, update);
        applyRecruitSettings(order, update);
        if (update.getOrderNum() != null) {
            if (update.getOrderNum() <= 0) {
                throw new IllegalArgumentException("招工人数必须大于 0");
            }
            order.setOrderNum(update.getOrderNum());
        }
        if (update.getDuration() != null) {
            if (update.getDuration() <= 0) {
                throw new IllegalArgumentException("工作时长必须大于 0");
            }
            order.setDuration(update.getDuration());
        }
        if (update.getSalary() != null) {
            if (update.getSalary() == null || update.getSalary().signum() <= 0) {
                throw new IllegalArgumentException("工资必须大于 0");
            }
            order.setSalary(update.getSalary());
        }
        if (update.getStartTime() != null) {
            order.setStartTime(update.getStartTime());
        }
        if (update.getEndTime() != null) {
            order.setEndTime(update.getEndTime());
        }
        if (update.getRestMode() != null) order.setRestMode(update.getRestMode());
        if (update.getRestSlots() != null) order.setRestSlots(update.getRestSlots());
        if (update.getRestMinutes() != null) order.setRestMinutes(update.getRestMinutes());
        if (update.getRestCheckin() != null) order.setRestCheckin(update.getRestCheckin());
        if (update.getRestCheckinCount() != null) order.setRestCheckinCount(update.getRestCheckinCount());
        normalizeRestSettings(order);
        // 试工时间：仅月结订单可设置
        if (BossType.MONTH.equals(order.getType()) && StringUtils.hasText(update.getTrialDuration())) {
            order.setTrialDuration(update.getTrialDuration());
        }
        if (locationChanged) {
            validateOrderCoordinatesRequired(order.getLongitude(), order.getLatitude());
        } else {
            validateOrderCoordinates(order.getLongitude(), order.getLatitude());
        }
        checkTimeRange(order.getStartTime(), order.getEndTime());
        validateRecruitRules(order);
        BossOrder saved = orderRepository.save(order);
        if (update.getOrderNum() != null) {
            reconcileOrderAfterHeadcountChange(saved);
        }
        return saved;
    }

    private void normalizeRestSettings(BossOrder order) {
        if (!"rest".equalsIgnoreCase(order.getRestMode())) {
            order.setRestMode("none");
            order.setRestSlots("[]");
            order.setRestMinutes(0);
            order.setRestCheckin(false);
            order.setRestCheckinCount(0);
            return;
        }
        if (!StringUtils.hasText(order.getRestSlots())) order.setRestSlots("[]");
        if (order.getRestMinutes() == null || order.getRestMinutes() < 0) order.setRestMinutes(0);
        if (order.getRestCheckin() == null) order.setRestCheckin(false);
        if (order.getRestCheckinCount() == null || order.getRestCheckinCount() < 0) order.setRestCheckinCount(0);
    }

    /** 订单详情 */
    public BossOrder getOrder(Long id) {
        BossOrder order = getOrderOrThrow(id);
        normalizeRecruitSettings(order);
        return order;
    }

    /**
     * 订单列表分页查询，可按类型/状态/标题过滤，按 id 倒序（最新在前）。
     * bossId 非空时仅返回该老板创建的岗位；为空时保留零工端公开列表的原有查询行为。
     */
    public Page<BossOrder> listOrders(Long bossId, BossOrderQuery query) {
        validateListQuery(query);
        // 页码从 0 开始，size 限制在 [1, 100]
        int safePage = Math.max(query.getPage(), 0);
        int safeSize = Math.min(Math.max(query.getSize(), 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id"));
        Page<BossOrder> result = orderRepository.findAll(BossOrderSpecifications.from(bossId, query), pageable);
        fillCurrentApply(result.getContent());
        result.getContent().forEach(this::normalizeRecruitSettings);
        return result;
    }

    /** 企业维度订单列表；createBy 仅作为操作人，不再作为数据租户边界。 */
    public Page<BossOrder> listOrdersByEnterprise(Long enterpriseId, BossOrderQuery query) {
        if (enterpriseId == null) throw new IllegalArgumentException("enterpriseId 不能为空");
        BossOrderQuery safe = query == null ? new BossOrderQuery() : query;
        int page = safe.getPage();
        int size = safe.getSize();
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page 或 size 参数无效");
        Page<BossOrder> result = orderRepository.findAll(BossOrderSpecifications.fromEnterprise(enterpriseId, safe),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        fillCurrentApply(result.getContent());
        result.getContent().forEach(this::normalizeRecruitSettings);
        return result;
    }

    /** 兼容现有服务层调用。 */
    public Page<BossOrder> listOrders(Long bossId, String type, String status, String title, int page, int size) {
        BossOrderQuery query = new BossOrderQuery();
        query.setType(type);
        query.setStatus(status);
        query.setTitle(title);
        query.setPage(page);
        query.setSize(size);
        return listOrders(bossId, query);
    }

    /** 删除订单，仅"待审核"/"审核拒绝"/"招工中"/"取消招工"状态可删除 */
    @Transactional
    public void deleteOrder(Long id) {
        BossOrder order = getOrderOrThrow(id);
        String s = order.getOrderStatus();
        if (!BossStatus.ORDER_PENDING_AUDIT.equals(s)
                && !BossStatus.ORDER_AUDIT_REJECT.equals(s)
                && !BossStatus.ORDER_RECRUITING.equals(s)
                && !BossStatus.ORDER_CANCELED.equals(s)) {
            throw new IllegalStateException("该状态下的订单不可删除");
        }
        orderRepository.delete(order);
    }

    /**
     * 订单状态流转：
     * 招工中 -> 招工结束 -> 工作中 -> 待结算 -> 已完成；任意未完成状态 -> 取消招工
     */
    @Transactional
    public BossOrder changeOrderStatus(Long id, String targetStatus) {
        BossOrder order = getOrderOrThrow(id);
        String current = order.getOrderStatus();

        if (BossStatus.ORDER_WORKING.equals(current)
                && BossStatus.ORDER_PENDING_SETTLE.equals(targetStatus)
                && earlyLeaveCodeEnabled(order)) {
            throw new IllegalArgumentException("已开启早退码，请由零工输入早退码后完成工作");
        }

        // 兼容修复前已进入“待结算”但尚未生成结算单的历史订单；重复请求保持幂等并补齐结算单。
        if (BossStatus.ORDER_PENDING_SETTLE.equals(current)
                && BossStatus.ORDER_PENDING_SETTLE.equals(targetStatus)) {
            markHiredItemsOnWork(order);
            createPendingSettlements(order);
            return order;
        }

        boolean validTransition = switch (current) {
            case BossStatus.ORDER_PENDING_AUDIT ->
                    targetStatus.equals(BossStatus.ORDER_CANCELED);
            case BossStatus.ORDER_RECRUITING ->
                    targetStatus.equals(BossStatus.ORDER_RECRUIT_END)
                            || targetStatus.equals(BossStatus.ORDER_WORKING)
                            || targetStatus.equals(BossStatus.ORDER_CANCELED);
            case BossStatus.ORDER_RECRUIT_END ->
                    targetStatus.equals(BossStatus.ORDER_WORKING)
                            || targetStatus.equals(BossStatus.ORDER_PENDING_SETTLE)
                            || targetStatus.equals(BossStatus.ORDER_CANCELED);
            case BossStatus.ORDER_WORKING ->
                    targetStatus.equals(BossStatus.ORDER_PENDING_SETTLE) || targetStatus.equals(BossStatus.ORDER_CANCELED);
            case BossStatus.ORDER_PENDING_SETTLE ->
                    targetStatus.equals(BossStatus.ORDER_COMPLETED) || targetStatus.equals(BossStatus.ORDER_CANCELED);
            default -> false;
        };
        if (!validTransition) {
            throw new IllegalStateException("不允许从「" + current + "」流转到「" + targetStatus + "」");
        }

        order.setOrderStatus(targetStatus);

        // 老板主动取消招工：按距离开工时间扣除老板信用分，且每个订单只执行一次。
        if (BossStatus.ORDER_CANCELED.equals(targetStatus) && creditScoreService != null
                && order.getCreateBy() != null && order.getStartTime() != null) {
            long hours = java.time.Duration.between(java.time.LocalDateTime.now(),
                    order.getStartTime().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()).toHours();
            int delta = hours <= 1 ? -10 : hours <= 4 ? -5 : hours <= 16 ? -2 : 0;
            if (delta != 0) {
                creditScoreService.adjust(order.getCreateBy(), CreditScoreService.BOSS_CREDIT, delta,
                        "BOSS_CANCEL_ORDER", "ORDER", "BOSS_CANCEL_ORDER:" + order.getId(),
                        "老板主动取消招工");
            }
        }

        // 取消招工：把该订单下所有未完成的报名记录置为"取消招工"，并收集受影响用户以便通知
        List<Long> canceledUserIds = new ArrayList<>();
        if (BossStatus.ORDER_CANCELED.equals(targetStatus)) {
            List<BaseOrderItem> items = itemRepository.findByOrderId(id);
            for (BaseOrderItem item : items) {
                if (!BossStatus.ITEM_FINISHED.equals(item.getStatus())) {
                    item.setStatus(BossStatus.ITEM_CANCEL_BY_BOSS);
                    item.setCancelDate(Date.valueOf(LocalDate.now()));
                    canceledUserIds.add(item.getUserId());
                }
            }
            itemRepository.saveAll(items);
        }
        BossOrder saved = orderRepository.save(order);
        if (BossStatus.ORDER_WORKING.equals(targetStatus)) {
            markHiredItemsOnWork(saved);
        }
        if (BossStatus.ORDER_PENDING_SETTLE.equals(targetStatus)) {
            // 兼容旧流程直接“招工结束 -> 待结算”，也支持新流程从“工作中 -> 待结算”。
            markHiredItemsOnWork(saved);
            markOnWorkItemsPendingSettle(saved);
            createPendingSettlements(saved);
        }
        // 取消招工：通知该订单所有受影响报名者
        if (!canceledUserIds.isEmpty()) {
            messageService.sendToList(canceledUserIds, UserRole.USER, MessageType.ORDER_CANCEL, "招工已取消",
                    "很抱歉，您报名的「" + order.getOrderTitle() + "」岗位已取消招工。",
                    BizType.ORDER, order.getId());
        }
        return saved;
    }

    /**
     * 【预留】岗位快开始提醒：手动/前端触发，通知该订单 已报名/已录用/已到岗 的用户。
     * 后续如需自动提醒，可在此之上加定时任务扫描 startTime。
     */
    @Transactional
    public void notifyOrderStart(Long orderId) {
        BossOrder order = getOrderOrThrow(orderId);
        List<Long> userIds = itemRepository.findByOrderId(orderId).stream()
                .filter(i -> BossStatus.ITEM_APPLIED.equals(i.getStatus())
                        || BossStatus.ITEM_HIRED.equals(i.getStatus())
                        || BossStatus.ITEM_ON_WORK.equals(i.getStatus()))
                .map(BaseOrderItem::getUserId)
                .distinct()
                .toList();
        messageService.sendToList(userIds, UserRole.USER, MessageType.ORDER_START_REMIND, "岗位即将开始",
                "您报名的「" + order.getOrderTitle() + "」" + order.getPostion() + "岗位即将开始，请提前做好准备准时到岗！",
                BizType.ORDER, order.getId());
    }

    // ==================== 报名管理 ====================

    /** 用户报名订单，订单须"招工中"且未重复报名；月结订单可勾选"我要试工" */
    @Transactional
    public BaseOrderItem applyOrder(Long orderId, Long userId, String remark, Boolean trial) {
        BossOrder order = getOrderOrThrow(orderId);
        if (!BossStatus.ORDER_RECRUITING.equals(order.getOrderStatus())) {
            throw new IllegalStateException("该订单当前不可报名");
        }
        User applicant = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在: " + userId));
        if (recruitRules.realnameRequired() && !CertificationStatus.isFaceVerified(applicant)) {
            throw new ForbiddenBusinessException("请先完成实名认证");
        }
        if (itemRepository.existsByOrderIdAndUserId(orderId, userId)) {
            throw new IllegalStateException("您已报名过该订单");
        }
        // 报名人数校验：已录用 + 已到岗 + 已完成的人数不能超过招工人数
        long hiredCount = itemRepository.findByOrderId(orderId).stream()
                .filter(i -> BossStatus.ITEM_HIRED.equals(i.getStatus())
                        || BossStatus.ITEM_ON_WORK.equals(i.getStatus())
                        || BossStatus.ITEM_FINISHED.equals(i.getStatus()))
                .count();
        if (hiredCount >= order.getOrderNum()) {
            throw new IllegalStateException("该订单已招满");
        }
        // 试工标记：仅月结订单可勾选
        boolean wantTrial = Boolean.TRUE.equals(trial);
        if (wantTrial && !BossType.MONTH.equals(order.getType())) {
            throw new IllegalStateException("仅月结订单可选择试工");
        }

        BaseOrderItem item = new BaseOrderItem();
        item.setOrderId(orderId);
        item.setUserId(userId);
        // 先落「已报名」，自动通过模式再走与老板录用相同的流程，保证录用时间、通知和招满结束一致
        item.setStatus(BossStatus.ITEM_APPLIED);
        item.setRemark(remark);
        item.setTrialRequested(wantTrial);
        item.setApplyDate(Date.valueOf(LocalDate.now()));
        item.setApplyAt(LocalDateTime.now());
        BaseOrderItem saved;
        try {
            saved = itemRepository.save(item);
            itemRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("您已报名过该订单");
        }
        boolean manualReview = "manual".equalsIgnoreCase(order.getSignMode());
        // 手动审核必须通知老板处理；自动通过只按报名通知开关提醒
        if (manualReview || !Boolean.FALSE.equals(order.getSignNotify())) {
            String content = userName(userId) + " 报名了您发布的「" + order.getOrderTitle() + "」"
                    + order.getPostion() + "岗位" + (manualReview ? "，请及时审核报名信息" : "，已自动录用");
            messageService.sendToUser(order.getCreateBy(), UserRole.BOSS, MessageType.ORDER_APPLY,
                    manualReview ? "有新的报名待审核" : "您有新的报名", content, BizType.ITEM, saved.getId());
        }
        return manualReview ? saved : hire(saved, order);
    }

    /** 老板录用：已报名 -> 已录用 */
    @Transactional
    public BaseOrderItem hireItem(Long itemId) {
        BaseOrderItem item = getItemOrThrow(itemId);
        if (!BossStatus.ITEM_APPLIED.equals(item.getStatus())) {
            throw new IllegalStateException("仅已报名的记录可以录用");
        }
        return hire(item, getOrderOrThrow(item.getOrderId()));
    }

    /** 已报名 -> 已录用：老板手动录用与自动通过共用。 */
    private BaseOrderItem hire(BaseOrderItem item, BossOrder order) {
        item.setStatus(BossStatus.ITEM_HIRED);
        item.setHireDate(Date.valueOf(LocalDate.now()));
        item.setHireAt(LocalDateTime.now());
        BaseOrderItem saved = itemRepository.save(item);
        // 已录用/已到岗/已完成人数达到需求人数后，自动结束招工，停止继续报名。
        if (BossStatus.ORDER_RECRUITING.equals(order.getOrderStatus())
                && order.getOrderNum() != null && order.getOrderNum() > 0) {
            long hiredCount = itemRepository.findByOrderId(order.getId()).stream()
                    .filter(i -> BossStatus.ITEM_HIRED.equals(i.getStatus())
                            || BossStatus.ITEM_ON_WORK.equals(i.getStatus())
                            || BossStatus.ITEM_FINISHED.equals(i.getStatus()))
                    .count();
            if (hiredCount >= order.getOrderNum()) {
                order.setOrderStatus(BossStatus.ORDER_RECRUIT_END);
                orderRepository.save(order);
            }
        }
        messageService.sendToUser(item.getUserId(), UserRole.USER, MessageType.ORDER_HIRE, "录用通知",
                "恭喜您！您已被「" + order.getOrderTitle() + "」" + order.getPostion() + "岗位录用（"
                        + order.getSalary() + "元/天），请准时到岗。",
                BizType.ITEM, item.getId(), java.util.Map.of("orderTitle", String.valueOf(order.getOrderTitle()), "position", String.valueOf(order.getPostion()), "salary", String.valueOf(order.getSalary())));
        return saved;
    }

    /** 老板拒绝报名：仅待审核的“已报名”记录可操作。 */
    @Transactional
    public BaseOrderItem rejectItem(Long itemId, Long bossId, String reason) {
        BaseOrderItem item = getItemOrThrow(itemId);
        BossOrder order = getOrderOrThrow(item.getOrderId());
        if (bossId == null || !bossId.equals(order.getCreateBy())) {
            throw new ForbiddenBusinessException("无权处理该岗位的报名记录");
        }
        if (!BossStatus.ITEM_APPLIED.equals(item.getStatus())) {
            throw new IllegalStateException("仅待审核的报名记录可以拒绝");
        }
        item.setStatus(BossStatus.ITEM_REJECTED);
        item.setCancelReason(StringUtils.hasText(reason) ? reason.trim() : null);
        item.setCancelDate(Date.valueOf(LocalDate.now()));
        BaseOrderItem saved = itemRepository.save(item);
        messageService.sendToUser(item.getUserId(), UserRole.USER, MessageType.ORDER_APPLY_REJECT, "报名审核未通过",
                "您报名的「" + order.getOrderTitle() + "」岗位未通过审核"
                        + (StringUtils.hasText(reason) ? "，原因：" + reason.trim() : ""),
                BizType.ITEM, item.getId());
        return saved;
    }

    /** 老板未开启开工码时手动确认到岗：已录用 -> 已到岗，订单进入工作中。 */
    @Transactional
    public BaseOrderItem confirmWorkManually(Long itemId) {
        BaseOrderItem item = getItemOrThrow(itemId);
        BossOrder order = getOrderOrThrow(item.getOrderId());
        if (manualStartCodeEnabled(order)) {
            throw new IllegalArgumentException("已开启开工码，请由零工输入开工码后开始工作");
        }
        return confirmWork(itemId);
    }

    /** 确认到岗：已录用 -> 已到岗，订单进入工作中。 */
    @Transactional
    public BaseOrderItem confirmWork(Long itemId) {
        BaseOrderItem item = getItemOrThrow(itemId);
        if (!BossStatus.ITEM_HIRED.equals(item.getStatus())) {
            throw new IllegalStateException("仅已录用的记录可以确认到岗");
        }
        item.setStatus(BossStatus.ITEM_ON_WORK);
        item.setWorkDate(Date.valueOf(LocalDate.now()));
        item.setWorkAt(LocalDateTime.now());
        BaseOrderItem saved = itemRepository.save(item);
        // 零工确认到岗：通知老板
        BossOrder order = getOrderOrThrow(item.getOrderId());
        if (BossStatus.ORDER_RECRUITING.equals(order.getOrderStatus())
                || BossStatus.ORDER_RECRUIT_END.equals(order.getOrderStatus())
                || BossStatus.ORDER_WORKING.equals(order.getOrderStatus())) {
            order.setOrderStatus(BossStatus.ORDER_WORKING);
            orderRepository.save(order);
        }
        messageService.sendToUser(order.getCreateBy(), UserRole.BOSS, MessageType.ITEM_WORK_CONFIRM, "零工已到岗",
                userName(item.getUserId()) + " 已到达「" + order.getOrderTitle() + "」岗位，请留意安排工作。",
                BizType.ITEM, item.getId());
        return saved;
    }

    /** 完成：已到岗 -> 待结算（等待老板发起结算；结算成功后才为「已完成」） */
    @Transactional
    public BaseOrderItem finishItemManually(Long itemId) {
        BaseOrderItem item = getItemOrThrow(itemId);
        BossOrder order = getOrderOrThrow(item.getOrderId());
        if (earlyLeaveCodeEnabled(order)) {
            throw new IllegalArgumentException("已开启早退码，请由零工输入早退码后完成工作");
        }
        return finishItem(itemId);
    }

    /** 完成：已到岗 -> 待结算（等待老板发起结算；结算成功后才为「已完成」） */
    @Transactional
    public BaseOrderItem finishItem(Long itemId) {
        BaseOrderItem item = getItemOrThrow(itemId);
        if (!BossStatus.ITEM_ON_WORK.equals(item.getStatus())) {
            throw new IllegalStateException("仅已到岗的记录可以完成");
        }
        item.setStatus(BossStatus.ITEM_PENDING_SETTLE);
        item.setFinishDate(Date.valueOf(LocalDate.now()));
        item.setFinishAt(LocalDateTime.now());
        BaseOrderItem saved = itemRepository.save(item);
        BossOrder order = getOrderOrThrow(item.getOrderId());
        if (BossStatus.ORDER_WORKING.equals(order.getOrderStatus())
                && allActiveWorkersFinished(order)) {
            order.setOrderStatus(BossStatus.ORDER_PENDING_SETTLE);
            orderRepository.save(order);
        }
        if (BossStatus.ORDER_PENDING_SETTLE.equals(order.getOrderStatus())) {
            createPendingSettlements(order);
        }
        return saved;
    }

    /** 早退码确认：报名记录状态 -> 待结算（earlyLeave=true 标记早退），订单立即进入待结算并生成待结算单。 */
    @Transactional
    public BossOrder finishByEarlyLeave(Long itemId) {
        BaseOrderItem item = getItemOrThrow(itemId);
        if (!BossStatus.ITEM_ON_WORK.equals(item.getStatus())) {
            throw new IllegalStateException("仅已到岗的记录可以早退");
        }
        item.setStatus(BossStatus.ITEM_PENDING_SETTLE);
        item.setEarlyLeave(true);
        item.setFinishDate(Date.valueOf(LocalDate.now()));
        item.setFinishAt(LocalDateTime.now());
        itemRepository.save(item);
        if (creditScoreService != null && item.getUserId() != null) {
            creditScoreService.adjust(item.getUserId(), CreditScoreService.WORKER_STAR, -10,
                    "WORKER_EARLY_LEAVE", "EARLY_LEAVE", "WORKER_EARLY_LEAVE:" + item.getId(), "早退");
        }
        BossOrder order = getOrderOrThrow(item.getOrderId());
        order.setOrderStatus(BossStatus.ORDER_PENDING_SETTLE);
        BossOrder saved = orderRepository.save(order);
        createPendingSettlements(saved);
        return saved;
    }

    /** 用户取消报名：已报名/已录用 -> 取消报名 */
    @Transactional
    public BaseOrderItem cancelItem(Long itemId, String reason) {
        BaseOrderItem item = getItemOrThrow(itemId);
        if (!BossStatus.ITEM_APPLIED.equals(item.getStatus())
                && !BossStatus.ITEM_HIRED.equals(item.getStatus())) {
            throw new IllegalStateException("当前状态不可取消报名");
        }
        item.setStatus(BossStatus.ITEM_CANCELED);
        item.setCancelReason(reason);
        item.setCancelDate(Date.valueOf(LocalDate.now()));
        BaseOrderItem saved = itemRepository.save(item);
        // 零工取消报名：通知老板
        BossOrder order = getOrderOrThrow(item.getOrderId());
        messageService.sendToUser(order.getCreateBy(), UserRole.BOSS, MessageType.ITEM_CANCEL, "报名取消提醒",
                userName(item.getUserId()) + " 取消了「" + order.getOrderTitle() + "」岗位的报名"
                        + (StringUtils.hasText(reason) ? "，原因：" + reason : ""),
                BizType.ITEM, item.getId());
        return saved;
    }

    /** 零工取消自己的报名，先校验条目归属，避免越权操作。 */
    @Transactional
    public BaseOrderItem cancelWorkerItem(Long itemId, Long userId, String reason) {
        BaseOrderItem item = getItemOrThrow(itemId);
        if (item.getUserId() == null || !item.getUserId().equals(userId)) {
            throw new com.kuaima.app.common.ForbiddenBusinessException("无权操作该报名记录");
        }
        return cancelItem(itemId, reason);
    }

    /** 某订单的报名列表 */
    public List<BaseOrderItem> listItemsByOrder(Long orderId) {
        return itemRepository.findByOrderId(orderId).stream().peek(item ->
                item.setNickname(userRepository.findById(item.getUserId())
                        .map(User::getNickname)
                        .filter(StringUtils::hasText)
                        .orElse(item.getUserId() == null ? "" : "零工#" + item.getUserId()))).toList();
    }

    /** 某用户报名的记录 */
    public List<BaseOrderItem> listItemsByUser(Long userId) {
        return itemRepository.findByUserId(userId);
    }

    // ==================== 草稿管理 ====================

    /** 保存草稿：不校验必填字段，状态固定为"草稿" */
    @Transactional
    public BossOrder saveDraft(BossOrder order) {
        if (order.getAddressId() != null) {
            applySavedAddress(order, order.getAddressId());
        }
        if (StringUtils.hasText(order.getAddress())) {
            validateOrderCoordinatesRequired(order.getLongitude(), order.getLatitude());
        } else {
            validateOrderCoordinates(order.getLongitude(), order.getLatitude());
        }
        order.setOrderStatus(BossStatus.ORDER_DRAFT);
        return orderRepository.save(order);
    }

    /** 某老板的草稿列表 */
    public List<BossOrder> listDrafts(Long userId) {
        return orderRepository.findByOrderStatusAndCreateByOrderByIdDesc(BossStatus.ORDER_DRAFT, userId);
    }

    /** 更新草稿：仅草稿状态可更新 */
    @Transactional
    public BossOrder updateDraft(Long id, BossOrder update) {
        BossOrder order = getOrderOrThrow(id);
        if (order.getEnterpriseId() == null && update.getEnterpriseId() != null) {
            order.setEnterpriseId(update.getEnterpriseId());
        }
        if (!BossStatus.ORDER_DRAFT.equals(order.getOrderStatus())) {
            throw new IllegalStateException("仅草稿状态的订单可以更新");
        }
        if (StringUtils.hasText(update.getOrderTitle())) order.setOrderTitle(update.getOrderTitle());
        if (StringUtils.hasText(update.getType())) order.setType(update.getType());
        if (StringUtils.hasText(update.getPostion())) order.setPostion(update.getPostion());
        if (update.getOrderNum() != null) order.setOrderNum(update.getOrderNum());
        if (update.getDuration() != null) order.setDuration(update.getDuration());
        if (update.getSalary() != null) order.setSalary(update.getSalary());
        if (StringUtils.hasText(update.getOrderContent())) order.setOrderContent(update.getOrderContent());
        if (StringUtils.hasText(update.getOrderRemark())) order.setOrderRemark(update.getOrderRemark());
        boolean locationChanged = update.getAddressId() != null || StringUtils.hasText(update.getAddress());
        if (update.getAddressId() != null) {
            applySavedAddress(order, update.getAddressId());
        } else if (StringUtils.hasText(update.getAddress())) {
            if (update.getLongitude() == null || update.getLatitude() == null) {
                throw new IllegalArgumentException("修改工作地址时必须同时提供经度和纬度");
            }
            order.setAddress(update.getAddress());
        }
        if (StringUtils.hasText(update.getTags())) order.setTags(update.getTags());
        applyFilterFields(order, update);
        applyRecruitSettings(order, update);
        if (update.getStartTime() != null) order.setStartTime(update.getStartTime());
        if (update.getEndTime() != null) order.setEndTime(update.getEndTime());
        if (StringUtils.hasText(update.getTrialDuration())) order.setTrialDuration(update.getTrialDuration());
        if (locationChanged) {
            validateOrderCoordinatesRequired(order.getLongitude(), order.getLatitude());
        } else {
            validateOrderCoordinates(order.getLongitude(), order.getLatitude());
        }
        return orderRepository.save(order);
    }

    // ==================== 统计与资料 ====================

    /** 老板首页统计：总订单数、招工中数、报名数、已结算金额(元) */
    public com.kuaima.app.domain.boss.model.BossHomeModels.BossStats getBossStats(Long userId) {
        List<BossOrder> orders = orderRepository.findByCreateByOrderByIdDesc(userId);
        int totalOrders = orders.size();
        long recruitingCount = orders.stream().filter(o -> BossStatus.ORDER_RECRUITING.equals(o.getOrderStatus())).count();
        long applicantCount = itemRepository.countApplicantsByBossId(userId);
        List<Long> orderIds = orders.stream().map(BossOrder::getId).toList();
        BigDecimal totalSpent = orderIds.isEmpty() ? BigDecimal.ZERO : settlementRespository.findByOrderIdIn(orderIds).stream()
                .filter(s -> SettlementStatus.PAID.equals(s.getStatus()))
                .map(s -> s.getTotalAmount() == null ? BigDecimal.ZERO : s.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String city = userRepository.findById(userId).map(User::getCity).filter(StringUtils::hasText).orElse("");
        long nearbyWorkers = StringUtils.hasText(city)
                ? userRepository.countWorkerIdentitiesByCity(city)
                : userRepository.countWorkerIdentities();
        return new com.kuaima.app.domain.boss.model.BossHomeModels.BossStats(
                totalOrders, recruitingCount, applicantCount, totalSpent,
                nearbyWorkers, 0, city);
    }

    /** 老板账户统计：与首页统计类似，用于"我的"页面 */
    public java.util.Map<String, Object> getBossProfileStats(Long userId) {
        var stats = getBossStats(userId);
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("totalOrders", stats.totalOrders());
        result.put("recruitingCount", stats.recruitingCount());
        result.put("applicantCount", stats.applicantCount());
        result.put("settledAmount", stats.settledAmount());
        result.put("nearbyWorkers", stats.nearbyWorkers());
        result.put("fastestMinutes", stats.fastestMinutes());
        result.put("city", stats.city());
        return result;
    }

    /** 老板资料 */
    public User getBossProfile(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在: " + userId));
    }

    // ==================== 高级筛选 ====================

    /** 零工端高级筛选岗位（仅招工中） */
    public Page<BossOrder> filterOrders(String city, Integer salaryMin, Integer salaryMax,
                                        String tag, String type, Integer duration, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id"));
        return orderRepository.filter(city, salaryMin, salaryMax, tag, type, duration, pageable);
    }

    // ==================== 月结/压薪订单 ====================

    /** 零工的月结订单列表 */
    public List<BaseOrderItem> listMonthlyOrders(Long userId) {
        return itemRepository.findByUserIdAndOrderType(userId, BossType.MONTH);
    }

    /** 零工的压薪日结订单列表 */
    public List<BaseOrderItem> listPressSalaryOrders(Long userId) {
        return itemRepository.findByUserIdAndOrderType(userId, BossType.HELD_BACK);
    }

    // ==================== 企业认证 ====================

    /** 提交企业认证 */
    @Transactional
    public User submitEnterpriseCert(Long userId, String companyName, String industry, String licenseNo, String legalRep) {
        if (certificationService == null) {
            throw new IllegalStateException("认证服务未初始化");
        }
        return certificationService.submitEnterprise(userId, companyName, industry, licenseNo, legalRep);
    }

    // ==================== 内部方法 ====================

    private BossOrder getOrderOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("订单不存在: " + id));
    }

    private BaseOrderItem getItemOrThrow(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("报名记录不存在: " + id));
    }

    /** 订单进入待结算时，为已到岗/待结算/已完成零工生成待支付结算单。 */
    private void createPendingSettlements(BossOrder order) {
        if (order.getSalary() == null || order.getSalary().signum() <= 0) {
            throw new IllegalStateException("订单工资未设置，无法生成结算单");
        }
        for (BaseOrderItem item : itemRepository.findByOrderId(order.getId())) {
            if (!BossStatus.ITEM_ON_WORK.equals(item.getStatus())
                    && !BossStatus.ITEM_PENDING_SETTLE.equals(item.getStatus())
                    && !BossStatus.ITEM_FINISHED.equals(item.getStatus())) {
                continue;
            }
            if (settlementRespository.existsByItemIdAndStatusIn(item.getId(),
                    List.of(SettlementStatus.PENDING, SettlementStatus.PAID))) {
                continue;
            }
            int workDays = resolveSettlementWorkDays(item);
            BigDecimal wage = order.getSalary().multiply(BigDecimal.valueOf(workDays)).setScale(2, java.math.RoundingMode.HALF_UP);
            Settlement settlement = new Settlement();
            settlement.setItemId(item.getId());
            settlement.setOrderId(order.getId());
            settlement.setWorkerId(item.getUserId());
            settlement.setWorkDays(workDays);
            settlement.setWage(wage);
            BigDecimal serviceFee = calculateServiceFee(wage);
            settlement.setServiceFee(serviceFee);
            settlement.setTotalAmount(wage.add(serviceFee));
            settlement.setStatus(SettlementStatus.PENDING);
            settlementRespository.save(settlement);
        }
    }

    private BigDecimal calculateServiceFee(BigDecimal wage) {
        BigDecimal rate = configuredServiceFeeRate();
        if (rate.signum() <= 0) return BigDecimal.ZERO.setScale(2);
        return wage
                .multiply(rate)
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal configuredServiceFeeRate() {
        if (adminSettingRepository == null) return BigDecimal.ZERO;
        boolean enabled = adminSettingRepository.findById("rules.platformFeeEnabled")
                .map(AdminSetting::getSettingValue).map(String::trim).map(Boolean::parseBoolean).orElse(false);
        if (!enabled) return BigDecimal.ZERO;
        return adminSettingRepository.findById("rules.feeRate")
                .map(AdminSetting::getSettingValue).map(String::trim).map(this::parseRate).orElse(BigDecimal.ZERO);
    }

    private BigDecimal parseRate(String value) {
        try { return new BigDecimal(value).max(BigDecimal.ZERO).min(BigDecimal.valueOf(100)); }
        catch (RuntimeException e) { return BigDecimal.ZERO; }
    }

    /** 招工人数变更后，根据现有录用/到岗/完成人数重新推进订单并补建结算单。 */
    private void reconcileOrderAfterHeadcountChange(BossOrder order) {
        if (!BossStatus.ORDER_RECRUITING.equals(order.getOrderStatus())
                || order.getOrderNum() == null || order.getOrderNum() <= 0) {
            return;
        }
        List<BaseOrderItem> activeItems = itemRepository.findByOrderId(order.getId()).stream()
                .filter(i -> BossStatus.ITEM_HIRED.equals(i.getStatus())
                        || BossStatus.ITEM_ON_WORK.equals(i.getStatus())
                        || BossStatus.ITEM_FINISHED.equals(i.getStatus()))
                .toList();
        if (activeItems.size() < order.getOrderNum()) {
            return;
        }
        boolean allArrived = activeItems.stream().allMatch(i ->
                BossStatus.ITEM_ON_WORK.equals(i.getStatus()) || BossStatus.ITEM_FINISHED.equals(i.getStatus()));
        order.setOrderStatus(allArrived ? BossStatus.ORDER_WORKING : BossStatus.ORDER_RECRUIT_END);
        orderRepository.save(order);
    }

    private void markHiredItemsOnWork(BossOrder order) {
        List<BaseOrderItem> hiredItems = itemRepository.findByOrderId(order.getId()).stream()
                .filter(item -> BossStatus.ITEM_HIRED.equals(item.getStatus()))
                .toList();
        if (hiredItems.isEmpty()) {
            return;
        }
        Date workDate = Date.valueOf(LocalDate.now());
        hiredItems.forEach(item -> {
            item.setStatus(BossStatus.ITEM_ON_WORK);
            if (item.getWorkDate() == null) {
                item.setWorkDate(workDate);
                item.setWorkAt(LocalDateTime.now());
            }
        });
        itemRepository.saveAll(hiredItems);
    }

    private void markOnWorkItemsPendingSettle(BossOrder order) {
        List<BaseOrderItem> workingItems = itemRepository.findByOrderId(order.getId()).stream()
                .filter(item -> BossStatus.ITEM_ON_WORK.equals(item.getStatus()))
                .toList();
        Date finishDate = Date.valueOf(LocalDate.now());
        workingItems.forEach(item -> {
            item.setStatus(BossStatus.ITEM_PENDING_SETTLE);
            if (item.getFinishDate() == null) {
                item.setFinishDate(finishDate);
                item.setFinishAt(LocalDateTime.now());
            }
        });
        itemRepository.saveAll(workingItems);
    }

    private boolean allActiveWorkersFinished(BossOrder order) {
        List<BaseOrderItem> items = itemRepository.findByOrderId(order.getId());
        boolean hasActive = items.stream().anyMatch(item ->
                BossStatus.ITEM_HIRED.equals(item.getStatus())
                        || BossStatus.ITEM_ON_WORK.equals(item.getStatus())
                        || BossStatus.ITEM_PENDING_SETTLE.equals(item.getStatus())
                        || BossStatus.ITEM_FINISHED.equals(item.getStatus()));
        return hasActive && items.stream().noneMatch(item ->
                BossStatus.ITEM_HIRED.equals(item.getStatus())
                        || BossStatus.ITEM_ON_WORK.equals(item.getStatus()));
    }

    private boolean manualStartCodeEnabled(BossOrder order) {
        return attendanceCodeEnabled(order, true);
    }

    private boolean earlyLeaveCodeEnabled(BossOrder order) {
        return attendanceCodeEnabled(order, false);
    }

    private boolean attendanceCodeEnabled(BossOrder order, boolean work) {
        if (recruitSettingsRepository == null) return false;
        var settings = order.getEnterpriseId() != null
                ? recruitSettingsRepository.findByEnterpriseId(order.getEnterpriseId())
                : recruitSettingsRepository.findByBossId(order.getCreateBy());
        return settings.map(work ? BossRecruitSettings::getStartCodeEnabled
                        : BossRecruitSettings::getEarlyCodeEnabled)
                .map(Boolean.TRUE::equals)
                .orElse(Boolean.FALSE);
    }

    private int resolveSettlementWorkDays(BaseOrderItem item) {
        if (item.getWorkDate() != null && item.getFinishDate() != null
                && !item.getFinishDate().before(item.getWorkDate())) {
            long days = ChronoUnit.DAYS.between(
                    item.getWorkDate().toLocalDate(), item.getFinishDate().toLocalDate()) + 1;
            return Math.max(1, (int) days);
        }
        return 1;
    }

    /** 为岗位列表批量填充有效报名数（排除取消报名、取消招工）。 */
    private void fillCurrentApply(List<BossOrder> orders) {
        if (orders.isEmpty()) {
            return;
        }
        List<Long> orderIds = orders.stream().map(BossOrder::getId).toList();
        List<String> activeStatuses = List.of(
                BossStatus.ITEM_APPLIED,
                BossStatus.ITEM_HIRED,
                BossStatus.ITEM_ON_WORK,
                BossStatus.ITEM_PENDING_SETTLE,
                BossStatus.ITEM_FINISHED);
        Map<Long, Long> countByOrderId = itemRepository
                .countByOrderIdsAndStatuses(orderIds, activeStatuses)
                .stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).longValue()));
        orders.forEach(order -> order.setCurrentApply(countByOrderId.getOrDefault(order.getId(), 0L)));
    }

    /** 校验开始/结束时间：两者都填时开始必须早于结束 */
    private void checkTimeRange(java.util.Date startTime, java.util.Date endTime) {
        if (startTime != null && endTime != null && startTime.after(endTime)) {
            throw new IllegalArgumentException("结束时间必须晚于开始时间");
        }
    }

    /** 更新筛选相关字段；null/空字符串表示调用方未修改该字段。 */
    private void applyFilterFields(BossOrder target, BossOrder source) {
        if (source.getJobCategoryId() != null) {
            target.setJobCategoryId(source.getJobCategoryId());
        }
        // addressId 指向后端校验过的常用地址，请求体坐标不得覆盖该可信坐标。
        if (source.getAddressId() == null) {
            if (source.getLongitude() != null) {
                target.setLongitude(source.getLongitude());
            }
            if (source.getLatitude() != null) {
                target.setLatitude(source.getLatitude());
            }
        }
        if (StringUtils.hasText(source.getExperience())) {
            target.setExperience(source.getExperience().trim());
        }
        if (StringUtils.hasText(source.getGender())) {
            target.setGender(source.getGender().trim());
        }
    }

    /** 创建时写入默认值，更新时仅覆盖请求中明确传入的设置。 */
    private void applyRecruitSettings(BossOrder target, BossOrder source) {
        if (source == target || source.getSignMode() != null || target.getSignMode() == null) {
            if (StringUtils.hasText(source.getSignMode())) {
                target.setSignMode(source.getSignMode().trim());
            } else if (target.getSignMode() == null) {
                target.setSignMode("auto");
            }
        }
        if (source == target || source.getPhoneNotify() != null || target.getPhoneNotify() == null) {
            target.setPhoneNotify(source.getPhoneNotify() != null ? source.getPhoneNotify() : Boolean.TRUE);
        }
        if (source == target || source.getSignNotify() != null || target.getSignNotify() == null) {
            target.setSignNotify(source.getSignNotify() != null ? source.getSignNotify() : Boolean.TRUE);
        }
        if (source == target || source.getStartRemind() != null || target.getStartRemind() == null) {
            target.setStartRemind(source.getStartRemind() != null ? source.getStartRemind() : Boolean.TRUE);
        }
        if (source == target || source.getSettleNotify() != null || target.getSettleNotify() == null) {
            target.setSettleNotify(source.getSettleNotify() != null ? source.getSettleNotify() : Boolean.TRUE);
        }
        validateSignMode(target.getSignMode());
    }

    private void applyGlobalRecruitDefaults(BossOrder order) {
        if (recruitSettingsRepository == null || order.getCreateBy() == null) return;
        BossRecruitSettings s = recruitSettingsRepository.findByBossId(order.getCreateBy()).orElse(null);
        if (s == null) return;
        if (!StringUtils.hasText(order.getType())) order.setType(s.getType());
        if (!StringUtils.hasText(order.getSignMode())) order.setSignMode(s.getSignMode());
        if (order.getPhoneNotify() == null) order.setPhoneNotify(s.getPhoneNotify());
        if (order.getSignNotify() == null) order.setSignNotify(s.getSignNotify());
        if (order.getStartRemind() == null) order.setStartRemind(s.getStartRemind());
        if (order.getSettleNotify() == null) order.setSettleNotify(s.getSettleNotify());
    }

    private void normalizeRecruitSettings(BossOrder order) {
        if (!StringUtils.hasText(order.getSignMode())) {
            order.setSignMode("auto");
        }
        if (order.getPhoneNotify() == null) order.setPhoneNotify(Boolean.TRUE);
        if (order.getSignNotify() == null) order.setSignNotify(Boolean.TRUE);
        if (order.getStartRemind() == null) order.setStartRemind(Boolean.TRUE);
        if (order.getSettleNotify() == null) order.setSettleNotify(Boolean.TRUE);
    }

    private void validateSignMode(String signMode) {
        if (!"auto".equals(signMode) && !"manual".equals(signMode)) {
            throw new IllegalArgumentException("报名方式只能是 auto 或 manual");
        }
    }

    /** 坐标必须成对出现并落在经纬度合法范围内。历史订单可保持坐标为空。 */
    private void validateOrderCoordinates(BigDecimal longitude, BigDecimal latitude) {
        if ((longitude == null) != (latitude == null)) {
            throw new IllegalArgumentException("经度和纬度必须同时填写");
        }
        if (longitude != null && (longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new IllegalArgumentException("经度必须在 -180 到 180 之间");
        }
        if (latitude != null && (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0)) {
            throw new IllegalArgumentException("纬度必须在 -90 到 90 之间");
        }
    }

    private void validateOrderCoordinatesRequired(BigDecimal longitude, BigDecimal latitude) {
        validateOrderCoordinates(longitude, latitude);
        if (longitude == null || latitude == null) {
            throw new IllegalArgumentException("工作地址必须提供经度和纬度，请重新选择地址");
        }
    }

    /** 使用当前企业的常用地址，后端同时复制文字地址和坐标，避免地址与坐标不一致。 */
    private void applySavedAddress(BossOrder order, Long addressId) {
        if (bossAddressRepository == null) {
            throw new IllegalStateException("常用地址服务未配置");
        }
        if (order.getEnterpriseId() == null) {
            throw new IllegalArgumentException("企业信息不能为空");
        }
        BossAddress address = bossAddressRepository.findByIdAndEnterpriseId(addressId, order.getEnterpriseId())
                .orElseThrow(() -> new ForbiddenBusinessException("地址不存在或不属于当前企业"));
        if (address.getLat() == null || address.getLng() == null) {
            throw new IllegalArgumentException("所选地址缺少经纬度，请重新选择地址");
        }
        order.setAddress(formatAddress(address));
        order.setLatitude(BigDecimal.valueOf(address.getLat()));
        order.setLongitude(BigDecimal.valueOf(address.getLng()));
    }

    private String formatAddress(BossAddress address) {
        return java.util.stream.Stream.of(address.getCity(), address.getDistrict(), address.getDetail())
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(""));
    }

    /** 校验列表筛选参数，避免生成无意义或不安全的距离/范围条件。 */
    private void validateListQuery(BossOrderQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("查询参数不能为空");
        }
        if (query.getSalaryMin() != null && query.getSalaryMax() != null
                && query.getSalaryMin() > query.getSalaryMax()) {
            throw new IllegalArgumentException("最低工资不能高于最高工资");
        }
        if (query.getSalaryMin() != null && query.getSalaryMin() < 0
                || query.getSalaryMax() != null && query.getSalaryMax() < 0) {
            throw new IllegalArgumentException("工资筛选值不能小于 0");
        }
        if (query.getStartDate() != null && query.getEndDate() != null
                && query.getStartDate().isAfter(query.getEndDate())) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        boolean hasLongitude = query.getLongitude() != null;
        boolean hasLatitude = query.getLatitude() != null;
        if (hasLongitude != hasLatitude) {
            throw new IllegalArgumentException("经度和纬度必须同时填写");
        }
        if (hasLongitude) {
            validateOrderCoordinates(query.getLongitude(), query.getLatitude());
        }
        if (query.getDistanceKm() != null && query.getDistanceKm() <= 0) {
            throw new IllegalArgumentException("距离必须大于 0 公里");
        }
        if (query.getDistanceKm() != null && !hasLongitude) {
            throw new IllegalArgumentException("按距离筛选时必须提供经度和纬度");
        }
        if (StringUtils.hasText(query.getTagMode())
                && !"ALL".equalsIgnoreCase(query.getTagMode().trim())
                && !"ANY".equalsIgnoreCase(query.getTagMode().trim())) {
            throw new IllegalArgumentException("tagMode 只能是 ALL 或 ANY");
        }
    }

    /** 用户显示名：优先昵称，其次 零工#id */
    private String userName(Long userId) {
        if (userId == null) {
            return "";
        }
        return userRepository.findById(userId)
                .map(User::getNickname)
                .filter(StringUtils::hasText)
                .orElse("零工#" + userId);
    }

    /** 岗位一句话摘要（广播/通知文案用） */
    private String jobSummary(BossOrder order) {
        StringBuilder sb = new StringBuilder("「")
                .append(order.getOrderTitle()).append("」招")
                .append(order.getPostion()).append(" ")
                .append(order.getOrderNum()).append("人，")
                .append(order.getSalary()).append("元/天");
        if (StringUtils.hasText(order.getAddress())) {
            sb.append("，地点：").append(order.getAddress());
        }
        return sb.toString();
    }
}
