package vn.com.tns_hrms.helper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import vn.com.tns_hrms.dto.request.ReqDeviceUser;
import vn.com.tns_hrms.dto.request.ReqPunch;

/**
 * Reads the two CSV files dumped from an attendance device:
 * <ul>
 * <li>users: uid,user_id,name,privilege,password,group_id,card</li>
 * <li>punches: uid,user_id,thoi_gian,verify,punch,phan_duoi_hex</li>
 * </ul>
 * Lines that cannot be read are still returned (with an empty code or time) so the import counts them as skipped
 * instead of silently losing them.
 */
@Component
public class DeviceCsvHelper {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<ReqDeviceUser> readUsers(Path file) {
        List<ReqDeviceUser> users = new ArrayList<>();
        for (String[] cols : readRows(file)) {
            ReqDeviceUser user = new ReqDeviceUser();
            if (cols.length >= 4) {
                user.setStaffCode(cols[1]);
                user.setName(cols[2]);
                // an unreadable privilege is treated as "not a normal user" so it is skipped, not imported
                user.setPrivilege(parseInt(cols[3], -1));
            }
            users.add(user);
        }
        return users;
    }

    public List<ReqPunch> readPunches(Path file) {
        List<ReqPunch> punches = new ArrayList<>();
        for (String[] cols : readRows(file)) {
            ReqPunch punch = new ReqPunch();
            if (cols.length >= 4) {
                punch.setSourceRef(cols[0]);
                punch.setStaffCode(cols[1]);
                punch.setPunchTime(parseTime(cols[2]));
                punch.setVerifyType(cols[3].isBlank() ? null : parseInteger(cols[3]));
            }
            punches.add(punch);
        }
        return punches;
    }

    // data rows only: the header is skipped unread (it may start with a BOM)
    private List<String[]> readRows(Path file) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<String[]> rows = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                if (!lines.get(i).isBlank()) {
                    rows.add(lines.get(i).split(",", -1));
                }
            }
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    private static LocalDateTime parseTime(String value) {
        try {
            return LocalDateTime.parse(value.trim(), TIME_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static int parseInt(String value, int fallback) {
        Integer parsed = parseInteger(value);
        return parsed == null ? fallback : parsed;
    }

    private static Integer parseInteger(String value) {
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
