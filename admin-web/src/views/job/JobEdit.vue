<template>
  <div>
    <div class="page-header">
      <h1 class="page-title">{{ isEdit ? '编辑招工' : '新增招工' }}</h1>
      <p class="page-desc">填写招工信息，提交后将进入审核流程</p>
    </div>

    <div class="card">
      <el-form :model="form" label-width="120px" style="max-width: 800px;">
        <el-divider content-position="left">基本信息</el-divider>
        
        <el-form-item label="岗位名称" required>
          <el-input v-model="form.name" placeholder="请输入岗位名称" />
        </el-form-item>
        
        <el-form-item label="行业分类" required>
          <el-select v-model="form.category" placeholder="请选择行业分类" style="width: 100%;">
            <el-option label="电子厂" value="电子厂" />
            <el-option label="物流" value="物流" />
            <el-option label="餐饮" value="餐饮" />
            <el-option label="仓储" value="仓储" />
            <el-option label="制造业" value="制造业" />
            <el-option label="汽车" value="汽车" />
            <el-option label="服务业" value="服务业" />
            <el-option label="农业" value="农业" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="薪资标准" required>
          <div style="display: flex; gap: 10px;">
            <el-input v-model="form.price" type="number" placeholder="金额" style="width: 160px;" />
            <el-select v-model="form.priceUnit" placeholder="单位" style="width: 120px;">
              <el-option label="元/天" value="天" />
              <el-option label="元/小时" value="小时" />
              <el-option label="元/件" value="件" />
            </el-select>
          </div>
        </el-form-item>
        
        <el-form-item label="招工人数" required>
          <el-input v-model="form.count" type="number" placeholder="请输入人数" style="width: 200px;" />
        </el-form-item>

        <el-divider content-position="left">工作地点与时间</el-divider>
        
        <el-form-item label="工作地点" required>
          <el-input v-model="form.location" placeholder="请输入详细工作地点" />
        </el-form-item>
        
        <el-form-item label="工作时间" required>
          <el-date-picker v-model="form.timeRange" type="datetimerange" start-placeholder="开始时间" end-placeholder="结束时间" style="width: 100%;" />
        </el-form-item>

        <el-divider content-position="left">岗位要求</el-divider>
        
        <el-form-item label="性别要求" required>
          <el-radio-group v-model="form.gender">
            <el-radio label="不限">不限</el-radio>
            <el-radio label="男">男</el-radio>
            <el-radio label="女">女</el-radio>
          </el-radio-group>
        </el-form-item>
        
        <el-form-item label="经验要求" required>
          <el-radio-group v-model="form.experience">
            <el-radio label="不限">不限</el-radio>
            <el-radio label="有经验">有经验</el-radio>
            <el-radio label="无经验">无经验</el-radio>
          </el-radio-group>
        </el-form-item>
        
        <el-form-item label="技能要求">
          <el-select v-model="form.skills" multiple placeholder="请选择技能要求" style="width: 100%;">
            <el-option label="装配" value="装配" />
            <el-option label="分拣" value="分拣" />
            <el-option label="搬运" value="搬运" />
            <el-option label="包装" value="包装" />
            <el-option label="质检" value="质检" />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">福利待遇</el-divider>
        
        <el-form-item label="福利待遇">
          <el-checkbox-group v-model="form.benefits">
            <el-checkbox label="包吃住">包吃住</el-checkbox>
            <el-checkbox label="全勤奖">全勤奖</el-checkbox>
            <el-checkbox label="月结">月结</el-checkbox>
            <el-checkbox label="保险">保险</el-checkbox>
            <el-checkbox label="交通补贴">交通补贴</el-checkbox>
            <el-checkbox label="加班补助">加班补助</el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <el-divider content-position="left">岗位描述</el-divider>
        
        <el-form-item label="岗位描述" required>
          <el-input v-model="form.description" type="textarea" :rows="5" placeholder="请详细描述工作内容、要求等" />
        </el-form-item>

        <el-divider content-position="left">联系人信息</el-divider>
        
        <el-form-item label="联系人" required>
          <el-input v-model="form.contact" placeholder="请输入联系人姓名" style="width: 200px;" />
        </el-form-item>
        
        <el-form-item label="联系电话" required>
          <el-input v-model="form.phone" placeholder="请输入联系电话" style="width: 200px;" />
        </el-form-item>

        <el-form-item style="margin-top: 32px;">
          <el-button type="primary" @click="handleSubmit">提交审核</el-button>
          <el-button @click="handleSaveDraft">存草稿</el-button>
          <el-button @click="handleCancel">取消</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getJob } from '@/api/job'
import { getUser } from '@/api/user'

const router = useRouter()
const route = useRoute()

const isEdit = computed(() => !!route.params.id)

const form = reactive({
  name: '',
  category: '',
  price: '',
  priceUnit: '天',
  count: '',
  location: '',
  timeRange: [],
  gender: '不限',
  experience: '不限',
  skills: [],
  benefits: [],
  description: '',
  contact: '',
  phone: ''
})

const firstValue = (...values) => values.find(value => value !== undefined && value !== null && value !== '')

const normalizeList = (value) => {
  if (Array.isArray(value)) return value.filter(Boolean).map(String)
  if (value === undefined || value === null || value === '') return []
  try {
    const parsed = JSON.parse(value)
    if (Array.isArray(parsed)) return parsed.filter(Boolean).map(String)
  } catch (_) {
    // 普通逗号分隔字符串继续按文本处理
  }
  return String(value).split(/[,，、;；\n]/).map(item => item.trim()).filter(Boolean)
}

const normalizeDate = (value) => {
  if (!value) return null
  if (value instanceof Date) return value
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

const normalizeGender = (value) => ({
  male: '男', M: '男', MAN: '男', 男: '男',
  female: '女', F: '女', WOMAN: '女', 女: '女',
  all: '不限', ALL: '不限', none: '不限', unlimited: '不限', 不限: '不限'
}[value] || value || '不限')

const normalizeExperience = (value) => ({
  none: '无经验', NO_REQUIREMENT: '不限', all: '不限', ALL: '不限', unlimited: '不限',
  有经验: '有经验', 无经验: '无经验', 不限: '不限'
}[value] || value || '不限')

const normalizePriceUnit = (job) => {
  const explicit = firstValue(job.priceUnit, job.salaryUnit)
  if (explicit) return ({ day: '天', DAILY: '天', hour: '小时', HOURLY: '小时', piece: '件', PIECE: '件' }[explicit] || explicit)
  if (job.type === 'month' || job.type === 'MONTH') return '天'
  return '天'
}

const loadJob = async () => {
  if (!route.params.id) return
  try {
    const result = await getJob(route.params.id)
    const job = result?.data || {}
    Object.assign(form, {
      name: firstValue(job.postion, job.position, job.orderTitle, ''),
      category: firstValue(job.industryName, job.jobCategoryName, job.category, ''),
      price: firstValue(job.price, job.salary, ''),
      priceUnit: normalizePriceUnit(job),
      count: firstValue(job.orderNum, job.count, ''),
      location: firstValue(job.address, job.location, ''),
      timeRange: [normalizeDate(job.startTime), normalizeDate(job.endTime)].filter(Boolean),
      gender: normalizeGender(job.gender),
      experience: normalizeExperience(job.experience),
      skills: normalizeList(firstValue(job.skills, job.tags)),
      benefits: normalizeList(job.benefits),
      description: firstValue(job.orderContent, job.description, ''),
      contact: firstValue(job.contact, job.contactName, ''),
      phone: firstValue(job.contactPhone, job.phone, '')
    })

    if (job.createBy && (!form.contact || !form.phone)) {
      try {
        const employer = (await getUser(job.createBy))?.data || {}
        form.contact = firstValue(form.contact, employer.contact, employer.realName, employer.nickname, employer.username, '')
        form.phone = firstValue(form.phone, employer.contactPhone, employer.phone, '')
      } catch (_) {
        // 岗位详情已成功时，雇主资料失败不影响其它字段回显
      }
    }
  } catch (error) {
    console.warn('[JobEdit] 加载岗位详情失败:', error)
    ElMessage.error('加载岗位信息失败')
  }
}

const handleSubmit = () => {
  ElMessage.success('已提交审核')
  router.push('/admin/job-audit')
}

const handleSaveDraft = () => ElMessage.success('已保存草稿')
const handleCancel = () => router.back()

onMounted(loadJob)
</script>
