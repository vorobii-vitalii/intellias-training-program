package document_editor.dto;

import java.util.List;

public record TreePathDTO(List<Boolean> directions, List<Integer> disambiguators) {
}
