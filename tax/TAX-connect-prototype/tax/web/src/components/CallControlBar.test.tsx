import { render, screen, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi } from "vitest";
import CallControlBar from "./CallControlBar";

const createTrack = (enabled: boolean) =>
  ({
    enabled
  } as MediaStreamTrack);

describe("CallControlBar", () => {
  it("renders active states and announces status", () => {
    render(
      <CallControlBar
        localAudioTrack={createTrack(true)}
        localVideoTrack={createTrack(true)}
        isRemoteAudioMuted={false}
        isRemoteVideoMuted={false}
        onToggleAudio={vi.fn()}
        onToggleVideo={vi.fn()}
        onHangUp={vi.fn()}
      />
    );

    expect(screen.getByText("Mic On")).toBeInTheDocument();
    expect(screen.getByText("Camera On")).toBeInTheDocument();
    expect(screen.getByText("Remote mic on")).toBeInTheDocument();
    expect(screen.getByText("Remote camera on")).toBeInTheDocument();
    expect(
      screen.getByText("Microphone on. Camera on. Remote audio and video active")
    ).toBeInTheDocument();
  });

  it("toggles audio and video on click", async () => {
    const onToggleAudio = vi.fn();
    const onToggleVideo = vi.fn();
    const user = userEvent.setup();

    render(
      <CallControlBar
        localAudioTrack={createTrack(true)}
        localVideoTrack={createTrack(true)}
        isRemoteAudioMuted={false}
        isRemoteVideoMuted={false}
        onToggleAudio={onToggleAudio}
        onToggleVideo={onToggleVideo}
        onHangUp={vi.fn()}
      />
    );

    await user.click(screen.getByTestId("toggle-mic"));
    await user.click(screen.getByTestId("toggle-camera"));

    expect(onToggleAudio).toHaveBeenCalledWith(false);
    expect(onToggleVideo).toHaveBeenCalledWith(false);
  });

  it("handles hang up with vibration support", async () => {
    const onHangUp = vi.fn();
    const vibrateMock = vi.fn();
    const navigatorAny = navigator as Navigator & { vibrate?: (pattern: number) => boolean };

    Object.defineProperty(navigator, "vibrate", {
      value: vibrateMock,
      configurable: true
    });

    const user = userEvent.setup();

    render(
      <CallControlBar
        localAudioTrack={createTrack(true)}
        localVideoTrack={createTrack(true)}
        isRemoteAudioMuted={false}
        isRemoteVideoMuted={false}
        onToggleAudio={vi.fn()}
        onToggleVideo={vi.fn()}
        onHangUp={onHangUp}
      />
    );

    await user.click(screen.getByTestId("hang-up"));

    expect(onHangUp).toHaveBeenCalled();
    expect(vibrateMock).toHaveBeenCalledWith(10);

    delete navigatorAny.vibrate;
  });

  it("responds to keyboard shortcuts and escape", () => {
    const onToggleAudio = vi.fn();
    const onToggleVideo = vi.fn();
    const onHangUp = vi.fn();

    render(
      <CallControlBar
        localAudioTrack={createTrack(false)}
        localVideoTrack={createTrack(false)}
        isRemoteAudioMuted={true}
        isRemoteVideoMuted={true}
        onToggleAudio={onToggleAudio}
        onToggleVideo={onToggleVideo}
        onHangUp={onHangUp}
      />
    );

    fireEvent.keyDown(window, { key: "e", metaKey: true });
    fireEvent.keyDown(window, { key: "d", ctrlKey: true });
    fireEvent.keyDown(window, { key: "Escape" });

    expect(onToggleAudio).toHaveBeenCalledWith(true);
    expect(onToggleVideo).toHaveBeenCalledWith(true);
    expect(onHangUp).toHaveBeenCalled();
    expect(screen.getByText("Remote mic muted")).toBeInTheDocument();
    expect(screen.getByText("Remote camera off")).toBeInTheDocument();
  });

  it("ignores shortcuts when typing in inputs", () => {
    const onToggleAudio = vi.fn();

    render(
      <div>
        <input aria-label="Chat input" />
        <textarea aria-label="Message input" />
        <div aria-label="Rich input" contentEditable />
        <CallControlBar
          localAudioTrack={null}
          localVideoTrack={null}
          isRemoteAudioMuted={false}
          isRemoteVideoMuted={false}
          onToggleAudio={onToggleAudio}
          onToggleVideo={vi.fn()}
          onHangUp={vi.fn()}
        />
      </div>
    );

    const input = screen.getByLabelText("Chat input");
    const textarea = screen.getByLabelText("Message input");
    const editable = screen.getByLabelText("Rich input");
    fireEvent.keyDown(input, { key: "e", metaKey: true });
    fireEvent.keyDown(textarea, { key: "e", metaKey: true });
    fireEvent.keyDown(editable, { key: "e", ctrlKey: true });

    expect(onToggleAudio).not.toHaveBeenCalled();
  });
});
