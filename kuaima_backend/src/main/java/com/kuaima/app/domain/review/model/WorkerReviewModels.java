package com.kuaima.app.domain.review.model;

import java.time.LocalDateTime;

public final class WorkerReviewModels {
    public record SaveRequest(Integer overallScore, Integer attitudeScore, Integer efficiencyScore,
                              Integer skillScore, String content) {}
    public record ReviewView(Long id, Long itemId, Long orderId, Long workerId, Integer overallScore,
                             Integer attitudeScore, Integer efficiencyScore, Integer skillScore,
                             String content, LocalDateTime createdAt, String workerName) {}
    public record ReviewStatusView(Long orderId, Long reviewableWorkerCount, Long reviewCount) {}
    private WorkerReviewModels() {}
}
