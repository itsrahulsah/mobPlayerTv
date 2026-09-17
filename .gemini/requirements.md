# Android TV App - Development Plan

### **Phase 1: Project Setup & Architecture**
*   **Task 1.1:** Initialize the Android TV project using Kotlin (1.9+) and configure the build for Coroutines and Flow.
*   **Task 1.2:** Add necessary dependencies to `build.gradle`, including Ktor (Server-CIO engine), `kotlinx.serialization` (JSON), AndroidX Media3 (ExoPlayer), and AndroidX Security (`EncryptedSharedPreferences`).

### **Phase 2: Core Server & Background Execution**
*   **Task 2.1:** Implement the Ktor WebSocket server to handle local client connections and message routing.
*   **Task 2.2:** Wrap the WebSocket server inside an Android **Foreground Service** to ensure the OS does not kill it during media playback or when backgrounded.
*   **Task 2.3:** Implement data models using `kotlinx.serialization` for incoming commands and outgoing state broadcasts.

### **Phase 3: Network Discovery & Resilience**
*   **Task 3.1:** Implement Android Network Service Discovery (NSD) to broadcast a unique service name and the Ktor server's port when the app or service launches.
*   **Task 3.2:** Create a network state listener (e.g., using `ConnectivityManager`) to detect network changes (like switching between Ethernet and Wi-Fi) or DHCP lease renewals.
*   **Task 3.3:** Wire the network listener to automatically restart the NSD broadcast and re-bind the server socket if the IP address changes.

### **Phase 4: Authentication & Connection Management**
*   **Task 4.1:** Build the TV UI to display a randomly generated 4-digit PIN when an unauthenticated device attempts a WebSocket connection.
*   **Task 4.2:** Implement token generation upon successful PIN verification and save it securely using `EncryptedSharedPreferences`.
*   **Task 4.3:** Implement the connection restriction logic to support only **one active controller**. 
*   **Task 4.4:** Build the TV UI prompt ("Allow" or "Reject") that triggers when a second device attempts to connect, and write the logic to terminate the old session if the new one is allowed.

### **Phase 5: Media Integration & State Sync**
*   **Task 5.1:** Set up AndroidX Media3 (ExoPlayer) and link it to Android's `MediaSession`.
*   **Task 5.2:** Map incoming WebSocket commands to trigger `MediaSession` actions (Play, Pause, Seek).
*   **Task 5.3:** Observe local media state changes (playback status) and volume level changes.
*   **Task 5.4:** Implement the WebSocket broadcast logic to push these state updates to the connected mobile client in real-time.

### **Phase 6: Testing & Quality Assurance**
*   **Task 6.1:** Write unit tests for ViewModels, Ktor WebSocket routing, and `MediaSession` logic.
*   **Task 6.2:** Write UI/Instrumentation tests for the PIN pairing screen, the connection prompt dialog, and the media playback controls.
*   **Task 6.3:** Write integration tests to verify the full flow from receiving a WebSocket command to executing the corresponding ExoPlayer action.
*   **Task 6.4:** Measure command execution time to ensure end-to-end latency remains under the 100ms threshold.
*   **Task 6.5:** Test edge cases (force killing network connections, switching Wi-Fi bands) to verify that the app operates entirely local-only without cloud dependencies.
