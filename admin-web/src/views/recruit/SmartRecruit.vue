<template>
  <div>
    <div class="page-header">
      <h1 class="page-title">智能招聘</h1>
      <p class="page-desc">智能化招聘管理，包含简历库、岗位管理、直播橱窗与候选人追踪</p>
    </div>

    <!-- ===== 统计卡（/admin/positions/stats + /admin/resumes/stats） ===== -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-card-header"><span class="stat-card-title">岗位总数</span><div class="stat-card-icon"><i class="fas fa-briefcase"></i></div></div>
        <div class="stat-card-value">{{ stats.positionTotal }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-card-header"><span class="stat-card-title">在招岗位</span><div class="stat-card-icon green"><i class="fas fa-people-group"></i></div></div>
        <div class="stat-card-value">{{ stats.positionActive }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-card-header"><span class="stat-card-title">简历总数</span><div class="stat-card-icon blue"><i class="fas fa-file-lines"></i></div></div>
        <div class="stat-card-value">{{ stats.resumeTotal }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-card-header"><span class="stat-card-title">导入简历</span><div class="stat-card-icon yellow"><i class="fas fa-upload"></i></div></div>
        <div class="stat-card-value">{{ stats.resumeImport }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-card-header"><span class="stat-card-title">简历投递</span><div class="stat-card-icon"><i class="fas fa-paper-plane"></i></div></div>
        <div class="stat-card-value">{{ stats.applicationTotal }}</div>
      </div>
    </div>

    <div class="card">
      <div class="card-header">
        <div class="card-title title-switch-group">
          <span class="title-switch" :class="{ active: tab === 'resume' }" @click="tab = 'resume'">简历库</span>
          <span class="title-switch-divider"></span>
          <span class="title-switch" :class="{ active: tab === 'position' }" @click="tab = 'position'">岗位管理</span>
          <span class="title-switch-divider"></span>
          <span class="title-switch" :class="{ active: tab === 'candidate' }" @click="tab = 'candidate'">候选人</span>
          <span class="title-switch-divider"></span>
          <span class="title-switch" :class="{ active: tab === 'live' }" @click="tab = 'live'">直播橱窗</span>
        </div>
      </div>

      <!-- ===== 简历库 ===== -->
      <div v-show="tab === 'resume'">
        <div class="filter-bar">
          <div class="filter-item">
            <span class="filter-label">姓名</span>
            <el-input v-model="resInput.name" placeholder="请输入姓名" clearable style="width: 240px;" prefix-icon="Search" @keyup.enter="searchResumes" />
          </div>
          <div class="filter-item">
            <span class="filter-label">意向岗位</span>
            <el-input v-model="resInput.job" placeholder="请输入意向岗位" clearable style="width: 160px;" />
          </div>
          <div class="filter-item">
            <span class="filter-label">简历上传时间</span>
            <el-date-picker v-model="resInput.date" type="date" placeholder="请选择日期" value-format="YYYY-MM-DD" style="width: 150px;" />
          </div>
          <button class="btn btn-primary btn-sm" @click="searchResumes"><i class="fas fa-search"></i> 查询</button>
          <button class="btn btn-outline btn-sm" @click="resetResumeFilters"><i class="fas fa-arrow-rotate-left"></i> 重置</button>
          <div style="margin-left: auto;"></div>
          <button class="btn btn-primary btn-sm" @click="openResumeImport"><i class="fas fa-upload"></i> 导入简历</button>
          <input ref="resumeFileInput" type="file" accept=".pdf,.doc,.docx" multiple style="display: none;" @change="handleResumeFilesPicked" />
        </div>
        <el-table :data="resumePageData" v-loading="resumeLoading" :header-cell-style="{ background: '#F3F4F6', color: '#6B7280', fontWeight: 500, fontSize: '13px' }">
          <el-table-column prop="name" label="姓名" min-width="100" />
          <el-table-column prop="job" label="意向岗位" min-width="140" show-overflow-tooltip />
          <el-table-column prop="exp" label="工作经验" min-width="100" />
          <el-table-column prop="edu" label="学历" min-width="90" />
          <el-table-column prop="uploadTime" label="简历上传时间" min-width="170" />
          <el-table-column label="操作" width="320" align="right" fixed="right">
            <template #default="{ row }">
              <div class="action-btns">
                <button class="btn btn-outline btn-sm" @click="openResumeDoc(row)"><i class="fas fa-file-lines"></i> 查看附件简历</button>
                <button class="btn btn-outline btn-sm" :disabled="joiningResumeId === row.id" @click="joinCandidate(row)"><i class="fas fa-user-plus"></i> 加入候选人</button>
                <button class="btn btn-outline btn-sm danger-text" @click="confirmDeleteResume(row)"><i class="fas fa-trash"></i> 删除</button>
              </div>
            </template>
          </el-table-column>
          <template #empty><div class="table-empty"><i class="fas fa-folder-open"></i>暂无匹配的简历数据</div></template>
        </el-table>
        <div class="pagination">
          <div class="pagination-info">共 {{ resumeTotal }} 条记录</div>
          <el-pagination v-model:current-page="resumePage" :page-size="RESUME_PAGE_SIZE" :total="resumeTotal"
            layout="prev, pager, next" background @current-change="loadResumes" />
        </div>
      </div>

      <!-- ===== 岗位管理 ===== -->
      <div v-show="tab === 'position'">
        <div class="filter-bar">
          <div class="filter-item keyword-item">
            <span class="filter-label">关键词</span>
            <el-input v-model="posInput.keyword" placeholder="岗位名称 / 编号" clearable style="width: 240px;" prefix-icon="Search" @keyup.enter="searchPositions" />
          </div>
          <div class="filter-item">
            <span class="filter-label">岗位类型</span>
            <el-select v-model="posInput.type" style="width: 160px;">
              <el-option label="全部类型" value="all" />
              <el-option label="电子厂" value="electronics" />
              <el-option label="仓储" value="warehouse" />
              <el-option label="物流" value="logistics" />
              <el-option label="餐饮" value="catering" />
              <el-option label="制造业" value="manufacture" />
              <el-option label="汽车" value="auto" />
              <el-option label="服务业" value="service" />
            </el-select>
          </div>
          <button class="btn btn-primary btn-sm" @click="searchPositions"><i class="fas fa-search"></i> 查询</button>
          <button class="btn btn-outline btn-sm" @click="resetPositionFilters"><i class="fas fa-arrow-rotate-left"></i> 重置</button>
          <button class="btn btn-primary btn-sm" style="margin-left: auto;" @click="publishPosition"><i class="fas fa-plus"></i> 发布岗位</button>
        </div>
        <el-table :data="positionPageData" v-loading="positionLoading" :header-cell-style="{ background: '#F3F4F6', color: '#6B7280', fontWeight: 500, fontSize: '13px' }"
          @selection-change="onPosSelect">
          <el-table-column type="selection" width="40" />
          <el-table-column label="岗位名称" min-width="220">
            <template #default="{ row }">
              <div class="job-name-cell">
                <div class="job-info">
                  <span class="job-name">{{ row.name }}<span class="job-type-tag" :class="row.typeClass">{{ row.type }}</span></span>
                  <span class="job-id">{{ row.code }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="招聘人数" width="110">
            <template #default="{ row }"><span class="count-cell">{{ row.headcount }}</span></template>
          </el-table-column>
          <el-table-column label="招聘状态" width="110">
            <template #default="{ row }">
              <span class="status-badge" :class="row.statusClass">{{ row.statusName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="所属老板" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.bossName }}</template>
          </el-table-column>
          <el-table-column prop="publishTime" label="发布时间" min-width="150" />
          <el-table-column label="操作" width="230" align="right" fixed="right">
            <template #default="{ row }">
              <div class="action-btns">
                <button class="btn btn-outline btn-sm" @click="showPositionDetail(row)"><i class="fas fa-eye"></i> 详情</button>
                <button class="btn btn-primary btn-sm" @click="editPosition(row)"><i class="fas fa-edit"></i> 编辑</button>
                <button class="btn btn-outline btn-sm danger-text" @click="confirmDeletePos(row)"><i class="fas fa-trash"></i> 删除</button>
              </div>
            </template>
          </el-table-column>
          <template #empty><div class="table-empty"><i class="fas fa-folder-open"></i>暂无匹配的岗位数据</div></template>
        </el-table>
        <div class="pagination">
          <div class="pagination-info">共 {{ positionTotal }} 条记录</div>
          <el-pagination v-model:current-page="positionPage" :page-size="POSITION_PAGE_SIZE" :total="positionTotal"
            layout="prev, pager, next" background @current-change="loadPositions" />
        </div>
      </div>

      <!-- ===== 候选人 ===== -->
      <div v-show="tab === 'candidate'">
        <div class="filter-bar">
          <div class="filter-item">
            <span class="filter-label">候选人</span>
            <el-input v-model="candInput.name" placeholder="请输入候选人姓名" clearable style="width: 240px;" prefix-icon="Search" @keyup.enter="searchCandidates" />
          </div>
          <div class="filter-item">
            <span class="filter-label">应聘岗位</span>
            <el-select v-model="candInput.positionId" placeholder="请选择应聘岗位" clearable filterable style="width: 160px;">
              <el-option v-for="p in positionOptions" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </div>
          <div class="filter-item">
            <span class="filter-label">应聘时间</span>
            <el-date-picker v-model="candInput.date" type="date" placeholder="请选择日期" value-format="YYYY-MM-DD" style="width: 150px;" />
          </div>
          <button class="btn btn-primary btn-sm" @click="searchCandidates"><i class="fas fa-search"></i> 查询</button>
          <button class="btn btn-outline btn-sm" @click="resetCandidateFilters"><i class="fas fa-arrow-rotate-left"></i> 重置</button>
          <div style="margin-left: auto;"></div>
          <button class="btn btn-primary btn-sm" @click="openAddCandidateModal"><i class="fas fa-plus"></i> 新增候选人</button>
        </div>
        <el-table :data="candidatePageData" v-loading="candidateLoading" :header-cell-style="{ background: '#F3F4F6', color: '#6B7280', fontWeight: 500, fontSize: '13px' }">
          <el-table-column prop="name" label="候选人" min-width="110" />
          <el-table-column prop="job" label="应聘岗位" min-width="150" show-overflow-tooltip />
          <el-table-column prop="applyTime" label="应聘时间" min-width="150" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <span class="status-badge" :class="candStatusCls(row.statusName)">{{ row.statusName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="330" align="right" fixed="right">
            <template #default="{ row }">
              <span class="op-link" @click="openCandDetail(row)">详情</span>
              <span class="op-link" @click="openJoinProject(row)">加入项目</span>
              <span v-for="act in candidateActions[row.statusName] || []" :key="act.act" class="op-link" @click="doCandAction(act.act, row)">
                {{ act.label }}
              </span>
              <span class="op-link danger-link" @click="confirmDeleteCandidate(row)">删除</span>
            </template>
          </el-table-column>
          <template #empty><div class="table-empty"><i class="fas fa-folder-open"></i>暂无匹配的候选人数据</div></template>
        </el-table>
        <div class="pagination">
          <div class="pagination-info">共 {{ candidateTotal }} 条记录</div>
          <el-pagination v-model:current-page="candidatePage" :page-size="CANDIDATE_PAGE_SIZE" :total="candidateTotal"
            layout="prev, pager, next" background @current-change="loadCandidates" />
        </div>
      </div>

      <!-- ===== 直播橱窗 ===== -->
      <div v-show="tab === 'live'">
        <div class="filter-bar">
          <div class="filter-item">
            <span class="filter-label">直播名称</span>
            <el-input v-model="liveInput.title" placeholder="请输入直播名称" clearable style="width: 240px;" prefix-icon="Search" @keyup.enter="searchLives" />
          </div>
          <div class="filter-item">
            <span class="filter-label">直播人员</span>
            <el-input v-model="liveInput.host" placeholder="请输入直播人员" clearable style="width: 140px;" />
          </div>
          <div class="filter-item">
            <span class="filter-label">直播账号</span>
            <el-input v-model="liveInput.account" placeholder="请输入直播账号" clearable style="width: 150px;" />
          </div>
          <div class="filter-item">
            <span class="filter-label">创建时间</span>
            <el-date-picker v-model="liveInput.date" type="date" placeholder="请选择日期" value-format="YYYY-MM-DD" style="width: 150px;" />
          </div>
          <button class="btn btn-primary btn-sm" @click="searchLives"><i class="fas fa-search"></i> 查询</button>
          <button class="btn btn-outline btn-sm" @click="resetLiveFilters"><i class="fas fa-arrow-rotate-left"></i> 重置</button>
          <button class="btn btn-primary btn-sm" style="margin-left: auto;" @click="openLiveCreate"><i class="fas fa-plus"></i> 新建直播橱窗</button>
        </div>
        <el-table :data="livePageData" v-loading="liveLoading" :header-cell-style="{ background: '#F3F4F6', color: '#6B7280', fontWeight: 500, fontSize: '13px' }">
          <el-table-column label="直播名称" min-width="200">
            <template #default="{ row }"><span style="font-weight: 500;">{{ row.title }}</span></template>
          </el-table-column>
          <el-table-column prop="host" label="直播人员" min-width="110" />
          <el-table-column prop="account" label="直播账号" min-width="150" />
          <el-table-column prop="time" label="创建时间" min-width="150" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <span class="status-badge" :class="liveStatusCls(row.status)">{{ row.statusName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" align="right" fixed="right">
            <template #default="{ row }">
              <span v-for="act in liveActions[row.status] || []" :key="act.status" class="op-link" @click="doLiveAction(row, act.status)">
                {{ act.label }}
              </span>
              <span class="op-link danger-link" @click="confirmDeleteLive(row)">删除</span>
            </template>
          </el-table-column>
          <template #empty><div class="table-empty"><i class="fas fa-folder-open"></i>暂无匹配的直播橱窗数据</div></template>
        </el-table>
        <div class="pagination">
          <div class="pagination-info">共 {{ liveTotal }} 条记录</div>
          <el-pagination v-model:current-page="livePage" :page-size="LIVE_PAGE_SIZE" :total="liveTotal"
            layout="prev, pager, next" background @current-change="loadLives" />
        </div>
      </div>
    </div>

    <!-- ===== 附件简历预览弹窗（A4 纸样式） ===== -->
    <div class="resume-doc-overlay" v-show="resumeDocVisible" @click.self="resumeDocVisible = false">
      <div class="resume-doc-panel" @click.stop>
        <div class="resume-doc-header">
          <div class="title"><i class="fas fa-file-lines"></i><span>{{ resumeDoc ? resumeDoc.name + ' 的附件简历' : '附件简历' }}</span></div>
          <button class="resume-doc-close" @click="resumeDocVisible = false"><i class="fas fa-xmark"></i></button>
        </div>
        <div class="resume-doc-body">
          <div class="resume-paper" v-if="resumeDoc">
            <div class="resume-name">{{ resumeDoc.name }}</div>
            <div class="resume-intent">求职意向：{{ resumeDoc.job }}</div>
            <div class="resume-base">
              <span><i class="fas fa-phone" style="margin-right: 5px;"></i>{{ resumeDoc.phone || '—' }}</span>
              <span><i class="fas fa-graduation-cap" style="margin-right: 5px;"></i>{{ resumeDoc.edu || '—' }}</span>
              <span><i class="fas fa-briefcase" style="margin-right: 5px;"></i>工作经验 {{ resumeDoc.exp || '—' }}</span>
              <span><i class="fas fa-location-dot" style="margin-right: 5px;"></i>{{ resumeDoc.city || '—' }}</span>
            </div>
            <div class="resume-tabs" style="margin-top: 20px;">
              <div v-for="t in RESUME_TABS" :key="t.key" class="resume-tab" :class="{ active: resumeTab === t.key }" @click="resumeTab = t.key">{{ t.label }}</div>
            </div>
            <div v-show="resumeDocLoading" class="resume-item-desc">简历详情加载中…</div>
            <div class="resume-pane" v-show="resumeTab === 'basic'">
              <div class="resume-sec-title">基本信息</div>
              <div class="resume-item"><div class="resume-item-title"><span>联系电话</span></div><div class="resume-item-desc">{{ resumeDoc.phone || '—' }}</div></div>
              <div class="resume-item"><div class="resume-item-title"><span>最高学历</span></div><div class="resume-item-desc">{{ resumeDoc.edu || '—' }}</div></div>
              <div class="resume-item"><div class="resume-item-title"><span>工作年限</span></div><div class="resume-item-desc">{{ resumeDoc.exp || '—' }}</div></div>
              <div class="resume-item"><div class="resume-item-title"><span>所在城市</span></div><div class="resume-item-desc">{{ resumeDoc.city || '—' }}</div></div>
              <div class="resume-item"><div class="resume-item-title"><span>期望薪资</span></div><div class="resume-item-desc">{{ resumeDoc.expectedSalary || '—' }}</div></div>
              <div class="resume-item"><div class="resume-item-title"><span>简历来源</span></div><div class="resume-item-desc">{{ resumeSourceName(resumeDoc.source) }}</div></div>
              <div class="resume-item"><div class="resume-item-title"><span>归属老板</span></div><div class="resume-item-desc">{{ resumeDoc.bossName || '—' }}</div></div>
              <div class="resume-item"><div class="resume-item-title"><span>上传时间</span></div><div class="resume-item-desc">{{ resumeDoc.uploadTime }}</div></div>
            </div>
            <div class="resume-pane" v-show="resumeTab === 'edu'">
              <div class="resume-sec-title">教育经历</div>
              <div class="resume-item" v-for="(item, i) in resumeDoc.eduExperiences" :key="item.id || 'edu-' + i">
                <div class="resume-item-title"><span>{{ item.title || '—' }}</span><span class="time">{{ item.period }}</span></div>
                <div class="resume-item-desc">{{ item.org || '' }}<template v-if="item.description"><br>{{ item.description }}</template></div>
              </div>
              <div v-if="!resumeDoc.eduExperiences.length" class="resume-item-desc">暂无教育经历</div>
            </div>
            <div class="resume-pane" v-show="resumeTab === 'work'">
              <div class="resume-sec-title">工作经历</div>
              <div class="resume-item" v-for="(item, i) in resumeDoc.workExperiences" :key="item.id || 'work-' + i">
                <div class="resume-item-title"><span>{{ item.title || '—' }}</span><span class="time">{{ item.period }}</span></div>
                <div class="resume-item-desc">{{ item.org || '' }}<template v-if="item.description"><br>{{ item.description }}</template></div>
              </div>
              <div v-if="!resumeDoc.workExperiences.length" class="resume-item-desc">暂无工作经历</div>
            </div>
            <div class="resume-pane" v-show="resumeTab === 'project'">
              <div class="resume-sec-title">项目经验</div>
              <div class="resume-item" v-for="(item, i) in resumeDoc.projectExperiences" :key="item.id || 'project-' + i">
                <div class="resume-item-title"><span>{{ item.title || '—' }}</span><span class="time">{{ item.period }}</span></div>
                <div class="resume-item-desc">{{ item.description || item.org || '暂无描述' }}</div>
              </div>
              <div v-if="!resumeDoc.projectExperiences.length" class="resume-item-desc">暂无项目经验</div>
            </div>
            <div class="resume-pane" v-show="resumeTab === 'file'">
              <div class="resume-sec-title">
                附件原件
                <a v-if="resumeDoc.filePreviewUrl" class="op-link" style="margin-left: 12px;" :href="resumeDoc.filePreviewUrl" target="_blank" rel="noopener">在新窗口打开</a>
              </div>
              <img v-if="resumeDoc.filePreviewUrl && resumeDoc.filePreviewIsImage" :src="resumeDoc.filePreviewUrl" style="width: 100%; border: 1px solid var(--border); border-radius: 8px;" />
              <iframe v-else-if="resumeDoc.filePreviewUrl" :src="resumeDoc.filePreviewUrl" style="width: 100%; height: 520px; border: 1px solid var(--border); border-radius: 8px; background: #fff;" />
              <div v-else class="resume-item-desc">该简历暂无可预览的附件原件</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 候选人详情弹窗 ===== -->
    <div class="resume-doc-overlay" v-show="candDetailVisible" @click.self="candDetailVisible = false">
      <div class="resume-doc-panel cand-detail-panel" @click.stop>
        <div class="resume-doc-header">
          <div class="title"><i class="fas fa-user"></i>候选人详情</div>
          <button class="resume-doc-close" @click="candDetailVisible = false"><i class="fas fa-xmark"></i></button>
        </div>
        <div class="resume-doc-body cand-detail-body" v-if="candDetail">
          <div class="cand-head">
            <div class="cand-avatar" :style="{ background: avatarBg(candDetail.name) }">{{ (candDetail.name || '?').charAt(0) }}</div>
            <div>
              <div class="cand-head-name">{{ candDetail.name }}
                <span class="status-badge" :class="candStatusCls(candDetail.statusName)">{{ candDetail.statusName }}</span>
              </div>
              <div class="cand-head-sub">{{ candDetail.gender || '—' }} · {{ candDetail.age || '—' }}岁</div>
            </div>
          </div>
          <div class="cand-block">
            <div class="cand-block-title"><i class="fas fa-file-signature"></i>应聘信息</div>
            <div class="cand-line"><span class="k">应聘岗位</span><span class="v">{{ candDetail.job }}</span></div>
            <div class="cand-line"><span class="k">应聘时间</span><span class="v">{{ candDetail.applyTime }}</span></div>
            <div class="cand-line"><span class="k">面试时间</span><span class="v">{{ candDetail.interviewTime || '—' }}</span></div>
            <div class="cand-line"><span class="k">所属老板</span><span class="v">{{ candDetail.bossName || '—' }}</span></div>
            <div class="cand-line" v-if="candDetail.remark"><span class="k">备注</span><span class="v">{{ candDetail.remark }}</span></div>
          </div>
          <div class="cand-block">
            <div class="cand-block-title"><i class="fas fa-user"></i>个人信息</div>
            <div class="cand-line"><span class="k">手机号</span><span class="v">{{ candDetail.phone || '—' }}</span></div>
            <div class="cand-line"><span class="k">现居住地</span><span class="v">{{ candDetail.city || '—' }}</span></div>
            <div class="cand-line"><span class="k">工作经验</span><span class="v">{{ candDetail.expLabel || '—' }}</span></div>
            <div class="cand-line"><span class="k">学历</span><span class="v">{{ candDetail.edu || '—' }}</span></div>
          </div>
          <div class="cand-block">
            <div class="cand-block-title"><i class="fas fa-graduation-cap"></i>教育经历</div>
            <template v-if="candDetail.eduExperiences && candDetail.eduExperiences.length">
              <div class="cand-edu" v-for="(item, i) in candDetail.eduExperiences" :key="item.id || 'edu-' + i" :style="i ? 'margin-top: 10px;' : ''">
                <div class="cand-edu-top"><span class="cand-edu-school">{{ item.title || '—' }}</span><span class="cand-edu-tag">{{ item.period }}</span></div>
                <div class="cand-edu-major">{{ item.org || '—' }}</div>
                <div class="cand-edu-years" v-if="item.description">{{ item.description }}</div>
              </div>
            </template>
            <div v-else-if="candDetail.school" class="cand-edu">
              <div class="cand-edu-top"><span class="cand-edu-school">{{ candDetail.school }}</span><span class="cand-edu-tag">{{ candDetail.schoolTag || '—' }}</span></div>
              <div class="cand-edu-major">{{ candDetail.major || '—' }}</div>
              <div class="cand-edu-years">{{ candDetail.schoolYears || '—' }}</div>
            </div>
            <div v-else class="cand-edu-years" style="padding: 8px 0;">暂无教育经历</div>
          </div>
          <div class="cand-block">
            <div class="cand-block-title"><i class="fas fa-briefcase"></i>工作经历</div>
            <div class="cand-edu" v-for="(item, i) in candDetail.workExperiences" :key="item.id || 'work-' + i" :style="i ? 'margin-top: 10px;' : ''">
              <div class="cand-edu-top"><span class="cand-edu-school">{{ item.title || '—' }}</span><span class="cand-edu-tag">{{ item.period }}</span></div>
              <div class="cand-edu-major">{{ [item.org, item.description].filter(Boolean).join(' · ') || '—' }}</div>
            </div>
            <div v-if="!candDetail.workExperiences.length" class="cand-edu-years" style="padding: 8px 0;">暂无工作经历</div>
          </div>
          <div class="cand-block">
            <div class="cand-block-title"><i class="fas fa-diagram-project"></i>项目经验</div>
            <div class="cand-edu" v-for="(item, i) in candDetail.projectExperiences" :key="item.id || 'project-' + i" :style="i ? 'margin-top: 10px;' : ''">
              <div class="cand-edu-top"><span class="cand-edu-school">{{ item.title || '—' }}</span><span class="cand-edu-tag">{{ item.period }}</span></div>
              <div class="cand-edu-major">{{ item.description || item.org || '暂无描述' }}</div>
            </div>
            <div v-if="!candDetail.projectExperiences.length" class="cand-edu-years" style="padding: 8px 0;">暂无项目经验</div>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 新增候选人弹窗（从简历库选择） ===== -->
    <div class="resume-doc-overlay" v-show="addCandVisible" @click.self="addCandVisible = false">
      <div class="resume-doc-panel add-cand-panel" @click.stop>
        <div class="resume-doc-header">
          <div class="title"><i class="fas fa-user-plus"></i>从简历库选择候选人</div>
          <button class="resume-doc-close" @click="addCandVisible = false"><i class="fas fa-xmark"></i></button>
        </div>
        <div class="resume-doc-body add-cand-body">
          <div class="form-group">
            <label class="form-label">应聘岗位</label>
            <el-select v-model="addCandPositionId" placeholder="请选择应聘岗位" filterable clearable style="width: 100%;">
              <el-option v-for="p in positionOptions" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </div>
          <el-input v-model="addCandSearch" placeholder="搜索简历姓名/岗位" clearable style="width: 240px; margin-bottom: 12px;" prefix-icon="Search" @keyup.enter="searchAddCandResumes" />
          <button class="btn btn-outline btn-sm" @click="searchAddCandResumes"><i class="fas fa-search"></i> 搜索简历</button>
          <div class="add-cand-list" v-loading="addCandLoading">
            <label v-for="r in addCandResumes" :key="r.id" class="add-cand-item">
              <input type="checkbox" v-model="selectedResumeIds" :value="r.id" style="width: 16px; height: 16px;" />
              <div class="add-cand-avatar">{{ (r.name || '?').charAt(0) }}</div>
              <div style="flex: 1;">
                <div class="add-cand-name">{{ r.name }} <span class="add-cand-job">{{ r.job }}</span></div>
                <div class="add-cand-sub">{{ r.exp }} · {{ r.edu }} · 上传于 {{ r.uploadTime }}</div>
              </div>
            </label>
            <div v-if="!addCandResumes.length" class="add-cand-empty">暂无匹配简历</div>
          </div>
          <div style="margin-top: 16px; display: flex; gap: 10px; justify-content: flex-end;">
            <button class="btn btn-outline" @click="addCandVisible = false">取消</button>
            <button class="btn btn-primary" :disabled="addingCand" @click="confirmAddCandidate">确认添加</button>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 候选人加入项目弹窗（原型 joinProjectOverlay：单个「关联项目」下拉） ===== -->
    <div class="resume-doc-overlay" v-show="joinProjectVisible" @click.self="joinProjectVisible = false">
      <div class="resume-doc-panel join-project-panel" @click.stop>
        <div class="resume-doc-header">
          <div class="title"><i class="fas fa-diagram-project"></i>加入项目</div>
          <button class="resume-doc-close" @click="joinProjectVisible = false"><i class="fas fa-xmark"></i></button>
        </div>
        <div class="resume-doc-body join-project-body">
          <div class="join-hint">为候选人「<span class="join-target">{{ joinProjectRow ? joinProjectRow.name : '' }}</span>」选择需要加入的项目</div>
          <div class="form-group">
            <label class="form-label">关联项目</label>
            <el-select v-model="joinProjectId" placeholder="请选择关联项目" filterable clearable style="width: 100%;" :loading="projectLoading">
              <el-option v-for="p in projectOptions" :key="p.id" :label="p.label" :value="p.id" />
            </el-select>
          </div>
          <div style="margin-top: 20px; display: flex; gap: 10px; justify-content: flex-end;">
            <button class="btn btn-outline" @click="joinProjectVisible = false">取消</button>
            <button class="btn btn-primary" :disabled="joiningProject" @click="confirmJoinProject">确认加入</button>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 简历库加入候选人：意向岗位未匹配到岗位时的岗位回退选择 ===== -->
    <div class="resume-doc-overlay" v-show="joinPosVisible" @click.self="joinPosVisible = false">
      <div class="resume-doc-panel join-project-panel" @click.stop>
        <div class="resume-doc-header">
          <div class="title"><i class="fas fa-user-plus"></i>选择应聘岗位</div>
          <button class="resume-doc-close" @click="joinPosVisible = false"><i class="fas fa-xmark"></i></button>
        </div>
        <div class="resume-doc-body join-project-body">
          <div class="join-hint">
            简历「<span class="join-target">{{ joinResumeRow ? joinResumeRow.name : '' }}</span>」的意向岗位「{{ joinResumeRow ? joinResumeRow.job || '—' : '—' }}」未匹配到岗位，请手动选择应聘岗位
          </div>
          <div class="form-group">
            <label class="form-label">应聘岗位</label>
            <el-select v-model="joinPositionId" placeholder="请选择应聘岗位" filterable clearable style="width: 100%;">
              <el-option v-for="p in positionOptions" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </div>
          <div style="margin-top: 20px; display: flex; gap: 10px; justify-content: flex-end;">
            <button class="btn btn-outline" @click="joinPosVisible = false">取消</button>
            <button class="btn btn-primary" :disabled="joiningResume" @click="confirmJoinCandidate">确认加入</button>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 新建直播橱窗弹窗 ===== -->
    <div class="resume-doc-overlay" v-show="liveCreateVisible" @click.self="liveCreateVisible = false">
      <div class="resume-doc-panel live-create-panel" @click.stop>
        <div class="resume-doc-header">
          <div class="title"><i class="fas fa-plus"></i>新建直播橱窗</div>
          <button class="resume-doc-close" @click="liveCreateVisible = false"><i class="fas fa-xmark"></i></button>
        </div>
        <div class="resume-doc-body live-create-body">
          <div class="form-group">
            <label class="form-label">直播橱窗名称</label>
            <el-input v-model="liveForm.name" placeholder="请输入直播橱窗名称" class="form-input-el" />
          </div>
          <div class="form-group">
            <label class="form-label">直播人员</label>
            <el-input v-model="liveForm.host" placeholder="请输入直播人员姓名" class="form-input-el" />
          </div>
          <div class="form-group">
            <label class="form-label">直播账号</label>
            <el-input v-model="liveForm.account" placeholder="请输入直播账号" class="form-input-el" />
          </div>
          <div style="display: flex; gap: 10px; margin-top: 24px;">
            <button class="btn btn-outline" style="flex: 1;" @click="liveCreateVisible = false">取消</button>
            <button class="btn btn-primary" style="flex: 1;" :disabled="liveCreating" @click="saveLiveCreate">确定</button>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 简历导入弹窗（后端 import 需 multipart file + 必填 bossId） ===== -->
    <div class="resume-doc-overlay" v-show="resumeImportVisible" @click.self="resumeImportVisible = false">
      <div class="resume-doc-panel live-create-panel" @click.stop>
        <div class="resume-doc-header">
          <div class="title"><i class="fas fa-upload"></i>导入简历</div>
          <button class="resume-doc-close" @click="resumeImportVisible = false"><i class="fas fa-xmark"></i></button>
        </div>
        <div class="resume-doc-body live-create-body">
          <div class="form-group">
            <label class="form-label">归属老板 / 企业</label>
            <el-select v-model="importBossId" placeholder="请选择归属老板" filterable clearable style="width: 100%;" :loading="bossLoading">
              <el-option v-for="b in bossOptions" :key="b.id" :label="b.companyCode ? b.name + '（' + b.companyCode + '）' : b.name" :value="b.id" />
            </el-select>
          </div>
          <div class="form-group">
            <label class="form-label">简历文件</label>
            <button class="btn btn-outline btn-sm" @click="triggerResumeFilePick"><i class="fas fa-file-arrow-up"></i> 选择文件（可多选）</button>
            <div class="resume-import-files" v-if="resumeImportFiles.length">
              <div v-for="(f, i) in resumeImportFiles" :key="i"><i class="fas fa-file-pdf" style="margin-right: 6px;"></i>{{ f.name }}</div>
            </div>
            <div v-else style="font-size: 12px; color: var(--text-muted); margin-top: 8px;">支持 .pdf / .doc / .docx</div>
          </div>
          <div style="display: flex; gap: 10px; margin-top: 24px;">
            <button class="btn btn-outline" style="flex: 1;" @click="resumeImportVisible = false">取消</button>
            <button class="btn btn-primary" style="flex: 1;" :disabled="importingResume" @click="submitResumeImport">
              {{ importingResume ? '导入中…' : '确定导入' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 岗位发布/编辑弹窗（字段对齐 BossPosition，沿用 resume-doc-overlay 弹层风格） ===== -->
    <div class="resume-doc-overlay" v-show="posFormVisible" @click.self="posFormVisible = false">
      <div class="resume-doc-panel pos-form-panel" @click.stop>
        <div class="resume-doc-header">
          <div class="title">
            <i class="fas" :class="posFormMode === 'edit' ? 'fa-pen' : 'fa-plus'"></i>
            {{ posFormMode === 'edit' ? '编辑岗位' : '发布岗位' }}
          </div>
          <button class="resume-doc-close" @click="posFormVisible = false"><i class="fas fa-xmark"></i></button>
        </div>
        <div class="resume-doc-body live-create-body">
          <div class="form-group" v-if="posFormMode === 'create'">
            <label class="form-label">归属老板 <span class="form-required">*</span></label>
            <el-select v-model="posForm.bossId" placeholder="请选择归属老板" filterable clearable style="width: 100%;" :loading="bossLoading">
              <el-option v-for="b in bossOptions" :key="b.id" :label="b.companyCode ? b.name + '（' + b.companyCode + '）' : b.name" :value="b.id" />
            </el-select>
          </div>
          <div class="form-group">
            <label class="form-label">岗位名称 <span class="form-required">*</span></label>
            <el-input v-model="posForm.name" placeholder="请输入岗位名称" class="form-input-el" />
          </div>
          <div class="form-group">
            <label class="form-label">岗位编码<span class="form-label-hint" v-if="posFormMode === 'edit'">（必填，编辑不可清空）</span><span class="form-label-hint" v-else>（选填）</span></label>
            <el-input v-model="posForm.code" :placeholder="posFormMode === 'edit' ? '请输入岗位编码' : '留空自动生成 POS+时间戳'" class="form-input-el" />
          </div>
          <div class="form-row">
            <div class="form-group">
              <label class="form-label">岗位类别</label>
              <el-select v-model="posForm.category" placeholder="请选择岗位类别" clearable style="width: 100%;">
                <el-option v-for="t in POSITION_CATEGORIES" :key="t" :label="t" :value="t" />
              </el-select>
            </div>
            <div class="form-group">
              <label class="form-label">所属部门</label>
              <el-input v-model="posForm.department" placeholder="请输入所属部门" class="form-input-el" />
            </div>
          </div>
          <div class="form-group">
            <label class="form-label">工作地点</label>
            <el-input v-model="posForm.location" placeholder="请输入工作地点" class="form-input-el" />
          </div>
          <div class="form-row">
            <div class="form-group">
              <label class="form-label">招聘人数</label>
              <el-input-number v-model="posForm.hireCount" :min="0" :max="99999" controls-position="right" style="width: 100%;" />
            </div>
            <div class="form-group">
              <label class="form-label">薪资范围</label>
              <el-input v-model="posForm.salaryRange" placeholder="如 5000-8000元/月" class="form-input-el" />
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label class="form-label">经验要求</label>
              <el-select v-model="posForm.experience" placeholder="请选择经验要求" clearable style="width: 100%;">
                <el-option v-for="x in ['不限', '1年以下', '1-3年', '3-5年', '5年以上']" :key="x" :label="x" :value="x" />
              </el-select>
            </div>
            <div class="form-group">
              <label class="form-label">学历要求</label>
              <el-select v-model="posForm.education" placeholder="请选择学历要求" clearable style="width: 100%;">
                <el-option v-for="x in ['不限', '初中', '高中', '中专', '大专', '本科', '硕士及以上']" :key="x" :label="x" :value="x" />
              </el-select>
            </div>
          </div>
          <div class="form-group" v-if="posFormMode === 'edit'">
            <label class="form-label">招聘状态</label>
            <el-select v-model="posForm.status" style="width: 100%;">
              <el-option label="招聘中（on）" value="on" />
              <el-option label="已停用（off）" value="off" />
            </el-select>
          </div>
          <div class="form-group">
            <label class="form-label">岗位描述</label>
            <el-input v-model="posForm.description" type="textarea" :rows="4" placeholder="请输入岗位描述" />
          </div>
          <div style="display: flex; gap: 10px; margin-top: 24px;">
            <button class="btn btn-outline" style="flex: 1;" @click="posFormVisible = false">取消</button>
            <button class="btn btn-primary" style="flex: 1;" :disabled="posFormSaving" @click="savePosition">
              {{ posFormSaving ? '保存中…' : (posFormMode === 'edit' ? '保存' : '发布') }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 岗位删除确认弹窗 ===== -->
    <div class="resume-doc-overlay" v-show="delPosVisible" @click.self="delPosVisible = false">
      <div class="resume-doc-panel del-pos-panel" @click.stop>
        <div class="del-pos-top">
          <div class="del-pos-icon"><i class="fas fa-trash"></i></div>
          <div class="del-pos-title">确认删除岗位？</div>
          <div class="del-pos-desc">删除后「<span class="del-pos-name">{{ pendingDeletePos ? pendingDeletePos.name : '' }}</span>」岗位及其招聘数据将无法恢复，确认要删除吗？</div>
        </div>
        <div class="del-pos-foot">
          <button class="btn btn-outline" style="flex: 1;" @click="delPosVisible = false">取消</button>
          <button class="btn btn-primary del-confirm-btn" style="flex: 1;" :disabled="deletingPos" @click="doDeletePos">
            {{ deletingPos ? '删除中…' : '确认删除' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  unwrapPage,
  listPositions, positionStats, getPosition, mapPosition, mapPositionDetail,
  createPosition, updatePosition, deletePosition,
  listResumes, resumeStats, getResume, deleteResume, importResume, mapResume, mapResumeDetail,
  listCandidates, getCandidate, updateCandidateStatus, createCandidate, addCandidateFromResume, deleteCandidate,
  mapCandidate, mapCandidateDetail,
  listShowcases, createShowcase, updateShowcaseStatus, deleteShowcase, mapShowcase,
  listBossOptions, listEnterpriseOptions,
} from '@/api/recruit'
import { listProjects, addProjectMember } from '@/api/project'

/* ============================================================
 * 智能招聘：四个 tab 全部走后台接口
 *   岗位 /admin/positions · 简历 /admin/resumes · 候选人 /admin/candidates
 *   直播橱窗 /admin/live-showcases
 *   简历库「加入候选人」POST /admin/candidates/from-resume
 *   候选人「加入项目」POST /admin/projects/{id}/members（project api）
 * 分页为服务端分页（后端 page 从 0 开始，页面页码从 1 开始）。
 * ============================================================ */
const RESUME_PAGE_SIZE = 5
const POSITION_PAGE_SIZE = 5
const CANDIDATE_PAGE_SIZE = 5
const LIVE_PAGE_SIZE = 5

// 岗位类型 -> 原型样式类
const JOB_TYPE_CLASS = {
  电子厂: 'type-electronics', 仓储: 'type-warehouse', 物流: 'type-logistics',
  餐饮: 'type-catering', 制造业: 'type-manufacture', 汽车: 'type-auto', 服务业: 'type-service',
}

/* ---------- 公共 ---------- */
const tab = ref('resume')
const showToast = (msg) => ElMessage({ message: msg, customClass: 'sr-toast' })
const numOrDash = (v) => (v === null || v === undefined || v === '' ? '—' : v)
const escapeHtml = (v) => String(v ?? '—').replace(/[&<>"']/g, (c) => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
}[c]))

const AVATAR_BGS = [
  'linear-gradient(135deg,#FF7743,#FFB84D)', 'linear-gradient(135deg,#4A90E2,#357ABD)',
  'linear-gradient(135deg,#10B981,#059669)', 'linear-gradient(135deg,#8B5CF6,#6D28D9)',
  'linear-gradient(135deg,#EC4899,#BE185D)', 'linear-gradient(135deg,#F5A623,#E8910C)',
]
const avatarBg = (name) => {
  let hash = 0
  for (const ch of String(name || '')) hash = (hash * 31 + ch.charCodeAt(0)) % 9973
  return AVATAR_BGS[hash % AVATAR_BGS.length]
}

/* ---------- 统计卡 ---------- */
const stats = reactive({
  positionTotal: '—', positionActive: '—',
  resumeTotal: '—', resumeImport: '—', applicationTotal: '—',
})
const loadStats = async () => {
  const [pos, res] = await Promise.allSettled([positionStats(), resumeStats()])
  if (pos.status === 'fulfilled') {
    const d = pos.value?.data || {}
    stats.positionTotal = numOrDash(d.totalCount)
    stats.positionActive = numOrDash(d.activeCount)
    stats.applicationTotal = numOrDash(d.applicationCount)
  }
  if (res.status === 'fulfilled') {
    const d = res.value?.data || {}
    stats.resumeTotal = numOrDash(d.total)
    stats.resumeImport = numOrDash(d.importCount)
  }
}

/* ---------- 岗位管理 ---------- */
const positions = ref([])
const positionTotal = ref(0)
const positionLoading = ref(false)
const positionPage = ref(1)
const positionOptions = ref([])
const selectedPositions = ref([])
const posInput = reactive({ keyword: '', type: 'all' })
const posApplied = reactive({ keyword: '', type: 'all' })

// 后端仅支持 keyword，岗位类型在当前页内二次过滤（招聘状态按原型已取消筛选）
const positionPageData = computed(() => positions.value.filter((p) => {
  return posApplied.type === 'all' || p.typeClass === 'type-' + posApplied.type
}))

const mapPositionRow = (row) => ({ ...mapPosition(row), typeClass: JOB_TYPE_CLASS[row.type] || '' })

const loadPositions = async () => {
  positionLoading.value = true
  try {
    const res = await listPositions({
      keyword: posApplied.keyword.trim() || undefined,
      page: positionPage.value - 1,
      size: POSITION_PAGE_SIZE,
    })
    const { list, total } = unwrapPage(res)
    positions.value = list.map(mapPositionRow)
    positionTotal.value = total
  } catch (err) {
    console.warn('[SmartRecruit] 岗位列表加载失败:', err?.message)
    positions.value = []
    positionTotal.value = 0
  } finally {
    positionLoading.value = false
  }
}

const loadPositionOptions = async () => {
  try {
    const res = await listPositions({ page: 0, size: 100 })
    positionOptions.value = unwrapPage(res).list.map(mapPositionRow)
  } catch (err) {
    console.warn('[SmartRecruit] 岗位下拉加载失败:', err?.message)
    positionOptions.value = []
  }
}

const searchPositions = () => {
  posApplied.keyword = posInput.keyword
  posApplied.type = posInput.type
  positionPage.value = 1
  loadPositions()
}
const resetPositionFilters = () => {
  posInput.keyword = ''; posInput.type = 'all'
  searchPositions()
}
const onPosSelect = (rows) => { selectedPositions.value = rows }

const showPositionDetail = async (row) => {
  try {
    const res = await getPosition(row.id)
    const d = mapPositionDetail(res.data || {})
    const lines = [
      `岗位编码：${escapeHtml(d.code)}`,
      `岗位类型：${escapeHtml(d.type || '—')}`,
      `招聘人数：${escapeHtml(d.headcount)}　已投递：${escapeHtml(d.applied)}　面试：${escapeHtml(d.interviewed)}　录用：${escapeHtml(d.hired)}`,
      `招聘状态：${escapeHtml(d.statusName)}`,
      `所属老板：${escapeHtml(d.bossName)}`,
      `发布时间：${escapeHtml(d.publishTime)}`,
      `投递总数：${escapeHtml(d.applicationCount)}（详情返回最近 ${d.recentApplications.length} 条投递）`,
      `岗位描述：${escapeHtml(d.description || '—')}`,
    ].join('<br>')
    await ElMessageBox.alert(lines, `岗位详情：${d.name}`, {
      dangerouslyUseHTMLString: true, confirmButtonText: '关闭', customClass: 'sr-msgbox',
    })
  } catch (err) {
    if (err !== 'close' && err?.message) console.warn('[SmartRecruit] 岗位详情加载失败:', err.message)
  }
}

/* 岗位发布/编辑弹窗（POST/PUT /admin/positions，字段对齐 BossPosition；
 * 发布归属老板复用简历导入同一份 bossOptions，作为 query bossId 传入） */
const POSITION_CATEGORIES = ['电子厂', '仓储', '物流', '餐饮', '制造业', '汽车', '服务业']
const posFormVisible = ref(false)
const posFormMode = ref('create')
const posFormSaving = ref(false)
const editingPosId = ref(null)
const posForm = reactive({
  bossId: null, name: '', code: '', category: '', department: '', location: '',
  hireCount: 0, salaryRange: '', experience: '', education: '', description: '', status: 'on',
})
const resetPosForm = () => {
  posForm.bossId = null; posForm.name = ''; posForm.code = ''; posForm.category = ''
  posForm.department = ''; posForm.location = ''; posForm.hireCount = 0
  posForm.salaryRange = ''; posForm.experience = ''; posForm.education = ''
  posForm.description = ''; posForm.status = 'on'
  editingPosId.value = null
}

const publishPosition = async () => {
  resetPosForm()
  posFormMode.value = 'create'
  posFormVisible.value = true
  if (!bossOptions.value.length) await loadBossOptions()
}

const editPosition = async (row) => {
  resetPosForm()
  posFormMode.value = 'edit'
  editingPosId.value = row.id
  posForm.bossId = row.bossId ?? null
  posForm.name = row.name === '—' ? '' : row.name
  posForm.code = row.code == null ? '' : String(row.code)
  posForm.category = row.type || ''
  posForm.department = row.department || ''
  posForm.location = row.location || ''
  posForm.hireCount = Number(row.headcount) || 0
  posForm.salaryRange = row.salaryRange || ''
  posForm.status = row.rawStatus || 'on'
  posFormVisible.value = true
  if (!bossOptions.value.length) loadBossOptions()
  // 列表行无 经验/学历/描述，取详情补齐表单项；失败保留列表行已填内容
  try {
    const res = await getPosition(row.id)
    const raw = res.data || {}
    const d = mapPositionDetail(raw)
    posForm.name = d.name === '—' ? posForm.name : d.name
    posForm.code = d.code || posForm.code
    posForm.category = d.type || posForm.category
    posForm.department = d.department || posForm.department
    posForm.location = d.location || posForm.location
    posForm.hireCount = Number(d.headcount) || 0
    posForm.salaryRange = d.salaryRange || ''
    posForm.experience = raw.experience || ''
    posForm.education = raw.education || ''
    posForm.description = raw.description || ''
    posForm.status = d.rawStatus || posForm.status
  } catch (err) {
    console.warn('[SmartRecruit] 岗位详情加载失败:', err?.message)
  }
}

const savePosition = async () => {
  const name = posForm.name.trim()
  if (!name) { showToast('请输入岗位名称'); return }
  const code = posForm.code.trim()
  if (posFormMode.value === 'edit' && !code) { showToast('岗位编码不能为空（编辑接口整体覆盖字段）'); return }
  if (posFormMode.value === 'create' && (posForm.bossId === null || posForm.bossId === undefined || posForm.bossId === '')) {
    showToast('请选择归属老板'); return
  }
  const { bossId, ...fields } = posForm
  const form = { ...fields, name, code }
  if (posFormMode.value === 'create') delete form.status
  posFormSaving.value = true
  try {
    if (posFormMode.value === 'create') {
      await createPosition(form, bossId)
      showToast('岗位发布成功')
      positionPage.value = 1
    } else {
      await updatePosition(editingPosId.value, form)
      showToast('岗位已更新')
    }
    posFormVisible.value = false
    await Promise.all([loadPositions(), loadPositionOptions(), loadStats()])
  } catch (err) {
    console.warn('[SmartRecruit] 岗位保存失败:', err?.message)
  } finally {
    posFormSaving.value = false
  }
}

/* 岗位删除（DELETE /admin/positions/{id}，弹窗二次确认后执行） */
const delPosVisible = ref(false)
const pendingDeletePos = ref(null)
const deletingPos = ref(false)
const confirmDeletePos = (row) => { pendingDeletePos.value = row; delPosVisible.value = true }
const doDeletePos = async () => {
  const row = pendingDeletePos.value
  if (!row || deletingPos.value) return
  deletingPos.value = true
  try {
    await deletePosition(row.id)
    delPosVisible.value = false
    pendingDeletePos.value = null
    showToast('岗位已删除')
    if (positions.value.length === 1 && positionPage.value > 1) positionPage.value -= 1
    await Promise.all([loadPositions(), loadPositionOptions(), loadStats()])
  } catch (err) {
    console.warn('[SmartRecruit] 岗位删除失败:', err?.message)
  } finally {
    deletingPos.value = false
  }
}

/* ---------- 简历库 ---------- */
const resumes = ref([])
const resumeTotal = ref(0)
const resumeLoading = ref(false)
const resumePage = ref(1)
const resInput = reactive({ name: '', job: '', date: '' })
const resApplied = reactive({ name: '', job: '', date: '' })

// 后端 keyword 同时命中姓名/岗位/工种/经验/手机号，且无上传时间参数：
// 检索下发 keyword = 姓名（空则取意向岗位），其余条件在当前页内二次过滤
const resumePageData = computed(() => {
  const name = (resApplied.name || '').trim().toLowerCase()
  const job = (resApplied.job || '').trim().toLowerCase()
  const date = resApplied.date || ''
  return resumes.value.filter((r) => {
    if (name && !String(r.name).toLowerCase().includes(name)) return false
    if (job && !String(r.job).toLowerCase().includes(job)) return false
    if (date && !String(r.uploadTime).includes(date)) return false
    return true
  })
})

const loadResumes = async () => {
  resumeLoading.value = true
  try {
    const res = await listResumes({
      keyword: (resApplied.name || '').trim() || (resApplied.job || '').trim() || undefined,
      page: resumePage.value - 1,
      size: RESUME_PAGE_SIZE,
    })
    const { list, total } = unwrapPage(res)
    resumes.value = list.map(mapResume)
    resumeTotal.value = total
  } catch (err) {
    console.warn('[SmartRecruit] 简历列表加载失败:', err?.message)
    resumes.value = []
    resumeTotal.value = 0
  } finally {
    resumeLoading.value = false
  }
}

const searchResumes = () => {
  resApplied.name = resInput.name; resApplied.job = resInput.job; resApplied.date = resInput.date
  resumePage.value = 1
  loadResumes()
}
const resetResumeFilters = () => {
  resInput.name = ''; resInput.job = ''; resInput.date = ''
  searchResumes()
}

/* 简历导入：multipart file + 必填 bossId */
const resumeFileInput = ref(null)
const resumeImportVisible = ref(false)
const resumeImportFiles = ref([])
const importBossId = ref(null)
const bossOptions = ref([])
const bossLoading = ref(false)
const importingResume = ref(false)

const loadBossOptions = async () => {
  bossLoading.value = true
  try {
    bossOptions.value = await listBossOptions()
  } catch (err) {
    console.warn('[SmartRecruit] 雇主列表加载失败:', err?.message)
    bossOptions.value = []
  }
  if (!bossOptions.value.length) {
    // 雇主列表为空/异常时兜底企业列表（企业 id 与老板 user id 不同源，仅作可选项）
    try {
      bossOptions.value = await listEnterpriseOptions()
    } catch (inner) {
      console.warn('[SmartRecruit] 企业列表加载失败:', inner?.message)
      bossOptions.value = []
    }
  }
  bossLoading.value = false
}

const openResumeImport = async () => {
  resumeImportFiles.value = []
  resumeImportVisible.value = true
  if (!bossOptions.value.length) await loadBossOptions()
}
const triggerResumeFilePick = () => resumeFileInput.value && resumeFileInput.value.click()
const handleResumeFilesPicked = (e) => {
  resumeImportFiles.value = Array.from(e.target.files || [])
  e.target.value = ''
}
const submitResumeImport = async () => {
  if (importBossId.value === null || importBossId.value === undefined || importBossId.value === '') {
    showToast('请选择归属老板'); return
  }
  if (!resumeImportFiles.value.length) { showToast('请选择要导入的简历文件'); return }
  importingResume.value = true
  let succeeded = 0
  const failed = []
  for (const file of resumeImportFiles.value) {
    try {
      await importResume(file, importBossId.value)
      succeeded += 1
    } catch (err) {
      console.warn('[SmartRecruit] 简历导入失败:', file.name, err?.message)
      failed.push(file.name)
    }
  }
  importingResume.value = false
  resumeImportVisible.value = false
  resumeImportFiles.value = []
  if (succeeded) {
    showToast(`成功导入 ${succeeded} 份简历${failed.length ? `，失败 ${failed.length} 份` : ''}`)
  } else if (failed.length) {
    ElMessage.error('简历导入失败，请检查文件格式与归属老板')
  }
  resumePage.value = 1
  await Promise.all([loadResumes(), loadStats()])
}

/* 附件简历预览（详情接口：经历 + filePreviewUrl） */
const resumeDocVisible = ref(false)
const resumeDoc = ref(null)
const resumeDocLoading = ref(false)
const resumeTab = ref('basic')
const RESUME_TABS = [
  { key: 'basic', label: '基本信息' }, { key: 'edu', label: '教育经历' },
  { key: 'work', label: '工作经历' }, { key: 'project', label: '项目经验' },
  { key: 'file', label: '附件原件' },
]
const resumeSourceName = (source) => ({ IMPORT: '后台导入', APPLY: '投递' }[source] || source || '—')

const openResumeDoc = async (row) => {
  resumeTab.value = 'basic'
  resumeDocVisible.value = true
  resumeDoc.value = {
    ...mapResume(row), eduExperiences: [], workExperiences: [], projectExperiences: [],
    filePreviewUrl: '', filePreviewIsImage: false,
  }
  resumeDocLoading.value = true
  try {
    const res = await getResume(row.id)
    resumeDoc.value = mapResumeDetail(res.data || {}, row)
  } catch (err) {
    console.warn('[SmartRecruit] 简历详情加载失败:', err?.message)
  } finally {
    resumeDocLoading.value = false
  }
}

const confirmDeleteResume = async (row) => {
  try {
    await ElMessageBox.confirm(`删除后「${row.name}」的简历及其经历草稿将无法恢复，确认删除吗？`, '确认删除简历？', {
      type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消',
    })
  } catch { return }
  try {
    await deleteResume(row.id)
    showToast('简历已删除')
    await Promise.all([loadResumes(), loadStats()])
  } catch (err) {
    console.warn('[SmartRecruit] 简历删除失败:', err?.message)
  }
}

/* 简历库「加入候选人」（原型 joinCandidate）：后端按意向岗位匹配岗位，
 * 匹配不到（400）时回退到岗位下拉手选，改走 POST /admin/candidates */
const joiningResumeId = ref(null)
const joinPosVisible = ref(false)
const joinResumeRow = ref(null)
const joinPositionId = ref(undefined)
const joiningResume = ref(false)

const joinCandidate = async (row) => {
  if (joiningResumeId.value !== null) return
  joiningResumeId.value = row.id
  try {
    const res = await addCandidateFromResume(row.id)
    if (res?.data?.created === false) {
      showToast(`${row.name} 已在候选人列表中`)
    } else {
      showToast(`已加入候选人：${row.name}`)
      candidatePage.value = 1
    }
    await Promise.all([loadCandidates(), loadStats()])
  } catch (err) {
    console.warn('[SmartRecruit] 加入候选人失败:', err?.message)
    // 常见为后端 400「意向岗位匹配不到岗位」，此处回退到岗位下拉，让运营手选岗位完成加入
    joinResumeRow.value = row
    joinPositionId.value = undefined
    joinPosVisible.value = true
    if (!positionOptions.value.length) loadPositionOptions()
  } finally {
    joiningResumeId.value = null
  }
}

const confirmJoinCandidate = async () => {
  const row = joinResumeRow.value
  if (!row) { joinPosVisible.value = false; return }
  if (!joinPositionId.value) { showToast('请选择应聘岗位'); return }
  joiningResume.value = true
  try {
    await createCandidate({ positionId: joinPositionId.value, resumeId: row.id })
    joinPosVisible.value = false
    showToast(`已加入候选人：${row.name}`)
    candidatePage.value = 1
    await Promise.all([loadCandidates(), loadStats()])
  } catch (err) {
    console.warn('[SmartRecruit] 手动选择岗位加入候选人失败:', err?.message)
  } finally {
    joiningResume.value = false
  }
}

/* ---------- 候选人 ---------- */
const candidates = ref([])
const candidateTotal = ref(0)
const candidateLoading = ref(false)
const candidatePage = ref(1)
const candInput = reactive({ name: '', positionId: undefined, date: '' })
const candApplied = reactive({ name: '', positionId: undefined, date: '' })

// 后端 4 态：PENDING 待筛选 / INTERVIEWING 面试中 / HIRED 已录用 / REJECTED 已拒绝（无「待面试」）
const candidateActions = {
  待筛选: [{ label: '拒绝', act: 'reject' }, { label: '邀请面试', act: 'invite' }],
  面试中: [{ label: '录用', act: 'hire' }, { label: '取消面试', act: 'cancelInterview' }],
  已录用: [{ label: '取消录用', act: 'cancelHire' }],
  已拒绝: [{ label: '重新邀请面试', act: 'reinvite' }],
}
const CANDIDATE_ACTION_STATUS = {
  reject: 'REJECTED', invite: 'INTERVIEWING', hire: 'HIRED',
  cancelInterview: 'PENDING', cancelHire: 'INTERVIEWING', reinvite: 'INTERVIEWING',
}
const CANDIDATE_STATUS_CLS = { 待筛选: 'warning', 待面试: 'cyan', 面试中: 'info', 已录用: 'success', 已拒绝: 'default' }
const candStatusCls = (s) => CANDIDATE_STATUS_CLS[s] || 'default'
const candidatePageData = computed(() => candidates.value)

const loadCandidates = async () => {
  candidateLoading.value = true
  try {
    const res = await listCandidates({
      keyword: (candApplied.name || '').trim() || undefined,
      positionId: candApplied.positionId || undefined,
      startDate: candApplied.date || undefined,
      endDate: candApplied.date || undefined,
      page: candidatePage.value - 1,
      size: CANDIDATE_PAGE_SIZE,
    })
    const { list, total } = unwrapPage(res)
    candidates.value = list.map(mapCandidate)
    candidateTotal.value = total
  } catch (err) {
    console.warn('[SmartRecruit] 候选人列表加载失败:', err?.message)
    candidates.value = []
    candidateTotal.value = 0
  } finally {
    candidateLoading.value = false
  }
}

const searchCandidates = () => {
  candApplied.name = candInput.name
  candApplied.positionId = candInput.positionId
  candApplied.date = candInput.date
  candidatePage.value = 1
  loadCandidates()
}
const resetCandidateFilters = () => {
  candInput.name = ''; candInput.positionId = undefined; candInput.date = ''
  searchCandidates()
}

const ACTION_TOAST = {
  reject: '已拒绝', invite: '已邀请面试', hire: '已录用',
  cancelInterview: '已取消面试', cancelHire: '已取消录用', reinvite: '已重新邀请面试',
}
const doCandAction = async (act, row) => {
  const status = CANDIDATE_ACTION_STATUS[act]
  if (!status) return
  try {
    const res = await updateCandidateStatus(row.id, status)
    const updated = mapCandidate(res.data || {})
    const index = candidates.value.findIndex((c) => c.id === row.id)
    if (index >= 0) candidates.value[index] = { ...candidates.value[index], ...updated }
    showToast(`${ACTION_TOAST[act] || '状态已更新'}：${row.name}`)
    loadStats()
  } catch (err) {
    console.warn('[SmartRecruit] 候选人状态流转失败:', err?.message)
  }
}

const confirmDeleteCandidate = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除候选人「${row.name}」的投递记录？岗位应聘人数会同步 -1。`, '确认删除候选人？', {
      type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消',
    })
  } catch { return }
  try {
    await deleteCandidate(row.id)
    showToast('候选人已删除')
    await Promise.all([loadCandidates(), loadStats()])
  } catch (err) {
    console.warn('[SmartRecruit] 候选人删除失败:', err?.message)
  }
}

const candDetailVisible = ref(false)
const candDetail = ref(null)
const openCandDetail = async (row) => {
  candDetail.value = { ...row, eduExperiences: [], workExperiences: [], projectExperiences: [] }
  candDetailVisible.value = true
  try {
    const res = await getCandidate(row.id)
    candDetail.value = mapCandidateDetail(res.data || {})
  } catch (err) {
    console.warn('[SmartRecruit] 候选人详情加载失败:', err?.message)
  }
}

/* 候选人「加入项目」（原型 openJoinProject/confirmJoinProject）：
 * 弹层单选关联项目 → POST /admin/projects/{id}/members，成员岗位取应聘岗位、日薪由后端按岗位补齐 */
const pad = (n) => (n < 10 ? '0' + n : '' + n)
const todayISO = () => {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
const projectOptions = ref([])
const projectLoading = ref(false)
const joinProjectVisible = ref(false)
const joinProjectRow = ref(null)
const joinProjectId = ref(undefined)
const joiningProject = ref(false)

const loadProjectOptions = async () => {
  projectLoading.value = true
  try {
    const res = await listProjects({ page: 0, size: 100 })
    projectOptions.value = (Array.isArray(res?.data) ? res.data : [])
      .filter((p) => p.id != null)
      .map((p) => ({
        id: p.id,
        label: p.status === 'archived' ? `${p.name}（已归档）` : p.name,
      }))
  } catch (err) {
    console.warn('[SmartRecruit] 项目列表加载失败:', err?.message)
    projectOptions.value = []
  } finally {
    projectLoading.value = false
  }
}

const openJoinProject = async (row) => {
  joinProjectRow.value = row
  joinProjectId.value = undefined
  joinProjectVisible.value = true
  if (!projectOptions.value.length) await loadProjectOptions()
}

const confirmJoinProject = async () => {
  const row = joinProjectRow.value
  if (!row) { joinProjectVisible.value = false; return }
  if (!joinProjectId.value) { showToast('请选择项目'); return }
  joiningProject.value = true
  try {
    await addProjectMember(joinProjectId.value, {
      name: row.name,
      phone: row.phone,
      userId: row.userId,
      role: row.job,
      status: 'active',
      employmentType: 'fulltime',
      joinDate: todayISO(),
    })
    const label = projectOptions.value.find((p) => p.id === joinProjectId.value)?.label || joinProjectId.value
    joinProjectVisible.value = false
    showToast(`已将 ${row.name} 加入项目「${label}」`)
  } catch (err) {
    console.warn('[SmartRecruit] 候选人加入项目失败:', err?.message)
  } finally {
    joiningProject.value = false
  }
}

/* 新增候选人：岗位下拉 + 简历库搜索 + POST /admin/candidates */
const addCandVisible = ref(false)
const addCandSearch = ref('')
const addCandLoading = ref(false)
const addCandResumes = ref([])
const selectedResumeIds = ref([])
const addCandPositionId = ref(undefined)
const addingCand = ref(false)

const searchAddCandResumes = async () => {
  addCandLoading.value = true
  try {
    const res = await listResumes({ keyword: addCandSearch.value.trim() || undefined, page: 0, size: 20 })
    addCandResumes.value = unwrapPage(res).list.map(mapResume)
  } catch (err) {
    console.warn('[SmartRecruit] 简历搜索失败:', err?.message)
    addCandResumes.value = []
  } finally {
    addCandLoading.value = false
  }
}

const openAddCandidateModal = async () => {
  addCandSearch.value = ''
  selectedResumeIds.value = []
  if (addCandPositionId.value === undefined) addCandPositionId.value = candApplied.positionId
  addCandVisible.value = true
  if (!positionOptions.value.length) await loadPositionOptions()
  searchAddCandResumes()
}

const confirmAddCandidate = async () => {
  if (!addCandPositionId.value) { showToast('请选择应聘岗位'); return }
  if (!selectedResumeIds.value.length) { showToast('请至少选择一份简历'); return }
  addingCand.value = true
  let succeeded = 0
  for (const resumeId of selectedResumeIds.value) {
    try {
      await createCandidate({ positionId: addCandPositionId.value, resumeId })
      succeeded += 1
    } catch (err) {
      console.warn('[SmartRecruit] 新增候选人失败:', resumeId, err?.message)
    }
  }
  addingCand.value = false
  addCandVisible.value = false
  if (succeeded) {
    showToast(`已添加 ${succeeded} 位候选人`)
    candidatePage.value = 1
    await Promise.all([loadCandidates(), loadStats()])
  } else {
    ElMessage.error('候选人添加失败')
  }
}

/* ---------- 直播橱窗 ---------- */
const lives = ref([])
const liveTotal = ref(0)
const liveLoading = ref(false)
const livePage = ref(1)
const liveInput = reactive({ title: '', host: '', account: '', date: '' })
const liveApplied = reactive({ title: '', host: '', account: '', date: '' })

// 后端 keyword 命中 标题/主播/抖音号/老板名，且无创建时间参数：其余条件当页内二次过滤
const livePageData = computed(() => {
  const title = (liveApplied.title || '').trim().toLowerCase()
  const host = (liveApplied.host || '').trim().toLowerCase()
  const account = (liveApplied.account || '').trim().toLowerCase()
  const date = liveApplied.date || ''
  const sentKeyword = title || host || account
  return lives.value.filter((l) => {
    if (sentKeyword !== title && title && !String(l.title).toLowerCase().includes(title)) return false
    if (sentKeyword !== host && host && !String(l.host).toLowerCase().includes(host)) return false
    if (sentKeyword !== account && account && !String(l.account).toLowerCase().includes(account)) return false
    if (date && !String(l.time).includes(date)) return false
    return true
  })
})

const loadLives = async () => {
  liveLoading.value = true
  try {
    const res = await listShowcases({
      keyword: (liveApplied.title || '').trim() || (liveApplied.host || '').trim()
        || (liveApplied.account || '').trim() || undefined,
      page: livePage.value - 1,
      size: LIVE_PAGE_SIZE,
    })
    const { list, total } = unwrapPage(res)
    lives.value = list.map(mapShowcase)
    liveTotal.value = total
  } catch (err) {
    console.warn('[SmartRecruit] 直播橱窗加载失败:', err?.message)
    lives.value = []
    liveTotal.value = 0
  } finally {
    liveLoading.value = false
  }
}

const searchLives = () => {
  liveApplied.title = liveInput.title; liveApplied.host = liveInput.host
  liveApplied.account = liveInput.account; liveApplied.date = liveInput.date
  livePage.value = 1
  loadLives()
}
const resetLiveFilters = () => {
  liveInput.title = ''; liveInput.host = ''; liveInput.account = ''; liveInput.date = ''
  searchLives()
}

const liveActions = {
  PENDING: [{ label: '开始直播', status: 'LIVE' }],
  LIVE: [{ label: '结束直播', status: 'ENDED' }],
  ENDED: [{ label: '重新开播', status: 'LIVE' }],
}
const liveStatusCls = (status) => ({ PENDING: 'warning', LIVE: 'success', ENDED: 'default' }[status] || 'default')

const doLiveAction = async (row, status) => {
  try {
    const res = await updateShowcaseStatus(row.id, status)
    const index = lives.value.findIndex((l) => l.id === row.id)
    if (index >= 0) lives.value[index] = mapShowcase(res.data || {})
    showToast('直播状态已更新')
  } catch (err) {
    console.warn('[SmartRecruit] 橱窗状态更新失败:', err?.message)
  }
}

const confirmDeleteLive = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除直播橱窗「${row.title}」？`, '确认删除橱窗？', {
      type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消',
    })
  } catch { return }
  try {
    await deleteShowcase(row.id)
    showToast('直播橱窗已删除')
    loadLives()
  } catch (err) {
    console.warn('[SmartRecruit] 橱窗删除失败:', err?.message)
  }
}

const liveCreateVisible = ref(false)
const liveForm = reactive({ name: '', host: '', account: '' })
const liveCreating = ref(false)
const openLiveCreate = () => {
  liveForm.name = ''; liveForm.host = ''; liveForm.account = ''
  liveCreateVisible.value = true
}
const saveLiveCreate = async () => {
  const title = liveForm.name.trim()
  const anchorName = liveForm.host.trim()
  const douyinAccount = liveForm.account.trim()
  if (!title) { showToast('请输入直播橱窗名称'); return }
  if (!anchorName) { showToast('请输入直播人员'); return }
  if (!douyinAccount) { showToast('请输入直播账号'); return }
  liveCreating.value = true
  try {
    await createShowcase({ title, anchorName, douyinAccount })
    liveCreateVisible.value = false
    livePage.value = 1
    showToast('直播橱窗创建成功')
    await Promise.all([loadLives(), loadStats()])
  } catch (err) {
    console.warn('[SmartRecruit] 橱窗创建失败:', err?.message)
  } finally {
    liveCreating.value = false
  }
}

/* ---------- 初始化 ---------- */
onMounted(() => {
  loadStats()
  loadResumes()
  loadPositions()
  loadPositionOptions()
  loadCandidates()
  loadLives()
})
</script>

<style scoped>
/* ===== Tab 切换（对齐原型 admin.css title-switch 统一风格） ===== */
.card-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.title-switch-group { display: flex; align-items: center; gap: 28px; }
.title-switch { font-size: 16px; font-weight: 600; color: var(--text-muted, #9CA3AF); cursor: pointer; padding: 6px 0; background: transparent; border: none; user-select: none; transition: color 0.15s; }
.title-switch:hover { color: var(--text-secondary, #6B7280); }
.title-switch.active { color: var(--text-primary, #1F2937); cursor: default; }
.title-switch.active:hover { color: var(--text-primary, #1F2937); }
.title-switch-divider { width: 1px; height: 14px; background: var(--border, #E5E7EB); }

/* ===== 筛选栏 ===== */
.filter-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.filter-item { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--text-secondary); }
.filter-label { font-size: 13px; color: var(--text-secondary); font-weight: 500; white-space: nowrap; flex-shrink: 0; }

/* ===== 表格（对齐原型 data-table：表头 #F3F4F6/13px、单元格 14px、行悬停 #FAFAFA、无斑马纹） ===== */
:deep(.el-table) {
  --el-table-border-color: #E5E7EB;
  --el-table-header-bg-color: #F3F4F6;
  --el-table-row-hover-bg-color: #FAFAFA;
  font-size: 14px;
  color: var(--text-primary);
}
:deep(.el-table th.el-table__cell) { padding: 12px 0; }
:deep(.el-table td.el-table__cell) { padding: 14px 0; }
:deep(.el-table .cell) { padding: 0 16px; }

/* ===== 分页 ===== */
.pagination { display: flex; align-items: center; justify-content: space-between; margin-top: 16px; }
.pagination-info { font-size: 13px; color: var(--text-secondary); }
.table-empty { padding: 48px 0; color: var(--text-muted); font-size: 13px; }
.table-empty i { font-size: 32px; color: #D1D5DB; display: block; margin-bottom: 10px; }

/* ===== 单元格与操作按钮（对齐原型 count-cell / action-btns） ===== */
.count-cell { font-weight: 500; }
.action-btns { display: flex; gap: 6px; justify-content: flex-end; align-items: center; }
.action-btns .btn { padding: 4px 10px; font-size: 12px; }
.action-btns .btn.danger-text { color: #DC2626; border-color: #FECACA; }

/* ===== 岗位名称单元格 + 类型标签 ===== */
.job-type-tag { display: inline-block; padding: 3px 8px; border-radius: 4px; font-size: 12px; margin-left: 8px; vertical-align: middle; }
.type-electronics { background: #EFF6FF; color: #2563EB; }
.type-manufacture { background: #FEF3C7; color: #D97706; }
.type-logistics { background: #D1FAE5; color: #059669; }
.type-warehouse { background: #DBEAFE; color: #0369A1; }
.type-catering { background: #FCE7F3; color: #BE185D; }
.type-auto { background: #E0E7FF; color: #4338CA; }
.type-service { background: #F3E8FF; color: #7C3AED; }
.job-name-cell { display: flex; align-items: center; gap: 4px; }
.job-name-cell .job-name { font-weight: 500; font-size: 14px; }
.job-name-cell .job-id { font-size: 12px; color: var(--text-muted); margin-top: 2px; }
.job-name-cell .job-info { display: flex; flex-direction: column; }

/* ===== 状态徽标补充（cyan 原型自定义） ===== */
.status-badge.cyan { background: #E6F9FF; color: #0EA5E9; }

/* ===== 操作文字链 ===== */
.op-link { color: #2563EB; font-size: 13px; cursor: pointer; }
.op-link + .op-link { margin-left: 14px; }
.op-link:hover { text-decoration: underline; }
.op-link.danger-link { color: #DC2626; }

/* ===== 简历导入弹窗已选文件列表 ===== */
.resume-import-files { margin-top: 8px; font-size: 12px; color: #4B5563; background: #fff; border: 1px solid var(--border); border-radius: 8px; padding: 8px 12px; max-height: 120px; overflow-y: auto; }
.resume-import-files div { padding: 2px 0; }

/* ===== 通用弹窗遮罩（resume-doc-overlay） ===== */
.resume-doc-overlay {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.5); z-index: 2000;
  display: flex; align-items: center; justify-content: center;
}
.resume-doc-panel {
  width: 720px; max-height: 84vh; background: #fff;
  border-radius: 12px; display: flex; flex-direction: column; overflow: hidden;
}
.resume-doc-header {
  padding: 14px 20px; border-bottom: 1px solid var(--border);
  display: flex; align-items: center; justify-content: space-between; flex-shrink: 0;
}
.resume-doc-header .title { font-size: 15px; font-weight: 600; color: #1F2937; display: flex; align-items: center; gap: 8px; }
.resume-doc-header .title i { color: var(--primary); }
.resume-doc-close { width: 30px; height: 30px; border-radius: 8px; border: none; background: #F3F4F6; color: #6B7280; cursor: pointer; font-size: 14px; }
.resume-doc-close:hover { background: #E5E7EB; }
.resume-doc-body { padding: 28px 36px; overflow-y: auto; background: #F5F6F8; }

/* ===== 附件简历 A4 预览 ===== */
.resume-paper { background: #fff; border: 1px solid var(--border); border-radius: 8px; padding: 32px 36px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
.resume-name { font-size: 22px; font-weight: 700; color: #1F2937; }
.resume-intent { font-size: 13px; color: var(--primary); font-weight: 600; margin-top: 4px; }
.resume-base { display: flex; flex-wrap: wrap; gap: 8px 24px; margin-top: 12px; font-size: 13px; color: #6B7280; }
.resume-sec-title { font-size: 14px; font-weight: 700; color: #1F2937; margin: 22px 0 10px; padding-left: 10px; border-left: 3px solid var(--primary); }
.resume-item { margin-bottom: 12px; }
.resume-item-title { font-size: 13px; font-weight: 600; color: #374151; display: flex; justify-content: space-between; }
.resume-item-title .time { color: #9CA3AF; font-weight: 400; font-size: 12px; }
.resume-item-desc { font-size: 13px; color: #6B7280; line-height: 1.8; margin-top: 4px; }
.resume-tabs { display: flex; gap: 0; border-bottom: 1px solid var(--border); margin-bottom: 18px; }
.resume-tab { padding: 10px 18px; font-size: 13px; color: #6B7280; cursor: pointer; border-bottom: 2px solid transparent; margin-bottom: -1px; font-weight: 500; }
.resume-tab.active { color: var(--primary); border-bottom-color: var(--primary); }

/* ===== 候选人详情弹窗 ===== */
.cand-detail-panel { width: 440px; }
.cand-detail-body { padding: 16px; }
.cand-head { display: flex; align-items: center; gap: 14px; background: #fff; border-radius: 12px; padding: 16px; margin-bottom: 12px; }
.cand-avatar { width: 56px; height: 56px; border-radius: 50%; flex-shrink: 0; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 20px; font-weight: 600; }
.cand-head-name { font-size: 17px; font-weight: 700; color: #1F2937; display: flex; align-items: center; gap: 8px; }
.cand-head-sub { font-size: 13px; color: #9CA3AF; margin-top: 4px; }
.cand-block { background: #fff; border-radius: 12px; padding: 4px 16px 10px; margin-bottom: 12px; }
.cand-block-title { font-size: 14px; font-weight: 600; color: #1F2937; padding: 12px 0 4px; display: flex; align-items: center; gap: 7px; }
.cand-block-title i { color: var(--primary); font-size: 13px; }
.cand-line { display: flex; padding: 11px 0; border-bottom: 1px solid #F3F4F6; font-size: 13.5px; }
.cand-line:last-child { border-bottom: none; }
.cand-line .k { width: 96px; color: #9CA3AF; flex-shrink: 0; }
.cand-line .v { color: #1F2937; font-weight: 500; flex: 1; }
.cand-edu { background: #F9FAFB; border-radius: 10px; padding: 12px 14px; margin-top: 4px; }
.cand-edu-top { display: flex; align-items: center; justify-content: space-between; }
.cand-edu-school { font-size: 14px; font-weight: 600; color: #1F2937; }
.cand-edu-tag { font-size: 12px; color: #6B7280; background: #EEF0F3; border-radius: 10px; padding: 2px 10px; }
.cand-edu-major { font-size: 13px; color: #4B5563; margin-top: 6px; }
.cand-edu-years { font-size: 12px; color: #9CA3AF; margin-top: 4px; }

/* ===== 新增候选人弹窗 ===== */
.add-cand-panel { width: 560px; }
.add-cand-body { padding: 16px; }
.add-cand-list { max-height: 340px; overflow-y: auto; border: 1px solid #eee; border-radius: 8px; background: #fff; }
.add-cand-item { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border-bottom: 1px solid #F0F0F0; cursor: pointer; margin: 0; }
.add-cand-avatar { width: 36px; height: 36px; border-radius: 8px; background: linear-gradient(135deg, #FF7743, #FFB84D); color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 600; flex-shrink: 0; }
.add-cand-name { font-size: 14px; font-weight: 600; color: #333; }
.add-cand-name .add-cand-job { font-weight: 400; color: #999; font-size: 12px; }
.add-cand-sub { font-size: 12px; color: #999; }
.add-cand-empty { text-align: center; padding: 30px; color: #BBB; }

/* ===== 加入项目 / 岗位回退弹窗（原型 joinProjectOverlay：400px 单下拉） ===== */
.join-project-panel { width: 400px; }
.join-project-body { padding: 20px; }
.join-hint { font-size: 13px; color: #6B7280; line-height: 1.6; margin-bottom: 14px; }
.join-target { color: var(--primary, #FF6B35); font-weight: 600; }

/* ===== 新建直播橱窗弹窗 ===== */
.live-create-panel { width: 460px; }
.live-create-body { padding: 24px; }
.form-group { margin-bottom: 18px; }
.form-label { display: block; font-size: 13px; color: #374151; font-weight: 500; margin-bottom: 8px; }
/* 原型 .form-input：padding 10px 12px、radius 8px、font 14px、聚焦橘色描边 */
.form-input-el :deep(.el-input__wrapper) { padding: 6px 12px; border-radius: 8px; }
.form-input-el :deep(.el-input__inner) { font-size: 14px; color: #1F2937; }
.form-input-el :deep(.el-input__inner)::placeholder { color: #9CA3AF; }

/* ===== 岗位删除确认弹窗 ===== */
.del-pos-panel { width: 400px; max-height: none; }
.del-pos-top { padding: 28px 24px 20px; text-align: center; }
.del-pos-icon { width: 48px; height: 48px; border-radius: 50%; background: #FFF1F1; color: #DC2626; display: flex; align-items: center; justify-content: center; font-size: 20px; margin: 0 auto 14px; }
.del-pos-title { font-size: 16px; font-weight: 600; color: #1F2937; margin-bottom: 8px; }
.del-pos-desc { font-size: 13px; color: #6B7280; line-height: 1.6; }
.del-pos-name { color: #DC2626; font-weight: 600; }
.del-pos-foot { display: flex; gap: 10px; padding: 0 24px 24px; }
.del-confirm-btn { background: #DC2626; border-color: #DC2626; }

/* ===== 岗位发布/编辑弹窗 ===== */
.pos-form-panel { width: 560px; }
.form-row { display: flex; gap: 12px; }
.form-row .form-group { flex: 1; min-width: 0; }
.form-required { color: #DC2626; margin-left: 2px; }
.form-label-hint { font-size: 12px; color: var(--text-muted, #9CA3AF); font-weight: 400; margin-left: 4px; }
</style>
