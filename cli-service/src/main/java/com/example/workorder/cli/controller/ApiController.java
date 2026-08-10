package com.example.workorder.cli.controller;

import com.example.workorder.cli.dto.request.QueryRequest;
import com.example.workorder.cli.dto.response.ApiResponse;
import com.example.workorder.cli.dto.response.DataCodeDTO;
import com.example.workorder.cli.dto.response.SchemaDTO;
import com.example.workorder.cli.service.QueryService;
import com.example.workorder.cli.service.SchemaService;
import com.example.workorder.cli.service.WriteService;
import com.example.workorder.cli.util.LogUtils;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private QueryService queryService;

    @Autowired
    private WriteService writeService;

    @GetMapping("/dataCodes")
    public ApiResponse<List<DataCodeDTO>> listDataCodes() {
        String traceId = MDC.get("traceId");
        LogUtils.entrance(traceId, "/api/dataCodes", "GET", null);
        List<DataCodeDTO> dataCodes = schemaService.listDataCodes();
        LogUtils.returnLog(traceId, "/api/dataCodes", "GET", dataCodes);
        return ApiResponse.success(dataCodes);
    }

    @GetMapping("/schema/{dataCode}")
    public ApiResponse<SchemaDTO> getSchema(@PathVariable String dataCode) {
        String traceId = MDC.get("traceId");
        LogUtils.entrance(traceId, "/api/schema/" + dataCode, "GET", dataCode);
        
        SchemaDTO schema = schemaService.getSchema(dataCode);
        if (schema == null) {
            LogUtils.warn(traceId, "/api/schema/" + dataCode, "Schema not found");
            return ApiResponse.error(404, "Schema not found for dataCode: " + dataCode, traceId);
        }
        LogUtils.returnLog(traceId, "/api/schema/" + dataCode, "GET", schema);
        return ApiResponse.success(schema, traceId);
    }

    @PostMapping("/query")
    public ResponseEntity<Object> query(
            @Valid @RequestBody QueryRequest request,
            @RequestHeader("Authorization") String token) {
        String traceId = MDC.get("traceId");
        Map<String, Object> params = new HashMap<>();
        params.put("dataCode", request.getDataCode());
        params.put("params", request.getParams());
        params.put("token", "***");
        LogUtils.entrance(traceId, "/api/query", "POST", params);

        Object result = queryService.query(
                request.getDataCode(),
                request.getParams(),
                token,
                traceId);

        LogUtils.returnLog(traceId, "/api/query", "POST", result);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/execute")
    public ResponseEntity<Object> execute(
            @Valid @RequestBody QueryRequest request,
            @RequestHeader("Authorization") String token) {
        String traceId = MDC.get("traceId");
        Map<String, Object> params = new HashMap<>();
        params.put("dataCode", request.getDataCode());
        params.put("params", request.getParams());
        params.put("token", "***");
        LogUtils.entrance(traceId, "/api/execute", "POST", params);

        Object result = writeService.execute(
                request.getDataCode(),
                request.getParams(),
                token,
                traceId);

        LogUtils.returnLog(traceId, "/api/execute", "POST", result);
        return ResponseEntity.ok(result);
    }
}