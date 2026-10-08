import React, { useEffect, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  Camera,
  ChevronRight,
  Clock,
  ImagePlus,
  LocateFixed,
  MapPin,
  RefreshCw,
  Sparkles,
  Trophy,
  Upload,
  X,
  Zap,
} from "lucide-react";
import "./style.css";

const API_URL =
  import.meta.env.VITE_API_URL || "http://localhost:8080";

const api = async (u, o = {}) => {
  const r = await fetch(`${API_URL}${u}`, o);
  const t = await r.text();

  let d = null;
  try {
    d = t ? JSON.parse(t) : null;
  } catch {
    d = null;
  }

  if (!r.ok) {
    throw Error(d?.error || "Request failed");
  }

  return d;
};

const icon = {
  LEAF: "🍃",
  TREE: "🌳",
  STATUE: "🗿",
  CYCLE: "🚲",
  SAND: "🏖️",
  WATER: "💧",
  FLOWER: "🌸",
  ROCK: "🪨",
  SHADOW: "🌑",
  BENCH: "🪑",
  TEXTURE: "🌀",
  CLOUD: "☁️",
  BUILDING: "🏛️",
};

function App() {
  const [screen, setScreen] = useState("home");
  const [name, setName] = useState("");
  const [p, setP] = useState(null);
  const [lat, setLat] = useState("");
  const [lon, setLon] = useState("");
  const [battle, setBattle] = useState(null);
  const [bid, setBid] = useState("");
  const [err, setErr] = useState("");

  useEffect(() => {
    try {
      const x = JSON.parse(
        localStorage.getItem("irl-player") || "null"
      );

      if (x) {
        setP(x);
        setName(x.username);
      }
    } catch {
      localStorage.removeItem("irl-player");
    }
  }, []);

  async function start() {
    try {
      setErr("");

      if (!name.trim()) {
        throw Error("Enter a nickname.");
      }

      const x = await api(
        `/api/players/register?username=${encodeURIComponent(
          name.trim()
        )}`,
        {
          method: "POST",
        }
      );

      localStorage.setItem("irl-player", JSON.stringify(x));

      setP(x);
      setScreen("loc");
    } catch (e) {
      setErr(e.message);
    }
  }

  function gps() {
    if (!navigator.geolocation) {
      setErr("Geolocation is not supported by this browser.");
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (x) => {
        setLat(x.coords.latitude.toFixed(6));
        setLon(x.coords.longitude.toFixed(6));
        setErr("");
      },
      () =>
        setErr(
          "Location permission failed. Enter coordinates manually."
        ),
      {
        enableHighAccuracy: true,
        timeout: 10000,
        maximumAge: 0,
      }
    );
  }

  async function find() {
    try {
      setErr("");

      if (!p) {
        throw Error("Player session not found.");
      }

      if (!lat || !lon) {
        throw Error("Enter or detect your location first.");
      }

      const latitude = Number(lat);
      const longitude = Number(lon);

      if (
        !Number.isFinite(latitude) ||
        !Number.isFinite(longitude) ||
        latitude < -90 ||
        latitude > 90 ||
        longitude < -180 ||
        longitude > 180
      ) {
        throw Error("Enter valid latitude and longitude.");
      }

      const x = await api("/api/players/location", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          playerId: p.playerId,
          username: p.username,
          latitude,
          longitude,
        }),
      });

      if (x.found) {
        setBid(x.battleId);
        setScreen("battle");
      } else {
        setScreen("search");
      }
    } catch (e) {
      setErr(e.message);
    }
  }

  useEffect(() => {
    if (screen !== "search" || !p || !lat || !lon) {
      return;
    }

    const i = setInterval(async () => {
      try {
        const x = await api("/api/players/location", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            playerId: p.playerId,
            username: p.username,
            latitude: Number(lat),
            longitude: Number(lon),
          }),
        });

        if (x.found) {
          setBid(x.battleId);
          setScreen("battle");
        }
      } catch (e) {
        setErr(e.message);
      }
    }, 2000);

    return () => clearInterval(i);
  }, [screen, p, lat, lon]);

  useEffect(() => {
    if (!bid || !p || screen !== "battle") {
      return;
    }

    const i = setInterval(async () => {
      try {
        const x = await api(
          `/api/battles/${encodeURIComponent(
            bid
          )}?playerId=${encodeURIComponent(p.playerId)}`
        );

        setBattle(x);

        if (["FINISHED", "ERROR"].includes(x.status)) {
          setScreen("results");
        }
      } catch (e) {
        setErr(e.message);
      }
    }, 700);

    return () => clearInterval(i);
  }, [bid, p, screen]);

  return (
    <>
      <header>
        <b>
          <Zap fill="currentColor" /> IRL CLASH
        </b>
        <span>NO ROOMS • JUST BATTLE</span>
      </header>

      <main>
        {screen === "home" && (
          <section className="hero">
            <div>
              <small>REAL-WORLD MULTIPLAYER</small>

              <h1>
                Touch grass.
                <br />
                <em>Win a battle.</em>
              </h1>

              <p>
                Find someone nearby. Complete five random outdoor photo
                missions. Let Gemini judge the final clash.
              </p>

              <div className="box">
                <label>Nickname</label>

                <input
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="Somesh"
                  maxLength={30}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      start();
                    }
                  }}
                />

                <button onClick={start}>
                  Continue <ChevronRight />
                </button>
              </div>

              {err && <Error message={err} />}
            </div>

            <div className="art">
              <div className="circle">
                <small>THE WORLD IS YOUR</small>
                <strong>GAME BOARD</strong>
                <small>GO FIND IT.</small>
              </div>

              <i>🍃</i>
              <i>🚲</i>
              <i>💧</i>
            </div>
          </section>
        )}

        {screen === "loc" && (
          <section className="page">
            <small>LOCATION MATCHING</small>

            <h2>Find your opponent.</h2>

            <p>
              Use GPS or enter coordinates manually. Players within the
              match radius are paired automatically.
            </p>

            <div className="two">
              <div className="card">
                <b>{p?.username}</b>

                <button className="gps" onClick={gps}>
                  <LocateFixed /> Use my location
                </button>

                <div className="coords">
                  <label>
                    Latitude
                    <input
                      value={lat}
                      onChange={(e) => setLat(e.target.value)}
                      placeholder="31.468500"
                    />
                  </label>

                  <label>
                    Longitude
                    <input
                      value={lon}
                      onChange={(e) => setLon(e.target.value)}
                      placeholder="77.588000"
                    />
                  </label>
                </div>

                <button onClick={find}>
                  Find nearby players <ChevronRight />
                </button>

                {err && <Error message={err} />}
              </div>

              <div className="dark">
                <MapPin />

                <h3>Temporary matching</h3>

                <p>
                  No database. Exact coordinates are never shown to the
                  opponent. Only approximate distance is displayed.
                </p>
              </div>
            </div>
          </section>
        )}

        {screen === "search" && (
          <section className="center">
            <div className="card search">
              <LocateFixed className="pulse" />

              <small>MATCHMAKING LIVE</small>

              <h2>Scanning for a challenger...</h2>

              <p>
                Keep this page open. Another active player must be inside
                the match radius.
              </p>

              {err && <Error message={err} />}
            </div>
          </section>
        )}

        {screen === "battle" && battle && (
          <Battle
            b={battle}
            p={p}
            bid={bid}
            err={err}
            setErr={setErr}
          />
        )}

        {screen === "results" && battle && (
          <Results
            b={battle}
            p={p}
            again={() => {
              setBattle(null);
              setBid("");
              setErr("");
              setScreen("loc");
            }}
          />
        )}
      </main>

      <footer>
        IRL CLASH • 5 ROUNDS • OUTDOOR MISSIONS • AI REFEREE
      </footer>
    </>
  );
}

function Battle({ b, p, bid, err, setErr }) {
  const [file, setFile] = useState(null);
  const [cam, setCam] = useState(false);
  const [busy, setBusy] = useState(false);

  if (b.status === "EVALUATING") {
    return (
      <section className="center">
        <div className="card search">
          <Sparkles size={45} />

          <h2>Gemini is judging...</h2>

          <p>
            Comparing all submitted images against their missions.
          </p>

          <div className="bar" />
        </div>
      </section>
    );
  }

  const m = b.mission;

  if (!m) {
    return null;
  }

  const pct = Math.max(
    0,
    Math.min(
      100,
      (b.remainingSeconds / m.seconds) * 100
    )
  );

  async function submit() {
    if (!file) {
      setErr("Choose or take a photo first.");
      return;
    }

    try {
      setBusy(true);
      setErr("");

      const form = new FormData();

      form.append("playerId", p.playerId);
      form.append("file", file);

      await api(`/api/battles/${bid}/photo`, {
        method: "POST",
        body: form,
      });

      setFile(null);
    } catch (e) {
      setErr(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="page">
      <div className="battlebar">
        <small>ROUND {b.round} / 5</small>

        <b>
          {p.username} <i>VS</i> {b.opponent.username}
        </b>
      </div>

      <div className="battlegrid">
        <div className="mission">
          <div className="timer">
            <Clock /> {b.remainingSeconds}s
          </div>

          <div className="mi">
            {icon[m.category] || "🎯"}
          </div>

          <small>{m.category}</small>

          <h1>{m.title}</h1>

          <p>{m.instruction}</p>

          <div className="bar">
            <span style={{ width: `${pct}%` }} />
          </div>

          {b.myPhotoSubmitted ? (
            <div className="submitted">
              ✓ Photo submitted. Waiting for opponent...
            </div>
          ) : (
            <div className="actions">
              <button onClick={() => setCam(true)}>
                <Camera /> Take photo
              </button>

              <label>
                <ImagePlus /> Gallery

                <input
                  type="file"
                  accept="image/*"
                  capture="environment"
                  onChange={(e) =>
                    setFile(e.target.files?.[0] || null)
                  }
                />
              </label>
            </div>
          )}

          {file && (
            <div className="preview">
              <img
                src={URL.createObjectURL(file)}
                alt="Selected submission"
              />

              <button
                onClick={submit}
                disabled={busy}
              >
                {busy ? <RefreshCw /> : <Upload />}
                {busy ? " Uploading..." : " Submit photo"}
              </button>
            </div>
          )}

          {err && <Error message={err} />}
        </div>

        <aside className="card opp">
          <small>OPPONENT</small>

          <h3>{b.opponent.username}</h3>

          <p>{b.opponent.distanceMeters}m away</p>

          <div>
            {b.opponentPhotoSubmitted
              ? "✓ Photo submitted"
              : "● Taking photo..."}
          </div>

          <hr />

          <p>
            🛡️ Stay in safe public areas. No roads, trespassing or
            dangerous activity.
          </p>
        </aside>
      </div>

      {cam && (
        <CameraModal
          close={() => setCam(false)}
          done={(f) => {
            setFile(f);
            setCam(false);
          }}
        />
      )}
    </section>
  );
}

function CameraModal({ close, done }) {
  const v = useRef();
  const s = useRef();

  const [ready, setReady] = useState(false);

  useEffect(() => {
    navigator.mediaDevices
      ?.getUserMedia({
        video: {
          facingMode: {
            ideal: "environment",
          },
        },
        audio: false,
      })
      .then((x) => {
        s.current = x;

        if (v.current) {
          v.current.srcObject = x;
        }

        setReady(true);
      })
      .catch(() => {});

    return () =>
      s.current?.getTracks().forEach((t) => t.stop());
  }, []);

  function snap() {
    const x = v.current;

    if (!x || !x.videoWidth || !x.videoHeight) {
      return;
    }

    const c = document.createElement("canvas");

    const scale = Math.min(1, 1600 / x.videoWidth);

    c.width = Math.round(x.videoWidth * scale);
    c.height = Math.round(x.videoHeight * scale);

    c.getContext("2d").drawImage(
      x,
      0,
      0,
      c.width,
      c.height
    );

    c.toBlob(
      (z) => {
        if (!z) {
          return;
        }

        done(
          new File(
            [z],
            "irl-clash.jpg",
            {
              type: "image/jpeg",
            }
          )
        );
      },
      "image/jpeg",
      0.82
    );
  }

  return (
    <div className="modal">
      <div className="cam">
        <button onClick={close}>
          <X />
        </button>

        <video
          ref={v}
          autoPlay
          playsInline
          muted
        />

        <button
          className="shutter"
          disabled={!ready}
          onClick={snap}
        >
          <Camera />
        </button>
      </div>
    </div>
  );
}

function Results({ b, p, again }) {
  const me = b.playerResults?.find(
    (x) => x.playerId === p.playerId
  );

  const op = b.playerResults?.find(
    (x) => x.playerId !== p.playerId
  );

  const win = b.winnerPlayerId === p.playerId;

  return (
    <section className="page results">
      <div className="win">
        <Trophy />

        <small>
          {win ? "BATTLE WON" : "BATTLE COMPLETE"}
        </small>

        <h1>
          {win
            ? "YOU CONQUERED IT!"
            : `${b.winnerUsername} wins.`}
        </h1>

        <p>{b.message}</p>
      </div>

      <div className="scores">
        <Score x={me} hi={win} />

        <b>VS</b>

        <Score x={op} hi={!win} />
      </div>

      <button onClick={again}>
        Battle again <RefreshCw />
      </button>

      <h2>AI referee breakdown</h2>

      <div className="reviews">
        {b.playerResults?.map((x) => (
          <div className="card" key={x.playerId}>
            <h3>
              {x.username} — {x.totalScore}/500
            </h3>

            {x.images?.map((i) => (
              <article key={i.round}>
                <b>
                  Round {i.round}: {i.score}/100
                </b>

                <p>{i.description}</p>

                <small>{i.reason}</small>
              </article>
            ))}
          </div>
        ))}
      </div>
    </section>
  );
}

function Score({ x, hi }) {
  return (
    <div className={hi ? "score hi" : "score"}>
      <span>{x?.username}</span>

      <strong>{x?.totalScore || 0}</strong>

      <small>/500</small>
    </div>
  );
}

function Error({ message }) {
  return (
    <div className="error">
      {message ||
        "Something went wrong. Check the values and try again."}
    </div>
  );
}

createRoot(document.getElementById("root")).render(<App />);