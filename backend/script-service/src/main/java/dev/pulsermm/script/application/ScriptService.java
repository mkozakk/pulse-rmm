package dev.pulsermm.script.application;

import dev.pulsermm.script.api.CreateScriptRequest;
import dev.pulsermm.script.domain.Script;
import dev.pulsermm.script.infrastructure.persistence.ScriptRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class ScriptService {

    private final ScriptRepository scriptRepository;

    public ScriptService(ScriptRepository scriptRepository) {
        this.scriptRepository = scriptRepository;
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
        script.setApprovedAt(java.time.OffsetDateTime.now());
        return scriptRepository.save(script);
    }

    public enum ScriptStatus {
        ALL, PENDING, LIBRARY
    }
}
