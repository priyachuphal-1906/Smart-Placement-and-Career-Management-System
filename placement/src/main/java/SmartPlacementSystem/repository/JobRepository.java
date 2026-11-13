
package SmartPlacementSystem.repository;

import SmartPlacementSystem.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findAllByOrderByIdDesc();

    List<Job> findByCompany_IdOrderByIdDesc(Long companyId);
}
