package vn.com.tns_hrms.service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.com.tns_hrms.dto.request.ReqRole;
import vn.com.tns_hrms.dto.response.ResRole;
import vn.com.tns_hrms.dto.response.ResultPagination;
import vn.com.tns_hrms.dto.response.stats.ResRoleStats;
import vn.com.tns_hrms.entity.Permission;
import vn.com.tns_hrms.entity.Role;
import vn.com.tns_hrms.exception.BadRequestException;
import vn.com.tns_hrms.exception.ResourceAlreadyExistsException;
import vn.com.tns_hrms.exception.ResourceNotFoundException;
import vn.com.tns_hrms.mapper.PaginationMapper;
import vn.com.tns_hrms.mapper.RoleMapper;
import vn.com.tns_hrms.repository.PermissionRepository;
import vn.com.tns_hrms.repository.RoleRepository;

@Service
public class RoleService {
    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;
    private final PermissionRepository permissionRepository;

    public RoleService(RoleRepository roleRepository, RoleMapper roleMapper,
            PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.roleMapper = roleMapper;
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public ResultPagination getAllRoleWithPagination(Specification<Role> spec, Pageable pageable) {
        Page<Role> pageRoles = this.roleRepository.findAll(spec, pageable);
        return PaginationMapper.toPaginationDTO(pageRoles, roleMapper.toDTOList(pageRoles.getContent()));
    }

    @Transactional(readOnly = true)
    public ResRole.Detail getRoleDetail(long id) {
        Role role = roleRepository.findWithPermissionsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + id));
        return roleMapper.toDetailDTO(role);
    }

    @Transactional
    public ResRole.Detail createRole(ReqRole reqRole) {
        if (roleRepository.existsByName(reqRole.getName())) {
            throw new ResourceAlreadyExistsException("Role already exists with name: " + reqRole.getName());
        }

        Role role = roleMapper.toEntity(reqRole);

        if (reqRole.getPermissions() != null && !reqRole.getPermissions().isEmpty()) {
            List<Permission> permissions = permissionRepository.findAllById(reqRole.getPermissions());
            role.setPermissions(new HashSet<>(permissions));
        }

        return roleMapper.toDetailDTO(roleRepository.save(role));
    }

    @Transactional
    public ResRole.Detail updateRole(ReqRole reqRole) {
        Role existingRole = roleRepository.findById(reqRole.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + reqRole.getId()));

        Optional<Role> duplicateRole = roleRepository.findByName(reqRole.getName());
        if (duplicateRole.isPresent() && duplicateRole.get().getId() != reqRole.getId()) {
            throw new ResourceAlreadyExistsException("Another role already exists with name: " + reqRole.getName());
        }

        if (Role.ADMIN.equals(existingRole.getName())
                && (!Role.ADMIN.equals(reqRole.getName()) || !reqRole.isActive())) {
            throw new BadRequestException("The ADMIN role cannot be renamed or deactivated");
        }

        existingRole.setName(reqRole.getName());
        existingRole.setDescription(reqRole.getDescription());
        existingRole.setActive(reqRole.isActive());

        if (reqRole.getPermissions() != null) {
            List<Permission> permissions = permissionRepository.findAllById(reqRole.getPermissions());
            existingRole.setPermissions(new HashSet<>(permissions));
        } else {
            existingRole.setPermissions(new HashSet<>());
        }

        return roleMapper.toDetailDTO(roleRepository.save(existingRole));
    }

    @Transactional
    public void deleteRole(long id) {
        Role existingRole = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + id));

        if (Role.ADMIN.equals(existingRole.getName())) {
            throw new BadRequestException("The ADMIN role cannot be deleted");
        }
        if (existingRole.getClients() != null && !existingRole.getClients().isEmpty()) {
            throw new BadRequestException("Cannot delete role because it is currently assigned to one or more clients");
        }

        roleRepository.delete(existingRole);
    }

    @Transactional(readOnly = true)
    public ResRoleStats getRoleStats() {
        return ResRoleStats.builder()
                .totalRoles(roleRepository.count())
                .activeRoles(roleRepository.countByActive(true))
                .totalPermissions(permissionRepository.count())
                .totalModules(permissionRepository.countDistinctModule())
                .build();
    }
}
