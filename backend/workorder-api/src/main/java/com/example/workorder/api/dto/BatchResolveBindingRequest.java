package com.example.workorder.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BatchResolveBindingRequest {
    @NotBlank
    private String platform;

    @NotEmpty
    @Size(max = 100)
    private List<Long> userIds;
}
