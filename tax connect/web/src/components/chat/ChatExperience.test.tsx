import { render, screen, fireEvent, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ChatExperience from "./ChatExperience";

describe("ChatExperience", () => {
  it("renders the input and counter", () => {
    render(<ChatExperience />);
    expect(screen.getByLabelText("Message input")).toBeInTheDocument();
    expect(screen.getByText("0/5000")).toBeInTheDocument();
  });

  it("inserts mentions from autocomplete", async () => {
    const user = userEvent.setup();
    render(<ChatExperience />);
    const input = screen.getByLabelText("Message input");
    await act(async () => {
      await user.type(input, "Hello @Ma");
    });
    expect(screen.getByRole("listbox")).toBeInTheDocument();
    await act(async () => {
      await user.click(screen.getByRole("option", { name: "@Maya" }));
    });
    expect((input as HTMLTextAreaElement).value).toContain("@Maya ");
  });

  it("adds emoji from picker", async () => {
    const user = userEvent.setup();
    render(<ChatExperience />);
    await act(async () => {
      await user.click(screen.getByRole("button", { name: "Emoji" }));
    });
    await act(async () => {
      await user.click(screen.getByRole("button", { name: "😀" }));
    });
    expect((screen.getByLabelText("Message input") as HTMLTextAreaElement).value).toContain(
      "😀"
    );
  });

  it("closes overlays on Escape", async () => {
    const user = userEvent.setup();
    render(<ChatExperience />);
    await act(async () => {
      await user.click(screen.getByRole("button", { name: "Emoji" }));
    });
    const input = screen.getByLabelText("Message input");
    await act(async () => {
      await user.type(input, "{Escape}");
    });
    expect(screen.queryByRole("dialog", { name: "Emoji picker" })).not.toBeInTheDocument();
  });

  it("sends on Ctrl+Enter and clears input", async () => {
    render(<ChatExperience />);
    const input = screen.getByLabelText("Message input");
    act(() => {
      fireEvent.change(input, { target: { value: "Ready to send" } });
    });
    act(() => {
      fireEvent.keyDown(input, { key: "Enter", ctrlKey: true });
    });
    expect((input as HTMLTextAreaElement).value).toBe("");
    expect(screen.getByText("Ready to send")).toBeInTheDocument();
  });

  it("limits input to 5000 characters", () => {
    render(<ChatExperience />);
    const input = screen.getByLabelText("Message input");
    const longText = "a".repeat(5100);
    act(() => {
      fireEvent.change(input, { target: { value: longText } });
    });
    expect((input as HTMLTextAreaElement).value.length).toBe(5000);
  });

  it("adds dropped files as attachments", () => {
    render(<ChatExperience />);
    const inputArea = screen.getByTestId("chat-input");
    const file = new File(["content"], "tax.pdf", { type: "application/pdf" });
    act(() => {
      fireEvent.drop(inputArea, { dataTransfer: { files: [file] } });
    });
    expect(screen.getByText("tax.pdf")).toBeInTheDocument();
  });

});
