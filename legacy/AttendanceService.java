import java.util.*;

public class AttendanceService {
    private List<AttendanceRecord> records;

    public AttendanceService(List<AttendanceRecord> records) {
        this.records = records;
    }

    public void roleSeparate() {
        Map<String, Integer> Countsroles = new HashMap<>();// create a HashMap to store the count of each role
        Map<String, Long> roleStay = new HashMap<>();// create a HashMap to store the total stay minutes of each role
        for (AttendanceRecord record : records) {
            String role = record.getRole();// get the role of the record
            long stayMinutes = record.stayMinutes();// get the stay minutes of the record by using stayMinutes() method
            if (Countsroles.containsKey(role)) {// if the role is already in the HashMap, then increment the count by 1
                Countsroles.put(role, Countsroles.get(role) + 1);
            } else {// if the role is not in the HashMap, then add the role to the HashMap and set
                    // the count to 1
                int count = 1;//
                Countsroles.put(role, count);
            }
            if (roleStay.containsKey(role)) {// if the role is already in the HashMap, then increment the total stay
                                             // minutes by the stay minutes of the record
                roleStay.put(role, roleStay.get(role) + stayMinutes);// add the role to the HashMap and set the total
                                                                     // stay minutes to the stay minutes of the record
            } else {
                roleStay.put(role, stayMinutes);
            }
        }
        for (String role : Countsroles.keySet()) {
            int count = Countsroles.get(role);
            long totalStayMinutes = roleStay.get(role);
            double averageStayMinutes = (double) totalStayMinutes / count;
            // print the count, total
            // stay minutes, and
            // average stay minutes of
            // the role
            System.out.println("Role: " + role);// print the role
            System.out.println("The participation = " + count);
            System.out.println("The total Stay Minutes = " + totalStayMinutes);
            System.out.printf("The average Stay Minutes = around %.1f%n", averageStayMinutes);
            System.out.println("-----------------------------");
        }
    }
}
