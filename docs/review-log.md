# Review log

One entry per review run (newest last). The next run reviews from the last "to" commit.

| Date | Range | PR | Summary | Open proposals |
|---|---|---|---|---|
| 2026-10-25 | first run: whole app, PC client, tools ..`c159424` | #23 | 6 fixes: error replies decoded as values, link adopted after onDestroy, guard null-intent crash, stale guard thread clears `running`, app blocks 1500 + parity test requires 1403/1500, PC README; new Gaia/frames JVM tests | resume guard after process kill; keep link across rotation; MAC out of `gaia_client.py`; no raw channel-15 fallback without `--channel`; fewer guard notification updates |

All 5 proposals of the first run were built in v0.11.1 (Ryane's OK, 25 Oct 2026): guard resume after a kill, configChanges for rotation/fold (dark mode still recreates), MAC out of gaia_client.py, no channel-15 fallback without --channel, notification only on change. Next run: review from the v0.11.1 merge commit.
