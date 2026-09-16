package com.smartpm.controller;

import com.smartpm.common.result.R;
import com.smartpm.dto.ExecutiveSummaryEmailDTO;
import com.smartpm.service.ManagementOperationsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/project/{projectId}/executive-summary")
@RequiredArgsConstructor
public class ExecutiveSummaryController {
    private final ManagementOperationsService managementService;

    @GetMapping
    public R<Map<String, Object>> summary(@PathVariable Long projectId) {
        return R.ok(managementService.executiveSummary(projectId));
    }

    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long projectId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("smartpm-project-summary.pdf", StandardCharsets.UTF_8).build().toString())
                .body(managementService.executiveSummaryPdf(projectId));
    }

    @PostMapping("/email")
    public R<Map<String, Object>> email(@PathVariable Long projectId,
                                        @Valid @RequestBody ExecutiveSummaryEmailDTO dto) {
        return R.ok(Map.of("queued", managementService.emailExecutiveSummary(projectId, dto.getRecipientUserIds())));
    }
}
