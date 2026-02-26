import type { Meta, StoryObj } from "@storybook/react";
import ChatExperience from "./ChatExperience";

const meta: Meta<typeof ChatExperience> = {
  title: "Chat/ChatExperience",
  component: ChatExperience,
  parameters: {
    layout: "fullscreen"
  }
};

export default meta;

type Story = StoryObj<typeof ChatExperience>;

export const Default: Story = {};
