package app.overfit.share

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** iOS: the camera app scans the QR code and opens the link (see docs/DECISIONS.md), so there is no in-app scanner. */
@Composable
actual fun QrScanner(modifier: Modifier, onScanned: (String) -> Unit, onPermissionDenied: () -> Unit) = Unit
