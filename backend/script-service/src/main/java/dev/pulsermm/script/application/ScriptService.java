package dev.pulsermm.script.application;

import dev.pulsermm.script.api.CreateScriptRequest;
import dev.pulsermm.script.domain.Script;
import dev.pulsermm.script.domain.ScriptRun;
import dev.pulsermm.script.domain.ScriptRunResult;
import dev.pulsermm.script.domain.ScriptSecret;
import dev.pulsermm.script.infrastructure.persistence.ScriptRepository;
import dev.pulsermm.script.infrastructure.persistence.ScriptRunRepository;
import dev.pulsermm.script.infrastructure.persistence.ScriptRunResultRepository;
import dev.pulsermm.script.infrastructure.persistence.ScriptSecretRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@Transactional
public class ScriptService {

    private final ScriptRepository scriptRepository;
    private final ScriptRunRepository scriptRunRepository;
    private final ScriptRunResultRepository scriptRunResultRepository;
    private final ScriptSecretRepository scriptSecretRepository;

    public ScriptService(ScriptRepository scriptRepository,
                         ScriptRunRepository scriptRunRepository,
                         ScriptRunResultRepository scriptRunResultRepository,
                         ScriptSecretRepository scriptSecretRepository) {
        this.scriptRepository = scriptRepository;
        this.scriptRunRepository = scriptRunRepository;
        this.scriptRunResultRepository = scriptRunResultRepository;
        this.scriptSecretRepository = scriptSecretRepository;
    }

    public Script createScript(CreateScriptRequest request, UUID createdBy) {
        var script = new Script(request.name(), request.body(), createdBy);
        return scriptRepository.save(script);
    }

    @Transactional(readOnly = true)
    public Script getScriptById(UUID id) {
        return scriptRepository.findById(id)
                .orElseThrow(() -> new ScriptNotFoundException("Script not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<Script> listScripts(ScriptStatus status, Pageable pageable) {
        return switch (status) {
            case PENDING -> scriptRepository.findPendingScripts(pageable);
            case LIBRARY -> scriptRepository.findApprovedScripts(pageable);
            case ALL -> scriptRepository.findAllScripts(pageable);
        };
    }

    public Script approveScript(UUID scriptId) {
        var script = getScriptById(scriptId);
        if (script.isApproved()) {
            throw new ScriptAlreadyApprovedException("Script is already approved: " + scriptId);
        }
        script.setApprovedAt(OffsetDateTime.now());
        return scriptRepository.save(script);
    }

    public ScriptRunData runScript(UUID scriptId, java.util.List<String> endpointIds,
                                   java.util.Map<String, String> secrets, UUID initiatedBy) {
        var script = getScriptById(scriptId);

        var run = new ScriptRun(scriptId, initiatedBy);
        var savedRun = scriptRunRepository.save(run);

        var results = endpointIds.stream()
                .map(endpointIdStr -> {
                    var result = new ScriptRunResult(savedRun.getId(), UUID.fromString(endpointIdStr));
                    return scriptRunResultRepository.save(result);
                })
                .toList();

        if (secrets != null && !secrets.isEmpty()) {
            secrets.forEach((key, value) -> {
                var secret = new ScriptSecret(savedRun.getId(), key, value);
                scriptSecretRepository.save(secret);
            });
        }

        return new ScriptRunData(savedRun.getId(), results.size());
    }

    @Transactional(readOnly = true)
    public ScriptRunResponseData getScriptRunResults(UUID runId) {
        var results = scriptRunResultRepository.findByRunId(runId);
        if (results.isEmpty()) {
            throw new ScriptRunNotFoundException("Script run not found: " + runId);
        }
        var pending = results.stream().filter(ScriptRunResult::isPending).count();
        return new ScriptRunResponseData(runId, results, results.size(), pending);
    }

    public record ScriptRunData(UUID runId, int endpointCount) {
    }

    public record ScriptRunResponseData(UUID runId, java.util.List<ScriptRunResult> results, int total, long pending) {
    }

    public enum ScriptStatus {
        ALL, PENDING, LIBRARY
    }
}
