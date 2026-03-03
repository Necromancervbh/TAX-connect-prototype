import { useEffect, useMemo, useState } from "react";
import CallControlBar from "./components/CallControlBar";
import ChatExperience from "./components/chat/ChatExperience";
import "./App.css";

type CallPhase = "idle" | "incoming" | "active";
type ThemeMode = "light" | "dark" | "system";

const createTrack = (enabled: boolean) =>
  ({
    enabled
  } as MediaStreamTrack);

export default function App() {
  const [phase, setPhase] = useState<CallPhase>("idle");
  const [audioEnabled, setAudioEnabled] = useState(true);
  const [videoEnabled, setVideoEnabled] = useState(true);
  const [remoteAudioMuted, setRemoteAudioMuted] = useState(false);
  const [remoteVideoMuted, setRemoteVideoMuted] = useState(false);
  const [theme, setTheme] = useState<ThemeMode>("system");

  const audioTrack = useMemo(() => createTrack(audioEnabled), [audioEnabled]);
  const videoTrack = useMemo(() => createTrack(videoEnabled), [videoEnabled]);

  const startIncoming = () => {
    setPhase("incoming");
    setRemoteAudioMuted(false);
    setRemoteVideoMuted(false);
  };

  const answerCall = () => {
    setPhase("active");
    setAudioEnabled(true);
    setVideoEnabled(true);
  };

  const declineCall = () => {
    setPhase("idle");
  };

  const hangUp = () => {
    setPhase("idle");
  };

  useEffect(() => {
    const stored = window.localStorage.getItem("theme");
    if (stored === "light" || stored === "dark" || stored === "system") {
      setTheme(stored);
    }
  }, []);

  useEffect(() => {
    const media = window.matchMedia?.("(prefers-color-scheme: dark)");
    const computeResolved = () => {
      if (theme === "system") {
        return media?.matches ? "dark" : "light";
      }
      return theme;
    };
    const applyResolved = () => {
      const next = computeResolved();
      document.body.setAttribute("data-theme", next);
    };
    applyResolved();
    media?.addEventListener("change", applyResolved);
    return () => media?.removeEventListener("change", applyResolved);
  }, [theme]);

  useEffect(() => {
    window.localStorage.setItem("theme", theme);
  }, [theme]);

  return (
    <div className="page">
      <header className="hero">
        <div className="hero__content">
          <div className="eyebrow">TaxConnect</div>
          <h1>Communication workspace</h1>
          <p>Coordinate consultations with real-time calls and modern messaging.</p>
        </div>
        <div className="hero__status">
          <div className="theme-toggle" data-testid="toggle-theme">
            <button
              className={`ghost ${theme === "light" ? "ghost--active" : ""}`}
              onClick={() => setTheme("light")}
            >
              Light
            </button>
            <button
              className={`ghost ${theme === "dark" ? "ghost--active" : ""}`}
              onClick={() => setTheme("dark")}
            >
              Dark
            </button>
            <button
              className={`ghost ${theme === "system" ? "ghost--active" : ""}`}
              onClick={() => setTheme("system")}
            >
              System
            </button>
          </div>
          <span className={`chip chip--${phase}`}>{phase.toUpperCase()}</span>
        </div>
      </header>

      <main className="content">
        <section className="summary">
          <div className="summary__card">
            <div className="summary__label">Call status</div>
            <div className="summary__value">
              {phase === "idle" ? "Standby" : phase === "incoming" ? "Ringing" : "Live"}
            </div>
            <div className="summary__meta">
              {phase === "active" ? "Session in progress" : "Awaiting next action"}
            </div>
          </div>
          <div className="summary__card">
            <div className="summary__label">Local controls</div>
            <div className="summary__value">
              {audioEnabled ? "Mic on" : "Mic off"} · {videoEnabled ? "Cam on" : "Cam off"}
            </div>
            <div className="summary__meta">Match the consultation state</div>
          </div>
          <div className="summary__card">
            <div className="summary__label">Remote status</div>
            <div className="summary__value">
              {remoteAudioMuted ? "Client muted" : "Client audio"} ·{" "}
              {remoteVideoMuted ? "Video off" : "Video on"}
            </div>
            <div className="summary__meta">Visibility updates in real time</div>
          </div>
        </section>

        <section className="card">
          <div className="card__header">
            <div>
              <h2>Live session</h2>
              <p>Run the next step of the consultation flow.</p>
            </div>
          </div>

          {phase === "idle" && (
            <div className="actions">
              <button
                className="primary"
                onClick={startIncoming}
                data-testid="simulate-notification"
              >
                Simulate notification
              </button>
            </div>
          )}

          {phase === "incoming" && (
            <div className="incoming">
              <div className="incoming-text">Incoming call from client</div>
              <div className="incoming-actions">
                <button
                  className="primary"
                  onClick={answerCall}
                  data-testid="answer-call"
                >
                  Answer
                </button>
                <button
                  className="secondary"
                  onClick={declineCall}
                  data-testid="decline-call"
                >
                  Decline
                </button>
              </div>
            </div>
          )}

          {phase === "active" && (
            <div className="active">
              <div className="remote-state">
                <button
                  className="ghost"
                  onClick={() => setRemoteAudioMuted((prev) => !prev)}
                  data-testid="toggle-remote-mic"
                >
                  Toggle remote mic
                </button>
                <button
                  className="ghost"
                  onClick={() => setRemoteVideoMuted((prev) => !prev)}
                  data-testid="toggle-remote-camera"
                >
                  Toggle remote camera
                </button>
              </div>
              <CallControlBar
                localAudioTrack={audioTrack}
                localVideoTrack={videoTrack}
                isRemoteAudioMuted={remoteAudioMuted}
                isRemoteVideoMuted={remoteVideoMuted}
                onToggleAudio={(next) => setAudioEnabled(next)}
                onToggleVideo={(next) => setVideoEnabled(next)}
                onHangUp={hangUp}
              />
            </div>
          )}
        </section>

        <section className="card">
          <div className="card__header">
            <div>
              <h2>Guidance</h2>
              <p>Keep sessions aligned with the in-app workflow.</p>
            </div>
          </div>
          <div className="guidance">
            <div className="guidance__item">
              <div className="guidance__title">Negotiation</div>
              <div className="guidance__text">
                Confirm scope, deliverables, and timeline before answering.
              </div>
            </div>
            <div className="guidance__item">
              <div className="guidance__title">Agreement</div>
              <div className="guidance__text">
                Share the agreement PDF and capture client confirmation.
              </div>
            </div>
            <div className="guidance__item">
              <div className="guidance__title">Payment</div>
              <div className="guidance__text">
                Collect the advance before work begins, then release escrow on
                milestone completion.
              </div>
            </div>
          </div>
        </section>
        <section className="card card--chat">
          <div className="card__header">
            <div>
              <h2>Messages</h2>
              <p>Deliver fast, accessible, and responsive conversations.</p>
            </div>
          </div>
          <ChatExperience />
        </section>
      </main>
    </div>
  );
}
