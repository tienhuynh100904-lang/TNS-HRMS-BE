package vn.com.tns_hrms.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.turkraft.springfilter.boot.Filter;

import jakarta.validation.Valid;
import vn.com.tns_hrms.dto.ApiResponse;
import vn.com.tns_hrms.dto.request.ReqAccount;
import vn.com.tns_hrms.dto.response.ResAccount;
import vn.com.tns_hrms.dto.response.ResultPagination;
import vn.com.tns_hrms.dto.response.stats.ResAccountStats;
import vn.com.tns_hrms.entity.Account;
import vn.com.tns_hrms.service.AccountService;

@RestController
@RequestMapping("/api/v1/accounts")
@Validated
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ResultPagination>> getAllAccountsWithPagination(
            @Filter Specification<Account> spec,
            Pageable pageable) {
        ApiResponse<ResultPagination> response = ApiResponse.<ResultPagination>builder()
                .status(HttpStatus.OK.value())
                .message("Accounts retrieved successfully")
                .data(accountService.getAllAccountsWithPagination(spec, pageable))
                .build();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ResAccount.Full>> createAccount(
            @Valid @RequestBody ReqAccount request) {
        ApiResponse<ResAccount.Full> response = ApiResponse.<ResAccount.Full>builder()
                .status(HttpStatus.CREATED.value())
                .message("Account created successfully")
                .data(accountService.createAccount(request))
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<ResAccountStats>> getAccountStats() {
        ApiResponse<ResAccountStats> response = ApiResponse.<ResAccountStats>builder()
                .status(HttpStatus.OK.value())
                .message("Account statistics retrieved successfully")
                .data(accountService.getAccountStats())
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ResAccount.Full>> getAccountDetail(@PathVariable long id) {
        ApiResponse<ResAccount.Full> response = ApiResponse.<ResAccount.Full>builder()
                .status(HttpStatus.OK.value())
                .message("Account retrieved successfully")
                .data(accountService.getAccountDetail(id))
                .build();
        return ResponseEntity.ok(response);
    }
}
