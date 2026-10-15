package com.kuaima.app.domain.boss.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import lombok.Getter;
import lombok.Setter;

/** 老板岗位列表的数据库分页筛选条件。 */
@Getter
@Setter
public class BossOrderQuery {

    private String type;
    private String status;
    private String title;
    private String keyword;
    private String position;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    private Long jobCategoryId;
    private Integer salaryMin;
    private Integer salaryMax;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Double distanceKm;
    private String experience;
    private String gender;
    private String tags;
    private String tagMode = "ALL";
    private Integer durationMin;
    private Integer durationMax;
    private Boolean sameDay;
    private String sort;
    private int page = 0;
    private int size = 10;
}
