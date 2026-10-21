package cn.edu.fudan.spacewise.platform.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** name 可选。 */
public record CreateRoomRequest(
        @NotNull Long buildingId,
        @NotNull @Pattern(regexp = "[A-Za-z0-9-]{1,32}", message = "只能包含字母、数字和连字符，最长 32 位") String code,
        @Size(max = 64) String name,
        @NotNull @Positive Integer capacity) {
}
