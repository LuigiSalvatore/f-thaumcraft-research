package com.luigi.thaumcraft.researchsolver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.research.ResearchNoteData;
import thaumcraft.common.lib.utils.HexUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Per-solve diagnostic trace intended to make live puzzle failures reproducible. */
final class SolverTrace {

    private static final Logger LOG = LogManager.getLogger("ThaumcraftResearchSolver");
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final int MAX_FILES = 25;
    private static volatile File traceDirectory;

    private final PrintWriter writer;
    private final File file;
    private final long startedNanos;

    private SolverTrace(PrintWriter writer, File file) {
        this.writer = writer;
        this.file = file;
        this.startedNanos = System.nanoTime();
    }

    static SolverTrace disabled() {
        return new SolverTrace(null, null);
    }

    static void configure(File gameDirectory) {
        if (!Config.solverDiagnosticLogging || gameDirectory == null) {
            traceDirectory = null;
            return;
        }
        File directory = new File(new File(gameDirectory, "logs"), "thaumcraftresearchsolver");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            LOG.warn("[ResearchSolver] Could not create diagnostic trace directory: " + directory);
            traceDirectory = null;
            return;
        }
        traceDirectory = directory;
        LOG.info("[ResearchSolver] Solver diagnostic traces: " + directory.getAbsolutePath());
    }

    static SolverTrace start(
        ResearchNoteData note, AspectList playerPool, AspectList bonusPool, int maxSolveMs,
        double heuristicWeight) {
        File directory = traceDirectory;
        if (directory == null) return disabled();
        try {
            prune(directory);
            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(new Date());
            File file = new File(directory,
                "solve-" + timestamp + '-' + SEQUENCE.incrementAndGet() + ".log");
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8), true);
            SolverTrace trace = new SolverTrace(writer, file);
            trace.line("TRACE format=1 modVersion=%s algorithm=weighted-a-star-first-complete",
                ThaumcraftResearchSolver.VERSION);
            trace.line("CONFIG maxSolveMs=%d heuristicWeight=%.2f objective=complete-then-abundance-local-best",
                maxSolveMs, heuristicWeight);
            trace.writeSnapshot(note, playerPool, bonusPool);
            return trace;
        } catch (Exception exception) {
            LOG.warn("[ResearchSolver] Could not open solver diagnostic trace", exception);
            return disabled();
        }
    }

    boolean enabled() {
        return writer != null;
    }

    void line(String format, Object... arguments) {
        if (writer == null) return;
        writer.printf(Locale.ROOT, "+%dms ", elapsedMillis());
        writer.printf(Locale.ROOT, format, arguments);
        writer.println();
    }

    void finish(ResearchSolver.SolveResult result, Throwable failure) {
        if (writer == null) return;
        if (failure != null) {
            line("RESULT status=exception type=%s message=%s", failure.getClass().getName(),
                safe(failure.getMessage()));
        } else if (result == null) {
            line("RESULT status=null");
        } else {
            line("RESULT status=%s timedOut=%s ink=%d playerPoints=%d bonusPoints=%d scarcity=%.8f combinations=%d",
                result.complete ? "complete" : "no-solution", result.timedOut, result.inkRequired,
                result.playerPointsConsumed, result.bonusPointsConsumed, result.scarcityCost,
                result.combinations.size());
            for (Map.Entry<String, Aspect> placement : result.placements.entrySet()) {
                line("PLACEMENT hex=%s aspect=%s", placement.getKey(), tag(placement.getValue()));
            }
            for (ResearchSolver.Combination combination : result.combinations) {
                line("COMBINATION first=%s firstSource=%s second=%s secondSource=%s result=%s",
                    tag(combination.first), combination.firstFromBonus ? "bonus" : "player",
                    tag(combination.second), combination.secondFromBonus ? "bonus" : "player",
                    tag(combination.result));
            }
        }
        line("TRACE_END elapsedMs=%d", elapsedMillis());
        writer.close();
        LOG.info("[ResearchSolver] Wrote solver diagnostic trace: " + file.getAbsolutePath());
    }

    private void writeSnapshot(ResearchNoteData note, AspectList playerPool, AspectList bonusPool) {
        if (note == null) {
            line("NOTE null");
            return;
        }
        line("NOTE key=%s complete=%s hexes=%d entries=%d", safe(note.key), note.complete,
            note.hexes == null ? -1 : note.hexes.size(), note.hexEntries == null ? -1 : note.hexEntries.size());
        if (note.hexEntries != null) {
            List<String> keys = new ArrayList<String>(note.hexEntries.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                ResearchManager.HexEntry entry = note.hexEntries.get(key);
                HexUtils.Hex hex = note.hexes == null ? null : note.hexes.get(key);
                line("HEX key=%s q=%s r=%s type=%s aspect=%s", key,
                    hex == null ? "?" : Integer.toString(hex.q),
                    hex == null ? "?" : Integer.toString(hex.r),
                    entry == null ? "null" : Integer.toString(entry.type),
                    entry == null ? "null" : tag(entry.aspect));
            }
        }
        writePool("PLAYER_POOL", playerPool);
        writePool("BONUS_POOL", bonusPool);

        List<Aspect> aspects = new ArrayList<Aspect>(Aspect.aspects.values());
        Collections.sort(aspects, new Comparator<Aspect>() {
            @Override
            public int compare(Aspect left, Aspect right) {
                return left.getTag().compareTo(right.getTag());
            }
        });
        for (Aspect aspect : aspects) {
            Aspect[] parents = aspect.getComponents();
            line("ASPECT tag=%s primal=%s parent1=%s parent2=%s player=%d bonus=%d",
                tag(aspect), aspect.isPrimal(), parents == null ? "-" : tag(parents[0]),
                parents == null ? "-" : tag(parents[1]), amount(playerPool, aspect), amount(bonusPool, aspect));
        }
        writeAbundantPairs(aspects, playerPool, bonusPool);
    }

    private void writePool(String name, AspectList pool) {
        List<Aspect> aspects = pool == null
            ? new ArrayList<Aspect>() : new ArrayList<Aspect>(pool.aspects.keySet());
        Collections.sort(aspects, new Comparator<Aspect>() {
            @Override
            public int compare(Aspect left, Aspect right) {
                int amount = Integer.compare(pool.getAmount(right), pool.getAmount(left));
                return amount != 0 ? amount : left.getTag().compareTo(right.getTag());
            }
        });
        StringBuilder values = new StringBuilder();
        for (Aspect aspect : aspects) {
            if (values.length() != 0) values.append(',');
            values.append(tag(aspect)).append('=').append(pool.getAmount(aspect));
        }
        line("%s %s", name, values.length() == 0 ? "empty" : values.toString());
    }

    private void writeAbundantPairs(List<Aspect> aspects, AspectList player, AspectList bonus) {
        List<AspectPair> pairs = new ArrayList<AspectPair>();
        for (int i = 0; i < aspects.size(); i++) {
            Aspect first = aspects.get(i);
            int firstAmount = amount(player, first) + amount(bonus, first);
            if (firstAmount <= 0) continue;
            for (int j = i + 1; j < aspects.size(); j++) {
                Aspect second = aspects.get(j);
                int secondAmount = amount(player, second) + amount(bonus, second);
                if (secondAmount > 0 && ResearchSolver.areRelated(first, second)) {
                    pairs.add(new AspectPair(first, second, Math.min(firstAmount, secondAmount),
                        firstAmount + secondAmount));
                }
            }
        }
        Collections.sort(pairs);
        int limit = Math.min(20, pairs.size());
        for (int i = 0; i < limit; i++) {
            AspectPair pair = pairs.get(i);
            line("ABUNDANT_BRIDGE rank=%d first=%s second=%s repeatCapacity=%d combinedStock=%d",
                i + 1, tag(pair.first), tag(pair.second), pair.repeatCapacity, pair.combinedStock);
        }
    }

    private long elapsedMillis() {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private static int amount(AspectList list, Aspect aspect) {
        return list == null || aspect == null ? 0 : list.getAmount(aspect);
    }

    private static String tag(Aspect aspect) {
        return aspect == null ? "null" : aspect.getTag();
    }

    private static String safe(String value) {
        return value == null ? "null" : value.replace('\r', ' ').replace('\n', ' ');
    }

    private static void prune(File directory) {
        File[] files = directory.listFiles();
        if (files == null || files.length < MAX_FILES) return;
        List<File> logs = new ArrayList<File>();
        for (File file : files) {
            if (file.isFile() && file.getName().startsWith("solve-") && file.getName().endsWith(".log")) {
                logs.add(file);
            }
        }
        Collections.sort(logs, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return Long.compare(left.lastModified(), right.lastModified());
            }
        });
        while (logs.size() >= MAX_FILES) {
            File oldest = logs.remove(0);
            if (!oldest.delete()) break;
        }
    }

    private static final class AspectPair implements Comparable<AspectPair> {
        final Aspect first;
        final Aspect second;
        final int repeatCapacity;
        final int combinedStock;

        AspectPair(Aspect first, Aspect second, int repeatCapacity, int combinedStock) {
            this.first = first;
            this.second = second;
            this.repeatCapacity = repeatCapacity;
            this.combinedStock = combinedStock;
        }

        @Override
        public int compareTo(AspectPair other) {
            int result = Integer.compare(other.repeatCapacity, repeatCapacity);
            if (result != 0) return result;
            result = Integer.compare(other.combinedStock, combinedStock);
            if (result != 0) return result;
            result = first.getTag().compareTo(other.first.getTag());
            return result != 0 ? result : second.getTag().compareTo(other.second.getTag());
        }
    }
}
