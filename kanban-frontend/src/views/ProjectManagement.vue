<template>
  <AppShell :project-id="projectId" :project-name="projectName">
    <main class="management-main" v-loading="loading">
      <PageHeader eyebrow="项目管理" :title="projectName"><template #actions><el-button v-if="activeSection === 'overview'" @click="downloadSummaryPdf">导出管理简报</el-button><el-button v-if="activeSection === 'overview' && canManageSchedule" @click="sendSummaryEmail">发送周报</el-button><el-button v-if="activeSection === 'milestones'" type="primary" :disabled="!canWriteSchedule" :title="writePermissionHint" @click="openMilestone()">新建里程碑</el-button><el-button v-if="canManageSchedule" @click="templateVisible=true">保存为模板</el-button></template></PageHeader>
      <nav class="management-tabs" aria-label="项目管理栏目">
        <button v-for="item in managementSections" :key="item.value" :class="{ active: activeSection === item.value }" @click="setSection(item.value)">{{ item.label }}</button>
      </nav>
      <StatePanel v-if="loadError" tone="error" title="项目管理数据暂时无法加载" :description="loadError"><template #actions><el-button type="primary" plain @click="load">重新加载</el-button></template></StatePanel>
      <template v-if="!loadError && activeSection === 'overview'">
        <section class="health-hero" :class="(executive.health?.level || 'GREEN').toLowerCase()">
          <div class="health-grade"><span>PROJECT HEALTH</span><strong>{{ healthLabel(executive.health?.level) }}</strong></div>
          <div class="health-copy"><h2>项目决策驾驶舱</h2><p v-for="reason in executive.health?.reasons || ['正在计算项目健康度']" :key="reason">{{ reason }}</p></div>
          <div class="health-finish"><span>预计完工</span><strong>{{ executive.schedule?.finishDate || '待排期' }}</strong><small>{{ signedDays(executive.schedule?.finishVarianceDays) }} 相对基线</small></div>
        </section>
        <section class="summary-grid decision-metrics">
          <div class="summary-card"><span title="总浮动为 0，延期会直接影响预计完工日的任务">关键任务</span><strong>{{ executive.schedule?.criticalTaskCount || 0 }}</strong></div>
          <div class="summary-card danger"><span>高风险</span><strong>{{ executive.risks?.high || 0 }}</strong></div>
          <div class="summary-card"><span>待确认决策</span><strong>{{ executive.openDecisions?.length || 0 }}</strong></div>
          <div class="summary-card"><span>负载异常</span><strong>{{ executive.overloadedMemberCount || 0 }}</strong></div>
          <div class="summary-card"><span>未分配</span><strong>{{ executive.unassignedTaskCount || 0 }}</strong></div>
          <div class="summary-card"><span>阻塞链</span><strong>{{ executive.blockedTaskCount || 0 }}</strong></div>
        </section>
        <section class="overview-columns">
          <article class="panel"><div class="panel-heading"><div><p class="eyebrow">需要管理者确认</p><h2>产品决策</h2></div></div><div class="decision-list"><button v-for="item in executive.openDecisions || []" :key="item.id" @click="$router.push(`/project/${projectId}/product-lab`)"><strong>{{ item.title }}</strong><span>{{ item.dueDate || '未设期限' }} →</span></button><p v-if="!executive.openDecisions?.length" class="empty">暂无待确认决策。</p></div></article>
          <article class="panel"><div class="panel-heading"><div><p class="eyebrow">最近项目动态</p><h2>执行轨迹</h2></div></div><div class="activity-list"><span v-for="item in executive.recentActivities || []" :key="item.id"><i /><div><strong>{{ item.actorName || '系统' }}</strong> {{ item.summary }}<small>{{ formatDateTime(item.createdAt) }}</small></div></span><p v-if="!executive.recentActivities?.length" class="empty">暂无项目动态。</p></div></article>
        </section>
      </template>

      <section v-show="!loadError && activeSection === 'risk'" class="panel risk-panel">
        <div class="panel-heading">
          <div><p class="eyebrow">可解释预警</p><h2>项目风险轨迹</h2><p class="panel-note">分数由逾期、依赖、停滞和工作负载等确定性规则计算。</p></div>
          <el-button :loading="aiRiskLoading" @click="analyzeRisks">AI 给出调整建议</el-button>
        </div>
        <div class="risk-summary" aria-label="风险等级统计">
          <div class="high"><span>高风险</span><strong>{{ risks.highCount || 0 }}</strong></div>
          <div class="medium"><span>中风险</span><strong>{{ risks.mediumCount || 0 }}</strong></div>
          <div class="low"><span>低风险</span><strong>{{ risks.lowCount || 0 }}</strong></div>
        </div>
        <div class="risk-layout">
          <div class="risk-list">
            <article v-for="risk in (risks.risks || []).slice(0, 30)" :key="risk.taskId" class="risk-row" :class="risk.level.toLowerCase()" @click="openRiskAction(risk)">
              <div class="risk-score"><strong>{{ risk.score }}</strong><span>风险分</span></div>
              <div class="risk-copy"><header><h3>{{ risk.title }}</h3><span>{{ riskLevelLabel(risk.level) }}</span></header><p>{{ risk.suggestion }}</p><div class="risk-action-meta"><b>{{ riskActionLabel(risk.actionStatus) }}</b><span v-if="risk.riskOwnerName">负责人：{{ risk.riskOwnerName }}</span><span v-if="risk.actionDueDate">期限：{{ risk.actionDueDate }}</span></div><div><small v-for="factor in risk.factors" :key="factor">{{ factor }}</small></div></div>
              <span class="risk-open">处理 →</span>
            </article>
            <div v-if="!(risks.risks || []).length" class="empty">当前没有需要计算的未完成任务。</div>
          </div>
          <aside class="risk-side">
            <h3>阻塞关系</h3>
            <div v-for="edge in (risks.blockedEdges || []).slice(0, 6)" :key="`${edge.taskId}-${edge.prerequisiteTaskId}`" class="blocked-edge"><strong>{{ edge.prerequisiteTitle }}</strong><span>阻塞</span><strong>{{ edge.taskTitle }}</strong></div>
            <p v-if="!(risks.blockedEdges || []).length" class="panel-note">暂无未完成的前置依赖。</p>
          </aside>
        </div>
        <div v-if="aiRiskContent" class="ai-risk-result">
          <div class="ai-risk-heading"><strong>AI 调整建议</strong><div v-if="aiOperationId"><span>这份建议有帮助吗？</span><el-rate v-model="aiRating" @change="rateAiSuggestion" /></div></div>
          <div class="markdown-body" v-html="renderedAiRisk" />
        </div>
      </section>

      <section v-show="!loadError && activeSection === 'schedule'" class="panel gantt-panel">
        <div class="panel-heading">
          <div><p class="eyebrow">CPM · 时间线与依赖</p><h2>关键路径调度台</h2><p class="panel-note">基线影子保留原计划，实线展示系统推算排期；关键任务没有可用浮动。</p></div>
          <div class="schedule-actions">
            <el-select v-model="selectedBaselineId" clearable placeholder="不对比基线" size="small" @change="loadScheduleContext">
              <el-option v-for="baseline in baselines" :key="baseline.id" :label="baseline.name" :value="baseline.id" />
            </el-select>
            <el-button size="small" :disabled="!canWriteSchedule" :title="writePermissionHint" @click="baselineVisible = true">保存基线</el-button>
            <el-button size="small" type="primary" :disabled="!activeScheduleTasks.length" title="临时改变任务日期或工期，不会修改真实排期" @click="openSimulation">What-if 模拟</el-button>
          </div>
        </div>

        <div class="schedule-metrics">
          <article><span>预计完工</span><strong>{{ schedule.projectFinishDate || '待排期' }}</strong><small>{{ schedule.durationDays || 0 }} 个日历日</small></article>
          <article><span title="总浮动为 0 的任务会构成关键路径">关键任务</span><strong>{{ schedule.criticalTaskCount || 0 }}</strong><small>{{ criticalPathLabel }}</small></article>
          <article :class="{ delayed: (schedule.finishVarianceDays || 0) > 0 }"><span title="当前预计完工日与所选计划基线的日历日差值">基线偏差</span><strong>{{ signedDays(schedule.finishVarianceDays) }}</strong><small>{{ selectedBaselineId ? '相对所选基线' : '尚未选择基线' }}</small></article>
          <article><span title="任务缺少日期时，系统按预计工时换算日历日">自动推算</span><strong>{{ schedule.inferredTaskCount || 0 }}</strong><small>缺失日期按工时换算</small></article>
        </div>

        <div v-if="schedule.warnings?.length" class="schedule-warnings">
          <strong>排期提示</strong>
          <span v-for="warning in schedule.warnings" :key="warning">{{ warning }}</span>
        </div>

        <div v-if="scheduleTasks.length" class="gantt-wrap">
          <div class="gantt-head" :style="timelineRowStyle">
            <span>任务 / 浮动</span>
            <div class="gantt-days" :style="timelineTrackStyle"><span v-for="day in timelineLabels" :key="day.date">{{ day.label }}</span></div>
          </div>
          <div v-for="task in scheduleTasks" :key="task.taskId" class="gantt-row" :class="{ 'critical-row': task.critical }" :style="timelineRowStyle">
            <div class="gantt-task-name">
              <div><strong>{{ task.title }}</strong><em v-if="task.critical">关键</em><em v-else>{{ task.totalSlackDays }} 天浮动</em></div>
              <small>{{ task.source === 'EXPLICIT' ? '已设置日期' : task.source === 'SIMULATED' ? '模拟排期' : '系统推算' }}<template v-if="task.finishVarianceDays"> · {{ varianceLabel(task.finishVarianceDays) }}</template></small>
            </div>
            <div class="gantt-track" :style="timelineTrackStyle">
              <div v-if="task.baselineStartDate" class="baseline-bar" :style="ganttBarStyle(task.baselineStartDate, task.baselineFinishDate)" :title="`基线：${task.baselineStartDate} → ${task.baselineFinishDate}`" />
              <div class="gantt-bar" :class="{ done: task.status === 'DONE', critical: task.critical }" :style="ganttBarStyle(task.plannedStartDate, task.plannedFinishDate)" :title="`${task.plannedStartDate} → ${task.plannedFinishDate}`">
                <span>{{ task.plannedStartDate }} → {{ task.plannedFinishDate }}</span>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="empty">当前项目还没有可计算的主任务。创建任务后即可生成关键路径。</div>

        <footer v-if="baselines.length" class="baseline-ledger">
          <div><strong>计划基线</strong><span>不可变快照用于审计初版计划与后续偏差。</span></div>
          <div class="baseline-chips">
            <button v-for="baseline in baselines" :key="baseline.id" :class="{ active: baseline.id === selectedBaselineId }" @click="selectBaseline(baseline.id)">
              <span>{{ baseline.name }}</span><small>{{ baseline.projectFinishDate || '无完工日' }} · {{ baseline.taskCount }} 项</small>
              <i v-if="canManageSchedule" role="button" tabindex="0" aria-label="删除基线" @click.stop="removeBaseline(baseline)" @keydown.enter.stop="removeBaseline(baseline)">×</i>
            </button>
          </div>
        </footer>
      </section>

      <section v-show="!loadError && (activeSection === 'milestones' || activeSection === 'team')" class="two-column single">
        <div v-show="activeSection === 'milestones'" class="panel">
          <div class="panel-heading"><div><p class="eyebrow">关键节点</p><h2>项目里程碑</h2></div></div>
          <div v-if="milestones.length" class="milestone-list">
            <article v-for="milestone in milestones" :key="milestone.id" class="milestone-card">
              <div class="milestone-date">{{ milestone.targetDate || '待定' }}</div>
              <div class="milestone-content">
                <div class="milestone-title"><h3>{{ milestone.name }}</h3><span :class="milestone.status === 'COMPLETED' ? 'complete' : 'planned'">{{ milestone.status === 'COMPLETED' ? '已达成' : '进行中' }}</span></div>
                <p v-if="milestone.description">{{ milestone.description }}</p>
                <small>关联 {{ milestoneTasks(milestone).length }} 个任务 / 已完成 {{ milestoneDoneCount(milestone) }} 个</small>
              </div>
              <div class="milestone-actions"><el-button text :disabled="!canWriteSchedule" :title="writePermissionHint" @click="openMilestone(milestone)">编辑</el-button><el-button text type="danger" :disabled="!canWriteSchedule" :title="writePermissionHint" @click="removeMilestone(milestone)">删除</el-button></div>
            </article>
          </div>
          <div v-else class="empty">还没有里程碑。可创建“需求评审、Alpha 版本、测试验收、正式上线”等关键节点。</div>
        </div>

        <div v-show="activeSection === 'team'" class="panel">
          <div class="panel-heading"><div><p class="eyebrow">容量与投入</p><h2 title="计划剩余工时除以本周可用容量；超过 100% 表示可能超负荷">工时执行情况</h2></div><div><span class="panel-note">剩余工时 ÷ 本周可用容量</span><el-button v-if="canManageCapacity" size="small" @click="openCapacityException">配置请假 / 例外</el-button></div></div>
          <div class="workload-list">
            <div v-for="person in capacityRows" :key="person.userId" class="workload-row" :class="{ overloaded: person.overloaded }">
              <div class="workload-person"><span class="avatar">{{ (person.nickname || '成').slice(0, 1) }}</span><div><strong>{{ person.nickname }}</strong><small>{{ roleLabel(person.identity) }} · 本周 {{ person.availableHours }}h 可用</small></div></div>
              <div class="workload-hours"><strong>{{ person.utilizationPercent }}%</strong><span>{{ person.remainingHours }}h</span><small>负载 / 剩余计划</small></div>
              <div class="workload-meter"><i :style="{ width: Math.min(100, Number(person.utilizationPercent || 0)) + '%' }"></i></div>
              <div v-if="person.exceptions?.length" class="capacity-exceptions"><span v-for="item in person.exceptions" :key="item.id">{{ item.exceptionDate }} · {{ item.availableHours }}h {{ item.reason || '' }}<button v-if="canManageCapacity" aria-label="删除容量例外" @click="removeCapacityException(item)">×</button></span></div>
            </div>
            <div v-if="!capacityRows.length" class="empty">暂无项目成员容量数据。</div>
          </div>
        </div>
      </section>
    </main>

    <el-drawer v-model="riskActionVisible" title="风险处理" size="min(560px, 94vw)" append-to-body>
      <template v-if="selectedRisk">
        <div class="risk-drawer-heading"><span :class="selectedRisk.level.toLowerCase()">{{ riskLevelLabel(selectedRisk.level) }} · {{ selectedRisk.score }} 分</span><h2>{{ selectedRisk.title }}</h2><p>{{ selectedRisk.suggestion }}</p><el-button text @click="$router.push({ path: `/project/${projectId}`, query: { task: selectedRisk.taskId } })">打开关联任务 →</el-button></div>
        <div class="risk-action-form">
          <label>处理状态<el-select v-model="riskActionForm.status" @change="onRiskStatusChange"><el-option label="待处理" value="OPEN" /><el-option label="处理中" value="IN_PROGRESS" /><el-option label="接受风险" value="ACCEPTED" /><el-option label="已解决" value="RESOLVED" /></el-select></label>
          <label v-if="riskActionForm.status === 'IN_PROGRESS'">负责人<el-select v-model="riskActionForm.ownerUserId" filterable :disabled="!canManageSchedule" :title="canManageSchedule ? '' : '普通成员可以自行接取风险，但只有项目负责人或项目管理员可以重新分派'"><el-option v-for="member in members" :key="member.userId" :label="member.nickname || member.username" :value="member.userId" /></el-select><small v-if="!canManageSchedule" class="panel-note">普通成员接取后不能改派给他人</small></label>
          <label v-if="riskActionForm.status === 'IN_PROGRESS'" class="span-2">应对措施<el-input v-model="riskActionForm.responsePlan" type="textarea" :rows="4" maxlength="2000" show-word-limit /></label>
          <label v-if="riskActionForm.status === 'IN_PROGRESS'">处理期限<el-date-picker v-model="riskActionForm.dueDate" type="date" value-format="YYYY-MM-DD" /></label>
          <label v-if="riskActionForm.status === 'ACCEPTED'" class="span-2">接受原因<el-input v-model="riskActionForm.reason" type="textarea" :rows="3" maxlength="1000" show-word-limit /></label>
          <div class="span-2"><el-button type="primary" :loading="riskActionSaving" :disabled="!canWriteSchedule" :title="writePermissionHint" @click="saveRiskAction">保存处理记录</el-button></div>
        </div>
        <section class="risk-events"><header><strong>处理时间线</strong><span>{{ riskEvents.length }} 条记录</span></header><article v-for="event in riskEvents" :key="event.id"><i /><div><strong>{{ event.actorName || '系统' }} · {{ riskEventLabel(event.eventType) }}</strong><p v-if="event.note">{{ event.note }}</p><small>{{ formatDateTime(event.createdAt) }}<template v-if="event.fromStatus || event.toStatus"> · {{ riskActionLabel(event.fromStatus) }} → {{ riskActionLabel(event.toStatus) }}</template></small></div></article><p v-if="!riskEvents.length" class="empty">尚无处理事件。</p></section>
      </template>
    </el-drawer>

    <el-dialog v-model="templateVisible" title="保存项目模板" width="480px" :close-on-click-modal="false">
      <div class="form-grid"><label class="span-2">模板名称<el-input v-model="templateForm.name" maxlength="100" /></label><label class="span-2">说明<el-input v-model="templateForm.description" type="textarea" :rows="3" maxlength="500" /></label><label>可见范围<el-select v-model="templateForm.visibility"><el-option label="我的私有模板" value="PRIVATE" /><el-option v-if="userStore.systemRole === 'ADMIN'" label="系统模板" value="SYSTEM" /></el-select></label></div>
      <template #footer><el-button @click="templateVisible=false">取消</el-button><el-button type="primary" :loading="templateSaving" @click="saveAsTemplate">保存模板</el-button></template>
    </el-dialog>

    <el-dialog v-model="capacityExceptionVisible" title="配置容量例外" width="480px" :close-on-click-modal="false">
      <div class="form-grid">
        <label>项目成员<el-select v-model="capacityExceptionForm.userId" filterable><el-option v-for="member in members" :key="member.userId" :label="member.nickname || member.username" :value="member.userId" /></el-select></label>
        <label>日期<el-date-picker v-model="capacityExceptionForm.exceptionDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></label>
        <label>当日可用工时<el-input-number v-model="capacityExceptionForm.availableHours" :min="0" :max="24" :step="0.5" style="width:100%" /></label>
        <label class="span-2">原因<el-input v-model="capacityExceptionForm.reason" maxlength="500" placeholder="例如：请假、培训或临时支援" /></label>
      </div>
      <template #footer><el-button @click="capacityExceptionVisible=false">取消</el-button><el-button type="primary" :loading="capacityExceptionSaving" @click="saveCapacityException">保存例外</el-button></template>
    </el-dialog>

    <el-dialog v-model="milestoneVisible" :title="editingMilestone ? '编辑里程碑' : '新建里程碑'" width="520px" :close-on-click-modal="false">
      <div class="form-grid">
        <label>名称<el-input v-model="milestoneForm.name" placeholder="例如：Alpha 版本" /></label>
        <label>目标日期<el-date-picker v-model="milestoneForm.targetDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" /></label>
        <label>状态<el-select v-model="milestoneForm.status" style="width:100%"><el-option label="进行中" value="PLANNED" /><el-option label="已达成" value="COMPLETED" /></el-select></label>
        <label class="span-2">说明<el-input v-model="milestoneForm.description" type="textarea" :rows="2" placeholder="本阶段的目标和验收条件" /></label>
        <label class="span-2">关联任务<el-select v-model="milestoneForm.taskIds" multiple filterable placeholder="选择多个任务" style="width:100%"><el-option v-for="task in tasks" :key="task.id" :label="task.title" :value="task.id" /></el-select></label>
      </div>
      <template #footer><el-button @click="milestoneVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveMilestone">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="baselineVisible" title="保存计划基线" width="520px" :close-on-click-modal="false">
      <div class="baseline-dialog-copy"><strong>冻结当前推算排期</strong><p>基线创建后不可编辑。后续任务调整会以影子条和偏差天数与它对比。</p></div>
      <div class="form-grid">
        <label class="span-2">基线名称<el-input v-model="baselineForm.name" maxlength="100" placeholder="例如：需求确认版" /></label>
        <label class="span-2">说明 <span class="optional">可选</span><el-input v-model="baselineForm.description" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="记录本次计划的背景或评审结论" /></label>
      </div>
      <template #footer><el-button @click="baselineVisible = false">取消</el-button><el-button type="primary" :loading="baselineSaving" @click="saveBaseline">保存基线</el-button></template>
    </el-dialog>

    <el-drawer v-model="simulationVisible" title="What-if 排期模拟" size="min(640px, 94vw)" append-to-body>
      <div class="simulation-intro"><span>不写入任务</span><p>临时改变一个活动任务的开始日期或工期，查看下游级联、关键路径和风险变化。</p></div>
      <div class="simulation-form">
        <label>模拟任务<el-select v-model="simulationForm.taskId" filterable @change="syncSimulationTask"><el-option v-for="task in activeScheduleTasks" :key="task.taskId" :label="task.title" :value="task.taskId" /></el-select></label>
        <label>模拟开始日<el-date-picker v-model="simulationForm.startDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></label>
        <label>模拟工期（天）<el-input-number v-model="simulationForm.durationDays" :min="1" :max="3650" style="width:100%" /></label>
        <div class="simulation-form-actions"><el-button @click="resetSimulation">重置</el-button><el-button type="primary" :loading="simulationLoading" @click="runSimulation">运行模拟</el-button></div>
      </div>

      <template v-if="simulationResult">
        <div class="simulation-impact">
          <article :class="{ danger: simulationResult.projectFinishDeltaDays > 0 }"><span>项目完工变化</span><strong>{{ signedDays(simulationResult.projectFinishDeltaDays) }}</strong></article>
          <article><span>高风险任务</span><strong>{{ simulationResult.riskBefore.highCount }} → {{ simulationResult.riskAfter.highCount }}</strong></article>
          <article><span>中风险任务</span><strong>{{ simulationResult.riskBefore.mediumCount }} → {{ simulationResult.riskAfter.mediumCount }}</strong></article>
        </div>
        <div class="simulation-result-heading"><strong>受影响任务</strong><span>{{ simulationResult.changedTasks.length }} 项发生日期、关键性或风险变化</span></div>
        <div v-if="simulationResult.changedTasks.length" class="simulation-changes">
          <article v-for="change in simulationResult.changedTasks" :key="change.taskId">
            <header><strong>{{ change.title }}</strong><span :class="{ late: change.finishDeltaDays > 0 }">{{ signedDays(change.finishDeltaDays) }}</span></header>
            <p>{{ change.currentStartDate }}—{{ change.currentFinishDate }} <b>→</b> {{ change.simulatedStartDate }}—{{ change.simulatedFinishDate }}</p>
            <small>风险 {{ change.riskBefore ?? '—' }} → {{ change.riskAfter ?? '—' }}<template v-if="change.wasCritical !== change.critical"> · {{ change.critical ? '进入关键路径' : '离开关键路径' }}</template></small>
          </article>
        </div>
        <div v-else class="empty">本次输入没有改变项目排期。</div>
      </template>
    </el-drawer>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listProjects, listProjectMembers, listMilestones, createMilestone, updateMilestone, deleteMilestone } from '@/api/project'
import { listTasks } from '@/api/task'
import { getProjectRisks, generateAiRiskAnalysis, updateRiskAction, getRiskEvents } from '@/api/risk'
import { saveProjectTemplate } from '@/api/template'
import { submitAiFeedback } from '@/api/ai'
import { createScheduleBaseline, deleteScheduleBaseline, getProjectSchedule, listScheduleBaselines, simulateProjectSchedule } from '@/api/schedule'
import { downloadExecutiveSummaryPdf, emailExecutiveSummary, getExecutiveSummary } from '@/api/management'
import { addCapacityException, deleteCapacityException, getProjectCapacity } from '@/api/delivery'
import { useUserStore } from '@/store/user'
import AppShell from '@/components/AppShell.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import { riskLevelLabel } from '@/utils/permissions'
import { parseScheduleDate, scheduleBarStyle, varianceLabel } from '@/utils/schedule'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const projectId = Number(route.params.id)
const loading = ref(false)
const loadError = ref('')
const projectName = ref('项目')
const currentProject = ref(null)
const tasks = ref([])
const members = ref([])
const milestones = ref([])
const risks = ref({ highCount: 0, mediumCount: 0, lowCount: 0, risks: [], workloads: [], blockedEdges: [] })
const executive = ref({ health: { level: 'GREEN', reasons: [] }, schedule: {}, risks: {}, openDecisions: [], recentActivities: [], capacity: [] })
const schedule = ref({ tasks: [], warnings: [], criticalPathTaskIds: [], durationDays: 0, criticalTaskCount: 0, inferredTaskCount: 0 })
const baselines = ref([])
const selectedBaselineId = ref(null)
const aiRiskLoading = ref(false)
const aiRiskContent = ref('')
const aiOperationId = ref(null)
const aiRating = ref(0)
const md = new MarkdownIt({ breaks: true, linkify: true })
const renderedAiRisk = computed(() => md.render(aiRiskContent.value || ''))
const milestoneVisible = ref(false)
const saving = ref(false)
const editingMilestone = ref(null)
const milestoneForm = reactive({ name: '', description: '', targetDate: null, status: 'PLANNED', taskIds: [] })
const baselineVisible = ref(false)
const baselineSaving = ref(false)
const baselineForm = reactive({ name: '', description: '' })
const simulationVisible = ref(false)
const simulationLoading = ref(false)
const simulationResult = ref(null)
const simulationForm = reactive({ taskId: null, startDate: null, durationDays: 1 })
const managementSections = [{ value: 'overview', label: '概览' }, { value: 'risk', label: '风险' }, { value: 'schedule', label: '排期' }, { value: 'milestones', label: '里程碑' }, { value: 'team', label: '工时与容量' }]
const activeSection = ref(managementSections.some(item => item.value === route.query.section) ? route.query.section : 'overview')
const riskActionVisible = ref(false)
const riskActionSaving = ref(false)
const selectedRisk = ref(null)
const riskEvents = ref([])
const riskActionForm = reactive({ status: 'OPEN', ownerUserId: null, responsePlan: '', dueDate: '', reason: '' })
const templateVisible = ref(false)
const templateSaving = ref(false)
const templateForm = reactive({ name: '', description: '', visibility: 'PRIVATE' })
const capacityExceptionVisible = ref(false)
const capacityExceptionSaving = ref(false)
const capacityExceptionForm = reactive({ userId: null, exceptionDate: '', availableHours: 0, reason: '' })

const taskMap = computed(() => Object.fromEntries(tasks.value.map(task => [task.id, task])))
const overdueTasks = computed(() => tasks.value.filter(isOverdue))
const capacityRows = computed(() => executive.value.capacity || [])
const scheduleTasks = computed(() => (schedule.value.tasks || []).filter(task => task.changeType !== 'REMOVED' && task.plannedStartDate && task.plannedFinishDate))
const activeScheduleTasks = computed(() => scheduleTasks.value.filter(task => task.status !== 'DONE'))
const currentMember = computed(() => members.value.find(member => member.userId === userStore.userInfo?.userId))
const isProjectOwner = computed(() => currentProject.value?.creatorId === userStore.userInfo?.userId)
const canWriteSchedule = computed(() => isProjectOwner.value || (currentMember.value && currentMember.value.permission !== 'VIEWER'))
const canManageSchedule = computed(() => isProjectOwner.value || currentMember.value?.permission === 'PROJECT_ADMIN')
const canManageCapacity = computed(() => canManageSchedule.value || currentMember.value?.identity === 'PROJECT_MANAGER')
const writePermissionHint = computed(() => canWriteSchedule.value ? '' : '只读成员只能查看项目内容')
const criticalPathLabel = computed(() => {
  const byId = Object.fromEntries(scheduleTasks.value.map(task => [task.taskId, task.title]))
  const names = (schedule.value.criticalPathTaskIds || []).map(id => byId[id]).filter(Boolean)
  return names.length ? names.join(' → ') : '暂无关键路径'
})
const timelineStartDate = computed(() => schedule.value.projectStartDate || new Date().toISOString().slice(0, 10))
const timelineDays = computed(() => Math.max(14, schedule.value.durationDays || 14))
const timelineWidth = computed(() => Math.max(720, Math.min(12000, timelineDays.value * 22)))
const timelineTrackStyle = computed(() => ({ width: `${timelineWidth.value}px`, '--timeline-days': timelineDays.value }))
const timelineRowStyle = computed(() => ({ '--timeline-width': `${timelineWidth.value}px` }))
const timelineLabels = computed(() => {
  const interval = Math.max(1, Math.ceil(timelineDays.value / 10))
  return Array.from({ length: timelineDays.value }, (_, index) => {
    const date = parseScheduleDate(timelineStartDate.value); date.setDate(date.getDate() + index)
    return { date: date.toISOString(), label: index % interval === 0 ? `${date.getMonth() + 1}/${date.getDate()}` : '' }
  })
})
const workload = computed(() => {
  const people = new Map(members.value.map(member => [member.userId, { id: member.userId, name: member.nickname || member.username, role: roleLabel(member.identity), estimated: 0, actual: 0, active: 0 }]))
  tasks.value.filter(task => task.assigneeId).forEach(task => {
    const person = people.get(task.assigneeId) || { id: task.assigneeId, name: '项目成员', role: '未设置岗位', estimated: 0, actual: 0, active: 0 }
    person.estimated += task.estimatedHours || 0
    person.actual += task.actualHours || 0
    if (task.status === 'IN_PROGRESS') person.active++
    people.set(task.assigneeId, person)
  })
  return [...people.values()].filter(person => person.estimated || person.actual || person.active)
})

function isOverdue(task) { return task.status !== 'DONE' && task.dueDate && parseScheduleDate(task.dueDate) < new Date(new Date().toDateString()) }
function ganttBarStyle(start, finish) { return scheduleBarStyle(start, finish, timelineStartDate.value, timelineDays.value) }
function signedDays(value) { return value == null ? '—' : `${value > 0 ? '+' : ''}${value} 天` }
function milestoneTasks(milestone) { return String(milestone.taskIds || '').split(',').filter(Boolean).map(id => taskMap.value[id]).filter(Boolean) }
function milestoneDoneCount(milestone) { return milestoneTasks(milestone).filter(task => task.status === 'DONE').length }
function workloadPercent(person) { return Math.min(100, person.estimated ? Math.round(person.actual / person.estimated * 100) : 0) }
function roleLabel(role) { return ({ PROJECT_MANAGER: '项目经理', PRODUCT_MANAGER: '产品经理', FRONTEND_DEV: '前端', BACKEND_DEV: '后端', QA_TESTER: '测试', UI_DESIGNER: 'UI 设计' })[role] || '未设置岗位' }
function healthLabel(level) { return ({ GREEN: '健康', AMBER: '关注', RED: '告警' })[level] || '计算中' }
function setSection(value) { activeSection.value = value; router.replace({ query: { ...route.query, section: value } }) }
function riskActionLabel(value) { return ({ OPEN: '待处理', IN_PROGRESS: '处理中', ACCEPTED: '已接受', RESOLVED: '已解决' })[value] || '待处理' }
function riskEventLabel(value) { return ({ ACTION_CREATED: '创建处理记录', ACTION_UPDATED: '更新处理记录', AUTO_STATUS_CHANGED: '系统自动更新状态' })[value] || value }
function formatDateTime(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '' }

async function openRiskAction(risk) {
  selectedRisk.value = risk
  Object.assign(riskActionForm, { status: risk.actionStatus || 'OPEN', ownerUserId: risk.riskOwnerId || null, responsePlan: risk.responsePlan || '', dueDate: risk.actionDueDate || '', reason: risk.actionReason || '' })
  riskActionVisible.value = true
  try { riskEvents.value = (await getRiskEvents(projectId, risk.taskId)).data?.data || [] } catch { riskEvents.value = [] }
}

function onRiskStatusChange(status) {
  if (status === 'IN_PROGRESS' && !canManageSchedule.value && !riskActionForm.ownerUserId) {
    riskActionForm.ownerUserId = userStore.userInfo?.userId || null
  }
}

async function saveRiskAction() {
  if (!canWriteSchedule.value) return ElMessage.warning(writePermissionHint.value)
  if (riskActionForm.status === 'IN_PROGRESS' && (!riskActionForm.ownerUserId || !riskActionForm.responsePlan.trim() || !riskActionForm.dueDate)) return ElMessage.warning('处理中风险需要填写负责人、应对措施和处理期限')
  if (riskActionForm.status === 'ACCEPTED' && !riskActionForm.reason.trim()) return ElMessage.warning('请填写接受风险的原因')
  riskActionSaving.value = true
  try {
    await updateRiskAction(projectId, selectedRisk.value.taskId, { ...riskActionForm, dueDate: riskActionForm.dueDate || '' })
    ElMessage.success('风险处理记录已保存')
    await loadScheduleContext()
    const refreshed = (risks.value.risks || []).find(item => item.taskId === selectedRisk.value.taskId)
    if (refreshed) selectedRisk.value = refreshed
    riskEvents.value = (await getRiskEvents(projectId, selectedRisk.value.taskId)).data?.data || []
  } finally { riskActionSaving.value = false }
}

async function saveAsTemplate() {
  if (!templateForm.name.trim()) return ElMessage.warning('请输入模板名称')
  templateSaving.value = true
  try {
    await saveProjectTemplate(projectId, { ...templateForm, name: templateForm.name.trim() })
    ElMessage.success('项目模板已保存，可在工作台新建项目时使用')
    templateVisible.value = false
    Object.assign(templateForm, { name: '', description: '', visibility: 'PRIVATE' })
  } finally { templateSaving.value = false }
}

function openCapacityException() {
  Object.assign(capacityExceptionForm, { userId: members.value[0]?.userId || null, exceptionDate: new Date().toISOString().slice(0, 10), availableHours: 0, reason: '' })
  capacityExceptionVisible.value = true
}
async function refreshCapacity() { executive.value.capacity = (await getProjectCapacity(projectId)).data.data || [] }
async function saveCapacityException() {
  if (!capacityExceptionForm.userId || !capacityExceptionForm.exceptionDate) return ElMessage.warning('请选择成员和日期')
  capacityExceptionSaving.value = true
  try { await addCapacityException(projectId, capacityExceptionForm); await refreshCapacity(); capacityExceptionVisible.value=false; ElMessage.success('容量例外已保存') }
  finally { capacityExceptionSaving.value=false }
}
async function removeCapacityException(item) {
  try { await ElMessageBox.confirm(`确认删除 ${item.exceptionDate} 的容量例外？删除后团队负载会立即重新计算。`,'删除容量例外',{type:'warning'}) } catch { return }
  try { await deleteCapacityException(projectId,item.id); await refreshCapacity(); ElMessage.success('容量例外已删除，团队负载已更新') } catch { /* 请求错误由统一反馈层显示 */ }
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [projectRes, taskRes, memberRes, milestoneRes, baselineRes] = await Promise.all([
      listProjects(), listTasks(projectId), listProjectMembers(projectId), listMilestones(projectId), listScheduleBaselines(projectId)
    ])
    currentProject.value = projectRes.data.data.find(project => project.id === projectId) || null
    projectName.value = currentProject.value?.name || '项目'
    tasks.value = taskRes.data.data || []
    members.value = memberRes.data.data || []
    milestones.value = milestoneRes.data.data || []
    baselines.value = baselineRes.data.data || []
    if (!baselines.value.some(item => item.id === selectedBaselineId.value)) selectedBaselineId.value = baselines.value[0]?.id || null
    await Promise.all([loadScheduleContext(), loadExecutive()])
  } catch (error) {
    loadError.value = error.message || '暂时无法连接服务器，请检查网络或稍后重试'
  } finally { loading.value = false }
}
async function loadExecutive() {
  try { executive.value = (await getExecutiveSummary(projectId)).data.data || executive.value }
  catch { /* 概览降级时不影响其他管理模块 */ }
}
async function downloadSummaryPdf() {
  const response = await downloadExecutiveSummaryPdf(projectId)
  const url = URL.createObjectURL(new Blob([response.data], { type:'application/pdf' })); const link=document.createElement('a'); link.href=url; link.download=`${projectName.value}-项目简报.pdf`; link.click(); URL.revokeObjectURL(url)
}
async function sendSummaryEmail() {
  const recipients = members.value.map(item => item.userId)
  if (!recipients.length) return ElMessage.warning('当前项目没有可发送的成员')
  try { await ElMessageBox.confirm('简报只会发给已验证邮箱的项目成员，且同一成员当天不会重复入队。','发送项目周报',{type:'warning'}) } catch { return }
  try { const response=await emailExecutiveSummary(projectId,recipients); ElMessage.success(`已将 ${response.data.data.queued} 封邮件加入发送队列`) } catch { /* 请求错误由统一反馈层显示 */ }
}
async function loadScheduleContext() {
  const [scheduleRes, riskRes] = await Promise.all([
    getProjectSchedule(projectId, selectedBaselineId.value), getProjectRisks(projectId, selectedBaselineId.value)
  ])
  schedule.value = scheduleRes.data.data || schedule.value
  risks.value = riskRes.data.data || risks.value
}
async function analyzeRisks() {
  aiRiskLoading.value = true
  try {
    const response = await generateAiRiskAnalysis(projectId)
    aiRiskContent.value = response.data.data || ''
    aiOperationId.value = Number(response.headers['x-ai-operation-id']) || null
    aiRating.value = 0
  } finally { aiRiskLoading.value = false }
}
async function rateAiSuggestion(rating) {
  if (!aiOperationId.value || !rating) return
  await submitAiFeedback(aiOperationId.value, rating)
  ElMessage.success('评价已记录')
}
async function saveBaseline() {
  if (!baselineForm.name.trim()) return ElMessage.warning('请输入基线名称')
  baselineSaving.value = true
  try {
    const response = await createScheduleBaseline(projectId, { name: baselineForm.name.trim(), description: baselineForm.description.trim() })
    baselineVisible.value = false
    Object.assign(baselineForm, { name: '', description: '' })
    const baselineRes = await listScheduleBaselines(projectId)
    baselines.value = baselineRes.data.data || []
    selectedBaselineId.value = response.data.data.id
    await loadScheduleContext()
    ElMessage.success('计划基线已保存')
  } finally { baselineSaving.value = false }
}
async function removeBaseline(baseline) {
  if (!canManageSchedule.value) return ElMessage.warning('只有项目负责人或项目管理员可以删除计划基线')
  try { await ElMessageBox.confirm(`基线“${baseline.name}”是不可变审计快照。删除后无法恢复，但不会修改任何任务或实际排期。`, '永久删除计划基线', { type: 'error', confirmButtonText: '永久删除', cancelButtonText: '取消' }) }
  catch { return }
  try {
    await deleteScheduleBaseline(projectId, baseline.id)
    const response = await listScheduleBaselines(projectId)
    baselines.value = response.data.data || []
    selectedBaselineId.value = baselines.value[0]?.id || null
    await loadScheduleContext()
    ElMessage.success('计划基线已删除')
  } catch { /* 请求错误由统一反馈层显示 */ }
}
async function selectBaseline(id) { selectedBaselineId.value = id; await loadScheduleContext() }
function openSimulation() { simulationVisible.value = true; resetSimulation() }
function syncSimulationTask() {
  const task = activeScheduleTasks.value.find(item => item.taskId === simulationForm.taskId)
  if (!task) return
  simulationForm.startDate = task.plannedStartDate
  simulationForm.durationDays = task.durationDays
  simulationResult.value = null
}
function resetSimulation() {
  simulationResult.value = null
  simulationForm.taskId = activeScheduleTasks.value[0]?.taskId || null
  syncSimulationTask()
}
async function runSimulation() {
  if (!simulationForm.taskId) return ElMessage.warning('请选择模拟任务')
  simulationLoading.value = true
  try {
    const response = await simulateProjectSchedule(projectId, {
      baselineId: selectedBaselineId.value || null,
      taskId: simulationForm.taskId,
      startDate: simulationForm.startDate,
      durationDays: simulationForm.durationDays
    })
    simulationResult.value = response.data.data
  } finally { simulationLoading.value = false }
}
function openMilestone(milestone) {
  if (!canWriteSchedule.value) return ElMessage.warning(writePermissionHint.value)
  editingMilestone.value = milestone || null
  Object.assign(milestoneForm, milestone ? { name: milestone.name, description: milestone.description || '', targetDate: milestone.targetDate, status: milestone.status, taskIds: String(milestone.taskIds || '').split(',').filter(Boolean).map(Number) } : { name: '', description: '', targetDate: null, status: 'PLANNED', taskIds: [] })
  milestoneVisible.value = true
}
async function saveMilestone() {
  if (!milestoneForm.name.trim()) return ElMessage.warning('请输入里程碑名称')
  saving.value = true
  try {
    const payload = { ...(editingMilestone.value ? { id: editingMilestone.value.id } : {}), ...milestoneForm, taskIds: milestoneForm.taskIds.join(',') }
    if (editingMilestone.value) await updateMilestone(projectId, payload)
    else await createMilestone(projectId, payload)
    milestoneVisible.value = false; ElMessage.success('里程碑已保存'); await load()
  } finally { saving.value = false }
}
async function removeMilestone(milestone) {
  if (!canWriteSchedule.value) return ElMessage.warning(writePermissionHint.value)
  try { await ElMessageBox.confirm(`确认删除里程碑“${milestone.name}”？关联任务不会被删除。`, '删除里程碑', { type: 'warning', confirmButtonText: '删除里程碑', cancelButtonText: '取消' }) }
  catch { return }
  try {
    await deleteMilestone(projectId, milestone.id); ElMessage.success('里程碑已删除，关联任务保持不变'); await load()
  } catch { /* 请求错误由统一反馈层显示 */ }
}
onMounted(load)
</script>

<style scoped media="not all">
.management-page { min-height: 100vh; color: var(--text-primary); }
.topbar { height: 58px; display:flex; align-items:center; justify-content:space-between; padding:0 28px; border-bottom:1px solid var(--border-light); background:rgba(255,255,255,.82); }
.topbar-left { display:flex; align-items:center; gap:10px; }.topbar h3 { margin:0; font-size:16px; }.sep { color:var(--text-tertiary); }.management-main { max-width:1280px; margin:0 auto; padding:30px; }
.summary-grid { display:grid; grid-template-columns:repeat(4,1fr); gap:16px; margin-bottom:20px; }.summary-card,.panel { background:rgba(255,255,255,.92); border:1px solid var(--border-light); border-radius:14px; box-shadow:0 8px 24px rgba(15,23,42,.04); }.summary-card { padding:18px 20px; }.summary-card span,.summary-card small { color:var(--text-tertiary); font-size:12px; }.summary-card strong { display:block; margin-top:8px; font-size:28px; }.summary-card.danger strong { color:#dc5a3d; }
.panel { padding:22px; }.panel-heading { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:18px; }.eyebrow { margin:0 0 4px; color:#b47725; font-size:11px; font-weight:700; letter-spacing:.08em; text-transform:uppercase; }.panel h2 { margin:0; font-size:18px; }.panel-note { margin:5px 0 0; color:var(--text-tertiary); font-size:12px; }.gantt-wrap { overflow-x:auto; }.gantt-head,.gantt-row { display:grid; grid-template-columns:220px minmax(620px,1fr); gap:14px; }.gantt-head { color:var(--text-tertiary); font-size:12px; border-bottom:1px solid var(--border-light); padding-bottom:8px; }.gantt-days { display:grid; grid-template-columns:repeat(14,1fr); }.gantt-row { min-height:58px; align-items:center; border-bottom:1px solid rgba(148,163,184,.13); }.gantt-task-name { min-width:0; }.gantt-task-name strong { display:block; overflow:hidden; white-space:nowrap; text-overflow:ellipsis; font-size:13px; }.gantt-task-name small { color:#b45309; font-size:11px; }.gantt-track { position:relative; height:28px; border-radius:7px; background:repeating-linear-gradient(90deg,transparent,transparent calc(7.14% - 1px),rgba(148,163,184,.15) calc(7.14% - 1px),rgba(148,163,184,.15) 7.14%); }.gantt-bar { position:absolute; top:4px; min-width:34px; height:20px; border-radius:5px; padding:2px 6px; box-sizing:border-box; overflow:hidden; white-space:nowrap; color:white; background:#b47725; font-size:10px; }.gantt-bar.done { background:#4f8a6b; }.gantt-bar.overdue { background:#d55a45; }.two-column { display:grid; grid-template-columns:1.1fr .9fr; gap:20px; margin-top:20px; }.milestone-card { display:flex; gap:14px; padding:14px 0; border-bottom:1px solid var(--border-light); }.milestone-date { min-width:72px; color:#b47725; font-weight:650; font-size:12px; }.milestone-content { flex:1; }.milestone-title { display:flex; gap:8px; align-items:center; }.milestone-title h3 { margin:0; font-size:14px; }.milestone-title span { padding:2px 7px; border-radius:9px; font-size:10px; }.planned { background:#fdf1db; color:#a66711; }.complete { background:#e4f3e9; color:#287248; }.milestone-content p { margin:6px 0; color:var(--text-secondary); font-size:12px; }.milestone-content small { color:var(--text-tertiary); }.milestone-actions { display:flex; }.workload-row { display:grid; grid-template-columns:1fr auto; gap:8px 12px; padding:12px 0; border-bottom:1px solid var(--border-light); }.workload-person { display:flex; align-items:center; gap:9px; }.avatar { width:30px; height:30px; display:grid; place-items:center; border-radius:50%; background:#f3e8d3; color:#9a631d; font-size:13px; }.workload-person strong,.workload-hours strong { font-size:13px; }.workload-person small,.workload-hours small { display:block; color:var(--text-tertiary); font-size:11px; }.workload-hours { text-align:right; }.workload-hours span { color:var(--text-tertiary); font-size:12px; }.workload-meter { grid-column:1 / -1; height:4px; border-radius:3px; overflow:hidden; background:#edf0f4; }.workload-meter i { display:block; height:100%; background:#b47725; }.empty { padding:28px 8px; color:var(--text-tertiary); text-align:center; font-size:13px; }.form-grid { display:grid; grid-template-columns:1fr 1fr; gap:14px; }.form-grid label { display:block; color:var(--text-secondary); font-size:12px; }.form-grid :deep(.el-input),.form-grid :deep(.el-select),.form-grid :deep(.el-date-editor) { margin-top:6px; }.span-2 { grid-column:span 2; }
@media (max-width:800px) { .management-main{padding:16px}.summary-grid,.two-column{grid-template-columns:1fr 1fr}.two-column{display:block}.two-column .panel+ .panel{margin-top:16px}.gantt-head,.gantt-row{grid-template-columns:130px minmax(620px,1fr)} }
</style>

<style scoped>
.management-main { max-width:1280px; margin:0 auto; padding:34px 36px 52px; color:var(--text-primary); }
.management-tabs { display:flex; gap:4px; margin:4px 0 22px; padding:5px; overflow-x:auto; border:1px solid var(--border-light); border-radius:11px; background:var(--surface); }.management-tabs button { min-width:88px; padding:9px 14px; border:0; border-radius:7px; color:var(--text-secondary); background:transparent; font-size:12px; font-weight:650; cursor:pointer; white-space:nowrap; }.management-tabs button:hover { color:var(--text-primary); background:var(--surface-subtle); }.management-tabs button.active { color:var(--brand-deep); background:var(--brand-soft); }
.summary-grid { display:grid; grid-template-columns:repeat(4,1fr); margin:24px 0 18px; border:1px solid var(--border); border-radius:12px; background:var(--surface); box-shadow:var(--shadow-xs); }
.summary-card { min-width:0; padding:19px 22px; }.summary-card + .summary-card { border-left:1px solid var(--border-light); }.summary-card span { color:var(--text-tertiary); font-size:12px; font-weight:650; }.summary-card strong { display:block; margin-top:7px; font-size:29px; line-height:1; font-variant-numeric:tabular-nums; }.summary-card.danger strong { color:var(--danger); }
.health-hero { display:grid; grid-template-columns:180px 1fr 220px; align-items:center; gap:24px; margin-top:24px; padding:24px; overflow:hidden; border-radius:14px; color:#f8faff; background:#214f42; box-shadow:var(--shadow-sm); }.health-hero.amber{background:#73521f}.health-hero.red{background:#713a3d}.health-grade{display:grid;gap:6px;padding-right:24px;border-right:1px solid rgba(255,255,255,.18)}.health-grade span{font-size:9px;font-weight:800;letter-spacing:.15em;opacity:.7}.health-grade strong{font-size:29px;letter-spacing:-.04em}.health-copy h2{margin:0 0 7px;font-size:17px}.health-copy p{display:inline;margin:0 10px 0 0;color:rgba(255,255,255,.75);font-size:10px}.health-finish{display:grid;gap:4px;text-align:right}.health-finish span,.health-finish small{font-size:10px;opacity:.7}.health-finish strong{font:750 21px var(--font-mono)}
.decision-metrics{grid-template-columns:repeat(6,1fr)}.decision-metrics .summary-card:nth-child(n+2){border-left:1px solid var(--border-light)}.overview-columns{display:grid;grid-template-columns:1fr 1fr;gap:16px}.decision-list,.activity-list{display:grid}.decision-list button{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:11px 0;border:0;border-bottom:1px solid var(--border-light);color:var(--text-primary);background:transparent;text-align:left;cursor:pointer}.decision-list button span{color:var(--text-tertiary);font-size:10px}.activity-list>span{display:grid;grid-template-columns:8px 1fr;gap:9px;padding:8px 0}.activity-list i{width:6px;height:6px;margin-top:5px;border-radius:50%;background:var(--brand)}.activity-list div{color:var(--text-secondary);font-size:11px}.activity-list small{display:block;color:var(--text-tertiary);font-size:9px}.workload-row.overloaded .workload-meter i{background:var(--danger)}.workload-row.overloaded .workload-hours strong{color:var(--danger)}
.capacity-exceptions{display:flex;grid-column:1/-1;flex-wrap:wrap;gap:6px}.capacity-exceptions span{display:flex;align-items:center;gap:5px;padding:4px 7px;border-radius:7px;color:var(--text-secondary);background:var(--surface-strong);font-size:10px}.capacity-exceptions button{padding:0;border:0;color:var(--danger);background:transparent;cursor:pointer}.panel-heading>div:last-child:not(:first-child){display:flex;align-items:center;gap:10px}
.panel { min-width:0; padding:22px; border:1px solid var(--border); border-radius:12px; background:var(--surface); box-shadow:var(--shadow-xs); }.panel-heading { display:flex; justify-content:space-between; align-items:flex-start; gap:16px; margin-bottom:18px; }.eyebrow { margin:0 0 4px; color:var(--brand); font-size:11px; font-weight:700; }.panel h2 { margin:0; font-size:17px; }.panel-note { margin:4px 0 0; color:var(--text-tertiary); font-size:12px; }
.schedule-actions { display:flex; flex-wrap:wrap; justify-content:flex-end; gap:8px; }.schedule-actions .el-select { width:180px; }.schedule-metrics { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); margin-bottom:16px; border:1px solid var(--border-light); border-radius:10px; background:var(--surface-subtle); }.schedule-metrics article { min-width:0; padding:15px 17px; }.schedule-metrics article+article { border-left:1px solid var(--border-light); }.schedule-metrics span,.schedule-metrics small { display:block; color:var(--text-tertiary); font-size:10px; }.schedule-metrics strong { display:block; overflow:hidden; margin:5px 0 4px; font:720 18px/1.15 var(--font-mono); text-overflow:ellipsis; white-space:nowrap; }.schedule-metrics .delayed strong { color:var(--danger); }.schedule-warnings { display:grid; gap:5px; margin-bottom:14px; padding:12px 14px; border-left:3px solid var(--warning); border-radius:8px; color:var(--text-secondary); background:var(--warning-soft); font-size:11px; }.schedule-warnings strong { color:var(--warning); }.gantt-wrap { max-width:100%; overflow-x:auto; overscroll-behavior-inline:contain; }.gantt-head,.gantt-row { display:grid; grid-template-columns:220px var(--timeline-width,680px); gap:14px; width:max-content; min-width:914px; }.gantt-head { position:sticky; top:0; z-index:2; padding-bottom:8px; border-bottom:1px solid var(--border-light); color:var(--text-tertiary); background:var(--surface); font-size:12px; }.gantt-days { display:grid; grid-template-columns:repeat(var(--timeline-days),1fr); }.gantt-days span { padding-left:3px; border-left:1px solid var(--border-light); font:500 9px/1.3 var(--font-mono); }.gantt-row { min-height:62px; align-items:center; border-bottom:1px solid var(--border-light); }.gantt-row.critical-row { background:linear-gradient(90deg,var(--danger-bg),transparent 260px); }.gantt-task-name { position:sticky; left:0; z-index:1; min-width:0; padding:10px 8px 10px 0; background:inherit; }.gantt-task-name>div { display:flex; align-items:center; gap:7px; }.gantt-task-name strong { display:block; overflow:hidden; font-size:13px; text-overflow:ellipsis; white-space:nowrap; }.gantt-task-name em { flex:0 0 auto; padding:2px 6px; border-radius:999px; color:var(--text-tertiary); background:var(--surface-strong); font-size:9px; font-style:normal; }.critical-row .gantt-task-name em { color:var(--danger); background:var(--danger-bg); }.gantt-task-name small { display:block; overflow:hidden; margin-top:4px; color:var(--text-tertiary); font-size:10px; text-overflow:ellipsis; white-space:nowrap; }.gantt-track { position:relative; height:38px; border-radius:7px; background:repeating-linear-gradient(90deg,transparent,transparent calc(154px - 1px),var(--border-light) calc(154px - 1px),var(--border-light) 154px); }.baseline-bar { position:absolute; top:5px; height:8px; min-width:5px; border:1px dashed var(--border-strong); border-radius:3px; background:#cbd3df; opacity:.9; }.gantt-bar { position:absolute; top:16px; box-sizing:border-box; min-width:10px; height:17px; overflow:hidden; padding:1px 6px; border-radius:4px; color:white; background:var(--brand); font-size:9px; white-space:nowrap; box-shadow:var(--shadow-xs); }.gantt-bar.done { background:var(--success); }.gantt-bar.critical { background:var(--danger); box-shadow:0 0 0 2px rgba(189,71,71,.14); }.baseline-ledger { display:grid; gap:12px; margin-top:18px; padding-top:16px; border-top:1px solid var(--border-light); }.baseline-ledger>div:first-child { display:flex; align-items:baseline; gap:10px; }.baseline-ledger strong { font-size:12px; }.baseline-ledger span { color:var(--text-tertiary); font-size:11px; }.baseline-chips { display:flex; gap:8px; overflow-x:auto; padding-bottom:3px; }.baseline-chips button { position:relative; display:grid; flex:0 0 auto; gap:2px; min-width:150px; padding:9px 28px 9px 10px; border:1px solid var(--border); border-radius:8px; color:var(--text-primary); background:var(--surface); text-align:left; cursor:pointer; }.baseline-chips button.active { border-color:var(--brand); background:var(--brand-soft); box-shadow:0 0 0 2px var(--focus-ring); }.baseline-chips button span { color:var(--text-primary); font-size:11px; font-weight:650; }.baseline-chips button small { color:var(--text-tertiary); font-size:9px; }.baseline-chips button i { position:absolute; top:7px; right:7px; display:grid; place-items:center; width:18px; height:18px; border-radius:50%; color:var(--text-tertiary); font-style:normal; }.baseline-chips button i:hover { color:var(--danger); background:var(--danger-bg); }.baseline-dialog-copy { margin-bottom:18px; padding:13px 14px; border-radius:9px; background:var(--brand-soft); }.baseline-dialog-copy strong { font-size:13px; }.baseline-dialog-copy p { margin:4px 0 0; color:var(--text-secondary); font-size:11px; line-height:1.6; }.simulation-intro { margin-bottom:18px; padding:14px 16px; border-left:3px solid var(--brand); border-radius:8px; background:var(--brand-soft); }.simulation-intro span { color:var(--brand-deep); font-size:10px; font-weight:700; }.simulation-intro p { margin:4px 0 0; color:var(--text-secondary); font-size:12px; line-height:1.6; }.simulation-form { display:grid; grid-template-columns:1fr 1fr; gap:13px; }.simulation-form label { display:grid; gap:6px; color:var(--text-secondary); font-size:11px; }.simulation-form label:first-child { grid-column:1/-1; }.simulation-form-actions { grid-column:1/-1; display:flex; justify-content:flex-end; gap:8px; padding-top:4px; }.simulation-result { margin-top:22px; }.simulation-summary { display:grid; grid-template-columns:repeat(3,1fr); border:1px solid var(--border-light); border-radius:9px; background:var(--surface-subtle); }.simulation-summary div { padding:12px; }.simulation-summary div+div { border-left:1px solid var(--border-light); }.simulation-summary span,.simulation-summary small { display:block; color:var(--text-tertiary); font-size:9px; }.simulation-summary strong { display:block; margin:4px 0; font:720 18px/1 var(--font-mono); }.simulation-summary strong.danger { color:var(--danger); }.simulation-changes { display:grid; gap:8px; margin-top:14px; }.simulation-changes article { display:grid; grid-template-columns:minmax(0,1fr) auto; gap:5px 12px; padding:11px 13px; border:1px solid var(--border-light); border-radius:8px; }.simulation-changes strong { overflow:hidden; font-size:12px; text-overflow:ellipsis; white-space:nowrap; }.simulation-changes span { color:var(--danger); font:650 11px/1 var(--font-mono); }.simulation-changes small { grid-column:1/-1; color:var(--text-tertiary); font-size:10px; }
.two-column { display:grid; grid-template-columns:minmax(0,1.1fr) minmax(0,.9fr); gap:18px; margin-top:18px; }.two-column.single { grid-template-columns:1fr; }.milestone-card { display:flex; gap:14px; padding:14px 0; border-bottom:1px solid var(--border-light); }.milestone-date { min-width:72px; color:var(--brand-deep); font-size:12px; font-weight:650; }.milestone-content { min-width:0; flex:1; }.milestone-title { display:flex; align-items:center; gap:8px; }.milestone-title h3 { margin:0; font-size:14px; }.milestone-title span { padding:2px 7px; border-radius:999px; font-size:10px; }.planned { background:var(--warning-soft); color:var(--warning); }.complete { background:var(--success-soft); color:var(--success); }.milestone-content p { margin:6px 0; color:var(--text-secondary); font-size:12px; }.milestone-content small { color:var(--text-tertiary); }.milestone-actions { display:flex; }
.workload-row { display:grid; grid-template-columns:1fr auto; gap:8px 12px; padding:12px 0; border-bottom:1px solid var(--border-light); }.workload-person { display:flex; align-items:center; gap:9px; }.avatar { display:grid; place-items:center; width:30px; height:30px; border-radius:50%; color:var(--brand-deep); background:var(--brand-light); font-size:13px; }.workload-person strong,.workload-hours strong { font-size:13px; }.workload-person small,.workload-hours small { display:block; color:var(--text-tertiary); font-size:11px; }.workload-hours { text-align:right; }.workload-hours span { color:var(--text-tertiary); font-size:12px; }.workload-meter { grid-column:1 / -1; height:4px; overflow:hidden; border-radius:3px; background:var(--surface-strong); }.workload-meter i { display:block; height:100%; background:var(--brand); }.empty { padding:28px 8px; color:var(--text-tertiary); text-align:center; font-size:13px; }.form-grid { display:grid; grid-template-columns:1fr 1fr; gap:14px; }.form-grid label { display:block; color:var(--text-secondary); font-size:12px; }.form-grid :deep(.el-input),.form-grid :deep(.el-select),.form-grid :deep(.el-date-editor) { margin-top:6px; }.span-2 { grid-column:span 2; }
.risk-panel { margin-bottom:18px; overflow:hidden; }.risk-summary { display:grid; grid-template-columns:repeat(3,1fr); margin-bottom:18px; border:1px solid var(--border-light); border-radius:10px; background:var(--surface-subtle); }.risk-summary>div { padding:13px 16px; border-left:3px solid var(--border); }.risk-summary>div+div { border-left-width:1px; }.risk-summary span { color:var(--text-tertiary); font-size:11px; }.risk-summary strong { display:block; margin-top:3px; font-size:23px; }.risk-summary .high { border-left-color:var(--danger); }.risk-summary .high strong { color:var(--danger); }.risk-summary .medium strong { color:var(--warning); }.risk-summary .low strong { color:var(--success); }.risk-layout { display:grid; grid-template-columns:minmax(0,1.55fr) minmax(260px,.45fr); gap:18px; }.risk-list { display:grid; gap:8px; }.risk-row { display:grid; grid-template-columns:58px minmax(0,1fr) auto; align-items:stretch; border:1px solid var(--border-light); border-left:4px solid var(--success); border-radius:9px; background:var(--surface); cursor:pointer; }.risk-row:hover { box-shadow:var(--shadow-sm); }.risk-row.medium { border-left-color:var(--warning); }.risk-row.high { border-left-color:var(--danger); }.risk-score { display:grid; place-content:center; padding:12px; border-right:1px solid var(--border-light); text-align:center; }.risk-score strong { font:750 22px/1 var(--font-mono); }.risk-score span { margin-top:4px; color:var(--text-tertiary); font-size:9px; }.risk-copy { min-width:0; padding:11px 13px; }.risk-copy header { display:flex; align-items:center; gap:8px; }.risk-copy h3 { margin:0; overflow:hidden; font-size:13px; text-overflow:ellipsis; white-space:nowrap; }.risk-copy header span { padding:2px 7px; border-radius:999px; color:var(--text-secondary); background:var(--surface-strong); font-size:10px; }.risk-copy p { margin:5px 0; color:var(--text-secondary); font-size:11px; }.risk-copy small { display:inline-block; margin:2px 5px 0 0; color:var(--text-tertiary); font-size:10px; }.risk-action-meta { display:flex!important; flex-wrap:wrap; align-items:center; gap:8px; margin:8px 0 2px; }.risk-action-meta b { padding:3px 7px; border-radius:999px; color:var(--brand-deep); background:var(--brand-soft); font-size:10px; }.risk-action-meta span { color:var(--text-tertiary); font-size:10px; }.risk-open { align-self:center; padding-right:13px; color:var(--brand); font-size:11px; white-space:nowrap; }.risk-side { padding:15px; border-radius:10px; background:var(--surface-subtle); }.risk-side h3 { margin:0 0 12px; font-size:13px; }.blocked-edge { display:grid; grid-template-columns:1fr auto 1fr; align-items:center; gap:6px; padding:9px 0; border-bottom:1px solid var(--border-light); font-size:10px; }.blocked-edge strong { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.blocked-edge span { color:var(--danger); }.ai-risk-result { margin-top:18px; padding:17px; border:1px solid #b9c8f5; border-radius:10px; background:linear-gradient(135deg,var(--brand-light),var(--surface) 72%); }.ai-risk-heading { display:flex; justify-content:space-between; gap:12px; }.ai-risk-heading>div { display:flex; align-items:center; gap:8px; color:var(--text-tertiary); font-size:11px; }.markdown-body { margin-top:12px; color:var(--text-secondary); font-size:12px; line-height:1.75; }.markdown-body :deep(h2),.markdown-body :deep(h3) { color:var(--text-primary); font-size:14px; }
.risk-drawer-heading { padding-bottom:18px; border-bottom:1px solid var(--border-light); }.risk-drawer-heading>span { display:inline-block; padding:4px 8px; border-radius:999px; font-size:10px; font-weight:750; }.risk-drawer-heading>span.high { color:var(--danger); background:var(--danger-bg); }.risk-drawer-heading>span.medium { color:var(--warning); background:var(--warning-soft); }.risk-drawer-heading>span.low { color:var(--success); background:var(--success-soft); }.risk-drawer-heading h2 { margin:10px 0 6px; font-size:20px; }.risk-drawer-heading p { color:var(--text-secondary); font-size:12px; line-height:1.7; }.risk-action-form { display:grid; grid-template-columns:1fr 1fr; gap:14px; padding:20px 0; }.risk-action-form label { display:grid; gap:6px; color:var(--text-secondary); font-size:12px; }.risk-action-form .span-2 { grid-column:span 2; }.risk-events { padding-top:16px; border-top:1px solid var(--border-light); }.risk-events>header { display:flex; justify-content:space-between; margin-bottom:12px; }.risk-events>header span { color:var(--text-tertiary); font-size:11px; }.risk-events article { display:grid; grid-template-columns:10px 1fr; gap:10px; padding:0 0 18px; }.risk-events article i { width:7px; height:7px; margin-top:5px; border-radius:50%; background:var(--brand); }.risk-events article strong { font-size:12px; }.risk-events article p { margin:5px 0; color:var(--text-secondary); font-size:11px; }.risk-events article small { color:var(--text-tertiary); font-size:10px; }
.simulation-impact { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); margin-top:22px; border:1px solid var(--border-light); border-radius:10px; background:var(--surface-subtle); }.simulation-impact article { min-width:0; padding:14px; }.simulation-impact article+article { border-left:1px solid var(--border-light); }.simulation-impact span,.simulation-result-heading span { display:block; color:var(--text-tertiary); font-size:10px; }.simulation-impact strong { display:block; margin-top:5px; font:720 17px/1.2 var(--font-mono); }.simulation-impact .danger strong { color:var(--danger); }.simulation-result-heading { display:flex; align-items:baseline; justify-content:space-between; gap:12px; margin:18px 0 9px; }.simulation-result-heading strong { font-size:12px; }.simulation-changes { display:grid; gap:8px; }.simulation-changes article { padding:12px 13px; border:1px solid var(--border-light); border-radius:9px; }.simulation-changes header { display:flex; align-items:center; justify-content:space-between; gap:10px; }.simulation-changes header strong { min-width:0; overflow:hidden; font-size:12px; text-overflow:ellipsis; white-space:nowrap; }.simulation-changes header span { color:var(--text-tertiary); font:650 11px/1 var(--font-mono); }.simulation-changes header span.late { color:var(--danger); }.simulation-changes p { margin:7px 0 5px; color:var(--text-secondary); font:10px/1.5 var(--font-mono); }.simulation-changes p b { color:var(--brand); }.simulation-changes small { color:var(--text-tertiary); font-size:10px; }
@media (max-width:1024px) { .decision-metrics{grid-template-columns:repeat(3,1fr)}.overview-columns{grid-template-columns:1fr}.health-hero{grid-template-columns:150px 1fr 180px} }
@media (max-width:767px) { .health-hero{grid-template-columns:1fr;gap:14px;padding:19px}.health-grade{padding:0 0 12px;border-right:0;border-bottom:1px solid rgba(255,255,255,.18)}.health-finish{text-align:left}.decision-metrics{grid-template-columns:1fr 1fr} }
@media (max-width:1024px) { .management-main { padding:28px 24px 44px; }.two-column { grid-template-columns:1fr; }.schedule-metrics { grid-template-columns:1fr 1fr; }.schedule-metrics article:nth-child(3) { border-left:0; border-top:1px solid var(--border-light); }.schedule-metrics article:nth-child(4) { border-top:1px solid var(--border-light); } }
@media (max-width:767px) { .management-main { max-width:100vw; padding:22px 16px 36px; overflow:hidden; }.summary-grid { grid-template-columns:1fr 1fr; }.summary-card:nth-child(3) { border-left:0; border-top:1px solid var(--border-light); }.summary-card:nth-child(4) { border-top:1px solid var(--border-light); }.summary-card { padding:16px; }.summary-card strong { font-size:25px; }.panel { padding:16px; }.panel-heading { flex-direction:column; }.schedule-actions { width:100%; justify-content:flex-start; }.schedule-actions .el-select { min-width:0; flex:1; }.schedule-metrics strong { font-size:15px; }.risk-layout { grid-template-columns:1fr; }.risk-summary>div { padding:11px; }.risk-score { padding:10px 6px; }.risk-open { display:none; }.ai-risk-heading { align-items:flex-start; flex-direction:column; }.gantt-wrap { margin-inline:-16px; padding-inline:16px; }.gantt-head,.gantt-row { grid-template-columns:140px var(--timeline-width,680px); min-width:874px; }.baseline-ledger>div:first-child { align-items:flex-start; flex-direction:column; gap:3px; }.milestone-card { flex-wrap:wrap; }.milestone-actions { width:100%; justify-content:flex-end; }.form-grid,.simulation-form,.risk-action-form { grid-template-columns:1fr; }.simulation-form label,.simulation-form label:first-child,.simulation-form-actions,.risk-action-form .span-2 { grid-column:auto; }.simulation-impact { grid-template-columns:1fr; }.simulation-impact article+article { border-left:0; border-top:1px solid var(--border-light); }.simulation-result-heading { align-items:flex-start; flex-direction:column; gap:3px; }.span-2 { grid-column:auto; } }
</style>
