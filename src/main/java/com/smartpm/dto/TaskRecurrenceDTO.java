package com.smartpm.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;
@Data public class TaskRecurrenceDTO {
    @NotBlank @Pattern(regexp="DAILY|WEEKLY|MONTHLY", message="无效的重复频率") private String frequency;
    @NotNull @Min(1) @Max(52) private Integer intervalValue = 1;
    @Size(max=7) private List<@Min(1) @Max(7) Integer> weekdays;
    @Min(1) @Max(31) private Integer dayOfMonth;
    @NotBlank @Pattern(regexp="^\\d{4}-\\d{2}-\\d{2}$") private String startDate;
    @Pattern(regexp="^$|^\\d{4}-\\d{2}-\\d{2}$") private String endDate;
    @Min(0) @Max(365) private Integer dueOffsetDays = 0;
    private Boolean active = true;
}
