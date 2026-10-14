package vn.com.tns_hrms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ReqPermission {
    private long id;

    @NotBlank(message = "Permission name is required")
    @Size(max = 255, message = "Permission name must not exceed 255 characters")
    private String name;

    @NotBlank(message = "API Path is required")
    @Size(max = 255, message = "API Path must not exceed 255 characters")
    private String apiPath;

    @NotBlank(message = "Method is required")
    @Size(max = 50, message = "Method must not exceed 50 characters")
    private String method;

    @NotBlank(message = "Module is required")
    @Size(max = 255, message = "Module must not exceed 255 characters")
    private String module;

    private boolean active;
}
