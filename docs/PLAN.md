# Current plan

Release 1.3.1-beta.1 packages weighted first-complete search, rotating diagnostic traces, and cursor-transfer fixes on top of published v1.3.0. See ../PLAN.MD for the original design and algorithm references, and RELEASE_NOTES.md for the changes in this release.

Next acceptance steps: run both pictured notes with the new JAR; collect their logs from the instance's logs/thaumcraftresearchsolver directory; verify successful server completion, tool/note swaps, and empty cursor. Use the traces to compare time spent in each path and identify queue growth or resource exhaustion. Consider explicit abundant-pair hub routing if the weighted search remains too slow.
