
# AirControl Car App - Wireless Control Using Hand and Foot Movements

This Android app allows users to control a robotic vehicle using **hand and foot gestures** detected via the **front camera**. It sends real-time commands to an **ESP32-powered robotic car** over **UDP via Wi-Fi**, and also displays the **real-time video feed streamed from the ESP32 camera module**.

This app is designed to work with the [AirControl Car ESP32](https://github.com/kennygoooo/gesture-control-ESP32-robotic-vehicle).

---

## Features

- Uses the phone’s **front-facing camera** for gesture detection
- Supports **hand and foot gestures** for driving commands
- Sends UDP packets to an ESP32 robot over Wi-Fi
- Displays MJPEG live stream from the ESP32’s OV2640 camera
- Lightweight Android app with intuitive UI
- Real-time control with minimal delay

---

## Installation

1. Clone or download the repository:
   ```bash
   git clone https://github.com/kennygoooo/gesture-control-app.git
   ```

2. Open the project in **Android Studio**.

3. Build and run the app on your Android device (minimum SDK: 21).

---

## How to Use

1. **Connect to ESP32 Wi-Fi** on your Android phone:
   - **SSID:** `ESP32-Car`
   - **Password:** `12345678`

2. **Launch the app**.

3. The front camera will activate and begin detecting gestures.

4. Recognized gestures will send corresponding commands (e.g., `left`, `right`, `spead_up`, `stop`) over UDP to the ESP32.

---

## ESP32 Robotic Vehicle Project

To use this app, the robotic vehicle must be running the companion project:
[AirControl Car ESP32](https://github.com/kennygoooo/gesture-control-ESP32-robotic-vehicle)

---

### Demo Video

Watch the project in action:  
[YouTube Demo](https://youtube.com/shorts/OtwPKSwx-Y8)

---

### System Architecture
![System Diagram](https://drive.google.com/uc?export=view&id=117BBJ2OMhRqG3LdELHGqwN1iL7YeLGva)

---

