package vn.com.tns_hrms.controller;

import java.util.List;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.turkraft.springfilter.boot.Filter;

import jakarta.validation.Valid;
import vn.com.tns_hrms.dto.ApiResponse;
import vn.com.tns_hrms.dto.request.ReqPermission;
import vn.com.tns_hrms.dto.response.ResPermission;
import vn.com.tns_hrms.dto.response.ResultPagination;
import vn.com.tns_hrms.dto.response.stats.ResPermissionStats;
import vn.com.tns_hrms.entity.Permission;
import vn.com.tns_hrms.service.PermissionService;

@RestController
@RequestMapping("/api/v1/permissions")
@Validated
public class PermissionController {
    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ResultPagination>> getAllPermissionWithPagination(
            @Filter Specification<Permission> spec,
            @RequestParam(required = false) String module,
            Pageable pageable) {
        ApiResponse<ResultPagination> response = ApiResponse.<ResultPagination>builder()
                .status(HttpStatus.OK.value())
                .message("Permissions retrieved successfully")
                .data(permissionService.getAllPermissionWithPagination(spec, module, pageable))
                .build();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ResPermission>> createPermission(
            @Valid @RequestBody ReqPermission reqPermission) {
        ApiResponse<ResPermission> response = ApiResponse.<ResPermission>builder()
                .status(HttpStatus.CREATED.value())
                .message("Permission created successfully")
                .data(permissionService.createPermission(reqPermission))
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping
    public ResponseEntity<ApiResponse<ResPermission>> updatePermission(
            @Valid @RequestBody ReqPermission reqPermission) {
        ApiResponse<ResPermission> response = ApiResponse.<ResPermission>builder()
                .status(HttpStatus.OK.value())
                .message("Permission updated successfully")
                .data(permissionService.updatePermission(reqPermission))
                .build();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePermission(@PathVariable long id) {
        permissionService.deletePermission(id);
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Permission deleted successfully")
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<ResPermissionStats>> getPermissionStats() {
        ApiResponse<ResPermissionStats> response = ApiResponse.<ResPermissionStats>builder()
                .status(HttpStatus.OK.value())
                .message("Permission statistics retrieved successfully")
                .data(permissionService.getPermissionStats())
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/modules")
    public ResponseEntity<ApiResponse<List<String>>> getAllModules() {
        ApiResponse<List<String>> response = ApiResponse.<List<String>>builder()
                .status(HttpStatus.OK.value())
                .message("Modules retrieved successfully")
                .data(permissionService.getAllModules())
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<ResPermission>>> getAllPermissions() {
        ApiResponse<List<ResPermission>> response = ApiResponse.<List<ResPermission>>builder()
                .status(HttpStatus.OK.value())
                .message("All permissions retrieved successfully")
                .data(permissionService.getAllPermissions())
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ResPermission>> getPermissionById(@PathVariable long id) {
        ApiResponse<ResPermission> response = ApiResponse.<ResPermission>builder()
                .status(HttpStatus.OK.value())
                .message("Permission retrieved successfully")
                .data(permissionService.getPermissionById(id))
                .build();
        return ResponseEntity.ok(response);
    }
}
