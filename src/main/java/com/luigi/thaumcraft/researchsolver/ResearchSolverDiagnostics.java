package com.luigi.thaumcraft.researchsolver;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.research.ResearchNoteData;
import thaumcraft.common.lib.utils.HexUtils;

import java.util.ArrayList;
import java.util.List;

/** Deterministic checks shared by development and the in-game test command. */
final class ResearchSolverDiagnostics {
    static final class Report {
        int passed;
        int failed;
        final List<String> messages = new ArrayList<String>();

        void check(boolean condition, String name) {
            if (condition) {
                passed++;
                messages.add("PASS: " + name);
            } else {
                failed++;
                messages.add("FAIL: " + name);
            }
        }
    }

    static Report runAll() {
        Report report = new Report();
        report.check(ResearchSolver.areRelated(Aspect.GREED, Aspect.MAN),
            "Lucrum is directly related to Humanus");
        report.check(ResearchSolver.areRelated(Aspect.MAN, Aspect.TOOL),
            "Humanus is directly related to Instrumentum");
        report.check(!ResearchSolver.areRelated(Aspect.GREED, Aspect.TOOL),
            "shared Humanus parent alone is not direct adjacency");

        ResearchNoteData bridge = bridgeNote();
        AspectList direct = new AspectList().add(Aspect.MAN, 1);
        ResearchSolver.SolveResult directResult = ResourceAwareResearchSolver.solve(
            bridge, direct, new AspectList(), 2000);
        report.check(directResult.complete && directResult.inkRequired == 1 &&
            directResult.placements.get("1:0") == Aspect.MAN,
            "Lucrum-Humanus-Instrumentum bridge uses one ink");

        AspectList parents = new AspectList().add(Aspect.BEAST, 1).add(Aspect.MIND, 1);
        ResearchSolver.SolveResult crafted = ResourceAwareResearchSolver.solve(
            bridge, parents, new AspectList(), 2000);
        report.check(crafted.complete && crafted.combinations.size() == 1 &&
            crafted.combinations.get(0).result == Aspect.MAN,
            "missing Humanus is synthesized from Bestia and Cognitio");

        AspectList player = new AspectList().add(Aspect.BEAST, 1);
        AspectList bonus = new AspectList().add(Aspect.MIND, 1);
        ResearchSolver.SolveResult mixed = ResourceAwareResearchSolver.solve(bridge, player, bonus, 2000);
        report.check(mixed.complete && mixed.combinations.size() == 1 &&
            !mixed.combinations.get(0).firstFromBonus && mixed.combinations.get(0).secondFromBonus,
            "mixed player/bonus combination provenance is retained");

        AspectList before = parents.copy();
        ResourceAwareResearchSolver.solve(bridge, parents, new AspectList(), 100);
        report.check(parents.getAmount(Aspect.BEAST) == before.getAmount(Aspect.BEAST) &&
            parents.getAmount(Aspect.MIND) == before.getAmount(Aspect.MIND),
            "solver snapshots never mutate live aspect pools");

        ResearchSolver.SolveResult impossible = ResourceAwareResearchSolver.solve(
            bridge, new AspectList(), new AspectList(), 100);
        report.check(!impossible.complete && impossible.placements.isEmpty(),
            "impossible/timeout result never exposes a partial layout");
        return report;
    }

    public static void main(String[] args) {
        Report report = runAll();
        for (String message : report.messages) System.out.println(message);
        if (report.failed != 0) throw new AssertionError(report.failed + " diagnostic(s) failed");
        System.out.println("All " + report.passed + " diagnostics passed.");
    }

    private static ResearchNoteData bridgeNote() {
        ResearchNoteData note = new ResearchNoteData();
        note.key = "diagnostic.bridge";
        add(note, 0, 0, Aspect.GREED, 1);
        add(note, 1, 0, null, 0);
        add(note, 2, 0, Aspect.TOOL, 1);
        return note;
    }

    private static void add(ResearchNoteData note, int q, int r, Aspect aspect, int type) {
        HexUtils.Hex hex = new HexUtils.Hex(q, r);
        note.hexes.put(hex.toString(), hex);
        note.hexEntries.put(hex.toString(), new ResearchManager.HexEntry(aspect, type));
    }
}
