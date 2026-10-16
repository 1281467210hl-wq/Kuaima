<template>
  <div>
    <div class="page-header">
      <h1 class="page-title">企业成员</h1>
      <p class="page-desc">企业内部成员协作管理，包含成员邀请、部门架构与权限分配</p>
    </div>

    <div class="card">
      <div class="card-header">
        <div class="card-title title-switch-group">
          <span class="title-switch" :class="{ active: view === 'member' }" @click="switchView('member')">成员列表</span>
          <span class="title-switch-divider"></span>
          <span class="title-switch" :class="{ active: view === 'apply' }" @click="switchView('apply')">申请列表<span v-if="pendingCount" class="count-pill">{{ pendingCount }}</span></span>
          <span class="title-switch-divider"></span>
          <span class="title-switch" :class="{ active: view === 'role' }" @click="switchView('role')">角色管理</span>
        </div>
        <div class="card-header-actions" v-if="view === 'role'">
          <button class="btn btn-primary btn-sm" @click="openRoleModal()"><i class="fas fa-plus"></i> 新建角色</button>
        </div>
      </div>

      <!-- 成员列表 -->
      <div v-show="view === 'member'">
        <div class="filter-bar">
          <div class="filter-item">
            <span class="filter-label">所属公司</span>
            <select v-model="memberFilters.company">
              <option value="all">全部公司</option>
              <option v-for="c in COMPANY_OPTIONS" :key="c" :value="c">{{ c }}</option>
            </select>
          </div>
          <div class="filter-item">
            <span class="filter-label">角色</span>
            <select v-model="memberFilters.role">
              <option value="all">全部角色</option>
              <option v-for="opt in roleFilterOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
          </div>
          <div class="filter-item">
            <span class="filter-label">状态</span>
            <select v-model="memberFilters.status">
              <option value="all">全部状态</option>
              <option value="active">在职</option>
              <option value="resigned">离职</option>
            </select>
          </div>
          <div class="filter-item keyword-item">
            <span class="filter-label">关键词</span>
            <div class="search-box no-margin">
              <i class="fas fa-search"></i>
              <input type="text" placeholder="成员姓名 / 手机号" v-model="memberFilters.keyword">
            </div>
          </div>
          <button class="btn btn-primary btn-sm" @click="goToFirstPage"><i class="fas fa-search"></i> 查询</button>
          <button class="btn btn-outline btn-sm" @click="resetMemberFilters"><i class="fas fa-arrow-rotate-left"></i> 重置</button>
          <button class="btn btn-primary btn-sm" style="margin-left:auto;" @click="openBatchPermModal"><i class="fas fa-shield-halved"></i> 批量改权限</button>
          <button class="btn btn-primary btn-sm" @click="inviteVisible = true"><i class="fas fa-user-plus"></i> 邀请成员</button>
        </div>

        <div class="table-container">
          <el-table
            ref="memberTableRef"
            :data="pagedMembers"
            row-key="name"
            @selection-change="onMemberSelect"
            :header-cell-style="{ background: '#F3F4F6', color: '#6B7280', fontWeight: 500, fontSize: '13px' }"
          >
            <el-table-column type="selection" width="40" />
            <el-table-column label="成员" min-width="120">
              <template #default="{ row }">
                <div class="avatar-cell">
                  <div class="mini-avatar" :class="row.avatar">{{ (row.name || '?').charAt(0) }}</div>
                  <div class="emp-info"><span class="emp-name">{{ row.name }}</span></div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="phone" label="手机号" min-width="120" />
            <el-table-column prop="dept" label="所属公司" min-width="200" show-overflow-tooltip />
            <el-table-column prop="position" label="职位" min-width="100" />
            <el-table-column label="角色" min-width="100">
              <template #default="{ row }"><span class="role-tag" :class="roleTagCls(row.role)">{{ row.roleName }}</span></template>
            </el-table-column>
            <el-table-column prop="joinTime" label="加入时间" min-width="150" />
            <el-table-column label="状态" min-width="90">
              <template #default="{ row }">
                <span class="status-badge" :class="row.status === 'active' ? 'success' : 'default'">{{ row.statusName }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="180" align="right">
              <template #default="{ row }">
                <div class="action-btns" style="justify-content:flex-end;">
                  <button class="btn btn-outline" @click="showDetail(row)"><i class="fas fa-eye"></i> 详情</button>
                  <button class="btn btn-primary btn-sm" @click="openEditMember(row)"><i class="fas fa-pen-to-square"></i> 编辑</button>
                  <button class="btn btn-outline" style="color:var(--danger);border-color:#FCA5A5;" @click="confirmRemove(row)">移除</button>
                </div>
              </template>
            </el-table-column>
            <template #empty>
              <div class="empty-cell"><i class="fas fa-users-slash"></i><div>暂无符合条件的成员</div></div>
            </template>
          </el-table>
        </div>

        <div class="pagination">
          <div class="pagination-info">共 {{ memberTotal }} 条记录</div>
          <el-pagination
            v-model:current-page="memberPage"
            :page-size="memberSize"
            :total="filteredMembers.length"
            layout="prev, pager, next"
            background
            small
          />
        </div>
      </div>

      <!-- 申请列表 -->
      <div v-show="view === 'apply'">
        <div class="filter-bar">
          <div class="filter-item">
            <span class="filter-label">状态</span>
            <select v-model="applyFilters.status">
              <option value="all">全部状态</option>
              <option value="pending">待审批</option>
              <option value="approved">已同意</option>
              <option value="rejected">已拒绝</option>
            </select>
          </div>
          <div class="filter-item">
            <span class="filter-label">申请时间</span>
            <div style="display:flex;align-items:center;gap:6px;">
              <input type="date" class="date-input" v-model="applyFilters.dateFrom">
              <span style="color:#999;font-size:12px;">至</span>
              <input type="date" class="date-input" v-model="applyFilters.dateTo">
            </div>
          </div>
          <div class="filter-item keyword-item">
            <span class="filter-label">关键词</span>
            <div class="search-box no-margin">
              <i class="fas fa-search"></i>
              <input type="text" placeholder="申请人姓名 / 手机号" v-model="applyFilters.keyword">
            </div>
          </div>
          <button class="btn btn-primary btn-sm" @click="applyPage = 1"><i class="fas fa-search"></i> 查询</button>
          <button class="btn btn-outline btn-sm" @click="resetApplyFilters"><i class="fas fa-arrow-rotate-left"></i> 重置</button>
        </div>

        <div class="table-container">
          <el-table
            :data="pagedApplies"
            row-key="name"
            :header-cell-style="{ background: '#F3F4F6', color: '#6B7280', fontWeight: 500, fontSize: '13px' }"
          >
            <el-table-column label="申请人" min-width="150">
              <template #default="{ row }">
                <div class="avatar-cell">
                  <div class="mini-avatar" :class="row.avatar">{{ (row.name || '?').charAt(0) }}</div>
                  <div class="emp-info"><span class="emp-name">{{ row.name }}</span><span class="emp-phone">{{ row.phone }}</span></div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="job" label="申请职位" min-width="110" />
            <el-table-column label="申请角色" min-width="90">
              <template #default="{ row }"><span class="role-tag" :class="row.roleName === '管理员' ? 'admin' : 'staff'">{{ row.roleName }}</span></template>
            </el-table-column>
            <el-table-column prop="proj" label="意向项目" min-width="130" show-overflow-tooltip />
            <el-table-column prop="time" label="申请时间" min-width="150" />
            <el-table-column label="状态" min-width="90">
              <template #default="{ row }">
                <span class="status-badge" :class="applyStatusClass(row.status)">{{ applyStatusText(row.status) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="130" align="right">
              <template #default="{ row }">
                <div class="action-btns" style="justify-content:flex-end;" v-if="row.status === 'pending'">
                  <button class="btn btn-outline btn-sm" @click="reviewApply(row, 'rejected')">拒绝</button>
                  <button class="btn btn-primary btn-sm" @click="reviewApply(row, 'approved')">同意</button>
                </div>
                <span v-else class="btn-done">已处理</span>
              </template>
            </el-table-column>
            <template #empty>
              <div class="empty-cell"><i class="fas fa-inbox"></i><div>暂无符合条件的申请</div></div>
            </template>
          </el-table>
        </div>

        <div class="pagination">
          <div class="pagination-info">共 {{ filteredApplies.length }} 条记录</div>
          <el-pagination
            v-model:current-page="applyPage"
            :page-size="applySize"
            :total="filteredApplies.length"
            layout="prev, pager, next"
            background
            small
          />
        </div>
      </div>

      <!-- 角色管理 -->
      <div v-show="view === 'role'">
        <div class="role-grid">
          <div class="role-card" v-for="r in roles" :key="r.id">
            <div class="role-card-head">
              <div class="role-icon" :style="{ background: r.color + '15', color: r.color }">
                <i class="fas" :class="r.icon"></i>
              </div>
              <div style="flex:1;">
                <div class="role-name">{{ r.name }}
                  <span v-if="r.system" class="sys-badge">系统内置</span>
                </div>
                <div class="role-desc">{{ r.desc }}</div>
              </div>
            </div>
            <div class="role-mod-line">权限模块 <span :style="{ color: r.color }">{{ rolePermKeys(r.id).length }}</span> 项 · 分组 {{ roleSectionNames(r.id).length }}</div>
            <div class="role-perm-tags">
              <span v-for="s in roleSectionNames(r.id).slice(0, 3)" :key="s" class="perm-tag" :style="{ color: r.color, background: r.color + '15' }">{{ s }}</span>
              <span v-if="!roleSectionNames(r.id).length" class="no-data">暂无权限</span>
            </div>
            <div class="role-member-box">
              <div class="role-member-line">该角色下 <span :style="{ color: r.color }">{{ roleMemberNames(r.id).length }}</span> 名成员</div>
              <div class="role-member-pills">
                <span v-for="m in roleMemberNames(r.id)" :key="m" class="member-pill">{{ m }}</span>
                <span v-if="!roleMemberNames(r.id).length" class="no-data">暂无成员</span>
              </div>
            </div>
            <div class="role-actions">
              <button class="btn btn-outline btn-sm" @click="openRoleModal(r)"><i class="fas fa-key"></i> 编辑权限</button>
              <button class="btn btn-outline btn-sm" @click="viewRoleMembers(r.id)"><i class="fas fa-users"></i> 查看成员</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 邀请成员弹窗 -->
    <el-dialog v-model="inviteVisible" title="邀请成员" width="500px" class="proto-dialog">
      <div class="form-group">
        <label class="form-label">职位名称</label>
        <input class="form-input" type="text" placeholder="请输入职位名称，如：项目负责人" v-model="inviteForm.position">
      </div>
      <div class="form-group">
        <label class="form-label">角色权限</label>
        <select class="form-select" v-model="inviteForm.role">
          <option value="staff">普通成员（仅查看与日常操作）</option>
          <option value="admin">管理员（可管理成员与项目）</option>
          <option value="super">超级管理员（拥有全部权限）</option>
        </select>
      </div>
      <div class="form-group">
        <label class="form-label">邀请手机号</label>
        <input class="form-input" type="text" maxlength="11" placeholder="请输入被邀请人11位手机号" v-model="inviteForm.phone">
      </div>
      <div class="form-group">
        <label class="form-label">邀请链接</label>
        <div class="invite-link-box">
          <span>{{ INVITE_LINK }}</span>
          <button class="btn btn-outline btn-sm" @click="copyLink"><i class="fas fa-copy"></i> 复制</button>
        </div>
      </div>
      <div class="invite-qrcode" :class="{ 'has-image': inviteQr.image }" title="扫码加入企业">
        <img v-if="inviteQr.image" class="invite-qr-img" :src="inviteQr.image" alt="邀请二维码">
      </div>
      <p class="invite-tip">微信扫码 / 点击链接即可申请加入企业，申请后需管理员审批</p>
      <template #footer>
        <button class="btn btn-secondary" @click="inviteVisible = false">取消</button>
        <button class="btn btn-primary" @click="sendInvite"><i class="fas fa-paper-plane"></i> 发送邀请</button>
      </template>
    </el-dialog>

    <!-- 员工详情弹窗 -->
    <el-dialog v-model="detailVisible" title="员工详情" width="500px" class="proto-dialog detail-dialog">
      <div v-if="detail" class="detail-body">
        <div class="detail-head">
          <div class="mini-avatar" :class="detail.avatar" style="width:52px;height:52px;font-size:20px;">{{ (detail.name || '?').charAt(0) }}</div>
          <div>
            <div class="detail-name">{{ detail.name }}</div>
            <div class="detail-sub">{{ detail.roleName }} · {{ detail.dept }}</div>
          </div>
        </div>

        <div class="detail-section-title" :class="{ collapsed: detailCollapsed.base }" @click="toggleSection('base')">基本信息<i class="fas fa-chevron-down collapse-arrow"></i></div>
        <div class="detail-section-body" :class="{ collapsed: detailCollapsed.base }">
          <div class="detail-info-grid">
            <div class="detail-info-item"><span class="detail-info-label">手机号</span><span class="detail-info-value">{{ detail.phone }}</span></div>
            <div class="detail-info-item"><span class="detail-info-label">性别</span><span class="detail-info-value">{{ genderText(detail.gender) }}</span></div>
            <div class="detail-info-item"><span class="detail-info-label">身份证号</span><span class="detail-info-value">{{ detail.idCard || '—' }}</span></div>
            <div class="detail-info-item"><span class="detail-info-label">年龄</span><span class="detail-info-value">{{ detail.age != null && detail.age !== '' ? detail.age : '—' }}</span></div>
            <div class="detail-info-item"><span class="detail-info-label">加入时间</span><span class="detail-info-value">{{ detail.joinTime }}</span></div>
          </div>
        </div>

        <div class="detail-section-title" :class="{ collapsed: detailCollapsed.job }" @click="toggleSection('job')">任职信息<i class="fas fa-chevron-down collapse-arrow"></i></div>
        <div class="detail-section-body" :class="{ collapsed: detailCollapsed.job }">
          <div class="detail-info-grid">
            <div class="detail-info-item"><span class="detail-info-label">职位</span><span class="detail-info-value">{{ detail.position }}</span></div>
            <div class="detail-info-item"><span class="detail-info-label">角色权限</span><span class="detail-info-value">{{ detail.roleName }}</span></div>
            <div class="detail-info-item"><span class="detail-info-label">所属公司</span><span class="detail-info-value">{{ detail.dept }}</span></div>
            <div class="detail-info-item"><span class="detail-info-label">所属项目</span><span class="detail-info-value">{{ detail.proj || '全部项目' }}</span></div>
          </div>
        </div>

        <div class="detail-section-title" :class="{ collapsed: detailCollapsed.proj }" @click="toggleSection('proj')">
          <i class="fas fa-diagram-project" style="color:#FF6B35;font-size:12px;margin-right:6px;"></i>参与项目<i class="fas fa-chevron-down collapse-arrow"></i>
        </div>
        <div class="detail-section-body" :class="{ collapsed: detailCollapsed.proj }">
          <div class="proj-list" v-if="detail.projects && detail.projects.length">
            <div class="proj-item" v-for="p in detail.projects" :key="p.name">
              <i class="fas fa-diagram-project proj-icon"></i>
              <div class="proj-main">
                <div class="proj-name">{{ p.name }}</div>
                <div class="proj-sub">{{ p.role }} · 加入于 {{ p.joined }}</div>
              </div>
              <span class="proj-status" :style="{ color: p.status === '进行中' ? '#10B981' : '#DC2626' }">
                <span class="proj-dot" :style="{ background: p.status === '进行中' ? '#10B981' : '#DC2626' }"></span>{{ p.status }}
              </span>
            </div>
          </div>
          <div v-else class="proj-empty">暂无项目记录</div>
        </div>

        <div class="detail-section-title" :class="{ collapsed: detailCollapsed.perm }" @click="toggleSection('perm')">工作台权限<span class="detail-section-hint">手机端可访问的模块</span><i class="fas fa-chevron-down collapse-arrow"></i></div>
        <div class="detail-section-body" :class="{ collapsed: detailCollapsed.perm }">
          <div class="dp-list">
            <div class="dp-org-label"><i class="fas fa-sitemap"></i> 所属组织</div>
            <div class="dp-org-rows">
              <div class="dp-org-row"><i class="fas fa-building dp-org-icon"></i><span class="dp-org-name">{{ detail.dept }}</span><span class="dp-org-tag">所属公司</span></div>
              <div class="dp-org-row"><i class="fas fa-users dp-org-icon"></i><span class="dp-org-name">企业成员</span></div>
              <div class="dp-org-row"><i class="fas fa-diagram-project dp-org-icon"></i><span class="dp-org-name">项目管理</span></div>
            </div>
            <div v-for="sec in detailPermSections(detail)" :key="sec.section">
              <div class="dp-org-label"><i class="fas fa-tag"></i> {{ sec.section }}</div>
              <div class="dp-sec-rows">
                <div class="dp-item" v-for="it in sec.items" :key="it.key" :style="{ background: it.checked ? '#FFF7F0' : '#FAFAFA' }">
                  <i class="fas" :class="it.icon" :style="{ color: it.checked ? 'var(--primary)' : '#D1D5DB' }"></i>
                  <span class="dp-item-name" :style="{ color: it.checked ? '#333' : '#BBB' }">{{ it.name }}</span>
                  <span class="status-badge" :class="it.checked ? 'success' : 'default'">{{ it.checked ? '已开启' : '已关闭' }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 移除确认弹窗 -->
    <el-dialog v-model="removeVisible" width="440px" class="proto-dialog remove-dialog">
      <template #header>
        <div class="modal-title" style="color:var(--danger);"><i class="fas fa-triangle-exclamation" style="margin-right:8px;"></i>确认移除</div>
      </template>
      <div class="remove-body">
        <div class="remove-icon"><i class="fas fa-user-minus"></i></div>
        <div class="remove-title">确认移除该企业成员？</div>
        <div class="remove-desc">移除后，该成员将从企业成员列表中删除，无法登录企业工作台，相关数据将保留 30 天后永久清除。</div>
      </div>
      <template #footer>
        <button class="btn btn-secondary" @click="removeVisible = false">取消</button>
        <button class="btn btn-danger" @click="doRemoveMember"><i class="fas fa-trash"></i> 确认移除</button>
      </template>
    </el-dialog>

    <!-- 编辑成员弹窗 -->
    <el-dialog v-model="editVisible" title="编辑成员信息" width="520px" class="proto-dialog">
      <div class="form-group">
        <label class="form-label">成员姓名</label>
        <input class="form-input" type="text" v-model="editForm.name">
      </div>
      <div class="form-group">
        <label class="form-label">手机号</label>
        <input class="form-input" type="text" v-model="editForm.phone">
      </div>
      <div class="form-group">
        <label class="form-label">性别</label>
        <select class="form-select" v-model="editForm.gender">
          <option value="male">男</option>
          <option value="female">女</option>
        </select>
      </div>
      <div class="form-group">
        <label class="form-label">身份证号</label>
        <input class="form-input" type="text" maxlength="18" placeholder="请输入18位身份证号" v-model="editForm.idCard">
      </div>
      <div class="form-group">
        <label class="form-label">年龄</label>
        <input class="form-input" type="number" min="16" max="70" placeholder="请输入年龄" v-model="editForm.age">
      </div>
      <div class="form-group">
        <label class="form-label">所属公司</label>
        <select class="form-select" v-model="editForm.dept">
          <option v-for="c in COMPANY_OPTIONS" :key="c" :value="c">{{ c }}</option>
        </select>
      </div>
      <div class="form-group">
        <label class="form-label">职位</label>
        <input class="form-input" type="text" v-model="editForm.position">
      </div>
      <div class="form-group">
        <label class="form-label">角色</label>
        <select class="form-select" v-model="editForm.role">
          <option value="super">超级管理员</option>
          <option value="admin">管理员</option>
          <option value="member">普通成员</option>
        </select>
      </div>
      <div class="form-group">
        <label class="form-label">工作台权限</label>
        <div class="perm-tree">
          <div class="perm-group" v-for="grp in WB_PERM_TREE" :key="grp.group">
            <div class="perm-group-title"><i class="fas fa-heart"></i>{{ grp.group }}</div>
            <div class="perm-item" v-if="grp.fixed">
              <div class="perm-item-left"><i class="fas" :class="grp.fixed.icon"></i>{{ grp.fixed.name }}<span class="perm-item-tag">{{ grp.fixed.tag }}</span></div>
            </div>
            <div class="perm-item" v-for="it in grp.items" :key="it.name">
              <div class="perm-item-left"><i class="fas" :class="it.icon"></i>{{ it.name }}</div>
              <label class="perm-switch">
                <input type="checkbox" :checked="editForm.wbPerms.includes(it.name)" @change="onToggleWb(editForm.wbPerms, it.name, $event.target.checked)">
                <span class="perm-slider"></span>
              </label>
            </div>
          </div>
        </div>
      </div>
      <div class="form-group">
        <label class="form-label">状态</label>
        <select class="form-select" v-model="editForm.status">
          <option value="active">在职</option>
          <option value="resigned">离职</option>
        </select>
      </div>
      <template #footer>
        <button class="btn btn-secondary" @click="editVisible = false">取消</button>
        <button class="btn btn-primary" @click="saveEditMember"><i class="fas fa-check"></i> 保存</button>
      </template>
    </el-dialog>

    <!-- 批量改权限弹窗 -->
    <el-dialog v-model="batchVisible" title="批量修改工作台权限" width="520px" class="proto-dialog">
      <div class="form-group">
        <label class="form-label">已选成员</label>
        <div class="batch-selected">{{ batchNames.length ? batchNames.join('、') : '未选择成员' }}</div>
      </div>
      <div class="form-group">
        <label class="form-label">工作台权限</label>
        <div class="perm-tree">
          <div class="perm-group" v-for="grp in WB_PERM_TREE" :key="grp.group">
            <div class="perm-group-title"><i class="fas fa-heart"></i>{{ grp.group }}</div>
            <div class="perm-item" v-if="grp.fixed">
              <div class="perm-item-left"><i class="fas" :class="grp.fixed.icon"></i>{{ grp.fixed.name }}<span class="perm-item-tag">{{ grp.fixed.tag }}</span></div>
            </div>
            <div class="perm-item" v-for="it in grp.items" :key="it.name">
              <div class="perm-item-left"><i class="fas" :class="it.icon"></i>{{ it.name }}</div>
              <label class="perm-switch">
                <input type="checkbox" :checked="batchWb.includes(it.name)" @change="onToggleWb(batchWb, it.name, $event.target.checked)">
                <span class="perm-slider"></span>
              </label>
            </div>
          </div>
        </div>
      </div>
      <div class="batch-tip">勾选的权限将应用到所有已选成员，未勾选的权限将被移除。</div>
      <template #footer>
        <button class="btn btn-secondary" @click="batchVisible = false">取消</button>
        <button class="btn btn-primary" @click="saveBatchPerm"><i class="fas fa-check"></i> 确认修改</button>
      </template>
    </el-dialog>

    <!-- 新建 / 编辑角色弹窗 -->
    <el-dialog v-model="roleVisible" :title="roleModalTitle" width="680px" class="proto-dialog">
      <div class="form-group">
        <label class="form-label">角色名称</label>
        <input class="form-input" type="text" placeholder="请输入角色名称" v-model="roleForm.name" :disabled="roleFormIsSystem" :class="{ 'is-disabled': roleFormIsSystem }">
      </div>
      <div class="form-group">
        <label class="form-label">角色描述</label>
        <input class="form-input" type="text" placeholder="请输入角色描述（选填）" v-model="roleForm.desc">
      </div>
      <div class="form-group" style="margin-bottom:0;">
        <label class="form-label">该角色可访问的模块权限</label>
      </div>
      <div class="role-perm-list">
        <div class="rp-org-label"><i class="fas fa-sitemap"></i> 所属组织</div>
        <div class="rp-org-row">
          <input type="checkbox" disabled checked>
          <i class="fas fa-building rp-org-icon"></i>
          <span class="rp-org-name">{{ currentEnterpriseName || '当前企业' }}</span>
        </div>
        <div v-for="sec in ROLE_PERM_SECTIONS" :key="sec.section">
          <div class="rp-section-head">
            <input type="checkbox" class="rp-section-check" :checked="sectionChecked(sec.section)" @change="onSectionToggle(sec.section, $event.target.checked)">
            <span class="rp-section-title"><i class="fas fa-tag"></i> {{ sec.section }}</span>
          </div>
          <div class="rp-items">
            <label class="rp-item" v-for="p in sec.items" :key="p.key" :class="{ on: roleForm.permissions.includes(p.key) }">
              <input type="checkbox" :checked="roleForm.permissions.includes(p.key)" @change="onRolePermToggle(p.key, $event.target.checked)">
              <div class="rp-item-main">
                <div class="rp-item-name">{{ p.name }}</div>
                <div class="rp-item-desc">{{ p.desc }}</div>
              </div>
              <i class="fas" :class="p.icon" :style="{ color: roleForm.permissions.includes(p.key) ? '#FF6B35' : '#D1D5DB' }"></i>
            </label>
          </div>
        </div>
      </div>
      <template #footer>
        <button v-if="editingRoleId" class="btn btn-outline role-delete-btn" @click="deleteRole"><i class="fas fa-trash"></i> 删除角色</button>
        <div style="flex:1;"></div>
        <button class="btn btn-secondary" @click="roleVisible = false">取消</button>
        <button class="btn btn-primary" @click="saveRole"><i class="fas fa-check"></i> 保存</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listEnterprises, getInviteQr, inviteMember,
  listMembers, getMember, updateMemberRole, updateMemberPermissions, updateMemberStatus,
  batchUpdateMemberPermissions, removeMember,
  listApplies, reviewApply as reviewApplyApi,
  listEmployeeRoles, createEmployeeRole, updateEmployeeRole, deleteEmployeeRole,
  permCodesToWbNames,
} from '@/api/enterpriseMember'

defineOptions({ name: 'EnterpriseMember' })

/* ===================================================================
   数据层：全部走 @/api/enterpriseMember 的真实后端接口。
   admin 账号是平台视角，先加载企业列表（GET /admin/enterprises）作为「所属公司」
   筛选来源并默认选中第一个企业，enterpriseId 贯穿成员/申请/邀请/审批各调用。
   请求失败时列表置空，错误提示由 axios 响应拦截器统一弹出。
   =================================================================== */

/** 表格沿用前端切片分页，一次拉取的窗口大小（后端 page 从 0 开始）。 */
const LOAD_SIZE = 200

/* ---- 工作台权限（编辑 / 批量改权限开关树；首组固定项展示当前企业名） ---- */
const WB_PERM_BASE = [
  { group: '所属组织', fixed: { icon: 'fa-building', name: '当前企业', tag: '所属公司' }, items: [
    { icon: 'fa-users', name: '企业成员' }, { icon: 'fa-diagram-project', name: '项目管理' } ] },
  { group: '智能招聘', items: [
    { icon: 'fa-file-lines', name: '简历库' }, { icon: 'fa-briefcase', name: '岗位管理' },
    { icon: 'fa-video', name: '直播橱窗' }, { icon: 'fa-user-plus', name: '候选人' } ] },
  { group: '闪电发薪', items: [
    { icon: 'fa-money-check-dollar', name: '批量发薪' }, { icon: 'fa-circle-check', name: '待我审批' },
    { icon: 'fa-right-left', name: '转账记录' }, { icon: 'fa-wallet', name: '余额查询' } ] },
  { group: '员工管理', items: [
    { icon: 'fa-list-ul', name: '员工列表' }, { icon: 'fa-clipboard-check', name: '考勤打卡' } ] },
]
/** 开关树：首组固定项显示当前选中企业名（其余为权限开关）。 */
const WB_PERM_TREE = computed(() => WB_PERM_BASE.map((grp, i) => (
  i === 0 && grp.fixed
    ? { ...grp, fixed: { ...grp.fixed, name: currentEnterpriseName.value || '未选择企业' } }
    : grp
)))

/* 角色权限树（角色弹窗 / 详情工作台权限） */
const ROLE_PERMISSIONS = [
  { key: 'resume_pool',   section: '智能招聘', name: '简历库',   icon: 'fa-file-lines',       desc: '查看和管理企业收到的简历' },
  { key: 'job_manage',    section: '智能招聘', name: '岗位管理', icon: 'fa-briefcase',        desc: '发布、编辑、下线招聘岗位' },
  { key: 'live_showcase', section: '智能招聘', name: '直播橱窗', icon: 'fa-tv',               desc: '企业带岗直播入口' },
  { key: 'candidate',     section: '智能招聘', name: '候选人',   icon: 'fa-user-plus',        desc: '报名人员自动分配与管理' },
  { key: 'batch_payroll',   section: '闪电发薪', name: '批量发薪', icon: 'fa-money-bill-wave', desc: '创建发薪单、执行批量发薪' },
  { key: 'payroll_approve', section: '闪电发薪', name: '待我审批', icon: 'fa-check-circle',    desc: '审核发薪单、转账申请' },
  { key: 'transfer_record', section: '闪电发薪', name: '转账记录', icon: 'fa-right-left',      desc: '查看企业转账历史与明细' },
  { key: 'balance_view',    section: '闪电发薪', name: '余额查询', icon: 'fa-wallet',          desc: '查看企业账户余额与流水' },
  { key: 'member_list',     section: '员工管理', name: '员工列表', icon: 'fa-users',           desc: '管理企业员工、角色、权限' },
  { key: 'attend_check',    section: '员工管理', name: '考勤打卡', icon: 'fa-calendar-check',  desc: '员工签到考勤、查看考勤记录' },
]
const ROLE_PERM_SECTIONS = (() => {
  const order = []
  const map = {}
  for (const p of ROLE_PERMISSIONS) {
    if (!map[p.section]) { map[p.section] = []; order.push(p.section) }
    map[p.section].push(p)
  }
  return order.map((s) => ({ section: s, items: map[s] }))
})()

const ROLE_DEFAULT_PERMS = {
  super: ['resume_pool', 'job_manage', 'live_showcase', 'candidate', 'batch_payroll', 'payroll_approve', 'transfer_record', 'balance_view', 'member_list', 'attend_check'],
  admin: ['batch_payroll', 'payroll_approve', 'transfer_record', 'balance_view', 'member_list', 'attend_check'],
  staff: ['batch_payroll', 'transfer_record', 'balance_view', 'member_list', 'attend_check'],
}

/* ===================== 企业（所属公司）上下文 ===================== */
const enterprises = ref([])
const enterpriseId = ref(null)
const COMPANY_OPTIONS = computed(() => enterprises.value.map((e) => e.companyName))
const currentEnterpriseName = computed(() => {
  const hit = enterprises.value.find((e) => String(e.id) === String(enterpriseId.value))
  return hit ? hit.companyName : ''
})

const loadEnterprises = async () => {
  try {
    const res = await listEnterprises()
    enterprises.value = (res && res.data ? res.data : []).map((e) => ({
      id: e.id,
      companyName: e.companyName || `企业#${e.id}`,
      companyCode: e.companyCode || '',
    }))
  } catch (err) {
    console.warn('[EnterpriseMember] 企业列表加载失败:', err.message)
    enterprises.value = []
  }
  // 默认选中第一个企业，后续所有调用都带该 enterpriseId
  const first = enterprises.value[0]
  enterpriseId.value = first ? first.id : null
  memberFilters.company = first ? first.companyName : 'all'
  if (!enterpriseId.value) {
    members.value = []
    applies.value = []
    return
  }
  await Promise.all([loadMembers(), loadApplies()])
}

/* ===================== 公共 ===================== */
const view = ref('member')
const switchView = (v) => {
  view.value = v
  if (v === 'member') loadMembers()
  else if (v === 'apply') loadApplies()
  else loadRoles()
}

const ROLE_NAME_MAP = { super: '超级管理员', admin: '管理员', member: '普通成员', staff: '普通成员' }
const roleTagCls = (r) => (r === 'super' ? 'super' : r === 'admin' ? 'admin' : 'staff')
const applyStatusText = (s) => ({ pending: '待审批', approved: '已同意', rejected: '已拒绝' }[s] || s || '—')
const applyStatusClass = (s) => ({ pending: 'warning', approved: 'success', rejected: 'danger' }[s] || 'default')
// 后端 gender 为 male/female，UI 统一展示男/女；无值回退 '—'（已是中文则原样返回）。
const genderText = (g) => (g === 'male' ? '男' : g === 'female' ? '女' : (g && g !== '—' ? g : '—'))

const toast = (msg) => ElMessage.success(msg)

/* ===================== 成员列表 ===================== */
const members = ref([])
const memberTotal = ref(0)
const memberFilters = reactive({ company: 'all', role: 'all', status: 'all', keyword: '' })
const memberPage = ref(1)
const memberSize = ref(10)
const selectedNames = ref([])
const selectedIds = ref([])
const memberTableRef = ref(null)
/** 角色卡「查看成员」进入时按 employee_role.id 过滤（后端 role 参数只认 all/super/admin/staff）。 */
const roleFilterId = ref(null)
/** 后端 role 参数可识别的规范值（其余下拉值即 employee_role.id，走前端 roleId 过滤）。 */
const CANONICAL_ROLE_KEYS = ['all', 'super', 'admin', 'staff']
/** 角色筛选下拉选项：对齐原型 updateMemberRoleFilter()——由角色管理全部角色生成（含自建角色，value=角色 id）；
 *  角色接口未返回数据时回退 3 个内置角色（value=规范 key，仍走后端筛选）。 */
const roleFilterOptions = computed(() => (roles.value.length
  ? roles.value.map((r) => ({ value: String(r.id), label: r.name }))
  : [
    { value: 'super', label: '超级管理员' },
    { value: 'admin', label: '管理员' },
    { value: 'staff', label: '普通成员' },
  ]))

// 状态 / 角色 / 关键词 / 公司 已由后端筛选，这里只做角色维度的二次过滤
const filteredMembers = computed(() => {
  if (roleFilterId.value == null) return members.value
  const id = String(roleFilterId.value)
  const key = roleKeyOfId(roleFilterId.value)
  return members.value.filter((m) => String(m.roleId) === id || (!m.roleId && key && m.role === key))
})
const pagedMembers = computed(() => {
  const start = (memberPage.value - 1) * memberSize.value
  return filteredMembers.value.slice(start, start + memberSize.value)
})

const loadMembers = async () => {
  if (!enterpriseId.value) { members.value = []; memberTotal.value = 0; return }
  try {
    const res = await listMembers({
      enterpriseId: enterpriseId.value,
      keyword: memberFilters.keyword.trim() || undefined,
      status: memberFilters.status,
      role: roleFilterId.value == null && CANONICAL_ROLE_KEYS.includes(memberFilters.role)
        ? memberFilters.role
        : 'all',
      company: memberFilters.company !== 'all' ? memberFilters.company : undefined,
      page: 0,
      size: LOAD_SIZE,
    })
    members.value = res.rows
    memberTotal.value = res.total
  } catch (err) {
    console.warn('[EnterpriseMember] 成员列表加载失败:', err.message)
    members.value = []
    memberTotal.value = 0
  }
}
// 查询：按当前下拉值过滤（原型 renderTable 直接读下拉选中值）。下拉值为 employee_role.id 时
// 走前端 roleId 过滤（matchesRole），规范 key 仍交给后端筛选。
const goToFirstPage = () => {
  roleFilterId.value = CANONICAL_ROLE_KEYS.includes(memberFilters.role) ? null : memberFilters.role
  memberPage.value = 1
  loadMembers()
}
const resetMemberFilters = () => {
  memberFilters.company = currentEnterpriseName.value || 'all'
  memberFilters.role = 'all'; memberFilters.status = 'all'; memberFilters.keyword = ''
  roleFilterId.value = null
  memberPage.value = 1
  loadMembers()
}
const onMemberSelect = (rows) => {
  selectedNames.value = rows.map((r) => r.name)
  selectedIds.value = rows.map((r) => (r.memberId != null ? r.memberId : r.id))
}

// 「所属公司」即企业维度：切换公司 = 切换 enterpriseId，所有接口随之重载
watch(() => memberFilters.company, (name) => {
  const hit = name && name !== 'all'
    ? enterprises.value.find((e) => e.companyName === name)
    : enterprises.value[0]
  const nextId = hit ? hit.id : null
  if (nextId !== enterpriseId.value) enterpriseId.value = nextId
})
watch(enterpriseId, (id) => {
  memberPage.value = 1
  applyPage.value = 1
  memberFilters.role = 'all'
  roleFilterId.value = null
  if (!id) { members.value = []; applies.value = []; return }
  loadMembers()
  loadApplies()
  if (view.value === 'role') loadRoles()
  if (inviteVisible.value) loadInviteQr()
})

/* ===================== 邀请 ===================== */
const inviteVisible = ref(false)
const inviteForm = reactive({ position: '', role: 'staff', phone: '' })
const inviteQr = reactive({ image: '', link: '', code: '' })
const INVITE_LINK = computed(() => inviteQr.link || '—')

// 二维码按当前企业生成（invite-qr 返回 base64 data URL）
const loadInviteQr = async () => {
  if (!enterpriseId.value) { inviteQr.image = ''; inviteQr.link = ''; inviteQr.code = ''; return }
  try {
    const res = await getInviteQr(enterpriseId.value)
    const d = (res && res.data) || {}
    inviteQr.image = d.qrImage || ''
    inviteQr.link = d.link || ''
    inviteQr.code = d.inviteCode || ''
  } catch (err) {
    console.warn('[EnterpriseMember] 邀请二维码加载失败:', err.message)
    inviteQr.image = ''; inviteQr.link = ''; inviteQr.code = ''
  }
}
watch(inviteVisible, (v) => { if (v) loadInviteQr() })

const copyLink = () => {
  if (!inviteQr.link) { ElMessage.warning('邀请链接尚未加载'); return }
  navigator.clipboard?.writeText(inviteQr.link)
  toast('邀请链接已复制')
}
const sendInvite = async () => {
  const phone = inviteForm.phone.trim()
  if (!/^1\d{10}$/.test(phone)) { ElMessage.warning('请输入正确的11位手机号'); return }
  if (!enterpriseId.value) { ElMessage.warning('请先选择企业'); return }
  let res
  try {
    res = await inviteMember(enterpriseId.value, { phone, role: inviteForm.role })
  } catch (err) {
    console.warn('[EnterpriseMember] 邀请发送失败:', err.message)
    return
  }
  const d = (res && res.data) || {}
  inviteVisible.value = false
  inviteForm.phone = ''
  toast(`邀请已发送至 ${d.phone || phone}${d.inviteCode ? `（邀请码 ${d.inviteCode}）` : ''}`)
  loadApplies()
}

/* ===================== 成员详情 ===================== */
const detailVisible = ref(false)
const detail = ref(null)
const detailCollapsed = reactive({ base: false, job: false, proj: false, perm: false })
const toggleSection = (k) => { detailCollapsed[k] = !detailCollapsed[k] }

const detailPermSections = (m) => {
  // 后端 permissions 为统一 code 数组（空数组即「未开启任何模块」），仅在字段缺失时回退角色默认值
  const permKeys = Array.isArray(m.permissions)
    ? (m.permKeys || [])
    : (ROLE_DEFAULT_PERMS[m.role] || ROLE_DEFAULT_PERMS.staff)
  const set = new Set(permKeys)
  return ROLE_PERM_SECTIONS.map((sec) => ({
    section: sec.section,
    items: sec.items.map((p) => ({ key: p.key, name: p.name, icon: p.icon, checked: set.has(p.key) })),
  }))
}

const showDetail = async (row) => {
  detail.value = row
  detailCollapsed.base = false; detailCollapsed.job = false; detailCollapsed.proj = false; detailCollapsed.perm = false
  detailVisible.value = true
  if (!enterpriseId.value || !row || row.memberId == null) return
  try {
    const res = await getMember(enterpriseId.value, row.memberId)
    if (res && res.data) detail.value = res.data
  } catch (err) {
    console.warn('[EnterpriseMember] 成员详情加载失败:', err.message)
  }
}

/* ===================== 移除 ===================== */
const removeVisible = ref(false)
const pendingRemove = ref(null)
const confirmRemove = (row) => { pendingRemove.value = row; removeVisible.value = true }
const doRemoveMember = async () => {
  const row = pendingRemove.value
  removeVisible.value = false
  if (!row || row.memberId == null || !enterpriseId.value) return
  try {
    await removeMember(enterpriseId.value, row.memberId)
  } catch (err) {
    console.warn('[EnterpriseMember] 移除成员失败:', err.message)
    return
  }
  await loadMembers()
  ElMessage({
    type: 'success',
    message: `成员「${row.name}」已移除，该成员已从企业成员列表移除`,
    duration: 4000,
  })
}

/* ===================== 编辑成员 ===================== */
const editVisible = ref(false)
const editForm = reactive({ name: '', phone: '', gender: 'male', idCard: '', age: '', dept: '', position: '', role: 'staff', status: 'active', wbPerms: [] })
const editingMemberId = ref(null)
let editOriginalStatus = 'active'

const openEditMember = (row) => {
  editingMemberId.value = row.memberId != null ? row.memberId : row.id
  editOriginalStatus = row.status
  editForm.name = row.name === '—' ? '' : row.name
  editForm.phone = row.phone === '—' ? '' : row.phone
  // 性别缺省按男（对齐原型 eGender = m.gender || '男'）；身份证号回填脱敏串，
  // 保存时仅提交完整 18 位值（脱敏串不覆盖后端）；年龄可编辑，身份证为完整值时后端会重算。
  editForm.gender = row.gender === 'female' ? 'female' : 'male'
  editForm.idCard = row.idCard === '—' ? '' : (row.idCard || '')
  editForm.age = row.age != null ? row.age : ''
  editForm.dept = row.dept === '—' ? (currentEnterpriseName.value || '') : row.dept
  editForm.position = row.position === '—' ? '' : row.position
  editForm.role = row.role || 'staff'
  editForm.status = row.status || 'active'
  // 后端返回统一 code → 开关树中文名（'企业成员'/'员工列表' 同源于 MEMBER_MANAGE）
  editForm.wbPerms = row.wbPermNames ? [...row.wbPermNames] : permCodesToWbNames(row.permissions || [])
  editVisible.value = true
}
const onToggleWb = (arr, name, checked) => {
  if (checked) { if (!arr.includes(name)) arr.push(name) }
  else { const i = arr.indexOf(name); if (i >= 0) arr.splice(i, 1) }
}
const saveEditMember = async () => {
  if (!editForm.name.trim()) { ElMessage.warning('请填写成员姓名'); return }
  if (!enterpriseId.value || editingMemberId.value == null) { ElMessage.warning('缺少成员上下文，请刷新后重试'); return }
  // 姓名 / 手机号 / 所属公司来自账号维度，后端成员接口不提供改写入口，此处仅提交职位 + 角色 + 性别/身份证/年龄 + 工作台权限
  try {
    // 身份证号仅在为完整 18 位（数字+末位校验位 X/x）时提交，避免把脱敏串写回后端；
    // 完整值后端会据此重算年龄，age 字段作为手动覆盖值一并提交。
    const idCardVal = String(editForm.idCard || '').trim()
    const fullIdCard = /^[0-9Xx]{18}$/.test(idCardVal) ? idCardVal : undefined
    const ageVal = editForm.age === '' || editForm.age == null ? undefined : Number(editForm.age)
    await updateMemberRole(enterpriseId.value, editingMemberId.value, {
      role: editForm.role,
      title: editForm.position.trim(),
      gender: editForm.gender,
      idCard: fullIdCard,
      age: Number.isFinite(ageVal) ? ageVal : undefined,
    })
    await updateMemberPermissions(enterpriseId.value, editingMemberId.value, editForm.wbPerms)
  } catch (err) {
    console.warn('[EnterpriseMember] 成员更新失败:', err.message)
    return
  }
  // 在职状态：仅当用户改动状态时才调 PUT status（超级管理员置离职会被后端拒绝，失败保留原状）
  let statusFailed = false
  if (editForm.status !== editOriginalStatus) {
    try {
      await updateMemberStatus(enterpriseId.value, editingMemberId.value, editForm.status)
    } catch (err) {
      console.warn('[EnterpriseMember] 成员在职状态更新失败:', err.message)
      statusFailed = true
    }
  }
  editVisible.value = false
  await loadMembers()
  if (statusFailed) {
    ElMessage.warning('成员信息已更新，但在职状态修改未生效')
  } else {
    toast(`成员信息已更新（角色：${ROLE_NAME_MAP[editForm.role] || editForm.role}）`)
  }
}

/* ===================== 批量改权限 ===================== */
const batchVisible = ref(false)
const batchWb = ref([])
const batchNames = ref([])
const openBatchPermModal = () => {
  if (!selectedIds.value.length) { ElMessage.warning('请先勾选要修改的成员'); return }
  batchNames.value = [...selectedNames.value]
  batchWb.value = []
  batchVisible.value = true
}
const saveBatchPerm = async () => {
  if (!enterpriseId.value || !selectedIds.value.length) { ElMessage.warning('请先勾选要修改的成员'); return }
  const memberIds = [...selectedIds.value]
  try {
    await batchUpdateMemberPermissions(enterpriseId.value, memberIds, batchWb.value)
  } catch (err) {
    console.warn('[EnterpriseMember] 批量改权限失败:', err.message)
    return
  }
  batchVisible.value = false
  await loadMembers()
  toast(`已批量更新 ${memberIds.length} 名成员的工作台权限`)
}

/* ===================== 申请列表 ===================== */
const applies = ref([])
const applyFilters = reactive({ status: 'all', keyword: '', dateFrom: '', dateTo: '' })
const applyPage = ref(1)
const applySize = ref(10)

const filteredApplies = computed(() => applies.value.filter((a) => {
  if (applyFilters.status !== 'all' && a.status !== applyFilters.status) return false
  const kw = applyFilters.keyword.trim()
  if (kw && String(a.name).indexOf(kw) < 0 && String(a.phone).indexOf(kw) < 0) return false
  const day = String(a.time || '').substring(0, 10)
  if (applyFilters.dateFrom && (!day || day < applyFilters.dateFrom)) return false
  if (applyFilters.dateTo && (!day || day > applyFilters.dateTo)) return false
  return true
}))
const pagedApplies = computed(() => {
  const start = (applyPage.value - 1) * applySize.value
  return filteredApplies.value.slice(start, start + applySize.value)
})
const pendingCount = computed(() => applies.value.filter((a) => a.status === 'pending').length)

// 状态 / 关键词 / 时间区间均在前端过滤（待审批角标要统计全量，故列表按 status=all 拉取）
const loadApplies = async () => {
  if (!enterpriseId.value) { applies.value = []; return }
  try {
    const res = await listApplies({ enterpriseId: enterpriseId.value, status: 'all', page: 0, size: LOAD_SIZE })
    applies.value = res.rows
  } catch (err) {
    console.warn('[EnterpriseMember] 入企申请加载失败:', err.message)
    applies.value = []
  }
}
const resetApplyFilters = () => {
  applyFilters.status = 'all'; applyFilters.keyword = ''; applyFilters.dateFrom = ''; applyFilters.dateTo = ''
  applyPage.value = 1
}
// 审批落在 enterprise_join_apply：/admin/join-applies/{id}/approve|reject（api 层回传 source）
const reviewApply = async (row, result) => {
  if (!row || row.id == null) { ElMessage.warning('缺少申请记录，无法审批'); return }
  const tip = result === 'approved' ? `确认同意「${row.name}」加入企业？` : `确认拒绝「${row.name}」的加入申请？`
  try {
    await ElMessageBox.confirm(tip, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: result === 'approved' ? 'success' : 'warning',
    })
  } catch { return }
  try {
    await reviewApplyApi(row.id, result)
  } catch (err) {
    console.warn('[EnterpriseMember] 申请审批失败:', err.message)
    return
  }
  await loadApplies()
  toast(result === 'approved' ? `已同意「${row.name}」加入企业` : `已拒绝「${row.name}」的申请`)
}

/* ===================== 角色管理（/admin/employee-roles） ===================== */
const roles = ref([])
const rolePerms = ref({}) // employee_role.id → 本页权限 key 列表
const roleVisible = ref(false)
const roleModalTitle = ref('新建角色')
const editingRoleId = ref(null)
const roleFormIsSystem = ref(false)
const roleForm = reactive({ name: '', desc: '', permissions: [], color: '', icon: '' })

/** employee_role 是平台级角色，成员按 role_id 绑定；无绑定时回退按 member_role 归类。 */
const roleKeyOfId = (id) => {
  const r = roles.value.find((x) => String(x.id) === String(id))
  if (!r) return ''
  if (r.name.indexOf('超级管理员') >= 0) return 'super'
  if (r.name.indexOf('管理员') >= 0) return 'admin'
  return 'staff'
}
const matchesRole = (m, id) => {
  if (id == null) return false
  if (String(m.roleId) === String(id)) return true
  if (m.roleId) return false
  const key = roleKeyOfId(id)
  return !!key && m.role === key
}
const rolePermKeys = (id) => rolePerms.value[String(id)] || []
const roleSectionNames = (id) => {
  const set = new Set(rolePermKeys(id))
  const order = []
  for (const p of ROLE_PERMISSIONS) {
    if (set.has(p.key) && !order.includes(p.section)) order.push(p.section)
  }
  return order
}
const roleMemberNames = (id) => members.value.filter((m) => matchesRole(m, id)).map((m) => m.name)

const loadRoles = async () => {
  try {
    const res = await listEmployeeRoles()
    roles.value = res.rows
    const map = {}
    res.rows.forEach((r) => { map[String(r.id)] = r.keys || [] })
    rolePerms.value = map
  } catch (err) {
    console.warn('[EnterpriseMember] 角色列表加载失败:', err.message)
    roles.value = []
    rolePerms.value = {}
  }
  // 角色卡统计依赖成员数据（roleId 绑定）
  if (!members.value.length) await loadMembers()
}

const openRoleModal = (role) => {
  if (role) {
    editingRoleId.value = role.id
    roleModalTitle.value = `编辑角色「${role.name}」`
    roleForm.name = role.name
    roleForm.desc = role.desc || ''
    roleForm.permissions = [...rolePermKeys(role.id)]
    roleForm.color = role.color || ''
    roleForm.icon = role.icon || ''
    roleFormIsSystem.value = !!role.system
  } else {
    editingRoleId.value = null
    roleModalTitle.value = '新建角色'
    roleForm.name = ''
    roleForm.desc = ''
    roleForm.permissions = []
    roleForm.color = ''
    roleForm.icon = ''
    roleFormIsSystem.value = false
  }
  roleVisible.value = true
}
const sectionChecked = (sec) => {
  const keys = (ROLE_PERM_SECTIONS.find((s) => s.section === sec)?.items || []).map((i) => i.key)
  return keys.some((k) => roleForm.permissions.includes(k))
}
const onSectionToggle = (sec, checked) => {
  const keys = (ROLE_PERM_SECTIONS.find((s) => s.section === sec)?.items || []).map((i) => i.key)
  if (checked) keys.forEach((k) => { if (!roleForm.permissions.includes(k)) roleForm.permissions.push(k) })
  else roleForm.permissions = roleForm.permissions.filter((k) => !keys.includes(k))
}
const onRolePermToggle = (key, checked) => {
  if (checked) { if (!roleForm.permissions.includes(key)) roleForm.permissions.push(key) }
  else { const i = roleForm.permissions.indexOf(key); if (i >= 0) roleForm.permissions.splice(i, 1) }
}
const saveRole = async () => {
  if (!roleForm.name.trim()) { ElMessage.warning('请输入角色名称'); return }
  const isEdit = !!editingRoleId.value
  const payload = {
    name: roleForm.name.trim(),
    desc: roleForm.desc.trim(),
    permissions: [...roleForm.permissions],
    color: roleForm.color,
    icon: roleForm.icon,
  }
  try {
    if (isEdit) await updateEmployeeRole(editingRoleId.value, payload)
    else await createEmployeeRole(payload)
  } catch (err) {
    console.warn('[EnterpriseMember] 角色保存失败:', err.message)
    return
  }
  roleVisible.value = false
  await loadRoles()
  toast(isEdit ? '角色已更新' : '角色已创建')
}
const deleteRole = async () => {
  if (!editingRoleId.value) return
  const cnt = roleMemberNames(editingRoleId.value).length
  if (cnt > 0) { ElMessage.warning(`该角色下还有 ${cnt} 名成员，无法删除`); return }
  const r = roles.value.find((x) => x.id === editingRoleId.value)
  try {
    await ElMessageBox.confirm(`确认删除角色「${r?.name}」？删除后无法恢复`, '提示', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch { return }
  try {
    await deleteEmployeeRole(editingRoleId.value)
  } catch (err) {
    console.warn('[EnterpriseMember] 角色删除失败:', err.message)
    return
  }
  roleVisible.value = false
  await loadRoles()
  toast('角色已删除')
}
// 查看成员：原型为 switchTab('member') + filterMemberByRole(role.id)——切回成员列表，
// 把「角色」筛选下拉置为该角色并立即过滤（下拉此时展示当前生效的角色条件）。
const viewRoleMembers = (roleId) => {
  view.value = 'member'
  memberFilters.role = String(roleId)
  roleFilterId.value = String(roleId)
  memberPage.value = 1
  loadMembers()
}

/* ===================== 初始化 ===================== */
onMounted(async () => {
  await loadEnterprises()
  // 原型 init 即执行 updateMemberRoleFilter()：成员列表「角色」下拉需包含角色管理的全部角色，
  // 故初始化时同步拉取一次 employee-roles（角色 tab 切换时仍会刷新）
  await loadRoles()
})
</script>

<style scoped>
/* --- tab 切换（沿用 admin.css 体系，gap 28px） --- */
.title-switch-group { display: flex; align-items: center; gap: 28px; }
.title-switch { font-size: 16px; font-weight: 600; color: var(--text-muted); cursor: pointer; padding: 6px 0; user-select: none; transition: color 0.15s; }
.title-switch:hover { color: var(--text-secondary); }
.title-switch.active { color: var(--text-primary); cursor: default; }
.title-switch-divider { width: 1px; height: 14px; background: var(--border); }
.count-pill { background: var(--danger); color: #fff; font-size: 11px; font-weight: 600; padding: 1px 7px; border-radius: 10px; margin-left: 4px; }

/* --- 筛选栏 --- */
.filter-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.filter-item { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--text-secondary); }
.filter-item select, .filter-item input { padding: 7px 12px; border: 1px solid var(--border); border-radius: 8px; font-size: 13px; outline: none; background: #fff; }
.filter-item select:focus, .filter-item input:focus { border-color: var(--primary); }
.filter-item .filter-label + select,
.filter-item .filter-label + .search-box { min-width: 160px; }
.filter-item.keyword-item .filter-label + .search-box { min-width: 220px; }
.filter-label { font-size: 13px; color: var(--text-secondary); font-weight: 500; white-space: nowrap; flex-shrink: 0; }
.date-input { padding: 5px 8px; border: 1px solid #E5E5E5; border-radius: 6px; font-size: 12px; outline: none; }
.search-box { position: relative; }
.search-box i { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: var(--text-muted); font-size: 13px; }
.search-box.no-margin { margin: 0; }
.search-box.no-margin input { width: 240px; padding: 7px 12px 7px 32px; }

/* --- 表格容器（对齐原型 data-table 单元格间距与 hover） --- */
.table-container { overflow-x: auto; }
.table-container :deep(.el-table th.el-table__cell) { padding: 12px 0; }
.table-container :deep(.el-table td.el-table__cell) { padding: 14px 0; color: var(--text-primary); }
.table-container :deep(.el-table .cell) { padding-left: 16px; padding-right: 16px; }
.table-container :deep(.el-table td.el-table__cell),
.table-container :deep(.el-table th.el-table__cell.is-leaf) { border-bottom-color: var(--border); }
.table-container :deep(.el-table tbody tr:hover > td.el-table__cell) { background: #FAFAFA; }
.action-btns { display: flex; gap: 6px; }
.action-btns .btn { padding: 4px 10px; font-size: 12px; }
.btn-done { padding: 4px 10px; font-size: 12px; border: 1px solid var(--border); border-radius: 6px; background: #F9FAFB; color: var(--text-muted); display: inline-block; }
.empty-cell { text-align: center; padding: 48px 0; color: var(--text-muted); }
.empty-cell i { font-size: 32px; color: #D1D5DB; display: block; margin-bottom: 10px; }

/* --- 分页 --- */
.pagination { display: flex; align-items: center; justify-content: space-between; margin-top: 16px; }
.pagination-info { font-size: 13px; color: var(--text-secondary); }

/* --- 头像 / 角色标签 --- */
.avatar-cell { display: flex; align-items: center; gap: 10px; }
.avatar-cell .emp-info { display: flex; flex-direction: column; }
.emp-name { font-weight: 500; font-size: 14px; }
.emp-phone { font-size: 12px; color: var(--text-muted); }
.mini-avatar { width: 36px; height: 36px; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 600; color: #fff; flex-shrink: 0; }
.mini-avatar.orange { background: linear-gradient(135deg, #FF8C42 0%, #FF6B35 100%); }
.mini-avatar.blue { background: linear-gradient(135deg, #60A5FA 0%, #2563EB 100%); }
.mini-avatar.green { background: linear-gradient(135deg, #34D399 0%, #10B981 100%); }
.mini-avatar.purple { background: linear-gradient(135deg, #A78BFA 0%, #7C3AED 100%); }
.role-tag { display: inline-block; padding: 3px 8px; border-radius: 4px; font-size: 12px; }
.role-tag.super { background: #FFF0EB; color: var(--primary); }
.role-tag.admin { background: #EFF6FF; color: var(--secondary); }
.role-tag.staff { background: #F3F4F6; color: var(--text-secondary); }

/* --- 角色管理卡片 --- */
.role-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px; }
.role-card { border: 1px solid var(--border); border-radius: 12px; padding: 16px; background: #fff; transition: all 0.2s; }
.role-card:hover { box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06); }
.role-card-head { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; }
.role-icon { width: 40px; height: 40px; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 16px; }
.role-name { font-size: 15px; font-weight: 600; color: #1F2937; display: flex; align-items: center; }
.sys-badge { font-size: 11px; color: #9CA3AF; background: #F3F4F6; padding: 1px 6px; border-radius: 8px; margin-left: 6px; font-weight: 400; }
.role-desc { font-size: 12px; color: #9CA3AF; margin-top: 2px; }
.role-mod-line { font-size: 12px; color: #6B7280; margin-bottom: 4px; }
.role-perm-tags { margin-bottom: 12px; line-height: 1.8; display: flex; flex-wrap: wrap; gap: 4px; }
.perm-tag { font-size: 11px; padding: 2px 8px; border-radius: 10px; }
.no-data { font-size: 11px; color: #D1D5DB; }
.role-member-box { border-top: 1px dashed var(--border); padding-top: 10px; margin-bottom: 12px; }
.role-member-line { font-size: 12px; color: #6B7280; margin-bottom: 6px; }
.role-member-pills { display: flex; flex-wrap: wrap; gap: 2px 4px; }
.member-pill { font-size: 11px; color: #6B7280; background: #F3F4F6; padding: 2px 8px; border-radius: 10px; }
.role-actions { display: flex; gap: 8px; justify-content: flex-end; }

/* --- 弹窗通用表单（对齐原型 modal：圆角 16px、body padding 24px、60vh 内滚动） --- */
:deep(.proto-dialog.el-dialog) { border-radius: 16px; }
:deep(.proto-dialog .el-dialog__body) { padding: 24px; max-height: 60vh; overflow-y: auto; }
:deep(.proto-dialog .el-dialog__footer) { padding: 16px 24px; border-top: 1px solid var(--border); display: flex; justify-content: flex-end; gap: 12px; }
:deep(.proto-dialog .el-dialog__header) { padding: 20px 24px; border-bottom: 1px solid var(--border); margin-right: 0; }
:deep(.proto-dialog .el-dialog__title) { font-size: 18px; font-weight: 600; color: var(--text-primary); }
.form-group { margin-bottom: 16px; }
.form-label { display: block; font-size: 13px; font-weight: 500; margin-bottom: 6px; color: var(--text-primary); }
.form-input, .form-select { width: 100%; padding: 9px 12px; border: 1px solid var(--border); border-radius: 8px; font-size: 14px; outline: none; transition: border-color 0.2s; background-color: #fff; }
.form-input:focus, .form-select:focus { border-color: var(--primary); }
.form-select { appearance: none; background-image: url("data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%23999' d='M6 8L1 3h10z'/%3E%3C/svg%3E"); background-repeat: no-repeat; background-position: right 12px center; padding-right: 32px; }
.form-input.is-disabled { opacity: 0.6; background: var(--bg-page); cursor: not-allowed; }

/* --- 邀请链接 / 二维码 --- */
.invite-link-box { background: var(--bg-page); border: 1px dashed var(--border); border-radius: 8px; padding: 12px; font-size: 13px; color: var(--text-secondary); word-break: break-all; display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.invite-qrcode { width: 120px; height: 120px; margin: 16px auto 0; background: repeating-conic-gradient(#1F2937 0% 25%, #fff 0% 50%) 0 0 / 20px 20px; border: 6px solid #fff; box-shadow: 0 0 0 1px var(--border), 0 4px 12px rgba(0, 0, 0, 0.08); border-radius: 8px; position: relative; cursor: pointer; transition: transform 0.15s; }
.invite-qrcode:hover { transform: scale(1.05); }
.invite-qrcode::after { content: '\f291'; font-family: 'Font Awesome 7 Free'; font-weight: 900; position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; font-size: 28px; color: #fff; background: var(--primary); width: 40px; height: 40px; margin: auto; border-radius: 8px; }
.invite-qrcode.has-image { background: #fff; overflow: hidden; }
.invite-qrcode.has-image::after { display: none; }
.invite-qr-img { width: 100%; height: 100%; object-fit: contain; display: block; }
.invite-tip { text-align: center; font-size: 12px; color: var(--text-muted); margin-top: 12px; }

/* --- 员工详情（折叠分区） --- */
.detail-head { display: flex; align-items: center; gap: 16px; margin-bottom: 20px; padding-bottom: 20px; border-bottom: 1px solid var(--border); }
.detail-name { font-size: 16px; font-weight: 600; }
.detail-sub { font-size: 12px; color: var(--text-muted); margin-top: 2px; }
.detail-section-title { font-size: 14px; font-weight: 600; color: var(--text-primary); margin-bottom: 12px; margin-top: 20px; cursor: pointer; user-select: none; display: flex; align-items: center; }
.detail-section-title:first-of-type { margin-top: 0; }
.detail-section-title:hover { color: var(--primary); }
.detail-section-title .collapse-arrow { margin-left: auto; font-size: 11px; color: #9CA3AF; transition: transform 0.2s; }
.detail-section-title.collapsed .collapse-arrow { transform: rotate(-90deg); }
.detail-section-hint { font-size: 12px; color: #999; font-weight: 400; margin-left: 8px; }
.detail-section-body.collapsed { display: none; }
.detail-info-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
.detail-info-item { display: flex; flex-direction: column; gap: 4px; }
.detail-info-label { font-size: 12px; color: var(--text-muted); }
.detail-info-value { font-size: 14px; font-weight: 500; }

.proj-list { display: flex; flex-direction: column; gap: 8px; margin-top: 6px; }
.proj-item { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid var(--border); border-radius: 8px; background: #FAFAFA; }
.proj-icon { font-size: 14px; color: var(--primary); }
.proj-main { flex: 1; }
.proj-name { font-size: 14px; color: #1F2937; font-weight: 500; }
.proj-sub { font-size: 12px; color: #9CA3AF; margin-top: 2px; }
.proj-status { display: flex; align-items: center; gap: 4px; font-size: 12px; }
.proj-dot { width: 6px; height: 6px; border-radius: 50%; display: inline-block; }
.proj-empty { font-size: 13px; color: #9CA3AF; padding: 12px; text-align: center; }

.dp-list { display: flex; flex-direction: column; gap: 12px; }
.dp-org-label { font-size: 11px; font-weight: 600; color: #FF6B35; display: flex; align-items: center; gap: 4px; margin-bottom: 4px; }
.dp-org-label i { font-size: 9px; }
.dp-org-rows, .dp-sec-rows { display: flex; flex-direction: column; gap: 4px; }
.dp-org-row { display: flex; align-items: center; gap: 10px; padding: 8px 10px; border-radius: 8px; background: #FFF7F0; }
.dp-org-icon { font-size: 13px; color: var(--primary); width: 16px; text-align: center; }
.dp-org-name { flex: 1; font-size: 13px; color: #333; }
.dp-org-tag { font-size: 12px; color: #9CA3AF; }
.dp-item { display: flex; align-items: center; gap: 10px; padding: 8px 10px; border-radius: 8px; }
.dp-item > i:first-child { font-size: 13px; width: 16px; text-align: center; }
.dp-item-name { flex: 1; font-size: 13px; }

/* --- 工作台权限开关树（编辑 / 批量） --- */
.perm-switch { position: relative; display: inline-block; width: 38px; height: 20px; flex-shrink: 0; }
.perm-switch input { opacity: 0; width: 0; height: 0; }
.perm-slider { position: absolute; cursor: pointer; inset: 0; background-color: #E5E5E5; transition: 0.2s; border-radius: 20px; }
.perm-slider:before { position: absolute; content: ""; height: 16px; width: 16px; left: 2px; bottom: 2px; background-color: white; transition: 0.2s; border-radius: 50%; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.15); }
.perm-switch input:checked + .perm-slider { background-color: #10B981; }
.perm-switch input:checked + .perm-slider:before { transform: translateX(18px); }
.perm-tree { background: #FFF9F5; border: 1px solid #FDE8DC; border-radius: 10px; padding: 6px 0; }
.perm-group { padding: 6px 14px; }
.perm-group-title { display: flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 600; color: #FF6B35; margin: 6px 0 4px; }
.perm-group-title i { font-size: 11px; }
.perm-item { display: flex; align-items: center; justify-content: space-between; padding: 7px 10px; margin: 2px 0; border-radius: 7px; background: #FFF; }
.perm-item-left { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #333; }
.perm-item-left i { width: 16px; text-align: center; color: #999; font-size: 12px; }
.perm-item-tag { font-size: 11px; color: #999; }
.batch-selected { padding: 10px 12px; border: 1px solid var(--border); border-radius: 8px; background: #fafafa; font-size: 13px; color: #374151; line-height: 1.8; }
.batch-tip { font-size: 12px; color: #9ca3af; margin-top: -4px; }

/* --- 移除确认 --- */
.remove-dialog :deep(.el-dialog__footer) { justify-content: flex-end; }
.remove-body { text-align: center; padding: 12px 0 8px; }
.remove-icon { width: 56px; height: 56px; margin: 0 auto 16px; background: #FEF2F2; border-radius: 50%; display: flex; align-items: center; justify-content: center; }
.remove-icon i { font-size: 24px; color: var(--danger); }
.remove-title { font-size: 16px; font-weight: 600; color: #1F2937; margin-bottom: 8px; }
.remove-desc { font-size: 13px; color: #6B7280; line-height: 1.8; max-width: 340px; margin: 0 auto; }

/* --- 角色权限勾选树（角色弹窗） --- */
.role-perm-list { margin-top: 6px; display: flex; flex-direction: column; gap: 14px; }
.rp-org-label { font-size: 12px; font-weight: 600; color: #FF6B35; display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
.rp-org-label i { font-size: 10px; }
.rp-org-row { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid var(--border); border-radius: 8px; background: #FFF7F0; }
.rp-org-row input { accent-color: var(--primary); width: 16px; height: 16px; }
.rp-org-icon { font-size: 14px; color: var(--primary); }
.rp-org-name { flex: 1; font-size: 14px; color: #333; }
.rp-section-head { display: flex; align-items: center; gap: 8px; margin-top: 4px; margin-bottom: 6px; }
.rp-section-check { accent-color: var(--primary); width: 14px; height: 14px; }
.rp-section-title { font-size: 12px; font-weight: 600; color: #FF6B35; display: flex; align-items: center; gap: 6px; }
.rp-section-title i { font-size: 10px; }
.rp-items { display: flex; flex-direction: column; gap: 6px; }
.rp-item { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid var(--border); border-radius: 8px; cursor: pointer; background: #fff; }
.rp-item.on { background: #FFF7F0; }
.rp-item input { accent-color: var(--primary); width: 16px; height: 16px; }
.rp-item-main { flex: 1; min-width: 0; }
.rp-item-name { font-size: 14px; font-weight: 500; color: #333; }
.rp-item-desc { font-size: 12px; color: #999; margin-top: 2px; }
.role-delete-btn { color: #EF4444; border-color: #FCA5A5; }
</style>
