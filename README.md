# Chatterino-Mobile

An unofficial mobile Twitch chat client inspired by **Chatterino** and **Chatterino7**. Built with Kotlin and Jetpack Compose, with an eye toward Kotlin Multiplatform (KMP) down the road.

**Status:** Early development. The data layer is working; chat UI, WebSockets, and paint rendering are next. This is basically a learning project for me to figure out high-performance rendering in Compose.

---

### Why this exists
The official Twitch app is pretty bloated. It ignores third-party emotes and wastes a ton of screen space on stuff I don't care about. Existing alternatives are either dead, don't support 7TV/BTTV/FFZ, or use non-native frameworks that lag when chat starts moving fast.

I wanted something lightweight and native with:
* **7TV emotes & paints** (animated webp, zero-width stacking, and username gradients).
* **BTTV & FFZ emotes.**
* **Twitch native** emotes and badges.
* **Fast rendering:** Needs to handle hundreds of messages per minute without dropping frames.

### Tech stack
* **UI:** Jetpack Compose
* **Async:** Coroutines + Flow
* **Networking:** Ktor (OkHttp engine)
* **Serialization:** kotlinx.serialization
* **DI:** Koin
* **Target:** MVVM architecture. The data/domain layers are pure Kotlin (no Android imports) so I can hopefully share them with an iOS client later.


