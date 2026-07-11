package com.luigi.thaumcraft.researchsolver;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.lib.research.ResearchNoteData;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.utils.HexUtils;

import java.util.*;

public class ResearchSolver {

    public static class Combination {
        public final Aspect first;
        public final Aspect second;

        public Combination(Aspect first, Aspect second) {
            this.first = first;
            this.second = second;
        }
    }

    public static class SolveResult {
        public final Map<String, Aspect> placements;
        public final List<Combination> combinations;
        /** True when the permutation search was cut short by the 1-second wall-clock budget. */
        public final boolean timedOut;

        public SolveResult(Map<String, Aspect> placements, List<Combination> combinations) {
            this.placements = placements;
            this.combinations = combinations;
            this.timedOut = false;
        }

        public SolveResult(Map<String, Aspect> placements, List<Combination> combinations, boolean timedOut) {
            this.placements = placements;
            this.combinations = combinations;
            this.timedOut = timedOut;
        }
    }

    public static boolean areRelated(Aspect first, Aspect second) {
        if (first == null || second == null) return false;
        if (first.isPrimal() && second.isPrimal()) return false;
        if (first.isPrimal()) {
            return isComponentOf(first, second);
        }
        if (second.isPrimal()) {
            return isComponentOf(second, first);
        }
        return isComponentOf(first, second) || isComponentOf(second, first);
    }

    private static boolean isComponentOf(Aspect component, Aspect compound) {
        Aspect[] comps = compound.getComponents();
        if (comps == null) return false;
        for (Aspect c : comps) {
            if (c != null && c.getTag().equals(component.getTag())) {
                return true;
            }
        }
        return false;
    }

    public static boolean tryToConsume(Aspect aspect, AspectList pool, List<Combination> steps) {
        if (pool.getAmount(aspect) > 0) {
            pool.reduce(aspect, 1);
            return true;
        }
        if (aspect.isPrimal()) {
            return false;
        }
        Aspect[] comps = aspect.getComponents();
        if (comps == null || comps.length != 2) {
            return false;
        }
        AspectList tempPool = pool.copy();
        List<Combination> tempSteps = new ArrayList<Combination>();
        if (tryToConsume(comps[0], tempPool, tempSteps) && tryToConsume(comps[1], tempPool, tempSteps)) {
            steps.addAll(tempSteps);
            steps.add(new Combination(comps[0], comps[1]));
            // Update pool by copying the contents of tempPool
            pool.aspects.clear();
            pool.add(tempPool);
            return true;
        }
        return false;
    }

    /**
     * Cost of placing aspect {@code a} given the current pool, encoding §3 priority:
     * 1. Deplete abundant aspects first  → primary key: 1.0 / amount  (low amount = high cost)
     * 2. Prefer primal over compound at same abundance → tiebreaker: +0 (primal) vs +0.5 (compound)
     * 3. Prefer shallower compound chains → tertiary: +0.1 * combination depth
     *
     * Crucially, primal bias is a *tiebreaker within the same abundance bucket*, never overriding it.
     * A compound held in large quantity is always chosen before a primal held in small quantity.
     */
    public static double getAspectCost(Aspect a, AspectList pool) {
        int amount = pool.getAmount(a);
        if (amount > 0) {
            // Primary: 1/amount so high abundance → low cost.
            // Secondary: primal costs 0 extra, compound costs 0.5 extra (within the same bucket).
            // The 0.5 is safely less than the difference between any two consecutive int amounts
            // at amounts >= 1 (where 1/n - 1/(n+1) = 1/(n*(n+1)) >= 1/2 only at n=1),
            // so we dampen it to 1e-4 to make it a pure tiebreaker.
            double primBonus = a.isPrimal() ? 0.0 : 1e-4;
            return (1.0 / amount) + primBonus;
        }
        // Not held directly — can we synthesize it from components?
        if (a.isPrimal()) {
            return Double.POSITIVE_INFINITY; // Can't synthesize primals.
        }
        Aspect[] comps = a.getComponents();
        if (comps == null || comps.length != 2) {
            return Double.POSITIVE_INFINITY;
        }
        double cost1 = getAspectCost(comps[0], pool);
        double cost2 = getAspectCost(comps[1], pool);
        if (cost1 == Double.POSITIVE_INFINITY || cost2 == Double.POSITIVE_INFINITY) {
            return Double.POSITIVE_INFINITY;
        }
        // Tertiary: each combination step adds a small depth penalty so shallower
        // chains are preferred over deeper ones at equal resource cost.
        return cost1 + cost2 + 1e-3;
    }

    /**
     * State for the Dijkstra search.
     * Uses String keys (hex.toString()) as canonical hex identifiers instead of
     * HexUtils.Hex objects, because HexUtils.Hex only overrides equals(HexUtils.Hex)
     * — not equals(Object) — so it is unsafe in HashSet/HashMap.
     */
    private static class SearchState implements Comparable<SearchState> {
        final String hexKey;          // canonical key: hex.toString()
        final HexUtils.Hex hex;       // kept only for getNeighbour() enumeration
        final Aspect aspect;
        final AspectList remainingPool;
        final double cost;
        final List<String> pathKeys;
        final List<Aspect> pathAspects;
        final List<Combination> combinations;

        SearchState(String hexKey, HexUtils.Hex hex, Aspect aspect, AspectList remainingPool, double cost,
                    List<String> pathKeys, List<Aspect> pathAspects, List<Combination> combinations) {
            this.hexKey = hexKey;
            this.hex = hex;
            this.aspect = aspect;
            this.remainingPool = remainingPool;
            this.cost = cost;
            this.pathKeys = pathKeys;
            this.pathAspects = pathAspects;
            this.combinations = combinations;
        }

        @Override
        public int compareTo(SearchState o) {
            return Double.compare(this.cost, o.cost);
        }
    }

    public static SolveResult solve(ResearchNoteData data, AspectList playerPool, AspectList bonusPool) {
        if (data == null || data.hexEntries == null || data.hexes == null) {
            return new SolveResult(Collections.<String, Aspect>emptyMap(), Collections.<Combination>emptyList());
        }

        // Initialize total pool = playerPool + bonusPool
        AspectList totalPool = playerPool.copy();
        totalPool.add(bonusPool);

        // Find all root hex keys (String keys from the map, safe for Set/Map use).
        List<String> rootKeys = new ArrayList<String>();
        for (Map.Entry<String, ResearchManager.HexEntry> entry : data.hexEntries.entrySet()) {
            if (entry.getValue().type == 1) { // ROOT
                if (data.hexes.containsKey(entry.getKey())) {
                    rootKeys.add(entry.getKey());
                }
            }
        }

        if (rootKeys.isEmpty()) {
            return new SolveResult(Collections.<String, Aspect>emptyMap(), Collections.<Combination>emptyList());
        }

        if (rootKeys.size() > 1) {
            List<String> targets = new ArrayList<String>(rootKeys);
            String startKey = targets.remove(0);

            // 1-second wall-clock budget for the permutation search.
            // (N-1)! orderings can be enormous for 10+ roots — cap to avoid hanging the render thread.
            final long TIMEOUT_NS = 1_000_000_000L;
            long deadline = System.nanoTime() + TIMEOUT_NS;
            boolean timedOut = false;

            // Best partial result found while no complete solution has been seen yet.
            SolveResult bestPartial = null;

            List<List<String>> perms = getPermutations(targets);
            for (List<String> perm : perms) {
                if (System.nanoTime() >= deadline) {
                    timedOut = true;
                    break;
                }
                SolveResult res = solveWithOrder(data, totalPool, startKey, perm);
                if (res != null) {
                    return res; // Complete solution found — return immediately, no timeout flag.
                }
                // No complete solution from this ordering, but track any greedy partial result
                // so we have something to show if we exhaust all orderings or hit the deadline.
                if (bestPartial == null) {
                    SolveResult partial = solveGreedyFallback(data, totalPool, rootKeys);
                    if (partial != null && !partial.placements.isEmpty()) {
                        bestPartial = partial;
                    }
                }
            }

            if (timedOut) {
                // Return best partial found so far (greedy fallback as last resort).
                if (bestPartial != null && !bestPartial.placements.isEmpty()) {
                    return new SolveResult(bestPartial.placements, bestPartial.combinations, true);
                }
                SolveResult fallback = solveGreedyFallback(data, totalPool, rootKeys);
                boolean hasPlacements = fallback != null && !fallback.placements.isEmpty();
                Map<String, Aspect> p = hasPlacements ? fallback.placements : Collections.<String, Aspect>emptyMap();
                List<Combination> c = hasPlacements ? fallback.combinations : Collections.<Combination>emptyList();
                return new SolveResult(p, c, true);
            }
        }

        // Fallback to greedy if permutations all failed or there is only 1 root.
        return solveGreedyFallback(data, totalPool, rootKeys);
    }

    private static List<List<String>> getPermutations(List<String> list) {
        List<List<String>> result = new ArrayList<List<String>>();
        if (list.isEmpty()) {
            result.add(list);
            return result;
        }
        permute(list, 0, result);
        return result;
    }

    private static void permute(List<String> list, int k, List<List<String>> result) {
        if (k >= list.size()) {
            result.add(new ArrayList<String>(list));
            return;
        }
        for (int i = k; i < list.size(); i++) {
            Collections.swap(list, i, k);
            permute(list, k + 1, result);
            Collections.swap(list, k, i);
        }
    }

    private static SolveResult solveWithOrder(ResearchNoteData data, AspectList pool, String startKey, List<String> orderedTargets) {
        Set<String> connected = new HashSet<String>();
        connected.add(startKey);

        Map<String, Aspect> finalPlacements = new HashMap<String, Aspect>();
        List<Combination> finalCombinations = new ArrayList<Combination>();

        AspectList totalPool = pool.copy();

        for (String targetKey : orderedTargets) {
            SearchState cheapestTargetState = null;
            PriorityQueue<SearchState> queue = new PriorityQueue<SearchState>();

            // Push all connected hexes into the queue
            for (String startConnected : connected) {
                HexUtils.Hex startHex = data.hexes.get(startConnected);
                if (startHex == null) continue;
                Aspect startAspect = getAspectAt(data, finalPlacements, startConnected);
                if (startAspect != null) {
                    queue.add(new SearchState(
                        startConnected, startHex, startAspect, totalPool.copy(), 0.0,
                        Collections.singletonList(startConnected),
                        Collections.singletonList(startAspect),
                        Collections.<Combination>emptyList()
                    ));
                }
            }

            // visited key: hexKey + ":" + aspect tag
            Set<String> visited = new HashSet<String>();

            while (!queue.isEmpty()) {
                SearchState current = queue.poll();
                String visitedKey = current.hexKey + ":" + current.aspect.getTag();

                if (visited.contains(visitedKey)) continue;
                visited.add(visitedKey);

                if (current.hexKey.equals(targetKey)) {
                    cheapestTargetState = current;
                    break;
                }

                // Explore neighbors
                for (int i = 0; i < 6; i++) {
                    HexUtils.Hex neighbor = current.hex.getNeighbour(i);
                    String neighborKey = neighbor.toString();

                    if (!data.hexes.containsKey(neighborKey)) continue; // Void/obstacle

                    ResearchManager.HexEntry neighborEntry = data.hexEntries.get(neighborKey);
                    if (neighborEntry == null) continue;

                    // If neighbor is a ROOT but not our target, we cannot cross it
                    if (neighborEntry.type == 1 && !neighborKey.equals(targetKey)) {
                        continue;
                    }

                    // Preplaced aspect (ROOT, or already placed aspect)
                    Aspect preplaced = getAspectAt(data, finalPlacements, neighborKey);
                    if (preplaced != null) {
                        if (areRelated(current.aspect, preplaced)) {
                            List<String> nextPathKeys = new ArrayList<String>(current.pathKeys);
                            nextPathKeys.add(neighborKey);
                            List<Aspect> nextPathAspects = new ArrayList<Aspect>(current.pathAspects);
                            nextPathAspects.add(preplaced);

                            queue.add(new SearchState(
                                neighborKey, neighbor, preplaced, current.remainingPool.copy(), current.cost,
                                nextPathKeys, nextPathAspects, current.combinations
                            ));
                        }
                        continue;
                    }

                    // Otherwise, neighbor is vacant. We can try placing any registered aspect
                    for (Aspect cand : Aspect.aspects.values()) {
                        if (areRelated(current.aspect, cand)) {
                            AspectList nextPool = current.remainingPool.copy();
                            List<Combination> nextCombos = new ArrayList<Combination>(current.combinations);

                            if (tryToConsume(cand, nextPool, nextCombos)) {
                                double stepCost = getAspectCost(cand, current.remainingPool);
                                if (stepCost != Double.POSITIVE_INFINITY) {
                                    List<String> nextPathKeys = new ArrayList<String>(current.pathKeys);
                                    nextPathKeys.add(neighborKey);
                                    List<Aspect> nextPathAspects = new ArrayList<Aspect>(current.pathAspects);
                                    nextPathAspects.add(cand);

                                    queue.add(new SearchState(
                                        neighborKey, neighbor, cand, nextPool, current.cost + stepCost,
                                        nextPathKeys, nextPathAspects, nextCombos
                                    ));
                                }
                            }
                        }
                    }
                }
            }

            if (cheapestTargetState == null) {
                // Cannot connect this target, search fails
                return null;
            }

            // Path found! Apply it
            for (int i = 0; i < cheapestTargetState.pathKeys.size(); i++) {
                String hexKey = cheapestTargetState.pathKeys.get(i);
                Aspect a = cheapestTargetState.pathAspects.get(i);
                connected.add(hexKey);

                // If it was vacant, record the placement
                ResearchManager.HexEntry entry = data.hexEntries.get(hexKey);
                if (entry != null && entry.type == 0 && !finalPlacements.containsKey(hexKey)) {
                    finalPlacements.put(hexKey, a);
                }
            }

            finalCombinations.addAll(cheapestTargetState.combinations);
            totalPool = cheapestTargetState.remainingPool; // Deduct used aspects
        }

        return new SolveResult(finalPlacements, finalCombinations);
    }

    private static SolveResult solveGreedyFallback(ResearchNoteData data, AspectList totalPool, List<String> rootKeys) {
        Set<String> connected = new HashSet<String>();
        connected.add(rootKeys.get(0));

        Set<String> remainingTargets = new HashSet<String>(rootKeys);
        remainingTargets.remove(rootKeys.get(0));

        Map<String, Aspect> finalPlacements = new HashMap<String, Aspect>();
        List<Combination> finalCombinations = new ArrayList<Combination>();

        // Loop to connect all targets
        while (!remainingTargets.isEmpty()) {
            SearchState cheapestTargetState = null;
            PriorityQueue<SearchState> queue = new PriorityQueue<SearchState>();

            // Push all connected hexes into the queue
            for (String startKey : connected) {
                HexUtils.Hex startHex = data.hexes.get(startKey);
                if (startHex == null) continue;
                Aspect startAspect = getAspectAt(data, finalPlacements, startKey);
                if (startAspect != null) {
                    queue.add(new SearchState(
                        startKey, startHex, startAspect, totalPool.copy(), 0.0,
                        Collections.singletonList(startKey),
                        Collections.singletonList(startAspect),
                        Collections.<Combination>emptyList()
                    ));
                }
            }

            // visited key: hexKey + ":" + aspect tag
            Set<String> visited = new HashSet<String>();

            while (!queue.isEmpty()) {
                SearchState current = queue.poll();
                String visitedKey = current.hexKey + ":" + current.aspect.getTag();

                if (visited.contains(visitedKey)) continue;
                visited.add(visitedKey);

                if (remainingTargets.contains(current.hexKey)) {
                    cheapestTargetState = current;
                    break;
                }

                // Explore neighbors
                for (int i = 0; i < 6; i++) {
                    HexUtils.Hex neighbor = current.hex.getNeighbour(i);
                    String neighborKey = neighbor.toString(); // safe: key == toString()

                    if (!data.hexes.containsKey(neighborKey)) continue; // Void/obstacle

                    ResearchManager.HexEntry neighborEntry = data.hexEntries.get(neighborKey);
                    if (neighborEntry == null) continue;

                    // If neighbor is a ROOT but not our target, we cannot cross it
                    if (neighborEntry.type == 1 && !remainingTargets.contains(neighborKey)) {
                        continue;
                    }

                    // Preplaced aspect (ROOT, or already placed aspect)
                    Aspect preplaced = getAspectAt(data, finalPlacements, neighborKey);
                    if (preplaced != null) {
                        if (areRelated(current.aspect, preplaced)) {
                            List<String> nextPathKeys = new ArrayList<String>(current.pathKeys);
                            nextPathKeys.add(neighborKey);
                            List<Aspect> nextPathAspects = new ArrayList<Aspect>(current.pathAspects);
                            nextPathAspects.add(preplaced);

                            queue.add(new SearchState(
                                neighborKey, neighbor, preplaced, current.remainingPool.copy(), current.cost,
                                nextPathKeys, nextPathAspects, current.combinations
                            ));
                        }
                        continue;
                    }

                    // Otherwise, neighbor is vacant. We can try placing any registered aspect
                    for (Aspect cand : Aspect.aspects.values()) {
                        if (areRelated(current.aspect, cand)) {
                            AspectList nextPool = current.remainingPool.copy();
                            List<Combination> nextCombos = new ArrayList<Combination>(current.combinations);

                            if (tryToConsume(cand, nextPool, nextCombos)) {
                                double stepCost = getAspectCost(cand, current.remainingPool);
                                if (stepCost != Double.POSITIVE_INFINITY) {
                                    List<String> nextPathKeys = new ArrayList<String>(current.pathKeys);
                                    nextPathKeys.add(neighborKey);
                                    List<Aspect> nextPathAspects = new ArrayList<Aspect>(current.pathAspects);
                                    nextPathAspects.add(cand);

                                    queue.add(new SearchState(
                                        neighborKey, neighbor, cand, nextPool, current.cost + stepCost,
                                        nextPathKeys, nextPathAspects, nextCombos
                                    ));
                                }
                            }
                        }
                    }
                }
            }

            if (cheapestTargetState == null) {
                // Cannot connect remaining targets, return what we have so far
                break;
            }

            // Path found! Apply it
            for (int i = 0; i < cheapestTargetState.pathKeys.size(); i++) {
                String hexKey = cheapestTargetState.pathKeys.get(i);
                Aspect a = cheapestTargetState.pathAspects.get(i);
                connected.add(hexKey);

                // If it was vacant, record the placement
                ResearchManager.HexEntry entry = data.hexEntries.get(hexKey);
                if (entry != null && entry.type == 0 && !finalPlacements.containsKey(hexKey)) {
                    finalPlacements.put(hexKey, a);
                }
            }

            finalCombinations.addAll(cheapestTargetState.combinations);
            totalPool = cheapestTargetState.remainingPool; // Deduct used aspects
            remainingTargets.remove(cheapestTargetState.hexKey);
        }

        return new SolveResult(finalPlacements, finalCombinations);
    }

    /**
     * Returns the aspect at a given hex key, or null if vacant/unknown.
     * Uses String key throughout — no HexUtils.Hex identity issues.
     */
    private static Aspect getAspectAt(ResearchNoteData data, Map<String, Aspect> finalPlacements, String hexKey) {
        if (finalPlacements.containsKey(hexKey)) {
            return finalPlacements.get(hexKey);
        }
        ResearchManager.HexEntry entry = data.hexEntries.get(hexKey);
        if (entry != null) {
            if (entry.type == 1) { // ROOT
                return entry.aspect;
            }
            if (entry.type == 2) { // NODE (placed aspect)
                return entry.aspect;
            }
        }
        return null;
    }
}
