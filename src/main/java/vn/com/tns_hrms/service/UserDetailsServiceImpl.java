package vn.com.tns_hrms.service;

import java.util.Collections;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import vn.com.tns_hrms.entity.Account;
import vn.com.tns_hrms.repository.AccountRepository;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final AccountRepository accountRepository;

    public UserDetailsServiceImpl(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = accountRepository.findByEmail(username);

        if (account == null) {
            throw new UsernameNotFoundException("User not found with email: " + username);
        }

        return User.builder()
                .username(account.getEmail())
                .password(account.getPassword())
                .disabled(!account.isActive())
                .authorities(Collections.singletonList(
                        new SimpleGrantedAuthority(
                                "ROLE_" + (account.getRole() != null ? account.getRole().getName() : "USER"))))
                .build();
    }
}
