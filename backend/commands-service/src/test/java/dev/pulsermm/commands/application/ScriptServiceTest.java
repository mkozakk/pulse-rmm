package dev.pulsermm.commands.application;

import dev.pulsermm.commands.domain.Script;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptServiceTest {

    @Test
    void scriptCreationSetsInitialState() {
        UUID userId = UUID.randomUUID();
        var script = new Script("backup.sh", "#!/bin/bash\necho backup", userId);

        assertThat(script.getName()).isEqualTo("backup.sh");
        assertThat(script.getBody()).contains("backup");
        assertThat(script.getCreatedBy()).isEqualTo(userId);
        assertThat(script.getApprovedAt()).isNull();
    }

    @Test
    void scriptApprovalSetsTimestamp() {
        UUID userId = UUID.randomUUID();
        var script = new Script("test", "echo test", userId);
        script.setApprovedAt(OffsetDateTime.now());

        assertThat(script.getApprovedAt()).isNotNull();
    }

    @Test
    void scriptCanHaveLongName() {
        UUID userId = UUID.randomUUID();
        var longName = "a".repeat(255);
        var script = new Script(longName, "echo test", userId);

        assertThat(script.getName().length()).isEqualTo(255);
    }

    @Test
    void scriptCanHaveMultilineBody() {
        UUID userId = UUID.randomUUID();
        var multilineBody = "#!/bin/bash\nline1\nline2\nline3";
        var script = new Script("multi", multilineBody, userId);

        assertThat(script.getBody()).contains("line1").contains("line2").contains("line3");
    }
}
