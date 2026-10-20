package vn.com.tns_hrms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReqClient {
    // ignored when creating, the id of the client when updating
    private long id;

    // required when creating (checked by the service), ignored when updating: a client id cannot be changed later
    @Size(min = 3, max = 100, message = "Client id must be between 3-100 characters")
    @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Client id may only contain letters, digits, '.', '_' and '-'")
    private String clientId;

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;

    @NotNull(message = "Role ID is required")
    @Positive(message = "Role ID must be greater than 0")
    private Long roleId;

    private boolean active = true;
}
