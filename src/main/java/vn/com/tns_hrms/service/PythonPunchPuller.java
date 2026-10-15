package vn.com.tns_hrms.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import vn.com.tns_hrms.config.AttendancePullProperties;
import vn.com.tns_hrms.config.AttendancePullProperties.DeviceConfig;
import vn.com.tns_hrms.dto.request.ReqPunch;
import vn.com.tns_hrms.helper.DeviceCsvHelper;

/**
 * Reads a device by running its Python script (see app.attendance.pull.script-dir and the script of the device,
 * zk_pull_punches.py for ZKTeco machines), which writes the punches to a CSV file that is then read back.
 * The scripts only read from the device.
 */
@Component
public class PythonPunchPuller implements PunchPuller {
    private static final Logger log = LoggerFactory.getLogger(PythonPunchPuller.class);
    private static final DateTimeFormatter SINCE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AttendancePullProperties properties;
    private final DeviceCsvHelper csvHelper;

    public PythonPunchPuller(AttendancePullProperties properties, DeviceCsvHelper csvHelper) {
        this.properties = properties;
        this.csvHelper = csvHelper;
    }

    @Override
    public List<ReqPunch> pull(DeviceConfig device, LocalDateTime since) {
        Path script = Path.of(properties.getScriptDir()).resolve(device.getScript());
        if (!Files.isRegularFile(script)) {
            throw new IllegalStateException("Python script not found: " + script.toAbsolutePath());
        }
        try {
            Path dir = Path.of(properties.getOutputDir());
            Files.createDirectories(dir);
            Path csv = dir.resolve(device.getBranchCode() + "_chamcong.csv");
            Path output = dir.resolve(device.getBranchCode() + "_pull.log");
            // a file left by an earlier run must never be mistaken for the result of this one
            Files.deleteIfExists(csv);

            List<String> command = new ArrayList<>(List.of(properties.getPython(), script.toString(), "--ip",
                    device.getIp(), "--port", String.valueOf(device.getPort()), "--out-logs", csv.toString()));
            if (device.getPassword() != 0) {
                command.addAll(List.of("--password", String.valueOf(device.getPassword())));
            }
            if (since != null) {
                command.addAll(List.of("--since", since.format(SINCE_FORMAT)));
            }

            // the output goes to a file, so a chatty script can never block on a full pipe while we wait for it
            Process process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(output.toFile()).start();
            if (!process.waitFor(properties.getTimeoutMinutes(), TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IllegalStateException("Pulling " + device.getBranchCode() + " took longer than "
                        + properties.getTimeoutMinutes() + " minutes and was stopped, see " + output);
            }
            String text = Files.readString(output, StandardCharsets.UTF_8).strip();
            if (process.exitValue() != 0 || !Files.isRegularFile(csv)) {
                throw new IllegalStateException("Pulling " + device.getBranchCode() + " failed (exit code "
                        + process.exitValue() + "): " + text);
            }
            log.info("Pulled {}: {}", device.getBranchCode(), text.lines().reduce((a, b) -> b).orElse(""));

            List<ReqPunch> punches = csvHelper.readPunches(csv);
            Files.deleteIfExists(csv);
            Files.deleteIfExists(output);
            return punches;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot run the pull of " + device.getBranchCode(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Pull of " + device.getBranchCode() + " was interrupted", e);
        }
    }
}
