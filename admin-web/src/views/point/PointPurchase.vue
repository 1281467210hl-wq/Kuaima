<template>
  <div>
    <div class="page-header">
      <h1 class="page-title">积分管理</h1>
      <p class="page-desc">平台积分售卖与老板购买订单管理，支持代客下单，1 元 = 100 积分</p>
    </div>

    <div class="stat-cards">
      <div v-for="card in statCards" :key="card.label" class="stat-card">
        <div class="stat-card-header"><span class="stat-card-title">{{ card.label }}</span><div :class="['stat-card-icon', card.color]"><i :class="['fas', card.icon]"></i></div></div>
        <div class="stat-card-value">{{ card.value }}</div>
        <div class="stat-card-change" :class="card.change >= 0 ? 'up' : 'down'"><i :class="['fas', card.change >= 0 ? 'fa-arrow-up' : 'fa-arrow-down']"></i><span>{{ card.compareLabel }} {{ card.change >= 0 ? '+' : '' }}{{ card.change }}%</span></div>
      </div>
    </div>

    <div class="card">
      <div class="filter-bar">
        <div class="filter-item"><span>关键词</span><el-input v-model="filters.keyword" placeholder="企业名称/老板ID/订单号" clearable style="width: 220px" prefix-icon="Search" @keyup.enter="search" /></div>
        <div class="filter-item"><span>支付方式</span><el-select v-model="filters.payMethod" placeholder="全部" clearable style="width: 130px"><el-option label="全部" value="" /><el-option v-for="v in payMethods" :key="v" :label="v" :value="v" /></el-select></div>
        <div class="filter-item"><span>订单状态</span><el-select v-model="filters.status" placeholder="全部" clearable style="width: 130px"><el-option label="全部" value="" /><el-option v-for="v in statuses" :key="v" :label="v" :value="v" /></el-select></div>
        <div class="filter-item"><span>购买日期</span><el-date-picker v-model="filters.date" type="date" value-format="YYYY-MM-DD" placeholder="购买日期" style="width: 150px" /></div>
        <button class="btn btn-primary btn-sm" @click="search"><i class="fas fa-search"></i> 查询</button>
        <button class="btn btn-outline btn-sm" @click="reset"><i class="fas fa-rotate-left"></i> 重置</button>
        <div class="filter-spacer"></div>
        <button class="btn btn-outline btn-sm" @click="openSettings"><i class="fas fa-sliders-h"></i> 积分设置</button>
        <button class="btn btn-outline btn-sm" @click="exportOrders"><i class="fas fa-download"></i> 导出数据</button>
        <button class="btn btn-primary btn-sm" @click="openDrawer"><i class="fas fa-plus"></i> 购买积分</button>
      </div>

      <el-table :data="orders" stripe :header-cell-style="{ background: '#F9FAFB', color: '#6B7280', fontWeight: 500 }">
        <el-table-column prop="orderNo" label="订单号" min-width="150"><template #default="{ row }"><span class="order-no">{{ row.orderNo }}</span></template></el-table-column>
        <el-table-column label="购买老板" min-width="160"><template #default="{ row }"><div class="boss-cell"><span class="mini-avatar" :style="{ background: row.color }">{{ (row.bossName || '?').slice(0, 1) }}</span><div><div>{{ row.bossName || '-' }}</div><small>ID: {{ row.employerId || '-' }}</small></div></div></template></el-table-column>
        <el-table-column prop="companyName" label="企业名称" min-width="170" show-overflow-tooltip />
        <el-table-column prop="points" label="购买积分" width="110"><template #default="{ row }"><span class="points-val">{{ number(row.points) }}</span></template></el-table-column>
        <el-table-column prop="amount" label="实付金额" width="110"><template #default="{ row }"><span class="money-val">¥{{ money(row.amount) }}</span></template></el-table-column>
        <el-table-column label="单价" width="80">0.01元</el-table-column>
        <el-table-column prop="payMethod" label="支付方式" width="110"><template #default="{ row }"><span :class="['pay-tag', payClass(row.payMethod)]"><i :class="payIcon(row.payMethod)"></i>{{ row.payMethod || '-' }}</span></template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{ row }"><span :class="['status-badge', statusClass(row.status)]">{{ row.status || '-' }}</span></template></el-table-column>
        <el-table-column prop="purchaseTime" label="购买时间" min-width="155"><template #default="{ row }">{{ formatTime(row.purchaseTime) }}</template></el-table-column>
        <el-table-column label="操作" width="140" fixed="right"><template #default="{ row }"><div class="action-btns"><el-button v-if="row.status === '待支付'" class="pay-action" type="primary" size="small" @click="openPayment(row)"><i class="fas fa-credit-card"></i>支付</el-button><el-button link type="primary" size="small" @click="showDetail(row)">详情</el-button></div></template></el-table-column>
      </el-table>
      <div class="pagination"><div class="pagination-info">共 {{ total }} 条记录</div><el-pagination v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50, 100]" :total="total" layout="sizes, prev, pager, next, jumper" background @size-change="load" @current-change="load" /></div>
    </div>

    <Teleport to="body">
      <Transition name="modal-fade">
        <div v-if="detailVisible" class="modal-overlay" @click.self="detailVisible = false">
          <div v-if="current" class="purchase-modal">
            <div class="modal-header">
              <div class="modal-title">购买订单详情</div>
              <button class="modal-close" type="button" aria-label="关闭" @click="detailVisible = false"><i class="fas fa-times"></i></button>
            </div>
            <div class="modal-body">
              <div class="d-section-title">订单信息</div>
              <div class="d-grid d-section-grid"><div class="d-item"><span>订单号</span><b class="order-no">{{ current.orderNo }}</b></div><div class="d-item"><span>订单状态</span><b><span :class="['status-badge', statusClass(current.status)]">{{ current.status }}</span></b></div><div class="d-item"><span>购买时间</span><b>{{ formatTime(current.purchaseTime) }}</b></div><div class="d-item"><span>支付方式</span><b>{{ current.payMethod }}</b></div></div>
              <div class="d-section-title">购买信息</div>
              <div class="d-grid d-section-grid"><div class="d-item"><span>购买老板</span><b>{{ current.bossName }}（{{ current.employerId || '-' }}）</b></div><div class="d-item"><span>企业名称</span><b>{{ current.companyName }}</b></div><div class="d-item"><span>购买积分</span><b class="points-val">{{ number(current.points) }} 积分</b></div><div class="d-item"><span>兑换单价</span><b>0.01 元/积分（1元=100积分）</b></div></div>
              <div class="d-section-title">金额信息</div>
              <div class="d-grid"><div class="d-item"><span>实付金额</span><b class="money-val">¥{{ currency(current.amount) }}</b></div><div class="d-item"><span>积分到账</span><b class="arrival">{{ current.status === '支付成功' ? '已实时到账' : '待支付' }}</b></div></div>
            </div>
            <div class="modal-footer"><button class="btn btn-outline" @click="detailVisible = false">关闭</button></div>
          </div>
        </div>
      </Transition>

      <Transition name="modal-fade">
        <div v-if="paymentVisible" class="modal-overlay" @click.self="closePayment">
          <div v-if="paymentCurrent" class="purchase-modal payment-modal">
            <div class="modal-header">
              <div class="modal-title"><i class="fas fa-credit-card payment-title-icon"></i>订单支付</div>
              <button class="modal-close" type="button" aria-label="关闭" @click="closePayment"><i class="fas fa-times"></i></button>
            </div>
            <div class="modal-body">
              <div class="d-section-title payment-section-title">订单信息</div>
              <div class="pay-info-grid">
                <div class="pay-kv"><span>订单号</span><b class="order-no">{{ paymentCurrent.orderNo }}</b></div>
                <div class="pay-kv"><span>订单状态</span><b><span class="status-badge warning">待支付</span></b></div>
                <div class="pay-kv"><span>购买老板</span><b>{{ paymentCurrent.bossName || '-' }}</b></div>
                <div class="pay-kv"><span>老板ID</span><b class="order-no">{{ paymentCurrent.employerId || '-' }}</b></div>
                <div class="pay-kv"><span>企业名称</span><b>{{ paymentCurrent.companyName || '-' }}</b></div>
                <div class="pay-kv"><span>购买积分</span><b class="points-val">{{ number(paymentCurrent.points) }} 积分</b></div>
              </div>
              <div class="d-section-title payment-section-title">应付金额</div>
              <div class="pay-amount-box">
                <div class="label">请在支付截止时间前完成付款</div>
                <div class="money"><small>¥</small>{{ currency(paymentCurrent.amount) }}</div>
                <div class="sub">积分数量 <span>{{ number(paymentCurrent.points) }}</span> · 单价 ¥0.01/积分</div>
              </div>
              <div class="d-section-title payment-section-title">选择支付方式</div>
              <div class="pay-method-tabs">
                <button :class="['pay-method-tab', { active: paymentMethod === '微信支付' }]" type="button" @click="switchPaymentMethod('微信支付')"><i class="fab fa-weixin"></i>微信支付</button>
                <button :class="['pay-method-tab', { active: paymentMethod === '支付宝' }]" type="button" @click="switchPaymentMethod('支付宝')"><i class="fab fa-alipay"></i>支付宝</button>
              </div>
              <div class="pay-qr-wrap">
                <div :class="['pay-qr-box', paymentMethod === '微信支付' ? 'wechat' : 'alipay']">
                  <div class="qr-img">
                    <span v-if="paymentQrLoading" class="qr-loading">正在生成二维码...</span>
                    <span v-else-if="!paymentQrUrl" class="qr-error">二维码暂不可用</span>
                    <template v-else><img :src="paymentQrUrl" :alt="paymentMethod + '二维码'"><div class="qr-icon"><i :class="payIcon(paymentMethod)"></i></div></template>
                  </div>
                  <div class="qr-tip">{{ paymentQrLoading ? '正在连接微信支付' : paymentQrUrl ? `请使用${paymentMethod}扫描二维码完成支付` : paymentQrError }}</div>
                </div>
              </div>
              <div class="pay-countdown">二维码将在 <span class="num">{{ countdownText }}</span> 后过期</div>
            </div>
            <div class="modal-footer payment-footer"><button v-if="paymentMethod === '微信支付'" class="btn btn-outline" type="button" @click="refreshPaymentQr"><i class="fas fa-arrows-rotate"></i>刷新二维码</button><button v-if="paymentMethod === '微信支付'" class="btn btn-primary" type="button" @click="checkPaymentStatus"><i class="fas fa-circle-check"></i>查询支付状态</button><button v-else class="btn btn-primary" type="button" @click="confirmManualPayment"><i class="fas fa-circle-check"></i>确认已收款</button></div>
          </div>
        </div>
      </Transition>

      <div :class="['drawer-mask', { open: drawerVisible }]" @click="drawerVisible = false"></div>
      <aside :class="['purchase-drawer-panel', { open: drawerVisible }]" aria-label="购买积分（代客下单）">
        <div class="drawer-header"><div class="drawer-title"><i class="fas fa-coins"></i>购买积分（代客下单）</div><button class="drawer-close" type="button" aria-label="关闭" @click="drawerVisible = false"><i class="fas fa-times"></i></button></div>
        <div class="drawer-body">
          <div class="form-block">
            <div class="form-block-title">购买老板 <span>*</span></div>
            <el-select v-model="form.bossId" placeholder="请选择购买老板" filterable popper-class="boss-select-popper" style="width:100%">
              <el-option v-for="b in bosses" :key="b.id" :label="bossOptionLabel(b)" :value="b.id">
                <div class="boss-option">
                  <span class="boss-option-name">{{ b.name }}（{{ employerId(b) }}）</span>
                  <span class="boss-option-company">{{ b.companyName || '未填写企业名称' }} · 企业编号：{{ b.companyCode || '-' }}</span>
                </div>
              </el-option>
            </el-select>
          </div>
          <div class="form-block"><div class="form-block-title">积分套餐</div><div class="pkg-grid"><div v-for="pkg in packages" :key="pkg" :class="['pkg-card', { active: form.points === pkg }]" @click="selectPackage(pkg)"><em v-if="packageTag(pkg)">{{ packageTag(pkg) }}</em><div class="pkg-points">{{ packageCount(pkg) }}<small>万</small></div><div class="pkg-money">¥{{ currency(pkg / 100) }}</div></div></div><div class="custom-points"><el-input v-model="customPoints" placeholder="自定义积分数量（100的整数倍）" inputmode="numeric" @input="onCustomPoints" /><span class="unit">积分</span></div></div>
          <div class="form-block"><div class="form-block-title">支付方式 <span>*</span></div><div class="pick-cards"><div v-for="v in payMethods" :key="v" :class="['pick-card', { active: form.payMethod === v }]" @click="form.payMethod = v"><i :class="payIcon(v)"></i>{{ v }}</div></div></div>
          <div class="form-block"><div class="form-block-title">订单处理 <span>*</span></div><div class="pick-cards deal-cards"><div :class="['pick-card', { active: form.deal === 'paid' }]" @click="form.deal = 'paid'"><i class="fas fa-circle-check"></i>确认收款并发放</div><div :class="['pick-card', { active: form.deal === 'pending' }]" @click="form.deal = 'pending'"><i class="fas fa-clock"></i>仅生成待支付订单</div></div><div class="form-tip"><i class="fas fa-circle-info"></i> 选择“确认收款”后积分将实时到账老板账户；“待支付”订单需老板后续完成付款。</div></div>
          <div class="form-block"><div class="form-block-title">订单金额</div><div class="calc-box"><div><span>兑换单价</span><b>0.01 元/积分（1元 = 100积分）</b></div><div><span>购买积分</span><b class="points-val">{{ number(form.points) }} 积分</b></div><div><span>手续费</span><b class="fee-free">¥0.00（平台免收）</b></div><div class="calc-total"><span>应付金额</span><strong><small>¥</small>{{ currency(form.points / 100) }}</strong></div></div></div>
          <div class="form-block"><div class="form-block-title">备注</div><el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填，如：线下转账代录入、活动赠送等" /></div>
        </div>
        <div class="drawer-footer"><button class="btn btn-outline" @click="drawerVisible = false">取消</button><button class="btn btn-primary" :disabled="submitting" @click="submit"><i class="fas fa-check"></i>{{ submitting ? '提交中...' : '确认购买' }}</button></div>
      </aside>

      <div :class="['drawer-mask', { open: settingsVisible }]" @click="settingsVisible = false"></div>
      <aside :class="['settings-drawer-panel', { open: settingsVisible }]" aria-label="积分设置">
        <div class="drawer-header"><div class="drawer-title"><i class="fas fa-sliders-h"></i>积分设置</div><button class="drawer-close" type="button" aria-label="关闭" @click="settingsVisible = false"><i class="fas fa-times"></i></button></div>
        <div class="pts-tab-bar"><div v-for="t in settingsTabs" :key="t.key" :class="['pts-tab', { active: settingsTab === t.key }]" @click="switchSettingsTab(t.key)">{{ t.label }}</div></div>
        <div v-loading="settingsLoading" class="drawer-body">
          <div v-if="settingsTab === 'package'">
            <div class="pts-stat-row">
              <div class="pts-stat-box"><div class="lv">生效套餐</div><div class="vv">{{ settingsStats.enabledPackageCount }}</div></div>
              <div class="pts-stat-box"><div class="lv">本月售卖积分</div><div class="vv" style="color:var(--primary);">{{ number(settingsStats.soldPoints || 0) }}</div></div>
              <div class="pts-stat-box"><div class="lv">本月收入</div><div class="vv" style="color:var(--primary);">¥{{ money(settingsStats.soldAmount || 0) }}</div></div>
            </div>
            <div class="pts-pkg-grid">
              <div v-for="(p, i) in packagesList" :key="p.id || i" :class="['pts-pkg', { rec: p.rec }]">
                <div v-if="p.rec" class="bbadge">🔥 热门推荐</div>
                <div class="bname">{{ p.name }}</div>
                <div class="bsub">{{ p.sub }}</div>
                <span class="bpts">{{ number(p.points || p.pts || 0) }}</span><span class="bunit">积分</span>
                <div class="bprice">¥{{ currency(p.price || p.money || 0) }}<span v-if="p.originalPrice || p.old" style="font-size:12px;color:#9CA3AF;text-decoration:line-through;margin-left:6px;">{{ p.originalPrice || p.old }}</span><span v-if="p.save" class="bold">{{ p.save }}</span></div>
                <div class="bfooter">
                  <div class="bstatus" :class="p.enabled ? 'on' : ''"><i :class="['fas', p.enabled ? 'fa-check-circle' : 'fa-minus-circle']"></i>{{ p.enabled ? '已启用' : '已停用' }}</div>
                  <button class="btn btn-outline btn-sm" @click="openEditPkg(p)">编辑</button>
                </div>
              </div>
            </div>
            <el-empty v-if="!settingsLoading && !packagesList.length" description="暂无积分套餐" />
          </div>

          <div v-if="settingsTab === 'exchange'">
            <div class="pts-stat-row">
              <div class="pts-stat-box"><div class="lv">已启用规则</div><div class="vv">{{ settingsStats.enabledExchangeCount }}</div></div>
              <div class="pts-stat-box"><div class="lv">本月兑换积分</div><div class="vv" style="color:var(--primary);">{{ number(settingsStats.exchangedPoints || 0) }}</div></div>
              <div class="pts-stat-box"><div class="lv">本月兑换老板</div><div class="vv" style="color:var(--primary);">{{ number(settingsStats.exchangedBossCount || 0) }}</div></div>
              <div class="pts-stat-box"><div class="lv">兑换使用率</div><div class="vv" style="color:var(--success);">{{ settingsStats.exchangeRate || 0 }}%</div></div>
            </div>
            <div class="pts-table-toolbar">
              <div class="pts-rule-tip"><i class="fas fa-circle-info"></i> 规则说明：不同业务场景可设置独立兑换比例与触发条件</div>
              <div class="pts-toolbar-spacer"></div>
              <button class="btn btn-primary btn-sm" @click="openEditRule(null)"><i class="fas fa-plus"></i> 新增规则</button>
            </div>
            <div class="pts-table-wrap">
              <table class="pts-rule-table">
                <thead><tr><th style="width:220px;">规则名称</th><th>兑换比例</th><th>每日上限</th><th>兑换条件</th><th>状态</th><th style="width:80px;">操作</th></tr></thead>
                <tbody>
                  <tr v-for="(r, i) in exchangeList" :key="r.id || i">
                    <td><div class="pts-ritem"><div class="pts-ricon" :style="{ background: (r.iconColor || '#FF8C5A') + '22', color: r.iconColor || '#FF8C5A' }"><i :class="['fas', r.icon || 'fa-circle']"></i></div><div><div class="rn">{{ r.name }}</div><div class="pts-subtitle">{{ r.sub }}</div></div></div></td>
                    <td class="rate-cell">{{ r.rate }}</td>
                    <td>{{ r.limit }}</td>
                    <td>{{ r.cond }}</td>
                    <td><el-switch v-model="r.enabled" @change="toggleRule(r, 'exchange')" /></td>
                    <td><button class="btn btn-outline btn-sm" @click="openEditRule(r)">编辑</button></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <el-empty v-if="!settingsLoading && !exchangeList.length" description="暂无兑换规则" />
          </div>

          <div v-if="settingsTab === 'earn'">
            <div class="pts-stat-row">
              <div class="pts-stat-box"><div class="lv">已启用规则</div><div class="vv">{{ settingsStats.enabledEarnCount }}</div></div>
              <div class="pts-stat-box"><div class="lv">本月发放积分</div><div class="vv" style="color:var(--primary);">{{ number(settingsStats.earnedPoints || 0) }}</div></div>
              <div class="pts-stat-box"><div class="lv">本月获取零工</div><div class="vv" style="color:var(--primary);">{{ number(settingsStats.earnedWorkerCount || 0) }}</div></div>
              <div class="pts-stat-box"><div class="lv">积分活跃率</div><div class="vv" style="color:var(--success);">{{ settingsStats.earnActiveRate || 0 }}%</div></div>
            </div>
            <div class="pts-table-toolbar">
              <div class="pts-rule-tip"><i class="fas fa-circle-info"></i> 规则说明：零工完成指定行为后自动获得积分，修改后立即生效，积分按整数发放</div>
              <div class="pts-toolbar-spacer"></div>
              <button class="btn btn-primary btn-sm" @click="openEditEarn(null)"><i class="fas fa-plus"></i> 新增规则</button>
            </div>
            <div class="pts-table-wrap">
              <table class="pts-rule-table">
                <thead><tr><th style="width:220px;">规则名称</th><th>获取积分</th><th>每日上限</th><th>获取条件</th><th>状态</th><th style="width:80px;">操作</th></tr></thead>
                <tbody>
                  <tr v-for="(r, i) in earnList" :key="r.id || i">
                    <td><div class="pts-ritem"><div class="pts-ricon" :style="{ background: (r.iconColor || '#FF8C5A') + '22', color: r.iconColor || '#FF8C5A' }"><i :class="['fas', r.icon || 'fa-coins']"></i></div><div><div class="rn">{{ r.name }}</div><div class="pts-subtitle">{{ r.sub }}</div></div></div></td>
                    <td class="earn-cell">{{ r.rate }}</td>
                    <td>{{ r.limit }}</td>
                    <td>{{ r.cond }}</td>
                    <td><el-switch v-model="r.enabled" @change="toggleRule(r, 'earn')" /></td>
                    <td><button class="btn btn-outline btn-sm" @click="openEditEarn(r)">编辑</button></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <el-empty v-if="!settingsLoading && !earnList.length" description="暂无积分获取规则" />
          </div>
        </div>
      </aside>

      <el-dialog v-model="editModalVisible" :title="editModalTitle" width="520px" :class="{ 'point-package-dialog': editing.type === 'pkg' }" destroy-on-close>
        <el-form v-if="editing.type === 'pkg'" class="point-package-form" label-position="top">
          <el-form-item label="套餐名称"><el-input v-model="editForm.name" placeholder="如：体验套餐" /></el-form-item>
          <el-form-item label="套餐描述"><el-input v-model="editForm.sub" placeholder="如：适合首次购买的中小老板" /></el-form-item>
          <div class="point-package-form-grid">
            <el-form-item label="积分数量"><el-input v-model.number="editForm.pts" type="number" min="100" step="100" /></el-form-item>
            <el-form-item label="套餐价格（元）"><el-input v-model.number="editForm.money" type="number" min="0" step="0.01" /></el-form-item>
          </div>
          <div class="point-package-form-grid">
            <el-form-item label="原价（可选）"><el-input v-model="editForm.old" placeholder="如：¥500" /></el-form-item>
            <el-form-item label="省多少（可选）"><el-input v-model="editForm.save" placeholder="如：省 ¥20" /></el-form-item>
          </div>
          <div class="point-package-switch-row"><span>是否启用</span><el-switch v-model="editForm.enabled" /></div>
        </el-form>

        <el-form v-if="editing.type === 'rule'" label-position="top">
          <el-form-item label="规则名称" required><el-input v-model="editForm.name" placeholder="如：发薪结算" /></el-form-item>
          <el-form-item label="规则标签/场景"><el-input v-model="editForm.sub" placeholder="如：全局适用 / 条件触发" /></el-form-item>
          <el-row :gutter="12">
            <el-col :span="12"><el-form-item label="兑换比例" required><el-input v-model="editForm.rate" placeholder="如：1 分/元" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="每日上限"><el-input v-model="editForm.limit" placeholder="如：200 次 / 无限制" /></el-form-item></el-col>
          </el-row>
          <el-form-item label="兑换条件说明"><el-input v-model="editForm.cond" placeholder="如：每笔发薪金额 × 1 积分" /></el-form-item>
          <el-form-item label="启用状态"><el-switch v-model="editForm.enabled" /></el-form-item>
        </el-form>

        <el-form v-if="editing.type === 'earn'" label-position="top">
          <el-form-item label="规则名称" required><el-input v-model="editForm.name" placeholder="如：完成日结任务" /></el-form-item>
          <el-form-item label="规则标签/场景"><el-input v-model="editForm.sub" placeholder="如：日常任务 / 签到任务 / 邀请拉新" /></el-form-item>
          <el-row :gutter="12">
            <el-col :span="12"><el-form-item label="获取积分" required><el-input v-model="editForm.rate" placeholder="如：5 分/次、1 分/元" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="每日上限"><el-input v-model="editForm.limit" placeholder="如：每日 3 次 / 500 分/天" /></el-form-item></el-col>
          </el-row>
          <el-form-item label="获取条件说明"><el-input v-model="editForm.cond" placeholder="如：完工确认后自动发放" /></el-form-item>
          <el-form-item label="启用状态"><el-switch v-model="editForm.enabled" /></el-form-item>
        </el-form>

        <template #footer>
          <el-button @click="editModalVisible = false">取消</el-button>
          <el-button type="primary" :loading="editSaving" @click="saveEdit"><i v-if="editing.type === 'pkg' && !editSaving" class="fas fa-check"></i>保存</el-button>
        </template>
      </el-dialog>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listBosses } from '@/api/user'
import {
  createPointPurchaseOrder,
  confirmPointPurchasePayment,
  createPointPurchasePayUrl,
  getPointPurchaseOrder,
  exportPointPurchaseOrders,
  getPointPurchaseStats,
  listPointPurchaseOrders,
  getPointSettingsStats,
  listPointPackages,
  savePointPackage,
  listPointExchangeRules,
  savePointExchangeRule,
  togglePointExchangeRule,
  listPointEarnRules,
  savePointEarnRule,
  togglePointEarnRule
} from '@/api/pointPurchase'

const payMethods = ['微信支付', '支付宝', '对公转账']
const statuses = ['支付成功', '待支付', '已退款']
const packages = [10000, 50000, 100000, 500000, 1000000, 5000000]
const filters = reactive({ keyword: '', payMethod: '', status: '', date: '' })
const orders = ref([]); const bosses = ref([]); const total = ref(0); const page = ref(1); const size = ref(10); const stats = reactive({ todayCount: 0, todayPoints: 0, monthPoints: 0, monthAmount: 0, changes: {} })
const drawerVisible = ref(false); const detailVisible = ref(false); const current = ref(null); const customPoints = ref(''); const submitting = ref(false)
const paymentVisible = ref(false); const paymentCurrent = ref(null); const paymentMethod = ref('微信支付'); const paymentSeconds = ref(15 * 60); const paymentQrLoading = ref(false); const paymentQrError = ref('微信支付二维码生成失败，请稍后重试'); let paymentTimer = null; let paymentPollTimer = null
const form = reactive({ bossId: '', points: 0, payMethod: '微信支付', deal: 'paid', remark: '' })
const statCards = computed(() => [{ label: '今日购买笔数', value: number(stats.todayCount), icon: 'fa-file-invoice-dollar', color: '' , change: stats.changes.todayCount || 0, compareLabel: '较昨日' }, { label: '今日售出积分', value: number(stats.todayPoints), icon: 'fa-coins', color: 'purple', change: stats.changes.todayPoints || 0, compareLabel: '较昨日' }, { label: '本月售出积分', value: number(stats.monthPoints), icon: 'fa-chart-line', color: 'purple', change: stats.changes.monthPoints || 0, compareLabel: '较上月' }, { label: '本月收入金额', value: `¥${money(stats.monthAmount)}`, icon: 'fa-sack-dollar', color: 'green', change: stats.changes.monthAmount || 0, compareLabel: '较上月' }])
const number = v => Number(v || 0).toLocaleString('zh-CN')
const money = v => Number(v || 0).toFixed(2)
const currency = v => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const formatTime = v => { if (!v) return '-'; const d = typeof v === 'number' ? new Date(v) : null; if (d) return isNaN(d.getTime()) ? '-' : d.toLocaleString('zh-CN', { hour12: false }).replaceAll('/', '-'); return String(v).replace('T', ' ').slice(0, 19) }
const statusClass = s => ({ '支付成功': 'success', '待支付': 'warning', '已退款': 'default' }[s] || 'default')
const payClass = v => ({ '微信支付': 'pay-wechat', '支付宝': 'pay-alipay', '对公转账': 'pay-bank' }[v] || '')
const payIcon = v => ({ '微信支付': 'fab fa-weixin', '支付宝': 'fab fa-alipay', '对公转账': 'fas fa-building-columns' }[v] || 'fas fa-wallet')
const countdownText = computed(() => paymentSeconds.value <= 0 ? '已过期' : String(Math.floor(paymentSeconds.value / 60)).padStart(2, '0') + ':' + String(paymentSeconds.value % 60).padStart(2, '0'))
const paymentCodeUrl = ref('')
const paymentQrUrl = computed(() => paymentCodeUrl.value ? 'https://api.qrserver.com/v1/create-qr-code/?size=130x130&data=' + encodeURIComponent(paymentCodeUrl.value) : '')
const packageCount = v => v / 10000
const packageTag = v => ({ 500000: '常用', 5000000: '企业推荐' }[v] || '')
const employerId = b => b.companyCode || b.bossCode || `B${String(b.id ?? 0).padStart(7, '0')}`
const bossOptionLabel = b => `${b.name}（${employerId(b)}） · ${b.companyName || '未填写企业名称'}`
const normalize = o => ({ ...o, orderNo: o.orderNo || o.no, bossId: o.bossId || o.bid, employerId: o.employerId || o.companyCode || o.bossCode || (o.bossId != null ? `B${String(o.bossId).padStart(7, '0')}` : ''), bossName: o.bossName || o.boss, companyName: o.companyName || o.company, points: o.points || 0, amount: o.amount ?? o.money ?? 0, payMethod: o.payMethod || o.pay, purchaseTime: o.purchaseTime || o.time, color: o.color || '#7C3AED' })
async function load () { try { const [listRes, statRes] = await Promise.all([listPointPurchaseOrders({ ...filters, page: page.value - 1, size: size.value }), getPointPurchaseStats(filters.date ? { date: filters.date } : {})]); const d = listRes.data; const list = Array.isArray(d) ? d : (d?.content || d?.list || []); orders.value = list.map(normalize); total.value = listRes.total ?? d?.totalElements ?? d?.total ?? list.length; Object.assign(stats, statRes.data || {}) } catch (e) { orders.value = []; total.value = 0; ElMessage.error('加载积分订单失败') } }
async function loadBosses () { try { const r = await listBosses({ page: 0, size: 100 }); const d = r.data; bosses.value = (Array.isArray(d) ? d : (d?.content || d?.list || [])).map(b => ({ ...b, name: b.name || b.nickname || b.username || '-', companyName: b.companyName || b.company || b.enterpriseName || '', companyCode: b.companyCode || b.enterpriseCode || '' })) } catch { bosses.value = [] } }
function search () { page.value = 1; load() }; function reset () { Object.assign(filters, { keyword: '', payMethod: '', status: '', date: '' }); search() }
function showDetail (row) { current.value = row; detailVisible.value = true }
function stopPaymentTimer () { if (paymentTimer) { clearInterval(paymentTimer); paymentTimer = null } }
function stopPaymentPolling () { if (paymentPollTimer) { clearInterval(paymentPollTimer); paymentPollTimer = null } }
function startPaymentTimer () { stopPaymentTimer(); paymentTimer = setInterval(() => { if (paymentSeconds.value <= 0) return stopPaymentTimer(); paymentSeconds.value -= 1 }, 1000) }
async function loadPaymentQr () {
  if (!paymentCurrent.value) return
  paymentQrLoading.value = true; paymentQrError.value = '微信支付二维码生成失败，请稍后重试'; paymentCodeUrl.value = ''
  try {
    const res = await createPointPurchasePayUrl(paymentCurrent.value.orderNo, paymentMethod.value)
    paymentCodeUrl.value = res.data?.codeUrl || ''
    if (!paymentCodeUrl.value) throw new Error('未返回微信支付二维码')
  } catch (_) {
    paymentQrError.value = '微信支付二维码生成失败，请检查支付配置'
  } finally { paymentQrLoading.value = false }
}
async function pollPaymentStatus () {
  if (!paymentCurrent.value) return
  try {
    const res = await getPointPurchaseOrder(paymentCurrent.value.orderNo)
    const order = normalize(res.data || {})
    paymentCurrent.value = { ...paymentCurrent.value, ...order }
    if (order.status === '支付成功') {
      ElMessage.success('订单 ' + order.orderNo + ' 已支付，积分已发放')
      closePayment(); await load()
    }
  } catch (_) { /* 轮询失败时保留弹窗，下一次继续查询 */ }
}
function startPaymentPolling () { stopPaymentPolling(); paymentPollTimer = setInterval(pollPaymentStatus, 3000) }
function openPayment (row) {
  paymentCurrent.value = row; paymentMethod.value = row.payMethod || '微信支付'; paymentSeconds.value = 15 * 60; paymentVisible.value = true; startPaymentTimer()
  if (paymentMethod.value === '微信支付') { startPaymentPolling(); loadPaymentQr() } else { paymentQrError.value = '对公转账请核对平台收款后确认'; paymentCodeUrl.value = ''; }
}
function closePayment () { paymentVisible.value = false; paymentCurrent.value = null; paymentCodeUrl.value = ''; paymentQrLoading.value = false; stopPaymentTimer(); stopPaymentPolling() }
function switchPaymentMethod (method) { if (method !== '微信支付') return ElMessage.info('支付宝扫码支付尚未接入'); paymentMethod.value = method; refreshPaymentQr() }
function refreshPaymentQr () { paymentSeconds.value = 15 * 60; startPaymentTimer(); loadPaymentQr(); ElMessage.success('正在刷新微信支付二维码') }
async function checkPaymentStatus () { await pollPaymentStatus(); if (paymentCurrent.value?.status === '待支付') ElMessage.info('暂未收到支付结果，请完成扫码后重试') }
async function confirmManualPayment () {
  if (!paymentCurrent.value) return
  try {
    await confirmPointPurchasePayment(paymentCurrent.value.orderNo, { payMethod: paymentMethod.value })
    ElMessage.success('订单 ' + paymentCurrent.value.orderNo + ' 已确认收款，积分已发放')
    closePayment(); await load()
  } catch (_) { ElMessage.error('确认收款失败，请稍后重试') }
}
function selectPackage (points) { form.points = points; customPoints.value = number(points) }
function onCustomPoints (v) { const raw = String(v).replace(/\D/g, ''); form.points = raw ? Number(raw) : 0; customPoints.value = raw ? number(form.points) : '' }
function openDrawer () { Object.assign(form, { bossId: '', points: 0, payMethod: '微信支付', deal: 'paid', remark: '' }); customPoints.value = ''; drawerVisible.value = true; if (!bosses.value.length) loadBosses() }
async function submit () { if (!form.bossId) return ElMessage.warning('请选择购买老板'); if (!form.points || form.points < 100 || form.points % 100) return ElMessage.warning('购买积分须为 100 的整数倍且不少于 100'); submitting.value = true; try { await createPointPurchaseOrder({ ...form, amount: form.points / 100 }); ElMessage.success(form.deal === 'paid' ? '购买成功，积分已发放' : '已生成待支付订单'); drawerVisible.value = false; load() } catch { ElMessage.error('创建积分订单失败') } finally { submitting.value = false } }
async function exportOrders () { try { const blob = await exportPointPurchaseOrders({ ...filters }); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = '积分购买订单.xlsx'; a.click(); URL.revokeObjectURL(url) } catch { ElMessage.error('导出失败') } }

const settingsVisible = ref(false); const settingsLoading = ref(false); const editModalVisible = ref(false); const editSaving = ref(false)
const settingsTab = ref('package')
const settingsTabs = [{ key: 'package', label: '积分套餐' }, { key: 'exchange', label: '兑换规则' }, { key: 'earn', label: '积分获取规则' }]
const settingsStats = reactive({ enabledPackageCount: 0, soldPoints: 0, soldAmount: 0, enabledExchangeCount: 0, exchangedPoints: 0, exchangedBossCount: 0, exchangeRate: 0, enabledEarnCount: 0, earnedPoints: 0, earnedWorkerCount: 0, earnActiveRate: 0 })
const packagesList = ref([])
const exchangeList = ref([])
const earnList = ref([])
const editing = reactive({ type: '', id: null })
const editForm = reactive({ name: '', sub: '', pts: 0, money: 0, old: '', save: '', rec: false, enabled: true, rate: '', limit: '', cond: '', icon: '', iconColor: '' })
const editModalTitle = computed(() => { if (editing.type === 'pkg') return editing.id ? '编辑积分套餐' : '新增积分套餐'; if (editing.type === 'rule') return editing.id ? '编辑兑换规则' : '新增兑换规则'; if (editing.type === 'earn') return editing.id ? '编辑积分获取规则' : '新增积分获取规则'; return '编辑' })
function switchSettingsTab (tab) { settingsTab.value = tab }
async function openSettings () { settingsVisible.value = true; settingsLoading.value = true; try { await Promise.all([loadSettingsStats(), loadPackages(), loadExchangeRules(), loadEarnRules()]) } catch (_) { ElMessage.error('加载积分设置失败') } finally { settingsLoading.value = false } }
function openEditPkg (p) { editing.type = 'pkg'; editing.id = p?.id ?? null; Object.assign(editForm, { name: p?.name || '', sub: p?.sub || '', pts: p?.pts || p?.points || 0, money: p?.money || p?.price || 0, old: p?.old || p?.originalPrice || '', save: p?.save || '', rec: !!p?.rec, enabled: p?.enabled ?? true, rate: '', limit: '', cond: '' }); editModalVisible.value = true }
function openEditRule (r) { editing.type = 'rule'; editing.id = r?.id ?? null; Object.assign(editForm, { name: r?.name || '', sub: r?.sub || '全局适用', pts: 0, money: 0, old: '', save: '', rec: false, enabled: r?.enabled ?? true, rate: r?.rate || '', limit: r?.limit || '', cond: r?.cond || '', icon: r?.icon || 'fa-circle', iconColor: r?.iconColor || '#64748B' }); editModalVisible.value = true }
function openEditEarn (r) { editing.type = 'earn'; editing.id = r?.id ?? null; Object.assign(editForm, { name: r?.name || '', sub: r?.sub || '日常任务', pts: 0, money: 0, old: '', save: '', rec: false, enabled: r?.enabled ?? true, rate: r?.rate || '', limit: r?.limit || '', cond: r?.cond || '', icon: r?.icon || 'fa-coins', iconColor: r?.iconColor || '#64748B' }); editModalVisible.value = true }
async function saveEdit () { editSaving.value = true; try { if (editing.type === 'pkg') { if (!editForm.name) return ElMessage.warning('请输入套餐名称'); if (!editForm.pts || editForm.pts < 100) return ElMessage.warning('积分数量不合法'); const payload = { id: editing.id || undefined, name: editForm.name, sub: editForm.sub, points: editForm.pts, price: Number(editForm.money), originalPrice: editForm.old, save: editForm.save, rec: editForm.rec, enabled: editForm.enabled }; await savePointPackage(payload); ElMessage.success('套餐已保存'); await Promise.all([loadPackages(), loadSettingsStats()]) } else if (editing.type === 'rule') { if (!editForm.name) return ElMessage.warning('请输入规则名称'); if (!editForm.rate) return ElMessage.warning('请输入兑换比例'); const payload = { id: editing.id || undefined, name: editForm.name, sub: editForm.sub, rate: editForm.rate, limit: editForm.limit, cond: editForm.cond, enabled: editForm.enabled, icon: editForm.icon, iconColor: editForm.iconColor }; await savePointExchangeRule(payload); ElMessage.success('兑换规则已保存'); await Promise.all([loadExchangeRules(), loadSettingsStats()]) } else if (editing.type === 'earn') { if (!editForm.name) return ElMessage.warning('请输入规则名称'); if (!editForm.rate) return ElMessage.warning('请输入获取积分'); const payload = { id: editing.id || undefined, name: editForm.name, sub: editForm.sub, rate: editForm.rate, limit: editForm.limit, cond: editForm.cond, enabled: editForm.enabled, icon: editForm.icon, iconColor: editForm.iconColor }; await savePointEarnRule(payload); ElMessage.success('积分获取规则已保存'); await Promise.all([loadEarnRules(), loadSettingsStats()]) } editModalVisible.value = false } catch (e) { const msg = e?.response?.data?.message || e?.message || '保存失败'; ElMessage.error(msg) } finally { editSaving.value = false } }
async function toggleRule (r, type) { try { if (type === 'exchange') await togglePointExchangeRule(r.id); else if (type === 'earn') await togglePointEarnRule(r.id); await loadSettingsStats(); ElMessage.success('状态已更新') } catch (_) { r.enabled = !r.enabled; ElMessage.error('更新状态失败') } }
const responseList = data => Array.isArray(data) ? data : (data?.content || data?.list || [])
async function loadSettingsStats () { const r = await getPointSettingsStats(); Object.assign(settingsStats, { enabledPackageCount: 0, soldPoints: 0, soldAmount: 0, enabledExchangeCount: 0, exchangedPoints: 0, exchangedBossCount: 0, exchangeRate: 0, enabledEarnCount: 0, earnedPoints: 0, earnedWorkerCount: 0, earnActiveRate: 0 }, r.data || {}) }
async function loadPackages () { const r = await listPointPackages(); packagesList.value = responseList(r.data) }
async function loadExchangeRules () { const r = await listPointExchangeRules(); exchangeList.value = responseList(r.data) }
async function loadEarnRules () { const r = await listPointEarnRules(); earnList.value = responseList(r.data) }
onMounted(() => { load(); loadBosses() })
onUnmounted(() => { stopPaymentTimer(); stopPaymentPolling() })
</script>

<style scoped>
.filter-bar { display:flex; align-items:center; gap:10px; flex-wrap:wrap; }
.filter-item { display:flex; align-items:center; gap:8px; }
.filter-item > span { color:var(--text-secondary); font-size:13px; white-space:nowrap; }
.filter-spacer { flex:1; }
.boss-cell { display:flex; align-items:center; gap:10px; }.boss-cell small { display:block; margin-top:2px; color:var(--text-muted); font-size:12px; }.mini-avatar { width:32px; height:32px; display:inline-flex; align-items:center; justify-content:center; flex-shrink:0; border-radius:8px; color:#fff; font-weight:600; }.order-no { color:var(--primary); font-family:monospace; font-size:12px; }.points-val { color:#9333EA; font-weight:600; }.money-val { color:var(--danger); font-weight:700; }.down { color:var(--danger) !important; }
.pay-tag { display:inline-flex; align-items:center; gap:5px; color:#4b5563; font-size:12px; white-space:nowrap; }.pay-tag i { font-size:14px; }.pay-wechat i { color:#07c160; }.pay-alipay i { color:#1677ff; }.pay-bank i { color:#6b7280; }
.action-btns { display:flex; align-items:center; gap:4px; white-space:nowrap; }.pay-action { padding:5px 9px; }.pay-action i { margin-right:4px; }
.modal-overlay { position:fixed; inset:0; z-index:1000; display:flex; align-items:center; justify-content:center; padding:24px; background:rgba(0,0,0,.5); }
.purchase-modal { width:90%; max-width:600px; max-height:90vh; overflow:hidden; background:#fff; border-radius:16px; box-shadow:0 20px 50px rgba(0,0,0,.18); }
.modal-header { display:flex; align-items:center; justify-content:space-between; padding:20px 24px; border-bottom:1px solid var(--border); }
.modal-title { color:var(--text-primary); font-size:18px; font-weight:600; }
.modal-close,.drawer-close { padding:0; border:0; background:transparent; }
.modal-close { width:28px; height:28px; display:flex; align-items:center; justify-content:center; border-radius:6px; color:var(--text-muted); cursor:pointer; }.modal-close:hover { color:var(--text-primary); background:var(--bg-page); }
.modal-body { max-height:60vh; overflow-y:auto; padding:24px; }.modal-footer { display:flex; justify-content:flex-end; gap:12px; padding:16px 24px; border-top:1px solid var(--border); }
.modal-fade-enter-active,.modal-fade-leave-active { transition:opacity .2s ease; }.modal-fade-enter-active .purchase-modal,.modal-fade-leave-active .purchase-modal { transition:transform .2s ease; }.modal-fade-enter-from,.modal-fade-leave-to { opacity:0; }.modal-fade-enter-from .purchase-modal,.modal-fade-leave-to .purchase-modal { transform:translateY(8px) scale(.98); }
.d-section-title { margin:0 0 12px; padding-bottom:8px; color:var(--text-primary); font-size:14px; font-weight:600; border-bottom:1px solid var(--border); }.d-section-grid { margin-bottom:20px; }.d-grid { display:grid; grid-template-columns:repeat(2,1fr); gap:14px 24px; }.d-item { display:flex; flex-direction:column; gap:4px; min-width:0; }.d-item > span { color:var(--text-muted); font-size:12px; }.d-item > b { color:var(--text-primary); font-size:14px; font-weight:500; overflow-wrap:anywhere; }.d-item > b.arrival { color:var(--success); }
.payment-modal { max-width:560px; }.payment-title-icon { margin-right:7px; color:var(--primary); }.payment-section-title { margin-top:0; }.pay-info-grid { display:grid; grid-template-columns:1fr 1fr; gap:10px 24px; margin-bottom:14px; }.pay-kv { display:flex; align-items:center; min-width:0; gap:10px; font-size:13px; }.pay-kv > span { flex-shrink:0; width:66px; color:var(--text-secondary); }.pay-kv > b { min-width:0; overflow-wrap:anywhere; color:var(--text-primary); font-weight:500; }.pay-amount-box { margin-bottom:16px; padding:10px 0 8px; text-align:center; background:#fffaf6; border-radius:8px; }.pay-amount-box .label { color:var(--text-secondary); font-size:12px; }.pay-amount-box .money { margin-top:4px; color:var(--primary); font-size:30px; font-weight:700; letter-spacing:-1px; }.pay-amount-box .money small { margin-right:3px; font-size:15px; font-weight:500; }.pay-amount-box .sub { margin-top:2px; color:var(--text-secondary); font-size:12px; }.pay-amount-box .sub span { color:var(--primary); font-weight:600; }.pay-method-tabs { display:flex; gap:10px; margin-bottom:16px; }.pay-method-tab { flex:1; display:flex; align-items:center; justify-content:center; gap:8px; padding:11px 0; color:var(--text-secondary); font-size:14px; cursor:pointer; background:#fff; border:1px solid var(--border); border-radius:10px; transition:all .2s; }.pay-method-tab:hover { color:var(--primary); border-color:var(--primary); }.pay-method-tab.active { color:var(--primary); font-weight:600; background:#fff7f0; border-color:var(--primary); box-shadow:0 0 0 2px rgba(255,107,53,.1); }.pay-method-tab i.fa-weixin { color:#07c160; }.pay-method-tab i.fa-alipay { color:#1677ff; }.pay-method-tab.active i { color:var(--primary); }.pay-qr-wrap { display:flex; justify-content:center; }.pay-qr-box { padding:18px 24px; text-align:center; border-radius:12px; }.pay-qr-box.wechat { background:#f0fbf2; border:1px solid #d1fadf; }.pay-qr-box.alipay { background:#eff6ff; border:1px solid #bfdbfe; }.qr-img { position:relative; display:flex; align-items:center; justify-content:center; width:150px; height:150px; margin-bottom:10px; padding:10px; background:#fff; border-radius:8px; box-sizing:border-box; }.pay-qr-box.wechat .qr-img { border:1px dashed #07c160; }.pay-qr-box.alipay .qr-img { border:1px dashed #1677ff; }.qr-img img { display:block; width:130px; height:130px; }.qr-icon { position:absolute; top:50%; left:50%; display:flex; align-items:center; justify-content:center; width:30px; height:30px; color:#fff; font-size:13px; border-radius:6px; transform:translate(-50%,-50%); }.wechat .qr-icon { background:#07c160; }.alipay .qr-icon { background:#1677ff; }.qr-icon i { color:#fff !important; }.qr-tip { font-size:12px; }.wechat .qr-tip { color:#14532d; }.alipay .qr-tip { color:#1e3a8a; }.pay-countdown { margin-top:10px; color:var(--text-secondary); font-size:12px; text-align:center; }.pay-countdown .num { color:var(--danger); font-weight:600; }.payment-footer { justify-content:flex-end; }.payment-footer .btn { flex:0 0 auto; }
.drawer-mask { position:fixed; inset:0; z-index:1000; visibility:hidden; opacity:0; background:rgba(0,0,0,.45); transition:opacity .25s,visibility .25s; }.drawer-mask.open { visibility:visible; opacity:1; }
.purchase-drawer-panel { position:fixed; top:0; right:0; bottom:0; z-index:1001; width:520px; max-width:92vw; display:flex; flex-direction:column; background:#fff; box-shadow:-8px 0 30px rgba(0,0,0,.12); transform:translateX(105%); transition:transform .28s ease; }.purchase-drawer-panel.open { transform:translateX(0); }
.drawer-header { display:flex; align-items:center; justify-content:space-between; flex-shrink:0; padding:18px 24px; border-bottom:1px solid var(--border); }.drawer-body { flex:1; overflow-y:auto; padding:20px 24px; }.drawer-footer { display:flex; flex-shrink:0; gap:12px; padding:14px 24px; border-top:1px solid var(--border); }.drawer-footer .btn { flex:1; justify-content:center; }
.drawer-title { display:flex; align-items:center; gap:8px; color:var(--text-primary); font-size:16px; font-weight:600; }.drawer-title i { color:var(--primary); }
.drawer-close { width:30px; height:30px; display:flex; align-items:center; justify-content:center; border-radius:6px; color:var(--text-muted); cursor:pointer; }.drawer-close:hover { color:var(--text-primary); background:var(--bg-page); }
.form-block { margin-bottom:22px; }.form-block-title { display:flex; align-items:center; gap:6px; margin-bottom:10px; color:var(--text-primary); font-size:13px; font-weight:600; }.form-block-title::before { content:''; width:3px; height:13px; border-radius:2px; background:var(--primary); }.form-block-title span { color:var(--danger); }
.pkg-grid { display:grid; grid-template-columns:repeat(3,1fr); gap:10px; }.pkg-card { position:relative; padding:12px 8px; text-align:center; cursor:pointer; background:#fff; border:1.5px solid var(--border); border-radius:10px; transition:.15s; }.pkg-card:hover { border-color:#ffb98a; }.pkg-card.active { border-color:#9333ea; background:#faf5ff; }.pkg-points { color:#9333ea; font-size:15px; font-weight:700; font-variant-numeric:tabular-nums; }.pkg-points small { font-size:11px; font-weight:500; }.pkg-money { margin-top:3px; color:var(--text-muted); font-size:12px; }.pkg-card.active .pkg-money { color:#9333ea; }.pkg-card em { position:absolute; top:-7px; right:-4px; padding:1px 7px; color:#fff; font-size:10px; font-style:normal; font-weight:600; background:linear-gradient(135deg,#ff6b35,#ff8c5a); border-radius:8px; }
.custom-points { display:flex; align-items:center; gap:8px; margin-top:10px; }.custom-points :deep(.el-input) { flex:1; }.custom-points .unit { color:var(--text-muted); font-size:13px; white-space:nowrap; }
:global(.boss-select-popper .el-select-dropdown__item) { height:auto; min-height:52px; padding:8px 14px; line-height:normal; }
:global(.boss-select-popper .boss-option) { display:flex; flex-direction:column; gap:5px; min-width:0; }
:global(.boss-select-popper .boss-option-name) { overflow:hidden; color:var(--text-primary); font-size:14px; font-weight:500; text-overflow:ellipsis; white-space:nowrap; }
:global(.boss-select-popper .boss-option-company) { overflow:hidden; color:var(--text-muted); font-size:12px; text-overflow:ellipsis; white-space:nowrap; }
:global(.boss-select-popper .el-select-dropdown__item.is-selected .boss-option-name), :global(.boss-select-popper .el-select-dropdown__item.is-selected .boss-option-company) { color:var(--primary); }
.pick-cards { display:flex; gap:10px; }.pick-card { flex:1; padding:12px 10px; color:var(--text-secondary); font-size:13px; text-align:center; cursor:pointer; background:#fff; border:1.5px solid var(--border); border-radius:10px; transition:.15s; }.pick-card i { display:block; margin-bottom:6px; color:var(--text-muted); font-size:18px; }.pick-card.active { color:var(--primary); font-weight:600; background:#fff7f0; border-color:var(--primary); }.pick-card.active i { color:var(--primary); }.pick-card .fa-weixin { color:#07c160; }.pick-card .fa-alipay { color:#1677ff; }.pick-card .fa-building-columns { color:#6b7280; }
.calc-box { padding:14px 16px; border-radius:12px; background:linear-gradient(135deg,#faf5ff,#fff7f0); }.calc-box > div { display:flex; justify-content:space-between; align-items:center; padding:5px 0; color:var(--text-secondary); font-size:13px; }.calc-box b { color:var(--text-primary); font-weight:500; }.calc-box .fee-free { color:var(--success); }.calc-total { margin-top:6px; padding-top:10px !important; border-top:1px dashed #e9d5ff; }.calc-total span { color:var(--text-primary); font-weight:600; }.calc-total strong { color:var(--danger); font-size:24px; }.calc-total strong small { font-size:13px; }.form-tip { display:flex; align-items:flex-start; gap:5px; margin-top:8px; color:var(--text-muted); font-size:12px; line-height:1.5; }.form-tip i { margin-top:2px; color:#ffb98a; }
.settings-drawer-panel { position:fixed; top:0; right:0; bottom:0; z-index:1001; width:860px; max-width:94vw; display:flex; flex-direction:column; background:#fff; box-shadow:-8px 0 30px rgba(0,0,0,.12); transform:translateX(105%); transition:transform .28s ease; }.settings-drawer-panel.open { transform:translateX(0); }
.pts-tab-bar { display:flex; gap:4px; padding:0 20px; flex-shrink:0; border-bottom:1px solid var(--border); background:#fff; }.pts-tab { padding:12px 22px; font-size:14px; color:var(--text-secondary); cursor:pointer; border-bottom:2px solid transparent; font-weight:500; transition:all .2s; }.pts-tab:hover { color:var(--primary); }.pts-tab.active { color:var(--primary); border-bottom-color:var(--primary); }
.pts-stat-row { display:flex; gap:12px; margin-bottom:16px; }.pts-stat-box { flex:1; background:#fff; border:1px solid var(--border); border-radius:10px; padding:12px 14px; }.pts-stat-box .lv { font-size:11px; color:var(--text-muted); }.pts-stat-box .vv { font-size:20px; font-weight:700; color:var(--text-primary); margin-top:2px; }
.pts-pkg-grid { display:grid; grid-template-columns:1fr 1fr; gap:14px; }.pts-pkg { border:2px solid #F3F4F6; border-radius:10px; padding:16px; position:relative; transition:all .2s; background:#fff; }.pts-pkg:hover { border-color:var(--primary); }.pts-pkg.rec { border-color:var(--primary); background:linear-gradient(180deg,#FFF9F5,#fff); }.pts-pkg .bname { font-size:15px; font-weight:600; color:var(--text-primary); }.pts-pkg .bsub { font-size:12px; color:var(--text-muted); margin:2px 0 10px; }.pts-pkg .bpts { font-size:26px; font-weight:700; background:linear-gradient(135deg,#FF8C5A,#FF6B35); -webkit-background-clip:text; -webkit-text-fill-color:transparent; background-clip:text; }.pts-pkg .bunit { font-size:12px; color:var(--text-secondary); font-weight:500; margin-left:4px; }.pts-pkg .bprice { font-size:18px; font-weight:600; color:var(--text-primary); margin-top:6px; }.pts-pkg .bold { font-size:11px; color:var(--primary); background:#FFF0EB; padding:2px 8px; border-radius:10px; margin-left:6px; }.pts-pkg .bbadge { position:absolute; top:0; right:14px; background:linear-gradient(135deg,#FF8C5A,#FF6B35); color:#fff; font-size:11px; padding:3px 10px; border-radius:0 0 8px 8px; }.pts-pkg .bfooter { margin-top:12px; display:flex; justify-content:space-between; align-items:center; }.pts-pkg .bstatus { font-size:12px; color:#9CA3AF; display:flex; align-items:center; gap:4px; }.pts-pkg .bstatus.on { color:var(--success); }
.pts-table-toolbar { display:flex; gap:8px; align-items:center; margin-bottom:12px; }.pts-toolbar-spacer { flex:1; }.pts-rule-tip { font-size:12px; color:var(--text-muted); display:flex; align-items:center; gap:5px; }.pts-rule-tip i { color:#FFB98A; }
.pts-table-wrap { border:1px solid var(--border); border-radius:10px; overflow:hidden; }
.pts-rule-table { width:100%; border-collapse:collapse; font-size:13px; }.pts-rule-table th, .pts-rule-table td { padding:10px 12px; text-align:left; border-bottom:1px solid #F3F4F6; }.pts-rule-table th { background:#FAFAFA; font-weight:600; color:var(--text-secondary); font-size:12px; }.pts-ritem { display:flex; align-items:center; gap:8px; }.pts-ricon { width:28px; height:28px; border-radius:7px; display:flex; align-items:center; justify-content:center; font-size:12px; flex-shrink:0; }.pts-ritem .rn { font-weight:500; }.pts-subtitle { font-size:12px; color:var(--text-muted); margin-top:2px; }.rate-cell { color:var(--primary); font-weight:600; }.earn-cell { color:var(--success); font-weight:600; }
:deep(.point-package-dialog) { border-radius:16px; overflow:hidden; }
:deep(.point-package-dialog .el-dialog__header) { margin-right:0; padding:20px 24px; border-bottom:1px solid var(--border); }
:deep(.point-package-dialog .el-dialog__title) { color:var(--text-primary); font-size:18px; font-weight:600; }
:deep(.point-package-dialog .el-dialog__headerbtn) { top:20px; right:24px; width:28px; height:28px; border-radius:6px; }
:deep(.point-package-dialog .el-dialog__headerbtn:hover) { background:var(--bg-page); }
:deep(.point-package-dialog .el-dialog__body) { padding:24px; max-height:60vh; overflow-y:auto; }
:deep(.point-package-dialog .el-dialog__footer) { padding:16px 24px; border-top:1px solid var(--border); }
:deep(.point-package-dialog .el-dialog__footer .el-button) { min-width:88px; height:38px; border-radius:8px; font-size:14px; }
:deep(.point-package-dialog .el-dialog__footer .el-button > span) { display:flex; align-items:center; gap:6px; }
:deep(.point-package-dialog .el-dialog__footer .el-button--primary) { background:var(--primary); border-color:var(--primary); }
:deep(.point-package-form .el-form-item) { margin-bottom:16px; }
:deep(.point-package-form .el-form-item__label) { height:auto; padding:0; margin-bottom:6px; color:var(--text-primary); font-size:13px; font-weight:500; line-height:20px; }
:deep(.point-package-form .el-input__wrapper) { min-height:38px; padding:1px 12px; border:1px solid var(--border); border-radius:8px; box-shadow:none; }
:deep(.point-package-form .el-input__wrapper:hover), :deep(.point-package-form .el-input__wrapper.is-focus) { border-color:var(--primary); box-shadow:none; }
:deep(.point-package-form .el-input__inner) { font-size:14px; }
.point-package-form-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px; }
.point-package-switch-row { display:flex; align-items:center; gap:10px; margin-top:2px; color:var(--text-primary); font-size:13px; font-weight:500; }
:deep(.point-package-switch-row .el-switch.is-checked .el-switch__core) { border-color:var(--primary); background:var(--primary); }
@media (max-width:640px) { .modal-overlay { padding:14px; }.purchase-modal { width:100%; }.d-grid { grid-template-columns:1fr; }.pick-cards { flex-direction:column; }.pts-pkg-grid { grid-template-columns:1fr; }.pts-stat-row { flex-direction:column; }.settings-drawer-panel { width:94vw; } }
.qr-loading, .qr-error { color:var(--text-muted); font-size:12px; text-align:center; }
</style>
