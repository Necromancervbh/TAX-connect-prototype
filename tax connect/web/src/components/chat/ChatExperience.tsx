import { useEffect, useMemo, useRef, useState } from "react";
import "./ChatExperience.css";

type MessageStatus = "sending" | "sent" | "delivered" | "read";

type Message = {
  id: string;
  author: string;
  text: string;
  time: string;
  status: MessageStatus;
  direction: "incoming" | "outgoing";
};

const rowHeight = 88;
const overscan = 8;
const maxLength = 5000;

const emojiOptions = ["😀", "🎉", "✅", "🔥", "🙌", "💡", "🚀", "🤝"];
const mentionOptions = ["Aarav", "Maya", "Liam", "Zara", "Noah", "Priya"];
const threadNames = ["Acme Tax Review", "Q4 Planning", "Annual Filing", "Refund Support"];

const statusLabel = (status: MessageStatus) => {
  if (status === "sending") return "Sending";
  if (status === "sent") return "Sent";
  if (status === "delivered") return "Delivered";
  return "Read";
};

const formatTime = (index: number) => {
  const hour = 9 + (index % 9);
  const minute = (index * 7) % 60;
  const suffix = hour >= 12 ? "PM" : "AM";
  const hour12 = hour > 12 ? hour - 12 : hour;
  return `${hour12}:${minute.toString().padStart(2, "0")} ${suffix}`;
};

const createMessages = (count: number): Message[] =>
  Array.from({ length: count }).map((_, index) => {
    const outgoing = index % 3 === 0;
    const status: MessageStatus = outgoing
      ? index % 4 === 0
        ? "read"
        : index % 3 === 0
          ? "delivered"
          : "sent"
      : "read";
    return {
      id: `msg-${index}`,
      author: outgoing ? "You" : "Client",
      text: outgoing
        ? "Shared the checklist and next steps for the filing."
        : "Thanks, I will review the checklist and confirm any questions.",
      time: formatTime(index),
      status,
      direction: outgoing ? "outgoing" : "incoming"
    };
  });

export default function ChatExperience() {
  const initialMessageCount = 10000;
  const [messages, setMessages] = useState<Message[]>(() =>
    createMessages(initialMessageCount)
  );
  const [inputValue, setInputValue] = useState("");
  const [emojiOpen, setEmojiOpen] = useState(false);
  const [mentionQuery, setMentionQuery] = useState("");
  const [mentionIndex, setMentionIndex] = useState(0);
  const [isDragging, setIsDragging] = useState(false);
  const [attachments, setAttachments] = useState<string[]>([]);
  const [scrollTop, setScrollTop] = useState(() =>
    Math.max(0, initialMessageCount * rowHeight - 480)
  );
  const [viewportHeight, setViewportHeight] = useState(480);
  const [unreadCount, setUnreadCount] = useState(0);
  const [remoteTyping, setRemoteTyping] = useState(false);
  const listRef = useRef<HTMLDivElement | null>(null);
  const inputRef = useRef<HTMLTextAreaElement | null>(null);
  const lastMessageCount = useRef(messages.length);

  const totalHeight = messages.length * rowHeight;
  const isAtBottom = scrollTop + viewportHeight >= totalHeight - rowHeight;

  const visibleRange = useMemo(() => {
    const start = Math.max(0, Math.floor(scrollTop / rowHeight) - overscan);
    const end = Math.min(
      messages.length - 1,
      Math.ceil((scrollTop + viewportHeight) / rowHeight) + overscan
    );
    return { start, end };
  }, [scrollTop, viewportHeight, messages.length]);

  const visibleMessages = useMemo(
    () => messages.slice(visibleRange.start, visibleRange.end + 1),
    [messages, visibleRange]
  );

  useEffect(() => {
    const update = () => {
      if (listRef.current) {
        const height = listRef.current.clientHeight;
        setViewportHeight(height || 480);
      }
    };
    update();
    window.addEventListener("resize", update);
    return () => window.removeEventListener("resize", update);
  }, []);

  useEffect(() => {
    if (listRef.current) {
      listRef.current.scrollTop = scrollTop;
    }
  }, []);

  const scrollToBottom = () => {
    if (listRef.current) {
      const next = Math.max(0, totalHeight - viewportHeight);
      listRef.current.scrollTop = next;
      setScrollTop(next);
    }
  };

  useEffect(() => {
    const current = messages.length;
    if (current !== lastMessageCount.current) {
      if (isAtBottom) {
        requestAnimationFrame(() => {
          scrollToBottom();
        });
      } else {
        setUnreadCount((prev) => prev + (current - lastMessageCount.current));
      }
      lastMessageCount.current = current;
    }
  }, [messages.length, isAtBottom, totalHeight]);

  useEffect(() => {
    if (isAtBottom) {
      setUnreadCount(0);
    }
  }, [isAtBottom]);

  const handleScroll = () => {
    if (!listRef.current) return;
    setScrollTop(listRef.current.scrollTop);
  };

  const handleInputChange = (value: string) => {
    const nextValue = value.slice(0, maxLength);
    setInputValue(nextValue);
    const caret = inputRef.current?.selectionStart ?? nextValue.length;
    const prefix = nextValue.slice(0, caret);
    const match = prefix.match(/@([a-zA-Z0-9_]*)$/);
    if (match) {
      setMentionQuery(match[1]);
      setMentionIndex(0);
    } else {
      setMentionQuery("");
    }
  };

  const mentionResults = useMemo(() => {
    if (!mentionQuery) return [];
    return mentionOptions.filter((name) =>
      name.toLowerCase().startsWith(mentionQuery.toLowerCase())
    );
  }, [mentionQuery]);

  const insertAtCursor = (insertText: string) => {
    const input = inputRef.current;
    if (!input) {
      setInputValue((prev) => prev + insertText);
      return;
    }
    const start = input.selectionStart ?? inputValue.length;
    const end = input.selectionEnd ?? inputValue.length;
    const updated = `${inputValue.slice(0, start)}${insertText}${inputValue.slice(end)}`;
    setInputValue(updated.slice(0, maxLength));
    requestAnimationFrame(() => {
      const nextPos = start + insertText.length;
      input.focus();
      input.setSelectionRange(nextPos, nextPos);
    });
  };

  const applyMention = (name: string) => {
    const input = inputRef.current;
    const caret = input?.selectionStart ?? inputValue.length;
    const prefix = inputValue.slice(0, caret);
    const suffix = inputValue.slice(caret);
    const nextPrefix = prefix.replace(/@([a-zA-Z0-9_]*)$/, `@${name} `);
    const nextValue = `${nextPrefix}${suffix}`.slice(0, maxLength);
    setInputValue(nextValue);
    setMentionQuery("");
    requestAnimationFrame(() => {
      const nextPos = nextPrefix.length;
      input?.focus();
      input?.setSelectionRange(nextPos, nextPos);
    });
  };

  const sendMessage = () => {
    if (!inputValue.trim()) return;
    const newMessage: Message = {
      id: `out-${Date.now()}`,
      author: "You",
      text: inputValue.trim(),
      time: formatTime(messages.length + 1),
      status: "sending",
      direction: "outgoing"
    };
    setMessages((prev) => [...prev, newMessage]);
    setInputValue("");
    setEmojiOpen(false);
    setMentionQuery("");
    setAttachments([]);
    setRemoteTyping(true);
    setTimeout(() => {
      setMessages((prev) =>
        prev.map((message) =>
          message.id === newMessage.id ? { ...message, status: "sent" } : message
        )
      );
    }, 600);
    setTimeout(() => {
      setMessages((prev) =>
        prev.map((message) =>
          message.id === newMessage.id ? { ...message, status: "delivered" } : message
        )
      );
    }, 1200);
    setTimeout(() => {
      setMessages((prev) =>
        prev.map((message) =>
          message.id === newMessage.id ? { ...message, status: "read" } : message
        )
      );
      setRemoteTyping(false);
      setMessages((prev) => [
        ...prev,
        {
          id: `in-${Date.now()}`,
          author: "Client",
          text: "Got it. I will review and confirm the updated document.",
          time: formatTime(prev.length + 1),
          status: "read",
          direction: "incoming"
        }
      ]);
    }, 2000);
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Escape") {
      setEmojiOpen(false);
      setMentionQuery("");
      return;
    }
    if (event.key === "Enter" && (event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      sendMessage();
      return;
    }
    if (mentionResults.length > 0) {
      if (event.key === "ArrowDown") {
        event.preventDefault();
        setMentionIndex((prev) => (prev + 1) % mentionResults.length);
      }
      if (event.key === "ArrowUp") {
        event.preventDefault();
        setMentionIndex((prev) => (prev - 1 + mentionResults.length) % mentionResults.length);
      }
      if (event.key === "Enter" && !event.ctrlKey && !event.metaKey) {
        event.preventDefault();
        applyMention(mentionResults[mentionIndex]);
      }
    }
  };

  const handleDrop = (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    const files = Array.from(event.dataTransfer.files).map((file) => file.name);
    if (files.length) {
      setAttachments(files.slice(0, 5));
    }
    setIsDragging(false);
  };

  return (
    <div className="chat-shell">
      <aside className="chat-sidebar" aria-label="Conversation list">
        <div className="chat-sidebar__header">
          <div>
            <div className="chat-sidebar__title">Active threads</div>
            <div className="chat-sidebar__subtitle">4 open requests</div>
          </div>
          <button className="ghost chat-sidebar__action" type="button">
            New
          </button>
        </div>
        <div className="chat-sidebar__list">
          {threadNames.map((name, index) => (
            <button
              key={name}
              className={`chat-sidebar__item ${index === 0 ? "is-active" : ""}`}
              type="button"
              aria-current={index === 0 ? "true" : "false"}
            >
              <div className="chat-sidebar__item-title">{name}</div>
              <div className="chat-sidebar__item-meta">Updated 2m ago</div>
            </button>
          ))}
        </div>
      </aside>
      <section className="chat-main">
        <header className="chat-header">
          <div className="chat-header__title">
            <div className="chat-header__name">Acme Tax Review</div>
            <div className="chat-header__status">
              <span className="presence-dot" aria-hidden="true" />
              Client online
            </div>
          </div>
          <div className="chat-header__actions">
            <button className="ghost" type="button">
              Search
            </button>
            <button className="ghost" type="button">
              Info
            </button>
          </div>
        </header>
        <div className="chat-list">
          <div className="chat-list__scroller" onScroll={handleScroll} ref={listRef} data-testid="message-list">
            <div className="chat-list__spacer" style={{ height: totalHeight }}>
              {visibleMessages.map((message, index) => {
                const messageIndex = visibleRange.start + index;
                const offset = messageIndex * rowHeight;
                return (
                  <div
                    key={message.id}
                    className={`chat-message ${message.direction}`}
                    style={{ top: offset }}
                  >
                    <div className="chat-message__bubble">
                      <div className="chat-message__meta">
                        <span className="chat-message__author">{message.author}</span>
                        <span className="chat-message__time">{message.time}</span>
                      </div>
                      <div className="chat-message__text">{message.text}</div>
                    </div>
                    {message.direction === "outgoing" && (
                      <div
                        className={`chat-message__status status--${message.status}`}
                        aria-label={`Message status: ${statusLabel(message.status)}`}
                      >
                        <span className="status-dot" aria-hidden="true" />
                        <span className="status-text">{statusLabel(message.status)}</span>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
          {remoteTyping && (
            <div className="typing-indicator" role="status" aria-live="polite">
              Client is typing…
            </div>
          )}
          {unreadCount > 0 && (
            <button
              type="button"
              className="unread-banner"
              onClick={scrollToBottom}
              data-testid="unread-banner"
            >
              {unreadCount} new message{unreadCount > 1 ? "s" : ""} · Jump to latest
            </button>
          )}
        </div>
        <div
          className={`chat-input ${isDragging ? "is-dragging" : ""}`}
          onDragEnter={(event) => {
            event.preventDefault();
            setIsDragging(true);
          }}
          onDragOver={(event) => event.preventDefault()}
          onDragLeave={() => setIsDragging(false)}
          onDrop={handleDrop}
          data-testid="chat-input"
        >
          <label className="sr-only" htmlFor="message-input">
            Message input
          </label>
          <textarea
            id="message-input"
            ref={inputRef}
            value={inputValue}
            placeholder="Write a message, use @ to mention"
            onChange={(event) => handleInputChange(event.target.value)}
            onKeyDown={handleKeyDown}
            aria-label="Message input"
            aria-expanded={emojiOpen || mentionResults.length > 0}
            aria-controls="mention-list"
          />
          <div className="chat-input__toolbar">
            <div className="chat-input__left">
              <button
                type="button"
                className="ghost"
                onClick={() => setEmojiOpen((prev) => !prev)}
                aria-expanded={emojiOpen}
              >
                Emoji
              </button>
              <button type="button" className="ghost">
                Attach
              </button>
            </div>
            <div className="chat-input__right">
              <div className="char-counter" aria-live="polite">
                {inputValue.length}/{maxLength}
              </div>
              <button type="button" className="primary" onClick={sendMessage}>
                Send
              </button>
            </div>
          </div>
          {mentionResults.length > 0 && (
            <div className="mention-list" role="listbox" id="mention-list">
              {mentionResults.map((name, index) => (
                <button
                  type="button"
                  key={name}
                  className={`mention-item ${index === mentionIndex ? "is-active" : ""}`}
                  onClick={() => applyMention(name)}
                  role="option"
                  aria-selected={index === mentionIndex}
                >
                  @{name}
                </button>
              ))}
            </div>
          )}
          {emojiOpen && (
            <div className="emoji-panel" role="dialog" aria-label="Emoji picker">
              {emojiOptions.map((emoji) => (
                <button
                  type="button"
                  key={emoji}
                  className="emoji-button"
                  onClick={() => insertAtCursor(emoji)}
                >
                  {emoji}
                </button>
              ))}
            </div>
          )}
          {attachments.length > 0 && (
            <div className="attachment-list" aria-live="polite">
              {attachments.map((file) => (
                <div key={file} className="attachment-item">
                  {file}
                </div>
              ))}
            </div>
          )}
          {isDragging && (
            <div className="drop-overlay" role="status" aria-live="polite">
              Drop files to attach
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
