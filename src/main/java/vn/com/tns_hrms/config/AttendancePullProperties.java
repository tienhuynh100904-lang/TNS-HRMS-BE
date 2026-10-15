package vn.com.tns_hrms.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/** Settings of the nightly pull of punches from the attendance devices (app.attendance.pull.*). */
@Component
@ConfigurationProperties(prefix = "app.attendance.pull")
@Getter
@Setter
public class AttendancePullProperties {
    // the scheduled job only exists when this is true, so a developer machine never contacts a real device
    private boolean enabled = false;

    private String cron = "0 0 1 * * *";

    // command that starts Python
    private String python = "python";

    // folder that holds the pull scripts; each device names the script that can read it (DeviceConfig.script),
    // so devices of another make just need their own script in this folder
    private String scriptDir = "scripts/attendance";

    // folder for the temporary CSV and log of each pull
    private String outputDir = System.getProperty("java.io.tmpdir");

    // a pull that takes longer than this is killed
    private int timeoutMinutes = 10;

    // each pull re-reads this many hours before the newest punch already stored, to catch punches that reached
    // the device late; the duplicates this brings are dropped by the importer
    private int overlapHours = 48;

    private List<DeviceConfig> devices = new ArrayList<>();

    @Getter
    @Setter
    public static class DeviceConfig {
        // branch the punches of this device are stored under
        private String branchCode;
        private String ip;
        private int port = 4370;
        // file name, inside the script folder, of the script that reads this device
        private String script = "zk_pull_punches.py";
        // connection password of the device, 0 when none
        private int password = 0;
    }
}
