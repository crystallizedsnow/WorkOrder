package com.example.workorder.cli.guard;

import com.example.workorder.cli.preview.WritePreview;
import java.util.Map;

public record AuthorizedWriteRequest(String dataCode, Map<String, Object> params, WritePreview preview) {}
