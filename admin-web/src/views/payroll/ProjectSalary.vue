<template>
  <div>
    <div class="page-header">
      <h1 class="page-title">闪电发薪</h1>
      <p class="page-desc">签约考勤·入职离职项目统一管理，支持一键制单、批量发薪与转账审批</p>
    </div>

    <!-- 合并卡片：已提交 / 已审核 / 转账记录 / 余额查询 Tab 切换 -->
    <div class="card panel-card">
      <div class="title-switch-group">
        <span class="title-switch" :class="{ active: tab === 'submitted' }" @click="switchTab('submitted')">已提交的发薪单 <span class="count-pill">{{ submittedCount }}</span></span>
        <span class="title-switch-divider"></span>
        <span class="title-switch" :class="{ active: tab === 'reviewed' }" @click="switchTab('reviewed')">已审核的发薪单 <span class="count-pill">{{ reviewedCount }}</span></span>
        <span class="title-switch-divider"></span>
        <span class="title-switch" :class="{ active: tab === 'transfer' }" @click="switchTab('transfer')">转账记录</span>
        <span class="title-switch-divider"></span>
        <span class="title-switch" :class="{ active: tab === 'balance' }" @click="switchTab('balance')">余额查询</span>
      </div>

      <!-- 已提交的发薪单 -->
      <div v-show="tab === 'submitted'">
        <div class="filter-bar tight">
          <div class="filter-item">
            <span class="filter-label">所属公司</span>
            <select v-model="filters.company">
              <option value="all">全部公司</option>
              <option v-for="c in companyOptions" :key="c" :value="c">{{ c }}</option>
            </select>
          </div>
          <div class="filter-item">
            <span class="filter-label">所属项目</span>
            <select v-model="filters.projectName">
              <option value="all">全部项目</option>
              <option v-for="p in projectOptions" :key="p" :value="p">{{ p }}</option>
            </select>
          </div>
          <div class="filter-item keyword-item">
            <span class="filter-label">标题</span>
            <div class="search-box no-margin">
              <i class="fas fa-search"></i>
              <input type="text" placeholder="发薪单标题 / 制单人员" v-model="filters.keyword">
            </div>
          </div>
          <button class="btn btn-primary btn-sm" @click="handleSearch"><i class="fas fa-search"></i> 查询</button>
          <button class="btn btn-outline btn-sm" @click="handleReset"><i class="fas fa-arrow-rotate-left"></i> 重置</button>
          <button class="btn btn-primary btn-sm" style="margin-left:auto;" @click="openCreate"><i class="fas fa-plus"></i> 新建发薪</button>
        </div>
        <div class="table-container">
          <el-table :data="payrollList" v-loading="loading" :header-cell-style="headerStyle">
            <el-table-column prop="company" label="所属公司" min-width="170" show-overflow-tooltip />
            <el-table-column prop="title" label="薪单标题" min-width="150" show-overflow-tooltip />
            <el-table-column prop="projectName" label="所属项目" min-width="150" show-overflow-tooltip />
            <el-table-column label="应发金额" min-width="100" align="right">
              <template #default="{ row }"><span class="money">{{ fmtMoney(row.amount) }}</span></template>
            </el-table-column>
            <el-table-column label="应发人数" width="90" align="center">
              <template #default="{ row }">{{ row.peopleCount || 0 }} 人</template>
            </el-table-column>
            <el-table-column prop="creator" label="制单人员" width="95" />
            <el-table-column prop="submitTime" label="提交时间" width="130" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <span :class="['status-badge', statusClass(row.status)]">{{ statusText(row.status) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" align="right">
              <template #default="{ row }">
                <div class="action-btns" style="justify-content:flex-end;">
                  <button class="btn btn-outline" @click="goDetail(row)">查看</button>
                  <template v-if="row.status === 'pending'">
                    <button class="btn btn-outline danger-btn" @click="doReject(row)">驳回</button>
                    <button class="btn btn-outline success-btn" @click="doApprove(row)">通过</button>
                  </template>
                  <template v-else>
                    <button class="btn btn-outline" @click="exportStub(row)">下载</button>
                  </template>
                </div>
              </template>
            </el-table-column>
            <template #empty><div class="empty-cell plain">暂无数据</div></template>
          </el-table>
        </div>
        <div class="pagination">
          <div class="pagination-info">共 {{ total }} 条记录</div>
          <el-pagination v-model:current-page="currentPage" :page-size="pageSize"
            :total="total" layout="prev, pager, next" background
            @current-change="loadData" />
        </div>
      </div>

      <!-- 已审核的发薪单 -->
      <div v-show="tab === 'reviewed'">
        <div class="filter-bar tight">
          <div class="filter-item">
            <span class="filter-label">所属公司</span>
            <select v-model="filters.company">
              <option value="all">全部公司</option>
              <option v-for="c in companyOptions" :key="c" :value="c">{{ c }}</option>
            </select>
          </div>
          <div class="filter-item">
            <span class="filter-label">所属项目</span>
            <select v-model="filters.projectName">
              <option value="all">全部项目</option>
              <option v-for="p in projectOptions" :key="p" :value="p">{{ p }}</option>
            </select>
          </div>
          <div class="filter-item keyword-item">
            <span class="filter-label">标题</span>
            <div class="search-box no-margin">
              <i class="fas fa-search"></i>
              <input type="text" placeholder="发薪单标题 / 制单人员 / 审核人员" v-model="revFilters.keyword">
            </div>
          </div>
          <div class="filter-item">
            <span class="filter-label">提交时间</span>
            <input type="date" class="date-input" v-model="revFilters.submitFrom">
            <span class="date-sep">至</span>
            <input type="date" class="date-input" v-model="revFilters.submitTo">
          </div>
          <div class="filter-item">
            <span class="filter-label">审核时间</span>
            <input type="date" class="date-input" v-model="revFilters.reviewFrom">
            <span class="date-sep">至</span>
            <input type="date" class="date-input" v-model="revFilters.reviewTo">
          </div>
          <button class="btn btn-primary btn-sm" @click="handleSearch"><i class="fas fa-search"></i> 查询</button>
          <button class="btn btn-outline btn-sm" @click="handleReset"><i class="fas fa-arrow-rotate-left"></i> 重置</button>
        </div>
        <div class="table-container">
          <el-table :data="payrollList" v-loading="loading" :header-cell-style="headerStyle">
            <el-table-column prop="company" label="所属公司" min-width="170" show-overflow-tooltip />
            <el-table-column prop="title" label="薪单标题" min-width="140" show-overflow-tooltip />
            <el-table-column prop="projectName" label="所属项目" min-width="150" show-overflow-tooltip />
            <el-table-column label="应发金额" min-width="100" align="right">
              <template #default="{ row }"><span class="money">{{ fmtMoney(row.amount) }}</span></template>
            </el-table-column>
            <el-table-column label="应发人数" width="90" align="center">
              <template #default="{ row }">{{ row.peopleCount || 0 }} 人</template>
            </el-table-column>
            <el-table-column prop="creator" label="制单人员" width="95" />
            <el-table-column prop="submitTime" label="提交时间" width="130" />
            <el-table-column prop="reviewBy" label="审核人员" width="95" />
            <el-table-column prop="reviewTime" label="审核时间" width="130" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <span :class="['status-badge', statusClass(row.status)]">{{ statusText(row.status) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" align="right">
              <template #default="{ row }">
                <div class="action-btns" style="justify-content:flex-end;">
                  <button class="btn btn-outline" @click="goDetail(row)">查看</button>
                  <button class="btn btn-outline" @click="exportStub(row)">下载</button>
                </div>
              </template>
            </el-table-column>
            <template #empty><div class="empty-cell plain">暂无数据</div></template>
          </el-table>
        </div>
        <div class="pagination">
          <div class="pagination-info">共 {{ total }} 条记录</div>
          <el-pagination v-model:current-page="currentPage" :page-size="pageSize"
            :total="total" layout="prev, pager, next" background
            @current-change="loadData" />
        </div>
      </div>

      <!-- 转账记录 -->
      <div v-show="tab === 'transfer'">
        <div class="filter-bar tight">
          <div class="filter-item">
            <span class="filter-label">所属公司</span>
            <select v-model="trFilters.company">
              <option value="all">全部公司</option>
              <option v-for="c in trCompanyOptions" :key="c" :value="c">{{ c }}</option>
            </select>
          </div>
          <div class="filter-item">
            <span class="filter-label">状态</span>
            <select v-model="trFilters.status">
              <option value="all">全部状态</option>
              <option value="success">转账成功</option>
              <option value="processing">处理中</option>
              <option value="failed">转账失败</option>
            </select>
          </div>
          <div class="filter-item keyword-item">
            <span class="filter-label">关键词</span>
            <div class="search-box no-margin">
              <i class="fas fa-search"></i>
              <input type="text" placeholder="转账单号 / 收款方" v-model="trFilters.keyword">
            </div>
          </div>
          <div class="filter-item">
            <span class="filter-label">转账时间</span>
            <input type="date" class="date-input" v-model="trFilters.dateFrom">
            <span class="date-sep">至</span>
            <input type="date" class="date-input" v-model="trFilters.dateTo">
          </div>
          <button class="btn btn-primary btn-sm" @click="applyTransferFilters"><i class="fas fa-search"></i> 查询</button>
          <button class="btn btn-outline btn-sm" @click="resetTransfers"><i class="fas fa-arrow-rotate-left"></i> 重置</button>
          <button class="btn btn-outline btn-sm" style="margin-left:auto;" @click="exportTransfers"><i class="fas fa-download"></i> 导出</button>
        </div>
        <div class="table-container">
          <el-table :data="transfers" v-loading="trLoading" :header-cell-style="headerStyle">
            <el-table-column label="转账单号" min-width="140">
              <template #default="{ row }"><span class="mono no-primary">{{ row.no }}</span></template>
            </el-table-column>
            <el-table-column prop="company" label="所属公司" min-width="170" show-overflow-tooltip />
            <el-table-column label="付款账号" min-width="110">
              <template #default="{ row }"><span class="mono">{{ row.payAccount }}</span></template>
            </el-table-column>
            <el-table-column label="转账金额" min-width="110" align="right">
              <template #default="{ row }"><span class="amount-primary">{{ fmtMoney(row.amount) }}</span></template>
            </el-table-column>
            <el-table-column prop="payee" label="收款方" min-width="90" />
            <el-table-column label="收款账号" min-width="120">
              <template #default="{ row }"><span class="mono">{{ row.recvAccount }}</span></template>
            </el-table-column>
            <el-table-column prop="time" label="转账时间" min-width="130" />
            <el-table-column label="状态" min-width="100">
              <template #default="{ row }">
                <span :class="['status-badge', trStatusClass(row.status)]">{{ trStatusText(row.status) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" align="right">
              <template #default="{ row }">
                <div class="action-btns" style="justify-content:flex-end;">
                  <button class="btn btn-outline btn-sm" @click="openTransferDetail(row)">详情</button>
                </div>
              </template>
            </el-table-column>
            <template #empty>
              <div class="empty-cell"><i class="fas fa-inbox"></i><div>暂无符合条件的转账记录</div></div>
            </template>
          </el-table>
        </div>
        <div class="pagination">
          <div class="pagination-info">共 {{ trTotal }} 条记录</div>
          <el-pagination v-model:current-page="trPage" :page-size="pageSize"
            :total="trTotal" layout="prev, pager, next" background
            @current-change="loadTransfers" />
        </div>
      </div>

      <!-- 余额查询 -->
      <div v-show="tab === 'balance'" v-loading="balLoading">
        <div class="filter-bar">
          <div class="filter-item">
            <span class="filter-label">所属公司</span>
            <select v-model="balCompany">
              <option value="all">全部公司</option>
              <option v-for="c in balCompanyOptions" :key="c" :value="c">{{ c }}</option>
            </select>
          </div>
          <button class="btn btn-outline btn-sm" @click="refreshBalance"><i class="fas fa-rotate"></i> 刷新</button>
          <button class="btn btn-primary btn-sm" style="margin-left:auto;" @click="openAddAccount"><i class="fas fa-plus"></i> 新增账户</button>
        </div>
        <div v-if="filteredAccounts.length" class="balance-grid">
          <div class="acct-card" v-for="(a, idx) in filteredAccounts" :key="idx">
            <div class="acct-head">
              <span class="acct-short">{{ a.short }}</span>
              <span v-if="a.isDefault" class="default-tag">默认账户</span>
            </div>
            <div class="acct-line"><span class="acct-label">主体全称</span><span class="acct-value">{{ a.company }}</span></div>
            <div class="acct-line"><span class="acct-label">商户号</span><span class="acct-value">{{ a.merchant }}</span></div>
            <div class="acct-line"><span class="acct-label">账户余额</span><span class="acct-balance">¥ {{ a.balanceYuan }}</span></div>
            <div class="acct-actions">
              <button class="btn btn-outline btn-sm danger-text" @click="deleteAccount(idx)"><i class="fas fa-trash"></i> 删除</button>
              <button class="btn btn-outline btn-sm success-text" @click="openRecharge(a)"><i class="fas fa-wallet"></i> 充值</button>
              <button class="btn btn-outline btn-sm" @click="openEditAccount(a)"><i class="fas fa-pen-to-square"></i> 编辑账户信息</button>
            </div>
          </div>
        </div>
        <div v-else class="empty-cell"><i class="fas fa-inbox"></i><div>暂无账户</div></div>
      </div>
    </div>

    <!-- 创建发薪单弹窗 -->
    <el-dialog v-model="createVisible" title="创建发薪单" width="520px" class="proto-dialog">
      <div class="form-group">
        <label class="form-label">转账标题</label>
        <input class="form-input" type="text" placeholder="请输入转账标题，如：9月8日零工工资" v-model="createForm.title">
      </div>
      <div class="form-group">
        <label class="form-label">关联项目</label>
        <select class="form-select" v-model="createForm.projectName">
          <option value="">请选择关联项目</option>
          <option v-for="p in projectOptions" :key="p" :value="p">{{ p }}</option>
        </select>
      </div>
      <div class="form-group">
        <label class="form-label">转账类型</label>
        <div class="type-switch">
          <div class="type-option" :class="{ active: createForm.type === 'wage' }" @click="createForm.type = 'wage'">工资</div>
          <div class="type-option" :class="{ active: createForm.type === 'advance' }" @click="createForm.type = 'advance'">预支</div>
          <div class="type-option" :class="{ active: createForm.type === 'other' }" @click="createForm.type = 'other'">其他</div>
        </div>
      </div>
      <div class="payroll-tip">
        <i class="fas fa-circle-info"></i>
        <span>发薪流程：① 开批发薪 → ② 添加人员 → ③ 设置薪资 → ④ 提交支付，提交后需管理员审批通过才会打款</span>
      </div>
      <template #footer>
        <button class="btn btn-secondary" @click="createVisible = false">取消</button>
        <button class="btn btn-primary" @click="submitCreate">确定</button>
      </template>
    </el-dialog>

    <!-- 转账明细弹窗 -->
    <el-dialog v-model="trDetailVisible" title="转账明细" width="720px" class="proto-dialog">
      <div class="detail-info-grid" style="margin-bottom:16px;">
        <div class="detail-info-item"><span class="detail-info-label">转账单号</span><span class="detail-info-value mono">{{ trDetail.no }}</span></div>
        <div class="detail-info-item"><span class="detail-info-label">所属公司</span><span class="detail-info-value">{{ trDetail.company }}</span></div>
        <div class="detail-info-item"><span class="detail-info-label">转账金额</span><span class="detail-info-value amount-primary">{{ fmtMoney(trDetail.amount) }}</span></div>
        <div class="detail-info-item"><span class="detail-info-label">转账时间</span><span class="detail-info-value">{{ trDetail.time }}</span></div>
      </div>
      <div class="table-container bordered">
        <el-table :data="trDetail.details || []" :header-cell-style="headerStyle">
          <el-table-column label="姓名" min-width="110">
            <template #default="{ row }">
              <div class="manager-cell">
                <span class="mini-avatar warm">{{ (row.name || '?').charAt(0) }}</span>
                <span>{{ row.name }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="岗位" min-width="90">
            <template #default="{ row }"><span class="wb-tag">{{ row.job }}</span></template>
          </el-table-column>
          <el-table-column label="手机号" min-width="120">
            <template #default="{ row }"><span class="mono">{{ row.phone }}</span></template>
          </el-table-column>
          <el-table-column label="出勤" min-width="70">
            <template #default="{ row }">{{ row.days }}天</template>
          </el-table-column>
          <el-table-column label="日薪" min-width="80">
            <template #default="{ row }">¥{{ fmtYuan(row.daily) }}</template>
          </el-table-column>
          <el-table-column label="应发金额(元)" min-width="110" align="right">
            <template #default="{ row }"><span class="amount-primary">¥{{ fmtYuan(row.amount) }}</span></template>
          </el-table-column>
          <el-table-column label="状态" min-width="90">
            <template #default="{ row }">
              <span :class="['status-badge', trDetailStatusClass(row.status)]">{{ trDetailStatusText(row.status) }}</span>
            </template>
          </el-table-column>
          <template #empty><div class="empty-cell plain">暂无明细</div></template>
        </el-table>
      </div>
      <template #footer>
        <button class="btn btn-secondary" @click="trDetailVisible = false">关闭</button>
      </template>
    </el-dialog>

    <!-- 账户编辑弹窗 -->
    <el-dialog v-model="acctVisible" :title="acctMode === 'edit' ? '编辑账户信息' : '新增账户'" width="480px" class="proto-dialog">
      <div class="form-group">
        <label class="form-label">公司简称</label>
        <input class="form-input" type="text" placeholder="请输入公司简称" v-model="acctForm.short">
      </div>
      <div class="form-group">
        <label class="form-label">主体全称</label>
        <input class="form-input" type="text" placeholder="请输入主体全称" v-model="acctForm.company">
      </div>
      <div class="form-group">
        <label class="form-label">商户号</label>
        <input class="form-input" type="text" placeholder="请输入商户号" v-model="acctForm.merchant">
      </div>
      <div class="form-group">
        <label class="form-label">开户银行</label>
        <input class="form-input" type="text" placeholder="请输入开户银行" v-model="acctForm.bank">
      </div>
      <template #footer>
        <button class="btn btn-secondary" @click="acctVisible = false">取消</button>
        <button class="btn btn-primary" @click="saveAccount"><i class="fas fa-check"></i> 保存</button>
      </template>
    </el-dialog>

    <!-- 删除账户确认弹窗 -->
    <el-dialog v-model="delVisible" width="380px" class="proto-dialog no-head" :show-close="false">
      <div class="del-confirm">
        <div class="del-icon"><i class="fas fa-circle-exclamation"></i></div>
        <div class="del-title">确认删除该账户？</div>
        <div class="del-desc">删除后无法恢复，请谨慎操作。</div>
      </div>
      <template #footer>
        <div class="del-footer">
          <button class="btn btn-secondary" @click="delVisible = false">取消</button>
          <button class="btn btn-primary danger-bg" @click="confirmDeleteAccount">确认删除</button>
        </div>
      </template>
    </el-dialog>

    <!-- 账户充值弹窗 -->
    <el-dialog v-model="rcVisible" title="账户充值" width="520px" class="proto-dialog">
      <div class="rc-account-box">
        <div>
          <div class="rc-short">{{ rcAccount?.short || '—' }}</div>
          <div class="rc-merchant">商户号 <span>{{ rcAccount?.merchant || '—' }}</span></div>
        </div>
        <span class="rc-tag">充值账户</span>
      </div>
      <div class="form-group">
        <label class="form-label">充值金额（元）</label>
        <input class="form-input" type="number" placeholder="请输入充值金额" v-model="rcAmount" @input="clearRcPreset">
        <div class="rc-presets">
          <span v-for="v in RC_PRESETS" :key="v" class="rc-preset" :class="{ active: rcPreset === v }" @click="setRcPreset(v)">¥ {{ v.toLocaleString('zh-CN') }}</span>
        </div>
      </div>
      <div class="form-group" style="margin-top:18px;">
        <label class="form-label">支付方式</label>
        <div class="rc-pay-grid">
          <div class="pay-method wechat" :class="{ active: rcPayType === 'wechat' }" @click="selectRcPay('wechat')">
            <div class="pay-icon wechat"><i class="fab fa-weixin"></i></div>
            <div>
              <div class="pay-name">微信支付</div>
              <div class="pay-desc">微信扫码付款</div>
            </div>
            <div class="pay-check"></div>
          </div>
          <div class="pay-method alipay" :class="{ active: rcPayType === 'alipay' }" @click="selectRcPay('alipay')">
            <div class="pay-icon alipay"><i class="fab fa-alipay"></i></div>
            <div>
              <div class="pay-name">支付宝支付</div>
              <div class="pay-desc">支付宝扫码付款</div>
            </div>
            <div class="pay-check"></div>
          </div>
        </div>
      </div>
      <div v-if="rcQrVisible" class="rc-qr-panel">
        <div class="rc-qr-box" :class="rcPayType"><i class="fas fa-qrcode"></i></div>
        <div class="rc-qr-amount">¥ {{ rcAmountText }}</div>
        <div class="rc-qr-tip">{{ rcPayType === 'wechat' ? '请使用微信扫一扫，扫描二维码完成付款' : '请使用支付宝扫一扫，扫描二维码完成付款' }}</div>
        <div class="rc-qr-wait"><i class="fas fa-circle-notch spin"></i> 等待扫码支付…</div>
      </div>
      <template #footer>
        <button class="btn btn-secondary" @click="rcVisible = false">取消支付</button>
        <button v-if="rcQrVisible" class="btn btn-primary" @click="rcPaySuccess"><i class="fas fa-check"></i> 模拟支付成功</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listPayrolls, createPayroll, approvePayroll, rejectPayroll } from '@/api/payroll'
import { listBossOptions } from '@/api/recruit'
import {
  listTransfers, getTransferDetails, exportTransfers as apiExportTransfers,
  listMerchantAccounts, createMerchantAccount, updateMerchantAccount, deleteMerchantAccount,
} from '@/api/transfer'

const router = useRouter()

const tab = ref('submitted')
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const payrollList = ref([])
const submittedCount = ref(0)
const reviewedCount = ref(0)

const headerStyle = { background: '#F3F4F6', color: '#6B7280', fontWeight: 500 }

const filters = reactive({ company: 'all', projectName: 'all', keyword: '' })
const revFilters = reactive({ keyword: '', submitFrom: '', submitTo: '', reviewFrom: '', reviewTo: '' })

const companyOptions = computed(() => [...new Set(payrollList.value.map((x) => x.company).filter((c) => c && c !== '—'))])
const projectOptions = computed(() => [...new Set(payrollList.value.map((x) => x.projectName).filter((p) => p && p !== '—'))])

const fen2yuan = (fen) => {
  if (fen == null || fen === '') return '0.00'
  const n = Number(fen)
  if (isNaN(n)) return '0.00'
  return (n / 100).toFixed(2)
}

/** 分 → 金额文本（原型 fmtMoney：整数不带小数，两位小数内保留） */
const fmtMoney = (fen) => {
  const yuan = Number(fen || 0) / 100
  return '¥' + yuan.toLocaleString('zh-CN', { minimumFractionDigits: yuan % 1 === 0 ? 0 : 2, maximumFractionDigits: 2 })
}
/** 已是元的数值 → 保留两位小数的金额文本 */
const fmtYuan = (yuan) => Number(yuan || 0).toFixed(2)

const pick = (obj, keys, fallback) => {
  for (const k of keys) {
    if (obj && obj[k] != null && obj[k] !== '') return obj[k]
  }
  return fallback
}

const statusText = (s) => ({ pending: '待审批', approved: '审批通过', rejected: '已驳回', withdrawn: '已撤回' }[s] || s || '—')
const statusClass = (s) => ({ pending: 'warning', approved: 'success', rejected: 'danger', withdrawn: 'danger' }[s] || 'default')

const formatTime = (t) => {
  if (!t) return '—'
  const s = String(t).replace('T', ' ')
  return s.length > 16 ? s.substring(0, 16) : s
}

// 时间字段兼容：列表/详情页统一回退链
const pickSubmitTime = (o) => pick(o, ['submitTime', 'submit_time', 'commitTime', 'commit_time', 'createdAt', 'createTime', 'create_time', 'created_at'])
const pickReviewTime = (o) => pick(o, ['reviewTime', 'review_time', 'approvedAt', 'approved_at'])
const pickReviewBy = (o) => pick(o, ['reviewBy', 'review_by', 'reviewer', 'approvedBy', 'approved_by'], '—')
const pickCreator = (o) => pick(o, ['creator', 'createdBy', 'created_by', 'creatorName'], '—')

const normalize = (o) => ({
  id: o.id,
  orderNo: pick(o, ['orderNo', 'order_no', 'no']),
  company: o.company || '—',
  title: o.title || '—',
  projectName: pick(o, ['projectName', 'project_name'], '—'),
  type: pick(o, ['type'], 'wage'),
  amount: o.amount ?? 0,
  peopleCount: pick(o, ['peopleCount', 'people_count', 'memberCount', 'workerCount'], 0),
  creator: pickCreator(o),
  submitTime: formatTime(pickSubmitTime(o)),
  reviewBy: pickReviewBy(o),
  reviewTime: formatTime(pickReviewTime(o)),
  status: o.status || 'pending',
})

const loadData = async () => {
  loading.value = true
  try {
    const keyword = (tab.value === 'reviewed' ? revFilters.keyword : filters.keyword) || undefined
    const res = await listPayrolls({
      tab: tab.value,
      keyword,
      page: currentPage.value - 1,
      size: pageSize.value,
    })
    let list = (res.data?.content || res.data?.list || (Array.isArray(res.data) ? res.data : [])).map(normalize)
    // 客户端补充过滤：公司 / 项目 / 时间区间（后端仅支持 keyword/projectId）
    if (filters.company !== 'all') list = list.filter((x) => x.company === filters.company)
    if (filters.projectName !== 'all') list = list.filter((x) => x.projectName === filters.projectName)
    if (tab.value === 'reviewed') {
      const dayMatch = (t, from, to) => {
        if (!t || t === '—') return true
        const d = t.slice(0, 10)
        if (from && d < from) return false
        if (to && d > to) return false
        return true
      }
      list = list.filter((x) => dayMatch(x.submitTime, revFilters.submitFrom, revFilters.submitTo))
      list = list.filter((x) => dayMatch(x.reviewTime, revFilters.reviewFrom, revFilters.reviewTo))
    }
    payrollList.value = list
    const hasLocal = filters.company !== 'all' || filters.projectName !== 'all'
    total.value = hasLocal ? list.length : (res.total ?? list.length)
    if (tab.value === 'submitted') submittedCount.value = total.value
    else reviewedCount.value = total.value
  } catch (err) {
    console.warn('[ProjectSalary] 加载失败:', err.message)
    payrollList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// tab 计数角标：拉取两个列表各自的总数
const loadTabCounts = async () => {
  try {
    const [sub, rev] = await Promise.all([
      listPayrolls({ tab: 'submitted', page: 0, size: 1 }),
      listPayrolls({ tab: 'reviewed', page: 0, size: 1 }),
    ])
    const countOf = (r) => r?.total ?? (r?.data?.content?.length ?? (Array.isArray(r?.data) ? r.data.length : 0)) ?? 0
    submittedCount.value = countOf(sub)
    reviewedCount.value = countOf(rev)
  } catch (err) {
    console.warn('[ProjectSalary] 计数加载失败:', err.message)
  }
}

const switchTab = (t) => {
  tab.value = t
  if (t === 'submitted' || t === 'reviewed') {
    currentPage.value = 1
    loadData()
  } else if (t === 'transfer') {
    trPage.value = 1
    loadTransfers()
  } else if (t === 'balance') {
    loadAccounts()
  }
}

const handleSearch = () => { currentPage.value = 1; loadData() }
const handleReset = () => {
  filters.company = 'all'; filters.projectName = 'all'; filters.keyword = ''
  revFilters.keyword = ''; revFilters.submitFrom = ''; revFilters.submitTo = ''
  revFilters.reviewFrom = ''; revFilters.reviewTo = ''
  handleSearch()
}

const goDetail = (row) => router.push(`/admin/payroll-detail/${row.id}`)

// ===== 新建发薪 =====
const createVisible = ref(false)
const createForm = reactive({ title: '', projectName: '', type: 'wage' })

const openCreate = () => {
  createForm.title = ''; createForm.projectName = ''; createForm.type = 'wage'
  createVisible.value = true
}
const submitCreate = async () => {
  if (!createForm.title.trim()) { ElMessage.warning('请输入转账标题'); return }
  if (!createForm.projectName) { ElMessage.warning('请选择关联项目'); return }
  try {
    await createPayroll({
      order: {
        title: createForm.title.trim(),
        company: null,
        projectName: createForm.projectName,
        type: createForm.type,
      },
      details: [],
    })
    ElMessage.success('发薪单创建成功，请添加人员后提交审批')
    createVisible.value = false
    handleSearch(); loadTabCounts()
  } catch (err) {
    console.warn('[ProjectSalary] 创建失败:', err.message)
    ElMessage.error('创建失败，请确认后端服务已启动')
  }
}

const doApprove = async (row) => {
  try { await approvePayroll(row.id); ElMessage.success('已通过'); loadData() }
  catch (err) { ElMessage.error('审批失败') }
}
const doReject = async (row) => {
  try { await rejectPayroll(row.id); ElMessage.success('已驳回'); loadData() }
  catch (err) { ElMessage.error('驳回失败') }
}

/** 把任意字符串包上引号（CSV 安全）。 */
const csvCell = (v) => {
  if (v == null || v === '') return '""'
  const s = String(v).replace(/"/g, '""')
  return `"${s}"`
}

/** 通用：二维数组 → CSV Blob 下载。 */
const downloadCsv = (rows, filename) => {
  const bom = '﻿' // 防 Excel 中文乱码
  const content = bom + rows.map(row => row.map(csvCell).join(',')).join('\r\n')
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

/** 下载单条薪单凭证 CSV（列表页"下载"按钮）。 */
const exportStub = (row) => {
  if (!row) return
  const rows = [
    ['薪单标题', row.title || ''],
    ['发薪单号', row.orderNo || ''],
    ['所属公司', row.company || ''],
    ['所属项目', row.projectName || ''],
    ['转账类型', ({ wage: '工资', advance: '预支', other: '其他' })[row.type] || row.type || ''],
    ['应发人数', `${row.peopleCount ?? 0}`],
    ['应发金额(元)', fen2yuan(row.amount)],
    ['制单人员', row.creator || ''],
    ['提交时间', row.submitTime || ''],
    ['审核人', row.reviewBy || ''],
    ['审核时间', row.reviewTime || ''],
    ['状态', statusText(row.status)],
  ]
  const filename = `发薪单_${row.orderNo || row.id}_${row.title || ''}.csv`.replace(/[\\/:*?"<>|]/g, '_')
  downloadCsv(rows, filename)
  ElMessage.success('导出成功')
}

// ===== 转账记录（GET /admin/transfers，列表/分页均走服务端） =====
const trFilters = reactive({ company: 'all', status: 'all', keyword: '', dateFrom: '', dateTo: '' })
const transfers = ref([])
const trLoading = ref(false)
const trPage = ref(1)
const trTotal = ref(0)
const trCompanyOptions = computed(() => [...new Set(transfers.value.map((t) => t.company).filter(Boolean))])

const trStatusText = (s) => ({ success: '转账成功', processing: '处理中', failed: '转账失败' }[s] || s || '—')
const trStatusClass = (s) => ({ success: 'success', processing: 'warning', failed: 'danger' }[s] || 'default')

/** 组装当前筛选 → 后端查询参数（所属公司经 bossKeyword 命中老板公司名，状态/关键词/日期区间直传）。 */
const buildTransferQuery = () => ({
  bossKeyword: trFilters.company !== 'all' ? trFilters.company : undefined,
  status: trFilters.status,
  keyword: trFilters.keyword.trim(),
  startDate: trFilters.dateFrom || undefined,
  endDate: trFilters.dateTo || undefined,
})

const loadTransfers = async () => {
  trLoading.value = true
  try {
    const res = await listTransfers({
      ...buildTransferQuery(),
      page: trPage.value - 1,
      size: pageSize.value,
    })
    transfers.value = res.records.map((t) => ({ ...t, time: formatTime(t.time) }))
    trTotal.value = res.total
  } catch (err) {
    console.warn('[ProjectSalary] 转账记录加载失败:', err.message)
    transfers.value = []
    trTotal.value = 0
  } finally {
    trLoading.value = false
  }
}

const applyTransferFilters = () => { trPage.value = 1; loadTransfers() }
const resetTransfers = () => {
  trFilters.company = 'all'; trFilters.status = 'all'; trFilters.keyword = ''
  trFilters.dateFrom = ''; trFilters.dateTo = ''
  applyTransferFilters()
}

/** 导出：POST /admin/transfers/export 返回 text/csv 流（含 BOM），Blob 下载。 */
const exportTransfers = async () => {
  try {
    const blob = await apiExportTransfers(buildTransferQuery())
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `转账记录_${new Date().toISOString().slice(0, 10)}.csv`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (err) {
    console.warn('[ProjectSalary] 转账记录导出失败:', err.message)
    ElMessage.error('导出失败')
  }
}

// 转账明细弹窗（GET /admin/transfers/{id}/details；入账充值单后端无人员明细）
const trDetailVisible = ref(false)
const trDetail = ref({ no: '', company: '', amount: 0, time: '', details: [] })
const openTransferDetail = async (row) => {
  trDetail.value = { no: row.no, company: row.company, amount: row.amount, time: formatTime(row.time), details: [] }
  trDetailVisible.value = true
  if (row.direction === 'in') {
    ElMessage.info('入账记录（充值）无人员明细，可在余额查询的充值记录中查看')
    return
  }
  try {
    const res = await getTransferDetails(row.id)
    trDetail.value = {
      no: res.head.no || row.no,
      company: res.head.company || row.company,
      amount: res.head.amountFen || row.amount,
      time: res.head.payTime ? formatTime(res.head.payTime) : trDetail.value.time,
      details: res.details,
    }
  } catch (err) {
    console.warn('[ProjectSalary] 转账明细加载失败:', err.message)
    trDetail.value = { ...trDetail.value, details: [] }
    ElMessage.error('转账明细加载失败')
  }
}
const trDetailStatusText = (s) => ({ success: '已到账', processing: '处理中', failed: '失败' }[s] || s || '—')
const trDetailStatusClass = (s) => ({ success: 'success', processing: 'warning', failed: 'danger' }[s] || 'default')

// ===== 余额查询（GET /admin/merchant-accounts，余额为后端真实金额） =====
const balCompany = ref('all')
const accounts = ref([])
const balLoading = ref(false)
const balCompanyOptions = computed(() => [...new Set(accounts.value.map((a) => a.company).filter((c) => c && c !== '—'))])
const filteredAccounts = computed(() =>
  accounts.value.filter((a) => balCompany.value === 'all' || a.company === balCompany.value))

const loadAccounts = async () => {
  balLoading.value = true
  try {
    const res = await listMerchantAccounts({ page: 0, size: 200 })
    accounts.value = res.records
  } catch (err) {
    console.warn('[ProjectSalary] 商户账户加载失败:', err.message)
    accounts.value = []
  } finally {
    balLoading.value = false
  }
}
const refreshBalance = () => loadAccounts()

// 账户编辑 / 新增 / 删除（POST/PUT/DELETE /admin/merchant-accounts）
const acctVisible = ref(false)
const acctMode = ref('add')
const acctId = ref(null)
const acctForm = reactive({ bossId: '', short: '', company: '', merchant: '', bank: '' })

const openAddAccount = () => {
  acctMode.value = 'add'; acctId.value = null
  acctForm.bossId = ''; acctForm.short = ''; acctForm.company = ''; acctForm.merchant = ''; acctForm.bank = ''
  acctVisible.value = true
}
const openEditAccount = (a) => {
  acctMode.value = 'edit'; acctId.value = a.id
  acctForm.bossId = a.bossId ?? ''
  acctForm.short = a.short === '—' ? '' : a.short
  acctForm.company = a.company === '—' ? '' : a.company
  acctForm.merchant = a.merchant === '—' ? '' : a.merchant
  acctForm.bank = a.bank === '待完善' ? '' : a.bank
  acctVisible.value = true
}
const saveAccount = async () => {
  const short = acctForm.short.trim()
  const company = acctForm.company.trim()
  const merchant = acctForm.merchant.trim()
  if (!short || !company || !merchant) { ElMessage.warning('请填写必填项'); return }
  let bossId = acctForm.bossId
  if (acctMode.value === 'add' && !bossId) {
    // 原型弹窗无老板字段：按主体全称自动匹配老板账号，匹配不到则提示改用已有公司名
    try {
      const bosses = await listBossOptions()
      const hits = bosses.filter((b) => b.name === company)
      const pool = hits.length ? hits : bosses.filter((b) => b.name.includes(company) || company.includes(b.name))
      if (pool.length === 1) bossId = pool[0].id
      else if (pool.length > 1) { ElMessage.warning('存在多个同名老板账号，无法自动归属，请完善主体全称'); return }
      else { ElMessage.warning('未找到与该主体全称匹配的老板账号'); return }
    } catch (err) {
      console.warn('[ProjectSalary] 老板列表加载失败:', err.message)
      ElMessage.error('账户归属校验失败')
      return
    }
  }
  const form = { bossId, short, company, merchant, bank: acctForm.bank.trim() }
  try {
    if (acctMode.value === 'edit' && acctId.value != null) {
      await updateMerchantAccount(acctId.value, form)
      ElMessage.success('账户信息已更新')
    } else {
      await createMerchantAccount(form)
      ElMessage.success('账户已添加')
    }
    acctVisible.value = false
    loadAccounts()
  } catch (err) {
    console.warn('[ProjectSalary] 保存账户失败:', err.message)
    ElMessage.error('保存失败，请检查账户信息（商户号需唯一、老板需存在）')
  }
}

const delVisible = ref(false)
const delAccount = ref(null)
const deleteAccount = (idx) => {
  delAccount.value = filteredAccounts.value[idx] || null
  delVisible.value = true
}
const confirmDeleteAccount = async () => {
  delVisible.value = false
  const account = delAccount.value
  delAccount.value = null
  if (!account) return
  if (account.isDefault) { ElMessage.warning('默认账户不可删除'); return }
  try {
    await deleteMerchantAccount(account.id)
    ElMessage.success('账户已删除')
    loadAccounts()
  } catch (err) {
    console.warn('[ProjectSalary] 删除账户失败:', err.message)
    ElMessage.error('删除失败')
  }
}

// 账户充值（支付渠道未对接，仍为模拟流程，与原型一致）
const RC_PRESETS = [5000, 10000, 20000, 50000, 100000]
const rcVisible = ref(false)
const rcAccount = ref(null)
const rcAmount = ref('')
const rcPreset = ref(0)
const rcPayType = ref('')
const rcQrVisible = computed(() => !!rcPayType.value && Number(rcAmount.value) > 0)
const rcAmountText = computed(() => (Number(rcAmount.value) || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }))

const openRecharge = (a) => {
  rcAccount.value = a
  rcAmount.value = ''; rcPreset.value = 0; rcPayType.value = ''
  rcVisible.value = true
}
const setRcPreset = (v) => { rcPreset.value = v; rcAmount.value = String(v) }
const clearRcPreset = () => { rcPreset.value = 0 }
const selectRcPay = (type) => {
  const amt = parseFloat(rcAmount.value)
  if (!amt || amt <= 0) { ElMessage.warning('请先输入有效的充值金额'); return }
  rcPayType.value = type
}
const rcPaySuccess = () => {
  const amt = parseFloat(rcAmount.value) || 0
  rcVisible.value = false
  ElMessage.success('支付成功！充值 ¥ ' + amt.toLocaleString('zh-CN', { minimumFractionDigits: 2 }) + ' 已到账')
  loadAccounts()
}

onMounted(() => { loadData(); loadTabCounts() })
</script>

<style scoped>
/* --- 合并卡片面板 --- */
.panel-card { padding: 18px 20px; }
.panel-card :deep(.title-switch-group),
.title-switch-group { display: flex; align-items: center; gap: 28px; margin-bottom: 16px; }

/* --- tab 切换（与员工管理页 title-switch 风格统一） --- */
.title-switch { font-size: 16px; font-weight: 600; color: var(--text-muted); cursor: pointer; padding: 6px 0; user-select: none; transition: color 0.15s; }
.title-switch:hover { color: var(--text-secondary); }
.title-switch.active { color: var(--text-primary); cursor: default; }
.title-switch-divider { width: 1px; height: 14px; background: var(--border); }
.count-pill { background: var(--primary); color: #fff; font-size: 11px; font-weight: 600; padding: 1px 8px; border-radius: 10px; margin-left: 4px; }

/* --- 筛选栏 --- */
.filter-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.filter-bar.tight { margin-bottom: 10px; }
.filter-item { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--text-secondary); margin-bottom: 0; }
.filter-item select, .filter-item input { padding: 7px 12px; border: 1px solid var(--border); border-radius: 8px; font-size: 13px; outline: none; background: #fff; }
.filter-item select:focus, .filter-item input:focus { border-color: var(--primary); }
.filter-label { font-size: 13px; color: var(--text-secondary); font-weight: 500; white-space: nowrap; flex-shrink: 0; }
.filter-item .filter-label + select, .filter-item .filter-label + .search-box { min-width: 160px; }
.filter-item.keyword-item .filter-label + .search-box { min-width: 220px; }
.search-box { position: relative; }
.search-box.no-margin { margin: 0; }
.search-box.no-margin input { border: 1px solid var(--border); border-radius: 8px; padding: 7px 12px 7px 32px; outline: none; background: #fff; font-size: 13px; }
.search-box.no-margin input:focus { border-color: var(--primary); }
.search-box i { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: var(--text-muted); font-size: 13px; }
.date-input { padding: 7px 12px; border: 1px solid var(--border); border-radius: 8px; font-size: 13px; outline: none; background: #fff; width: 140px; }
.date-sep { color: #9ca3af; margin: 0 4px; }

/* --- 表格 --- */
.table-container { overflow-x: auto; }
.table-container.bordered { border: 1px solid #EEF0F4; border-radius: 8px; }
.money { font-weight: 600; color: var(--text-primary); }
.amount-primary { font-weight: 600; color: var(--primary); text-align: right; }
.mono { font-family: monospace; font-size: 12px; }
.mono.no-primary { color: var(--primary); }
.action-btns { display: flex; gap: 6px; }
.action-btns .btn { padding: 4px 10px; font-size: 12px; }
.danger-btn { border-color: #F87171; color: #F87171; }
.success-btn { border-color: #10B981; color: #10B981; }
.empty-cell { text-align: center; padding: 48px 0; color: var(--text-muted); }
.empty-cell i { font-size: 32px; color: #D1D5DB; display: block; margin-bottom: 10px; }
.empty-cell.plain { padding: 40px 0; }

/* --- 分页 --- */
.pagination { display: flex; align-items: center; justify-content: space-between; margin-top: 16px; }
.pagination-info { font-size: 13px; color: var(--text-secondary); }

/* --- 转账明细弹窗 --- */
.manager-cell { display: flex; align-items: center; gap: 8px; }
.mini-avatar.warm { width: 28px; height: 28px; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 600; color: #fff; background: linear-gradient(135deg, #FF7743, #FFB84D); }
.wb-tag { display: inline-flex; align-items: center; gap: 3px; font-size: 10px; padding: 2px 7px; border-radius: 5px; font-weight: 600; }

/* --- 余额卡片 --- */
.balance-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 16px; }
.acct-card { border: 1px solid var(--border); border-radius: 12px; padding: 20px 24px; background: #fff; }
.acct-head { display: flex; align-items: center; gap: 10px; margin-bottom: 18px; }
.acct-short { font-size: 18px; font-weight: 700; color: #1F2937; }
.default-tag { background: #EFF6FF; color: #2563EB; font-size: 11px; font-weight: 500; padding: 2px 8px; border-radius: 4px; }
.acct-line { display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px solid #F3F4F6; }
.acct-line:last-of-type { border-bottom: none; }
.acct-label { font-size: 13px; color: #9CA3AF; }
.acct-value { font-size: 13px; color: #1F2937; font-weight: 500; }
.acct-balance { font-size: 18px; font-weight: 700; color: #FF6B35; }
.acct-actions { padding-top: 14px; border-top: 1px solid #F3F4F6; display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.danger-text { color: #DC2626; border-color: #FECACA; }
.success-text { color: #10B981; border-color: #A7F3D0; }

/* --- 创建发薪单弹窗 --- */
.type-switch { display: flex; gap: 8px; }
.type-option { flex: 1; padding: 9px 0; text-align: center; border: 1px solid var(--border); border-radius: 8px; font-size: 13px; cursor: pointer; color: var(--text-secondary); transition: all 0.2s; }
.type-option.active { border-color: var(--primary); background: var(--primary-light); color: var(--primary); font-weight: 500; }
.payroll-tip { background: var(--primary-light); border-radius: 8px; padding: 12px 14px; font-size: 12px; color: var(--primary); display: flex; gap: 8px; align-items: flex-start; }
.payroll-tip i { margin-top: 2px; }

/* --- 弹窗通用表单 --- */
:deep(.proto-dialog .el-dialog__header) { padding: 20px 24px; border-bottom: 1px solid var(--border); margin-right: 0; }
:deep(.proto-dialog .el-dialog__title) { font-size: 18px; font-weight: 600; color: var(--text-primary); }
:deep(.proto-dialog .el-dialog__body) { padding: 20px 24px; }
:deep(.proto-dialog .el-dialog__footer) { padding: 16px 24px; border-top: 1px solid var(--border); display: flex; justify-content: flex-end; gap: 12px; }
:deep(.proto-dialog.no-head .el-dialog__header) { display: none; }
.form-group { margin-bottom: 16px; }
.form-label { display: block; font-size: 13px; font-weight: 500; margin-bottom: 6px; color: var(--text-primary); }
.form-input, .form-select { width: 100%; padding: 9px 12px; border: 1px solid var(--border); border-radius: 8px; font-size: 14px; outline: none; transition: border-color 0.2s; background-color: #fff; }
.form-input:focus, .form-select:focus { border-color: var(--primary); }
.form-select { appearance: none; background-image: url("data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%23999' d='M6 8L1 3h10z'/%3E%3C/svg%3E"); background-repeat: no-repeat; background-position: right 12px center; padding-right: 32px; }

/* --- 详情信息网格 --- */
.detail-info-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
.detail-info-item { display: flex; flex-direction: column; gap: 4px; }
.detail-info-label { font-size: 12px; color: var(--text-muted); }
.detail-info-value { font-size: 14px; font-weight: 500; }

/* --- 删除确认 --- */
.del-confirm { text-align: center; padding: 12px 20px; }
.del-icon { font-size: 40px; color: #DC2626; margin-bottom: 12px; }
.del-title { font-size: 16px; font-weight: 600; color: #1F2937; margin-bottom: 8px; }
.del-desc { font-size: 13px; color: #9CA3AF; }
.del-footer { justify-content: center; display: flex; gap: 12px; }
.danger-bg { background: #DC2626; border-color: #DC2626; }

/* --- 账户充值 --- */
.rc-account-box { background: #FFF8F5; border: 1px solid #FFE0D1; border-radius: 10px; padding: 12px 16px; display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
.rc-short { font-size: 14px; font-weight: 700; color: #1F2937; }
.rc-merchant { font-size: 12px; color: #9CA3AF; margin-top: 3px; }
.rc-tag { font-size: 12px; color: var(--primary); font-weight: 600; }
.rc-presets { display: flex; gap: 8px; margin-top: 10px; flex-wrap: wrap; }
.rc-preset { padding: 6px 14px; border: 1px solid var(--border); border-radius: 8px; font-size: 12px; color: #374151; cursor: pointer; background: #fff; transition: all 0.15s; }
.rc-preset:hover { border-color: var(--primary); color: var(--primary); }
.rc-preset.active { border-color: var(--primary); background: rgba(255, 107, 53, 0.08); color: var(--primary); font-weight: 600; }
.rc-pay-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.pay-method { border: 2px solid var(--border); border-radius: 10px; padding: 12px; cursor: pointer; display: flex; align-items: center; gap: 10px; background: #fff; transition: all 0.15s; }
.pay-method.active.wechat { border-color: #22C55E; background: rgba(34, 197, 94, 0.05); }
.pay-method.active.alipay { border-color: #2563EB; background: rgba(37, 99, 235, 0.05); }
.pay-icon { width: 36px; height: 36px; border-radius: 8px; flex-shrink: 0; display: flex; align-items: center; justify-content: center; font-size: 17px; color: #fff; }
.pay-icon.wechat { background: #22C55E; }
.pay-icon.alipay { background: #2563EB; }
.pay-name { font-size: 13px; font-weight: 600; color: #1F2937; }
.pay-desc { font-size: 11px; color: #9CA3AF; margin-top: 2px; }
.pay-check { margin-left: auto; width: 18px; height: 18px; border-radius: 50%; border: 2px solid #D1D5DB; flex-shrink: 0; display: flex; align-items: center; justify-content: center; }
.pay-method.active .pay-check { border-color: var(--primary); background: var(--primary); }
.pay-method.active .pay-check::after { content: ''; width: 5px; height: 9px; border: solid #fff; border-width: 0 2px 2px 0; transform: rotate(45deg) translateY(-1px); }
.rc-qr-panel { margin-top: 18px; text-align: center; border: 1px dashed var(--border); border-radius: 12px; padding: 20px; }
.rc-qr-box { width: 150px; height: 150px; margin: 0 auto; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 84px; }
.rc-qr-box.wechat { color: #22C55E; background: rgba(34, 197, 94, 0.08); border: 2px solid #22C55E; }
.rc-qr-box.alipay { color: #2563EB; background: rgba(37, 99, 235, 0.08); border: 2px solid #2563EB; }
.rc-qr-amount { font-size: 20px; font-weight: 700; color: #1F2937; margin-top: 10px; }
.rc-qr-tip { font-size: 13px; color: #6B7280; margin-top: 4px; }
.rc-qr-wait { font-size: 12px; color: #9CA3AF; margin-top: 8px; display: flex; align-items: center; justify-content: center; gap: 6px; }
.spin { color: var(--primary); animation: spin 1.2s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
