package vn.com.tns_hrms.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.com.tns_hrms.dto.request.ReqEmployee;
import vn.com.tns_hrms.dto.response.ResEmployee;
import vn.com.tns_hrms.dto.response.ResultPagination;
import vn.com.tns_hrms.entity.Department;
import vn.com.tns_hrms.entity.Employee;
import vn.com.tns_hrms.exception.ResourceAlreadyExistsException;
import vn.com.tns_hrms.exception.ResourceNotFoundException;
import vn.com.tns_hrms.mapper.PaginationMapper;
import vn.com.tns_hrms.repository.DepartmentRepository;
import vn.com.tns_hrms.repository.EmployeeRepository;

@Service
public class EmployeeService {
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;

    public EmployeeService(EmployeeRepository employeeRepository, DepartmentRepository departmentRepository) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public ResultPagination getAllEmployeesWithPagination(Specification<Employee> spec, Pageable pageable) {
        Page<Employee> page = employeeRepository.findAll(spec, pageable);
        return PaginationMapper.toPaginationDTO(page, page.getContent().stream().map(EmployeeService::toDto).toList());
    }

    /** Replaces the editable data of an employee. An empty code is stored as "no code" so it cannot clash. */
    @Transactional
    public ResEmployee updateEmployee(ReqEmployee request) {
        Employee employee = employeeRepository.findById(request.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + request.getId()));

        String employeeCode = blankToNull(request.getEmployeeCode());
        String userCode = blankToNull(request.getUserCode());
        if (employeeCode != null && employeeRepository.findByEmployeeCode(employeeCode)
                .filter(other -> !other.getId().equals(employee.getId())).isPresent()) {
            throw new ResourceAlreadyExistsException("Another employee already has employee code: " + employeeCode);
        }
        if (userCode != null && employeeRepository.findByUserCode(userCode)
                .filter(other -> !other.getId().equals(employee.getId())).isPresent()) {
            throw new ResourceAlreadyExistsException("Another employee already has user code: " + userCode);
        }
        Department department = request.getDepartmentId() == null ? null
                : departmentRepository.findById(request.getDepartmentId()).orElseThrow(
                        () -> new ResourceNotFoundException("Department not found with id: " + request.getDepartmentId()));

        employee.setEmployeeCode(employeeCode);
        employee.setUserCode(userCode);
        employee.setCardCode(blankToNull(request.getCardCode()));
        employee.setFullName(blankToNull(request.getFullName()));
        employee.setDepartment(department);
        employee.setActive(request.getActive());
        try {
            return toDto(employeeRepository.saveAndFlush(employee));
        } catch (DataIntegrityViolationException e) {
            // someone else took the code between our check and the save
            throw new ResourceAlreadyExistsException("Employee code or user code is already used by another employee");
        }
    }

    /**
     * Deletes the employee. Their punches stay in punch_raw (they belong to the user code, not to the employee), so
     * giving the same user code to another employee later shows them again.
     */
    @Transactional
    public void deleteEmployee(long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
        employeeRepository.delete(employee);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ResEmployee toDto(Employee e) {
        return new ResEmployee(e.getId(), e.getEmployeeCode(), e.getUserCode(), e.getCardCode(), e.getFullName(),
                e.getDepartment() == null ? null : e.getDepartment().getId(),
                e.getDepartment() == null ? null : e.getDepartment().getName(), e.isActive());
    }
}
