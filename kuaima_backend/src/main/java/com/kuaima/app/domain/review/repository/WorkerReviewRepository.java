package com.kuaima.app.domain.review.repository;

import com.kuaima.app.domain.review.entity.WorkerReview;
import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerReviewRepository extends JpaRepository<WorkerReview, Long> {
    boolean existsByItemId(Long itemId);
    List<WorkerReview> findByItemIdIn(Collection<Long> itemIds);
    List<WorkerReview> findByOrderIdIn(Collection<Long> orderIds);
    List<WorkerReview> findByOrderIdOrderByIdAsc(Long orderId);
}
