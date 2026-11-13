
package SmartPlacementSystem.controller;

import SmartPlacementSystem.entity.Job;
import SmartPlacementSystem.entity.JobApplication;
import SmartPlacementSystem.entity.Student;
import SmartPlacementSystem.repository.JobApplicationRepository;
import SmartPlacementSystem.repository.JobRepository;
import SmartPlacementSystem.repository.StudentRepository;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final JobApplicationRepository applicationRepository;
    private final StudentRepository studentRepository;
    private final JobRepository jobRepository;

    public ApplicationController(
            JobApplicationRepository applicationRepository,
            StudentRepository studentRepository,
            JobRepository jobRepository) {

        this.applicationRepository = applicationRepository;
        this.studentRepository = studentRepository;
        this.jobRepository = jobRepository;
    }

    // Apply for a job
    @PostMapping("/jobs/{jobId}")
    public ResponseEntity<Map<String, String>> applyForJob(
            @PathVariable Long jobId,
            HttpSession session) {

        Object sessionValue = session.getAttribute("studentId");

        if (!(sessionValue instanceof Long studentId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("message", "Please log in as a student first.")
            );
        }

        Optional<Student> studentOptional =
                studentRepository.findById(studentId);

        if (studentOptional.isEmpty()) {
            session.invalidate();

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("message", "Student account not found. Please log in again.")
            );
        }

        Optional<Job> jobOptional = jobRepository.findById(jobId);

        if (jobOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("message", "This job could not be found.")
            );
        }

        Student student = studentOptional.get();
        Job job = jobOptional.get();

        if (job.getApplicationDeadline().isBefore(LocalDate.now())) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "The application deadline has passed.")
            );
        }

        // Check minimum CGPA when the job specifies one
        if (job.getMinimumCgpa() != null) {
            if (student.getCgpa() == null) {
                return ResponseEntity.badRequest().body(
                        Map.of("message",
                                "Add your CGPA to your student profile before applying.")
                );
            }

            if (student.getCgpa() < job.getMinimumCgpa()) {
                return ResponseEntity.badRequest().body(
                        Map.of("message",
                                "You do not meet this job's minimum CGPA requirement.")
                );
            }
        }

        // Check all listed skills, using comma-separated names
        if (job.getRequiredSkills() != null
                && !job.getRequiredSkills().isBlank()) {

            Set<String> requiredSkills = Arrays.stream(
                            job.getRequiredSkills().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());

            Set<String> studentSkills = student.getSkills() == null
                    ? Set.of()
                    : Arrays.stream(student.getSkills().split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .map(s -> s.toLowerCase(Locale.ROOT))
                            .collect(Collectors.toSet());

            if (!studentSkills.containsAll(requiredSkills)) {
                return ResponseEntity.badRequest().body(
                        Map.of("message",
                                "Your profile does not contain all the required skills.")
                );
            }
        }

        if (applicationRepository.existsByStudent_IdAndJob_Id(
                studentId, jobId)) {

            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    Map.of("message",
                            "You have already applied for this job.")
            );
        }

        JobApplication application = new JobApplication();
        application.setStudent(student);
        application.setJob(job);
        application.setStatus("APPLIED");

        applicationRepository.save(application);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                Map.of("message", "Application submitted successfully!")
        );
    }

    // Get the logged-in student's applications
    @GetMapping("/mine")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMyApplications(HttpSession session) {

        Object sessionValue = session.getAttribute("studentId");

        if (!(sessionValue instanceof Long studentId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("message", "Please log in as a student first.")
            );
        }

        List<Map<String, Object>> results =
                applicationRepository
                        .findByStudent_IdOrderByAppliedAtDesc(studentId)
                        .stream()
                        .map(application -> {
                            Map<String, Object> item =
                                    new LinkedHashMap<>();

                            item.put("id", application.getId());
                            item.put("jobId", application.getJob().getId());
                            item.put("jobTitle",
                                    application.getJob().getTitle());
                            item.put("companyName",
                                    application.getJob()
                                            .getCompany().getCompanyName());
                            item.put("status", application.getStatus());
                            item.put("appliedAt",
                                    application.getAppliedAt());
                            item.put("applicationDeadline",
                                    application.getJob()
                                            .getApplicationDeadline());

                            return item;
                        })
                        .toList();

        return ResponseEntity.ok(results);
    }
}
