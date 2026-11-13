
package SmartPlacementSystem.controller;

import SmartPlacementSystem.entity.Student;
import SmartPlacementSystem.repository.StudentRepository;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentRepository studentRepository;

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    public StudentController(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    // Student Registration
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(
            @RequestBody Student student) {

        if (student.getFullName() == null
                || student.getFullName().isBlank()
                || student.getEmail() == null
                || student.getEmail().isBlank()
                || student.getPassword() == null
                || student.getPassword().length() < 8) {

            return ResponseEntity.badRequest().body(
                    Map.of("message",
                            "Enter your name and email, and use a password of at least 8 characters.")
            );
        }

        String email = student.getEmail().trim().toLowerCase();

        if (studentRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    Map.of("message", "This email is already registered.")
            );
        }

        student.setEmail(email);
        student.setPassword(
                passwordEncoder.encode(student.getPassword())
        );

        studentRepository.save(student);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                Map.of("message",
                        "Registration successful! You can now log in.")
        );
    }

    // Student Login
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @RequestBody Map<String, String> credentials,
            HttpSession session) {

        String email = credentials.get("email");
        String password = credentials.get("password");

        if (email == null || email.isBlank()
                || password == null || password.isBlank()) {

            return ResponseEntity.badRequest().body(
                    Map.of("message", "Email and password are required.")
            );
        }

        Optional<Student> studentOptional =
                studentRepository.findByEmail(
                        email.trim().toLowerCase()
                );

        if (studentOptional.isEmpty()
                || !passwordEncoder.matches(
                        password,
                        studentOptional.get().getPassword())) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("message", "Invalid email or password.")
            );
        }

        Student student = studentOptional.get();
        session.setAttribute("studentId", student.getId());

        return ResponseEntity.ok(
                Map.of("message",
                        "Login successful! Welcome, "
                                + student.getFullName() + ".")
        );
    }

    // Get the logged-in student's profile
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMyProfile(
            HttpSession session) {

        Object sessionId = session.getAttribute("studentId");

        if (!(sessionId instanceof Long studentId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("message", "Please log in first.")
            );
        }

        Optional<Student> studentOptional =
                studentRepository.findById(studentId);

        if (studentOptional.isEmpty()) {
            session.invalidate();

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("message", "Please log in again.")
            );
        }

        Student student = studentOptional.get();

        // Return profile fields only. Never return the password hash.
        Map<String, Object> profile = new LinkedHashMap<>();

        profile.put("id", student.getId());
        profile.put("fullName", student.getFullName());
        profile.put("email", student.getEmail());
        profile.put("branch", student.getBranch());
        profile.put("cgpa", student.getCgpa());
        profile.put("graduationYear", student.getGraduationYear());
        profile.put("skills", student.getSkills());

        return ResponseEntity.ok(profile);
    }

    // Data allowed to be edited by the student
    public record ProfileUpdateRequest(
            String fullName,
            String branch,
            Double cgpa,
            Integer graduationYear,
            String skills) {
    }

    // Update the logged-in student's profile
    @PutMapping("/me")
    public ResponseEntity<Map<String, String>> updateMyProfile(
            @RequestBody ProfileUpdateRequest update,
            HttpSession session) {

        Object sessionId = session.getAttribute("studentId");

        if (!(sessionId instanceof Long studentId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("message", "Please log in before editing your profile.")
            );
        }

        Optional<Student> studentOptional =
                studentRepository.findById(studentId);

        if (studentOptional.isEmpty()) {
            session.invalidate();

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("message", "Please log in again.")
            );
        }

        if (update.fullName() == null
                || update.fullName().isBlank()
                || update.fullName().trim().length() > 255) {

            return ResponseEntity.badRequest().body(
                    Map.of("message",
                            "Full name is required and must be at most 255 characters.")
            );
        }

        if (update.branch() != null && update.branch().length() > 255) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Course / Branch is too long.")
            );
        }

        if (update.skills() != null && update.skills().length() > 1000) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Skills must be at most 1000 characters.")
            );
        }

        if (update.cgpa() != null
                && (update.cgpa() < 0 || update.cgpa() > 10
                || !Double.isFinite(update.cgpa()))) {

            return ResponseEntity.badRequest().body(
                    Map.of("message", "CGPA must be between 0 and 10.")
            );
        }

        if (update.graduationYear() != null
                && (update.graduationYear() < 2000
                || update.graduationYear() > 2100)) {

            return ResponseEntity.badRequest().body(
                    Map.of("message", "Enter a valid graduation year.")
            );
        }

        Student student = studentOptional.get();

        student.setFullName(update.fullName().trim());
        student.setBranch(cleanOptionalText(update.branch()));
        student.setCgpa(update.cgpa());
        student.setGraduationYear(update.graduationYear());
        student.setSkills(cleanOptionalText(update.skills()));

        studentRepository.save(student);

        return ResponseEntity.ok(
                Map.of("message", "Your profile has been updated successfully.")
        );
    }

    private String cleanOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    // Student Logout
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            HttpSession session) {

        session.invalidate();

        return ResponseEntity.ok(
                Map.of("message", "Logged out successfully.")
        );
    }
}
