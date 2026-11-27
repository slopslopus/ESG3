package ru.mirea.visitpass.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.mirea.visitpass.dto.note.NoteDto;
import ru.mirea.visitpass.dto.note.NoteResponse;
import ru.mirea.visitpass.error.ForbiddenException;
import ru.mirea.visitpass.service.NoteService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/requests")
public class NoteController {

  private final NoteService notes;

  public NoteController(NoteService notes) {
    this.notes = notes;
  }

  @GetMapping("/{id}/notes")
  public List<NoteResponse> list(@PathVariable UUID id) {
    requireAny("VIEWER", "OPERATOR", "ADMIN");
    return notes.list(id);
  }

  @PostMapping("/{id}/notes")
  public ResponseEntity<NoteResponse> create(@PathVariable UUID id,
      @Valid @RequestBody NoteDto dto) {
    requireAny("OPERATOR", "ADMIN");
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(notes.create(id, dto));
  }

  @PutMapping("/notes/{noteId}")
  public NoteResponse update(@PathVariable UUID noteId,
      @Valid @RequestBody NoteDto dto) {
    requireAny("OPERATOR", "ADMIN");
    return notes.update(noteId, dto);
  }

  @DeleteMapping("/notes/{noteId}")
  public ResponseEntity<Void> delete(@PathVariable UUID noteId) {
    requireAny("OPERATOR", "ADMIN");
    notes.delete(noteId);
    return ResponseEntity.noContent().build();
  }


  // Manual role check in the style of RequestController: SecurityConfig is
  // owned by another module, so @PreAuthorize is not enabled from here.
  private static void requireAny(String... allowed) {
    Authentication auth =
        SecurityContextHolder.getContext().getAuthentication();
    Set<String> have = auth == null ? Set.of()
        : auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
    Set<String> expanded = new HashSet<>(have);
    if (expanded.contains("ROLE_ADMIN")) {
      expanded.add("ROLE_OPERATOR");
      expanded.add("ROLE_VIEWER");
    }
    if (expanded.contains("ROLE_OPERATOR")) {
      expanded.add("ROLE_VIEWER");
    }
    for (String role : allowed) {
      if (expanded.contains("ROLE_" + role)) {
        return;
      }
    }
    throw new ForbiddenException("Access denied");
  }
}
