package vn.com.tns_hrms.config.seed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

/** Reads the CSV files of the seeders (classpath:seed/*.csv): UTF-8, one header line, values optionally in quotes. */
final class SeedCsv {
    private static final Logger log = LoggerFactory.getLogger(SeedCsv.class);

    private SeedCsv() {
    }

    /**
     * @param columns number of columns of the file; a comma inside the LAST column stays part of its value
     * @return the data rows (header and blank lines left out), every value trimmed and unquoted
     */
    static List<String[]> read(String resource, int columns) {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(resource).getInputStream(), StandardCharsets.UTF_8))) {
            reader.readLine(); // header (may start with a BOM)
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = line.split(",", columns);
                if (cols.length < columns) {
                    log.warn("Skipping unreadable line in {}: {}", resource, line);
                    continue;
                }
                for (int i = 0; i < cols.length; i++) {
                    cols[i] = unquote(cols[i]);
                }
                rows.add(cols);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + resource, e);
        }
        return rows;
    }

    private static String unquote(String value) {
        String v = value.trim();
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1);
        }
        return v.trim();
    }
}
