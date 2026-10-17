package com.kuaima.app.domain.review.model;

import java.time.LocalDateTime;

public final class BossReviewModels {
    public record SaveRequest(Integer attitudeScore, Integer settlementScore,
                              Integer environmentScore, String content) {}
    public record ReviewView(Long id, Long itemId, Long orderId, Long workerId, Long bossId,
                             Integer attitudeScore, Integer settlementScore, Integer environmentScore,
                             String content, LocalDateTime createdAt, LocalDateTime updatedAt, String workerName) {}
    public record ReviewSummary(Integer attitudeScore, Integer settlementScore,
                                Integer environmentScore, String content, LocalDateTime updatedAt) {}
    private BossReviewModels() {}
}
