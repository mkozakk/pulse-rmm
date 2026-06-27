package dev.pulsermm.commands.application;

import dev.pulsermm.common.audit.Auditable;
import dev.pulsermm.common.events.DomainEvent;
import dev.pulsermm.common.events.DomainEventPublisher;
import dev.pulsermm.common.rbac.IdentityClient;
import dev.pulsermm.common.rbac.PermissionChecker;
import dev.pulsermm.commands.api.dto.CreateScriptRequest;
import dev.pulsermm.commands.domain.Script;
import dev.pulsermm.commands.domain.ScriptRun;
import dev.pulsermm.commands.domain.ScriptRunResult;
import dev.pulsermm.commands.domain.ScriptSecret;
import dev.pulsermm.commands.infrastructure.config.ScriptDispatchRabbitConfig;
import dev.pulsermm.commands.infrastructure.messaging.ScriptDispatchMessage;
import dev.pulsermm.commands.infrastructure.persistence.ScriptRepository;
import dev.pulsermm.commands.infrastructure.persistence.ScriptRunRepository;
import dev.pulsermm.commands.infrastructure.persistence.ScriptRunResultRepository;
import dev.pulsermm.commands.infrastructure.persistence.ScriptSecretRepository;
import dev.pulsermm.commands.infrastructure.security.ScriptSecretEncryptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class ScriptService {

    private final ScriptRepository scriptRepository;
    private final ScriptRunRepository scriptRunRepository;
    private final ScriptRunResultRepository scriptRunResultRepository;
    private final ScriptSecretRepository scriptSecretRepository;
    private final ScriptSecretEncryptor encryptor;
    private final String scriptSecretKek;
    private final RabbitTemplate rabbitTemplate;
    private final DomainEventPublisher domainEventPublisher;
    private final String scriptServiceBaseUrl;
    private final IdentityClient identityClient;

    public ScriptService(ScriptRepository scriptRepository,
                         ScriptRunRepository scriptRunRepository,
                         ScriptRunResultRepository scriptRunResultRepository,
                         ScriptSecretRepository scriptSecretRepository,
                         ScriptSecretEncryptor encryptor,
                         @Qualifier("scriptSecretKek") String scriptSecretKek,
                         RabbitTemplate rabbitTemplate,
                         DomainEventPublisher domainEventPublisher,
                         @Value("${pulse.script.base-url:http://localhost:8084}") String scriptServiceBaseUrl,
                         IdentityClient identityClient) {
        this.scriptRepository = scriptRepository;
        this.scriptRunRepository = scriptRunRepository;
        this.scriptRunResultRepository = scriptRunResultRepository;
        this.scriptSecretRepository = scriptSecretRepository;
        this.encryptor = encryptor;
        this.scriptSecretKek = scriptSecretKek;
        this.rabbitTemplate = rabbitTemplate;
        this.domainEventPublisher = domainEventPublisher;
        this.scriptServiceBaseUrl = scriptServiceBaseUrl;
        this.identityClient = identityClient;
    }

    @Auditable(action = "script.create", permission = "script:adhoc")
    public Script createScript(CreateScriptRequest request, UUID createdBy, UUID orgId) {
        var script = new Script(request.name(), request.body(), createdBy, orgId);
        return scriptRepository.save(script);
    }

    @Auditable(action = "script.create", permission = "script:adhoc")
    public Script createScript(CreateScriptRequest request, UUID createdBy) {
        return createScript(request, createdBy, null);
    }

    @Transactional(readOnly = true)
    public Script getScriptById(UUID id) {
        return scriptRepository.findById(id)
                .orElseThrow(() -> new ScriptNotFoundException("Script not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<Script> listScripts(ScriptStatus status, Pageable pageable, UUID orgId) {
        if (orgId == null) {
            return switch (status) {
                case PENDING -> scriptRepository.findPendingScripts(pageable);
                case LIBRARY -> scriptRepository.findApprovedScripts(pageable);
                case ALL -> scriptRepository.findAllScripts(pageable);
            };
        }
        return switch (status) {
            case PENDING -> scriptRepository.findPendingForOrg(orgId, pageable);
            case LIBRARY -> scriptRepository.findApprovedForOrg(orgId, pageable);
            case ALL -> scriptRepository.findVisibleToOrg(orgId, pageable);
        };
    }

    @Transactional(readOnly = true)
    public Page<Script> listScripts(ScriptStatus status, Pageable pageable) {
        return listScripts(status, pageable, null);
    }

    @Auditable(action = "script.approve", permission = "script:approve")
    public Script approveScript(UUID scriptId) {
        var script = getScriptById(scriptId);
        if (script.isApproved()) {
            throw new ScriptAlreadyApprovedException("Script is already approved: " + scriptId);
        }
        script.setApprovedAt(OffsetDateTime.now());
        return scriptRepository.save(script);
    }

    @Auditable(action = "script.run", permission = "script:run", capturePayload = false)
    public ScriptRunData runScript(UUID scriptId, java.util.List<String> endpointIds,
                                   java.util.Map<String, String> secrets, UUID initiatedBy, UUID callerOrgId) {
        var script = getScriptById(scriptId);

        if (!script.isApproved()) {
            throw new ScriptNotApprovedException("Script is not approved: " + scriptId);
        }

        if (callerOrgId != null && !script.isGlobal() &&
                (script.getOrgId() == null || !script.getOrgId().equals(callerOrgId))) {
            throw new ScriptNotFoundException("Script not found: " + scriptId);
        }

        var perms = identityClient.getPermissions(initiatedBy.toString());
        for (String endpointIdStr : endpointIds) {
            UUID groupId = identityClient.getEndpointGroup(endpointIdStr).orElse(null);
            if (!PermissionChecker.hasPermission(perms, "script:run", groupId)) {
                throw new ScriptRunForbiddenException("No permission for endpoint: " + endpointIdStr);
            }
        }

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
                try {
                    var encryptedValue = encryptor.encrypt(value, scriptSecretKek);
                    var secret = new ScriptSecret(savedRun.getId(), key, encryptedValue);
                    scriptSecretRepository.save(secret);
                } catch (Exception e) {
                    throw new SecretEncryptionException("Failed to encrypt secret: " + key, e);
                }
            });
        }

        var decryptedSecrets = getDecryptedSecretsForRun(savedRun.getId());
        var messages = results.stream()
                .map(result -> {
                    var callbackUrl = scriptServiceBaseUrl + "/api/scripts/runs/" + savedRun.getId() +
                                    "/endpoints/" + result.getEndpointId() + "/ack";
                    return new ScriptDispatchMessage(result.getEndpointId(), result.getId(), script.getBody(), decryptedSecrets, callbackUrl);
                })
                .toList();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                messages.forEach(msg -> rabbitTemplate.convertAndSend(ScriptDispatchRabbitConfig.QUEUE, msg));
            }
        });

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

    @Transactional
    public void ackScriptExecution(UUID runId, UUID endpointId, int exitCode, String output) {
        var result = scriptRunResultRepository.findByRunId(runId).stream()
                .filter(r -> r.getEndpointId().equals(endpointId))
                .findFirst()
                .orElseThrow(() -> new ScriptRunResultNotFoundException(
                        "Result not found for run: " + runId + ", endpoint: " + endpointId));

        result.setExitCode(exitCode);
        result.setOutput(output);
        result.setAckedAt(OffsetDateTime.now());
        scriptRunResultRepository.save(result);

        domainEventPublisher.publish(DomainEvent.of("script.result", Map.of(
                "runId", runId.toString(),
                "endpointId", endpointId.toString(),
                "exitCode", exitCode
        )));
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, String> getDecryptedSecretsForRun(UUID runId) {
        var secrets = scriptSecretRepository.findByRunId(runId);
        var decrypted = new java.util.HashMap<String, String>();

        for (var secret : secrets) {
            try {
                var plaintext = encryptor.decrypt(secret.getEncryptedValue(), scriptSecretKek);
                decrypted.put(secret.getKey(), plaintext);
            } catch (Exception e) {
                throw new SecretDecryptionException("Failed to decrypt secret: " + secret.getKey(), e);
            }
        }

        return decrypted;
    }

    public record ScriptRunData(UUID runId, int endpointCount) {
    }

    public record ScriptRunResponseData(UUID runId, java.util.List<ScriptRunResult> results, int total, long pending) {
    }

    public enum ScriptStatus {
        ALL, PENDING, LIBRARY
    }
}
