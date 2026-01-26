package dev.pulsermm.identity.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pulsermm.identity.api.dto.ResolvedPermission;
import dev.pulsermm.identity.domain.*;
import dev.pulsermm.identity.infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionEvaluationServiceTest {

    @Mock UserRoleRepository userRoleRepository;
    @Mock RolePermissionRepository rolePermissionRepository;
    @Mock UserPermissionRepository userPermissionRepository;
    @Mock PermissionRepository permissionRepository;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) StringRedisTemplate redisTemplate;

    final ObjectMapper objectMapper = new ObjectMapper();

    PermissionEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new PermissionEvaluationService(
            userRoleRepository, rolePermissionRepository, userPermissionRepository,
            permissionRepository, redisTemplate, objectMapper);
    }

    @Test
    void userWithRoleGetsRolePermissions() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID permId = UUID.randomUUID();
        Permission perm = new Permission("endpoint:view");
        perm.setId(permId);

        when(userRoleRepository.findAllByIdUserId(userId))
            .thenReturn(List.of(new UserRole(new UserRoleId(userId, roleId))));
        when(rolePermissionRepository.findAllByIdRoleId(roleId))
            .thenReturn(List.of(new RolePermission(roleId, permId)));
        when(userPermissionRepository.findAllByIdUserId(userId)).thenReturn(List.of());
        when(permissionRepository.findById(permId)).thenReturn(Optional.of(perm));

        Set<ResolvedPermission> result = service.resolve(userId);

        assertThat(result).containsExactly(new ResolvedPermission("endpoint:view", null));
    }

    @Test
    void directGrantWithGroupScopeIsIncluded() {
        UUID userId = UUID.randomUUID();
        UUID permId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Permission perm = new Permission("remote:shell");
        perm.setId(permId);

        when(userRoleRepository.findAllByIdUserId(userId)).thenReturn(List.of());
        when(userPermissionRepository.findAllByIdUserId(userId))
            .thenReturn(List.of(new UserPermission(userId, permId, groupId, null)));
        when(permissionRepository.findById(permId)).thenReturn(Optional.of(perm));

        Set<ResolvedPermission> result = service.resolve(userId);

        assertThat(result).containsExactly(new ResolvedPermission("remote:shell", groupId));
    }

    @Test
    void expiredDirectGrantIsExcluded() {
        UUID userId = UUID.randomUUID();
        UUID permId = UUID.randomUUID();

        when(userRoleRepository.findAllByIdUserId(userId)).thenReturn(List.of());
        when(userPermissionRepository.findAllByIdUserId(userId))
            .thenReturn(List.of(new UserPermission(userId, permId, null, OffsetDateTime.now().minusSeconds(1))));

        Set<ResolvedPermission> result = service.resolve(userId);

        assertThat(result).isEmpty();
        verify(permissionRepository, never()).findById(any());
    }

    @Test
    void roleAndDirectGrantAreUnioned() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID perm1Id = UUID.randomUUID();
        UUID perm2Id = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Permission perm1 = new Permission("endpoint:view");
        perm1.setId(perm1Id);
        Permission perm2 = new Permission("remote:shell");
        perm2.setId(perm2Id);

        when(userRoleRepository.findAllByIdUserId(userId))
            .thenReturn(List.of(new UserRole(new UserRoleId(userId, roleId))));
        when(rolePermissionRepository.findAllByIdRoleId(roleId))
            .thenReturn(List.of(new RolePermission(roleId, perm1Id)));
        when(userPermissionRepository.findAllByIdUserId(userId))
            .thenReturn(List.of(new UserPermission(userId, perm2Id, groupId, null)));
        when(permissionRepository.findById(perm1Id)).thenReturn(Optional.of(perm1));
        when(permissionRepository.findById(perm2Id)).thenReturn(Optional.of(perm2));

        Set<ResolvedPermission> result = service.resolve(userId);

        assertThat(result).containsExactlyInAnyOrder(
            new ResolvedPermission("endpoint:view", null),
            new ResolvedPermission("remote:shell", groupId)
        );
    }
}
