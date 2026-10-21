package akyrion.thepredict.adapter.server.patterns;

import org.bukkit.entity.Player;

/**
 * Main-hand weapon class without opening an inventory view (C21). A spear or a trident is not a critical weapon.
 */
final class HeldWeapon {
    private HeldWeapon() {
    }

    static boolean weapon(Player player) {
        var name = name(player);
        return name.contains("SWORD") || name.contains("_AXE") || name.contains("SPEAR") || name.equals("TRIDENT")
                || name.equals("MACE");
    }

    static boolean spear(Player player) {
        var name = name(player);
        return name.contains("SPEAR") || name.equals("TRIDENT");
    }

    static String kind(Player player) {
        var name = name(player);
        if (name.contains("SPEAR") || name.equals("TRIDENT")) {
            return "spear";
        }
        if (name.contains("SWORD")) {
            return "sword";
        }
        if (name.contains("_AXE")) {
            return "axe";
        }
        if (name.equals("MACE")) {
            return "mace";
        }
        return "";
    }

    private static String name(Player player) {
        var equipment = player.getEquipment();
        if (equipment == null) {
            return "";
        }
        return equipment.getItemInMainHand().getType().name();
    }
}
