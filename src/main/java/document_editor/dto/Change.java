package document_editor.dto;

import java.util.List;

public record Change(List<TreePathEntry> treePath, Character character) {

}
