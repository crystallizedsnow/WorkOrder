package com.example.workorder.cli.preview;

import java.time.Duration;
import java.util.Optional;

public interface WritePreviewStore {
    void save(WritePreview preview, Duration ttl);
    Optional<WritePreview> find(String previewId);
    Optional<WritePreview> transition(String previewId, WritePreviewStatus expected, WritePreviewStatus target);
}
