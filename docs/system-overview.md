# TNS HRMS Backend – Tài liệu hệ thống

Tài liệu này mô tả cách hệ thống hoạt động: kiến trúc, cơ chế xác thực và phân quyền (OAuth2 client credentials), dữ liệu, chấm công, API, cấu hình và vận hành. Dành cho lập trình viên mới vào dự án và cho người tích hợp API.

## Mục lục
1. [Tổng quan](#1-tổng-quan)
2. [Cấu trúc source](#2-cấu-trúc-source)
3. [Xác thực và phân quyền](#3-xác-thực-và-phân-quyền)
4. [Mô hình dữ liệu](#4-mô-hình-dữ-liệu)
5. [Dữ liệu khởi tạo (seed)](#5-dữ-liệu-khởi-tạo-seed)
6. [Chấm công](#6-chấm-công)
7. [Tham chiếu API](#7-tham-chiếu-api)
8. [Cấu hình](#8-cấu-hình)
9. [Vận hành](#9-vận-hành)
10. [Test](#10-test)
11. [Lưu ý bảo mật và hạn chế đã biết](#11-lưu-ý-bảo-mật-và-hạn-chế-đã-biết)

---

## 1. Tổng quan

TNS HRMS là backend REST API quản lý nhân sự, hiện gồm các phần:
- **Nhân viên, phòng ban, chi nhánh**: dữ liệu danh mục.
- **Chấm công**: lấy dữ liệu quẹt (punch) từ máy chấm công ZKTeco, lưu lại và lập báo cáo theo ngày hoặc theo nhân viên.
- **Xác thực và phân quyền**: ứng dụng tự cấp token OAuth2 (client credentials) và phân quyền theo role.

Hệ thống **không có người dùng đăng nhập bằng email và mật khẩu**. Bên gọi API là các *client* (ứng dụng hoặc tài khoản kỹ thuật), mỗi client có `client_id`, `client_secret` và một role.

| Thành phần | Công nghệ |
|---|---|
| Ngôn ngữ, framework | Java 21, Spring Boot 4.1.1 |
| Bảo mật | Spring Security 7.1 (resource server + Authorization Server nhúng), JWT HS512 |
| Dữ liệu | Spring Data JPA (Hibernate), SQL Server; H2 cho test |
| Ánh xạ, tiện ích | MapStruct, Lombok, Spring Filter (lọc theo biểu thức) |
| Lấy dữ liệu máy chấm công | Script Python dùng thư viện `pyzk==0.9` |

Ứng dụng chạy mặc định ở cổng 8080.

---

## 2. Cấu trúc source

```
src/main/java/vn/com/tns_hrms
├── config/        Cấu hình bảo mật, CORS, interceptor phân quyền, log request, properties chấm công
│   └── seed/      Các seeder chạy khi khởi động (permission, role, client admin, chi nhánh, phòng ban, nhân viên)
├── controller/    REST controller: client, role, permission, employee, attendance
├── service/       Nghiệp vụ: ApiClientService, RoleService, PermissionService, EmployeeService,
│                  AttendanceReportService, PunchPullService, PunchImportService, PythonPunchPuller, PunchPullJob
├── repository/    Spring Data JPA repository + PunchReportRepository (truy vấn báo cáo)
├── entity/        Thực thể JPA
├── dto/           Request (Req*) và response (Res*), ApiResponse, ErrorResponse
├── mapper/        MapStruct và PaginationMapper
├── exception/     Exception nghiệp vụ và GlobalException (chuyển exception thành JSON)
├── helper/        DeviceCsvHelper (đọc CSV từ máy chấm công)
└── util/          SecurityUtil (khóa JWT, lấy client hiện tại)

src/main/resources
├── application.properties     Cấu hình (file này KHÔNG nằm trong git, xem mục 8)
└── seed/                      branches.csv, departments.csv, employees.csv

scripts/attendance/            zk_pull_punches.py + requirements.txt
lib/                           mssql-jdbc_auth-*.dll (đăng nhập SQL Server bằng Windows Authentication)
```

Quy ước chung:
- Controller mỏng, nghiệp vụ nằm ở service.
- Mọi response thành công bọc trong `ApiResponse`; mọi lỗi nghiệp vụ trả `ErrorResponse` (mục 7).
- Trường audit `createdAt/updatedAt/createdBy/updatedBy` được điền tự động; `createdBy` là `client_id` của người gọi.

---

## 3. Xác thực và phân quyền

### 3.1 Bức tranh tổng thể

```
        ┌────────────────────────── Ứng dụng tns-hrms ──────────────────────────┐
        │                                                                        │
Client ─┼─ POST /oauth2/token ─► [Chain 1: Authorization Server]                 │
        │   client_id+secret      kiểm tra client trong bảng api_clients         │
        │ ◄─ access_token (JWT) ─ ký HS512 bằng jwt.secret.base64                │
        │                                                                        │
Client ─┼─ GET /api/v1/... ─────► [Chain 2: Resource Server]                     │
        │   Authorization:         1. kiểm tra chữ ký + hạn của JWT              │
        │   Bearer <token>         2. PermissionInterceptor: client active?      │
        │                             role active? permission khớp path+method?  │
        │ ◄─ dữ liệu ──────────── 3. Controller                                  │
        └────────────────────────────────────────────────────────────────────────┘
```

Cấu hình nằm ở `config/SecurityConfiguration.java`. Có hai `SecurityFilterChain`:

| Chain | Áp dụng cho | Nhiệm vụ |
|---|---|---|
| 1 (`@Order(1)`) | chỉ `/oauth2/token` | Authorization Server: xác thực client và phát token |
| 2 (`@Order(2)`) | mọi đường dẫn còn lại | Resource Server: kiểm tra Bearer token. Cho qua `/`, `/error`, `/actuator/health` |

Chỉ endpoint token được mở. Các endpoint khác của Authorization Server (`/oauth2/authorize`, `/oauth2/jwks`, `/.well-known/...`) nằm ngoài chain 1 nên bị chain 2 chặn (401).

### 3.2 Lấy token (client credentials)

```
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
```
Client gửi thông tin xác thực theo một trong hai cách:
- Header `Authorization: Basic base64(client_id:client_secret)` (khuyến nghị).
- Hoặc `client_id` và `client_secret` trong body form.

Quá trình xử lý:
1. `ApiClientRegisteredClientRepository` tìm client theo `client_id` trong bảng `api_clients`. Client không tồn tại hoặc `active=false` thì coi như không có.
2. Secret gửi lên được băm bằng BCrypt và so với `client_secret` (hash) trong DB.
3. Đúng thì phát JWT, thời hạn `app.oauth2.access-token-validity-in-seconds` (mặc định 3600).

Kết quả:
```json
{ "access_token": "eyJhbGciOiJIUzUxMiJ9...", "token_type": "Bearer", "expires_in": 3599 }
```

Nội dung JWT:

| Claim | Ý nghĩa |
|---|---|
| `sub`, `client_id` | `client_id` của client |
| `aud` | `client_id` |
| `iss` | URL gốc của ứng dụng |
| `iat`, `nbf`, `exp` | thời điểm phát và hạn dùng |
| `jti` | mã định danh token |

Token ký **HS512** bằng khóa đối xứng `jwt.secret.base64` (tối thiểu 64 byte, mã hóa base64). Cùng khóa này được dùng để kiểm tra token ở chain 2.

Chỉ có grant `client_credentials`. Không có refresh token: khi hết hạn, client gọi lại endpoint token. Các grant khác (`password`, `refresh_token`, `authorization_code`) trả 400.

Lỗi của endpoint token theo chuẩn RFC 6749 (không bọc trong `ApiResponse`):

| Mã | `error` | Nguyên nhân |
|---|---|---|
| 400 | `invalid_request` | Thiếu `grant_type`, body không phải form, hoặc `client_id`/`client_secret` rỗng hay sai định dạng |
| 400 | `unsupported_grant_type` | Grant không phải `client_credentials` |
| 401 | `invalid_client` | Sai `client_id` hoặc secret, hoặc client bị khóa |

### 3.3 Gọi API và phân quyền

Mỗi request tới API:

1. **`RequestLogFilter`** ghi log method, đường dẫn, query và IP, trước cả Spring Security. Không ghi header hay body (nên không lộ token).
2. **Kiểm tra token.** Thiếu, sai chữ ký hoặc hết hạn thì trả 401 qua `CustomAuthenticationEntryPoint`:
   ```json
   { "status": 401, "error": "Token expired", "message": "Your token has expired, please log in again" }
   ```
3. **`PermissionInterceptor`** (chạy sau khi Spring MVC chọn được controller):
   - Lấy `sub` của token, tra client trong DB. Không tìm thấy hoặc `active=false` thì 403.
   - Role của client phải `active`, nếu không thì 403.
   - Role **`ADMIN`** đi qua mọi endpoint.
   - Role khác phải có permission `active` khớp **cả `apiPath` (mẫu đường dẫn, ví dụ `/api/v1/clients/{id}`) lẫn HTTP method**. Không khớp thì 403.
4. Controller xử lý.

Vì bước 3 đọc DB ở mỗi request, **khóa client hoặc đổi role có hiệu lực ngay lập tức**, kể cả khi token chưa hết hạn.

Endpoint chưa có permission nào thì chỉ role ADMIN gọi được (mặc định từ chối).

### 3.4 Mô hình Client, Role, Permission

```
ApiClient (api_clients) ──N:1──► Role (roles) ──N:M──► Permission (permissions)
 client_id, hash secret          name, active          apiPath + method + module
```

**Các role có sẵn** (tạo bởi `RoleSeeder`):

| Role | Quyền |
|---|---|
| `ADMIN` | Mọi permission, và bỏ qua kiểm tra permission. Không thể đổi tên, tắt hoặc xóa |
| `USER` | Mặc định chưa có permission nào |
| `CLIENT` | Chỉ 3 API chấm công, đều `GET`: `/attendance/users`, `/attendance/monthly`, `/attendance/daily` |

Quy tắc đáng nhớ:
- Role `CLIENT` chỉ được tạo một lần khi DB chưa có role tên đó. Sau đó seeder không đụng tới, nên các thay đổi của admin không bị ghi đè.
- Role `USER` và `ADMIN`: nếu đã tồn tại, mỗi lần khởi động chỉ bổ sung các permission mặc định còn thiếu và không gỡ permission nào. `ADMIN` nhận thêm mọi permission mới; `USER` mặc định không có permission nào.
- Không thể xóa role đang gán cho client.
- Client ADMIN đang hoạt động cuối cùng không thể bị khóa hoặc bị đổi sang role khác (tránh mất quyền quản trị).

### 3.5 Vòng đời client

| Việc | API | Ghi chú |
|---|---|---|
| Tạo | `POST /api/v1/clients` | Server sinh secret ngẫu nhiên 32 byte, trả về **một lần** |
| Đổi secret | `POST /api/v1/clients/{id}/secret` | Secret cũ ngừng hoạt động ngay |
| Khóa, đổi role, đổi tên | `PUT /api/v1/clients` | `active=false` chặn cả token đang dùng |
| Xem | `GET /api/v1/clients`, `GET /api/v1/clients/{id}` | Không bao giờ trả secret |

Secret **không được lưu dạng thô**: DB chỉ giữ hash BCrypt. Nếu mất secret thì phải đổi secret mới, không thể lấy lại.

### 3.6 Client admin đầu tiên

`AdminClientSeeder` tạo client admin khi bảng `api_clients` còn trống:
- `client_id`: `app.seed.admin-client-id` (mặc định `tns-hrms-admin`).
- Secret: `app.seed.admin-client-secret` (biến môi trường `ADMIN_CLIENT_SECRET`). Nếu để trống, hệ thống sinh secret ngẫu nhiên và **in ra log đúng một lần**.

Muốn đặt lại secret admin khi đã mất: dừng ứng dụng, chạy `DELETE FROM dbo.api_clients;`, đặt `ADMIN_CLIENT_SECRET` rồi khởi động lại (lưu ý lệnh này xóa **mọi** client).

### 3.7 Các biện pháp bảo mật khác
- `PasswordEncoder` BCrypt cho secret của client.
- CORS: chỉ các origin trong `app.cors.allowed-origins`, cho phép credentials; header cho phép `Authorization`, `Content-Type`, `Accept`, `x-no-retry`.
- CSRF tắt vì API stateless dùng Bearer token, không có session hay cookie xác thực.
- Lỗi không kèm stack trace: lỗi không lường trước trả 500 với thông báo chung, chi tiết chỉ nằm trong log.

---

## 4. Mô hình dữ liệu

Hibernate tự tạo và cập nhật bảng (`spring.jpa.hibernate.ddl-auto=update`).

| Bảng | Entity | Nội dung chính |
|---|---|---|
| `api_clients` | `ApiClient` | `client_id` (duy nhất), `client_secret` (hash BCrypt), `name`, `description`, `active`, `role_id`, trường audit |
| `roles` | `Role` | `name` (duy nhất), `description`, `active`, trường audit |
| `permissions` | `Permission` | `name`, `api_path`, `method`, `module`, `active`, trường audit |
| `permission_role` | (bảng nối) | Role và Permission (N:M) |
| `branches` | `Branch` | `branch_code` (khóa chính), `branch_name`, `source_system` |
| `departments` | `Department` | `id`, `code` (duy nhất), `name` (duy nhất) |
| `employees` | `Employee` | `employee_code`, `user_code` (đều duy nhất khi có), `card_code`, `full_name`, `department_id`, `active` |
| `punch_raw` | `PunchRaw` | Dữ liệu quẹt thô: `branch_code`, `user_code`, `punch_time`, `source_ref`, `verify_type`, `direction` |
| `attendance` | `Attendance` | Dự phòng cho bảng chấm công tính sẵn. **Hiện chưa có code ghi vào**; các báo cáo tính trực tiếp từ `punch_raw` |

Một số điểm cần nắm:
- **`user_code`** là mã của người trên máy chấm công. `employees.user_code` là cầu nối từ `punch_raw.user_code` tới nhân viên. Một nhân viên không gắn với một chi nhánh cố định: chi nhánh của một lần quẹt là chi nhánh của máy đã quẹt.
- **`punch_raw` chỉ thêm, không sửa.** Một lần quẹt được nhận diện bằng `(branch_code, user_code, punch_time)` (ràng buộc `uq_punch`), không dựa vào id trên máy, nên kéo trùng khoảng thời gian không tạo bản ghi trùng.
- Tên tiếng Việt lưu `nvarchar` (`@Nationalized`) để không mất dấu.
- Xóa nhân viên **không** xóa dữ liệu quẹt: dữ liệu quẹt gắn với `user_code`.

---

## 5. Dữ liệu khởi tạo (seed)

`DatabaseInitializer` chạy theo thứ tự mỗi lần khởi động:

| # | Seeder | Hành vi |
|---|---|---|
| 1 | `PermissionSeeder` | Đăng ký một permission cho mỗi endpoint được bảo vệ, khớp theo `(apiPath, method)` nên id và các lần gán cho role được giữ nguyên. Xóa các permission module `ACCOUNT` cũ |
| 2 | `RoleSeeder` | Tạo `USER`, `ADMIN`, `CLIENT` (xem 3.4) |
| 3 | `AdminClientSeeder` | Tạo client admin nếu bảng `api_clients` trống (xem 3.6) |
| 4 | `BranchSeeder` | Thêm các chi nhánh còn thiếu từ `seed/branches.csv`; chi nhánh đã có thì giữ nguyên |
| 5 | `DepartmentSeeder` | Đọc `seed/departments.csv` **chỉ khi bảng `departments` trống** |
| 6 | `EmployeeSeeder` | Đọc `seed/employees.csv` **chỉ khi bảng `employees` trống** |

Vì seed phòng ban và nhân viên chỉ chạy khi bảng trống, dữ liệu đã sửa tay trong DB không bị khôi phục khi khởi động lại.

Khi thêm endpoint mới cần phân quyền, hãy thêm permission tương ứng vào `PermissionSeeder`; nếu không, chỉ role ADMIN gọi được.

---

## 6. Chấm công

### 6.1 Luồng lấy dữ liệu từ máy chấm công

```
 Cron 01:00 hàng đêm            POST /api/v1/attendance/pull
 (PunchPullJob, nếu bật)        (gọi thủ công)
            └──────────┬───────────────┘
                       ▼
               PunchPullService          chỉ một lần kéo tại một thời điểm (khóa)
                       │  với mỗi thiết bị trong cấu hình:
                       ▼
   since = punch cuối cùng của chi nhánh − overlap-hours (48h); chưa có thì lấy tất cả
                       ▼
   PythonPunchPuller ── chạy zk_pull_punches.py --ip --port --since --out-logs <csv>
                       ▼
   DeviceCsvHelper đọc CSV ──► PunchImportService
                       ▼
   Bỏ dòng lỗi và trùng, chỉ INSERT các lần quẹt mới vào punch_raw
```

- Chỉ **đọc** từ máy, không xóa hay sửa gì trên máy.
- Script Python ghi CSV ra thư mục `app.attendance.pull.output-dir`, Java đọc lại rồi xóa file tạm. Script bị hủy nếu chạy quá `timeout-minutes` (10 phút).
- **Chống trùng ba lớp:** loại các dòng lặp trong cùng lô; loại các dòng đã có trong DB; ràng buộc `uq_punch` làm chốt cuối khi hai lần nhập chạy đồng thời.
- Một thiết bị lỗi chỉ được ghi log và không làm dừng các thiết bị khác. Với lệnh gọi thủ công, kết quả của từng thiết bị được trả về riêng (số lượng hoặc thông báo lỗi).
- Vì chỉ một lần kéo được chạy một lúc: lệnh gọi thủ công khi đang có lần kéo khác trả **409**; job ban đêm gặp trường hợp này thì bỏ qua lần đó.

### 6.2 Các báo cáo

Tất cả đều tính trực tiếp từ `punch_raw` nối với `employees` (qua `user_code`) và `departments`. Một người quẹt nhưng chưa có nhân viên nào sở hữu `user_code` vẫn xuất hiện, với `employeeCode`, `fullName`, `departmentName` là `null`.

- **`/attendance/users`**: mọi lần quẹt trong một ngày (mỗi dòng một lần quẹt), sắp xếp theo giờ.
- **`/attendance/daily`**: một dòng cho mỗi người trong ngày: giờ vào là lần quẹt đầu, giờ ra là lần quẹt cuối, kèm số lần quẹt và trạng thái.
- **`/attendance/monthly`**: toàn bộ lần quẹt của một nhân viên, lọc theo `from`/`to` (tính cả hai ngày), sắp xếp theo `punchTime`.

**Quy tắc của một ngày** (`daily`):
- Chỉ coi là có giờ ra khi có ít nhất 2 lần quẹt và lần cuối cách lần đầu ít nhất **2 phút** (tránh một lần quẹt bị ghi hai lần).
- Trạng thái: `OK` (có vào và ra); `SINGLE_PUNCH` (một lần quẹt, không biết là vào hay ra); `IN_PROGRESS` (một lần quẹt **trong ngày hôm nay**, giờ ra có thể chưa tới). "Hôm nay" tính theo múi giờ `Asia/Ho_Chi_Minh`.
- Ca đêm vắt qua nửa đêm bị chia sang hai ngày.

---

## 7. Tham chiếu API

### 7.1 Quy ước chung

**Header:** `Authorization: Bearer <access_token>` cho mọi endpoint (trừ `/oauth2/token`, `/`, `/error`, `/actuator/health`).

**Response thành công:**
```json
{ "status": 200, "message": "...", "data": { } }
```
Trường rỗng không được trả (`null` bị bỏ).

**Danh sách phân trang** (`data`):
```json
{ "meta": { "page": 1, "pageSize": 20, "pages": 3, "total": 55 }, "result": [ ] }
```
Tham số: `page` (bắt đầu từ **0**), `size` (mặc định 20), `sort=<trường>,<asc|desc>`. Lưu ý `meta.page` trả về bắt đầu từ 1.

**Lọc** (`filter`): `GET` danh sách của `roles`, `permissions`, `employees` hỗ trợ biểu thức Spring Filter, ví dụ `filter=active:true`, `filter=department.id:5`, `filter=fullName~'*thanh*'`.

**Lỗi nghiệp vụ** (do `GlobalException` xử lý):
```json
{ "timestamp": "2026-10-20T14:35:46", "status": 404, "error": "Not Found", "message": "Employee not found with id: 9" }
```

| Mã | Khi nào |
|---|---|
| 400 | Dữ liệu không hợp lệ, thiếu tham số, JSON sai |
| 401 | Thiếu, sai hoặc hết hạn token |
| 403 | Client bị khóa, role bị tắt, hoặc role không có permission cho endpoint |
| 404 | Không tìm thấy tài nguyên |
| 409 | Trùng dữ liệu, hoặc đang có lần kéo chấm công khác |
| 500 | Lỗi không lường trước (thông báo chung) |

### 7.2 Endpoint

Cột **Permission** là cặp method + đường dẫn mà role cần có (role ADMIN không cần).

**Client** (module `CLIENT`)

| Method | Đường dẫn | Mô tả |
|---|---|---|
| GET | `/api/v1/clients` | Danh sách client (phân trang) |
| GET | `/api/v1/clients/{id}` | Chi tiết client |
| POST | `/api/v1/clients` | Tạo client, trả `clientSecret` một lần |
| PUT | `/api/v1/clients` | Sửa `name`, `description`, `roleId`, `active` (gửi `id` trong body) |
| POST | `/api/v1/clients/{id}/secret` | Đổi secret, trả secret mới một lần |

Body tạo client:
```json
{ "clientId": "payroll-app", "name": "Payroll", "description": "Hệ thống lương", "roleId": 3 }
```
`clientId`: 3 đến 100 ký tự, chỉ gồm chữ, số, `.`, `_`, `-`; không đổi được sau khi tạo. `PUT` thay thế toàn bộ các trường sửa được, nên phải gửi đủ `name` và `roleId`.

**Role** (module `ROLE`)

| Method | Đường dẫn | Mô tả |
|---|---|---|
| GET | `/api/v1/roles` | Danh sách role (hỗ trợ `filter`) |
| GET | `/api/v1/roles/{id}` | Chi tiết role kèm permission |
| POST | `/api/v1/roles` | Tạo role, `permissions` là danh sách id permission |
| PUT | `/api/v1/roles` | Sửa role (gửi `id` trong body); danh sách permission được thay thế hoàn toàn |
| DELETE | `/api/v1/roles/{id}` | Xóa role (không xóa được ADMIN hoặc role đang gán cho client) |
| GET | `/api/v1/roles/stats` | Thống kê |

**Permission** (module `PERMISSION`)

| Method | Đường dẫn | Mô tả |
|---|---|---|
| GET | `/api/v1/permissions` | Danh sách phân trang (`filter`, `module`) |
| GET | `/api/v1/permissions/all` | Toàn bộ permission, không phân trang |
| GET | `/api/v1/permissions/{id}` | Chi tiết |
| GET | `/api/v1/permissions/modules` | Danh sách module |
| GET | `/api/v1/permissions/stats` | Thống kê |
| POST / PUT | `/api/v1/permissions` | Tạo / sửa (cặp `apiPath` + `method` phải duy nhất) |
| DELETE | `/api/v1/permissions/{id}` | Xóa (tự gỡ khỏi các role) |

**Employee** (module `EMPLOYEE`)

| Method | Đường dẫn | Mô tả |
|---|---|---|
| GET | `/api/v1/employees` | Danh sách nhân viên (phân trang, `filter`) |
| PUT | `/api/v1/employees` | Thay thế dữ liệu nhân viên: `id`, `employeeCode`, `userCode`, `cardCode`, `fullName`, `departmentId`, `active` |
| DELETE | `/api/v1/employees/{id}` | Xóa nhân viên (dữ liệu quẹt giữ nguyên) |

`PUT` thay thế toàn bộ: mã hoặc phòng ban bỏ trống sẽ bị xóa; `active` bắt buộc gửi. Trùng mã nhân viên hoặc `userCode` với người khác trả 409.

**Attendance** (module `ATTENDANCE`)

| Method | Đường dẫn | Tham số | Mô tả |
|---|---|---|---|
| POST | `/api/v1/attendance/pull` | `branchCode` (tùy chọn) | Kéo dữ liệu ngay từ máy chấm công. 200 kèm kết quả từng thiết bị; 404 nếu không có thiết bị khớp; 409 nếu đang kéo |
| GET | `/api/v1/attendance/users` | `date` (bắt buộc, `yyyy-MM-dd`), `branchCode`, `keyword` | Các lần quẹt trong ngày |
| GET | `/api/v1/attendance/daily` | `date` (bắt buộc), `branchCode`, `keyword` | Chấm công từng người trong ngày |
| GET | `/api/v1/attendance/monthly` | `employeeId` (bắt buộc), `from`, `to`, `sort=punchTime,asc|desc` | Các lần quẹt của một nhân viên. 404 nếu nhân viên không tồn tại |

`keyword` tìm không phân biệt hoa thường, theo kiểu "chứa", trong `userCode`, `employeeCode` và `fullName`. Các ký tự `%` và `_` được hiểu là ký tự thường, không phải ký tự đại diện.

Ví dụ một dòng của `daily`:
```json
{ "branchCode": "PKD_ND", "userCode": "7", "employeeCode": "TNS1224", "fullName": "PHAN VĂN THANH",
  "departmentName": "KINH DOANH NỘI ĐỊA", "workDate": "2026-10-14",
  "checkInTime": "2026-10-14T07:30:00", "checkOutTime": "2026-10-14T17:05:00",
  "punchCount": 4, "status": "OK" }
```

---

## 8. Cấu hình

File `src/main/resources/application.properties` **không được đưa vào git** (đã nằm trong `.gitignore`) vì chứa thông tin nhạy cảm. Cấu hình có thể đặt bằng biến môi trường.

| Thuộc tính | Biến môi trường | Mặc định | Ý nghĩa |
|---|---|---|---|
| `spring.datasource.url` | `DB_URL` | SQL Server `localhost`, DB `db_tns_hrms`, `integratedSecurity=true` | Kết nối DB. DB phải tồn tại trước khi chạy lần đầu |
| `jwt.secret.base64` | `JWT_SECRET_BASE64` | *(có giá trị mặc định cho dev, xem 11)* | Khóa ký JWT HS512, tối thiểu 64 byte, base64 |
| `app.oauth2.access-token-validity-in-seconds` | | `3600` | Thời hạn access token |
| `app.cors.allowed-origins` | `CORS_ALLOWED_ORIGINS` | các origin localhost | Danh sách origin, ngăn cách bằng dấu phẩy |
| `app.seed.admin-client-id` | `ADMIN_CLIENT_ID` | `tns-hrms-admin` | `client_id` của client admin đầu tiên |
| `app.seed.admin-client-secret` | `ADMIN_CLIENT_SECRET` | *(trống: sinh ngẫu nhiên, in log một lần)* | Secret của client admin đầu tiên |
| `app.attendance.pull.enabled` | `ATTENDANCE_PULL_ENABLED` | `false` | Bật job kéo dữ liệu hàng đêm |
| `app.attendance.pull.cron` | | `0 0 1 * * *` | Lịch kéo (múi giờ `Asia/Ho_Chi_Minh`) |
| `app.attendance.pull.run-on-startup` | `ATTENDANCE_PULL_RUN_ON_STARTUP` | `false` | Kéo thêm một lần ngay khi khởi động (để thử) |
| `app.attendance.pull.python` | `PYTHON_CMD` | `python` | Lệnh chạy Python |
| `app.attendance.pull.script-dir` | `ATTENDANCE_SCRIPT_DIR` | `scripts/attendance` | Thư mục chứa script |
| `app.attendance.pull.output-dir` | `ATTENDANCE_OUTPUT_DIR` | thư mục temp | Nơi ghi CSV và log tạm |
| `app.attendance.pull.timeout-minutes` | | `10` | Quá thời gian này thì hủy lần kéo |
| `app.attendance.pull.overlap-hours` | | `48` | Mỗi lần kéo đọc lùi lại số giờ này so với lần quẹt mới nhất đã lưu |
| `app.attendance.pull.devices[n]` | | | Mỗi thiết bị: `branch-code`, `ip`, `port` (4370), `script`, `password` (0) |

Khai báo thiết bị ví dụ:
```properties
app.attendance.pull.devices[0].branch-code=PKD_ND
app.attendance.pull.devices[0].ip=10.34.1.4
app.attendance.pull.devices[0].port=4370
app.attendance.pull.devices[0].script=zk_pull_punches.py
```
`branch-code` phải là chi nhánh đã có trong bảng `branches`. Thiết bị hãng khác chỉ cần thêm script riêng vào thư mục script và khai báo tên script ở `script`.

---

## 9. Vận hành

### 9.1 Chạy ứng dụng
```bash
./mvnw spring-boot:run        # chạy
./mvnw test                   # chạy test
./mvnw package                # đóng gói
```
Yêu cầu: JDK 21; SQL Server đã có DB `db_tns_hrms`; file `lib/mssql-jdbc_auth-*.dll` (đã được cấu hình trong `java.library.path` của plugin Maven) nếu dùng `integratedSecurity=true`. Để kéo chấm công cần thêm Python và `pip install -r scripts/attendance/requirements.txt`.

### 9.2 Lần chạy đầu tiên
1. Tạo DB `db_tns_hrms` trong SQL Server.
2. Đặt `ADMIN_CLIENT_SECRET` (khuyến nghị) rồi chạy ứng dụng. Hibernate tạo bảng và các seeder tạo dữ liệu ban đầu.
3. Lấy token của client admin (mục 3.2).
4. Tạo các client khác (mục 9.3).

### 9.3 Tạo client cho một ứng dụng mới
1. `GET /api/v1/roles` để lấy `id` role cần gán (ví dụ `CLIENT` cho ứng dụng chỉ đọc chấm công). Cần quyền riêng thì tạo role mới: `GET /api/v1/permissions/all` lấy id permission, rồi `POST /api/v1/roles`.
2. `POST /api/v1/clients` với `clientId`, `name`, `roleId`. Sao chép `clientSecret` trong response ngay.
3. Giao cho bên sử dụng: `client_id`, `client_secret`, URL token, và cách gọi ở mục 3.2. Gửi secret qua kênh an toàn.

### 9.4 Thử bằng Bruno hoặc curl
```bash
# lấy token
curl -u <client_id>:<client_secret> -d grant_type=client_credentials http://localhost:8080/oauth2/token

# gọi API
curl -H "Authorization: Bearer <access_token>" "http://localhost:8080/api/v1/attendance/daily?date=2026-10-14"
```
Trong Bruno: request `POST {{baseUrl}}/oauth2/token`, tab **Auth** chọn **Basic Auth**, tab **Body** chọn **Form URL Encoded** với `grant_type=client_credentials`. Lưu ý secret phải là giá trị thô nhận được lúc tạo, không phải hash trong DB.

### 9.5 Xử lý sự cố nhanh

| Hiện tượng | Nguyên nhân thường gặp |
|---|---|
| `invalid_request` ở `/oauth2/token` | Body không phải form hoặc thiếu `grant_type`; hoặc `client_id`/secret rỗng |
| `invalid_client` | Sai secret (hay nhầm với hash trong DB), client không tồn tại hoặc bị khóa |
| 403 ở API | Role của client chưa có permission cho endpoint đó, hoặc role/client bị tắt |
| 401 ở API | Không gửi token hoặc token hết hạn (xin token mới) |
| Không thấy secret của admin | Chỉ in log một lần khi seed; xem mục 3.6 để đặt lại |
| Kéo chấm công lỗi | Xem log: script không tìm thấy, Python chưa cài `pyzk`, hoặc máy chấm công không truy cập được |

### 9.6 Cập nhật từ phiên bản dùng tài khoản đăng nhập cũ
- Bảng `accounts` cũ không còn được dùng và có thể xóa thủ công; các permission module `ACCOUNT` tự được dọn khi khởi động.
- Bảng `api_clients` tự được tạo khi khởi động.

---

## 10. Test

Test chạy với profile `test` (H2 trong bộ nhớ, `src/test/resources/application-test.properties`, có khóa JWT và client admin riêng cho test):

| Nhóm | Nội dung |
|---|---|
| `AuthFlowIntegrationTest` | Luồng OAuth2: lấy token, sai secret, grant không hỗ trợ, đổi secret, khóa client, quyền của role `CLIENT`, endpoint bị đóng |
| `AttendanceApiIntegrationTest`, `EmployeeAttendanceApiIntegrationTest` | Báo cáo chấm công |
| `EmployeeWriteApiIntegrationTest`, `EmployeeDepartmentTest`, `EmployeeUserCodeTest` | Sửa, xóa và ràng buộc nhân viên |
| `PunchPullApiIntegrationTest`, `PunchPullServiceTest`, `PunchPullJob*Test`, `PunchImportIntegrationTest` | Kéo và nhập dữ liệu chấm công (dùng script giả `fake_pull.py`) |
| `*SeederTest` | Seed chi nhánh, phòng ban, nhân viên |
| `RequestLogFilterTest` | Log request không lộ token |

Lớp `TestAuth` lấy token cho test theo đúng luồng client credentials.

---

## 11. Lưu ý bảo mật và hạn chế đã biết

- **Khóa JWT mặc định.** `application.properties` hiện có giá trị mặc định cho `JWT_SECRET_BASE64` để chạy dev. Khóa này đã từng nằm trong lịch sử git, cần coi là đã lộ. Ở mọi môi trường thật phải đặt khóa mới bằng biến môi trường (64 byte ngẫu nhiên, base64). Ai biết khóa đều có thể tự ký token hợp lệ.
- **Secret client admin.** Đặt secret dài, ngẫu nhiên, qua biến môi trường của máy chủ. Không để trong file hay gửi qua chat.
- **Không có rate limit** ở `/oauth2/token`. Secret sinh ngẫu nhiên nên khó dò, nhưng nên giới hạn tốc độ ở tầng reverse proxy khi triển khai thật.
- **Token không thu hồi riêng lẻ.** Cách chặn duy nhất là khóa client (`active=false`, có hiệu lực ngay) hoặc đổi khóa JWT (vô hiệu hóa mọi token). Vì vậy giữ thời hạn token ngắn.
- **Phân quyền chỉ ở cấp endpoint.** Chưa có giới hạn theo dữ liệu (ví dụ chỉ xem một chi nhánh). Client có quyền `attendance` xem được dữ liệu của mọi chi nhánh.
- **Người có quyền `Create Client` hoặc `Update Role` có thể tự nâng quyền** (tạo client role ADMIN, hoặc thêm permission cho role của chính mình). Chỉ cấp hai quyền này cho người quản trị tin cậy.
- **Không dùng cho frontend có người dùng tự đăng nhập.** `client_secret` không được để trong mã chạy trên trình duyệt. Frontend nên gọi qua backend riêng giữ secret. Muốn đăng nhập theo từng người dùng cần thiết kế luồng khác (ví dụ authorization code).
- **Cấu hình DB.** `trustServerCertificate=true` trong URL mặc định bỏ qua kiểm tra chứng chỉ TLS, và `ddl-auto=update` tự sửa schema; cả hai chỉ nên dùng cho dev, nên thay bằng chứng chỉ hợp lệ và công cụ migration (Flyway/Liquibase) khi lên production.
- **Triển khai.** Chạy sau HTTPS; token và secret không được truyền qua kênh không mã hóa.
