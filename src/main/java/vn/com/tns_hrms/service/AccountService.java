package vn.com.tns_hrms.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.com.tns_hrms.dto.request.ReqAccount;
import vn.com.tns_hrms.dto.response.ResAccount;
import vn.com.tns_hrms.dto.response.ResultPagination;
import vn.com.tns_hrms.dto.response.stats.ResAccountStats;
import vn.com.tns_hrms.entity.Account;
import vn.com.tns_hrms.entity.Role;
import vn.com.tns_hrms.exception.BadRequestException;
import vn.com.tns_hrms.exception.ResourceAlreadyExistsException;
import vn.com.tns_hrms.exception.ResourceNotFoundException;
import vn.com.tns_hrms.mapper.AccountMapper;
import vn.com.tns_hrms.mapper.PaginationMapper;
import vn.com.tns_hrms.repository.AccountRepository;
import vn.com.tns_hrms.repository.RoleRepository;

@Service
public class AccountService {
    public static final String ADMIN_ROLE = "ADMIN";

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;

    public AccountService(
            AccountRepository accountRepository,
            RoleRepository roleRepository,
            AccountMapper accountMapper,
            PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.accountMapper = accountMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public ResultPagination getAllAccountsWithPagination(Specification<Account> spec, Pageable pageable) {
        Page<Account> pageAccounts = accountRepository.findAll(spec, pageable);
        return PaginationMapper.toPaginationDTO(
                pageAccounts,
                accountMapper.toResAccountList(pageAccounts.getContent()));
    }

    @Transactional(readOnly = true)
    public ResAccount.Full getAccountDetail(long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + id));
        return accountMapper.toFullDTO(account);
    }

    @Transactional
    public ResAccount.Full createAccount(ReqAccount request) {
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Role not found with id: " + request.getRoleId()));
        if (!role.isActive()) {
            throw new BadRequestException("Cannot assign an inactive role");
        }

        if (accountRepository.findByEmail(request.getEmail()) != null) {
            throw new ResourceAlreadyExistsException(
                    "Account with email " + request.getEmail() + " already exists");
        }

        Account account = new Account();
        account.setEmail(request.getEmail());
        account.setUsername(request.getUsername());
        account.setPhone(request.getPhone());
        account.setPassword(passwordEncoder.encode(request.getPassword()));
        account.setRole(role);
        return accountMapper.toFullDTO(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public ResAccountStats getAccountStats() {
        return ResAccountStats.builder()
                .totalAccounts(accountRepository.count())
                .activeAccounts(accountRepository.countByActive(true))
                .adminAccounts(accountRepository.countByRole_Name(ADMIN_ROLE))
                .build();
    }

    @Transactional(readOnly = true)
    public Account getUserByEmail(String email) {
        return accountRepository.findByEmail(email);
    }
}
