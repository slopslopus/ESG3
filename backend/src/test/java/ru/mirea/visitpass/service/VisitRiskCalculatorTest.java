package ru.mirea.visitpass.service;

import org.junit.jupiter.api.Test;
import ru.mirea.visitpass.domain.VisitRequest;
import ru.mirea.visitpass.domain.VisitRisk;
import ru.mirea.visitpass.domain.VisitStatus;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisitRiskCalculatorTest {

  private final VisitRiskCalculator calculator = new VisitRiskCalculator();

  private VisitRequest request(VisitStatus status, LocalDate visitDate,
      int guestCount, boolean needsEscort) {
    VisitRequest r = new VisitRequest();
    r.setStatus(status);
    r.setVisitDate(visitDate);
    r.setGuestCount(guestCount);
    r.setNeedsEscort(needsEscort);
    return r;
  }

  @Test
  void cancelledIsLow() {
    VisitRequest r = request(VisitStatus.CANCELLED,
        LocalDate.now().plusDays(1), 5, true);
    assertEquals(VisitRisk.LOW, calculator.calculate(r));
  }

  @Test
  void completedInPastIsLow() {
    VisitRequest r = request(VisitStatus.COMPLETED,
        LocalDate.now().minusDays(1), 1, false);
    assertEquals(VisitRisk.LOW, calculator.calculate(r));
  }

  @Test
  void escortWithThreeGuestsIsHigh() {
    VisitRequest r = request(VisitStatus.ISSUED,
        LocalDate.now().plusWeeks(2), 3, true);
    assertEquals(VisitRisk.HIGH, calculator.calculate(r));
  }

  @Test
  void submittedForTomorrowIsHigh() {
    VisitRequest r = request(VisitStatus.SUBMITTED,
        LocalDate.now().plusDays(1), 1, false);
    assertEquals(VisitRisk.HIGH, calculator.calculate(r));
  }

  @Test
  void ordinarySubmittedIsMedium() {
    VisitRequest r = request(VisitStatus.SUBMITTED,
        LocalDate.now().plusWeeks(1), 1, false);
    assertEquals(VisitRisk.MEDIUM, calculator.calculate(r));
  }
}
