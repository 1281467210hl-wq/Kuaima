package com.kuaima.app.domain.review.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.kuaima.app.domain.review.entity.BossReview;

public interface BossReviewRepository extends JpaRepository<BossReview, Long> {
    Optional<BossReview> findByItemId(Long itemId);
    List<BossReview> findByItemIdIn(Collection<Long> itemIds);
    List<BossReview> findByOrderIdOrderByIdAsc(Long orderId);
}
