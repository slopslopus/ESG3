package ru.mirea.visitpass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.mirea.visitpass.config.AppProperties;

@EnableConfigurationProperties(AppProperties.class)
@SpringBootApplication
public class VisitPassApplication {

  public static void main(String[] args) {
    SpringApplication.run(VisitPassApplication.class, args);
  }
}
