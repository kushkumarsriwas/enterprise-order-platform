#!/usr/bin/env node
// Creates the Agile backlog of docs/AGILE.md on GitHub: one label per epic, one milestone per sprint and one issue per
// user story (closed as completed, because every story is done). Running it again skips what already exists.
// Needs the GitHub CLI, signed in:  gh auth login
// Usage:  node scripts/agile-board.mjs [owner/repo]      (default: kushkumarsriwas/enterprise-order-platform)
//         node scripts/agile-board.mjs --dry-run         (only prints what it would create)

import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";

const args = process.argv.slice(2);
const dryRun = args.includes("--dry-run");
const repo = args.find((a) => !a.startsWith("--")) || "kushkumarsriwas/enterprise-order-platform";
const gh = (...args) => execFileSync("gh", args, { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] }).trim();
const COLORS = ["0e8a16", "1d76db", "b60205", "fbca04", "5319e7", "006b75", "d93f0b"];

// ------------------------------------------------------------------ read the plan
const plan = readFileSync(new URL("../docs/AGILE.md", import.meta.url), "utf8").split("\n");
const epics = [];
const sprints = [];
for (const line of plan) {
  let m = line.match(/^\| (EPIC-\d+) ([^|]+?) \| ([^|]+?) \|$/);
  if (m) epics.push({ id: m[1], name: `${m[1]} ${m[2]}`, goal: m[3] });
  m = line.match(/^### (Sprint (\d+) — .+)$/);
  if (m) sprints.push({ title: m[1], number: Number(m[2]), goal: "", stories: [] });
  m = line.match(/^Goal: (.+)$/);
  if (m && sprints.length) sprints.at(-1).goal = m[1];
  m = line.match(/^\| (NX-\d+) \| (.+?) \| (\d+) \| (EPIC-\d+) \|$/);
  if (m && sprints.length) sprints.at(-1).stories.push({ id: m[1], story: m[2], points: Number(m[3]), epic: m[4] });
}
const stories = sprints.flatMap((s) => s.stories.map((story) => ({ ...story, sprint: s })));
if (!epics.length || !stories.length) throw new Error("No epics or stories found in docs/AGILE.md");
console.log(`${repo}: ${epics.length} epics, ${sprints.length} sprints, ${stories.length} user stories`);
if (dryRun) {
  for (const sprint of sprints) {
    console.log(`\n${sprint.title} (${sprint.stories.reduce((sum, s) => sum + s.points, 0)} points) - ${sprint.goal}`);
    for (const story of sprint.stories) console.log(`  ${story.id} ${shortTitle(story.story)} [${story.epic}, ${story.points}]`);
  }
  process.exit(0);
}

/** "As a customer, I want to create an account and sign in, so that ..." -> "Create an account and sign in" */
function shortTitle(story) {
  const want = story.match(/I want (?:to )?(.+?)(?:, so that|\.$)/);
  const text = (want ? want[1] : story).replace(/\.$/, "");
  return text.charAt(0).toUpperCase() + text.slice(1);
}

// ------------------------------------------------------------------ labels
gh("label", "create", "user story", "--repo", repo, "--color", "c5def5", "--description", "A user story of the backlog", "--force");
for (const [i, epic] of epics.entries()) {
  gh("label", "create", epic.name, "--repo", repo, "--color", COLORS[i % COLORS.length], "--description", epic.goal.slice(0, 100), "--force");
}
console.log("labels ready");

// ------------------------------------------------------------------ milestones (open while issues are added)
const milestones = JSON.parse(gh("api", `repos/${repo}/milestones?state=all&per_page=100`));
for (const sprint of sprints) {
  const existing = milestones.find((m) => m.title === sprint.title);
  if (existing) {
    if (existing.state === "closed") gh("api", "-X", "PATCH", `repos/${repo}/milestones/${existing.number}`, "-f", "state=open");
    sprint.milestone = existing.number;
  } else {
    sprint.milestone = JSON.parse(gh("api", `repos/${repo}/milestones`, "-f", `title=${sprint.title}`, "-f", `description=Goal: ${sprint.goal}`)).number;
  }
}
console.log("milestones ready");

// ------------------------------------------------------------------ issues, one per story
const issues = JSON.parse(gh("issue", "list", "--repo", repo, "--state", "all", "--limit", "500", "--json", "number,title"));
for (const story of stories) {
  if (issues.some((issue) => issue.title.startsWith(`${story.id} `))) {
    console.log(`  ${story.id} already exists`);
    continue;
  }
  const epic = epics.find((e) => e.id === story.epic);
  const body = [
    `**${story.story}**`,
    "",
    `- Sprint: ${story.sprint.title}`,
    `- Epic: ${epic.name}`,
    `- Story points: ${story.points}`,
    "",
    "Done when the definition of done in docs/AGILE.md is met (CI green, tests, Swagger, no secrets).",
  ].join("\n");
  const url = gh("issue", "create", "--repo", repo, "--title", `${story.id} ${shortTitle(story.story)}`, "--body", body,
    "--label", "user story", "--label", epic.name, "--milestone", story.sprint.title);
  gh("issue", "close", url, "--repo", repo, "--reason", "completed");
  console.log(`  ${story.id} created and closed: ${url}`);
}

// ------------------------------------------------------------------ the sprints are over
for (const sprint of sprints) {
  gh("api", "-X", "PATCH", `repos/${repo}/milestones/${sprint.milestone}`, "-f", "state=closed");
}
console.log(`Done: https://github.com/${repo}/milestones?state=closed`);
