# CamCon

CamCon is an Android application for secure local-network camera sharing.

It allows one Android phone to share its camera and microphone feed over the same Wi-Fi network, while the owner keeps control over who can access the feed.

## Features

- Share camera feed over local Wi-Fi
- Live video streaming
- Live audio streaming
- Browser-based viewer
- Password-protected access
- Owner approval before streaming
- Allow or deny viewer requests
- Front and back camera switching
- Local video recording
- Save recordings to the phone
- Dark user interface
- Android Jetpack Compose UI


## How It Works

CamCon uses an owner-controlled access flow:

Password → Access Request → Owner Approval → Live Feed

The password alone does not grant access.

The camera owner must explicitly approve every viewer request.

## Usage

### 1. Share My Camera

On the Android phone that will act as the camera:

1. Open CamCon.
2. Select **Share My Camera**.
3. Set an access password.
4. Start camera sharing.
5. The app displays the local IP address.

### 2. See Other Feed

On another phone connected to the same Wi-Fi network:

1. Open CamCon.
2. Select **See Other Feed**.
3. Enter the camera phone's IP address.
4. Enter the password.
5. Send an access request.

The viewer must wait for the camera owner to approve the request.

### 3. Owner Approval

The camera owner can:

- Allow the viewer
- Deny the viewer
- Disconnect an approved viewer

After approval, the viewer can receive the live video and audio feed.

## Browser Viewer

CamCon also supports browser-based viewing.

On a device connected to the same Wi-Fi network, open:

http://CAMERA_IP:8080/viewer

The browser will ask for the access password and wait for approval from the camera owner.

After approval, the browser receives the live video and audio feed.

## Requirements

- Android device with camera and microphone
- Android 8.0 (API 26) or higher
- Camera and microphone permissions
- Camera and viewer devices must be connected to the same local Wi-Fi network

## Technology

- Kotlin
- Jetpack Compose
- CameraX
- Android AudioRecord
- Embedded HTTP server
- MJPEG video streaming
- PCM audio streaming
- Web Audio API

## Recording

CamCon supports local video recording on the camera phone.

Recorded videos are saved to the device and can be accessed through the phone's media storage.

## Project Structure

CamCon/
├── app/
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── .gitignore
└── README.md

## Development

Clone the repository:

git clone https://github.com/sumujnibeen/CamCon.git

Open the project in Android Studio and allow Gradle to sync.

Make sure the required Android SDK and build tools are installed.

## Security

CamCon uses an owner-controlled access system.

The access flow is:

Password → Access Request → Owner Approval → Live Feed

A valid password alone is not sufficient to access the camera feed.

The camera owner has control over viewer access and can deny or disconnect a viewer.

## Limitations

- Camera and viewer devices must be on the same local network.
- Internet-based remote streaming is not currently supported.
- Streaming performance depends on the local Wi-Fi network and device hardware.

## License

This project is licensed under the MIT License.

## Author

**Shafiul Mujnibeen**

Undergraduate Researcher | AI/ML Enthusiast
