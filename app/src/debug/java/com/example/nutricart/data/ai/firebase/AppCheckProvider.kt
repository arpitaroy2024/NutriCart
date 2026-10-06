package com.example.nutricart.data.ai.firebase

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

// Debug builds: the debug provider. It makes a token on the device and writes it to logcat
// once; that token is registered by hand in the Firebase console and is never put in a file.
internal fun appCheckProviderFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
