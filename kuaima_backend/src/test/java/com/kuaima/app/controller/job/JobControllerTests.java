package com.kuaima.app.controller.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.service.BossOrderService;
import com.kuaima.app.domain.browsehistory.entity.BrowseHistory;
import com.kuaima.app.domain.browsehistory.repository.BrowseHistoryRepository;
import com.kuaima.app.domain.jobfavorite.repository.JobFavoriteRepository;
import com.kuaima.app.domain.enterprise.entity.Enterprise;
import com.kuaima.app.domain.enterprise.repository.EnterpriseRepository;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.common.service.OssStorageService;
import com.kuaima.app.common.service.RecruitmentMediaPreviewService;

class JobControllerTests {

    @Test
    void recordBrowse_shouldAcceptJsonIntegerValues() {
        BrowseHistoryRepository historyRepository = mock(BrowseHistoryRepository.class);
        when(historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        JobController controller = new JobController(
                mock(JobFavoriteRepository.class), historyRepository,
                mock(BossOrderRespository.class), mock(BossOrderService.class));
        Map<String, Object> body = new HashMap<>();
        body.put("userId", Integer.valueOf(2));
        body.put("orderId", Integer.valueOf(19));

        BrowseHistory saved = controller.recordBrowse(body).getData();

        assertEquals(2L, saved.getUserId());
        assertEquals(19L, saved.getOrderId());
    }

    @Test
    void publicJobDetail_shouldReturnEnterpriseCompanyName() {
        BossOrderRespository orders = mock(BossOrderRespository.class);
        EnterpriseRepository enterprises = mock(EnterpriseRepository.class);
        BossOrder order = new BossOrder();
        order.setId(9L);
        order.setOrderStatus("招工中");
        order.setEnterpriseId(3L);
        order.setCreateBy(8L);
        order.setLongitude(new java.math.BigDecimal("121.473701"));
        order.setLatitude(new java.math.BigDecimal("31.230416"));
        Enterprise enterprise = new Enterprise();
        enterprise.setId(3L);
        enterprise.setCompanyName("快马科技");
        User boss = new User();
        boss.setId(8L);
        boss.setContactPhone("13800138000");
        UserRepository users = mock(UserRepository.class);
        when(orders.findById(9L)).thenReturn(java.util.Optional.of(order));
        when(enterprises.findById(3L)).thenReturn(java.util.Optional.of(enterprise));
        when(users.findById(8L)).thenReturn(java.util.Optional.of(boss));
        JobController controller = new JobController(mock(JobFavoriteRepository.class),
                mock(BrowseHistoryRepository.class), orders, mock(BossOrderService.class),
                enterprises, null, users);

        var result = controller.getPublicJob(9L);

        assertEquals("快马科技", result.getData().getCompanyName());
        assertEquals("13800138000", result.getData().getContactPhone());
        assertEquals(new java.math.BigDecimal("121.473701"), result.getData().getLongitude());
        assertEquals(new java.math.BigDecimal("31.230416"), result.getData().getLatitude());
    }

    @Test
    void publicJobDetail_shouldUseSameDomainVideoProxy() {
        BossOrderRespository orders = mock(BossOrderRespository.class);
        BossOrder order = new BossOrder();
        order.setOrderStatus("招工中");
        order.setVideoKey("insurance/recruitment/2026/10/15/e9b4d56a626c4376b3bcf9d8bad8289e.mp4");
        when(orders.findById(9L)).thenReturn(java.util.Optional.of(order));
        RecruitmentMediaPreviewService previewService = mock(RecruitmentMediaPreviewService.class);
        when(previewService.previewUrl(order.getVideoKey()))
                .thenReturn("https://ke.taifang.xyz/api/public/recruitment-media?objectKey=x");

        JobController controller = new JobController(mock(JobFavoriteRepository.class),
                mock(BrowseHistoryRepository.class), orders, mock(BossOrderService.class),
                null, mock(OssStorageService.class), null, previewService);

        var result = controller.getPublicJob(9L);

        assertEquals("https://ke.taifang.xyz/api/public/recruitment-media?objectKey=x",
                result.getData().getVideoUrl());
    }
}
