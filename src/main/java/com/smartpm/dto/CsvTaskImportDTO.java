package com.smartpm.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;
@Data public class CsvTaskImportDTO {
    @NotEmpty @Size(max=500) private List<@Valid CsvTaskRowDTO> rows;
}
