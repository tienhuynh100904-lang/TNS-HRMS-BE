package vn.com.tns_hrms.mapper;

import java.util.List;

import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Named;

import vn.com.tns_hrms.dto.response.ResAccount;
import vn.com.tns_hrms.entity.Account;

@Mapper(componentModel = "spring", uses = { RoleMapper.class })
public interface AccountMapper {
    @Named("toBasicDTO")
    ResAccount toResAccount(Account account);

    @IterableMapping(qualifiedByName = "toBasicDTO")
    List<ResAccount> toResAccountList(List<Account> accounts);

    @Named("toFullDTO")
    ResAccount.Full toFullDTO(Account account);

    @IterableMapping(qualifiedByName = "toFullDTO")
    List<ResAccount.Full> toFullDTOList(List<Account> accounts);
}
