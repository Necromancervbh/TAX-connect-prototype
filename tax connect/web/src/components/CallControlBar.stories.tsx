import type { Meta, StoryObj } from "@storybook/react";
import CallControlBar from "./CallControlBar";

const createTrack = (enabled: boolean) =>
  ({
    enabled
  } as MediaStreamTrack);

const meta: Meta<typeof CallControlBar> = {
  title: "Call/CallControlBar",
  component: CallControlBar,
  args: {
    localAudioTrack: createTrack(true),
    localVideoTrack: createTrack(true),
    isRemoteAudioMuted: false,
    isRemoteVideoMuted: false
  },
  argTypes: {
    onToggleAudio: { action: "toggleAudio" },
    onToggleVideo: { action: "toggleVideo" },
    onHangUp: { action: "hangUp" }
  }
};

export default meta;

type Story = StoryObj<typeof CallControlBar>;

export const Default: Story = {};

export const MutedAudio: Story = {
  args: {
    localAudioTrack: createTrack(false)
  }
};

export const VideoOff: Story = {
  args: {
    localVideoTrack: createTrack(false)
  }
};

export const RemoteMuted: Story = {
  args: {
    isRemoteAudioMuted: true,
    isRemoteVideoMuted: true
  }
};
