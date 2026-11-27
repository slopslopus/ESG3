package ru.mirea.visitpass.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.visitpass.domain.UserAccount;
import ru.mirea.visitpass.domain.VisitNote;
import ru.mirea.visitpass.domain.VisitRequest;
import ru.mirea.visitpass.dto.note.NoteDto;
import ru.mirea.visitpass.dto.note.NoteResponse;
import ru.mirea.visitpass.error.ResourceNotFoundException;
import ru.mirea.visitpass.repository.UserAccountRepository;
import ru.mirea.visitpass.repository.VisitNoteRepository;
import ru.mirea.visitpass.repository.VisitRequestRepository;
import ru.mirea.visitpass.security.CurrentUser;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class NoteService {

  private static final String NOTE_CREATED = "NOTE_CREATED";
  private static final String NOTE_UPDATED = "NOTE_UPDATED";
  private static final String NOTE_DELETED = "NOTE_DELETED";

  private final VisitNoteRepository notes;
  private final VisitRequestRepository requests;
  private final UserAccountRepository users;
  private final AuditService audits;

  public NoteService(VisitNoteRepository notes,
      VisitRequestRepository requests, UserAccountRepository users,
      AuditService audits) {
    this.notes = notes;
    this.requests = requests;
    this.users = users;
    this.audits = audits;
  }

  @Transactional(readOnly = true)
  public List<NoteResponse> list(UUID requestUuid) {
    findRequest(requestUuid);
    return notes.findByRequestUuidOrderByPinnedDescCreatedAtAsc(requestUuid).stream()
        .map(NoteService::toResponse)
        .toList();
  }

  @Transactional
  public NoteResponse create(UUID requestUuid, NoteDto dto) {
    VisitRequest request = findRequest(requestUuid);
    UserAccount author = currentAuthor();
    VisitNote note = new VisitNote();
    note.setUuid(UUID.randomUUID());
    note.setRequest(request);
    note.setAuthor(author);
    note.setText(dto.getText().trim());
    note.setPinned(Boolean.TRUE.equals(dto.getPinned()));
    note.setCreatedAt(LocalDateTime.now());
    note.setUpdatedAt(LocalDateTime.now());
    VisitNote saved = notes.save(note);
    audits.record(NOTE_CREATED, "VISIT_NOTE", saved.getUuid(),
        "Note created for request " + requestUuid);
    return toResponse(saved);
  }

  @Transactional
  public NoteResponse update(UUID noteUuid, NoteDto dto) {
    VisitNote note = findNote(noteUuid);
    if (dto.getText().isBlank()) {
      throw new IllegalArgumentException("text must not be blank");
    }
    note.setText(dto.getText().trim());
    if (dto.getPinned() != null) {
      note.setPinned(dto.getPinned());
    }
    note.setUpdatedAt(LocalDateTime.now());
    VisitNote saved = notes.save(note);
    audits.record(NOTE_UPDATED, "VISIT_NOTE", saved.getUuid(),
        "Note updated " + noteUuid);
    return toResponse(saved);
  }

  @Transactional
  public void delete(UUID noteUuid) {
    VisitNote note = findNote(noteUuid);
    notes.delete(note);
    audits.record(NOTE_DELETED, "VISIT_NOTE", noteUuid,
        "Note deleted " + noteUuid);
  }

  private VisitRequest findRequest(UUID uuid) {
    return requests.findByUuid(uuid)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Request not found: " + uuid));
  }

  private VisitNote findNote(UUID uuid) {
    return notes.findByUuid(uuid)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Note not found: " + uuid));
  }

  private UserAccount currentAuthor() {
    CurrentUser current = CurrentUser.current();
    return users.findByUuid(current.id())
        .orElseThrow(() -> new ResourceNotFoundException(
            "Current user not found"));
  }

  private static NoteResponse toResponse(VisitNote note) {
    NoteResponse response = new NoteResponse();
    response.setId(note.getUuid());
    response.setText(note.getText());
    response.setPinned(note.isPinned());
    response.setAuthorName(note.getAuthor().getName());
    response.setCreatedAt(note.getCreatedAt());
    response.setUpdatedAt(note.getUpdatedAt());
    return response;
  }
}
