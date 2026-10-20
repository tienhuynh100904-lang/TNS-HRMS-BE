package vn.com.tns_hrms.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import vn.com.tns_hrms.dto.ApiResponse;
import vn.com.tns_hrms.dto.request.ReqClient;
import vn.com.tns_hrms.dto.response.ResClient;
import vn.com.tns_hrms.dto.response.ResultPagination;
import vn.com.tns_hrms.service.ApiClientService;

/** Manages the OAuth2 clients that may call the API. Secrets are shown once, when created or renewed. */
@RestController
@RequestMapping("/api/v1/clients")
@Validated
public class ApiClientController {
    private final ApiClientService clientService;

    public ApiClientController(ApiClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ResultPagination>> getAllClientsWithPagination(Pageable pageable) {
        ApiResponse<ResultPagination> response = ApiResponse.<ResultPagination>builder()
                .status(HttpStatus.OK.value())
                .message("Clients retrieved successfully")
                .data(clientService.getAllClientsWithPagination(pageable))
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ResClient>> getClientDetail(@PathVariable long id) {
        ApiResponse<ResClient> response = ApiResponse.<ResClient>builder()
                .status(HttpStatus.OK.value())
                .message("Client retrieved successfully")
                .data(clientService.getClientDetail(id))
                .build();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ResClient.WithSecret>> createClient(@Valid @RequestBody ReqClient request) {
        ApiResponse<ResClient.WithSecret> response = ApiResponse.<ResClient.WithSecret>builder()
                .status(HttpStatus.CREATED.value())
                .message("Client created successfully, store the secret now: it cannot be shown again")
                .data(clientService.createClient(request))
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping
    public ResponseEntity<ApiResponse<ResClient>> updateClient(@Valid @RequestBody ReqClient request) {
        ApiResponse<ResClient> response = ApiResponse.<ResClient>builder()
                .status(HttpStatus.OK.value())
                .message("Client updated successfully")
                .data(clientService.updateClient(request))
                .build();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/secret")
    public ResponseEntity<ApiResponse<ResClient.WithSecret>> rotateSecret(@PathVariable long id) {
        ApiResponse<ResClient.WithSecret> response = ApiResponse.<ResClient.WithSecret>builder()
                .status(HttpStatus.OK.value())
                .message("Secret renewed successfully, store it now: it cannot be shown again")
                .data(clientService.rotateSecret(id))
                .build();
        return ResponseEntity.ok(response);
    }
}
