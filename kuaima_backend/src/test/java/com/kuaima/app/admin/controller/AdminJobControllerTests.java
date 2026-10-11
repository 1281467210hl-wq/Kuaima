package com.kuaima.app.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.kuaima.app.admin.repository.ReportRepository;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.boss.service.BossOrderService;
import com.kuaima.app.domain.insurance.entity.Insurance;
import com.kuaima.app.domain.insurance.repository.InsuranceRepository;
import com.kuaima.app.domain.jobcategory.repository.JobCategoryRepository;
import com.kuaima.app.domain.jobcategory.repository.JobIndustryRepository;
import com.kuaima.app.domain.user.repository.UserRepository;

class AdminJobControllerTests {

    @Test
    void getShouldAttachInsuranceInfoForJobDetail() {
        BossOrderRespository orders = mock(BossOrderRespository.class);
        InsuranceRepository insurances = mock(InsuranceRepository.class);
        BossOrder order = new BossOrder();
        order.setId(7L);
        when(orders.findById(7L)).thenReturn(Optional.of(order));
        Insurance insurance = new Insurance();
        insurance.setOrderId(7L);
        insurance.setType("意外险");
        insurance.setAmount(330000.0);
        insurance.setPremium(3.0);
        insurance.setStatus("ACTIVE");
        when(insurances.findByOrderId(7L)).thenReturn(List.of(insurance));

        var data = controller(orders, insurances).get(7L).getData();

        var insuranceInfo = data.getJSONObject("insurance");
        assertEquals("意外险", insuranceInfo.getString("type"));
        assertEquals(330000.0, insuranceInfo.getDoubleValue("amount"));
        assertEquals(3.0, insuranceInfo.getDoubleValue("premium"));
        assertEquals(1, insuranceInfo.getIntValue("count"));
    }

    private AdminJobController controller(BossOrderRespository orders, InsuranceRepository insurances) {
        return new AdminJobController(orders, mock(BossOrderService.class),
                mock(UserRepository.class), mock(BaseOrderItemRespository.class),
                mock(JobCategoryRepository.class), mock(JobIndustryRepository.class),
                mock(ReportRepository.class), insurances);
    }
}
