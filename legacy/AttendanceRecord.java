import java.time.LocalDateTime;
import java.time.Duration;

public class AttendanceRecord {
    private String studentId;
    private String name;
    private String role;
    private String groupId;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOuTime;

    public AttendanceRecord(String studentId, String name, String role, String groupId, LocalDateTime checkInTime,
            LocalDateTime checkOuTime) {
        this.studentId = studentId; // Those are same name so it can be used the this. name
        this.groupId = groupId;
        this.checkInTime = checkInTime;
        this.checkOuTime = checkOuTime;
        this.name = name;
        this.role = role;
    }

    public String getStudentId() {// get method for studentId because studentId is private so we need to use get
                                  // method to get the value of studentId
        return studentId;
    }

    public String getName() {// get method for name
        return name;
    }

    public String getRole() {// get method for role
        return role;
    }

    public String getGroupId() {// get method for groupId
        return groupId;
    }

    public LocalDateTime getCheckInTime() {// get method for checkInTime
        return checkInTime;
    }

    public LocalDateTime getCheckOuTime() {// get method for checkOuTime
        return checkOuTime;
    }

    public long stayMinutes() {// this method is to calculate the stay minutes by using Duration.between()
                               // method to get the gap of time between checkInTime and checkOuTime and then
                               // convert it to minutes by using toMinutes() method
        return Duration.between(checkInTime, checkOuTime).toMinutes();
    }
}