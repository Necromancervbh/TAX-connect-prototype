# TaxConnect Push Notification API (Backend)

This is a simple Node.js Express application that handles sending Firebase Cloud Messaging (FCM) push notifications. It replaces Firebase Cloud Functions, allowing you to use a free hosting tier.

## How it works
The Android app calls this API whenever a user sends a chat message or initiates a video call. This API uses the Firebase Admin SDK to fetch the recipient's FCM token and securely dispatch a "data-only" push notification to their device.

## Prerequisites
1. **Firebase Service Account:** You need a Firebase Service Account key file (`serviceAccountKey.json`).
    * Go to Firebase Console > Project Settings > Service Accounts.
    * Click "Generate new private key".
    * Keep this file secure!

## Local Testing
1. Copy your downloaded Firebase `serviceAccountKey.json` into the `backend` folder.
2. In the `backend` directory, run:
   ```bash
   npm install
   ```
3. Create a `.env` file in the `backend` directory:
   ```env
   FIREBASE_SERVICE_ACCOUNT_PATH=./serviceAccountKey.json
   PORT=3000
   ```
4. Start the server:
   ```bash
   npm start
   ```
5. Note your local IP or use `ngrok http 3000` to expose the API to the internet, and update `BASE_URL` in `ApiClient.kt` in the Android app to test it.

## Deployment to Render.com (Free Tier)
1. **Push to GitHub:** Commit this `backend` folder to a GitHub repository.
2. **Create Web Service:** Create a free account on [Render.com](https://render.com) and create a new "Web Service".
3. **Connect Repository:** Link your GitHub repository and select the `backend` directory if it's a monorepo, or directly if it's a separate repo.
4. **Environment Setup:** 
    * Build Command: `npm install`
    * Start Command: `npm start`
5. **Add Environment Variables:** 
    * Instead of uploading the `serviceAccountKey.json` file, base64 encode it and store it as an environment variable to keep it secure.
    * Run this command to encode it:
      * macOS/Linux: `base64 -i serviceAccountKey.json | pbcopy`
      * Windows (PowerShell): `[Convert]::ToBase64String([IO.File]::ReadAllBytes("serviceAccountKey.json")) | clip`
    * On Render.com, add an Environment Variable named `FIREBASE_SERVICE_ACCOUNT_BASE64` and paste the encoded string.
6. **Deploy:** Click "Create Web Service".
7. **Update Android App:** Once deployed, Render will give you a live URL (e.g., `https://taxconnect-push.onrender.com/`). Open `ApiClient.kt` in the Android App and update the `BASE_URL` to point to your new live API.
