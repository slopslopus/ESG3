package ru.mirea.visitpass.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.visitpass.domain.VisitRequest;
import ru.mirea.visitpass.domain.VisitRisk;
import ru.mirea.visitpass.dto.analytics.AnalyticsResponse;
import ru.mirea.visitpass.repository.VisitRequestRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {

  private final VisitRequestRepository repository;
  private final VisitRiskCalculator riskCalculator;

  public AnalyticsService(VisitRequestRepository repository,
      VisitRiskCalculator riskCalculator) {
    this.repository = repository;
    this.riskCalculator = riskCalculator;
  }

  @Transactional(readOnly = true)
  public AnalyticsResponse summary() {
    List<VisitRequest> all = repository.findAll();
    long total = all.size();
    long archived = all.stream().filter(VisitRequest::isArchived).count();
    long active = total - archived;

    List<VisitRequest> activeRows =
        all.stream().filter(r -> !r.isArchived()).toList();
    long escortCount =
        activeRows.stream().filter(VisitRequest::isNeedsEscort).count();
    double avgGuestCount = activeRows.isEmpty() ? 0.0
        : activeRows.stream().mapToInt(VisitRequest::getGuestCount).average()
            .orElse(0.0);

    Map<String, Long> byStatus = new HashMap<>();
    Map<String, Long> byRisk = new HashMap<>();
    Map<String, long[]> byEmployeeAcc = new HashMap<>();
    for (VisitRequest r : activeRows) {
      byStatus.merge(r.getStatus().name(), 1L, Long::sum);
      VisitRisk risk = riskCalculator.calculate(r);
      byRisk.merge(risk.name(), 1L, Long::sum);
      String hostId = r.getHost().getUuid().toString();
      long[] acc = byEmployeeAcc.computeIfAbsent(hostId, k -> new long[1]);
      acc[0]++;
    }
    Map<String, String> hostNames = new HashMap<>();
    for (VisitRequest r : activeRows) {
      hostNames.putIfAbsent(r.getHost().getUuid().toString(),
          r.getHost().getFullName());
    }
    List<AnalyticsResponse.EmployeeStat> byEmployee = byEmployeeAcc.entrySet()
        .stream()
        .map(e -> new AnalyticsResponse.EmployeeStat(e.getKey(),
            hostNames.get(e.getKey()), e.getValue()[0]))
        .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
        .toList();

    return new AnalyticsResponse(total, active, archived, escortCount,
        avgGuestCount, byEmployee, byStatus, byRisk);
  }
}
