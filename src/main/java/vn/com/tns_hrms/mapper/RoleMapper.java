package vn.com.tns_hrms.mapper;

import java.util.List;

import org.mapstruct.InheritConfiguration;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import vn.com.tns_hrms.dto.request.ReqRole;
import vn.com.tns_hrms.dto.response.ResRole;
import vn.com.tns_hrms.entity.Role;

@Mapper(componentModel = "spring", uses = { PermissionMapper.class })
public interface RoleMapper {
    @Named("toBasicDTO")
    ResRole toDTO(Role role);

    @IterableMapping(qualifiedByName = "toBasicDTO")
    List<ResRole> toDTOList(List<Role> roles);

    @Named("toDetailDTO")
    @InheritConfiguration(name = "toDTO")
    @Mapping(source = "permissions", target = "permissions")
    ResRole.Detail toDetailDTO(Role role);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "accounts", ignore = true)
    @Mapping(target = "permissions", ignore = true)
    Role toEntity(ReqRole reqRole);
}
