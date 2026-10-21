package akyrion.thepredict.adapter.server.duel;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One duelist as the recorder samples it (M18.4). Immutable.
 *
 * @param id UUID of the entity
 * @param faction its MythicMobs faction as read
 * @param healthRatio health over maximum health, in {@code [0, 1]}
 * @param absorption absorption points, such as those of the {@code shield} mechanic
 * @param x position
 * @param y position
 * @param z position
 * @param yaw body yaw, in degrees
 * @param widthBlocks width of its bounding box
 * @param heightBlocks height of its bounding box
 * @param target UUID of its target, if any
 * @param raisedShield whether it holds a raised shield
 * @param blocking whether the raised shield blocks already
 * @param activeItem key of the item in use, empty text for none
 */
public record DuelistReading(UUID id, String faction, double healthRatio, double absorption, double x, double y,
                             double z, float yaw, double widthBlocks, double heightBlocks, Optional<UUID> target,
                             boolean raisedShield, boolean blocking, String activeItem) {
    /**
     * Validates the reading.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public DuelistReading {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(activeItem, "activeItem");
    }

    /**
     * The gap between the bounding boxes of two duelists, each box centred on its position and as tall as the entity.
     *
     * @param other the other duelist
     * @return the Euclidean gap, 0 when the boxes touch or overlap
     */
    public double gapTo(DuelistReading other) {
        var dx = Math.max(0.0, Math.abs(x - other.x) - (widthBlocks + other.widthBlocks) / 2);
        var dz = Math.max(0.0, Math.abs(z - other.z) - (widthBlocks + other.widthBlocks) / 2);
        var dy = Math.max(0.0, Math.max(other.y - (y + heightBlocks), y - (other.y + other.heightBlocks)));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * The distance between the positions of two duelists.
     *
     * @param other the other duelist
     * @return the distance, in blocks
     */
    public double distanceTo(DuelistReading other) {
        var dx = x - other.x;
        var dy = y - other.y;
        var dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
