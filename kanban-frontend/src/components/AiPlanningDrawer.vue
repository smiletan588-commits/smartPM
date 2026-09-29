<template>
  <el-drawer v-model="visible" :size="drawerSize" :close-on-click-modal="false" class="planning-drawer" @closed="onClosed">
    <template #header>
      <div class="drawer-heading"><span class="eyebrow">SMARTPM · AI 规划</span><h2>{{ modeTitle }}</h2><p>先明确目标，再检查每项任务，确认后才加入看板。</p></div>
    </template>

    <div class="planning-layout">
      <div class="phase-strip"><span :class="{ active: phase === 'context' }">1 明确需求</span><span :class="{ active: phase === 'review' }">2 审核草稿</span><span>3 创建任务</span></div>

      <template v-if="phase === 'context'">
        <div class="context-note"><strong>{{ projectName }}</strong><span v-if="parentTask">父任务：{{ parentTask.title }}</span><span>AI 仅使用你在这里提供的内容和选中的文档。</span></div>
        <div class="field"><label>项目类型 <em>必填</em></label><el-select v-model="input.projectType" placeholder="选择项目类型"><el-option label="软件研发" value="SOFTWARE" /><el-option label="活动、运营或其他" value="GENERAL" /></el-select></div>
        <div class="field"><label>要达成什么目标 <em>必填</em></label><el-input v-model="input.goal" type="textarea" :rows="2" maxlength="2000" show-word-limit placeholder="例如：让学生在线报名活动，并让组织者查看名单" /></div>
        <div class="field"><label>最终应交付什么 <em>必填</em></label><el-input v-model="input.deliverable" type="textarea" :rows="2" maxlength="2000" show-word-limit placeholder="例如：可运行的报名页面、名单导出与验收记录" /></div>
        <div class="field"><label>这次做哪些内容 <em>必填</em></label><el-input v-model="input.scope" type="textarea" :rows="2" maxlength="2000" show-word-limit placeholder="列出本轮范围，也可以写明暂不做的部分" /></div>
        <div class="optional-grid"><div class="field"><label>期望期限</label><el-date-picker v-model="input.deadline" type="date" value-format="YYYY-MM-DD" placeholder="暂未确定" style="width:100%" /></div><div class="field"><label>团队条件</label><el-input v-model="input.team" maxlength="2000" placeholder="人数、岗位或其他限制（选填）" /></div></div>
        <div class="field"><label>参考项目文档 <small>最多 3 篇，自主选择</small></label><el-select v-model="input.wikiIds" multiple collapse-tags collapse-tags-tooltip :multiple-limit="3" placeholder="不选择文档" style="width:100%"><el-option v-for="wiki in wikis" :key="wiki.id" :label="wiki.title" :value="wiki.id" /></el-select></div>
        <p v-if="contextError" class="field-error" role="alert">{{ contextError }}</p>
        <div class="context-actions"><el-button type="primary" :loading="generating" @click="generate">生成可审核草稿</el-button></div>

        <section v-if="priorDrafts.length" class="draft-history"><h3>继续之前的草稿</h3><button v-for="entry in priorDrafts" :key="entry.id" type="button" @click="resume(entry.id)"><span>{{ entry.mode === 'DECOMPOSE' ? '子任务拆解' : entry.mode === 'PLAN' ? '完整计划' : '初始任务' }} · {{ entry.content?.tasks?.length || 0 }} 项</span><small>{{ entry.updatedAt?.replace('T', ' ').slice(0, 16) }} · {{ entry.status === 'APPLIED' ? '已创建' : '待审核' }}</small></button></section>
      </template>

      <template v-else-if="draft">
        <div class="review-top"><div><span class="eyebrow">草稿 #{{ draft.id }} · 第 {{ draft.version }} 版</span><h3>逐项确认工作内容</h3></div><el-button plain @click="phase = 'context'">查看需求</el-button></div>
        <div class="field"><label>规划说明</label><el-input v-model="draft.content.overview" type="textarea" :rows="2" @input="markDirty" /></div>
        <div v-if="draft.content.assumptions?.length" class="assumptions"><strong>AI 标出的待确认事项</strong><p v-for="(assumption, index) in draft.content.assumptions" :key="index">{{ assumption }}</p></div>
        <div v-if="draft.content.noSplitReason && !draft.content.tasks?.length" class="no-split"><strong>无需继续拆解</strong><p>{{ draft.content.noSplitReason }}</p><span>你可以关闭抽屉，保留现有任务。</span></div>
        <div v-if="draft.issues?.length" class="issue-list" role="status"><strong>检查提示</strong><p v-for="(issue, index) in draft.issues" :key="index" :class="{ blocking: issue.blocking }">{{ issue.blocking ? '需修正' : '请核对' }} · {{ issue.message }}</p></div>
        <p v-if="dirty" class="unsaved" role="status">有尚未检查和保存的修改</p>

        <div v-for="(item, index) in draft.content.tasks" :key="item._localKey || index" class="draft-card">
          <div class="card-head"><span class="item-number">{{ String(index + 1).padStart(2, '0') }}</span><strong>任务草稿</strong><div class="card-actions"><el-button text size="small" :disabled="index === 0" @click="moveItem(index, -1)">上移</el-button><el-button text size="small" :disabled="index === draft.content.tasks.length - 1" @click="moveItem(index, 1)">下移</el-button><el-button text size="small" :disabled="index === 0" @click="mergeItem(index)">并入上项</el-button><el-button text size="small" :loading="regeneratingIndex === index" @click="regenerate(index)">重生成</el-button><el-button text type="danger" size="small" @click="removeItem(index)">移除</el-button></div></div>
          <div class="field"><label>任务标题</label><el-input v-model="item.title" maxlength="255" @input="markDirty" /></div>
          <div class="field"><label>具体工作</label><el-input v-model="item.description" type="textarea" :rows="2" @input="markDirty" /></div>
          <div class="field"><label>交付物</label><el-input v-model="item.deliverable" placeholder="完成后能交出什么" @input="markDirty" /></div>
          <div class="field"><label>验收标准</label><el-input v-model="item.acceptanceCriteria" type="textarea" :rows="2" placeholder="怎样判断确实完成" @input="markDirty" /></div>
          <div class="field"><label>与目标的关系</label><el-input v-model="item.fitReason" placeholder="为什么这项工作属于本次范围" @input="markDirty" /></div>
          <div class="optional-grid"><div class="field"><label>建议岗位</label><el-select v-model="item.recommendedRole" clearable placeholder="不限定岗位" @change="markDirty"><el-option v-for="role in roles" :key="role.value" :label="role.label" :value="role.value" /></el-select></div><div class="field"><label>建议能力</label><el-input v-model="item.recommendedSkill" maxlength="100" placeholder="如活动策划、文案写作" @input="markDirty" /></div></div>
          <div class="optional-grid"><div class="field"><label>负责人 <small>默认不指派</small></label><el-select v-model="item.assigneeId" clearable placeholder="由成员领取" @change="markDirty"><el-option v-for="member in members" :key="member.userId" :label="member.nickname || member.username" :value="member.userId" /></el-select></div><div class="field"><label>优先级</label><el-select v-model="item.priority" @change="markDirty"><el-option label="高" value="HIGH" /><el-option label="中" value="MEDIUM" /><el-option label="低" value="LOW" /></el-select></div></div>
          <div class="optional-grid"><div class="field"><label>标签 <small>其他类型项目可留空</small></label><el-select :model-value="splitTags(item.tags)" multiple clearable placeholder="无标签" @change="values => { item.tags = values.join(','); markDirty() }"><el-option v-for="tag in tagOptions" :key="tag.value" :label="tag.label" :value="tag.value" /></el-select></div><div class="field"><label>预计工时</label><el-input-number v-model="item.estimatedHours" :min="0" :max="10000" controls-position="right" style="width:100%" @change="markDirty" /></div></div>
          <div class="optional-grid"><div class="field"><label>开始日期</label><el-date-picker v-model="item.startDate" type="date" value-format="YYYY-MM-DD" placeholder="使用默认日期" style="width:100%" @change="markDirty" /></div><div class="field"><label>截止日期</label><el-date-picker v-model="item.dueDate" type="date" value-format="YYYY-MM-DD" placeholder="遵守父任务期限" style="width:100%" @change="markDirty" /></div></div>
          <div class="field"><label>前置任务 <small>仅可选本草稿中更早的任务</small></label><el-select v-model="item.dependencyIndexes" multiple clearable placeholder="无前置任务" style="width:100%" @change="markDirty"><el-option v-for="(candidate, candidateIndex) in draft.content.tasks.slice(0, index)" :key="candidateIndex" :label="candidate.title || `任务 ${candidateIndex + 1}`" :value="candidateIndex" /></el-select></div>
        </div>

        <section v-if="draft.mode === 'PLAN' && draft.content.milestones?.length" class="milestones"><h3>里程碑</h3><div v-for="(milestone, index) in draft.content.milestones" :key="index" class="milestone-row"><el-input v-model="milestone.name" placeholder="里程碑名称" @input="markDirty" /><el-date-picker v-model="milestone.targetDate" type="date" value-format="YYYY-MM-DD" placeholder="目标日期" @change="markDirty" /><el-button text type="danger" @click="draft.content.milestones.splice(index, 1); markDirty()">移除</el-button></div></section>
        <section v-if="draft.mode === 'PLAN' && draft.content.stages?.length" class="stage-list"><h3>阶段建议</h3><p v-for="(stage, index) in draft.content.stages" :key="index">{{ stage.name }} · {{ stage.goal }}</p></section>
        <section v-if="draft.mode === 'PLAN' && draft.content.risks?.length" class="stage-list"><h3>风险提醒</h3><p v-for="(risk, index) in draft.content.risks" :key="index">{{ risk.title }} · {{ risk.mitigation }}</p></section>
      </template>
    </div>

    <template #footer v-if="phase === 'review' && draft"><div class="drawer-footer"><span>{{ draft.content.tasks?.length || 0 }} 项待审 · {{ draft.status === 'APPLIED' ? '已创建' : '尚未创建真实任务' }}</span><div><el-button @click="visible = false">关闭</el-button><el-button :loading="saving" :disabled="draft.status !== 'DRAFT'" @click="save">检查并保存</el-button><el-button type="primary" :loading="applying" :disabled="draft.status !== 'DRAFT' || !draft.content.tasks?.length" @click="apply">确认创建 {{ draft.content.tasks?.length || 0 }} 项任务</el-button></div></div></template>
  </el-drawer>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listWiki } from '@/api/wiki'
import { listPlanningDrafts, generatePlanningDraft, getPlanningDraft, savePlanningDraft, regeneratePlanningItem, applyPlanningDraft } from '@/api/planning'

const props = defineProps({ projectId: { type: Number, required: true }, projectName: { type: String, default: '' }, projectDescription: { type: String, default: '' }, members: { type: Array, default: () => [] } })
const emit = defineEmits(['applied'])
const roles = [
  { value: 'PROJECT_MANAGER', label: '项目经理' }, { value: 'PRODUCT_MANAGER', label: '产品经理' },
  { value: 'FRONTEND_DEV', label: '前端工程师' }, { value: 'BACKEND_DEV', label: '后端工程师' },
  { value: 'QA_TESTER', label: '测试工程师' }, { value: 'UI_DESIGNER', label: 'UI 设计师' }
]
const tagOptions = [{ value: 'BUG', label: 'Bug' }, { value: 'REQUIREMENT', label: '需求' }, { value: 'DESIGN', label: '设计' }, { value: 'DEVELOPMENT', label: '开发' }, { value: 'TESTING', label: '测试' }, { value: 'DOCUMENTATION', label: '文档' }]
const splitTags = value => String(value || '').split(',').filter(Boolean)
const visible = ref(false)
const phase = ref('context')
const mode = ref('INIT')
const parentTask = ref(null)
const input = ref({})
const draft = ref(null)
const priorDrafts = ref([])
const wikis = ref([])
const contextError = ref('')
const dirty = ref(false)
const generating = ref(false)
const saving = ref(false)
const applying = ref(false)
const regeneratingIndex = ref(-1)
const drawerSize = computed(() => window.innerWidth < 700 ? '100%' : 'min(860px, 92%)')
const modeTitle = computed(() => ({ INIT: '规划初始任务', PLAN: '制定完整计划', DECOMPOSE: '拆解当前任务' })[mode.value])

watch(phase, async () => {
  await nextTick()
  document.querySelector('.planning-drawer .el-drawer__body')?.scrollTo({ top: 0 })
})

function open(nextMode, task = null) {
  mode.value = nextMode
  parentTask.value = task
  input.value = {
    mode: nextMode, parentTaskId: task?.id || null,
    goal: task?.description || props.projectDescription || '',
    deliverable: '', scope: task?.title || '', projectType: '', deadline: task?.dueDate || '', team: '', wikiIds: []
  }
  draft.value = null
  phase.value = 'context'
  contextError.value = ''
  dirty.value = false
  visible.value = true
  Promise.allSettled([listWiki(props.projectId), listPlanningDrafts(props.projectId)]).then(results => {
    if (results[0].status === 'fulfilled') wikis.value = results[0].value.data.data || []
    if (results[1].status === 'fulfilled') priorDrafts.value = (results[1].value.data.data || []).filter(entry => entry.mode === nextMode && (nextMode !== 'DECOMPOSE' || entry.parentTaskId === task?.id))
  })
}

function onClosed() { contextError.value = '' }
function markDirty() { dirty.value = true }
function validContext() {
  if (!input.value.projectType) { contextError.value = '请选择项目类型'; return false }
  const missing = [['goal', '目标'], ['deliverable', '预期交付物'], ['scope', '工作范围']].find(([key]) => !input.value[key]?.trim())
  contextError.value = missing ? `请填写${missing[1]}` : ''
  return !missing
}
async function generate() {
  if (!validContext() || generating.value) return
  generating.value = true
  try {
    const result = await generatePlanningDraft(props.projectId, input.value)
    draft.value = result.data.data
    phase.value = 'review'
    dirty.value = false
    ElMessage.success('草稿已生成，请逐项审核')
  } finally { generating.value = false }
}
async function resume(id) {
  const result = await getPlanningDraft(props.projectId, id)
  draft.value = result.data.data
  input.value = { ...draft.value.input }
  phase.value = 'review'
  dirty.value = false
}
async function save() {
  if (!draft.value || saving.value) return null
  saving.value = true
  try {
    const result = await savePlanningDraft(props.projectId, draft.value.id, draft.value.version, draft.value.content)
    draft.value = result.data.data
    dirty.value = false
    ElMessage.success('草稿已保存，检查结果已更新')
    return draft.value
  } finally { saving.value = false }
}
async function apply() {
  if (!draft.value || applying.value) return
  applying.value = true
  try {
    const current = dirty.value ? await save() : draft.value
    if (current.issues?.some(issue => issue.blocking)) { ElMessage.warning('请先修正检查提示中的问题'); return }
    const result = await applyPlanningDraft(props.projectId, current.id, current.version)
    ElMessage.success(`已创建 ${result.data.data?.length || 0} 项任务`)
    visible.value = false
    emit('applied', current.parentTaskId)
  } finally { applying.value = false }
}
async function regenerate(index) {
  if (regeneratingIndex.value !== -1) return
  regeneratingIndex.value = index
  try {
    const result = await regeneratePlanningItem(props.projectId, draft.value.id, index)
    draft.value.content.tasks[index] = { ...result.data.data, dependencyIndexes: result.data.data.dependencyIndexes || [] }
    markDirty()
    ElMessage.success('单项建议已更新，请检查并保存')
  } finally { regeneratingIndex.value = -1 }
}
function reindex(oldToNew) {
  draft.value.content.tasks.forEach(item => {
    item.dependencyIndexes = (item.dependencyIndexes || []).map(value => oldToNew[value]).filter(value => value !== undefined)
  })
  ;(draft.value.content.milestones || []).forEach(item => {
    item.taskIndexes = (item.taskIndexes || []).map(value => oldToNew[value]).filter(value => value !== undefined)
  })
  markDirty()
}
function moveItem(index, direction) {
  const other = index + direction
  const items = draft.value.content.tasks
  ;[items[index], items[other]] = [items[other], items[index]]
  reindex(Object.fromEntries(items.map((_, value) => [value, value === index ? other : value === other ? index : value])))
}
function removeItem(index) {
  draft.value.content.tasks.splice(index, 1)
  const oldToNew = {}
  for (let i = 0; i <= draft.value.content.tasks.length; i++) if (i !== index) oldToNew[i] = i > index ? i - 1 : i
  reindex(oldToNew)
}
function mergeItem(index) {
  const items = draft.value.content.tasks
  const previous = items[index - 1], current = items[index]
  previous.description = [previous.description, current.description].filter(Boolean).join('\n')
  previous.deliverable = [previous.deliverable, current.deliverable].filter(Boolean).join('；')
  previous.acceptanceCriteria = [previous.acceptanceCriteria, current.acceptanceCriteria].filter(Boolean).join('；')
  previous.dependencyIndexes = [...new Set([...(previous.dependencyIndexes || []), ...(current.dependencyIndexes || [])])].filter(value => value !== index - 1)
  removeItem(index)
}
defineExpose({ open })
</script>

<style scoped>
.drawer-heading h2{margin:2px 0 4px;color:#172238;font-size:22px;letter-spacing:-.03em}.drawer-heading p{margin:0;color:#64748b;font-size:13px}.eyebrow{font-size:11px;font-weight:700;letter-spacing:.11em;color:#315cf4}.planning-layout{padding:0 8px 24px}.phase-strip{display:flex;gap:8px;padding:0 0 22px;border-bottom:1px solid #e6ebf2;margin-bottom:22px}.phase-strip span{font-size:12px;color:#94a3b8;padding:7px 10px;border-radius:7px}.phase-strip .active{color:#315cf4;background:#eaf0ff;font-weight:700}.context-note{display:flex;gap:12px;flex-wrap:wrap;padding:14px 16px;margin-bottom:20px;background:#f2f5fa;border-left:3px solid #315cf4;border-radius:8px;font-size:13px;color:#475569}.context-note strong{color:#172238}.field{display:flex;flex-direction:column;gap:7px;margin-bottom:16px}.field label{font-size:13px;font-weight:650;color:#34445c}.field label em{font-style:normal;color:#d44d4d;font-size:11px}.field label small{font-weight:400;color:#7d8ca4}.optional-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.field-error{color:#c53f3f;font-size:13px}.context-actions{display:flex;justify-content:flex-end;margin:8px 0 28px}.draft-history{border-top:1px solid #e6ebf2;padding-top:20px}.draft-history h3,.milestones h3,.stage-list h3{font-size:14px;color:#172238}.draft-history button{width:100%;display:flex;justify-content:space-between;align-items:center;text-align:left;padding:12px 8px;border:0;border-bottom:1px solid #edf0f5;background:transparent;cursor:pointer;color:#26364f}.draft-history button:hover{background:#f5f7fc}.draft-history small{color:#8592a8}.review-top{display:flex;justify-content:space-between;align-items:center;margin-bottom:18px}.review-top h3{font-size:20px;margin:5px 0 0;color:#172238}.assumptions,.no-split,.issue-list{padding:14px 16px;border-radius:9px;margin:15px 0;background:#f5f7fb;color:#42536b;font-size:13px}.assumptions{border-left:3px solid #7e71c9}.no-split{border-left:3px solid #35a97c}.issue-list{border-left:3px solid #d6a044}.assumptions strong,.no-split strong,.issue-list strong{color:#172238}.assumptions p,.no-split p,.issue-list p{margin:7px 0 0}.issue-list .blocking{color:#b94a39}.unsaved{color:#a66c26;font-size:12px}.draft-card{border:1px solid #dfe6f0;border-radius:12px;padding:18px 18px 3px;margin:18px 0;background:#fff;box-shadow:0 4px 18px rgba(32,53,89,.035)}.card-head{display:flex;align-items:center;gap:9px;margin-bottom:17px;flex-wrap:wrap}.card-head strong{color:#172238;font-size:14px}.item-number{font-variant-numeric:tabular-nums;color:#315cf4;font-weight:750;font-size:13px}.card-actions{margin-left:auto;display:flex;flex-wrap:wrap;gap:1px}.milestones,.stage-list{border-top:1px solid #e7ebf1;padding-top:14px}.milestone-row{display:flex;gap:10px;margin:10px 0}.stage-list p{font-size:13px;color:#5a6880}.drawer-footer{display:flex;justify-content:space-between;align-items:center;gap:12px}.drawer-footer span{font-size:12px;color:#6b7890}.drawer-footer>div{display:flex;gap:8px}@media(max-width:700px){.optional-grid{grid-template-columns:1fr;gap:0}.phase-strip{gap:0}.phase-strip span{padding:7px 6px;font-size:11px}.card-actions{margin-left:0;width:100%}.draft-card{padding:13px 12px 2px}.drawer-footer{align-items:stretch;flex-direction:column}.drawer-footer>div{display:flex;overflow:auto}.milestone-row{flex-wrap:wrap}}
</style>
