import { useMemo, useState } from "react";
import CallControlBar from "./components/CallControlBar";
import "./App.css";

type CallPhase = "idle" | "incoming" | "active";

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

  return (
    <div className="app">
      <h1>Video Call Controls</h1>
      {phase === "idle" && (
        <button
          className="primary"
          onClick={startIncoming}
          data-testid="simulate-notification"
        >
          Simulate Notification
        </button>
      )}
      {phase === "incoming" && (
        <div className="incoming">
          <div className="incoming-text">Incoming call</div>
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
              className="secondary"
              onClick={() => setRemoteAudioMuted((prev) => !prev)}
              data-testid="toggle-remote-mic"
            >
              Toggle Remote Mic
            </button>
            <button
              className="secondary"
              onClick={() => setRemoteVideoMuted((prev) => !prev)}
              data-testid="toggle-remote-camera"
            >
              Toggle Remote Camera
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
    </div>
  );
}
