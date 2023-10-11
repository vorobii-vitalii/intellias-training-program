package document_editor.dto;

public record Change(String charId, String parentCharId, boolean isRight, int disambiguator, Character character) {

}
