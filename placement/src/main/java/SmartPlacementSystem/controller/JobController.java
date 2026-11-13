
package SmartPlacementSystem.controller;

import SmartPlacementSystem.entity.Company;
import SmartPlacementSystem.entity.Job;
import SmartPlacementSystem.repository.CompanyRepository;
import SmartPlacementSystem.repository.JobRepository;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;

    public JobController(
            JobRepository jobRepository,
            CompanyRepository companyRepository) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
    }

    public record JobRequest(
            String title,
            String description,
            String location,
            String employmentType,
            Double salaryMinLpa,
            Double salaryMaxLpa,
            Double minimumCgpa,
            String requiredSkills,
            LocalDate applicationDeadline) {
    }

    // Create a job for the logged-in company
    @PostMapping
    public ResponseEntity<Map<String, Object>> createJob(
            @RequestBody JobRequest request,
            HttpSession session) {

        Object sessionValue = session.getAttribute("companyId");

        if (!(sessionValue instanceof Long companyId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("message", "Please log in as a company first.")
            );
        }

        if (request.title() == null || request.title().isBlank()
                || request.description() == null
                || request.description().isBlank()
                || request.location() == null
                || request.location().isBlank()
                || request.employmentType() == null
                || request.employmentType().isBlank()
                || request.applicationDeadline() == null) {

            return ResponseEntity.badRequest().body(
                    Map.of("message",
                            "Please complete all required job fields.")
            );
        }

        if (request.title().trim().length() > 255
                || request.location().trim().length() > 255
                || request.description().length() > 3000
                || (request.requiredSkills() != null
                    && request.requiredSkills().length() > 1000)) {

            return ResponseEntity.badRequest().body(
                    Map.of("message", "One or more fields are too long.")
            );
        }

        if (request.applicationDeadline().isBefore(LocalDate.now())) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "The application deadline cannot be in the past.")
            );
        }

        if (request.salaryMinLpa() != null
                && (!Double.isFinite(request.salaryMinLpa())
                || request.salaryMinLpa() < 0)) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Minimum salary must be zero or greater.")
            );
        }

        if (request.salaryMaxLpa() != null
                && (!Double.isFinite(request.salaryMaxLpa())
                || request.salaryMaxLpa() < 0)) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Maximum salary must be zero or greater.")
            );
        }

        if (request.salaryMinLpa() != null
                && request.salaryMaxLpa() != null
                && request.salaryMinLpa() > request.salaryMaxLpa()) {
            return ResponseEntity.badRequest().body(
                    Map.of("message",
                            "Minimum salary cannot exceed maximum salary.")
            );
        }

        if (request.minimumCgpa() != null
                && (!Double.isFinite(request.minimumCgpa())
                || request.minimumCgpa() < 0
                || request.minimumCgpa() > 10)) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Minimum CGPA must be between 0 and 10.")
            );
        }

        Company company = companyRepository.findById(companyId)
                .orElse(null);

        if (company == null) {
            session.invalidate();

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("message", "Company account not found. Please log in again.")
            );
        }

        Job job = new Job();
        job.setTitle(request.title().trim());
        job.setDescription(request.description().trim());
        job.setLocation(request.location().trim());
        job.setEmploymentType(request.employmentType().trim());
        job.setSalaryMinLpa(request.salaryMinLpa());
        job.setSalaryMaxLpa(request.salaryMaxLpa());
        job.setMinimumCgpa(request.minimumCgpa());
        job.setRequiredSkills(clean(request.requiredSkills()));
        job.setApplicationDeadline(request.applicationDeadline());
        job.setCompany(company);

        Job saved = jobRepository.save(job);

        Map<String, Object> result = toMap(saved);
        result.put("message", "Job posted successfully.");

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // Public job listings for students
    @GetMapping
    public List<Map<String, Object>> getAllJobs() {
        return jobRepository.findAllByOrderByIdDesc()
                .stream()
                .map(this::toMap)
                .toList();
    }

    // Jobs posted by the logged-in company
    @GetMapping("/mine")
    public ResponseEntity<?> getMyJobs(HttpSession session) {
        Object sessionValue = session.getAttribute("companyId");

        if (!(sessionValue instanceof Long companyId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("message", "Please log in as a company first.")
            );
        }

        return ResponseEntity.ok(
                jobRepository.findByCompany_IdOrderByIdDesc(companyId)
                        .stream()
                        .map(this::toMap)
                        .toList()
        );
    }

    private Map<String, Object> toMap(Job job) {
        Map<String, Object> data = new LinkedHashMap<>();

        data.put("id", job.getId());
        data.put("title", job.getTitle());
        data.put("description", job.getDescription());
        data.put("location", job.getLocation());
        data.put("employmentType", job.getEmploymentType());
        data.put("salaryMinLpa", job.getSalaryMinLpa());
        data.put("salaryMaxLpa", job.getSalaryMaxLpa());
        data.put("minimumCgpa", job.getMinimumCgpa());
        data.put("requiredSkills", job.getRequiredSkills());
        data.put("applicationDeadline", job.getApplicationDeadline());
        data.put("companyName", job.getCompany().getCompanyName());

        return data;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
