package com.jetbeans;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;
import java.util.List;

public class BuildingRequirement {

    public final String buildingId;
    public final int constructionDays;
    public final List<StackRequirement> requirements;

    public BuildingRequirement(String buildingId, int constructionDays,
                               List<StackRequirement> requirements) {
        this.buildingId = buildingId;
        this.constructionDays = constructionDays;
        this.requirements = requirements;
    }

    public static class StackRequirement {
        public final Item item;
        public final int count;

        public StackRequirement(Item item, int count) {
            this.item = item;
            this.count = count;
        }
    }

    public static BuildingRequirement fromJson(JsonObject obj) {
        String id = obj.get("id").getAsString();
        int days = obj.has("construction_days")
                ? obj.get("construction_days").getAsInt()
                : 1;
        List<StackRequirement> reqs = new ArrayList<>();

        JsonArray requires = obj.getAsJsonArray("requires");
        for (JsonElement el : requires) {
            JsonObject entry = el.getAsJsonObject();
            String itemId = entry.get("item").getAsString();
            int count = entry.get("count").getAsInt();
            Item item = Registry.ITEM.get(new Identifier(itemId));
            reqs.add(new StackRequirement(item, count));
        }

        return new BuildingRequirement(id, days, reqs);
    }
}