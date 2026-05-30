package dev.pulsermm.rbac.api.dto;

import java.util.List;
import java.util.UUID;

public record UpdateUserRolesRequest(List<UUID> roleIds) {}
