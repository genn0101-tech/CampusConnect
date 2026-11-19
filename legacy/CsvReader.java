import java.io.*;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CsvReader {
    public static List<AttendanceRecord> readAttendanceRecords(String filePath) {
        List<AttendanceRecord> records = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            br.readLine(); // Skip header line
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length == 6) {
                    String studentId = values[0];
                    String name = values[1];
                    String role = values[2];
                    String groupId = values[3];
                    LocalDateTime checkInTime = LocalDateTime.parse(values[4], formatter);
                    LocalDateTime checkOuTime = LocalDateTime.parse(values[5], formatter);
                    records.add(new AttendanceRecord(studentId, name, role, groupId, checkInTime, checkOuTime));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return records;
    }
}
