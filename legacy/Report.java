import java.io.*;
import java.util.*;

public class Report {
    public static void reportSummary(List<AttendanceRecord> records, String filePath) {// this method is to write the
                                                                                       // report summary to a text file
                                                                                       // by using PrintStream class to
                                                                                       // write the data to the file
        try {
            PrintStream out = new PrintStream(new File(filePath));// create a PrintStream object to write the data to
                                                                  // the file
            out.println("Report Summary");
            out.println("========================================================");
            out.println("Total participants: " + records.size());
            out.println("Name\tRole\tGroup ID\tStay Minutes");
            for (AttendanceRecord record : records) {
                out.println(record.getName() + " / " + record.getRole() + " / " + record.getGroupId() + " / "
                        + record.stayMinutes());
            }
            out.println("========================================================");
            out.close();
            System.out.println("\nReport summary has been written to " + filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
