package com.smartpm.vo;
import com.smartpm.dto.CsvTaskRowDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;
@Data @AllArgsConstructor public class CsvPreviewVO {
    private List<CsvTaskRowDTO> rows;
    private int validCount;
    private int invalidCount;
}
