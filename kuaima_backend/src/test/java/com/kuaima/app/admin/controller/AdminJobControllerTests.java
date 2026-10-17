package com.kuaima.app.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.kuaima.app.admin.repository.ReportRepository;
import com.kuaima.app.common.service.OssStorageService;
import com.kuaima.app.common.service.RecruitmentMediaPreviewService;
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

    @Test
    void getShouldAttachRejectReasonForRejectedJobDetail() {
        BossOrderRespository orders = mock(BossOrderRespository.class);
        BossOrder order = new BossOrder();
        order.setId(8L);
        order.setOrderStatus("审核拒绝");
        order.setOrderRemark("包吃住，室内工作 [审核拒绝] 信息不完整：缺少工作地址");
        when(orders.findById(8L)).thenReturn(Optional.of(order));
        InsuranceRepository insurances = mock(InsuranceRepository.class);
        when(insurances.findByOrderId(8L)).thenReturn(List.of());

        var data = controller(orders, insurances).get(8L).getData();

        assertEquals("信息不完整：缺少工作地址", data.getString("rejectReason"));
        assertEquals(List.of("包吃住", "室内工作"), data.getJSONArray("benefits").toJavaList(String.class));
    }

    @Test
    void getShouldAttachSubmittedOrderMediaInsteadOfEmployerFields() {
        BossOrderRespository orders = mock(BossOrderRespository.class);
        BossOrder order = new BossOrder();
        order.setId(9L);
        order.setImageKeys("insurance/recruitment/2026/10/16/a.png, insurance/recruitment/2026/10/16/b.jpg");
        order.setVideoKey("insurance/recruitment/2026/10/16/v.mp4");
        when(orders.findById(9L)).thenReturn(Optional.of(order));
        InsuranceRepository insurances = mock(InsuranceRepository.class);
        when(insurances.findByOrderId(9L)).thenReturn(List.of());
        OssStorageService storage = mock(OssStorageService.class);
        when(storage.playableUrl("insurance/recruitment/2026/10/16/a.png"))
                .thenReturn("signed-a.png");
        when(storage.playableUrl("insurance/recruitment/2026/10/16/b.jpg"))
                .thenReturn("signed-b.jpg");
        RecruitmentMediaPreviewService mediaPreview = mock(RecruitmentMediaPreviewService.class);
        when(mediaPreview.previewUrl("insurance/recruitment/2026/10/16/v.mp4"))
                .thenReturn("proxy-v.mp4");

        var data = controller(orders, insurances, storage, mediaPreview).get(9L).getData();

        var materials = data.getJSONArray("submittedMaterials");
        assertEquals(3, materials.size());
        assertEquals("岗位图片1.png", materials.getJSONObject(0).getString("name"));
        assertEquals("signed-a.png", materials.getJSONObject(0).getString("url"));
        assertEquals("岗位图片2.jpg", materials.getJSONObject(1).getString("name"));
        assertEquals("signed-b.jpg", materials.getJSONObject(1).getString("url"));
        assertEquals("岗位视频.mp4", materials.getJSONObject(2).getString("name"));
        assertEquals("proxy-v.mp4", materials.getJSONObject(2).getString("url"));
    }

    private AdminJobController controller(BossOrderRespository orders, InsuranceRepository insurances) {
        return controller(orders, insurances, mock(OssStorageService.class),
                mock(RecruitmentMediaPreviewService.class));
    }

    private AdminJobController controller(BossOrderRespository orders,
                                          InsuranceRepository insurances,
                                          OssStorageService storage,
                                          RecruitmentMediaPreviewService mediaPreview) {
        return new AdminJobController(orders, mock(BossOrderService.class),
                mock(UserRepository.class), mock(BaseOrderItemRespository.class),
                mock(JobCategoryRepository.class), mock(JobIndustryRepository.class),
                mock(ReportRepository.class), insurances, storage, mediaPreview);
    }
}
