package blue.contract.utils;

import blue.language.model.Node;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.diff.JsonDiff;

import java.io.File;
import java.io.IOException;

import static blue.language.utils.UncheckedObjectMapper.JSON_MAPPER;
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class Patches {

    public static JsonPatch generatePatch(Object stateBefore, Object stateAfter) {
        JsonNode firstState = JSON_MAPPER.valueToTree(stateBefore);
        JsonNode secondState = JSON_MAPPER.valueToTree(stateAfter);
        return JsonDiff.asJsonPatch(firstState, secondState);
    }

    public static JsonNode applyPatch(JsonPatch patch, JsonNode target) throws JsonPatchException {
        return patch.apply(target);
    }

}