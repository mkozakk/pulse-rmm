package dev.pulsermm.commands.processes.application;

import dev.pulsermm.commands.infrastructure.AgentHubClient;
import dev.pulsermm.commands.processes.domain.ProcessKillCommand;
import dev.pulsermm.commands.processes.domain.ProcessSnapshot;
import dev.pulsermm.commands.processes.infrastructure.ProcessKillCommandRepository;
import dev.pulsermm.commands.processes.infrastructure.ProcessSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class ProcessService {

    private static final Logger logger = LoggerFactory.getLogger(ProcessService.class);

    private final ProcessSnapshotRepository snapshotRepository;
    private final ProcessKillCommandRepository killRepository;
    private final AgentHubClient agentHubClient;
    private final String baseUrl;

    public ProcessService(ProcessSnapshotRepository snapshotRepository,
                          ProcessKillCommandRepository killRepository,
                          AgentHubClient agentHubClient,
                          @Value("${pulse.script.base-url:http://localhost:8084}") String baseUrl) {
        this.snapshotRepository = snapshotRepository;
        this.killRepository = killRepository;
        this.agentHubClient = agentHubClient;
        this.baseUrl = baseUrl;
    }

    public UUID refresh(UUID endpointId, UUID requestedBy) {
        var snapshot = new ProcessSnapshot(endpointId, requestedBy);
        var saved = snapshotRepository.save(snapshot);
        var commandId = saved.getId();
        var callbackUrl = baseUrl + "/api/processes/commands/" + commandId + "/ack";

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    agentHubClient.dispatchListProcesses(endpointId, commandId.toString(), callbackUrl);
                }
            });
        } else {
            agentHubClient.dispatchListProcesses(endpointId, commandId.toString(), callbackUrl);
        }

        return commandId;
    }

    public UUID kill(UUID endpointId, int pid, UUID requestedBy) {
        var cmd = new ProcessKillCommand(endpointId, pid, requestedBy);
        var saved = killRepository.save(cmd);
        var commandId = saved.getId();
        var callbackUrl = baseUrl + "/api/processes/kill-commands/" + commandId + "/ack";

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    agentHubClient.dispatchKillProcess(endpointId, commandId.toString(), pid, callbackUrl);
                }
            });
        } else {
            agentHubClient.dispatchKillProcess(endpointId, commandId.toString(), pid, callbackUrl);
        }

        return commandId;
    }

    public void ackListProcesses(UUID commandId, int exitCode, String output) {
        var snapshot = snapshotRepository.findById(commandId).orElse(null);
        if (snapshot == null) {
            logger.warn("Ack for unknown process-list command {}", commandId);
            return;
        }
        if (exitCode == 0) {
            snapshot.setStatus("COMPLETED");
            snapshot.setProcesses(output);
        } else {
            snapshot.setStatus("FAILED");
            snapshot.setError(output);
        }
        snapshot.setCompletedAt(OffsetDateTime.now());
        snapshotRepository.save(snapshot);
    }

    public void ackKillProcess(UUID commandId, int exitCode, String output) {
        var cmd = killRepository.findById(commandId).orElse(null);
        if (cmd == null) {
            logger.warn("Ack for unknown kill command {}", commandId);
            return;
        }
        cmd.setStatus(exitCode == 0 ? "COMPLETED" : "FAILED");
        if (exitCode != 0) {
            cmd.setError(output);
        }
        cmd.setCompletedAt(OffsetDateTime.now());
        killRepository.save(cmd);
    }

    @Transactional(readOnly = true)
    public Optional<ProcessSnapshot> latestCompleted(UUID endpointId) {
        return snapshotRepository.findTopByEndpointIdAndStatusOrderByCompletedAtDesc(endpointId, "COMPLETED");
    }
}
