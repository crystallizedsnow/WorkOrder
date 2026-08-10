package com.example.workorder.cli.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.workorder.cli.dto.response.DataCodeDTO;
import com.example.workorder.cli.dto.response.SchemaDTO;
import com.example.workorder.cli.enums.DataCodeEnum;
import com.example.workorder.cli.enums.WriteDataCodeEnum;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SchemaService {

    private JSONObject schemas;

    @PostConstruct
    public void init() {
        try (InputStream is = new ClassPathResource("data-schemas.json").getInputStream()) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            schemas = JSON.parseObject(content);
            log.info("Loaded {} data schemas", schemas.size());
        } catch (Exception e) {
            log.error("Failed to load data-schemas.json", e);
            schemas = new JSONObject();
        }
    }

    public List<DataCodeDTO> listDataCodes() {
        List<DataCodeDTO> list = new ArrayList<>();
        for (DataCodeEnum e : DataCodeEnum.values()) {
            list.add(new DataCodeDTO(e.getDataCode(), e.getName(), e.getDescription(), e.getPermission(), "read"));
        }
        for (WriteDataCodeEnum e : WriteDataCodeEnum.values()) {
            list.add(new DataCodeDTO(e.getDataCode(), e.getName(), e.getDescription(), e.getPermission(), "write"));
        }
        return list;
    }

    public SchemaDTO getSchema(String dataCode) {
        DataCodeEnum readEnum = DataCodeEnum.fromDataCode(dataCode);
        WriteDataCodeEnum writeEnum = WriteDataCodeEnum.fromDataCode(dataCode);

        if (readEnum == null && writeEnum == null) {
            return null;
        }

        JSONObject schemaJson = schemas.getJSONObject(dataCode);
        if (schemaJson == null) {
            return null;
        }

        Map<String, Object> inputSchema = schemaJson.getJSONObject("inputSchema");
        Map<String, Object> outputSchema = schemaJson.getJSONObject("outputSchema");

        String name = readEnum != null ? readEnum.getName() : writeEnum.getName();
        String description = readEnum != null ? readEnum.getDescription() : writeEnum.getDescription();

        return new SchemaDTO(dataCode, name, description, inputSchema, outputSchema);
    }
}