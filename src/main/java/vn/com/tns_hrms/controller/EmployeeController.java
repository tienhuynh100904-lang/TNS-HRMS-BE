package vn.com.tns_hrms.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.turkraft.springfilter.boot.Filter;

import jakarta.validation.Valid;
import vn.com.tns_hrms.dto.ApiResponse;
import vn.com.tns_hrms.dto.request.ReqEmployee;
import vn.com.tns_hrms.dto.response.ResEmployee;
import vn.com.tns_hrms.dto.response.ResultPagination;
import vn.com.tns_hrms.entity.Employee;
import vn.com.tns_hrms.service.EmployeeService;

@RestController
@RequestMapping("/api/v1/employees")
@Validated
public class EmployeeController {
    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    /**
     * Employees, paged (page starts at 0, default size 20; sort with sort=fullName,asc). Filter with spring-filter,
     * for example filter=active:true or filter=department.id:5 or filter=fullName~'*thanh*'.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<ResultPagination>> getAllEmployeesWithPagination(
            @Filter Specification<Employee> spec,
            Pageable pageable) {
        ApiResponse<ResultPagination> response = ApiResponse.<ResultPagination>builder()
                .status(HttpStatus.OK.value())
                .message("Employees retrieved successfully")
                .data(employeeService.getAllEmployeesWithPagination(spec, pageable))
                .build();
        return ResponseEntity.ok(response);
    }

    /**
     * Replaces the data of an employee (id in the body): codes, name, department and active. Whatever is left out
     * is cleared. 404 when the employee or the department does not exist, 409 when another employee has the code.
     */
    @PutMapping
    public ResponseEntity<ApiResponse<ResEmployee>> updateEmployee(@Valid @RequestBody ReqEmployee request) {
        ApiResponse<ResEmployee> response = ApiResponse.<ResEmployee>builder()
                .status(HttpStatus.OK.value())
                .message("Employee updated successfully")
                .data(employeeService.updateEmployee(request))
                .build();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEmployee(@PathVariable long id) {
        employeeService.deleteEmployee(id);
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Employee deleted successfully")
                .build();
        return ResponseEntity.ok(response);
    }
}
