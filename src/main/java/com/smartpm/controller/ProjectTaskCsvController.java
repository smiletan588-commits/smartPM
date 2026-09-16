package com.smartpm.controller;
import com.smartpm.common.result.R;
import com.smartpm.dto.CsvTaskImportDTO;
import com.smartpm.entity.Task;
import com.smartpm.service.TaskCsvService;
import com.smartpm.vo.CsvPreviewVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController @RequestMapping("/api/project/{projectId}/tasks") @RequiredArgsConstructor
public class ProjectTaskCsvController {
    private final TaskCsvService csvService;
    @GetMapping(value="/export.csv", produces="text/csv")
    public ResponseEntity<byte[]> export(@PathVariable Long projectId) {
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("smartpm-tasks-" + projectId + ".csv", StandardCharsets.UTF_8).build().toString())
                .body(csvService.export(projectId));
    }
    @PostMapping(value="/import/preview", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<CsvPreviewVO> preview(@PathVariable Long projectId, @RequestParam("file") MultipartFile file) {
        return R.ok(csvService.preview(projectId, file));
    }
    @PostMapping("/import")
    public R<List<Task>> importRows(@PathVariable Long projectId, @Valid @RequestBody CsvTaskImportDTO dto) {
        return R.ok(csvService.importRows(projectId, dto.getRows()));
    }
}
