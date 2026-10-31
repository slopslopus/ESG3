package ru.mirea.visitpass.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.mirea.visitpass.dto.analytics.AnalyticsResponse;
import ru.mirea.visitpass.service.AnalyticsService;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

  private final AnalyticsService service;

  public AnalyticsController(AnalyticsService service) {
    this.service = service;
  }

  @GetMapping("/summary")
  public AnalyticsResponse summary() {
    return service.summary();
  }
}
