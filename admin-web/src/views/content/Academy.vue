<template>
  <div class="academy-page">
    <div class="page-header">
      <h1 class="page-title">学堂管理</h1>
      <p class="page-desc">管理接单课堂教学内容：模拟接单全流程视频、新人答题测试题库、新手如何接单视频课程</p>
    </div>

    <div class="tabs-bar">
      <button v-for="tab in tabs" :key="tab.key" class="tab-btn" :class="{ active: activeTab === tab.key }" @click="activeTab = tab.key">
        <i :class="['fas', tab.icon]"></i>{{ tab.label }}
      </button>
    </div>

    <template v-if="activeTab === 'simulate'">
      <div class="stat-cards">
        <div v-for="item in simulateStats" :key="item.label" class="stat-card">
          <div class="stat-card-header"><span>{{ item.label }}</span><div class="stat-card-icon" :class="item.color"><i :class="['fas', item.icon]"></i></div></div>
          <div class="stat-card-value">{{ item.value }}</div><div class="stat-card-change"><span class="text-muted">{{ item.note }}</span></div>
        </div>
      </div>
      <div class="academy-card">
        <div class="academy-toolbar">
          <span class="muted">零工在「接单课堂 - 模拟接单-体验全流程」中按顺序学习以下视频</span>
          <button class="btn btn-primary btn-sm" @click="openVideo('add')"><i class="fas fa-upload"></i> 上传视频</button>
        </div>
        <div class="table-wrap">
          <table class="data-table">
            <thead><tr><th class="index-col">序号</th><th>视频</th><th class="time-col">时长</th><th class="upload-col">上传时间</th><th class="learn-col">学习人次</th><th class="status-col">状态</th><th class="ops-col">操作</th></tr></thead>
            <tbody>
              <tr v-for="(row, index) in videos" :key="row.id">
                <td class="muted index-cell">{{ index + 1 }}</td><td>
                  <div class="video-cell">
                    <button v-if="row.url" class="video-thumb" @click="previewVideo(row)"><i class="fas fa-film"></i><span class="play-mask"><i class="fas fa-play"></i></span><span v-if="row.duration" class="dur-tag">{{ formatDuration(row.duration) }}</span></button>
                    <div v-else class="video-thumb video-thumb-empty"><i class="fas fa-film"></i></div>
                    <div class="video-meta"><div class="video-name" :class="{ 'video-name-empty': !row.url }">{{ row.title }}</div><div class="video-sub">{{ row.url ? `${row.ext?.toUpperCase() || 'MP4'} · ${formatSize(row.size)}` : '待上传' }}</div></div>
                  </div>
                </td>
                <td>{{ formatDuration(row.duration) }}</td>
                <td>{{ formatDateTime(row.createdAt) }}</td>
                <td>{{ formatNumber(row.learners) }}</td>
                <td class="status-cell"><button type="button" class="academy-toggle" :class="{ on: row.enabled }" :aria-label="row.enabled ? '下线' : '上线'" @click="toggleVideo(row)"></button></td>
                <td>
                  <div class="action-btns">
                    <button v-if="row.url" class="btn btn-outline btn-sm" @click="previewVideo(row)"><i class="fas fa-play"></i> 预览</button>
                    <button class="btn btn-outline btn-sm" @click="openVideo(row.url ? 'replace' : 'add', row)"><i :class="['fas', row.url ? 'fa-rotate' : 'fa-upload']"></i> {{ row.url ? '替换' : '上传' }}</button>
                    <button class="btn btn-outline btn-sm danger-text" @click="removeVideo(row)"><i class="fas fa-trash"></i> 删除</button>
                  </div>
                </td>
              </tr>
            <tr v-if="!videos.length"><td colspan="7" class="empty-row">暂无视频</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>

    <template v-else-if="activeTab === 'quiz'">
      <div class="stat-cards">
        <div v-for="item in quizStats" :key="item.label" class="stat-card">
          <div class="stat-card-header"><span>{{ item.label }}</span><div class="stat-card-icon" :class="item.color"><i :class="['fas', item.icon]"></i></div></div>
          <div class="stat-card-value">{{ item.value }}</div><div class="stat-card-change"><span class="text-muted">{{ item.note }}</span></div>
        </div>
      </div>
      <div class="academy-card">
        <div class="academy-toolbar">
          <div class="quiz-filters">
            <button v-for="filter in quizFilters" :key="filter.key" class="btn btn-sm" :class="quizFilter === filter.key ? 'btn-primary' : 'btn-outline'" @click="switchQuizFilter(filter.key)">{{ filter.label }}</button>
          </div>
          <div class="toolbar-actions"><button class="btn btn-outline btn-sm" @click="openImport"><i class="fas fa-file-import"></i> 导入题目</button><button class="btn btn-primary btn-sm" @click="openQuiz()"><i class="fas fa-plus"></i> 新建题目</button></div>
        </div>
        <div class="table-wrap"><table class="data-table">
          <thead><tr><th class="index-col">题号</th><th>题目内容</th><th class="type-col">题型</th><th class="score-col">分值</th><th class="answer-col">正确答案</th><th class="ops-col">操作</th></tr></thead>
          <tbody>
            <tr v-for="(quiz, index) in displayedQuizzes" :key="quiz.id">
              <td class="muted">Q{{ index + 1 }}</td>
              <td>
                <div class="q-stem">{{ quiz.stem }}</div>
                <div class="q-opts"><span v-for="(option, optionIndex) in quiz.options" :key="optionIndex" :class="{ correct: quiz.answer.includes(optionIndex) }">{{ letters[optionIndex] }}. {{ option }}</span></div>
              </td>
              <td><span class="type-badge" :class="`type-${quiz.type}`">{{ typeMap[quiz.type] }}</span></td>
              <td>{{ quiz.score }} 分</td>
              <td class="ans-badge">{{ quiz.answer.map(item => letters[item]).join('、') }}</td>
              <td><div class="action-btns"><button class="btn btn-outline btn-sm" @click="openQuiz(quiz)"><i class="fas fa-edit"></i> 编辑</button><button class="btn btn-outline btn-sm danger-text" @click="removeQuiz(quiz)"><i class="fas fa-trash"></i> 删除</button></div></td>
            </tr>
            <tr v-if="!displayedQuizzes.length"><td colspan="6" class="empty-row"><i class="fas fa-file-circle-question"></i>暂无题目</td></tr>
          </tbody>
        </table></div>
      </div>
    </template>

    <template v-else>
      <div class="stat-cards">
        <div v-for="item in lessonStats" :key="item.label" class="stat-card">
          <div class="stat-card-header"><span>{{ item.label }}</span><div class="stat-card-icon" :class="item.color"><i :class="['fas', item.icon]"></i></div></div>
          <div class="stat-card-value">{{ item.value }}</div><div class="stat-card-change"><span class="text-muted">{{ item.note }}</span></div>
        </div>
      </div>
      <div class="academy-card">
        <div class="academy-toolbar"><span class="muted">新手必修课程视频，上传后自动对新人开放学习</span></div>
        <div class="table-wrap"><table class="data-table">
            <thead><tr><th class="index-col">序号</th><th>课程视频</th><th class="time-col">时长</th><th class="upload-col">上传时间</th><th class="learn-col">学习人次</th><th class="status-col">状态</th><th class="ops-col">操作</th></tr></thead>
          <tbody>
            <tr v-for="(lesson, index) in lessons" :key="lesson.key">
              <td class="muted index-cell">{{ index + 1 }}</td><td><div class="video-cell">
                <button v-if="lesson.video" class="video-thumb" @click="previewLesson(lesson)"><i class="fas fa-film"></i><span class="play-mask"><i class="fas fa-play"></i></span><span v-if="lesson.duration" class="dur-tag">{{ formatDuration(lesson.duration) }}</span></button>
                <div v-else class="video-thumb video-thumb-empty"><i class="fas fa-film"></i></div>
                <div class="video-meta"><div class="video-name" :class="{ 'video-name-empty': !lesson.video }" :title="lesson.desc">{{ lesson.title }}</div><div class="video-sub">{{ lesson.video ? `${lesson.ext?.toUpperCase() || 'MP4'} · ${formatSize(lesson.size)}` : '待上传' }}</div></div>
              </div></td>
              <td>{{ lesson.duration ? formatDuration(lesson.duration) : '—' }}</td>
              <td>{{ formatDateTime(lesson.uploadedAt) }}</td>
              <td>{{ formatNumber(lesson.learners) }}</td>
                <td class="status-cell"><button type="button" class="academy-toggle" :class="{ on: lesson.enabled }" :aria-label="lesson.enabled ? '下线' : '上线'" @click="toggleLesson(lesson)"></button></td>
              <td><div class="action-btns">
                <button class="btn btn-outline btn-sm" @click="openVideo('lesson', lesson)"><i :class="['fas', lesson.video ? 'fa-rotate' : 'fa-upload']"></i> {{ lesson.video ? '替换' : '上传' }}</button>
                <button v-if="lesson.video" class="btn btn-outline btn-sm" @click="previewLesson(lesson)"><i class="fas fa-play"></i> 预览</button>
                <button v-if="lesson.video" class="btn btn-outline btn-sm danger-text" @click="removeLesson(lesson)"><i class="fas fa-trash"></i> 删除</button>
              </div></td>
            </tr>
          </tbody>
        </table></div>
      </div>
    </template>

    <el-dialog v-model="videoVisible" :title="videoTitle" width="560px" destroy-on-close class="academy-dialog">
      <el-form label-position="top">
        <el-form-item label="视频标题"><el-input v-model="videoForm.title" placeholder="如：第一步 · 浏览并报名岗位" /></el-form-item>
        <el-form-item label="视频文件">
          <input ref="fileRef" type="file" accept=".mp4,.mov,.webm" hidden @change="pickFile">
          <div v-if="!videoForm.file" class="upload-zone" :class="{ dragover: videoDragover }" @click="fileRef?.click()" @dragover.prevent="videoDragover = true" @dragleave.prevent="videoDragover = false" @drop.prevent="dropVideo"><i class="fas fa-cloud-arrow-up"></i><div>点击选择视频，或将视频拖拽到此处</div><small>支持 MP4 / MOV / WebM，单个文件不超过 200MB</small></div>
          <div v-else class="file-picked"><span class="fp-icon"><i class="fas fa-film"></i></span><span class="fp-info"><strong>{{ videoForm.file.name }}</strong><small>{{ formatSize(videoForm.file.size) }} · {{ videoDuration ? formatDuration(videoDuration) : '读取时长中…' }} · 点击保存后上传</small></span><button type="button" class="icon-btn" @click.stop="clearVideoFile"><i class="fas fa-xmark"></i></button></div>
          <div v-if="uploadProgress > 0" class="progress-bar"><div class="progress-fill" :style="{ width: `${uploadProgress}%` }"></div></div>
        </el-form-item>
        <div class="switch-row"><span>立即上线</span><el-switch v-model="videoForm.enabled" /><small>关闭后保存为草稿，零工端暂不展示</small></div>
      </el-form>
      <template #footer><el-button @click="videoVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveVideo">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="previewVisible" :title="preview.title || '视频预览'" width="680px" class="academy-dialog" @close="stopPreview" @closed="clearPreview">
      <div class="preview-box"><video v-if="preview.url" ref="previewVideoRef" :src="preview.url" controls autoplay playsinline @error="previewError = true" /><div v-if="previewError" class="preview-fallback"><i class="fas fa-file-video"></i>视频加载失败，请检查 OSS 访问权限或视频地址</div></div>
      <div class="preview-meta"><span>{{ preview.ext?.toUpperCase() || 'MP4' }} · {{ formatSize(preview.size) }} · 时长 {{ preview.duration ? formatDuration(preview.duration) : '—' }}</span><span :class="preview.enabled === false ? 'preview-offline' : 'preview-online'"><i :class="['fas', preview.enabled === false ? 'fa-circle-pause' : 'fa-circle-check']"></i> {{ preview.enabled === false ? '当前为下线状态（仅管理员可预览）' : '已上线，零工端可见' }}</span></div>
    </el-dialog>

    <el-dialog v-model="quizVisible" :title="quizForm.id ? '编辑题目' : '新建测试题目'" width="600px" destroy-on-close class="academy-dialog quiz-dialog">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="题目类型"><el-select v-model="quizForm.type" style="width:100%" @change="onQuizTypeChange"><el-option label="单选题" value="single" /><el-option label="多选题" value="multi" /><el-option label="判断题" value="judge" /></el-select></el-form-item>
          <el-form-item label="题目分值"><el-input-number v-model="quizForm.score" :min="1" :max="100" style="width:100%" /></el-form-item>
        </div>
        <el-form-item label="题干"><el-input v-model="quizForm.stem" type="textarea" :rows="2" placeholder="请输入题目内容" /></el-form-item>
        <el-form-item label="选项（点击左侧圆圈/方块设置正确答案）">
          <div class="opt-editor">
            <div v-for="(option, index) in quizForm.options" :key="index" class="opt-row">
              <button type="button" class="opt-mark" :class="{ correct: quizForm.answer.includes(index), square: quizForm.type === 'multi' }" @click="toggleAnswer(index)">{{ quizForm.type === 'judge' ? (index === 0 ? '✓' : '✕') : letters[index] }}</button>
              <el-input v-model="quizForm.options[index]" :placeholder="`选项 ${letters[index]}`" />
              <button v-if="quizForm.type !== 'judge' && quizForm.options.length > 2" type="button" class="icon-btn opt-del" @click="removeOption(index)"><i class="fas fa-trash"></i></button>
            </div>
            <el-button v-if="quizForm.type !== 'judge' && quizForm.options.length < 6" @click="quizForm.options.push('')">添加选项</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="quizVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveQuiz"><i class="fas fa-check"></i> 保存题目</el-button></template>
    </el-dialog>

    <el-dialog v-model="importVisible" title="批量导入题目" width="660px" destroy-on-close class="academy-dialog import-dialog">
      <div class="import-guide"><i class="fas fa-circle-info"></i><div><strong>使用 Excel 模板批量导入</strong><p>支持 .xlsx / .xls / .csv，单次最多 200 题，异常行会跳过，不影响其他题目。</p></div><button class="btn btn-outline btn-sm" @click="downloadTemplate"><i class="fas fa-download"></i> 下载模板</button></div>
      <input ref="importFileRef" type="file" accept=".xlsx,.xls,.csv" hidden @change="pickImportFile">
      <div class="import-dropzone" :class="{ dragover: importDragover }" @click="importFileRef?.click()" @dragover.prevent="importDragover = true" @dragleave.prevent="importDragover = false" @drop.prevent="dropImportFile"><i class="fas fa-file-excel"></i><strong>{{ importFileName || '点击选择文件，或拖拽到此处' }}</strong><span>{{ importFileName ? '已完成解析，可查看下方导入结果' : '文件不超过 5MB' }}</span></div>
      <div v-if="importParsed" class="import-result">
        <div class="import-summary"><div class="ok"><span>可导入题目</span><strong>{{ importValid.length }} 题</strong></div><div class="bad"><span>异常 / 跳过</span><strong>{{ importErrors.length }} 行</strong></div></div>
        <div v-if="importErrors.length" class="import-errors"><strong>异常明细（不影响其他题目导入）</strong><div><p v-for="(error, index) in importErrors.slice(0, 20)" :key="index">• {{ error }}</p></div></div>
        <div v-if="importValid.length" class="import-preview"><strong>导入预览</strong><div><p v-for="(item, index) in importValid.slice(0, 50)" :key="index"><span>{{ index + 1 }}</span><em class="type-badge" :class="`type-${item.type}`">{{ typeMap[item.type] }}</em><b>{{ item.stem }}</b><i>{{ item.answer.map(answer => letters[answer]).join('、') }}</i></p></div></div>
      </div>
      <template #footer><el-button @click="importVisible = false">取消</el-button><el-button type="primary" :loading="importing" :disabled="!importValid.length" @click="confirmImport">确认导入 {{ importValid.length || '' }}</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as XLSX from 'xlsx'
import {
  createQuiz, createSimulateVideo, deleteLessonVideo, deleteQuiz, deleteSimulateVideo,
  listLessons, listQuizzes, listSimulateVideos, saveLessonVideo, toggleLesson as toggleLessonApi,
  toggleSimulateVideo, updateQuiz, updateSimulateVideo, uploadAcademyVideo
} from '@/api/academy'

const tabs = [
  { key: 'simulate', label: '模拟接单-体验全流程', icon: 'fa-gamepad' },
  { key: 'quiz', label: '答题测试', icon: 'fa-file-circle-question' },
  { key: 'howto', label: '如何接单', icon: 'fa-circle-play' }
]
const quizFilters = [{ key: '', label: '全部' }, { key: 'single', label: '单选题' }, { key: 'multi', label: '多选题' }, { key: 'judge', label: '判断题' }]
const typeMap = { single: '单选题', multi: '多选题', judge: '判断题' }
const letters = ['A', 'B', 'C', 'D', 'E', 'F']
const activeTab = ref('simulate')
const videos = ref([])
const quizzes = ref([])
const lessons = ref([])
const quizFilter = ref('')
const fileRef = ref()
const importFileRef = ref()
const videoVisible = ref(false)
const previewVisible = ref(false)
const previewVideoRef = ref(null)
const quizVisible = ref(false)
const videoMode = ref('add')
const currentVideo = ref(null)
const currentLesson = ref(null)
const saving = ref(false)
const uploadProgress = ref(0)
const videoDuration = ref(0)
const videoDragover = ref(false)
const previewError = ref(false)
const importVisible = ref(false)
const importDragover = ref(false)
const importFileName = ref('')
const importParsed = ref(false)
const importValid = ref([])
const importErrors = ref([])
const importing = ref(false)
const preview = reactive({})
const videoForm = reactive({ title: '', file: null, enabled: true })
const quizForm = reactive({ id: null, type: 'single', score: 10, stem: '', options: ['', ''], answer: [0] })
const displayedQuizzes = computed(() => quizFilter.value ? quizzes.value.filter(item => item.type === quizFilter.value) : quizzes.value)

const simulateStats = computed(() => [
  { label: '教学视频', value: videos.value.length, note: `共 ${videos.value.length} 个流程节点`, icon: 'fa-film' },
  { label: '已上线', value: videos.value.filter(item => item.enabled).length, note: '下线后零工端不可见', icon: 'fa-circle-check', color: 'green' },
  { label: '视频总时长', value: formatDuration(videos.value.reduce((sum, item) => sum + Number(item.duration || 0), 0)), note: '全部流程学习时长', icon: 'fa-clock', color: 'blue' },
  { label: '累计学习人次', value: formatNumber(videos.value.reduce((sum, item) => sum + Number(item.learners || 0), 0)), note: '服务端统计', icon: 'fa-users', color: 'yellow' }
])
const quizStats = computed(() => [
  { label: '题目总数', value: quizzes.value.length, note: `当前筛选满分 ${quizzes.value.reduce((sum, item) => sum + item.score, 0)} 分`, icon: 'fa-list-check' },
  { label: '单选题', value: quizzes.value.filter(item => item.type === 'single').length, icon: 'fa-circle-dot', color: 'blue' },
  { label: '多选题', value: quizzes.value.filter(item => item.type === 'multi').length, icon: 'fa-square-check' },
  { label: '判断题', value: quizzes.value.filter(item => item.type === 'judge').length, icon: 'fa-scale-balanced', color: 'green' }
])
const lessonStats = computed(() => [
  { label: '课程总数', value: lessons.value.length, note: '服务端课程槽位', icon: 'fa-book-open' },
  { label: '已上传', value: lessons.value.filter(item => item.video).length, icon: 'fa-circle-check', color: 'green' },
  { label: '待上传', value: lessons.value.filter(item => !item.video).length, icon: 'fa-hourglass-half', color: 'yellow' },
  { label: '累计学习人次', value: formatNumber(lessons.value.reduce((sum, item) => sum + Number(item.learners || 0), 0)), icon: 'fa-users', color: 'blue' }
])
const videoTitle = computed(() => videoMode.value === 'lesson' ? (currentLesson.value?.video ? '替换课程视频' : '上传课程视频') : (videoMode.value === 'replace' ? '替换视频' : '上传视频'))

const rows = result => result?.data || result || []
const formatNumber = value => Number(value || 0).toLocaleString('zh-CN')
const formatDuration = value => {
  const seconds = Math.max(0, Math.round(Number(value || 0)))
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
}
const formatSize = value => value ? `${(Number(value) / 1024 / 1024).toFixed(1)}MB` : '—'
const formatDateTime = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'

async function loadVideos() { videos.value = rows(await listSimulateVideos()) }
async function loadQuizzes() { quizzes.value = rows(await listQuizzes()) }
async function loadLessons() { lessons.value = rows(await listLessons()) }
function switchQuizFilter(type) { quizFilter.value = type }

function openVideo(mode, row = null) {
  videoMode.value = mode
  currentVideo.value = mode === 'lesson' ? null : row
  currentLesson.value = mode === 'lesson' ? row : null
  Object.assign(videoForm, { title: row?.title || '', file: null, enabled: row?.enabled ?? true })
  uploadProgress.value = 0
  videoDuration.value = 0
  videoVisible.value = true
}
function validateVideoFile(file) {
  if (!file) return false
  const ext = file.name.split('.').pop()?.toLowerCase()
  if (!['mp4', 'mov', 'webm'].includes(ext)) { ElMessage.warning('仅支持 MP4、MOV、WebM 视频'); return false }
  if (file.size > 200 * 1024 * 1024) { ElMessage.warning('视频大小不能超过 200MB'); return false }
  return true
}
function setVideoFile(file) {
  if (!validateVideoFile(file)) return
  videoForm.file = file
  videoDuration.value = 0
  const url = URL.createObjectURL(file)
  const probe = document.createElement('video')
  probe.preload = 'metadata'
  probe.src = url
  probe.onloadedmetadata = () => { videoDuration.value = Math.round(probe.duration || 0); URL.revokeObjectURL(url) }
  probe.onerror = () => URL.revokeObjectURL(url)
}
function pickFile(event) { setVideoFile(event.target.files?.[0]) }
function dropVideo(event) { videoDragover.value = false; setVideoFile(event.dataTransfer.files?.[0]) }
function clearVideoFile() { videoForm.file = null; videoDuration.value = 0; uploadProgress.value = 0; if (fileRef.value) fileRef.value.value = '' }

async function saveVideo() {
  if (!videoForm.title) return ElMessage.warning('请输入视频标题')
  if (!videoForm.file) return ElMessage.warning('请选择视频文件')
  saving.value = true
  try {
    if (videoForm.file.size > 200 * 1024 * 1024) { ElMessage.error('视频大小不能超过200MB'); uploadProgress.value = 0; return }
    const upload = rows(await uploadAcademyVideo(videoForm.file, videoForm.title, videoMode.value === 'lesson' ? 'lesson' : 'simulate', {
      onUploadProgress: event => { if (event.total) uploadProgress.value = Math.min(99, Math.round(event.loaded * 100 / event.total)) }
    }))
    uploadProgress.value = 100
    if (videoMode.value === 'lesson') {
      await saveLessonVideo(currentLesson.value.key, { title: videoForm.title, url: upload.url, duration: upload.duration, size: upload.size, ext: upload.ext })
      if (Boolean(currentLesson.value.enabled) !== Boolean(videoForm.enabled)) await toggleLessonApi(currentLesson.value.key, videoForm.enabled)
      await loadLessons()
    } else if (videoMode.value === 'replace') {
      await updateSimulateVideo(currentVideo.value.id, { title: videoForm.title, url: upload.url, duration: upload.duration, size: upload.size, ext: upload.ext, enabled: videoForm.enabled, sort: currentVideo.value.sort, learners: currentVideo.value.learners })
      await loadVideos()
    } else {
      await createSimulateVideo({ title: videoForm.title, url: upload.url, duration: upload.duration, size: upload.size, ext: upload.ext, sort: videos.value.length + 1, enabled: videoForm.enabled, learners: 0 })
      await loadVideos()
    }
    videoVisible.value = false
    ElMessage.success('视频已保存')
  } finally { saving.value = false }
}

function clearPreviewData() { Object.keys(preview).forEach(key => delete preview[key]) }
function stopPreview() {
  const player = previewVideoRef.value
  if (!player) return
  player.pause()
  player.currentTime = 0
}
function clearPreview() {
  stopPreview()
  clearPreviewData()
  previewError.value = false
}
function previewVideo(row) { clearPreviewData(); previewError.value = false; Object.assign(preview, row); previewVisible.value = true }
function previewLesson(lesson) { clearPreviewData(); previewError.value = false; Object.assign(preview, { ...lesson, url: lesson.video }); previewVisible.value = true }
async function toggleVideo(row) { await toggleSimulateVideo(row.id); await loadVideos() }
async function removeVideo(row) {
  await ElMessageBox.confirm(`确定删除视频「${row.title}」吗？`, '提示', { type: 'warning' })
  await deleteSimulateVideo(row.id); await loadVideos(); ElMessage.success('视频已删除')
}
async function toggleLesson(lesson) {
  if (!lesson.video) return ElMessage.warning('请先上传课程视频')
  await toggleLessonApi(lesson.key)
  await loadLessons()
}
async function removeLesson(lesson) {
  await ElMessageBox.confirm(`确定删除课程「${lesson.title}」的视频吗？`, '提示', { type: 'warning' })
  await deleteLessonVideo(lesson.key); await loadLessons(); ElMessage.success('课程视频已删除')
}

function openQuiz(quiz = null) {
  Object.assign(quizForm, quiz ? JSON.parse(JSON.stringify({ id: quiz.id, type: quiz.type, score: quiz.score, stem: quiz.stem, options: quiz.options, answer: quiz.answer })) : { id: null, type: 'single', score: 10, stem: '', options: ['', ''], answer: [0] })
  quizVisible.value = true
}
function onQuizTypeChange(type) {
  if (type === 'judge') {
    quizForm.options = ['正确', '错误']
    quizForm.answer = [0]
  } else if (quizForm.options.length < 2 || quizForm.options.every(item => item === '正确' || item === '错误')) {
    quizForm.options = ['', '']
    quizForm.answer = [0]
  } else if (type === 'single') {
    quizForm.answer = [quizForm.answer[0] ?? 0]
  }
}
function removeOption(index) {
  quizForm.options.splice(index, 1)
  quizForm.answer = quizForm.answer.filter(item => item !== index).map(item => item > index ? item - 1 : item)
}
function toggleAnswer(index) {
  if (quizForm.type === 'single' || quizForm.type === 'judge') quizForm.answer = [index]
  else quizForm.answer = quizForm.answer.includes(index) ? quizForm.answer.filter(item => item !== index) : [...quizForm.answer, index]
}
async function saveQuiz() {
  if (!quizForm.stem) return ElMessage.warning('请输入题干')
  saving.value = true
  try {
    if (quizForm.id) await updateQuiz(quizForm.id, { ...quizForm })
    else await createQuiz({ ...quizForm })
    quizVisible.value = false; await loadQuizzes(); ElMessage.success('题目已保存')
  } finally { saving.value = false }
}
async function removeQuiz(quiz) {
  await ElMessageBox.confirm('确定删除该题目吗？', '提示', { type: 'warning' })
  await deleteQuiz(quiz.id); await loadQuizzes(); ElMessage.success('题目已删除')
}

function openImport() {
  importVisible.value = true
  importFileName.value = ''
  importParsed.value = false
  importValid.value = []
  importErrors.value = []
  if (importFileRef.value) importFileRef.value.value = ''
}
function downloadTemplate() {
  const rows = [
    ['题型', '题目内容', '选项A', '选项B', '选项C', '选项D', '选项E', '选项F', '正确答案', '分值'],
    ['单选题', '报名接单前，以下哪项信息不需要提前确认？', '工价是否符合期望', '任务内容自己是否有能力做', '老板的兴趣爱好', '任务日期和时间能否准时到达', '', '', 'C', 10],
    ['多选题', '以下哪些行为会被扣信用分？', '接单后迟到早退', '使用本人实名账号接单', '虚假打卡', '引导老板取消订单', '', '', 'A、C、D', 15],
    ['判断题', '信用分低于 60 分将无法接单。', '', '', '', '', '', '', '正确', 10]
  ]
  const sheet = XLSX.utils.aoa_to_sheet(rows)
  sheet['!cols'] = [{ wch: 10 }, { wch: 42 }, { wch: 20 }, { wch: 20 }, { wch: 20 }, { wch: 22 }, { wch: 12 }, { wch: 12 }, { wch: 12 }, { wch: 8 }]
  const book = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(book, sheet, '题目导入模板')
  XLSX.writeFile(book, '学堂答题-题目导入模板.xlsx')
}
function pickImportFile(event) { parseImportFile(event.target.files?.[0]) }
function dropImportFile(event) { importDragover.value = false; parseImportFile(event.dataTransfer.files?.[0]) }
function parseImportFile(file) {
  if (!file) return
  if (!/\.(xlsx|xls|csv)$/i.test(file.name)) return ElMessage.warning('仅支持 .xlsx / .xls / .csv 文件')
  if (file.size > 5 * 1024 * 1024) return ElMessage.warning('文件大小不能超过 5MB')
  importFileName.value = file.name
  const reader = new FileReader()
  reader.onload = event => {
    try {
      const book = XLSX.read(event.target.result, { type: 'array' })
      const sheet = book.Sheets[book.SheetNames[0]]
      parseImportRows(XLSX.utils.sheet_to_json(sheet, { header: 1, defval: '' }))
    } catch { ElMessage.error('文件解析失败，请使用最新模板') }
  }
  reader.readAsArrayBuffer(file)
}
function parseImportRows(sourceRows) {
  const valid = [], errors = [], columns = {}
  let headerRow = -1
  for (let rowIndex = 0; rowIndex < Math.min(sourceRows.length, 5); rowIndex++) {
    const cells = (sourceRows[rowIndex] || []).map(cell => String(cell).trim())
    if (!cells.some(cell => ['题目内容', '题干'].includes(cell)) || !cells.includes('题型')) continue
    headerRow = rowIndex
    cells.forEach((cell, index) => {
      if (cell === '题型') columns.type = index
      if (['题目内容', '题干'].includes(cell)) columns.stem = index
      const option = /^选项\s*([A-F])$/.exec(cell)
      if (option) columns[`opt${option[1]}`] = index
      if (['正确答案', '答案'].includes(cell)) columns.answer = index
      if (['分值', '分数'].includes(cell)) columns.score = index
    })
    break
  }
  if (headerRow < 0) errors.push('未找到「题型」和「题目内容」表头，请下载标准模板填写')
  const aliases = { 单选题: 'single', 单选: 'single', single: 'single', 多选题: 'multi', 多选: 'multi', multi: 'multi', 判断题: 'judge', 判断: 'judge', judge: 'judge' }
  for (let index = headerRow + 1; headerRow >= 0 && index < sourceRows.length; index++) {
    const excelRow = index + 1
    const row = (sourceRows[index] || []).map(cell => String(cell).trim())
    if (!row.join('')) continue
    if (valid.length + errors.length >= 200) { errors.push(`第 ${excelRow} 行：单次最多导入 200 题，其余已忽略`); break }
    const stem = row[columns.stem] || ''
    if (!stem) { errors.push(`第 ${excelRow} 行：题目内容为空`); continue }
    const rawType = (row[columns.type] || '单选题').replace(/\s/g, '')
    const type = aliases[rawType] || aliases[rawType.toLowerCase()]
    if (!type) { errors.push(`第 ${excelRow} 行：题型「${rawType}」无法识别`); continue }
    const score = row[columns.score] === '' || columns.score === undefined ? 10 : Number.parseInt(row[columns.score], 10)
    if (!Number.isInteger(score) || score <= 0 || score > 100) { errors.push(`第 ${excelRow} 行：分值应为 1-100 的整数`); continue }
    let options = [], answer = []
    const rawAnswer = String(row[columns.answer] || '').trim()
    if (type === 'judge') {
      options = ['正确', '错误']
      if (['正确', '对', '√', 'A', '是', 'true', 'T'].includes(rawAnswer)) answer = [0]
      else if (['错误', '错', '×', 'B', '否', 'false', 'F'].includes(rawAnswer)) answer = [1]
      else { errors.push(`第 ${excelRow} 行：判断题答案应填「正确」或「错误」`); continue }
    } else {
      letters.forEach(letter => { const value = row[columns[`opt${letter}`]]; if (value) options.push(value) })
      if (options.length < 2) { errors.push(`第 ${excelRow} 行：至少需要填写 2 个选项`); continue }
      answer = rawAnswer.toUpperCase().split(/[^A-F]+/).filter(Boolean).map(letter => letter.charCodeAt(0) - 65)
      answer = [...new Set(answer)].sort((a, b) => a - b)
      if (!answer.length || answer.some(item => item >= options.length)) { errors.push(`第 ${excelRow} 行：正确答案无效或超出选项范围`); continue }
      if (type === 'single' && answer.length !== 1) { errors.push(`第 ${excelRow} 行：单选题只能有 1 个正确答案`); continue }
      if (type === 'multi' && answer.length < 2) { errors.push(`第 ${excelRow} 行：多选题至少需要 2 个正确答案`); continue }
    }
    valid.push({ type, score, stem, options, answer })
  }
  importValid.value = valid
  importErrors.value = errors
  importParsed.value = true
}
async function confirmImport() {
  importing.value = true
  let success = 0
  try {
    for (let index = 0; index < importValid.value.length; index++) {
      await createQuiz({ ...importValid.value[index], sort: quizzes.value.length + index + 1 })
      success++
    }
    importVisible.value = false
    await loadQuizzes()
    ElMessage.success(`成功导入 ${success} 道题目${importErrors.value.length ? `，${importErrors.value.length} 行异常已跳过` : ''}`)
  } finally { importing.value = false }
}

onMounted(() => Promise.all([loadVideos(), loadQuizzes(), loadLessons()]))
onBeforeUnmount(stopPreview)
</script>

<style scoped>
.index-col{width:60px}.time-col{width:90px}.upload-col{width:150px}.learn-col{width:110px}.status-col{width:90px}.ops-col{width:190px}.type-col{width:90px}.score-col{width:80px}.answer-col{width:150px}.index-cell,.status-cell{white-space:nowrap}.toolbar-actions{display:flex;gap:8px}.academy-toggle{position:relative;width:38px;height:21px;padding:0;border:0;border-radius:11px;background:#D1D5DB;cursor:pointer;transition:background .2s}.academy-toggle::after{content:'';position:absolute;top:2px;left:2px;width:17px;height:17px;border-radius:50%;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,.2);transition:left .2s}.academy-toggle.on{background:var(--primary)}.academy-toggle.on::after{left:19px}
:deep(.academy-dialog .el-dialog__header){margin-right:0;padding:20px 24px;border-bottom:1px solid var(--border)}
:deep(.academy-dialog .el-dialog__title){font-size:16px;font-weight:600;color:var(--text-primary)}
:deep(.academy-dialog .el-dialog__headerbtn){top:17px;right:20px;width:30px;height:30px;border-radius:6px}
:deep(.academy-dialog .el-dialog__headerbtn:hover){background:var(--bg-page)}
:deep(.academy-dialog .el-dialog__body){padding:24px;max-height:65vh;overflow:auto}
:deep(.academy-dialog .el-dialog__footer){padding:16px 24px;border-top:1px solid var(--border)}
:deep(.academy-dialog .el-dialog__footer .el-button){min-width:82px;height:36px;border-radius:8px}
.quiz-dialog .form-grid{align-items:start}.quiz-dialog .el-form-item{margin-bottom:18px}.quiz-dialog .el-form-item__label{font-size:13px;font-weight:500;color:var(--text-primary);line-height:20px;padding-bottom:7px}.quiz-dialog .el-textarea__inner{line-height:1.6}.quiz-dialog .opt-editor{padding:2px 0}
.tabs-bar{display:flex;gap:4px;background:#fff;border:1px solid var(--border);border-radius:12px;padding:0 12px;margin:4px 0 18px;box-shadow:0 1px 2px rgba(0,0,0,.03)}.tab-btn{padding:14px 20px;border:0;border-bottom:2px solid transparent;background:none;color:var(--text-secondary);cursor:pointer;font-size:14px;font-weight:500}.tab-btn.active,.tab-btn:hover{color:var(--primary)}.tab-btn.active{border-bottom-color:var(--primary);font-weight:600}.tab-btn i{margin-right:6px}
.academy-card{padding:16px 18px;background:#fff;border:1px solid var(--border);border-radius:14px}.academy-toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:12px}.action-btns,.quiz-filters{display:flex;gap:6px;white-space:nowrap}.action-btns .btn{padding:4px 9px;font-size:12px}.muted,.video-sub{color:var(--text-muted);font-size:12px}.video-cell{display:flex;align-items:center;gap:12px}.video-meta{min-width:0}.video-thumb{position:relative;display:flex;align-items:center;justify-content:center;width:88px;height:52px;flex-shrink:0;border:0;border-radius:8px;background:linear-gradient(135deg,#4B5563,#1F2937);color:rgba(255,255,255,.9);cursor:pointer;overflow:hidden}.video-thumb .play-mask{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;background:rgba(0,0,0,.28);font-size:16px;color:#fff}.video-thumb-empty{background:#F3F4F6;color:#9CA3AF;cursor:default;border:1px dashed #D1D5DB}.dur-tag{position:absolute;right:4px;bottom:4px;padding:1px 5px;border-radius:4px;background:rgba(0,0,0,.65);font-size:10px;line-height:1.4}.video-name{max-width:300px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:13px;font-weight:600;color:var(--text-primary)}.video-name-empty{color:var(--text-muted);font-weight:400}.video-sub{margin-top:3px}.online{color:#16A34A}.offline{color:var(--text-muted)}.danger-text{color:#DC2626}.q-stem{max-width:380px;line-height:1.6;font-size:13px}.q-opts{display:flex;flex-direction:column;margin-top:4px;color:var(--text-muted);font-size:12px;line-height:1.7}.q-opts .correct{color:#16A34A;font-weight:600}.type-badge{display:inline-block;padding:3px 10px;border-radius:6px;font-size:12px}.type-single{color:#2563EB;background:#EFF6FF}.type-multi{color:#7C3AED;background:#F5F3FF}.type-judge{color:#16A34A;background:#F0FDF4}.ans-badge{color:#16A34A;font-weight:600}.empty-row{padding:44px 0;text-align:center;color:var(--text-muted)}
.upload-zone{padding:30px 16px;border:1.5px dashed #D1D5DB;border-radius:12px;background:#FAFAFA;text-align:center;cursor:pointer}.upload-zone.dragover,.import-dropzone.dragover{border-color:var(--primary);background:#FFF7ED}.upload-zone i{color:var(--primary);font-size:34px}.upload-zone small{display:block;margin-top:5px;color:var(--text-muted)}.file-picked{display:flex;align-items:center;gap:10px;margin-top:10px;padding:12px 14px;border:1px solid var(--border);border-radius:10px}.file-picked .fp-icon{display:flex;align-items:center;justify-content:center;width:40px;height:40px;border-radius:8px;background:#FFF7ED;color:var(--primary);flex-shrink:0}.file-picked .fp-info{display:flex;flex:1;min-width:0;flex-direction:column;gap:4px}.file-picked .fp-info strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:13px}.file-picked .fp-info small{color:var(--text-muted);font-size:12px}.icon-btn{border:0;background:none;color:var(--text-muted);cursor:pointer}.switch-row{display:flex;align-items:center;gap:10px}.switch-row small{color:var(--text-muted);font-size:12px}.progress-bar{height:6px;margin-top:10px;overflow:hidden;border-radius:3px;background:#F3F4F6}.progress-fill{height:100%;border-radius:3px;background:linear-gradient(90deg,#FB923C,#EA580C);transition:width .15s}.preview-box{position:relative;display:flex;align-items:center;justify-content:center;aspect-ratio:16/9;border-radius:10px;background:#000}.preview-box video{width:100%;height:100%}.preview-fallback{color:#9CA3AF;text-align:center;font-size:13px;line-height:2}.preview-fallback i{display:block;margin-bottom:8px;font-size:38px}.preview-meta{display:flex;justify-content:space-between;gap:16px;margin-top:10px;color:var(--text-muted);font-size:12px}.preview-online{color:#16A34A}.preview-offline{color:#DC2626}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.opt-editor{display:flex;flex-direction:column;gap:8px;width:100%}.opt-row{display:flex;align-items:center;gap:8px}.opt-row .el-input{flex:1}.opt-mark{width:30px;height:30px;border:1px solid var(--border);border-radius:50%;background:#fff;color:var(--text-muted)}.opt-mark.correct{border-color:#16A34A;background:#16A34A;color:#fff}.table-wrap{overflow:auto}.import-guide{display:flex;align-items:flex-start;gap:12px;padding:12px 14px;margin-bottom:14px;border:1px solid #BFDBFE;border-radius:10px;background:#EFF6FF;color:#1D4ED8}.import-guide>i{margin-top:3px}.import-guide>div{flex:1}.import-guide strong{font-size:13px}.import-guide p{margin:4px 0 0;color:#4B5563;font-size:12px}.import-dropzone{display:flex;align-items:center;justify-content:center;flex-direction:column;gap:7px;padding:28px 16px;border:1.5px dashed #D1D5DB;border-radius:12px;background:#FAFAFA;color:var(--text-primary);cursor:pointer}.import-dropzone i{color:#16A34A;font-size:32px}.import-dropzone span{color:var(--text-muted);font-size:12px}.import-summary{display:flex;gap:10px;margin-top:16px}.import-summary>div{flex:1;padding:10px 14px;border:1px solid;border-radius:10px}.import-summary span{display:block;color:#6B7280;font-size:12px}.import-summary strong{display:block;margin-top:2px;font-size:20px}.import-summary .ok{border-color:#BBF7D0;background:#F0FDF4;color:#16A34A}.import-summary .bad{border-color:#FECACA;background:#FEF2F2;color:#DC2626}.import-errors,.import-preview{margin-top:14px}.import-errors>strong,.import-preview>strong{display:block;margin-bottom:6px;font-size:12.5px}.import-errors>div{max-height:120px;overflow:auto;padding:8px 12px;border:1px solid #FECACA;border-radius:8px;background:#FEF2F2}.import-errors p{margin:0;color:#B91C1C;font-size:12px;line-height:1.8}.import-preview>div{max-height:170px;overflow:auto;border:1px solid var(--border);border-radius:8px}.import-preview p{display:flex;align-items:center;gap:8px;margin:0;padding:8px 12px;border-bottom:1px solid #F3F4F6;font-size:12px}.import-preview p>span{width:26px;color:#9CA3AF}.import-preview p>b{flex:1;overflow:hidden;color:#374151;text-overflow:ellipsis;white-space:nowrap;font-weight:400}.import-preview p>i{color:#16A34A;font-style:normal;font-weight:600}.table-wrap{overflow:auto}
.academy-page .table-wrap{width:100%;overflow-x:auto}.academy-page .data-table{width:100%;min-width:860px;border-collapse:collapse;table-layout:fixed}.academy-page .data-table th{white-space:nowrap;text-align:left}.academy-page .data-table td{vertical-align:middle;text-align:left}.academy-page .data-table .q-stem{max-width:none}
</style>
