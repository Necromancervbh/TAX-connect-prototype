---
description: Navigation map — all entry points for each major screen in the app
---

# Navigation Workflow

## Client App (`HomeActivity`)

### Bottom Navigation (`bottom_nav_menu_client.xml`)
| Tab | Destination |
|---|---|
| Home | `HomeActivity` (fragment host) |
| Bookings | `MyBookingsActivity` |
| Chats | `MyChatsActivity` |
| Profile | `ProfileActivity` |

### Toolbar Icons
| Icon | Destination |
|---|---|
| 🔔 `ivNotifications` | `NotificationHistoryActivity` |
| 💬 `ivMyChats` | `MyChatsActivity` |

### Side Drawer (`side_menu.xml`)
| Menu Item | Destination |
|---|---|
| nav_home | Home |
| nav_bookings | `MyBookingsActivity` |
| nav_chats | `MyChatsActivity` |
| nav_notifications | `NotificationHistoryActivity` |
| nav_settings | `NotificationSettingsActivity` |
| nav_profile | `ProfileActivity` |

### FAB
Opens `ExploreCAsActivity` → find CAs → `CADetailActivity` → `BookAppointmentActivity`

---

## CA Dashboard (`CADashboardActivity`)

### Bottom Navigation (`bottom_nav_menu.xml`)
| Tab | Destination |
|---|---|
| Home | Dashboard |
| Bookings | `MyBookingsActivity` |
| Chats | `MyChatsActivity` |
| Profile | `ProfileActivity` |

### Toolbar / Header
| Icon | Destination |
|---|---|
| 🔔 `ivNotifications` | `NotificationHistoryActivity` |

### Side Drawer
Same items as Client + nav_notifications + nav_settings

---

## Key Entry Paths to ChatActivity
ChatActivity can be launched with 3 different intent patterns:
```kotlin
// From booking acceptance (has bookingId)
Intent(context, ChatActivity::class.java).apply {
    putExtra("chatId", chatId)
    putExtra("otherUserId", booking.userId)
    putExtra("otherUserName", booking.userName)
    putExtra("bookingId", booking.id)
}

// From chat list (chatId known, otherUserId known)
Intent(context, ChatActivity::class.java).apply {
    putExtra("chatId", chatId)
    putExtra("otherUserId", otherUserId)
    putExtra("otherUserName", otherUserName)
}

// From notification (only chatId known)
Intent(context, ChatActivity::class.java).apply {
    putExtra("chatId", chatId)
}
```
All 3 paths are handled in `ChatActivity.initViews()`.
