<template>
  <AppShell :project-id="projectId" :project-name="projectName">
    <div class="wiki-page">
      <div class="wiki-heading">
        <PageHeader eyebrow="文档中心" :title="projectName">
          <template #actions><el-button class="docs-trigger" :icon="Collection" @click="docsOpen = true">文档列表</el-button><span v-if="currentDocId" class="save-state" :class="saveState" aria-live="polite">{{ saveStateLabel }}</span><el-button v-if="currentDocId" type="primary" :loading="saving" :disabled="!canWrite" :title="writePermissionHint" @click="saveDoc">{{ saveState === 'error' ? '重新保存' : '保存文档' }}</el-button></template>
        </PageHeader>
      </div>
    <div class="wiki-body">
      <button v-if="docsOpen" class="docs-backdrop" aria-label="关闭文档列表" @click="docsOpen = false" />
      <!-- 左侧：文档列表 -->
      <aside class="wiki-sidebar" :class="{ 'is-open': docsOpen }">
        <div class="sidebar-header">
          <span class="sidebar-title">文档列表</span>
          <el-button type="primary" size="small" :icon="Plus" :disabled="!canWrite" :title="writePermissionHint" @click="handleCreateDoc">
            新建
          </el-button>
        </div>
        <div class="doc-search">
          <el-input v-model="wikiKeyword" clearable placeholder="搜索标题或正文" @input="queueWikiSearch" />
          <el-select v-model="wikiTaskFilter" clearable filterable placeholder="按任务筛选" @change="loadDocs"><el-option v-for="task in tasks" :key="task.id" :label="task.title" :value="task.id" /></el-select>
        </div>
        <div class="doc-list" v-loading="docsLoading">
          <div
            v-for="doc in docs"
            :key="doc.id"
            class="doc-item"
            :class="{ active: currentDocId === doc.id }"
            @click="selectDoc(doc); docsOpen = false"
          >
            <div class="doc-item-main">
              <el-icon class="doc-icon"><Document /></el-icon>
              <div class="doc-info">
                <span class="doc-title">{{ doc.title }}</span>
                <span class="doc-time">{{ doc.editorName ? `${doc.editorName} · ` : '' }}{{ formatTime(doc.updateTime) }}</span>
              </div>
            </div>
            <el-popconfirm
              :title="`将“${doc.title}”移入回收站？之后可以恢复。`"
              confirm-button-text="移入回收站"
              @confirm="handleDeleteDoc(doc)"
              @click.stop
            >
              <template #reference>
                <el-button text size="small" type="danger" :icon="Delete" :disabled="!canWrite" :title="writePermissionHint" @click.stop />
              </template>
            </el-popconfirm>
          </div>
          <div v-if="!docsLoading && docsError" class="empty-docs docs-error" role="alert">
            <p>文档列表暂时无法加载</p><span>{{ docsError }}</span><el-button size="small" type="primary" plain @click="loadDocs">重新加载</el-button>
          </div>
          <div v-else-if="!docsLoading && docs.length === 0" class="empty-docs">
            <p>暂无文档</p>
            <span>点击「新建」创建第一篇文档</span>
          </div>
        </div>
      </aside>

      <!-- 右侧：编辑器 + AI 面板 -->
      <div class="wiki-main" @mouseup="onEditorMouseUp">
        <div v-if="!currentDocId && !creatingNew" class="editor-placeholder">
          <el-icon class="placeholder-icon"><Document /></el-icon>
          <p>选择左侧文档开始编辑，或新建一篇文档</p>
        </div>

        <div v-else class="editor-wrapper">
          <div class="editor-toolbar">
            <span class="editor-doc-title">{{ currentDocTitle }}</span>
            <el-button size="small" @click="taskLinksVisible = true">关联任务 {{ currentDocTaskIds.length || '' }}</el-button>
            <el-button size="small" @click="openVersions">历史版本</el-button>
            <el-button
              :type="aiPanelVisible ? 'primary' : 'default'"
              size="small"
              :icon="MagicStick"
              @click="toggleAiPanel"
            >
              AI 写作助手
            </el-button>
          </div>
          <MdEditor
            v-model="docContent"
            language="zh-CN"
            :disabled="!canWrite"
            :toolbars="editorToolbars"
            style="flex:1;"
          />
        </div>

        <!-- AI 写作助手面板 -->
        <transition name="ai-slide">
          <div v-if="aiPanelVisible" class="ai-panel">
            <div class="ai-panel-header">
              <span><el-icon><MagicStick /></el-icon> AI 写作助手</span>
              <el-button text size="small" @click="aiPanelVisible = false">
                <el-icon><Close /></el-icon>
              </el-button>
            </div>

            <div class="ai-panel-body">
              <div class="ai-field">
                <label>AI 指令</label>
                <el-input
                  v-model="aiPrompt"
                  placeholder="例如：润色、续写、翻译为英文、格式化排版..."
                  clearable
                />
              </div>

              <div class="ai-field">
                <label>
                  待处理文本
                  <span class="ai-hint">在编辑器中划选文字可自动填入</span>
                </label>
                <el-input
                  v-model="aiText"
                  type="textarea"
                  :rows="5"
                  placeholder="选中编辑器中的文本，或直接在此输入..."
                />
              </div>

              <el-button
                type="primary"
                :loading="aiRunning"
                :disabled="!canWrite || !aiPrompt || !aiText"
                :title="writePermissionHint"
                @click="runAi"
                style="width:100%;"
              >
                <el-icon v-if="!aiRunning"><MagicStick /></el-icon>
                {{ aiRunning ? 'AI 生成中...' : 'AI 运行' }}
              </el-button>

              <div v-if="aiOutput" class="ai-output-area">
                <div class="ai-output-header">
                  <span>AI 生成结果</span>
                  <div class="ai-output-actions">
                    <el-button size="small" type="primary" :disabled="!canWrite" :title="writePermissionHint" @click="replaceWithAi">
                      替换原文
                    </el-button>
                    <el-button size="small" :disabled="!canWrite" :title="writePermissionHint" @click="insertAiAtEnd">
                      插入末尾
                    </el-button>
                  </div>
                </div>
                <div class="ai-output-content" ref="aiOutputEl">
                  <div class="markdown-body" v-html="renderedAiOutput" />
                </div>
              </div>
            </div>
          </div>
        </transition>
      </div>
    </div>
    <el-drawer v-model="versionsVisible" title="历史版本" size="min(560px, 94vw)" append-to-body>
      <div class="version-list" v-loading="versionsLoading">
        <article v-for="version in versions" :key="version.id"><header><strong>v{{ version.versionNo }} · {{ version.title }}</strong><el-button size="small" :disabled="!canWrite" :title="writePermissionHint" @click="restoreVersion(version)">恢复为新版本</el-button></header><p>{{ version.content?.slice(0, 180) || '空文档' }}</p><small>{{ version.editorName || '未知编辑人' }} · {{ formatTime(version.createdAt) }}</small></article>
        <p v-if="!versions.length && !versionsLoading" class="version-empty">暂无历史版本</p>
      </div>
    </el-drawer>
    <el-dialog v-model="taskLinksVisible" title="关联项目任务" width="520px"><el-select v-model="currentDocTaskIds" multiple filterable collapse-tags placeholder="选择任务" style="width:100%" :disabled="!canWrite"><el-option v-for="task in tasks" :key="task.id" :label="task.title" :value="task.id" /></el-select><template #footer><el-button @click="taskLinksVisible=false">关闭</el-button><el-button v-if="canWrite" type="primary" :loading="linkSaving" @click="saveTaskLinks">保存关联</el-button></template></el-dialog>
    </div>
  </AppShell>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Document, MagicStick, Close, Collection } from '@element-plus/icons-vue'
import { listWiki, getWiki, createWiki, updateWiki, deleteWiki, listWikiVersions, restoreWikiVersion, updateWikiTaskLinks, streamAiCopilot } from '@/api/wiki'
import { listTasks } from '@/api/task'
import { listProjectMembers, listProjects } from '@/api/project'
import { useUserStore } from '@/store/user'
import MarkdownIt from 'markdown-it'
import AppShell from '@/components/AppShell.vue'
import PageHeader from '@/components/PageHeader.vue'

const route = useRoute()
const userStore = useUserStore()
const projectId = computed(() => Number(route.params.id))

const projectName = ref('文档中心')
const docs = ref([])
const docsLoading = ref(false)
const docsError = ref('')
const currentDocId = ref(null)
const currentDocTitle = ref('')
const docContent = ref('')
const saving = ref(false)
const creatingNew = ref(false)
const docsOpen = ref(false)
const wikiKeyword = ref('')
const wikiTaskFilter = ref(route.query.task ? Number(route.query.task) : null)
const tasks = ref([])
const currentDocTaskIds = ref([])
const versionsVisible = ref(false)
const versionsLoading = ref(false)
const versions = ref([])
const taskLinksVisible = ref(false)
const linkSaving = ref(false)
const saveState = ref('saved')
const savedContent = ref('')
const currentProject = ref(null)
const currentMember = ref(null)
const canWrite = computed(() => Boolean(currentProject.value?.owner || (currentMember.value && currentMember.value.permission !== 'VIEWER')))
const writePermissionHint = computed(() => canWrite.value ? '' : '只读成员只能查看项目内容')
let wikiSearchTimer

// md-editor-v3 工具栏配置
const editorToolbars = [
  'bold', 'italic', 'strikethrough', 'title', '|',
  'unorderedList', 'orderedList', 'code', 'quote', 'table', '|',
  'link', 'image', '|',
  'preview', 'fullscreen'
]

// AI 面板
const aiPanelVisible = ref(false)
const aiPrompt = ref('')
const aiText = ref('')
const aiOutput = ref('')
const aiRunning = ref(false)
const aiOutputEl = ref(null)

const md = new MarkdownIt({ breaks: true, linkify: true })
const renderedAiOutput = computed(() => {
  if (!aiOutput.value) return ''
  return md.render(aiOutput.value)
})
const saveStateLabel = computed(() => ({ saved: '已保存', unsaved: '尚未保存', error: '保存失败' })[saveState.value] || '')

// ── 文档列表 ──

async function loadDocs() {
  docsLoading.value = true
  docsError.value = ''
  try {
    const res = await listWiki(projectId.value, { keyword: wikiKeyword.value.trim() || undefined, taskId: wikiTaskFilter.value || undefined }, { errorMode: 'silent' })
    docs.value = res.data?.data || []
  } catch (e) {
    console.error('[Wiki] 加载文档列表失败:', e)
    docs.value = []
    docsError.value = e.message || '暂时无法连接服务器，请稍后重试'
  } finally {
    docsLoading.value = false
  }
}

function queueWikiSearch() { clearTimeout(wikiSearchTimer); wikiSearchTimer = setTimeout(loadDocs, 220) }

async function selectDoc(doc) {
  if (currentDocId.value === doc.id) return
  // 切换前保存当前文档
  if (currentDocId.value && docContent.value !== undefined) {
    if (!(await saveDocSilent())) return
  }
  currentDocId.value = doc.id
  currentDocTitle.value = doc.title
  currentDocTaskIds.value = [...(doc.taskIds || [])]
  docsLoading.value = true
  try {
    const res = await getWiki(doc.id)
    const wiki = res.data?.data
    docContent.value = wiki?.content || ''
    savedContent.value = docContent.value
    saveState.value = 'saved'
  } catch (e) {
    console.error('[Wiki] 加载文档失败:', e)
  } finally {
    docsLoading.value = false
  }
}

async function handleCreateDoc() {
  if (!canWrite.value) return ElMessage.warning(writePermissionHint.value)
  const { value: title } = await ElMessageBox.prompt('请输入文档标题', '新建文档', {
    confirmButtonText: '创建',
    cancelButtonText: '取消',
    inputPattern: /\S/,
    inputErrorMessage: '标题不能为空'
  }).catch(() => ({ value: null }))

  if (!title) return

  try {
    const res = await createWiki(projectId.value, title, '')
    const wiki = res.data?.data
    ElMessage.success('文档创建成功')
    creatingNew.value = false
    await loadDocs()
    if (wiki?.id) {
      currentDocId.value = wiki.id
      currentDocTitle.value = wiki.title
      docContent.value = ''
      savedContent.value = ''
      saveState.value = 'saved'
    }
  } catch (e) { console.error('[Wiki] 创建文档失败:', e) }
}

async function saveDoc() {
  if (!canWrite.value) return ElMessage.warning(writePermissionHint.value)
  if (!currentDocId.value) return
  saving.value = true
  try {
    await updateWiki(currentDocId.value, currentDocTitle.value, docContent.value)
    savedContent.value = docContent.value
    saveState.value = 'saved'
    ElMessage.success('文档已保存')
    await loadDocs()
  } catch (e) {
    console.error('[Wiki] 保存失败:', e)
    saveState.value = 'error'
  } finally {
    saving.value = false
  }
}

async function openVersions() {
  if (!currentDocId.value) return
  versionsVisible.value = true
  versionsLoading.value = true
  try { versions.value = (await listWikiVersions(currentDocId.value)).data?.data || [] }
  finally { versionsLoading.value = false }
}

async function restoreVersion(version) {
  if (!canWrite.value) return ElMessage.warning(writePermissionHint.value)
  try { await ElMessageBox.confirm(`将 v${version.versionNo} 恢复为新的当前版本？现有内容仍保留在历史中。`, '恢复版本', { type: 'warning' }) } catch { return }
  const result = (await restoreWikiVersion(currentDocId.value, version.id)).data?.data
  currentDocTitle.value = result?.title || version.title
  docContent.value = result?.content ?? version.content
  ElMessage.success('版本已恢复')
  await loadDocs()
  await openVersions()
}

async function saveTaskLinks() {
  if (!canWrite.value) return ElMessage.warning(writePermissionHint.value)
  linkSaving.value = true
  try {
    await updateWikiTaskLinks(currentDocId.value, currentDocTaskIds.value)
    taskLinksVisible.value = false
    ElMessage.success('关联任务已更新')
    await loadDocs()
  } finally { linkSaving.value = false }
}

async function saveDocSilent() {
  if (!currentDocId.value || docContent.value === savedContent.value) return true
  try {
    await updateWiki(currentDocId.value, currentDocTitle.value, docContent.value, { errorMode: 'silent' })
    savedContent.value = docContent.value
    saveState.value = 'saved'
    return true
  } catch {
    saveState.value = 'error'
    return false
  }
}

async function handleDeleteDoc(doc) {
  if (!canWrite.value) return ElMessage.warning(writePermissionHint.value)
  try {
    await deleteWiki(doc.id)
    ElMessage.success('文档已移入回收站')
    if (currentDocId.value === doc.id) {
      currentDocId.value = null
      currentDocTitle.value = ''
      docContent.value = ''
      savedContent.value = ''
      saveState.value = 'saved'
    }
    await loadDocs()
  } catch (e) {
    console.error('[Wiki] 删除文档失败:', e)
  }
}

watch(docContent, value => {
  if (currentDocId.value && value !== savedContent.value && saveState.value !== 'error') saveState.value = 'unsaved'
})

function formatTime(time) {
  if (!time) return ''
  const d = new Date(time)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// ── 文本选中自动填入 AI 面板 ──

function onEditorMouseUp() {
  if (aiRunning.value) return
  setTimeout(() => {
    const sel = window.getSelection().toString().trim()
    if (sel && sel.length > 0) {
      aiText.value = sel
    }
  }, 100)
}

// ── AI 面板 ──

function toggleAiPanel() {
  aiPanelVisible.value = !aiPanelVisible.value
  if (!aiPanelVisible.value) {
    aiOutput.value = ''
  }
}

async function runAi() {
  if (!canWrite.value) return ElMessage.warning(writePermissionHint.value)
  if (!aiPrompt.value || !aiText.value) return
  aiRunning.value = true
  aiOutput.value = ''
  try {
    streamAiCopilot(aiPrompt.value, aiText.value, {
      onChunk(chunk) {
        aiOutput.value += chunk
        nextTick(() => {
          if (aiOutputEl.value) {
            aiOutputEl.value.scrollTop = aiOutputEl.value.scrollHeight
          }
        })
      },
      onDone() {
        aiRunning.value = false
      },
      onError(err) {
        console.error('[AI-Copilot] 错误:', err)
        ElMessage.error(err.message || 'AI 请求失败')
        aiRunning.value = false
      }
    })
  } catch (e) {
    console.error('[AI-Copilot] 异常:', e)
    ElMessage.error('AI 请求异常')
    aiRunning.value = false
  }
}

function replaceWithAi() {
  if (!canWrite.value) return ElMessage.warning(writePermissionHint.value)
  if (!aiOutput.value) return
  docContent.value = aiOutput.value
  aiOutput.value = ''
  ElMessage.success('已替换编辑器内容')
}

function insertAiAtEnd() {
  if (!canWrite.value) return ElMessage.warning(writePermissionHint.value)
  if (!aiOutput.value) return
  docContent.value = (docContent.value || '') + '\n\n' + aiOutput.value
  aiOutput.value = ''
  ElMessage.success('已插入到文档末尾')
}

// ── 生命周期 ──

onMounted(async () => {
  projectName.value = route.query.projectName || '文档中心'
  try {
    const [projectsRes, membersRes, tasksRes] = await Promise.all([listProjects(), listProjectMembers(projectId.value), listTasks(projectId.value)])
    currentProject.value = (projectsRes.data?.data || []).find(item => item.id === projectId.value) || null
    currentMember.value = (membersRes.data?.data || []).find(item => item.userId === userStore.userInfo?.userId) || null
    tasks.value = tasksRes.data?.data || []
  } catch { tasks.value = [] }
  await loadDocs()
  const requestedWiki = Number(route.query.wiki)
  const doc = requestedWiki ? docs.value.find(item => item.id === requestedWiki) : null
  if (doc) await selectDoc(doc)
})
</script>

<style scoped media="not all">
.wiki-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg-base);
}

/* 顶栏 */
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 52px;
  background: #242321;
  border-bottom: 1px solid #3A3732;
  flex-shrink: 0;
  z-index: 10;
}
.topbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.topbar-left h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #F7F1E7;
}
.sep { color: var(--text-tertiary); font-size: 14px; }
.page-label {
  font-size: 13px;
  color: #B9B1A5;
  background: #3A3732;
  padding: 2px 10px;
  border-radius: 12px;
}
.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.avatar-dot {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #D58A22;
  color: #241D14;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
}

/* 主体两栏布局 */
.wiki-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* 左侧栏 */
.wiki-sidebar {
  width: 280px;
  background: #2B2A28;
  border-right: 1px solid #403D38;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid var(--border);
}
.sidebar-title {
  font-weight: 600;
  font-size: 14px;
  color: #F7F1E7;
}
.doc-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.doc-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background 0.15s;
  margin-bottom: 2px;
}
.doc-item:hover { background: #3A3732; }
.doc-item.active {
  background: var(--brand-light);
  border-left: 3px solid var(--brand);
  padding-left: 9px;
}
.doc-item-main {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}
.doc-icon { color: var(--text-tertiary); flex-shrink: 0; }
.doc-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.doc-title {
  font-size: 13px;
  font-weight: 500;
  color: #F7F1E7;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.doc-time {
  font-size: 11px;
  color: var(--text-tertiary);
  margin-top: 2px;
}
.empty-docs {
  text-align: center;
  padding: 40px 16px;
  color: var(--text-tertiary);
}
.empty-docs p { margin: 0 0 4px; font-size: 14px; }
.empty-docs span { font-size: 12px; }

/* 右侧编辑器区域 */
.wiki-main {
  flex: 1;
  display: flex;
  overflow: hidden;
  position: relative;
}
.editor-placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: var(--text-tertiary);
}
.editor-placeholder p { margin: 0; font-size: 14px; }
.editor-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  transition: width 0.3s;
}

/* 右侧有 AI 面板时压缩编辑器 */
.wiki-main:has(.ai-panel) .editor-wrapper {
  /* editor 自动缩小给 ai 面板让位 */
}

.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #2B2A28;
  border-bottom: 1px solid #403D38;
}
.editor-doc-title {
  font-weight: 600;
  font-size: 14px;
  color: #F7F1E7;
}

/* AI 面板 */
.ai-panel {
  width: 380px;
  background: #242321;
  border-left: 1px solid #403D38;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow: hidden;
}
.ai-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
  font-weight: 600;
  font-size: 14px;
  color: var(--brand);
}
.ai-panel-header span {
  display: flex;
  align-items: center;
  gap: 6px;
}
.ai-panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.ai-field label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 6px;
}
.ai-hint {
  font-weight: 400;
  font-size: 12px;
  color: var(--text-tertiary);
}
.ai-output-area {
  border-top: 1px solid var(--border);
  padding-top: 14px;
  margin-top: 4px;
}
.ai-output-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  flex-wrap: wrap;
  gap: 8px;
}
.ai-output-header > span {
  font-size: 13px;
  font-weight: 500;
}
.ai-output-actions {
  display: flex;
  gap: 6px;
}
.ai-output-content {
  max-height: 360px;
  overflow-y: auto;
  background: var(--bg-elevated);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 12px 14px;
  font-size: 13px;
  line-height: 1.7;
}

/* AI 面板过渡动画 */
.ai-slide-enter-active,
.ai-slide-leave-active {
  transition: all 0.25s ease;
}
.ai-slide-enter-from,
.ai-slide-leave-to {
  width: 0;
  opacity: 0;
  transform: translateX(20px);
}

/* Markdown 渲染样式 */
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3) {
  margin-top: 12px;
  margin-bottom: 8px;
  color: var(--text-primary);
}
.markdown-body :deep(p) { margin: 6px 0; }
.markdown-body :deep(code) {
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
  font-family: var(--font-mono);
}
.markdown-body :deep(pre) {
  background: #1e293b;
  color: #e2e8f0;
  padding: 12px;
  border-radius: var(--radius-sm);
  overflow-x: auto;
}
.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--brand);
  padding-left: 12px;
  color: var(--text-secondary);
  margin: 8px 0;
}
.markdown-body :deep(ul), .markdown-body :deep(ol) {
  padding-left: 20px;
}
.markdown-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.markdown-body :deep(th), .markdown-body :deep(td) {
  border: 1px solid var(--border);
  padding: 6px 10px;
  text-align: left;
}
.markdown-body :deep(th) { background: var(--bg-elevated); }
</style>

<style scoped>
.wiki-page { display:flex; flex-direction:column; height:100dvh; min-width:0; overflow:hidden; background:var(--bg-base); }.wiki-heading { flex:0 0 auto; padding:24px 28px 0; background:var(--bg-base); }.wiki-heading :deep(.page-header-component) { align-items:center; margin-bottom:20px; }.wiki-heading :deep(h1) { font-size:23px; }.save-state{font-size:11px;color:var(--text-tertiary);white-space:nowrap}.save-state.unsaved{color:var(--warning)}.save-state.error{color:var(--danger);font-weight:650}
.doc-search { display:grid; gap:7px; padding:10px 12px; border-bottom:1px solid var(--border-light); }.version-list { display:grid; gap:10px; }.version-list article { padding:14px; border:1px solid var(--border-light); border-radius:9px; background:var(--surface); }.version-list header { display:flex; align-items:center; justify-content:space-between; gap:12px; }.version-list strong { min-width:0; overflow:hidden; font-size:12px; text-overflow:ellipsis; white-space:nowrap; }.version-list p { margin:9px 0; overflow:hidden; color:var(--text-secondary); font-size:11px; line-height:1.65; display:-webkit-box; -webkit-box-orient:vertical; -webkit-line-clamp:4; }.version-list small { color:var(--text-tertiary); font-size:10px; }.version-empty { padding:50px 0; color:var(--text-tertiary); text-align:center; }
.wiki-body { position:relative; display:flex; flex:1; min-height:0; margin:0 28px 28px; overflow:hidden; border:1px solid var(--border); border-radius:12px; background:var(--surface); box-shadow:var(--shadow-xs); }.wiki-sidebar { display:flex; flex:0 0 260px; flex-direction:column; border-right:1px solid var(--border); background:var(--surface); }.sidebar-header { display:flex; align-items:center; justify-content:space-between; padding:14px; border-bottom:1px solid var(--border-light); }.sidebar-title { color:var(--text-primary); font-size:13px; font-weight:700; }.doc-list { flex:1; overflow-y:auto; padding:8px; }.doc-item { display:flex; align-items:center; justify-content:space-between; margin-bottom:2px; padding:10px; border-radius:8px; cursor:pointer; transition:background-color 160ms ease; }.doc-item:hover { background:var(--bg-hover); }.doc-item.active { color:var(--brand-deep); background:var(--brand-light); }.doc-item-main { display:flex; align-items:center; gap:9px; min-width:0; flex:1; }.doc-icon { flex:0 0 auto; color:var(--text-tertiary); }.doc-info { display:flex; min-width:0; flex-direction:column; }.doc-title { overflow:hidden; color:var(--text-primary); font-size:13px; font-weight:600; text-overflow:ellipsis; white-space:nowrap; }.doc-time { margin-top:2px; color:var(--text-tertiary); font-size:11px; }.empty-docs { padding:40px 14px; color:var(--text-tertiary); text-align:center; }.empty-docs p { margin:0 0 4px; font-size:13px; }.empty-docs span { font-size:12px; }.docs-error .el-button{margin-top:12px}
.wiki-main { position:relative; display:flex; min-width:0; flex:1; overflow:hidden; }.editor-placeholder { display:flex; flex:1; flex-direction:column; align-items:center; justify-content:center; gap:14px; color:var(--text-tertiary); text-align:center; }.placeholder-icon { font-size:48px; color:var(--border-strong); }.editor-placeholder p { margin:0 18px; font-size:13px; }.editor-wrapper { display:flex; min-width:0; flex:1; flex-direction:column; }.editor-toolbar { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:9px 14px; border-bottom:1px solid var(--border-light); background:var(--surface); }.editor-doc-title { overflow:hidden; color:var(--text-primary); font-size:14px; font-weight:650; text-overflow:ellipsis; white-space:nowrap; }
.ai-panel { display:flex; flex:0 0 360px; flex-direction:column; overflow:hidden; border-left:1px solid var(--border); background:var(--surface); }.ai-panel-header { display:flex; align-items:center; justify-content:space-between; padding:13px 15px; border-bottom:1px solid var(--border-light); color:var(--brand-deep); font-size:13px; font-weight:700; }.ai-panel-header span { display:flex; align-items:center; gap:6px; }.ai-panel-body { display:flex; flex:1; flex-direction:column; gap:14px; overflow-y:auto; padding:15px; }.ai-field label { display:block; margin-bottom:6px; color:var(--text-secondary); font-size:12px; font-weight:600; }.ai-hint { display:block; margin-top:3px; color:var(--text-tertiary); font-size:11px; font-weight:400; }.ai-output-area { margin-top:4px; padding-top:14px; border-top:1px solid var(--border); }.ai-output-header { display:flex; align-items:center; justify-content:space-between; gap:8px; margin-bottom:10px; flex-wrap:wrap; }.ai-output-header>span { font-size:13px; font-weight:600; }.ai-output-actions { display:flex; gap:6px; }.ai-output-content { max-height:360px; overflow-y:auto; padding:12px 14px; border:1px solid var(--border); border-radius:8px; background:var(--surface-strong); font-size:13px; line-height:1.7; }.ai-slide-enter-active,.ai-slide-leave-active { transition:opacity 180ms ease,transform 180ms ease; }.ai-slide-enter-from,.ai-slide-leave-to { opacity:0; transform:translateX(12px); }
.markdown-body :deep(h1),.markdown-body :deep(h2),.markdown-body :deep(h3) { margin:12px 0 8px; color:var(--text-primary); }.markdown-body :deep(p) { margin:6px 0; }.markdown-body :deep(code) { padding:2px 6px; border-radius:4px; background:var(--surface-strong); font-size:12px; font-family:var(--font-mono); }.markdown-body :deep(pre) { overflow-x:auto; padding:12px; border-radius:8px; color:#e7ecf4; background:#202938; }.markdown-body :deep(blockquote) { margin:8px 0; padding-left:12px; border-left:3px solid var(--brand); color:var(--text-secondary); }.markdown-body :deep(ul),.markdown-body :deep(ol) { padding-left:20px; }.markdown-body :deep(table) { width:100%; border-collapse:collapse; font-size:12px; }.markdown-body :deep(th),.markdown-body :deep(td) { padding:6px 10px; border:1px solid var(--border); text-align:left; }.markdown-body :deep(th) { background:var(--surface-strong); }.docs-trigger,.docs-backdrop { display:none; }
@media (max-width:1100px) { .ai-panel { position:absolute; inset:0 0 0 auto; z-index:5; width:min(360px,72%); box-shadow:var(--shadow-lg); } }
@media (max-width:767px) { .wiki-page { height:calc(100dvh - 60px); }.wiki-heading { padding:18px 16px 0; }.wiki-heading :deep(.page-header-component) { margin-bottom:16px; }.wiki-heading :deep(.page-header-copy p) { display:none; }.wiki-heading :deep(.page-header-actions) { width:auto; }.wiki-body { margin:0 16px 16px; }.docs-trigger { display:inline-flex; }.wiki-sidebar { position:absolute; inset:0 auto 0 0; z-index:12; width:min(280px,86%); transform:translateX(-104%); transition:transform 190ms ease; box-shadow:var(--shadow-lg); }.wiki-sidebar.is-open { transform:translateX(0); }.docs-backdrop { position:absolute; inset:0; z-index:11; display:block; border:0; background:rgba(21,31,50,.34); }.ai-panel { width:100%; }.editor-toolbar { padding:8px 10px; flex-wrap:wrap; }.editor-doc-title { flex-basis:100%; }.editor-toolbar .el-button { padding-inline:8px; } }
</style>

<!-- 覆盖 md-editor-v3 默认样式（unscoped） -->
<style>
.wiki-main .md-editor {
  border: none;
  border-radius: 0;
  height: 100%;
}
.wiki-main .md-editor-toolbar {
  border-radius: 0;
}
</style>
