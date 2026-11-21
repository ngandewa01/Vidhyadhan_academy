-keep class in.vidyadhan.academy.** { *; }

# Google sign-in (Credential Manager): keep the Play services provider, which is found by name at run time.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
