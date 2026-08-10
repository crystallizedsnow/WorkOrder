package com.example.workorder.cli.dto.response;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataCodeDTO {

    private String dataCode;
    private String name;
    private String description;
    private String permission;
    private String type;
}