package com.smartpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartpm.common.exception.BusinessException;
import com.smartpm.common.utils.UserHolder;
import com.smartpm.entity.Project;
import com.smartpm.entity.ProjectMember;
import com.smartpm.entity.Task;
import com.smartpm.entity.User;
import com.smartpm.mapper.ProjectMapper;
import com.smartpm.mapper.ProjectMemberMapper;
import com.smartpm.mapper.ProjectMilestoneMapper;
import com.smartpm.mapper.TaskMapper;
import com.smartpm.mapper.TaskAttachmentMapper;
import com.smartpm.mapper.AttachmentDownloadLogMapper;
import com.smartpm.mapper.UserMapper;
import com.smartpm.service.AIService;
import com.smartpm.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.security.SecureRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private static final String INVITE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int INVITE_CODE_LENGTH = 8;
    private static final SecureRandom INVITE_CODE_RANDOM = new SecureRandom();

    private final ProjectMapper projectMapper;
    private final TaskMapper taskMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectMilestoneMapper milestoneMapper;
    private final TaskAttachmentMapper attachmentMapper;
    private final AttachmentDownloadLogMapper downloadLogMapper;
    private final UserMapper userMapper;
    private final AIService aiService;

    @Override
    public Project create(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("项目名称不能为空");
        }

        Project project = new Project();
        project.setName(name);
        project.setDescription(description);
        project.setInviteCode(generateUniqueInviteCode());
        project.setCreatorId(UserHolder.getUserId());
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());

        projectMapper.insert(project);

        ProjectMember owner = new ProjectMember();
        owner.setProjectId(project.getId());
        owner.setUserId(UserHolder.getUserId());
        owner.setIdentity(UserHolder.get().getIdentity());
        owner.setPermission("PROJECT_ADMIN");
        projectMemberMapper.insert(owner);
        return project;
    }

    @Override
    public List<Project> list() {
        Long userId = UserHolder.getUserId();
        List<Long> memberProjectIds = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserId, userId))
                .stream().map(ProjectMember::getProjectId).toList();
        LambdaQueryWrapper<Project> query = new LambdaQueryWrapper<Project>().isNull(Project::getDeletedAt);
        query.and(group -> {
            group.eq(Project::getCreatorId, userId);
            if (!memberProjectIds.isEmpty()) group.or().in(Project::getId, memberProjectIds);
        });
        return projectMapper.selectList(query.orderByDesc(Project::getCreatedAt));
    }

    @Override
    public Project update(Long id, String name, String description) {
        Project project = projectMapper.selectById(id);
        if (project == null || project.getDeletedAt() != null) {
            throw new BusinessException("项目不存在");
        }
        if (!project.getCreatorId().equals(UserHolder.getUserId()) && !canManageMembers(id)) {
            throw new BusinessException("无权修改此项目");
        }
        if (name != null && !name.isBlank()) {
            project.setName(name);
        }
        if (description != null) {
            project.setDescription(description);
        }
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);
        return project;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        if (!project.getCreatorId().equals(UserHolder.getUserId())) {
            throw new BusinessException(403, "只有项目负责人可以删除项目");
        }

        if (project.getDeletedAt() != null) throw new BusinessException("项目已在回收站中");
        project.setDeletedAt(LocalDateTime.now());
        project.setDeletedBy(UserHolder.getUserId());
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);
    }

    @Override
    public void assertProjectAccess(Long projectId, boolean write) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) throw new BusinessException("项目不存在或已移入回收站");
        Long userId = UserHolder.getUserId();
        if (project.getCreatorId().equals(userId)) return;
        ProjectMember member = projectMemberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId).eq(ProjectMember::getUserId, userId));
        if (member == null) throw new BusinessException("您不是该项目成员");
        if (write && "VIEWER".equals(member.getPermission())) {
            throw new BusinessException("只读成员不能修改项目内容");
        }
    }

    @Override
    public boolean canManageMembers(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) return false;
        if (project.getCreatorId().equals(UserHolder.getUserId())) return true;
        ProjectMember member = projectMemberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId).eq(ProjectMember::getUserId, UserHolder.getUserId()));
        return member != null && "PROJECT_ADMIN".equals(member.getPermission());
    }

    @Override
    public Project getByIdForAccess(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) throw new BusinessException("项目不存在或已移入回收站");
        return project;
    }

    @Override
    @Transactional
    public void inviteMember(Long projectId, String username, String identity, String permission) {
        requireManager(projectId);
        User user = findUser(username);
        ProjectMember exists = projectMemberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId).eq(ProjectMember::getUserId, user.getId()));
        if (exists != null) throw new BusinessException("该用户已经是项目成员");
        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setUserId(user.getId());
        member.setIdentity(validIdentity(identity, user.getIdentity()));
        member.setPermission(validPermission(permission));
        member.setJoinedAt(LocalDateTime.now());
        projectMemberMapper.insert(member);
    }

    @Override
    public void updateMember(Long projectId, Long userId, String identity, String permission) {
        requireManager(projectId);
        Project project = projectMapper.selectById(projectId);
        if (project.getCreatorId().equals(userId)) throw new BusinessException("项目负责人请使用转移负责人操作");
        ProjectMember member = getMember(projectId, userId);
        if (identity != null) member.setIdentity(validIdentity(identity, null));
        if (permission != null) member.setPermission(validPermission(permission));
        projectMemberMapper.updateById(member);
    }

    @Override
    public void removeMember(Long projectId, Long userId) {
        requireManager(projectId);
        Project project = projectMapper.selectById(projectId);
        if (project.getCreatorId().equals(userId)) throw new BusinessException("不能移除项目负责人");
        projectMemberMapper.delete(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId).eq(ProjectMember::getUserId, userId));
    }

    @Override
    @Transactional
    public void transferOwner(Long projectId, Long userId) {
        requireManager(projectId);
        Project project = projectMapper.selectById(projectId);
        getMember(projectId, userId);
        Long oldOwnerId = project.getCreatorId();
        ProjectMember oldOwner = getOrCreateOwner(projectId, oldOwnerId);
        oldOwner.setPermission("PROJECT_ADMIN");
        projectMemberMapper.updateById(oldOwner);
        ProjectMember newOwner = getMember(projectId, userId);
        newOwner.setPermission("PROJECT_ADMIN");
        projectMemberMapper.updateById(newOwner);
        project.setCreatorId(userId);
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);
    }

    @Override
    @Transactional
    public String getInviteCode(Long projectId) {
        requireManager(projectId);
        Project project = projectMapper.selectById(projectId);
        if (project.getInviteCode() == null || project.getInviteCode().isBlank()) {
            project.setInviteCode(generateUniqueInviteCode());
            projectMapper.updateById(project);
        }
        return project.getInviteCode();
    }

    @Override
    @Transactional
    public Project joinByInviteCode(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) throw new BusinessException("请输入项目邀请码");
        String normalizedCode = inviteCode.trim().toUpperCase(Locale.ROOT);
        Project project = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getInviteCode, normalizedCode).isNull(Project::getDeletedAt));
        if (project == null) throw new BusinessException("邀请码无效，请向项目负责人确认");

        Long userId = UserHolder.getUserId();
        ProjectMember existing = projectMemberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, project.getId()).eq(ProjectMember::getUserId, userId));
        if (existing != null) throw new BusinessException("您已经加入该项目");

        ProjectMember member = new ProjectMember();
        member.setProjectId(project.getId());
        member.setUserId(userId);
        member.setPermission("MEMBER");
        member.setJoinedAt(LocalDateTime.now());
        projectMemberMapper.insert(member);
        return project;
    }

    @Override
    public void updateMyProjectIdentity(Long projectId, String identity) {
        assertProjectAccess(projectId, false);
        ProjectMember member = getMember(projectId, UserHolder.getUserId());
        member.setIdentity(validIdentity(identity, null));
        projectMemberMapper.updateById(member);
    }

    private void requireManager(Long projectId) {
        if (!canManageMembers(projectId)) throw new BusinessException("只有项目管理员可以管理成员");
    }

    private User findUser(String username) {
        if (username == null || username.isBlank()) throw new BusinessException("请输入用户名");
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username.trim()));
        if (user == null) throw new BusinessException("用户不存在，请确认用户名");
        return user;
    }

    private ProjectMember getMember(Long projectId, Long userId) {
        ProjectMember member = projectMemberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId).eq(ProjectMember::getUserId, userId));
        if (member == null) throw new BusinessException("该用户不是项目成员");
        return member;
    }

    private ProjectMember getOrCreateOwner(Long projectId, Long userId) {
        ProjectMember member = projectMemberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId).eq(ProjectMember::getUserId, userId));
        if (member != null) return member;
        member = new ProjectMember();
        member.setProjectId(projectId); member.setUserId(userId); member.setPermission("PROJECT_ADMIN");
        member.setJoinedAt(LocalDateTime.now()); projectMemberMapper.insert(member);
        return member;
    }

    private String validIdentity(String identity, String fallback) {
        String value = identity == null || identity.isBlank() ? fallback : identity.trim().toUpperCase(Locale.ROOT);
        List<String> allowed = List.of("PROJECT_MANAGER", "PRODUCT_MANAGER", "FRONTEND_DEV", "BACKEND_DEV", "QA_TESTER", "UI_DESIGNER");
        if (value == null || !allowed.contains(value)) throw new BusinessException("无效的岗位身份");
        return value;
    }

    private String validPermission(String permission) {
        String value = permission == null || permission.isBlank() ? "MEMBER" : permission;
        if (!List.of("PROJECT_ADMIN", "MEMBER", "VIEWER").contains(value)) throw new BusinessException("无效的成员权限");
        return value;
    }

    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder code = new StringBuilder(INVITE_CODE_LENGTH);
            for (int index = 0; index < INVITE_CODE_LENGTH; index++) {
                code.append(INVITE_CODE_CHARS.charAt(INVITE_CODE_RANDOM.nextInt(INVITE_CODE_CHARS.length())));
            }
            String candidate = code.toString();
            if (projectMapper.selectCount(new LambdaQueryWrapper<Project>()
                    .eq(Project::getInviteCode, candidate)) == 0) {
                return candidate;
            }
        }
        throw new BusinessException("邀请码生成失败，请重试");
    }

    // ── AI 项目周报 ──

    @Override
    public Flux<String> generateSummary(Long projectId) {
        try {
            Project project = projectMapper.selectById(projectId);
            if (project == null || project.getDeletedAt() != null) {
                throw new BusinessException("项目不存在");
            }
            log.info("[AI-Summary] 项目: id={}, name={}", projectId, project.getName());

            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);

            // 查询全部任务，MyBatis-Plus 返回空列表而非 null，此处显式兜底
            List<Task> allTasks = taskMapper.selectList(
                    new LambdaQueryWrapper<Task>().eq(Task::getProjectId, projectId));
            if (allTasks == null) {
                allTasks = new ArrayList<>();
            }
            log.info("[AI-Summary] 查询到 {} 个任务（含子任务）", allTasks.size());

            // 近 7 天完成的任务 — 逐个判空，防止 NPE
            List<Task> doneTasks = new ArrayList<>();
            List<Task> inProgressTasks = new ArrayList<>();
            List<Task> overdueTasks = new ArrayList<>();

            LocalDate today = LocalDate.now();
            for (Task t : allTasks) {
                if (t == null) continue;
                String status = t.getStatus();
                if (status == null) continue;

                if ("DONE".equals(status) && t.getUpdatedAt() != null
                        && t.getUpdatedAt().isAfter(sevenDaysAgo)) {
                    doneTasks.add(t);
                } else if ("IN_PROGRESS".equals(status)) {
                    inProgressTasks.add(t);
                } else if (!"DONE".equals(status) && t.getDueDate() != null
                        && t.getDueDate().isBefore(today)) {
                    overdueTasks.add(t);
                }
            }

            log.info("[AI-Summary] 数据分类 — 已完成:{} | 进行中:{} | 逾期:{}",
                    doneTasks.size(), inProgressTasks.size(), overdueTasks.size());

            // 无任务数据时，生成项目启动寄语而非硬编码文案
            if (doneTasks.isEmpty() && inProgressTasks.isEmpty() && overdueTasks.isEmpty()) {
                log.info("[AI-Summary] 无任务数据，调用 AI 生成项目启动寄语");
                String kickoffPrompt = buildKickoffPrompt(project);
                return aiService.streamChat(kickoffPrompt);
            }

            String prompt = buildSummaryPrompt(project.getName(), doneTasks, inProgressTasks, overdueTasks);
            log.info("[AI-Summary] Prompt 长度: {} 字符，开始调用 AI...", prompt.length());
            return aiService.streamChat(prompt);

        } catch (Exception e) {
            log.error("[AI-Summary] 生成项目总结失败，具体原因为：", e);
            return Flux.error(e);
        }
    }

    /**
     * 无历史数据时的项目启动寄语 Prompt。
     * 调用 AI 生成专业、激励性的启动文案和开发规划。
     */
    private String buildKickoffPrompt(Project project) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个资深的项目经理和敏捷教练。\n\n");
        sb.append("你面前有一个刚刚启动的全新项目，目前还没有任何历史完工记录。\n");
        sb.append("请为团队撰写一份高质量的项目启动文档（Markdown 格式），包含以下内容：\n\n");
        sb.append("【项目名称】").append(project.getName()).append("\n");
        if (project.getDescription() != null && !project.getDescription().isBlank()) {
            sb.append("【项目描述】").append(project.getDescription()).append("\n");
        }
        sb.append("\n");
        sb.append("【输出要求】\n");
        sb.append("1. 项目启动寄语：一段温暖又充满力量的开场白，欢迎团队开启新项目\n");
        sb.append("2. 核心目标展望：根据项目名称和描述，推测并列出 3-5 个关键目标\n");
        sb.append("3. 推荐的开发规划：按阶段给出合理的开发路线图（建议 3-4 个阶段，每阶段给出关键任务建议）\n");
        sb.append("4. 敏捷实践建议：给出团队协作、站会节奏、看板使用等方面的实用建议\n");
        sb.append("5. 结尾金句：一句鼓舞人心的名言或团队格言\n\n");
        sb.append("【格式要求】\n");
        sb.append("- 使用专业的 Markdown 格式，层次分明\n");
        sb.append("- 总字数控制在 500-800 字\n");
        sb.append("- 语气专业但不失亲切，适合在团队内部会议上展示\n");
        sb.append("- 直接输出 Markdown，不要加任何开场白或结束语");
        return sb.toString();
    }

    /**
     * 构造项目周报 Prompt。
     */
    private String buildSummaryPrompt(String projectName,
                                      List<Task> doneTasks,
                                      List<Task> inProgressTasks,
                                      List<Task> overdueTasks) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个专业的项目经理。请根据以下项目数据，生成一份简洁、专业的项目周报（Markdown格式）。\n\n");
        sb.append("【项目名称】").append(projectName).append("\n\n");

        if (!doneTasks.isEmpty()) {
            sb.append("## 本周已完成的任务\n");
            for (Task t : doneTasks) {
                if (t == null) continue;
                sb.append("- ").append(t.getTitle());
                if (t.getDescription() != null && !t.getDescription().isBlank()) {
                    sb.append("（").append(t.getDescription()).append("）");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (!inProgressTasks.isEmpty()) {
            sb.append("## 进行中的任务\n");
            for (Task t : inProgressTasks) {
                if (t == null) continue;
                sb.append("- ").append(t.getTitle());
                if (t.getDueDate() != null) {
                    sb.append(" | 截止: ").append(t.getDueDate());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (!overdueTasks.isEmpty()) {
            sb.append("## 已逾期的任务\n");
            for (Task t : overdueTasks) {
                if (t == null) continue;
                sb.append("- ").append(t.getTitle());
                if (t.getDueDate() != null) {
                    sb.append(" | 截止: ").append(t.getDueDate());
                }
                sb.append(" | 当前状态: ")
                        .append("IN_PROGRESS".equals(t.getStatus()) ? "进行中" : "待办");
                sb.append("\n");
            }
            sb.append("\n");
        }

        sb.append("【周报要求】\n");
        sb.append("1. 使用 Markdown 格式，包含：总体概述、本周成果、进行中工作、风险与逾期提醒、下周展望\n");
        sb.append("2. 语言专业但不过于正式，适合团队内部传阅\n");
        sb.append("3. 对于逾期的任务要温和地指出风险\n");
        sb.append("4. 字数控制在500字以内，重点突出\n");
        sb.append("5. 直接输出 Markdown，不要加任何开场白或结束语");
        return sb.toString();
    }
}
