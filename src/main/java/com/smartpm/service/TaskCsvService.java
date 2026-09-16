package com.smartpm.service;
import com.smartpm.dto.CsvTaskRowDTO;
import com.smartpm.entity.Task;
import com.smartpm.vo.CsvPreviewVO;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
public interface TaskCsvService {
    byte[] export(Long projectId);
    CsvPreviewVO preview(Long projectId, MultipartFile file);
    List<Task> importRows(Long projectId, List<CsvTaskRowDTO> rows);
}
