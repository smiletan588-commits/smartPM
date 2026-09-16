<template>
  <AppShell :project-id="projectId" :project-name="projectName">
    <main class="lab-page">
      <header class="lab-header">
        <div>
          <span class="lab-eyebrow">PRODUCT LAB · 产品共创</span>
          <h1>{{ projectName }}</h1>
          <p>让对话与产品成果同步演进。AI 负责澄清和质疑，所有真实变更仍由你确认。</p>
        </div>
        <div class="header-actions">
          <el-select v-model="activeConversationId" placeholder="选择共创会话" @change="loadConversation">
            <el-option v-for="item in conversations" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
          <el-button type="primary" :disabled="!canWrite" :title="permissionHint" @click="conversationDialog = true">新建会话</el-button>
        </div>
      </header>

      <nav class="mobile-lab-tabs">
        <button v-for="item in mobileTabs" :key="item.value" :class="{ active: mobileTab === item.value }" @click="mobileTab = item.value">{{ item.label }}</button>
      </nav>

      <section class="lab-shell">
        <aside class="context-rail" :class="{ 'mobile-active': mobileTab === 'context' }">
          <div class="rail-heading"><small>01 · DEFINE</small><h2>产品上下文</h2><p>只选择本轮真正需要的资料。</p></div>
          <label class="field-label">产品阶段</label>
          <div class="stage-list">
            <button v-for="item in stages" :key="item.value" :class="{ active: stage === item.value }" @click="stage = item.value"><i />{{ item.label }}</button>
          </div>
          <label class="field-label">共创模式</label>
          <el-select v-model="mode" style="width:100%"><el-option v-for="item in modes" :key="item.value" :label="item.label" :value="item.value" /></el-select>
          <label class="context-toggle"><span><strong>项目描述</strong><small>名称、目标与当前说明</small></span><el-switch v-model="context.includeProjectDescription" /></label>
          <label class="context-toggle"><span><strong>风险摘要</strong><small>仅发送风险等级分布</small></span><el-switch v-model="context.includeRisk" /></label>
          <label class="context-toggle"><span><strong>排期摘要</strong><small>最近基线与预计完工</small></span><el-switch v-model="context.includeSchedule" /></label>
          <div class="context-picker">
            <span>引用任务 <b>{{ context.taskIds.length }}</b></span>
            <el-select v-model="context.taskIds" multiple collapse-tags filterable placeholder="选择任务"><el-option v-for="item in tasks" :key="item.id" :label="item.title" :value="item.id" /></el-select>
          </div>
          <div class="context-picker">
            <span>引用文档 <b>{{ context.wikiIds.length }}</b></span>
            <el-select v-model="context.wikiIds" multiple collapse-tags filterable placeholder="选择 Wiki"><el-option v-for="item in wikis" :key="item.id" :label="item.title" :value="item.id" /></el-select>
          </div>
          <div class="privacy-note"><span>上下文边界</span><p>不会默认读取整个项目，也不会把产品内容放进 URL。</p></div>
        </aside>

        <section class="conversation-stage" :class="{ 'mobile-active': mobileTab === 'conversation' }">
          <header class="conversation-heading">
            <div><span class="status-dot" :class="{ thinking }" /><strong>{{ activeConversation?.title || '开始一次产品共创' }}</strong></div>
            <small>{{ modeLabel }} · {{ messages.length }} 条消息</small>
          </header>
          <div ref="messageViewport" class="message-viewport">
            <div v-if="!messages.length" class="conversation-empty">
              <span class="empty-index">00</span><h2>把模糊的想法放在这里</h2>
              <p>AI 会先找出目标用户、场景、矛盾和缺失条件，再与你一起形成可发布的产品成果。</p>
              <div class="prompt-starters"><button v-for="item in starters" :key="item" :disabled="!canWrite" :title="permissionHint" @click="message = item">{{ item }} <span>↗</span></button></div>
            </div>
            <article v-for="item in messages" :key="item.localId || item.id" class="message" :class="item.role.toLowerCase()">
              <div class="message-meta"><strong>{{ item.role === 'USER' ? '你' : 'AI 产品顾问' }}</strong><span v-if="item.status && item.status !== 'COMPLETE'">{{ item.status === 'INTERRUPTED' ? '回答中断' : '生成失败' }}</span></div>
              <div v-if="item.role === 'ASSISTANT'" class="message-content markdown-body" v-html="renderMarkdown(item.content)" />
              <div v-else class="message-content">{{ item.content }}</div>
              <button v-if="item.role === 'ASSISTANT' && item.content && !thinking" class="extract-action" :disabled="!canWrite" :title="permissionHint" @click="openArtifactDraft(item)">提炼为产品成果 →</button>
            </article>
            <article v-if="thinking" class="message assistant thinking-message"><div class="message-meta"><strong>AI 产品顾问</strong><span>正在梳理</span></div><div class="thinking-line"><i /><i /><i /></div></article>
          </div>
          <footer class="composer">
            <el-input v-model="message" type="textarea" :rows="3" resize="none" maxlength="12000" :disabled="!canWrite" :placeholder="canWrite ? composerPlaceholder : permissionHint" @keydown.ctrl.enter.prevent="sendMessage" />
            <div class="composer-footer"><span>{{ canWrite ? 'Ctrl + Enter 发送 · AI 只生成草稿' : permissionHint }}</span><el-button v-if="thinking" plain @click="stopMessage">停止生成</el-button><el-button v-else type="primary" :disabled="!canWrite || !message.trim() || !activeConversationId" :title="permissionHint" @click="sendMessage">发送并继续澄清</el-button></div>
          </footer>
        </section>

        <aside class="artifact-rail" :class="{ 'mobile-active': mobileTab === 'artifacts' }">
          <div class="rail-heading"><small>03 · SHAPE</small><h2>成果与决策</h2><p>版本化草稿，确认后才写入项目。</p></div>
          <div class="artifact-stats"><span><strong>{{ artifacts.length }}</strong> 项成果</span><span><strong>{{ publishedCount }}</strong> 已发布</span></div>
          <div class="artifact-list">
            <article v-for="item in artifacts" :key="item.id" :class="{ selected: selectedArtifact?.id === item.id }" @click="selectArtifact(item)">
              <header><span>{{ artifactTypeLabel(item.type) }}</span><b>v{{ item.versionNo }}</b></header><h3>{{ item.title }}</h3><p>{{ plainSnippet(item.content) }}</p>
              <footer><i :class="item.status.toLowerCase()" />{{ artifactStatusLabel(item.status) }}<time>{{ formatDate(item.updatedAt) }}</time></footer>
            </article>
            <div v-if="!artifacts.length" class="artifact-empty"><span>成果会出现在这里</span><p>从一条 AI 回答提炼 PRD、用户故事、验收标准或版本规划。</p></div>
          </div>
          <div v-if="selectedArtifact" class="artifact-actions">
            <el-button @click="openArtifactDetail">版本</el-button>
            <el-button v-if="selectedArtifact.status !== 'PUBLISHED'" :disabled="!canWrite" :title="permissionHint" @click="editArtifact">编辑</el-button>
            <el-button v-if="selectedArtifact.status !== 'PUBLISHED'" type="primary" plain :disabled="!canCurate" :title="curatorHint" @click="publishArtifact">发布到 Wiki</el-button>
            <el-button :disabled="!canCurate" :title="curatorHint" @click="openApply">转换到项目</el-button>
          </div>
        </aside>
      </section>
    </main>

    <el-dialog v-model="conversationDialog" title="新建产品共创会话" width="460px">
      <div class="dialog-fields"><label>会话标题<el-input v-model="conversationForm.title" placeholder="例如：移动端审批流程 MVP" /></label><label>产品阶段<el-select v-model="conversationForm.stage"><el-option v-for="item in stages" :key="item.value" :label="item.label" :value="item.value" /></el-select></label></div>
      <template #footer><el-button @click="conversationDialog=false">取消</el-button><el-button type="primary" :loading="creatingConversation" :disabled="!canWrite" :title="permissionHint" @click="createConversation">创建会话</el-button></template>
    </el-dialog>

    <el-dialog v-model="artifactDialog" :title="editingArtifactId ? '编辑产品成果' : '提炼为产品成果'" width="620px">
      <div class="dialog-fields artifact-form"><label>成果类型<el-select v-model="artifactForm.type"><el-option v-for="item in artifactTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select></label><label>标题<el-input v-model="artifactForm.title" /></label><label class="wide">可编辑草稿<el-input v-model="artifactForm.content" type="textarea" :rows="12" /></label></div>
      <template #footer><span class="draft-reminder">每次保存都会新增一个可追溯版本</span><el-button @click="artifactDialog=false">取消</el-button><el-button type="primary" :loading="savingArtifact" :disabled="!canWrite" :title="permissionHint" @click="saveArtifact">保存草稿</el-button></template>
    </el-dialog>

    <el-drawer v-model="artifactDetailVisible" title="成果版本" size="min(620px, 94vw)">
      <div v-if="artifactDetail" class="version-drawer">
        <span class="apply-kicker">{{ artifactTypeLabel(artifactDetail.type) }} · CURRENT</span>
        <h2>{{ artifactDetail.title }}</h2>
        <div class="version-current markdown-body" v-html="renderMarkdown(artifactDetail.content)" />
        <h3>版本记录</h3>
        <article v-for="version in artifactDetail.versions || []" :key="version.id" class="version-item">
          <header><strong>v{{ version.versionNo }} · {{ artifactStatusLabel(version.status) }}</strong><time>{{ version.editorName || '项目成员' }} · {{ formatDate(version.createdAt) }}</time></header>
          <p>{{ plainSnippet(version.content) }}</p>
        </article>
      </div>
    </el-drawer>

    <el-drawer v-model="applyDrawer" title="变更预览" size="min(520px, 94vw)">
      <div v-if="selectedArtifact" class="apply-drawer">
        <span class="apply-kicker">CONFIRM BEFORE APPLY</span><h2>{{ selectedArtifact.title }}</h2><p>选择写入目标。确认前不会创建任何真实数据。</p>
        <label>写入目标<el-radio-group v-model="applyForm.target"><el-radio-button value="WIKI">Wiki</el-radio-button><el-radio-button value="TASKS">任务</el-radio-button></el-radio-group></label>
        <template v-if="applyForm.target === 'TASKS'"><label>任务标题<el-input v-model="applyForm.taskTitle" /></label><label>默认工期<el-input-number v-model="applyForm.durationDays" :min="1" :max="3650" /> 天</label><label>任务描述<el-input v-model="applyForm.taskDescription" type="textarea" :rows="7" /></label></template>
        <div v-if="applyPreview" class="preview-box"><strong>预览结果</strong><p>将创建 {{ applyPreview.willCreate }} 条记录，目标：{{ applyPreview.target }}</p><span>此操作将在一个事务中提交。</span></div>
        <div class="apply-footer"><el-button :loading="previewing" :disabled="!canCurate" :title="curatorHint" @click="previewApply">刷新预览</el-button><el-button type="primary" :disabled="!canCurate || !applyPreview" :title="curatorHint" :loading="applying" @click="confirmApply">确认应用</el-button></div>
      </div>
    </el-drawer>
  </AppShell>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import MarkdownIt from 'markdown-it'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import AppShell from '@/components/AppShell.vue'
import { listProjectMembers, listProjects } from '@/api/project'
import { listTasks } from '@/api/task'
import { listWiki } from '@/api/wiki'
import { applyProductArtifact, createProductArtifact, createProductConversation, getProductArtifact, getProductConversation, listProductArtifacts, listProductConversations, previewProductArtifact, publishProductArtifact, streamProductMessage, updateProductArtifact } from '@/api/productLab'

const route = useRoute(); const userStore = useUserStore(); const projectId = Number(route.params.id); const md = new MarkdownIt({ html: false, linkify: true, breaks: true })
const projectName = ref('项目产品共创'); const conversations = ref([]); const activeConversationId = ref(null); const activeConversation = ref(null)
const messages = ref([]); const artifacts = ref([]); const tasks = ref([]); const wikis = ref([]); const message = ref(''); const thinking = ref(false); const messageViewport = ref(null)
const activeStream = ref(null)
const stage = ref('IDEA'); const mode = ref('IDEA_REFINEMENT'); const mobileTab = ref('conversation'); const selectedArtifact = ref(null)
const context = reactive({ includeProjectDescription: true, includeRisk: false, includeSchedule: false, taskIds: [], wikiIds: [] })
const conversationDialog = ref(false); const creatingConversation = ref(false); const conversationForm = reactive({ title: '', stage: 'IDEA' })
const artifactDialog = ref(false); const savingArtifact = ref(false); const artifactForm = reactive({ type: 'PRD', title: '', content: '' })
const editingArtifactId = ref(null); const artifactDetailVisible = ref(false); const artifactDetail = ref(null)
const applyDrawer = ref(false); const applyPreview = ref(null); const previewing = ref(false); const applying = ref(false); const applyForm = reactive({ target: 'WIKI', taskTitle: '', taskDescription: '', durationDays: 15 })
const mobileTabs = [{ value:'context',label:'上下文'},{ value:'conversation',label:'对话'},{ value:'artifacts',label:'成果' }]
const stages = [{value:'IDEA',label:'创意探索'},{value:'RESEARCH',label:'用户调研'},{value:'REQUIREMENT',label:'需求定义'},{value:'PLANNING',label:'版本规划'},{value:'REVIEW',label:'反方评审'}]
const modes = [{value:'IDEA_REFINEMENT',label:'完善产品想法'},{value:'REQUIREMENT_CLARIFICATION',label:'需求澄清'},{value:'USER_STORY',label:'用户故事'},{value:'PRD_DRAFT',label:'PRD 草拟'},{value:'PRIORITY',label:'优先级评估'},{value:'DEVILS_ADVOCATE',label:'反方评审'},{value:'RELEASE_PLAN',label:'版本规划'},{value:'MEETING_NOTES',label:'会议整理'}]
const artifactTypes = [{value:'IDEA_BRIEF',label:'产品想法简报'},{value:'PRD',label:'PRD 文档'},{value:'USER_STORIES',label:'用户故事'},{value:'ACCEPTANCE_CRITERIA',label:'验收标准'},{value:'ROADMAP',label:'版本路线图'},{value:'RISK_REVIEW',label:'风险评审'},{value:'DECISION_LOG',label:'决策记录'}]
const starters = ['帮我判断这个想法解决的是真需求还是伪需求','把这段需求改写成用户故事和验收标准','站在反方角度挑战当前方案的边界条件']
const modeLabel = computed(() => modes.find(item => item.value === mode.value)?.label || '产品共创')
const composerPlaceholder = computed(() => `以「${modeLabel.value}」模式继续讨论…`)
const publishedCount = computed(() => artifacts.value.filter(item => item.status === 'PUBLISHED').length)
const currentProject = ref(null); const currentMember = ref(null)
const canWrite = computed(() => Boolean(currentProject.value?.owner || (currentMember.value && currentMember.value.permission !== 'VIEWER')))
const canCurate = computed(() => canWrite.value && (currentProject.value?.owner || currentMember.value?.permission === 'PROJECT_ADMIN' || ['PRODUCT_MANAGER','PROJECT_MANAGER'].includes(currentMember.value?.identity)))
const permissionHint = computed(() => canWrite.value ? '' : '只读成员只能查看已发布成果和项目内容')
const curatorHint = computed(() => canCurate.value ? '' : '只有具备写权限的产品经理、项目经理或项目管理员可以发布和应用成果')

async function bootstrap() {
  const [projectsRes,membersRes,tasksRes,wikiRes,conversationsRes] = await Promise.all([listProjects(),listProjectMembers(projectId),listTasks(projectId),listWiki(projectId),listProductConversations(projectId)])
  currentProject.value = projectsRes.data.data.find(item => item.id === projectId) || null; currentMember.value = (membersRes.data.data || []).find(item => item.userId === userStore.userInfo?.userId) || null
  projectName.value = currentProject.value?.name || '项目产品共创'; tasks.value = tasksRes.data.data || []; wikis.value = wikiRes.data.data || []; conversations.value = conversationsRes.data.data || []
  if (conversations.value.length) { activeConversationId.value = conversations.value[0].id; await loadConversation() }
}
async function loadConversation() {
  if (!activeConversationId.value) return
  const res = await getProductConversation(projectId,activeConversationId.value); activeConversation.value = res.data.data; messages.value = activeConversation.value.messages || []; artifacts.value = activeConversation.value.artifacts || []; stage.value = activeConversation.value.stage; mode.value = activeConversation.value.mode; selectedArtifact.value = artifacts.value[0] || null; scrollToBottom()
}
async function createConversation() {
  if (!canWrite.value) return ElMessage.warning(permissionHint.value)
  if (!conversationForm.title.trim()) return ElMessage.warning('请输入会话标题')
  creatingConversation.value=true
  try { const res=await createProductConversation(projectId,{title:conversationForm.title,stage:conversationForm.stage,mode:mode.value}); conversationDialog.value=false; conversationForm.title=''; await refreshConversations(); activeConversationId.value=res.data.data.id; await loadConversation() } finally { creatingConversation.value=false }
}
async function refreshConversations() { conversations.value=(await listProductConversations(projectId)).data.data || [] }
async function sendMessage() {
  if (!canWrite.value) return ElMessage.warning(permissionHint.value)
  if (!message.value.trim() || thinking.value || !activeConversationId.value) return
  const text=message.value.trim(); message.value=''; messages.value.push({localId:`u-${Date.now()}`,role:'USER',content:text,status:'COMPLETE'}); const assistant={localId:`a-${Date.now()}`,role:'ASSISTANT',content:'',status:'COMPLETE'}; messages.value.push(assistant); thinking.value=true; scrollToBottom()
  activeStream.value = new AbortController()
  try { await streamProductMessage(projectId,activeConversationId.value,{message:text,mode:mode.value,...context},(event,data)=>{ if(event==='delta'){assistant.content+=data; scrollToBottom()} if(event==='error') throw new Error(data) },activeStream.value.signal); await refreshArtifacts() }
  catch(error){ assistant.status=assistant.content ? 'INTERRUPTED':'FAILED'; if(error.name==='AbortError') ElMessage.info('已停止生成，当前回答已按未完成留档'); else ElMessage.error(error.message || 'AI 回答生成失败') }
  finally { activeStream.value=null; thinking.value=false; await refreshConversations(); scrollToBottom() }
}
function stopMessage(){ activeStream.value?.abort() }
function selectArtifact(item){ selectedArtifact.value=item }
function openArtifactDraft(item){ if(!canWrite.value)return ElMessage.warning(permissionHint.value); editingArtifactId.value=null; artifactForm.type=mode.value==='USER_STORY'?'USER_STORIES':mode.value==='RELEASE_PLAN'?'ROADMAP':mode.value==='MEETING_NOTES'?'DECISION_LOG':'PRD'; artifactForm.title=`${modeLabel.value}成果`; artifactForm.content=item.content; artifactDialog.value=true }
function editArtifact(){ if(!canWrite.value)return ElMessage.warning(permissionHint.value); editingArtifactId.value=selectedArtifact.value.id; artifactForm.type=selectedArtifact.value.type; artifactForm.title=selectedArtifact.value.title; artifactForm.content=selectedArtifact.value.content; artifactDialog.value=true }
async function saveArtifact(){ if(!canWrite.value)return ElMessage.warning(permissionHint.value); if(!artifactForm.title.trim()||!artifactForm.content.trim())return ElMessage.warning('标题和内容不能为空'); savingArtifact.value=true; try{const payload={conversationId:activeConversationId.value,type:artifactForm.type,title:artifactForm.title,content:artifactForm.content,status:'DRAFT'}; if(editingArtifactId.value) await updateProductArtifact(projectId,editingArtifactId.value,payload); else await createProductArtifact(projectId,payload); artifactDialog.value=false; await refreshArtifacts(); ElMessage.success(editingArtifactId.value?'新版本已保存':'成果草稿已保存'); editingArtifactId.value=null}finally{savingArtifact.value=false} }
async function openArtifactDetail(){ artifactDetail.value=(await getProductArtifact(projectId,selectedArtifact.value.id)).data.data; artifactDetailVisible.value=true }
async function refreshArtifacts(){ artifacts.value=(await listProductArtifacts(projectId,activeConversationId.value)).data.data||[]; selectedArtifact.value=artifacts.value.find(item=>item.id===selectedArtifact.value?.id)||artifacts.value[0]||null }
async function publishArtifact(){ if(!canCurate.value)return ElMessage.warning(curatorHint.value); try{await ElMessageBox.confirm('将当前成果发布为 Wiki 文档并保留产品成果版本，是否继续？','确认发布',{type:'warning'})}catch{return}; selectedArtifact.value=(await publishProductArtifact(projectId,selectedArtifact.value.id)).data.data; await refreshArtifacts(); ElMessage.success('产品成果已发布到 Wiki') }
function openApply(){ if(!canCurate.value)return ElMessage.warning(curatorHint.value); applyForm.target='WIKI'; applyForm.taskTitle=selectedArtifact.value.title; applyForm.taskDescription=selectedArtifact.value.content; applyForm.durationDays=15; applyPreview.value=null; applyDrawer.value=true }
function applyPayload(confirmed=false){ return applyForm.target==='TASKS'?{target:'TASKS',confirmed,tasks:[{title:applyForm.taskTitle,description:applyForm.taskDescription,durationDays:applyForm.durationDays,priority:'MEDIUM'}]}:{target:'WIKI',confirmed} }
async function previewApply(){ if(!canCurate.value)return ElMessage.warning(curatorHint.value); previewing.value=true; try{applyPreview.value=(await previewProductArtifact(projectId,selectedArtifact.value.id,applyPayload(false))).data.data}finally{previewing.value=false} }
async function confirmApply(){ if(!canCurate.value)return ElMessage.warning(curatorHint.value); applying.value=true; try{await applyProductArtifact(projectId,selectedArtifact.value.id,applyPayload(true)); ElMessage.success('产品成果变更已应用到项目'); applyDrawer.value=false; await refreshArtifacts()}finally{applying.value=false} }
function renderMarkdown(value){return md.render(value||'')}; function plainSnippet(value){return (value||'').replace(/[#*`>\[\]]/g,' ').replace(/\s+/g,' ').slice(0,90)}
function artifactTypeLabel(value){return artifactTypes.find(item=>item.value===value)?.label||value}; function artifactStatusLabel(value){return {DRAFT:'草稿',IN_REVIEW:'评审中',APPROVED:'已批准',PUBLISHED:'已发布'}[value]||value}
function formatDate(value){return value?String(value).slice(0,10):''}; function scrollToBottom(){nextTick(()=>{if(messageViewport.value)messageViewport.value.scrollTop=messageViewport.value.scrollHeight})}
onMounted(()=>bootstrap().catch(()=>{}))
onBeforeUnmount(()=>activeStream.value?.abort())
</script>

<style scoped>
.lab-page{height:100dvh;min-height:700px;padding:24px 28px 28px;overflow:hidden;background:#f3f5f8}.lab-header{display:flex;align-items:flex-end;justify-content:space-between;gap:24px;height:112px;max-width:1800px;margin:0 auto;padding:0 4px 22px}.lab-eyebrow,.rail-heading small,.apply-kicker{color:#6f55c7;font-size:10px;font-weight:800;letter-spacing:.16em}.lab-header h1{margin:5px 0 3px;font-size:26px;letter-spacing:-.04em}.lab-header p{margin:0;color:var(--text-tertiary);font-size:12px}.header-actions{display:flex;gap:8px}.header-actions .el-select{width:220px}.lab-shell{display:grid;grid-template-columns:260px minmax(420px,1fr) 300px;height:calc(100dvh - 164px);max-width:1800px;margin:0 auto;overflow:hidden;border:1px solid #dfe4ec;border-radius:16px;background:var(--surface);box-shadow:0 18px 48px rgba(27,41,67,.08)}.context-rail,.artifact-rail{min-width:0;padding:24px 20px;overflow:auto;background:#f8f9fc}.context-rail{border-right:1px solid var(--border-light)}.artifact-rail{display:flex;flex-direction:column;border-left:1px solid var(--border-light)}.rail-heading h2{margin:6px 0 4px;font-size:17px}.rail-heading p{margin:0 0 22px;color:var(--text-tertiary);font-size:11px;line-height:1.6}.field-label{display:block;margin:18px 0 8px;color:var(--text-secondary);font-size:11px;font-weight:700}.stage-list{display:grid;gap:3px}.stage-list button{display:flex;align-items:center;gap:9px;padding:8px 10px;border:0;border-radius:8px;color:var(--text-secondary);background:transparent;text-align:left;cursor:pointer}.stage-list button i{width:6px;height:6px;border:1px solid #9aa5b6;border-radius:50%}.stage-list button.active{color:var(--brand-deep);background:var(--brand-light);font-weight:700}.stage-list button.active i{border-color:var(--brand);background:var(--brand)}.context-toggle{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:13px 0;border-bottom:1px solid var(--border-light)}.context-toggle span{display:grid}.context-toggle strong,.context-picker>span{font-size:12px}.context-toggle small{color:var(--text-tertiary);font-size:10px}.context-picker{display:grid;gap:7px;margin-top:16px}.context-picker>span{display:flex;justify-content:space-between;color:var(--text-secondary);font-weight:650}.privacy-note{margin-top:22px;padding:12px;border-left:2px solid #7658d6;background:#f1effa}.privacy-note span{color:#5f47ae;font-size:10px;font-weight:800}.privacy-note p{margin:5px 0 0;color:var(--text-secondary);font-size:10px;line-height:1.5}.conversation-stage{display:grid;grid-template-rows:64px 1fr auto;min-width:0;overflow:hidden;background:#fff}.conversation-heading{display:flex;align-items:center;justify-content:space-between;padding:0 24px;border-bottom:1px solid var(--border-light)}.conversation-heading>div{display:flex;align-items:center;gap:10px}.conversation-heading small{color:var(--text-tertiary)}.status-dot{width:7px;height:7px;border-radius:50%;background:#5eae84}.status-dot.thinking{background:#7658d6;box-shadow:0 0 0 5px rgba(118,88,214,.12);animation:pulse 1.3s infinite}.message-viewport{min-height:0;padding:28px clamp(24px,5vw,70px);overflow-y:auto;scroll-behavior:smooth}.conversation-empty{max-width:600px;margin:8vh auto 0}.empty-index{font:700 58px/1 var(--font-mono);color:#e2e6ee}.conversation-empty h2{margin:-12px 0 8px;font-size:25px;letter-spacing:-.04em}.conversation-empty p{max-width:520px;color:var(--text-secondary);line-height:1.8}.prompt-starters{display:grid;gap:8px;margin-top:26px}.prompt-starters button{display:flex;justify-content:space-between;padding:12px 14px;border:1px solid var(--border);border-radius:9px;color:var(--text-secondary);background:#fafbfc;text-align:left;cursor:pointer}.prompt-starters button:hover{border-color:#9cafee;color:var(--brand-deep);background:var(--brand-soft)}.message{max-width:760px;margin:0 0 28px}.message.user{margin-left:auto;padding:14px 16px;border-radius:12px 12px 3px 12px;background:#edf2ff}.message.assistant{padding-left:18px;border-left:2px solid #7658d6}.message-meta{display:flex;justify-content:space-between;margin-bottom:7px}.message-meta strong{font-size:11px}.message-meta span{color:#7658d6;font-size:10px}.message-content{color:#344054;font-size:13px;line-height:1.75;white-space:pre-wrap}.message-content.markdown-body{white-space:normal}.message-content :deep(h1),.message-content :deep(h2),.message-content :deep(h3){margin:18px 0 8px;color:var(--text-primary)}.message-content :deep(p){margin:7px 0}.extract-action{margin-top:10px;padding:0;border:0;color:#6448be;background:transparent;font-size:11px;font-weight:700;cursor:pointer}.thinking-line{display:flex;gap:4px}.thinking-line i{width:6px;height:6px;border-radius:50%;background:#8d73df;animation:bounce 1s infinite}.thinking-line i:nth-child(2){animation-delay:.15s}.thinking-line i:nth-child(3){animation-delay:.3s}.composer{margin:0 clamp(20px,4vw,56px) 22px;padding:12px;border:1px solid #d9deea;border-radius:12px;background:#fbfcfe;box-shadow:0 8px 24px rgba(32,46,72,.08)}.composer :deep(.el-textarea__inner){box-shadow:none;background:transparent}.composer-footer{display:flex;align-items:center;justify-content:space-between;gap:14px;padding:8px 4px 0}.composer-footer span{color:var(--text-tertiary);font-size:10px}.artifact-stats{display:flex;gap:16px;margin-bottom:14px;color:var(--text-tertiary);font-size:10px}.artifact-stats strong{color:var(--text-primary);font-size:13px}.artifact-list{display:grid;gap:9px;min-height:0;overflow:auto}.artifact-list article{padding:13px;border:1px solid var(--border-light);border-radius:10px;background:#fff;cursor:pointer}.artifact-list article.selected{border-color:#8b73db;box-shadow:0 0 0 2px rgba(118,88,214,.09)}.artifact-list header,.artifact-list footer{display:flex;align-items:center;justify-content:space-between}.artifact-list header span{color:#6f55c7;font-size:9px;font-weight:800}.artifact-list header b{color:var(--text-tertiary);font:600 9px var(--font-mono)}.artifact-list h3{margin:8px 0 5px;font-size:13px}.artifact-list p{margin:0 0 12px;color:var(--text-tertiary);font-size:10px;line-height:1.6}.artifact-list footer{justify-content:flex-start;gap:6px;color:var(--text-secondary);font-size:9px}.artifact-list footer i{width:6px;height:6px;border-radius:50%;background:#aab2c0}.artifact-list footer i.published{background:var(--success)}.artifact-list time{margin-left:auto;color:var(--text-tertiary)}.artifact-empty{margin-top:30px;padding:20px 12px;border:1px dashed var(--border);border-radius:10px;text-align:center}.artifact-empty span{font-size:12px;font-weight:700}.artifact-empty p{color:var(--text-tertiary);font-size:10px;line-height:1.6}.artifact-actions{display:grid;grid-template-columns:1fr 1fr;gap:7px;padding-top:14px}.dialog-fields{display:grid;gap:14px}.dialog-fields label,.apply-drawer label{display:grid;gap:7px;color:var(--text-secondary);font-size:12px;font-weight:650}.artifact-form{grid-template-columns:1fr 2fr}.artifact-form .wide{grid-column:1/-1}.draft-reminder{margin-right:auto;color:var(--text-tertiary);font-size:11px}.apply-drawer{display:grid;gap:18px}.apply-drawer h2{margin:0;font-size:22px}.apply-drawer>p{margin:-10px 0 4px;color:var(--text-secondary);line-height:1.7}.preview-box{padding:14px;border:1px solid #cad5fa;border-radius:10px;background:var(--brand-soft)}.preview-box p{margin:6px 0}.preview-box span{color:var(--text-tertiary);font-size:11px}.apply-footer{display:flex;justify-content:flex-end;gap:8px}.mobile-lab-tabs{display:none}@keyframes pulse{50%{opacity:.5}}@keyframes bounce{50%{transform:translateY(-4px)}}
.version-drawer h2{margin:7px 0 18px}.version-current{max-height:38vh;padding:18px;overflow:auto;border:1px solid var(--border-light);border-radius:10px;background:#fafbfc;color:var(--text-secondary);font-size:12px;line-height:1.75}.version-drawer>h3{margin:26px 0 8px;font-size:14px}.version-item{padding:13px 0;border-bottom:1px solid var(--border-light)}.version-item header{display:flex;justify-content:space-between;gap:12px}.version-item strong{font-size:11px}.version-item time{color:var(--text-tertiary);font-size:10px}.version-item p{margin:7px 0 0;color:var(--text-tertiary);font-size:11px;line-height:1.6}
@media(max-width:1100px){.lab-shell{grid-template-columns:220px minmax(390px,1fr) 250px}.lab-header{height:104px}.lab-page{padding:18px}.message-viewport{padding-inline:28px}}
@media(max-width:767px){.lab-page{height:calc(100dvh - 60px);min-height:0;padding:14px;overflow:hidden}.lab-header{align-items:flex-start;height:126px;padding:2px 2px 14px}.lab-header p{display:none}.lab-header h1{max-width:180px;font-size:20px}.header-actions{display:grid;width:150px}.header-actions .el-select{width:150px}.mobile-lab-tabs{display:grid;grid-template-columns:repeat(3,1fr);height:42px;margin-bottom:10px;padding:3px;border-radius:9px;background:#e9edf3}.mobile-lab-tabs button{border:0;border-radius:7px;color:var(--text-secondary);background:transparent}.mobile-lab-tabs button.active{color:var(--text-primary);background:#fff;box-shadow:var(--shadow-xs);font-weight:700}.lab-shell{display:block;height:calc(100% - 178px);border-radius:12px}.context-rail,.conversation-stage,.artifact-rail{display:none;width:100%;height:100%;border:0}.context-rail.mobile-active,.conversation-stage.mobile-active,.artifact-rail.mobile-active{display:grid}.context-rail.mobile-active,.artifact-rail.mobile-active{display:block}.message-viewport{padding:20px 16px}.composer{margin:0 12px 12px}.composer-footer span{display:none}.conversation-heading{padding-inline:16px}.conversation-empty{margin-top:3vh}.conversation-empty h2{font-size:21px}.artifact-form{grid-template-columns:1fr}}
</style>
