package com.kuaima.app.domain.boss.repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.model.BossOrderQuery;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/** BossOrder 动态查询条件，所有非空条件按 AND 组合。 */
public final class BossOrderSpecifications {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private BossOrderSpecifications() {
    }

    public static Specification<BossOrder> from(Long bossId, BossOrderQuery filter) {
        return fromOwner(bossId, null, filter);
    }

    public static Specification<BossOrder> fromEnterprise(Long enterpriseId, BossOrderQuery filter) {
        return fromOwner(null, enterpriseId, filter);
    }

    private static Specification<BossOrder> fromOwner(Long bossId, Long enterpriseId, BossOrderQuery filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (bossId != null) {
                predicates.add(cb.equal(root.get("createBy"), bossId));
            }
            if (enterpriseId != null) {
                predicates.add(cb.equal(root.get("enterpriseId"), enterpriseId));
            }
            if (StringUtils.hasText(filter.getType())) {
                predicates.add(cb.equal(root.get("type"), filter.getType().trim()));
            }
            if (StringUtils.hasText(filter.getStatus())) {
                predicates.add(cb.equal(root.get("orderStatus"), filter.getStatus().trim()));
            }
            if (StringUtils.hasText(filter.getTitle())) {
                predicates.add(cb.like(root.get("orderTitle"), "%" + filter.getTitle().trim() + "%"));
            }
            if (StringUtils.hasText(filter.getKeyword())) {
                String keyword = filter.getKeyword().trim();
                predicates.add(cb.or(
                        cb.like(root.get("orderTitle"), "%" + keyword + "%"),
                        cb.like(root.get("postion"), "%" + keyword + "%"),
                        cb.like(root.get("address"), "%" + keyword + "%")));
            }
            if (StringUtils.hasText(filter.getPosition())) {
                predicates.add(cb.like(root.get("postion"), "%" + filter.getPosition().trim() + "%"));
            }
            if (filter.getStartDate() != null) {
                Date start = Timestamp.valueOf(filter.getStartDate().atStartOfDay());
                predicates.add(cb.greaterThanOrEqualTo(root.get("startTime"), start));
            }
            if (filter.getEndDate() != null) {
                Date endExclusive = Timestamp.valueOf(filter.getEndDate().plusDays(1).atStartOfDay());
                predicates.add(cb.lessThan(root.get("startTime"), endExclusive));
            }
            if (filter.getJobCategoryId() != null) {
                predicates.add(cb.equal(root.get("jobCategoryId"), filter.getJobCategoryId()));
            }
            if (filter.getSalaryMin() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("salary"), filter.getSalaryMin()));
            }
            if (filter.getSalaryMax() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("salary"), filter.getSalaryMax()));
            }
            if (StringUtils.hasText(filter.getExperience())) {
                predicates.add(cb.equal(root.get("experience"), filter.getExperience().trim()));
            }
            if (StringUtils.hasText(filter.getGender())) {
                predicates.add(cb.equal(root.get("gender"), filter.getGender().trim()));
            }
            if (filter.getDurationMin() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("duration"), filter.getDurationMin()));
            }
            if (filter.getDurationMax() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("duration"), filter.getDurationMax()));
            }
            if (Boolean.TRUE.equals(filter.getSameDay())) {
                predicates.add(cb.equal(
                        cb.function("date", java.sql.Date.class, root.get("endTime")),
                        cb.function("date", java.sql.Date.class, root.get("startTime"))));
            } else if (Boolean.FALSE.equals(filter.getSameDay())) {
                predicates.add(cb.notEqual(
                        cb.function("date", java.sql.Date.class, root.get("endTime")),
                        cb.function("date", java.sql.Date.class, root.get("startTime"))));
            }
            addTags(predicates, root.get("tags"), filter, cb);
            if (filter.getDistanceKm() != null) {
                addDistance(predicates, root.get("longitude"), root.get("latitude"), filter, cb);
            }
            Predicate predicate = cb.and(predicates.toArray(Predicate[]::new));
            applySort(root, query, cb, filter);
            return predicate;
        };
    }

    private static void applySort(Root<BossOrder> root,
                                  CriteriaQuery<?> query,
                                  CriteriaBuilder cb,
                                  BossOrderQuery filter) {
        if (!StringUtils.hasText(filter.getSort())) return;
        switch (filter.getSort()) {
            case "NEARBY" -> {
                if (filter.getLatitude() != null && filter.getLongitude() != null) {
                    query.orderBy(cb.asc(distanceExpression(
                            root.get("longitude"), root.get("latitude"), filter, cb)));
                }
            }
            case "TIME" -> query.orderBy(cb.asc(root.get("startTime")));
            case "SENIOR_EMPLOYER" -> {
                var historyCount = query.subquery(Long.class);
                Root<BossOrder> history = historyCount.from(BossOrder.class);
                historyCount.select(cb.count(history))
                        .where(cb.equal(history.get("createBy"), root.get("createBy")),
                                cb.lessThan(history.get("id"), root.get("id")));
                query.orderBy(cb.desc(historyCount), cb.desc(root.get("id")));
            }
            default -> {
            }
        }
    }

    private static void addTags(List<Predicate> predicates,
                                Expression<String> tagsExpression,
                                BossOrderQuery filter,
                                jakarta.persistence.criteria.CriteriaBuilder cb) {
        if (!StringUtils.hasText(filter.getTags())) {
            return;
        }
        List<Predicate> tagPredicates = Arrays.stream(filter.getTags().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .map(tag -> cb.greaterThan(
                        cb.function("find_in_set", Integer.class, cb.literal(tag), tagsExpression), 0))
                .toList();
        if (tagPredicates.isEmpty()) {
            return;
        }
        predicates.add("ANY".equalsIgnoreCase(filter.getTagMode())
                ? cb.or(tagPredicates.toArray(Predicate[]::new))
                : cb.and(tagPredicates.toArray(Predicate[]::new)));
    }

    private static void addDistance(List<Predicate> predicates,
                                    Expression<Number> longitude,
                                    Expression<Number> latitude,
                                    BossOrderQuery filter,
                                    jakarta.persistence.criteria.CriteriaBuilder cb) {
        predicates.add(cb.isNotNull(longitude));
        predicates.add(cb.isNotNull(latitude));

        Expression<Double> distance = distanceExpression(longitude, latitude, filter, cb);
        predicates.add(cb.le(distance, filter.getDistanceKm()));
    }

    private static Expression<Double> distanceExpression(Expression<Number> longitude,
                                                         Expression<Number> latitude,
                                                         BossOrderQuery filter,
                                                         jakarta.persistence.criteria.CriteriaBuilder cb) {
        double requestLatitudeRadians = Math.toRadians(filter.getLatitude().doubleValue());
        double requestLongitudeRadians = Math.toRadians(filter.getLongitude().doubleValue());
        Expression<Double> latitudeRadians = cb.function("radians", Double.class, latitude);
        Expression<Double> longitudeRadians = cb.function("radians", Double.class, longitude);
        Expression<Double> longitudeDifference = cb.diff(longitudeRadians, requestLongitudeRadians);

        Expression<Double> firstTerm = cb.prod(
                cb.prod(Math.cos(requestLatitudeRadians),
                        cb.function("cos", Double.class, latitudeRadians)),
                cb.function("cos", Double.class, longitudeDifference));
        Expression<Double> secondTerm = cb.prod(
                Math.sin(requestLatitudeRadians),
                cb.function("sin", Double.class, latitudeRadians));
        Expression<Double> cosine = cb.sum(firstTerm, secondTerm);
        Expression<Double> clampedCosine = cb.function(
                "least", Double.class, cb.literal(1.0),
                cb.function("greatest", Double.class, cb.literal(-1.0), cosine));
        return cb.prod(EARTH_RADIUS_KM, cb.function("acos", Double.class, clampedCosine));
    }
}
