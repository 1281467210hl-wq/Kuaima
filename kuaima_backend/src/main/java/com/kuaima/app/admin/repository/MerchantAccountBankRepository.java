package com.kuaima.app.admin.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kuaima.app.admin.entity.MerchantAccountBank;

/** 商户账户银行信息（后台维护用扩展表）。 */
public interface MerchantAccountBankRepository extends JpaRepository<MerchantAccountBank, Long> {

    Optional<MerchantAccountBank> findByAccountId(Long accountId);

    List<MerchantAccountBank> findByAccountIdIn(Collection<Long> accountIds);
}
