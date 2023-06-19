package document_editor.dto;

import java.util.List;

public record Changes(List<Change> changes, boolean isEndOfStream) {
}
