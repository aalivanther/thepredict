package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EncounterId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;

/**
 * The request in flight of each encounter with its future (M09.1 steps 1 and 2, M09.8). Server thread only. At most
 * one entry per encounter, so at most {@code maxInFlightRequests} entries because the budget reserves first
 * (rule A5). Adding an entry marks the encounter in flight and removing it clears the mark, so the agenda never
 * offers an encounter with a request in flight and an encounter is never left blocked by a lost answer.
 */
public final class InFlightRegistry {
    private final Map<EncounterId, Entry> entries = new LinkedHashMap<>();

    /** Creates an empty registry. */
    public InFlightRegistry() {
    }

    /**
     * Records a sent request and marks its encounter in flight.
     *
     * @param encounter encounter of the request
     * @param request the request as sent
     * @param future future returned by the client, cancelled on expiry and at shutdown
     * @throws IllegalArgumentException if the request belongs to another encounter
     * @throws IllegalStateException if the encounter already has a request in flight
     */
    public void add(Encounter encounter, InFlightRequest request, Future<?> future) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(future, "future");
        if (!encounter.id().equals(request.encounter())) {
            throw new IllegalArgumentException("request of encounter " + request.encounter().value()
                    + " added for encounter " + encounter.id().value());
        }
        if (entries.putIfAbsent(encounter.id(), new Entry(encounter, request, future)) != null) {
            throw new IllegalStateException("encounter " + encounter.id().value() + " already has a request in flight");
        }
        encounter.schedule().requestInFlight(true);
    }

    /**
     * Removes a request if it is still the one in flight of its encounter, matched by encounter and sequence.
     *
     * @param request request of a completion
     * @return {@code true} if it was in flight; {@code false} for a late or unknown one
     */
    public boolean remove(InFlightRequest request) {
        var entry = entries.get(request.encounter());
        if (entry == null || entry.request().sequence() != request.sequence()) {
            return false;
        }
        entries.remove(request.encounter());
        entry.encounter().schedule().requestInFlight(false);
        return true;
    }

    /**
     * Removes and cancels the requests whose deadline passed; a late answer of one is later matched to nothing.
     *
     * @param nowNanos monotonic time now
     * @return the expired requests, oldest first
     */
    public List<InFlightRequest> expire(long nowNanos) {
        var expired = new ArrayList<Entry>();
        for (var entry : entries.values()) {
            if (nowNanos - entry.request().deadlineNanos() >= 0) {
                expired.add(entry);
            }
        }
        return release(expired);
    }

    /**
     * Cancels every request in flight and forgets them; part of the shutdown (rule T7).
     *
     * @return the cancelled requests
     */
    public List<InFlightRequest> cancelAll() {
        return release(List.copyOf(entries.values()));
    }

    /**
     * Counts the requests in flight.
     *
     * @return entries in the registry
     */
    public int size() {
        return entries.size();
    }

    /** Forgets the entries, clears the marks of their encounters and cancels their futures, in that order. */
    private List<InFlightRequest> release(List<Entry> released) {
        var requests = new ArrayList<InFlightRequest>(released.size());
        for (var entry : released) {
            entries.remove(entry.request().encounter());
            entry.encounter().schedule().requestInFlight(false);
            requests.add(entry.request());
        }
        released.forEach(entry -> entry.future().cancel(true));
        return requests;
    }

    /** One request in flight. */
    private record Entry(Encounter encounter, InFlightRequest request, Future<?> future) {
    }
}
