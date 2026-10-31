package ru.mirea.visitpass.dto.note;

import jakarta.validation.constraints.NotBlank;

public class NoteDto {

  @NotBlank
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
