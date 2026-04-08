package dev.pulsermm.commands.processes.application;

import dev.pulsermm.commands.infrastructure.AgentHubClient;
import dev.pulsermm.commands.processes.domain.ProcessSnapshot;
import dev.pulsermm.commands.processes.infrastructure.ProcessKillCommandRepository;
import dev.pulsermm.commands.processes.infrastructure.ProcessSnapshotRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessServiceTest {

    @Test
    void refreshInsertsPendingSnapshotAndDispatchesToGateway() {
        var repo = mock(ProcessSnapshotRepository.class);
        var killRepo = mock(ProcessKillCommandRepository.class);
        var agentHub = mock(AgentHubClient.class);

        var endpointId = UUID.randomUUID();
        var userId = UUID.randomUUID();

        when(repo.save(any(ProcessSnapshot.class))).thenAnswer(inv -> {
            ProcessSnapshot s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        var service = new ProcessService(repo, killRepo, agentHub, "http://localhost:8084");
        UUID commandId = service.refresh(endpointId, userId);

        assertThat(commandId).isNotNull();
        verify(repo, times(1)).save(any(ProcessSnapshot.class));
        verify(agentHub, times(1)).dispatchListProcesses(
            any(UUID.class), anyString(), anyString());
    }
}
