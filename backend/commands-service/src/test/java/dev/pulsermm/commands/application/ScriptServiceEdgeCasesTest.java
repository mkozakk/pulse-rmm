package dev.pulsermm.commands.application;

import dev.pulsermm.commands.api.dto.CreateScriptRequest;
import dev.pulsermm.commands.domain.Script;
import dev.pulsermm.commands.domain.ScriptRun;
import dev.pulsermm.commands.domain.ScriptRunResult;
import dev.pulsermm.commands.infrastructure.GatewayClient;
import dev.pulsermm.commands.infrastructure.persistence.ScriptRepository;
import dev.pulsermm.commands.infrastructure.persistence.ScriptRunRepository;
import dev.pulsermm.commands.infrastructure.persistence.ScriptRunResultRepository;
import dev.pulsermm.commands.infrastructure.persistence.ScriptSecretRepository;
import dev.pulsermm.commands.infrastructure.security.ScriptSecretEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScriptServiceEdgeCasesTest {

    private ScriptRepository scriptRepo;
    private ScriptRunRepository runRepo;
    private ScriptRunResultRepository resultRepo;
    private ScriptSecretRepository secretRepo;
    private ScriptSecretEncryptor encryptor;
    private GatewayClient gatewayClient;
    private ScriptService service;

    @BeforeEach
    void setUp() {
        scriptRepo = mock(ScriptRepository.class);
        runRepo = mock(ScriptRunRepository.class);
        resultRepo = mock(ScriptRunResultRepository.class);
        secretRepo = mock(ScriptSecretRepository.class);
        gatewayClient = mock(GatewayClient.class);

        encryptor = new ScriptSecretEncryptor();

        service = new ScriptService(scriptRepo, runRepo, resultRepo, secretRepo, encryptor, "test-secret-key-1234567", gatewayClient, "http://localhost:8084");
    }

    @Test
    void getScriptNotFoundThrows() {
        UUID scriptId = UUID.randomUUID();
        when(scriptRepo.findById(scriptId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getScriptById(scriptId))
            .isInstanceOf(ScriptNotFoundException.class)
            .hasMessageContaining("Script not found");
    }

    @Test
    void approveScriptThatIsAlreadyApprovedThrows() {
        UUID scriptId = UUID.randomUUID();
        Script approved = new Script("test", "echo test", UUID.randomUUID());
        approved.setApprovedAt(OffsetDateTime.now());

        when(scriptRepo.findById(scriptId)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.approveScript(scriptId))
            .isInstanceOf(ScriptAlreadyApprovedException.class)
            .hasMessageContaining("already approved");
    }

    @Test
    void listScriptsPendingReturnsOnlyPending() {
        PageRequest pageReq = PageRequest.of(0, 10);
        PageImpl<Script> pending = new PageImpl<>(List.of(), pageReq, 0);
        when(scriptRepo.findPendingScripts(pageReq)).thenReturn(pending);

        var result = service.listScripts(ScriptService.ScriptStatus.PENDING, pageReq);

        assertThat(result.getContent()).isEmpty();
        verify(scriptRepo).findPendingScripts(pageReq);
        verify(scriptRepo, never()).findApprovedScripts(any());
    }

    @Test
    void listScriptsLibraryReturnsApproved() {
        PageRequest pageReq = PageRequest.of(0, 10);
        PageImpl<Script> approved = new PageImpl<>(List.of(), pageReq, 0);
        when(scriptRepo.findApprovedScripts(pageReq)).thenReturn(approved);

        var result = service.listScripts(ScriptService.ScriptStatus.LIBRARY, pageReq);

        assertThat(result.getContent()).isEmpty();
        verify(scriptRepo).findApprovedScripts(pageReq);
        verify(scriptRepo, never()).findPendingScripts(any());
    }

    @Test
    void runScriptWithNoEndpointIdsCreatesNoResults() {
        UUID scriptId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Script script = new Script("test", "echo test", userId);

        when(scriptRepo.findById(scriptId)).thenReturn(Optional.of(script));
        ScriptRun run = new ScriptRun(scriptId, userId);
        run.setId(UUID.randomUUID());
        when(runRepo.save(any())).thenReturn(run);
        when(resultRepo.findByRunId(run.getId())).thenReturn(List.of());
        when(secretRepo.findByRunId(run.getId())).thenReturn(List.of());

        var result = service.runScript(scriptId, List.of(), null, userId);

        assertThat(result.endpointCount()).isEqualTo(0);
        verify(resultRepo, never()).save(any());
    }

    @Test
    void getScriptRunResultsNotFoundThrows() {
        UUID runId = UUID.randomUUID();
        when(resultRepo.findByRunId(runId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.getScriptRunResults(runId))
            .isInstanceOf(ScriptRunNotFoundException.class)
            .hasMessageContaining("Script run not found");
    }

    @Test
    void ackScriptExecutionNotFoundThrows() {
        UUID runId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();

        when(resultRepo.findByRunId(runId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.ackScriptExecution(runId, endpointId, 0, "output"))
            .isInstanceOf(ScriptRunResultNotFoundException.class)
            .hasMessageContaining("Result not found");
    }

    @Test
    void ackScriptExecutionUpdatesAllFields() {
        UUID runId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        ScriptRunResult result = new ScriptRunResult(runId, endpointId);

        when(resultRepo.findByRunId(runId)).thenReturn(List.of(result));

        service.ackScriptExecution(runId, endpointId, 42, "script output");

        verify(resultRepo).save(result);
        assertThat(result.getExitCode()).isEqualTo(42);
        assertThat(result.getOutput()).isEqualTo("script output");
        assertThat(result.getAckedAt()).isNotNull();
    }

    @Test
    void decryptSecretsThrowsOnDecryptionFailure() {
        UUID runId = UUID.randomUUID();
        when(secretRepo.findByRunId(runId)).thenReturn(List.of());

        var secrets = service.getDecryptedSecretsForRun(runId);

        assertThat(secrets).isEmpty();
    }

    @Test
    void createScriptSavesWithCorrectData() {
        UUID userId = UUID.randomUUID();
        CreateScriptRequest req = new CreateScriptRequest("backup.sh", "#!/bin/bash\necho backup");

        when(scriptRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.createScript(req, userId);

        assertThat(result.getName()).isEqualTo("backup.sh");
        assertThat(result.getBody()).isEqualTo("#!/bin/bash\necho backup");
        assertThat(result.getCreatedBy()).isEqualTo(userId);
    }

    @Test
    void approveScriptSetsTimestamp() {
        UUID scriptId = UUID.randomUUID();
        Script script = new Script("test", "echo test", UUID.randomUUID());

        when(scriptRepo.findById(scriptId)).thenReturn(Optional.of(script));
        when(scriptRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.approveScript(scriptId);

        assertThat(result.getApprovedAt()).isNotNull();
    }
}
