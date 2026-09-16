package com.smartpm.service.support;

import com.smartpm.entity.Task;
import com.smartpm.entity.TaskDependency;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 确定性的关键路径计算器：只处理内存数据，不访问数据库，也不修改任务。 */
@Component
public class ScheduleEngine {

    public Result calculate(List<Task> sourceTasks, List<TaskDependency> sourceDependencies,
                            SimulationOverride simulationOverride, LocalDate today) {
        List<Task> tasks = Optional.ofNullable(sourceTasks).orElseGet(List::of).stream()
                .filter(task -> task.getParentId() == null && task.getDeletedAt() == null)
                .sorted(Comparator.comparing(Task::getId))
                .toList();
        if (tasks.isEmpty()) return new Result(null, null, 0, List.of(), List.of(), List.of());

        Map<Long, Node> nodes = new LinkedHashMap<>();
        for (Task task : tasks) nodes.put(task.getId(), createNode(task, simulationOverride));

        LinkedHashSet<String> warnings = new LinkedHashSet<>();
        for (TaskDependency edge : Optional.ofNullable(sourceDependencies).orElseGet(List::of)) {
            Node dependent = nodes.get(edge.getTaskId());
            if (dependent == null) continue;
            Node prerequisite = nodes.get(edge.getPrerequisiteTaskId());
            if (prerequisite == null) {
                warnings.add("任务“" + dependent.task.getTitle() + "”存在调度范围外的前置依赖，已忽略");
                continue;
            }
            dependent.predecessors.add(prerequisite.id());
            prerequisite.successors.add(dependent.id());
        }

        List<Long> topologicalOrder = topologicalOrder(nodes);
        LocalDate projectAnchor = nodes.values().stream().map(node -> node.candidateStart)
                .filter(Objects::nonNull).min(LocalDate::compareTo).orElse(today);

        for (Long id : topologicalOrder) {
            Node node = nodes.get(id);
            LocalDate dependencyReady = node.predecessors.stream().map(nodes::get)
                    .map(predecessor -> predecessor.earliestFinish.plusDays(1))
                    .max(LocalDate::compareTo).orElse(projectAnchor);
            LocalDate requestedStart = node.candidateStart == null ? projectAnchor : node.candidateStart;
            node.earliestStart = requestedStart.isAfter(dependencyReady) ? requestedStart : dependencyReady;
            node.earliestFinish = node.earliestStart.plusDays(node.durationDays - 1L);
            if (node.task.getDueDate() != null && node.earliestFinish.isAfter(node.task.getDueDate())) {
                warnings.add("任务“" + node.task.getTitle() + "”受依赖约束后预计完成于 "
                        + node.earliestFinish + "，晚于原截止日期 " + node.task.getDueDate());
            }
        }

        LocalDate projectStart = nodes.values().stream().map(node -> node.earliestStart)
                .min(LocalDate::compareTo).orElse(projectAnchor);
        LocalDate projectFinish = nodes.values().stream().map(node -> node.earliestFinish)
                .max(LocalDate::compareTo).orElse(projectStart);

        ListIterator<Long> reverse = topologicalOrder.listIterator(topologicalOrder.size());
        while (reverse.hasPrevious()) {
            Node node = nodes.get(reverse.previous());
            node.latestFinish = node.successors.isEmpty() ? projectFinish : node.successors.stream()
                    .map(nodes::get).map(successor -> successor.latestStart.minusDays(1))
                    .min(LocalDate::compareTo).orElse(projectFinish);
            node.latestStart = node.latestFinish.minusDays(node.durationDays - 1L);
            node.totalSlackDays = Math.max(0, (int) ChronoUnit.DAYS.between(node.earliestStart, node.latestStart));
            node.critical = node.totalSlackDays == 0;
        }

        List<TaskResult> taskResults = topologicalOrder.stream().map(id -> toResult(nodes.get(id))).toList();
        List<Long> criticalPath = representativeCriticalPath(nodes, topologicalOrder);
        int durationDays = (int) ChronoUnit.DAYS.between(projectStart, projectFinish) + 1;
        return new Result(projectStart, projectFinish, durationDays, taskResults, criticalPath, List.copyOf(warnings));
    }

    private Node createNode(Task task, SimulationOverride override) {
        boolean hasBothDates = task.getStartDate() != null && task.getDueDate() != null;
        int durationDays = hasBothDates
                ? Math.max(1, (int) ChronoUnit.DAYS.between(task.getStartDate(), task.getDueDate()) + 1)
                : task.getEstimatedHours() == null || task.getEstimatedHours() <= 0
                    ? 1 : Math.max(1, (int) Math.ceil(task.getEstimatedHours() / 8.0));
        LocalDate candidateStart = task.getStartDate();
        if (candidateStart == null && task.getDueDate() != null) {
            candidateStart = task.getDueDate().minusDays(durationDays - 1L);
        }
        String source = hasBothDates ? "EXPLICIT" : "INFERRED";
        if (override != null && Objects.equals(override.taskId(), task.getId())) {
            if (override.startDate() != null) candidateStart = override.startDate();
            if (override.durationDays() != null) durationDays = override.durationDays();
            source = "SIMULATED";
        }
        return new Node(task, durationDays, candidateStart, source);
    }

    private List<Long> topologicalOrder(Map<Long, Node> nodes) {
        Map<Long, Integer> indegree = new HashMap<>();
        nodes.values().forEach(node -> indegree.put(node.id(), node.predecessors.size()));
        PriorityQueue<Long> ready = new PriorityQueue<>();
        indegree.forEach((id, count) -> { if (count == 0) ready.add(id); });
        List<Long> result = new ArrayList<>(nodes.size());
        while (!ready.isEmpty()) {
            Long id = ready.poll();
            result.add(id);
            for (Long successorId : nodes.get(id).successors) {
                int remaining = indegree.compute(successorId, (key, value) -> value - 1);
                if (remaining == 0) ready.add(successorId);
            }
        }
        if (result.size() != nodes.size()) throw new IllegalArgumentException("任务依赖存在循环，无法计算关键路径");
        return result;
    }

    private List<Long> representativeCriticalPath(Map<Long, Node> nodes, List<Long> topologicalOrder) {
        Map<Long, List<Long>> bestFrom = new HashMap<>();
        ListIterator<Long> reverse = topologicalOrder.listIterator(topologicalOrder.size());
        while (reverse.hasPrevious()) {
            Node node = nodes.get(reverse.previous());
            if (!node.critical) continue;
            List<Long> bestTail = node.successors.stream().map(nodes::get).filter(successor -> successor.critical)
                    .map(successor -> bestFrom.getOrDefault(successor.id(), List.of(successor.id())))
                    .max(Comparator.<List<Long>>comparingInt(path -> path.stream().map(nodes::get)
                            .mapToInt(item -> item.durationDays).sum()).thenComparing(Object::toString))
                    .orElseGet(List::of);
            List<Long> path = new ArrayList<>();
            path.add(node.id());
            path.addAll(bestTail);
            bestFrom.put(node.id(), List.copyOf(path));
        }
        return bestFrom.values().stream()
                .max(Comparator.<List<Long>>comparingInt(path -> path.stream().map(nodes::get)
                        .mapToInt(item -> item.durationDays).sum()).thenComparing(Object::toString))
                .orElseGet(List::of);
    }

    private TaskResult toResult(Node node) {
        return new TaskResult(node.id(), node.task.getTitle(), node.task.getStatus(),
                node.task.getStartDate(), node.task.getDueDate(), node.earliestStart, node.earliestFinish,
                node.latestStart, node.latestFinish, node.durationDays, node.totalSlackDays,
                node.critical, node.source, List.copyOf(node.predecessors));
    }

    private static final class Node {
        private final Task task;
        private final int durationDays;
        private final LocalDate candidateStart;
        private final String source;
        private final SortedSet<Long> predecessors = new TreeSet<>();
        private final SortedSet<Long> successors = new TreeSet<>();
        private LocalDate earliestStart;
        private LocalDate earliestFinish;
        private LocalDate latestStart;
        private LocalDate latestFinish;
        private int totalSlackDays;
        private boolean critical;

        private Node(Task task, int durationDays, LocalDate candidateStart, String source) {
            this.task = task;
            this.durationDays = durationDays;
            this.candidateStart = candidateStart;
            this.source = source;
        }

        private Long id() { return task.getId(); }
    }

    public record SimulationOverride(Long taskId, LocalDate startDate, Integer durationDays) { }

    public record TaskResult(Long taskId, String title, String status,
                             LocalDate declaredStartDate, LocalDate declaredDueDate,
                             LocalDate plannedStartDate, LocalDate plannedFinishDate,
                             LocalDate latestStartDate, LocalDate latestFinishDate,
                             int durationDays, int totalSlackDays, boolean critical,
                             String source, List<Long> predecessorIds) { }

    public record Result(LocalDate projectStartDate, LocalDate projectFinishDate, int durationDays,
                         List<TaskResult> tasks, List<Long> criticalPathTaskIds, List<String> warnings) { }
}
