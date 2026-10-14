package vn.com.tns_hrms.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.com.tns_hrms.dto.request.ReqPermission;
import vn.com.tns_hrms.dto.response.ResPermission;
import vn.com.tns_hrms.dto.response.ResultPagination;
import vn.com.tns_hrms.dto.response.stats.ResPermissionStats;
import vn.com.tns_hrms.entity.Permission;
import vn.com.tns_hrms.exception.ResourceAlreadyExistsException;
import vn.com.tns_hrms.exception.ResourceNotFoundException;
import vn.com.tns_hrms.mapper.PaginationMapper;
import vn.com.tns_hrms.mapper.PermissionMapper;
import vn.com.tns_hrms.repository.PermissionRepository;

@Service
public class PermissionService {
    private final PermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;

    public PermissionService(PermissionRepository permissionRepository, PermissionMapper permissionMapper) {
        this.permissionRepository = permissionRepository;
        this.permissionMapper = permissionMapper;
    }

    @Transactional(readOnly = true)
    public ResultPagination getAllPermissionWithPagination(Specification<Permission> spec, String module,
            Pageable pageable) {
        Specification<Permission> finalSpec = spec;

        if (module != null && !module.isBlank()) {
            Specification<Permission> moduleSpec = (root, query, cb) -> cb.equal(cb.lower(root.get("module")),
                    module.trim().toLowerCase());
            finalSpec = (finalSpec == null) ? moduleSpec : finalSpec.and(moduleSpec);
        }

        Page<Permission> pagePermissions = this.permissionRepository.findAll(finalSpec, pageable);
        return PaginationMapper.toPaginationDTO(pagePermissions,
                permissionMapper.toDTOList(pagePermissions.getContent()));
    }

    @Transactional
    public ResPermission createPermission(ReqPermission reqPermission) {
        if (permissionRepository.existsByApiPathAndMethod(reqPermission.getApiPath(), reqPermission.getMethod())) {
            throw new ResourceAlreadyExistsException(
                    "Permission already exists with this ApiPath and Method");
        }
        Permission permission = permissionMapper.toEntity(reqPermission);
        return permissionMapper.toDTO(permissionRepository.save(permission));
    }

    @Transactional
    public ResPermission updatePermission(ReqPermission reqPermission) {
        Permission existingPermission = permissionRepository.findById(reqPermission.getId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Permission not found with id: " + reqPermission.getId()));

        Optional<Permission> duplicatePermission = permissionRepository.findByApiPathAndMethod(
                reqPermission.getApiPath(), reqPermission.getMethod());
        if (duplicatePermission.isPresent() && duplicatePermission.get().getId() != reqPermission.getId()) {
            throw new ResourceAlreadyExistsException(
                    "Another permission already exists with this ApiPath and Method combination");
        }

        existingPermission.setName(reqPermission.getName());
        existingPermission.setApiPath(reqPermission.getApiPath());
        existingPermission.setMethod(reqPermission.getMethod());
        existingPermission.setModule(reqPermission.getModule());
        existingPermission.setActive(reqPermission.isActive());

        return permissionMapper.toDTO(permissionRepository.save(existingPermission));
    }

    @Transactional
    public void deletePermission(long id) {
        Permission existingPermission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found with id: " + id));

        // The permission_role join rows are owned by Role, so detach it from every role first.
        existingPermission.getRoles().forEach(role -> role.getPermissions().remove(existingPermission));
        permissionRepository.delete(existingPermission);
    }

    @Transactional(readOnly = true)
    public ResPermissionStats getPermissionStats() {
        return ResPermissionStats.builder()
                .totalPermissions(permissionRepository.count())
                .activePermissions(permissionRepository.countByActive(true))
                .totalModules(permissionRepository.countDistinctModule())
                .build();
    }

    @Transactional(readOnly = true)
    public List<String> getAllModules() {
        return this.permissionRepository.findDistinctModules();
    }

    @Transactional(readOnly = true)
    public ResPermission getPermissionById(long id) {
        Permission permission = this.permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found with id: " + id));
        return this.permissionMapper.toDTO(permission);
    }

    @Transactional(readOnly = true)
    public List<ResPermission> getAllPermissions() {
        return this.permissionMapper.toDTOList(this.permissionRepository.findAll());
    }
}
