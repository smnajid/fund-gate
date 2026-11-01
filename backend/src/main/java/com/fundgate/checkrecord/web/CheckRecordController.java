package com.fundgate.checkrecord.web;

import com.fundgate.checkrecord.AuditChecks;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "What the AI saw": the Check Record of one check, read only; no endpoint changes or deletes one (screens.md). */
@RestController
@Profile("!pipeline")
@RequestMapping("/api/check-records")
public class CheckRecordController {

    private final AuditChecks auditChecks;

    public CheckRecordController(AuditChecks auditChecks) {
        this.auditChecks = auditChecks;
    }

    @GetMapping(path = "/{checkId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CheckRecordResponse> checkRecord(@PathVariable UUID checkId) {
        return ResponseEntity.of(auditChecks.audit(checkId).map(CheckRecordResponse::of));
    }
}
