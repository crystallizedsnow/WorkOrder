package com.aiassistant.rag.manage;

import com.aiassistant.rag.TrustLevel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/knowledge-bases/{space}")
public class KnowledgeAdminController {
    private final KnowledgeRevisionManager manager;
    private final KnowledgeAdminAuthorizer authorizer;
    public KnowledgeAdminController(KnowledgeRevisionManager manager, KnowledgeAdminAuthorizer authorizer) {
        this.manager = manager;
        this.authorizer = authorizer;
    }

    @PostMapping(value = "/documents:check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KnowledgeRevisionManager.CheckResult check(@PathVariable String space, @RequestPart MultipartFile file,
            @RequestParam(required = false) String versionLabel, @RequestParam String owner,
            @RequestParam(defaultValue = "REVIEWED") TrustLevel trustLevel,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) throws IOException {
        return manager.check(space, file.getOriginalFilename(), file.getContentType(), file.getBytes(),
                versionLabel, owner, trustLevel, actor(authorization));
    }

    @PostMapping("/documents/{documentId}:apply")
    public KnowledgeRevisionManager.Revision apply(@PathVariable String documentId, @RequestBody ApplyRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return manager.apply(documentId, request.checkId(), request.confirmationToken(), request.confirmed(), actor(authorization),
                request.expectedActiveRevisionId());
    }

    @PostMapping("/documents/{documentId}/revisions/{revisionId}:evaluate")
    public KnowledgeRevisionManager.EvaluationReport evaluate(@PathVariable String documentId,
            @PathVariable String revisionId, @RequestBody EvaluateRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        actor(authorization);
        return manager.evaluate(documentId, revisionId, request.cases());
    }

    @GetMapping("/evaluations/{evaluationId}")
    public KnowledgeRevisionManager.EvaluationReport evaluation(@PathVariable String evaluationId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        actor(authorization);
        return manager.evaluation(evaluationId);
    }

    @PostMapping("/evaluations/{evaluationId}:activate")
    public KnowledgeRevisionManager.Revision activate(@PathVariable String evaluationId,
            @RequestBody ActivateRequest request, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return manager.activate(evaluationId, actor(authorization), request.expectedActiveRevisionId());
    }

    @GetMapping("/documents/{documentId}/revisions")
    public List<KnowledgeRevisionManager.Revision> revisions(@PathVariable String documentId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        actor(authorization);
        return manager.revisions(documentId);
    }

    @PostMapping("/documents/{documentId}/revisions/{revisionId}:rollback-check")
    public KnowledgeRevisionManager.RollbackCheck rollbackCheck(@PathVariable String documentId,
            @PathVariable String revisionId, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return manager.rollbackCheck(documentId, revisionId, actor(authorization));
    }

    @PostMapping("/documents/{documentId}/revisions/{revisionId}:rollback")
    public KnowledgeRevisionManager.Revision rollback(@PathVariable String documentId, @PathVariable String revisionId,
            @RequestBody RollbackRequest request, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return manager.rollback(request.checkId(), request.confirmationToken(), actor(authorization), request.expectedActiveRevisionId());
    }

    private String actor(String authorization) {
        return authorizer.requireAdmin(authorization);
    }

    public record ApplyRequest(String checkId, String confirmationToken, boolean confirmed,
                               String expectedActiveRevisionId) {}
    public record EvaluateRequest(List<KnowledgeRevisionManager.EvaluationCase> cases) {}
    public record ActivateRequest(String expectedActiveRevisionId) {}
    public record RollbackRequest(String checkId, String confirmationToken, String expectedActiveRevisionId) {}
}
