# Koda — Feature Roadmap

## Vision
Koda is a native Android AI chat app that runs OpenClaude locally via Termux.
Self-contained APK, no cloud account needed — just an API key and go.

## Design Reference
- **Claude app** — warm minimal aesthetic, wide content column, comfortable reading
- **ChatGPT** — sidebar conversations, model switcher, polished input bar
- **RikkaHub** — Material You, multi-provider, Markdown rendering, dark mode
- **Gemini** — silky animations, native Material Design 3, excellent mobile UX

## Design Principles
1. **Dark-first** — Slate palette (#0F172A bg, #1E293B surfaces), accent #7DD3FC
2. **Material You where it matters** — rounded corners, elevation, transitions
3. **Content is king** — wide message column, generous padding, readable fonts
4. **No clutter** — features accessible but not in-your-face
5. **Animations for delight** — message appear, typing indicator, drawer slide

---

## Phase 1 — Core Polish (Current → v0.2)
*Make what we have feel professional*

### 1.1 ✅ Streaming responses (DONE — commit c20650b)
### 1.2 ✅ Message bubbles (DONE)
### 1.3 ✅ Session continuity via --resume (DONE)

### 1.4 Markdown rendering
- Bold, italic, inline code, code blocks with syntax highlight
- Lists (ordered + unordered)
- Links (clickable)
- Library: Markwon (mature, lightweight)

### 1.5 Typing indicator
- Animated dots while waiting for first token
- Disappears when streaming starts

### 1.6 Copy message
- Long-press on any message → copy to clipboard
- Toast confirmation

### 1.7 Input bar improvements
- Auto-grow multi-line (up to 6 lines, then scroll)
- Send button changes color when input is non-empty
- Keyboard handling (don't jump on orientation change)

---

## Phase 2 — Conversation Management (v0.3)
*Navigate between conversations like a real chat app*

### 2.1 Conversation list (drawer)
- Slide-from-left drawer or bottom sheet
- Shows recent conversations with title + preview + timestamp
- Tap to switch, swipe to delete
- "New conversation" button

### 2.2 Conversation persistence
- Store conversations in SQLite (Room-like, or raw SQLiteDatabase)
- Save: messages, session_id, model used, timestamps
- Survive app restart

### 2.3 Auto-title
- After first assistant response, generate a title
- Use first ~50 chars of user message as fallback
- Later: ask model to generate title (optional)

### 2.4 Search conversations
- Search bar in drawer
- Full-text search across message content

---

## Phase 3 — Multi-Provider & Settings (v0.4)
*Use any LLM provider, not just RelayGPU*

### 3.1 Provider management
- Settings screen with provider list
- Each provider: name, base URL, API key, available models
- Pre-configured templates:
  - RelayGPU (Anthropic)
  - OpenAI
  - Anthropic (direct)
  - OpenRouter
  - Ollama (local)
  - Custom OpenAI-compatible

### 3.2 Model selector
- Dropdown/chip in chat header or input bar
- Shows models from active provider
- Remember last used model per conversation

### 3.3 API key management
- Securely stored (EncryptedSharedPreferences or Android Keystore)
- Show/hide toggle
- Test connection button per provider

### 3.4 Settings screen
- Theme (dark/light/system)
- Default provider + model
- Max tokens
- Temperature slider
- System prompt (global default)

---

## Phase 4 — Rich Features (v0.5)
*Differentiate from basic chat apps*

### 4.1 System prompt per conversation
- Set custom system prompt when creating new conversation
- Library of saved prompts / personas

### 4.2 Message editing
- Tap edit on user message → re-send from that point
- Message branching (keep history tree, navigate branches)

### 4.3 File/image input
- Attach images from gallery or camera
- Multimodal: send to vision-capable models
- PDF/text file attachment → extract and include in prompt

### 4.4 Export/share
- Export conversation as Markdown
- Share individual responses
- Copy code blocks with one tap

### 4.5 Token usage display
- Show tokens used per message (from stream_event data)
- Running total per conversation
- Cost estimate (configurable $/1K tokens per model)

---

## Phase 5 — Premium UX (v0.6)
*The details that make it feel like a $50M app*

### 5.1 Animations
- Message slide-in from bottom (user) and left (assistant)
- Typing indicator pulse
- Drawer slide with spring physics
- Send button morph (idle → sending → done)
- Smooth scroll-to-bottom with FAB

### 5.2 Haptic feedback
- Light haptic on send
- Medium haptic on long-press actions

### 5.3 Material You dynamic colors
- Optional: derive accent from wallpaper (Android 12+)
- Keep slate dark as default for brand identity

### 5.4 Onboarding flow
- First-launch tutorial (3 swipeable cards)
- Quick provider setup wizard
- Sample conversation to show off capabilities

### 5.5 Widgets
- Home screen widget: quick prompt → opens with response
- Notification quick reply

---

## Phase 6 — Power Features (v0.7+)
*For developers and power users*

### 6.1 MCP (Model Context Protocol) support
- Configure MCP servers
- Tool use visualization in chat

### 6.2 Code execution sandbox
- Run code blocks in Termux environment
- Show output inline

### 6.3 Voice input/output
- Speech-to-text for input
- TTS for responses (system or custom)

### 6.4 Conversation sync
- Optional: sync conversations via user's own storage
- Export/import JSON backup

### 6.5 Plugin system
- Custom tools
- Custom pre/post processors
- Community prompt library

---

## Technical Decisions

### UI Framework
**Java + XML** (current) — we're already here, it works, Maxi compiles it.
Consider Kotlin + Jetpack Compose migration for Phase 5+ if needed.

### Markdown Rendering
**Markwon** — battle-tested Android Markdown library
- `markwon-core` for basics
- `markwon-syntax-highlight` for code (via Prism4j)
- `markwon-strikethrough`, `markwon-tables` for extras

### Database
**SQLite** via Android's built-in SQLiteOpenHelper
- Simple, no extra dependencies
- Tables: conversations, messages, providers, models

### Secure Storage
**EncryptedSharedPreferences** (AndroidX Security)
- For API keys only
- Everything else in regular SharedPreferences or SQLite

### Architecture
Keep it simple:
- Activities (not Fragments for now)
- Service for OpenClaude process management
- AsyncTask/Thread for background work (already working)
- Consider ViewModel + LiveData for Phase 3+

---

## Priority Order
1. **Phase 1.4** (Markdown) — biggest visual impact
2. **Phase 1.5-1.7** (Input polish) — small effort, big feel
3. **Phase 2.1-2.3** (Conversations) — essential functionality
4. **Phase 3.1-3.3** (Multi-provider) — unlocks all LLMs
5. Everything else follows naturally
