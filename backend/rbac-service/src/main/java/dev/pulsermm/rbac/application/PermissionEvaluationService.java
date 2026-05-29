package dev.pulsermm.rbac.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pulsermm.rbac.api.dto.ResolvedPermission;
import dev.pulsermm.rbac.domain.UserPermission;
import dev.pulsermm.rbac.infrastructure.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class PermissionEvaluationService {

    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final PermissionRepository permissionRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PermissionEvaluationService(UserRoleRepository userRoleRepository,
                                       RolePermissionRepository rolePermissionRepository,
                                       UserPermissionRepository userPermissionRepository,
                                       PermissionRepository permissionRepository,
                                       StringRedisTemplate redisTemplate,
                                       ObjectMapper objectMapper) {
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userPermissionRepository = userPermissionRepository;
        this.permissionRepository = permissionRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Set<ResolvedPermission> resolve(UUID userId) {
        String key = "perm:" + userId;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        Set<ResolvedPermission> result = computeFromDb(userId);

        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result), Duration.ofSeconds(60));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    public void invalidate(UUID userId) {
        redisTemplate.delete("perm:" + userId);
    }

    private Set<ResolvedPermission> computeFromDb(UUID userId) {
        Set<ResolvedPermission> result = new HashSet<>();

        for (var userRole : userRoleRepository.findAllByIdUserId(userId)) {
            for (var rp : rolePermissionRepository.findAllByIdRoleId(userRole.getId().getRoleId())) {
                var perm = permissionRepository.findById(rp.getId().getPermissionId()).orElseThrow();
                result.add(new ResolvedPermission(perm.getName(), rp.getGroupScopeId()));
            }
        }

        for (UserPermission up : userPermissionRepository.findAllByIdUserId(userId)) {
            if (up.getExpiresAt() != null && up.getExpiresAt().isBefore(OffsetDateTime.now())) {
                continue;
            }
            var perm = permissionRepository.findById(up.getId().getPermissionId()).orElseThrow();
            result.add(new ResolvedPermission(perm.getName(), up.getGroupScopeId()));
        }

        return result;
    }
}
