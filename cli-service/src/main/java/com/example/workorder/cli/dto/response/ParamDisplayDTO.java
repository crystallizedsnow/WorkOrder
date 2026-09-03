package com.example.workorder.cli.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParamDisplayDTO {
    private String key;
    private Object value;
    private String displayValue;
    private String description;
}
