package com.example.workorder.cli.guard;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

@Component
public class CanonicalRequestDigester {
    private final ObjectMapper canonicalMapper;

    public CanonicalRequestDigester(ObjectMapper mapper) {
        canonicalMapper = mapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public Map<String, Object> canonicalize(Map<String, Object> params) {
        Map<String, Object> source = params == null ? Map.of() : params;
        return new TreeMap<>(source);
    }

    public String digest(String userId, String dataCode, String schemaVersion, Map<String, Object> params) {
        try {
            String payload = userId + "\n" + dataCode + "\n" + schemaVersion + "\n"
                    + canonicalMapper.writeValueAsString(canonicalize(params));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("cannot digest write request", error);
        }
    }
}
