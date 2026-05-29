package dev.pulsermm.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

@Aspect
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditPublisher publisher;
    private final ObjectMapper objectMapper;

    public AuditAspect(AuditPublisher publisher, ObjectMapper objectMapper) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        Object result = pjp.proceed();

        try {
            publisher.publish(buildMessage(pjp, auditable));
        } catch (Exception e) {
            log.warn("Failed to build audit message for action {}: {}", auditable.action(), e.getMessage());
        }

        return result;
    }

    private AuditEventMessage buildMessage(ProceedingJoinPoint pjp, Auditable auditable) {
        UUID userId = extractUserId();
        UUID endpointId = extractEndpointId(pjp);
        String payloadJson = auditable.capturePayload() ? extractPayload(pjp) : null;

        return new AuditEventMessage(
            userId,
            null,
            auditable.permission(),
            auditable.action(),
            endpointId,
            payloadJson,
            Instant.now()
        );
    }

    private UUID extractUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        // getName() is the JWT subject (Keycloak sub) for resource-server auth,
        // and the principal string for the older filter-based auth.
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private UUID extractEndpointId(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        Object[] args = pjp.getArgs();

        for (int i = 0; i < paramAnnotations.length; i++) {
            for (Annotation ann : paramAnnotations[i]) {
                if (ann instanceof EndpointId && args[i] instanceof UUID id) {
                    return id;
                }
            }
        }
        return null;
    }

    private String extractPayload(ProceedingJoinPoint pjp) {
        for (Object arg : pjp.getArgs()) {
            if (arg == null || arg instanceof UUID
                    || arg.getClass().isPrimitive() || arg.getClass().isEnum()) {
                continue;
            }
            try {
                return objectMapper.writeValueAsString(arg);
            } catch (JsonProcessingException e) {
                return null;
            }
        }
        return null;
    }
}
