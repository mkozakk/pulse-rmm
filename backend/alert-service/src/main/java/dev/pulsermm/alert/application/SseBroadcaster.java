package dev.pulsermm.alert.application;

import dev.pulsermm.alert.api.dto.AlertEventResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SseBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(SseBroadcaster.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    @EventListener
    public void onAlertFired(AlertFiredEvent event) {
        AlertEventResponse payload = AlertEventResponse.from(event.getAlertEvent());
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("alert").data(payload));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
        log.debug("SSE: broadcast alert to {} clients", emitters.size());
    }

    @Scheduled(fixedDelay = 15_000)
    public void heartbeat() {
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }
}
