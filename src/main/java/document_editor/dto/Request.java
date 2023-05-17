package document_editor.dto;

import java.util.List;

public record Request(RequestType type, List<Change> payload) {
}
