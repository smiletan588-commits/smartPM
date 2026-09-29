<template>
  <AppShell :project-id="projectId" :project-name="projectName">
  <div class="board">
    <main class="board-main">
      <PageHeader :title="projectName || '项目看板'">
        <template #actions>
          <el-button :disabled="!canWrite" :title="writePermissionHint" @click="openCreate('TODO')">新建任务</el-button>
          <input ref="csvInput" class="visually-hidden" type="file" accept=".csv,text/csv" @change="previewCsv" />
          <el-dropdown trigger="click">
            <el-button>数据工具</el-button>
            <template #dropdown><el-dropdown-menu>
              <el-dropdown-item @click="downloadCsv"><el-icon><Download /></el-icon>导出 CSV</el-dropdown-item>
              <el-dropdown-item :disabled="!canManageProject" @click="csvInput?.click()"><el-icon><Upload /></el-icon>{{ canManageProject ? '导入 CSV' : '导入 CSV（仅管理员）' }}</el-dropdown-item>
            </el-dropdown-menu></template>
          </el-dropdown>
          <el-dropdown trigger="click">
            <el-button type="primary"><el-icon><MagicStick /></el-icon> AI 助手</el-button>
            <template #dropdown><el-dropdown-menu>
              <el-dropdown-item :disabled="!canWrite || hasTasks" @click="openPlanning('PLAN')">AI 完整计划</el-dropdown-item>
              <el-dropdown-item :disabled="!canWrite || hasTasks" @click="openPlanning('INIT')">AI 生成任务</el-dropdown-item>
              <el-dropdown-item divided :disabled="summaryLoading" @click="openSummary">生成项目总结</el-dropdown-item>
            </el-dropdown-menu></template>
          </el-dropdown>
        </template>
      </PageHeader>
      <div class="board-filters">
        <el-input v-model="filters.keyword" clearable placeholder="搜索任务" size="small" style="width:180px" />
        <el-select v-model="filters.assigneeId" clearable placeholder="全部负责人" size="small">
          <el-option v-for="member in projectMembers" :key="member.userId" :label="member.nickname || member.username" :value="member.userId" />
        </el-select>
        <el-select v-model="filters.role" clearable placeholder="全部角色" size="small">
          <el-option v-for="role in projectRoleOptions" :key="role.value" :label="role.label" :value="role.value" />
        </el-select>
        <el-select v-model="filters.tag" clearable placeholder="全部标签" size="small">
          <el-option v-for="tag in tagOptions" :key="tag.value" :label="tag.label" :value="tag.value" />
        </el-select>
        <el-check-tag v-for="item in quickFilters" :key="item.value" :checked="filters.scope === item.value" @change="checked => filters.scope = checked ? item.value : ''">{{ item.label }}</el-check-tag>
        <el-button :type="batchMode ? 'primary' : 'default'" plain size="small" :disabled="!canWrite" :title="writePermissionHint" @click="toggleBatchMode">{{ batchMode ? '退出批量' : '批量操作' }}</el-button>
        <el-button text size="small" @click="clearFilters">清除筛选</el-button>
      </div>
      <div v-if="batchMode" class="batch-toolbar">
        <el-checkbox :model-value="allVisibleSelected" @change="toggleAllVisible">选择当前筛选结果</el-checkbox>
        <span>已选 {{ selectedTaskIds.length }} 项</span>
        <el-select v-model="batchForm.status" clearable :disabled="!batchStatusOptions.length" :placeholder="batchStatusPlaceholder" size="small"><el-option v-for="option in batchStatusOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select>
        <el-select v-model="batchForm.assigneeId" clearable placeholder="修改负责人" size="small"><el-option v-for="member in projectMembers" :key="member.userId" :label="member.nickname || member.username" :value="member.userId" /></el-select>
        <el-select v-model="batchForm.priority" clearable placeholder="修改优先级" size="small"><el-option label="高" value="HIGH" /><el-option label="中" value="MEDIUM" /><el-option label="低" value="LOW" /></el-select>
        <el-date-picker v-model="batchForm.dueDate" value-format="YYYY-MM-DD" type="date" clearable placeholder="修改截止日期" size="small" />
        <el-button type="primary" size="small" :loading="batchSaving" :disabled="!selectedTaskIds.length" @click="applyBatchUpdate">应用修改</el-button>
      </div>
      <div class="workflow-guide" role="note">
        <div class="workflow-path"><strong>任务流转</strong><span>待办</span><i>→</i><span>进行中</span><i>→</i><span>已完成</span></div>
        <p>任务只能逐级推进；已完成任务仅测试工程师可因 Bug 打回。</p>
      </div>
      <div class="mobile-status-tabs" role="tablist" aria-label="任务状态">
        <button v-for="item in mobileStatuses" :key="item.value" :class="{ active: mobileStatus === item.value }" @click="mobileStatus = item.value">
          {{ item.label }} <span>{{ item.count }}</span>
        </button>
      </div>
      <StatePanel v-if="taskLoadError" tone="error" title="任务看板暂时无法加载" :description="taskLoadError">
        <template #actions><el-button type="primary" plain @click="fetchTasks">重新加载</el-button></template>
      </StatePanel>
      <div v-else class="columns">

        <!-- TODO 列 -->
        <div class="column" :class="{ 'mobile-active': mobileStatus === 'TODO' }">
          <div class="column-header todo">
            <div class="col-title">
              <span>待办</span>
              <span class="col-count">{{ todoList.length }}</span>
            </div>
            <el-button text size="small" :disabled="!canWrite" :title="writePermissionHint" @click="openCreate('TODO')">+</el-button>
          </div>
          <draggable v-model="todoList" group="tasks" item-key="id" :disabled="!canWrite"
            class="column-body" data-status="TODO" ghost-class="ghost" animation="180" handle=".card-grip" :move="allowTaskMove"
            @change="(e) => onDragChange(e, 'TODO')">
            <template #item="{ element }">
              <div class="task-card" @click="openDetail(element)">
                <div class="card-grip">
                  <span v-for="i in 3" :key="i" class="grip-dot" />
                </div>
                <el-checkbox v-if="batchMode" class="task-select" :model-value="selectedTaskIds.includes(element.id)" @click.stop @change="checked => toggleTaskSelection(element.id, checked)" />
                <div class="card-content">
                  <p class="task-title">{{ element.title }}</p>
                  <p class="task-desc" v-if="element.description">{{ element.description }}</p>
                  <div class="task-meta-chips">
                    <span class="priority-chip" :class="`priority-${element.priority || 'MEDIUM'}`">{{ priorityLabel(element.priority) }}</span>
                    <span v-for="tag in taskTags(element)" :key="tag" class="task-label">{{ tagLabel(tag) }}</span>
                    <span v-if="element.blocked" class="blocked-chip">被阻塞</span>
                  </div>
                  <div v-if="element.recommendedRole || element.recommendedSkill || element.assigneeId" class="task-assignment-row">
                    <span v-if="element.recommendedRole || element.recommendedSkill" class="role-tag"
                      :style="{ background: (roleConfig[element.recommendedRole] || {}).color || '#94A3B8' }">
                      {{ (roleConfig[element.recommendedRole] || {}).label || element.recommendedSkill || element.recommendedRole }}
                    </span>
                    <span v-if="assigneeName(element)" class="assignee-name">负责人：{{ assigneeName(element) }}</span>
                    <span v-else class="assignee-name pending">等待成员接取</span>
                  </div>
                  <div class="task-footer">
                    <span v-if="element.dueDate" class="task-due"
                      :class="{ overdue: isOverdue(element.dueDate) }">{{ element.dueDate }}</span>
                    <span v-else />
                    <div class="footer-actions">
                      <el-button v-if="canShowStartAction(element)" class="claim-button" type="primary" plain size="small"
                        :loading="claimingTaskId === element.id" :disabled="element.blocked" :title="element.blocked ? '前置任务完成后才能领取' : '领取后任务会进入进行中并分配给你'"
                        @click.stop="claimAndStart(element)">{{ element.assigneeId ? '开始任务' : '领取并开始' }}</el-button>
                      <div class="subtask-badge" v-if="subtaskProgress(element.id).total > 0">
                        <div class="subtask-minibar">
                          <div class="subtask-minibar-fill"
                            :style="{ width: subtaskProgress(element.id).percent + '%' }" />
                        </div>
                        <span class="subtask-minitext">
                          {{ subtaskProgress(element.id).done }}/{{ subtaskProgress(element.id).total }}
                        </span>
                      </div>
                      <el-button type="danger" text size="small" :disabled="!canWrite" :title="writePermissionHint" @click.stop="handleDelete(element)">
                        <el-icon><Delete /></el-icon>
                      </el-button>
                    </div>
                  </div>
                </div>
              </div>
            </template>
          </draggable>
          <div v-if="todoList.length === 0" class="column-empty">新任务会从待办开始</div>
        </div>

        <!-- IN_PROGRESS 列 -->
        <div class="column" :class="{ 'mobile-active': mobileStatus === 'IN_PROGRESS' }">
          <div class="column-header progress">
            <div class="col-title">
              <span>进行中</span>
              <span class="col-count">{{ inProgressList.length }}</span>
            </div>
          </div>
          <draggable v-model="inProgressList" group="tasks" item-key="id" :disabled="!canWrite"
            class="column-body" data-status="IN_PROGRESS" ghost-class="ghost" animation="180" handle=".card-grip" :move="allowTaskMove"
            @change="(e) => onDragChange(e, 'IN_PROGRESS')">
            <template #item="{ element }">
              <div class="task-card" @click="openDetail(element)">
                <div class="card-grip">
                  <span v-for="i in 3" :key="i" class="grip-dot" />
                </div>
                <el-checkbox v-if="batchMode" class="task-select" :model-value="selectedTaskIds.includes(element.id)" @click.stop @change="checked => toggleTaskSelection(element.id, checked)" />
                <div class="card-content">
                  <p class="task-title">{{ element.title }}</p>
                  <p class="task-desc" v-if="element.description">{{ element.description }}</p>
                  <div class="task-meta-chips">
                    <span class="priority-chip" :class="`priority-${element.priority || 'MEDIUM'}`">{{ priorityLabel(element.priority) }}</span>
                    <span v-for="tag in taskTags(element)" :key="tag" class="task-label">{{ tagLabel(tag) }}</span>
                    <span v-if="element.blocked" class="blocked-chip">被阻塞</span>
                  </div>
                  <div v-if="element.recommendedRole || element.recommendedSkill || element.assigneeId" class="task-assignment-row">
                    <span v-if="element.recommendedRole || element.recommendedSkill" class="role-tag"
                      :style="{ background: (roleConfig[element.recommendedRole] || {}).color || '#94A3B8' }">
                      {{ (roleConfig[element.recommendedRole] || {}).label || element.recommendedSkill || element.recommendedRole }}
                    </span>
                    <span v-if="assigneeName(element)" class="assignee-name">负责人：{{ assigneeName(element) }}</span>
                    <span v-else class="assignee-name pending">等待成员接取</span>
                  </div>
                  <div class="task-footer">
                    <span v-if="element.dueDate" class="task-due"
                      :class="{ overdue: isOverdue(element.dueDate) }">{{ element.dueDate }}</span>
                    <span v-else />
                    <div class="footer-actions">
                      <div class="subtask-badge" v-if="subtaskProgress(element.id).total > 0">
                        <div class="subtask-minibar">
                          <div class="subtask-minibar-fill"
                            :style="{ width: subtaskProgress(element.id).percent + '%' }" />
                        </div>
                        <span class="subtask-minitext">
                          {{ subtaskProgress(element.id).done }}/{{ subtaskProgress(element.id).total }}
                        </span>
                      </div>
                      <el-button type="danger" text size="small" :disabled="!canWrite" :title="writePermissionHint" @click.stop="handleDelete(element)">
                        <el-icon><Delete /></el-icon>
                      </el-button>
                    </div>
                  </div>
                </div>
              </div>
            </template>
          </draggable>
          <div v-if="inProgressList.length === 0" class="column-empty">领取待办任务后会出现在这里</div>
        </div>

        <!-- DONE 列 -->
        <div class="column" :class="{ 'mobile-active': mobileStatus === 'DONE' }">
          <div class="column-header done">
            <div class="col-title">
              <span>已完成</span>
              <span class="col-count">{{ doneList.length }}</span>
            </div>
          </div>
          <draggable v-model="doneList" group="tasks" item-key="id" :disabled="!canWrite"
            class="column-body" data-status="DONE" ghost-class="ghost" animation="180" handle=".card-grip" :move="allowTaskMove"
            @change="(e) => onDragChange(e, 'DONE')">
            <template #item="{ element }">
              <div class="task-card done-card" @click="openDetail(element)">
                <div class="card-grip">
                  <span v-for="i in 3" :key="i" class="grip-dot" />
                </div>
                <el-checkbox v-if="batchMode" class="task-select" :model-value="selectedTaskIds.includes(element.id)" @click.stop @change="checked => toggleTaskSelection(element.id, checked)" />
                <div class="card-content">
                  <p class="task-title">{{ element.title }}</p>
                  <p class="task-desc" v-if="element.description">{{ element.description }}</p>
                  <div class="task-meta-chips">
                    <span class="priority-chip" :class="`priority-${element.priority || 'MEDIUM'}`">{{ priorityLabel(element.priority) }}</span>
                    <span v-for="tag in taskTags(element)" :key="tag" class="task-label">{{ tagLabel(tag) }}</span>
                    <span v-if="element.blocked" class="blocked-chip">被阻塞</span>
                  </div>
                  <div v-if="element.recommendedRole || element.recommendedSkill || element.assigneeId" class="task-assignment-row">
                    <span v-if="element.recommendedRole || element.recommendedSkill" class="role-tag"
                      :style="{ background: (roleConfig[element.recommendedRole] || {}).color || '#94A3B8' }">
                      {{ (roleConfig[element.recommendedRole] || {}).label || element.recommendedSkill || element.recommendedRole }}
                    </span>
                    <span v-if="assigneeName(element)" class="assignee-name">负责人：{{ assigneeName(element) }}</span>
                    <span v-else class="assignee-name pending">等待成员接取</span>
                  </div>
                  <div class="task-footer">
                    <span v-if="element.dueDate" class="task-due">{{ element.dueDate }}</span>
                    <span v-else />
                    <div class="footer-actions">
                      <div class="subtask-badge" v-if="subtaskProgress(element.id).total > 0">
                        <div class="subtask-minibar">
                          <div class="subtask-minibar-fill"
                            :style="{ width: subtaskProgress(element.id).percent + '%' }" />
                        </div>
                        <span class="subtask-minitext">
                          {{ subtaskProgress(element.id).done }}/{{ subtaskProgress(element.id).total }}
                        </span>
                      </div>
                      <el-button type="danger" text size="small" :disabled="!canWrite" :title="writePermissionHint" @click.stop="handleDelete(element)">
                        <el-icon><Delete /></el-icon>
                      </el-button>
                    </div>
                  </div>
                </div>
              </div>
            </template>
          </draggable>
          <div v-if="doneList.length === 0" class="column-empty">进行中的任务完成后会归档到这里</div>
        </div>

      </div>
    </main>

    <!-- 任务详情对话框（子任务清单） -->
    <el-dialog v-model="detailVisible" :close-on-click-modal="false"
      width="min(760px, calc(100vw - 32px))" top="4vh" class="detail-dialog">
      <template #header>
        <div class="detail-header">
          <div class="detail-heading">
            <div class="detail-kicker">
              <span class="detail-status-tag" :class="selectedTask ? statusClass(selectedTask.status) : ''">
                {{ selectedTask ? statusLabel(selectedTask.status) : '' }}
              </span>
              <span v-if="selectedTask" class="detail-task-id">任务 #{{ selectedTask.id }}</span>
            </div>
            <h2 class="detail-title">{{ selectedTask?.title }}</h2>
          </div>
          <div class="detail-actions" v-if="selectedTask">
            <div class="detail-tool-actions">
              <el-button size="small" :disabled="!canWrite" :title="writePermissionHint" @click="openEdit(selectedTask)"><el-icon><EditPen /></el-icon>编辑</el-button>
              <el-button v-if="!selectedTask.parentId" size="small" :disabled="!canWrite" :title="writePermissionHint" @click="openRecurrence"><el-icon><RefreshRight /></el-icon>周期任务</el-button>
              <el-button size="small" @click="router.push({ path: `/project/${projectId}/wiki`, query: { task: selectedTask.id, projectName } })"><el-icon><Document /></el-icon>关联文档</el-button>
              <el-button size="small" :loading="optimizationLoading" :disabled="!canWrite" :title="writePermissionHint" @click="optimizeTask"><el-icon><MagicStick /></el-icon>AI 优化</el-button>
            </div>
            <div class="detail-state-actions">
              <el-button v-if="selectedTask.status === 'TODO'" type="primary" size="small"
                :loading="claimingTaskId === selectedTask.id" :disabled="!canStartSelectedTask" :title="startActionHint" @click="handleStartTask">
                <el-icon><VideoPlay /></el-icon>{{ startActionLabel }}
              </el-button>
              <el-button v-if="selectedTask.status === 'IN_PROGRESS' && !selectedTask.reviewRequired" type="success" size="small"
                :loading="taskActionLoading" :disabled="!canWrite" :title="writePermissionHint" @click="handleCompleteTask">
                <el-icon><CircleCheck /></el-icon>完成任务
              </el-button>
              <el-button v-else-if="selectedTask.status === 'IN_PROGRESS' && !['PENDING','IN_REVIEW'].includes(acceptance.status)" type="success" size="small" :loading="acceptanceSaving" :disabled="!canWrite" :title="writePermissionHint" @click="acceptanceAction('SUBMIT')"><el-icon><CircleCheck /></el-icon>提交验收</el-button>
              <el-button v-if="selectedTask.status === 'DONE' && canQaReturn" type="danger" plain size="small"
                :loading="acceptanceSaving" title="发现影响交付的 Bug 时，将任务打回进行中" @click="returnCompletedForFix">打回修改</el-button>
            </div>
          </div>
        </div>
      </template>
      <div class="detail-body" v-if="selectedTask">
        <section class="detail-overview">
          <p class="detail-desc" :class="{ empty: !selectedTask.description }">{{ selectedTask.description || '暂无任务描述' }}</p>
          <div class="detail-facts">
            <div class="detail-fact">
              <span>优先级</span>
              <strong><i class="priority-marker" :class="`priority-${selectedTask.priority || 'MEDIUM'}`" />{{ priorityLabel(selectedTask.priority) }}</strong>
            </div>
            <div class="detail-fact">
              <span>负责人</span>
              <strong>{{ selectedTask.assigneeId && memberMap[selectedTask.assigneeId] ? memberMap[selectedTask.assigneeId].nickname : '待指派' }}</strong>
            </div>
            <div class="detail-fact">
              <span>截止日期</span>
              <strong>{{ selectedTask.dueDate || '未设置' }}</strong>
            </div>
            <div class="detail-fact">
              <span>开始日期</span>
              <strong>{{ selectedTask.startDate || '未设置' }}</strong>
            </div>
            <div class="detail-fact">
              <span>预计工时</span>
              <strong>{{ selectedTask.estimatedHours !== null && selectedTask.estimatedHours !== undefined ? `${selectedTask.estimatedHours} 小时` : '未设置' }}</strong>
            </div>
            <div class="detail-fact">
              <span>实际工时</span>
              <strong>{{ selectedTask.actualHours !== null && selectedTask.actualHours !== undefined ? `${selectedTask.actualHours} 小时` : '未记录' }}</strong>
            </div>
          </div>
          <div v-if="taskTags(selectedTask).length" class="detail-labels">
            <span>标签</span>
            <div><span v-for="tag in taskTags(selectedTask)" :key="tag" class="task-label">{{ tagLabel(tag) }}</span></div>
          </div>
        </section>
        <div v-if="selectedTask.blocked" class="blocked-notice">此任务被前置任务阻塞：{{ (selectedTask.blockedByTaskTitles || []).join('、') }}</div>
        <div v-else-if="dependencyTitles(selectedTask).length" class="dependency-notice"><span>前置依赖</span><strong>{{ dependencyTitles(selectedTask).join('、') }}</strong></div>
        <div class="detail-meta task-assignment" v-if="selectedTask.recommendedRole || selectedTask.recommendedSkill">
          <span class="role-tag" :style="{ background: (roleConfig[selectedTask.recommendedRole] || {}).color || '#94A3B8' }">
            推荐{{ selectedTask.recommendedRole ? '岗位' : '能力' }}：{{ (roleConfig[selectedTask.recommendedRole] || {}).label || selectedTask.recommendedSkill || selectedTask.recommendedRole }}
          </span>
          <span v-if="selectedTask.assigneeId && memberMap[selectedTask.assigneeId]">
            已指派给 {{ memberMap[selectedTask.assigneeId].nickname }}
          </span>
          <span v-else class="assignee-info pending">等待匹配项目成员</span>
        </div>
        <div v-if="selectedTask.acceptanceCriteria" class="acceptance-section">
          <strong>验收标准</strong><p>{{ selectedTask.acceptanceCriteria }}</p>
        </div>
        <section class="delivery-panel">
          <header><div><span>QUALITY GATE</span><h3>交付与验收</h3></div><span class="acceptance-status" :class="(acceptance.status || 'not_required').toLowerCase()">{{ acceptanceStatusLabel(acceptance.status) }}</span></header>
          <div v-if="!selectedTask.reviewRequired" class="acceptance-disabled"><p>当前任务不需要独立评审，可直接完成。</p><el-button size="small" plain :disabled="!canWrite" :title="writePermissionHint" @click="configureAcceptance(true)">启用验收流程</el-button></div>
          <template v-else>
            <div class="acceptance-checklist">
              <label v-for="item in acceptance.checklist || []" :key="item.id"><el-checkbox :model-value="item.checked" :disabled="!canWrite || acceptance.status === 'PASSED'" :title="writePermissionHint" @change="checked => toggleAcceptance(item, checked)" /><span :class="{ checked:item.checked }">{{ item.content }}</span><button v-if="canWrite && acceptance.status !== 'PASSED'" @click="removeAcceptanceItem(item)">×</button></label>
              <div v-if="acceptance.status !== 'PASSED' && canWrite" class="checklist-composer"><el-input v-model="checklistText" size="small" placeholder="添加可验证的验收项" @keyup.enter="addAcceptanceItem" /><el-button size="small" @click="addAcceptanceItem">添加</el-button></div>
            </div>
            <div class="review-actions">
              <el-select v-if="canReview && ['PENDING','IN_REVIEW'].includes(acceptance.status) && previewableAttachments.length" v-model="acceptanceEvidenceId" clearable size="small" placeholder="选择交付证据"><el-option v-for="item in previewableAttachments" :key="item.id" :label="item.originalName" :value="item.id" /></el-select>
              <el-button v-if="!['PENDING','IN_REVIEW','PASSED'].includes(acceptance.status)" type="primary" size="small" :loading="acceptanceSaving" :disabled="!canWrite" :title="writePermissionHint" @click="acceptanceAction('SUBMIT')">提交验收</el-button>
              <el-button v-if="canReview && acceptance.status === 'PENDING'" size="small" @click="acceptanceAction('START')">开始评审</el-button>
              <el-button v-if="canReview && ['PENDING','IN_REVIEW'].includes(acceptance.status)" type="success" size="small" @click="acceptanceAction('PASS')">通过</el-button>
              <el-button v-if="canReview && ['PENDING','IN_REVIEW'].includes(acceptance.status)" type="danger" plain size="small" @click="rejectAcceptance">驳回</el-button>
              <el-button v-if="acceptance.status === 'PASSED'" size="small" disabled>已形成交付记录</el-button>
            </div>
            <div v-if="acceptance.reviews?.length" class="review-trail"><span v-for="item in acceptance.reviews.slice(0,3)" :key="item.id"><b>{{ item.reviewerName || '系统' }}</b> {{ acceptanceActionLabel(item.action) }}<small>{{ formatActivityTime(item.createdAt) }}</small><em v-if="item.comment">{{ item.comment }}</em><button v-if="attachmentById(item.evidenceAttachmentId)" @click="previewAttachment(attachmentById(item.evidenceAttachmentId))">查看交付证据</button></span></div>
          </template>
        </section>
        <section class="time-panel">
          <header><div><span>TIME LOG</span><h3>工时记录</h3></div><strong>{{ timeEntryTotal }}h</strong></header>
          <div v-if="canWrite" class="time-entry-form"><el-date-picker v-model="timeForm.workDate" type="date" value-format="YYYY-MM-DD" size="small" /><el-input-number v-model="timeForm.hours" :min="0.25" :max="24" :step="0.25" size="small" /><el-input v-model="timeForm.note" size="small" placeholder="本次工作说明" /><el-button size="small" :loading="timeSaving" @click="saveTimeEntry">登记</el-button></div>
          <p v-else class="viewer-note">只读成员可以查看工时记录，但不能登记工时。</p>
          <div v-if="timeEntries.length" class="time-entry-list"><span v-for="item in timeEntries.slice(0,4)" :key="item.id"><b>{{ item.workDate }}</b>{{ item.userName }} · {{ item.hours }}h<em>{{ item.note || '未填写说明' }}</em></span></div>
        </section>
        <div class="subtask-section">
          <div class="subtask-section-header">
            <div class="subtask-section-title">
              <span class="subtask-label">子任务清单</span>
              <span class="subtask-summary">
                {{ doneCount(selectedTask.id) }}/{{ totalCount(selectedTask.id) }} 已完成
              </span>
            </div>
            <el-button type="primary" size="small"
              :disabled="!canWrite || selectedTask.status === 'DONE'" :title="selectedTask.status === 'DONE' ? '已完成任务无需继续拆解' : writePermissionHint" @click="openPlanning('DECOMPOSE', selectedTask)">
               AI 辅助拆解
            </el-button>
          </div>
          <div class="subtask-progress-bar" v-if="totalCount(selectedTask.id) > 0">
            <div class="progress-track">
              <div class="progress-fill" :style="{ width: progressPercent(selectedTask.id) + '%' }" />
            </div>
          </div>
          <div v-if="currentSubtasks.length === 0 && decomposingTaskId !== selectedTask.id"
            class="subtask-empty">
            暂无子任务，点击上方按钮让 AI 帮你拆解为具体步骤
          </div>
          <div v-for="sub in currentSubtasks" :key="sub.id" class="subtask-item"
            :class="{ done: sub.status === 'DONE' }">
            <el-checkbox :model-value="sub.status === 'DONE'" :disabled="!canWrite" :title="writePermissionHint"
              @change="(val) => toggleSubtaskStatus(sub, val)">
              <span class="subtask-title">{{ sub.title }}</span>
            </el-checkbox>
            <p class="subtask-desc" v-if="sub.description">{{ sub.description }}</p>
            <div class="subtask-meta" v-if="sub.recommendedRole || sub.recommendedSkill">
              <span class="role-tag" :style="{ background: (roleConfig[sub.recommendedRole] || {}).color || '#94A3B8' }">
                推荐：{{ (roleConfig[sub.recommendedRole] || {}).label || sub.recommendedSkill || sub.recommendedRole }}
              </span>
              <span class="assignee-info" v-if="sub.assigneeId && memberMap[sub.assigneeId]">
                指派给：{{ memberMap[sub.assigneeId].nickname }}
              </span>
              <span class="assignee-info pending" v-else-if="sub.recommendedRole && !sub.assigneeId">
                等待加入 / 指派
              </span>
            </div>
          </div>
        </div>
        <div class="attachment-section">
          <div class="subtask-section-header">
            <span class="subtask-label">任务附件</span>
            <el-button size="small" :disabled="!canWrite" :title="writePermissionHint" @click="attachmentInput?.click()">上传附件</el-button>
            <input ref="attachmentInput" class="hidden-file-input" type="file" accept="image/*,.pdf,.zip,.rar,.7z" @change="handleAttachmentUpload" />
          </div>
          <p class="attachment-tip">支持图片、PDF、ZIP / RAR / 7Z，单个文件不超过 20MB。</p>
          <div v-for="attachment in attachments" :key="attachment.id" class="attachment-row">
          <span><el-icon><Paperclip /></el-icon> {{ attachment.originalName }}</span><small>{{ formatFileSize(attachment.size) }}</small>
            <div><el-button v-if="isPreviewable(attachment)" text type="primary" size="small" @click="previewAttachment(attachment)">预览</el-button><el-button text size="small" @click="downloadAttachment(attachment)">下载</el-button><el-button text size="small" @click="showDownloadLogs(attachment)">记录</el-button><el-button text type="danger" size="small" :disabled="!canWrite" :title="writePermissionHint" @click="removeAttachment(attachment)">移入回收站</el-button></div>
          </div>
          <p v-if="!attachments.length" class="attachment-tip">暂无附件</p>
        </div>
        <div class="collaboration-section">
          <el-tabs v-model="collaborationTab" stretch>
            <el-tab-pane label="评论协作" name="comments">
              <div class="comment-list" v-loading="collaborationLoading">
                <article v-for="comment in taskComments" :key="comment.id" class="comment-item">
                  <span class="comment-avatar">{{ (comment.authorName || '用').slice(0, 1) }}</span>
                  <div class="comment-copy"><header><strong>{{ comment.authorName }}</strong><time>{{ formatActivityTime(comment.createdAt) }}</time></header><p>{{ comment.content }}</p></div>
                  <el-button v-if="canComment && comment.userId === userStore.userInfo?.userId" text type="danger" size="small" @click="removeComment(comment)">删除</el-button>
                </article>
                <p v-if="!taskComments.length && !collaborationLoading" class="collaboration-empty">还没有评论，留下第一条协作说明。</p>
              </div>
              <div v-if="canComment" class="comment-composer">
                <el-select v-model="commentMentions" multiple collapse-tags clearable placeholder="提醒项目成员（选填）" style="width:100%">
                  <el-option v-for="member in mentionableMembers" :key="member.userId" :value="member.userId" :label="member.nickname || member.username" />
                </el-select>
                <el-input v-model="commentText" type="textarea" :rows="3" maxlength="2000" show-word-limit placeholder="补充进展、问题或需要确认的事项" />
                <el-button type="primary" :loading="commentSubmitting" @click="submitComment">发表评论</el-button>
              </div>
              <p v-else class="viewer-note">只读成员可以查看评论，但不能发表评论。</p>
            </el-tab-pane>
            <el-tab-pane label="活动时间线" name="activities">
              <div class="activity-timeline" v-loading="collaborationLoading">
                <article v-for="activity in taskActivities" :key="activity.id" class="activity-item"><i /><div><p><strong>{{ activity.actorName }}</strong> {{ activity.summary }}</p><time>{{ formatActivityTime(activity.createdAt) }}</time></div></article>
                <p v-if="!taskActivities.length && !collaborationLoading" class="collaboration-empty">任务发生变更后，时间线会自动记录。</p>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="csvPreviewVisible" title="CSV 导入预览" width="min(900px, 94vw)" :close-on-click-modal="false">
      <div class="csv-summary"><strong>{{ csvPreview.validCount || 0 }} 行可导入</strong><span v-if="csvPreview.invalidCount">{{ csvPreview.invalidCount }} 行需要修正后重新上传</span></div>
      <el-table :data="csvPreview.rows || []" max-height="460">
        <el-table-column prop="rowNumber" label="行" width="58" />
        <el-table-column prop="title" label="任务标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="priority" label="优先级" width="95" />
        <el-table-column prop="assigneeUsername" label="负责人" width="120" />
        <el-table-column label="校验结果" min-width="220"><template #default="{ row }"><span :class="row.errors?.length ? 'csv-error' : 'csv-valid'">{{ row.errors?.join('；') || '可以导入' }}</span></template></el-table-column>
      </el-table>
      <template #footer><el-button @click="csvPreviewVisible=false">取消</el-button><el-button type="primary" :disabled="!csvPreview.validCount || csvPreview.invalidCount" :loading="csvImporting" @click="confirmCsvImport">确认导入</el-button></template>
    </el-dialog>

    <el-dialog v-model="recurrenceVisible" title="周期任务" width="480px" :close-on-click-modal="false">
      <div class="dialog-form">
        <div class="input-group"><label>重复频率</label><el-select v-model="recurrenceForm.frequency" style="width:100%"><el-option label="每天" value="DAILY" /><el-option label="每周" value="WEEKLY" /><el-option label="每月" value="MONTHLY" /></el-select></div>
        <div class="recurrence-row"><div class="input-group"><label>间隔</label><el-input-number v-model="recurrenceForm.intervalValue" :min="1" :max="52" /></div><div class="input-group"><label>截止偏移（天）</label><el-input-number v-model="recurrenceForm.dueOffsetDays" :min="0" :max="365" /></div></div>
        <div v-if="recurrenceForm.frequency === 'WEEKLY'" class="input-group"><label>每周生成日</label><el-checkbox-group v-model="recurrenceForm.weekdays"><el-checkbox-button v-for="item in weekdayOptions" :key="item.value" :value="item.value">{{ item.label }}</el-checkbox-button></el-checkbox-group></div>
        <div v-if="recurrenceForm.frequency === 'MONTHLY'" class="input-group"><label>每月日期</label><el-input-number v-model="recurrenceForm.dayOfMonth" :min="1" :max="31" /></div>
        <div class="recurrence-row"><div class="input-group"><label>开始日期</label><el-date-picker v-model="recurrenceForm.startDate" value-format="YYYY-MM-DD" type="date" /></div><div class="input-group"><label>结束日期（选填）</label><el-date-picker v-model="recurrenceForm.endDate" value-format="YYYY-MM-DD" type="date" clearable /></div></div>
        <div class="switch-line"><span>启用规则</span><el-switch v-model="recurrenceForm.active" /></div>
        <p v-if="recurrenceExisting?.nextRunDate" class="form-hint">下次生成：{{ recurrenceExisting.nextRunDate }}</p>
      </div>
      <template #footer><el-button v-if="recurrenceExisting" type="danger" plain @click="removeRecurrence">删除规则</el-button><el-button @click="recurrenceVisible=false">取消</el-button><el-button type="primary" :loading="recurrenceSaving" @click="persistRecurrence">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="attachmentPreviewVisible" :title="attachmentPreview.name || '交付物预览'" width="min(960px, 94vw)" destroy-on-close @closed="closeAttachmentPreview">
      <div class="attachment-preview">
        <img v-if="attachmentPreview.kind === 'image'" :src="attachmentPreview.url" :alt="attachmentPreview.name" />
        <iframe v-else-if="attachmentPreview.kind === 'pdf'" :src="attachmentPreview.url" title="PDF 交付物预览" />
      </div>
    </el-dialog>

    <!-- 创建 / 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editingTask ? '编辑任务' : '新建任务'"
      width="460px" :close-on-click-modal="false">
      <div class="dialog-form">
        <div class="input-group">
          <label>标题</label>
          <el-input v-model="form.title" placeholder="任务标题" size="large" />
        </div>
        <div class="input-group">
          <label>描述 <span class="optional">选填</span></label>
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="任务描述" />
        </div>
        <div class="input-row">
          <div class="input-group">
            <label>状态</label>
            <el-select v-model="form.status" disabled size="large" style="width:100%">
              <el-option label="待办" value="TODO" />
              <el-option label="进行中" value="IN_PROGRESS" />
              <el-option label="已完成" value="DONE" />
            </el-select>
            <small class="field-help">状态请通过“领取并开始”或“完成任务”逐级推进</small>
          </div>
          <div class="input-group">
            <label>优先级</label>
            <el-select v-model="form.priority" size="large" style="width:100%">
              <el-option label="高优先级" value="HIGH" /><el-option label="中优先级" value="MEDIUM" /><el-option label="低优先级" value="LOW" />
            </el-select>
          </div>
        </div>
        <div v-if="editingTask && isProjectOwner" class="input-group">
          <label>任务负责人 <span class="optional">项目负责人可纠正误接任务</span></label>
          <el-select v-model="form.assigneeId" clearable filterable placeholder="暂不指派" size="large" style="width:100%">
            <el-option v-for="member in projectMembers" :key="member.userId"
              :label="`${member.nickname || member.username} · ${roleLabel(member.identity)}`"
              :value="member.userId" />
          </el-select>
        </div>
        <div class="input-row">
          <div class="input-group">
            <label>开始日期 <span class="optional">选填</span></label>
            <el-date-picker v-model="form.startDate" type="date" placeholder="选择日期"
              value-format="YYYY-MM-DD" size="large" style="width:100%" />
          </div>
          <div class="input-group">
            <label>截止日期 <span class="optional">选填</span></label>
            <el-date-picker v-model="form.dueDate" type="date" placeholder="选择日期"
              value-format="YYYY-MM-DD" size="large" style="width:100%" />
          </div>
        </div>
        <div class="input-row">
          <div class="input-group"><label>预计工时（小时）</label><el-input-number v-model="form.estimatedHours" :min="0" :max="10000" style="width:100%" /></div>
          <div class="input-group"><label>实际工时（小时）</label><el-input-number v-model="form.actualHours" :min="0" :max="10000" style="width:100%" /></div>
        </div>
        <div class="input-group">
          <label>标签 <span class="optional">可多选</span></label>
          <el-select v-model="form.tags" multiple collapse-tags placeholder="选择任务标签" style="width:100%"><el-option v-for="tag in tagOptions" :key="tag.value" :label="tag.label" :value="tag.value" /></el-select>
        </div>
        <div class="input-group">
          <label>前置依赖 <span class="optional">完成后才能开始本任务</span></label>
          <el-select v-model="form.dependencyIds" multiple filterable collapse-tags placeholder="选择前置任务" style="width:100%"><el-option v-for="task in dependencyCandidates" :key="task.id" :label="task.title" :value="task.id" /></el-select>
        </div>
        <div class="input-group">
          <label>验收标准 <span class="optional">选填</span></label>
          <el-input v-model="form.acceptanceCriteria" type="textarea" :rows="2" placeholder="任务完成后应满足哪些可验证条件" />
        </div>
        <div v-if="editingTask" class="switch-line"><span><strong>需要独立验收</strong><small>启用后必须经过提交与评审才能完成</small></span><el-switch v-model="form.reviewRequired" /></div>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ editingTask ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="projectRoleDialogVisible" width="520px" class="project-role-dialog"
      :show-close="false" :close-on-click-modal="false" :close-on-press-escape="false">
      <template #header>
        <div class="project-role-header">
          <h3>欢迎加入 {{ projectName }}</h3>
          <p>请选择你在这个项目中的岗位，方便团队分配任务。</p>
        </div>
      </template>
      <div class="project-role-grid">
        <button v-for="role in projectRoleOptions" :key="role.value" class="project-role-card"
          :disabled="savingProjectRole" @click="selectProjectRole(role.value)">
          <span>{{ role.short }}</span><strong>{{ role.label }}</strong>
        </button>
      </div>
    </el-dialog>

    <!-- AI 项目总结对话框 -->
    <el-dialog v-model="summaryDialogVisible" title="AI 项目周报" width="700px"
      :close-on-click-modal="false" @close="closeSummary">
      <div class="summary-body">
        <div v-if="summaryLoading && !summaryContent" class="summary-loading">
          <el-icon class="is-loading" :size="24"><Loading /></el-icon>
          <span>AI 正在生成项目周报...</span>
        </div>
        <div v-if="summaryContent" class="markdown-body" v-html="renderedMarkdown"></div>
      </div>
      <template #footer v-if="!summaryLoading">
        <el-button @click="closeSummary">关闭</el-button>
      </template>
    </el-dialog>

    <AiPlanningDrawer ref="planningDrawer" :project-id="projectId" :project-name="projectName" :project-description="projectDescription" :members="projectMembers" @applied="onPlanningApplied" />

    <el-dialog v-model="optimizationVisible" title="AI 任务优化建议" width="620px" :close-on-click-modal="false">
      <div v-if="optimization" class="optimization-preview">
        <label>优化标题</label><strong>{{ optimization.title }}</strong>
        <label>任务描述</label><p>{{ optimization.description }}</p>
        <label>验收标准</label><p>{{ optimization.acceptanceCriteria }}</p>
        <div v-if="optimization.oversized" class="blocked-notice"><strong>建议拆分：</strong>{{ optimization.splitAdvice }}</div>
        <p v-else class="optimization-ok">该任务粒度适中，可直接执行。</p>
        <div v-if="optimizationOperationId" class="ai-feedback"><span>为本次建议评分</span><el-rate v-model="optimizationRating" @change="rating => rateAi(optimizationOperationId, rating)" /></div>
      </div>
      <template #footer><el-button @click="optimizationVisible = false">保留原任务</el-button><el-button type="primary" @click="applyOptimization">应用优化内容</el-button></template>
    </el-dialog>
  </div>
  </AppShell>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Loading, VideoPlay, CircleCheck, MagicStick, Paperclip, Download, Upload, EditPen, RefreshRight, Document } from '@element-plus/icons-vue'
import draggable from 'vuedraggable'
import MarkdownIt from 'markdown-it'
import { useUserStore } from '@/store/user'
import { listProjects, listProjectMembers, updateMyProjectIdentity } from '@/api/project'
import { listTasks, createTask, updateTask, batchUpdateTasks, deleteTask, listSubtasks, toggleSubtask, listTaskAttachments, uploadTaskAttachment, deleteTaskAttachment, listAttachmentDownloadLogs, optimizeTaskWithAi } from '@/api/task'
import { streamProjectSummary } from '@/api/summary'
import { listTaskComments, addTaskComment, deleteTaskComment, listTaskActivities } from '@/api/collaboration'
import { submitAiFeedback, markAiOperationApplied } from '@/api/ai'
import { exportTasks, previewTaskCsv, importTaskCsv } from '@/api/csv'
import { getTaskRecurrence, saveTaskRecurrence, deleteTaskRecurrence } from '@/api/recurrence'
import { addAcceptanceChecklist, addTimeEntry, deleteAcceptanceChecklist, getTaskAcceptance, listTimeEntries, toggleAcceptanceChecklist, updateTaskAcceptance } from '@/api/delivery'
import request from '@/utils/request'
import AppShell from '@/components/AppShell.vue'
import AiPlanningDrawer from '@/components/AiPlanningDrawer.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import { canCommentInProject } from '@/utils/permissions'
import { canMoveTask, nextTaskStatus, transitionHint } from '@/utils/taskWorkflow'

const md = new MarkdownIt({ breaks: true, linkify: true })

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const projectId = Number(route.params.id)
const projectName = ref('')
const projectDescription = ref('')

// 看板三列（仅主任务）
const todoList = ref([])
const inProgressList = ref([])
const doneList = ref([])
const allTasks = ref([])
const taskLoadError = ref('')
const filters = reactive({ keyword: '', assigneeId: null, role: '', tag: '', scope: '' })
const quickFilters = [{ value: 'MY', label: '只看我的' }, { value: 'TODAY', label: '今天' }, { value: 'OVERDUE', label: '逾期' }, { value: 'BLOCKED', label: '阻塞' }]
const batchMode = ref(false)
const batchSaving = ref(false)
const selectedTaskIds = ref([])
const batchForm = reactive({ status: '', assigneeId: null, priority: '', dueDate: '' })
const mobileStatus = ref('TODO')
const csvInput = ref(null)
const csvPreviewVisible = ref(false)
const csvImporting = ref(false)
const csvPreview = ref({ rows: [], validCount: 0, invalidCount: 0 })
const recurrenceVisible = ref(false)
const recurrenceSaving = ref(false)
const recurrenceExisting = ref(null)
const recurrenceForm = reactive({ frequency: 'WEEKLY', intervalValue: 1, weekdays: [], dayOfMonth: 1, startDate: '', endDate: '', dueOffsetDays: 0, active: true })
const weekdayOptions = [{ value: 1, label: '一' }, { value: 2, label: '二' }, { value: 3, label: '三' }, { value: 4, label: '四' }, { value: 5, label: '五' }, { value: 6, label: '六' }, { value: 7, label: '日' }]

// 项目成员：userId -> { nickname, identity }
const memberMap = ref({})
const projectMembers = ref([])
const projectRoleDialogVisible = ref(false)
const savingProjectRole = ref(false)

const projectRoleOptions = [
  { value: 'PROJECT_MANAGER', label: '项目经理', short: 'PM' },
  { value: 'PRODUCT_MANAGER', label: '产品经理', short: 'PD' },
  { value: 'FRONTEND_DEV', label: '前端工程师', short: 'FE' },
  { value: 'BACKEND_DEV', label: '后端工程师', short: 'BE' },
  { value: 'QA_TESTER', label: '测试工程师', short: 'QA' },
  { value: 'UI_DESIGNER', label: 'UI 设计师', short: 'UI' }
]

const mobileStatuses = computed(() => [
  { value: 'TODO', label: '待办', count: todoList.value.length },
  { value: 'IN_PROGRESS', label: '进行中', count: inProgressList.value.length },
  { value: 'DONE', label: '已完成', count: doneList.value.length }
])

const roleConfig = {
  PROJECT_MANAGER: { label: '项目经理', color: '#315EE7' },
  PRODUCT_MANAGER: { label: '产品经理', color: '#6F55C7' },
  FRONTEND_DEV:   { label: '前端',     color: '#52627A' },
  BACKEND_DEV:    { label: '后端',     color: '#52627A' },
  QA_TESTER:      { label: '测试',     color: '#52627A' },
  UI_DESIGNER:    { label: 'UI设计',   color: '#52627A' }
}

const tagOptions = [
  { value: 'BUG', label: 'Bug' }, { value: 'REQUIREMENT', label: '需求' },
  { value: 'DESIGN', label: '设计' }, { value: 'DEVELOPMENT', label: '开发' },
  { value: 'TESTING', label: '测试' }, { value: 'DOCUMENTATION', label: '文档' }
]
const priorityConfig = { HIGH: '高优先级', MEDIUM: '中优先级', LOW: '低优先级' }

const hasTasks = computed(() =>
  allTasks.value.length > 0
)
const isProjectOwner = computed(() =>
  projectMembers.value.some(member => member.owner && member.userId === userStore.userInfo?.userId)
)
const dependencyCandidates = computed(() => allTasks.value.filter(task => task.id !== editingTask.value?.id))

function assigneeName(task) {
  if (!task?.assigneeId) return ''
  return memberMap.value[task.assigneeId]?.nickname ||
    (task.assigneeId === userStore.userInfo?.userId ? userStore.userInfo?.nickname || userStore.userInfo?.username : '')
}
function priorityLabel(priority) { return priorityConfig[priority || 'MEDIUM'] || '中优先级' }
function roleLabel(role) { return roleConfig[role]?.label || role || '待分配' }
function tagLabel(tag) { return tagOptions.find(option => option.value === tag)?.label || tag }
function taskTags(task) { return String(task?.tags || '').split(',').filter(Boolean) }
function dependencyTitles(task) {
  return String(task?.dependencyIds || '').split(',').filter(Boolean)
    .map(id => allTasks.value.find(candidate => candidate.id === Number(id))?.title || `任务 #${id}`)
}
function clearFilters() { Object.assign(filters, { keyword: '', assigneeId: null, role: '', tag: '', scope: '' }) }
const visibleTaskIds = computed(() => [...todoList.value, ...inProgressList.value, ...doneList.value].map(task => task.id))
const allVisibleSelected = computed(() => visibleTaskIds.value.length > 0 && visibleTaskIds.value.every(id => selectedTaskIds.value.includes(id)))
const selectedTaskRecords = computed(() => allTasks.value.filter(task => !task.parentId && selectedTaskIds.value.includes(task.id)))
const batchStatusOptions = computed(() => {
  if (!selectedTaskRecords.value.length) return []
  const statuses = [...new Set(selectedTaskRecords.value.map(task => task.status))]
  if (statuses.length !== 1) return []
  const next = nextTaskStatus(statuses[0])
  if (!next) return []
  return [{ value: next, label: next === 'IN_PROGRESS' ? '批量领取并开始' : '批量完成任务' }]
})
const batchStatusPlaceholder = computed(() => {
  if (!selectedTaskRecords.value.length) return '先选择任务'
  const statuses = [...new Set(selectedTaskRecords.value.map(task => task.status))]
  if (statuses.length !== 1) return '所选任务状态不一致'
  return statuses[0] === 'DONE' ? '已完成不能批量回退' : '推进到下一阶段'
})
function toggleBatchMode() { batchMode.value = !batchMode.value; if (!batchMode.value) selectedTaskIds.value = [] }
function toggleTaskSelection(id, checked) { selectedTaskIds.value = checked ? [...new Set([...selectedTaskIds.value, id])] : selectedTaskIds.value.filter(value => value !== id) }
function toggleAllVisible(checked) {
  selectedTaskIds.value = checked ? [...new Set([...selectedTaskIds.value, ...visibleTaskIds.value])]
    : selectedTaskIds.value.filter(id => !visibleTaskIds.value.includes(id))
}
async function applyBatchUpdate() {
  if (!batchForm.status && !batchForm.assigneeId && !batchForm.priority && !batchForm.dueDate) return ElMessage.warning('请选择至少一项要修改的内容')
  if (batchForm.status && !batchStatusOptions.value.some(option => option.value === batchForm.status)) return ElMessage.warning('所选任务不能执行该状态流转')
  batchSaving.value = true
  try {
    await batchUpdateTasks({ taskIds: selectedTaskIds.value, status: batchForm.status || null, assigneeId: batchForm.assigneeId || null, priority: batchForm.priority || null, dueDate: batchForm.dueDate || null })
    ElMessage.success(`已更新 ${selectedTaskIds.value.length} 项任务`)
    selectedTaskIds.value = []; Object.assign(batchForm, { status: '', assigneeId: null, priority: '', dueDate: '' })
    await fetchTasks()
  } finally { batchSaving.value = false }
}

async function fetchMembers() {
  try {
    const res = await listProjectMembers(projectId)
    const members = res.data.data || []
    projectMembers.value = members
    const map = {}
    members.forEach(m => {
      map[m.userId] = { nickname: m.nickname || m.username, identity: m.identity }
    })
    memberMap.value = map
    applyFilters()
    const currentMember = members.find(m => m.userId === userStore.userInfo?.userId)
    if (route.query.setupRole === '1' && !currentMember?.identity) {
      projectRoleDialogVisible.value = true
    }
  } catch { /* ignore */ }
}

async function selectProjectRole(identity) {
  if (savingProjectRole.value) return
  savingProjectRole.value = true
  try {
    await updateMyProjectIdentity(projectId, identity)
    await userStore.updateIdentity(identity)
    projectRoleDialogVisible.value = false
    ElMessage.success('项目岗位已设置')
    await fetchMembers()
  } finally {
    savingProjectRole.value = false
  }
}

// 子任务缓存：taskId -> Task[]
const subtaskData = reactive({})

/** 子任务进度 { done, total, percent } */
function subtaskProgress(taskId) {
  const list = subtaskData[taskId]
  if (!list || list.length === 0) return { done: 0, total: 0, percent: 0 }
  const done = list.filter(s => s.status === 'DONE').length
  return { done, total: list.length, percent: Math.round(done / list.length * 100) }
}
function doneCount(taskId) { return subtaskProgress(taskId).done }
function totalCount(taskId) { return subtaskProgress(taskId).total }
function progressPercent(taskId) { return subtaskProgress(taskId).percent }

// 详情弹窗
const detailVisible = ref(false)
const selectedTask = ref(null)
const collaborationTab = ref('comments')
const collaborationLoading = ref(false)
const taskComments = ref([])
const taskActivities = ref([])
const commentText = ref('')
const commentMentions = ref([])
const commentSubmitting = ref(false)
const currentMember = computed(() => projectMembers.value.find(member => member.userId === userStore.userInfo?.userId))
const canWrite = computed(() => isProjectOwner.value || (currentMember.value && currentMember.value.permission !== 'VIEWER'))
const canManageProject = computed(() => isProjectOwner.value || currentMember.value?.permission === 'PROJECT_ADMIN')
const writePermissionHint = computed(() => canWrite.value ? '' : '只读成员只能查看项目内容')
const canReview = computed(() => canWrite.value && (currentMember.value?.permission === 'PROJECT_ADMIN' || ['PROJECT_MANAGER', 'PRODUCT_MANAGER', 'QA_TESTER', 'UI_DESIGNER'].includes(currentMember.value?.identity)))
const canQaReturn = computed(() => canWrite.value && currentMember.value?.identity === 'QA_TESTER')
const canStartSelectedTask = computed(() => Boolean(selectedTask.value && canShowStartAction(selectedTask.value) && !selectedTask.value.blocked))
const startActionHint = computed(() => {
  if (!canWrite.value) return writePermissionHint.value
  if (selectedTask.value?.blocked) return '前置任务完成后才能领取'
  if (selectedTask.value?.assigneeId && selectedTask.value.assigneeId !== userStore.userInfo?.userId && !canManageProject.value) return '该任务已分配给其他成员'
  return selectedTask.value?.assigneeId ? '开始执行任务' : '领取后任务会进入进行中并分配给你'
})
const startActionLabel = computed(() => {
  if (!selectedTask.value?.assigneeId) return '领取并开始'
  return ({ QA_TESTER: '开始测试', UI_DESIGNER: '开始设计' }[selectedTask.value?.recommendedRole || currentMember.value?.identity] || '开始任务')
})
const canComment = computed(() => canCommentInProject(currentMember.value, isProjectOwner.value))
const mentionableMembers = computed(() => projectMembers.value.filter(member => member.userId !== userStore.userInfo?.userId))
const currentSubtasks = computed(() => {
  if (!selectedTask.value) return []
  return subtaskData[selectedTask.value.id] || []
})

function statusLabel(s) {
  return { TODO: '待办', IN_PROGRESS: '进行中', DONE: '已完成' }[s] || s
}
function statusClass(s) {
  return { TODO: 'tag-todo', IN_PROGRESS: 'tag-progress', DONE: 'tag-done' }[s] || ''
}

function canShowStartAction(task) {
  if (!canWrite.value || task?.status !== 'TODO') return false
  return !task.assigneeId || task.assigneeId === userStore.userInfo?.userId || canManageProject.value
}

// 创建/编辑弹窗
const dialogVisible = ref(false)
const submitting = ref(false)
const editingTask = ref(null)
const form = reactive({ title: '', description: '', status: 'TODO', assigneeId: null, dueDate: null, startDate: null, priority: 'MEDIUM', tags: [], dependencyIds: [], estimatedHours: null, actualHours: null, acceptanceCriteria: '', reviewRequired: false })

function isOverdue(dateStr) {
  if (!dateStr) return false
  return new Date(dateStr) < new Date(new Date().toDateString())
}

// 数据加载
async function fetchProjectName() {
  try {
    const res = await listProjects()
    const project = res.data.data.find(p => p.id === projectId)
    projectName.value = project?.name || '未知项目'
    projectDescription.value = project?.description || ''
  } catch { projectName.value = '未知项目' }
}

async function fetchTasks() {
  taskLoadError.value = ''
  try {
    const res = await listTasks(projectId, { errorMode: 'silent' })
    allTasks.value = res.data.data || []
    applyFilters()
    fetchAllSubtaskCounts(allTasks.value)
  } catch (error) {
    allTasks.value = []
    applyFilters()
    taskLoadError.value = error.message || '暂时无法连接服务器，请检查网络或稍后重试'
  }
}

async function downloadCsv() {
  const response = await exportTasks(projectId)
  const blob = response.data instanceof Blob ? response.data : new Blob([response.data], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${projectName.value || 'SmartPM'}-任务.csv`
  document.body.appendChild(link); link.click(); link.remove(); URL.revokeObjectURL(url)
}

async function previewCsv(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (!canManageProject.value) return ElMessage.warning('只有项目负责人或项目管理员可以导入任务')
  try {
    csvPreview.value = (await previewTaskCsv(projectId, file)).data?.data || { rows: [], validCount: 0, invalidCount: 0 }
    csvPreviewVisible.value = true
  } catch { /* 请求错误已由统一反馈处理 */ }
}

async function confirmCsvImport() {
  csvImporting.value = true
  try {
    await importTaskCsv(projectId, csvPreview.value.rows)
    ElMessage.success(`已导入 ${csvPreview.value.validCount} 项任务`)
    csvPreviewVisible.value = false
    await fetchTasks()
  } finally { csvImporting.value = false }
}

async function openRecurrence() {
  if (!selectedTask.value) return
  recurrenceExisting.value = null
  const start = selectedTask.value.startDate || new Date().toISOString().slice(0, 10)
  Object.assign(recurrenceForm, { frequency: 'WEEKLY', intervalValue: 1, weekdays: [new Date(`${start}T00:00:00`).getDay() || 7], dayOfMonth: Number(start.slice(8, 10)), startDate: start, endDate: '', dueOffsetDays: 0, active: true })
  try {
    const rule = (await getTaskRecurrence(selectedTask.value.id)).data?.data
    if (rule) {
      recurrenceExisting.value = rule
      Object.assign(recurrenceForm, { frequency: rule.frequency, intervalValue: rule.intervalValue, weekdays: String(rule.weekdays || '').split(',').filter(Boolean).map(Number), dayOfMonth: rule.dayOfMonth || 1, startDate: rule.startDate, endDate: rule.endDate || '', dueOffsetDays: rule.dueOffsetDays || 0, active: rule.active !== false })
    }
  } finally { recurrenceVisible.value = true }
}

async function persistRecurrence() {
  if (!recurrenceForm.startDate) return ElMessage.warning('请选择开始日期')
  recurrenceSaving.value = true
  try {
    const payload = { ...recurrenceForm, endDate: recurrenceForm.endDate || '' }
    recurrenceExisting.value = (await saveTaskRecurrence(selectedTask.value.id, payload)).data?.data
    ElMessage.success('周期规则已保存')
    recurrenceVisible.value = false
  } finally { recurrenceSaving.value = false }
}

async function removeRecurrence() {
  try { await ElMessageBox.confirm('删除后不会影响已经生成的任务，确定继续？', '删除周期规则', { type: 'warning' }) } catch { return }
  await deleteTaskRecurrence(selectedTask.value.id)
  recurrenceExisting.value = null
  recurrenceVisible.value = false
  ElMessage.success('周期规则已删除')
}

function applyFilters() {
  const matches = task => {
    if (filters.keyword && !`${task.title || ''} ${task.description || ''}`.toLowerCase().includes(filters.keyword.trim().toLowerCase())) return false
    if (filters.assigneeId && task.assigneeId !== filters.assigneeId) return false
    const assigneeRole = task.assigneeId ? memberMap.value[task.assigneeId]?.identity : null
    if (filters.role && task.recommendedRole !== filters.role && assigneeRole !== filters.role) return false
    if (filters.tag && !taskTags(task).includes(filters.tag)) return false
    const today = new Date().toISOString().slice(0, 10)
    if (filters.scope === 'MY' && task.assigneeId !== userStore.userInfo?.userId) return false
    if (filters.scope === 'TODAY' && task.dueDate !== today) return false
    if (filters.scope === 'OVERDUE' && (!task.dueDate || task.dueDate >= today || task.status === 'DONE')) return false
    if (filters.scope === 'BLOCKED' && !task.blocked) return false
    return true
  }
  const filtered = allTasks.value.filter(matches)
  todoList.value = filtered.filter(task => task.status === 'TODO').sort((a, b) => (a.orderIndex || 0) - (b.orderIndex || 0))
  inProgressList.value = filtered.filter(task => task.status === 'IN_PROGRESS').sort((a, b) => (a.orderIndex || 0) - (b.orderIndex || 0))
  doneList.value = filtered.filter(task => task.status === 'DONE').sort((a, b) => (a.orderIndex || 0) - (b.orderIndex || 0))
}
watch(filters, applyFilters, { deep: true })

async function fetchAllSubtaskCounts(tasks) {
  if (!tasks || tasks.length === 0) return
  const promises = tasks.map(task =>
    listSubtasks(task.id)
      .then(res => { subtaskData[task.id] = res.data.data || [] })
      .catch(() => { subtaskData[task.id] = [] })
  )
  await Promise.all(promises)
}

async function fetchSubtasksForTask(taskId) {
  try {
    const res = await listSubtasks(taskId)
    subtaskData[taskId] = res.data.data || []
  } catch {
    subtaskData[taskId] = []
  }
}

// 详情弹窗
async function openDetail(task) {
  selectedTask.value = task
  detailVisible.value = true
  if (!subtaskData[task.id]) {
    await fetchSubtasksForTask(task.id)
  }
  await Promise.all([fetchAttachments(task.id), loadCollaboration(task.id), loadAcceptance(task.id), loadTimeEntries(task.id)])
}

const acceptance = ref({ status: 'NOT_REQUIRED', checklist: [], reviews: [] })
const acceptanceSaving = ref(false)
const acceptanceEvidenceId = ref(null)
const checklistText = ref('')
const timeEntries = ref([])
const timeSaving = ref(false)
const timeForm = reactive({ workDate: new Date().toISOString().slice(0, 10), hours: 1, note: '' })
const timeEntryTotal = computed(() => timeEntries.value.reduce((sum, item) => sum + Number(item.hours || 0), 0).toFixed(2).replace(/\.00$/, ''))

async function loadAcceptance(taskId) {
  acceptanceEvidenceId.value = null
  try { acceptance.value = (await getTaskAcceptance(taskId)).data.data || { status: 'NOT_REQUIRED', checklist: [], reviews: [] } }
  catch { acceptance.value = { status: 'NOT_REQUIRED', checklist: [], reviews: [] } }
}
async function configureAcceptance(reviewRequired) {
  if (!canWrite.value) return ElMessage.warning(writePermissionHint.value)
  acceptanceSaving.value = true
  try { acceptance.value = (await updateTaskAcceptance(selectedTask.value.id, { action: 'CONFIGURE', reviewRequired })).data.data; selectedTask.value.reviewRequired = reviewRequired; selectedTask.value.acceptanceStatus = acceptance.value.status; ElMessage.success('验收流程已启用') }
  finally { acceptanceSaving.value = false }
}
async function acceptanceAction(action, comment) {
  if (!selectedTask.value || acceptanceSaving.value) return
  if (!canWrite.value) return ElMessage.warning(writePermissionHint.value)
  acceptanceSaving.value = true
  try {
    acceptance.value = (await updateTaskAcceptance(selectedTask.value.id, { action, comment, evidenceAttachmentId: acceptanceEvidenceId.value })).data.data
    selectedTask.value.acceptanceStatus = acceptance.value.status
    if (action === 'PASS') selectedTask.value.status = 'DONE'
    if (['REJECT', 'RETURN_FOR_FIX'].includes(action)) selectedTask.value.status = 'IN_PROGRESS'
    ElMessage.success({ SUBMIT:'已提交验收', START:'已开始评审', PASS:'验收通过，任务已完成', REJECT:'已驳回并退回进行中', RETURN_FOR_FIX:'Bug 已登记，任务已打回进行中' }[action] || '验收状态已更新')
    await fetchTasks()
  } finally { acceptanceSaving.value = false }
}
async function rejectAcceptance() {
  try { const { value } = await ElMessageBox.prompt('请说明未通过的具体原因，负责人将据此修改。', '驳回验收', { inputType:'textarea', inputValidator:value => Boolean(value?.trim()) || '必须填写驳回原因' }); await acceptanceAction('REJECT', value.trim()) }
  catch { /* 取消 */ }
}
async function returnCompletedForFix() {
  if (!canQaReturn.value) return ElMessage.warning('只有测试工程师可以将已完成任务打回修改')
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt('请说明可复现的 Bug、影响范围或预期结果，系统会保留打回记录。', '打回修改', {
      inputType: 'textarea', confirmButtonText: '确认打回', cancelButtonText: '取消',
      inputValidator: value => Boolean(value?.trim()) || '必须填写 Bug 原因'
    })
    reason = value.trim()
  } catch { return }
  await acceptanceAction('RETURN_FOR_FIX', reason)
}
async function addAcceptanceItem() {
  if (!canWrite.value) return ElMessage.warning(writePermissionHint.value)
  if (!checklistText.value.trim()) return
  await addAcceptanceChecklist(selectedTask.value.id, checklistText.value.trim()); checklistText.value=''; await loadAcceptance(selectedTask.value.id)
}
async function toggleAcceptance(item, checked) { if (!canWrite.value) return ElMessage.warning(writePermissionHint.value); await toggleAcceptanceChecklist(selectedTask.value.id, item.id, checked); await loadAcceptance(selectedTask.value.id) }
async function removeAcceptanceItem(item) { if (!canWrite.value) return ElMessage.warning(writePermissionHint.value); await deleteAcceptanceChecklist(selectedTask.value.id, item.id); await loadAcceptance(selectedTask.value.id) }
function acceptanceStatusLabel(value) { return { NOT_REQUIRED:'无需验收', NOT_READY:'准备交付', PENDING:'待评审', IN_REVIEW:'评审中', PASSED:'已通过', REJECTED:'已驳回' }[value] || '无需验收' }
function acceptanceActionLabel(value) { return { CONFIGURE:'配置了验收', SUBMIT:'提交验收', START:'开始评审', PASS:'通过验收', REJECT:'驳回验收', RETURN_FOR_FIX:'因 Bug 打回修改', RESET:'重置验收' }[value] || value }
async function loadTimeEntries(taskId) { try { timeEntries.value=(await listTimeEntries(taskId)).data.data||[] } catch { timeEntries.value=[] } }
async function saveTimeEntry() {
  if (!canWrite.value) return ElMessage.warning(writePermissionHint.value)
  if (!timeForm.workDate || !timeForm.hours) return ElMessage.warning('请选择日期并填写工时')
  timeSaving.value=true
  try { await addTimeEntry(selectedTask.value.id, { ...timeForm }); timeForm.note=''; await loadTimeEntries(selectedTask.value.id); await fetchTasks(); ElMessage.success('工时已登记') }
  finally { timeSaving.value=false }
}

async function loadCollaboration(taskId) {
  collaborationLoading.value = true
  try {
    const [commentsRes, activitiesRes] = await Promise.all([listTaskComments(taskId), listTaskActivities(taskId)])
    taskComments.value = commentsRes.data.data || []
    taskActivities.value = activitiesRes.data.data || []
  } finally { collaborationLoading.value = false }
}

async function submitComment() {
  if (!commentText.value.trim() || !selectedTask.value) return ElMessage.warning('请输入评论内容')
  commentSubmitting.value = true
  try {
    await addTaskComment(selectedTask.value.id, commentText.value.trim(), commentMentions.value)
    commentText.value = ''; commentMentions.value = []; ElMessage.success('评论已发表')
    await loadCollaboration(selectedTask.value.id)
  } finally { commentSubmitting.value = false }
}

async function removeComment(comment) {
  try { await ElMessageBox.confirm('确认将这条评论移入回收站？', '移入回收站', { type: 'warning', confirmButtonText: '移入回收站', cancelButtonText: '取消' }) }
  catch { return }
  try {
    await deleteTaskComment(selectedTask.value.id, comment.id)
    ElMessage.success('评论已移入回收站')
    await loadCollaboration(selectedTask.value.id)
  } catch { /* 请求错误由统一反馈层显示 */ }
}

function formatActivityTime(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '' }

const taskActionLoading = ref(false)
const claimingTaskId = ref(null)

async function claimAndStart(task) {
  if (!task || claimingTaskId.value) return
  if (!canWrite.value) return ElMessage.warning(writePermissionHint.value)
  if (task.blocked) return ElMessage.warning('前置任务尚未完成，暂时不能领取')
  if (task.assigneeId && task.assigneeId !== userStore.userInfo?.userId && !canManageProject.value) {
    return ElMessage.warning('该任务已分配给其他成员')
  }
  claimingTaskId.value = task.id
  const wasUnassigned = !task.assigneeId
  try {
    const payload = { id: task.id, status: 'IN_PROGRESS' }
    if (wasUnassigned) payload.assigneeId = userStore.userInfo?.userId
    const response = await updateTask(payload)
    const updated = response.data.data
    task.status = 'IN_PROGRESS'
    task.assigneeId = updated?.assigneeId || task.assigneeId || userStore.userInfo?.userId
    if (selectedTask.value?.id === task.id) selectedTask.value = { ...selectedTask.value, ...updated }
    ElMessage.success(wasUnassigned ? '任务已领取并开始' : '任务已开始')
    await fetchTasks()
  } finally {
    claimingTaskId.value = null
  }
}

async function changeTaskStatus(status, successMessage) {
  if (!selectedTask.value || taskActionLoading.value) return
  if (!canWrite.value) return ElMessage.warning(writePermissionHint.value)
  taskActionLoading.value = true
  try {
    await updateTask({ id: selectedTask.value.id, status })
    selectedTask.value.status = status
    ElMessage.success(successMessage)
    await fetchTasks()
  } finally {
    taskActionLoading.value = false
  }
}

function handleStartTask() {
  return claimAndStart(selectedTask.value)
}

function handleCompleteTask() {
  return changeTaskStatus('DONE', '任务已完成')
}

// 子任务勾选（乐观更新）
async function toggleSubtaskStatus(sub, checked) {
  if (!canWrite.value) return ElMessage.warning(writePermissionHint.value)
  const prev = sub.status
  sub.status = checked ? 'DONE' : 'TODO'
  try {
    await toggleSubtask(sub.id)
  } catch {
    sub.status = prev
  }
}

const planningDrawer = ref(null)
function openPlanning(mode, task = null) {
  if (!canWrite.value) return ElMessage.warning(writePermissionHint.value)
  planningDrawer.value?.open(mode, task)
}
async function onPlanningApplied(parentTaskId) {
  if (parentTaskId) await fetchSubtasksForTask(parentTaskId)
  else await fetchTasks()
}

// 拖拽：同列可排序，跨列只能向前推进一个阶段。
let lastMoveWarning = ''
let lastMoveWarningAt = 0
function allowTaskMove(event) {
  const sourceStatus = event.draggedContext?.element?.status
  const targetStatus = event.to?.dataset?.status
  if (!sourceStatus || !targetStatus || canMoveTask(sourceStatus, targetStatus)) return true
  const message = transitionHint(sourceStatus, targetStatus)
  const now = Date.now()
  if (message !== lastMoveWarning || now - lastMoveWarningAt > 1500) {
    ElMessage.warning(message)
    lastMoveWarning = message
    lastMoveWarningAt = now
  }
  return false
}

async function onDragChange(event, targetStatus) {
  if (!canWrite.value) {
    ElMessage.warning(writePermissionHint.value)
    await fetchTasks()
    return
  }
  let taskId = null
  let targetOrderIndex = null
  if (event.added) {
    taskId = event.added.element.id
    targetOrderIndex = event.added.newIndex
  } else if (event.moved) {
    taskId = event.moved.element.id
    targetOrderIndex = event.moved.newIndex
  } else {
    return
  }
  try {
    await request.put('/task/drag', { taskId, targetStatus, targetOrderIndex })
    const draggedTask = event.added?.element || event.moved?.element
    if (event.added && draggedTask) {
      if (targetStatus === 'IN_PROGRESS' && !draggedTask.assigneeId) ElMessage.success('任务已领取并开始')
      else if (targetStatus === 'DONE') ElMessage.success('任务已完成')
    }
    await fetchTasks()
  } catch {
    await fetchTasks()
  }
}

// 创建 / 编辑
function openCreate() {
  if (!canWrite.value) return ElMessage.warning(writePermissionHint.value)
  editingTask.value = null
  form.title = ''
  form.description = ''
  form.status = 'TODO'
  form.assigneeId = null
  form.dueDate = null
  form.startDate = null
  form.priority = 'MEDIUM'
  form.tags = []
  form.dependencyIds = []
  form.estimatedHours = null
  form.actualHours = null
  form.acceptanceCriteria = ''
  form.reviewRequired = false
  dialogVisible.value = true
}

function openEdit(task) {
  if (!canWrite.value) return ElMessage.warning(writePermissionHint.value)
  editingTask.value = task
  form.title = task.title || ''
  form.description = task.description || ''
  form.status = task.status || 'TODO'
  form.assigneeId = task.assigneeId ?? null
  form.dueDate = task.dueDate || null
  form.startDate = task.startDate || null
  form.priority = task.priority || 'MEDIUM'
  form.tags = taskTags(task)
  form.dependencyIds = String(task.dependencyIds || '').split(',').filter(Boolean).map(Number)
  form.estimatedHours = task.estimatedHours ?? null
  form.actualHours = task.actualHours ?? null
  form.acceptanceCriteria = task.acceptanceCriteria || ''
  form.reviewRequired = Boolean(task.reviewRequired)
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!canWrite.value) return ElMessage.warning(writePermissionHint.value)
  if (!form.title.trim()) { ElMessage.warning('请输入任务标题'); return }
  submitting.value = true
  try {
    if (editingTask.value) {
      const updatePayload = {
        id: editingTask.value.id,
        title: form.title,
        description: form.description,
        status: form.status,
        dueDate: form.dueDate || '',
        startDate: form.startDate || '',
        priority: form.priority,
        tags: form.tags.join(','),
        dependencyIds: form.dependencyIds.join(','),
        estimatedHours: form.estimatedHours,
        actualHours: form.actualHours,
        acceptanceCriteria: form.acceptanceCriteria
      }
      if (Boolean(editingTask.value.reviewRequired) !== form.reviewRequired) updatePayload.reviewRequired = form.reviewRequired
      if (isProjectOwner.value) {
        if (form.assigneeId) updatePayload.assigneeId = form.assigneeId
        else updatePayload.clearAssignee = true
      }
      await updateTask(updatePayload)
      ElMessage.success('任务已更新')
    } else {
      await createTask(projectId, form.title, form.description, undefined, form.dueDate || undefined,
        form.startDate || undefined, form.priority, form.tags.join(','), form.dependencyIds.join(','),
        form.estimatedHours ?? undefined, form.actualHours ?? undefined, form.acceptanceCriteria || undefined)
      ElMessage.success('任务已创建')
    }
    dialogVisible.value = false
    await fetchTasks()
  } finally { submitting.value = false }
}

const optimizationVisible = ref(false)
const optimizationLoading = ref(false)
const optimization = ref(null)
const optimizationOperationId = ref(null)
const optimizationRating = ref(0)
async function optimizeTask() {
  if (!canWrite.value) return ElMessage.warning(writePermissionHint.value)
  if (!selectedTask.value || optimizationLoading.value) return
  optimizationLoading.value = true
  try {
    const response = await optimizeTaskWithAi(selectedTask.value.id)
    optimization.value = response.data.data
    optimizationOperationId.value = Number(response.headers['x-ai-operation-id']) || null
    optimizationRating.value = 0
    optimizationVisible.value = true
  } finally { optimizationLoading.value = false }
}
async function applyOptimization() {
  if (!canWrite.value) return ElMessage.warning(writePermissionHint.value)
  if (!selectedTask.value || !optimization.value) return
  try {
    await updateTask({ id: selectedTask.value.id, title: optimization.value.title, description: optimization.value.description, acceptanceCriteria: optimization.value.acceptanceCriteria })
    if (optimizationOperationId.value) await markAiOperationApplied(optimizationOperationId.value)
    Object.assign(selectedTask.value, optimization.value)
    optimizationVisible.value = false
    ElMessage.success('已应用 AI 优化内容')
    await fetchTasks()
  } catch { /* 请求错误已由统一反馈处理 */ }
}

async function rateAi(operationId, rating) {
  if (!operationId || !rating) return
  await submitAiFeedback(operationId, rating)
  ElMessage.success('评价已记录')
}

// 附件：下载由带授权头的请求完成，确保只有项目成员能访问。
const attachments = ref([])
const attachmentInput = ref(null)
const attachmentPreviewVisible = ref(false)
const attachmentPreview = reactive({ url: '', kind: '', name: '' })
const previewableAttachments = computed(() => attachments.value.filter(isPreviewable))
function isPreviewable(attachment) { return attachment?.contentType?.startsWith('image/') || attachment?.contentType === 'application/pdf' || /\.pdf$/i.test(attachment?.originalName || '') }
function attachmentById(id) { return attachments.value.find(item => item.id === id) }
async function fetchAttachments(taskId) {
  try { attachments.value = (await listTaskAttachments(taskId)).data.data || [] }
  catch { attachments.value = [] }
}
async function handleAttachmentUpload(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || !selectedTask.value) return
  if (!canWrite.value) return ElMessage.warning(writePermissionHint.value)
  try {
    await uploadTaskAttachment(selectedTask.value.id, file)
    ElMessage.success('附件已上传')
    await fetchAttachments(selectedTask.value.id)
  } catch { /* 已由请求拦截器提示 */ }
}
async function downloadAttachment(attachment) {
  try {
    const response = await request.get(`/task/attachments/${attachment.id}/download`, { responseType: 'blob' })
    const url = URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a'); link.href = url; link.download = attachment.originalName; link.click()
    URL.revokeObjectURL(url)
  } catch { /* 已由请求拦截器提示 */ }
}
async function previewAttachment(attachment) {
  if (!attachment) return
  closeAttachmentPreview()
  const response = await request.get(`/task/attachments/${attachment.id}/download`, { params:{ inline:true }, responseType:'blob' })
  attachmentPreview.url = URL.createObjectURL(new Blob([response.data], { type:attachment.contentType || 'application/octet-stream' }))
  attachmentPreview.kind = attachment.contentType?.startsWith('image/') ? 'image' : 'pdf'
  attachmentPreview.name = attachment.originalName
  attachmentPreviewVisible.value = true
}
function closeAttachmentPreview() {
  if (attachmentPreview.url) URL.revokeObjectURL(attachmentPreview.url)
  Object.assign(attachmentPreview, { url:'', kind:'', name:'' })
}
async function removeAttachment(attachment) {
  if (!canWrite.value) return ElMessage.warning(writePermissionHint.value)
  try { await ElMessageBox.confirm(`附件“${attachment.originalName}”将移入回收站，之后可由有权限的成员恢复。`, '移入回收站', { type: 'warning', confirmButtonText: '移入回收站', cancelButtonText: '取消' }) }
  catch { return }
  try {
    await deleteTaskAttachment(attachment.id)
    ElMessage.success('附件已移入回收站')
    await fetchAttachments(selectedTask.value.id)
  } catch { /* 请求错误由统一反馈层显示 */ }
}
async function showDownloadLogs(attachment) {
  try {
    const logs = (await listAttachmentDownloadLogs(attachment.id)).data.data || []
    const content = logs.length ? logs.map(log => `${memberMap.value[log.downloaderId]?.nickname || `成员 #${log.downloaderId}`} · ${log.downloadedAt}`).join('<br>') : '暂无下载记录'
    await ElMessageBox.alert(content, `${attachment.originalName} 的下载记录`, { dangerouslyUseHTMLString: true, confirmButtonText: '关闭' })
  } catch { /* 非管理员或请求失败时由拦截器提示 */ }
}
function formatFileSize(size) {
  if (!size) return '0 B'
  return size < 1024 * 1024 ? `${Math.ceil(size / 1024)} KB` : `${(size / 1024 / 1024).toFixed(1)} MB`
}

async function handleDelete(task) {
  if (!canWrite.value) return ElMessage.warning(writePermissionHint.value)
  try { await ElMessageBox.confirm(`“${task.title}”及其子任务会一并移入回收站，任务依赖关系会保留，之后可以恢复。`, `移入回收站：${task.title}`, {
      type: 'warning', confirmButtonText: '移入回收站', cancelButtonText: '取消'
    }) }
  catch { return }
  try {
    await deleteTask(task.id)
    delete subtaskData[task.id]
    ElMessage.success('任务已移入回收站')
    await fetchTasks()
  } catch { /* 请求错误由统一反馈层显示 */ }
}

// AI 项目总结
const summaryDialogVisible = ref(false)
const summaryLoading = ref(false)
const summaryContent = ref('')

const renderedMarkdown = computed(() => {
  return summaryContent.value ? md.render(summaryContent.value) : ''
})

function openSummary() {
  summaryDialogVisible.value = true
  summaryLoading.value = true
  summaryContent.value = ''
  streamProjectSummary(projectId, {
    onChunk(chunk) { summaryContent.value += chunk },
    onDone() { summaryLoading.value = false },
    onError(err) {
      summaryLoading.value = false
      summaryContent.value = summaryContent.value || '生成失败'
      ElMessage.error('AI 总结生成失败: ' + (err.message || '未知错误'))
    }
  })
}

function closeSummary() {
  summaryDialogVisible.value = false
  summaryLoading.value = false
  summaryContent.value = ''
}

// WebSocket 实时同步
const MAX_RETRIES = 5
const BASE_DELAY_MS = 1000
const MAX_DELAY_MS = 30000

const ws = ref(null)
const wsConnected = ref(false)
let retryCount = 0
let reconnectTimer = null

function connectWebSocket() {
  if (ws.value && (ws.value.readyState === WebSocket.OPEN || ws.value.readyState === WebSocket.CONNECTING)) return
  const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
  const token = userStore.token
  const url = `${protocol}://${location.host}/ws/project/${projectId}?token=${encodeURIComponent(token || '')}`
  ws.value = new WebSocket(url)

  ws.value.onopen = () => { wsConnected.value = true; retryCount = 0 }
  ws.value.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data)
      if (msg.type === 'TASK_UPDATED') {
        fetchTasks()
        if (detailVisible.value && selectedTask.value) {
          fetchSubtasksForTask(selectedTask.value.id)
        }
      }
      if (msg.type === 'COMMENT_UPDATED' && detailVisible.value && selectedTask.value && (!msg.taskId || msg.taskId === selectedTask.value.id)) {
        loadCollaboration(selectedTask.value.id)
      }
      if (msg.type === 'NOTIFICATION_UPDATED') window.dispatchEvent(new CustomEvent('smartpm:notifications'))
    } catch { /* ignore */ }
  }
  ws.value.onclose = () => {
    wsConnected.value = false
    if (retryCount < MAX_RETRIES) {
      const delay = Math.min(BASE_DELAY_MS * Math.pow(2, retryCount), MAX_DELAY_MS)
      retryCount++
      reconnectTimer = setTimeout(connectWebSocket, delay)
    }
  }
  ws.value.onerror = () => {}
}

function disconnectWebSocket() {
  clearTimeout(reconnectTimer)
  reconnectTimer = null
  if (ws.value) {
    ws.value.onclose = null
    ws.value.onerror = null
    ws.value.onmessage = null
    ws.value.close()
    ws.value = null
  }
  wsConnected.value = false
  retryCount = 0
}

onMounted(async () => {
  await Promise.all([fetchProjectName(), fetchTasks(), fetchMembers()])
  if (['MY', 'TODAY', 'OVERDUE', 'BLOCKED'].includes(route.query.scope)) filters.scope = route.query.scope
  if (route.query.scope === 'DONE') mobileStatus.value = 'DONE'
  const requestedTask = Number(route.query.task || route.query.taskId)
  const task = requestedTask ? allTasks.value.find(item => item.id === requestedTask) : null
  if (task) {
    collaborationTab.value = route.query.tab === 'comments' ? 'comments' : collaborationTab.value
    openDetail(task)
  }
  connectWebSocket()
})

onUnmounted(() => {
  disconnectWebSocket()
  closeAttachmentPreview()
})
</script>

<style scoped>
.visually-hidden { position:absolute; width:1px; height:1px; padding:0; margin:-1px; overflow:hidden; clip:rect(0,0,0,0); white-space:nowrap; border:0; }
.csv-summary { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-bottom:12px; padding:12px 14px; border-radius:8px; background:var(--surface-subtle); }.csv-summary strong { color:var(--text-primary); }.csv-summary span,.csv-error { color:var(--danger); }.csv-valid { color:var(--success); }
.recurrence-row { display:grid; grid-template-columns:1fr 1fr; gap:14px; }.recurrence-row :deep(.el-input-number),.recurrence-row :deep(.el-date-editor) { width:100%; }.switch-line { display:flex; align-items:center; justify-content:space-between; min-height:44px; }
.board { min-height: 100vh; display: flex; flex-direction: column; }
.topbar {
  display: flex; justify-content: space-between; align-items: center;
  height: 56px; padding: 0 20px; flex-shrink: 0;
  background: #242321; border-bottom: 1px solid #3A3732;
}
.topbar-left { display: flex; align-items: center; gap: 8px; }
.topbar-left h3 { margin: 0; font-size: 16px; font-weight: 600; color: #F7F1E7; }
.sep { color: #81796E; }
.topbar-right { display: flex; align-items: center; gap: 8px; }
.avatar-dot {
  width: 26px; height: 26px; border-radius: 50%;
  background: #D58A22; color: #241D14;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 600;
}
.username { font-size: 13px; color: #B9B1A5; }

.board-main { flex: 1; overflow-x: auto; padding: 24px 30px 30px; }
.board-filters { display: flex; align-items: center; gap: 10px; max-width: 1280px; margin: 0 auto 16px; }
.board-filters :deep(.el-select) { width: 150px; }
.columns { display: flex; gap: 18px; min-width: 780px; height: 100%; max-width: 1280px; margin: 0 auto; }

.column {
  flex: 1; min-width: 260px; max-width: 360px;
  background: #2B2A28; border-radius: var(--radius);
  border: 1px solid #403D38; display: flex; flex-direction: column;
}
.column-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 16px; border-radius: var(--radius) var(--radius) 0 0; flex-shrink: 0;
}
.column-header.todo    { border-bottom: 2px solid #FDE68A; }
.column-header.progress { border-bottom: 2px solid #BFDBFE; }
.column-header.done    { border-bottom: 2px solid #A7F3D0; }
.col-title { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 600; color: #F7F1E7; }
.col-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.col-count {
  font-size: 11px; font-weight: 500; color: var(--text-tertiary);
  background: var(--border); border-radius: 10px; padding: 1px 7px; min-width: 18px; text-align: center;
}

.column-body {
  flex: 1; overflow-y: auto; padding: 8px 10px;
  display: flex; flex-direction: column; gap: 8px; min-height: 60px;
}
.column-empty {
  text-align: center; padding: 24px 12px; color: var(--text-tertiary);
  font-size: 13px; border: 1px dashed var(--border); border-radius: var(--radius-sm);
  margin: 0 10px 12px;
}

.task-card {
  display: flex; gap: 8px;
  background: #1F1F1E; border-radius: var(--radius-sm);
  padding: 14px; cursor: pointer; border: 1px solid #3A3732;
  box-shadow: var(--shadow-xs); transition: box-shadow 0.18s, border-color 0.18s;
}
.task-card:hover { box-shadow: var(--shadow-sm); border-color: var(--brand); }
.done-card .task-title { text-decoration: line-through; color: var(--text-tertiary); }

.card-grip {
  display: flex; flex-direction: column; gap: 2px; padding-top: 3px;
  flex-shrink: 0; opacity: 0.25;
}
.grip-dot {
  display: block; width: 3px; height: 3px; border-radius: 50%;
  background: var(--text-tertiary);
}

.card-content { flex: 1; min-width: 0; }
.task-title { margin: 0; font-size: 14px; font-weight: 500; color: #F7F1E7; line-height: 1.4; word-break: break-word; }
.task-desc {
  margin: 5px 0 0; font-size: 12px; color: var(--text-tertiary); line-height: 1.4;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.task-meta-chips { display: flex; flex-wrap: wrap; gap: 5px; margin: 8px 0 4px; }
.priority-chip, .task-label, .blocked-chip { display: inline-flex; align-items: center; border-radius: 10px; padding: 2px 7px; font-size: 10px; line-height: 16px; }
.priority-HIGH { background: #fee8e5; color: #be3f2a; }.priority-MEDIUM { background: #fff1d6; color: #a9650d; }.priority-LOW { background: #e8f0fa; color: #507394; }
.task-label { background: #edf1f5; color: #607080; }.blocked-chip { background: #fee8e5; color: #be3f2a; }
.task-assignment-row {
  display: flex; align-items: center; gap: 7px; flex-wrap: wrap; margin-top: 9px;
}
.task-assignment-row .role-tag { padding: 2px 7px; border-radius: 4px; font-size: 10px; }
.assignee-name { font-size: 11px; color: #B9B1A5; }
.assignee-name.pending { color: var(--text-tertiary); font-style: italic; }
.task-footer {
  display: flex; justify-content: space-between; align-items: center;
  margin-top: 12px; padding-top: 10px; border-top: 1px solid #3A3732;
}
.task-due { font-size: 11px; color: var(--text-tertiary); }
.task-due.overdue { color: #EF4444; font-weight: 500; }

.footer-actions { display: flex; gap: 6px; align-items: center; }

/* AI 初始任务按钮 */
.ai-init-btn {
  color: #E2A43A !important; border-color: #785A2E !important;
  background: #302A20 !important; font-weight: 600;
}
.ai-init-btn:hover { background: #3B3021 !important; border-color: #D58A22 !important; }
.ai-init-btn.is-disabled { opacity: 0.55; }
.subtask-badge {
  display: flex; align-items: center; gap: 6px; flex: 1; min-width: 0;
}
.subtask-minibar {
  flex: 1; height: 4px; max-width: 72px;
  background: var(--border); border-radius: 2px; overflow: hidden;
}
.subtask-minibar-fill {
  height: 100%; border-radius: 2px;
  background: var(--brand-gradient);
  transition: width 0.35s ease;
}
.subtask-minitext {
  font-size: 11px; font-weight: 500; color: var(--text-tertiary);
  white-space: nowrap; font-variant-numeric: tabular-nums;
}

.ghost {
  opacity: 0.4;
  background: var(--brand-light) !important;
  border: 2px dashed var(--brand) !important;
}

/* 详情弹窗 */
.detail-dialog :deep(.el-dialog__header) {
  padding: 26px 30px 18px;
  border-bottom: 1px solid var(--border-light);
}
.detail-dialog :deep(.el-dialog__body) {
  max-height: calc(92dvh - 150px);
  overflow-y: auto;
  padding: 22px 30px 30px;
}
.detail-dialog :deep(.el-dialog__headerbtn) { top: 18px; right: 20px; }
.detail-header { display: grid; gap: 18px; min-width: 0; }
.detail-heading { min-width: 0; padding-right: 36px; }
.detail-kicker { display: flex; align-items: center; gap: 10px; margin-bottom: 9px; }
.detail-status-tag {
  display: inline-flex; align-items: center; min-height: 24px; padding: 3px 10px; border-radius: 999px;
  font-size: 11px; font-weight: 700; line-height: 1; flex-shrink: 0;
}
.tag-todo { background: #FFFBEB; color: #B45309; }
.tag-progress { background: #EFF6FF; color: #1D4ED8; }
.tag-done { background: #ECFDF5; color: #047857; }
.detail-task-id { color: var(--text-tertiary); font-family: var(--font-mono); font-size: 11px; }
.detail-title {
  max-width: 30ch; margin: 0; color: var(--text-primary); font-size: 24px; font-weight: 720;
  line-height: 1.35; letter-spacing: -.025em; overflow-wrap: anywhere;
}
.detail-actions { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 12px; }
.detail-tool-actions,.detail-state-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; }
.detail-state-actions { justify-content: flex-end; }
.detail-actions :deep(.el-button) { margin-left: 0; }
.detail-actions :deep(.el-button .el-icon) { margin-right: 4px; }
.detail-body { min-width: 0; }
.detail-overview { margin-bottom: 18px; }
.detail-desc {
  margin: 0 0 18px; padding: 0 0 18px; border-bottom: 1px solid var(--border-light);
  color: var(--text-secondary); font-size: 14px; line-height: 1.75; white-space: pre-wrap;
}
.detail-desc.empty { color: var(--text-tertiary); font-style: italic; }
.detail-facts { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 9px; }
.detail-fact {
  display: grid; gap: 5px; min-width: 0; padding: 12px 14px;
  border: 1px solid var(--border-light); border-radius: 10px; background: var(--surface-subtle);
}
.detail-fact > span { color: var(--text-tertiary); font-size: 11px; }
.detail-fact > strong { display: flex; align-items: center; min-width: 0; color: var(--text-primary); font-size: 13px; font-weight: 650; overflow-wrap: anywhere; }
.priority-marker { display: inline-block; width: 7px; height: 7px; margin-right: 7px; border-radius: 2px; }
.priority-marker.priority-HIGH { background: var(--danger); }
.priority-marker.priority-MEDIUM { background: var(--todo); }
.priority-marker.priority-LOW { background: var(--progress); }
.detail-labels { display: grid; grid-template-columns: 52px 1fr; align-items: start; gap: 8px; margin-top: 12px; }
.detail-labels > span { padding-top: 3px; color: var(--text-tertiary); font-size: 11px; }
.detail-labels > div { display: flex; flex-wrap: wrap; gap: 6px; }
.detail-meta { display: flex; align-items: center; margin: 0 0 18px; font-size: 12px; color: var(--text-tertiary); }
.blocked-notice { margin: 0 0 14px; padding: 11px 13px; border-radius: 10px; background: #fff0ed; color: #bd482f; font-size: 12px; line-height: 1.6; }
.dependency-notice { display: grid; grid-template-columns: 72px 1fr; gap: 10px; margin: 0 0 14px; padding: 11px 13px; border-radius: 10px; background: var(--surface-subtle); font-size: 12px; }
.dependency-notice span { color: var(--text-tertiary); }.dependency-notice strong { color: var(--text-secondary); font-weight: 600; }
.acceptance-section { margin: 0 0 22px; padding: 14px 16px; border-radius: 10px; background: var(--surface-subtle); color: var(--text-secondary); font-size: 13px; }
.acceptance-section strong { color: var(--text-primary); font-size: 12px; }.acceptance-section p { margin: 7px 0 0; line-height: 1.7; white-space: pre-wrap; }
.delivery-panel,.time-panel { margin:0 0 18px; padding:16px; border:1px solid var(--border-light); border-radius:12px; background:#fff; }
.delivery-panel>header,.time-panel>header { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-bottom:14px; }
.delivery-panel>header div,.time-panel>header div { display:grid; gap:2px; }
.delivery-panel>header div>span,.time-panel>header div>span { color:#6f55c7; font-size:8px; font-weight:800; letter-spacing:.14em; }
.delivery-panel h3,.time-panel h3 { margin:0; font-size:14px; }
.acceptance-status { padding:4px 8px; border-radius:999px; color:var(--text-secondary); background:var(--surface-strong); font-size:9px; font-weight:750; }
.acceptance-status.pending,.acceptance-status.in_review{color:var(--brand-deep);background:var(--brand-light)}.acceptance-status.passed{color:var(--success);background:var(--success-soft)}.acceptance-status.rejected{color:var(--danger);background:var(--danger-bg)}
.acceptance-disabled { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:12px; border-radius:9px; background:var(--surface-subtle); }.acceptance-disabled p{margin:0;color:var(--text-secondary);font-size:11px}
.acceptance-checklist { display:grid; gap:6px; }.acceptance-checklist>label { display:grid; grid-template-columns:auto 1fr auto; align-items:center; gap:8px; min-height:34px; padding:3px 8px; border-radius:7px; background:var(--surface-subtle); font-size:11px; }.acceptance-checklist>label span.checked{text-decoration:line-through;color:var(--text-tertiary)}.acceptance-checklist>label button{border:0;color:var(--text-tertiary);background:transparent;cursor:pointer}.checklist-composer{display:flex;gap:7px;margin-top:4px}.review-actions{display:flex;flex-wrap:wrap;gap:7px;margin-top:12px}.review-trail{display:grid;gap:6px;margin-top:12px;padding-top:10px;border-top:1px solid var(--border-light)}.review-trail>span{display:grid;grid-template-columns:auto 1fr auto;gap:5px;color:var(--text-secondary);font-size:10px}.review-trail small{color:var(--text-tertiary)}.review-trail em{grid-column:1/-1;padding-left:8px;border-left:2px solid var(--border);font-style:normal;color:var(--text-tertiary)}
.time-panel>header>strong{font:750 20px var(--font-mono);color:var(--brand-deep)}.time-entry-form{display:grid;grid-template-columns:135px 110px 1fr auto;gap:7px}.time-entry-form>*{min-width:0}.time-entry-form :deep(.el-date-editor),.time-entry-form :deep(.el-input-number),.time-entry-form>.el-input{width:100%}.time-entry-list{display:grid;gap:5px;margin-top:12px}.time-entry-list>span{display:grid;grid-template-columns:86px 130px 1fr;gap:8px;padding:7px 9px;border-top:1px solid var(--border-light);color:var(--text-secondary);font-size:10px}.time-entry-list em{overflow:hidden;color:var(--text-tertiary);font-style:normal;text-overflow:ellipsis;white-space:nowrap}
.switch-line>span { display:grid; }.switch-line>span small { color:var(--text-tertiary); font-size:10px; }
.task-assignment { gap: 10px; min-height: 34px; padding: 5px 0; }

.subtask-section { border-top: 1px solid var(--border); padding-top: 18px; }
.subtask-section-header {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;
}
.subtask-section-title { display: flex; align-items: center; gap: 10px; }
.subtask-label { font-size: 14px; font-weight: 600; }
.subtask-summary { font-size: 12px; color: var(--text-tertiary); font-variant-numeric: tabular-nums; }

.subtask-progress-bar { margin-bottom: 14px; }
.progress-track {
  height: 6px; background: var(--border); border-radius: 3px; overflow: hidden;
}
.progress-fill {
  height: 100%; border-radius: 3px;
  background: var(--brand-gradient);
  transition: width 0.4s ease;
}

.subtask-item {
  padding: 10px 12px; border-radius: var(--radius-sm);
  margin-bottom: 4px; transition: background 0.15s;
}
.subtask-item:hover { background: #F8FAFC; }
.subtask-item.done { opacity: 0.65; }
.subtask-item.done .subtask-title { text-decoration: line-through; }
.subtask-item :deep(.el-checkbox) { display: flex; align-items: flex-start; }
.subtask-item :deep(.el-checkbox__label) { line-height: 1.4; }
.subtask-title { font-size: 13px; font-weight: 500; color: var(--text-primary); }
.subtask-desc {
  margin: 4px 0 0 24px; font-size: 12px; color: var(--text-tertiary); line-height: 1.4;
}
.subtask-meta {
  display: flex; align-items: center; gap: 10px; margin: 6px 0 0 24px;
}
.role-tag {
  display: inline-block; padding: 2px 8px; border-radius: 4px;
  font-size: 11px; font-weight: 600; color: #fff; white-space: nowrap;
}
.assignee-info {
  font-size: 12px; color: var(--text-secondary);
}
.assignee-info.pending {
  color: #94A3B8; font-style: italic;
}
.project-role-dialog :deep(.el-dialog) { border-radius: 16px; }
.project-role-header { text-align: center; padding: 4px 0 8px; }
.project-role-header h3 { margin: 8px 0 6px; color: var(--text-primary); }
.project-role-header p { margin: 0; color: var(--text-tertiary); font-size: 13px; }
.project-role-icon { font-size: 32px; }
.project-role-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.project-role-card { padding: 17px 8px; border: 1px solid var(--border); border-radius: 10px; background: var(--bg-surface); color: var(--text-primary); cursor: pointer; display: flex; flex-direction: column; align-items: center; gap: 7px; transition: .16s; }
.project-role-card:hover:not(:disabled) { border-color: var(--brand); transform: translateY(-2px); box-shadow: 0 5px 14px rgba(213,138,34,.18); }
.project-role-card span { font-size: 26px; }
.project-role-card strong { font-size: 13px; }
@media (max-width: 500px) { .project-role-grid { grid-template-columns: repeat(2, 1fr); } }
.subtask-empty {
  text-align: center; padding: 32px 12px; color: var(--text-tertiary); font-size: 13px;
}

/* 创建/编辑 */
.dialog-form .input-group { margin-bottom: 16px; }
.dialog-form label { display: block; font-size: 13px; font-weight: 500; color: var(--text-secondary); margin-bottom: 6px; }
.optional { font-weight: 400; color: var(--text-tertiary); font-size: 12px; }
.input-row { display: flex; gap: 12px; }
.input-row .input-group { flex: 1; }
.dialog-form :deep(.el-input__wrapper) { border-radius: var(--radius-sm); }
.attachment-section { margin-top: 22px; padding-top: 18px; border-top: 1px solid var(--border); }
.hidden-file-input { display: none; }
.attachment-tip { margin: 8px 0; color: var(--text-tertiary); font-size: 12px; }
.attachment-row { display: flex; align-items: center; gap: 8px; padding: 7px 0; border-bottom: 1px solid var(--border); font-size: 12px; }
.attachment-row > span { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.attachment-row small { color: var(--text-tertiary); }
.attachment-preview{display:grid;place-items:center;min-height:420px;max-height:72vh;overflow:auto;border-radius:10px;background:#eef1f5}.attachment-preview img{display:block;max-width:100%;max-height:70vh;object-fit:contain}.attachment-preview iframe{width:100%;height:70vh;border:0;background:#fff}.review-trail button{grid-column:1/-1;justify-self:start;padding:0;border:0;color:var(--brand);background:transparent;font-size:10px;cursor:pointer}.review-actions .el-select{width:180px}
.ai-plan-preview { max-height: 58vh; overflow-y: auto; padding-right: 6px; }.plan-overview { margin: 0 0 16px; color: var(--text-secondary); line-height: 1.6; }.ai-plan-preview section { margin-top: 18px; }.ai-plan-preview h4 { margin: 0 0 8px; font-size: 14px; }.plan-stage-list { display: flex; flex-wrap: wrap; gap: 7px; }.plan-stage-list span { padding: 5px 8px; border-radius: 8px; background: #fdf1db; color: #965d14; font-size: 12px; }.plan-task, .plan-risk { padding: 10px 12px; margin-top: 7px; border: 1px solid var(--border); border-radius: 8px; }.plan-task strong, .plan-risk strong { display: block; font-size: 13px; }.plan-task span, .plan-task small, .plan-risk small { display: block; margin-top: 4px; color: var(--text-tertiary); font-size: 12px; }.plan-task p, .plan-risk p { margin: 5px 0; color: var(--text-secondary); font-size: 12px; line-height: 1.5; }.optimization-preview label { display: block; margin: 14px 0 5px; color: var(--text-tertiary); font-size: 12px; }.optimization-preview label:first-child { margin-top: 0; }.optimization-preview p { margin: 0; padding: 10px 12px; border-radius: 8px; background: #f4f7fa; color: var(--text-secondary); font-size: 13px; line-height: 1.6; }.optimization-ok { color: #287248 !important; background: #edf8f0 !important; }

/* AI 总结 */
.summary-body { min-height: 180px; max-height: 60vh; overflow-y: auto; }
.summary-loading {
  display: flex; flex-direction: column; align-items: center; gap: 12px;
  padding: 48px 0; color: var(--text-tertiary); font-size: 14px;
}
.markdown-body {
  color: var(--text-primary); line-height: 1.7; font-size: 14px;
}
.markdown-body :deep(h1) { font-size: 1.5em; margin: 0.6em 0 0.4em; border-bottom: 2px solid var(--border); padding-bottom: 0.3em; }
.markdown-body :deep(h2) { font-size: 1.25em; margin: 0.6em 0 0.35em; border-bottom: 1px solid var(--border); padding-bottom: 0.25em; }
.markdown-body :deep(h3) { font-size: 1.1em; margin: 0.5em 0 0.3em; }
.markdown-body :deep(p) { margin: 0.4em 0; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 1.5em; margin: 0.4em 0; }
.markdown-body :deep(li) { margin: 0.2em 0; }
.markdown-body :deep(code) {
  background: #F1F5F9; padding: 2px 6px; border-radius: 4px;
  font-family: var(--font-mono); font-size: 0.9em;
}
.markdown-body :deep(pre) {
  background: #1E293B; color: #E2E8F0; padding: 14px 16px;
  border-radius: var(--radius-sm); overflow-x: auto; margin: 0.6em 0;
}
.markdown-body :deep(pre code) { background: none; padding: 0; color: inherit; }
.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--brand); padding: 4px 14px; margin: 0.5em 0;
  color: var(--text-secondary); background: #F8FAFC; border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
}
.markdown-body :deep(table) { border-collapse: collapse; width: 100%; margin: 0.6em 0; }
.markdown-body :deep(th), .markdown-body :deep(td) { border: 1px solid var(--border); padding: 8px 12px; text-align: left; }
.markdown-body :deep(th) { background: #F8FAFC; font-weight: 600; }
.markdown-body :deep(strong) { font-weight: 600; color: var(--text-primary); }
.markdown-body :deep(hr) { border: none; border-top: 1px solid var(--border); margin: 1em 0; }

</style>

<style scoped>
.board { min-height: 100dvh; display: block; }
.board-main { width: min(1380px, 100%); margin: 0 auto; padding: 42px clamp(20px, 3.6vw, 52px) 60px; overflow: hidden; }
.board-filters { display: flex; align-items: center; gap: 10px; max-width: none; margin: 0 0 18px; padding: 12px; border: 1px solid var(--border-light); border-radius: var(--radius); background: var(--surface); }
.board-filters :deep(.el-select) { width: 156px; }
.workflow-guide { display:flex; align-items:center; justify-content:space-between; gap:16px; margin:0 0 16px; padding:10px 13px; border:1px solid #d8e0f2; border-radius:10px; background:#f8faff; }
.workflow-guide p { margin:0; color:var(--text-tertiary); font-size:11px; }
.workflow-path { display:flex; align-items:center; gap:7px; color:var(--text-secondary); font-size:11px; white-space:nowrap; }
.workflow-path strong { margin-right:3px; color:var(--brand-deep); font-size:11px; }
.workflow-path span { padding:3px 8px; border-radius:999px; background:var(--surface); box-shadow:inset 0 0 0 1px var(--border-light); font-weight:650; }
.workflow-path i { color:#8fa0bc; font-style:normal; }
.batch-toolbar { display:flex; align-items:center; flex-wrap:wrap; gap:10px; margin:-8px 0 18px; padding:12px 14px; border:1px solid #b9c8f5; border-radius:var(--radius); background:var(--brand-soft); }.batch-toolbar>span { color:var(--brand-deep); font-size:12px; font-weight:700; }.batch-toolbar :deep(.el-select) { width:140px; }.batch-toolbar :deep(.el-date-editor) { width:156px; }.task-select { flex:0 0 auto; }
.mobile-status-tabs { display: none; }
.columns { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; min-width: 0; max-width: none; height: auto; margin: 0; align-items: start; }
.column { min-width: 0; max-width: none; min-height: 420px; border: 1px solid var(--border-light); border-radius: var(--radius); background: var(--surface-subtle); }
.column-header { padding: 14px 15px; border-radius: var(--radius) var(--radius) 0 0; background: var(--surface); }
.column-header.todo { border-bottom: 2px solid #e7b66e; }
.column-header.progress { border-bottom: 2px solid #7ca9de; }
.column-header.done { border-bottom: 2px solid #75b99a; }
.col-title { color: var(--text-primary); font-size: 13px; font-weight: 680; }
.col-count { color: var(--text-secondary); background: var(--surface-strong); }
.column-body { padding: 10px; gap: 9px; overflow: visible; }
.column-empty { border-color: var(--border); background: var(--surface); }
.task-card { gap: 9px; padding: 14px; border: 1px solid var(--border-light); border-radius: 10px; color: var(--text-primary); background: var(--surface); box-shadow: var(--shadow-xs); }
.task-card:hover { border-color: #aab9eb; box-shadow: var(--shadow-sm); }
.task-title { color: var(--text-primary); font-weight: 650; }
.task-desc { color: var(--text-tertiary); }
.card-grip { opacity: .36; cursor:grab; touch-action:none; }
.card-grip:active { cursor:grabbing; }
.grip-dot { background: var(--text-tertiary); }
.task-footer { border-top-color: var(--border-light); }
.footer-actions { flex-wrap:wrap; justify-content:flex-end; }
.claim-button { --el-button-size:26px; padding-inline:9px; font-weight:650; }
.field-help { display:block; margin-top:5px; color:var(--text-tertiary); font-size:10px; line-height:1.45; }
.assignee-name { color: var(--text-secondary); }
.priority-chip,.task-label,.blocked-chip { border-radius: 999px; }
.priority-HIGH { color: var(--danger); background: var(--danger-bg); }
.priority-MEDIUM { color: var(--todo); background: var(--todo-bg); }
.priority-LOW { color: var(--progress); background: var(--progress-bg); }
.task-label { color: var(--text-secondary); background: var(--surface-strong); }
.blocked-chip { color: var(--danger); background: var(--danger-bg); }
.role-tag { border-radius: 999px; }
.subtask-minibar-fill,.progress-fill { background: var(--brand); }
.optimization-preview p { background: var(--surface-subtle); }
.blocked-notice { color: var(--danger); background: var(--danger-bg); }
.plan-stage-list span { color: var(--brand-deep); background: var(--brand-light); }
.plan-task,.plan-risk { border-color: var(--border-light); background: var(--surface); }
.optimization-ok { color: var(--done) !important; background: var(--done-bg) !important; }
.project-role-header { text-align: left; }
.project-role-header h3 { margin-top: 0; }
.project-role-card { align-items: flex-start; padding: 16px; border-color: var(--border); border-radius: var(--radius); background: var(--surface); }
.project-role-card:hover:not(:disabled) { border-color: var(--brand); background: var(--brand-soft); box-shadow: none; }
.project-role-card span { color: var(--brand); font-family: var(--font-mono); font-size: 18px; font-weight: 750; }
.attachment-row > span { display: inline-flex; align-items: center; gap: 6px; }
.collaboration-section { margin-top:20px; padding-top:8px; border-top:1px solid var(--border-light); }
.comment-list { min-height:72px; max-height:280px; overflow:auto; }.comment-item { display:grid; grid-template-columns:34px 1fr auto; gap:10px; padding:12px 0; border-bottom:1px solid var(--border-light); }.comment-avatar { display:grid; place-items:center; width:32px; height:32px; border-radius:50%; color:var(--brand-deep); background:var(--brand-light); font-size:12px; font-weight:700; }.comment-copy { min-width:0; }.comment-copy header { display:flex; align-items:center; gap:8px; }.comment-copy strong { color:var(--text-primary); font-size:12px; }.comment-copy time,.activity-item time { color:var(--text-tertiary); font-size:10px; }.comment-copy p { margin:5px 0 0; color:var(--text-secondary); font-size:13px; line-height:1.65; white-space:pre-wrap; overflow-wrap:anywhere; }.comment-composer { display:grid; gap:9px; margin-top:14px; }.comment-composer .el-button { justify-self:end; }.collaboration-empty,.viewer-note { margin:0; padding:24px 8px; color:var(--text-tertiary); font-size:12px; text-align:center; }
.activity-timeline { position:relative; min-height:72px; max-height:320px; overflow:auto; padding-left:5px; }.activity-timeline::before { position:absolute; top:12px; bottom:12px; left:10px; width:1px; background:linear-gradient(var(--brand),var(--border-light)); content:''; }.activity-item { position:relative; display:grid; grid-template-columns:12px 1fr; gap:12px; padding:10px 0; }.activity-item>i { z-index:1; width:9px; height:9px; margin-top:5px; border:2px solid var(--surface); border-radius:50%; background:var(--brand); box-shadow:0 0 0 1px var(--brand); }.activity-item p { margin:0 0 3px; color:var(--text-secondary); font-size:12px; }.activity-item strong { color:var(--text-primary); }
.ai-feedback { display:flex; align-items:center; justify-content:flex-end; gap:10px; margin-top:14px; padding-top:12px; border-top:1px solid var(--border-light); color:var(--text-tertiary); font-size:11px; }
.mobile-status-tabs button:active,.task-card:active,.project-role-card:active { transform: translateY(1px); }
@media (max-width: 980px) {
  .board-main { padding-inline: 24px; }
  .columns { gap: 12px; }
  .task-card { padding: 12px; }
}
@media (max-width: 767px) {
  .board-main { padding: 26px 16px 48px; overflow: hidden; }
  .board-filters { display: grid; grid-template-columns: 1fr 1fr; padding: 10px; }
  .board-filters :deep(.el-select) { width: 100%; }
  .board-filters :deep(.el-button) { margin-left: 0; justify-self: start; }
  .batch-toolbar { align-items:stretch; }.batch-toolbar>* { width:100%!important; }
  .workflow-guide { align-items:flex-start; flex-direction:column; gap:7px; }
  .workflow-guide p { line-height:1.5; }
  .mobile-status-tabs { display: grid; grid-template-columns: repeat(3, 1fr); gap: 4px; margin: 0 0 14px; padding: 4px; border: 1px solid var(--border-light); border-radius: 10px; background: var(--surface); }
  .mobile-status-tabs button { min-width: 0; min-height: 38px; border: 0; border-radius: 7px; color: var(--text-tertiary); background: transparent; font-size: 12px; font-weight: 650; cursor: pointer; }
  .mobile-status-tabs button.active { color: var(--brand-deep); background: var(--brand-light); }
  .mobile-status-tabs span { margin-left: 3px; font-size: 10px; }
  .columns { display: block; min-width: 0; }
  .column { display: none; width: 100%; min-width: 0; min-height: 360px; max-width: none; }
  .column.mobile-active { display: flex; }
  .detail-dialog :deep(.el-dialog__header) { padding: 22px 20px 16px; }
  .detail-dialog :deep(.el-dialog__body) { max-height: calc(92dvh - 165px); padding: 18px 20px 24px; }
  .detail-header { display: grid; gap: 15px; }
  .detail-title { max-width: none; min-width: 0; font-size: 21px; }
  .detail-actions { grid-template-columns: 1fr; }
  .detail-state-actions { justify-content: flex-start; }
  .detail-facts { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .input-row { display: grid; grid-template-columns: 1fr; gap: 0; }
  .project-role-grid { grid-template-columns: repeat(2, 1fr); gap: 9px; }
  .attachment-row { align-items: flex-start; flex-wrap: wrap; }
  .attachment-row > span { flex-basis: calc(100% - 80px); }
  .time-entry-form { grid-template-columns:1fr 1fr; }.time-entry-form .el-input{grid-column:1/-1}.time-entry-list>span{grid-template-columns:75px 1fr}.time-entry-list em{grid-column:1/-1}
}
@media (max-width: 420px) {
  .board-filters { grid-template-columns: 1fr; }
  .project-role-grid { grid-template-columns: 1fr 1fr; }
  .detail-dialog :deep(.el-dialog__header) { padding-inline: 16px; }
  .detail-dialog :deep(.el-dialog__body) { padding-inline: 16px; }
  .detail-tool-actions,.detail-state-actions { display: grid; grid-template-columns: 1fr 1fr; }
  .detail-actions :deep(.el-button) { width: 100%; }
}
</style>
