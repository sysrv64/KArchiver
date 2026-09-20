# Known patterns and canned responses

These are the recurring shapes this repository sees. Prefer these answers
over writing a new diagnosis from scratch, after checking that the pattern
really applies.

### Pattern: bug report without logs
**Recognize when:** the report is a crash, freeze, wrong result or other
runtime problem, but the Logs section is empty and no log file is attached.
**Verdict / label:** valid / needs-info
**Canned comment:**
> Thanks. To act on this we need the app logs: open Settings -> About ->
> Save logs to Downloads/KArchiver and attach the saved file (or paste the
> relevant logcat lines). Without logs a runtime bug can't be confirmed.

### Pattern: bug report without version or reproduction steps
**Recognize when:** the report exists but skips the template's version,
device or "Steps to reproduce" fields, leaving nothing concrete to check.
**Verdict / label:** valid / needs-info
**Canned comment:**
> Please fill in the issue template: the app version, your device and
> Android version, and the exact steps that reproduce the problem.

### Pattern: known limitation reported as a bug
**Recognize when:** the report matches something listed under "Known
limitations" in `repo_context.md`, for example Shizuku not reaching `/data`,
a read-only `/system`, RAR packing being locked, or a root-only file showing
no thumbnail.
**Verdict / label:** valid / invalid
**Canned comment:**
> This is expected behavior, not a bug: it is a documented limitation of the
> app (see QA.md and the repository context). [state the specific reason]

### Pattern: request already answered by the wiki or QA
**Recognize when:** the issue asks how to do something that is already
covered by the wiki or QA.md, with no bug behind it.
**Verdict / label:** valid / question
**Canned comment:**
> This is covered by the documentation: see the wiki
> (https://github.com/sysrv64/KArchiver/wiki) and QA.md. If something there
> is wrong or missing, say which part and we will fix it.

### Pattern: empty or template-only submission
**Recognize when:** the body has no real content beyond the template
headings, or is only a title with no description at all.
**Verdict / label:** trash
**Canned comment:**
> This issue has no usable content. Please reopen it with the template
> filled in: what happened, how to reproduce it, and the logs.
