# R8 keeps what kotlinx.serialization and Koin need from their own consumer rules.
#
# Linphone (full flavor) brings its own too: they keep the org.linphone.core classes its native
# code calls back, and org.linphone.mediastream.** whole, which is what mediastreamer's JNI reaches
# for video (the camera capture, the TextureView display). Nothing for video is added here.
