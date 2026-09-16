<template>
  <AppShell>
    <main class="content">
      <PageHeader eyebrow="回收站" title="找回误删内容" description="删除的数据会保留在这里。恢复后将回到原来的项目和位置。"><template #actions><el-button :loading="loading" @click="fetchItems">刷新</el-button></template></PageHeader>
      <section class="notice"><strong>恢复规则</strong><span>项目管理员可恢复本项目数据；系统管理员可永久删除。永久删除后无法撤销。</span></section>
      <StatePanel v-if="loadError" tone="error" title="回收站暂时无法加载" :description="loadError"><template #actions><el-button type="primary" plain @click="fetchItems">重新加载</el-button></template></StatePanel>
      <section v-else class="bin" v-loading="loading">
        <div class="bin-head"><strong>已删除项目、任务与文档</strong><small>{{ items.length }} 项</small></div>
        <el-table :data="items" class="bin-table desktop-table" empty-text="回收站为空">
          <el-table-column label="内容" min-width="210"><template #default="{ row }"><div class="item-title"><span :class="['type', row.type.toLowerCase()]">{{ typeLabel(row.type) }}</span><strong>{{ row.title }}</strong></div></template></el-table-column>
          <el-table-column label="所属项目" min-width="160"><template #default="{ row }"><span class="muted">{{ row.projectName }}</span></template></el-table-column>
          <el-table-column label="删除人" width="120"><template #default="{ row }"><span class="muted">{{ row.deletedByName }}</span></template></el-table-column>
          <el-table-column label="删除时间" width="165"><template #default="{ row }"><span class="muted">{{ formatTime(row.deletedAt) }}</span></template></el-table-column>
          <el-table-column label="操作" width="210" fixed="right"><template #default="{ row }"><el-button text type="primary" :loading="busy === keyOf(row)" @click="restore(row)">恢复</el-button><el-button v-if="userStore.systemRole === 'ADMIN'" text type="danger" :loading="busy === keyOf(row)" @click="permanentlyDelete(row)">永久删除</el-button></template></el-table-column>
        </el-table>
        <div class="mobile-records">
          <article v-for="item in items" :key="keyOf(item)" class="record-card">
            <div class="item-title"><span :class="['type', item.type.toLowerCase()]">{{ typeLabel(item.type) }}</span><strong>{{ item.title }}</strong></div>
            <dl><div><dt>所属项目</dt><dd>{{ item.projectName }}</dd></div><div><dt>删除人</dt><dd>{{ item.deletedByName }}</dd></div><div><dt>删除时间</dt><dd>{{ formatTime(item.deletedAt) }}</dd></div></dl>
            <div class="record-actions"><el-button text type="primary" :loading="busy === keyOf(item)" @click="restore(item)">恢复</el-button><el-button v-if="userStore.systemRole === 'ADMIN'" text type="danger" :loading="busy === keyOf(item)" @click="permanentlyDelete(item)">永久删除</el-button></div>
          </article>
          <StatePanel v-if="!loading && !items.length" title="回收站为空" description="删除的项目、任务与文档会显示在这里。" compact />
        </div>
      </section>
    </main>
  </AppShell>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { listRecycleBin, permanentlyDeleteRecycleItem, restoreRecycleItem } from '@/api/recycleBin'
import AppShell from '@/components/AppShell.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'

const userStore = useUserStore(); const items = ref([]); const loading = ref(false); const busy = ref(''); const loadError = ref('')
const labels = { PROJECT: '项目', TASK: '任务', WIKI: '文档', ATTACHMENT: '附件' }
const typeLabel = type => labels[type] || type
const keyOf = row => `${row.type}-${row.id}`
const formatTime = value => value ? String(value).replace('T', ' ').slice(0, 16) : '未记录'
async function fetchItems() { loading.value = true; loadError.value=''; try { items.value = (await listRecycleBin({ errorMode:'silent' })).data.data || [] } catch(error) { items.value=[]; loadError.value=error.message || '暂时无法连接服务器，请稍后重试' } finally { loading.value = false } }
async function restore(row) { busy.value = keyOf(row); try { await restoreRecycleItem(row.type, row.id); ElMessage.success('已恢复到原项目'); await fetchItems() } finally { busy.value = '' } }
async function permanentlyDelete(row) { try { await ElMessageBox.prompt(`永久删除“${row.title}”后将无法恢复。请输入完整名称确认：`, '永久删除', { type: 'error', confirmButtonText: '永久删除', cancelButtonText: '取消', inputPlaceholder: row.title, inputValidator: value => value === row.title || '输入内容与名称不一致' }) } catch { return }; busy.value = keyOf(row); try { await permanentlyDeleteRecycleItem(row.type, row.id); ElMessage.success(`“${row.title}”已永久删除`); await fetchItems() } finally { busy.value = '' } }
onMounted(fetchItems)
</script>

<style scoped>
.content { max-width: 1200px; margin: 0 auto; padding: 34px 36px 52px; }.notice { display:flex; gap:14px; padding:14px 16px; margin:24px 0 18px; border:1px solid var(--border); border-left:3px solid var(--brand); border-radius:10px; background:var(--brand-light); font-size:13px; }.notice strong { color:var(--brand-deep); white-space:nowrap; }.notice span { color:var(--text-secondary); }.bin { overflow:hidden; border:1px solid var(--border); border-radius:12px; background:var(--surface); box-shadow:var(--shadow-xs); }.bin-head { display:flex; justify-content:space-between; padding:16px 20px; border-bottom:1px solid var(--border-light); }.bin-head small,.muted { color:var(--text-tertiary); font-size:12px; }.item-title { display:flex; align-items:center; gap:9px; min-width:0; }.item-title strong { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.type { flex:0 0 auto; padding:3px 7px; border-radius:999px; font-size:10px; font-weight:700; background:var(--surface-strong); color:var(--text-secondary); }.type.project { background:var(--brand-light); color:var(--brand-deep); }.type.task { background:var(--info-soft); color:var(--info); }.type.wiki { background:#f0eefc; color:#6355a5; }.type.attachment { background:var(--success-soft); color:var(--success); }.mobile-records { display:none; }
@media (max-width:767px) { .content { padding:22px 16px 36px; }.notice { flex-direction:column; gap:5px; }.desktop-table { display:none; }.mobile-records { display:grid; gap:12px; padding:14px; }.record-card { display:grid; gap:14px; padding:16px; border:1px solid var(--border-light); border-radius:10px; }.record-card dl { display:grid; gap:8px; margin:0; }.record-card dl div { display:flex; justify-content:space-between; gap:12px; }.record-card dt { color:var(--text-tertiary); font-size:12px; }.record-card dd { margin:0; color:var(--text-secondary); font-size:13px; text-align:right; }.record-actions { display:flex; justify-content:flex-end; border-top:1px solid var(--border-light); padding-top:8px; } }
</style>
