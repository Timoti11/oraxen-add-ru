package io.th0rgal.oraxen.items;

import com.syntaxphoenix.syntaxapi.nbt.NbtInt;
import com.syntaxphoenix.syntaxapi.nbt.NbtString;
import com.syntaxphoenix.syntaxapi.nbt.NbtTag;
import io.th0rgal.oraxen.OraxenPlugin;
import io.th0rgal.oraxen.mechanics.Mechanic;
import io.th0rgal.oraxen.mechanics.MechanicFactory;
import io.th0rgal.oraxen.mechanics.MechanicsManager;
import io.th0rgal.oraxen.settings.MessageOld;
import io.th0rgal.oraxen.settings.Plugin;
import io.th0rgal.oraxen.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.EnchantmentWrapper;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ItemParser {

    private static final Map<String, ModelData> MODEL_DATAS_BY_ID = new HashMap<>();

    private final OraxenMeta oraxenMeta;
    private final ConfigurationSection section;
    private final Material type;
    private boolean configUpdated = false;

    public ItemParser(ConfigurationSection section) {
        this.section = section;
        this.type = Material.getMaterial(section.getString("material"));
        this.oraxenMeta = new OraxenMeta();
        if (section.isConfigurationSection("Pack")) {
            ConfigurationSection packSection = section.getConfigurationSection("Pack");
            this.oraxenMeta.setPackInfos(packSection);
            if (packSection.isInt("custom_model_data"))
                MODEL_DATAS_BY_ID
                        .put(section.getName(),
                                new ModelData(type, oraxenMeta.getModelName(), packSection.getInt("custom_model_data")));
        }
    }

    public ItemBuilder buildItem(String name) {
        ItemBuilder item = new ItemBuilder(type);
        item.setDisplayName(name);
        return applyConfig(item);
    }

    public ItemBuilder buildItem() {
        ItemBuilder item = new ItemBuilder(type);
        if (section.contains("displayname"))
            item.setDisplayName(Utils.handleColors(section.getString("displayname")));
        return applyConfig(item);
    }

    private ItemBuilder applyConfig(ItemBuilder item) {

        if (section.contains("durability"))
            item.setDurability((short) section.getInt("durability"));

        if (section.contains("lore")) {
            List<String> lore = section.getStringList("lore");
            for (int i = 0; i < lore.size(); i++)
                lore.set(i, Utils.handleColors(lore.get(i)));
            item.setLore(lore);
        }

        if (section.contains("unbreakable"))
            item.setUnbreakable(section.getBoolean("unbreakable"));

        if (section.contains("color")) {
            String[] colors = section.getString("color").split(", ");
            item
                    .setColor(org.bukkit.Color
                            .fromRGB(Integer.parseInt(colors[0]), Integer.parseInt(colors[1]), Integer.parseInt(colors[2])));
        }

        if (section.contains("excludeFromInventory") && section.getBoolean("excludeFromInventory"))
            oraxenMeta.setExcludedFromInventory();

        if (!section.contains("injectID") || section.getBoolean("injectId"))
            item
                    .setCustomTag(new NamespacedKey(OraxenPlugin.get(), "id"), PersistentDataType.STRING,
                            section.getName());

        if (section.contains("ItemFlags")) {
            List<String> itemFlags = section.getStringList("ItemFlags");
            for (String itemFlag : itemFlags)
                item.addItemFlags(ItemFlag.valueOf(itemFlag));
        }

        if (section.contains("PotionEffects")) {
            @SuppressWarnings("unchecked") // because this sections must always return a List<LinkedHashMap<String, ?>>
            List<LinkedHashMap<String, Object>> potionEffects = (List<LinkedHashMap<String, Object>>) section
                    .getList("PotionEffects");
            for (Map<String, Object> serializedPotionEffect : potionEffects) {
                PotionEffectType effect = PotionEffectType.getByName((String) serializedPotionEffect.get("type"));
                int duration = (int) serializedPotionEffect.get("duration");
                int amplifier = (int) serializedPotionEffect.get("amplifier");
                boolean ambient = (boolean) serializedPotionEffect.get("ambient");
                boolean particles = (boolean) serializedPotionEffect.get("particles");
                boolean icon = (boolean) serializedPotionEffect.get("icon");
                item.addPotionEffect(new PotionEffect(effect, duration, amplifier, ambient, particles, icon));
            }
        }

        if (section.contains("AttributeModifiers")) {
            @SuppressWarnings("unchecked") // because this sections must always return a List<LinkedHashMap<String, ?>>
            List<LinkedHashMap<String, Object>> attributes = (List<LinkedHashMap<String, Object>>) section
                    .getList("AttributeModifiers");
            for (LinkedHashMap<String, Object> attributeJson : attributes) {
                AttributeModifier attributeModifier = AttributeModifier.deserialize(attributeJson);
                Attribute attribute = Attribute.valueOf((String) attributeJson.get("attribute"));
                item.addAttributeModifiers(attribute, attributeModifier);
            }
        }

        if (section.contains("Enchantments")) {
            ConfigurationSection enchantSection = section.getConfigurationSection("Enchantments");
            for (String enchant : enchantSection.getKeys(false))
                item
                        .addEnchant(EnchantmentWrapper.getByKey(NamespacedKey.minecraft(enchant)),
                                enchantSection.getInt(enchant));
        }

        if (section.contains("NBTTags")) {

            @SuppressWarnings("unchecked") // because this sections must always return a List<LinkedHashMap<String, ?>>
            List<LinkedHashMap<String, ?>> tagsList = (List<LinkedHashMap<String, ?>>) section.getList("NBTTags");

            for (LinkedHashMap<String, ?> tag : tagsList) {
                String type = tag.get("type").toString();
                String field = tag.get("name").toString();
                NbtTag nbtTag;

                switch (type) {
                    case "int":
                        nbtTag = new NbtInt(Integer.parseInt(tag.get("value").toString()));
                        break;

                    case "String":
                        nbtTag = new NbtString(tag.get("value").toString());
                        break;

                    default:
                        nbtTag = null;
                        MessageOld.WRONG_TYPE.send(Bukkit.getConsoleSender());
                        break;

                }
                item.setNbtTag(field, nbtTag);

            }
        }

        if (section.isConfigurationSection("Mechanics")) {
            ConfigurationSection mechanicsSection = section.getConfigurationSection("Mechanics");
            for (String mechanicID : mechanicsSection.getKeys(false)) {
                MechanicFactory factory = MechanicsManager.getMechanicFactory(mechanicID);
                if (factory != null) {
                    Mechanic mechanic = factory.parse(mechanicsSection.getConfigurationSection(mechanicID));
                    // Apply item modifiers
                    for (Function<ItemBuilder, ItemBuilder> itemModifier : mechanic.getItemModifiers())
                        item = itemModifier.apply(item);
                }
            }
        }

        if (oraxenMeta.hasPackInfos()) {
            int customModelData;
            if (MODEL_DATAS_BY_ID.containsKey(section.getName())) {
                customModelData = MODEL_DATAS_BY_ID.get(section.getName()).getDurability();
            } else {
                customModelData = ModelData.generateId(oraxenMeta.getModelName(), type);
                if ((boolean) Plugin.AUTOMATICALLY_SET_MODEL_ID.getValue()) {
                    this.configUpdated = true;
                    section.getConfigurationSection("Pack").set("custom_model_data", customModelData);
                }
            }
            item.setCustomModelData(customModelData);
            oraxenMeta.setCustomModelData(customModelData);
        }
        item.setOraxenMeta(oraxenMeta);

        return item;
    }

    public boolean isConfigUpdated() {
        return configUpdated;
    }

}
