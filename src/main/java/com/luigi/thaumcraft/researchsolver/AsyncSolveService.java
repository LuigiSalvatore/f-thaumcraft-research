package com.luigi.thaumcraft.researchsolver;

import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.research.ResearchNoteData;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Single daemon worker so expensive search never blocks GUI rendering. */
final class AsyncSolveService {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Thaumcraft Research Solver");
        thread.setDaemon(true);
        return thread;
    });
    private Future<ResearchSolver.SolveResult> future;
    private ResearchSolver.SolveResult result;
    private String error;

    boolean start(ResearchNoteData liveNote, AspectList player, AspectList bonus) {
        if (isRunning() || liveNote == null) return false;
        final ResearchNoteData note = snapshot(liveNote);
        final AspectList playerCopy = player == null ? new AspectList() : player.copy();
        final AspectList bonusCopy = bonus == null ? new AspectList() : bonus.copy();
        result = null;
        error = null;
        future = executor.submit(() -> ResearchSolver.solve(note, playerCopy, bonusCopy));
        return true;
    }

    void poll() {
        if (future == null || !future.isDone()) return;
        try {
            result = future.get();
        } catch (Exception exception) {
            error = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        } finally {
            future = null;
        }
    }

    boolean isRunning() { return future != null; }
    boolean hasResult() { return result != null; }
    ResearchSolver.SolveResult takeResult() {
        ResearchSolver.SolveResult value = result;
        result = null;
        return value;
    }
    String takeError() {
        String value = error;
        error = null;
        return value;
    }

    void cancel() {
        if (future != null) future.cancel(true);
        future = null;
        result = null;
        error = null;
    }

    private static ResearchNoteData snapshot(ResearchNoteData source) {
        ResearchNoteData copy = new ResearchNoteData();
        copy.key = source.key;
        copy.color = source.color;
        copy.complete = source.complete;
        copy.copies = source.copies;
        copy.hexes.putAll(source.hexes);
        for (Map.Entry<String, ResearchManager.HexEntry> entry : source.hexEntries.entrySet()) {
            ResearchManager.HexEntry value = entry.getValue();
            copy.hexEntries.put(entry.getKey(), value == null ? null :
                new ResearchManager.HexEntry(value.aspect, value.type));
        }
        return copy;
    }
}
