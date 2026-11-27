package ru.mirea.visitpass.dto.note;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class NoteDto {

  @NotBlank
  @Size(max = 2000)
  private String text;

  private Boolean pinned;

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public Boolean getPinned() {
    return pinned;
  }

  public void setPinned(Boolean pinned) {
    this.pinned = pinned;
  }
}
