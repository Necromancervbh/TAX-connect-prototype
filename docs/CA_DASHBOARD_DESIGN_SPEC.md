# CA Dashboard Redesign Specification

## Purpose
Deliver a modern, intuitive CA dashboard that preserves all existing data, interactions, and workflows while improving visual hierarchy, navigation efficiency, and accessibility. The implemented screen serves as a high‑fidelity prototype for user testing.

## Information Architecture (Current vs. Redesigned)
### Current
- Header with menu, messages, and profile actions
- Online status toggle
- Requests and bookings cards
- Business overview with revenue and clients
- Community card
- Bookings list with filters and sorting

### Redesigned
1. Header: global navigation and help
2. Onboarding banner: contextual guidance for first‑time use
3. Quick actions: direct access to Requests, Bookings, Wallet, Messages
4. Priority: pending requests and bookings
5. Business Overview: revenue, wallet, and clients
6. Community: discovery and networking entry point
7. Bookings: filters, sorting, and list

This structure groups decision‑critical content early and moves exploratory content later in the flow.

## User Workflows
1. Handle incoming requests
2. Review new bookings
3. Monitor revenue and wallet
4. Track client activity
5. Navigate to chats and documents
6. Filter and sort bookings list

The redesigned layout reduces navigation steps by adding quick actions and in‑context help.

## Visual Hierarchy
- Primary: Priority section and Business Overview cards
- Secondary: Onboarding banner, quick actions
- Tertiary: Community and bookings filters
- Emphasis: Title text and key metrics using size and weight
- De‑emphasis: Supporting labels and captions

## Layout Structure
### Header
- Menu, Messages, Help, Profile
- Online status toggle aligned with header

### Onboarding Banner
- Title, short explanation, and actions: View tips, Got it
- Dismissed state stored in shared preferences

### Quick Actions
- Two rows of outlined buttons
- Direct access to Requests, Bookings, Wallet, Messages

### Priority
- Pending Requests card
- Pending Bookings card

### Business Overview
- Revenue card with wallet and breakdowns
- Active Clients card with returning clients

### Community
- Explore peers and knowledge sharing

### Bookings
- Filter chips: Upcoming, Expired
- Sort selector: Date ↑, Date ↓, Urgency
- List view and empty state

## Responsive Behavior
- Content uses full‑width cards and stacked sections for small screens
- Quick actions are arranged in two rows to reduce horizontal compression
- Metrics cards remain side‑by‑side but scale with available width
- Layout maintains consistent spacing using existing dimension tokens

## Accessibility
- Icon buttons include content descriptions
- Tooltips provide context for icons and filters
- Touch targets follow the existing touch target dimensions
- Text hierarchy uses existing Material typography styles

## Contextual Help
- Global help entry in header and navigation drawer
- Tooltips for key controls: filters, sorting, metrics
- Onboarding banner explains how to use the dashboard

## Prototype for User Testing
The redesigned CA dashboard implementation serves as Prototype v1:
- Evaluate comprehension of Priority and Business Overview
- Validate quick actions discoverability
- Verify users understand filters and sorting

### Suggested Testing Tasks
1. Find and open pending requests
2. Filter bookings to Expired and sort by Urgency
3. Locate wallet balance and revenue breakdown
4. Access messages from the dashboard

### Iteration Plan
- Collect feedback on clarity and navigation speed
- Adjust section ordering or labels as needed
- Refine onboarding copy and tooltip wording

## Component Behaviors
### Onboarding Banner
- Visible on first launch
- Dismissed with Got it and never shown again
- View tips opens help dialog

### Quick Actions
- Requests: opens RequestsActivity
- Bookings: opens MyBookingsActivity
- Wallet: opens WalletActivity
- Messages: opens MyChatsActivity

### Priority Cards
- Requests and bookings cards navigate to their respective screens
- Text color emphasizes non‑zero counts

### Business Overview
- Revenue card opens BalanceSheetActivity
- Active Clients card opens MyChatsActivity
- Revenue breakdown displays Today, Week, Month

### Bookings Filters
- Upcoming and Expired chips are mutually exclusive
- Sort spinner updates list ordering

## Implementation Guidelines
- Keep existing IDs for all data bindings and interactions
- Add new IDs only for onboarding, help, and quick action controls
- Use existing styles and dimension tokens
- Avoid introducing new dependencies or custom widgets
- Ensure tooltips and content descriptions for new icons

## QA Checklist
- All data binds correctly after layout changes
- Help dialog and onboarding behave as expected
- Quick actions navigate correctly
- Bookings filters and sort still operate
- Layout remains readable on small screens
