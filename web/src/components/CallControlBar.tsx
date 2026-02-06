import { useEffect, useMemo, useState } from "react";
import "./CallControlBar.css";

type Props = {
  localAudioTrack: MediaStreamTrack | null;
  localVideoTrack: MediaStreamTrack | null;
  isRemoteAudioMuted: boolean;
  isRemoteVideoMuted: boolean;
  onToggleAudio: (nextEnabled: boolean) => void;
  onToggleVideo: (nextEnabled: boolean) => void;
  onHangUp: () => void;
};

const isEditableTarget = (target: EventTarget | null) => {
  if (!(target instanceof HTMLElement)) {
    return false;
  }
  const tag = target.tagName.toLowerCase();
  return (
    tag === "input" ||
    tag === "textarea" ||
    target.isContentEditable ||
    target.getAttribute("contenteditable") === "true"
  );
};

const vibrate = () => {
  if ("vibrate" in navigator) {
    navigator.vibrate(10);
  }
};

export default function CallControlBar({
  localAudioTrack,
  localVideoTrack,
  isRemoteAudioMuted,
  isRemoteVideoMuted,
  onToggleAudio,
  onToggleVideo,
  onHangUp
}: Props) {
  const audioEnabled = localAudioTrack?.enabled ?? false;
  const videoEnabled = localVideoTrack?.enabled ?? false;
  const [announcement, setAnnouncement] = useState("");

  const remoteStatus = useMemo(() => {
    const parts = [];
    if (isRemoteAudioMuted) {
      parts.push("Remote mic muted");
    }
    if (isRemoteVideoMuted) {
      parts.push("Remote camera off");
    }
    return parts.length ? parts.join(", ") : "Remote audio and video active";
  }, [isRemoteAudioMuted, isRemoteVideoMuted]);

  useEffect(() => {
    const parts = [
      audioEnabled ? "Microphone on" : "Microphone muted",
      videoEnabled ? "Camera on" : "Camera off",
      remoteStatus
    ];
    setAnnouncement(parts.join(". "));
  }, [audioEnabled, videoEnabled, remoteStatus]);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (isEditableTarget(event.target)) {
        return;
      }
      const key = event.key.toLowerCase();
      const metaPressed = event.metaKey || event.ctrlKey;
      if (metaPressed && key === "e") {
        event.preventDefault();
        vibrate();
        onToggleAudio(!audioEnabled);
        return;
      }
      if (metaPressed && key === "d") {
        event.preventDefault();
        vibrate();
        onToggleVideo(!videoEnabled);
        return;
      }
      if (key === "escape") {
        event.preventDefault();
        vibrate();
        onHangUp();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [audioEnabled, videoEnabled, onHangUp, onToggleAudio, onToggleVideo]);

  return (
    <div className="control-bar" aria-live="off">
      <div className="sr-only" aria-live="polite">
        {announcement}
      </div>
      <div className="control-bar__remote" aria-label={remoteStatus}>
        <span className={isRemoteAudioMuted ? "pill pill--warn" : "pill"}>
          {isRemoteAudioMuted ? "Remote mic muted" : "Remote mic on"}
        </span>
        <span className={isRemoteVideoMuted ? "pill pill--warn" : "pill"}>
          {isRemoteVideoMuted ? "Remote camera off" : "Remote camera on"}
        </span>
      </div>
      <div className="control-bar__buttons" role="toolbar" aria-label="Call controls">
        <button
          type="button"
          className={audioEnabled ? "control-button" : "control-button control-button--active"}
          aria-pressed={!audioEnabled}
          aria-label={audioEnabled ? "Mute microphone" : "Unmute microphone"}
          onClick={() => {
            vibrate();
            onToggleAudio(!audioEnabled);
          }}
          data-testid="toggle-mic"
        >
          {audioEnabled ? "Mic On" : "Mic Muted"}
        </button>
        <button
          type="button"
          className={videoEnabled ? "control-button" : "control-button control-button--active"}
          aria-pressed={!videoEnabled}
          aria-label={videoEnabled ? "Turn camera off" : "Turn camera on"}
          onClick={() => {
            vibrate();
            onToggleVideo(!videoEnabled);
          }}
          data-testid="toggle-camera"
        >
          {videoEnabled ? "Camera On" : "Camera Off"}
        </button>
        <button
          type="button"
          className="control-button control-button--danger"
          aria-label="Hang up call"
          onClick={() => {
            vibrate();
            onHangUp();
          }}
          data-testid="hang-up"
        >
          Hang Up
        </button>
      </div>
    </div>
  );
}
