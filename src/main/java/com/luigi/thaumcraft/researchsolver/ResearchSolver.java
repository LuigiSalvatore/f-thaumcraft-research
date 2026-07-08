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

        public SolveResult(Map<String, Aspect> placements, List<Combination> combinations) {
            this.placements = placements;
            this.combinations = combinations;
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

    private static class SearchState implements Comparable<SearchState> {
        final HexUtils.Hex hex;
        final Aspect aspect;
        final AspectList remainingPool;
        final double cost;
        final List<HexUtils.Hex> pathHexes;
        final List<Aspect> pathAspects;
        final List<Combination> combinations;

        SearchState(HexUtils.Hex hex, Aspect aspect, AspectList remainingPool, double cost,
                    List<HexUtils.Hex> pathHexes, List<Aspect> pathAspects, List<Combination> combinations) {
            this.hex = hex;
            this.aspect = aspect;
            this.remainingPool = remainingPool;
            this.cost = cost;
            this.pathHexes = pathHexes;
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

        // Find all roots
        List<HexUtils.Hex> roots = new ArrayList<HexUtils.Hex>();
        for (Map.Entry<String, ResearchManager.HexEntry> entry : data.hexEntries.entrySet()) {
            if (entry.getValue().type == 1) { // ROOT
                HexUtils.Hex hex = data.hexes.get(entry.getKey());
                if (hex != null) {
                    roots.add(hex);
                }
            }
        }

        if (roots.isEmpty()) {
            return new SolveResult(Collections.<String, Aspect>emptyMap(), Collections.<Combination>emptyList());
        }

        Set<HexUtils.Hex> connected = new HashSet<HexUtils.Hex>();
        connected.add(roots.get(0));

        Set<HexUtils.Hex> remainingTargets = new HashSet<HexUtils.Hex>(roots);
        remainingTargets.remove(roots.get(0));

        Map<String, Aspect> finalPlacements = new HashMap<String, Aspect>();
        List<Combination> finalCombinations = new ArrayList<Combination>();

        // Loop to connect all targets
        while (!remainingTargets.isEmpty()) {
            SearchState cheapestTargetState = null;
            PriorityQueue<SearchState> queue = new PriorityQueue<SearchState>();

            // Push all connected hexes into the queue
            for (HexUtils.Hex startHex : connected) {
                Aspect startAspect = getAspectAt(data, finalPlacements, startHex);
                if (startAspect != null) {
                    queue.add(new SearchState(
                        startHex, startAspect, totalPool.copy(), 0.0,
                        Collections.singletonList(startHex),
                        Collections.singletonList(startAspect),
                        Collections.<Combination>emptyList()
                    ));
                }
            }

            Set<String> visited = new HashSet<String>();

            while (!queue.isEmpty()) {
                SearchState current = queue.poll();
                String key = current.hex.toString() + ":" + current.aspect.getTag();

                if (visited.contains(key)) continue;
                visited.add(key);

                if (remainingTargets.contains(current.hex)) {
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
                    if (neighborEntry.type == 1 && !remainingTargets.contains(neighbor)) {
                        continue;
                    }

                    // Preplaced aspect (ROOT, or already placed aspect)
                    Aspect preplaced = getAspectAt(data, finalPlacements, neighbor);
                    if (preplaced != null) {
                        if (areRelated(current.aspect, preplaced)) {
                            List<HexUtils.Hex> nextPathHexes = new ArrayList<HexUtils.Hex>(current.pathHexes);
                            nextPathHexes.add(neighbor);
                            List<Aspect> nextPathAspects = new ArrayList<Aspect>(current.pathAspects);
                            nextPathAspects.add(preplaced);

                            queue.add(new SearchState(
                                neighbor, preplaced, current.remainingPool.copy(), current.cost,
                                nextPathHexes, nextPathAspects, current.combinations
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
                                    List<HexUtils.Hex> nextPathHexes = new ArrayList<HexUtils.Hex>(current.pathHexes);
                                    nextPathHexes.add(neighbor);
                                    List<Aspect> nextPathAspects = new ArrayList<Aspect>(current.pathAspects);
                                    nextPathAspects.add(cand);

                                    queue.add(new SearchState(
                                        neighbor, cand, nextPool, current.cost + stepCost,
                                        nextPathHexes, nextPathAspects, nextCombos
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
            for (int i = 0; i < cheapestTargetState.pathHexes.size(); i++) {
                HexUtils.Hex h = cheapestTargetState.pathHexes.get(i);
                Aspect a = cheapestTargetState.pathAspects.get(i);
                connected.add(h);

                // If it was vacant, place it
                String hexKey = h.toString();
                ResearchManager.HexEntry entry = data.hexEntries.get(hexKey);
                if (entry != null && entry.type == 0 && !finalPlacements.containsKey(hexKey)) {
                    finalPlacements.put(hexKey, a);
                }
            }

            finalCombinations.addAll(cheapestTargetState.combinations);
            totalPool = cheapestTargetState.remainingPool; // Deduct used aspects
            remainingTargets.remove(cheapestTargetState.hex);
        }

        return new SolveResult(finalPlacements, finalCombinations);
    }

    private static Aspect getAspectAt(ResearchNoteData data, Map<String, Aspect> finalPlacements, HexUtils.Hex hex) {
        String key = hex.toString();
        if (finalPlacements.containsKey(key)) {
            return finalPlacements.get(key);
        }
        ResearchManager.HexEntry entry = data.hexEntries.get(key);
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
