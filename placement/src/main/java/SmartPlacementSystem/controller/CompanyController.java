
package SmartPlacementSystem.controller;

import SmartPlacementSystem.entity.Company;
import SmartPlacementSystem.repository.CompanyRepository;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyRepository companyRepository;

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    public CompanyController(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    public record CompanyRegistrationRequest(
            String companyName,
            String email,
            String password,
            String industry,
            String website,
            String location) {
    }

    // COMPANY REGISTRATION
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(
            @RequestBody CompanyRegistrationRequest request) {

        if (request.companyName() == null
                || request.companyName().isBlank()
                || request.email() == null
                || request.email().isBlank()
                || !request.email().trim().contains("@")
                || request.password() == null
                || request.password().length() < 8) {

            return ResponseEntity.badRequest().body(
                    Map.of("message",
                            "Enter a company name, valid email, and password of at least 8 characters.")
            );
        }

        String email = request.email().trim().toLowerCase();

        if (companyRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    Map.of("message",
                            "This company email is already registered.")
            );
        }

        Company company = new Company();
        company.setCompanyName(request.companyName().trim());
        company.setEmail(email);
        company.setPassword(
                passwordEncoder.encode(request.password())
        );
        company.setIndustry(clean(request.industry()));
        company.setWebsite(clean(request.website()));
        company.setLocation(clean(request.location()));

        companyRepository.save(company);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                Map.of("message",
                        "Company registration successful! You can now log in.")
        );
    }

    // COMPANY LOGIN
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @RequestBody Map<String, String> credentials,
            HttpSession session,
            jakarta.servlet.http.HttpServletRequest request) {

        String email = credentials.get("email");
        String password = credentials.get("password");

        if (email == null || email.isBlank()
                || password == null || password.isBlank()) {

            return ResponseEntity.badRequest().body(
                    Map.of("message",
                            "Email and password are required.")
            );
        }

        Optional<Company> companyOptional =
                companyRepository.findByEmail(
                        email.trim().toLowerCase()
                );

        if (companyOptional.isEmpty()
                || !passwordEncoder.matches(
                        password,
                        companyOptional.get().getPassword())) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("message", "Invalid email or password.")
            );
        }

        // Change the session ID after successful authentication
        request.changeSessionId();

        Company company = companyOptional.get();
        session.setAttribute("companyId", company.getId());

        return ResponseEntity.ok(
                Map.of("message",
                        "Login successful! Welcome, "
                                + company.getCompanyName() + ".")
        );
    }

    // GET LOGGED-IN COMPANY PROFILE
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMyProfile(
            HttpSession session) {

        Object sessionId = session.getAttribute("companyId");

        if (!(sessionId instanceof Long companyId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("message", "Please log in first.")
            );
        }

        Optional<Company> companyOptional =
                companyRepository.findById(companyId);

        if (companyOptional.isEmpty()) {
            session.invalidate();

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("message", "Please log in again.")
            );
        }

        Company company = companyOptional.get();

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", company.getId());
        profile.put("companyName", company.getCompanyName());
        profile.put("email", company.getEmail());
        profile.put("industry", company.getIndustry());
        profile.put("website", company.getWebsite());
        profile.put("location", company.getLocation());

        return ResponseEntity.ok(profile);
    }

    // COMPANY LOGOUT
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            HttpSession session) {

        session.invalidate();

        return ResponseEntity.ok(
                Map.of("message", "Logged out successfully.")
        );
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
