/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.mockserver.repository;

import com.carolcoral.mockserver.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    List<RolePermission> findByRoleId(Long roleId);
    void deleteByRoleId(Long roleId);
    void deleteByRoleIdAndPermissionId(Long roleId, Long permissionId);
    void deleteByPermissionId(Long permissionId);
    boolean existsByRoleIdAndPermissionId(Long roleId, Long permissionId);
    boolean existsByPermissionId(Long permissionId);
    List<RolePermission> findByRoleIdIn(List<Long> roleIds);
}
