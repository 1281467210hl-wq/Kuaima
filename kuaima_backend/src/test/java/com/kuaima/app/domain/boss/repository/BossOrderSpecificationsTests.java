package com.kuaima.app.domain.boss.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.model.BossOrderQuery;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

class BossOrderSpecificationsTests {

    @Test
    void from_shouldCombineOwnerStatusDateAndSalaryWithAnd() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Root<BossOrder> root = mock(Root.class);
        CriteriaQuery<?> criteriaQuery = mock(CriteriaQuery.class);
        Path<Long> owner = mock(Path.class);
        Path<String> status = mock(Path.class);
        Path<java.util.Date> startTime = mock(Path.class);
        Path<Integer> salary = mock(Path.class);
        Path<Integer> duration = mock(Path.class);
        Path<String> position = mock(Path.class);
        when(root.<Long>get("createBy")).thenReturn(owner);
        when(root.<String>get("orderStatus")).thenReturn(status);
        when(root.<java.util.Date>get("startTime")).thenReturn(startTime);
        when(root.<Integer>get("salary")).thenReturn(salary);
        when(root.<Integer>get("duration")).thenReturn(duration);
        when(root.<String>get("postion")).thenReturn(position);

        BossOrderQuery filter = new BossOrderQuery();
        filter.setStatus("招工中");
        filter.setStartDate(LocalDate.of(2026, 9, 8));
        filter.setEndDate(LocalDate.of(2026, 9, 8));
        filter.setSalaryMin(100);
        filter.setSalaryMax(200);
        filter.setPosition("仓储");
        filter.setDurationMin(3);
        filter.setDurationMax(6);

        BossOrderSpecifications.from(2L, filter).toPredicate(root, criteriaQuery, cb);

        verify(cb).equal(owner, 2L);
        verify(cb).equal(status, "招工中");
        verify(cb).greaterThanOrEqualTo(eq(startTime), any(java.util.Date.class));
        verify(cb).lessThan(eq(startTime), any(java.util.Date.class));
        verify(cb).greaterThanOrEqualTo(salary, 100);
        verify(cb).lessThanOrEqualTo(salary, 200);
        verify(cb).like(position, "%仓储%");
        verify(cb).greaterThanOrEqualTo(duration, 3);
        verify(cb).lessThanOrEqualTo(duration, 6);
        verify(cb, atLeastOnce()).and(any(Predicate[].class));
    }

    @Test
    void from_shouldUseAndForAllTagsAndOrForAnyTags() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Root<BossOrder> root = mock(Root.class);
        CriteriaQuery<?> criteriaQuery = mock(CriteriaQuery.class);
        Path<String> tags = mock(Path.class);
        Expression<String> firstLiteral = mock(Expression.class);
        Expression<String> secondLiteral = mock(Expression.class);
        Expression<Integer> firstPosition = mock(Expression.class);
        Expression<Integer> secondPosition = mock(Expression.class);
        Predicate firstMatch = mock(Predicate.class);
        Predicate secondMatch = mock(Predicate.class);
        when(root.<String>get("tags")).thenReturn(tags);
        when(cb.literal("包吃住")).thenReturn(firstLiteral);
        when(cb.literal("日结")).thenReturn(secondLiteral);
        when(cb.function("find_in_set", Integer.class, firstLiteral, tags)).thenReturn(firstPosition);
        when(cb.function("find_in_set", Integer.class, secondLiteral, tags)).thenReturn(secondPosition);
        when(cb.greaterThan(firstPosition, 0)).thenReturn(firstMatch);
        when(cb.greaterThan(secondPosition, 0)).thenReturn(secondMatch);

        BossOrderQuery all = new BossOrderQuery();
        all.setTags("包吃住,日结");
        BossOrderSpecifications.from(null, all).toPredicate(root, criteriaQuery, cb);

        verify(cb, times(2)).and(any(Predicate[].class));

        BossOrderQuery any = new BossOrderQuery();
        any.setTags("包吃住,日结");
        any.setTagMode("ANY");
        BossOrderSpecifications.from(null, any).toPredicate(root, criteriaQuery, cb);

        verify(cb).or(any(Predicate[].class));
    }
}
