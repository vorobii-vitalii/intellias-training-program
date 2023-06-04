package document_editor.dto;

import java.util.List;

public record ClientRequest(RequestType type, List<Change> payload, String changeId) {
}
