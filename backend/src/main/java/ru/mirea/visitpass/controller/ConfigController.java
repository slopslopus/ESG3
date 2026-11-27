package ru.mirea.visitpass.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.mirea.visitpass.config.AppProperties;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

  private final AppProperties properties;

  public ConfigController(AppProperties properties) {
    this.properties = properties;
  }

  @GetMapping
  public UiConfig get() {
    return new UiConfig(
        properties.getSecurity().isPermanentDeleteEnabled());
  }

  public record UiConfig(boolean permanentDeleteEnabled) {
  }
}
