package ru.mirea.visitpass.service;

import org.springframework.stereotype.Component;
import ru.mirea.visitpass.domain.VisitRequest;
import ru.mirea.visitpass.domain.VisitRisk;
import ru.mirea.visitpass.domain.VisitStatus;

import java.time.LocalDate;

@Component
public class VisitRiskCalculator {

  public VisitRisk calculate(VisitRequest r) {
    if (r.getStatus() == VisitStatus.CANCELLED) {
      return VisitRisk.LOW;
    }
    LocalDate today = LocalDate.now();
    if (r.getStatus() == VisitStatus.COMPLETED
        && r.getVisitDate().isBefore(today)) {
      return VisitRisk.LOW;
    }
    if ((r.isNeedsEscort() && r.getGuestCount() >= 3)
        || (r.getStatus() == VisitStatus.SUBMITTED
            && !r.getVisitDate().isAfter(today.plusDays(1)))) {
      return VisitRisk.HIGH;
    }
    return VisitRisk.MEDIUM;
  }
}
