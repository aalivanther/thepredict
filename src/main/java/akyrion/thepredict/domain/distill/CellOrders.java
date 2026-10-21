package akyrion.thepredict.domain.distill;

import java.util.Map;
import java.util.Optional;

/**
 * Fine-cell orders with at least {@code distill.minCellOffers} offers (M29.2). At most 512 cells.
 *
 * @param orders orders by context text
 */
public record CellOrders(Map<String, TacticOrder> orders) {
    /** Cap. */
    public static final int MAX_CELLS = 512;

    /**
     * Copies the map and checks the cap.
     */
    public CellOrders {
        orders = Map.copyOf(orders);
        if (orders.size() > MAX_CELLS) {
            throw new IllegalArgumentException("cell orders");
        }
    }

    /**
     * The order of a cell.
     *
     * @param context fine context
     * @return the order
     */
    public Optional<TacticOrder> get(String context) {
        return Optional.ofNullable(orders.get(context));
    }
}
