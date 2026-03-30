package com.jetbeans;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class BuildingRequirementsLoader {

    private static final List<BuildingRequirement> requirements = new ArrayList<>();

    public static void load() {
        try {
            InputStream stream = BuildingRequirementsLoader.class
                    .getResourceAsStream("/data/villageexpansion/building_requirements.json");

            if (stream == null) {
                VillageExpansionMod.LOGGER.error("building_requirements.json not found!");
                return;
            }

            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream))
                    .getAsJsonObject();

            JsonArray buildings = root.getAsJsonArray("buildings");
            for (JsonElement el : buildings) {
                BuildingRequirement req = BuildingRequirement.fromJson(el.getAsJsonObject());
                requirements.add(req);
                VillageExpansionMod.LOGGER.info("Loaded building requirement: {}", req.buildingId);
            }

        } catch (Exception e) {
            VillageExpansionMod.LOGGER.error("Failed to load building_requirements.json", e);
        }
    }

    /** Returns all known building requirements (for the GUI catalogue page). */
    public static List<BuildingRequirement> getAll() {
        return Collections.unmodifiableList(requirements);
    }

    public static Optional<BuildingRequirement> getForBuilding(String buildingId) {
        return requirements.stream()
                .filter(r -> r.buildingId.equals(buildingId))
                .findFirst();
    }

    /**
     * Returns the next building the village should construct.
     * Currently always the first entry; will become level-aware later.
     */
    public static Optional<BuildingRequirement> getNext() {
        if (requirements.isEmpty()) return Optional.empty();
        return Optional.of(requirements.get(0));
    }
}