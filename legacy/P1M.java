
/*  
Green River College
Gen Nakamura
Project; CampusConnect
*/
import java.util.*;

public class P1M {
    public static void main(String[] args) {
        String filePath = "data/dummy_data.csv";
        List<AttendanceRecord> records = CsvReader.readAttendanceRecords(filePath);

        for (AttendanceRecord record : records) {
            System.out.println("Student ID: " + record.getStudentId());
            System.out.println("Name: " + record.getName());
            System.out.println("Role: " + record.getRole());
            System.out.println("Group ID: " + record.getGroupId());
            System.out.println("Check-in Time: " + record.getCheckInTime());
            System.out.println("Check-out Time: " + record.getCheckOuTime());
            System.out.println("Stay Minutes: " + record.stayMinutes());
            System.out.println("-----------------------------");
        }
        AttendanceService service = new AttendanceService(records);
        service.roleSeparate();
        Report.reportSummary(records, "report_summary.txt");
    }
}
