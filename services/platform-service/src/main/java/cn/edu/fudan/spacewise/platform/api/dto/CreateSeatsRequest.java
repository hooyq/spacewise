package cn.edu.fudan.spacewise.platform.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 一次新建多个座位，最多 200 个。 */
public record CreateSeatsRequest(
        @NotEmpty @Size(max = 200) List<
                @NotNull @Pattern(regexp = "[A-Za-z0-9-]{1,16}", message = "只能包含字母、数字和连字符，最长 16 位") String> codes) {
}
