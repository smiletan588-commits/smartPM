package com.smartpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartpm.common.exception.BusinessException;
import com.smartpm.dto.*;
import com.smartpm.entity.*;
import com.smartpm.mapper.*;
import com.smartpm.service.*;
import com.smartpm.vo.CsvPreviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class TaskCsvServiceImpl implements TaskCsvService {
    private static final List<String> HEADERS = List.of("任务标题", "状态", "优先级", "负责人用户名", "开始日期", "截止日期", "预计工时", "实际工时", "标签", "前置任务标题");
    private static final Set<String> STATUSES = Set.of("TODO", "IN_PROGRESS", "DONE");
    private static final Set<String> PRIORITIES = Set.of("HIGH", "MEDIUM", "LOW");
    private final ProjectService projectService;
    private final TaskService taskService;
    private final TaskMapper taskMapper;
    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;

    @Override
    public byte[] export(Long projectId) {
        projectService.assertProjectAccess(projectId, false);
        List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>().eq(Task::getProjectId, projectId)
                .isNull(Task::getParentId).isNull(Task::getDeletedAt).orderByAsc(Task::getOrderIndex));
        Set<Long> userIds = tasks.stream().map(Task::getAssigneeId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> users = userIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
        Map<Long, String> titles = tasks.stream().collect(Collectors.toMap(Task::getId, Task::getTitle));
        StringBuilder csv = new StringBuilder("\uFEFF").append(HEADERS.stream().map(this::cell).collect(Collectors.joining(","))).append("\r\n");
        for (Task task : tasks) {
            String deps = parseIds(task.getDependencyIds()).stream().map(id -> titles.getOrDefault(id, "已删除任务")).collect(Collectors.joining("|"));
            List<String> row = List.of(safe(task.getTitle()), safe(task.getStatus()), safe(task.getPriority()),
                    safe(users.get(task.getAssigneeId())), safe(task.getStartDate()), safe(task.getDueDate()), safe(task.getEstimatedHours()),
                    safe(task.getActualHours()), safe(task.getTags()), deps);
            csv.append(row.stream().map(this::cell).collect(Collectors.joining(","))).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public CsvPreviewVO preview(Long projectId, MultipartFile file) {
        projectService.assertProjectAccess(projectId, true);
        if (!projectService.canManageMembers(projectId)) throw new BusinessException("只有项目负责人或项目管理员可以导入任务");
        if (file == null || file.isEmpty()) throw new BusinessException("请选择 CSV 文件");
        if (file.getSize() > 2 * 1024 * 1024) throw new BusinessException("CSV 文件不能超过 2MB");
        String content;
        try { content = new String(file.getBytes(), StandardCharsets.UTF_8).replace("\uFEFF", ""); }
        catch (Exception e) { throw new BusinessException("CSV 文件读取失败"); }
        List<List<String>> parsed = parseCsv(content);
        if (parsed.isEmpty()) throw new BusinessException("CSV 文件为空");
        if (!parsed.get(0).equals(HEADERS)) throw new BusinessException("CSV 表头不符合模板");
        if (parsed.size() - 1 > 500) throw new BusinessException("单次最多导入 500 行任务");
        Map<String, User> users = projectUsers(projectId);
        List<CsvTaskRowDTO> rows = new ArrayList<>();
        Set<String> titles = new HashSet<>();
        for (int i = 1; i < parsed.size(); i++) {
            List<String> values = new ArrayList<>(parsed.get(i)); while (values.size() < HEADERS.size()) values.add("");
            CsvTaskRowDTO row = row(i + 1, values); validate(row, users, titles); rows.add(row);
        }
        Set<String> allTitles = rows.stream().map(CsvTaskRowDTO::getTitle).filter(Objects::nonNull).collect(Collectors.toSet());
        rows.forEach(row -> validateDependencies(row, allTitles));
        int invalid = (int) rows.stream().filter(r -> !r.getErrors().isEmpty()).count();
        return new CsvPreviewVO(rows, rows.size() - invalid, invalid);
    }

    @Override
    @Transactional
    public List<Task> importRows(Long projectId, List<CsvTaskRowDTO> rows) {
        projectService.assertProjectAccess(projectId, true);
        if (!projectService.canManageMembers(projectId)) throw new BusinessException("只有项目负责人或项目管理员可以导入任务");
        if (rows == null || rows.isEmpty() || rows.size() > 500) throw new BusinessException("导入任务数量应为 1-500 行");
        Map<String, User> users = projectUsers(projectId); Set<String> titles = new HashSet<>();
        rows.forEach(row -> { row.setErrors(new ArrayList<>()); validate(row, users, titles); });
        Set<String> allTitles = rows.stream().map(CsvTaskRowDTO::getTitle).filter(Objects::nonNull).collect(Collectors.toSet());
        rows.forEach(row -> validateDependencies(row, allTitles));
        if (rows.stream().anyMatch(r -> !r.getErrors().isEmpty())) throw new BusinessException("导入数据已变化，请重新预览并修正错误");
        List<Task> created = new ArrayList<>(); Map<String, Long> ids = new LinkedHashMap<>();
        for (CsvTaskRowDTO row : rows) {
            User assignee = blank(row.getAssigneeUsername()) ? null : users.get(row.getAssigneeUsername().trim().toLowerCase(Locale.ROOT));
            Task task = taskService.create(projectId, row.getTitle(), null, assignee == null ? null : assignee.getId(), row.getDueDate(),
                    row.getStartDate(), row.getPriority(), row.getTags(), null, row.getEstimatedHours(), row.getActualHours(), null);
            if (!"TODO".equals(row.getStatus())) {
                TaskUpdateDTO update = new TaskUpdateDTO(); update.setId(task.getId()); update.setStatus(row.getStatus()); task = taskService.update(update);
            }
            created.add(task); ids.put(row.getTitle(), task.getId());
        }
        for (int i = 0; i < rows.size(); i++) if (!blank(rows.get(i).getDependencyTitles())) {
            String depIds = Arrays.stream(rows.get(i).getDependencyTitles().split("\\|")).map(String::trim).filter(s -> !s.isEmpty())
                    .map(ids::get).map(String::valueOf).collect(Collectors.joining(","));
            TaskUpdateDTO update = new TaskUpdateDTO(); update.setId(created.get(i).getId()); update.setDependencyIds(depIds);
            created.set(i, taskService.update(update));
        }
        return created;
    }

    private CsvTaskRowDTO row(int rowNumber, List<String> v) {
        CsvTaskRowDTO row = new CsvTaskRowDTO(); row.setRowNumber(rowNumber); row.setTitle(trim(v.get(0)));
        row.setStatus(blank(v.get(1)) ? "TODO" : trim(v.get(1)).toUpperCase(Locale.ROOT));
        row.setPriority(blank(v.get(2)) ? "MEDIUM" : trim(v.get(2)).toUpperCase(Locale.ROOT)); row.setAssigneeUsername(trim(v.get(3)));
        row.setStartDate(trim(v.get(4))); row.setDueDate(trim(v.get(5))); row.setEstimatedHours(integer(v.get(6)));
        row.setActualHours(integer(v.get(7))); row.setTags(trim(v.get(8))); row.setDependencyTitles(trim(v.get(9))); return row;
    }

    private void validate(CsvTaskRowDTO row, Map<String, User> users, Set<String> titles) {
        if (blank(row.getTitle())) row.getErrors().add("任务标题不能为空");
        else if (row.getTitle().length() > 255) row.getErrors().add("任务标题不能超过 255 字");
        else if (!titles.add(row.getTitle())) row.getErrors().add("任务标题在文件中重复");
        if (!STATUSES.contains(row.getStatus())) row.getErrors().add("状态必须为 TODO/IN_PROGRESS/DONE");
        if (!PRIORITIES.contains(row.getPriority())) row.getErrors().add("优先级必须为 HIGH/MEDIUM/LOW");
        if (!blank(row.getAssigneeUsername()) && !users.containsKey(row.getAssigneeUsername().trim().toLowerCase(Locale.ROOT))) row.getErrors().add("负责人不是项目成员");
        LocalDate start = date(row.getStartDate(), "开始日期", row); LocalDate due = date(row.getDueDate(), "截止日期", row);
        if (start != null && due != null && due.isBefore(start)) row.getErrors().add("截止日期不能早于开始日期");
        if (row.getEstimatedHours() != null && (row.getEstimatedHours() < 0 || row.getEstimatedHours() > 10000)) row.getErrors().add("预计工时应为 0-10000");
        if (row.getActualHours() != null && (row.getActualHours() < 0 || row.getActualHours() > 10000)) row.getErrors().add("实际工时应为 0-10000");
    }

    private void validateDependencies(CsvTaskRowDTO row, Set<String> titles) {
        if (blank(row.getDependencyTitles())) return;
        for (String dep : row.getDependencyTitles().split("\\|")) {
            String title = dep.trim(); if (!titles.contains(title)) row.getErrors().add("前置任务“" + title + "”不在导入文件中");
            if (title.equals(row.getTitle())) row.getErrors().add("任务不能依赖自身");
        }
    }

    private Map<String, User> projectUsers(Long projectId) {
        Project project = projectMapper.selectById(projectId); Set<Long> ids = memberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)).stream().map(ProjectMember::getUserId).collect(Collectors.toSet());
        if (project != null) ids.add(project.getCreatorId());
        return ids.isEmpty() ? Map.of() : userMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(u -> u.getUsername().toLowerCase(Locale.ROOT), u -> u));
    }

    private List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>(); List<String> row = new ArrayList<>(); StringBuilder cell = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') { if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; } else quoted = !quoted; }
            else if (c == ',' && !quoted) { row.add(cell.toString()); cell.setLength(0); }
            else if ((c == '\n' || c == '\r') && !quoted) { if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++; row.add(cell.toString()); cell.setLength(0); if (row.stream().anyMatch(v -> !v.isBlank())) rows.add(row); row = new ArrayList<>(); }
            else cell.append(c);
        }
        row.add(cell.toString()); if (row.stream().anyMatch(v -> !v.isBlank())) rows.add(row); return rows;
    }

    private String cell(String value) {
        String safe = value == null ? "" : value; if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
    private List<Long> parseIds(String value) { if (blank(value)) return List.of(); return Arrays.stream(value.split(",")).map(String::trim).filter(s -> s.matches("\\d+")).map(Long::valueOf).toList(); }
    private String safe(Object value) { return value == null ? "" : String.valueOf(value); }
    private String trim(String value) { return value == null ? "" : value.trim(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private Integer integer(String value) { if (blank(value)) return null; try { return Integer.valueOf(value.trim()); } catch (Exception e) { return Integer.MIN_VALUE; } }
    private LocalDate date(String value, String label, CsvTaskRowDTO row) { if (blank(value)) return null; try { return LocalDate.parse(value); } catch (Exception e) { row.getErrors().add(label + "格式应为 yyyy-MM-dd"); return null; } }
}
