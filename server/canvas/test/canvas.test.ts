import { test } from "node:test";
import assert from "node:assert/strict";
import { type Deps, type Env, handle, parse } from "../src/canvas.ts";

const ID = "4Dvkj6JhhA12EX05fT7y2e";
const MP4 = "https://canvaz.scdn.co/upload/licensor/x/video/abc.cnvs.mp4";
const FOUND = { canvasesList: [{ canvasUrl: MP4, trackUri: `spotify:track:${ID}` }] };

/** Deps whose upstream calls are answered by [answer], in order; records what was asked. */
function deps(...answers: (() => Response)[]): Deps & { asked: string[]; pending: Promise<unknown>[]; clock: { t: number } } {
  const asked: string[] = [];
  const clock = { t: 0 };
  const pending: Promise<unknown>[] = [];
  return {
    asked,
    pending,
    clock,
    now: () => clock.t,
    fetch: async (input) => {
      asked.push(input);
      const next = answers.shift();
      if (!next) throw new Error("unexpected call");
      return next();
    },
    cache: null,
    sleep: async (ms) => void (clock.t += ms),
    waitUntil: (p) => void pending.push(p),
  };
}

function kv(store = new Map<string, string>()) {
  return {
    store,
    get: async (key: string) => store.get(key) ?? null,
    put: async (key: string, value: string) => void store.set(key, value),
  } as unknown as KVNamespace & { store: Map<string, string> };
}

const ok = (body: unknown) => () => new Response(JSON.stringify(body), { status: 200 });
const status = (code: number) => () => new Response("{}", { status: code });
const ask = (env: Env, d: Deps, id = ID) => handle(new Request(`https://w.example/api/canvas?trackId=${id}`), env, d);

test("passes a found canvas through in the app's shape and remembers it in KV", async () => {
  const store = kv();
  const d = deps(ok(FOUND));
  const response = await ask({ CANVAS: store }, d);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), FOUND);
  await Promise.all(d.pending);
  assert.equal(JSON.parse(store.store.get(ID)!).canvasUrl, MP4);
  assert.equal(d.asked[0], `https://spotify-canva.vercel.app/api/canvas?trackId=${ID}`);
});

test("an empty list is a miss, not a failure, and is not written to KV", async () => {
  const store = kv();
  const d = deps(ok({ canvasesList: [] }));
  const response = await ask({ CANVAS: store }, d);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { canvasesList: [] });
  await Promise.all(d.pending);
  assert.equal(store.store.size, 0);
});

test("retries after a server error", async () => {
  const d = deps(status(500), ok(FOUND));
  const response = await ask({}, d);
  assert.equal(response.status, 200);
  assert.equal(d.asked.length, 2);
});

test("falls through to the next upstream before retrying", async () => {
  const d = deps(status(500), ok(FOUND));
  const response = await ask({ UPSTREAMS: "https://a.example/api/canvas, https://b.example/api/canvas" }, d);
  assert.equal(response.status, 200);
  assert.deepEqual(d.asked.map((u) => new URL(u).host), ["a.example", "b.example"]);
});

test("serves the remembered canvas when every upstream fails", async () => {
  const store = kv(new Map([[ID, JSON.stringify(FOUND.canvasesList[0])]]));
  const d = deps(status(500), status(503));
  const response = await ask({ CANVAS: store }, d);
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("X-Canvas-Stale"), "1");
  assert.deepEqual(await response.json(), FOUND);
});

test("is a 502 when every upstream fails and nothing is remembered", async () => {
  const d = deps(status(500), () => { throw new TypeError("network"); });
  const response = await ask({ CANVAS: kv() }, d);
  assert.equal(response.status, 502);
  assert.equal(response.headers.get("Cache-Control"), "no-store");
});

test("an unreadable body counts as a failure", async () => {
  const d = deps(ok({ nope: 1 }), ok(FOUND));
  assert.equal((await ask({}, d)).status, 200);
  assert.equal(d.asked.length, 2);
});

test("refuses a bad track ID without asking anyone", async () => {
  const d = deps();
  assert.equal((await ask({}, d, "not-an-id")).status, 400);
  assert.equal(d.asked.length, 0);
});

test("passes an upstream 400 on as a 400", async () => {
  assert.equal((await ask({}, deps(status(400)))).status, 400);
});

test("skips the second pass when the first used up the time", async () => {
  const d = deps(() => { d.clock.t += 4000; return status(500)(); }, ok(FOUND));
  const response = await ask({}, d);
  assert.equal(response.status, 502);
  assert.equal(d.asked.length, 1);
});

test("only answers /api/canvas", async () => {
  const response = await handle(new Request("https://w.example/"), {}, deps());
  assert.equal(response.status, 404);
});

test("parse keeps HTTPS MP4s for the track, under data too", () => {
  const body = {
    data: {
      canvasesList: [
        { canvasUrl: "https://canvaz.scdn.co/still.jpg", trackUri: `spotify:track:${ID}` },
        { canvasUrl: "http://canvaz.scdn.co/plain.mp4", trackUri: `spotify:track:${ID}` },
        { canvasUrl: "https://canvaz.scdn.co/other.mp4", trackUri: "spotify:track:0000000000000000000000" },
        { canvasUrl: MP4 },
      ],
    },
  };
  assert.deepEqual(parse(body, ID), [{ canvasUrl: MP4, trackUri: `spotify:track:${ID}` }]);
  assert.equal(parse("junk", ID), null);
});
