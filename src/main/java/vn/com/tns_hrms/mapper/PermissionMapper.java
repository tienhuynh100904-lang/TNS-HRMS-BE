package vn.com.tns_hrms.mapper;

import java.util.List;
import java.util.Set;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import vn.com.tns_hrms.dto.request.ReqPermission;
import vn.com.tns_hrms.dto.response.ResPermission;
import vn.com.tns_hrms.entity.Permission;

@Mapper(componentModel = "spring")
public interface PermissionMapper {
    ResPermission toDTO(Permission permission);

    List<ResPermission> toDTOList(List<Permission> permissions);

    List<ResPermission> toDTOSet(Set<Permission> permissions);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "roles", ignore = true)
    Permission toEntity(ReqPermission reqPermission);
}
