package com.kuaima.app.domain.user.model;

import java.util.Date;
import java.math.BigDecimal;
import com.kuaima.app.domain.review.model.BossReviewModels.ReviewSummary;

public final class WorkerOrderModels {

    public record WorkerOrder(
            Long id,
            Long orderId,
            String status,
            Boolean trialRequested,
            Date applyDate,
            Date hireDate,
            Date workDate,
            Date finishDate,
            String orderTitle,
            String type,
            BigDecimal salary,
            String address,
            BigDecimal longitude,
            BigDecimal latitude,
            Date startTime,
            Date endTime,
            String companyName,
            String postion,
            String orderStatus,
            Integer duration,
            String tags,
            boolean workCodeEnabled,
            boolean leaveCodeEnabled,
            boolean reviewed,
            ReviewSummary bossReview) {
    }

    private WorkerOrderModels() {
    }
}
