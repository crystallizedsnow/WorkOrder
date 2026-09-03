package com.example.workorder.cli.dto.request;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

@Data
public class QueryRequest {

    private String previewId;

    @NotBlank(message = "dataCode must not be blank")
    private String dataCode;

    private Map<String, Object> params;
}
