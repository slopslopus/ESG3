package ru.mirea.visitpass.dto.bulk;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BulkResponse {

  private List<UUID> succeeded = new ArrayList<>();
  private List<BulkFailure> failed = new ArrayList<>();

  public List<UUID> getSucceeded() {
    return succeeded;
  }

  public void setSucceeded(List<UUID> succeeded) {
    this.succeeded = succeeded;
  }

  public List<BulkFailure> getFailed() {
    return failed;
  }

  public void setFailed(List<BulkFailure> failed) {
    this.failed = failed;
  }

  public static class BulkFailure {

    private UUID id;
    private String error;

    public BulkFailure() {
    }

    public BulkFailure(UUID id, String error) {
      this.id = id;
      this.error = error;
    }

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public String getError() {
      return error;
    }

    public void setError(String error) {
      this.error = error;
    }
  }
}
