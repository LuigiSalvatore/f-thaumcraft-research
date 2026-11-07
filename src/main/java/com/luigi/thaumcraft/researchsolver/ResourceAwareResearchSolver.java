package com.luigi.thaumcraft.researchsolver;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.research.ResearchNoteData;
import thaumcraft.common.lib.utils.HexUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Complete-only, ink-first solver which keeps player, bonus and crafted aspect
 * provenance separate. Kept outside {@link ResearchSolver} so the public API
 * remains compatible with older callers.
 */
final class ResourceAwareResearchSolver {

    static final double HEURISTIC_WEIGHT = 2.0D;

    private ResourceAwareResearchSolver() {}

    private enum Source { PLAYER, BONUS }

    static final class Ledger {
        final AspectList player;
        final AspectList bonus;
        final AspectList crafted;
        final Set<Aspect> discovered;
        final List<Aspect> fingerprintOrder;
        int playerSpent;
        int bonusSpent;
        double scarcity;

        Ledger(AspectList playerPool, AspectList bonusPool, List<Aspect> fingerprintOrder) {
            player = playerPool == null ? new AspectList() : playerPool.copy();
            bonus = bonusPool == null ? new AspectList() : bonusPool.copy();
            crafted = new AspectList();
            discovered = new HashSet<Aspect>();
            this.fingerprintOrder = fingerprintOrder;
            if (playerPool != null) discovered.addAll(playerPool.aspects.keySet());
        }

        Ledger(Ledger source) {
            player = source.player.copy();
            bonus = source.bonus.copy();
            crafted = source.crafted.copy();
            discovered = new HashSet<Aspect>(source.discovered);
            fingerprintOrder = source.fingerprintOrder;
            playerSpent = source.playerSpent;
            bonusSpent = source.bonusSpent;
            scarcity = source.scarcity;
        }

        Ledger copy() {
            return new Ledger(this);
        }

        void copyFrom(Ledger source) {
            player.aspects.clear();
            player.add(source.player);
            bonus.aspects.clear();
            bonus.add(source.bonus);
            crafted.aspects.clear();
            crafted.add(source.crafted);
            discovered.clear();
            discovered.addAll(source.discovered);
            playerSpent = source.playerSpent;
            bonusSpent = source.bonusSpent;
            scarcity = source.scarcity;
        }

        Source consumeDirect(Aspect aspect) {
            int playerBefore = player.getAmount(aspect);
            if (playerBefore > 0) {
                player.reduce(aspect, 1);
                if (crafted.getAmount(aspect) > 0) {
                    crafted.reduce(aspect, 1);
                } else {
                    playerSpent++;
                    scarcity += 1.0D / playerBefore;
                }
                return Source.PLAYER;
            }
            int bonusBefore = bonus.getAmount(aspect);
            if (bonusBefore > 0) {
                bonus.reduce(aspect, 1);
                bonusSpent++;
                scarcity += 0.05D / bonusBefore;
                return Source.BONUS;
            }
            return null;
        }

        void addCrafted(Aspect aspect, int amount) {
            player.add(aspect, amount);
            crafted.add(aspect, amount);
            discovered.add(aspect);
        }

        String fingerprint() {
            StringBuilder out = new StringBuilder();
            for (Aspect aspect : fingerprintOrder) {
                int p = player.getAmount(aspect);
                int b = bonus.getAmount(aspect);
                int c = crafted.getAmount(aspect);
                if (p != 0 || b != 0 || c != 0 || discovered.contains(aspect)) {
                    out.append(aspect.getTag()).append(':').append(p).append(':').append(b).append(':').append(c)
                        .append(':').append(discovered.contains(aspect) ? '1' : '0').append(';');
                }
            }
            return out.toString();
        }
    }

    private static final class Cost implements Comparable<Cost> {
        final int ink;
        final double scarcity;
        final int points;
        final int combinations;

        Cost(int ink, double scarcity, int points, int combinations) {
            this.ink = ink;
            this.scarcity = scarcity;
            this.points = points;
            this.combinations = combinations;
        }

        @Override
        public int compareTo(Cost other) {
            int result = Integer.compare(ink, other.ink);
            if (result != 0) return result;
            result = Double.compare(scarcity, other.scarcity);
            if (result != 0) return result;
            result = Integer.compare(points, other.points);
            if (result != 0) return result;
            return Integer.compare(combinations, other.combinations);
        }
    }

    private static final class State implements Comparable<State> {
        final String key;
        final HexUtils.Hex hex;
        final Aspect aspect;
        final Ledger ledger;
        final LinkedHashMap<String, Aspect> placements;
        final List<ResearchSolver.Combination> combinations;
        final Set<String> path;
        final int committedPlacementCount;
        final int remainingPlacementLowerBound;

        State(String key, HexUtils.Hex hex, Aspect aspect, Ledger ledger,
              LinkedHashMap<String, Aspect> placements,
              List<ResearchSolver.Combination> combinations, Set<String> path,
              int committedPlacementCount, int remainingPlacementLowerBound) {
            this.key = key;
            this.hex = hex;
            this.aspect = aspect;
            this.ledger = ledger;
            this.placements = placements;
            this.combinations = combinations;
            this.path = path;
            this.committedPlacementCount = committedPlacementCount;
            this.remainingPlacementLowerBound = remainingPlacementLowerBound;
        }

        Cost cost() {
            return new Cost(
                committedPlacementCount + placements.size(),
                ledger.scarcity,
                ledger.playerSpent + ledger.bonusSpent,
                combinations.size());
        }

        @Override
        public int compareTo(State other) {
            double estimatedInk = committedPlacementCount + placements.size()
                + HEURISTIC_WEIGHT * remainingPlacementLowerBound;
            double otherEstimatedInk = other.committedPlacementCount + other.placements.size()
                + HEURISTIC_WEIGHT * other.remainingPlacementLowerBound;
            int result = Double.compare(estimatedInk, otherEstimatedInk);
            if (result != 0) return result;
            return cost().compareTo(other.cost());
        }
    }

    private static final class OrderContext {
        final ResearchNoteData data;
        final Ledger initial;
        final String start;
        final long deadline;
        final Map<Aspect, List<Aspect>> adjacency;
        final SolverTrace trace;
        ResearchSolver.SolveResult best;
        boolean timedOut;
        int ordersAttempted;

        OrderContext(ResearchNoteData data, Ledger initial, String start, long deadline,
                     Map<Aspect, List<Aspect>> adjacency, SolverTrace trace) {
            this.data = data;
            this.initial = initial;
            this.start = start;
            this.deadline = deadline;
            this.adjacency = adjacency;
            this.trace = trace;
        }
    }

    static ResearchSolver.SolveResult solve(
        ResearchNoteData data, AspectList playerPool, AspectList bonusPool, int maxSolveMs) {
        return solve(data, playerPool, bonusPool, maxSolveMs, SolverTrace.disabled());
    }

    static ResearchSolver.SolveResult solve(
        ResearchNoteData data, AspectList playerPool, AspectList bonusPool, int maxSolveMs,
        SolverTrace trace) {
        if (data == null || data.hexEntries == null || data.hexes == null) return noSolution(false);

        List<String> roots = roots(data);
        if (roots.isEmpty()) return noSolution(false);
        if (isComplete(data, Collections.<String, Aspect>emptyMap(), roots)) {
            return new ResearchSolver.SolveResult(
                Collections.<String, Aspect>emptyMap(), Collections.<ResearchSolver.Combination>emptyList(),
                false, true, 0, 0, 0, 0.0D);
        }

        long deadline = System.nanoTime() + Math.max(100, maxSolveMs) * 1_000_000L;
        String start = roots.get(0);
        List<String> remaining = new ArrayList<String>(roots.subList(1, roots.size()));
        sortByDistance(data, start, remaining);
        trace.line("SEARCH_START roots=%s start=%s initialTargets=%s", roots, start, remaining);

        List<Aspect> aspects = sortedAspects();
        OrderContext context = new OrderContext(
            data, new Ledger(playerPool, bonusPool, aspects), start, deadline, buildAdjacency(aspects), trace);
        searchOrders(context, remaining, 0);
        if (context.best == null) {
            trace.line("SEARCH_END status=no-solution timedOut=%s ordersAttempted=%d",
                context.timedOut, context.ordersAttempted);
            return noSolution(context.timedOut);
        }

        ResearchSolver.SolveResult best = context.best;
        trace.line("SEARCH_END status=complete timedOut=%s ordersAttempted=%d ink=%d",
            context.timedOut, context.ordersAttempted, best.inkRequired);
        return new ResearchSolver.SolveResult(
            best.placements, best.combinations, context.timedOut, true, best.inkRequired,
            best.playerPointsConsumed, best.bonusPointsConsumed, best.scarcityCost);
    }

    static boolean validateComplete(ResearchNoteData data, Map<String, Aspect> placements) {
        if (data == null || data.hexEntries == null || data.hexes == null) return false;
        List<String> roots = roots(data);
        return !roots.isEmpty() && isComplete(data, placements, roots);
    }

    private static ResearchSolver.SolveResult noSolution(boolean timedOut) {
        return new ResearchSolver.SolveResult(
            Collections.<String, Aspect>emptyMap(), Collections.<ResearchSolver.Combination>emptyList(),
            timedOut, false, 0, 0, 0, 0.0D);
    }

    private static void searchOrders(OrderContext context, List<String> targets, int index) {
        if (context.best != null) return;
        if (System.nanoTime() >= context.deadline) {
            context.timedOut = true;
            context.trace.line("ORDER_SEARCH_TIMEOUT depth=%d attempted=%d", index, context.ordersAttempted);
            return;
        }
        if (index == targets.size()) {
            context.ordersAttempted++;
            long started = System.nanoTime();
            context.trace.line("ORDER_START number=%d targets=%s", context.ordersAttempted, targets);
            ResearchSolver.SolveResult candidate = solveOrder(
                context.data, context.initial, context.start, targets, context.deadline, context.adjacency,
                context.trace);
            context.trace.line("ORDER_END number=%d status=%s elapsedMs=%d ink=%s",
                context.ordersAttempted, candidate == null ? "failed" : "complete",
                (System.nanoTime() - started) / 1_000_000L,
                candidate == null ? "-" : Integer.toString(candidate.inkRequired));
            if (candidate != null) {
                context.best = candidate;
            }
            return;
        }
        for (int i = index; i < targets.size(); i++) {
            if (System.nanoTime() >= context.deadline) {
                context.timedOut = true;
                return;
            }
            Collections.swap(targets, index, i);
            searchOrders(context, targets, index + 1);
            Collections.swap(targets, index, i);
            if (context.best != null) return;
        }
    }

    private static ResearchSolver.SolveResult solveOrder(
        ResearchNoteData data, Ledger initial, String start, List<String> targets, long deadline,
        Map<Aspect, List<Aspect>> adjacency, SolverTrace trace) {
        LinkedHashMap<String, Aspect> placements = new LinkedHashMap<String, Aspect>();
        List<ResearchSolver.Combination> combinations = new ArrayList<ResearchSolver.Combination>();
        Ledger ledger = initial.copy();
        Set<String> connected = connectedComponent(data, placements, start);

        for (String target : targets) {
            if (System.nanoTime() >= deadline) return null;
            if (connected.contains(target)) continue;
            State path = findPath(
                data, placements, connected, target, ledger, combinations, deadline, adjacency, trace);
            if (path == null) return null;
            placements.putAll(path.placements);
            combinations = path.combinations;
            ledger = path.ledger;
            connected = connectedComponent(data, placements, start);
            if (!connected.contains(target)) return null;
        }

        if (!isComplete(data, placements, roots(data))) return null;
        return new ResearchSolver.SolveResult(
            placements, combinations, false, true, placements.size(), ledger.playerSpent,
            ledger.bonusSpent, ledger.scarcity);
    }

    private static State findPath(
        ResearchNoteData data, Map<String, Aspect> committed, Set<String> connected, String target,
        Ledger startingLedger, List<ResearchSolver.Combination> existingCombinations, long deadline,
        Map<Aspect, List<Aspect>> adjacency, SolverTrace trace) {
        long started = System.nanoTime();
        int polled = 0;
        int expanded = 0;
        int duplicateStates = 0;
        int generated = 0;
        int unavailableCandidates = 0;
        int maxQueue = 0;
        PriorityQueue<State> queue = new PriorityQueue<State>();
        Map<String, Integer> placementLowerBounds = placementLowerBounds(data, committed, connected, target);
        trace.line("PATH_START target=%s connectedCells=%d committedPlacements=%d reachableCells=%d",
            target, connected.size(), committed.size(), placementLowerBounds.size());
        for (String start : connected) {
            Aspect aspect = aspectAt(data, committed, null, start);
            HexUtils.Hex hex = data.hexes.get(start);
            if (aspect == null || hex == null) continue;
            Integer lowerBound = placementLowerBounds.get(start);
            if (lowerBound == null) continue;
            Set<String> path = new HashSet<String>();
            path.add(start);
            queue.add(new State(start, hex, aspect, startingLedger.copy(),
                new LinkedHashMap<String, Aspect>(),
                new ArrayList<ResearchSolver.Combination>(existingCombinations), path, committed.size(),
                lowerBound));
        }

        Set<String> visited = new HashSet<String>();
        while (!queue.isEmpty()) {
            if (queue.size() > maxQueue) maxQueue = queue.size();
            if (System.nanoTime() >= deadline) {
                trace.line("PATH_END target=%s status=timeout elapsedMs=%d polled=%d expanded=%d duplicates=%d generated=%d unavailable=%d visited=%d maxQueue=%d",
                    target, (System.nanoTime() - started) / 1_000_000L, polled, expanded, duplicateStates,
                    generated, unavailableCandidates, visited.size(), maxQueue);
                return null;
            }
            State current = queue.poll();
            polled++;
            if (!visited.add(current.key + '|' + current.aspect.getTag() + '|' + current.ledger.fingerprint())) {
                duplicateStates++;
                continue;
            }
            if (current.key.equals(target)) {
                trace.line("PATH_END target=%s status=found elapsedMs=%d newPlacements=%d polled=%d expanded=%d duplicates=%d generated=%d unavailable=%d visited=%d maxQueue=%d path=%s",
                    target, (System.nanoTime() - started) / 1_000_000L, current.placements.size(), polled,
                    expanded, duplicateStates, generated, unavailableCandidates, visited.size(), maxQueue,
                    current.placements);
                return current;
            }
            expanded++;

            for (int direction = 0; direction < 6; direction++) {
                HexUtils.Hex neighbour = current.hex.getNeighbour(direction);
                String neighbourKey = neighbour.toString();
                if (!data.hexes.containsKey(neighbourKey) || current.path.contains(neighbourKey)) continue;
                Integer lowerBound = placementLowerBounds.get(neighbourKey);
                if (lowerBound == null) continue;
                ResearchManager.HexEntry entry = data.hexEntries.get(neighbourKey);
                if (entry == null) continue;
                if (entry.type == 1 && !neighbourKey.equals(target) && !connected.contains(neighbourKey)) continue;

                Aspect fixed = aspectAt(data, committed, current.placements, neighbourKey);
                if (fixed != null) {
                    if (ResearchSolver.areRelated(current.aspect, fixed)) {
                        Set<String> nextPath = new HashSet<String>(current.path);
                        nextPath.add(neighbourKey);
                        queue.add(new State(neighbourKey, neighbour, fixed, current.ledger.copy(),
                            new LinkedHashMap<String, Aspect>(current.placements),
                            new ArrayList<ResearchSolver.Combination>(current.combinations), nextPath,
                            current.committedPlacementCount, lowerBound));
                        generated++;
                    }
                    continue;
                }

                List<Aspect> candidates = adjacency.get(current.aspect);
                if (candidates == null) continue;
                for (Aspect candidate : candidates) {
                    Ledger nextLedger = current.ledger.copy();
                    List<ResearchSolver.Combination> nextCombinations =
                        new ArrayList<ResearchSolver.Combination>(current.combinations);
                    if (consume(candidate, nextLedger, nextCombinations) == null) {
                        unavailableCandidates++;
                        continue;
                    }

                    LinkedHashMap<String, Aspect> nextPlacements =
                        new LinkedHashMap<String, Aspect>(current.placements);
                    nextPlacements.put(neighbourKey, candidate);
                    Set<String> nextPath = new HashSet<String>(current.path);
                    nextPath.add(neighbourKey);
                    queue.add(new State(neighbourKey, neighbour, candidate, nextLedger, nextPlacements,
                        nextCombinations, nextPath, current.committedPlacementCount, lowerBound));
                    generated++;
                }
            }
        }
        trace.line("PATH_END target=%s status=exhausted elapsedMs=%d polled=%d expanded=%d duplicates=%d generated=%d unavailable=%d visited=%d maxQueue=%d",
            target, (System.nanoTime() - started) / 1_000_000L, polled, expanded, duplicateStates,
            generated, unavailableCandidates, visited.size(), maxQueue);
        return null;
    }

    private static Source consume(
        Aspect aspect, Ledger ledger, List<ResearchSolver.Combination> combinations) {
        Source direct = ledger.consumeDirect(aspect);
        if (direct != null) return direct;
        if (aspect == null || aspect.isPrimal()) return null;
        Aspect[] parents = aspect.getComponents();
        if (parents == null || parents.length != 2 || parents[0] == null || parents[1] == null) return null;

        Ledger trial = ledger.copy();
        List<ResearchSolver.Combination> steps = new ArrayList<ResearchSolver.Combination>();
        Source first = consume(parents[0], trial, steps);
        if (first == null) return null;
        Source second = consume(parents[1], trial, steps);
        if (second == null) return null;
        Aspect result = ResearchManager.getCombinationResult(parents[0], parents[1]);
        if (result != aspect) return null;

        steps.add(new ResearchSolver.Combination(
            parents[0], parents[1], first == Source.BONUS, second == Source.BONUS));
        trial.addCrafted(aspect, trial.discovered.contains(aspect) ? 1 : 3);
        if (trial.consumeDirect(aspect) == null) return null;

        ledger.copyFrom(trial);
        combinations.addAll(steps);
        return Source.PLAYER;
    }

    private static boolean isComplete(
        ResearchNoteData data, Map<String, Aspect> placements, List<String> roots) {
        return !roots.isEmpty() && connectedComponent(data, placements, roots.get(0)).containsAll(roots);
    }

    private static Set<String> connectedComponent(
        ResearchNoteData data, Map<String, Aspect> placements, String start) {
        Set<String> visited = new HashSet<String>();
        Deque<String> queue = new ArrayDeque<String>();
        if (aspectAt(data, placements, null, start) == null) return visited;
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            String key = queue.removeFirst();
            HexUtils.Hex hex = data.hexes.get(key);
            Aspect aspect = aspectAt(data, placements, null, key);
            if (hex == null || aspect == null) continue;
            for (int direction = 0; direction < 6; direction++) {
                String neighbour = hex.getNeighbour(direction).toString();
                if (visited.contains(neighbour) || !data.hexes.containsKey(neighbour)) continue;
                Aspect other = aspectAt(data, placements, null, neighbour);
                if (other != null && ResearchSolver.areRelated(aspect, other)) {
                    visited.add(neighbour);
                    queue.addLast(neighbour);
                }
            }
        }
        return visited;
    }

    private static Aspect aspectAt(
        ResearchNoteData data, Map<String, Aspect> committed, Map<String, Aspect> path, String key) {
        if (path != null && path.containsKey(key)) return path.get(key);
        if (committed != null && committed.containsKey(key)) return committed.get(key);
        ResearchManager.HexEntry entry = data.hexEntries.get(key);
        return entry != null && entry.type >= 1 ? entry.aspect : null;
    }

    private static List<String> roots(ResearchNoteData data) {
        List<String> result = new ArrayList<String>();
        for (Map.Entry<String, ResearchManager.HexEntry> entry : data.hexEntries.entrySet()) {
            if (entry.getValue() != null && entry.getValue().type == 1 && data.hexes.containsKey(entry.getKey())) {
                result.add(entry.getKey());
            }
        }
        Collections.sort(result);
        return result;
    }

    private static Map<Aspect, List<Aspect>> buildAdjacency(List<Aspect> aspects) {
        Map<Aspect, List<Aspect>> result = new HashMap<Aspect, List<Aspect>>();
        for (Aspect aspect : aspects) {
            List<Aspect> neighbours = new ArrayList<Aspect>();
            for (Aspect candidate : aspects) {
                if (ResearchSolver.areRelated(aspect, candidate)) neighbours.add(candidate);
            }
            result.put(aspect, neighbours);
        }
        return result;
    }

    /**
     * Minimum number of currently vacant cells that any geometric path from each
     * board cell must fill before reaching {@code target}. This deliberately
     * ignores aspect compatibility and resources, so it is an admissible A*
     * lower bound. Existing roots/nodes and already committed placements cost
     * no additional ink.
     */
    private static Map<String, Integer> placementLowerBounds(
        ResearchNoteData data, Map<String, Aspect> committed, Set<String> connected, String target) {
        Map<String, Integer> distances = new HashMap<String, Integer>();
        PriorityQueue<BoardDistance> queue = new PriorityQueue<BoardDistance>();
        distances.put(target, 0);
        queue.add(new BoardDistance(target, 0));

        while (!queue.isEmpty()) {
            BoardDistance current = queue.poll();
            Integer known = distances.get(current.key);
            if (known == null || current.cost != known) continue;
            HexUtils.Hex hex = data.hexes.get(current.key);
            if (hex == null) continue;

            int enterCurrentCost = aspectAt(data, committed, null, current.key) == null ? 1 : 0;
            for (int direction = 0; direction < 6; direction++) {
                String predecessor = hex.getNeighbour(direction).toString();
                if (!data.hexes.containsKey(predecessor)) continue;
                ResearchManager.HexEntry entry = data.hexEntries.get(predecessor);
                if (entry == null) continue;
                if (entry.type == 1 && !predecessor.equals(target) && !connected.contains(predecessor)) continue;

                int candidate = current.cost + enterCurrentCost;
                Integer previous = distances.get(predecessor);
                if (previous == null || candidate < previous) {
                    distances.put(predecessor, candidate);
                    queue.add(new BoardDistance(predecessor, candidate));
                }
            }
        }
        return distances;
    }

    private static final class BoardDistance implements Comparable<BoardDistance> {
        final String key;
        final int cost;

        BoardDistance(String key, int cost) {
            this.key = key;
            this.cost = cost;
        }

        @Override
        public int compareTo(BoardDistance other) {
            return Integer.compare(cost, other.cost);
        }
    }

    private static List<Aspect> sortedAspects() {
        List<Aspect> aspects = new ArrayList<Aspect>(Aspect.aspects.values());
        Collections.sort(aspects, new Comparator<Aspect>() {
            @Override
            public int compare(Aspect left, Aspect right) {
                return left.getTag().compareTo(right.getTag());
            }
        });
        return aspects;
    }

    private static void sortByDistance(
        final ResearchNoteData data, final String start, List<String> targets) {
        final HexUtils.Hex origin = data.hexes.get(start);
        if (origin == null) return;
        Collections.sort(targets, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                int leftDistance = distance(origin, data.hexes.get(left));
                int rightDistance = distance(origin, data.hexes.get(right));
                int result = Integer.compare(leftDistance, rightDistance);
                return result != 0 ? result : left.compareTo(right);
            }
        });
    }

    private static int distance(HexUtils.Hex left, HexUtils.Hex right) {
        if (right == null) return Integer.MAX_VALUE;
        int dq = left.q - right.q;
        int dr = left.r - right.r;
        return (Math.abs(dq) + Math.abs(dr) + Math.abs(dq + dr)) / 2;
    }
}
