package vn.com.tns_hrms.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.turkraft.springfilter.boot.Filter;

import jakarta.validation.Valid;
import vn.com.tns_hrms.dto.ApiResponse;
import vn.com.tns_hrms.dto.request.ReqRole;
import vn.com.tns_hrms.dto.response.ResRole;
import vn.com.tns_hrms.dto.response.ResultPagination;
import vn.com.tns_hrms.dto.response.stats.ResRoleStats;
import vn.com.tns_hrms.entity.Role;
import vn.com.tns_hrms.service.RoleService;

@RestController
@RequestMapping("/api/v1/roles")
@Validated
public class RoleController {
    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ResultPagination>> getAllRoleWithPagination(
            @Filter Specification<Role> spec,
            Pageable pageable) {
        ApiResponse<ResultPagination> response = ApiResponse.<ResultPagination>builder()
                .status(HttpStatus.OK.value())
                .message("Roles retrieved successfully")
                .data(roleService.getAllRoleWithPagination(spec, pageable))
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ResRole.Detail>> getRoleDetail(@PathVariable long id) {
        ApiResponse<ResRole.Detail> response = ApiResponse.<ResRole.Detail>builder()
                .status(HttpStatus.OK.value())
                .message("Role retrieved successfully")
                .data(roleService.getRoleDetail(id))
                .build();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ResRole.Detail>> createRole(@Valid @RequestBody ReqRole reqRole) {
        ApiResponse<ResRole.Detail> response = ApiResponse.<ResRole.Detail>builder()
                .status(HttpStatus.CREATED.value())
                .message("Role created successfully")
                .data(roleService.createRole(reqRole))
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping
    public ResponseEntity<ApiResponse<ResRole.Detail>> updateRole(@Valid @RequestBody ReqRole reqRole) {
        ApiResponse<ResRole.Detail> response = ApiResponse.<ResRole.Detail>builder()
                .status(HttpStatus.OK.value())
                .message("Role updated successfully")
                .data(roleService.updateRole(reqRole))
                .build();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable long id) {
        roleService.deleteRole(id);
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Role deleted successfully")
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<ResRoleStats>> getRoleStats() {
        ApiResponse<ResRoleStats> response = ApiResponse.<ResRoleStats>builder()
                .status(HttpStatus.OK.value())
                .message("Role statistics retrieved successfully")
                .data(roleService.getRoleStats())
                .build();
        return ResponseEntity.ok(response);
    }
}
