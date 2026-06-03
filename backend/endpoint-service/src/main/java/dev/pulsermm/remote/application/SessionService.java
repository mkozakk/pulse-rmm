package dev.pulsermm.remote.application;

import dev.pulsermm.remote.domain.DesktopSession;
import dev.pulsermm.remote.infrastructure.AgentHubClient;
import dev.pulsermm.remote.infrastructure.IdentityClient;
import dev.pulsermm.remote.infrastructure.persistence.SessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class SessionService {

    private final SessionRepository sessions;
    private final IdentityClient identityClient;
    private final AgentHubClient agentHubClient;
    private final String turnSecret;
    private final List<String> turnUrls;

    public SessionService(
            SessionRepository sessions,
            IdentityClient identityClient,
            AgentHubClient agentHubClient,
            @Value("${pulse.turn.secret}") String turnSecret,
            @Value("${pulse.turn.urls}") String turnUrlsCsv) {
        this.sessions = sessions;
        this.identityClient = identityClient;
        this.agentHubClient = agentHubClient;
        this.turnSecret = turnSecret;
        this.turnUrls = List.of(turnUrlsCsv.split(","));
    }

    @Transactional
    public SessionResult createSession(UUID endpointId, UUID technicianId) {
        boolean canControl = identityClient.hasPermission(technicianId, "remote:desktop:control");
        boolean canView = identityClient.hasPermission(technicianId, "remote:desktop:view");
        if (!canControl && !canView) {
            throw new ForbiddenException("remote:desktop:view");
        }

        long expires = Instant.now().plusSeconds(3600).getEpochSecond();
        String sessionId = UUID.randomUUID().toString();
        String username = expires + ":" + sessionId;
        String credential = hmacSha1(turnSecret, username);

        DesktopSession session = sessions.save(
            new DesktopSession(endpointId, technicianId, username, credential));

        agentHubClient.startDesktopSession(endpointId, session.getId(), turnUrls, turnSecret);

        return new SessionResult(session, turnUrls, canControl);
    }

    public DesktopSession getSession(UUID sessionId) {
        return sessions.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));
    }

    @Transactional
    public void endSession(UUID sessionId, UUID technicianId) {
        DesktopSession session = sessions.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));
        if ("ended".equals(session.getStatus())) {
            return;
        }
        session.markEnded();
        sessions.save(session);
        agentHubClient.endDesktopSession(session.getEndpointId(), sessionId);
    }

    private String hmacSha1(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("TURN credential generation failed", e);
        }
    }

    public record SessionResult(DesktopSession session, List<String> turnUrls, boolean canControl) {}
}
